package com.wifipriority.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Registers [WifiNetworkSuggestion]s chosen by [SuggestionPolicy]. Phone suggestions
 * use a higher [SuggestionPlanner.PHONE_PRIORITY] and priority group than the truck.
 * Force-return removes the truck suggestion with
 * [WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT], which disconnects only when
 * the current association is that suggestion. A network saved by hand in Android
 * settings stays connected; the status screen tells the user to forget it.
 *
 * The connected SSID comes from [NetworkCapabilities.getTransportInfo] as [WifiInfo]
 * with [ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO].
 * `WifiManager.getConnectionInfo()` is not used.
 */
class SuggestionNetworkConnector(
    context: Context,
) : NetworkConnector {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val connected = MutableStateFlow<String?>(null)
    private val linkState = MutableStateFlow(LinkTracker().snapshot())
    private val failures = MutableSharedFlow<ConnectionFailure>(extraBufferCapacity = 8)
    private val addStatus = MutableStateFlow<Int?>(null)
    private val approval = MutableStateFlow(SuggestionApproval.UNKNOWN)
    private val mutex = Mutex()
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var approvalListener: WifiManager.SuggestionUserApprovalStatusListener? = null
    private var connectionListener: WifiManager.SuggestionConnectionStatusListener? = null
    private var postConnectionReceiver: BroadcastReceiver? = null
    private val listeners = ListenerGate()
    private val links = LinkTracker()
    @Volatile private var appliedKey: String? = null

    override val connectedSsid: Flow<String?> = connected.asStateFlow()
    override val link: Flow<LinkObservation> = linkState.asStateFlow()
    override val connectionFailures: Flow<ConnectionFailure> = failures.asSharedFlow()
    override val suggestionAddStatus: Flow<Int?> = addStatus.asStateFlow()
    val approvalState: StateFlow<SuggestionApproval> = approval.asStateFlow()

    fun start() {
        if (!listeners.tryOpen()) return
        try {
            registerCallback()
            registerApprovalListener()
            registerConnectionListener()
            registerPostConnection()
        } catch (error: RuntimeException) {
            stop()
            throw error
        }
    }

    fun stop() {
        if (!listeners.close()) return
        callback?.let { active ->
            runCatching { connectivity.unregisterNetworkCallback(active) }
        }
        callback = null
        approvalListener?.let { listener ->
            runCatching { wifiManager.removeSuggestionUserApprovalStatusListener(listener) }
        }
        approvalListener = null
        connectionListener?.let { listener ->
            runCatching { wifiManager.removeSuggestionConnectionStatusListener(listener) }
        }
        connectionListener = null
        postConnectionReceiver?.let { receiver ->
            runCatching { appContext.unregisterReceiver(receiver) }
        }
        postConnectionReceiver = null
    }

    override suspend fun applyTarget(target: WifiTarget) {
        // The coordinator applies the suggestion list. The target is recorded by the engine.
    }

    @SuppressLint("MissingPermission")
    override suspend fun applySuggestions(
        suggestions: List<PlannedSuggestion>,
        disconnectSsids: Set<String>,
        force: Boolean,
    ): SuggestionCommit {
        val key = suggestionContentKey(suggestions)
        return mutex.withLock {
            if (!force && key == appliedKey && disconnectSsids.isEmpty()) return SuggestionCommit.APPLIED
            val commit = withContext(Dispatchers.IO) {
                replace(suggestions, disconnectSsids)
            }
            if (commit == SuggestionCommit.APPLIED) appliedKey = key else appliedKey = null
            commit
        }
    }

    @SuppressLint("MissingPermission")
    private fun replace(planned: List<PlannedSuggestion>, disconnectSsids: Set<String>): SuggestionCommit {
        val existing = wifiManager.networkSuggestions
        val kick = existing.filter { it.safeSsid() in disconnectSsids }
        val rest = existing.filter { it.safeSsid() !in disconnectSsids }
        if (kick.isNotEmpty()) {
            wifiManager.removeNetworkSuggestions(kick, WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT)
        }
        if (rest.isNotEmpty()) {
            wifiManager.removeNetworkSuggestions(rest)
        }
        if (planned.isEmpty()) return SuggestionCommit.APPLIED
        val built = planned.map { suggestion ->
            val builder = WifiNetworkSuggestion.Builder()
                .setSsid(suggestion.ssid)
                .setPriority(suggestion.priority)
                .setPriorityGroup(suggestion.priorityGroup)
            when (suggestion.security) {
                WifiSecurity.WPA3 -> builder.setWpa3Passphrase(suggestion.passphrase)
                WifiSecurity.OPEN -> Unit
                WifiSecurity.WPA2, WifiSecurity.UNKNOWN, WifiSecurity.TRANSITION ->
                    builder.setWpa2Passphrase(suggestion.passphrase)
            }
            builder.build()
        }
        val status = wifiManager.addNetworkSuggestions(built)
        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS &&
            status != WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE
        ) {
            Log.w(TAG, "addNetworkSuggestions status $status")
        }
        addStatus.value = status
        noteAddStatus(status)
        return suggestionCommitFor(suggestionAddKind(status), removedOnly = false)
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
        val networkCallback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val info = caps.transportInfo as? WifiInfo
                val ssid = normalizePlatformSsid(info?.ssid)
                val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                publish(links.onCapabilities(network, ssid, validated))
            }

            override fun onLost(network: Network) {
                val next = links.onLost(network) ?: return
                publish(next)
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
                -> {
                    appliedKey = null
                    SuggestionApproval.REJECTED
                }
                WifiManager.STATUS_SUGGESTION_APPROVAL_PENDING,
                -> SuggestionApproval.PENDING
                else -> SuggestionApproval.UNKNOWN
            }
        }
        approvalListener = listener
        wifiManager.addSuggestionUserApprovalStatusListener(appContext.mainExecutor, listener)
    }

    @SuppressLint("MissingPermission")
    private fun registerConnectionListener() {
        val listener = WifiManager.SuggestionConnectionStatusListener { suggestion, status ->
            val reason = when (status) {
                WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_AUTHENTICATION -> FailureReason.AUTHENTICATION
                WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_ASSOCIATION -> FailureReason.ASSOCIATION
                WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_IP_PROVISIONING -> FailureReason.IP_PROVISIONING
                else -> FailureReason.UNKNOWN
            }
            failures.tryEmit(ConnectionFailure(suggestion.safeSsid(), reason))
        }
        connectionListener = listener
        wifiManager.addSuggestionConnectionStatusListener(appContext.mainExecutor, listener)
    }

    private fun registerPostConnection() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION) return
                val suggestion = intent.getParcelableExtra(
                    WifiManager.EXTRA_NETWORK_SUGGESTION,
                    WifiNetworkSuggestion::class.java,
                ) ?: return
                publish(links.onPosted(suggestion.safeSsid()))
            }
        }
        postConnectionReceiver = receiver
        appContext.registerReceiver(
            receiver,
            IntentFilter(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun publish(observation: LinkObservation) {
        connected.value = observation.connectedSsid
        linkState.value = observation
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

private fun WifiNetworkSuggestion.safeSsid(): String? {
    val named = wifiSsid
    return if (named != null) {
        ssidFromOctets(named.bytes, named.toString())
    } else {
        normalizePlatformSsid(ssid)
    }
}
