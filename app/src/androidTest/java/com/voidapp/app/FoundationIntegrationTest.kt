package com.voidapp.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.voidapp.core.common.github.Repo
import com.voidapp.core.database.RepoDao
import com.voidapp.core.database.RepoEntity
import com.voidapp.core.git.CloneManager
import com.voidapp.core.git.GitClient
import com.voidapp.core.security.SecretStore
import com.voidapp.core.workspace.GitResult
import com.voidapp.core.workspace.WorkspaceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Joins the parts that only work on a real device: the AndroidKeyStore-backed
 * secret store, Room over the device's SQLite, and JGit over real files.
 *
 * Network calls to GitHub are covered by unit tests with fakes; here the
 * "remote" is a local `file://` repository so the test needs no network.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FoundationIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var secretStore: SecretStore

    @Inject
    lateinit var repoDao: RepoDao

    @Inject
    lateinit var workspaceManager: WorkspaceManager

    @Inject
    lateinit var gitClient: GitClient

    @Inject
    @ApplicationContext
    lateinit var context: Context

    private lateinit var base: File

    @Before
    fun setUp() {
        hiltRule.inject()
        base = File(context.cacheDir, "foundation-integration-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        secretStore.clearAll()
        runBlocking { repoDao.deleteAll() }
        base.deleteRecursively()
    }

    @Test
    fun loginToPushRoundTrip() = runBlocking {
        val remote = createRemoteWithInitialCommit()
        val repo = Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)

        // Login: the device-flow token is persisted encrypted.
        secretStore.saveToken("gho_integration")
        assertEquals("gho_integration", secretStore.getToken())

        // Repo listing: the fetched repository is cached in Room.
        repoDao.insertAll(
            listOf(
                RepoEntity(
                    id = "1",
                    owner = "owner",
                    name = "repo",
                    fullName = "owner/repo",
                    isPrivate = false,
                    defaultBranch = "main",
                    description = null,
                    updatedAt = 0L,
                ),
            ),
        )
        assertTrue(repoDao.observeAll().first().any { it.fullName == "owner/repo" })

        // Clone into a workspace.
        val cloneRoot = File(base, "clones")
        val cloned = CloneManager(cloneRoot) { remote.toURI().toASCIIString() }
            .clone(repo, "gho_integration") {}
        assertTrue(cloned is GitResult.Success)
        val workspace = (cloned as GitResult.Success).workspace

        // Read and edit through the workspace API.
        val readme = workspaceManager.readFile(workspace, "README.md")
        assertTrue(readme.lines.any { it.contains("Seed") })
        assertTrue(workspaceManager.writeFile(workspace, "notes.txt", "written by the agent\n") is GitResult.Success)

        // Commit and push.
        assertTrue(gitClient.commit(workspace, "add notes") is GitResult.Success)
        assertTrue(gitClient.push(workspace, "gho_integration") is GitResult.Success)

        // The remote really received the commit.
        assertTrue(remoteMessages(remote).contains("add notes"))
        // ...and the workspace shows the edited file.
        assertTrue(File(workspace.path, "notes.txt").exists())
        assertTrue(workspaceManager.listFiles(workspace).any { it.name == "notes.txt" })
    }

    private fun createRemoteWithInitialCommit(): File {
        val remote = File(base, "remote.git").apply { mkdirs() }
        Git.init().setBare(true).setInitialBranch("main").setDirectory(remote).call().close()

        val seed = File(base, "seed").apply { mkdirs() }
        Git.init().setDirectory(seed).setInitialBranch("main").call().use { git ->
            File(seed, "README.md").writeText("# Seed\n")
            git.add().addFilepattern(".").call()
            git.commit()
                .setMessage("init")
                .setAuthor("Void", "void@localhost")
                .setCommitter("Void", "void@localhost")
                .call()
            git.push().setRemote(remote.toURI().toASCIIString()).call()
        }
        return remote
    }

    private fun remoteMessages(remote: File): List<String> {
        val repository = FileRepositoryBuilder().setGitDir(remote).build()
        return Git(repository).use { git -> git.log().call().map { it.fullMessage.trim() } }
    }
}
