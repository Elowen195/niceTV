package com.elowen.niceTV.utils

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurePrefs {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "nicetv_prefs_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val VERSION_PREFIX = "v1:"
    private const val IV_SEPARATOR = ':'

    fun contains(prefs: SharedPreferences, key: String): Boolean = prefs.contains(key)

    fun getEncryptedString(prefs: SharedPreferences, key: String, default: String? = null): String? {
        val stored = prefs.getString(key, null) ?: return default
        return runCatching { decryptToString(stored) }.getOrNull()
            ?: if (stored.startsWith(VERSION_PREFIX)) default else stored
    }

    fun putEncryptedString(prefs: SharedPreferences, key: String, value: String) {
        val stored = runCatching { encryptFromString(value) }.getOrElse { value }
        prefs.edit { putString(key, stored) }
    }

    fun getEncryptedInt(prefs: SharedPreferences, key: String, default: Int): Int {
        val stored = prefs.getString(key, null) ?: return default
        val plain = runCatching { decryptToString(stored) }.getOrNull()
            ?: if (stored.startsWith(VERSION_PREFIX)) return default else stored
        return plain.toIntOrNull() ?: default
    }

    fun putEncryptedInt(prefs: SharedPreferences, key: String, value: Int) {
        val stored = runCatching { encryptFromString(value.toString()) }.getOrElse { value.toString() }
        prefs.edit { putString(key, stored) }
    }

    fun remove(prefs: SharedPreferences, key: String) {
        prefs.edit { remove(key) }
    }

    private fun encryptFromString(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val ivEncoded = Base64.encodeToString(iv, Base64.NO_WRAP)
        val encryptedEncoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return buildString {
            append(VERSION_PREFIX)
            append(ivEncoded)
            append(IV_SEPARATOR)
            append(encryptedEncoded)
        }
    }

    private fun decryptToString(stored: String): String? {
        if (!stored.startsWith(VERSION_PREFIX)) return null
        val payload = stored.removePrefix(VERSION_PREFIX)
        val separatorIndex = payload.indexOf(IV_SEPARATOR)
        if (separatorIndex <= 0) return null
        val ivEncoded = payload.substring(0, separatorIndex)
        val encryptedEncoded = payload.substring(separatorIndex + 1)
        val iv = runCatching { Base64.decode(ivEncoded, Base64.NO_WRAP) }.getOrNull() ?: return null
        val encrypted = runCatching { Base64.decode(encryptedEncoded, Base64.NO_WRAP) }.getOrNull() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return runCatching { cipher.doFinal(encrypted) }.getOrNull()?.toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec =
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
