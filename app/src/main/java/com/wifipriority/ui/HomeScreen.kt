package com.wifipriority.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wifipriority.R
import com.wifipriority.wifi.FailureReason
import com.wifipriority.wifi.LogEvent
import com.wifipriority.wifi.LogKind
import com.wifipriority.wifi.SuggestionAddKind
import com.wifipriority.wifi.WifiSecurity
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenSettings: () -> Unit,
    onFix: (BannerId) -> Unit,
) {
    val home by viewModel.home.collectAsState()
    val banners by viewModel.banners.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings),
                )
            }
        }
        Text(
            text = stringResource(R.string.connected_now, home.connectedSsid ?: stringResource(R.string.none_connected)),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(text = stringResource(R.string.status_switch, formatWhen(home.lastSwitchTimeMillis)))
        Text(
            text = stringResource(R.string.last_scan, formatWhen(home.lastScanTimeMillis)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(if (home.running) R.string.running else R.string.paused),
            color = if (home.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
        )
        if (home.truckNote != TruckNote.NONE) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(noteText(home.truckNote)),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        BannerList(
            banners = banners,
            onFix = onFix,
            onDismissSamsung = viewModel::dismissSamsungHint,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 720.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NetworkCardView(home.phone, Modifier.weight(1f))
                    NetworkCardView(home.truck, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NetworkCardView(home.phone, Modifier.fillMaxWidth())
                    NetworkCardView(home.truck, Modifier.fillMaxWidth())
                }
            }
        }
        DiagnosticsSection(diagnostics)
    }
}

