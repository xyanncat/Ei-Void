package com.eivoid.app.agent

import com.eivoid.core.agent.UsageMeter
import com.eivoid.core.ai.Tier
import com.eivoid.core.ai.plan.Plan
import com.eivoid.core.database.UsageDao
import com.eivoid.core.database.UsageEntryEntity
import com.eivoid.feature.settings.SettingsStore
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
