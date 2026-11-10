package com.voidapp.core.workspace

import java.io.File
import com.voidapp.core.common.github.Repo

/** A checked-out repository inside the app's private storage. */
data class Workspace(
    val path: File,
    val repo: Repo,
) {
    /** Stable key used to serialise git operations on this workspace. */
    val key: String get() = path.absolutePath
}

/**
 * Result of any workspace or git operation. [Success] carries the workspace
 * the operation was performed on so callers can chain work.
 */
sealed interface GitResult {
    data class Success(val workspace: Workspace) : GitResult
    data class Failure(val message: String) : GitResult

    /** The working tree changed underneath the operation. */
    data class Conflict(val message: String) : GitResult
}

data class FileInfo(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
)

data class FileContent(
    val path: String,
    val lines: List<String>,
    val truncated: Boolean,
)

private val SAFE_SEGMENT = Regex("^[A-Za-z0-9._-]+$")

/**
 * The single place that decides where a repository lives on disk. Both the
 * clone manager and the workspace manager use it, so a repository is never
 * written to two different places.
 */
fun workspacePath(root: File, repo: Repo): File {
    require(SAFE_SEGMENT.matches(repo.owner) && repo.owner != "." && repo.owner != "..") {
        "Unsafe repository owner: ${repo.owner}"
    }
    require(SAFE_SEGMENT.matches(repo.name) && repo.name != "." && repo.name != "..") {
        "Unsafe repository name: ${repo.name}"
    }
    return File(File(root, repo.owner), repo.name)
}
