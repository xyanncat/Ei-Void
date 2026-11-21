package com.eivoid.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.eivoid.designsystem.component.EiCard
import com.eivoid.designsystem.component.EiDivider
import com.eivoid.designsystem.component.EiStatusPill
import com.eivoid.designsystem.theme.VoidColors

/** A tool call: the name, a status, and the output on demand. */
@Composable
fun ToolCallCard(entry: ChatEntry.Tool, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    EiCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = VoidColors.oledTextPrimary,
            )
            when {
                entry.running -> EiStatusPill("running", color = VoidColors.oledTextSecondary)
                entry.ok -> EiStatusPill("done", color = VoidColors.oledSuccess)
                else -> EiStatusPill("failed", color = VoidColors.oledDanger)
            }
        }

        if (expanded) {
            EiDivider()
            if (looksLikeDiff(entry.detail)) {
                DiffCard(entry.detail, Modifier.padding(12.dp))
            } else {
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = VoidColors.oledTextSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
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
                line.startsWith("+++") || line.startsWith("---") -> VoidColors.oledTextSecondary
                line.startsWith("+") -> VoidColors.oledSuccess
                line.startsWith("-") -> VoidColors.oledDanger
                line.startsWith("@@") -> VoidColors.oledAccentBlue
                else -> VoidColors.oledTextPrimary
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
