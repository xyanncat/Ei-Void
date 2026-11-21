package com.eivoid.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.eivoid.core.agent.AgentEngine
import com.eivoid.core.agent.ProviderResolver
import com.eivoid.core.agent.ProviderTarget
import com.eivoid.core.agent.Tool
import com.eivoid.core.agent.ToolContext
import com.eivoid.core.agent.ToolRegistry
import com.eivoid.core.agent.ToolResult
import com.eivoid.core.ai.AiProvider
import com.eivoid.core.ai.ChatChunk
import com.eivoid.core.ai.ChatRequest
import com.eivoid.core.ai.Model
import com.eivoid.core.ai.Tier
import com.eivoid.core.ai.ToolCall
import com.eivoid.core.common.github.Repo
import com.eivoid.core.database.ChatDao
import com.eivoid.core.database.ChatMessageEntity
import com.eivoid.core.database.ChatSessionEntity
import com.eivoid.core.security.SecretStore
import com.eivoid.core.workspace.WorkspaceManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sending a message shows the answer`() = runTest(dispatcher) {
        val vm = viewModel(script = listOf(delta("Hello")))

        vm.onInputChange("say hello")
        vm.send()

        val entries = vm.state.value.entries
        assertTrue(entries.first() is ChatEntry.User)
        assertEquals("Hello", (entries.last() as ChatEntry.Assistant).text)
        assertTrue(!vm.state.value.isRunning)
    }

    @Test
    fun `an empty message is ignored`() = runTest(dispatcher) {
        val vm = viewModel(script = emptyList())

        vm.onInputChange("   ")
        vm.send()

        assertTrue(vm.state.value.entries.isEmpty())
    }

    @Test
    fun `the input is cleared when a message is sent`() = runTest(dispatcher) {
        val vm = viewModel(script = listOf(delta("ok")))

        vm.onInputChange("hello")
        vm.send()

        assertEquals("", vm.state.value.input)
    }

    @Test
    fun `a restored conversation is shown and the same session is reused`() = runTest(dispatcher) {
        val dao = FakeChatDao().apply {
            seed(
                ChatSessionEntity("s1", "owner/repo", "hello", 1L),
                listOf(
                    ChatMessageEntity("m1", "s1", "user", "hello", null, null, null, 1L, 1L),
                    ChatMessageEntity("m2", "s1", "assistant", "hi", null, null, null, 2L, 2L),
                ),
            )
        }
        val vm = viewModel(script = listOf(delta("ok")), chatDao = dao)

        assertTrue(vm.state.value.entries.any { it is ChatEntry.User && it.text == "hello" })

        vm.onInputChange("next")
        vm.send()

        assertEquals(1, dao.sessionCount)
    }

    @Test
    fun `a destructive tool waits for approval`() = runTest(dispatcher) {
        val tool = RecordingDeleteTool()
        val call = ToolCall("call_1", tool.name, buildJsonObject { put("path", "x") })
        val vm = viewModel(
            script = listOf(toolCall(call), delta("done")),
            tools = listOf(tool),
        )

        vm.onInputChange("delete x")
        vm.send()

        // The run is paused on the gate.
        assertNotNull(vm.state.value.pendingConfirmation)
        assertTrue(!tool.ran)

        vm.approve()

        assertTrue(tool.ran)
        assertNull(vm.state.value.pendingConfirmation)
        assertTrue(vm.state.value.entries.any { it is ChatEntry.Tool && it.ok })
    }

    @Test
    fun `a declined tool is reported as failed`() = runTest(dispatcher) {
        val tool = RecordingDeleteTool()
        val call = ToolCall("call_1", tool.name, buildJsonObject { put("path", "x") })
        val vm = viewModel(
            script = listOf(toolCall(call), delta("done")),
            tools = listOf(tool),
        )

        vm.onInputChange("delete x")
        vm.send()
        vm.reject()

        assertTrue(!tool.ran)
        val toolEntry = vm.state.value.entries.filterIsInstance<ChatEntry.Tool>().single()
        assertTrue(!toolEntry.ok)
        assertNull(vm.state.value.pendingConfirmation)
    }

    private fun delta(text: String) = listOf(ChatChunk.Delta(text), ChatChunk.Finished("stop"))

    private fun toolCall(call: ToolCall) =
        listOf(ChatChunk.ToolCallDelta(call), ChatChunk.Finished("tool_calls"))

    private fun viewModel(
        script: List<List<ChatChunk>>,
        tools: List<Tool> = emptyList(),
        chatDao: FakeChatDao = FakeChatDao(),
    ): ChatViewModel {
        val provider = ScriptedProvider("scripted", script)
        val resolver = object : ProviderResolver {
            override suspend fun targets(): List<ProviderTarget> = listOf(
                ProviderTarget(
                    provider,
                    Model("scripted", "scripted", "White Dwarfs", Tier.WHITE_DWARF, 8_192),
                ),
            )

            override suspend fun keyFor(providerId: String): String = "key"
        }

        val engine = AgentEngine(
            registry = ToolRegistry(tools),
            resolver = resolver,
            // Plans are covered by the router tests.
            router = com.eivoid.core.ai.router.SmartRouter(
                plan = { com.eivoid.core.ai.plan.Plan.MAX },
            ),
        )
        val root = Files.createTempDirectory("chat-ui").toFile()
        // The chat tool gate requires an existing clone.
        File(root, "owner/repo/.git").mkdirs()
        val workspaceManager = WorkspaceManager(root)

        return ChatViewModel(
            engine = engine,
            workspaceManager = workspaceManager,
            secretStore = FakeSecretStore(),
            chatRepository = ChatRepository(chatDao),
            savedStateHandle = SavedStateHandle(
                mapOf("owner" to "owner", "name" to "repo", "branch" to "main"),
            ),
        )
    }

    private class ScriptedProvider(
        override val id: String,
        private val script: List<List<ChatChunk>>,
    ) : AiProvider {
        override val displayName: String get() = id
        private var calls = 0

        override suspend fun validate(apiKey: String): Boolean = true

        override suspend fun listModels(apiKey: String): List<Model> = emptyList()

        override suspend fun chat(request: ChatRequest, apiKey: String): Flow<ChatChunk> {
            val chunks = script.getOrElse(calls) { listOf(ChatChunk.Finished("stop")) }
            calls++
            return chunks.asFlow()
        }
    }

    private class RecordingDeleteTool : Tool {
        override val name: String = "delete_file"
        override val description: String = "Deletes a file"
        override val parameters: JsonObject = buildJsonObject { put("type", "object") }

        override val requiresConfirmation: Boolean = true

        var ran: Boolean = false
            private set

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            ran = true
            return ToolResult.Success("deleted")
        }
    }

    private class FakeSecretStore : SecretStore {
        private var token: String? = "gho_test"
        private val keys = mutableMapOf<String, String>()

        override fun saveToken(token: String) {
            this.token = token
        }

        override fun getToken(): String? = token

        override fun clearToken() {
            token = null
        }

        override fun saveProviderKey(providerId: String, key: String) {
            keys[providerId] = key
        }

        override fun getProviderKey(providerId: String): String? = keys[providerId]

        override fun clearProviderKey(providerId: String) {
            keys.remove(providerId)
        }

        override fun clearAll() {
            token = null
            keys.clear()
        }
    }

    private class FakeChatDao : ChatDao {
        private val sessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
        private val messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

        val sessionCount: Int get() = sessions.value.size

        fun seed(session: ChatSessionEntity, seededMessages: List<ChatMessageEntity>) {
            sessions.value = sessions.value + session
            messages.value = messages.value + seededMessages
        }

        override suspend fun insertSession(session: ChatSessionEntity) {
            sessions.value = sessions.value.filterNot { it.id == session.id } + session
        }

        override suspend fun insertMessages(messages: List<ChatMessageEntity>) {
            this.messages.value = this.messages.value + messages
        }

        override fun observeSessions(repoFullName: String): Flow<List<ChatSessionEntity>> =
            sessions.map { all -> all.filter { it.repoFullName == repoFullName } }

        override fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>> =
            messages.map { all -> all.filter { it.sessionId == sessionId } }

        override suspend fun deleteMessages(sessionId: String) {
            messages.value = messages.value.filterNot { it.sessionId == sessionId }
        }

        override suspend fun deleteSessionRow(sessionId: String) {
            sessions.value = sessions.value.filterNot { it.id == sessionId }
        }

        override suspend fun deleteAllMessages() {
            messages.value = emptyList()
        }

        override suspend fun deleteAllSessions() {
            sessions.value = emptyList()
        }
    }
}
