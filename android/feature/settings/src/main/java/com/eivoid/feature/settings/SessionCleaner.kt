package com.eivoid.feature.settings

import com.eivoid.core.common.auth.SignOutHook
import com.eivoid.core.database.ChatDao
import com.eivoid.core.database.RepoDao
import com.eivoid.core.database.UsageDao
import com.eivoid.core.security.SecretStore
import com.eivoid.core.workspace.WorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Wipes everything that belongs to the signed-in account: the secrets, the
 * cached repository list, the conversations, the usage ledger and every
 * cloned workspace.
 *
 * Secrets are cleared synchronously so that signing out never leaves a token
 * behind, even for a moment. The database and the filesystem are cleaned on
 * [scope] because the settings screen is torn down as soon as the user
 * navigates away.
 */
class SessionCleaner(
    private val secretStore: SecretStore,
    private val repoDao: RepoDao,
    private val chatDao: ChatDao,
    private val usageDao: UsageDao,
    private val workspaceManager: WorkspaceManager,
    private val signOutHook: SignOutHook,
    private val scope: CoroutineScope,
) {

    /** Returns the job that removes the cached data. */
    fun clear(): Job {
        secretStore.clearAll()
        return scope.launch {
            // End the identity-provider session before wiping the rest.
            runCatching { signOutHook.onSignOut() }
            repoDao.deleteAll()
            chatDao.deleteAll()
            usageDao.deleteAll()
            workspaceManager.deleteAll()
        }
    }
}
