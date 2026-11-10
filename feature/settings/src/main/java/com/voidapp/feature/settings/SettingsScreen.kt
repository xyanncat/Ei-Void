package com.voidapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voidapp.core.ai.Tier

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val preferredTier by viewModel.preferredTier.collectAsStateWithLifecycle()

    var showProviderKeys by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)

        Section("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = theme == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }

        HorizontalDivider()

        Section("Model tier") {
            Text(
                text = "Void hides the provider's model names. Pick a tier, or let the " +
                    "router choose one from the task.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = preferredTier == null,
                    onClick = { viewModel.setPreferredTier(null) },
                    label = { Text("Auto") },
                )
                Tier.entries.forEach { tier ->
                    FilterChip(
                        selected = preferredTier == tier,
                        onClick = { viewModel.setPreferredTier(tier) },
                        label = { Text(tier.displayName) },
                    )
                }
            }
            TierSummary()
        }

        HorizontalDivider()

        Section("Provider keys") {
            Text(
                text = "Keys are stored encrypted on this device and never leave it except " +
                    "to the provider you call.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { showProviderKeys = !showProviderKeys }) {
                Text(if (showProviderKeys) "Hide keys" else "Manage keys")
            }
            if (showProviderKeys) {
                ProviderKeysSection(viewModel)
            }
        }

        HorizontalDivider()

        Button(
            onClick = {
                viewModel.signOut()
                onLoggedOut()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign out")
        }
    }
}

/** The three tiers, without naming a single provider model. */
@Composable
private fun TierSummary() {
    val descriptions = mapOf(
        Tier.RED_DWARF to "Strongest reasoning. Used for scaffolding and broad changes.",
        Tier.WHITE_DWARF to "Balanced. Used for reviews and refactors.",
        Tier.NEUTRON_STAR to "Smallest and fastest. Used for explanations and small edits.",
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Tier.entries.forEach { tier ->
            Column {
                Text(
                    text = tier.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = descriptions[tier].orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.Start),
        )
        content()
    }
}
