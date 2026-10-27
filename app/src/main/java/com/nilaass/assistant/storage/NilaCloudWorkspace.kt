package com.nilaass.assistant.storage

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.nilaass.assistant.core.ChatLine
import com.nilaass.assistant.security.SecureVault
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.security.KeyStore

/**
 * App-private temporary workspace and conservative offline-first cloud snapshot sync.
 * The only files this manager may clean are files it created inside its own temp directory.
 */
class NilaCloudWorkspace(private val context: Context) {
    companion object {
        const val MAX_TEMP_BYTES = 2L * 1024L * 1024L * 1024L
        private const val PREFS = "nila_cloud_workspace"
        private const val KEY_QUEUE = "pending_snapshots"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "nila_cloud").apply { mkdirs() }
    private val temp = File(root, "temporary").apply { mkdirs() }
    private val queue = File(root, "queue").apply { mkdirs() }

    data class Candidate(val id: String, val label: String, val supportsFiles: Boolean, val supportsApi: Boolean, val requiresAccount: Boolean, val configured: Boolean, val note: String)

    fun tempBytes(): Long = temp.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun availableTempBytes(): Long = (MAX_TEMP_BYTES - tempBytes()).coerceAtLeast(0L)

    fun createTemp(name: String, bytes: ByteArray): File {
        require(name.matches(Regex("[A-Za-z0-9._-]{1,100}"))) { "Use a simple file name." }
        require(bytes.size.toLong() <= availableTempBytes()) { "Nila's 2 GB temporary storage limit would be exceeded." }
        val file = File(temp, "${UUID.randomUUID()}-$name")
        file.outputStream().use { it.write(bytes) }
        return file
    }

    fun enqueueSnapshot(snapshot: String): File {
        val file = File(queue, "snapshot-${System.currentTimeMillis()}.bin")
        file.writeBytes(encryptSnapshot(snapshot.toByteArray(Charsets.UTF_8)))
        return file
    }

    fun pendingCount(): Int = queue.listFiles()?.count { it.isFile && it.extension == "bin" } ?: 0

    fun pendingSnapshots(): List<File> = queue.listFiles()
        ?.filter { it.isFile && it.extension == "bin" }
        ?.sortedBy { it.lastModified() }
        .orEmpty()

    fun removeVerifiedQueueFile(file: File) {
        val canonicalRoot = queue.canonicalFile
        val canonicalFile = file.canonicalFile
        require(canonicalFile.parentFile == canonicalRoot) { "Refusing to remove a file outside Nila's queue." }
        require(canonicalFile.name.startsWith("snapshot-") && canonicalFile.extension == "bin") {
            "Refusing to remove an unrecognized queue file."
        }
        check(canonicalFile.delete()) { "Could not remove the verified queue file." }
    }

    fun cleanupTemporaryFiles(): Int {
        // Safe cleanup only: remove files within this app's dedicated temp directory.
        // Do not touch pending queue snapshots, media, user folders, or important memory.
        var removed = 0
        temp.listFiles()?.forEach { file ->
            if (file.isFile && file.name.matches(Regex("[0-9a-fA-F-]{36}-.+"))) {
                if (file.delete()) removed++
            }
        }
        return removed
    }

    fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun candidates(vault: SecureVault): List<Candidate> {
        val supabaseConfigured = !vault.get("cloud_supabase_url").isNullOrBlank() &&
            !vault.get("cloud_supabase_anon_key").isNullOrBlank() &&
            !vault.get("cloud_supabase_access_token").isNullOrBlank() &&
            !vault.get("cloud_supabase_user_id").isNullOrBlank()
        return listOf(
            Candidate("supabase", "Supabase Storage", true, true, true, supabaseConfigured,
                if (supabaseConfigured) "Configured with a user token; private per-user path is used." else "Free-tier candidate; requires project URL, anon key, signed-in user access token, user UUID, private bucket and restrictive policies."),
            Candidate("firebase", "Firebase", true, true, true, false,
                "Candidate only; Firebase adapter is not implemented in this version."),
            Candidate("webdav", "WebDAV-compatible storage", true, true, true, false,
                "Candidate only; no endpoint configured and adapter is not implemented.")
        )
    }

