package com.wifipriority.wifi

/**
 * Security advertised by one scan result, parsed from [android.net.wifi.ScanResult.capabilities].
 * The phone hotspot in the field is WPA2-Personal; WPA3 is still recognized so a future
 * hotspot is not suggested with the wrong passphrase type.
 */
enum class WifiSecurity {
    OPEN,
    WPA2,
    WPA3,
    /** WPA2/WPA3 transition (PSK and SAE together). Registered as two suggestions. */
    TRANSITION,
    UNKNOWN,
}

/**
 * Reads the capabilities string Android puts on a scan result.
 * SAE means WPA3-Personal. PSK means WPA2-Personal. Both means transition mode.
 */
fun securityFromCapabilities(capabilities: String?): WifiSecurity {
    if (capabilities.isNullOrBlank()) return WifiSecurity.UNKNOWN
    val sae = capabilities.contains("SAE", ignoreCase = true)
    val psk = capabilities.contains("PSK", ignoreCase = true)
    return when {
        sae && psk -> WifiSecurity.TRANSITION
        sae -> WifiSecurity.WPA3
        psk -> WifiSecurity.WPA2
        capabilities.contains("WEP", ignoreCase = true) ||
            capabilities.contains("EAP", ignoreCase = true) -> WifiSecurity.UNKNOWN
        capabilities.contains("ESS", ignoreCase = true) -> WifiSecurity.OPEN
        else -> WifiSecurity.UNKNOWN
    }
}

/** Two results for one SSID: mixed PSK and SAE becomes [WifiSecurity.TRANSITION]. */
fun mergeSecurity(current: WifiSecurity?, next: WifiSecurity): WifiSecurity {
    if (current == null || current == next) return next
    if (current == WifiSecurity.UNKNOWN) return next
    if (next == WifiSecurity.UNKNOWN) return current
    if (current == WifiSecurity.OPEN) return next
    if (next == WifiSecurity.OPEN) return current
    val eitherTransition = current == WifiSecurity.TRANSITION || next == WifiSecurity.TRANSITION
    val mixedPskSae = (current == WifiSecurity.WPA2 && next == WifiSecurity.WPA3) ||
        (current == WifiSecurity.WPA3 && next == WifiSecurity.WPA2)
    return if (eitherTransition || mixedPskSae) WifiSecurity.TRANSITION else next
}

/** Suggestion credentials to register for one observed security. Unknown falls back to WPA2. */
fun securityVariants(security: WifiSecurity): List<WifiSecurity> = when (security) {
    WifiSecurity.TRANSITION -> listOf(WifiSecurity.WPA2, WifiSecurity.WPA3)
    WifiSecurity.WPA3 -> listOf(WifiSecurity.WPA3)
    WifiSecurity.OPEN -> listOf(WifiSecurity.OPEN)
    WifiSecurity.WPA2, WifiSecurity.UNKNOWN -> listOf(WifiSecurity.WPA2)
}
