package com.nilaass.assistant.storage

import android.content.Context
import android.net.Uri
import com.nilaass.assistant.core.ChatLine
import com.nilaass.assistant.security.SecureVault
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/** Passphrase-encrypted backup. The passphrase is never saved by this class. */
object EncryptedBackup {
    private val keys = listOf("chat_history_json_encrypted", "learned_preferences", "learned_skills", "api_key", "base_url", "model", "image_model", "replicate_token", "replicate_version", "adult_mode", "vault_entries")

    fun restore(context: Context, fileUri: Uri, vault: SecureVault, passphrase: CharArray): List<ChatLine> {
        require(passphrase.size >= 10) { "Enter the backup passphrase used during export." }
        val wrapperText = context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() }
            ?: error("Could not read selected backup file")
        val wrapper = JSONObject(wrapperText)
        require(wrapper.optString("format") == "nilaass-encrypted-backup-v1") { "This is not a supported NilaasS encrypted backup." }
        val salt = Base64.decode(wrapper.getString("salt"), Base64.DEFAULT)
        val iv = Base64.decode(wrapper.getString("iv"), Base64.DEFAULT)
        val ciphertext = Base64.decode(wrapper.getString("ciphertext"), Base64.DEFAULT)
        val iterations = wrapper.optInt("iterations", 180000).coerceIn(100000, 500000)
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        val raw = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(raw, "AES"), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } finally { raw.fill(0) }
        val payload = JSONObject(String(plaintext, Charsets.UTF_8))
        require(payload.optString("format") == "nilaass-full-backup-v1") { "Backup contents are invalid." }
        vault.restoreSnapshot(payload.optJSONObject("secureValues") ?: JSONObject())
        val array = payload.optJSONArray("messages") ?: JSONArray()
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            ChatLine(item.optString("speaker"), item.optString("text"))
        }.takeLast(300)
    }

    fun write(context: Context, tree: Uri, lines: List<ChatLine>, vault: SecureVault, passphrase: CharArray): String {
        require(passphrase.size >= 10) { "Use a backup passphrase of at least 10 characters." }
        val messages = JSONArray(); lines.takeLast(300).forEach { messages.put(JSONObject().put("speaker", it.speaker).put("text", it.text)) }
        val payload = JSONObject().put("format", "nilaass-full-backup-v1")
            .put("messages", messages).put("secureValues", vault.snapshot(keys))
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val spec = PBEKeySpec(passphrase, salt, 180_000, 256)
        val raw = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(raw, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
        raw.fill(0)
        val fileJson = JSONObject().put("format", "nilaass-encrypted-backup-v1")
            .put("kdf", "PBKDF2WithHmacSHA256").put("iterations", 180000)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP)).toString(2)
        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree) ?: error("Selected backup folder is unavailable")
        val file = root.createFile("application/octet-stream", "nilaass-backup-${System.currentTimeMillis()}.nbackup") ?: error("Could not create backup file")
        context.contentResolver.openOutputStream(file.uri)?.bufferedWriter()?.use { it.write(fileJson) } ?: error("Could not write backup file")
        return file.uri.toString()
    }
}
