package com.voidapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voidapp.core.ai.Tier
import com.voidapp.core.ai.plan.Plan

@Composable
fun UsageScreen(
    onBack: () -> Unit,
    viewModel: UsageViewModel = hiltViewModel(),
) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val tiers by viewModel.tiers.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text("Plan & usage", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${plan.displayName} · ${plan.priceLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Tokens used this month",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
            )

            tiers.forEach { tier ->
                TierUsageRow(tier)
            }

            Text(
                text = "A tier your plan does not include shows no allowance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Text(
                text = "Plans",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
            )

            Plan.entries.forEach { candidate ->
                PlanCard(
                    plan = candidate,
                    current = candidate == plan,
                    onChoose = { viewModel.choose(candidate) },
                )
            }

            Text(
                text = "Billing is not connected yet: switching a plan here only changes " +
                    "the allowance on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TierUsageRow(tier: TierUsageUi) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = tier.tier.displayName,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = if (tier.included) {
                    "${UsageViewModel.formatTokens(tier.used)} / " +
                        UsageViewModel.formatTokens(tier.allowance)
                } else {
                    "Not in your plan"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { tier.fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlanCard(plan: Plan, current: Boolean, onChoose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (current) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = plan.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = plan.priceLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            Text(
                text = "${plan.maxProjects} projects · " +
                    "${UsageViewModel.formatTokens(plan.monthlyTokens)} tokens / month",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = buildString {
                    append("Neutron Stars")
                    if (plan.includesWhite) append(" · White Dwarfs ×${plan.whiteWeight}")
                    if (plan.includesRed) append(" · Red Dwarfs ×${plan.redWeight}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!current) {
                OutlinedButton(onClick = onChoose) {
                    Text("Use ${plan.displayName}")
                }
            } else {
                Text(
                    text = "Current plan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
