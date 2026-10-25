package com.wifipriority.wifi

/**
 * Fail-safe suggestion plan. The truck suggestion stays registered except during a
 * force-return that lasts at most [SuggestionPlanner.FORCE_RETURN_MAX_MILLIS].
 * That window starts only when the phone is visible, the tablet is on the truck,
 * and that truck link has validated internet. It ends immediately if the phone
 * disappears, the radio drops, the engine target is no longer the phone, the
 * tablet disconnects, or the clock runs out. A timeout then refuses another
 * force-return for [SuggestionPlanner.BACKOFF_MILLIS].
 *
 * If nothing has validated internet for [SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS],
 * force-return and auth backoff are cleared and both suggestions are registered again.
 * If the phone itself has no validated internet for that long, the phone suggestion
 * is removed until the backoff ends so the truck can take the link.
 */
object SuggestionPolicy {
    fun evaluate(state: PolicyState, observation: PolicyObservation, nowMillis: Long): PolicyStep {
        var next = state.expire(nowMillis)
        val events = mutableListOf<LogEvent>()
        var forceReapply = false
        var disconnect = emptySet<String>()

        observation.authFailure?.let { failure ->
            next = next.copy(
                lastFailureSsid = failure.ssid,
                lastFailureReason = failure.reason,
            )
            if (failure.ssid != null && failure.ssid == observation.phoneSsid) {
                val count = next.phoneFailures + 1
                next = if (count >= SuggestionPlanner.PHONE_FAILURES_BEFORE_BACKOFF) {
                    events += LogEvent(nowMillis, LogKind.PHONE_BACKOFF, failure.ssid)
                    next.copy(
                        phoneFailures = count,
                        failureBackoffUntil = nowMillis + SuggestionPlanner.BACKOFF_MILLIS,
                        forceReturnSince = null,
                    )
                } else {
                    events += LogEvent(nowMillis, LogKind.PHONE_AUTH_FAILURE, failure.ssid)
                    next.copy(phoneFailures = count)
                }
            } else {
                events += LogEvent(nowMillis, LogKind.PHONE_AUTH_FAILURE, failure.ssid.orEmpty())
            }
        }

        val onPhone = observation.connectedSsid != null && observation.connectedSsid == observation.phoneSsid
        val validatedLink = observation.validated
        if (onPhone && observation.fromOurSuggestion == true && observation.validated) {
            next = next.copy(
                phoneFailures = 0,
                failureBackoffUntil = null,
                lastFailureReason = null,
                lastFailureSsid = null,
            )
        }

        if (observation.linkKnown) {
            next = if (validatedLink) {
                next.copy(unvalidatedSince = null)
            } else {
                next.copy(unvalidatedSince = next.unvalidatedSince ?: nowMillis)
            }
            next = if (onPhone && !observation.validated) {
                next.copy(phoneUnvalidatedSince = next.phoneUnvalidatedSince ?: nowMillis)
            } else {
                next.copy(phoneUnvalidatedSince = null)
            }
        }

        val phoneTooLong = next.phoneUnvalidatedSince != null &&
            nowMillis - next.phoneUnvalidatedSince!! >= SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS
        val anyTooLong = next.unvalidatedSince != null &&
            nowMillis - next.unvalidatedSince!! >= SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS
        val phoneHeldDown = next.suppressPhoneUntil != null && nowMillis < next.suppressPhoneUntil!!

        if (phoneTooLong && observation.phoneSsid != null) {
            next = next.copy(
                forceReturnSince = null,
                suppressPhoneUntil = nowMillis + SuggestionPlanner.BACKOFF_MILLIS,
                phoneUnvalidatedSince = null,
                unvalidatedSince = nowMillis,
            )
            events += LogEvent(nowMillis, LogKind.WATCHDOG_DROP_PHONE, observation.phoneSsid)
            forceReapply = true
            disconnect = setOf(observation.phoneSsid)
        } else if (anyTooLong && !phoneHeldDown) {
            next = next.copy(
                forceReturnSince = null,
                suppressPhoneUntil = null,
                failureBackoffUntil = null,
                phoneFailures = 0,
                unvalidatedSince = nowMillis,
                phoneUnvalidatedSince = null,
            )
            events += LogEvent(nowMillis, LogKind.WATCHDOG_RESET, "")
            forceReapply = true
        } else if (anyTooLong) {
            next = next.copy(forceReturnSince = null, unvalidatedSince = nowMillis)
            events += LogEvent(nowMillis, LogKind.WATCHDOG_RESET, "phone-held")
        }

        val phoneVisible = observation.phoneSsid != null && observation.phoneSsid in observation.visibleSsids
        val onTruck = observation.connectedSsid != null && observation.connectedSsid == observation.truckSsid
        val backoff = next.failureBackoffUntil != null && nowMillis < next.failureBackoffUntil!!
        val wantForce = observation.enabled &&
            observation.radioOn &&
            observation.target == WifiTarget.PHONE &&
            phoneVisible &&
            onTruck &&
            !onPhone &&
            observation.validated &&
            !backoff &&
            !phoneHeldDown &&
            next.suppressPhoneUntil == null

        val phoneSuppressed = next.suppressPhoneUntil != null && nowMillis < next.suppressPhoneUntil!!
        if (phoneSuppressed && onPhone && !observation.validated) {
            val phone = observation.connectedSsid.orEmpty()
            val due = next.disconnectRetryAt
            if (phone.isNotEmpty() && (due == null || nowMillis >= due)) {
                disconnect = disconnect + phone
                next = next.copy(disconnectRetryAt = nowMillis + SuggestionPlanner.DISCONNECT_RETRY_MILLIS)
                events += LogEvent(nowMillis, LogKind.DISCONNECT_RETRY, phone)
            }
        } else if (!onPhone || observation.validated) {
            next = next.copy(disconnectRetryAt = null)
        }

        if (next.forceReturnSince != null) {
            val elapsed = nowMillis - next.forceReturnSince!!
            val expired = elapsed >= SuggestionPlanner.FORCE_RETURN_MAX_MILLIS
            val stillValid = wantForce && !expired
            if (!stillValid) {
                val kind = if (expired) LogKind.FORCE_RETURN_TIMEOUT else LogKind.FORCE_RETURN_ABORT
                events += LogEvent(nowMillis, kind, observation.connectedSsid.orEmpty())
                next = if (expired) {
                    next.copy(
                        forceReturnSince = null,
                        failureBackoffUntil = nowMillis + SuggestionPlanner.BACKOFF_MILLIS,
                    )
                } else {
                    next.copy(forceReturnSince = null)
                }
            }
        } else if (wantForce) {
            val truck = observation.truckSsid.orEmpty()
            next = next.copy(forceReturnSince = nowMillis)
            events += LogEvent(nowMillis, LogKind.FORCE_RETURN_START, truck)
            disconnect = disconnect + truck
        }

        if (!observation.enabled) {
            val hadSuggestions = next.suggestionSignature.isNotEmpty()
            next = next.copy(forceReturnSince = null, suggestionSignature = "")
            return PolicyStep(
                state = next,
                suggestions = emptyList(),
                disconnectSsids = emptySet(),
                events = events,
                forceReapply = hadSuggestions,
            )
        }

        val suppressTruck = next.forceReturnSince != null
        val suppressPhone = next.suppressPhoneUntil != null && nowMillis < next.suppressPhoneUntil!!
        val suggestions = buildList {
            if (!suppressPhone) {
                addAll(planned(observation.phoneSsid, observation.phonePassphrase, WifiTarget.PHONE, observation))
            }
            if (!suppressTruck) {
                addAll(planned(observation.truckSsid, observation.truckPassphrase, WifiTarget.TRUCK, observation))
            }
        }
        val signature = suggestions.joinToString(",") { "${it.role}:${it.ssid}:${it.security}" }
        if (signature != next.suggestionSignature) {
            events += LogEvent(nowMillis, LogKind.SUGGESTIONS_CHANGED, signature)
            next = next.copy(suggestionSignature = signature)
        }
        return PolicyStep(
            state = next,
            suggestions = suggestions,
            disconnectSsids = disconnect,
            events = events,
            forceReapply = forceReapply,
        )
    }

