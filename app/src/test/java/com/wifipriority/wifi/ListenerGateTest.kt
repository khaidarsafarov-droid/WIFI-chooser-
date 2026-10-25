package com.wifipriority.wifi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerGateTest {
    @Test
    fun close_allowsStartToRegisterAgain() {
        val gate = ListenerGate()
        assertTrue(gate.tryOpen())
        assertFalse(gate.tryOpen())
        assertTrue(gate.close())
        assertFalse(gate.close())
        assertTrue(gate.tryOpen())
        assertTrue(gate.isOpen())
    }
}
