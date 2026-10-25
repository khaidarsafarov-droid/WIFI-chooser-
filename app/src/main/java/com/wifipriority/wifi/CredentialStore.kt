package com.wifipriority.wifi

import kotlinx.coroutines.flow.Flow

/**
 * Saves and loads the phone and truck SSID/password pair.
 *
 * Stage 2 persists this with an AES key in the Android Keystore and Jetpack
 * DataStore. EncryptedSharedPreferences is deprecated and is not used.
 * Implementations must not log passwords.
 */
interface CredentialStore {
    val config: Flow<PriorityConfig>

    suspend fun load(): PriorityConfig

    suspend fun save(config: PriorityConfig)
}
