package com.eivoid.core.common.auth

import java.util.Base64

/**
 * Clerk is the only sign-in. The publishable key is public; the Clerk secret
 * key lives only in the Ei Void web service, which also returns the GitHub
 * token the app needs for clone/push.
 */
data class AuthConfiguration(
    val clerkPublishableKey: String = "",
    val backendUrl: String = "",
    val accountPortalUrl: String = "",
) {

    /** The key is present, so the Clerk sign-in UI can be shown. */
    val clerkConfigured: Boolean
        get() = clerkPublishableKey.startsWith("pk_")

    /** The gateway is deployed, so the GitHub token can be exchanged. */
    val backendConfigured: Boolean
        get() = backendUrl.startsWith("https://")

    val clerkEnabled: Boolean
        get() = clerkConfigured && backendConfigured

    /**
     * Clerk's Account Portal, where a user links GitHub to their Clerk
     * account. The explicit value wins; otherwise it is derived for Clerk's
     * development/production `.clerk.accounts.dev` hosting.
     */
    val accountPortal: String?
        get() {
            if (accountPortalUrl.startsWith("https://")) return accountPortalUrl
            val host = frontendApiHost ?: return null
            if (!host.endsWith(".clerk.accounts.dev")) return null
            val portal = host.removeSuffix(".clerk.accounts.dev") + ".accounts.dev"
            return "https://$portal/user"
        }

    /** The Frontend API host embedded in the publishable key. */
    private val frontendApiHost: String?
        get() = runCatching {
            val encoded = when {
                clerkPublishableKey.startsWith("pk_test_") ->
                    clerkPublishableKey.removePrefix("pk_test_")

                clerkPublishableKey.startsWith("pk_live_") ->
                    clerkPublishableKey.removePrefix("pk_live_")

                else -> return null
            }
            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
                .trimEnd('$')
                .takeIf { it.isNotBlank() }
        }.getOrNull()
}
