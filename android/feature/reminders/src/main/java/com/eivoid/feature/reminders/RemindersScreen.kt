package com.eivoid.feature.reminders

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.core.database.ReminderEntity
import com.eivoid.designsystem.component.VoidCard
import com.eivoid.designsystem.component.VoidEmptyState
import com.eivoid.designsystem.component.VoidSectionTitle
import com.eivoid.designsystem.component.VoidStatusChip
import com.eivoid.designsystem.component.VoidTopBar
import com.eivoid.designsystem.component.reminderAccent
import com.eivoid.designsystem.component.reminderAmber

/**
 * The full payment log: overdue and upcoming active rows, then paid history.
 * A row opens the editor; its inline actions are mark-paid, snooze and delete.
 */
@Composable
fun RemindersScreen(
    onAddReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    val accent = reminderAccent()
    val amber = reminderAmber()
    val now = System.currentTimeMillis()
    val dayStart = ReminderFormat.startOfDay(now)
    val active = state.active
    val overdue = active.filter { it.dueAt < dayStart }
    val upcoming = active.filter { it.dueAt >= dayStart }
    val paid = state.paid

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            VoidTopBar(
                title = "Reminders",
                subtitle = "${active.size} upcoming · ${paid.size} paid",
                actions = {
                    IconButton(
                        onClick = viewModel::syncNow,
                        enabled = !state.busy,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Sync reminders",
                            tint = if (state.busy) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                        )
                    }
                },
            )

            state.message?.let { notice ->
                MessageBanner(text = notice, onDismiss = viewModel::dismissMessage)
            }

            when {
                state.loading && state.reminders.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Loading reminders…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.reminders.isEmpty() -> VoidEmptyState(
                    icon = Icons.Filled.Notifications,
                    title = "Nothing due",
                    message = "Add your first bill, rent or subscription and Ei Void will " +
                        "remind you before it is due.",
                    actionLabel = "Add a reminder",
                    onAction = onAddReminder,
                    accent = accent,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 6.dp,
                        bottom = 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (overdue.isNotEmpty()) {
                        item(key = "overdue-title") {
                            VoidSectionTitle(
                                "Overdue",
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(overdue, key = { it.id }) { reminder ->
                            ReminderRow(
                                reminder = reminder,
                                dueColor = MaterialTheme.colorScheme.error,
                                onEdit = { onEditReminder(reminder.id) },
                                onMarkPaid = { viewModel.markPaid(reminder.id) },
                                onSnooze = { viewModel.snooze(reminder.id) },
                                onDelete = { viewModel.delete(reminder.id) },
                            )
                        }
                    }

                    item(key = "upcoming-title") {
                        VoidSectionTitle(
                            "Upcoming",
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (upcoming.isEmpty()) {
                        item(key = "caught-up") {
                            VoidCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "You're all caught up.",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "New reminders appear here in due-date order.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(upcoming, key = { it.id }) { reminder ->
                            ReminderRow(
                                reminder = reminder,
                                dueColor = if (reminder.dueAt < dayStart + DAY_MILLIS) {
                                    amber
                                } else {
                                    accent
                                },
                                onEdit = { onEditReminder(reminder.id) },
                                onMarkPaid = { viewModel.markPaid(reminder.id) },
                                onSnooze = { viewModel.snooze(reminder.id) },
                                onDelete = { viewModel.delete(reminder.id) },
                            )
                        }
                    }

                    if (paid.isNotEmpty()) {
                        item(key = "paid-title") {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VoidSectionTitle("Paid", modifier = Modifier.weight(1f))
                                TextButton(onClick = { confirmClear = true }) {
                                    Text(
                                        text = "Clear all",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        items(paid, key = { it.id }) { reminder ->
                            PaidRow(
                                reminder = reminder,
                                onEdit = { onEditReminder(reminder.id) },
                                onDelete = { viewModel.delete(reminder.id) },
                            )
                        }
                    }

                    item(key = "hint") {
                        Text(
                            text = "Mark a bill paid to stop its alarm. Repeating reminders " +
                                "schedule the next occurrence automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddReminder,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            containerColor = accent,
            contentColor = Color.Black,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add reminder")
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete every reminder?") },
            text = {
                Text(
                    "This removes all active, upcoming and paid reminders from this phone " +
                        "and cancels their alarms.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.deleteAll()
                    },
                ) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ReminderRow(
    reminder: ReminderEntity,
    dueColor: Color,
    onEdit: () -> Unit,
    onMarkPaid: () -> Unit,
    onSnooze: () -> Unit,
    onDelete: () -> Unit,
) {
    VoidCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(reminder.merchant)
                        if (reminder.bank.isNotBlank()) append(" · ${reminder.bank}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VoidStatusChip(
                        text = ReminderFormat.relativeDue(reminder.dueAt),
                        color = dueColor,
                    )
                    VoidStatusChip(
                        text = reminder.category.label,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    if (reminder.repeatRule.name != "NONE") {
                        VoidStatusChip(
                            text = reminder.repeatRule.label,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ReminderFormat.money(reminder.amountMinor, reminder.currency),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onMarkPaid) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Paid")
                    }
                    TextButton(onClick = onSnooze) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("+1d")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete ${reminder.title}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaidRow(
    reminder: ReminderEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    VoidCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${ReminderFormat.shortDate(reminder.dueAt)} · ${reminder.merchant}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = ReminderFormat.money(reminder.amountMinor, reminder.currency),
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete ${reminder.title}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MessageBanner(text: String, onDismiss: () -> Unit) {
    VoidCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

private const val DAY_MILLIS = 86_400_000L
