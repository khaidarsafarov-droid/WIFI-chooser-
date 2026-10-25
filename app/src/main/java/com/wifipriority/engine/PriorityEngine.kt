package com.wifipriority.engine

import com.wifipriority.wifi.Clock
import com.wifipriority.wifi.PriorityConfig
import com.wifipriority.wifi.ScanPolicy
import com.wifipriority.wifi.ScanSnapshot
import com.wifipriority.wifi.WifiTarget

/**
 * Pure priority state machine. It does not scan, connect, or read storage.
 *
 * Rules, evaluated on each scan:
 * 1. [PriorityConfig.enabled] is false → no action. Misses are not counted and the
 *    target is left untouched.
 * 2. No phone SSID and no truck SSID (null or blank) → no action, same as disabled.
 *    The enabled flag is checked first.
 * 3. Phone SSID is visible → target [WifiTarget.PHONE] on this scan, miss count reset.
 * 4. Phone SSID is absent for a single scan → keep the current target.
 * 5. Phone SSID is absent for [ScanPolicy.PHONE_MISSES_BEFORE_TRUCK] consecutive scans
 *    and the truck SSID is visible → target [WifiTarget.TRUCK].
 * 6. Neither SSID is visible → keep the last target. If no target has been chosen yet,
 *    stay on [WifiTarget.NONE].
 *
 * Choice for (6): holding the last target avoids tearing down a connection because
 * one or two scans saw nothing (a brief range gap is not evidence the association
 * should be dropped). [WifiTarget.NONE] is only the result when the engine has never
 * switched. The engine does not clear a previous target back to [WifiTarget.NONE]
 * just because both hotspots disappeared.
 *
 * [EngineState.lastSwitchTimeMillis] updates only when [WifiTarget] actually changes,
 * and it reads [Clock.nowMillis] for that instant. SSIDs are compared exactly after
 * trimming the configured value. Scan SSIDs are not trimmed.
 */
class PriorityEngine(
    private val clock: Clock,
    private val missesBeforeTruck: Int = ScanPolicy.PHONE_MISSES_BEFORE_TRUCK,
) {
    init {
        require(missesBeforeTruck >= 1) { "missesBeforeTruck must be at least 1" }
    }

    fun onScan(
        state: EngineState,
        snapshot: ScanSnapshot,
        config: PriorityConfig,
    ): EngineStep {
        if (!config.enabled) {
            return hold(state, DecisionReason.DISABLED)
        }
        val phoneSsid = config.phoneSsid()
        val truckSsid = config.truckSsid()
        if (phoneSsid == null && truckSsid == null) {
            return hold(state, DecisionReason.EMPTY_CONFIG)
        }

        if (phoneSsid != null && phoneSsid in snapshot.visibleSsids) {
            return move(state, WifiTarget.PHONE, misses = 0, DecisionReason.PHONE_VISIBLE)
        }

        val misses = state.consecutivePhoneMisses + 1
        val truckVisible = truckSsid != null && truckSsid in snapshot.visibleSsids
        if (misses >= missesBeforeTruck && truckVisible) {
            return move(state, WifiTarget.TRUCK, misses, DecisionReason.PHONE_ABSENT_TRUCK_VISIBLE)
        }

        val held = state.copy(consecutivePhoneMisses = misses)
        val reason = if (misses < missesBeforeTruck) {
            DecisionReason.SINGLE_MISS_HOLD
        } else {
            DecisionReason.NEITHER_VISIBLE_HOLD
        }
        return EngineStep(state = held, decision = decision(held, shouldApply = false, reason = reason))
    }

    private fun move(
        state: EngineState,
        target: WifiTarget,
        misses: Int,
        reason: DecisionReason,
    ): EngineStep {
        val changed = state.target != target
        val switchTime = if (changed) clock.nowMillis() else state.lastSwitchTimeMillis
        val next = EngineState(
            target = target,
            consecutivePhoneMisses = misses,
            lastSwitchTimeMillis = switchTime,
        )
        return EngineStep(
            state = next,
            decision = decision(next, shouldApply = changed, reason = reason),
        )
    }

    private fun hold(state: EngineState, reason: DecisionReason): EngineStep =
        EngineStep(state = state, decision = decision(state, shouldApply = false, reason = reason))

    private fun decision(
        state: EngineState,
        shouldApply: Boolean,
        reason: DecisionReason,
    ): EngineDecision = EngineDecision(
        target = state.target,
        shouldApply = shouldApply,
        reason = reason,
        lastSwitchTimeMillis = state.lastSwitchTimeMillis,
        consecutivePhoneMisses = state.consecutivePhoneMisses,
    )
}

data class EngineState(
    val target: WifiTarget = WifiTarget.NONE,
    val consecutivePhoneMisses: Int = 0,
    val lastSwitchTimeMillis: Long? = null,
)

data class EngineStep(
    val state: EngineState,
    val decision: EngineDecision,
)

/**
 * @param shouldApply true only when [target] changed and the caller should
 * invoke [com.wifipriority.wifi.NetworkConnector.applyTarget].
 */
data class EngineDecision(
    val target: WifiTarget,
    val shouldApply: Boolean,
    val reason: DecisionReason,
    val lastSwitchTimeMillis: Long?,
    val consecutivePhoneMisses: Int,
)

enum class DecisionReason {
    DISABLED,
    EMPTY_CONFIG,
    PHONE_VISIBLE,
    SINGLE_MISS_HOLD,
    PHONE_ABSENT_TRUCK_VISIBLE,
    NEITHER_VISIBLE_HOLD,
}
