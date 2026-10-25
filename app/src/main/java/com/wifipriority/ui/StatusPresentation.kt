package com.wifipriority.ui

import com.wifipriority.service.PriorityStatus
import com.wifipriority.wifi.FailureReason
import com.wifipriority.wifi.LogEvent
import com.wifipriority.wifi.PriorityConfig
import com.wifipriority.wifi.SuggestionAddKind
import com.wifipriority.wifi.SuggestionApproval
import com.wifipriority.wifi.WifiSecurity
import com.wifipriority.wifi.WifiTarget
import com.wifipriority.wifi.suggestionAddKind

enum class StartDestination { HOME, SETTINGS }

enum class CardPresence { CONNECTED, VISIBLE, NOT_VISIBLE, NOT_CONFIGURED }

enum class TruckNote { NONE, PHONE_NOT_VISIBLE, SWITCHING_BACK, ON_TRUCK_AFTER_MISS }

enum class BannerId {
    WIFI_OFF,
    FINE_LOCATION,
    NEARBY_WIFI,
    NOTIFICATIONS,
    LOCATION_OFF,
    SUGGESTIONS,
    BATTERY,
    SERVICE_DISABLED,
    AUTO_REVOKE,
    SAVED_NETWORK_BLOCKS,
    CANNOT_CONNECT_PHONE,
    WRONG_PASSWORD,
    SAMSUNG_NEVER_SLEEPING,
    PHONE_PASSPHRASE,
    TRUCK_PASSPHRASE,
}

data class DeviceSignals(
    val fineLocation: Boolean = true,
    val nearbyWifi: Boolean = true,
    val notifications: Boolean = true,
    val locationEnabled: Boolean = true,
    val wifiEnabled: Boolean = true,
    val batteryExempt: Boolean = true,
    val autoRevokeWhitelisted: Boolean = true,
    val manufacturer: String = "",
    val samsungHintDismissed: Boolean = false,
)

data class NetworkCard(
    val role: WifiTarget,
    val ssid: String?,
    val presence: CardPresence,
    val highlighted: Boolean,
)

data class HomeUi(
    val connectedSsid: String?,
    val lastSwitchTimeMillis: Long?,
    val lastScanTimeMillis: Long?,
    val running: Boolean,
    val phone: NetworkCard,
    val truck: NetworkCard,
    val truckNote: TruckNote,
)

data class DiagnosticsUi(
    val lastScanTimeMillis: Long?,
    val phoneSsid: String?,
    val phoneSecurity: WifiSecurity?,
    val phoneVisible: Boolean,
    val truckSsid: String?,
    val truckSecurity: WifiSecurity?,
    val truckVisible: Boolean,
    val connectedSsid: String?,
    val fromOurSuggestion: Boolean?,
    val validated: Boolean,
    val suggestionAddStatus: Int?,
    val suggestionAddKind: SuggestionAddKind,
    val lastFailureReason: FailureReason?,
    val lastFailureSsid: String?,
    val phoneFailures: Int,
    val failureBackoffUntil: Long?,
    val forceReturnActive: Boolean,
    val events: List<LogEvent>,
)

fun startDestination(config: PriorityConfig): StartDestination {
    return if (config.hasNoNetworks()) StartDestination.SETTINGS else StartDestination.HOME
}

fun presence(ssid: String?, connectedSsid: String?, visible: Set<String>): CardPresence {
    if (ssid.isNullOrBlank()) return CardPresence.NOT_CONFIGURED
    if (connectedSsid == ssid) return CardPresence.CONNECTED
    if (ssid in visible) return CardPresence.VISIBLE
    return CardPresence.NOT_VISIBLE
}

fun homeUi(config: PriorityConfig, status: PriorityStatus): HomeUi {
    val phoneSsid = config.phoneSsid()
    val truckSsid = config.truckSsid()
    val phonePresence = presence(phoneSsid, status.connectedSsid, status.visibleSsids)
    val truckPresence = presence(truckSsid, status.connectedSsid, status.visibleSsids)
    val onTruck = truckSsid != null && status.connectedSsid == truckSsid
    val phoneVisible = phoneSsid != null && phoneSsid in status.visibleSsids
    val note = when {
        !onTruck || phoneSsid == null -> TruckNote.NONE
        status.target == WifiTarget.PHONE && !phoneVisible -> TruckNote.PHONE_NOT_VISIBLE
        status.target == WifiTarget.PHONE && phoneVisible -> TruckNote.SWITCHING_BACK
        status.target == WifiTarget.TRUCK -> TruckNote.ON_TRUCK_AFTER_MISS
        else -> TruckNote.NONE
    }
    return HomeUi(
        connectedSsid = status.connectedSsid,
        lastSwitchTimeMillis = status.lastSwitchTimeMillis,
        lastScanTimeMillis = status.lastScanTimeMillis,
        running = config.enabled,
        phone = NetworkCard(WifiTarget.PHONE, phoneSsid, phonePresence, phonePresence == CardPresence.CONNECTED),
        truck = NetworkCard(WifiTarget.TRUCK, truckSsid, truckPresence, truckPresence == CardPresence.CONNECTED),
        truckNote = note,
    )
}

