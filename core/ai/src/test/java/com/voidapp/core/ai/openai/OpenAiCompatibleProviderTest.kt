package com.voidapp.core.ai.openai

import com.voidapp.core.ai.AiProviderException
import com.voidapp.core.ai.ChatChunk
import com.voidapp.core.ai.ChatMessage
import com.voidapp.core.ai.ChatRequest
import com.voidapp.core.ai.Role
import com.voidapp.core.ai.ToolCall
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleProviderTest {

    private val config = OpenAiConfig(
        providerId = "test",
        displayName = "Test Provider",
        baseUrl = "https://api.test/v1",
    )

    private fun provider(engine: MockEngine) = OpenAiCompatibleProvider(
        HttpClient(engine) { expectSuccess = false },
        config,
    )

    private fun request() = ChatRequest(
        model = "test-model",
        messages = listOf(ChatMessage(Role.USER, "hello")),
    )

    @Test
    fun `a plain reply becomes a delta and a finish chunk`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"choices":[{"message":{"content":"Hello"},"finish_reason":"stop"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val chunks = provider(engine).chat(request(), "key").toList()

        assertEquals(
            listOf(ChatChunk.Delta("Hello"), ChatChunk.Finished("stop")),
            chunks,
        )
    }

    @Test
    fun `a streamed reply becomes deltas in order`() = runTest {
        val stream = buildString {
            appendLine("""data: {"choices":[{"delta":{"content":"He"}}]}""")
            appendLine()
            appendLine("""data: {"choices":[{"delta":{"content":"llo"}}]}""")
            appendLine()
            appendLine("""data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""")
            appendLine()
            appendLine("data: [DONE]")
        }
        val engine = MockEngine {
            respond(
                content = stream,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
            )
        }

        val chunks = provider(engine).chat(request(), "key").toList()

        assertEquals(
            listOf(
                ChatChunk.Delta("He"),
                ChatChunk.Delta("llo"),
                ChatChunk.Finished("stop"),
            ),
            chunks,
        )
    }

    @Test
    fun `tool call fragments are accumulated into one call`() = runTest {
        val stream = buildString {
            appendLine(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"write_file","arguments":"{\"path\":"}}]}}]}""",
            )
            appendLine()
            appendLine(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"a.txt\"}"}}]}}]}""",
            )
            appendLine()
            appendLine("data: [DONE]")
        }
        val engine = MockEngine {
            respond(
                content = stream,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
            )
        }

        val chunks = provider(engine).chat(request(), "key").toList()

        val call = (chunks.first { it is ChatChunk.ToolCallDelta } as ChatChunk.ToolCallDelta).call
        assertEquals("call_1", call.id)
        assertEquals("write_file", call.name)
        assertEquals("a.txt", call.arguments["path"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the key travels in the authorization header and never in the body`() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                content = """{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        provider(engine).chat(request(), "secret-key").toList()

        assertEquals("Bearer secret-key", captured?.headers?.get(HttpHeaders.Authorization))
        val body = (captured?.body as? TextContent)?.text.orEmpty()
        assertTrue(body.contains("\"model\":\"test-model\""))
        assertFalse(body.contains("secret-key"))
    }

    @Test
    fun `an error status fails without leaking the key`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"invalid key"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val error = runCatching { provider(engine).chat(request(), "secret-key").toList() }
            .exceptionOrNull()

        assertTrue(error is AiProviderException)
        assertFalse(error?.message.orEmpty().contains("secret-key"))
    }

    @Test
    fun `listModels marks free models`() = runTest {
        val freeConfig = config.copy(freeModelSuffix = ":free")
        val engine = MockEngine {
            respond(
                content = """{"data":[{"id":"a/free:free"},{"id":"b/paid"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val provider = OpenAiCompatibleProvider(HttpClient(engine) { expectSuccess = false }, freeConfig)

        val models = provider.listModels("key")

        assertEquals(2, models.size)
        assertTrue(models.first { it.id == "a/free:free" }.isFree)
        assertFalse(models.first { it.id == "b/paid" }.isFree)
    }

    @Test
    fun `tools are sent as function definitions`() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                content = """{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val toolRequest = request().copy(
            tools = listOf(
                com.voidapp.core.ai.ToolDefinition(
                    name = "write_file",
                    description = "writes a file",
                    parameters = kotlinx.serialization.json.buildJsonObject { },
                ),
            ),
        )

        provider(engine).chat(toolRequest, "key").toList()

        val body = (captured?.body as? TextContent)?.text.orEmpty()
        assertTrue(body.contains("\"tools\""))
        assertTrue(body.contains("write_file"))
    }

    @Test
    fun `a tool call in a plain reply is parsed`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {"choices":[{"message":{"content":"","tool_calls":[{"id":"call_9","type":"function",
                    "function":{"name":"list_files","arguments":"{\"path\":\".\"}"}}]},
                    "finish_reason":"tool_calls"}]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val chunks = provider(engine).chat(request(), "key").toList()

        val call = (chunks.first { it is ChatChunk.ToolCallDelta } as ChatChunk.ToolCallDelta).call
        assertEquals(ToolCall("call_9", "list_files", call.arguments).id, call.id)
        assertEquals("list_files", call.name)
        assertEquals(".", call.arguments["path"]?.jsonPrimitive?.content)
        assertEquals(ChatChunk.Finished("tool_calls"), chunks.last())
    }
}
