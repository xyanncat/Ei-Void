package com.eivoid.app.agent

import com.eivoid.core.ai.plan.Plan
import com.eivoid.core.ai.plan.PlanProvider
import com.eivoid.feature.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/** The plan the app is currently on. */
@Singleton
class SettingsPlanProvider @Inject constructor(
    private val settingsStore: SettingsStore,
) : PlanProvider {
    override fun current(): Plan = settingsStore.plan.value
}
