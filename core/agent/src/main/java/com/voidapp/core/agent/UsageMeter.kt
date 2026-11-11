package com.voidapp.core.agent

import com.voidapp.core.ai.Tier

/**
 * Where the engine records what it spends and checks what is left.
 *
 * The engine only knows tiers, never the plan: the meter decides whether a
 * tier still has room.
 */
interface UsageMeter {

    /** Tokens this tier has used since [since]. */
    suspend fun used(tier: Tier, since: Long): Long

    /** Tokens allowed on this tier in the period starting at [since]. */
    suspend fun allowance(tier: Tier, since: Long): Long

    /** Records what a model call spent. */
    suspend fun record(tier: Tier, tokens: Int)
}
