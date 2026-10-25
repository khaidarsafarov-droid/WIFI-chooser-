package com.wifipriority

import com.wifipriority.engine.EngineState
import com.wifipriority.engine.EngineStep
import com.wifipriority.engine.PriorityEngine
import com.wifipriority.mock.FakeClock
import com.wifipriority.wifi.NetworkCredential
import com.wifipriority.wifi.PriorityConfig
import com.wifipriority.wifi.ScanSnapshot

internal const val PHONE_SSID = "PhoneHotspot"
internal const val TRUCK_SSID = "TruckHotspot"
internal const val PHONE_PASSWORD = "phone-test-password"
internal const val TRUCK_PASSWORD = "truck-test-password"

internal fun enabledConfig(
    enabled: Boolean = true,
    phoneSsid: String? = PHONE_SSID,
    truckSsid: String? = TRUCK_SSID,
    phonePassword: String = PHONE_PASSWORD,
    truckPassword: String = TRUCK_PASSWORD,
): PriorityConfig = PriorityConfig(
    enabled = enabled,
    phone = phoneSsid?.let { NetworkCredential(it, phonePassword) },
    truck = truckSsid?.let { NetworkCredential(it, truckPassword) },
)

internal fun PriorityEngine.scan(
    state: EngineState,
    clock: FakeClock,
    visible: Set<String>,
    config: PriorityConfig,
): EngineStep = onScan(
    state = state,
    snapshot = ScanSnapshot(visibleSsids = visible, timestampMillis = clock.nowMillis()),
    config = config,
)
