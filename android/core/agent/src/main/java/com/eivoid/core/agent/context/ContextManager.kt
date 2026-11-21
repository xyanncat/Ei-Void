package com.eivoid.core.agent.context

import com.eivoid.core.ai.ChatMessage
import com.eivoid.core.ai.Role
import com.eivoid.core.workspace.Workspace

/**
 * Builds the message list for each model call.
 *
 * The context is bounded by *tokens*, not by turns: the provider reports how
 * many tokens the last request took, and the history is trimmed until the
 * next request fits the model's window.
 */
class ContextManager(
    private val maxTurns: Int = 20,
) {

    /** The prompt that tells the model what it is and what it may do. */
    fun systemPrompt(
        task: String,
        workspace: Workspace,
        plan: List<String>,
    ): String = buildString {
        appendLine("You are Void, a coding agent working inside a cloned Git repository.")
        appendLine("The workspace is ${workspace.path.absolutePath} (${workspace.repo.fullName}).")
        appendLine("All paths are relative to the workspace root.")
        appendLine()
        appendLine("Rules:")
        appendLine("- Read a file before you change it.")
        appendLine("- Prefer small, focused edits over rewriting a file.")
        appendLine("- The user must confirm destructive tools; do not work around them.")
        appendLine("- Never invent file contents, commit hashes or command output.")
        appendLine("- If a tool fails, read the message and try again differently.")

        if (plan.isNotEmpty()) {
            appendLine()
            appendLine("Plan:")
            plan.forEachIndexed { index, step -> appendLine("${index + 1}. $step") }
        }

        appendLine()
        appendLine("Task: $task")
    }

    /**
     * Keeps the system prompt and as many trailing messages as fit in
     * [maxTokens]. A tool result is never dropped without the message that
     * asked for it.
     */
    fun trim(messages: List<ChatMessage>, maxTokens: Int): List<ChatMessage> {
        val system = messages.firstOrNull { it.role == Role.SYSTEM }
        val rest = messages.filter { it.role != Role.SYSTEM }

        if (rest.size <= maxTurns && estimateTokens(rest) <= maxTokens) {
            return listOfNotNull(system) + rest
        }

        val kept = mutableListOf<ChatMessage>()
        var used = 0
        for (message in rest.asReversed()) {
            val cost = estimateTokens(listOf(message))
            if (used + cost > maxTokens && kept.isNotEmpty()) break
            kept.add(0, message)
            used += cost
            if (kept.size >= maxTurns) break
        }

        // A tool message without the call that asked for it is not understood
        // by the model, so the assistant message that made the call is kept.
        if (kept.firstOrNull()?.role == Role.TOOL) {
            val toolIndex = rest.indexOf(kept.first())
            val question = rest.take(toolIndex).lastOrNull { it.role == Role.ASSISTANT }
            if (question != null) kept.add(0, question)
        }

        return listOfNotNull(system) + kept
    }

    /** A rough token count. Good enough to keep a request inside its window. */
    fun estimateTokens(messages: List<ChatMessage>): Int =
        messages.sumOf { estimateTokens(it) }

    private fun estimateTokens(message: ChatMessage): Int {
        val text = buildString {
            append(message.role.name)
            append(' ')
            append(message.content)
            message.toolCalls.forEach { call ->
                append(' ')
                append(call.name)
                append(call.arguments.toString())
            }
        }
        return text.length / CHARS_PER_TOKEN + 1
    }

    private companion object {
        const val CHARS_PER_TOKEN = 4
    }
}
