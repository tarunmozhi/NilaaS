package com.nilaass.assistant.voice

data class VoiceCapabilities(
    val speechRecognitionAvailable: Boolean,
    val tamilRecognitionAvailable: Boolean,
    val tamilTextToSpeechAvailable: Boolean,
    val offlineRecognitionAvailable: Boolean
)

/** Voice API integration will be added in a later milestone; this placeholder records no audio. */
