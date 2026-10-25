package com.wifipriority.wifi

/**
 * One-shot registration flag. [close] clears it so a later [tryOpen] registers again.
 * Leaving the flag set after [close] drops every callback until process death.
 */
class ListenerGate {
    private var open = false

    fun tryOpen(): Boolean {
        if (open) return false
        open = true
        return true
    }

    /** @return true when this call transitioned from open to closed. */
    fun close(): Boolean {
        if (!open) return false
        open = false
        return true
    }

    fun isOpen(): Boolean = open
}
