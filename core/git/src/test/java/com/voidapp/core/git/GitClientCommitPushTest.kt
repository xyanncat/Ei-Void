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
            val result = CloneManager(cloneRoot, remoteUrlFor = { bare.toURI().toASCIIString() })
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
    fun `a staged file is not reported as an unstaged change`() = runTest {
        File(workspace.path, "new.txt").writeText("hello")
        Git.open(workspace.path).use { it.add().addFilepattern("new.txt").call() }

        val status = client.status(workspace)

        assertTrue(status.staged.contains("new.txt"))
        assertFalse(status.modified.contains("new.txt"))
    }

    @Test
    fun `a diverged branch is reported as a conflict`() = runTest {
        val otherRoot = Files.createTempDirectory("void-clones-other").toFile()
        val other = runBlocking {
            val result = CloneManager(otherRoot, remoteUrlFor = { bare.toURI().toASCIIString() })
                .clone(repo, "token") {}
            (result as GitResult.Success).workspace
        }

        File(workspace.path, "first.txt").writeText("first")
        client.commit(workspace, "first commit")
        client.push(workspace, "token")

        File(other.path, "second.txt").writeText("second")
        client.commit(other, "second commit")
        val push = client.push(other, "token")

        assertTrue(push is GitResult.Conflict)
        assertFalse(remoteMessages().contains("second commit"))
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

    @Test
    fun `checkout of a remote only branch starts at the remote tip`() = runTest {
        val result = client.checkout(workspace, "remote-only")

        assertTrue(result is GitResult.Success)
        assertTrue(File(workspace.path, "remote-only.txt").exists())
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

            // A branch that only exists on the remote.
            git.checkout().setCreateBranch(true).setName("remote-only").call()
            File(seed, "remote-only.txt").writeText("remote only\n")
            git.add().addFilepattern(".").call()
            git.commit()
                .setMessage("remote only")
                .setAuthor("Void", "void@localhost")
                .setCommitter("Void", "void@localhost")
                .call()

            git.push()
                .setRemote(bareRepo.toURI().toASCIIString())
                .setPushAll()
                .call()
        }
        return bareRepo
    }

    private fun remoteMessages(): List<String> {
        val repository = FileRepositoryBuilder().setGitDir(bare).build()
        return Git(repository).use { git -> git.log().call().map { it.fullMessage.trim() } }
    }
}
