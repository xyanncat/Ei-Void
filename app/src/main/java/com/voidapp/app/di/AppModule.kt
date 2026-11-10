package com.voidapp.app.di

import android.content.Context
import com.voidapp.app.BuildConfig
import com.voidapp.core.common.github.GitHubAuthApi
import com.voidapp.core.common.github.KtorGitHubAuthApi
import com.voidapp.core.common.network.NetworkModule
import com.voidapp.core.common.network.PinConfig
import com.voidapp.core.security.EncryptedSecretStore
import com.voidapp.core.security.SecretStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

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
}
