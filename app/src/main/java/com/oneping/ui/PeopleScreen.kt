package com.oneping.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneping.data.AppSettings
import com.oneping.data.KnownSender
import com.oneping.data.PAUSE_MS
import com.oneping.data.Repository
import com.oneping.data.RuleMode
import com.oneping.data.SenderRule
import kotlinx.coroutines.launch

/** People and chats seen in any app, each with an optional own rule; apps at the bottom. */
@Composable
fun PeopleScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val scope = rememberCoroutineScope()
    val senders by repo.senders.collectAsStateWithLifecycle(initialValue = emptyList())
    val rules by repo.rules.collectAsStateWithLifecycle(initialValue = emptyMap())
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
    val seenApps by repo.seenApps.collectAsStateWithLifecycle(initialValue = emptyMap())
    var editing by remember { mutableStateOf<KnownSender?>(null) }
    val s = settings ?: return

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text("People", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Hint("Tap someone to give them their own rule.", Modifier.padding(top = 4.dp, bottom = 12.dp))
            if (senders.isEmpty()) Hint("Nobody yet. People appear here after their first message.")
        }
        items(senders, key = { it.key }) { sender ->
            val rule = rules[sender.key] ?: SenderRule()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { editing = sender }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(sender.pkg)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(sender.name, fontWeight = FontWeight.Medium, maxLines = 1)
                    Hint("${AppLabels.label(context, sender.pkg)}${if (sender.isGroup) " · group" else ""}")
                }
                RuleBadge(rule, s)
            }
        }
        item { AppsSection(s, seenApps.keys, repo) }
    }

    editing?.let { sender ->
        RuleSheet(
            sender = sender,
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
private fun RuleBadge(rule: SenderRule, s: AppSettings) {
    val context = LocalContext.current
    val text = when {
        rule.isTemporarilyMuted() -> "Muted till ${formatClock(context, rule.mutedUntil)}"
        rule.mode == RuleMode.DEFAULT -> formatDuration(s.globalCooldownSec)
        rule.mode == RuleMode.CUSTOM -> formatDuration(rule.customCooldownSec)
        rule.mode == RuleMode.ALWAYS -> "Always rings"
        else -> "Silent"
    }
    val custom = !rule.isDefault
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (custom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSheet(
    sender: KnownSender,
    initial: SenderRule,
    onDismiss: () -> Unit,
    onSave: (SenderRule) -> Unit,
    onForget: () -> Unit,
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(initial.mode) }
    var cooldown by remember { mutableStateOf(initial.customCooldownSec) }
    var mutedUntil by remember { mutableStateOf(initial.mutedUntil) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(sender.pkg, 48.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(sender.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Hint(AppLabels.label(context, sender.pkg))
                }
            }
            Spacer(Modifier.padding(4.dp))
            RuleMode.entries.forEach { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(mode == m, role = Role.RadioButton) { mode = m }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == m, onClick = null)
                    Text(m.label, Modifier.padding(start = 12.dp))
                }
            }
            AnimatedVisibility(mode == RuleMode.CUSTOM) {
                QuietPeriodChips(cooldown, onChange = { cooldown = it }, Modifier.padding(start = 36.dp))
            }
            val muted = mutedUntil > System.currentTimeMillis()
            OutlinedButton(
                onClick = { mutedUntil = if (muted) 0L else System.currentTimeMillis() + PAUSE_MS },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (muted) "Unmute (muted till ${formatClock(context, mutedUntil)})" else "Mute for 1 hour")
            }
            Row {
                TextButton(onClick = onForget) { Text("Forget") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onSave(SenderRule(mode, cooldown, mutedUntil)) }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun AppsSection(s: AppSettings, seenApps: Set<String>, repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val apps = remember(seenApps) { seenApps.sortedBy { AppLabels.label(context, it).lowercase() } }

    Column(Modifier.padding(top = 24.dp)) {
        SectionCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Hint("${apps.count { s.isMonitored(it) }} of ${apps.size} apps are handled")
                }
                Hint(if (open) "Hide" else "Show")
            }
            AnimatedVisibility(open) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Handle new apps automatically", Modifier.weight(1f))
                        Switch(s.monitorNewApps, onCheckedChange = { scope.launch { repo.setMonitorNewApps(it) } })
                    }
                    Hint("Calls, alarms, media and navigation are never touched.")
                    apps.forEach { pkg ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(pkg, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(AppLabels.label(context, pkg), Modifier.weight(1f), maxLines = 1)
                            Switch(
                                s.isMonitored(pkg),
                                onCheckedChange = { on -> scope.launch { repo.setAppMonitored(pkg, on) } },
                            )
                        }
                    }
                }
            }
        }
    }
}
