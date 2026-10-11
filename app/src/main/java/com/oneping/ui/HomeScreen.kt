package com.oneping.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import com.oneping.data.Mode
import com.oneping.data.FOREVER
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneping.OnePingApp
import com.oneping.R
import com.oneping.data.AppSettings
import com.oneping.data.Decision
import com.oneping.data.Repository
import com.oneping.data.TodayStats
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
    val stats by repo.todayStats.collectAsStateWithLifecycle(initialValue = TodayStats())
    val s = settings ?: return

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusHeader(s, onMode = { scope.launch { repo.setMode(it) } })
        PermissionBanner()

        SectionCard("Quiet period") {
            Hint("After someone's message rings, their next messages stay silent for this long.")
            QuietPeriodChips(s.globalCooldownSec, onChange = { scope.launch { repo.setGlobalCooldown(it) } })
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reset when I've seen it")
                    Hint("Opening or swiping away the chat makes the next message ring again.")
                }
                Switch(s.resetWhenSeen, onCheckedChange = { scope.launch { repo.setResetWhenSeen(it) } })
            }
        }

        TodayCard(stats)
        RecentActivity(repo)
    }
}

@Composable
private fun StatusHeader(s: AppSettings, onMode: (Mode) -> Unit) {
    val context = LocalContext.current
    // Ticks so the countdown updates and the timed modes end on their own.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val mode = s.mode(now)
    val status = when (mode) {
        Mode.ONE_PING -> "One ping per burst · ${formatDuration(s.globalCooldownSec)}"
        Mode.EVERYONE_RINGS ->
            if (s.pausedUntil == FOREVER) "Everyone rings until you switch back"
            else "Everyone rings till ${formatClock(context, s.pausedUntil)}"
        Mode.SILENCE_ALL -> {
            val left = ((s.silenceAllUntil - now + 59_999) / 60_000).coerceAtLeast(1)
            "Everything silent · $left min left"
        }
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.logo_round),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("OnePing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("One ping", "first message rings", mode == Mode.ONE_PING, Modifier.weight(1f)) {
                    onMode(Mode.ONE_PING)
                }
                ModeButton("Everyone rings", "no cooldown", mode == Mode.EVERYONE_RINGS, Modifier.weight(1f)) {
                    onMode(Mode.EVERYONE_RINGS)
                }
                ModeButton("Silence", "all for 15 min", mode == Mode.SILENCE_ALL, Modifier.weight(1f)) {
                    // Tapping again while silent restarts the 15 minutes.
                    onMode(Mode.SILENCE_ALL)
                }
            }
        }
    }
}

/** A large, tall mode button; the selected one is filled with the brand color. */
@Composable
private fun ModeButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = content.copy(alpha = 0.8f),
            )
        }
    }
}

/** Shown only while something needed is missing. */
@Composable
private fun PermissionBanner() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val listener by OnePingApp.listenerConnected.collectAsStateWithLifecycle()
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val hasAccess = refresh.let { Permissions.hasListenerAccess(context) }
    val canPost = refresh.let { Permissions.hasPostNotifications(context) }
    val batteryOk = refresh.let { Permissions.isIgnoringBatteryOptimizations(context) }
    if (hasAccess && listener && canPost && batteryOk) return

    fun open(intent: Intent) = runCatching { context.startActivity(intent) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Finish setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (!hasAccess) {
                SetupStep("Allow notification access", "Needed to see and silence notifications.", "Allow") {
                    open(Permissions.listenerSettingsIntent())
                }
                Hint("Greyed out? App info → ⋮ → Allow restricted settings.")
                TextButton(onClick = { open(Permissions.appDetailsIntent(context)) }) { Text("Open App info") }
            } else if (!listener) {
                SetupStep("Service isn't running", "Android stopped it. Reconnect to restart it.", "Reconnect") {
                    Permissions.requestRebind(context)
                }
            }
            if (!canPost && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SetupStep("Allow notifications", "Silenced messages are collected in a card.", "Allow") {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (!batteryOk) {
                SetupStep("Unrestricted battery", "Keeps Android from stopping OnePing.", "Allow") {
                    open(Permissions.batteryOptimizationIntent(context))
                }
            }
        }
    }
}

@Composable
private fun SetupStep(title: String, body: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Medium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        Button(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun TodayCard(stats: TodayStats) {
    val context = LocalContext.current
    val isToday = stats.day == today()
    val sources = if (isToday) stats.sources.values.sortedByDescending { it.lastTime } else emptyList()
    val silenced = if (isToday) stats.silenced else 0

    SectionCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$silenced",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (silenced == 1) "repeat silenced today" else "repeats silenced today",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (sources.isEmpty()) {
            Hint("Nothing yet today. People who message you will show up here.")
        }
        sources.take(8).forEach { src ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(src.pkg, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(src.name, fontWeight = FontWeight.Medium, maxLines = 1)
                    Hint(
                        "${AppLabels.label(context, src.pkg)} · ${src.messages} msg · " +
                            if (src.rang == 1) "rang once" else "rang ${src.rang}×",
                    )
                }
                Hint(formatClock(context, src.lastTime))
            }
        }
    }
}

@Composable
private fun RecentActivity(repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val events by repo.log.collectAsStateWithLifecycle(initialValue = emptyList())
    var open by remember { mutableStateOf(false) }

    SectionCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Recent activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Hint(if (open) "Hide" else "Show (${events.size})")
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (events.isEmpty()) Hint("No events yet.")
                events.forEach { e ->
                    Row {
                        Hint(formatClock(context, e.time), Modifier.width(56.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${e.sender} · ${AppLabels.label(context, e.pkg)}", maxLines = 1)
                            Text(
                                e.decision.label + if (e.detail.isNotEmpty()) " — ${e.detail}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (e.decision == Decision.ALERTED) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (events.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { repo.clearLog() } }) { Text("Clear") }
                }
            }
        }
    }
}

private fun today(): Int = Calendar.getInstance().run {
    get(Calendar.YEAR) * 10000 + (get(Calendar.MONTH) + 1) * 100 + get(Calendar.DAY_OF_MONTH)
}
