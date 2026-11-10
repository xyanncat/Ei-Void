package com.voidapp.core.ai.openai

/**
 * The providers Void ships with.
 *
 * `KIRA` and `NARA` still need their base URL confirmed with the user; the
 * values below are placeholders and only matter once a key for that provider
 * is stored.
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

    val KIRA = OpenAiConfig(
        providerId = "kira",
        displayName = "Kira AI",
        baseUrl = "https://api.kira.ai/v1", // TODO(user): confirm with the provider
    )

    val NARA = OpenAiConfig(
        providerId = "nara",
        displayName = "Nara Router",
        baseUrl = "https://api.nara.ai/v1", // TODO(user): confirm with the provider
    )

    val all: List<OpenAiConfig> = listOf(OPENROUTER, NVIDIA, KIRA, NARA)

    fun byId(providerId: String): OpenAiConfig? = all.firstOrNull { it.providerId == providerId }
}
