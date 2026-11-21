package com.eivoid.core.ai.plan

/** Where the app reads the current subscription plan from. */
interface PlanProvider {
    fun current(): Plan
}
