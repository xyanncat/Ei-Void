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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.core.database.ReminderEntity
import com.eivoid.designsystem.component.VoidAccentButton
import com.eivoid.designsystem.component.VoidAvatar
import com.eivoid.designsystem.component.VoidCard
import com.eivoid.designsystem.component.VoidSectionTitle
import com.eivoid.designsystem.component.VoidStatTile
import com.eivoid.designsystem.component.VoidStatusChip
import com.eivoid.designsystem.component.VoidTopBar
import com.eivoid.designsystem.component.reminderAccent
import com.eivoid.designsystem.component.reminderAmber

/**
 * The reminders home: greeting, the next payment, this month's totals and the
 * two shortcuts the user needs every day (all reminders, the Code tab).
 */
@Composable
fun HomeScreen(
    userName: String,
    onAddReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    onOpenReminders: () -> Unit,
    onOpenCode: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    val now = System.currentTimeMillis()
    val totals = ReminderFormat.monthTotals(state.reminders, now)
    val active = state.active
    val paid = state.paid
    val accent = reminderAccent()
    val amber = reminderAmber()

    Column(modifier = Modifier.fillMaxSize()) {
        VoidTopBar(
            title = "Home",
            subtitle = "${ReminderFormat.greeting()} · ${ReminderFormat.todayLong()}",
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { GreetingCard(userName = userName) }

            if (!state.permissions.canPostNotifications) {
                item {
                    PermissionBanner(text = state.permissions.summary)
                }
            }

            item {
                NextReminderCard(
                    reminder = active.firstOrNull(),
                    accent = amber,
                    onAdd = onAddReminder,
                    onClick = { reminder -> onEditReminder(reminder.id) },
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VoidCard(
                        modifier = Modifier.weight(1.25f),
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .padding(0.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DateRange,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "This month",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = ReminderFormat.moneyCompact(totals.inrMinor, "INR"),
                            style = MaterialTheme.typography.titleLarge,
                            color = accent,
                            maxLines = 1,
                        )
                        Text(
                            text = ReminderFormat.moneyCompact(totals.usdMinor, "USD"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    VoidStatTile(
                        label = "Paid",
                        value = paid.size.toString(),
                        icon = Icons.Filled.Check,
                        tint = accent,
                        modifier = Modifier.weight(1f),
                    )
                    VoidStatTile(
                        label = "Upcoming",
                        value = active.size.toString(),
                        icon = Icons.Filled.Notifications,
                        tint = amber,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                UpdatePlaceholderCard()
            }

            item {
                CodeShortcutCard(onOpenCode = onOpenCode)
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VoidSectionTitle("Upcoming", modifier = Modifier.weight(1f))
                    Text(
                        text = "See all  ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }

            if (active.isEmpty()) {
                item {
                    VoidCard(modifier = Modifier.fillMaxWidth(), onClick = onAddReminder) {
                        Text(
                            text = "No payments tracked yet.",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Add a bill, rent or subscription and Ei Void will remind you " +
                                "before it is due.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                active.take(3).forEach { reminder ->
                    item(key = "home-${reminder.id}") {
                        HomeReminderRow(
                            reminder = reminder,
                            onClick = { onEditReminder(reminder.id) },
                        )
                    }
                }
            }

            item {
                Box(Modifier.fillMaxWidth()) {
                    VoidAccentButton(
                        text = "All reminders",
                        onClick = onOpenReminders,
                        accent = accent,
                        leadingIcon = Icons.AutoMirrored.Outlined.List,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GreetingCard(userName: String) {
    VoidCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoidAvatar(
                text = userName.ifBlank { "V" },
                size = 48.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hello, ${userName.ifBlank { "there" }}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Your payments, in one orbit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextReminderCard(
    reminder: ReminderEntity?,
    accent: Color,
    onAdd: () -> Unit,
    onClick: (ReminderEntity) -> Unit,
) {
    VoidCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (reminder != null) ({ onClick(reminder) }) else onAdd,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .padding(0.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(9.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Next reminder",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (reminder != null) {
                VoidStatusChip(
                    text = ReminderFormat.relativeDue(reminder.dueAt),
                    color = accent,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (reminder == null) {
            Text(
                text = "Nothing scheduled",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap to add the first payment reminder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${ReminderFormat.weekdayDate(reminder.dueAt)} · ${reminder.merchant}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ReminderFormat.money(reminder.amountMinor, reminder.currency),
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Edit  ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeReminderRow(reminder: ReminderEntity, onClick: () -> Unit) {
    VoidCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
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
                    text = "${ReminderFormat.relativeDue(reminder.dueAt)} · ${reminder.merchant}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = ReminderFormat.money(reminder.amountMinor, reminder.currency),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionBanner(text: String) {
    VoidCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$text. Open Settings › Permissions to fix it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun UpdatePlaceholderCard() {
    VoidCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("App updates", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "You are on the latest build. In-app updates arrive in a later release.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VoidStatusChip(
                text = "CURRENT",
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun CodeShortcutCard(onOpenCode: () -> Unit) {
    VoidCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenCode,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Code", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Open your GitHub repositories and workspaces.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
