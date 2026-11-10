package com.voidapp.core.workspace

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * All file access inside a [Workspace]. Every path is resolved against the
 * workspace root and rejected if it escapes it.
 */
class WorkspaceManager(private val root: File) {

    fun workspaceFor(repo: com.voidapp.core.common.github.Repo): Workspace =
        Workspace(File(File(root, repo.owner), repo.name), repo)

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
                require(file.exists()) { "No such file: $path" }
                require(file.deleteRecursively()) { "Could not delete $path" }
                GitResult.Success(workspace)
            }.getOrElse { error ->
                GitResult.Failure(error.message ?: "Could not delete $path")
            }
        }

    /** Canonicalises [path] and refuses anything outside [workspace]. */
    private fun resolve(workspace: Workspace, path: String): File {
        val base = workspace.path.canonicalFile
        val target = File(base, path).canonicalFile
        val inside = target == base || target.path.startsWith(base.path + File.separator)
        require(inside) { "Path escapes the workspace: $path" }
        return target
    }

    companion object {
        const val WORKSPACE_LINE_CAP = 500
        private const val GIT_DIR = ".git"
    }
}
