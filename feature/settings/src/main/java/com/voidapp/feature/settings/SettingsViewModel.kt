package com.voidapp.feature.settings

import androidx.lifecycle.ViewModel
import com.voidapp.core.ai.Tier
import com.voidapp.core.security.SecretStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

data class ProviderInfo(
    val id: String,
    val displayName: String,
    val note: String,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secretStore: SecretStore,
    private val settingsStore: SettingsStore,
    private val sessionCleaner: SessionCleaner,
) : ViewModel() {

    val theme: StateFlow<ThemeMode> = settingsStore.theme
    val preferredTier: StateFlow<Tier?> = settingsStore.preferredTier

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
            ProviderInfo("kira", "Kira AI", "Free tier assistant"),
        )
    }
}
