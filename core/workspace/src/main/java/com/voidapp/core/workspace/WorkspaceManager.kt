package com.voidapp.core.workspace

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * All file access inside a [Workspace]. Every path is resolved against the
 * workspace root and rejected if it escapes it.
 */
class WorkspaceManager(private val root: File) {

    fun workspaceFor(repo: com.voidapp.core.common.github.Repo): Workspace =
        Workspace(workspacePath(root, repo), repo)

    suspend fun listFiles(workspace: Workspace, path: String = "."): List<FileInfo> =
        withContext(Dispatchers.IO) {
            val directory = resolve(workspace, path)
            if (!directory.isDirectory) return@withContext emptyList()

            directory.listFiles().orEmpty()
                .filter { it.name != GIT_DIR }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map { file ->
                    FileInfo(
                        path = file.relativeTo(workspace.path).invariantSeparatorsPath,
                        name = file.name,
                        isDirectory = file.isDirectory,
                        sizeBytes = if (file.isFile) file.length() else 0L,
                        lastModified = file.lastModified(),
                    )
                }
        }

    suspend fun readFile(
        workspace: Workspace,
        path: String,
        startLine: Int? = null,
        endLine: Int? = null,
    ): FileContent = withContext(Dispatchers.IO) {
        val file = resolve(workspace, path)
        require(file.isFile) { "Not a file: $path" }

        val collected = mutableListOf<String>()
        var truncated = false
        file.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (collected.size == WORKSPACE_LINE_CAP) {
                    truncated = true
                    break
                }
                collected += line
            }
        }

        val from = ((startLine ?: 1) - 1).coerceIn(0, collected.size)
        val to = (endLine ?: collected.size).coerceIn(from, collected.size)

        FileContent(
            path = path,
            lines = collected.subList(from, to),
            truncated = truncated,
        )
    }

    /** True when the path exists inside the workspace. */
    suspend fun exists(workspace: Workspace, path: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { resolve(workspace, path).exists() }.getOrDefault(false)
        }

    /**
     * The raw text of a file, exactly as stored, so an edit does not change
     * the line endings or lose the trailing newline.
     */
    suspend fun readText(workspace: Workspace, path: String): String =
        withContext(Dispatchers.IO) {
            val file = resolve(workspace, path)
            require(file.isFile) { "Not a file: $path" }
            require(file.length() <= MAX_EDIT_BYTES) {
                "$path is too large to edit (${file.length()} bytes)"
            }
            file.readText()
        }

    suspend fun writeFile(
        workspace: Workspace,
        path: String,
        content: String,
    ): GitResult = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolve(workspace, path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            GitResult.Success(workspace)
        }.getOrElse { error ->
            GitResult.Failure(error.message ?: "Could not write $path")
        }
    }

    suspend fun deleteFile(workspace: Workspace, path: String): GitResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = resolve(workspace, path)
                require(Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    "No such file: $path"
                }
                require(file.deleteWithoutFollowingLinks()) { "Could not delete $path" }
                GitResult.Success(workspace)
            }.getOrElse { error ->
                GitResult.Failure(error.message ?: "Could not delete $path")
            }
        }

    /** Removes every workspace. Used when signing out. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        root.listFiles()?.forEach { it.deleteWithoutFollowingLinks() }
        Unit
    }

    /** Canonicalises [path] and refuses anything outside [workspace]. */
    private fun resolve(workspace: Workspace, path: String): File {
        val base = workspace.path.canonicalFile
        val target = File(base, path).canonicalFile
        val inside = target == base || target.path.startsWith(base.path + File.separator)
        require(inside) { "Path escapes the workspace: $path" }
        return target
    }

    /**
     * Deletes a tree without ever following a symlink: a link is removed
     * itself, so a checkout containing `evil -> ../../` cannot reach files
     * outside the workspace.
     */
    private fun File.deleteWithoutFollowingLinks(): Boolean {
        if (Files.isSymbolicLink(toPath()) || !isDirectory) return delete()
        listFiles()?.forEach { child ->
            if (!child.deleteWithoutFollowingLinks()) return false
        }
        return delete()
    }

    companion object {
        const val WORKSPACE_LINE_CAP = 500
        const val MAX_EDIT_BYTES = 1_048_576L
        private const val GIT_DIR = ".git"
    }
}
