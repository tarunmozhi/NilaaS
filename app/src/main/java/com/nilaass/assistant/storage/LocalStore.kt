package com.nilaass.assistant.storage

import android.content.Context
import android.net.Uri
import com.nilaass.assistant.core.ChatLine
import com.nilaass.assistant.security.SecureVault
import org.json.JSONArray
import org.json.JSONObject

/** Keeps chat history encrypted at rest and stores SAF folder selections in app-private preferences. */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("nilaass_local", Context.MODE_PRIVATE)
    private val vault = SecureVault(context)

    fun loadChat(): List<ChatLine> = runCatching {
        val array = JSONArray(vault.get(KEY_CHAT) ?: "[]")
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            ChatLine(item.optString("speaker"), item.optString("text"))
        }
    }.getOrDefault(emptyList())

    fun saveChat(lines: List<ChatLine>) {
        val array = JSONArray()
        lines.takeLast(300).forEach { line ->
            array.put(JSONObject().put("speaker", line.speaker).put("text", line.text))
        }
        vault.put(KEY_CHAT, array.toString())
    }

    fun folder(category: String): Uri? = prefs.getString("folder_$category", null)?.let(Uri::parse)
    fun saveFolder(category: String, uri: Uri) { prefs.edit().putString("folder_$category", uri.toString()).apply() }
    fun forgetChat() { vault.remove(KEY_CHAT) }

    companion object { private const val KEY_CHAT = "chat_history_json_encrypted" }
}
