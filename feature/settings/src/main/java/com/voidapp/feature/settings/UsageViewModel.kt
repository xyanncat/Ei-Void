package com.voidapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voidapp.core.ai.Tier
import com.voidapp.core.ai.plan.Plan
import com.voidapp.core.common.time.startOfMonthMillis
import com.voidapp.core.database.UsageDao
import dagger.hilt.android.lifecycle.HiltViewModel
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
        /** 12_400_000 -> "12.4M" */
        fun formatTokens(tokens: Long): String = when {
            tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
            tokens >= 1_000 -> String.format("%.0fK", tokens / 1_000.0)
            else -> tokens.toString()
        }
    }
}
