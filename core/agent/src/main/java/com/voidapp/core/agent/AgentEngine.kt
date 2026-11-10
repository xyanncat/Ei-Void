package com.voidapp.core.agent

import com.voidapp.core.agent.context.ContextManager
import com.voidapp.core.agent.planner.Planner
import com.voidapp.core.ai.AiProviderException
import com.voidapp.core.ai.ChatChunk
import com.voidapp.core.ai.ChatMessage
import com.voidapp.core.ai.ChatRequest
import com.voidapp.core.ai.Role
import com.voidapp.core.ai.ToolCall
import com.voidapp.core.ai.router.Candidate
import com.voidapp.core.ai.router.Route
import com.voidapp.core.ai.router.SmartRouter
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

/** The bounds of one run. */
data class AgentLimits(
    /** How many model round trips one run may take. */
    val maxIterations: Int = 25,
    /** How many tokens the model may produce in one run (approximated). */
    val maxOutputTokens: Int = 32_000,
    /** How long one tool call may take. */
    val toolTimeoutMillis: Long = 60_000,
    /** How long the user has to answer a confirmation. */
    val confirmationTimeoutMillis: Long = 300_000,
)

/**
 * The agent loop: ask the model, run the tools it asks for, feed the results
 * back, repeat until the model answers without tools.
 *
 * The loop is bounded by [limits] and every provider failure is contained:
 * one dead provider does not end the run.
 */
class AgentEngine(
    private val registry: ToolRegistry,
    private val resolver: ProviderResolver,
    private val router: SmartRouter = SmartRouter(),
    private val contextManager: ContextManager = ContextManager(),
    private val planner: Planner = Planner(),
    private val limits: AgentLimits = AgentLimits(),
) {

    fun run(task: String, ctx: ToolContext): Flow<AgentEvent> = flow {
        val targets = resolver.targets()
        if (targets.isEmpty()) {
            emit(AgentEvent.Failed("No AI provider is configured. Add a provider key in Settings."))
            return@flow
        }

        val candidates = targets.map { Candidate(it.provider, it.model) }
        val first = router.pick(task, candidates)
        if (first == null) {
            emit(AgentEvent.Failed("No model is available."))
            return@flow
        }
        var route: Route = first

        val plan = buildPlan(task, route, targets)
        if (plan.isNotEmpty()) {
            emit(AgentEvent.PlanUpdated(plan))
        }
        emit(AgentEvent.Thinking(route.tierName, route.reason))

        val messages = mutableListOf(
            ChatMessage(
                role = Role.SYSTEM,
                content = contextManager.systemPrompt(task, ctx.workspace, plan),
            ),
            ChatMessage(role = Role.USER, content = task),
        )

        val failedProviders = mutableSetOf<String>()
        var iterations = 0
        var outputBudget = limits.maxOutputTokens

        while (iterations < limits.maxIterations) {
            iterations++

            val request = ChatRequest(
                model = route.model.id,
                messages = contextManager.trim(messages),
                tools = registry.definitions(),
                maxTokens = limits.maxOutputTokens,
            )

            val chunks = try {
                val key = resolver.keyFor(route.providerId)
                    ?: throw AiProviderException("No key for ${route.providerId}")
                providerFor(targets, route).chat(request, key)
            } catch (error: Exception) {
                val fallback = router.next(task, candidates, failedProviders + route.providerId)
                if (fallback == null) {
                    emit(
                        AgentEvent.Failed(
                            error.message ?: "The model provider could not be reached.",
                        ),
                    )
                    return@flow
                }
                failedProviders += route.providerId
                route = fallback
                emit(AgentEvent.Thinking(route.tierName, route.reason))
                continue
            }

            var text = ""
            val toolCalls = mutableListOf<ToolCall>()
            var finishReason = "stop"

            try {
                chunks.collect { chunk ->
                    when (chunk) {
                        is ChatChunk.Delta -> {
                            text += chunk.text
                            outputBudget -= chunk.text.length / CHARS_PER_TOKEN
                            emit(AgentEvent.Token(chunk.text))
                        }
                        is ChatChunk.ToolCallDelta -> toolCalls += chunk.call
                        is ChatChunk.Finished -> finishReason = chunk.reason
                    }
                }
            } catch (error: Exception) {
                emit(AgentEvent.Failed(error.message ?: "The model stream failed."))
                return@flow
            }

            if (text.isNotBlank()) {
                messages += ChatMessage(role = Role.ASSISTANT, content = text)
            }

            if (toolCalls.isEmpty()) {
                emit(AgentEvent.Finished(text.ifBlank { "Done." }))
                return@flow
            }

            for (call in toolCalls) {
                emit(AgentEvent.ToolCallStarted(call))
                val result = runTool(call, ctx)
                emit(AgentEvent.ToolCallFinished(call, result))
                messages += ChatMessage(
                    role = Role.TOOL,
                    content = when (result) {
                        is ToolResult.Success -> result.output
                        is ToolResult.Error -> "Error: ${result.message}"
                    },
                    toolCallId = call.id,
                )
            }

            if (outputBudget <= 0) {
                emit(AgentEvent.Failed("The run used up its output budget."))
                return@flow
            }
        }

        emit(AgentEvent.Failed("The agent stopped after ${limits.maxIterations} steps."))
    }

    /**
     * Runs a tool. A tool that needs the user's confirmation gets a longer
     * window, because the timeout covers the wait for the answer.
     */
    private suspend fun runTool(call: ToolCall, ctx: ToolContext): ToolResult = try {
        val needsConfirmation = registry.get(call.name)?.requiresConfirmation == true
        val timeout = if (needsConfirmation) {
            limits.confirmationTimeoutMillis
        } else {
            limits.toolTimeoutMillis
        }
        withTimeout(timeout) {
            registry.execute(call.name, call.arguments, ctx)
        }
    } catch (error: TimeoutCancellationException) {
        ToolResult.Error("${call.name} timed out")
    } catch (error: Exception) {
        ToolResult.Error(error.message ?: "${call.name} failed")
    }

    private suspend fun buildPlan(
        task: String,
        route: Route,
        targets: List<ProviderTarget>,
    ): List<String> {
        if (!router.classify(task).isComplex) return emptyList()

        val key = resolver.keyFor(route.providerId) ?: return emptyList()
        return planner.plan(task, providerFor(targets, route), key, route.model.id)
    }

    private fun providerFor(targets: List<ProviderTarget>, route: Route) =
        targets.first { it.provider.id == route.providerId }.provider

    private companion object {
        /** Rough characters-per-token ratio used for the output budget. */
        const val CHARS_PER_TOKEN = 4
    }
}
