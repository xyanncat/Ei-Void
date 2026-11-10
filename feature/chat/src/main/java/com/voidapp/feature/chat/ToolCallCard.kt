package com.voidapp.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A tool call: the name, a status, and the output on demand. */
@Composable
fun ToolCallCard(entry: ChatEntry.Tool, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = when {
                        entry.running -> "running…"
                        entry.ok -> "done"
                        else -> "failed"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        entry.running -> MaterialTheme.colorScheme.onSurfaceVariant
                        entry.ok -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }

            if (expanded) {
                if (looksLikeDiff(entry.detail)) {
                    DiffCard(entry.detail, Modifier.padding(12.dp))
                } else {
                    Text(
                        text = entry.detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** A unified diff with coloured lines. */
@Composable
fun DiffCard(diff: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        diff.lines().forEach { line ->
            val color = when {
                line.startsWith("+++") || line.startsWith("---") ->
                    MaterialTheme.colorScheme.onSurfaceVariant
                line.startsWith("+") -> MaterialTheme.colorScheme.secondary
                line.startsWith("-") -> MaterialTheme.colorScheme.error
                line.startsWith("@@") -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                text = line.ifEmpty { " " },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (line.startsWith("@@")) FontWeight.Bold else FontWeight.Normal,
                color = color,
            )
        }
    }
}

internal fun looksLikeDiff(text: String): Boolean =
    text.contains("@@") && (text.contains("\n+") || text.contains("\n-"))
