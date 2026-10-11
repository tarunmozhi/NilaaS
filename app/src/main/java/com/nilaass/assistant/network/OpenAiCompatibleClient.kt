package com.nilaass.assistant.network

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** OpenAI-compatible Chat Completions client. Runs only after the user enables online AI. */
class OpenAiCompatibleClient {
    fun chat(baseUrl: String, model: String, apiKey: String, message: String, systemPrompt: String): String {
        require(baseUrl.startsWith("https://")) { "Use an HTTPS provider URL." }
        require(model.isNotBlank() && apiKey.isNotBlank()) { "Set a model and API key in Settings." }
        val base = baseUrl.trim().trimEnd('/')
        val endpoint = if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 90000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            val body = JSONObject().put("model", model).put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", message)))
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException("Provider returned HTTP $status${detail?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}")
            }
            val choices = JSONObject(response).optJSONArray("choices") ?: error("Provider response had no choices.")
            val content = choices.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            return content?.takeIf { it.isNotBlank() } ?: "The provider returned an empty response."
        } finally { connection.disconnect() }
    }
}
