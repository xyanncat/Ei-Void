package com.voidapp.feature.settings

import com.voidapp.core.database.RepoDao
import com.voidapp.core.database.RepoEntity
import com.voidapp.core.ai.Tier
import com.voidapp.core.security.SecretStore
import com.voidapp.core.workspace.WorkspaceManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun `save and load a provider key`() = runTest {
        val vm = viewModel()

        vm.saveProviderKey("openrouter", "sk-or-abc")

        assertEquals("sk-or-abc", vm.loadProviderKey("openrouter"))
        assertTrue(vm.hasProviderKey("openrouter"))
    }

    @Test
    fun `clear removes the provider key`() = runTest {
        val vm = viewModel()
        vm.saveProviderKey("openrouter", "sk-or-abc")

        vm.clearProviderKey("openrouter")

        assertNull(vm.loadProviderKey("openrouter"))
        assertFalse(vm.hasProviderKey("openrouter"))
    }

    @Test
    fun `blank keys are ignored`() = runTest {
        val vm = viewModel()

        vm.saveProviderKey("nvidia", "   ")

        assertNull(vm.loadProviderKey("nvidia"))
    }

    @Test
    fun `the preferred tier is persisted`() = runTest {
        val store = FakeSettingsStore()
        val vm = viewModel(store = store)

        vm.setPreferredTier(Tier.RED_DWARF)

        assertEquals(Tier.RED_DWARF, store.preferredTier.value)
    }

    @Test
    fun `theme is persisted`() = runTest {
        val store = FakeSettingsStore()
        val vm = viewModel(store = store)

        vm.setTheme(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, store.theme.value)
    }

    @Test
    fun `sign out clears the token and every provider key`() = runTest {
        val secrets = FakeSecretStore()
        val vm = viewModel(secrets = secrets)
        secrets.saveToken("gho_abc")
        vm.saveProviderKey("openrouter", "sk-or-abc")

        vm.signOut()

        assertNull(secrets.getToken())
        assertNull(secrets.getProviderKey("openrouter"))
    }

    @Test
    fun `sign out clears the cached repositories and the cloned workspaces`() = runTest {
        val dao = FakeDao()
        val secrets = FakeSecretStore()
        val workspaces = Files.createTempDirectory("void-workspaces").toFile()
        val cleaner = SessionCleaner(
            secrets,
            dao,
            WorkspaceManager(workspaces),
            CoroutineScope(Dispatchers.Unconfined),
        )
        val vm = SettingsViewModel(secrets, FakeSettingsStore(), cleaner)
        dao.insertAll(listOf(entity()))
        File(workspaces, "owner/repo").mkdirs()

        cleaner.clear().join()

        assertTrue(dao.observeAll().first().isEmpty())
        assertFalse(File(workspaces, "owner/repo").exists())
    }

    private fun viewModel(
        secrets: FakeSecretStore = FakeSecretStore(),
        store: FakeSettingsStore = FakeSettingsStore(),
    ): SettingsViewModel {
        val workspaces = Files.createTempDirectory("void-workspaces").toFile()
        val cleaner = SessionCleaner(
            secrets,
            FakeDao(),
            WorkspaceManager(workspaces),
            CoroutineScope(Dispatchers.Unconfined),
        )
        return SettingsViewModel(secrets, store, cleaner)
    }

    private fun entity() = RepoEntity(
        id = "1",
        owner = "owner",
        name = "repo",
        fullName = "owner/repo",
        isPrivate = false,
        defaultBranch = "main",
        description = null,
        updatedAt = 0L,
    )

    private class FakeSettingsStore : SettingsStore {
        private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
        override val theme: StateFlow<ThemeMode> = _theme

        private val _preferredTier = MutableStateFlow<Tier?>(null)
        override val preferredTier: StateFlow<Tier?> = _preferredTier

        private val _plan = MutableStateFlow(com.voidapp.core.ai.plan.Plan.FREE)
        override val plan: StateFlow<com.voidapp.core.ai.plan.Plan> = _plan

        override fun setTheme(mode: ThemeMode) {
            _theme.value = mode
        }

        override fun setPreferredTier(tier: Tier?) {
            _preferredTier.value = tier
        }

        override fun setPlan(plan: com.voidapp.core.ai.plan.Plan) {
            _plan.value = plan
        }
    }

    private class FakeDao : RepoDao {
        private val state = MutableStateFlow<List<RepoEntity>>(emptyList())

        override suspend fun insertAll(repos: List<RepoEntity>) {
            val merged = state.value.associateBy { it.id }.toMutableMap()
            repos.forEach { merged[it.id] = it }
            state.value = merged.values.toList()
        }

        override fun observeAll(): Flow<List<RepoEntity>> = state

        override fun search(query: String): Flow<List<RepoEntity>> = state

        override suspend fun deleteAll() {
            state.value = emptyList()
        }

        override suspend fun deleteNotIn(ids: List<String>) {
            state.value = state.value.filter { it.id in ids }
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
