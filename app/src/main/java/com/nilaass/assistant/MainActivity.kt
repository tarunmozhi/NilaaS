package com.nilaass.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.nilaass.assistant.core.AssistantEngine
import com.nilaass.assistant.core.ChatLine
import com.nilaass.assistant.storage.LocalStore
import com.nilaass.assistant.storage.EncryptedBackup
import com.nilaass.assistant.security.SecureVault
import com.nilaass.assistant.network.OpenAiCompatibleClient
import com.nilaass.assistant.learning.LearningStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NilaasSApp() }
    }
}

private enum class Page(val title: String) { CHAT("Chat"), STORAGE("Storage"), SKILLS("Skills"), RESEARCH("Web"), MEDIA("Media"), SECURITY("Security Lab"), VAULT("Vault"), SETTINGS("AI Setup"), PRIVACY("Privacy") }

@Composable
private fun NilaasSApp() {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val engine = remember { AssistantEngine() }
    val secureVault = remember { SecureVault(context) }
    val learning = remember { LearningStore(context) }
    val scope = rememberCoroutineScope()
    var onlineEnabled by remember { mutableStateOf(secureVault.get("online_enabled") == "true") }
    var sending by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(Page.CHAT) }
    var input by remember { mutableStateOf("") }
    var speakReplies by remember { mutableStateOf(false) }
    val lines = remember {
        mutableStateListOf<ChatLine>().apply {
            addAll(store.loadChat())
            if (isEmpty()) add(ChatLine("NilaasS", "வணக்கம்! I'm NilaasS. I can answer a few local commands. Chat history is stored on this device."))
        }
    }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts.value?.language = Locale("en", "IN")
        }
        tts.value = instance
        onDispose { instance.stop(); instance.shutdown(); tts.value = null }
    }
    val speechPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSpeechRecognition(context) { input = it }
    }
    val folderCategory = remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        val category = folderCategory.value
        if (uri != null && category != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                store.saveFolder(category, uri)
            }
        }
        folderCategory.value = null
    }


    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("NilaasS", style = MaterialTheme.typography.headlineMedium)
                Text("Nila Assistant • local-first foundation", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Page.values().forEach { p -> TextButton(onClick = { page = p }) { Text(if (page == p) "● ${p.title}" else p.title, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                }
                if (statusMessage.isNotBlank()) Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                when (page) {
                    Page.CHAT -> {
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(lines) { line ->
                                Surface(
                                    Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                                    color = if (line.speaker == "You") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(line.speaker, style = MaterialTheme.typography.labelMedium)
                                        Text(line.text)
                                    }
                                }
                            }
                        }
                        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Message NilaasS") }, placeholder = { Text("help, time, offline, உதவி, நேரம்") })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = input.isNotBlank() && !sending, onClick = {
                                val request = input.trim()
                                if (request.isNotEmpty()) {
                                    lines.add(ChatLine("You", request)); input = ""; sending = true; statusMessage = ""
                                    store.saveChat(lines)
                                    scope.launch {
                                        val answer = withContext(Dispatchers.IO) {
                                            val custom = learning.matchSkill(request)
                                            if (custom != null) custom
                                            else if (onlineEnabled && secureVault.get("api_key").orEmpty().isBlank()) "Online AI is enabled, but no API key is saved. Open AI Setup to configure the provider."
                                            else if (onlineEnabled) {
                                                runCatching {
                                                    OpenAiCompatibleClient().chat(
                                                        secureVault.get("base_url") ?: "https://api.openai.com",
                                                        secureVault.get("model") ?: "gpt-4o-mini",
                                                        secureVault.get("api_key").orEmpty(), request,
                                                        "You are NilaasS, a helpful assistant. Follow the user's requests when allowed. Adult-content preference is ${if (secureVault.get("adult_mode") == "true") "enabled, for consenting adults only and subject to provider policy" else "disabled"}. Never facilitate harm, exploitation, or sexual content involving minors. Approved user preferences: ${learning.preferences().joinToString("; ")}"
                                                    )
                                                }.getOrElse { "Online AI failed: ${it.message ?: "unknown error"}. Check AI Setup, network, endpoint and provider access. No local fallback data was uploaded automatically." }
                                            } else engine.respond(request)
                                        }
                                        lines.add(ChatLine("NilaasS", answer)); store.saveChat(lines)
                                        if (speakReplies) { tts.value?.language = if (answer.any { it in '\u0B80'..'\u0BFF' }) Locale("ta", "IN") else Locale("en", "IN"); tts.value?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "nilaass-reply") }
                                        sending = false
                                    }
                                }
                            }) { Text(if (sending) "Working…" else "Send") }
                            Button(onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    startSpeechRecognition(context) { input = it }
                                } else speechPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }) { Text("Speak") }
                            Button(onClick = { speakReplies = !speakReplies }) { Text(if (speakReplies) "Voice replies: on" else "Voice replies: off") }
                        }
                    }
                    Page.STORAGE -> StoragePage(store, secureVault, folderCategory, folderPicker, lines) { onlineEnabled = false; secureVault.put("online_enabled", "false") }
                    Page.SKILLS -> SkillsPage(learning)
                    Page.RESEARCH -> ResearchPage()
                    Page.MEDIA -> MediaPage(secureVault, store)
                    Page.SECURITY -> SecurityLabPage()
                    Page.VAULT -> VaultPage(secureVault)
                    Page.SETTINGS -> AiSettingsPage(secureVault, onlineEnabled, { enabled -> onlineEnabled = enabled; secureVault.put("online_enabled", enabled.toString()) })
                    Page.PRIVACY -> PrivacyPage(lines) {
                        lines.clear()
                        lines.add(ChatLine("NilaasS", "Conversation history cleared on this device."))
                        store.forgetChat(); store.saveChat(lines)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoragePage(
    store: LocalStore,
    vault: SecureVault,
    folderCategory: MutableState<String?>,
    folderPicker: androidx.activity.compose.ManagedActivityResultLauncher<Uri?, Uri?>,
    lines: androidx.compose.runtime.snapshots.SnapshotStateList<ChatLine>,
    onRestoreComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupPassphrase by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf("") }
    var backupBusy by remember { mutableStateOf(false) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selectedUri ->
        if (selectedUri != null) {
            backupBusy = true
            val pass = backupPassphrase.toCharArray()
            scope.launch {
                val restored = withContext(Dispatchers.IO) { runCatching { EncryptedBackup.restore(context, selectedUri, vault, pass) } }
                pass.fill('\u0000')
                restored.onSuccess { restoredLines ->
                    lines.clear(); lines.addAll(restoredLines); store.saveChat(lines); onRestoreComplete()
                    backupStatus = "Encrypted backup restored. Review provider settings before enabling online services."
                }.onFailure { backupStatus = "Restore failed: ${it.message ?: "wrong passphrase or invalid backup"}" }
                backupPassphrase = ""
                backupBusy = false
            }
        }
    }
    val categories = listOf("media" to "Generated media and files", "backups" to "Encrypted backups")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose folders for NilaasS data", style = MaterialTheme.typography.titleLarge)
        Text("Android's folder picker grants access only to the folders you select. Private chat history stays in app storage in this version.")
        categories.forEach { (key, title) ->
            val uri = store.folder(key)
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(if (uri == null) "No folder selected" else (DocumentFile.fromTreeUri(context, uri)?.name ?: "Selected folder"))
                    Button(onClick = { folderCategory.value = key; folderPicker.launch(uri) }) { Text("Choose folder") }
                    if (key == "backups") {
                        OutlinedTextField(backupPassphrase, { backupPassphrase = it }, Modifier.fillMaxWidth(), label = { Text("Backup passphrase (10+ characters)") }, visualTransformation = PasswordVisualTransformation())
                        Button(enabled = uri != null && backupPassphrase.length >= 10 && !backupBusy, onClick = {
                            backupBusy = true
                            val pass = backupPassphrase.toCharArray()
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { runCatching { EncryptedBackup.write(context, uri!!, lines, vault, pass) } }
                                pass.fill('\u0000')
                                backupStatus = result.fold({ "Encrypted backup saved: $it. Keep the passphrase safe; it is not recoverable." }, { "Backup failed: ${it.message ?: "unknown error"}" })
                                backupPassphrase = ""
                                backupBusy = false
                            }
                        }) { Text(if (backupBusy) "Encrypting…" else "Export encrypted full backup") }
                        Button(enabled = !backupBusy && backupPassphrase.length >= 10, onClick = { restorePicker.launch(arrayOf("application/octet-stream", "application/json", "*/*")) }) { Text("Restore encrypted backup") }
                        if (backupStatus.isNotBlank()) Text(backupStatus, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text("Chat history, learned preferences, skills, API credentials, and vault entries are encrypted in app-private storage. Encrypted full backups include these data and are saved to the backup folder you choose. Use a strong passphrase and keep it separately.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SkillsPage(learning: LearningStore) {
    var preference by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Learning & skills", style = MaterialTheme.typography.titleLarge)
        Text("NilaasS learns preferences you explicitly save. This is persistent personalization, not autonomous model training.")
        OutlinedTextField(preference, { preference = it }, Modifier.fillMaxWidth(), label = { Text("Preference to remember") })
        Button(onClick = { learning.addPreference(preference); preference = ""; notice = "Preference saved locally." }, enabled = preference.isNotBlank()) { Text("Remember preference") }
        Text("Saved preferences (${learning.preferences().size})")
        learning.preferences().forEach { Text("• $it") }
        Button(onClick = { learning.clearPreferences(); notice = "Preferences cleared." }) { Text("Clear preferences") }
        Text("Create a simple local skill", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(trigger, { trigger = it }, Modifier.fillMaxWidth(), label = { Text("Exact trigger phrase") })
        OutlinedTextField(reply, { reply = it }, Modifier.fillMaxWidth(), label = { Text("Assistant response") })
        Button(onClick = { learning.addSkill(trigger, reply); trigger = ""; reply = ""; notice = "Skill saved." }, enabled = trigger.isNotBlank() && reply.isNotBlank()) { Text("Save skill") }
        Text("Local skills (${learning.skills().size})")
        learning.skills().forEach { (t, r) ->
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(10.dp)) { Text(t, style = MaterialTheme.typography.titleSmall); Text(r); TextButton(onClick = { learning.removeSkill(t); notice = "Skill removed." }) { Text("Remove") } }
            }
        }
        if (notice.isNotBlank()) Text(notice)
    }
}

@Composable
private fun AiSettingsPage(vault: SecureVault, onlineEnabled: Boolean, setOnlineEnabled: (Boolean) -> Unit) {
    var baseUrl by remember { mutableStateOf(vault.get("base_url") ?: "https://api.openai.com") }
    var model by remember { mutableStateOf(vault.get("model") ?: "gpt-4o-mini") }
    var apiKey by remember { mutableStateOf(vault.get("api_key").orEmpty()) }
    var adultMode by remember { mutableStateOf(vault.get("adult_mode") == "true") }
    var imageModel by remember { mutableStateOf(vault.get("image_model") ?: "gpt-image-1") }
    var replicateToken by remember { mutableStateOf(vault.get("replicate_token").orEmpty()) }
    var replicateVersion by remember { mutableStateOf(vault.get("replicate_version").orEmpty()) }
    var showReplicateToken by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var testingProvider by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AI provider setup", style = MaterialTheme.typography.titleLarge)
        Text("Use an OpenAI-compatible Chat Completions API. Enter credentials here in the app, never in chat. The key is encrypted with Android Keystore-backed AES-GCM.")
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Text("Allow online AI"); Text("Messages are sent to your configured provider only while enabled.", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = onlineEnabled, onCheckedChange = setOnlineEnabled)
        }
        OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("Provider base URL (HTTPS)") }, placeholder = { Text("https://api.openai.com") })
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Model name") })
        OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("API key") }, visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide key" else "Show key") }
            Button(onClick = {
                if (!baseUrl.startsWith("https://") || model.isBlank() || (onlineEnabled && apiKey.isBlank())) status = "Enter an HTTPS URL, model and API key when enabling online AI."
                else { vault.put("base_url", baseUrl.trim()); vault.put("model", model.trim()); if (apiKey.isNotBlank()) vault.put("api_key", apiKey.trim()); vault.put("image_model", imageModel.trim()); if (replicateToken.isNotBlank()) vault.put("replicate_token", replicateToken.trim()); if (replicateVersion.isNotBlank()) vault.put("replicate_version", replicateVersion.trim()); status = "Settings saved encrypted on this device." }
            }) { Text("Save settings") }
        }
        Button(enabled = !testingProvider && baseUrl.startsWith("https://") && model.isNotBlank() && apiKey.isNotBlank(), onClick = {
            testingProvider = true; status = "Testing chat provider…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { OpenAiCompatibleClient().chat(baseUrl.trim(), model.trim(), apiKey.trim(), "Reply with exactly: NilaasS connection successful.", "You are testing a connection. Reply briefly.") }
                }
                status = result.fold({ "Provider responded: ${it.take(240)}" }, { "Provider test failed: ${it.message ?: "unknown error"}" })
                testingProvider = false
            }
        }) { Text(if (testingProvider) "Testing…" else "Test chat connection") }
        Text("Image generation model", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(imageModel, { imageModel = it }, Modifier.fillMaxWidth(), label = { Text("Images API model") })
        Text("Video generation (Replicate)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(replicateToken, { replicateToken = it }, Modifier.fillMaxWidth(), label = { Text("Replicate API token") }, visualTransformation = if (showReplicateToken) VisualTransformation.None else PasswordVisualTransformation())
        OutlinedTextField(replicateVersion, { replicateVersion = it }, Modifier.fillMaxWidth(), label = { Text("Video model version ID") })
        TextButton(onClick = { showReplicateToken = !showReplicateToken }) { Text(if (showReplicateToken) "Hide video token" else "Show video token") }
        TextButton(onClick = { vault.remove("replicate_token"); vault.remove("replicate_version"); replicateToken = ""; replicateVersion = ""; status = "Video provider credentials removed." }) { Text("Remove video credentials") }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Text("Adult-content preference"); Text("This preference cannot override the AI provider's policies.", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = adultMode, onCheckedChange = { adultMode = it; vault.put("adult_mode", it.toString()) })
        }
        Button(onClick = { vault.remove("api_key"); apiKey = ""; setOnlineEnabled(false); status = "API key removed and online AI disabled." }) { Text("Delete API key") }
        if (status.isNotBlank()) Text(status)
        Text("The chat provider, image provider, and video provider use separate API capabilities. Configure only providers that support the selected model and content.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun VaultPage(vault: SecureVault) {
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val entries = remember { mutableStateListOf<Pair<String, String>>().apply {
        runCatching {
            val a = JSONArray(vault.get("vault_entries") ?: "[]")
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); add(o.optString("label") to o.optString("secret")) }
        }
    } }
    fun persist() { val a = JSONArray(); entries.forEach { (l,s) -> a.put(JSONObject().put("label", l).put("secret", s)) }; vault.put("vault_entries", a.toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Encrypted private vault", style = MaterialTheme.typography.titleLarge)
        Text("Values are encrypted at rest with Android Keystore-backed AES-GCM. This is a basic local vault, not a professionally audited password manager. Keep a separate secure recovery method; uninstalling the app can make data unrecoverable.")
        OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("Entry label") })
        OutlinedTextField(secret, { secret = it }, Modifier.fillMaxWidth(), label = { Text("Password / secret") }, visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide" else "Show") }
            Button(onClick = { if (label.isNotBlank() && secret.isNotBlank()) { entries.removeAll { it.first.equals(label.trim(), true) }; entries.add(label.trim().take(80) to secret); persist(); label = ""; secret = ""; status = "Encrypted entry saved." } }) { Text("Save secret") }
        }
        entries.forEach { (l, v) ->
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(10.dp)) { Text(l, style = MaterialTheme.typography.titleSmall); if (reveal) Text(v); TextButton(onClick = { entries.remove(l to v); persist(); status = "Entry deleted." }) { Text("Delete") } }
            }
        }
        if (status.isNotBlank()) Text(status)
    }
}

