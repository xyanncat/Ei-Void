package com.voidapp.core.ai.openai

/**
 * How one provider is reached. Every provider Void supports speaks the
 * OpenAI chat-completions dialect, so they differ only in base URL and
 * headers.
 */
data class OpenAiConfig(
    val providerId: String,
    val displayName: String,
    val baseUrl: String,
    val authHeader: String = "Authorization",
    val authScheme: String = "Bearer",
    val extraHeaders: Map<String, String> = emptyMap(),
    /** Suffix that marks a free model, e.g. `:free` on OpenRouter. */
    val freeModelSuffix: String? = null,
) {
    val chatCompletionsUrl: String get() = baseUrl.trimEnd('/') + "/chat/completions"
    val modelsUrl: String get() = baseUrl.trimEnd('/') + "/models"
}
