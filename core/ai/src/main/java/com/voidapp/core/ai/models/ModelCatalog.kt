package com.voidapp.core.ai.models

import com.voidapp.core.ai.Model
import com.voidapp.core.ai.Tier

/**
 * The models Void knows, mapped to the tier the user sees.
 *
 * The provider ids are internal: neither the chat nor the settings ever show
 * them. [rank] is the quality order of the models inside the whole catalog
 * (1 is the strongest), used to pick one model when a tier has several.
 */
data class CatalogModel(
    val id: String,
    val providerId: String,
    val tier: Tier,
    val rank: Int,
)

object ModelCatalog {

    /**
     * Order within a tier comes from [CatalogModel.rank].
     *
     * - Red Dwarfs: the two strongest coding models.
     * - White Dwarfs: the two in the middle.
     * - Neutron Stars: the small, fast ones.
     */
    val all: List<CatalogModel> = listOf(
        // Red Dwarfs (high)
        CatalogModel("deepseek-ai/deepseek-v4.1-flash", "nvidia", Tier.RED_DWARF, rank = 1),
        CatalogModel("minimaxai/minimax-m3.1-flash", "nvidia", Tier.RED_DWARF, rank = 2),

        // White Dwarfs (middle)
        CatalogModel("stepfun-ai/step-5-preview", "nvidia", Tier.WHITE_DWARF, rank = 3),
        CatalogModel("laguna-s-2.1", "nara", Tier.WHITE_DWARF, rank = 4),

        // Neutron Stars (small)
        CatalogModel("nvidia/nemotron-3-ultra-550b-a55b", "openrouter", Tier.NEUTRON_STAR, rank = 5),
        CatalogModel("poolside/laguna-xs-2.1", "nvidia", Tier.NEUTRON_STAR, rank = 6),
        CatalogModel("cohere/north-mini-code:free", "openrouter", Tier.NEUTRON_STAR, rank = 7),
    )

    fun forProvider(providerId: String): List<CatalogModel> =
        all.filter { it.providerId == providerId }.sortedBy { it.rank }

    fun forTier(tier: Tier): List<CatalogModel> =
        all.filter { it.tier == tier }.sortedBy { it.rank }

    fun find(modelId: String, providerId: String): CatalogModel? =
        all.firstOrNull { it.id == modelId && it.providerId == providerId }

    /** The quality rank of a model, or a large number when it is unknown. */
    fun rankOf(modelId: String, providerId: String): Int =
        find(modelId, providerId)?.rank ?: Int.MAX_VALUE

    /** Turns a catalog entry into the model the engine calls. */
    fun toModel(entry: CatalogModel): Model = Model(
        id = entry.id,
        providerId = entry.providerId,
        displayName = entry.tier.displayName,
        tier = entry.tier,
        contextWindow = DEFAULT_CONTEXT_WINDOW,
        isFree = true,
    )

    /** For a model the provider reports but the catalog does not know. */
    fun infer(modelId: String, providerId: String, freeModelSuffix: String? = null): Model {
        val tier = all.firstOrNull { it.id == modelId }?.tier ?: Tier.WHITE_DWARF
        return Model(
            id = modelId,
            providerId = providerId,
            displayName = tier.displayName,
            tier = tier,
            contextWindow = DEFAULT_CONTEXT_WINDOW,
            isFree = freeModelSuffix?.let { modelId.endsWith(it) } == true,
        )
    }

    const val DEFAULT_CONTEXT_WINDOW = 32_000
}
