package com.eivoid.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.core.ai.Tier
import com.eivoid.designsystem.component.VoidCard
import com.eivoid.designsystem.component.VoidDivider
import com.eivoid.designsystem.component.VoidSectionTitle
import com.eivoid.designsystem.component.VoidTonalButton
import com.eivoid.designsystem.component.VoidTopBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    onOpenUsage: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    reminderPrefs: ReminderPreferencesViewModel = hiltViewModel(),
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val preferredTier by viewModel.preferredTier.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val reminderSettings by reminderPrefs.settings.collectAsStateWithLifecycle()

    var showProviderKeys by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        VoidTopBar(
            title = "Settings",
            subtitle = "Make Ei Void yours",
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppearanceCard(selected = theme, onSelect = viewModel::setTheme)

            TierCard(
                plan = plan,
                preferredTier = preferredTier,
                onSelect = viewModel::setPreferredTier,
                onOpenUsage = onOpenUsage,
            )

            NotificationSettingsCard(
                settings = reminderSettings,
                onLeadDays = reminderPrefs::setLeadDays,
                onNotifyTime = reminderPrefs::setNotifyTime,
                onEmailEnabled = reminderPrefs::setEmailEnabled,
            )

            VoidCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenUsage,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Plan & usage", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${plan.displayName} · ${plan.priceLabel}" +
                                " · ${plan.maxProjects} projects",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            VoidCard(modifier = Modifier.fillMaxWidth()) {
                VoidSectionTitle("Provider keys")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Keys are stored encrypted on this device and never leave it " +
                        "except to the provider you call.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                VoidTonalButton(
                    text = if (showProviderKeys) "Hide keys" else "Manage keys",
                    onClick = { showProviderKeys = !showProviderKeys },
                )
                if (showProviderKeys) {
                    Spacer(Modifier.height(16.dp))
                    ProviderKeysSection(viewModel)
                }
            }

            SignOutCard(onClick = { showSignOutDialog = true })

            DiscordConnectionCard()

            PermissionStatusCard()

            DeleteDataCard(
                email = "",
                onDelete = {
                    reminderPrefs.deleteAllReminders()
                    viewModel.signOut()
                    onLoggedOut()
                },
            )
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "This removes your GitHub token and provider keys from this phone, " +
                        "deletes the cloned repositories, and clears the conversations " +
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
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceCard(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    VoidCard(modifier = Modifier.fillMaxWidth()) {
        VoidSectionTitle("Appearance")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Follow the system, or pin Ei Void to the light or the dark.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    shape = RoundedCornerShape(50),
                    label = {
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierCard(
    plan: com.eivoid.core.ai.plan.Plan,
    preferredTier: Tier?,
    onSelect: (Tier?) -> Unit,
    onOpenUsage: () -> Unit,
) {
    VoidCard(modifier = Modifier.fillMaxWidth()) {
        VoidSectionTitle("Model tier")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ei Void hides the provider's model names. Pick a tier, or let the " +
                "router choose one from the task.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = preferredTier == null,
                onClick = { onSelect(null) },
                shape = RoundedCornerShape(50),
                label = { Text("Auto") },
            )
            Tier.entries.forEach { tier ->
                FilterChip(
                    selected = preferredTier == tier,
                    onClick = { onSelect(tier) },
                    enabled = plan.allows(tier),
                    shape = RoundedCornerShape(50),
                    label = { Text(tier.displayName) },
                )
            }
        }

        val lockedTiers = Tier.entries.filter { !plan.allows(it) }
        if (lockedTiers.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Not in your ${plan.displayName} plan: " +
                    lockedTiers.joinToString { it.displayName } + ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOpenUsage) {
                Text("Compare plans")
            }
        }

        Spacer(Modifier.height(8.dp))
        VoidDivider()
        Spacer(Modifier.height(12.dp))

        Tier.entries.forEachIndexed { index, tier ->
            val dotColor = tier.markerColor()
            Row(verticalAlignment = Alignment.Top) {
                Spacer(
                    Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = tier.displayName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = tierDescription(tier),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (index != Tier.entries.lastIndex) Spacer(Modifier.height(12.dp))
        }
    }
}

private fun tierDescription(tier: Tier): String = when (tier) {
    Tier.RED_DWARF -> "Strongest reasoning. Scaffolding and broad changes."
    Tier.WHITE_DWARF -> "Balanced. Reviews and refactors."
    Tier.NEUTRON_STAR -> "Smallest and fastest. Explanations and small edits."
}

@Composable
private fun SignOutCard(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text("Sign out", style = MaterialTheme.typography.titleSmall)
    }
}
