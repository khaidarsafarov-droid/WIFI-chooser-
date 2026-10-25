package com.wifipriority.service

import com.wifipriority.wifi.shouldStartOnBoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceLaunchTest {
    @Test
    fun bootAndPackageReplaced_startImmediatelyUnlessKnownDisabled() {
        assertTrue(shouldStartOnBoot("android.intent.action.BOOT_COMPLETED", savedEnabled = true))
        assertTrue(shouldStartOnBoot("android.intent.action.BOOT_COMPLETED", savedEnabled = null))
        assertFalse(shouldStartOnBoot("android.intent.action.BOOT_COMPLETED", savedEnabled = false))
        assertTrue(shouldStartOnBoot("android.intent.action.MY_PACKAGE_REPLACED", savedEnabled = true))
        assertFalse(shouldStartOnBoot("android.intent.action.MY_PACKAGE_REPLACED", savedEnabled = false))
        assertFalse(shouldStartOnBoot("android.intent.action.AIRPLANE_MODE", savedEnabled = true))
        assertFalse(shouldStartOnBoot(null, savedEnabled = true))
    }

    @Test
    fun startDuringDisable_doesNotLetTheOldDisableStopTheService() {
        val gate = ServiceRunGate()
        val started = gate.onStart()
        assertTrue(gate.shouldEnsureLoop(loopActive = false))
        val disabling = gate.onDisable()
        assertTrue(gate.shouldStop(disabling))
        assertFalse(gate.isCurrent(started))
        val restarted = gate.onStart()
        assertFalse(gate.shouldStop(disabling))
        assertTrue(gate.isCurrent(restarted))
        assertEquals(restarted, gate.current())
        assertFalse(gate.shouldEnsureLoop(loopActive = true))
        assertTrue(gate.shouldEnsureLoop(loopActive = false))
    }

    @Test
    fun foregroundStart_treatsIllegalStateAsACleanStop() {
        assertTrue(isForegroundStartBlocked(IllegalStateException("ForegroundServiceStartNotAllowedException")))
        assertTrue(isForegroundStartBlocked(SecurityException("missing connectedDevice permission")))
        assertFalse(isForegroundStartBlocked(IllegalArgumentException("bad notification")))
    }

    @Test
    fun completedLoop_canBeLaunchedAgain() {
        val gate = ServiceRunGate()
        gate.onStart()
        assertFalse(gate.shouldEnsureLoop(loopActive = true))
        assertTrue(gate.shouldEnsureLoop(loopActive = false))
    }
}
