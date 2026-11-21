package com.eivoid.app.di

import com.eivoid.app.agent.RoomUsageMeter
import com.eivoid.app.agent.SettingsPlanProvider
import com.eivoid.app.agent.SecretProviderResolver
import com.eivoid.core.agent.AgentEngine
import com.eivoid.core.agent.AgentLimits
import com.eivoid.core.agent.ProviderResolver
import com.eivoid.core.agent.ToolRegistry
import com.eivoid.core.agent.UsageMeter
import com.eivoid.core.agent.context.ContextManager
import com.eivoid.core.agent.planner.Planner
import com.eivoid.core.agent.tools.FileTools
import com.eivoid.core.agent.tools.GitTools
import com.eivoid.core.agent.tools.RepoTools
import com.eivoid.core.ai.plan.PlanProvider
import com.eivoid.core.ai.router.SmartRouter
import com.eivoid.core.common.github.GitHubApi
import com.eivoid.core.git.GitClient
import com.eivoid.core.workspace.WorkspaceManager
import com.eivoid.feature.settings.SettingsStore
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

    @Binds
    @Singleton
    abstract fun bindUsageMeter(impl: RoomUsageMeter): UsageMeter

    @Binds
    @Singleton
    abstract fun bindPlanProvider(impl: SettingsPlanProvider): PlanProvider

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
            planProvider: PlanProvider,
            usageMeter: UsageMeter,
        ): AgentEngine = AgentEngine(
            registry = registry,
            resolver = resolver,
            router = SmartRouter(
                preferredTier = { settingsStore.preferredTier.value },
                plan = { planProvider.current() },
            ),
            contextManager = ContextManager(),
            planner = Planner(),
            limits = AgentLimits(),
            usageMeter = usageMeter,
        )
    }
}
