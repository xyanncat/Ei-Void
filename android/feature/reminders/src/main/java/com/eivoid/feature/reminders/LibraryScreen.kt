package com.eivoid.feature.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.core.database.ReminderEntity
import com.eivoid.designsystem.component.EiCard
import com.eivoid.designsystem.component.EiEmptyState
import com.eivoid.designsystem.component.EiIconButton
import com.eivoid.designsystem.component.EiNotice
import com.eivoid.designsystem.component.EiPrimaryButton
import com.eivoid.designsystem.component.EiScreen
import com.eivoid.designsystem.component.EiSegmented
import com.eivoid.designsystem.component.EiStat
import com.eivoid.designsystem.component.EiStatusPill
import com.eivoid.designsystem.component.EiTopBar
import com.eivoid.designsystem.theme.VoidColors
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private enum class LibraryFilter { UPCOMING, PAID }

/**
 * The reminder library: a calm, single-column OLED list of payments.
 *
 * Upcoming and paid rows live behind one segmented filter; each card opens the
 * editor, and its quiet action row settles the small chores (mark paid, push a
 * day, delete) without leaving the list. A full-width action sits above the
 * navigation bar inset so adding a reminder is always one thumb away.
 */
@Composable
fun LibraryScreen(
    onOpenDrawer: () -> Unit,
    onAddReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPaid by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    val now = System.currentTimeMillis()
    val filter = if (showPaid) LibraryFilter.PAID else LibraryFilter.UPCOMING
    val rows = if (showPaid) state.paid else state.active
    val totals = ReminderFormat.monthTotals(state.reminders, now)

    EiScreen(modifier = modifier) {
        EiTopBar(
            title = "Library",
            subtitle = "Reminders and saved tasks",
            onMenu = onOpenDrawer,
            actions = {
                EiIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Sync reminders",
                    onClick = viewModel::syncNow,
                    tint = if (state.busy) {
                        VoidColors.oledTextSecondary
                    } else {
                        VoidColors.oledTextPrimary
                    },
                )
            },
        )

        state.message?.let { notice ->
            EiNotice(
                message = notice,
                actionLabel = "Dismiss",
                onAction = viewModel::dismissMessage,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        when {
            state.loading && state.reminders.isEmpty() -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Loading reminders…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoidColors.oledTextSecondary,
                )
            }

            state.reminders.isEmpty() -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                EiEmptyState(
                    title = "No reminders yet",
                    message = "Add a bill, rent or subscription and Ei Void will remind " +
                        "you before it is due.",
                    icon = Icons.Filled.Notifications,
                )
            }

            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(4.dp))
                    EiSegmented(
                        options = LibraryFilter.entries,
                        selected = filter,
                        onSelect = { selected -> showPaid = selected == LibraryFilter.PAID },
                        label = { option ->
                            when (option) {
                                LibraryFilter.UPCOMING -> "Upcoming"
                                LibraryFilter.PAID -> "Paid"
                            }
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    LibraryStats(
                        totals = totals,
                        upcomingCount = state.active.size,
                        paidCount = state.paid.size,
                    )
                }

                Spacer(Modifier.height(14.dp))

                if (rows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EiEmptyState(
                            title = if (showPaid) "No paid reminders" else "You're all caught up",
                            message = if (showPaid) {
                                "Reminders you mark paid will be kept here."
                            } else {
                                "Nothing is due right now. Add a reminder to plan ahead."
                            },
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(rows, key = { reminder -> reminder.id }) { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                now = now,
                                onEdit = { onEditReminder(reminder.id) },
                                onMarkPaid = { viewModel.markPaid(reminder.id) },
                                onSnooze = { viewModel.snooze(reminder.id) },
                                onDelete = { viewModel.delete(reminder.id) },
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            EiPrimaryButton(text = "Add reminder", onClick = onAddReminder)
        }
    }
}

@Composable
private fun LibraryStats(
    totals: MonthTotals,
    upcomingCount: Int,
    paidCount: Int,
) {
    EiCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EiStat(
                value = ReminderFormat.moneyCompact(totals.inrMinor, "INR"),
                label = "Due this month",
                modifier = Modifier.weight(1f),
            )
            EiStat(
                value = upcomingCount.toString(),
                label = "Upcoming",
                modifier = Modifier.weight(1f),
            )
            EiStat(
                value = paidCount.toString(),
                label = "Paid",
                modifier = Modifier.weight(1f),
                accent = VoidColors.oledSuccess,
            )
        }
        if (totals.usdMinor > 0L) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Plus ${ReminderFormat.moneyCompact(totals.usdMinor, "USD")} due this month.",
                style = MaterialTheme.typography.bodySmall,
                color = VoidColors.oledTextSecondary,
            )
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: ReminderEntity,
    now: Long,
    onEdit: () -> Unit,
    onMarkPaid: () -> Unit,
    onSnooze: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusColor = when {
        reminder.isPaid -> VoidColors.oledSuccess
        dueDayOffset(reminder.dueAt, now) <= 1L -> VoidColors.oledAccentAmber
        else -> VoidColors.oledTextSecondary
    }
    val statusText = if (reminder.isPaid) {
        "Paid"
    } else {
        ReminderFormat.relativeDue(reminder.dueAt, now)
    }
    val secondary = if (reminder.isPaid) {
        "${reminder.merchant} · Paid ${ReminderFormat.shortDate(reminder.paidAt ?: reminder.dueAt)}"
    } else {
        "${reminder.merchant} · ${ReminderFormat.weekdayDate(reminder.dueAt)}"
    }

    EiCard(
        onClick = onEdit,
        contentPadding = PaddingValues(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoidColors.oledTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            EiStatusPill(text = statusText, color = statusColor)
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ReminderFormat.money(reminder.amountMinor, reminder.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VoidColors.oledTextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (!reminder.isPaid) {
                QuietAction(
                    text = "Mark paid",
                    contentDescription = "Mark ${reminder.title} paid",
                    onClick = onMarkPaid,
                )
                QuietAction(
                    text = "+1 day",
                    contentDescription = "Snooze ${reminder.title} by one day",
                    onClick = onSnooze,
                )
            }
            EiIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Delete ${reminder.title}",
                onClick = onDelete,
                tint = VoidColors.oledTextSecondary,
            )
        }
    }
}

@Composable
private fun QuietAction(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = contentDescription, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = VoidColors.oledTextSecondary,
        )
    }
}

private fun dueDayOffset(dueAt: Long, now: Long): Long {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val due = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(today, due)
}
