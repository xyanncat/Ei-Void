package com.voidapp.feature.settings

import androidx.lifecycle.ViewModel
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
) : ViewModel() {

    val defaultModel: StateFlow<String?> = settingsStore.defaultModel
    val theme: StateFlow<ThemeMode> = settingsStore.theme

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

    fun setDefaultModel(modelId: String) {
        settingsStore.setDefaultModel(modelId.trim())
    }

    fun setTheme(mode: ThemeMode) {
        settingsStore.setTheme(mode)
    }

    /** Spec: signing out clears every secret, including provider keys. */
    fun signOut() {
        secretStore.clearAll()
    }

    companion object {
        val PROVIDERS = listOf(
            ProviderInfo("openrouter", "OpenRouter", "Free models via a single key"),
            ProviderInfo("nvidia", "NVIDIA NIM", "Free hosted model endpoints"),
            ProviderInfo("kira", "Kira AI", "Free tier assistant"),
            ProviderInfo("nara", "Nara Router", "Routes across providers"),
        )
    }
}
