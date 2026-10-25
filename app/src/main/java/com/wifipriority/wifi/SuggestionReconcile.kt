package com.wifipriority.wifi

/**
 * What the platform currently has for this app.
 * [registeredSsids] null means the connector cannot see `WifiManager.getNetworkSuggestions()`
 * (mocks). An empty set means the platform was read and holds nothing.
 */
data class SuggestionApplyReport(
    val commit: SuggestionCommit = SuggestionCommit.APPLIED,
    val addStatus: Int? = null,
    val removeStatus: Int? = null,
    val registeredSsids: Set<String>? = null,
    /** Exception simple name when the platform call threw. No message: it can contain a passphrase. */
    val failure: String? = null,
)

/**
 * Platform list used by [registerSuggestions]. The Android connector adapts
 * `WifiManager`; tests use a fake. Add is attempted before any removal so a
 * rejected add cannot wipe suggestions that were already stored.
 */
interface SuggestionPlatform {
    fun currentSsids(): Set<String>
    fun add(suggestions: List<PlannedSuggestion>): Int
    fun remove(ssids: Set<String>, disconnect: Boolean): Int
}

fun expectedSsids(suggestions: List<PlannedSuggestion>): Set<String> =
    suggestions.map { it.ssid }.toSet()

fun missingSuggestionSsids(expected: List<PlannedSuggestion>, registeredSsids: Set<String>): Set<String> =
    expectedSsids(expected) - registeredSsids

/**
 * The persisted plan signature is not evidence the system has the networks.
 * Re-add whenever an expected SSID is absent. A null inventory (mocks) does not force.
 */
fun needsPlatformReadd(expected: List<PlannedSuggestion>, registeredSsids: Set<String>?): Boolean {
    if (registeredSsids == null || expected.isEmpty()) return false
    return missingSuggestionSsids(expected, registeredSsids).isNotEmpty()
}

/**
 * SUCCESS and DUPLICATE are not registration if the SSIDs are still absent.
 * An empty system list is retried even when the status was a user rejection,
 * so a later foreground add can raise the approval dialog again.
 */
fun commitAfterInventory(
    platform: SuggestionCommit,
    expected: List<PlannedSuggestion>,
    registeredAfter: Set<String>?,
): SuggestionCommit {
    if (registeredAfter == null || expected.isEmpty()) return platform
    return if (missingSuggestionSsids(expected, registeredAfter).isEmpty()) {
        platform
    } else {
        SuggestionCommit.FAILED
    }
}

fun withoutPriority(suggestions: List<PlannedSuggestion>): List<PlannedSuggestion> =
    suggestions.map { it.copy(priority = 0, priorityGroup = 0) }

/**
 * Add or update first, then remove only SSIDs that are no longer wanted.
 * If the privileged-looking priority is rejected, or the platform reports success
 * while `getNetworkSuggestions()` stays empty, try once more with priority 0.
 */
fun registerSuggestions(
    platform: SuggestionPlatform,
    planned: List<PlannedSuggestion>,
    disconnectSsids: Set<String>,
): SuggestionApplyReport {
    var addStatus: Int? = null
    if (planned.isNotEmpty()) {
        addStatus = platform.add(planned)
        val stillMissing = missingSuggestionSsids(planned, platform.currentSsids()).isNotEmpty()
        if (stillMissing) {
            addStatus = platform.add(withoutPriority(planned))
        }
    }
    val present = platform.currentSsids()
    val wanted = expectedSsids(planned)
    val disconnectNow = disconnectSsids.intersect(present)
    val retire = present - wanted - disconnectNow
    var removeStatus: Int? = null
    if (disconnectNow.isNotEmpty()) {
        removeStatus = platform.remove(disconnectNow, disconnect = true)
    }
    if (retire.isNotEmpty()) {
        val code = platform.remove(retire, disconnect = false)
        if (removeStatus == null) removeStatus = code
    }
    val registered = platform.currentSsids()
    val platformCommit = if (planned.isEmpty()) {
        SuggestionCommit.APPLIED
    } else {
        suggestionCommitFor(suggestionAddKind(addStatus ?: -1), removedOnly = false)
    }
    return SuggestionApplyReport(
        commit = commitAfterInventory(platformCommit, planned, registered),
        addStatus = addStatus,
        removeStatus = removeStatus,
        registeredSsids = registered,
    )
}

fun passwordMissing(ssid: String?, password: String?): Boolean =
    !ssid.isNullOrBlank() && password.isNullOrEmpty()

fun scanSummary(
    phoneSsid: String?,
    truckSsid: String?,
    visible: Set<String>,
    fresh: Boolean,
    wifiEnabled: Boolean,
): String {
    if (!wifiEnabled) return "wifi=off"
    val phone = presence(phoneSsid, visible)
    val truck = presence(truckSsid, visible)
    val head = if (fresh) "n=${visible.size}" else "refused"
    return "$head phone=$phone truck=$truck"
}

private fun presence(ssid: String?, visible: Set<String>): String = when {
    ssid.isNullOrBlank() -> "unset"
    ssid in visible -> "visible"
    else -> "absent"
}

fun suggestionStatusDetail(
    addCode: Int?,
    removeCode: Int?,
    registered: Set<String>?,
    missing: Set<String>,
): String {
    val add = addCode?.let { "$it ${suggestionAddKind(it).name}" } ?: "not-called"
    val remove = removeCode?.let { "$it ${suggestionAddKind(it).name}" } ?: "none"
    val have = when {
        registered == null -> "unknown"
        registered.isEmpty() -> "none"
        else -> registered.sorted().joinToString("|")
    }
    val miss = if (missing.isEmpty()) "none" else missing.sorted().joinToString("|")
    return "add=$add remove=$remove registered=$have missing=$miss"
}

/**
 * One event-log line for a cycle that threw. Passphrases are replaced, and the
 * message is shortened so the 500-line log stays readable.
 */
fun cycleErrorDetail(error: Throwable, secrets: Collection<String>): String {
    val frame = error.stackTrace.firstOrNull { it.className.startsWith("com.wifipriority.") }
    val where = if (frame == null) "unknown" else "${frame.fileName}:${frame.lineNumber}"
    var message = error.message.orEmpty()
    for (secret in secrets) {
        if (secret.length >= 8) message = message.replace(secret, "redacted")
    }
    message = message.replace(Regex("\\s+"), " ").take(180)
    return "${error.javaClass.simpleName} $where $message".trim()
}
