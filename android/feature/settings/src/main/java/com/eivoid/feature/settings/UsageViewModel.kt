package com.eivoid.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eivoid.core.ai.Tier
import com.eivoid.core.ai.plan.Plan
import com.eivoid.core.common.time.startOfMonthMillis
import com.eivoid.core.database.UsageDao
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TierUsageUi(
    val tier: Tier,
    val used: Long,
    val allowance: Long,
) {
    val fraction: Float
        get() = if (allowance <= 0) 0f else (used.toFloat() / allowance).coerceIn(0f, 1f)

    val included: Boolean get() = allowance > 0
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UsageViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    usageDao: UsageDao,
) : ViewModel() {

    val plan: StateFlow<Plan> = settingsStore.plan

    val tiers: StateFlow<List<TierUsageUi>> = settingsStore.plan
        .flatMapLatest { current ->
            usageDao.totalsSince(startOfMonthMillis()).map { rows ->
                val used = rows.associate { it.tier to it.tokens }
                Tier.entries.map { tier ->
                    TierUsageUi(
                        tier = tier,
                        used = used[tier.name] ?: 0L,
                        allowance = current.allowance(tier),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Switching plans is local until billing is connected. */
    fun choose(plan: Plan) {
        settingsStore.setPlan(plan)
    }

    companion object {
        /** 12_400_000 -> "12.4M", 30_000_000 -> "30M", 999_999 -> "1.0M". */
        fun formatTokens(tokens: Long): String {
            if (tokens >= 999_950) return millions(tokens)
            if (tokens >= 1_000) {
                val thousands = tokens / 1_000.0
                return if (thousands % 1.0 == 0.0) {
                    String.format(Locale.US, "%.0fK", thousands)
                } else {
                    String.format(Locale.US, "%.1fK", thousands)
                }
            }
            return tokens.toString()
        }

        private fun millions(tokens: Long): String {
            val value = tokens / 1_000_000.0
            return if (value % 1.0 == 0.0) {
                String.format(Locale.US, "%.0fM", value)
            } else {
                String.format(Locale.US, "%.1fM", value)
            }
        }
    }
}
