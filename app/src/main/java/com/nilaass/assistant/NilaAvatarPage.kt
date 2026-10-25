
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val NilaNavy = Color(0xFF071426)
private val NilaPanel = Color(0xFF10243B)
private val NilaCyan = Color(0xFF65DFFF)

@Composable
fun NilaAvatarPage(
    onSendMessage: (String, (String) -> Unit) -> Unit = { _, done ->
        done("Avatar chat is not connected to the AI.")
    }
) {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    val preferences = remember {
        context.getSharedPreferences("nila_avatar", android.content.Context.MODE_PRIVATE)
    }

    var avatarUri by remember {
        mutableStateOf<Uri?>(preferences.getString("avatar_uri", null)?.let(Uri::parse))
    }
    var message by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("வணக்கம்! நான் நிலா. என்னிடம் பேசுங்கள் ❤️") }
    var status by remember { mutableStateOf("Ready") }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var voiceEnabled by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var disposed = false
        val engine = TextToSpeech(context) { result ->
            handler.post {
                if (disposed) return@post
                if (result == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    tts.value?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                handler.post { if (!disposed) speaking = true }
                            }

                            override fun onDone(utteranceId: String?) {
                                handler.post {
                                    if (!disposed) {
                                        speaking = false
                                        status = "Ready"
                                    }
                                }
                            }

                            override fun onError(utteranceId: String?) {
                                handler.post {
                                    if (!disposed) {
                                        speaking = false
                                        status = "Voice playback failed"
                                    }
                                }
                            }
                        }
                    )
                    status = "Voice ready"
                } else {
                    status = "Text-to-speech unavailable"
                }
            }
        }
        tts.value = engine

        onDispose {
            disposed = true
            engine.stop()
            engine.shutdown()
            tts.value = null
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            avatarUri = uri
            preferences.edit().putString("avatar_uri", uri.toString()).apply()
            zoom = 1f
            panX = 0f
            panY = 0f
            status = "Nila image loaded"
        }
    }


    val animation = rememberInfiniteTransition(label = "nila-motion")
    val breathing by animation.animateFloat(
        initialValue = 1f,
        targetValue = 1.012f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    fun speak(text: String) {
        if (!voiceEnabled || text.isBlank()) return
        val engine = tts.value
        if (engine == null || !ttsReady) {
            status = "Voice engine is starting"
            return
        }

        val tamil = text.any { it in '\u0B80'..'\u0BFF' }
        val locale = if (tamil) Locale.forLanguageTag("ta-IN")
        else Locale.forLanguageTag("en-IN")

        val languageResult = engine.setLanguage(locale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            status = if (tamil) "Install Tamil TTS voice data"
            else "English TTS voice unavailable"
            return
        }

        engine.setSpeechRate(0.92f)
        engine.setPitch(1f)
        speaking = true
        status = if (tamil) "Speaking Tamil…" else "Speaking English…"
        val result = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "nila-${System.nanoTime()}"
        )
        if (result == TextToSpeech.ERROR) {
            speaking = false
            status = "Could not start voice"
        }
    }

    fun sendMessage(text: String, onReply: (String) -> Unit = {}) {
        val request = text.trim()
        if (request.isBlank() || busy) return
        busy = true
        status = "Nila is thinking…"
        onSendMessage(request) { answer ->
            handler.post {
                reply = answer
                busy = false
                status = "Ready"
                onReply(answer)
                if (onReply === {}) Unit
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        listening = false
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
            .trim()

        if (result.resultCode == Activity.RESULT_OK && text.isNotBlank()) {
            message = text
            status = "Speech recognized"
            sendMessage(text) { answer ->
                reply = answer
                if (voiceEnabled) speak(answer)
            }
        } else {
            status = "Speech not recognized. Try again."
        }
    }

    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "Speech service unavailable"
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ta-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "நிலாவிடம் தமிழில் பேசுங்கள்")
        }
        listening = true
        status = "Listening…"
        runCatching { speechLauncher.launch(intent) }
            .onFailure {
                listening = false
                status = "Could not start speech recognition"
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NilaNavy)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Nila • Avatar", color = NilaCyan,
            style = MaterialTheme.typography.headlineMedium)
        Text("வணக்கம்! நான் நிலா ❤️", color = Color.White)

        Spacer(Modifier.height(8.dp))

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
                    Button(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                        Text("Choose Nila image")
                    }
                }
            } else {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                panX += pan.x
                                panY += pan.y
                            }
                        }
                        .graphicsLayer {
                            scaleX = zoom * breathing
                            scaleY = zoom * breathing
                            translationX = panX
                            translationY = panY
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
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    shape = CircleShape,
                    color = NilaCyan
                ) {
                    Text(" ♪ Nila is speaking ", modifier = Modifier.padding(8.dp),
                        color = NilaNavy)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = { zoom = max(1f, zoom - 0.25f) }) {
                Text("− Zoom", color = NilaCyan)
            }
            TextButton(onClick = {
                zoom = 1f
                panX = 0f
                panY = 0f
            }) {
                Text("Reset", color = NilaCyan)
            }
            TextButton(onClick = { zoom = min(4f, zoom + 0.25f) }) {
                Text("+ Zoom", color = NilaCyan)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NilaPanel,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(reply, modifier = Modifier.padding(12.dp), color = Color.White)
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
            ) { Text(if (listening) "Listening…" else "🎙 Speak") }

            Button(
                modifier = Modifier.weight(1f),
                enabled = message.isNotBlank() && !busy,
                onClick = {
                    val request = message.trim()
                    message = ""
                    sendMessage(request) { answer ->
                        if (voiceEnabled) speak(answer)
                    }
                }
            ) { Text(if (busy) "Thinking…" else "Send") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                Text("Change image")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice", color = Color.White)
                Switch(
                    checked = voiceEnabled,
                    onCheckedChange = {
                        voiceEnabled = it
                        if (!it) {
                            tts.value?.stop()
                            speaking = false
                            status = "Voice disabled"
                        } else status = "Voice enabled"
                    }
                )
            }

            TextButton(onClick = {
                tts.value?.stop()
                speaking = false
                status = "Voice stopped"
            }) { Text("Stop") }
        }

        Text(status, color = NilaCyan, style = MaterialTheme.typography.bodySmall)
    }
}
