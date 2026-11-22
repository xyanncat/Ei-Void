package com.eivoid.feature.settings

import com.eivoid.core.common.auth.SignOutHook
import com.eivoid.core.database.ChatDao
import com.eivoid.core.database.ChatMessageEntity
import com.eivoid.core.database.ChatSessionEntity
import com.eivoid.core.database.RepoDao
import com.eivoid.core.database.RepoEntity
import com.eivoid.core.database.TierUsage
import com.eivoid.core.database.UsageDao
import com.eivoid.core.database.UsageEntryEntity
import com.eivoid.core.ai.Tier
import com.eivoid.core.reminders.DiscordIdentity
import com.eivoid.core.reminders.DiscordLinkService
import com.eivoid.core.reminders.EiVoidAccount
import com.eivoid.core.security.SecretStore
import com.eivoid.core.workspace.WorkspaceManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
            FakeChatDao(),
            FakeUsageDao(),
            WorkspaceManager(workspaces),
            NoSignOutHook,
            CoroutineScope(Dispatchers.Unconfined),
        )
        val vm = SettingsViewModel(secrets, FakeSettingsStore(), cleaner, NoDiscordLinkService)
        dao.insertAll(listOf(entity()))
        File(workspaces, "owner/repo").mkdirs()

        cleaner.clear().join()

        assertTrue(dao.observeAll().first().isEmpty())
        assertFalse(File(workspaces, "owner/repo").exists())
    }

    @Test
    fun `connecting discord shows the linked username`() = runTest {
        val service = FakeDiscordLinkService(
            linkResult = Result.success(DiscordIdentity(id = "42", username = "cat")),
        )
        val vm = viewModel(discord = service)

        vm.connectDiscord("one-time-token")

        assertEquals("one-time-token", service.lastAccessToken)
        assertTrue(vm.discord.value.linked)
        assertEquals("cat", vm.discord.value.username)
        assertFalse(vm.discord.value.busy)
    }

    @Test
    fun `connecting reports a guild membership failure`() = runTest {
        val service = FakeDiscordLinkService(
            linkResult = Result.failure(
                IllegalStateException(
                    "Join the Ei Void Discord server with this account, then connect again.",
                ),
            ),
        )
        val vm = viewModel(discord = service)

        vm.connectDiscord("one-time-token")

        assertFalse(vm.discord.value.linked)
        assertTrue(vm.discord.value.message.contains("Join the Ei Void Discord server"))
        assertFalse(vm.discord.value.busy)
    }

    @Test
    fun `refresh loads an existing discord link`() = runTest {
        val service = FakeDiscordLinkService(
            accountResult = Result.success(
                EiVoidAccount(discord = DiscordIdentity(id = "7", username = "void")),
            ),
        )
        val vm = viewModel(discord = service)

        vm.refreshDiscord()

        assertTrue(vm.discord.value.linked)
        assertEquals("void", vm.discord.value.username)
    }

    @Test
    fun `disconnect clears the linked discord user`() = runTest {
        val service = FakeDiscordLinkService(
            linkResult = Result.success(DiscordIdentity(id = "42", username = "cat")),
        )
        val vm = viewModel(discord = service)
        vm.connectDiscord("one-time-token")

        vm.disconnectDiscord()

        assertFalse(vm.discord.value.linked)
        assertEquals("", vm.discord.value.username)
        assertTrue(vm.discord.value.message.contains("disconnected"))
    }

    @Test
    fun `test requires a linked account before calling the service`() = runTest {
        val service = FakeDiscordLinkService()
        val vm = viewModel(discord = service)

        vm.testDiscord()

        assertFalse(service.testCalled)
        assertTrue(vm.discord.value.message.contains("Connect Discord first"))
    }

    @Test
    fun `sign out resets the discord card`() = runTest {
        val service = FakeDiscordLinkService(
            linkResult = Result.success(DiscordIdentity(id = "42", username = "cat")),
        )
        val vm = viewModel(discord = service)
        vm.connectDiscord("one-time-token")

        vm.signOut()

        assertFalse(vm.discord.value.linked)
        assertEquals("", vm.discord.value.username)
    }

    private fun viewModel(
        secrets: FakeSecretStore = FakeSecretStore(),
        store: FakeSettingsStore = FakeSettingsStore(),
        discord: DiscordLinkService = NoDiscordLinkService,
    ): SettingsViewModel {
        val workspaces = Files.createTempDirectory("void-workspaces").toFile()
        val cleaner = SessionCleaner(
            secrets,
            FakeDao(),
            FakeChatDao(),
            FakeUsageDao(),
            WorkspaceManager(workspaces),
            NoSignOutHook,
            CoroutineScope(Dispatchers.Unconfined),
        )
        return SettingsViewModel(secrets, store, cleaner, discord)
    }

    private class FakeDiscordLinkService(
        private var linkResult: Result<DiscordIdentity> =
            Result.failure(IllegalStateException("not configured")),
        private var unlinkResult: Result<Unit> = Result.success(Unit),
        private var testResult: Result<Unit> = Result.success(Unit),
        private var accountResult: Result<EiVoidAccount> =
            Result.success(EiVoidAccount()),
    ) : DiscordLinkService {
        var lastAccessToken: String? = null
        var testCalled = false

        override suspend fun link(accessToken: String): Result<DiscordIdentity> {
            lastAccessToken = accessToken
            return linkResult
        }

        override suspend fun unlink(): Result<Unit> = unlinkResult

        override suspend fun test(): Result<Unit> {
            testCalled = true
            return testResult
        }

        override suspend fun account(): Result<EiVoidAccount> = accountResult
    }

    private object NoDiscordLinkService : DiscordLinkService {
        override suspend fun link(accessToken: String): Result<DiscordIdentity> =
            Result.failure(IllegalStateException("not configured"))

        override suspend fun unlink(): Result<Unit> =
            Result.failure(IllegalStateException("not configured"))

        override suspend fun test(): Result<Unit> =
            Result.failure(IllegalStateException("not configured"))

        override suspend fun account(): Result<EiVoidAccount> =
            Result.failure(IllegalStateException("not configured"))
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

        private val _plan = MutableStateFlow(com.eivoid.core.ai.plan.Plan.FREE)
        override val plan: StateFlow<com.eivoid.core.ai.plan.Plan> = _plan

        override fun setTheme(mode: ThemeMode) {
            _theme.value = mode
        }

        override fun setPreferredTier(tier: Tier?) {
            _preferredTier.value = tier
        }

        override fun setPlan(plan: com.eivoid.core.ai.plan.Plan) {
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

        override fun observeLinked(): Flow<List<RepoEntity>> =
            state.map { all -> all.filter { it.isLinked } }

        override suspend fun linkedCount(): Int = state.value.count { it.isLinked }

        override suspend fun linkedIds(): List<String> =
            state.value.filter { it.isLinked }.map { it.id }

        override suspend fun setLinked(id: String, linked: Boolean) {
            state.value = state.value.map { entity ->
                if (entity.id == id) entity.copy(isLinked = linked) else entity
            }
        }
    }

    private object NoSignOutHook : SignOutHook {
        override suspend fun onSignOut() = Unit
    }

    private class FakeChatDao : ChatDao {
        override suspend fun insertSession(session: ChatSessionEntity) = Unit

        override suspend fun insertMessages(messages: List<ChatMessageEntity>) = Unit

        override fun observeSessions(repoFullName: String): Flow<List<ChatSessionEntity>> =
            MutableStateFlow(emptyList())

        override fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>> =
            MutableStateFlow(emptyList())

        override suspend fun deleteMessages(sessionId: String) = Unit

        override suspend fun deleteSessionRow(sessionId: String) = Unit

        override suspend fun deleteAllMessages() = Unit

        override suspend fun deleteAllSessions() = Unit
    }

    private class FakeUsageDao : UsageDao {
        override suspend fun insert(entry: UsageEntryEntity) = Unit

        override suspend fun tokensSince(tier: String, since: Long): Long = 0L

        override fun totalsSince(since: Long): Flow<List<TierUsage>> =
            MutableStateFlow(emptyList())

        override suspend fun deleteBefore(before: Long) = Unit

        override suspend fun deleteAll() = Unit
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
