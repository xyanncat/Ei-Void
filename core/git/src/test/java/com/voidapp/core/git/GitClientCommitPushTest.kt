package com.voidapp.core.git

import com.voidapp.core.common.github.Repo
import com.voidapp.core.workspace.GitResult
import com.voidapp.core.workspace.Workspace
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitClientCommitPushTest {

    private lateinit var bare: File
    private lateinit var workspace: Workspace

    private val repo = Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)
    private val client = GitClient()

    @Before
    fun setUp() {
        bare = createBareWithInitialCommit()
        val cloneRoot = Files.createTempDirectory("void-clones").toFile()

        runBlocking {
            val result = CloneManager(cloneRoot) { bare.toURI().toASCIIString() }
                .clone(repo, "token") {}
            workspace = (result as GitResult.Success).workspace
        }
    }

    @Test
    fun `commit and push to a local remote`() = runTest {
        File(workspace.path, "new.txt").writeText("hello")

        val commit = client.commit(workspace, "add new.txt")
        val push = client.push(workspace, "token")

        assertTrue(commit is GitResult.Success)
        assertTrue(push is GitResult.Success)
        assertTrue(remoteMessages().contains("add new.txt"))
    }

    @Test
    fun `commit with no changes fails cleanly`() = runTest {
        val result = client.commit(workspace, "empty")

        assertTrue(result is GitResult.Failure)
        assertFalse(remoteMessages().contains("empty"))
    }

    @Test
    fun `status reports untracked and modified files`() = runTest {
        File(workspace.path, "new.txt").writeText("hello")
        File(workspace.path, "README.md").appendText("more\n")

        val status = client.status(workspace)

        assertTrue(status.untracked.contains("new.txt"))
        assertTrue(status.modified.contains("README.md"))
    }

    @Test
    fun `log returns the latest commit first`() = runTest {
        File(workspace.path, "new.txt").writeText("hello")
        client.commit(workspace, "add new.txt")

        val log = client.log(workspace)

        assertEquals("add new.txt", log.first().message)
    }

    @Test
    fun `branches lists the current branch`() = runTest {
        val branches = client.branches(workspace)

        assertTrue(branches.any { it.name == "main" && it.current })
    }

    @Test
    fun `checkout creates a branch that does not exist yet`() = runTest {
        val result = client.checkout(workspace, "feature")

        assertTrue(result is GitResult.Success)
        assertTrue(client.branches(workspace).any { it.name == "feature" && it.current })
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
        val repository = FileRepositoryBuilder().setGitDir(bare).build()
        return Git(repository).use { git -> git.log().call().map { it.fullMessage.trim() } }
    }
}
