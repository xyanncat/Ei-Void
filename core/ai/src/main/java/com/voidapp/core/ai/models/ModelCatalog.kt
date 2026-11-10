package com.voidapp.core.ai.models

import com.voidapp.core.ai.Model
import com.voidapp.core.ai.Tier

/**
 * Turns a model id into the metadata the router needs: how heavy the model is,
 * how much context it takes and whether it is free.
 *
 * The provider reports which models exist; this decides how to rank them.
 */
object ModelCatalog {

    /** Models whose weight cannot be guessed from the id. */
    private val tierOverrides = mapOf(
        "gpt-4o-mini" to Tier.FAST,
        "gpt-4o" to Tier.STANDARD,
        "gpt-4.1-mini" to Tier.FAST,
        "gpt-4.1" to Tier.STANDARD,
        "claude-3-5-haiku" to Tier.FAST,
        "claude-3-5-sonnet" to Tier.STANDARD,
        "claude-sonnet-4" to Tier.STANDARD,
        "claude-opus-4" to Tier.STRONG,
        "llama-3.1-8b-instruct" to Tier.FAST,
        "llama-3.3-70b-instruct" to Tier.STRONG,
        "deepseek-coder-v2" to Tier.STANDARD,
        "qwen2.5-coder-32b-instruct" to Tier.STANDARD,
        "gemma-3-4b-it" to Tier.FAST,
    )

    private val contextOverrides = mapOf(
        "gpt-4o" to 128_000,
        "gpt-4.1" to 1_000_000,
        "claude-3-5-sonnet" to 200_000,
        "claude-sonnet-4" to 200_000,
        "llama-3.1-8b-instruct" to 128_000,
        "llama-3.3-70b-instruct" to 128_000,
    )

    const val DEFAULT_CONTEXT_WINDOW = 32_000

    fun tierFor(modelId: String): Tier {
        val bare = modelId.substringAfterLast('/')
        tierOverrides[bare]?.let { return it }

        val lower = bare.lowercase()
        return when {
            FAST_MARKERS.any { lower.contains(it) } -> Tier.FAST
            STRONG_MARKERS.any { lower.contains(it) } -> Tier.STRONG
            else -> Tier.STANDARD
        }
    }

    fun contextWindowFor(modelId: String): Int =
        contextOverrides[modelId.substringAfterLast('/')] ?: DEFAULT_CONTEXT_WINDOW

    /** Free models say so in their id, or the whole provider is free. */
    fun isFree(modelId: String, providerIsFree: Boolean = false): Boolean =
        providerIsFree || modelId.endsWith(":free") || modelId.contains("/free")

    fun infer(modelId: String, providerId: String, providerIsFree: Boolean = false): Model = Model(
        id = modelId,
        providerId = providerId,
        displayName = modelId.substringAfterLast('/'),
        tier = tierFor(modelId),
        contextWindow = contextWindowFor(modelId),
        isFree = isFree(modelId, providerIsFree),
    )

    private val FAST_MARKERS = listOf("mini", "flash", "haiku", "8b", "4b", "tiny", "small")
    private val STRONG_MARKERS = listOf("opus", "ultra", "70b", "405b", "large")
}
