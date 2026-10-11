package com.nilaass.assistant.core

import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AssistantEngine(private val router: IntentRouter = IntentRouter()) {
    fun respond(message: String): String = when (router.route(message)) {
        LocalIntent.Help -> "I can currently answer help, time, and offline-status commands. Research, voice, and app actions are planned next."
        LocalIntent.Time -> "The device's local time is ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}."
        LocalIntent.Offline -> "This starter runs local command handling. Online research and remote AI are not connected."
        is LocalIntent.Unknown -> "I don't know how to do that yet. Try 'help'. Your request has not been sent anywhere."
    }
}
