package com.voidapp.app.di

import android.content.Context
import androidx.room.Room
import com.voidapp.app.BuildConfig
import com.voidapp.core.common.github.GitHubApi
import com.voidapp.core.common.github.GitHubAuthApi
import com.voidapp.core.common.github.KtorGitHubAuthApi
import com.voidapp.core.common.github.KtorGitHubApi
import com.voidapp.core.common.network.NetworkModule
import com.voidapp.core.common.network.PinConfig
import com.voidapp.core.database.RepoDao
import com.voidapp.core.database.VoidDatabase
import com.voidapp.core.git.CloneManager
import com.voidapp.core.git.GitClient
import com.voidapp.core.git.RepoLocks
import com.voidapp.core.security.EncryptedSecretStore
import com.voidapp.core.security.SecretStore
import com.voidapp.core.workspace.WorkspaceManager
import com.voidapp.feature.settings.DataStoreSettingsStore
import com.voidapp.feature.settings.SessionCleaner
import com.voidapp.feature.settings.SettingsStore
import com.voidapp.feature.settings.settingsDataStore
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
    fun provideGitHubAuthApi(client: HttpClient): GitHubAuthApi =
        KtorGitHubAuthApi(client, BuildConfig.GITHUB_CLIENT_ID)

    @Provides
    @Singleton
    fun provideGitHubApi(client: HttpClient, secretStore: SecretStore): GitHubApi =
        KtorGitHubApi(client, secretStore)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VoidDatabase =
        Room.databaseBuilder(context, VoidDatabase::class.java, "void.db").build()

    @Provides
    fun provideRepoDao(database: VoidDatabase): RepoDao = database.repoDao()

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

    @Provides
    @Singleton
    fun provideSessionCleaner(
        secretStore: SecretStore,
        repoDao: RepoDao,
        workspaceManager: WorkspaceManager,
        scope: CoroutineScope,
    ): SessionCleaner = SessionCleaner(secretStore, repoDao, workspaceManager, scope)
}
