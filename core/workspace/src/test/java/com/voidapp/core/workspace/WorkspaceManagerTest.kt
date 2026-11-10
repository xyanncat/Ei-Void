package com.voidapp.core.workspace

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkspaceManagerTest {

    private lateinit var manager: WorkspaceManager
    private lateinit var workspace: Workspace

    @Before
    fun setUp() {
        val root = Files.createTempDirectory("void-workspaces").toFile()
        val repoPath = File(root, "owner/repo")
        File(repoPath, "src/main").mkdirs()
        File(repoPath, "README.md").writeText("# Readme\n")
        File(repoPath, ".git").mkdirs()

        val repo = com.voidapp.core.common.github.Repo(
            "1", "owner", "repo", "owner/repo", false, "main", null, 0L,
        )
        manager = WorkspaceManager(root)
        workspace = manager.workspaceFor(repo)
    }

    @Test
    fun `listFiles returns files and directories`() = runTest {
        val files = manager.listFiles(workspace)

        assertTrue(files.any { it.name == "src" && it.isDirectory })
        assertTrue(files.any { it.name == "README.md" && !it.isDirectory })
    }

    @Test
    fun `listFiles hides the git directory`() = runTest {
        val files = manager.listFiles(workspace)

        assertFalse(files.any { it.name == ".git" })
    }

    @Test
    fun `listFiles sorts directories before files`() = runTest {
        val files = manager.listFiles(workspace)

        assertEquals("src", files.first().name)
    }

    @Test
    fun `listFiles navigates into a subdirectory`() = runTest {
        val files = manager.listFiles(workspace, "src")

        assertTrue(files.any { it.name == "main" && it.isDirectory })
    }

    @Test
    fun `readFile truncates beyond the line cap`() = runTest {
        File(workspace.path, "big.txt").writeText((1..5000).joinToString("\n"))

        val content = manager.readFile(workspace, "big.txt")

        assertTrue(content.truncated)
        assertEquals(WorkspaceManager.WORKSPACE_LINE_CAP, content.lines.size)
    }

    @Test
    fun `readFile respects a line range`() = runTest {
        File(workspace.path, "small.txt").writeText("one\ntwo\nthree")

        val content = manager.readFile(workspace, "small.txt", startLine = 2, endLine = 3)

        assertEquals(listOf("two", "three"), content.lines)
        assertFalse(content.truncated)
    }

    @Test
    fun `writeFile creates nested paths`() = runTest {
        val result = manager.writeFile(workspace, "src/main/kotlin/New.kt", "fun main() {}")

        assertTrue(result is GitResult.Success)
        assertTrue(File(workspace.path, "src/main/kotlin/New.kt").exists())
    }

    @Test
    fun `deleteFile removes the file`() = runTest {
        manager.writeFile(workspace, "temp.txt", "x")

        val result = manager.deleteFile(workspace, "temp.txt")

        assertTrue(result is GitResult.Success)
        assertFalse(File(workspace.path, "temp.txt").exists())
    }

    @Test
    fun `paths cannot escape the workspace`() = runTest {
        val escaped = runCatching { manager.readFile(workspace, "../../../etc/passwd") }

        assertTrue(escaped.isFailure)
    }
}
