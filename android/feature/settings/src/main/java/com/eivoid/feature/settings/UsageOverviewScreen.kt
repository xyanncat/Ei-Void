package com.eivoid.feature.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.core.ai.Tier
import com.eivoid.core.ai.plan.Plan
import com.eivoid.designsystem.component.EiCard
import com.eivoid.designsystem.component.EiDivider
import com.eivoid.designsystem.component.EiNotice
import com.eivoid.designsystem.component.EiPill
import com.eivoid.designsystem.component.EiProgressBar
import com.eivoid.designsystem.component.EiScreen
import com.eivoid.designsystem.component.EiSectionLabel
import com.eivoid.designsystem.component.EiStat
import com.eivoid.designsystem.component.EiStatusPill
import com.eivoid.designsystem.component.EiTopBar
import com.eivoid.designsystem.theme.VoidColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val RESET_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/**
 * Tokens and plan, rebuilt on the OLED design kit.
 *
 * Usage is read from the local ledger in [UsageViewModel]; billing is not
 * connected, so the screen says so instead of implying a live subscription.
 */
@Composable
fun UsageOverviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageViewModel = hiltViewModel(),
    settings: SettingsViewModel = hiltViewModel(),
) {
    val plan by settings.plan.collectAsStateWithLifecycle()
    val preferredTier by settings.preferredTier.collectAsStateWithLifecycle()
    val tiers by viewModel.tiers.collectAsStateWithLifecycle()

    val totalUsed = tiers.sumOf { it.used }
    val totalLimit = tiers.sumOf { it.allowance }.takeIf { it > 0L } ?: plan.monthlyTokens
    val fraction = if (totalLimit <= 0L) {
        0f
    } else {
        (totalUsed.toFloat() / totalLimit).coerceIn(0f, 1f)
    }
    val resetDate = LocalDate.now()
        .withDayOfMonth(1)
        .plusMonths(1)
        .format(RESET_DATE_FORMAT)

    EiScreen(modifier = modifier) {
        EiTopBar(
            title = "Usage",
            subtitle = "Tokens and plan",
            onBack = onBack,
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
            item {
                UsageHeroCard(
                    plan = plan,
                    totalUsed = totalUsed,
                    totalLimit = totalLimit,
                    fraction = fraction,
                    preferredTier = preferredTier,
                    resetDate = resetDate,
                )
            }

            item { EiSectionLabel("BY TIER", Modifier.padding(top = 10.dp)) }

            item { TierBreakdownCard(tiers = tiers, plan = plan) }

            item { EiSectionLabel("PLAN", Modifier.padding(top = 10.dp)) }

            item { PlanPickerCard(plan = plan, onChoose = viewModel::choose) }

            item {
                EiNotice(
                    message = "Billing is not connected. Usage is counted from this " +
                        "device's local ledger; choosing a plan only changes the local " +
                        "allowance.",
                )
            }
        }
    }
}

