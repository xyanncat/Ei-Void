package com.eivoid.feature.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.core.ai.Tier
import com.eivoid.core.ai.plan.Plan
import com.eivoid.core.reminders.ReminderSettings
import com.eivoid.designsystem.component.EiCard
import com.eivoid.designsystem.component.EiDimens
import com.eivoid.designsystem.component.EiDivider
import com.eivoid.designsystem.component.EiListRow
import com.eivoid.designsystem.component.EiNotice
import com.eivoid.designsystem.component.EiPill
import com.eivoid.designsystem.component.EiScreen
import com.eivoid.designsystem.component.EiSectionLabel
import com.eivoid.designsystem.component.EiStat
import com.eivoid.designsystem.component.EiStatusPill
import com.eivoid.designsystem.component.EiTopBar
import com.eivoid.designsystem.theme.VoidColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CUSTOMIZE_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val LEAD_DAY_OPTIONS = listOf(1, 2, 4, 7)

/**
 * The Customize surface, rebuilt on the OLED design kit.
 *
 * It keeps the existing [SettingsViewModel] and [ReminderPreferencesViewModel]
 * behaviour: every toggle, pill and dialog writes through the same stores as
 * before. Provider keys and usage have their own screens, reached from the
 * rows below; the internal flag keeps this screen's signature free of a
 * navigation concern.
 */
@Composable
fun CustomizeScreen(
    onOpenDrawer: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenUsage: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    reminderPrefs: ReminderPreferencesViewModel = hiltViewModel(),
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val preferredTier by viewModel.preferredTier.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val reminders by reminderPrefs.settings.collectAsStateWithLifecycle()
    val discord by viewModel.discord.collectAsStateWithLifecycle()

    var showProviderKeys by rememberSaveable { mutableStateOf(false) }
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    if (showProviderKeys) {
        ProviderKeysScreen(
            onBack = { showProviderKeys = false },
            viewModel = viewModel,
        )
        return
    }

    val savedKeys = SettingsViewModel.PROVIDERS.count { viewModel.hasProviderKey(it.id) }

    EiScreen(modifier = modifier) {
        EiTopBar(
            title = "Customize",
            subtitle = "Ei Void and Ei Snore",
            onMenu = onOpenDrawer,
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { EiSectionLabel("EI VOID") }

            item { IdentityCard(plan = plan) }

            item {
                PlanUsageCard(
                    plan = plan,
                    onOpenUsage = onOpenUsage,
                )
            }

            item {
                ModelTierCard(
                    plan = plan,
                    preferredTier = preferredTier,
                    onSelect = viewModel::setPreferredTier,
                    onOpenUsage = onOpenUsage,
                )
            }

            item {
                ProviderKeysCard(
                    savedCount = savedKeys,
                    total = SettingsViewModel.PROVIDERS.size,
                    onClick = { showProviderKeys = true },
                )
            }

            item { EiSectionLabel("EI SNORE", Modifier.padding(top = 10.dp)) }

            item {
                ReminderCard(
                    settings = reminders,
                    onLeadDays = reminderPrefs::setLeadDays,
                    onNotifyTime = reminderPrefs::setNotifyTime,
                    onEmailEnabled = reminderPrefs::setEmailEnabled,
                )
            }

            item {
                DiscordCard(
                    state = discord,
                    onConnect = viewModel::connectDiscord,
                    onDisconnect = viewModel::disconnectDiscord,
                    onTest = viewModel::testDiscord,
                    onDismiss = viewModel::dismissDiscordMessage,
                )
            }

            item { PermissionsCard() }

            item { EiSectionLabel("APP", Modifier.padding(top = 10.dp)) }

            item {
                AppearanceCard(
                    selected = theme,
                    onSelect = viewModel::setTheme,
                )
            }

            item { SignOutCard(onClick = { showSignOutDialog = true }) }

            item { DeleteDataCard(onClick = { showDeleteDialog = true }) }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            containerColor = VoidColors.oledSurface,
            titleContentColor = VoidColors.oledTextPrimary,
            textContentColor = VoidColors.oledTextSecondary,
            shape = RoundedCornerShape(EiDimens.cardRadius),
            title = { Text("Sign out?") },
            text = {
                Text(
                    "This removes your GitHub token and provider keys from this phone, " +
                        "deletes the cloned repositories, and clears your conversations " +
                        "and usage records.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut()
                        onLoggedOut()
                    },
                ) {
                    Text("Sign out", color = VoidColors.oledDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel", color = VoidColors.oledTextSecondary)
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = VoidColors.oledSurface,
            titleContentColor = VoidColors.oledTextPrimary,
            textContentColor = VoidColors.oledTextSecondary,
            shape = RoundedCornerShape(EiDimens.cardRadius),
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "This deletes every reminder, cancels its alarm, clears the cached " +
                        "repositories, conversations and usage records, and signs you out. " +
                        "It cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        reminderPrefs.deleteAllReminders()
                        viewModel.signOut()
                        onLoggedOut()
                    },
                ) {
                    Text("Delete data", color = VoidColors.oledDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = VoidColors.oledTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun IdentityCard(plan: Plan) {
    EiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(VoidColors.oledAccentBlue),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "EV",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VoidColors.white,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ei Void",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = VoidColors.oledTextPrimary,
                )
                Text(
                    text = "Clerk account",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                )
            }
            EiStatusPill(
                text = plan.displayName,
                color = VoidColors.oledAccentBlue,
            )
        }

        Spacer(Modifier.height(16.dp))
        EiDivider()
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            EiStat(value = plan.displayName, label = "current plan")
            EiStat(value = plan.maxProjects.toString(), label = "projects")
            EiStat(
                value = UsageViewModel.formatTokens(plan.monthlyTokens),
                label = "tokens / month",
            )
        }
    }
}

