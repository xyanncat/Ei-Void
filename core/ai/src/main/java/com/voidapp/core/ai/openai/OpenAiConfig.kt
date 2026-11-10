package com.voidapp.core.ai.openai

/**
 * How one provider is reached. Every provider Void supports speaks the
 * OpenAI chat-completions dialect, so they differ only in base URL and
 * headers.
 *
 * [baseUrl] may be a plain root (`.../v1`) or a pasted endpoint
 * (`.../v1/chat/completions`, `.../v1/responses`). The endpoint suffix is
 * stripped, so both forms reach the same chat-completions URL.
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
    /** The provider root, whatever endpoint was configured. */
    val root: String
        get() {
            val trimmed = baseUrl.trimEnd('/')
            val suffix = ENDPOINT_SUFFIXES.firstOrNull { trimmed.endsWith(it) }
            return suffix?.let { trimmed.removeSuffix(it) } ?: trimmed
        }

    val chatCompletionsUrl: String get() = "$root/chat/completions"
    val modelsUrl: String get() = "$root/models"

    private companion object {
        /** Longest first: `/chat/completions` contains `/completions`. */
        val ENDPOINT_SUFFIXES = listOf("/chat/completions", "/completions", "/responses")
    }
}