    private fun planned(
        ssid: String?,
        passphrase: String?,
        role: WifiTarget,
        observation: PolicyObservation,
    ): List<PlannedSuggestion> {
        val name = ssid?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val secret = passphrase.orEmpty()
        val security = observation.securityBySsid[name] ?: WifiSecurity.UNKNOWN
        val variants = securityVariants(security)
        if (variants.singleOrNull() == WifiSecurity.OPEN) {
            return listOf(suggestion(role, name, "", WifiSecurity.OPEN))
        }
        if (secret.length < SuggestionPlanner.MIN_PASSPHRASE_LENGTH) return emptyList()
        return variants.map { variant -> suggestion(role, name, secret, variant) }
    }

    private fun suggestion(
        role: WifiTarget,
        ssid: String,
        passphrase: String,
        security: WifiSecurity,
    ): PlannedSuggestion {
        val phone = role == WifiTarget.PHONE
        return PlannedSuggestion(
            role = role,
            ssid = ssid,
            passphrase = passphrase,
            priority = if (phone) SuggestionPlanner.PHONE_PRIORITY else SuggestionPlanner.TRUCK_PRIORITY,
            priorityGroup = if (phone) SuggestionPlanner.PHONE_GROUP else SuggestionPlanner.TRUCK_GROUP,
            security = security,
        )
    }
}

