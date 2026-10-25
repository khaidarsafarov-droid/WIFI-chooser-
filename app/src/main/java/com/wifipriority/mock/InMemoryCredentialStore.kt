package com.wifipriority.mock

import com.wifipriority.wifi.CredentialStore
import com.wifipriority.wifi.PriorityConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local [CredentialStore]. Lost when the process dies. */
class InMemoryCredentialStore(
    initial: PriorityConfig = PriorityConfig(),
) : CredentialStore {
    private val state = MutableStateFlow(initial)

    override val config: Flow<PriorityConfig> = state.asStateFlow()

    override suspend fun load(): PriorityConfig = state.value

    override suspend fun save(config: PriorityConfig) {
        state.value = config
    }
}
