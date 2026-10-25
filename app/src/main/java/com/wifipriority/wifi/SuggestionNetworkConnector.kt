package com.wifipriority.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Device-wide connection via [WifiNetworkSuggestion]. Phone suggestions use a
 * higher priority than truck suggestions. When the engine wants the phone but
 * the tablet is still on the truck, the truck suggestion is removed until the
 * phone is the connected SSID, then it is registered again.
 *
 * The connected SSID is read from [NetworkCapabilities.getTransportInfo] as a
 * [WifiInfo], with [ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO].
 * `WifiManager.getConnectionInfo()` is not used.
 */
class SuggestionNetworkConnector(
    context: Context,
    private val credentials: CredentialStore,
) : NetworkConnector {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val connected = MutableStateFlow<String?>(null)
    private val desired = MutableStateFlow(WifiTarget.NONE)
    private val approval = MutableStateFlow(SuggestionApproval.UNKNOWN)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private val mutex = Mutex()
    private var suppressingTruck = false
    private var lastPlan: SuggestionPlanner.Plan? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var approvalListener: WifiManager.SuggestionUserApprovalStatusListener? = null
    private var started = false

    override val connectedSsid: Flow<String?> = connected.asStateFlow()
    val approvalState: StateFlow<SuggestionApproval> = approval.asStateFlow()

    fun start() {
        if (started) return
        started = true
        registerCallback()
        registerApprovalListener()
        scope.launch {
            combine(desired, connected, credentials.config) { target, ssid, config ->
                Triple(target, ssid, config)
            }.collect {
                sync()
            }
        }
    }

    fun stop() {
        callback?.let { active ->
            runCatching { connectivity.unregisterNetworkCallback(active) }
        }
        callback = null
        approvalListener?.let { listener ->
            runCatching { wifiManager.removeSuggestionUserApprovalStatusListener(listener) }
        }
        approvalListener = null
        job.cancel()
    }

    override suspend fun applyTarget(target: WifiTarget) {
        desired.value = target
        sync()
    }

    private suspend fun sync() {
        mutex.withLock {
            val config = credentials.load()
            val plan = SuggestionPlanner.plan(
                target = desired.value,
                connectedSsid = connected.value,
                config = config,
                suppressingTruck = suppressingTruck,
            )
            if (plan == lastPlan) return
            suppressingTruck = plan.suppressingTruck
            lastPlan = plan
            replaceSuggestions(plan.suggestions)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun replaceSuggestions(planned: List<SuggestionPlanner.PlannedSuggestion>) {
        withContext(Dispatchers.IO) {
            val existing = wifiManager.networkSuggestions
            if (existing.isNotEmpty()) {
                wifiManager.removeNetworkSuggestions(existing)
            }
            if (planned.isEmpty()) return@withContext
            val built = planned.map { suggestion ->
                WifiNetworkSuggestion.Builder()
                    .setSsid(suggestion.ssid)
                    .setWpa2Passphrase(suggestion.passphrase)
                    .setPriority(suggestion.priority)
                    .setPriorityGroup(suggestion.priorityGroup)
                    .build()
            }
            val status = wifiManager.addNetworkSuggestions(built)
            if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS &&
                status != WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE
            ) {
                Log.w(TAG, "addNetworkSuggestions status $status")
            }
            noteAddStatus(status)
        }
    }

    private fun noteAddStatus(status: Int) {
        when (status) {
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED,
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED,
            -> approval.value = SuggestionApproval.REJECTED
            WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
            -> if (approval.value == SuggestionApproval.UNKNOWN) {
                approval.value = SuggestionApproval.PENDING
            }
        }
    }

    private fun registerCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val networkCallback = object : ConnectivityManager.NetworkCallback(
            FLAG_INCLUDE_LOCATION_INFO,
        ) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val info = caps.transportInfo as? WifiInfo
                connected.value = normalizePlatformSsid(info?.ssid)
            }

            override fun onLost(network: Network) {
                connected.value = null
            }
        }
        callback = networkCallback
        connectivity.registerNetworkCallback(request, networkCallback)
    }

    private fun registerApprovalListener() {
        val listener = WifiManager.SuggestionUserApprovalStatusListener { status ->
            approval.value = when (status) {
                WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER,
                WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_CARRIER_PRIVILEGE,
                -> SuggestionApproval.APPROVED
                WifiManager.STATUS_SUGGESTION_APPROVAL_REJECTED_BY_USER,
                -> SuggestionApproval.REJECTED
                WifiManager.STATUS_SUGGESTION_APPROVAL_PENDING,
                -> SuggestionApproval.PENDING
                else -> SuggestionApproval.UNKNOWN
            }
        }
        approvalListener = listener
        wifiManager.addSuggestionUserApprovalStatusListener(appContext.mainExecutor, listener)
    }

    private companion object {
        const val TAG = "SuggestionConnector"
    }
}

enum class SuggestionApproval {
    UNKNOWN,
    PENDING,
    APPROVED,
    REJECTED,
}
