package com.oneping.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oneping.data.clampCooldown
import kotlin.math.abs
import kotlin.math.roundToInt

/** Slider stops between 30 s and 60 min; a text field allows any exact value in between. */
private val COOLDOWN_STEPS = listOf(
    30, 45, 60, 90, 120, 180, 240, 300, 420, 600, 900, 1200, 1800, 2400, 3000, 3600,
)

private fun nearestStep(sec: Int): Int =
    COOLDOWN_STEPS.indices.minBy { abs(COOLDOWN_STEPS[it] - sec) }

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

@Composable
fun CooldownPicker(valueSec: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var sliderPos by remember { mutableFloatStateOf(nearestStep(valueSec).toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(valueSec.toString()) }
    LaunchedEffect(valueSec) {
        if (!dragging) sliderPos = nearestStep(valueSec).toFloat()
        text = valueSec.toString()
    }
    val shown = if (dragging) COOLDOWN_STEPS[sliderPos.roundToInt()] else valueSec

    fun commitText() {
        text.toIntOrNull()?.let { onChange(it.clampCooldown()) } ?: run { text = valueSec.toString() }
    }

    Column(modifier) {
        Text(formatDuration(shown), style = MaterialTheme.typography.headlineSmall)
        Slider(
            value = sliderPos,
            onValueChange = { dragging = true; sliderPos = it },
            onValueChangeFinished = {
                dragging = false
                onChange(COOLDOWN_STEPS[sliderPos.roundToInt()])
            },
            valueRange = 0f..(COOLDOWN_STEPS.size - 1).toFloat(),
            steps = COOLDOWN_STEPS.size - 2,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(4) },
                label = { Text("Exact, seconds") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitText() }),
                modifier = Modifier
                    .width(160.dp)
                    .onFocusChanged { if (!it.isFocused) commitText() },
            )
            Text(
                "  30 – 3600 s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
