package com.eivoid.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.core.ai.Tier
import com.eivoid.core.ai.plan.Plan
import com.eivoid.designsystem.component.VoidCard
import com.eivoid.designsystem.component.VoidProgressBar
import com.eivoid.designsystem.component.VoidSectionTitle
import com.eivoid.designsystem.component.VoidStatusChip
import com.eivoid.designsystem.component.VoidTonalButton
import com.eivoid.designsystem.component.VoidTopBar

@Composable
fun UsageScreen(
    onBack: () -> Unit,
    viewModel: UsageViewModel = hiltViewModel(),
) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val tiers by viewModel.tiers.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        VoidTopBar(
            title = "Plan & usage",
            subtitle = "${plan.displayName} · ${plan.priceLabel}",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UsageHero(tiers = tiers)

            VoidSectionTitle("Plans", modifier = Modifier.padding(top = 8.dp))

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
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun UsageHero(tiers: List<TierUsageUi>) {
    val totalUsed = tiers.sumOf { it.used }
    val totalAllowance = tiers.sumOf { it.allowance }

    VoidCard(modifier = Modifier.fillMaxWidth()) {
        VoidSectionTitle("Tokens this month")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = UsageViewModel.formatTokens(totalUsed),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "/ ${UsageViewModel.formatTokens(totalAllowance)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        VoidProgressBar(
            progress = if (totalAllowance > 0) {
                totalUsed.toFloat() / totalAllowance
            } else {
                0f
            },
        )
        Spacer(Modifier.height(20.dp))

        tiers.forEachIndexed { index, tier ->
            TierUsageRow(tier)
            if (index != tiers.lastIndex) Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "A tier your plan does not include shows no allowance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TierUsageRow(tier: TierUsageUi) {
    val dot = tier.tier.markerColor()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = tier.tier.displayName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (tier.included) {
                Text(
                    text = "${UsageViewModel.formatTokens(tier.used)} / " +
                        UsageViewModel.formatTokens(tier.allowance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                VoidStatusChip(
                    text = "Not in plan",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (tier.included) {
            Spacer(Modifier.height(8.dp))
            VoidProgressBar(
                progress = tier.fraction,
                modifier = Modifier.padding(start = 18.dp),
            )
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, current: Boolean, onChoose: () -> Unit) {
    VoidCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (current) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.displayName,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = plan.priceLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (current) {
                VoidStatusChip("Current plan", MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(14.dp))
        FeatureLine("${plan.maxProjects} projects")
        FeatureLine("${UsageViewModel.formatTokens(plan.monthlyTokens)} tokens / month")
        FeatureLine(
            buildString {
                append("Neutron Stars")
                if (plan.includesWhite) append(" · White Dwarfs ×${plan.whiteWeight}")
                if (plan.includesRed) append(" · Red Dwarfs ×${plan.redWeight}")
            },
        )

        if (!current) {
            Spacer(Modifier.height(14.dp))
            VoidTonalButton(
                text = "Use ${plan.displayName}",
                onClick = onChoose,
            )
        }
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(6.dp))
}
