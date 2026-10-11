package com.nilaass.assistant.learning

import android.content.Context
import com.nilaass.assistant.security.SecureVault
import org.json.JSONArray
import org.json.JSONObject

/** Explicit user-approved preferences and simple text skills, encrypted at rest in the Keystore vault. */
class LearningStore(context: Context) {
    private val vault = SecureVault(context)

    fun preferences(): List<String> = readStrings("learned_preferences")
    fun addPreference(value: String) {
        val clean = value.trim().take(240)
        if (clean.isBlank()) return
        val all = (preferences().filterNot { it.equals(clean, true) } + clean).takeLast(100)
        writeStrings("learned_preferences", all)
    }
    fun clearPreferences() { vault.remove("learned_preferences") }

    fun skills(): List<Pair<String, String>> = runCatching {
        val arr = JSONArray(vault.get("learned_skills") ?: "[]")
        (0 until arr.length()).map { i -> arr.getJSONObject(i).optString("trigger") to arr.getJSONObject(i).optString("reply") }
    }.getOrDefault(emptyList())

    fun addSkill(trigger: String, reply: String) {
        val t = trigger.trim().take(80); val r = reply.trim().take(1000)
        if (t.isBlank() || r.isBlank()) return
        val all = skills().filterNot { it.first.equals(t, true) } + (t to r)
        val arr = JSONArray(); all.takeLast(50).forEach { (a,b) -> arr.put(JSONObject().put("trigger",a).put("reply",b)) }
        vault.put("learned_skills", arr.toString())
    }
    fun matchSkill(message: String): String? = skills().firstOrNull { message.trim().equals(it.first, true) }?.second
    fun removeSkill(trigger: String) {
        val arr = JSONArray(); skills().filterNot { it.first == trigger }.forEach { (a,b) -> arr.put(JSONObject().put("trigger",a).put("reply",b)) }
        vault.put("learned_skills", arr.toString())
    }

    private fun readStrings(key: String): List<String> = runCatching {
        val a = JSONArray(vault.get(key) ?: "[]"); (0 until a.length()).map { a.optString(it) }
    }.getOrDefault(emptyList())
    private fun writeStrings(key: String, values: List<String>) { val a=JSONArray(); values.forEach(a::put); vault.put(key, a.toString()) }
}
