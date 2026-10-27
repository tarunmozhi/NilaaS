package com.nilaass.assistant.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nilaass.assistant.core.ChatLine
import com.nilaass.assistant.security.SecureVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NilaCloudStoragePage(vault: SecureVault, lines: List<ChatLine>) {
    val context = LocalContext.current
    val workspace = remember(context) { NilaCloudWorkspace(context) }
    val scope = rememberCoroutineScope()
    var projectUrl by remember { mutableStateOf(vault.get("cloud_supabase_url").orEmpty()) }
    var anonKey by remember { mutableStateOf(vault.get("cloud_supabase_anon_key").orEmpty()) }
    var bucket by remember { mutableStateOf(vault.get("cloud_supabase_bucket") ?: "nila-private") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Nila Cloud Autonomy", style = MaterialTheme.typography.titleLarge)
        Text("Temporary phone workspace is capped at 2 GB and stored inside NilaaS app-private storage.")
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Temporary workspace", style = MaterialTheme.typography.titleMedium)
                Text("Used: ${formatBytes(workspace.tempBytes())} / 2 GB")
                Text("Remaining: ${formatBytes(workspace.availableTempBytes())}")
                Text("Offline snapshots waiting: ${workspace.pendingCount()}")
                Button(onClick = {
                    val result = runCatching { workspace.cleanupTemporaryFiles() }
                    status = result.fold(
                        { "Removed $it Nila-owned temporary file(s). Queued memory snapshots were not touched." },
                        { "Cleanup failed: ${it.message}" }
                    )
                    refresh++
                }) { Text("Clean Nila temporary files") }
            }
        }

        Text("Provider discovery and evaluation", style = MaterialTheme.typography.titleMedium)
        workspace.candidates(vault).forEach { candidate ->
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(candidate.label, style = MaterialTheme.typography.titleSmall)
                    Text(if (candidate.configured) "Configured" else "Not configured")
                    Text(candidate.note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("The chooser selects only configured providers with an implemented adapter. At present, Supabase Storage is the supported cloud adapter; other candidates are shown transparently, not falsely marked connected.")

        Text("Configure Supabase Storage", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = projectUrl,
            onValueChange = { projectUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Project URL (HTTPS)") },
            singleLine = true
        )
        OutlinedTextField(
            value = anonKey,
            onValueChange = { anonKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Publishable / anon key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        OutlinedTextField(
            value = bucket,
            onValueChange = { bucket = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Private bucket name") },
            singleLine = true
        )
        Button(
            enabled = projectUrl.startsWith("https://") && anonKey.isNotBlank() && bucket.isNotBlank(),
            onClick = {
                vault.put("cloud_supabase_url", projectUrl.trim().trimEnd('/'))
                vault.put("cloud_supabase_anon_key", anonKey.trim())
                vault.put("cloud_supabase_bucket", bucket.trim())
                status = "Configuration saved in SecureVault. Create a private Supabase Storage bucket named '$bucket' and apply restrictive access policies before syncing."
                refresh++
            }
        ) { Text("Save provider configuration") }

        Text("Memory sync", style = MaterialTheme.typography.titleMedium)
        Text("Nila queues an encrypted-at-rest local snapshot while offline. Cloud transfer uses HTTPS and the configured publishable key. A successful upload is read back and SHA-256 verified before the queue copy is removed.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy && lines.isNotEmpty(),
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { workspace.enqueueSnapshot(workspace.snapshotJson(lines)) }
                        }
                        status = result.fold(
                            { "Snapshot safely queued: ${it.name}" },
                            { "Could not queue snapshot: ${it.message}" }
                        )
                        busy = false
                        refresh++
                    }
                }
            ) { Text("Queue memory snapshot") }
            Button(
                enabled = !busy && workspace.pendingCount() > 0,
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { workspace.syncQueuedSnapshots(vault) }
                        }
                        status = result.fold(
                            { it },
                            { "Sync failed; local data was retained: ${it.message ?: "unknown error"}" }
                        )
                        busy = false
                        refresh++
                    }
                }
            ) { Text(if (busy) "Working…" else "Sync queued snapshots") }
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.secondary)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
