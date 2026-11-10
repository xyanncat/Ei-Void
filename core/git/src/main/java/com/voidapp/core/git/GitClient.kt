package com.voidapp.core.git

import com.voidapp.core.workspace.GitResult
import com.voidapp.core.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

data class GitStatus(
    /** Changed in the working tree but not staged. */
    val modified: List<String>,
    /** Staged in the index but not committed. */
    val staged: List<String>,
    val untracked: List<String>,
)

data class Commit(
    val hash: String,
    val message: String,
    val author: String,
    /** Epoch milliseconds. */
    val timestamp: Long,
)

data class Branch(
    val name: String,
    val current: Boolean,
)

/**
 * Working-tree operations on an already cloned [Workspace]. Cloning lives in
 * [CloneManager]; everything here assumes `.git` already exists.
 *
 * The token is handed to JGit's in-memory credential provider per call and is
 * never persisted to disk.
 */
class GitClient(
    private val locks: RepoLocks = RepoLocks(),
) {

    suspend fun status(workspace: Workspace): GitStatus = withContext(Dispatchers.IO) {
        locks.withLock(workspace.key) {
            Git.open(workspace.path).use { git ->
                val status = git.status().call()
                GitStatus(
                    modified = (status.modified + status.missing).distinct().sorted(),
                    staged = (status.added + status.changed + status.removed).distinct().sorted(),
                    untracked = status.untracked.sorted(),
                )
            }
        }
    }

    suspend fun commit(workspace: Workspace, message: String): GitResult =
        withContext(Dispatchers.IO) {
            locks.withLock(workspace.key) {
                runCatching {
                    val committed = Git.open(workspace.path).use { git ->
                        // Stage new, modified and deleted files.
                        git.add().addFilepattern(".").call()
                        git.add().addFilepattern(".").setUpdate(true).call()

                        if (git.status().call().isClean) {
                            false
                        } else {
                            git.commit()
                                .setMessage(message)
                                .setAuthor(AUTHOR_NAME, AUTHOR_EMAIL)
                                .setCommitter(AUTHOR_NAME, AUTHOR_EMAIL)
                                .call()
                            true
                        }
                    }

                    if (committed) {
                        GitResult.Success(workspace)
                    } else {
                        GitResult.Failure("Nothing to commit")
                    }
                }.getOrElse { error ->
                    GitResult.Failure(error.message ?: "Could not commit")
                }
            }
        }

    suspend fun push(workspace: Workspace, token: String): GitResult =
        withContext(Dispatchers.IO) {
            locks.withLock(workspace.key) {
                runCatching {
                    val results = Git.open(workspace.path).use { git ->
                        git.push()
                            .setCredentialsProvider(
                                UsernamePasswordCredentialsProvider(USERNAME, token),
                            )
                            .call()
                    }

                    val rejected = results
                        .flatMap { it.remoteUpdates }
                        .filter { update ->
                            update.status != RemoteRefUpdate.Status.OK &&
                                update.status != RemoteRefUpdate.Status.UP_TO_DATE
                        }

                    if (rejected.isNotEmpty()) {
                        GitResult.Conflict(
                            rejected.joinToString { "${it.remoteName}: ${it.status}" },
                        )
                    } else {
                        GitResult.Success(workspace)
                    }
                }.getOrElse { error ->
                    GitResult.Failure(error.message ?: "Could not push")
                }
            }
        }

    suspend fun log(workspace: Workspace, limit: Int = 30): List<Commit> =
        withContext(Dispatchers.IO) {
            locks.withLock(workspace.key) {
                Git.open(workspace.path).use { git ->
                    git.log().setMaxCount(limit).call().map { commit ->
                        Commit(
                            hash = commit.name,
                            message = commit.fullMessage.trim(),
                            author = commit.authorIdent.name,
                            timestamp = commit.commitTime.toLong() * 1_000L,
                        )
                    }
                }
            }
        }

    suspend fun branches(workspace: Workspace): List<Branch> =
        withContext(Dispatchers.IO) {
            locks.withLock(workspace.key) {
                Git.open(workspace.path).use { git ->
                    val current = git.repository.fullBranch
                    git.branchList().call()
                        .map { ref ->
                            val name = Repository.shortenRefName(ref.name)
                            Branch(name = name, current = ref.name == current)
                        }
                        .sortedBy { it.name }
                }
            }
        }

    suspend fun checkout(workspace: Workspace, branch: String): GitResult =
        withContext(Dispatchers.IO) {
            locks.withLock(workspace.key) {
                runCatching {
                    Git.open(workspace.path).use { git ->
                        val existing = git.branchList().call()
                            .map { Repository.shortenRefName(it.name) }
                        val create = branch !in existing
                        // A branch that only exists on the remote must start at
                        // the remote tip, not at the current HEAD.
                        val remoteTip = git.repository.exactRef("refs/remotes/origin/$branch")
                            ?.objectId
                            ?.name

                        git.checkout()
                            .setName(branch)
                            .setCreateBranch(create)
                            .apply {
                                if (create && remoteTip != null) setStartPoint(remoteTip)
                            }
                            .call()
                    }
                    GitResult.Success(workspace)
                }.getOrElse { error ->
                    GitResult.Failure(error.message ?: "Could not check out $branch")
                }
            }
        }

    private companion object {
        const val AUTHOR_NAME = "Void"
        const val AUTHOR_EMAIL = "void@localhost"
    }
}
