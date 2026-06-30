package com.projectnuke.fusion.ai.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidKeystoreSecretStore(
    context: Context
) : SecretStore {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override suspend fun putSecret(id: String, value: String) {
        withContext(Dispatchers.IO) {
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val payload = cipher.iv + ciphertext
            prefs.edit()
                .putString(id, Base64.encodeToString(payload, Base64.NO_WRAP))
                .apply()
        }
    }

    override suspend fun getSecret(id: String): String? {
        return withContext(Dispatchers.IO) {
            val encoded = prefs.getString(id, null) ?: return@withContext null
            runCatching {
                val payload = Base64.decode(encoded, Base64.NO_WRAP)
                if (payload.size <= IvSizeBytes) return@runCatching null
                val iv = payload.copyOfRange(0, IvSizeBytes)
                val ciphertext = payload.copyOfRange(IvSizeBytes, payload.size)
                val cipher = Cipher.getInstance(Transformation)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GcmTagBits, iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            }.getOrNull()
        }
    }

    override suspend fun deleteSecret(id: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().remove(id).apply()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        val spec = KeyGenParameterSpec.Builder(
            KeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val PrefsName = "fusion_ai_provider_secrets"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "fusion_ai_provider_api_key"
        const val Transformation = "AES/GCM/NoPadding"
        const val IvSizeBytes = 12
        const val GcmTagBits = 128
    }
}
