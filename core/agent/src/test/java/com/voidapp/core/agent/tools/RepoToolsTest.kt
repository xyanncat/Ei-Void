package com.voidapp.core.agent.tools

import com.voidapp.core.agent.AllowAllGate
import com.voidapp.core.agent.DenyAllGate
import com.voidapp.core.agent.ToolContext
import com.voidapp.core.agent.ToolRegistry
import com.voidapp.core.agent.ToolResult
import com.voidapp.core.common.github.GitHubApi
import com.voidapp.core.common.github.Repo
import com.voidapp.core.workspace.Workspace
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RepoToolsTest {

    private lateinit var api: FakeGitHubApi
    private lateinit var registry: ToolRegistry

    private val repo = Repo("1", "owner", "repo", "owner/repo", false, "main", "a repo", 0L)

    @Before
    fun setUp() {
        api = FakeGitHubApi(listOf(repo))
        registry = ToolRegistry(RepoTools(api).all())
    }

    private suspend fun call(
        name: String,
        vararg arguments: Pair<String, Any>,
        allow: Boolean = true,
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
        val gate = if (allow) AllowAllGate() else DenyAllGate()
        return registry.execute(
            name,
            json,
            ToolContext(
                workspace = Workspace(
                    path = java.io.File("/tmp/void-test"),
                    repo = repo,
                ),
                gate = gate,
            ),
        )
    }

    @Test
    fun `list_repos shows the repositories`() = runTest {
        val result = call("list_repos")

        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("owner/repo"))
        assertTrue(output.contains("a repo"))
    }

    @Test
    fun `list_repos can filter by name`() = runTest {
        api = FakeGitHubApi(listOf(repo, Repo("2", "owner", "other", "owner/other", false, "main", null, 0L)))
        registry = ToolRegistry(RepoTools(api).all())

        val result = call("list_repos", "query" to "other")

        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("owner/other"))
        assertTrue(!output.contains("owner/repo"))
    }

    @Test
    fun `list_repos reports no repositories`() = runTest {
        api = FakeGitHubApi(emptyList())
        registry = ToolRegistry(RepoTools(api).all())

        val result = call("list_repos")

        assertTrue((result as ToolResult.Success).output.contains("No repositories"))
    }

    @Test
    fun `repo_info shows the metadata`() = runTest {
        val result = call("repo_info", "name" to "owner/repo")

        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("owner/repo"))
        assertTrue(output.contains("Default branch: main"))
    }

    @Test
    fun `repo_info reports an unknown repository`() = runTest {
        val result = call("repo_info", "name" to "owner/missing")

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `create_repo creates a repository`() = runTest {
        val result = call("create_repo", "name" to "new-repo", "description" to "hello")

        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).output.contains("owner/new-repo"))
        assertEquals("hello", api.created?.description)
        assertEquals(true, api.created?.isPrivate)
    }

    @Test
    fun `create_repo needs confirmation`() = runTest {
        val result = call("create_repo", "name" to "new-repo", allow = false)

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("declined"))
        assertEquals(null, api.created)
    }

    @Test
    fun `every repo tool is registered`() {
        assertEquals(listOf("create_repo", "list_repos", "repo_info"), registry.names)
    }

    private class FakeGitHubApi(private val repos: List<Repo>) : GitHubApi {
        var created: Repo? = null

        override suspend fun listRepos(page: Int): List<Repo> =
            if (page == 1) repos else emptyList()

        override suspend fun createRepo(
            name: String,
            description: String?,
            isPrivate: Boolean,
        ): Repo {
            val repo = Repo(
                id = "new",
                owner = "owner",
                name = name,
                fullName = "owner/$name",
                isPrivate = isPrivate,
                defaultBranch = "main",
                description = description,
                updatedAt = 0L,
            )
            created = repo
            return repo
        }

        override suspend fun repoInfo(fullName: String): Repo =
            repos.first { it.fullName == fullName }
    }
}
