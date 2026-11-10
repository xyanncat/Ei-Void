package com.voidapp.core.agent.tools

import com.voidapp.core.agent.Schemas
import com.voidapp.core.agent.Tool
import com.voidapp.core.agent.ToolArguments
import com.voidapp.core.agent.ToolContext
import com.voidapp.core.agent.ToolResult
import com.voidapp.core.common.github.GitHubApi
import com.voidapp.core.common.github.Repo
import kotlinx.serialization.json.JsonObject

/**
 * The GitHub tools: look at the user's repositories and create new ones.
 */
class RepoTools(private val api: GitHubApi) {

    fun all(): List<Tool> = listOf(
        listRepos(),
        repoInfo(),
        createRepo(),
    )

    private fun listRepos() = object : Tool {
        override val name: String = "list_repos"
        override val description: String =
            "List the repositories of the signed-in GitHub account."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf(
                "query" to Schemas.string("Only return repositories whose name contains this"),
            ),
        )

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val query = ToolArguments(arguments).optionalString("query")

            val repos = runCatching {
                var page = 1
                val all = mutableListOf<Repo>()
                while (page <= MAX_PAGES) {
                    val batch = api.listRepos(page)
                    all += batch
                    if (batch.size < PAGE_SIZE) break
                    page++
                }
                all
            }.getOrElse { error ->
                return ToolResult.Error(error.message ?: "Could not read the repositories.")
            }

            val filtered = if (query == null) {
                repos
            } else {
                repos.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.fullName.contains(query, ignoreCase = true)
                }
            }

            if (filtered.isEmpty()) return ToolResult.Success("No repositories found.")

            return ToolResult.Success(
                filtered.joinToString("\n") { repo ->
                    "${repo.fullName}${if (repo.isPrivate) " (private)" else ""} - ${repo.description ?: "no description"}"
                },
            )
        }
    }

    private fun repoInfo() = object : Tool {
        override val name: String = "repo_info"
        override val description: String = "Show the metadata of one repository, by owner/name."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf("name" to Schemas.string("The repository as owner/name")),
            required = listOf("name"),
        )

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val name = ToolArguments(arguments).requiredString("name")

            val repo = runCatching { api.repoInfo(name) }.getOrElse { error ->
                return ToolResult.Error(error.message ?: "Could not read $name.")
            }

            return ToolResult.Success(
                buildString {
                    appendLine("Name: ${repo.fullName}")
                    appendLine("Private: ${repo.isPrivate}")
                    appendLine("Default branch: ${repo.defaultBranch}")
                    append("Description: ${repo.description ?: "-"}")
                },
            )
        }
    }

    private fun createRepo() = object : Tool {
        override val name: String = "create_repo"
        override val description: String =
            "Create a new GitHub repository for the signed-in account."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf(
                "name" to Schemas.string("The name of the new repository"),
                "description" to Schemas.string("What the repository is for"),
                "private" to Schemas.boolean("Create it private (default true)"),
            ),
            required = listOf("name"),
        )

        override val requiresConfirmation: Boolean = true

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val args = ToolArguments(arguments)
            val name = args.requiredString("name")
            val description = args.optionalString("description")
            val isPrivate = args.optionalBoolean("private", default = true)

            val repo = runCatching { api.createRepo(name, description, isPrivate) }
                .getOrElse { error ->
                    return ToolResult.Error(error.message ?: "Could not create $name.")
                }

            return ToolResult.Success("Created ${repo.fullName}.")
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 20
    }
}
