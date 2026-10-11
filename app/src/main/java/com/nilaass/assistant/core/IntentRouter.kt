package com.nilaass.assistant.core

sealed interface LocalIntent {
    data object Help : LocalIntent
    data object Time : LocalIntent
    data object Offline : LocalIntent
    data class Unknown(val original: String) : LocalIntent
}

class IntentRouter {
    fun route(message: String): LocalIntent {
        return when (message.trim().lowercase()) {
            "help", "உதவி", "what can you do" -> LocalIntent.Help
            "time", "what time is it", "நேரம்" -> LocalIntent.Time
            "offline", "offline status", "இணையம் இல்லாமல்" -> LocalIntent.Offline
            else -> LocalIntent.Unknown(message)
        }
    }
}
