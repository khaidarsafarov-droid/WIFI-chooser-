package com.wifipriority.service

import com.wifipriority.engine.EngineState
import com.wifipriority.engine.PriorityEngine
import com.wifipriority.wifi.Clock
import com.wifipriority.wifi.ConnectionFailure
import com.wifipriority.wifi.CredentialStore
import com.wifipriority.wifi.FailureReason
import com.wifipriority.wifi.LinkObservation
import com.wifipriority.wifi.LogEvent
import com.wifipriority.wifi.LogKind
import com.wifipriority.wifi.NetworkConnector
import com.wifipriority.wifi.PolicyObservation
import com.wifipriority.wifi.PolicyState
import com.wifipriority.wifi.ScanSource
import com.wifipriority.wifi.SuggestionApproval
import com.wifipriority.wifi.SuggestionPlanner
import com.wifipriority.wifi.SuggestionPolicy
import com.wifipriority.wifi.WallClock
import com.wifipriority.wifi.WifiSecurity
import com.wifipriority.wifi.WifiTarget
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs [PriorityEngine] on each trustworthy scan and applies the target.
 * Wi-Fi-off snapshots update status only; they are not engine scans.
 */
class PriorityCoordinator(
    private val scans: ScanSource,
    private val connector: NetworkConnector,
    private val credentials: CredentialStore,
    private val engine: PriorityEngine,
    private val status: MutableStateFlow<PriorityStatus>,
    private val approval: Flow<SuggestionApproval> = flowOf(SuggestionApproval.UNKNOWN),
    private val clock: Clock = WallClock,
    private val policyStore: PolicyStateStore = MemoryPolicyStateStore(),
    private val eventLog: EventLog = MemoryEventLog(),
) {
    suspend fun run() {
        var engineState = EngineState()
        var policyState = policyStore.load()
        var link = LinkObservation()
        var visible = emptySet<String>()
        var security = emptyMap<String, WifiSecurity>()
        var lastScan: Long? = null
        var radioOn = true
        var addStatus: Int? = null
        var connectedSince: Long? = null
        var postedSuggestionSsid: String? = null
        val mutex = Mutex()
        coroutineScope {
            val approvalJob = launch {
                approval.collect { value ->
                    status.update { it.copy(suggestionApproval = value) }
                }
            }
            val connectedJob = launch {
                connector.link.collect { observed ->
                    mutex.withLock {
                        val ssidChanged = observed.connectedSsid != link.connectedSsid
                        link = observed
                        if (ssidChanged) connectedSince = clock.nowMillis()
                        if (observed.fromOurSuggestion == true) {
                            postedSuggestionSsid = observed.connectedSsid
                        }
                        publish(
                            engineState, policyState, link, visible, security, lastScan, radioOn,
                            addStatus, connectedSince, postedSuggestionSsid, authFailure = null,
                        ).also { policyState = it }
                    }
                }
            }
            val failureJob = launch {
                connector.connectionFailures.collect { failure ->
                    mutex.withLock {
                        publish(
                            engineState, policyState, link, visible, security, lastScan, radioOn,
                            addStatus, connectedSince, postedSuggestionSsid, authFailure = failure,
                        ).also { policyState = it }
                    }
                }
            }
            val statusJob = launch {
                connector.suggestionAddStatus.collect { code ->
                    if (code == null) return@collect
                    mutex.withLock {
                        addStatus = code
                        status.update { it.copy(suggestionAddStatus = code) }
                    }
                }
            }
            try {
                scans.scans.collect { snapshot ->
                    mutex.withLock {
                        val config = credentials.load()
                        val wasRadioOn = radioOn
                        if (!snapshot.wifiEnabled) {
                            radioOn = false
                            if (wasRadioOn) {
                                eventLog.append(listOf(LogEvent(clock.nowMillis(), LogKind.WIFI_OFF, "")))
                            }
                            policyState = publish(
                                engineState, policyState, link, visible, security, lastScan, radioOn,
                                addStatus, connectedSince, postedSuggestionSsid, authFailure = null,
                            )
                            return@withLock
                        }
                        radioOn = true
                        if (!wasRadioOn) {
                            eventLog.append(listOf(LogEvent(clock.nowMillis(), LogKind.WIFI_ON, "")))
                        }
                        visible = snapshot.visibleSsids
                        security = snapshot.securityBySsid
                        lastScan = snapshot.timestampMillis
                        val step = engine.onScan(engineState, snapshot, config)
                        val previousTarget = engineState.target
                        engineState = step.state
                        if (engineState.target != previousTarget) {
                            val kind = when (engineState.target) {
                                WifiTarget.PHONE -> LogKind.TARGET_PHONE
                                WifiTarget.TRUCK -> LogKind.TARGET_TRUCK
                                WifiTarget.NONE -> LogKind.TARGET_NONE
                            }
                            eventLog.append(listOf(LogEvent(clock.nowMillis(), kind, "")))
                        }
                        if (config.enabled && step.decision.shouldApply) {
                            connector.applyTarget(step.decision.target)
                        }
                        link = connector.link.first()
                        if (link.connectedSsid != null && connectedSince == null) {
                            connectedSince = clock.nowMillis()
                        }
                        policyState = publish(
                            engineState, policyState, link, visible, security, lastScan, radioOn,
                            addStatus, connectedSince, postedSuggestionSsid, authFailure = null,
                        )
                    }
                }
            } finally {
                approvalJob.cancel()
                connectedJob.cancel()
                failureJob.cancel()
                statusJob.cancel()
            }
        }
    }

    private suspend fun publish(
        engineState: EngineState,
        policyState: PolicyState,
        link: LinkObservation,
        visible: Set<String>,
        security: Map<String, WifiSecurity>,
        lastScan: Long?,
        radioOn: Boolean,
        addStatus: Int?,
        connectedSince: Long?,
        postedSuggestionSsid: String?,
        authFailure: ConnectionFailure?,
    ): PolicyState {
        val config = credentials.load()
        val now = clock.nowMillis()
        val fromSuggestion = resolveSuggestion(link, postedSuggestionSsid, connectedSince, now)
        val observation = PolicyObservation(
            enabled = config.enabled,
            target = engineState.target,
            phoneSsid = config.phoneSsid(),
            truckSsid = config.truckSsid(),
            phonePassphrase = config.phone?.password,
            truckPassphrase = config.truck?.password,
            connectedSsid = link.connectedSsid,
            visibleSsids = if (radioOn) visible else emptySet(),
            securityBySsid = security,
            validated = link.validated && link.connectedSsid != null,
            radioOn = radioOn,
            fromOurSuggestion = fromSuggestion,
            authFailure = authFailure,
        )
        val step = SuggestionPolicy.evaluate(policyState, observation, now)
        policyStore.save(step.state)
        if (step.events.isNotEmpty()) eventLog.append(step.events)
        connector.applySuggestions(step.suggestions, step.disconnectSsids, step.forceReapply)
        status.update {
            it.copy(
                running = true,
                enabled = config.enabled,
                wifiEnabled = radioOn,
                connectedSsid = link.connectedSsid,
                target = engineState.target,
                lastSwitchTimeMillis = engineState.lastSwitchTimeMillis,
                visibleSsids = visible,
                lastScanTimeMillis = lastScan,
                securityBySsid = security,
                validated = observation.validated,
                connectionFromSuggestion = fromSuggestion,
                connectedSinceMillis = connectedSince,
                suggestionAddStatus = addStatus,
                lastFailureReason = step.state.lastFailureReason,
                lastFailureSsid = step.state.lastFailureSsid,
                phoneFailures = step.state.phoneFailures,
                failureBackoffUntil = step.state.failureBackoffUntil,
                forceReturnActive = step.state.forceReturnSince != null,
                suppressPhoneUntil = step.state.suppressPhoneUntil,
            )
        }
        return step.state
    }

    private fun resolveSuggestion(
        link: LinkObservation,
        postedSuggestionSsid: String?,
        connectedSince: Long?,
        now: Long,
    ): Boolean? {
        val ssid = link.connectedSsid ?: return null
        if (link.fromOurSuggestion == true || postedSuggestionSsid == ssid) return true
        if (link.fromOurSuggestion == false) return false
        val since = connectedSince ?: return null
        return if (now - since >= SuggestionPlanner.SUGGESTION_GRACE_MILLIS) false else null
    }
}

data class PriorityStatus(
    val running: Boolean = false,
    val enabled: Boolean = false,
    val connectedSsid: String? = null,
    val target: WifiTarget = WifiTarget.NONE,
    val lastSwitchTimeMillis: Long? = null,
    val wifiEnabled: Boolean = true,
    val suggestionApproval: SuggestionApproval = SuggestionApproval.UNKNOWN,
    val visibleSsids: Set<String> = emptySet(),
    val lastScanTimeMillis: Long? = null,
    val securityBySsid: Map<String, WifiSecurity> = emptyMap(),
    val validated: Boolean = false,
    val connectionFromSuggestion: Boolean? = null,
    val connectedSinceMillis: Long? = null,
    val suggestionAddStatus: Int? = null,
    val lastFailureReason: FailureReason? = null,
    val lastFailureSsid: String? = null,
    val phoneFailures: Int = 0,
    val failureBackoffUntil: Long? = null,
    val forceReturnActive: Boolean = false,
    val suppressPhoneUntil: Long? = null,
)

/** Process-wide status the service writes and the settings screen reads. */
object PriorityStatusRepository {
    val status = MutableStateFlow(PriorityStatus())
}

fun shouldStartService(enabled: Boolean): Boolean = enabled
