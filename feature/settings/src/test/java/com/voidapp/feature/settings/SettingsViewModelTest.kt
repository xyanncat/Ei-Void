package com.voidapp.feature.settings

import com.voidapp.core.security.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun `save and load a provider key`() = runTest {
        val vm = SettingsViewModel(FakeSecretStore(), FakeSettingsStore())

        vm.saveProviderKey("openrouter", "sk-or-abc")

        assertEquals("sk-or-abc", vm.loadProviderKey("openrouter"))
        assertTrue(vm.hasProviderKey("openrouter"))
    }

    @Test
    fun `clear removes the provider key`() = runTest {
        val vm = SettingsViewModel(FakeSecretStore(), FakeSettingsStore())
        vm.saveProviderKey("openrouter", "sk-or-abc")

        vm.clearProviderKey("openrouter")

        assertNull(vm.loadProviderKey("openrouter"))
        assertFalse(vm.hasProviderKey("openrouter"))
    }

    @Test
    fun `blank keys are ignored`() = runTest {
        val vm = SettingsViewModel(FakeSecretStore(), FakeSettingsStore())

        vm.saveProviderKey("nvidia", "   ")

        assertNull(vm.loadProviderKey("nvidia"))
    }

    @Test
    fun `default model is persisted`() = runTest {
        val store = FakeSettingsStore()
        val vm = SettingsViewModel(FakeSecretStore(), store)

        vm.setDefaultModel("google/gemini-2.0-flash")

        assertEquals("google/gemini-2.0-flash", store.defaultModel.value)
    }

    @Test
    fun `theme is persisted`() = runTest {
        val store = FakeSettingsStore()
        val vm = SettingsViewModel(FakeSecretStore(), store)

        vm.setTheme(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, store.theme.value)
    }

    @Test
    fun `sign out clears the token and every provider key`() = runTest {
        val secrets = FakeSecretStore()
        val vm = SettingsViewModel(secrets, FakeSettingsStore())
        secrets.saveToken("gho_abc")
        vm.saveProviderKey("openrouter", "sk-or-abc")

        vm.signOut()

        assertNull(secrets.getToken())
        assertNull(secrets.getProviderKey("openrouter"))
    }

    private class FakeSettingsStore : SettingsStore {
        override val defaultModel = MutableStateFlow<String?>(null)
        private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
        override val theme: StateFlow<ThemeMode> = _theme

        override fun setDefaultModel(modelId: String) {
            defaultModel.value = modelId
        }

        override fun setTheme(mode: ThemeMode) {
            _theme.value = mode
        }
    }

    private class FakeSecretStore : SecretStore {
        private var token: String? = null
        private val keys = mutableMapOf<String, String>()

        override fun saveToken(token: String) {
            this.token = token
        }

        override fun getToken(): String? = token

        override fun clearToken() {
            token = null
        }

        override fun saveProviderKey(providerId: String, key: String) {
            keys[providerId] = key
        }

        override fun getProviderKey(providerId: String): String? = keys[providerId]

        override fun clearProviderKey(providerId: String) {
            keys.remove(providerId)
        }

        override fun clearAll() {
            token = null
            keys.clear()
        }
    }
}
