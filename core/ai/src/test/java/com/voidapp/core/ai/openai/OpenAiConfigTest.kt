package com.voidapp.core.ai.openai

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiConfigTest {

    private fun config(baseUrl: String) = OpenAiConfig(
        providerId = "test",
        displayName = "Test",
        baseUrl = baseUrl,
    )

    @Test
    fun `a plain root gets the chat completions path`() {
        assertEquals(
            "https://router.bynara.id/v1/chat/completions",
            config("https://router.bynara.id/v1").chatCompletionsUrl,
        )
    }

    @Test
    fun `a trailing slash is ignored`() {
        assertEquals(
            "https://router.bynara.id/v1/chat/completions",
            config("https://router.bynara.id/v1/").chatCompletionsUrl,
        )
    }

    @Test
    fun `a pasted chat completions endpoint is kept`() {
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            config("https://openrouter.ai/api/v1/chat/completions").chatCompletionsUrl,
        )
    }

    @Test
    fun `a pasted responses endpoint is normalised to chat completions`() {
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            config("https://openrouter.ai/api/v1/responses").chatCompletionsUrl,
        )
    }

    @Test
    fun `the model list uses the root`() {
        assertEquals(
            "https://openrouter.ai/api/v1/models",
            config("https://openrouter.ai/api/v1/responses").modelsUrl,
        )
    }

    @Test
    fun `the shipped providers reach their documented endpoints`() {
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            ProviderCatalog.OPENROUTER.chatCompletionsUrl,
        )
        assertEquals(
            "https://integrate.api.nvidia.com/v1/chat/completions",
            ProviderCatalog.NVIDIA.chatCompletionsUrl,
        )
        assertEquals(
            "https://router.bynara.id/v1/chat/completions",
            ProviderCatalog.NARA.chatCompletionsUrl,
        )
    }
}
