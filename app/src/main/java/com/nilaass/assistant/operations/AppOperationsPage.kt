package com.nilaass.assistant.operations

import android.content.Intent
import android.provider.Settings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LaunchableApp(val label: String, val packageName: String)

@Composable
fun AppOperationsPage() {
    val context = LocalContext.current
    var accessibilityEnabled by remember { mutableStateOf(NilaAccessibilityService.isEnabled()) }
    var status by remember { mutableStateOf("Choose an action. Nila will not operate apps silently.") }
    val audit = remember { mutableStateListOf<String>() }
    val apps = remember { loadLaunchableApps(context) }

    LaunchedEffect(Unit) {
        accessibilityEnabled = NilaAccessibilityService.isEnabled()
    }

    fun record(action: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        audit.add(0, "$stamp  $action")
        while (audit.size > 20) audit.removeAt(audit.lastIndex)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("App operations", style = MaterialTheme.typography.titleLarge)
        Text("Two routes: screen control for apps, and direct service integrations for accounts.")

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Screen control", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (accessibilityEnabled) "Accessibility bridge is active in this app process."
                    else "Accessibility bridge status may update after you return from Android Settings."
                )
                OutlinedButton(onClick = {
                    accessibilityEnabled = NilaAccessibilityService.isEnabled()
                    status = if (accessibilityEnabled) "Accessibility bridge is active."
                    else "Bridge is not active yet. Enable it in Android Settings and return."
                }) { Text("Refresh status") }
                Text("This prototype only supports opening apps and explicit Back/Home navigation. It does not read screen contents or click arbitrary controls.")
                Button(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        status = "Enable NilaasS App Operations in Android Accessibility settings, then return here."
                    }.onFailure { status = "Could not open Accessibility settings: ${it.message}" }
                }) { Text("Open Accessibility settings") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val ok = NilaAccessibilityService.goBack()
                        status = if (ok) "Back action sent." else "Enable the accessibility bridge first."
                        if (ok) record("Screen navigation: Back")
                    }) { Text("Back") }
                    OutlinedButton(onClick = {
                        val ok = NilaAccessibilityService.goHome()
                        status = if (ok) "Home action sent." else "Enable the accessibility bridge first."
                        if (ok) record("Screen navigation: Home")
                    }) { Text("Home") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Open installed apps", style = MaterialTheme.typography.titleMedium)
                Text("Opening an app is available now. Operating its screens will be added incrementally with visible, user-authorized actions.")
                if (apps.isEmpty()) {
                    Text("No launchable apps were returned by Android.")
                } else {
                    apps.take(30).forEach { app ->
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (launch == null) {
                                    status = "Android could not launch ${app.label}."
                                } else {
                                    runCatching {
                                        context.startActivity(launch)
                                        status = "Opened ${app.label}."
                                        record("Opened app: ${app.label}")
                                    }.onFailure { status = "Could not open ${app.label}: ${it.message}" }
                                }
                            }
                        ) { Text(app.label) }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Direct account integrations", style = MaterialTheme.typography.titleMedium)
                Text("Not connected yet. Each service needs its own secure sign-in/authorization flow.")
                Text("• Email — not connected")
                Text("• Calendar — not connected")
                Text("• Cloud storage — not connected")
                Text("• Other services — add one integration at a time")
                Text("Never put account passwords in chat or ordinary assistant memory. Use OAuth or the provider's supported authorization method.")
            }
        }

        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recent actions (this session)", style = MaterialTheme.typography.titleMedium)
                if (audit.isEmpty()) Text("No actions recorded yet.")
                audit.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val matches = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(intent, 0)
    }
    return matches.mapNotNull { info ->
        val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
        val label = info.loadLabel(context.packageManager)?.toString()?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        LaunchableApp(label, packageName)
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) }
}
