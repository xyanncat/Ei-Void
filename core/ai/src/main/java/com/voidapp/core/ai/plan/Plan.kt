package com.voidapp.core.ai.plan

import com.voidapp.core.ai.Tier

/**
 * The subscription plans. The numbers are the product specification:
 *
 * - the monthly token budget is the whole allowance for a month;
 * - [redWeight] and [whiteWeight] are how much of that budget each expensive
 *   tier may use, relative to Neutron Stars (which always has weight 1).
 *
 * So Pro spreads 80M over weights 2 / 5 / 1 — 20M Red, 50M White, 10M
 * Neutron — and a tier with weight 0 is not included at all.
 */
enum class Plan(
    val displayName: String,
    val priceLabel: String,
    val maxProjects: Int,
    val monthlyTokens: Long,
    val redWeight: Int,
    val whiteWeight: Int,
) {
    FREE(
        displayName = "Free",
        priceLabel = "₹0",
        maxProjects = 7,
        monthlyTokens = 30_000_000,
        redWeight = 0,
        whiteWeight = 0,
    ),
    PRO(
        displayName = "Pro",
        priceLabel = "₹200 / month",
        maxProjects = 15,
        monthlyTokens = 80_000_000,
        redWeight = 2,
        whiteWeight = 5,
    ),
    MAX(
        displayName = "Max",
        priceLabel = "₹500 / month",
        maxProjects = 30,
        monthlyTokens = 200_000_000,
        redWeight = 5,
        whiteWeight = 10,
    ),
    ;

    val includesRed: Boolean get() = redWeight > 0
    val includesWhite: Boolean get() = whiteWeight > 0

    fun allows(tier: Tier): Boolean = when (tier) {
        Tier.RED_DWARF -> includesRed
        Tier.WHITE_DWARF -> includesWhite
        Tier.NEUTRON_STAR -> true
    }

    /** The weights of the tiers this plan includes, Neutron Stars last. */
    fun tierWeights(): Map<Tier, Int> = buildMap {
        if (redWeight > 0) put(Tier.RED_DWARF, redWeight)
        if (whiteWeight > 0) put(Tier.WHITE_DWARF, whiteWeight)
        put(Tier.NEUTRON_STAR, 1)
    }

    /** Tokens this plan may spend on [tier] in one month. */
    fun allowance(tier: Tier): Long {
        if (!allows(tier)) return 0L
        val weights = tierWeights()
        val total = weights.values.sum()
        return monthlyTokens * weights.getValue(tier) / total
    }

    /** The strongest tier this plan can reach. */
    val bestTier: Tier
        get() = when {
            includesRed -> Tier.RED_DWARF
            includesWhite -> Tier.WHITE_DWARF
            else -> Tier.NEUTRON_STAR
        }

    companion object {
        fun fromName(name: String?): Plan =
            entries.firstOrNull { it.name == name } ?: FREE
    }
}
