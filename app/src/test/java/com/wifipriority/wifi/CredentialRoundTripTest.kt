package com.wifipriority.wifi

import com.wifipriority.PHONE_PASSWORD
import com.wifipriority.PHONE_SSID
import com.wifipriority.TRUCK_PASSWORD
import com.wifipriority.TRUCK_SSID
import com.wifipriority.enabledConfig
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialRoundTripTest {
    @Test
    fun aesGcm_roundTripsBytes() {
        val cipher = AesGcmCipher(aesKey())
        val plain = "phone-secret-π".toByteArray()
        val payload = cipher.encrypt(plain)
        assertFalse(String(payload, Charsets.ISO_8859_1).contains("phone-secret"))
        assertEquals("phone-secret-π", cipher.decrypt(payload).toString(Charsets.UTF_8))
    }

    @Test
    fun aesGcm_rejectsTamperedCiphertext() {
        val cipher = AesGcmCipher(aesKey())
        val payload = cipher.encrypt("abcdef".toByteArray()).also { it[it.lastIndex] = it[it.lastIndex].inc() }
        assertThrows(GeneralSecurityException::class.java) {
            cipher.decrypt(payload)
        }
    }

    @Test
    fun codec_roundTripsConfig() {
        val config = enabledConfig()
        val decoded = PriorityConfigCodec.decode(PriorityConfigCodec.encode(config))
        assertEquals(config, decoded)
    }

    @Test
    fun keystoreCredentialStore_roundTripsWithoutPlaintext() = runTest {
        val blobs = MemoryCiphertextStore()
        val store = KeystoreCredentialStore(blobs, AesGcmCipher(aesKey()))
        val saved = enabledConfig()
        store.save(saved)
        val raw = blobs.read()
        assertTrue(raw != null && raw.isNotEmpty())
        assertFalse(raw!!.contains(PHONE_PASSWORD))
        assertFalse(raw.contains(TRUCK_PASSWORD))
        assertFalse(raw.contains(PHONE_SSID))
        assertEquals(saved, store.load())
        assertEquals(saved, store.config.first())
    }

    @Test
    fun corruptBlob_loadsEmptyConfig() = runTest {
        val blobs = MemoryCiphertextStore()
        blobs.write("not-valid-ciphertext")
        val store = KeystoreCredentialStore(blobs, AesGcmCipher(aesKey()))
        val loaded = store.load()
        assertFalse(loaded.enabled)
        assertEquals(null, loaded.phone)
        assertEquals(null, loaded.truck)
    }

    private fun aesKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}

private class MemoryCiphertextStore : CiphertextStore {
    private val state = MutableStateFlow<String?>(null)

    override fun updates(): Flow<String?> = state

    override suspend fun read(): String? = state.value

    override suspend fun write(value: String) {
        state.value = value
    }
}
