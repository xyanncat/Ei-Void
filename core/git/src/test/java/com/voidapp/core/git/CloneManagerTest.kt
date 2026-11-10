package com.voidapp.core.git

import com.voidapp.core.common.github.Repo
import com.voidapp.core.workspace.GitResult
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloneManagerTest {

    private lateinit var origin: File
    private lateinit var cloneRoot: File

    private val repo = Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)

    @Before
    fun setUp() {
        cloneRoot = Files.createTempDirectory("void-clones").toFile()
        origin = Files.createTempDirectory("void-origin").toFile()

        val git = Git.init().setDirectory(origin).setInitialBranch("main").call()
        File(origin, "README.md").writeText("# Hello\n")
        git.add().addFilepattern(".").call()
        git.commit()
            .setMessage("init")
            .setAuthor("Void", "void@localhost")
            .setCommitter("Void", "void@localhost")
            .call()
        git.close()
    }

    private fun manager() = CloneManager(cloneRoot, remoteUrlFor = { origin.toURI().toASCIIString() })

    @Test
    fun `clone creates a workspace with the repo files`() = runTest {
        val result = manager().clone(repo, "token") {}

        assertTrue(result is GitResult.Success)
        val workspace = (result as GitResult.Success).workspace
        assertTrue(File(workspace.path, "README.md").exists())
    }

    @Test
    fun `clone reports progress`() = runTest {
        val progress = mutableListOf<CloneProgress>()

        manager().clone(repo, "token") { progress.add(it) }

        assertTrue(progress.isNotEmpty())
        assertEquals(100, progress.last().percent)
    }

    @Test
    fun `cloning an existing workspace reuses it`() = runTest {
        val first = manager().clone(repo, "token") {}
        assertTrue(first is GitResult.Success)
        val workspace = (first as GitResult.Success).workspace
        val marker = File(workspace.path, "marker.txt").apply { writeText("marker") }

        val second = manager().clone(repo, "token") {}

        assertTrue(second is GitResult.Success)
        // A fresh clone into a non-empty directory would fail and remove the
        // workspace, so the surviving marker proves the workspace was reused.
        assertTrue(marker.exists())
    }

    @Test
    fun `the clone url carries no credentials`() {
        val url = githubRemoteUrl(repo)

        assertFalse(url.contains("@"))
        assertFalse(url.contains("gho_"))
        assertEquals("https://github.com/owner/repo.git", url)
    }

    @Test
    fun `the token goes to the credential provider and not to the url`() = runTest {
        var seenToken: String? = null
        val manager = CloneManager(
            workspaceRoot = cloneRoot,
            remoteUrlFor = { "https://127.0.0.1:1/owner/repo.git" },
            credentialsFor = { token ->
                seenToken = token
                UsernamePasswordCredentialsProvider("x-access-token", token)
            },
        )

        // The host does not exist, so the clone fails - but the token must
        // have been handed to the credential provider.
        val result = manager.clone(repo, "gho_supersecret") {}

        assertTrue(result is GitResult.Failure)
        assertEquals("gho_supersecret", seenToken)
    }

    @Test
    fun `a cloned repository stores no token in git config`() = runTest {
        manager().clone(repo, "gho_supersecret") {}

        val config = File(cloneRoot, "owner/repo/.git/config").readText()

        assertFalse(config.contains("gho_supersecret"))
    }

    @Test
    fun `a failed clone leaves no partial workspace`() = runTest {
        val failing = CloneManager(cloneRoot, remoteUrlFor = { "https://127.0.0.1:1/does/not/exist.git" })

        val result = failing.clone(repo, "token") {}

        assertTrue(result is GitResult.Failure)
        assertFalse(File(cloneRoot, "owner/repo").exists())
    }
}
