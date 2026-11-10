package com.voidapp.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * The three tiers Void exposes. The provider's model name is an
 * implementation detail: the app, the chat and the settings only ever show
 * the tier.
 *
 * [rank] orders quality: the lower the number, the stronger the tier.
 */
enum class Tier(val displayName: String, val rank: Int) {
    RED_DWARF("Red Dwarfs", 0),
    WHITE_DWARF("White Dwarfs", 1),
    NEUTRON_STAR("Neutron Stars", 2),
    ;

    /** True when this tier is at least as strong as [other]. */
    fun isAtLeast(other: Tier): Boolean = rank <= other.rank
}

enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

data class Model(
    /** The provider's model id. Never shown to the user. */
    val id: String,
    val providerId: String,
    /** What the user sees: the tier name. */
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
