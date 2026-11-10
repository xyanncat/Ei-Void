package com.voidapp.core.agent

import com.voidapp.core.ai.AiProvider
import com.voidapp.core.ai.Model

/** A provider the user can reach, with one of its models. */
data class ProviderTarget(
    val provider: AiProvider,
    val model: Model,
)

/**
 * Where the engine gets its providers and their keys. The engine never reads
 * storage itself.
 */
interface ProviderResolver {
    /** The providers that have a key, with the models they offer. */
    suspend fun targets(): List<ProviderTarget>

    /** The key of one provider, or null when it is not configured. */
    suspend fun keyFor(providerId: String): String?
}
