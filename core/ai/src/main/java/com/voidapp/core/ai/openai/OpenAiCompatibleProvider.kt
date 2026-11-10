package com.voidapp.core.ai.openai

import com.voidapp.core.ai.AiProvider
import com.voidapp.core.ai.AiProviderException
import com.voidapp.core.ai.ChatChunk
import com.voidapp.core.ai.ChatRequest
import com.voidapp.core.ai.Model
import com.voidapp.core.ai.models.ModelCatalog
import com.voidapp.core.ai.Role
import com.voidapp.core.ai.ToolCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The one adapter every provider uses: OpenRouter, NVIDIA NIM, Kira AI and
 * Nara Router all speak the OpenAI chat-completions dialect.
 *
 * The key is passed in per call and is only ever written into the
 * `Authorization` header.
 */
class OpenAiCompatibleProvider(
    private val client: HttpClient,
    private val config: OpenAiConfig,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AiProvider {

    override val id: String get() = config.providerId
    override val displayName: String get() = config.displayName

    override suspend fun validate(apiKey: String): Boolean = runCatching {
        listModels(apiKey)
        true
    }.getOrElse { false }

    override suspend fun listModels(apiKey: String): List<Model> {
        val response = client.get(config.modelsUrl) {
            applyAuth(apiKey)
        }

        if (!response.status.isSuccess()) {
            throw AiProviderException(
                "${config.displayName} returned ${response.status.value} for the model list",
            )
        }

        val root = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }
            .getOrElse { return emptyList() }

        val entries = root["data"]?.jsonArray ?: return emptyList()

        return entries.mapNotNull { element ->
            val modelId = element.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ModelCatalog.infer(modelId, config.providerId, config.freeModelSuffix)
                .copy(supportsTools = true)
        }
    }

    override suspend fun chat(request: ChatRequest, apiKey: String): Flow<ChatChunk> = flow {
        val response = client.post(config.chatCompletionsUrl) {
            applyAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(buildRequestBody(request).toString())
        }

        if (!response.status.isSuccess()) {
            val detail = runCatching { response.bodyAsText() }.getOrDefault("").take(300)
            throw AiProviderException(
                "${config.displayName} returned ${response.status.value}: $detail",
            )
        }

        val contentType = response.contentType()?.withoutParameters()
        if (contentType == ContentType.Text.EventStream) {
            emitAll(parseEventStream(response))
        } else {
            emitAll(parseSingleResponse(response.bodyAsText()))
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuth(apiKey: String) {
        config.extraHeaders.forEach { (name, value) -> header(name, value) }
        if (apiKey.isNotBlank()) {
            header(config.authHeader, "${config.authScheme} $apiKey")
        }
    }

    private fun buildRequestBody(request: ChatRequest): JsonObject = buildJsonObject {
        put("model", request.model)
        put("stream", true)
        put("temperature", request.temperature)
        put("max_tokens", request.maxTokens)

        put(
            "messages",
            buildJsonArray {
                request.messages.forEach { message ->
                    add(
                        buildJsonObject {
                            put("role", message.role.toWireName())
                            put("content", message.content)
                            message.toolCallId?.let { put("tool_call_id", it) }
                            if (message.toolCalls.isNotEmpty()) {
                                put(
                                    "tool_calls",
                                    buildJsonArray {
                                        message.toolCalls.forEach { call ->
                                            add(
                                                buildJsonObject {
                                                    put("id", call.id)
                                                    put("type", "function")
                                                    put(
                                                        "function",
                                                        buildJsonObject {
                                                            put("name", call.name)
                                                            put("arguments", call.arguments.toString())
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
            },
        )

        if (request.tools.isNotEmpty()) {
            put(
                "tools",
                buildJsonArray {
                    request.tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("type", "function")
                                put(
                                    "function",
                                    buildJsonObject {
                                        put("name", tool.name)
                                        put("description", tool.description)
                                        put("parameters", tool.parameters)
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
    }

    /** Parses an OpenAI server-sent event stream. */
    private suspend fun parseEventStream(response: io.ktor.client.statement.HttpResponse): Flow<ChatChunk> = flow {
        val channel = response.bodyAsChannel()
        val toolCalls = linkedMapOf<Int, ToolCallBuilder>()
        var finishReason: String? = null

        while (true) {
            val line = try {
                channel.readUTF8Line()
            } catch (error: Throwable) {
                null
            } ?: break

            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]") break
            if (data.isEmpty()) continue

            val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue

            choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { finishReason = it }

            val delta = choice["delta"]?.jsonObject ?: continue

            delta["content"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotEmpty() }
                ?.let { emit(ChatChunk.Delta(it)) }

            delta["tool_calls"]?.jsonArray?.forEach { element ->
                val call = element.jsonObject
                val index = call["index"]?.jsonPrimitive?.intOrNull ?: 0
                val builder = toolCalls.getOrPut(index) { ToolCallBuilder(index, json) }
                call["id"]?.jsonPrimitive?.contentOrNull?.let { builder.id = it }
                call["function"]?.jsonObject?.let { function ->
                    function["name"]?.jsonPrimitive?.contentOrNull?.let { builder.name = it }
                    function["arguments"]?.jsonPrimitive?.contentOrNull?.let { builder.arguments.append(it) }
                }
            }
        }

        toolCalls.values.sortedBy { it.index }.forEach { builder ->
            emit(ChatChunk.ToolCallDelta(builder.build()))
        }
        emit(ChatChunk.Finished(finishReason ?: "stop"))
    }

    /** Parses a single (non-streamed) chat completion. */
    private fun parseSingleResponse(body: String): Flow<ChatChunk> = flow {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@flow
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@flow

        val message = choice["message"]?.jsonObject
        message?.get("content")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotEmpty() }
            ?.let { emit(ChatChunk.Delta(it)) }

        message?.get("tool_calls")?.jsonArray?.forEach { element ->
            val call = element.jsonObject
            val id = call["id"]?.jsonPrimitive?.contentOrNull ?: "call"
            val function = call["function"]?.jsonObject
            val name = function?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
            val rawArguments = function?.get("arguments")?.jsonPrimitive?.contentOrNull ?: "{}"
            emit(
                ChatChunk.ToolCallDelta(
                    ToolCall(
                        id = id,
                        name = name,
                        arguments = parseArguments(rawArguments),
                    ),
                ),
            )
        }

        emit(ChatChunk.Finished(choice["finish_reason"]?.jsonPrimitive?.contentOrNull ?: "stop"))
    }

    private fun parseArguments(raw: String): JsonObject = parseArguments(json, raw)

    private class ToolCallBuilder(val index: Int, private val json: Json) {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()

        fun build(): ToolCall = ToolCall(
            id = id.ifEmpty { "call_$index" },
            name = name,
            arguments = parseArguments(json, arguments.toString()),
        )
    }
}

private fun Role.toWireName(): String = when (this) {
    Role.SYSTEM -> "system"
    Role.USER -> "user"
    Role.ASSISTANT -> "assistant"
    Role.TOOL -> "tool"
}

private fun parseArguments(json: Json, raw: String): JsonObject = runCatching {
    json.parseToJsonElement(raw).jsonObject
}.getOrElse { JsonObject(emptyMap()) }
