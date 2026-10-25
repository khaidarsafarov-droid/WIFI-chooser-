package com.wifipriority.service

import com.wifipriority.engine.EngineState
import com.wifipriority.engine.PriorityEngine
import com.wifipriority.wifi.CredentialStore
import com.wifipriority.wifi.NetworkConnector
import com.wifipriority.wifi.ScanSource
import com.wifipriority.wifi.SuggestionApproval
import com.wifipriority.wifi.WifiTarget
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) {
    suspend fun run() {
        var state = EngineState()
        coroutineScope {
            val approvalJob = launch {
                approval.collect { value ->
                    status.update { it.copy(suggestionApproval = value) }
                }
            }
            val connectedJob = launch {
                connector.connectedSsid.collect { ssid ->
                    status.update { it.copy(connectedSsid = ssid) }
                }
            }
            try {
                scans.scans.collect { snapshot ->
                    val config = credentials.load()
                    if (!snapshot.wifiEnabled) {
                        status.update {
                            it.copy(
                                running = true,
                                enabled = config.enabled,
                                wifiEnabled = false,
                                target = state.target,
                                lastSwitchTimeMillis = state.lastSwitchTimeMillis,
                            )
                        }
                        return@collect
                    }
                    val step = engine.onScan(state, snapshot, config)
                    state = step.state
                    if (config.enabled && step.decision.shouldApply) {
                        connector.applyTarget(step.decision.target)
                    }
                    val connected = connector.connectedSsid.first()
                    status.update {
                        it.copy(
                            running = true,
                            enabled = config.enabled,
                            wifiEnabled = true,
                            connectedSsid = connected,
                            target = state.target,
                            lastSwitchTimeMillis = state.lastSwitchTimeMillis,
                        )
                    }
                }
            } finally {
                approvalJob.cancel()
                connectedJob.cancel()
            }
        }
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
)

/** Process-wide status the service writes and the settings screen reads. */
object PriorityStatusRepository {
    val status = MutableStateFlow(PriorityStatus())
}

fun shouldStartService(enabled: Boolean): Boolean = enabled
