package com.nilaass.assistant.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Small offline fallback. This is intentionally transparent: it is not a general-purpose
 * language model. General knowledge requires a configured online provider or a local LLM.
 */
class AssistantEngine(private val router: IntentRouter = IntentRouter()) {
    fun respond(message: String): String {
        val normalized = message.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?。！？]+$"), "")
            .trim()

        return when {
            normalized.isBlank() -> "சொல்லுங்கள், என்ன உதவி வேண்டும்? / Tell me what you need help with."
            isGreeting(normalized) ->
                "வணக்கம்! நான் நிலா ❤️ எப்படி உதவலாம்? / Hi! I'm Nila. How can I help?"
            normalized in setOf("who are you", "உன் பெயர் என்ன", "நீ யார்", "what is your name") ->
                "நான் நிலா — உங்களுடைய NilaasS assistant. / I'm Nila, your NilaasS assistant."
            normalized in setOf("thanks", "thank you", "நன்றி", "ரொம்ப நன்றி") ->
                "மகிழ்ச்சி! வேறு ஏதாவது உதவி வேண்டுமா? ❤️ / You're welcome! Need anything else?"
            normalized in setOf("date", "today's date", "what is the date", "இன்று என்ன தேதி", "தேதி") ->
                "Today's date is ${LocalDate.now()}."
            router.route(message) == LocalIntent.Help ->
                "நான் இப்போது நேரம், தேதி, வணக்கம் போன்ற அடிப்படை விஷயங்களுக்கு offline-ஆக பதில் தர முடியும். பொதுவான கேள்விகளுக்கு AI Setup-ல் online AI-ஐ இயக்கி provider URL, model, API key அமைக்கவும். / Offline mode is limited; configure a provider in AI Setup for general questions."
            router.route(message) == LocalIntent.Time ->
                "இப்போது நேரம் ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}. / The device's local time is ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}."
            router.route(message) == LocalIntent.Offline ->
                "Offline mode is active. Basic local replies work; general knowledge needs a configured online provider or a local language model."
            else ->
                "இந்தக் கேள்விக்கு offline mode-ல் போதுமான அறிவு இல்லை. பொதுவான கேள்விகளுக்கு AI Setup-ல் online AI-ஐ அமைக்கவும். உங்கள் செய்தி எங்கும் அனுப்பப்படவில்லை. / I can't answer that from the current offline rules. Configure an AI provider in AI Setup for general questions. Your message was not sent anywhere."
        }
    }

    private fun isGreeting(message: String): Boolean {
        return message in setOf(
            "hi", "hello", "hey", "hi nila", "hello nila", "hey nila",
            "வணக்கம்", "ஹாய்", "ஹலோ", "நிலா", "ஹாய் நிலா", "ஹலோ நிலா"
        )
    }
}