@Composable
private fun UsageHeroCard(
    plan: Plan,
    totalUsed: Long,
    totalLimit: Long,
    fraction: Float,
    preferredTier: Tier?,
    resetDate: String,
) {
    val chosenTier = preferredTier
    val percentage = (fraction * 100).roundToInt()
    val remaining = (totalLimit - totalUsed).coerceAtLeast(0L)
    val hot = fraction >= 0.9f

    EiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CURRENT PLAN",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoidColors.oledTextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = plan.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = VoidColors.oledTextPrimary,
                )
                Text(
                    text = "${plan.priceLabel} · ${plan.maxProjects} projects",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                )
            }
            Spacer(Modifier.width(10.dp))
            EiStatusPill(
                text = chosenTier?.displayName ?: "Auto tier",
                color = VoidColors.oledAccentBlue,
            )
        }

        Spacer(Modifier.height(18.dp))
        EiDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "TOKENS THIS MONTH",
            style = MaterialTheme.typography.labelSmall,
            color = VoidColors.oledTextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${tokenLabel(totalUsed)} used",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = VoidColors.oledTextPrimary,
        )
        Text(
            text = "of ${tokenLabel(totalLimit)}",
            style = MaterialTheme.typography.bodySmall,
            color = VoidColors.oledTextSecondary,
        )

        Spacer(Modifier.height(12.dp))
        EiProgressBar(
            fraction = fraction,
            color = if (hot) VoidColors.oledDanger else VoidColors.oledAccentBlue,
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            EiStat(
                value = "$percentage%",
                label = "of allowance",
                accent = if (hot) VoidColors.oledDanger else VoidColors.oledTextPrimary,
            )
            EiStat(value = tokenLabel(totalLimit), label = "monthly limit")
            EiStat(
                value = tokenLabel(remaining),
                label = "remaining",
                accent = VoidColors.oledSuccess,
            )
        }

        Spacer(Modifier.height(14.dp))
        EiDivider()
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Resets $resetDate",
                style = MaterialTheme.typography.bodySmall,
                color = VoidColors.oledTextSecondary,
                modifier = Modifier.weight(1f),
            )
            EiStatusPill(
                text = "Local device",
                color = VoidColors.oledTextSecondary,
            )
        }
    }
}

@Composable
private fun TierBreakdownCard(tiers: List<TierUsageUi>, plan: Plan) {
    EiCard {
        Text(
            text = "Tier allowances",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = VoidColors.oledTextPrimary,
        )
        Text(
            text = "Each tier has its own slice of the monthly budget.",
            style = MaterialTheme.typography.bodySmall,
            color = VoidColors.oledTextSecondary,
        )

        Spacer(Modifier.height(16.dp))

        if (tiers.isEmpty()) {
            Text(
                text = "No usage recorded this month yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = VoidColors.oledTextSecondary,
            )
        } else {
            tiers.forEachIndexed { index, tier ->
                TierUsageRow(tier = tier, plan = plan)
                if (index != tiers.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                    EiDivider()
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun TierUsageRow(tier: TierUsageUi, plan: Plan) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tierMarker(tier.tier)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = tier.tier.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = VoidColors.oledTextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (tier.included) {
            Text(
                text = "${tokenLabel(tier.used)} / ${tokenLabel(tier.allowance)}",
                style = MaterialTheme.typography.bodySmall,
                color = VoidColors.oledTextSecondary,
            )
        } else {
            EiStatusPill(
                text = "Not in ${plan.displayName}",
                color = VoidColors.oledTextSecondary,
            )
        }
    }

    if (tier.included) {
        Spacer(Modifier.height(8.dp))
        EiProgressBar(
            fraction = tier.fraction,
            modifier = Modifier.padding(start = 18.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanPickerCard(plan: Plan, onChoose: (Plan) -> Unit) {
    EiCard {
        Text(
            text = "Change plan",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = VoidColors.oledTextPrimary,
        )
        Text(
            text = "${plan.displayName} includes " +
                "${tokenLabel(plan.monthlyTokens)} tokens and ${plan.maxProjects} projects.",
            style = MaterialTheme.typography.bodySmall,
            color = VoidColors.oledTextSecondary,
        )

        Spacer(Modifier.height(14.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Plan.entries.forEach { candidate ->
                EiPill(
                    text = candidate.displayName,
                    selected = candidate == plan,
                    onClick = { onChoose(candidate) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Switching plans updates the allowance immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = VoidColors.oledTextSecondary,
        )
    }
}

private fun tokenLabel(value: Long): String = UsageViewModel.formatTokens(value)

private fun tierMarker(tier: Tier): Color = when (tier) {
    Tier.RED_DWARF -> VoidColors.tierRed
    Tier.WHITE_DWARF -> VoidColors.tierWhite
    Tier.NEUTRON_STAR -> VoidColors.tierAmber
}
