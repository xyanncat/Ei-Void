package com.voidapp.core.agent

import com.voidapp.core.ai.AiProvider
import com.voidapp.core.ai.ChatChunk
import com.voidapp.core.ai.ChatRequest
import com.voidapp.core.ai.Model
import com.voidapp.core.ai.Tier
import com.voidapp.core.ai.ToolCall
import com.voidapp.core.common.github.Repo
import com.voidapp.core.workspace.Workspace
import com.voidapp.core.workspace.WorkspaceManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentEngineTest {

    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var workspace: Workspace

    private val repo = Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)

    @Before
    fun setUp() {
        val root = Files.createTempDirectory("void-engine").toFile()
        workspaceManager = WorkspaceManager(root)
        workspace = workspaceManager.workspaceFor(repo)
    }

    private fun context() = ToolContext(workspace, AllowAllGate(), token = "gho_token")

    private fun model(id: String) = Model(
        id = id,
        providerId = "scripted",
        displayName = id,
        tier = Tier.WHITE_DWARF,
        contextWindow = 8_192,
    )

    private fun resolverFor(vararg providers: AiProvider) = object : ProviderResolver {
        override suspend fun targets(): List<ProviderTarget> = providers.map {
            ProviderTarget(it, model(it.id))
        }

        override suspend fun keyFor(providerId: String): String? = "key"
    }

    private fun engine(
        provider: AiProvider,
        tools: List<Tool>,
        limits: AgentLimits = AgentLimits(),
    ) = AgentEngine(
        registry = ToolRegistry(tools),
        resolver = resolverFor(provider),
        limits = limits,
    )

    @Test
    fun `the engine answers without using a tool`() = runTest {
        val provider = ScriptedProvider("scripted",
            listOf(listOf(ChatChunk.Delta("Hello"), ChatChunk.Finished("stop"))),
        )

        val events = engine(provider, emptyList()).runFor("say hello")

        assertTrue(events.first() is AgentEvent.Thinking)
        assertTrue(events.any { it is AgentEvent.Token && it.text == "Hello" })
        val last = events.last()
        assertTrue(last is AgentEvent.Finished)
        assertEquals("Hello", (last as AgentEvent.Finished).summary)
    }

    @Test
    fun `the engine runs a tool and feeds the result back`() = runTest {
        val call = ToolCall(
            id = "call_1",
            name = "write_file",
            arguments = buildJsonObject {
                put("path", "notes.txt")
                put("content", "hello")
            },
        )
        val provider = ScriptedProvider("scripted",
            listOf(
                listOf(ChatChunk.ToolCallDelta(call), ChatChunk.Finished("tool_calls")),
                listOf(ChatChunk.Delta("written"), ChatChunk.Finished("stop")),
            ),
        )

        val events = engine(provider, listOf(WriteFileTool(workspaceManager)))
            .runFor("write a file")

        assertTrue("events: $events", File(workspace.path, "notes.txt").exists())
        assertTrue(events.any { it is AgentEvent.ToolCallStarted })
        assertTrue(events.any { it is AgentEvent.ToolCallFinished })

        // The second request carries the tool result.
        val secondRequest = provider.requests[1]
        val toolMessage = secondRequest.messages.last { it.role == com.voidapp.core.ai.Role.TOOL }
        assertEquals("call_1", toolMessage.toolCallId)
        assertTrue(toolMessage.content.contains("notes.txt"))
    }

    @Test
    fun `the iteration limit stops a looping model`() = runTest {
        val provider = ScriptedProvider("scripted",
            List(50) {
                listOf(
                    ChatChunk.ToolCallDelta(
                        ToolCall("call", "noop", buildJsonObject { }),
                    ),
                    ChatChunk.Finished("tool_calls"),
                )
            },
        )

        val events = engine(
            provider,
            listOf(NoopTool()),
            limits = AgentLimits(maxIterations = 3),
        ).runFor("loop forever")

        val failure = events.last()
        assertTrue(failure is AgentEvent.Failed)
        assertTrue((failure as AgentEvent.Failed).message.contains("3 steps"))
        assertEquals(3, provider.requests.size)
    }

    @Test
    fun `a broken provider falls back to the next one`() = runTest {
        val broken = ScriptedProvider("broken", emptyList(), failure = "provider is down")
        val working = ScriptedProvider("working",
            listOf(listOf(ChatChunk.Delta("from the backup"), ChatChunk.Finished("stop"))),
        )

        val engine = AgentEngine(
            registry = ToolRegistry(emptyList()),
            resolver = resolverFor(broken, working),
        )

        val events = engine.runFor("say hello")

        assertTrue(events.any { it is AgentEvent.Token && it.text == "from the backup" })
        assertTrue(events.last() is AgentEvent.Finished)
    }

    @Test
    fun `without a provider the run fails immediately`() = runTest {
        val engine = AgentEngine(
            registry = ToolRegistry(emptyList()),
            resolver = resolverFor(),
        )

        val events = engine.runFor("say hello")

        val failure = events.single()
        assertTrue(failure is AgentEvent.Failed)
        assertTrue((failure as AgentEvent.Failed).message.contains("No AI provider"))
    }

    @Test
    fun `a tool that runs too long becomes an error`() = runTest {
        val call = ToolCall("call_1", "slow", buildJsonObject { })
        val provider = ScriptedProvider("scripted",
            listOf(listOf(ChatChunk.ToolCallDelta(call), ChatChunk.Finished("tool_calls"))),
        )

        val events = engine(
            provider,
            listOf(SlowTool()),
            limits = AgentLimits(toolTimeoutMillis = 50),
        ).runFor("wait")

        val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
        assertTrue(finished.result is ToolResult.Error)
        assertTrue((finished.result as ToolResult.Error).message.contains("timed out"))
    }

    @Test
    fun `a complex task gets a plan`() = runTest {
        val provider = ScriptedProvider("scripted",
            listOf(
                listOf(ChatChunk.Delta("1. read the file"), ChatChunk.Finished("stop")),
                listOf(ChatChunk.Delta("done"), ChatChunk.Finished("stop")),
            ),
        )

        val events = engine(provider, emptyList()).runFor("refactor the parser")

        val plan = events.filterIsInstance<AgentEvent.PlanUpdated>().single()
        assertEquals(listOf("read the file"), plan.steps)
    }

    /**
     * The engine runs on a real dispatcher: `runTest`'s virtual clock would
     * fire the tool timeout before a real file write finishes.
     */
    private suspend fun AgentEngine.runFor(task: String): List<AgentEvent> =
        withContext(Dispatchers.Default) { run(task, context()).toList() }

    @Test
    fun `a model that never answers falls back to the next one`() = runTest {
        val silent = SilentProvider("silent")
        val working = ScriptedProvider(
            "working",
            listOf(listOf(ChatChunk.Delta("from the backup"), ChatChunk.Finished("stop"))),
        )

        val engine = AgentEngine(
            registry = ToolRegistry(emptyList()),
            resolver = resolverFor(silent, working),
            limits = AgentLimits(modelTimeoutMillis = 50),
        )

        val events = withContext(Dispatchers.Default) { engine.run("say hello", context()).toList() }

        assertTrue(events.any { it is AgentEvent.Token && it.text == "from the backup" })
        assertTrue(events.last() is AgentEvent.Finished)
    }

    private class SilentProvider(override val id: String) : AiProvider {
        override val displayName: String get() = id

        override suspend fun validate(apiKey: String): Boolean = true

        override suspend fun listModels(apiKey: String): List<Model> = emptyList()

        override suspend fun chat(request: ChatRequest, apiKey: String): Flow<ChatChunk> =
            kotlinx.coroutines.flow.flow {
                kotlinx.coroutines.delay(60_000)
            }
    }

    private class ScriptedProvider(
        override val id: String,
        private val script: List<List<ChatChunk>>,
        private val failure: String? = null,
    ) : AiProvider {
        override val displayName: String = "Scripted"

        val requests = mutableListOf<ChatRequest>()

        override suspend fun validate(apiKey: String): Boolean = true

        override suspend fun listModels(apiKey: String): List<Model> = emptyList()

        override suspend fun chat(request: ChatRequest, apiKey: String): Flow<ChatChunk> {
            requests += request
            failure?.let { throw IllegalStateException(it) }
            val index = requests.size - 1
            val chunks = script.getOrElse(index) { listOf(ChatChunk.Finished("stop")) }
            return chunks.asFlow()
        }
    }

    private class WriteFileTool(
        private val workspaceManager: WorkspaceManager,
    ) : Tool {
        override val name: String = "write_file"
        override val description: String = "writes a file"
        override val parameters: JsonObject = buildJsonObject { put("type", "object") }

        override suspend fun execute(
            arguments: JsonObject,
            ctx: ToolContext,
        ): ToolResult {
            val path = arguments["path"].toString().trim('"')
            val content = arguments["content"].toString().trim('"')
            workspaceManager.writeFile(ctx.workspace, path, content)
            return ToolResult.Success("Wrote $path")
        }
    }

    private class NoopTool : Tool {
        override val name: String = "noop"
        override val description: String = "does nothing"
        override val parameters: JsonObject = buildJsonObject { put("type", "object") }

        override suspend fun execute(
            arguments: JsonObject,
            ctx: ToolContext,
        ): ToolResult = ToolResult.Success("ok")
    }

    private class SlowTool : Tool {
        override val name: String = "slow"
        override val description: String = "takes too long"
        override val parameters: JsonObject = buildJsonObject { put("type", "object") }

        override suspend fun execute(
            arguments: JsonObject,
            ctx: ToolContext,
        ): ToolResult {
            delay(5_000)
            return ToolResult.Success("late")
        }
    }
}
