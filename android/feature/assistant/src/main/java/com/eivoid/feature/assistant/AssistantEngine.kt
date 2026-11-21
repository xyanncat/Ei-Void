package com.eivoid.feature.assistant

import com.eivoid.core.agent.ProviderResolver
import com.eivoid.core.ai.AiProvider
import com.eivoid.core.ai.AiProviderException
import com.eivoid.core.ai.ChatChunk
import com.eivoid.core.ai.ChatMessage
import com.eivoid.core.ai.ChatRequest
import com.eivoid.core.ai.Role
import com.eivoid.core.ai.router.Candidate
import com.eivoid.core.ai.router.Route
import com.eivoid.core.ai.router.SmartRouter
import com.eivoid.feature.settings.SettingsStore
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * The assistant's turn-taking: pick a tier through [SmartRouter], stream the
 * answer from the provider that key belongs to, and turn every failure into a
 * short sentence a user can act on.
 *
 * It owns no storage and reads the key per call, so nothing here can leak an
 * API key into a message, a log or the UI.
 */
open class AssistantEngine @Inject constructor(
    private val resolver: ProviderResolver,
    private val settingsStore: SettingsStore,
) {

    /**
     * Streams the answer to [prompt] into [onDelta].
     *
     * [history] is the conversation *before* this prompt: the engine appends
     * the prompt itself. One fallback is attempted when the chosen provider
     * fails; there is no retry loop, so the screen can never stall.
     */
    open suspend fun send(
        prompt: String,
        history: List<AssistantMessage>,
        onDelta: (String) -> Unit,
    ): Result<Unit> {
        val targets = try {
            resolver.targets()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        if (targets.isEmpty()) {
            return Result.failure(AiProviderException(NO_PROVIDER_MESSAGE))
        }

        val candidates = targets.map { Candidate(it.provider, it.model) }
        val router = SmartRouter(
            preferredTier = { settingsStore.preferredTier.value },
            plan = { settingsStore.plan.value },
        )

        val first = router.pick(prompt, candidates)
            ?: return Result.failure(AiProviderException(NO_PROVIDER_MESSAGE))

        val firstAttempt = attempt(first, candidates, prompt, history, onDelta)
        if (firstAttempt.isSuccess) return firstAttempt

        val fallback = router.next(prompt, candidates, setOf(first.key))
            ?: return firstAttempt
        return attempt(fallback, candidates, prompt, history, onDelta)
    }

    private suspend fun attempt(
        route: Route,
        candidates: List<Candidate>,
        prompt: String,
        history: List<AssistantMessage>,
        onDelta: (String) -> Unit,
    ): Result<Unit> {
        val provider = providerFor(route, candidates)
            ?: return Result.failure(AiProviderException(GENERIC_FAILURE_MESSAGE))

        val key = try {
            resolver.keyFor(provider.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (key.isNullOrBlank()) {
            return Result.failure(AiProviderException(NO_KEY_MESSAGE))
        }

        val request = ChatRequest(
            model = route.model.id,
            messages = buildMessages(history, prompt),
            tools = emptyList(),
            temperature = route.model.temperature ?: 0.5,
            topP = route.model.topP,
            maxTokens = route.model.maxTokens ?: 2048,
            stream = true,
            includeUsage = false,
        )

        return try {
            provider.chat(request, key).collect { chunk ->
                when (chunk) {
                    is ChatChunk.Delta -> if (chunk.text.isNotEmpty()) onDelta(chunk.text)
                    is ChatChunk.Finished -> Unit
                    else -> Unit
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiProviderException) {
            Result.failure(AiProviderException(shortError(e, key)))
        } catch (e: Exception) {
            Result.failure(AiProviderException(GENERIC_FAILURE_MESSAGE))
        }
    }

    private fun providerFor(route: Route, candidates: List<Candidate>): AiProvider? =
        candidates.firstOrNull {
            it.provider.id == route.providerId && it.model.id == route.model.id
        }?.provider ?: candidates.firstOrNull { it.provider.id == route.providerId }?.provider

    private fun buildMessages(
        history: List<AssistantMessage>,
        prompt: String,
    ): List<ChatMessage> = buildList {
        add(ChatMessage(Role.SYSTEM, ASSISTANT_SYSTEM_PROMPT))
        history.forEach { message ->
            if (message.text.isNotBlank()) {
                add(
                    ChatMessage(
                        role = when (message.role) {
                            AssistantRole.USER -> Role.USER
                            AssistantRole.ASSISTANT -> Role.ASSISTANT
                        },
                        content = message.text,
                    ),
                )
            }
        }
        add(ChatMessage(Role.USER, prompt))
    }

    /**
     * A provider's error message, shortened and with the key scrubbed, so a
     * verbose upstream error cannot fill the screen or expose a credential.
     */
    private fun shortError(error: AiProviderException, key: String): String {
        val raw = error.message?.trim().orEmpty()
        if (raw.isEmpty()) return GENERIC_FAILURE_MESSAGE
        val scrubbed = if (key.isNotEmpty()) raw.replace(key, "[redacted]") else raw
        return scrubbed.take(MAX_ERROR_LENGTH)
    }

    companion object {
        const val NO_PROVIDER_MESSAGE: String =
            "No AI provider is connected yet. Add a provider key in Customize to start chatting."
        const val NO_KEY_MESSAGE: String =
            "This provider has no key yet. Add one in Customize, then try again."
        const val GENERIC_FAILURE_MESSAGE: String =
            "Ei Void could not reach the assistant. Check Customize and try again."

        private const val MAX_ERROR_LENGTH = 160
    }
}
