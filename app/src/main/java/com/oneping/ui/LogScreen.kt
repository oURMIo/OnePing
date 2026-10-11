package com.oneping.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneping.data.Decision
import com.oneping.data.Repository
import kotlinx.coroutines.launch

@Composable
fun LogScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val scope = rememberCoroutineScope()
    val events by repo.log.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Last ${Repository.LOG_SIZE} events",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { scope.launch { repo.clearLog() } }, enabled = events.isNotEmpty()) {
                Text("Clear")
            }
        }
        if (events.isEmpty()) {
            Text(
                "No events yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn {
            items(events) { e ->
                val color = when (e.decision) {
                    Decision.ALERTED -> Color(0xFF2E7D32)
                    Decision.RESET -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                ListItem(
                    overlineContent = { Text(formatTime(context, e.time)) },
                    headlineContent = { Text("${e.sender} · ${AppLabels.label(context, e.pkg)}") },
                    supportingContent = {
                        Text(
                            e.decision.label + if (e.detail.isNotEmpty()) " — ${e.detail}" else "",
                            color = color,
                        )
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
