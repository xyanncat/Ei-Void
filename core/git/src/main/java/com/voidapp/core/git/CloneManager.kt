package com.voidapp.core.git

import com.voidapp.core.common.github.Repo
import com.voidapp.core.workspace.GitResult
import com.voidapp.core.workspace.Workspace
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

data class CloneProgress(
    val percent: Int,
    val message: String,
)

/**
 * Shallow-clones a repository into `workspaceRoot/<owner>/<name>`.
 *
 * [remoteUrlFor] exists so tests can clone from a local `file://` repository.
 */
class CloneManager(
    private val workspaceRoot: File,
    private val remoteUrlFor: (Repo) -> String = { "https://github.com/${it.fullName}.git" },
) {

    suspend fun clone(
        repo: Repo,
        token: String,
        onProgress: (CloneProgress) -> Unit,
    ): GitResult = withContext(Dispatchers.IO) {
        val workspace = Workspace(File(File(workspaceRoot, repo.owner), repo.name), repo)

        if (File(workspace.path, ".git").exists()) {
            return@withContext GitResult.Success(workspace)
        }

        val uri = remoteUrlFor(repo)

        runCatching {
            workspace.path.parentFile?.mkdirs()

            val command = Git.cloneRepository()
                .setURI(uri)
                .setDirectory(workspace.path)
                .setCloneAllBranches(false)
                .setBranch(repo.defaultBranch)
                .setProgressMonitor(CloneProgressMonitor(onProgress))

            if (uri.startsWith("https://") && token.isNotBlank()) {
                command.setDepth(1)
                command.setCredentialsProvider(
                    UsernamePasswordCredentialsProvider(token, "x-oauth-basic"),
                )
            }

            command.call().close()
            workspace
        }.fold(
            onSuccess = { GitResult.Success(it) },
            onFailure = { error ->
                workspace.path.deleteRecursively()
                GitResult.Failure(error.message ?: "Clone failed")
            },
        )
    }

    private class CloneProgressMonitor(
        private val onProgress: (CloneProgress) -> Unit,
    ) : ProgressMonitor {

        private var taskName: String = ""
        private var totalWork: Int = 0

        override fun start(totalTasks: Int) {
            onProgress(CloneProgress(0, "Starting clone"))
        }

        override fun beginTask(title: String, totalWork: Int) {
            taskName = title
            this.totalWork = totalWork
            onProgress(CloneProgress(0, title))
        }

        override fun update(completed: Int) {
            val percent = if (totalWork > 0) {
                (completed.toLong() * 100L / totalWork).toInt().coerceIn(0, 100)
            } else {
                0
            }
            onProgress(CloneProgress(percent, taskName))
        }

        override fun endTask() {
            onProgress(CloneProgress(100, taskName))
        }

        override fun isCancelled(): Boolean = false

        override fun showDuration(enabled: Boolean) = Unit
    }
}
