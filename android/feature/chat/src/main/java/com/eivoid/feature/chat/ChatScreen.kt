package com.eivoid.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eivoid.designsystem.component.EiCard
import com.eivoid.designsystem.component.EiEmptyState
import com.eivoid.designsystem.component.EiNotice
import com.eivoid.designsystem.component.EiStatusPill
import com.eivoid.designsystem.theme.VoidColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var autoFollow by remember { mutableStateOf(true) }

    // Stop following the tail as soon as the user drags away; a new send
    // brings them back.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Stop || interaction is DragInteraction.Cancel) {
                autoFollow = !listState.canScrollForward
            }
        }
    }

    val tail = state.entries.lastOrNull()
    val tailKey = when (tail) {
        is ChatEntry.Assistant -> tail.text.length
        is ChatEntry.Tool -> tail.detail.length * 2 + if (tail.running) 1 else 0
        else -> 0
    }

    // Keyed on the tail content, not just the entry count, so streaming
    // tokens keep the newest line in view. The list ends with a zero-height
    // anchor so scrollToItem lands on the true end of the content.
    LaunchedEffect(state.entries.size, tailKey) {
        if (tail is ChatEntry.User) autoFollow = true
        if (autoFollow && state.entries.isNotEmpty()) {
            listState.scrollToItem(state.entries.size)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.tier?.let { tier ->
                EiStatusPill(
                    text = "Tier: $tier",
                    color = VoidColors.oledTextSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.isRunning) {
                Text(
                    text = "Running",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoidColors.oledAccentBlue,
                )
            }
        }

        if (state.isRunning) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = VoidColors.oledAccentBlue,
                trackColor = VoidColors.oledSurfaceHigh,
            )
        }

        if (state.entries.isEmpty()) {
            EmptyChat(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(state.entries, key = { index, _ -> index }) { _, entry ->
                    when (entry) {
                        is ChatEntry.User -> UserBubble(entry)
                        is ChatEntry.Assistant -> AssistantBubble(entry)
                        is ChatEntry.Tool -> ToolCallCard(entry)
                        is ChatEntry.Plan -> PlanCard(entry)
                        is ChatEntry.Failure -> FailureCard(entry)
                    }
                }
                item(key = "tail-anchor") { Spacer(Modifier.height(0.dp)) }
            }
        }

        Composer(
            value = state.input,
            enabled = !state.isRunning,
            isRunning = state.isRunning,
            onValueChange = viewModel::onInputChange,
            onSend = viewModel::send,
            onStop = viewModel::stop,
        )
    }

    state.pendingConfirmation?.let { request ->
        AlertDialog(
            onDismissRequest = viewModel::reject,
            title = { Text(request.title) },
            text = { Text(request.detail) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::approve,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (request.destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
                ) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::reject) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun UserBubble(entry: ChatEntry.User) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        SelectionContainer {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyLarge,
                color = VoidColors.oledTextPrimary,
                modifier = Modifier
                    .background(
                        color = VoidColors.oledSurfaceHigh,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun AssistantBubble(entry: ChatEntry.Assistant) {
    SelectionContainer {
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyLarge,
            color = VoidColors.oledTextPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlanCard(entry: ChatEntry.Plan) {
    EiCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Plan",
            style = MaterialTheme.typography.titleSmall,
            color = VoidColors.oledTextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        entry.steps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                style = MaterialTheme.typography.bodyMedium,
                color = VoidColors.oledTextSecondary,
            )
            if (index != entry.steps.lastIndex) Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun FailureCard(entry: ChatEntry.Failure) {
    EiNotice(
        message = entry.message,
        danger = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        EiEmptyState(
            title = "Ask Ei Void to change something",
            message = "It reads and edits files, runs git, and asks before it deletes anything.",
        )
    }
}

@Composable
private fun Composer(
    value: String,
    enabled: Boolean,
    isRunning: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(22.dp),
        color = VoidColors.oledSurface,
        border = BorderStroke(1.dp, VoidColors.oledBorder),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Describe the change…",
                        color = VoidColors.oledTextSecondary,
                    )
                },
                enabled = enabled,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = VoidColors.oledTextPrimary,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = VoidColors.oledAccentBlue,
                    focusedTextColor = VoidColors.oledTextPrimary,
                    unfocusedTextColor = VoidColors.oledTextPrimary,
                ),
            )
            FilledIconButton(
                onClick = if (isRunning) onStop else onSend,
                enabled = isRunning || value.isNotBlank(),
                colors = if (isRunning) {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = VoidColors.oledSurfaceHigh,
                        contentColor = VoidColors.oledTextPrimary,
                    )
                } else {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = VoidColors.oledAccentBlue,
                        contentColor = Color.White,
                    )
                },
            ) {
                if (isRunning) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop the agent")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
