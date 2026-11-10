package com.voidapp.core.ai.openai

/**
 * The providers Void talks to. The models themselves come from
 * `ModelCatalog`, which maps them to the three tiers.
 *
 * `KIRA` still needs its base URL confirmed with the user.
 */
object ProviderCatalog {

    val OPENROUTER = OpenAiConfig(
        providerId = "openrouter",
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
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
        baseUrl = "https://api.nara.ai/v1", // TODO(user): confirm with the provider
    )

    val KIRA = OpenAiConfig(
        providerId = "kira",
        displayName = "Kira AI",
        baseUrl = "https://api.kira.ai/v1", // TODO(user): confirm with the provider
    )

    val all: List<OpenAiConfig> = listOf(OPENROUTER, NVIDIA, NARA, KIRA)

    fun byId(providerId: String): OpenAiConfig? = all.firstOrNull { it.providerId == providerId }
}
