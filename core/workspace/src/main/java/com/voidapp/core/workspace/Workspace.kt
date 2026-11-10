package com.voidapp.core.workspace

import java.io.File
import com.voidapp.core.common.github.Repo

/** A checked-out repository inside the app's private storage. */
data class Workspace(
    val path: File,
    val repo: Repo,
)

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
