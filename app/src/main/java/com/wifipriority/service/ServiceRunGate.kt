package com.wifipriority.service

/**
 * Decides whether a start command should launch the scan loop and whether a
 * disable that is already running is still allowed to stop the service.
 * A start that arrives while disable is in flight invalidates that disable.
 */
internal class ServiceRunGate {
    private var generation = 0
    private var disableGeneration: Int? = null

    fun onStart(): Int {
        generation += 1
        disableGeneration = null
        return generation
    }

    fun onDisable(): Int {
        generation += 1
        disableGeneration = generation
        return generation
    }

    fun current(): Int = generation

    fun shouldStop(token: Int): Boolean = disableGeneration == token

    /** True when this loop is still the latest start and disable is not in flight. */
    fun isCurrent(token: Int): Boolean = token == generation && disableGeneration == null

    fun shouldEnsureLoop(loopActive: Boolean): Boolean = disableGeneration == null && !loopActive
}

/**
 * `ForegroundServiceStartNotAllowedException` extends [IllegalStateException].
 * A missing permission is a [SecurityException]. Both must stop the service
 * cleanly; anything else is a real crash.
 */
internal fun isForegroundStartBlocked(error: Throwable): Boolean =
    error is SecurityException || error is IllegalStateException
