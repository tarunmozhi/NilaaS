package com.nilaass.assistant.research

/** Future boundary for a real search provider. Do not embed provider secrets in the APK. */
interface SearchProvider {
    suspend fun search(query: String): List<ResearchResult>
}

data class ResearchResult(val title: String, val url: String, val snippet: String)
