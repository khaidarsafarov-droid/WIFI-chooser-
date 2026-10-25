package com.wifipriority.wifi

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
