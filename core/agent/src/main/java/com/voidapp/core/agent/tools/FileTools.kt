package com.voidapp.core.agent.tools

import com.voidapp.core.agent.Schemas
import com.voidapp.core.agent.Tool
import com.voidapp.core.agent.ToolArguments
import com.voidapp.core.agent.ToolContext
import com.voidapp.core.agent.ToolResult
import com.voidapp.core.workspace.FileInfo
import com.voidapp.core.workspace.GitResult
import com.voidapp.core.workspace.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * The file tools: read, search and change files inside the workspace.
 *
 * Everything goes through [WorkspaceManager], which refuses paths that leave
 * the workspace.
 */
class FileTools(private val workspaceManager: WorkspaceManager) {

    fun all(): List<Tool> = listOf(
        listFiles(),
        readFile(),
        writeFile(),
        createFile(),
        editFile(),
        searchFiles(),
        deleteFile(),
    )

    private fun listFiles() = object : Tool {
        override val name: String = "list_files"
        override val description: String =
            "List the files and directories at a path in the workspace. Use '.' for the root."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf("path" to Schemas.string("Directory to list, '.' for the root")),
            required = listOf("path"),
        )

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val path = ToolArguments(arguments).requiredString("path")
            val files = workspaceManager.listFiles(ctx.workspace, path)
            if (files.isEmpty()) {
                return ToolResult.Success("No files in $path")
            }
            return ToolResult.Success(files.joinToString("\n") { describe(it) })
        }
    }

    private fun readFile() = object : Tool {
        override val name: String = "read_file"
        override val description: String =
            "Read a text file from the workspace. Returns numbered lines."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf(
                "path" to Schemas.string("File to read, relative to the workspace root"),
                "start_line" to Schemas.integer("First line to read (1-based)"),
                "end_line" to Schemas.integer("Last line to read (inclusive)"),
            ),
            required = listOf("path"),
        )

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val args = ToolArguments(arguments)
            val path = args.requiredString("path")
            val content = workspaceManager.readFile(
                workspace = ctx.workspace,
                path = path,
                startLine = args.optionalInt("start_line"),
                endLine = args.optionalInt("end_line"),
            )

            val body = content.lines.mapIndexed { index, line -> "${index + 1}\t$line" }
                .joinToString("\n")
            val notice = if (content.truncated) "\n\n(truncated at the line cap)" else ""
            return ToolResult.Success(body + notice)
        }
    }

    private fun writeFile() = object : Tool {
        override val name: String = "write_file"
        override val description: String =
            "Write text to a file, replacing whatever was there. Parent directories are created."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf(
                "path" to Schemas.string("File to write, relative to the workspace root"),
                "content" to Schemas.string("The new content of the file"),
            ),
            required = listOf("path", "content"),
        )

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val args = ToolArguments(arguments)
            val path = args.requiredString("path")
            val content = args.requiredString("content")

            return when (val result = workspaceManager.writeFile(ctx.workspace, path, content)) {
                is GitResult.Success -> ToolResult.Success("Wrote ${content.length} characters to $path")
                is GitResult.Failure -> ToolResult.Error(result.message)
                is GitResult.Conflict -> ToolResult.Error(result.message)
            }
        }
    }

    private fun createFile() = object : Tool {
        override val name: String = "create_file"
        override val description: String =
            "Create a new file. Fails when the file already exists."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf(
                "path" to Schemas.string("File to create, relative to the workspace root"),
                "content" to Schemas.string("The content of the new file"),
            ),
            required = listOf("path", "content"),
        )

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val args = ToolArguments(arguments)
            val path = args.requiredString("path")
            val content = args.requiredString("content")

            if (workspaceManager.exists(ctx.workspace, path)) {
                return ToolResult.Error("$path already exists. Use edit_file or write_file.")
            }
            return when (val result = workspaceManager.writeFile(ctx.workspace, path, content)) {
                is GitResult.Success -> ToolResult.Success("Created $path")
                is GitResult.Failure -> ToolResult.Error(result.message)
                is GitResult.Conflict -> ToolResult.Error(result.message)
            }
        }
    }

    private fun editFile() = object : Tool {
        override val name: String = "edit_file"
        override val description: String =
            "Replace a piece of text in a file. Fails when the text is missing or occurs more than once."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf(
                "path" to Schemas.string("File to edit, relative to the workspace root"),
                "old_text" to Schemas.string("The exact text to replace"),
                "new_text" to Schemas.string("The replacement text"),
            ),
            required = listOf("path", "old_text", "new_text"),
        )

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val args = ToolArguments(arguments)
            val path = args.requiredString("path")
            val oldText = args.requiredString("old_text")
            val newText = args.requiredString("new_text")

            val content = workspaceManager.readText(ctx.workspace, path)
            val text = content

            val occurrences = countOccurrences(text, oldText)
            if (occurrences == 0) {
                return ToolResult.Error("The text to replace was not found in $path.")
            }
            if (occurrences > 1) {
                return ToolResult.Error(
                    "The text to replace occurs $occurrences times in $path. Include more context.",
                )
            }

            val updated = text.replace(oldText, newText)
            workspaceManager.writeFile(ctx.workspace, path, updated)
            return ToolResult.Success("Edited $path")
        }
    }

    private fun searchFiles() = object : Tool {
        override val name: String = "search_files"
        override val description: String =
            "Search the workspace for text. Returns file, line number and the matching line."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf(
                "query" to Schemas.string("The text to look for"),
                "glob" to Schemas.string("Only search files matching this glob, e.g. '*.kt'"),
                "regex" to Schemas.boolean("Treat the query as a regular expression"),
                "path" to Schemas.string("Directory to search, '.' for the whole workspace"),
            ),
            required = listOf("query"),
        )

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val args = ToolArguments(arguments)
            val query = args.requiredString("query")
            val glob = args.optionalString("glob")
            val useRegex = args.optionalBoolean("regex")
            val root = args.optionalString("path") ?: "."

            val matcher = if (useRegex) {
                runCatching { Regex(query) }.getOrElse {
                    return ToolResult.Error("Invalid regular expression: ${it.message}")
                }
            } else {
                null
            }

            val files = collectFiles(ctx.workspace, root)
            val matches = mutableListOf<String>()

            for (file in files) {
                if (matches.size >= MAX_MATCHES) break
                if (glob != null && !matchesGlob(file.path, glob)) continue

                val content = runCatching { workspaceManager.readFile(ctx.workspace, file.path) }
                    .getOrNull() ?: continue

                content.lines.forEachIndexed { index, line ->
                    if (matches.size >= MAX_MATCHES) return@forEachIndexed
                    val hit = matcher?.containsMatchIn(line) ?: line.contains(query, ignoreCase = true)
                    if (hit) {
                        matches += "${file.path}:${index + 1}: ${line.trim()}"
                    }
                }
            }

            if (matches.isEmpty()) {
                return ToolResult.Success("No matches for '$query'")
            }
            val note = if (matches.size >= MAX_MATCHES) "\n(truncated at $MAX_MATCHES matches)" else ""
            return ToolResult.Success(matches.joinToString("\n") + note)
        }
    }

    private fun deleteFile() = object : Tool {
        override val name: String = "delete_file"
        override val description: String = "Delete a file or directory from the workspace."
        override val parameters: JsonObject = Schemas.obj(
            properties = mapOf("path" to Schemas.string("File or directory to delete")),
            required = listOf("path"),
        )

        override val requiresConfirmation: Boolean = true

        override suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult {
            val path = ToolArguments(arguments).requiredString("path")

            return when (val result = workspaceManager.deleteFile(ctx.workspace, path)) {
                is GitResult.Success -> ToolResult.Success("Deleted $path")
                is GitResult.Failure -> ToolResult.Error(result.message)
                is GitResult.Conflict -> ToolResult.Error(result.message)
            }
        }
    }

    private suspend fun collectFiles(
        workspace: com.voidapp.core.workspace.Workspace,
        root: String,
    ): List<FileInfo> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<FileInfo>()

        suspend fun walk(path: String) {
            if (collected.size >= MAX_FILES) return
            val entries = workspaceManager.listFiles(workspace, path)
            for (entry in entries) {
                if (collected.size >= MAX_FILES) return
                if (entry.isDirectory) {
                    walk(entry.path)
                } else {
                    collected += entry
                }
            }
        }

        walk(root)
        collected
    }

    private fun describe(file: FileInfo): String =
        if (file.isDirectory) "${file.path}/" else "${file.path} (${file.sizeBytes} bytes)"

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var index = text.indexOf(needle)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }

    private fun matchesGlob(path: String, glob: String): Boolean {
        val name = path.substringAfterLast('/')
        return when {
            glob.startsWith("*") && glob.endsWith("*") && glob.length > 2 ->
                path.contains(glob.removeSuffix("*").removePrefix("*"))
            glob.startsWith("*") -> name.endsWith(glob.removePrefix("*"))
            glob.endsWith("*") -> name.startsWith(glob.removeSuffix("*"))
            else -> name == glob || path == glob
        }
    }

    private companion object {
        const val MAX_MATCHES = 200
        const val MAX_FILES = 2_000
    }
}
