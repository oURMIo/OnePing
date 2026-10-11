package com.oneping.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneping.data.AppSettings
import com.oneping.data.Repository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
    val seenApps by repo.seenApps.collectAsStateWithLifecycle(initialValue = emptyMap())
    val s = settings ?: return

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Quiet period (default for everyone)") {
            Text(
                "After a notification from a person or chat rings, further notifications from that same " +
                    "source stay silent for this long. Every source in every app has its own timer.",
                style = MaterialTheme.typography.bodySmall,
            )
            CooldownPicker(s.globalCooldownSec, onChange = { scope.launch { repo.setGlobalCooldown(it) } })
        }

        SectionCard("Reset the timer when I've seen it") {
            SwitchRow("When I open or swipe away the notification", s.resetOnUserDismiss) {
                scope.launch { repo.setResetOnUserDismiss(it) }
            }
            SwitchRow(
                "Also when the app clears it (e.g. chat read on another device)",
                s.resetOnAppCancel,
                enabled = s.resetOnUserDismiss,
            ) { scope.launch { repo.setResetOnAppCancel(it) } }
        }

        AppsCard(s, seenApps.keys, onToggleNew = { scope.launch { repo.setMonitorNewApps(it) } }) { pkg, on ->
            scope.launch { repo.setAppMonitored(pkg, on) }
        }

        Text(
            "No internet access, no analytics. All data stays on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppsCard(
    s: AppSettings,
    seenApps: Set<String>,
    onToggleNew: (Boolean) -> Unit,
    onToggle: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember(seenApps) { seenApps.sortedBy { AppLabels.label(context, it).lowercase() } }
    SectionCard("Apps") {
        SwitchRow("Apply to new apps automatically", s.monitorNewApps, onChange = onToggleNew)
        Text(
            "Calls, alarms, media, navigation and ongoing notifications are never touched.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (apps.isEmpty()) {
            Text(
                "Apps appear here after they post their first notification.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        apps.forEach { pkg ->
            val icon = remember(pkg) { AppLabels.icon(context, pkg)?.toBitmap(96, 96)?.asImageBitmap() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Image(icon, contentDescription = null, Modifier.size(32.dp))
                else Spacer(Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(AppLabels.label(context, pkg))
                    Text(
                        pkg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.isMonitored(pkg), onCheckedChange = { onToggle(pkg, it) })
            }
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
