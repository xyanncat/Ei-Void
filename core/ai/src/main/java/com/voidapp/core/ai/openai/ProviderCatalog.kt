package com.voidapp.core.ai.openai

/**
 * The providers Void ships with.
 *
 * `KIRA` and `NARA` still need their base URL and model id confirmed with the
 * user; without a default model they stay unusable until the user names a
 * model for them in Settings.
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
        // Free tier model; the user can override it in Settings.
        defaultModelId = "meta-llama/llama-3.3-70b-instruct:free",
    )

    val NVIDIA = OpenAiConfig(
        providerId = "nvidia",
        displayName = "NVIDIA NIM",
        baseUrl = "https://integrate.api.nvidia.com/v1",
        defaultModelId = "meta/llama-3.1-8b-instruct",
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
