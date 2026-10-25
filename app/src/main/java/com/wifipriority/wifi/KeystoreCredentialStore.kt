package com.wifipriority.wifi

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.credentialDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wifi_priority_credentials",
)

/**
 * Phone and truck credentials encrypted with an AES-GCM key that lives only in
 * the Android Keystore. Preferences DataStore holds the ciphertext. Passwords
 * are not hardcoded and EncryptedSharedPreferences is not used.
 */
class KeystoreCredentialStore(
    private val blobs: CiphertextStore,
    private val cipher: AesGcmCipher,
) : CredentialStore {
    override val config: Flow<PriorityConfig> = blobs.updates()
        .map(::decode)
        .distinctUntilChanged()

    override suspend fun load(): PriorityConfig = decode(blobs.read())

    override suspend fun save(config: PriorityConfig) {
        val encoded = PriorityConfigCodec.encode(config)
        val payload = cipher.encrypt(encoded)
        blobs.write(Base64.getEncoder().encodeToString(payload))
    }

    private fun decode(stored: String?): PriorityConfig {
        if (stored == null) return PriorityConfig()
        return try {
            val payload = Base64.getDecoder().decode(stored)
            PriorityConfigCodec.decode(cipher.decrypt(payload))
        } catch (error: Exception) {
            // Corrupt or foreign ciphertext is treated as an empty config.
            PriorityConfig()
        }
    }

    companion object {
        private const val KEY_ALIAS = "wifi_priority_credentials"

        fun create(context: Context): KeystoreCredentialStore {
            val appContext = context.applicationContext
            return KeystoreCredentialStore(
                blobs = DataStoreCiphertextStore(appContext.credentialDataStore),
                cipher = AesGcmCipher(AndroidKeyStoreKeys.getOrCreate(KEY_ALIAS)),
            )
        }
    }
}

internal class DataStoreCiphertextStore(
    private val dataStore: DataStore<Preferences>,
) : CiphertextStore {
    override fun updates(): Flow<String?> = dataStore.data.map { prefs -> prefs[BLOB] }

    override suspend fun read(): String? = dataStore.data.first()[BLOB]

    override suspend fun write(value: String) {
        dataStore.edit { prefs -> prefs[BLOB] = value }
    }

    private companion object {
        val BLOB = stringPreferencesKey("credentials_blob")
    }
}

internal object AndroidKeyStoreKeys {
    fun getOrCreate(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
}
