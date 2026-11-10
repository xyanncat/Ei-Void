package com.voidapp.core.agent.tools

import com.voidapp.core.agent.Tool
import com.voidapp.core.agent.ToolContext
import com.voidapp.core.agent.ToolRegistry
import com.voidapp.core.agent.ToolResult
import com.voidapp.core.common.github.Repo
import com.voidapp.core.workspace.Workspace
import com.voidapp.core.workspace.WorkspaceManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileToolsTest {

    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var workspace: Workspace
    private lateinit var registry: ToolRegistry

    private val repo = Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)

    @Before
    fun setUp() {
        val root = Files.createTempDirectory("void-tools").toFile()
        workspaceManager = WorkspaceManager(root)
        workspace = workspaceManager.workspaceFor(repo)
        File(workspace.path, "src").mkdirs()
        File(workspace.path, "README.md").writeText("# Title\n\nSome text.\n")
        File(workspace.path, "src/Main.kt").writeText("fun main() {\n    println(\"hi\")\n}\n")
        registry = ToolRegistry(FileTools(workspaceManager).all())
    }

    private suspend fun call(name: String, vararg arguments: Pair<String, Any>): ToolResult {
        val json = buildJsonObject {
            arguments.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Int -> put(key, value)
                    is Boolean -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
        return registry.execute(name, json, ToolContext(workspace, com.voidapp.core.agent.AllowAllGate()))
    }

    @Test
    fun `list_files shows the files of a directory`() = runTest {
        val result = call("list_files", "path" to ".")

        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("README.md"))
        assertTrue(output.contains("src/"))
    }

    @Test
    fun `read_file returns numbered lines`() = runTest {
        val result = call("read_file", "path" to "README.md")

        val output = (result as ToolResult.Success).output
        assertTrue(output.startsWith("1\t# Title"))
        assertTrue(output.contains("3\tSome text."))
    }

    @Test
    fun `write_file creates a file`() = runTest {
        val result = call("write_file", "path" to "docs/new.md", "content" to "hello")

        assertTrue(result is ToolResult.Success)
        assertEquals("hello", File(workspace.path, "docs/new.md").readText())
    }

    @Test
    fun `create_file refuses an existing file`() = runTest {
        val result = call("create_file", "path" to "README.md", "content" to "x")

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("already exists"))
    }

    @Test
    fun `create_file makes a new file`() = runTest {
        val result = call("create_file", "path" to "docs/new.md", "content" to "hello")

        assertTrue(result is ToolResult.Success)
        assertEquals("hello", File(workspace.path, "docs/new.md").readText())
    }

    @Test
    fun `edit_file replaces one occurrence`() = runTest {
        val result = call(
            "edit_file",
            "path" to "README.md",
            "old_text" to "Some text.",
            "new_text" to "Other text.",
        )

        assertTrue(result is ToolResult.Success)
        val text = File(workspace.path, "README.md").readText()
        assertTrue(text.contains("Other text."))
        assertTrue(text.endsWith("\n"))
    }

    @Test
    fun `edit_file fails when the text is missing`() = runTest {
        val result = call(
            "edit_file",
            "path" to "README.md",
            "old_text" to "not in the file",
            "new_text" to "x",
        )

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("not found"))
    }

    @Test
    fun `edit_file fails when the text is ambiguous`() = runTest {
        File(workspace.path, "dup.txt").writeText("same\nsame\n")

        val result = call(
            "edit_file",
            "path" to "dup.txt",
            "old_text" to "same",
            "new_text" to "other",
        )

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("occurs 2 times"))
    }

    @Test
    fun `search_files finds text`() = runTest {
        val result = call("search_files", "query" to "println")

        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("src/Main.kt:2"))
    }

    @Test
    fun `search_files honours a glob`() = runTest {
        val result = call("search_files", "query" to "Title", "glob" to "*.md")

        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("README.md"))
    }

    @Test
    fun `search_files can use a regular expression`() = runTest {
        val result = call("search_files", "query" to "fun\\s+main", "regex" to true)

        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("src/Main.kt"))
    }

    @Test
    fun `search_files reports no matches`() = runTest {
        val result = call("search_files", "query" to "nothing here")

        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).output.contains("No matches"))
    }

    @Test
    fun `delete_file needs confirmation`() {
        val tool = registry.get("delete_file")

        assertTrue(tool?.requiresConfirmation == true)
    }

    @Test
    fun `delete_file removes the file`() = runTest {
        val result = call("delete_file", "path" to "README.md")

        assertTrue(result is ToolResult.Success)
        assertFalse(File(workspace.path, "README.md").exists())
    }

    @Test
    fun `a path outside the workspace is refused`() = runTest {
        val result = call("read_file", "path" to "../../../etc/passwd")

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `every file tool is registered`() {
        assertEquals(
            listOf("create_file", "delete_file", "edit_file", "list_files", "read_file", "search_files", "write_file"),
            registry.names,
        )
    }
}
