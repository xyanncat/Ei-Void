package com.voidapp.core.agent

import com.voidapp.core.workspace.Workspace
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    private val workspace = Workspace(
        path = java.io.File("/tmp/void-test"),
        repo = com.voidapp.core.common.github.Repo(
            "1", "owner", "repo", "owner/repo", false, "main", null, 0L,
        ),
    )

    @Test
    fun `definitions list every tool`() {
        val registry = ToolRegistry(listOf(EchoTool(), FailingTool(), NeedsConfirmationTool()))

        val definitions = registry.definitions()

        assertEquals(3, definitions.size)
        assertEquals(listOf("echo", "failing", "needs_confirmation"), definitions.map { it.name })
        assertTrue(definitions.first().description.isNotBlank())
    }

    @Test
    fun `an unknown tool is an error, not a crash`() = runTest {
        val registry = ToolRegistry(listOf(EchoTool()))

        val result = registry.execute("nope", JsonObject(emptyMap()), context())

        assertEquals(ToolResult.Error("Unknown tool 'nope'. Available: echo"), result)
    }

    @Test
    fun `a failing tool comes back as an error`() = runTest {
        val registry = ToolRegistry(listOf(FailingTool()))

        val result = registry.execute("failing", JsonObject(emptyMap()), context())

        assertTrue(result is ToolResult.Error)
        assertEquals("boom", (result as ToolResult.Error).message)
    }

    @Test
    fun `a tool that needs confirmation does not run when the user declines`() = runTest {
        val tool = NeedsConfirmationTool()
        val registry = ToolRegistry(listOf(tool))
        val gate = RecordingGate(approved = false)

        val result = registry.execute(
            "needs_confirmation",
            JsonObject(emptyMap()),
            context(gate),
        )

        assertTrue(result is ToolResult.Error)
        assertTrue(gate.asked)
        assertFalse(tool.ran)
    }

    @Test
    fun `a tool that needs confirmation runs when the user agrees`() = runTest {
        val tool = NeedsConfirmationTool()
        val registry = ToolRegistry(listOf(tool))
        val gate = RecordingGate(approved = true)

        val result = registry.execute(
            "needs_confirmation",
            JsonObject(emptyMap()),
            context(gate),
        )

        assertTrue(result is ToolResult.Success)
        assertTrue(gate.asked)
        assertTrue(tool.ran)
    }

    @Test
    fun `tool arguments are read strictly`() {
        val arguments = ToolArguments(
            buildJsonObject {
                put("path", "a.txt")
                put("count", 3)
                put("flag", true)
                put("empty", "")
            },
        )

        assertEquals("a.txt", arguments.requiredString("path"))
        assertEquals(3, arguments.optionalInt("count"))
        assertTrue(arguments.optionalBoolean("flag"))
        assertNull(arguments.optionalString("missing"))
        assertNull(arguments.optionalInt("missing"))
        assertFalse(arguments.optionalBoolean("missing"))
        assertNull(arguments.optionalString("empty"))
    }

    @Test
    fun `a missing required argument is reported`() {
        val arguments = ToolArguments(buildJsonObject { put("other", "x") })

        val error = runCatching { arguments.requiredString("path") }.exceptionOrNull()

        assertTrue(error is ToolArgumentException)
        assertEquals("Missing required argument 'path'", error?.message)
    }

    private fun context(gate: ConfirmationGate = AllowAllGate()) = ToolContext(
        workspace = workspace,
        gate = gate,
    )

    private class RecordingGate(private val approved: Boolean) : ConfirmationGate {
        var asked: Boolean = false
            private set

        override suspend fun confirm(request: ConfirmationRequest): Boolean {
            asked = true
            return approved
        }
    }

    private class EchoTool : Tool {
        override val name: String = "echo"
        override val description: String = "echoes the text"
        override val parameters: JsonObject = buildJsonObject {
            put("type", "object")
        }

        override suspend fun execute(
            arguments: JsonObject,
            ctx: ToolContext,
        ): ToolResult = ToolResult.Success("echo")
    }

    private class FailingTool : Tool {
        override val name: String = "failing"
        override val description: String = "always fails"
        override val parameters: JsonObject = buildJsonObject {
            put("type", "object")
        }

        override suspend fun execute(
            arguments: JsonObject,
            ctx: ToolContext,
        ): ToolResult = throw IllegalStateException("boom")
    }

    private class NeedsConfirmationTool : Tool {
        override val name: String = "needs_confirmation"
        override val description: String = "deletes things"
        override val parameters: JsonObject = buildJsonObject {
            put("type", "object")
        }

        override val requiresConfirmation: Boolean = true

        var ran: Boolean = false
            private set

        override suspend fun execute(
            arguments: JsonObject,
            ctx: ToolContext,
        ): ToolResult {
            ran = true
            return ToolResult.Success("done")
        }
    }
}
