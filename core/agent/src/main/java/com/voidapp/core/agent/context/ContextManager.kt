package com.voidapp.core.agent.context

import com.voidapp.core.ai.ChatMessage
import com.voidapp.core.ai.Role
import com.voidapp.core.workspace.Workspace

/**
 * Builds the message list for each model call.
 *
 * The context is bounded: the system prompt is always kept, the plan is
 * included while it exists, and only the last turns of the conversation are
 * kept.
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
     * Keeps the system prompt and the last [maxTurns] messages. A tool result
     * is never dropped without its question, so an orphaned tool message is
     * kept together with the message that asked for it.
     */
    fun trim(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.size <= maxTurns + 1) return messages

        val system = messages.firstOrNull { it.role == Role.SYSTEM }
        val rest = messages.filter { it.role != Role.SYSTEM }

        val kept = rest.takeLast(maxTurns)
        val firstKept = kept.firstOrNull()
        val needsQuestion = firstKept?.role == Role.TOOL
        val withQuestion = if (needsQuestion) {
            val index = rest.indexOfFirst { it.role != Role.TOOL }
            if (index > 0) listOf(rest[index - 1]) + kept else kept
        } else {
            kept
        }

        return listOfNotNull(system) + withQuestion
    }
}