@Composable
private fun PlanUsageCard(plan: Plan, onOpenUsage: () -> Unit) {
    EiCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenUsage,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    ) {
        EiListRow(
            title = "Plan & usage",
            subtitle = "${plan.displayName} · ${plan.priceLabel} · " +
                "${plan.maxProjects} projects",
            leading = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = VoidColors.oledAccentBlue,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailing = {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VoidColors.oledTextSecondary,
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelTierCard(
    plan: Plan,
    preferredTier: Tier?,
    onSelect: (Tier?) -> Unit,
    onOpenUsage: () -> Unit,
) {
    val activeTier = preferredTier
    val lockedTiers = Tier.entries.filterNot(plan::allows)

    EiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Model tier",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoidColors.oledTextPrimary,
                )
                Text(
                    text = "Provider names stay hidden. Pick a tier, or let the router " +
                        "choose one from the task.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                )
            }
            Spacer(Modifier.width(10.dp))
            EiStatusPill(
                text = activeTier?.displayName ?: "Auto",
                color = VoidColors.oledAccentBlue,
            )
        }

        Spacer(Modifier.height(14.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EiPill(
                text = "Auto",
                selected = activeTier == null,
                onClick = { onSelect(null) },
            )
            Tier.entries.forEach { tier ->
                val allowed = plan.allows(tier)
                EiPill(
                    text = tier.displayName,
                    selected = activeTier == tier,
                    onClick = if (allowed) {
                        { onSelect(tier) }
                    } else {
                        null
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = if (activeTier == null) {
                "The router picks the smallest tier that fits each task."
            } else {
                tierBlurb(activeTier)
            },
            style = MaterialTheme.typography.bodySmall,
            color = VoidColors.oledTextSecondary,
        )

        if (lockedTiers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            EiNotice(
                message = "Not in ${plan.displayName}: " +
                    lockedTiers.joinToString { it.displayName } + ".",
                actionLabel = "Plans",
                onAction = onOpenUsage,
            )
        }
    }
}

private fun tierBlurb(tier: Tier): String = when (tier) {
    Tier.RED_DWARF -> "Deepest reasoning for scaffolding and broad changes."
    Tier.WHITE_DWARF -> "Balanced reviews, refactors and everyday edits."
    Tier.NEUTRON_STAR -> "Fast answers and small, precise changes."
}

@Composable
private fun ProviderKeysCard(savedCount: Int, total: Int, onClick: () -> Unit) {
    EiCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    ) {
        EiListRow(
            title = "Provider keys",
            subtitle = "$savedCount of $total saved · stored on this device",
            leading = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = VoidColors.oledSuccess,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailing = {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VoidColors.oledTextSecondary,
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReminderCard(
    settings: ReminderSettings,
    onLeadDays: (Int) -> Unit,
    onNotifyTime: (Int, Int) -> Unit,
    onEmailEnabled: (Boolean) -> Unit,
) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    EiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoidColors.oledTextPrimary,
                )
                Text(
                    text = "Ei Snore announces a reminder before it is due.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                )
            }
            Spacer(Modifier.width(10.dp))
            EiStatusPill(
                text = "Ei Snore",
                color = VoidColors.oledAccentAmber,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "DEFAULT LEAD TIME",
            style = MaterialTheme.typography.labelSmall,
            color = VoidColors.oledTextSecondary,
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LEAD_DAY_OPTIONS.forEach { days ->
                EiPill(
                    text = if (days == 1) "1 day" else "$days days",
                    selected = settings.defaultLeadDays == days,
                    onClick = { onLeadDays(days) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        EiDivider()

        EiListRow(
            title = "Reminder time",
            subtitle = LocalTime.of(settings.notifyHour, settings.notifyMinute)
                .format(CUSTOMIZE_TIME_FORMAT),
            leading = {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = VoidColors.oledAccentAmber,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailing = {
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = VoidColors.oledAccentBlue,
                    modifier = Modifier.padding(end = 4.dp),
                )
            },
            onClick = { showTimePicker = true },
        )

        EiDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Email reminders",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = VoidColors.oledTextPrimary,
                )
                Text(
                    text = "Also send a copy to the email on your Clerk account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = settings.emailRemindersEnabled,
                onCheckedChange = onEmailEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VoidColors.white,
                    checkedTrackColor = VoidColors.oledAccentBlue,
                    uncheckedThumbColor = VoidColors.oledTextSecondary,
                    uncheckedTrackColor = VoidColors.oledSurfaceHigh,
                    uncheckedBorderColor = VoidColors.oledBorder,
                ),
            )
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = settings.notifyHour,
            initialMinute = settings.notifyMinute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = VoidColors.oledSurface,
            titleContentColor = VoidColors.oledTextPrimary,
            textContentColor = VoidColors.oledTextSecondary,
            shape = RoundedCornerShape(EiDimens.cardRadius),
            title = { Text("Reminder time") },
            text = { TimeInput(state = state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onNotifyTime(state.hour, state.minute)
                        showTimePicker = false
                    },
                ) {
                    Text("Set", color = VoidColors.oledAccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel", color = VoidColors.oledTextSecondary)
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscordCard(
    state: DiscordUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val linked = state.linked

    EiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Discord",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoidColors.oledTextPrimary,
                )
                Text(
                    text = "Reminder DMs and channel fallbacks for Ei Snore. This is not " +
                        "the app login.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                )
            }
            Spacer(Modifier.width(10.dp))
            EiStatusPill(
                text = if (linked) "Linked" else "Not linked",
                color = if (linked) VoidColors.oledSuccess else VoidColors.oledTextSecondary,
            )
        }

        Spacer(Modifier.height(12.dp))
        EiNotice(
            message = "Integration stub. Discord OAuth is not wired in this build yet.",
        )
        Spacer(Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (linked) {
                EiPill(
                    text = "Disconnect",
                    selected = false,
                    onClick = onDisconnect,
                )
            } else {
                EiPill(
                    text = "Connect",
                    selected = true,
                    onClick = onConnect,
                )
            }
            EiPill(
                text = "Send test",
                selected = false,
                onClick = onTest,
            )
        }

        if (state.message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            EiNotice(
                message = state.message,
                actionLabel = "Dismiss",
                onAction = onDismiss,
            )
        }
    }
}

@Composable
private fun PermissionsCard() {
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
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
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
    val notificationsReady = permissionGranted && notificationsEnabled

    EiCard {
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = VoidColors.oledTextPrimary,
        )
        Text(
            text = "Notifications need the runtime permission, the system switch and " +
                "an unmuted channel. Exact alarms keep the time accurate.",
            style = MaterialTheme.typography.bodySmall,
            color = VoidColors.oledTextSecondary,
        )

        Spacer(Modifier.height(10.dp))
        EiDivider()

        EiListRow(
            title = "Notifications",
            subtitle = if (notificationsReady) {
                "Granted and enabled"
            } else {
                "Open the system notification settings"
            },
            trailing = {
                EiStatusPill(
                    text = if (notificationsReady) "On" else "Off",
                    color = if (notificationsReady) {
                        VoidColors.oledSuccess
                    } else {
                        VoidColors.oledDanger
                    },
                )
            },
            onClick = { openNotificationSettings(context) },
        )

        EiDivider()

        EiListRow(
            title = "Exact alarms",
            subtitle = if (exactAlarms) {
                "Reminders fire at the chosen time"
            } else {
                "Blocked by the system; reminders may be delayed"
            },
            trailing = {
                EiStatusPill(
                    text = if (exactAlarms) "Allowed" else "Blocked",
                    color = if (exactAlarms) {
                        VoidColors.oledSuccess
                    } else {
                        VoidColors.oledDanger
                    },
                )
            },
            onClick = { openExactAlarmSettings(context) },
        )
    }
}

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
        )
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceCard(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    EiCard {
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = VoidColors.oledTextPrimary,
        )
        Text(
            text = "Follow the system, or pin the app to OLED black or paper.",
            style = MaterialTheme.typography.bodySmall,
            color = VoidColors.oledTextSecondary,
        )
        Spacer(Modifier.height(14.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                EiPill(
                    text = mode.label(),
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun SignOutCard(onClick: () -> Unit) {
    EiCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    ) {
        EiListRow(
            title = "Sign out",
            subtitle = "Remove your token, keys and local clones from this phone",
            leading = {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = VoidColors.oledDanger,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailing = {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VoidColors.oledDanger,
                )
            },
        )
    }
}

@Composable
private fun DeleteDataCard(onClick: () -> Unit) {
    EiCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    ) {
        EiListRow(
            title = "Delete data",
            subtitle = "Erase reminders, conversations and usage, then sign out",
            leading = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = VoidColors.oledDanger,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailing = {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VoidColors.oledDanger,
                )
            },
        )
    }
}
