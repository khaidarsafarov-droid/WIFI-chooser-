package com.wifipriority.service

import com.wifipriority.wifi.FailureReason
import com.wifipriority.wifi.LogEvent
import com.wifipriority.wifi.LogKind
import com.wifipriority.wifi.PolicyState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface EventLog {
    val recent: StateFlow<List<LogEvent>>
    suspend fun append(events: List<LogEvent>)
}

interface PolicyStateStore {
    suspend fun load(): PolicyState
    suspend fun save(state: PolicyState)
}

class MemoryEventLog : EventLog {
    private val state = MutableStateFlow<List<LogEvent>>(emptyList())
    override val recent: StateFlow<List<LogEvent>> = state.asStateFlow()

    override suspend fun append(events: List<LogEvent>) {
        if (events.isEmpty()) return
        state.value = (state.value + events).takeLast(EVENT_LIMIT)
    }
}

class MemoryPolicyStateStore : PolicyStateStore {
    private var state = PolicyState()
    override suspend fun load(): PolicyState = state
    override suspend fun save(state: PolicyState) {
        this.state = state
    }
}

/**
 * Rotating text log, last [EVENT_LIMIT] lines. No Room database: the tablet stays
 * offline and the log is only read on the diagnostics screen.
 */
class FileEventLog(private val file: File) : EventLog {
    private val state = MutableStateFlow(readFile())
    override val recent: StateFlow<List<LogEvent>> = state.asStateFlow()

    override suspend fun append(events: List<LogEvent>) {
        if (events.isEmpty()) return
        synchronized(file) {
            val combined = (state.value + events).takeLast(EVENT_LIMIT)
            file.parentFile?.mkdirs()
            file.writeText(combined.joinToString("\n") { encodeEvent(it) } + "\n")
            state.value = combined
        }
    }

    private fun readFile(): List<LogEvent> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull(::decodeEvent).takeLast(EVENT_LIMIT)
    }
}

class FilePolicyStateStore(private val file: File) : PolicyStateStore {
    override suspend fun load(): PolicyState {
        if (!file.exists()) return PolicyState()
        return decodePolicy(file.readText())
    }

    override suspend fun save(state: PolicyState) {
        synchronized(file) {
            file.parentFile?.mkdirs()
            file.writeText(encodePolicy(state))
        }
    }
}

internal fun encodeEvent(event: LogEvent): String {
    val detail = event.detail.replace('\n', ' ').replace('\t', ' ')
    return "${event.timeMillis}\t${event.kind.name}\t$detail"
}

internal fun decodeEvent(line: String): LogEvent? {
    val parts = line.split('\t', limit = 3)
    if (parts.size < 2) return null
    val time = parts[0].toLongOrNull() ?: return null
    val kind = runCatching { LogKind.valueOf(parts[1]) }.getOrNull() ?: return null
    return LogEvent(time, kind, parts.getOrElse(2) { "" })
}

internal fun encodePolicy(state: PolicyState): String = buildString {
    appendLine("forceReturnSince=${state.forceReturnSince ?: ""}")
    appendLine("phoneFailures=${state.phoneFailures}")
    appendLine("failureBackoffUntil=${state.failureBackoffUntil ?: ""}")
    appendLine("suppressPhoneUntil=${state.suppressPhoneUntil ?: ""}")
    appendLine("unvalidatedSince=${state.unvalidatedSince ?: ""}")
    appendLine("phoneUnvalidatedSince=${state.phoneUnvalidatedSince ?: ""}")
    appendLine("lastFailureSsid=${state.lastFailureSsid.orEmpty()}")
    appendLine("lastFailureReason=${state.lastFailureReason?.name.orEmpty()}")
    appendLine("suggestionSignature=${state.suggestionSignature}")
}

internal fun decodePolicy(text: String): PolicyState {
    val map = text.lineSequence().mapNotNull { line ->
        val index = line.indexOf('=')
        if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
    }.toMap()
    return PolicyState(
        forceReturnSince = map["forceReturnSince"]?.toLongOrNull(),
        phoneFailures = map["phoneFailures"]?.toIntOrNull() ?: 0,
        failureBackoffUntil = map["failureBackoffUntil"]?.toLongOrNull(),
        suppressPhoneUntil = map["suppressPhoneUntil"]?.toLongOrNull(),
        unvalidatedSince = map["unvalidatedSince"]?.toLongOrNull(),
        phoneUnvalidatedSince = map["phoneUnvalidatedSince"]?.toLongOrNull(),
        lastFailureSsid = map["lastFailureSsid"]?.takeIf { it.isNotEmpty() },
        lastFailureReason = map["lastFailureReason"]?.takeIf { it.isNotEmpty() }?.let {
            runCatching { FailureReason.valueOf(it) }.getOrNull()
        },
        suggestionSignature = map["suggestionSignature"].orEmpty(),
    )
}

private const val EVENT_LIMIT = 500

object AppStores {
    private val lock = Any()
    private var events: FileEventLog? = null
    private var policy: FilePolicyStateStore? = null

    fun events(context: android.content.Context): FileEventLog = synchronized(lock) {
        events ?: FileEventLog(File(context.applicationContext.filesDir, "wifi_priority_events.log")).also {
            events = it
        }
    }

    fun policy(context: android.content.Context): FilePolicyStateStore = synchronized(lock) {
        policy ?: FilePolicyStateStore(File(context.applicationContext.filesDir, "wifi_priority_policy.txt")).also {
            policy = it
        }
    }
}
