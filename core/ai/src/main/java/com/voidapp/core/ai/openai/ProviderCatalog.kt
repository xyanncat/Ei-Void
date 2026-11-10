package com.voidapp.core.ai.openai

/**
 * The providers Void talks to. The models themselves come from
 * `ModelCatalog`, which maps them to the three tiers.
 *
 * Two endpoint notes:
 *
 * - **OpenRouter** is configured with the URL the user supplied
 *   (`/api/v1/responses`). The adapter speaks chat completions, so the
 *   `/responses` suffix is stripped and `/chat/completions` is used instead:
 *   both live on the same host and the Responses API has a different request
 *   and event shape.
 * - **NVIDIA** uses `integrate.api.nvidia.com/v1`, the documented
 *   OpenAI-compatible root. `api.ngc.nvidia.com/v2` is the NGC catalog API
 *   (models, containers, registry) and has no chat endpoint.
 */
object ProviderCatalog {

    val OPENROUTER = OpenAiConfig(
        providerId = "openrouter",
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1/responses",
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://void.local",
            "X-Title" to "Void",
        ),
        freeModelSuffix = ":free",
    )

    val NVIDIA = OpenAiConfig(
        providerId = "nvidia",
        displayName = "NVIDIA NIM",
        baseUrl = "https://integrate.api.nvidia.com/v1",
    )

    val NARA = OpenAiConfig(
        providerId = "nara",
        displayName = "Nara Router",
        baseUrl = "https://router.bynara.id/v1",
    )

    val KIRA = OpenAiConfig(
        providerId = "kira",
        displayName = "Kira AI",
        baseUrl = "https://api.kira.ai/v1", // TODO(user): confirm with the provider
    )

    val all: List<OpenAiConfig> = listOf(OPENROUTER, NVIDIA, NARA, KIRA)

    fun byId(providerId: String): OpenAiConfig? = all.firstOrNull { it.providerId == providerId }
}
