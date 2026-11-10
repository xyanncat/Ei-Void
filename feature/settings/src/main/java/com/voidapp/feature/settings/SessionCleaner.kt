package com.voidapp.feature.settings

import com.voidapp.core.database.RepoDao
import com.voidapp.core.security.SecretStore
import com.voidapp.core.workspace.WorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Wipes everything that belongs to the signed-in account: the secrets, the
 * cached repository list and every cloned workspace.
 *
 * Secrets are cleared synchronously so that signing out never leaves a token
 * behind, even for a moment. The database and the filesystem are cleaned on
 * [scope] because the settings screen is torn down as soon as the user
 * navigates away.
 */
class SessionCleaner(
    private val secretStore: SecretStore,
    private val repoDao: RepoDao,
    private val workspaceManager: WorkspaceManager,
    private val scope: CoroutineScope,
) {

    /** Returns the job that removes the cached data. */
    fun clear(): Job {
        secretStore.clearAll()
        return scope.launch {
            repoDao.deleteAll()
            workspaceManager.deleteAll()
        }
    }
}
