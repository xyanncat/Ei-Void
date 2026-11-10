package com.voidapp.core.git

import com.voidapp.core.workspace.DiffEngine
import com.voidapp.core.workspace.GitResult
import com.voidapp.core.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

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

    /**
     * A unified diff of the workspace against HEAD. [path] limits the diff
     * to one file.
     *
     * The diff is built from the committed content and the file on disk, so
     * uncommitted work is included and nothing has to be written to the
     * object database first.
     */
    suspend fun diff(workspace: Workspace, path: String? = null): String =
        withContext(Dispatchers.IO) {
            locks.withLock(workspace.key) {
                Git.open(workspace.path).use { git ->
                    val status = git.status().call()
                    val changed = (
                        status.modified + status.missing + status.changed +
                            status.added + status.removed
                        ).distinct()
                        .filter { path == null || it == path }
                        .sorted()

                    val head = git.repository.resolve("HEAD^{commit}")
                    val diff = StringBuilder()

                    for (file in changed) {
                        val oldText = head?.let { readCommittedText(git, it, file) }.orEmpty()
                        val newText = if (status.missing.contains(file)) {
                            ""
                        } else {
                            readWorkTreeText(git, file) ?: continue
                        }
                        if (oldText == newText) continue

                        diff.append(DiffEngine.unifiedDiff(oldText, newText, file))
                        diff.append('\n')
                    }

                    diff.toString()
                }
            }
        }

    /** The content of [file] as of the given commit, or null when it is new. */
    private fun readCommittedText(git: Git, commitId: AnyObjectId, file: String): String? =
        runCatching {
            val walk = RevWalk(git.repository)
            val commit = walk.parseCommit(commitId)
            val finder = TreeWalk.forPath(git.repository, file, commit.tree)
                ?: return@runCatching null
            val blob = git.repository.open(finder.getObjectId(0))
            blob.bytes.toString(Charsets.UTF_8)
        }.getOrNull()

    /** The content of [file] on disk, or null when it cannot be read as text. */
    private fun readWorkTreeText(git: Git, file: String): String? = runCatching {
        val workTree = git.repository.workTree ?: return@runCatching null
        val target = File(workTree, file)
        if (!target.isFile) return null
        target.readText()
    }.getOrNull()

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
