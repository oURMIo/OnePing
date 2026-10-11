package com.oneping.ui

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import kotlin.math.roundToInt

/** Quick choices for the quiet period, in seconds. */
private val PRESETS = listOf(60, 120, 300, 900, 1800)

/** Slider stops for "Other…", 30 s – 60 min. */
private val STEPS = listOf(30, 45, 60, 90, 120, 180, 240, 300, 420, 600, 900, 1200, 1800, 2400, 3000, 3600)

fun formatDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return when {
        m == 0 -> "$s s"
        s == 0 -> "$m min"
        else -> "$m min $s s"
    }
}

fun formatTime(context: Context, millis: Long): String =
    DateUtils.formatDateTime(
        context, millis,
        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
    )

/** "16:05" / "4:05 PM", following the phone's 12/24 h setting. */
fun formatClock(context: Context, millis: Long): String =
    DateFormat.getTimeFormat(context).format(millis)

/** Preset chips plus "Other…" (slider dialog) for picking a quiet period. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuietPeriodChips(valueSec: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PRESETS.forEach { sec ->
            FilterChip(
                selected = valueSec == sec,
                onClick = { onChange(sec) },
                label = { Text(formatDuration(sec)) },
            )
        }
        val custom = valueSec !in PRESETS
        FilterChip(
            selected = custom,
            onClick = { showDialog = true },
            label = { Text(if (custom) formatDuration(valueSec) else "Other…") },
        )
    }
    if (showDialog) {
        CustomPeriodDialog(valueSec, onDismiss = { showDialog = false }) {
            onChange(it)
            showDialog = false
        }
    }
}

@Composable
private fun CustomPeriodDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var pos by remember { mutableFloatStateOf(STEPS.indices.minBy { abs(STEPS[it] - initial) }.toFloat()) }
    val value = STEPS[pos.roundToInt()]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quiet period") },
        text = {
            Column {
                Text(formatDuration(value), style = MaterialTheme.typography.headlineMedium)
                Slider(
                    value = pos,
                    onValueChange = { pos = it },
                    valueRange = 0f..(STEPS.size - 1).toFloat(),
                    steps = STEPS.size - 2,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(value) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

/** Round icon of an installed app (or a letter placeholder). */
@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    val bitmap = remember(pkg) { AppLabels.icon(context, pkg)?.toBitmap(128, 128)?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, Modifier.size(size).clip(CircleShape))
    } else {
        Surface(Modifier.size(size), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(pkg.substringAfterLast('.').take(1).uppercase())
            }
        }
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
