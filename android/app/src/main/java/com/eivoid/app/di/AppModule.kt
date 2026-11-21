package com.eivoid.app.di

import android.content.Context
import androidx.room.Room
import com.eivoid.app.BuildConfig
import com.eivoid.core.common.auth.AuthConfiguration
import com.eivoid.core.common.auth.SignOutHook
import com.eivoid.core.common.github.GitHubApi
import com.eivoid.core.common.github.KtorGitHubApi
import com.eivoid.core.common.network.NetworkModule
import com.eivoid.core.common.network.PinConfig
import com.eivoid.core.database.ChatDao
import com.eivoid.core.database.RepoDao
import com.eivoid.core.database.UsageDao
import com.eivoid.core.database.VoidDatabase
import com.eivoid.core.git.CloneManager
import com.eivoid.core.git.GitClient
import com.eivoid.core.git.RepoLocks
import com.eivoid.core.reminders.AlarmScheduler
import com.eivoid.core.reminders.NotificationHelper
import com.eivoid.core.security.EncryptedSecretStore
import com.eivoid.core.security.SecretStore
import com.eivoid.core.workspace.WorkspaceManager
import com.eivoid.feature.reminders.AndroidReminderPermissionSource
import com.eivoid.feature.reminders.ReminderPermissionSource
import com.eivoid.feature.settings.DataStoreSettingsStore
import com.eivoid.feature.settings.SessionCleaner
import com.eivoid.feature.settings.SettingsStore
import com.eivoid.feature.settings.settingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Empty until release pins are generated. Adding a [PinConfig] here turns
     * pinning on for that host with no other change.
     */
    private val PINS: List<PinConfig> = emptyList()

    @Provides
    @Singleton
    fun provideSecretStore(@ApplicationContext context: Context): SecretStore =
        EncryptedSecretStore(context)

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient =
        NetworkModule.provideKtorClient(NetworkModule.provideOkHttpClient(PINS))

    @Provides
    @Singleton
    fun provideAuthConfiguration(): AuthConfiguration = AuthConfiguration(
        clerkPublishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY,
        backendUrl = BuildConfig.EI_VOID_API_URL,
        accountPortalUrl = BuildConfig.CLERK_ACCOUNT_PORTAL_URL,
    )

    @Provides
    @Singleton
    fun provideGitHubApi(client: HttpClient, secretStore: SecretStore): GitHubApi =
        KtorGitHubApi(client, secretStore)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VoidDatabase =
        Room.databaseBuilder(context, VoidDatabase::class.java, "void.db")
            // The reminder feature moved the schema to version 4; pass every
            // migration in order so existing installs upgrade cleanly.
            .addMigrations(*VoidDatabase.ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideRepoDao(database: VoidDatabase): RepoDao = database.repoDao()

    @Provides
    fun provideChatDao(database: VoidDatabase): ChatDao = database.chatDao()

    @Provides
    fun provideUsageDao(database: VoidDatabase): UsageDao = database.usageDao()

    @Provides
    @Singleton
    fun provideWorkspaceRoot(@ApplicationContext context: Context): File =
        File(context.filesDir, "void/workspaces")

    @Provides
    @Singleton
    fun provideWorkspaceManager(root: File): WorkspaceManager = WorkspaceManager(root)

    @Provides
    @Singleton
    fun provideRepoLocks(): RepoLocks = RepoLocks()

    @Provides
    @Singleton
    fun provideCloneManager(root: File, locks: RepoLocks): CloneManager =
        CloneManager(root, locks = locks)

    @Provides
    @Singleton
    fun provideGitClient(locks: RepoLocks): GitClient = GitClient(locks)

    /**
     * Application scoped coroutine scope. Work that must outlive a screen
     * (sign-out cleanup, settings persistence) runs here.
     */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideSettingsStore(
        @ApplicationContext context: Context,
        scope: CoroutineScope,
    ): SettingsStore = DataStoreSettingsStore(settingsDataStore(context), scope)

    /**
     * :core:reminders already provides ReminderRepository, AlarmScheduler,
     * NotificationHelper and ReminderSettingsStore through its own Hilt module,
     * which is aggregated into this component. Re-declaring them here would be
     * a duplicate binding, so the app layer only supplies the platform
     * permission reader the reminder ViewModels inject.
     */
    @Provides
    @Singleton
    fun provideReminderPermissionSource(
        @ApplicationContext context: Context,
        notificationHelper: NotificationHelper,
        scheduler: AlarmScheduler,
    ): ReminderPermissionSource = AndroidReminderPermissionSource(
        context = context,
        notificationHelper = notificationHelper,
        scheduler = scheduler,
    )

    @Provides
    @Singleton
    fun provideSessionCleaner(
        secretStore: SecretStore,
        repoDao: RepoDao,
        chatDao: ChatDao,
        usageDao: UsageDao,
        workspaceManager: WorkspaceManager,
        signOutHook: SignOutHook,
        scope: CoroutineScope,
    ): SessionCleaner = SessionCleaner(
        secretStore = secretStore,
        repoDao = repoDao,
        chatDao = chatDao,
        usageDao = usageDao,
        workspaceManager = workspaceManager,
        signOutHook = signOutHook,
        scope = scope,
    )
}
