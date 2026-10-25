package com.wifipriority.wifi

import java.security.MessageDigest

/**
 * Result of `WifiManager.addNetworkSuggestions`, mapped from the API 36 status ints.
 * The connector passes the platform code through [suggestionAddKind].
 */
enum class SuggestionAddKind {
    NONE,
    SUCCESS,
    INTERNAL,
    APP_DISALLOWED,
    DUPLICATE,
    EXCEEDS_MAX,
    REMOVE_INVALID,
    NOT_ALLOWED,
    INVALID,
    RESTRICTED,
    OTHER,
}

fun suggestionAddKind(code: Int): SuggestionAddKind = when (code) {
    0 -> SuggestionAddKind.SUCCESS
    1 -> SuggestionAddKind.INTERNAL
    2 -> SuggestionAddKind.APP_DISALLOWED
    3 -> SuggestionAddKind.DUPLICATE
    4 -> SuggestionAddKind.EXCEEDS_MAX
    5 -> SuggestionAddKind.REMOVE_INVALID
    6 -> SuggestionAddKind.NOT_ALLOWED
    7 -> SuggestionAddKind.INVALID
    8 -> SuggestionAddKind.RESTRICTED
    else -> SuggestionAddKind.OTHER
}

/** What the coordinator should do after one `addNetworkSuggestions` call. */
enum class SuggestionCommit {
    /** The list is registered, or there was nothing to add. */
    APPLIED,

    /** User or app policy rejected the add. Wait for an approval change. */
    REJECTED,

    /** Transient failure. The previous list may already have been removed. */
    FAILED,
}

fun suggestionCommitFor(kind: SuggestionAddKind, removedOnly: Boolean): SuggestionCommit {
    if (removedOnly) return SuggestionCommit.APPLIED
    return when (kind) {
        SuggestionAddKind.SUCCESS,
        SuggestionAddKind.DUPLICATE,
        SuggestionAddKind.NONE,
        -> SuggestionCommit.APPLIED
        SuggestionAddKind.NOT_ALLOWED,
        SuggestionAddKind.APP_DISALLOWED,
        -> SuggestionCommit.REJECTED
        SuggestionAddKind.INTERNAL,
        SuggestionAddKind.EXCEEDS_MAX,
        SuggestionAddKind.REMOVE_INVALID,
        SuggestionAddKind.INVALID,
        SuggestionAddKind.RESTRICTED,
        SuggestionAddKind.OTHER,
        -> SuggestionCommit.FAILED
    }
}

/**
 * Identity of a suggestion list, including a passphrase digest.
 * The digest is not the password and must not be written to the event log.
 * Two plans that differ only by password still produce different keys, so the
 * connector does not keep registering the old secret.
 */
fun suggestionContentKey(suggestions: List<PlannedSuggestion>): String =
    suggestions.joinToString(",") { item ->
        "${item.role}:${item.ssid}:${item.security}:${item.priority}:${item.priorityGroup}:${passphraseTag(item.passphrase)}"
    }

fun passphraseTag(passphrase: String): String {
    if (passphrase.isEmpty()) return "-"
    val digest = MessageDigest.getInstance("SHA-256").digest(passphrase.toByteArray(Charsets.UTF_8))
    return digest.take(6).joinToString("") { byte -> "%02x".format(byte) }
}