@Composable
private fun ResearchPage() {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Web research", style = MaterialTheme.typography.titleLarge)
        Text("Search opens your browser. Your search query is sent to the search engine you choose; do not include secrets.")
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search the web") })
        Button(enabled = query.isNotBlank(), onClick = {
            val uri = Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(query))
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }) { Text("Search in browser") }
    }
}

@Composable
private fun SecurityLabPage() {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf("https://") }
    var authorized by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Authorized web security check", style = MaterialTheme.typography.titleLarge)
        Text("Runs a non-invasive HTTPS response-header and TLS certificate check against a site you own or have permission to assess. It does not exploit vulnerabilities, scan ports, or hide activity.")
        OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(), label = { Text("HTTPS URL") }, placeholder = { Text("https://your-domain.example") })
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = authorized, onCheckedChange = { authorized = it })
            Text("I own this site or have permission to test it")
        }
        Button(enabled = authorized && !running && target.startsWith("https://"), onClick = {
            running = true; report = "Checking HTTPS response headers…"
            val requested = target.trim()
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val url = java.net.URL(requested)
                        require(url.protocol == "https" && url.host.isNotBlank()) { "Enter a valid HTTPS URL." }
                        val connection = (url.openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                            requestMethod = "HEAD"; connectTimeout = 10000; readTimeout = 15000; instanceFollowRedirects = false
                            setRequestProperty("User-Agent", "NilaasS-AuthorizedSecurityCheck/1.0")
                        }
                        try {
                            val code = connection.responseCode
                            val headers = listOf("Strict-Transport-Security", "Content-Security-Policy", "X-Content-Type-Options", "X-Frame-Options", "Referrer-Policy", "Permissions-Policy")
                            val lines = mutableListOf("Target: ${url.host}", "HTTP status: $code", "TLS: certificate chain accepted by Android's trust store")
                            headers.forEach { h -> lines += "$h: ${connection.getHeaderField(h) ?: "MISSING"}" }
                            lines += "Server header: ${connection.getHeaderField("Server") ?: "not disclosed"}"
                            lines += "Redirect: ${connection.getHeaderField("Location") ?: "none"}"
                            lines += "Note: HEAD results may differ from GET responses; missing headers are review findings, not proof of a vulnerability."
                            lines.joinToString("\n")
                        } finally { connection.disconnect() }
                    }
                }
                report = result.fold({ it }, { "Check failed: ${it.message ?: "unknown error"}" })
                running = false
            }
        }) { Text(if (running) "Checking…" else "Run HTTPS check") }
        if (report.isNotBlank()) Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Text(report, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MediaPage(vault: SecureVault, store: LocalStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var resultUrl by remember { mutableStateOf("") }
    var resultKind by remember { mutableStateOf("image") }
    var status by remember { mutableStateOf("Image generation uses an OpenAI-compatible Images API. Video generation uses Replicate's Predictions API and a model version you configure.") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Image & video generation", style = MaterialTheme.typography.titleLarge)
        Text("Generation requires compatible provider credentials, model access, internet, and provider policy approval. Costs and adult-content permissions are set by the provider. Do not upload private people’s images without consent.")
        OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text("Describe the image or video") }, minLines = 3)
        Button(enabled = prompt.isNotBlank() && !busy, onClick = {
            busy = true; resultUrl = ""; status = "Generating image…"
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        val base = vault.get("base_url") ?: "https://api.openai.com"
                        val key = vault.get("api_key").orEmpty()
                        require(key.isNotBlank()) { "Configure an image-capable provider API key in AI Setup first." }
                        val endpoint = base.trimEnd('/') + if (base.trimEnd('/').endsWith("/v1")) "/images/generations" else "/v1/images/generations"
                        val conn = (java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection).apply {
                            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 90000; doOutput = true
                            setRequestProperty("Authorization", "Bearer $key"); setRequestProperty("Content-Type", "application/json")
                        }
                        try {
                            val body = JSONObject().put("model", vault.get("image_model") ?: "gpt-image-1").put("prompt", prompt).put("n", 1).put("size", "1024x1024")
                            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                            val statusCode = conn.responseCode
                            val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
                            val payload = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                            require(statusCode in 200..299) { "Image provider HTTP $statusCode: ${payload.take(300)}" }
                            val item = JSONObject(payload).optJSONArray("data")?.optJSONObject(0) ?: error("No image returned")
                            val remote = item.optString("url")
                            if (remote.startsWith("https://")) remote
                            else {
                                val encoded = item.optString("b64_json")
                                require(encoded.isNotBlank()) { "Provider returned neither a secure image URL nor base64 image data." }
                                val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                                val dir = java.io.File(context.filesDir, "GeneratedMedia").apply { mkdirs() }
                                val file = java.io.File(dir, "nilaass-image-${System.currentTimeMillis()}.png")
                                file.writeBytes(bytes)
                                "localpath:${file.absolutePath}"
                            }
                        } finally { conn.disconnect() }
                    }
                }
                outcome.onSuccess { resultUrl = it; resultKind = "image"; status = "Image generated. Use Open result to preview it. The result may be temporary; save it promptly." }
                    .onFailure { status = "Image generation failed: ${it.message ?: "unknown error"}" }
                busy = false
            }
        }) { Text(if (busy) "Working…" else "Generate image") }
        Text("Video generation", style = MaterialTheme.typography.titleMedium)
        Text("Configure a Replicate API token and a video model version in AI Setup. The adapter submits a prompt to the model's `input.prompt` field; some models require additional parameters and may need a model-specific adapter.")
        Button(enabled = prompt.isNotBlank() && !busy, onClick = {
            busy = true; resultUrl = ""; status = "Submitting video generation job…"
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        val token = vault.get("replicate_token").orEmpty()
                        val version = vault.get("replicate_version").orEmpty()
                        require(token.isNotBlank() && version.isNotBlank()) { "Configure a Replicate API token and video model version in AI Setup." }
                        val conn = (java.net.URL("https://api.replicate.com/v1/predictions").openConnection() as java.net.HttpURLConnection).apply {
                            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 90000; doOutput = true
                            setRequestProperty("Authorization", "Bearer $token"); setRequestProperty("Content-Type", "application/json"); setRequestProperty("Prefer", "wait=60")
                        }
                        val body = JSONObject().put("version", version).put("input", JSONObject().put("prompt", prompt))
                        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                        val code = conn.responseCode; val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                        val payload = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        require(code in 200..299) { "Video provider HTTP $code: ${payload.take(300)}" }
                        val job = JSONObject(payload)
                        var currentStatus = job.optString("status")
                        var output = job.opt("output")
                        var pollUrl = job.optJSONObject("urls")?.optString("get").orEmpty()
                        var polls = 0
                        while (currentStatus != "succeeded" && currentStatus != "failed" && currentStatus != "canceled" && pollUrl.startsWith("https://") && polls < 15) {
                            Thread.sleep(4000); polls++
                            val poll = (java.net.URL(pollUrl).openConnection() as java.net.HttpURLConnection).apply {
                                requestMethod = "GET"; connectTimeout = 10000; readTimeout = 20000; setRequestProperty("Authorization", "Bearer $token")
                            }
                            val pollPayload = try {
                                val pollCode = poll.responseCode
                                val pollStream = if (pollCode in 200..299) poll.inputStream else poll.errorStream
                                pollStream?.bufferedReader()?.use { it.readText() }.orEmpty().also { require(pollCode in 200..299) { "Video status check returned HTTP $pollCode" } }
                            } finally { poll.disconnect() }
                            val p = JSONObject(pollPayload); currentStatus = p.optString("status")
                            if (currentStatus == "failed" || currentStatus == "canceled") error(p.optString("error", "Video generation failed"))
                            output = p.opt("output"); pollUrl = p.optJSONObject("urls")?.optString("get").orEmpty()
                        }
                        val out = when (output) { is String -> output as String; is JSONArray -> (output as JSONArray).optString(0); else -> "" }
                        require(currentStatus == "succeeded" && out.startsWith("https://")) { "Video job is still processing or the model returned no video URL. Check the provider dashboard or configure a model-specific input adapter." }
                        out
                    }
                }
                outcome.onSuccess { resultUrl = it; resultKind = "video"; status = "Video generated. Open the result promptly; provider URLs may expire." }
                    .onFailure { status = "Video generation failed or is still processing: ${it.message ?: "unknown error"}" }
                busy = false
            }
        }) { Text(if (busy) "Working…" else "Generate video") }
        if (resultUrl.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    runCatching {
                        val uri = if (resultUrl.startsWith("localpath:")) {
                            val file = java.io.File(resultUrl.removePrefix("localpath:"))
                            androidx.core.content.FileProvider.getUriForFile(context, "com.nilaass.assistant.fileprovider", file)
                        } else Uri.parse(resultUrl)
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }.onFailure { status = "Could not open result: ${it.message ?: "no compatible viewer"}" }
                }) { Text("Open result") }
                Button(onClick = {
                    busy = true
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching {
                                val mime = if (resultKind == "video") "video/mp4" else "image/png"
                                val ext = if (resultKind == "video") "mp4" else "png"
                                val name = "nilaass-${resultKind}-${System.currentTimeMillis()}.$ext"
                                val folder = store.folder("media")
                                if (folder != null) {
                                    val root = DocumentFile.fromTreeUri(context, folder) ?: error("Selected media folder is unavailable")
                                    val file = root.createFile(mime, name) ?: error("Could not create output file in selected folder")
                                    context.contentResolver.openOutputStream(file.uri)?.use { out ->
                                        if (resultUrl.startsWith("localpath:")) java.io.File(resultUrl.removePrefix("localpath:")).inputStream().use { it.copyTo(out) }
                                        else java.net.URL(resultUrl).openStream().use { it.copyTo(out) }
                                    } ?: error("Could not open selected output file")
                                    file.uri.toString()
                                } else {
                                    val dir = java.io.File(context.filesDir, "GeneratedMedia").apply { mkdirs() }
                                    val file = java.io.File(dir, name)
                                    if (resultUrl.startsWith("localpath:")) java.io.File(resultUrl.removePrefix("localpath:")).copyTo(file, overwrite = true)
                                    else java.net.URL(resultUrl).openStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                                    file.absolutePath
                                }
                            }
                        }
                        status = saved.fold({ "Saved generated media: $it" }, { "Save failed: ${it.message ?: "unknown error"}" })
                        busy = false
                    }
                }, enabled = !busy) { Text("Save result") }
            }
        }
        Text(status)
    }
}

@Composable
private fun PrivacyPage(lines: List<ChatLine>, clearHistory: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Privacy and memory", style = MaterialTheme.typography.titleLarge)
        Text("• Local command handling does not send messages to a server.")
        Text("• Conversation history is encrypted at rest and capped at 300 messages.")
        Text("• Folder access is limited to folders you select.")
        Text("• Voice recognition may use the device's configured speech service; its offline and network behavior depends on that service.")
        Text("• API credentials and vault entries use AES-GCM encryption with an Android Keystore key.")
        Text("• Online AI sends the current message to the configured provider only when enabled; review provider terms and privacy.")
        Button(onClick = clearHistory) { Text("Clear conversation history") }
        Text("Messages currently stored: ${lines.size}")
    }
}

private fun startSpeechRecognition(context: android.content.Context, onResult: (String) -> Unit) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) return
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) { recognizer.destroy() }
        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onResult)
            recognizer.destroy()
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "சொல்லுங்கள் / Speak to NilaasS")
    }
    runCatching { recognizer.startListening(intent) }.onFailure { recognizer.destroy() }
}

