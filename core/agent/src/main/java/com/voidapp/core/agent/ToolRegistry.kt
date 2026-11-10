package com.voidapp.core.agent

import com.voidapp.core.ai.ToolDefinition
import kotlinx.serialization.json.JsonObject

/**
 * The tools one agent run may use, addressed by name.
 *
 * The registry is the only place that knows about confirmations: a tool that
 * asks for confirmation is never run before the user agreed.
 */
class ToolRegistry(tools: List<Tool>) {

    private val tools: Map<String, Tool> = tools.associateBy { it.name }

    val names: List<String> get() = tools.keys.sorted()

    fun get(name: String): Tool? = tools[name]

    /** The definitions handed to the model. */
    fun definitions(): List<ToolDefinition> = tools.values.map { tool ->
        ToolDefinition(
            name = tool.name,
            description = tool.description,
            parameters = tool.parameters,
        )
    }

    /**
     * Runs a tool. Unknown tools, refused confirmations and failures all come
     * back as [ToolResult.Error] so the model can recover.
     */
    suspend fun execute(
        name: String,
        arguments: JsonObject,
        ctx: ToolContext,
    ): ToolResult {
        val tool = tools[name]
            ?: return ToolResult.Error("Unknown tool '$name'. Available: ${names.joinToString()}")

        if (tool.requiresConfirmation) {
            val approved = ctx.gate.confirm(
                ConfirmationRequest(
                    title = "Run ${tool.name}?",
                    detail = tool.description,
                    destructive = true,
                ),
            )
            if (!approved) {
                return ToolResult.Error("The user declined ${tool.name}.")
            }
        }

        return runCatching { tool.execute(arguments, ctx) }
            .getOrElse { error ->
                ToolResult.Error(error.message ?: "${tool.name} failed")
            }
    }
}
