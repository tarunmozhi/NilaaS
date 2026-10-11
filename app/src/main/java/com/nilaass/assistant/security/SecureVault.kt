package com.nilaass.assistant.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM encrypted values protected by an Android Keystore key. Never log secrets. */
class SecureVault(context: Context) {
    private val prefs = context.getSharedPreferences("nilaass_secure_vault", Context.MODE_PRIVATE)
    private val alias = "nilaass.aes.gcm.v1"

    @Synchronized fun put(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).toString()
        prefs.edit().putString(name, packed).apply()
    }

    @Synchronized fun get(name: String): String? = runCatching {
        val packed = prefs.getString(name, null) ?: return null
        val obj = JSONObject(packed)
        val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(obj.getString("data"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    fun remove(name: String) { prefs.edit().remove(name).apply() }

    fun snapshot(names: List<String>): JSONObject {
        val result = JSONObject()
        names.forEach { name -> get(name)?.let { result.put(name, it) } }
        return result
    }

    fun restoreSnapshot(values: JSONObject) {
        val allowed = setOf("chat_history_json_encrypted", "learned_preferences", "learned_skills", "api_key", "base_url", "model", "image_model", "replicate_token", "replicate_version", "adult_mode", "online_enabled", "vault_entries")
        val iterator = values.keys()
        while (iterator.hasNext()) {
            val name = iterator.next()
            if (name in allowed && values.opt(name) is String) put(name, values.getString(name))
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}
