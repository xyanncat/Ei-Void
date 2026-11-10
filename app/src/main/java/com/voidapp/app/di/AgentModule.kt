package com.voidapp.app.di

import com.voidapp.app.agent.SecretProviderResolver
import com.voidapp.core.agent.AgentEngine
import com.voidapp.core.agent.AgentLimits
import com.voidapp.core.agent.ProviderResolver
import com.voidapp.core.agent.ToolRegistry
import com.voidapp.core.agent.context.ContextManager
import com.voidapp.core.agent.planner.Planner
import com.voidapp.core.agent.tools.FileTools
import com.voidapp.core.agent.tools.GitTools
import com.voidapp.core.agent.tools.RepoTools
import com.voidapp.core.ai.router.SmartRouter
import com.voidapp.core.common.github.GitHubApi
import com.voidapp.core.git.GitClient
import com.voidapp.core.workspace.WorkspaceManager
import com.voidapp.feature.settings.SettingsStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    @Binds
    @Singleton
    abstract fun bindProviderResolver(impl: SecretProviderResolver): ProviderResolver

    companion object {

        @Provides
        @Singleton
        fun provideToolRegistry(
            workspaceManager: WorkspaceManager,
            gitClient: GitClient,
            gitHubApi: GitHubApi,
        ): ToolRegistry = ToolRegistry(
            FileTools(workspaceManager).all() +
                GitTools(gitClient).all() +
                RepoTools(gitHubApi).all(),
        )

        @Provides
        @Singleton
        fun provideAgentEngine(
            registry: ToolRegistry,
            resolver: ProviderResolver,
            settingsStore: SettingsStore,
        ): AgentEngine = AgentEngine(
            registry = registry,
            resolver = resolver,
            router = SmartRouter(preferredTier = { settingsStore.preferredTier.value }),
            contextManager = ContextManager(),
            planner = Planner(),
            limits = AgentLimits(),
        )
    }
}