    fun chooseCandidate(vault: SecureVault): Candidate? = candidates(vault)
        .filter { it.configured && it.supportsApi && it.supportsFiles }
        .maxByOrNull { candidate ->
            (if (candidate.configured) 100 else 0) +
                (if (candidate.supportsApi) 20 else 0) +
                (if (candidate.supportsFiles) 20 else 0) -
                (if (candidate.requiresAccount) 5 else 0)
        }

    /**
     * Uploads queued snapshots to a private Supabase Storage bucket. The anon key is not an
     * admin key; the bucket must have restrictive policies and the key is only used over HTTPS.
     * Upload is verified by reading the object back and comparing its SHA-256 digest.
     */
    fun syncQueuedSnapshots(vault: SecureVault): String {
        if (!isOnline()) return "Offline: snapshots remain safely queued on this device."
        val candidate = chooseCandidate(vault) ?: return "No supported cloud provider is configured yet."
        if (candidate.id != "supabase") return "The selected provider adapter is not available."

        val base = vault.get("cloud_supabase_url").orEmpty().trimEnd('/')
        val key = vault.get("cloud_supabase_anon_key").orEmpty()
        val accessToken = vault.get("cloud_supabase_access_token").orEmpty()
        val userId = vault.get("cloud_supabase_user_id").orEmpty()
        require(accessToken.isNotBlank() && userId.matches(Regex("[0-9a-fA-F-]{36}"))) {
            "Supabase sign-in access token and user UUID are required for private per-user storage."
        }
        require(base.startsWith("https://")) { "Supabase URL must use HTTPS." }
        require(key.isNotBlank()) { "Supabase anon key is missing." }
        val bucket = (vault.get("cloud_supabase_bucket") ?: "nila-private").trim()
        require(bucket.matches(Regex("[A-Za-z0-9._-]{1,100}"))) { "Invalid bucket name." }

        var uploaded = 0
        pendingSnapshots().forEach { file ->
            val objectName = "$userId/snapshots/${file.name}"
            val endpoint = "$base/storage/v1/object/$bucket/$objectName"
            val bytes = file.readBytes()
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("apikey", key)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("x-upsert", "false")
            }
            try {
                connection.outputStream.use { it.write(bytes) }
                val code = connection.responseCode
                val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                require(code in 200..299 || code == 409) {
                    "Cloud upload failed (HTTP $code): ${response.take(180)}"
                }
            } finally {
                connection.disconnect()
            }

            // Verify the uploaded object can be retrieved and matches the queued snapshot.
            val verify = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("apikey", key)
                setRequestProperty("Authorization", "Bearer $key")
            }
            try {
                val code = verify.responseCode
                require(code in 200..299) { "Upload verification failed (HTTP $code); local copy retained." }
                val downloaded = verify.inputStream.use { it.readBytes() }
                val expected = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                val actual = java.security.MessageDigest.getInstance("SHA-256").digest(downloaded)
                require(expected.contentEquals(actual)) { "Cloud verification mismatch; local copy retained." }
            } finally {
                verify.disconnect()
            }

            removeVerifiedQueueFile(file)
            uploaded++
        }
        return if (uploaded == 0) "Cloud sync checked: no pending snapshots." else
            "Verified $uploaded snapshot(s) in cloud storage. Local queue copies were removed only after verification."
    }

    private fun encryptSnapshot(plain: ByteArray): ByteArray {
        val alias = "nilaass_cloud_snapshot_v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key: SecretKey = if (store.containsAlias(alias)) {
            store.getKey(alias, null) as SecretKey
        } else {
            val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
            generator.init(spec)
            generator.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plain)
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    fun snapshotJson(lines: List<ChatLine>): String {
        val messages = JSONArray()
        lines.takeLast(300).forEach { line ->
            messages.put(JSONObject().put("speaker", line.speaker).put("text", line.text))
        }
        return JSONObject()
            .put("format", "nilaass-memory-snapshot-v1")
            .put("created_at", System.currentTimeMillis())
            .put("messages", messages)
            .toString()
    }
}
