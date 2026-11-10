package com.voidapp.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Keep the newest message in view while the model streams.
    LaunchedEffect(state.entries.size) {
        if (state.entries.isNotEmpty()) {
            listState.animateScrollToItem(state.entries.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        state.model?.let { model ->
            Text(
                text = "Model: $model",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        if (state.isRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (state.entries.isEmpty()) {
            EmptyChat(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(state.entries) { _, entry ->
                    when (entry) {
                        is ChatEntry.User -> UserBubble(entry)
                        is ChatEntry.Assistant -> AssistantBubble(entry)
                        is ChatEntry.Tool -> ToolCallCard(entry)
                        is ChatEntry.Plan -> PlanCard(entry)
                        is ChatEntry.Failure -> FailureCard(entry)
                    }
                }
            }
        }

        Composer(
            value = state.input,
            enabled = !state.isRunning,
            onValueChange = viewModel::onInputChange,
            onSend = viewModel::send,
        )
    }

    state.pendingConfirmation?.let { request ->
        AlertDialog(
            onDismissRequest = viewModel::reject,
            title = { Text(request.title) },
            text = { Text(request.detail) },
            confirmButton = {
                TextButton(onClick = viewModel::approve) { Text("Allow") }
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
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AssistantBubble(entry: ChatEntry.Assistant) {
    Text(
        text = entry.text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlanCard(entry: ChatEntry.Plan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Plan", style = MaterialTheme.typography.titleSmall)
            entry.steps.forEachIndexed { index, step ->
                Text(
                    text = "${index + 1}. $step",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun FailureCard(entry: ChatEntry.Failure) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Ask the agent to change something in this repository.",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "It reads and edits files, runs git, and asks before it deletes anything.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Composer(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Describe the change…") },
            enabled = enabled,
            maxLines = 5,
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}
