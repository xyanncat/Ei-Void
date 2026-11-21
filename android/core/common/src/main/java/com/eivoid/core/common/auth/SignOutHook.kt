package com.eivoid.core.common.auth

/**
 * Runs provider-specific sign-out (for example ending the Clerk session)
 * before the local session is wiped. A no-op implementation is used when no
 * such provider is configured.
 */
interface SignOutHook {
    suspend fun onSignOut()
}
