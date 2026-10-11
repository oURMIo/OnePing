package com.oneping.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneping.OnePingApp

@Composable
fun SetupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Bumped on every resume so the checks below are re-evaluated after returning from Settings.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val listener by OnePingApp.listenerConnected.collectAsStateWithLifecycle()
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    // Reading `refresh` makes these recompute on resume.
    val hasAccess = refresh.let { Permissions.hasListenerAccess(context) }
    val canPost = refresh.let { Permissions.hasPostNotifications(context) }
    val batteryOk = refresh.let { Permissions.isIgnoringBatteryOptimizations(context) }

    fun open(intent: Intent) = runCatching { context.startActivity(intent) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("OnePing", style = MaterialTheme.typography.headlineMedium)
        Text(
            "When someone sends several messages in a row, only the first one rings. The rest arrive " +
                "silently until that person's quiet period runs out. Other people still ring normally. " +
                "Works for every app; you don't need to change any app's notification settings.",
            style = MaterialTheme.typography.bodyMedium,
        )

        SectionCard("Status") {
            if (hasAccess && listener && canPost) {
                ModeText(
                    "✓ Working",
                    "A repeat is cancelled the moment it arrives (the system cuts its sound off; a very short " +
                        "blip may be audible) and is re-shown as a silent copy from OnePing with the same text, " +
                        "tap action and reply button.",
                    ok = true,
                )
            } else {
                ModeText("✗ Not working yet", "Complete the steps below.", ok = false, error = true)
            }
        }

        SectionCard("1. Notification access") {
            StatusRow("Notification access", hasAccess, "Grant") { open(Permissions.listenerSettingsIntent()) }
            if (hasAccess) {
                StatusRow("Listener running", listener, "Reconnect", okText = "Connected", badText = "Not connected") {
                    Permissions.requestRebind(context)
                }
            } else {
                Hint(
                    "If the switch is greyed out (\"Restricted setting\"): open App info → ⋮ → " +
                        "Allow restricted settings, then try again.",
                )
                OutlinedButton(onClick = { open(Permissions.appDetailsIntent(context)) }) { Text("Open App info") }
            }
        }

        SectionCard("2. Notifications & battery") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                StatusRow("Post notifications (for silent copies)", canPost, "Allow") {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            StatusRow("Battery: unrestricted", batteryOk, "Allow", okText = "Yes", badText = "Optimized") {
                open(Permissions.batteryOptimizationIntent(context))
            }
            Hint("Excluding the app from battery optimization keeps Android from killing it.")
        }

        SectionCard("3. Try it") {
            Text(
                "Ask friend A to send 5 messages in a row: only the first should ring. While that happens, " +
                    "a message from friend B should still ring. The Log tab shows every decision.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ModeText(title: String, body: String, ok: Boolean, error: Boolean = false) {
    Text(
        title,
        fontWeight = FontWeight.SemiBold,
        color = when {
            ok -> Color(0xFF2E7D32)
            error -> MaterialTheme.colorScheme.error
            else -> Color(0xFFB26A00)
        },
    )
    Text(body, style = MaterialTheme.typography.bodyMedium)
}


@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatusRow(
    title: String,
    ok: Boolean,
    actionLabel: String?,
    okText: String = "Granted",
    badText: String = "Missing",
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (ok) "✓ $okText" else "✗ $badText",
                color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!ok && actionLabel != null) Button(onClick = onAction) { Text(actionLabel) }
    }
}
