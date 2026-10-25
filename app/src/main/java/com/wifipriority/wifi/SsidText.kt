package com.wifipriority.wifi

/** SSID string Android returns when location info is withheld. */
const val UNKNOWN_SSID = "<unknown ssid>"

/**
 * Turns a platform SSID (`"name"` with quotes, or [UNKNOWN_SSID]) into the
 * raw name the engine compares. Scan-result bytes are preferred over this
 * when [android.net.wifi.WifiSsid] is available.
 */
fun normalizePlatformSsid(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    if (trimmed == UNKNOWN_SSID || trimmed == "0x") return null
    val unquoted = if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
        trimmed.substring(1, trimmed.length - 1)
    } else {
        trimmed
    }
    return unquoted.trim().takeIf { it.isNotEmpty() }
}

/** UTF-8 SSID octets from [android.net.wifi.WifiSsid.getBytes], with a quoted-string fallback. */
fun ssidFromOctets(bytes: ByteArray?, fallback: String?): String? {
    if (bytes != null && bytes.isNotEmpty()) {
        val text = bytes.toString(Charsets.UTF_8).trimEnd('\u0000').trim()
        val printable = text.isNotEmpty() && text.all { char ->
            char == '\t' || char == '\n' || char >= ' '
        }
        if (printable) return text
    }
    return normalizePlatformSsid(fallback)
}
