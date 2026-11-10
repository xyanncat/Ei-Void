package com.voidapp.core.ai.router

import com.voidapp.core.ai.AiProvider
import com.voidapp.core.ai.ChatChunk
import com.voidapp.core.ai.ChatRequest
import com.voidapp.core.ai.Model
import com.voidapp.core.ai.Tier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRouterTest {

    private fun model(
        id: String,
        tier: Tier,
        isFree: Boolean = false,
        providerId: String = "openrouter",
    ) = Model(
        id = id,
        providerId = providerId,
        displayName = id,
        tier = tier,
        contextWindow = 8_192,
        isFree = isFree,
    )

    private fun candidate(
        id: String,
        tier: Tier,
        isFree: Boolean = false,
        providerId: String = "openrouter",
    ) = Candidate(FakeProvider(providerId), model(id, tier, isFree, providerId))

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
    fun `an edit prefers a free fast model`() {
        val router = SmartRouter()

        val route = router.pick(
            "fix the typo",
            listOf(
                candidate("strong/paid", Tier.STRONG),
                candidate("standard/paid", Tier.STANDARD),
                candidate("fast/free:free", Tier.FAST, isFree = true),
            ),
        )

        assertEquals("fast/free:free", route?.model?.id)
    }

    @Test
    fun `the user's default model wins`() {
        val router = SmartRouter(defaultModelId = { "standard/paid" })

        val route = router.pick(
            "fix the typo",
            listOf(
                candidate("fast/free:free", Tier.FAST, isFree = true),
                candidate("standard/paid", Tier.STANDARD),
            ),
        )

        assertEquals("standard/paid", route?.model?.id)
        assertTrue(route?.reason?.contains("default") == true)
    }

    @Test
    fun `a complex task upgrades past the default model`() {
        val router = SmartRouter(defaultModelId = { "fast/free:free" })

        val route = router.pick(
            "refactor the whole parser",
            listOf(
                candidate("fast/free:free", Tier.FAST, isFree = true),
                candidate("strong/paid", Tier.STRONG),
            ),
        )

        assertEquals("strong/paid", route?.model?.id)
    }

    @Test
    fun `a scaffold task wants a strong model`() {
        val router = SmartRouter()

        val route = router.pick(
            "scaffold a new module",
            listOf(
                candidate("fast/free:free", Tier.FAST, isFree = true),
                candidate("strong/paid", Tier.STRONG),
            ),
        )

        assertEquals("strong/paid", route?.model?.id)
    }

    @Test
    fun `the fallback skips the provider that failed`() {
        val router = SmartRouter()

        val route = router.next(
            "fix the typo",
            listOf(
                candidate("a/one", Tier.FAST, providerId = "provider-a"),
                candidate("b/two", Tier.FAST, providerId = "provider-b"),
            ),
            failed = setOf("provider-a"),
        )

        assertEquals("provider-b", route?.providerId)
    }

    @Test
    fun `no candidates means no route`() {
        val router = SmartRouter()

        assertNull(router.pick("fix the typo", emptyList()))
    }

    @Test
    fun `the model catalog guesses the tier and the free flag`() {
        assertEquals(Tier.FAST, com.voidapp.core.ai.models.ModelCatalog.tierFor("vendor/gpt-4o-mini"))
        assertEquals(Tier.STRONG, com.voidapp.core.ai.models.ModelCatalog.tierFor("vendor/claude-opus-4"))
        assertEquals(Tier.STANDARD, com.voidapp.core.ai.models.ModelCatalog.tierFor("vendor/mystery-1"))
        assertTrue(com.voidapp.core.ai.models.ModelCatalog.isFree("vendor/model:free"))
        assertTrue(com.voidapp.core.ai.models.ModelCatalog.isFree("vendor/model", providerIsFree = true))
    }

    private class FakeProvider(override val id: String) : AiProvider {
        override val displayName: String get() = id

        override suspend fun validate(apiKey: String): Boolean = true

        override suspend fun listModels(apiKey: String): List<Model> = emptyList()

        override suspend fun chat(request: ChatRequest, apiKey: String): Flow<ChatChunk> = emptyFlow()
    }
}
