package com.voidapp.app.agent

import com.voidapp.core.agent.UsageMeter
import com.voidapp.core.ai.Tier
import com.voidapp.core.ai.plan.Plan
import com.voidapp.core.database.UsageDao
import com.voidapp.core.database.UsageEntryEntity
import com.voidapp.feature.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Counts tokens per tier in Room and reads the monthly allowance from the
 * current plan.
 */
@Singleton
class RoomUsageMeter @Inject constructor(
    private val usageDao: UsageDao,
    private val settingsStore: SettingsStore,
) : UsageMeter {

    override suspend fun used(tier: Tier, since: Long): Long =
        usageDao.tokensSince(tier.name, since)

    override suspend fun allowance(tier: Tier, since: Long): Long {
        val plan: Plan = settingsStore.plan.first()
        return plan.allowance(tier)
    }

    override suspend fun record(tier: Tier, tokens: Int) {
        if (tokens <= 0) return
        usageDao.insert(
            UsageEntryEntity(
                tier = tier.name,
                tokens = tokens,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}
