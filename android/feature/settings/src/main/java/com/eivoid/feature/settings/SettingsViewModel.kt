package com.eivoid.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eivoid.core.ai.Tier
import com.eivoid.core.ai.plan.Plan
import com.eivoid.core.reminders.DiscordLinkService
import com.eivoid.core.security.SecretStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProviderInfo(
    val id: String,
    val displayName: String,
    val note: String,
)

/** The Ei Snore Discord notification connection shown in Customize. */
data class DiscordUiState(
    val linked: Boolean = false,
    val username: String = "",
    val busy: Boolean = false,
    val message: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secretStore: SecretStore,
    private val settingsStore: SettingsStore,
    private val sessionCleaner: SessionCleaner,
    private val discordLinkService: DiscordLinkService,
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
     * The OAuth Activity has already produced a Discord access token. The
     * backend verifies it, checks guild membership, and stores the link.
     */
    fun connectDiscord(accessToken: String) {
        if (accessToken.isBlank()) {
            _discord.update {
                it.copy(busy = false, message = "Discord did not return an access token.")
            }
            return
        }
        _discord.update { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            discordLinkService.link(accessToken).fold(
                onSuccess = { identity ->
                    _discord.update {
                        it.copy(
                            linked = true,
                            username = identity.username,
                            busy = false,
                            message = "Discord notifications linked.",
                        )
                    }
                },
                onFailure = { error ->
                    _discord.update {
                        it.copy(
                            busy = false,
                            message = error.message ?: "Discord linking failed.",
                        )
                    }
                },
            )
        }
    }

    fun onDiscordAuthError(message: String) {
        _discord.update {
            it.copy(busy = false, message = message.ifBlank { "Discord linking was cancelled." })
        }
    }

    /** Reloads the link from the backend; a missing Clerk session is silent. */
    fun refreshDiscord() {
        viewModelScope.launch {
            discordLinkService.account().onSuccess { account ->
                _discord.update {
                    it.copy(
                        linked = account.discord != null,
                        username = account.discord?.username.orEmpty(),
                        busy = false,
                    )
                }
            }
        }
    }

    fun disconnectDiscord() {
        _discord.update { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            discordLinkService.unlink().fold(
                onSuccess = {
                    _discord.update {
                        it.copy(
                            linked = false,
                            username = "",
                            busy = false,
                            message = "Discord notifications disconnected.",
                        )
                    }
                },
                onFailure = { error ->
                    _discord.update {
                        it.copy(
                            busy = false,
                            message = error.message ?: "Could not disconnect Discord.",
                        )
                    }
                },
            )
        }
    }

    fun testDiscord() {
        if (!_discord.value.linked) {
            _discord.update { it.copy(message = "Connect Discord first to send a test message.") }
            return
        }
        _discord.update { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            discordLinkService.test().fold(
                onSuccess = {
                    _discord.update {
                        it.copy(busy = false, message = "Test Discord message sent.")
                    }
                },
                onFailure = { error ->
                    _discord.update {
                        it.copy(
                            busy = false,
                            message = error.message ?: "Discord test failed.",
                        )
                    }
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
        _discord.value = DiscordUiState()
    }

    companion object {
        val PROVIDERS = listOf(
            ProviderInfo("openrouter", "OpenRouter", "Free models via a single key"),
            ProviderInfo("nvidia", "NVIDIA NIM", "Free hosted model endpoints"),
            ProviderInfo("nara", "Nara Router", "Routes across free models"),
        )
    }
}
