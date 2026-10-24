
package com.nilaass.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

private val NilaNavy = Color(0xFF071426)
private val NilaPanel = Color(0xFF10243B)
private val NilaCyan = Color(0xFF65DFFF)

@Composable
fun NilaAvatarPage(
    onSendMessage: (String, (String) -> Unit) -> Unit = { _, done ->
        done("Avatar chat has not been connected to the AI yet.")
    }
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf("") }
    var reply by remember {
        mutableStateOf("வணக்கம்! நான் நிலா. என்னிடம் பேசுங்கள் ❤️")
    }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var voiceEnabled by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Ready") }
    var ttsReady by remember { mutableStateOf(false) }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var disposed = false

        val engine = TextToSpeech(context) { result ->
            mainHandler.post {
                if (disposed) return@post

                val currentEngine = tts.value
                if (result == TextToSpeech.SUCCESS && currentEngine != null) {
                    ttsReady = true
                    status = "Voice ready"

                    currentEngine.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                mainHandler.post {
                                    if (!disposed) speaking = true
                                }
                            }

                            override fun onDone(utteranceId: String?) {
                                mainHandler.post {
                                    if (!disposed) {
                                        speaking = false
                                        status = "Ready"
                                    }
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                mainHandler.post {
                                    if (!disposed) {
                                        speaking = false
                                        status = "Voice playback failed"
                                    }
                                }
                            }

                            override fun onError(
                                utteranceId: String?,
                                errorCode: Int
                            ) {
                                mainHandler.post {
                                    if (!disposed) {
                                        speaking = false
                                        status = "Voice playback failed ($errorCode)"
                                    }
                                }
                            }
                        }
                    )
                } else {
                    ttsReady = false
                    speaking = false
                    status = "Text-to-speech initialization failed"
                }
            }
        }

        tts.value = engine

        onDispose {
            disposed = true
            engine.stop()
            engine.shutdown()
            tts.value = null
            ttsReady = false
            speaking = false
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) avatarUri = uri
    }

    val animation = rememberInfiniteTransition(label = "nila-avatar")

    val breathing by animation.animateFloat(
        initialValue = 1f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    val talkingMotion by animation.animateFloat(
        initialValue = 0f,
        targetValue = if (speaking) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(240),
            repeatMode = RepeatMode.Reverse
        ),
        label = "talking"
    )

    fun speak(text: String) {
        if (!voiceEnabled || text.isBlank()) return

        val engine = tts.value
        if (engine == null || !ttsReady) {
            status = "Voice engine is still starting. Try again shortly."
            return
        }

        val isTamil = text.any { it in '\u0B80'..'\u0BFF' }
        val language = if (isTamil) {
            Locale.forLanguageTag("ta-IN")
        } else {
            Locale.forLanguageTag("en-IN")
        }

        val availability = engine.isLanguageAvailable(language)

        if (availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            speaking = false
            status = if (isTamil) {
                "Tamil voice data is unavailable. Install Tamil voice data in your TTS settings."
            } else {
                "English voice data is unavailable. Check TTS settings."
            }
            return
        }

        val languageResult = engine.setLanguage(language)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            speaking = false
            status = if (isTamil) {
                "Tamil voice is not supported by the installed TTS engine."
            } else {
                "English voice is not supported by the installed TTS engine."
            }
            return
        }

        engine.setSpeechRate(0.92f)
        engine.setPitch(1.0f)

        speaking = true
        status = if (isTamil) "Speaking Tamil…" else "Speaking English…"

        val result = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "nila-avatar-${System.nanoTime()}"
        )

        if (result == TextToSpeech.ERROR) {
            speaking = false
            status = "Could not start voice playback"
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        listening = false

        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
            .trim()

        if (result.resultCode == Activity.RESULT_OK && spokenText.isNotBlank()) {
            message = spokenText
            status = "Speech recognized. Press Send to ask Nila."
        } else {
            status = "No speech recognized. Check microphone permission and try again."
        }
    }

    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "Speech recognition is unavailable. Check your phone's speech service."
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ta-IN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "நிலாவிடம் தமிழில் பேசுங்கள்")
        }

        listening = true
        status = "Listening for Tamil speech…"

        runCatching {
            speechLauncher.launch(intent)
        }.onFailure {
            listening = false
            status = "Could not start speech recognition: ${
                it.message ?: "unknown error"
            }"
        }
    }

    fun send() {
        val request = message.trim()
        if (request.isBlank() || busy) return

        message = ""
        busy = true
        status = "Nila is thinking…"

        try {
            onSendMessage(request) { answer ->
                mainHandler.post {
                    reply = answer
                    busy = false
                    status = "Ready"

                    if (voiceEnabled) {
                        speak(answer)
                    }
                }
            }
        } catch (error: Exception) {
            busy = false
            status = "Could not get a reply: ${
                error.message ?: "unknown error"
            }"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NilaNavy)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Nila • Avatar",
            style = MaterialTheme.typography.headlineMedium,
            color = NilaCyan
        )

        Text(
            "வணக்கம்! நான் நிலா ❤️",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(NilaPanel),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "நிலாவின் படத்தைத் தேர்ந்தெடுக்கவும்",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(onClick = { picker.launch("image/*") }) {
                        Text("Choose Nila image")
                    }
                }
            } else {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = breathing
                            scaleY = breathing
                            translationY = if (speaking) {
                                -3f * talkingMotion
                            } else {
                                0f
                            }
                        },
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            adjustViewBounds = true
                        }
                    },
                    update = { view ->
                        if (view.tag != avatarUri.toString()) {
                            view.setImageURI(avatarUri)
                            view.tag = avatarUri.toString()
                        }
                    }
                )
            }

            if (speaking) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    shape = CircleShape,
                    color = NilaCyan
                ) {
                    Text(
                        " ♪ Nila is speaking ",
                        modifier = Modifier.padding(8.dp),
                        color = NilaNavy
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NilaPanel,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                reply,
                modifier = Modifier.padding(12.dp),
                color = Color.White
            )
        }

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("நிலாவிடம் பேசுங்கள் / Message Nila") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            enabled = !busy,
            maxLines = 3
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = !listening && !busy,
                onClick = { listen() }
            ) {
                Text(if (listening) "Listening…" else "🎙 Speak")
            }

            Button(
                modifier = Modifier.weight(1f),
                enabled = message.isNotBlank() && !busy,
                onClick = { send() }
            ) {
                Text(if (busy) "Thinking…" else "Send")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { picker.launch("image/*") }) {
                Text("Change image")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice", color = Color.White)

                Switch(
                    checked = voiceEnabled,
                    onCheckedChange = { enabled ->
                        voiceEnabled = enabled
                        if (!enabled) {
                            tts.value?.stop()
                            speaking = false
                            status = "Voice disabled"
                        } else {
                            status = if (ttsReady) "Voice enabled" else "Voice is starting…"
                        }
                    }
                )
            }

            TextButton(
                onClick = {
                    tts.value?.stop()
                    speaking = false
                    status = "Voice stopped"
                }
            ) {
                Text("Stop")
            }
        }

        Text(
            status,
            color = NilaCyan,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
