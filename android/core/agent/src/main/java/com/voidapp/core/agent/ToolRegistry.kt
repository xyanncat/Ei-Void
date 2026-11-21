package com.eivoid.core.agent

import com.eivoid.core.ai.ToolDefinition
import kotlinx.coroutines.CancellationException
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
            // Name the target in the title: "Run delete_file?" gives the user
            // nothing to judge, and confirmation is the last line of defence.
            val args = ToolArguments(arguments)
            val target = args.optionalString("path") ?: args.optionalString("name")
            val title = when {
                tool.name == "delete_file" && target != null -> "Delete $target?"
                target != null -> "Run ${tool.name} on $target?"
                else -> "Run ${tool.name}?"
            }
            val approved = ctx.gate.confirm(
                ConfirmationRequest(
                    title = title,
                    detail = tool.confirmationDetail(arguments, ctx),
                    destructive = tool.destructive,
                ),
            )
            if (!approved) {
                return ToolResult.Error("The user declined ${tool.name}.")
            }
        }

        return try {
            tool.execute(arguments, ctx)
        } catch (cancelled: CancellationException) {
            // Stopping the agent is not a tool failure: let it propagate.
            throw cancelled
        } catch (error: Exception) {
            ToolResult.Error(error.message ?: "${tool.name} failed")
        }
    }
}
