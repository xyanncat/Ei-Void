package com.voidapp.core.ai.router

import com.voidapp.core.ai.AiProvider
import com.voidapp.core.ai.ChatChunk
import com.voidapp.core.ai.ChatRequest
import com.voidapp.core.ai.Model
import com.voidapp.core.ai.Tier
import com.voidapp.core.ai.models.ModelCatalog
import com.voidapp.core.ai.plan.Plan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRouterTest {

    private fun model(id: String, tier: Tier, providerId: String = "nvidia") = Model(
        id = id,
        providerId = providerId,
        displayName = tier.displayName,
        tier = tier,
        contextWindow = 8_192,
    )

    private fun candidate(id: String, tier: Tier, providerId: String = "nvidia") =
        Candidate(FakeProvider(providerId), model(id, tier, providerId))

    @Test
    fun `tasks are classified from their wording`() {
        val classifier = TaskClassifier()

        assertEquals(TaskKind.SCAFFOLD, classifier.classify("create a new module for billing"))
        assertEquals(TaskKind.REFACTOR, classifier.classify("refactor the parser into two files"))
        assertEquals(TaskKind.REVIEW, classifier.classify("review the last commit for bugs"))
        assertEquals(TaskKind.EXPLAIN, classifier.classify("explain how the cache works"))
        assertEquals(TaskKind.EDIT, classifier.classify("fix the off-by-one in the parser"))
    }

    @Test
    fun `a small edit goes to the smallest tier`() {
        val router = SmartRouter(plan = { Plan.MAX })

        val route = router.pick(
            "fix the typo",
            listOf(
                candidate("red", Tier.RED_DWARF),
                candidate("white", Tier.WHITE_DWARF),
                candidate("neutron", Tier.NEUTRON_STAR),
            ),
        )

        assertEquals(Tier.NEUTRON_STAR, route?.model?.tier)
        assertEquals("Neutron Stars", route?.tierName)
    }

    @Test
    fun `a review goes to the middle tier`() {
        val router = SmartRouter(plan = { Plan.MAX })

        val route = router.pick(
            "review the parser for bugs",
            listOf(
                candidate("red", Tier.RED_DWARF),
                candidate("white", Tier.WHITE_DWARF),
                candidate("neutron", Tier.NEUTRON_STAR),
            ),
        )

        assertEquals(Tier.WHITE_DWARF, route?.model?.tier)
    }

    @Test
    fun `scaffolding goes to the strongest tier`() {
        val router = SmartRouter(plan = { Plan.MAX })

        val route = router.pick(
            "scaffold a new module",
            listOf(
                candidate("red", Tier.RED_DWARF),
                candidate("white", Tier.WHITE_DWARF),
                candidate("neutron", Tier.NEUTRON_STAR),
            ),
        )

        assertEquals(Tier.RED_DWARF, route?.model?.tier)
    }

    @Test
    fun `the user's tier wins over the task`() {
        val router = SmartRouter(preferredTier = { Tier.RED_DWARF }, plan = { Plan.MAX })

        val route = router.pick(
            "fix the typo",
            listOf(
                candidate("red", Tier.RED_DWARF),
                candidate("neutron", Tier.NEUTRON_STAR),
            ),
        )

        assertEquals(Tier.RED_DWARF, route?.model?.tier)
        assertTrue(route?.reason?.contains("chosen") == true)
    }

    @Test
    fun `an unavailable tier falls back to a stronger one, not a weaker one`() {
        val router = SmartRouter(preferredTier = { Tier.RED_DWARF }, plan = { Plan.MAX })

        val route = router.pick(
            "fix the typo",
            listOf(candidate("neutron", Tier.NEUTRON_STAR)),
        )

        assertEquals(Tier.NEUTRON_STAR, route?.model?.tier)
    }

    @Test
    fun `the best model inside a tier wins`() {
        val router = SmartRouter()

        val route = router.pick(
            "fix the typo",
            listOf(
                // The catalog ranks North Mini Code (7) below Laguna XS (6).
                candidate("cohere/north-mini-code:free", Tier.NEUTRON_STAR, "openrouter"),
                candidate("poolside/laguna-xs-2.1", Tier.NEUTRON_STAR, "nvidia"),
            ),
        )

        assertEquals("poolside/laguna-xs-2.1", route?.model?.id)
    }

    @Test
    fun `the fallback skips the model that failed, not the whole provider`() {
        val router = SmartRouter()

        val route = router.next(
            "fix the typo",
            listOf(
                candidate("poolside/laguna-xs-2.1", Tier.NEUTRON_STAR, providerId = "nvidia"),
                candidate("cohere/north-mini-code:free", Tier.NEUTRON_STAR, providerId = "openrouter"),
            ),
            failedKeys = setOf("nvidia/poolside/laguna-xs-2.1"),
        )

        assertEquals("openrouter", route?.providerId)
    }

    @Test
    fun `a second model on the failed provider is still tried`() {
        val router = SmartRouter()

        val route = router.next(
            "fix the typo",
            listOf(
                candidate("poolside/laguna-xs-2.1", Tier.NEUTRON_STAR, providerId = "nvidia"),
                candidate("nvidia/nemotron-3-ultra-550b-a55b", Tier.NEUTRON_STAR, providerId = "nvidia"),
            ),
            failedKeys = setOf("nvidia/poolside/laguna-xs-2.1"),
        )

        // Both live on nvidia; the second model must still be a candidate.
        assertEquals("nvidia", route?.providerId)
        assertEquals("nvidia/nemotron-3-ultra-550b-a55b", route?.model?.id)
    }

    @Test
    fun `no candidates means no route`() {
        val router = SmartRouter()

        assertNull(router.pick("fix the typo", emptyList()))
    }

    @Test
    fun `the free plan never leaves the smallest tier`() {
        val router = SmartRouter(plan = { Plan.FREE })

        val scaffold = router.pick(
            "scaffold a new module",
            listOf(
                candidate("white", Tier.WHITE_DWARF),
                candidate("neutron", Tier.NEUTRON_STAR),
            ),
        )

        assertEquals(Tier.NEUTRON_STAR, scaffold?.model?.tier)
    }

    @Test
    fun `pro can reach white dwarfs for a review`() {
        val router = SmartRouter(plan = { Plan.PRO })

        val route = router.pick(
            "review the parser",
            listOf(
                candidate("white", Tier.WHITE_DWARF),
                candidate("neutron", Tier.NEUTRON_STAR),
            ),
        )

        assertEquals(Tier.WHITE_DWARF, route?.model?.tier)
    }

    @Test
    fun `a preferred tier the plan does not include is ignored`() {
        val router = SmartRouter(preferredTier = { Tier.WHITE_DWARF }, plan = { Plan.FREE })

        val route = router.pick(
            "fix the typo",
            listOf(
                candidate("white", Tier.WHITE_DWARF),
                candidate("neutron", Tier.NEUTRON_STAR),
            ),
        )

        // Free does not include White Dwarfs, so the preference is dropped and
        // the task falls to its natural tier.
        assertEquals(Tier.NEUTRON_STAR, route?.model?.tier)
    }

    @Test
    fun `the catalog maps every model to a tier`() {
        assertEquals(5, ModelCatalog.all.size)
        assertEquals(Tier.RED_DWARF, ModelCatalog.all.first().tier)
        assertEquals(Tier.NEUTRON_STAR, ModelCatalog.all.last().tier)

        // Every tier has at least one model.
        Tier.entries.forEach { tier ->
            assertTrue("no model for $tier", ModelCatalog.forTier(tier).isNotEmpty())
        }
    }

    @Test
    fun `the models that do not answer are not in the catalog`() {
        assertNull(ModelCatalog.find("deepseek-ai/deepseek-v4.1-flash", "nvidia"))
        assertNull(ModelCatalog.find("laguna-s-2.1", "nara"))
    }

    @Test
    fun `a model keeps the tier name as its display name`() {
        val entry = ModelCatalog.find("minimaxai/minimax-m3.1-flash", "nvidia")!!
        val model = ModelCatalog.toModel(entry)

        assertEquals("Red Dwarfs", model.displayName)
        assertEquals("minimaxai/minimax-m3.1-flash", model.id)
        assertTrue(model.streaming)
        assertNull(model.topP)
    }

    private class FakeProvider(override val id: String) : AiProvider {
        override val displayName: String get() = id

        override suspend fun validate(apiKey: String): Boolean = true

        override suspend fun listModels(apiKey: String): List<Model> = emptyList()

        override suspend fun chat(request: ChatRequest, apiKey: String): Flow<ChatChunk> = emptyFlow()
    }
}
