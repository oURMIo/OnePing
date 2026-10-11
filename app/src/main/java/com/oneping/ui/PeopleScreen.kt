package com.oneping.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneping.data.KnownSender
import com.oneping.data.Repository
import com.oneping.data.RuleMode
import com.oneping.data.SenderRule
import kotlinx.coroutines.launch

/** "Sources": every person / chat seen in any app, each with an optional own rule. */
@Composable
fun PeopleScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val scope = rememberCoroutineScope()
    val senders by repo.senders.collectAsStateWithLifecycle(initialValue = emptyList())
    val rules by repo.rules.collectAsStateWithLifecycle(initialValue = emptyMap())
    val lastSignals by repo.lastSignals.collectAsStateWithLifecycle(initialValue = emptyMap())
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
    var editing by remember { mutableStateOf<KnownSender?>(null) }

    Column(modifier.fillMaxSize()) {
        Text(
            "People and chats seen in your apps. The same name in two apps is two separate sources. " +
                "Tap one to give it its own rule.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        if (senders.isEmpty()) {
            Text(
                "Nothing yet. Sources appear here after their first notification.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn {
            items(senders, key = { it.key }) { sender ->
                val rule = rules[sender.key] ?: SenderRule()
                val ruleText = when (rule.mode) {
                    RuleMode.DEFAULT -> "Default: ${settings?.globalCooldownSec?.let(::formatDuration) ?: ""}"
                    RuleMode.CUSTOM -> "Own quiet period: ${formatDuration(rule.customCooldownSec)}"
                    RuleMode.ALWAYS -> "Always alert"
                    RuleMode.MUTED -> "Always silent"
                }
                val lastAlert = lastSignals[sender.key]?.let { " · rang ${formatTime(context, it)}" } ?: ""
                ListItem(
                    overlineContent = { Text(AppLabels.label(context, sender.pkg)) },
                    headlineContent = { Text(sender.name + if (sender.isGroup) "  (group)" else "") },
                    supportingContent = { Text(ruleText + lastAlert) },
                    modifier = Modifier.clickable { editing = sender },
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { sender ->
        RuleDialog(
            sender = sender,
            appLabel = AppLabels.label(context, sender.pkg),
            initial = rules[sender.key] ?: SenderRule(),
            onDismiss = { editing = null },
            onSave = { rule ->
                scope.launch { repo.setRule(sender.key, rule) }
                editing = null
            },
            onForget = {
                scope.launch { repo.forgetSender(sender.key) }
                editing = null
            },
        )
    }
}

@Composable
private fun RuleDialog(
    sender: KnownSender,
    appLabel: String,
    initial: SenderRule,
    onDismiss: () -> Unit,
    onSave: (SenderRule) -> Unit,
    onForget: () -> Unit,
) {
    var mode by remember { mutableStateOf(initial.mode) }
    var cooldown by remember { mutableStateOf(initial.customCooldownSec) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${sender.name} · $appLabel") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RuleMode.entries.forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(mode == m, role = Role.RadioButton) { mode = m }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == m, onClick = null)
                        Text(m.label, Modifier.padding(start = 8.dp))
                    }
                }
                if (mode == RuleMode.CUSTOM) {
                    CooldownPicker(cooldown, onChange = { cooldown = it })
                }
                TextButton(onClick = onForget) { Text("Forget this source") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(SenderRule(mode, cooldown)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
