package com.wifipriority.wifi

import kotlinx.coroutines.flow.Flow

/** Persistence for one ciphertext blob. DataStore implements this on device. */
interface CiphertextStore {
    fun updates(): Flow<String?>

    suspend fun read(): String?

    suspend fun write(value: String)
}
