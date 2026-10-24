
package com.nilaass.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import android.widget.ImageView
import androidx.compose.ui.layout.ContentScale
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

    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("வணக்கம்! நான் நிலா. என்னிடம் பேசுங்கள் ❤️") }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var voiceEnabled by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Ready") }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { result ->
            if (result == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale("ta", "IN")
            }
        }
        tts.value = engine

        onDispose {
            engine.stop()
            engine.shutdown()
            tts.value = null
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
        val engine = tts.value
        if (!voiceEnabled || engine == null) return

        val tamil = text.any { it in '\u0B80'..'\u0BFF' }
        val language = if (tamil) Locale("ta", "IN") else Locale("en", "IN")
        val availability = engine.isLanguageAvailable(language)

        if (availability < TextToSpeech.LANG_AVAILABLE) {
            status = if (tamil) {
                "Tamil voice is not installed on this device."
            } else {
                "English voice is unavailable."
            }
            speaking = false
            return
        }

        engine.language = language
        speaking = true
        val result = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "nila-avatar-${System.currentTimeMillis()}"
        )

        if (result == TextToSpeech.ERROR) {
            speaking = false
            status = "Could not start voice playback."
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

        if (result.resultCode == android.app.Activity.RESULT_OK &&
            spokenText.isNotBlank()
        ) {
            message = spokenText
            status = "Recognized speech"
        } else {
            status = "No speech recognized. Try again."
        }
    }

    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "Speech recognition is unavailable. Check your device's speech service."
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ta-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "நிலாவிடம் பேசுங்கள்")
        }

        listening = true
        runCatching {
            speechLauncher.launch(intent)
        }.onFailure {
            listening = false
            status = "Could not start the microphone: ${it.message ?: "unknown error"}"
        }
    }

    fun send() {
        val request = message.trim()
        if (request.isBlank() || busy) return

        message = ""
        busy = true
        status = "Nila is thinking…"

        onSendMessage(request) { answer ->
            reply = answer
            busy = false
            status = "Ready"
            speak(answer)
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
                        "  ♪ Nila is speaking  ",
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
                    onCheckedChange = { voiceEnabled = it }
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
