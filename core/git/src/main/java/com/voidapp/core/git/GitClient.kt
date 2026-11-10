package com.voidapp.core.git

import com.voidapp.core.common.github.Repo
import com.voidapp.core.workspace.GitResult

data class CloneProgress(
    val percent: Int,
    val message: String,
)

interface GitClient {
    /**
     * Clones [repo] into the app workspace using [token] for HTTPS auth.
     * [token] is passed to the credential provider and is never written to
     * `.git/config`.
     */
    suspend fun clone(
        repo: Repo,
        token: String,
        onProgress: (CloneProgress) -> Unit = {},
    ): GitResult
}
