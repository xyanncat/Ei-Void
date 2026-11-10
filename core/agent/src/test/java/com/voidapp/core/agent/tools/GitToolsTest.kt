package com.voidapp.core.agent.tools

import com.voidapp.core.agent.AllowAllGate
import com.voidapp.core.agent.Tool
import com.voidapp.core.agent.ToolContext
import com.voidapp.core.agent.ToolRegistry
import com.voidapp.core.agent.ToolResult
import com.voidapp.core.common.github.Repo
import com.voidapp.core.git.CloneManager
import com.voidapp.core.git.GitClient
import com.voidapp.core.workspace.GitResult
import com.voidapp.core.workspace.Workspace
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitToolsTest {

    private lateinit var bare: File
    private lateinit var workspace: Workspace
    private lateinit var registry: ToolRegistry

    private val repo = Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)
    private val client = GitClient()

    @Before
    fun setUp() {
        bare = createBareWithInitialCommit()
        val cloneRoot = Files.createTempDirectory("void-agent-clones").toFile()

        runBlocking {
            val result = CloneManager(cloneRoot, remoteUrlFor = { bare.toURI().toASCIIString() })
                .clone(repo, "token") {}
            workspace = (result as GitResult.Success).workspace
        }

        registry = ToolRegistry(GitTools(client).all())
    }

    private suspend fun call(
        name: String,
        vararg arguments: Pair<String, Any>,
        token: String? = "gho_token",
    ): ToolResult {
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
        return registry.execute(name, json, ToolContext(workspace, AllowAllGate(), token))
    }

    @Test
    fun `git_status reports an untracked file`() = runTest {
        File(workspace.path, "new.txt").writeText("hello")

        val result = call("git_status")

        assertTrue("unexpected: $result", result is ToolResult.Success)
        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("new.txt"))
        assertTrue(output.contains("Untracked"))
    }

    @Test
    fun `git_status says the tree is clean`() = runTest {
        val result = call("git_status")

        assertTrue((result as ToolResult.Success).output.contains("clean"))
    }

    @Test
    fun `git_diff shows the change`() = runTest {
        File(workspace.path, "README.md").appendText("more\n")

        val result = call("git_diff")

        assertTrue("unexpected: $result", result is ToolResult.Success)
        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("+more"))
        assertTrue(output.contains("README.md"))
    }

    @Test
    fun `git_diff can be limited to one file`() = runTest {
        File(workspace.path, "README.md").appendText("more\n")
        File(workspace.path, "other.txt").writeText("other\n")

        val result = call("git_diff", "path" to "README.md")

        assertTrue("unexpected: $result", result is ToolResult.Success)
        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("README.md"))
        assertFalse(output.contains("other.txt"))
    }

    @Test
    fun `git_diff reports no changes`() = runTest {
        val result = call("git_diff")

        assertTrue((result as ToolResult.Success).output.contains("No changes"))
    }

    @Test
    fun `git_commit commits everything`() = runTest {
        File(workspace.path, "new.txt").writeText("hello")

        val result = call("git_commit", "message" to "add new.txt")

        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).output.contains("add new.txt"))
        // The commit is local; the remote only learns about it after a push.
        assertTrue(client.log(workspace, 1).first().message.contains("add new.txt"))
        assertFalse(remoteMessages().contains("add new.txt"))
    }

    @Test
    fun `git_commit without changes fails`() = runTest {
        val result = call("git_commit", "message" to "nothing")

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("Nothing to commit"))
    }

    @Test
    fun `git_log shows the commits`() = runTest {
        File(workspace.path, "new.txt").writeText("hello")
        call("git_commit", "message" to "add new.txt")

        val result = call("git_log", "limit" to 5)

        assertTrue("unexpected: $result", result is ToolResult.Success)
        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("add new.txt"))
    }

    @Test
    fun `git_push pushes to the remote`() = runTest {
        File(workspace.path, "new.txt").writeText("hello")
        call("git_commit", "message" to "add new.txt")

        val result = call("git_push")

        assertTrue(result is ToolResult.Success)
        assertTrue(remoteMessages().contains("add new.txt"))
    }

    @Test
    fun `git_push without a token fails`() = runTest {
        val result = call("git_push", token = null)

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("Not signed in"))
    }

    @Test
    fun `git_branch marks the current branch`() = runTest {
        val result = call("git_branch")

        assertTrue("unexpected: $result", result is ToolResult.Success)
        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("* main"))
    }

    @Test
    fun `git_checkout switches the branch`() = runTest {
        val result = call("git_checkout", "branch" to "feature")

        assertTrue(result is ToolResult.Success)
        assertTrue(client.branches(workspace).any { it.name == "feature" && it.current })
    }

    @Test
    fun `every git tool is registered`() {
        assertEquals(
            listOf(
                "git_branch",
                "git_checkout",
                "git_commit",
                "git_diff",
                "git_log",
                "git_push",
                "git_status",
            ),
            registry.names,
        )
    }

    private fun createBareWithInitialCommit(): File {
        val bareRepo = Files.createTempDirectory("void-remote").toFile()
        Git.init().setBare(true).setInitialBranch("main").setDirectory(bareRepo).call().close()

        val seed = Files.createTempDirectory("void-seed").toFile()
        Git.init().setDirectory(seed).setInitialBranch("main").call().use { git ->
            File(seed, "README.md").writeText("# Seed\n")
            git.add().addFilepattern(".").call()
            git.commit()
                .setMessage("init")
                .setAuthor("Void", "void@localhost")
                .setCommitter("Void", "void@localhost")
                .call()
            git.push().setRemote(bareRepo.toURI().toASCIIString()).call()
        }
        return bareRepo
    }

    private fun remoteMessages(): List<String> {
        val repository = org.eclipse.jgit.storage.file.FileRepositoryBuilder()
            .setGitDir(bare)
            .build()
        return Git(repository).use { git -> git.log().call().map { it.fullMessage.trim() } }
    }
}
