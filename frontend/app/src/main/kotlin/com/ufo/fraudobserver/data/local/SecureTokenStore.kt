package com.ufo.fraudobserver.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.tokenDataStore by preferencesDataStore(name = "ufo_secure_tokens")

class SecureTokenStore(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ufo_aead_token_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12

        private val KEY_ACCESS_TOKEN = stringPreferencesKey("enc_access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("enc_refresh_token")

        @Volatile
        private var INSTANCE: SecureTokenStore? = null

        fun getInstance(context: Context): SecureTokenStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureTokenStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // Combine IV + ciphertext
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String?): String? {
        if (encryptedBase64.isNullOrBlank()) return null
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH) return null
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val cipherBytes = combined.copyOfRange(IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        val encAccess = encrypt(accessToken)
        val encRefresh = encrypt(refreshToken)
        context.tokenDataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = encAccess
            prefs[KEY_REFRESH_TOKEN] = encRefresh
        }
    }

    suspend fun getAccessToken(): String? {
        val enc = context.tokenDataStore.data.map { it[KEY_ACCESS_TOKEN] }.firstOrNull()
        return decrypt(enc)
    }

    suspend fun getRefreshToken(): String? {
        val enc = context.tokenDataStore.data.map { it[KEY_REFRESH_TOKEN] }.firstOrNull()
        return decrypt(enc)
    }

    fun getAccessTokenSync(): String? {
        return runBlocking { getAccessToken() }
    }

    fun getRefreshTokenSync(): String? {
        return runBlocking { getRefreshToken() }
    }

    suspend fun clearTokens() {
        context.tokenDataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
        }
    }
}

