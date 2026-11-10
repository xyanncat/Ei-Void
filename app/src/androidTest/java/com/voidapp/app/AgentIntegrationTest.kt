package com.voidapp.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.voidapp.core.agent.AgentEngine
import com.voidapp.core.agent.AllowAllGate
import com.voidapp.core.agent.ProviderResolver
import com.voidapp.core.agent.ProviderTarget
import com.voidapp.core.agent.ToolContext
import com.voidapp.core.agent.ToolRegistry
import com.voidapp.core.agent.tools.FileTools
import com.voidapp.core.agent.tools.GitTools
import com.voidapp.core.ai.AiProvider
import com.voidapp.core.ai.ChatChunk
import com.voidapp.core.ai.ChatRequest
import com.voidapp.core.ai.Model
import com.voidapp.core.ai.Tier
import com.voidapp.core.ai.ToolCall
import com.voidapp.core.common.github.Repo
import com.voidapp.core.git.CloneManager
import com.voidapp.core.git.GitClient
import com.voidapp.core.workspace.GitResult
import com.voidapp.core.workspace.WorkspaceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real agent stack on a device: the engine, the file and git
 * tools, the workspace manager and JGit, against a local `file://` remote.
 *
 * The model is scripted, so the test needs no network and no key.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AgentIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

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
        base = File(context.cacheDir, "agent-integration-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        base.deleteRecursively()
    }

    @Test
    fun theAgentEditsCommitsAndPushes() = runBlocking {
        val remote = createRemoteWithInitialCommit()
        val repo = Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)

        val cloned = CloneManager(File(base, "clones"), remoteUrlFor = { remote.toURI().toASCIIString() })
            .clone(repo, "gho_test") {}
        assertTrue(cloned is GitResult.Success)
        val workspace = (cloned as GitResult.Success).workspace

        val registry = ToolRegistry(
            FileTools(workspaceManager).all() + GitTools(gitClient).all(),
        )

        val provider = ScriptedProvider(
            listOf(
                listOf(
                    ChatChunk.ToolCallDelta(
                        ToolCall(
                            id = "call_1",
                            name = "write_file",
                            arguments = buildJsonObject {
                                put("path", "notes.txt")
                                put("content", "written by the agent\n")
                            },
                        ),
                    ),
                    ChatChunk.Finished("tool_calls"),
                ),
                listOf(
                    ChatChunk.ToolCallDelta(
                        ToolCall(
                            id = "call_2",
                            name = "git_commit",
                            arguments = buildJsonObject { put("message", "add notes") },
                        ),
                    ),
                    ChatChunk.Finished("tool_calls"),
                ),
                listOf(
                    ChatChunk.ToolCallDelta(
                        ToolCall("call_3", "git_push", buildJsonObject { }),
                    ),
                    ChatChunk.Finished("tool_calls"),
                ),
                listOf(ChatChunk.Delta("Done."), ChatChunk.Finished("stop")),
            ),
        )

        val engine = AgentEngine(registry, resolverFor(provider))

        val events = engine.run(
            "write notes.txt, commit it and push",
            ToolContext(workspace, AllowAllGate(), token = "gho_test"),
        ).toList()

        assertTrue(File(workspace.path, "notes.txt").exists())
        assertTrue(remoteMessages(remote).contains("add notes"))
        assertTrue(events.last() is com.voidapp.core.agent.AgentEvent.Finished)
    }

    private fun resolverFor(provider: AiProvider) = object : ProviderResolver {
        override suspend fun targets(): List<ProviderTarget> = listOf(
            ProviderTarget(
                provider,
                Model("scripted", "scripted", "scripted", Tier.STANDARD, 8_192),
            ),
        )

        override suspend fun keyFor(providerId: String): String = "key"
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

    private class ScriptedProvider(
        private val script: List<List<ChatChunk>>,
    ) : AiProvider {
        override val id: String = "scripted"
        override val displayName: String = "Scripted"
        private var calls = 0

        override suspend fun validate(apiKey: String): Boolean = true

        override suspend fun listModels(apiKey: String): List<Model> = emptyList()

        override suspend fun chat(request: ChatRequest, apiKey: String): Flow<ChatChunk> {
            val chunks = script.getOrElse(calls) { listOf(ChatChunk.Finished("stop")) }
            calls++
            return chunks.asFlow()
        }
    }
}
