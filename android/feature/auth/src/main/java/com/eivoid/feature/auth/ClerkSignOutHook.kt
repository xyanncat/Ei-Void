package com.eivoid.feature.auth

import com.eivoid.core.common.auth.AuthConfiguration
import com.eivoid.core.common.auth.SignOutHook
import javax.inject.Inject
import javax.inject.Singleton

/** Ends the Clerk session when the user signs out of Void. */
@Singleton
class ClerkSignOutHook @Inject constructor(
    private val clerk: ClerkAuthRepository,
    private val configuration: AuthConfiguration,
) : SignOutHook {

    override suspend fun onSignOut() {
        if (configuration.clerkEnabled) clerk.signOut()
    }
}