fun savedNetworkBlocks(config: PriorityConfig, status: PriorityStatus): Boolean {
    val truck = config.truckSsid() ?: return false
    if (config.phoneSsid() == null) return false
    if (status.target != WifiTarget.PHONE) return false
    if (status.connectedSsid != truck) return false
    return status.connectionFromSuggestion == false
}

fun deviceBanners(signals: DeviceSignals, status: PriorityStatus, config: PriorityConfig): List<BannerId> {
    val banners = mutableListOf<BannerId>()
    if (!signals.wifiEnabled || !status.wifiEnabled) banners += BannerId.WIFI_OFF
    if (savedNetworkBlocks(config, status)) banners += BannerId.SAVED_NETWORK_BLOCKS
    if (status.phoneFailures >= 3) banners += BannerId.CANNOT_CONNECT_PHONE
    if (status.lastFailureReason == FailureReason.AUTHENTICATION &&
        status.lastFailureSsid != null &&
        status.lastFailureSsid == config.phoneSsid()
    ) {
        banners += BannerId.WRONG_PASSWORD
    }
    if (!signals.fineLocation) banners += BannerId.FINE_LOCATION
    if (!signals.nearbyWifi) banners += BannerId.NEARBY_WIFI
    if (!signals.notifications) banners += BannerId.NOTIFICATIONS
    if (!signals.locationEnabled) banners += BannerId.LOCATION_OFF
    if (status.suggestionApproval == SuggestionApproval.PENDING ||
        status.suggestionApproval == SuggestionApproval.REJECTED
    ) {
        banners += BannerId.SUGGESTIONS
    }
    if (!signals.batteryExempt) banners += BannerId.BATTERY
    if (!config.enabled) banners += BannerId.SERVICE_DISABLED
    if (!signals.autoRevokeWhitelisted) banners += BannerId.AUTO_REVOKE
    if (signals.manufacturer.equals("samsung", ignoreCase = true) && !signals.samsungHintDismissed) {
        banners += BannerId.SAMSUNG_NEVER_SLEEPING
    }
    passphraseBanners(config.phone?.password, config.truck?.password).forEach { banners += it }
    return banners
}

fun passphraseBanners(phonePassword: String?, truckPassword: String?): List<BannerId> {
    val banners = mutableListOf<BannerId>()
    if (!phonePassword.isNullOrEmpty() && phonePassword.length < 8) banners += BannerId.PHONE_PASSPHRASE
    if (!truckPassword.isNullOrEmpty() && truckPassword.length < 8) banners += BannerId.TRUCK_PASSPHRASE
    return banners
}

fun diagnostics(config: PriorityConfig, status: PriorityStatus, events: List<LogEvent>): DiagnosticsUi {
    val phone = config.phoneSsid()
    val truck = config.truckSsid()
    return DiagnosticsUi(
        lastScanTimeMillis = status.lastScanTimeMillis,
        phoneSsid = phone,
        phoneSecurity = phone?.let { status.securityBySsid[it] },
        phoneVisible = phone != null && phone in status.visibleSsids,
        truckSsid = truck,
        truckSecurity = truck?.let { status.securityBySsid[it] },
        truckVisible = truck != null && truck in status.visibleSsids,
        connectedSsid = status.connectedSsid,
        fromOurSuggestion = status.connectionFromSuggestion,
        validated = status.validated,
        suggestionAddStatus = status.suggestionAddStatus,
        suggestionAddKind = status.suggestionAddStatus?.let(::suggestionAddKind) ?: SuggestionAddKind.NONE,
        lastFailureReason = status.lastFailureReason,
        lastFailureSsid = status.lastFailureSsid,
        phoneFailures = status.phoneFailures,
        failureBackoffUntil = status.failureBackoffUntil,
        forceReturnActive = status.forceReturnActive,
        events = events.takeLast(40),
    )
}
