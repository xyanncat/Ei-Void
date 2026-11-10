package com.voidapp.core.agent

import com.voidapp.core.workspace.Workspace
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A capability the model can invoke.
 *
 * A tool never throws at the model: problems come back as [ToolResult.Error]
 * so the agent can read them and correct itself.
 */
interface Tool {
    val name: String
    val description: String

    /** JSON Schema of the arguments. */
    val parameters: JsonObject

    /** True for tools that destroy something and need the user's yes. */
    val requiresConfirmation: Boolean get() = false

    suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult
}

sealed interface ToolResult {
    /** What the model gets to read. */
    data class Success(val output: String) : ToolResult

    /** What went wrong, in words the model can act on. */
    data class Error(val message: String) : ToolResult
}

/** Everything a tool is allowed to touch. */
data class ToolContext(
    val workspace: Workspace,
    val gate: ConfirmationGate,
    /** The GitHub token, when the tool needs one. Never shown to the model. */
    val token: String? = null,
)

/** Asks the user before a destructive tool runs. */
interface ConfirmationGate {
    suspend fun confirm(request: ConfirmationRequest): Boolean
}

data class ConfirmationRequest(
    val title: String,
    val detail: String,
    val destructive: Boolean = false,
)

/** A gate that approves everything. Used in tests and dry runs. */
class AllowAllGate : ConfirmationGate {
    override suspend fun confirm(request: ConfirmationRequest): Boolean = true
}

/** A gate that refuses everything. */
class DenyAllGate : ConfirmationGate {
    override suspend fun confirm(request: ConfirmationRequest): Boolean = false
}

/** Bad arguments. The message is shown to the model. */
class ToolArgumentException(message: String) : Exception(message)

/** Reads the arguments of a tool call. */
class ToolArguments(private val json: JsonObject) {

    fun requiredString(name: String): String =
        optionalString(name)?.takeIf { it.isNotBlank() }
            ?: throw ToolArgumentException("Missing required argument '$name'")

    fun optionalString(name: String): String? {
        val element = json[name] ?: return null
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content.takeIf { it.isNotBlank() }
    }

    fun optionalInt(name: String): Int? {
        val element = json[name] ?: return null
        val primitive = element as? JsonPrimitive ?: return null
        return runCatching { primitive.content.trim().toInt() }.getOrNull()
    }

    fun optionalBoolean(name: String, default: Boolean = false): Boolean {
        val element = json[name] ?: return default
        val primitive = element as? JsonPrimitive ?: return default
        return runCatching { primitive.content.trim().toBooleanStrict() }.getOrDefault(default)
    }

    fun has(name: String): Boolean = json.containsKey(name)
}
