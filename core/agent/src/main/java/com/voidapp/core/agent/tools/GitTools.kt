package com.voidapp.core.agent.tools

import com.voidapp.core.agent.Schemas
import com.voidapp.core.agent.Tool
import com.voidapp.core.agent.ToolArguments
import com.voidapp.core.agent.ToolContext
import com.voidapp.core.agent.ToolResult
import com.voidapp.core.git.GitClient
import com.voidapp.core.workspace.GitResult
import kotlinx.serialization.json.JsonObject

/**
 * The git tools. They act on the workspace the agent was started in.
 *
 * The GitHub token comes from the context and never appears in the output.
 */
class GitTools(private val gitClient: GitClient) {

    fun all(): List<Tool> = listOf(
        status(),
        diff(),
        log(),
        commit(),
        push(),
        branch(),
        checkout(),
    )

    private fun status() = object : Tool {
        override val name: String = "git_status"
        override val description: String =
            "Show what changed in the workspace: modified, staged and untracked files."
        override val parameters: JsonObject = Schemas.obj(properties = emptyMap())

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val status = gitClient.status(ctx.workspace)

            if (status.modified.isEmpty() && status.staged.isEmpty() && status.untracked.isEmpty()) {
                return ToolResult.Success("The working tree is clean.")
            }

            return ToolResult.Success(
                buildString {
                    appendLine("Modified: ${status.modified.joinToString(", ").ifEmpty { "-" }}")
                    appendLine("Staged: ${status.staged.joinToString(", ").ifEmpty { "-" }}")
                    append("Untracked: ${status.untracked.joinToString(", ").ifEmpty { "-" }}")
                },
            )
        }
    }

    private fun diff() = object : Tool {
        override val name: String = "git_diff"
        override val description: String =
            "Show the changes in the workspace as a unified diff. Optionally limit it to one file."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf("path" to Schemas.string("Only diff this file")),
        )

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val path = ToolArguments(arguments).optionalString("path")
            val text = gitClient.diff(ctx.workspace, path)

            if (text.isBlank()) return ToolResult.Success("No changes.")

            return ToolResult.Success(text)
        }
    }

    private fun log() = object : Tool {
        override val name: String = "git_log"
        override val description: String = "Show the recent commits of the workspace."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf("limit" to Schemas.integer("How many commits to show")),
        )

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val limit = ToolArguments(arguments).optionalInt("limit") ?: 10
            val commits = gitClient.log(ctx.workspace, limit)

            if (commits.isEmpty()) return ToolResult.Success("No commits yet.")

            return ToolResult.Success(
                commits.joinToString("\n") { commit ->
                    "${commit.hash.take(8)} ${commit.message} (${commit.author})"
                },
            )
        }
    }

    private fun commit() = object : Tool {
        override val name: String = "git_commit"
        override val description: String =
            "Stage every change in the workspace and commit it with the given message."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf("message" to Schemas.string("The commit message")),
            required = listOf("message"),
        )

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val message = ToolArguments(arguments).requiredString("message")

            return when (val result = gitClient.commit(ctx.workspace, message)) {
                is GitResult.Success -> {
                    val hash = gitClient.log(ctx.workspace, 1).firstOrNull()?.hash
                    ToolResult.Success(
                        if (hash != null) "Committed ${hash.take(8)}: $message" else "Committed: $message",
                    )
                }
                is GitResult.Failure -> ToolResult.Error(result.message)
                is GitResult.Conflict -> ToolResult.Error(result.message)
            }
        }
    }

    private fun push() = object : Tool {
        override val name: String = "git_push"
        override val description: String =
            "Push the current branch to GitHub. The remote must allow it; a rejected push is reported."
        override val parameters: JsonObject = Schemas.obj(properties = emptyMap())

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val token = ctx.token
            if (token.isNullOrBlank()) {
                return ToolResult.Error("Not signed in to GitHub.")
            }

            return when (val result = gitClient.push(ctx.workspace, token)) {
                is GitResult.Success -> ToolResult.Success("Pushed.")
                is GitResult.Failure -> ToolResult.Error(result.message)
                is GitResult.Conflict ->
                    ToolResult.Error("The push was rejected: ${result.message}")
            }
        }
    }

    private fun branch() = object : Tool {
        override val name: String = "git_branch"
        override val description: String = "List the branches and show the current one."
        override val parameters: JsonObject = Schemas.obj(properties = emptyMap())

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val branches = gitClient.branches(ctx.workspace)

            if (branches.isEmpty()) return ToolResult.Success("No branches yet.")

            return ToolResult.Success(
                branches.joinToString("\n") { branch ->
                    if (branch.current) "* ${branch.name}" else "  ${branch.name}"
                },
            )
        }
    }

    private fun checkout() = object : Tool {
        override val name: String = "git_checkout"
        override val description: String =
            "Switch to another branch. A branch that only exists on the remote starts at its tip."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf("branch" to Schemas.string("The branch to check out")),
            required = listOf("branch"),
        )

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val branch = ToolArguments(arguments).requiredString("branch")

            return when (val result = gitClient.checkout(ctx.workspace, branch)) {
                is GitResult.Success -> ToolResult.Success("Switched to $branch.")
                is GitResult.Failure -> ToolResult.Error(result.message)
                is GitResult.Conflict -> ToolResult.Error(result.message)
            }
        }
    }
}
