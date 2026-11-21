package com.eivoid.feature.assistant

import com.eivoid.core.agent.ProviderResolver
import com.eivoid.core.agent.ProviderTarget
import com.eivoid.core.ai.AiProviderException
import com.eivoid.core.ai.Tier
import com.eivoid.core.ai.plan.Plan
import com.eivoid.feature.settings.SettingsStore
import com.eivoid.feature.settings.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `send appends the user turn and the streamed assistant reply`() =
        runTest(dispatcher.scheduler) {
            val engine = FakeEngine { _, _, onDelta ->
                onDelta("Hi ")
                onDelta("there")
                Result.success(Unit)
            }
            val viewModel = AssistantViewModel(engine)

            viewModel.onInputChange("hello")
            viewModel.send()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(
                listOf(AssistantRole.USER, AssistantRole.ASSISTANT),
                state.messages.map { it.role },
            )
            assertEquals("hello", state.messages.first().text)
            assertEquals("Hi there", state.messages.last().text)
            assertEquals("", state.input)
            assertFalse(state.streaming)
            assertNull(state.error)
            assertEquals(listOf("hello"), engine.prompts)
            assertTrue(engine.histories.single().isEmpty())
        }

    @Test
    fun `deltas fill the assistant turn while streaming`() =
        runTest(dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val engine = FakeEngine { _, _, onDelta ->
                onDelta("part one ")
                gate.await()
                onDelta("part two")
                Result.success(Unit)
            }
            val viewModel = AssistantViewModel(engine)

            viewModel.onInputChange("go")
            viewModel.send()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.streaming)
            assertEquals("part one ", viewModel.state.value.messages.last().text)
            assertEquals(2, viewModel.state.value.messages.size)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals("part one part two", viewModel.state.value.messages.last().text)
            assertFalse(viewModel.state.value.streaming)
        }

    @Test
    fun `blank input is ignored`() = runTest(dispatcher.scheduler) {
        val engine = FakeEngine { _, _, _ -> Result.success(Unit) }
        val viewModel = AssistantViewModel(engine)

        viewModel.onInputChange("   ")
        viewModel.send()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.messages.isEmpty())
        assertTrue(engine.prompts.isEmpty())
        assertEquals("   ", viewModel.state.value.input)
    }

    @Test
    fun `a failure keeps the user turn and shows the error`() =
        runTest(dispatcher.scheduler) {
            val engine = FakeEngine { _, _, _ ->
                Result.failure(
                    AiProviderException("Add a provider key in Customize."),
                )
            }
            val viewModel = AssistantViewModel(engine)

            viewModel.onInputChange("hello")
            viewModel.send()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(1, state.messages.size)
            assertEquals(AssistantRole.USER, state.messages.single().role)
            assertEquals("hello", state.messages.single().text)
            assertEquals("Add a provider key in Customize.", state.error)
            assertFalse(state.streaming)
        }

    @Test
    fun `a task fills the input and closes the picker`() {
        val viewModel = AssistantViewModel(FakeEngine { _, _, _ -> Result.success(Unit) })

        viewModel.openTaskPicker()
        assertTrue(viewModel.state.value.showTaskPicker)

        viewModel.addTask("Check subscriptions")

        assertEquals("Check subscriptions", viewModel.state.value.input)
        assertFalse(viewModel.state.value.showTaskPicker)
    }

    @Test
    fun `incognito toggles`() {
        val viewModel = AssistantViewModel(FakeEngine { _, _, _ -> Result.success(Unit) })

        assertFalse(viewModel.state.value.incognito)
        viewModel.toggleIncognito()
        assertTrue(viewModel.state.value.incognito)
        viewModel.toggleIncognito()
        assertFalse(viewModel.state.value.incognito)
    }

    @Test
    fun `clear chat empties the transcript`() = runTest(dispatcher.scheduler) {
        val engine = FakeEngine { _, _, onDelta ->
            onDelta("reply")
            Result.success(Unit)
        }
        val viewModel = AssistantViewModel(engine)
        viewModel.onInputChange("hello")
        viewModel.send()
        advanceUntilIdle()

        viewModel.clearChat()

        assertTrue(viewModel.state.value.messages.isEmpty())
        assertNull(viewModel.state.value.error)
    }

    /** A scripted engine: the network never runs in these tests. */
    private class FakeEngine(
        private val reply: suspend (String, List<AssistantMessage>, (String) -> Unit) -> Result<Unit>,
    ) : AssistantEngine(NoTargetsResolver(), InMemorySettingsStore()) {

        val prompts = mutableListOf<String>()
        val histories = mutableListOf<List<AssistantMessage>>()

        override suspend fun send(
            prompt: String,
            history: List<AssistantMessage>,
            onDelta: (String) -> Unit,
        ): Result<Unit> {
            prompts += prompt
            histories += history
            return reply(prompt, history, onDelta)
        }
    }

    private class NoTargetsResolver : ProviderResolver {
        override suspend fun targets(): List<ProviderTarget> = emptyList()
        override suspend fun keyFor(providerId: String): String? = null
    }

    private class InMemorySettingsStore : SettingsStore {
        override val theme = MutableStateFlow(ThemeMode.DARK)
        override val preferredTier = MutableStateFlow<Tier?>(null)
        override val plan = MutableStateFlow(Plan.FREE)

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
}
