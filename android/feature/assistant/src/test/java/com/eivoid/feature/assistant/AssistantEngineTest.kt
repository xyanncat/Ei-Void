package com.eivoid.feature.assistant

import com.eivoid.core.agent.ProviderResolver
import com.eivoid.core.agent.ProviderTarget
import com.eivoid.core.ai.AiProvider
import com.eivoid.core.ai.AiProviderException
import com.eivoid.core.ai.ChatChunk
import com.eivoid.core.ai.ChatRequest
import com.eivoid.core.ai.Model
import com.eivoid.core.ai.Role
import com.eivoid.core.ai.Tier
import com.eivoid.core.ai.plan.Plan
import com.eivoid.feature.settings.SettingsStore
import com.eivoid.feature.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEngineTest {

    @Test
    fun `no targets fails with the customize hint`() = runTest {
        val engine = AssistantEngine(
            resolver = StaticResolver(targets = emptyList(), key = null),
            settingsStore = InMemorySettingsStore(),
        )

        val result = engine.send("hi", emptyList()) {}

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, message.contains("Customize"))
    }

    @Test
    fun `a successful call streams deltas in order`() = runTest {
        val provider = ScriptedProvider(
            id = "primary",
            chunks = listOf(
                ChatChunk.Delta("Hello"),
                ChatChunk.Delta(" world"),
                ChatChunk.Finished("stop"),
            ),
        )
        val engine = engineFor(ProviderTarget(provider, model("primary-model", "primary")))

        val deltas = mutableListOf<String>()
        val result = engine.send("say hi", emptyList()) { deltas += it }

        assertTrue(result.isSuccess)
        assertEquals(listOf("Hello", " world"), deltas)

        val request = provider.requests.single()
        assertEquals("primary-model", request.model)
        assertEquals(Role.SYSTEM, request.messages.first().role)
        assertEquals(ASSISTANT_SYSTEM_PROMPT, request.messages.first().content)
        assertEquals(Role.USER, request.messages.last().role)
        assertEquals("say hi", request.messages.last().content)
        assertTrue(request.stream)
        assertFalse(request.includeUsage)
        assertEquals(0.3, request.temperature, 0.0001)
        assertEquals(1_024, request.maxTokens)
    }

    @Test
    fun `history is replayed between the system prompt and the new turn`() = runTest {
        val provider = ScriptedProvider(id = "primary", chunks = listOf(ChatChunk.Finished("stop")))
        val engine = engineFor(ProviderTarget(provider, model("primary-model", "primary")))

        val history = listOf(
            AssistantMessage("1", AssistantRole.USER, "first"),
            AssistantMessage("2", AssistantRole.ASSISTANT, "answer"),
        )
        engine.send("second", history) {}

        val messages = provider.requests.single().messages
        assertEquals(
            listOf(Role.SYSTEM, Role.USER, Role.ASSISTANT, Role.USER),
            messages.map { it.role },
        )
        assertEquals(listOf("first", "answer", "second"), messages.drop(1).map { it.content })
    }

    @Test
    fun `a provider failure becomes a short error without the key`() = runTest {
        val key = "super-secret-key-123"
        val provider = ScriptedProvider(
            id = "primary",
            failure = AiProviderException("upstream rejected key $key"),
        )
        val engine = AssistantEngine(
            resolver = StaticResolver(
                targets = listOf(ProviderTarget(provider, model("primary-model", "primary"))),
                key = key,
            ),
            settingsStore = InMemorySettingsStore(),
        )

        val result = engine.send("hi", emptyList()) {}

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, message.isNotEmpty())
        assertFalse(message, message.contains(key))
        assertTrue(message, message.length <= 160)
    }

    @Test
    fun `a provider without a key fails before it is called`() = runTest {
        val provider = ScriptedProvider(id = "primary", chunks = listOf(ChatChunk.Finished("stop")))
        val engine = AssistantEngine(
            resolver = StaticResolver(
                targets = listOf(ProviderTarget(provider, model("primary-model", "primary"))),
                key = null,
            ),
            settingsStore = InMemorySettingsStore(),
        )

        val result = engine.send("hi", emptyList()) {}

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Customize"))
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun `a failed provider falls back to the next route once`() = runTest {
        val broken = ScriptedProvider(
            id = "broken",
            failure = AiProviderException("down"),
        )
        val working = ScriptedProvider(
            id = "working",
            chunks = listOf(ChatChunk.Delta("from backup"), ChatChunk.Finished("stop")),
        )
        val resolver = StaticResolver(
            targets = listOf(
                ProviderTarget(broken, model("broken-model", "broken")),
                ProviderTarget(working, model("working-model", "working")),
            ),
            key = "secret",
        )
        val engine = AssistantEngine(resolver, InMemorySettingsStore())

        val deltas = mutableListOf<String>()
        val result = engine.send("hi", emptyList()) { deltas += it }

        assertTrue(result.isSuccess)
        assertEquals(listOf("from backup"), deltas)
        assertEquals(1, broken.requests.size)
        assertEquals(1, working.requests.size)
    }

    /* ------------------------------------------------------------------ */

    private fun engineFor(vararg targets: ProviderTarget): AssistantEngine =
        AssistantEngine(
            resolver = StaticResolver(targets = targets.toList(), key = "secret"),
            settingsStore = InMemorySettingsStore(),
        )

    private fun model(id: String, providerId: String): Model = Model(
        id = id,
        providerId = providerId,
        displayName = Tier.NEUTRON_STAR.displayName,
        tier = Tier.NEUTRON_STAR,
        contextWindow = 8_192,
        temperature = 0.3,
        maxTokens = 1_024,
    )

    private class StaticResolver(
        private val targets: List<ProviderTarget>,
        private val key: String?,
    ) : ProviderResolver {
        override suspend fun targets(): List<ProviderTarget> = targets
        override suspend fun keyFor(providerId: String): String? = key
    }

    private class InMemorySettingsStore(plan: Plan = Plan.MAX) : SettingsStore {
        override val theme = MutableStateFlow(ThemeMode.DARK)
        override val preferredTier = MutableStateFlow<Tier?>(null)
        override val plan = MutableStateFlow(plan)

        override fun setTheme(mode: ThemeMode) {
            theme.value = mode
        }

        override fun setPreferredTier(tier: Tier?) {
            preferredTier.value = tier
        }

        override fun setPlan(plan: Plan) {
            this.plan.value = plan
        }
    }

    private class ScriptedProvider(
        override val id: String,
        private val chunks: List<ChatChunk> = emptyList(),
        private val failure: Throwable? = null,
    ) : AiProvider {
        override val displayName: String = "Scripted"

        val requests = mutableListOf<ChatRequest>()

        override suspend fun validate(apiKey: String): Boolean = true

        override suspend fun listModels(apiKey: String): List<Model> = emptyList()

        override suspend fun chat(request: ChatRequest, apiKey: String): Flow<ChatChunk> {
            requests += request
            failure?.let { throw it }
            return chunks.asFlow()
        }
    }
}
