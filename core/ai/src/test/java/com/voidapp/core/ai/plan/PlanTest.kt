package com.voidapp.core.ai.plan

import com.voidapp.core.ai.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTest {

    @Test
    fun `free includes only the smallest tier`() {
        assertFalse(Plan.FREE.allows(Tier.RED_DWARF))
        assertFalse(Plan.FREE.allows(Tier.WHITE_DWARF))
        assertTrue(Plan.FREE.allows(Tier.NEUTRON_STAR))
        assertEquals(Tier.NEUTRON_STAR, Plan.FREE.bestTier)
    }

    @Test
    fun `pro opens both expensive tiers`() {
        assertTrue(Plan.PRO.allows(Tier.RED_DWARF))
        assertTrue(Plan.PRO.allows(Tier.WHITE_DWARF))
        assertEquals(Tier.RED_DWARF, Plan.PRO.bestTier)
    }

    @Test
    fun `the free allowance is the whole budget on neutron stars`() {
        assertEquals(30_000_000L, Plan.FREE.allowance(Tier.NEUTRON_STAR))
        assertEquals(0L, Plan.FREE.allowance(Tier.RED_DWARF))
        assertEquals(0L, Plan.FREE.allowance(Tier.WHITE_DWARF))
    }

    @Test
    fun `pro splits 80 million by the tier multipliers`() {
        assertEquals(20_000_000L, Plan.PRO.allowance(Tier.RED_DWARF))
        assertEquals(50_000_000L, Plan.PRO.allowance(Tier.WHITE_DWARF))
        assertEquals(10_000_000L, Plan.PRO.allowance(Tier.NEUTRON_STAR))
    }

    @Test
    fun `max splits 200 million by the tier multipliers`() {
        assertEquals(62_500_000L, Plan.MAX.allowance(Tier.RED_DWARF))
        assertEquals(125_000_000L, Plan.MAX.allowance(Tier.WHITE_DWARF))
        assertEquals(12_500_000L, Plan.MAX.allowance(Tier.NEUTRON_STAR))
    }

    @Test
    fun `every plan spends exactly its monthly budget`() {
        Plan.entries.forEach { plan ->
            val total = Tier.entries.sumOf { plan.allowance(it) }
            assertEquals("${plan.name} spends $total", plan.monthlyTokens, total)
        }
    }

    @Test
    fun `the specification numbers are kept`() {
        assertEquals(7, Plan.FREE.maxProjects)
        assertEquals(30_000_000L, Plan.FREE.monthlyTokens)
        assertEquals(15, Plan.PRO.maxProjects)
        assertEquals(80_000_000L, Plan.PRO.monthlyTokens)
        assertEquals(30, Plan.MAX.maxProjects)
        assertEquals(200_000_000L, Plan.MAX.monthlyTokens)
        assertEquals("₹200 / month", Plan.PRO.priceLabel)
        assertEquals("₹500 / month", Plan.MAX.priceLabel)
    }

    @Test
    fun `an unknown stored name falls back to free`() {
        assertEquals(Plan.FREE, Plan.fromName(null))
        assertEquals(Plan.FREE, Plan.fromName("GOLD"))
        assertEquals(Plan.MAX, Plan.fromName("MAX"))
    }
}
