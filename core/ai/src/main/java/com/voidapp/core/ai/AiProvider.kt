package com.voidapp.core.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.Flow

/** How heavy a model is. The router picks a tier from the task. */
enum class Tier { FAST, STANDARD, STRONG }

enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

data class Model(
    val id: String,
    val providerId: String,
    val displayName: String,
    val tier: Tier,
    val contextWindow: Int,
    val supportsTools: Boolean = true,
    val isFree: Boolean = false,
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
)

data class ToolDefinition(
    val name: String,
    val description: String,
    /** JSON Schema of the tool's arguments. */
    val parameters: JsonObject,
)

data class ChatMessage(
    val role: Role,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    /** Set on the message that answers a tool call. */
    val toolCallId: String? = null,
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolDefinition> = emptyList(),
    val temperature: Double = 0.2,
    val maxTokens: Int = 4096,
)

sealed interface ChatChunk {
    /** A piece of assistant text. */
    data class Delta(val text: String) : ChatChunk

    /** A complete tool call. */
    data class ToolCallDelta(val call: ToolCall) : ChatChunk

    /** The provider stopped; [reason] is its finish reason. */
    data class Finished(val reason: String) : ChatChunk
}

/**
 * A chat provider. The API key is passed in per call: the provider never
 * reads storage and never keeps the key.
 */
interface AiProvider {
    val id: String
    val displayName: String

    /** True when the key works. Never throws: a bad key is `false`. */
    suspend fun validate(apiKey: String): Boolean

    /** The models the provider offers, as far as it reports them. */
    suspend fun listModels(apiKey: String): List<Model>

    /** Streams the answer. Errors are thrown as [AiProviderException]. */
    suspend fun chat(request: ChatRequest, apiKey: String): Flow<ChatChunk>
}

/** A provider request that failed. The message never contains the key. */
class AiProviderException(message: String) : Exception(message)