@Composable
fun BannerList(
    banners: List<BannerId>,
    onFix: (BannerId) -> Unit,
    onDismissSamsung: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        banners.distinct().forEach { banner ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(bannerText(banner)))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onFix(banner) }) {
                            Text(text = stringResource(bannerAction(banner)))
                        }
                        if (banner == BannerId.SAMSUNG_NEVER_SLEEPING) {
                            TextButton(onClick = onDismissSamsung) {
                                Text(text = stringResource(R.string.dismiss))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkCardView(card: NetworkCard, modifier: Modifier) {
    val highlighted = card.highlighted
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(
                    if (card.role == com.wifipriority.wifi.WifiTarget.PHONE) {
                        R.string.priority_phone_title
                    } else {
                        R.string.priority_truck_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = card.ssid ?: stringResource(R.string.not_configured), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(presenceText(card.presence)))
        }
    }
}

@Composable
private fun DiagnosticsSection(diagnostics: DiagnosticsUi) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) {
        Text(text = stringResource(if (open) R.string.diagnostics_hide else R.string.diagnostics_show))
    }
    if (!open) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = stringResource(R.string.diag_help_samsung), style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(R.string.diag_last_scan, formatWhen(diagnostics.lastScanTimeMillis)))
            SeenLine(stringResource(R.string.phone_ssid), diagnostics.phoneSsid, diagnostics.phoneVisible, diagnostics.phoneSecurity)
            SeenLine(stringResource(R.string.truck_ssid), diagnostics.truckSsid, diagnostics.truckVisible, diagnostics.truckSecurity)
            Text(
                text = stringResource(
                    R.string.diag_connection,
                    diagnostics.connectedSsid ?: stringResource(R.string.none_connected),
                    suggestionLabel(diagnostics.fromOurSuggestion),
                    if (diagnostics.validated) {
                        stringResource(R.string.diag_validated)
                    } else {
                        stringResource(R.string.diag_not_validated)
                    },
                ),
            )
            Text(
                text = stringResource(
                    R.string.diag_add_status,
                    addKindLabel(diagnostics.suggestionAddKind),
                    diagnostics.suggestionAddStatus?.toString() ?: "—",
                ),
            )
            if (diagnostics.lastFailureReason != null) {
                Text(
                    text = stringResource(
                        R.string.diag_failure,
                        diagnostics.lastFailureSsid ?: "—",
                        failureLabel(diagnostics.lastFailureReason),
                    ),
                )
            }
            Text(
                text = stringResource(
                    R.string.diag_force,
                    if (diagnostics.forceReturnActive) stringResource(R.string.yes) else stringResource(R.string.no),
                    diagnostics.phoneFailures,
                ),
            )
            Text(text = stringResource(R.string.diag_events), style = MaterialTheme.typography.titleSmall)
            if (diagnostics.events.isEmpty()) {
                Text(text = stringResource(R.string.diag_no_events))
            } else {
                diagnostics.events.asReversed().forEach { event ->
                    Text(text = eventLine(event), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SeenLine(label: String, ssid: String?, visible: Boolean, security: WifiSecurity?) {
    val name = ssid ?: stringResource(R.string.not_configured)
    val seen = if (ssid == null) {
        stringResource(R.string.not_configured)
    } else if (visible) {
        stringResource(R.string.presence_visible)
    } else {
        stringResource(R.string.presence_not_visible)
    }
    Text(text = stringResource(R.string.diag_seen, label, name, seen, securityLabel(security)))
}

@Composable
private fun suggestionLabel(fromOurs: Boolean?): String = when (fromOurs) {
    true -> stringResource(R.string.diag_suggestion_yes)
    false -> stringResource(R.string.diag_suggestion_no)
    null -> stringResource(R.string.diag_suggestion_unknown)
}

@Composable
private fun securityLabel(security: WifiSecurity?): String = when (security) {
    WifiSecurity.WPA2 -> stringResource(R.string.security_wpa2)
    WifiSecurity.WPA3 -> stringResource(R.string.security_wpa3)
    WifiSecurity.TRANSITION -> stringResource(R.string.security_transition)
    WifiSecurity.OPEN -> stringResource(R.string.security_open)
    WifiSecurity.UNKNOWN, null -> stringResource(R.string.security_unknown)
}

@Composable
private fun failureLabel(reason: FailureReason): String = when (reason) {
    FailureReason.AUTHENTICATION -> stringResource(R.string.failure_authentication)
    FailureReason.ASSOCIATION -> stringResource(R.string.failure_association)
    FailureReason.IP_PROVISIONING -> stringResource(R.string.failure_ip)
    FailureReason.UNKNOWN -> stringResource(R.string.failure_unknown)
}

@Composable
private fun addKindLabel(kind: SuggestionAddKind): String = when (kind) {
    SuggestionAddKind.NONE -> stringResource(R.string.add_none)
    SuggestionAddKind.SUCCESS -> stringResource(R.string.add_success)
    SuggestionAddKind.INTERNAL -> stringResource(R.string.add_internal)
    SuggestionAddKind.APP_DISALLOWED -> stringResource(R.string.add_disallowed)
    SuggestionAddKind.DUPLICATE -> stringResource(R.string.add_duplicate)
    SuggestionAddKind.EXCEEDS_MAX -> stringResource(R.string.add_too_many)
    SuggestionAddKind.REMOVE_INVALID -> stringResource(R.string.add_other)
    SuggestionAddKind.NOT_ALLOWED -> stringResource(R.string.add_not_allowed)
    SuggestionAddKind.INVALID -> stringResource(R.string.add_invalid)
    SuggestionAddKind.RESTRICTED -> stringResource(R.string.add_restricted)
    SuggestionAddKind.OTHER -> stringResource(R.string.add_other)
}

@Composable
private fun eventLine(event: LogEvent): String {
    val label = stringResource(eventKindText(event.kind))
    val detail = event.detail.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    return "${formatWhen(event.timeMillis)} · $label$detail"
}

private fun eventKindText(kind: LogKind): Int = when (kind) {
    LogKind.FORCE_RETURN_START -> R.string.event_force_start
    LogKind.FORCE_RETURN_ABORT -> R.string.event_force_abort
    LogKind.FORCE_RETURN_TIMEOUT -> R.string.event_force_timeout
    LogKind.WATCHDOG_RESET -> R.string.event_watchdog
    LogKind.WATCHDOG_DROP_PHONE -> R.string.event_drop_phone
    LogKind.DISCONNECT_RETRY -> R.string.event_disconnect_retry
    LogKind.PHONE_AUTH_FAILURE -> R.string.event_auth_failure
    LogKind.PHONE_BACKOFF -> R.string.event_backoff
    LogKind.SUGGESTIONS_CHANGED -> R.string.event_suggestions
    LogKind.SUGGESTION_STATUS -> R.string.event_suggestion_status
    LogKind.APPROVAL -> R.string.event_approval
    LogKind.SCAN -> R.string.event_scan
    LogKind.DECISION -> R.string.event_decision
    LogKind.PASSWORD_MISSING -> R.string.event_password_missing
    LogKind.WIFI_OFF -> R.string.event_wifi_off
    LogKind.WIFI_ON -> R.string.event_wifi_on
    LogKind.TARGET_PHONE -> R.string.event_target_phone
    LogKind.TARGET_TRUCK -> R.string.event_target_truck
    LogKind.TARGET_NONE -> R.string.event_target_none
    LogKind.CYCLE_ERROR -> R.string.event_cycle_error
}

private fun noteText(note: TruckNote): Int = when (note) {
    TruckNote.NONE -> R.string.note_phone_not_visible
    TruckNote.PHONE_NOT_VISIBLE -> R.string.note_phone_not_visible
    TruckNote.SWITCHING_BACK -> R.string.note_switching_back
    TruckNote.ON_TRUCK_AFTER_MISS -> R.string.note_on_truck
}

private fun presenceText(presence: CardPresence): Int = when (presence) {
    CardPresence.CONNECTED -> R.string.presence_connected
    CardPresence.VISIBLE -> R.string.presence_visible
    CardPresence.NOT_VISIBLE -> R.string.presence_not_visible
    CardPresence.NOT_CONFIGURED -> R.string.not_configured
}

private fun bannerText(banner: BannerId): Int = when (banner) {
    BannerId.WIFI_OFF -> R.string.banner_wifi_off
    BannerId.FINE_LOCATION -> R.string.banner_fine_location
    BannerId.NEARBY_WIFI -> R.string.banner_nearby
    BannerId.NOTIFICATIONS -> R.string.banner_notifications
    BannerId.LOCATION_OFF -> R.string.banner_location_off
    BannerId.SUGGESTIONS -> R.string.banner_suggestions
    BannerId.SUGGESTIONS_MISSING -> R.string.banner_suggestions_missing
    BannerId.PASSWORD_MISSING -> R.string.banner_password_missing
    BannerId.BATTERY -> R.string.banner_battery
    BannerId.SERVICE_DISABLED -> R.string.banner_service_disabled
    BannerId.AUTO_REVOKE -> R.string.banner_auto_revoke
    BannerId.SAVED_NETWORK_BLOCKS -> R.string.banner_saved_network
    BannerId.CANNOT_CONNECT_PHONE -> R.string.banner_cannot_connect_phone
    BannerId.WRONG_PASSWORD -> R.string.banner_wrong_password
    BannerId.SAMSUNG_NEVER_SLEEPING -> R.string.banner_samsung
    BannerId.PHONE_PASSPHRASE -> R.string.warn_phone_passphrase
    BannerId.TRUCK_PASSPHRASE -> R.string.warn_truck_passphrase
}

private fun bannerAction(banner: BannerId): Int = when (banner) {
    BannerId.WIFI_OFF, BannerId.SAVED_NETWORK_BLOCKS -> R.string.action_open_wifi
    BannerId.FINE_LOCATION, BannerId.NEARBY_WIFI, BannerId.NOTIFICATIONS -> R.string.action_grant
    BannerId.LOCATION_OFF -> R.string.action_open_location
    BannerId.SUGGESTIONS, BannerId.SUGGESTIONS_MISSING -> R.string.action_reregister
    BannerId.PASSWORD_MISSING -> R.string.action_open_settings
    BannerId.BATTERY -> R.string.action_battery
    BannerId.SERVICE_DISABLED, BannerId.CANNOT_CONNECT_PHONE, BannerId.WRONG_PASSWORD,
    BannerId.PHONE_PASSPHRASE, BannerId.TRUCK_PASSPHRASE,
    -> R.string.action_open_settings
    BannerId.AUTO_REVOKE, BannerId.SAMSUNG_NEVER_SLEEPING -> R.string.action_app_settings
}

internal fun formatWhen(millis: Long?): String {
    if (millis == null) return "—"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
}
