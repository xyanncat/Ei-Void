package com.voidapp.core.agent.planner

import com.voidapp.core.ai.AiProvider
import com.voidapp.core.ai.ChatChunk
import com.voidapp.core.ai.ChatMessage
import com.voidapp.core.ai.ChatRequest
import com.voidapp.core.ai.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList

/**
 * Turns a complex task into a short list of steps.
 *
 * The planner is best effort: when the model does not answer with a usable
 * list, the run continues without a plan.
 */
class Planner(
    private val maxSteps: Int = 8,
) {

    suspend fun plan(
        task: String,
        provider: AiProvider,
        apiKey: String,
        model: String,
    ): List<String> {
        val request = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage(Role.SYSTEM, SYSTEM_PROMPT),
                ChatMessage(Role.USER, task),
            ),
            temperature = 0.0,
            maxTokens = 512,
        )

        val text = try {
            provider.chat(request, apiKey).collectText()
        } catch (error: Exception) {
            return emptyList()
        }

        return parseSteps(text)
    }

    private suspend fun Flow<ChatChunk>.collectText(): String {
        val builder = StringBuilder()
        collect { chunk ->
            if (chunk is ChatChunk.Delta) builder.append(chunk.text)
        }
        return builder.toString()
    }

    internal fun parseSteps(text: String): List<String> = text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            // "1. do something" or "- do something" -> "do something"
            val cleaned = line.removePrefix("-").trim()
            val withoutNumber = cleaned.dropWhile { it.isDigit() || it == '.' || it == ')' }.trim()
            if (withoutNumber.isEmpty()) cleaned else withoutNumber
        }
        .filter { it.isNotEmpty() }
        .take(maxSteps)
        .toList()

    private companion object {
        const val SYSTEM_PROMPT =
            "Break the task into at most eight steps. " +
                "Answer with one step per line, each starting with a number. " +
                "No preamble, no closing line."
    }
}
