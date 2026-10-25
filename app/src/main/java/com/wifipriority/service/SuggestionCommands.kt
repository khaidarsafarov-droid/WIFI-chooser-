package com.wifipriority.service

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Asks the running coordinator to read `getNetworkSuggestions()` and re-add
 * anything missing. Called from the status screen while the activity is in
 * the foreground, so Android can show the suggestion-approval dialog.
 */
object SuggestionCommands {
    private val pulses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = pulses.asSharedFlow()

    fun reregister(context: Context) {
        pulses.tryEmit(Unit)
        PriorityService.start(context)
    }
}
