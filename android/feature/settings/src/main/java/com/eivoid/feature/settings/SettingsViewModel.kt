package com.eivoid.feature.settings

import androidx.lifecycle.ViewModel
import com.eivoid.core.ai.Tier
import com.eivoid.core.ai.plan.Plan
import com.eivoid.core.security.SecretStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProviderInfo(
    val id: String,
    val displayName: String,
    val note: String,
)

/**
 * The Discord connection is stubbed in this build. The UI is present so the
 * settings surface is complete; wiring the OAuth exchange is a follow-up.
 */
data class DiscordUiState(
    val linked: Boolean = false,
    val busy: Boolean = false,
    val message: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secretStore: SecretStore,
    private val settingsStore: SettingsStore,
    private val sessionCleaner: SessionCleaner,
) : ViewModel() {

    val theme: StateFlow<ThemeMode> = settingsStore.theme
    val preferredTier: StateFlow<Tier?> = settingsStore.preferredTier
    val plan: StateFlow<Plan> = settingsStore.plan

    private val _discord = MutableStateFlow(DiscordUiState())
    val discord: StateFlow<DiscordUiState> = _discord.asStateFlow()

    fun saveProviderKey(providerId: String, key: String) {
        if (key.isBlank()) return
        secretStore.saveProviderKey(providerId, key.trim())
    }

    fun loadProviderKey(providerId: String): String? = secretStore.getProviderKey(providerId)

    fun clearProviderKey(providerId: String) {
        secretStore.clearProviderKey(providerId)
    }

    fun hasProviderKey(providerId: String): Boolean =
        !secretStore.getProviderKey(providerId).isNullOrBlank()

    /** Null means "let the router decide". */
    fun setPreferredTier(tier: Tier?) {
        settingsStore.setPreferredTier(tier)
    }

    fun setTheme(mode: ThemeMode) {
        settingsStore.setTheme(mode)
    }

    /**
     * TODO(discord-oauth): Replace this stub with the full flow:
     * 1. Launch a Discord OAuth screen from a dedicated Activity (not a WebView
     *    embedded in this composable), with the client id from the build config.
     * 2. Exchange the returned access token with the backend, which stores the
     *    Discord user id against the Clerk account.
     * 3. Refresh [DiscordUiState] from that link instead of local state.
     *
     * The UI and state plumbing are intentionally finished here so the rest of
     * the settings screen compiles and behaves predictably without a secret.
     */
    fun connectDiscord() {
        _discord.update {
            it.copy(
                busy = false,
                message = "Discord OAuth is not wired in this build yet. " +
                    "The connect flow is a planned integration.",
            )
        }
    }

    fun disconnectDiscord() {
        _discord.update {
            it.copy(
                linked = false,
                busy = false,
                message = "Discord disconnected.",
            )
        }
    }

    fun testDiscord() {
        _discord.update {
            it.copy(
                busy = false,
                message = if (it.linked) {
                    "Discord test delivery is not wired in this build yet."
                } else {
                    "Connect Discord first to send a test message."
                },
            )
        }
    }

    fun dismissDiscordMessage() {
        _discord.update { it.copy(message = "") }
    }

    /**
     * Spec: signing out wipes tokens, keys, cached repositories and every
     * cloned workspace.
     */
    fun signOut() {
        sessionCleaner.clear()
    }

    companion object {
        val PROVIDERS = listOf(
            ProviderInfo("openrouter", "OpenRouter", "Free models via a single key"),
            ProviderInfo("nvidia", "NVIDIA NIM", "Free hosted model endpoints"),
            ProviderInfo("nara", "Nara Router", "Routes across free models"),
        )
    }
}