data class PolicyObservation(
    val enabled: Boolean,
    val target: WifiTarget,
    val phoneSsid: String?,
    val truckSsid: String?,
    val phonePassphrase: String?,
    val truckPassphrase: String?,
    val connectedSsid: String?,
    val visibleSsids: Set<String>,
    val securityBySsid: Map<String, WifiSecurity> = emptyMap(),
    val validated: Boolean,
    val radioOn: Boolean,
    val fromOurSuggestion: Boolean?,
    /** One failure since the previous evaluation. Null on ordinary scans. */
    val authFailure: ConnectionFailure? = null,
    /** False before the first link callback. Does not start the unvalidated clock. */
    val linkKnown: Boolean = true,
)

data class ConnectionFailure(
    val ssid: String?,
    val reason: FailureReason,
)

enum class FailureReason {
    AUTHENTICATION,
    ASSOCIATION,
    IP_PROVISIONING,
    UNKNOWN,
}

data class PolicyState(
    val forceReturnSince: Long? = null,
    val phoneFailures: Int = 0,
    val failureBackoffUntil: Long? = null,
    val suppressPhoneUntil: Long? = null,
    val unvalidatedSince: Long? = null,
    val phoneUnvalidatedSince: Long? = null,
    /** Next time to disconnect an unvalidated phone that is still associated. */
    val disconnectRetryAt: Long? = null,
    val lastFailureSsid: String? = null,
    val lastFailureReason: FailureReason? = null,
    val suggestionSignature: String = "",
    /**
     * True only after `WifiManager.getNetworkSuggestions()` contained the planned SSIDs.
     * [suggestionSignature] is the plan we want, not proof the system stored it.
     */
    val suggestionsInSystem: Boolean = false,
) {
    fun expire(nowMillis: Long): PolicyState = copy(
        failureBackoffUntil = failureBackoffUntil?.takeIf { nowMillis < it },
        suppressPhoneUntil = suppressPhoneUntil?.takeIf { nowMillis < it },
    )
}

/** Earliest moment the coordinator must evaluate again even if nothing else changes. */
fun PolicyState.recoveryDeadlineMillis(): Long? {
    val times = ArrayList<Long>(4)
    forceReturnSince?.let { times += it + SuggestionPlanner.FORCE_RETURN_MAX_MILLIS }
    unvalidatedSince?.let { times += it + SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS }
    phoneUnvalidatedSince?.let { times += it + SuggestionPlanner.UNVALIDATED_LIMIT_MILLIS }
    disconnectRetryAt?.let { times += it }
    return times.minOrNull()
}

data class PolicyStep(
    val state: PolicyState,
    val suggestions: List<PlannedSuggestion>,
    val disconnectSsids: Set<String>,
    val events: List<LogEvent>,
    val forceReapply: Boolean,
)

enum class LogKind {
    FORCE_RETURN_START,
    FORCE_RETURN_ABORT,
    FORCE_RETURN_TIMEOUT,
    WATCHDOG_RESET,
    WATCHDOG_DROP_PHONE,
    PHONE_AUTH_FAILURE,
    PHONE_BACKOFF,
    DISCONNECT_RETRY,
    SUGGESTIONS_CHANGED,
    SUGGESTION_STATUS,
    APPROVAL,
    SCAN,
    DECISION,
    PASSWORD_MISSING,
    WIFI_OFF,
    WIFI_ON,
    TARGET_PHONE,
    TARGET_TRUCK,
    TARGET_NONE,
    CYCLE_ERROR,
    SCAN_STALE,
}

data class LogEvent(
    val timeMillis: Long,
    val kind: LogKind,
    val detail: String,
)
