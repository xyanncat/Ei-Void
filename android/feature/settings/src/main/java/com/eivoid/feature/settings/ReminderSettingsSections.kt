package com.eivoid.feature.settings

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eivoid.core.reminders.ReminderSettings
import com.eivoid.designsystem.component.VoidCard
import com.eivoid.designsystem.component.VoidDangerCard
import com.eivoid.designsystem.component.VoidDivider
import com.eivoid.designsystem.component.VoidSectionTitle
import com.eivoid.designsystem.component.VoidSettingRow
import com.eivoid.designsystem.component.reminderAccent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NOTIFY_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/**
 * Notification preferences backed by :core:reminders' DataStore. Lead-day
 * chips also re-apply the default to rows the user never customised.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationSettingsCard(
    settings: ReminderSettings,
    onLeadDays: (Int) -> Unit,
    onNotifyTime: (Int, Int) -> Unit,
    onEmailEnabled: (Boolean) -> Unit,
) {
    var showTime by remember { mutableStateOf(false) }
    val accent = reminderAccent()

    VoidCard(modifier = Modifier.fillMaxWidth()) {
        VoidSectionTitle("Notifications")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Reminders are announced before the due date. Pick how early and at " +
                "what time of day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Default lead time",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LEAD_OPTIONS.forEach { days ->
                FilterChip(
                    selected = settings.defaultLeadDays == days,
                    onClick = { onLeadDays(days) },
                    shape = RoundedCornerShape(50),
                    label = {
                        Text(
                            when (days) {
                                0 -> "Same day"
                                1 -> "1 day"
                                else -> "$days days"
                            },
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        VoidDivider()
        VoidSettingRow(
            icon = Icons.Filled.Notifications,
            title = "Reminder time",
            subtitle = LocalTime.of(settings.notifyHour, settings.notifyMinute)
                .format(NOTIFY_TIME),
            onClick = { showTime = true },
            trailing = {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        VoidDivider()
        VoidSettingRow(
            icon = Icons.Filled.Email,
            title = "Email reminders",
            subtitle = "Also send the reminder to your Clerk email address",
            trailing = {
                Switch(
                    checked = settings.emailRemindersEnabled,
                    onCheckedChange = onEmailEnabled,
                )
            },
        )
    }

    if (showTime) {
        val state = rememberTimePickerState(
            initialHour = settings.notifyHour,
            initialMinute = settings.notifyMinute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Reminder time") },
            text = { TimeInput(state = state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onNotifyTime(state.hour, state.minute)
                        showTime = false
                    },
                ) {
                    Text("Set", color = accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Reads the three platform gates again every time the screen resumes, since
 * the user may have changed them in the system settings.
 */
@Composable
internal fun PermissionStatusCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionGranted = remember(tick) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val notificationsEnabled = remember(tick) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val exactAlarms = remember(tick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }
    }

    VoidCard(modifier = Modifier.fillMaxWidth()) {
        VoidSectionTitle("Permissions")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Notifications need the runtime permission, the system switch and an " +
                "un-muted channel. Exact alarms keep the time accurate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        VoidSettingRow(
            icon = Icons.Filled.Notifications,
            title = "Notification permission",
            subtitle = if (permissionGranted) "Granted" else "Not granted",
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        },
                    )
                }
            },
            trailing = { StatusDot(if (permissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) },
        )
        VoidDivider()
        VoidSettingRow(
            icon = Icons.Filled.Check,
            title = "System notifications",
            subtitle = if (notificationsEnabled) "Enabled" else "Muted for Ei Void",
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        },
                    )
                }
            },
            trailing = { StatusDot(if (notificationsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) },
        )
        VoidDivider()
        VoidSettingRow(
            icon = Icons.Filled.Check,
            title = "Exact alarms",
            subtitle = if (exactAlarms) "Allowed" else "Inexact alarms; may be delayed",
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarms) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            },
                        )
                    }
                }
            },
            trailing = { StatusDot(if (exactAlarms) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary) },
        )
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * Discord delivery is a server-side feature; the device only needs the OAuth
 * hop. The activity is deliberately not added yet, so the controls record a
 * clear status instead of pretending the link succeeded.
 *
 * TODO(discord): add DiscordAuthActivity with the client id and exchange the
 * returned token through the reminder backend, then persist the handle here.
 */
@Composable
internal fun DiscordConnectionCard() {
    var connected by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }

    VoidCard(modifier = Modifier.fillMaxWidth()) {
        VoidSectionTitle("Discord")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Connect Discord to receive reminder DMs. The OAuth hop is not wired in " +
                "this build yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        VoidSettingRow(
            icon = Icons.Filled.Lock,
            title = if (connected) "Discord connected" else "Discord connection",
            subtitle = if (connected) "Linked as @discord" else "Not linked",
            trailing = {
                TextButton(
                    onClick = {
                        if (connected) {
                            connected = false
                            status = "Discord disconnected."
                        } else {
                            status = "Discord OAuth is not configured yet (TODO: " +
                                "DiscordAuthActivity)."
                        }
                    },
                ) {
                    Text(if (connected) "Disconnect" else "Connect")
                }
            },
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { status = "Test DM queued (not yet available)." },
                enabled = connected,
            ) { Text("Test Discord") }
            TextButton(
                onClick = {
                    status = "Email reminders use the address on your Clerk account."
                },
            ) { Text("Email info") }
        }
        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
    }
}

/** The destructive escape hatch: local reminders plus the signed-in session. */
@Composable
internal fun DeleteDataCard(
    email: String,
    onDelete: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }

    VoidDangerCard {
        VoidSettingRow(
            icon = Icons.Filled.Delete,
            title = "Delete data",
            subtitle = "Remove every reminder on this phone and sign out" +
                if (email.isNotBlank()) " · $email" else "",
            onClick = { confirm = true },
            trailing = {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "This deletes every reminder, cancels its alarm, clears the cached " +
                        "GitHub repositories, conversations and usage records, and signs " +
                        "you out. It cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        onDelete()
                    },
                ) {
                    Text("Delete data", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            },
        )
    }
}

private val LEAD_OPTIONS = listOf(0, 1, 2, 3, 7)
