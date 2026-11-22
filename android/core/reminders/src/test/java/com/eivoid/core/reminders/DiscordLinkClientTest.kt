package com.eivoid.core.reminders

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordLinkClientTest {

    @Test
    fun `link posts the Discord token with the Clerk bearer`() = runTest {
        var path: String? = null
        var method: HttpMethod? = null
        var authorization: String? = null
        var body: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            method = request.method
            authorization = request.headers[HttpHeaders.Authorization]
            body = (request.body as? TextContent)?.text
            respond(
                content = """{"ok":true,"discord":{"id":"123456789012345678","username":"cat"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = client(engine).link("discord-access-token")

        assertTrue(result.isSuccess)
        assertEquals("cat", result.getOrNull()?.username)
        assertEquals("/api/v1/discord/link", path)
        assertEquals(HttpMethod.Post, method)
        assertEquals("Bearer clerk-token", authorization)
        assertNotNull(body)
        assertEquals(
            "discord-access-token",
            Json.parseToJsonElement(body!!).jsonObject["accessToken"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `a non-member is rejected with the guild message`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"guild_membership_required"}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val failure = client(engine).link("discord-access-token").exceptionOrNull()

        assertTrue(failure is DiscordLinkException)
        assertEquals(403, (failure as DiscordLinkException).statusCode)
        assertTrue(failure.message!!.contains("Join the Ei Void Discord server"))
    }

    @Test
    fun `unlink uses DELETE on the Discord link route`() = runTest {
        var path: String? = null
        var method: HttpMethod? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            method = request.method
            respond("""{"ok":true,"unlinked":true}""", HttpStatusCode.OK)
        }

        val result = client(engine).unlink()

        assertTrue(result.isSuccess)
        assertEquals("/api/v1/discord/link", path)
        assertEquals(HttpMethod.Delete, method)
    }

    @Test
    fun `a missing bot token is explained`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"discord_test_unavailable"}""",
                status = HttpStatusCode.NotImplemented,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val failure = client(engine).test().exceptionOrNull()

        assertTrue(failure is DiscordLinkException)
        assertTrue(failure!!.message!!.contains("bot token"))
    }

    @Test
    fun `account returns the linked Discord identity or null`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"email":"cat@example.com","displayName":"Cat","discord":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = client(engine).account()

        assertTrue(result.isSuccess)
        assertEquals("cat@example.com", result.getOrNull()?.email)
        assertNull(result.getOrNull()?.discord)
    }

    @Test
    fun `a missing base URL fails before any request`() = runTest {
        var called = false
        val engine = MockEngine {
            called = true
            respond("{}", HttpStatusCode.OK)
        }

        val failure = client(engine, baseUrl = "").link("token").exceptionOrNull()

        assertTrue(failure is DiscordLinkException)
        assertTrue(failure!!.message!!.contains("not configured"))
        assertTrue(!called)
    }

    @Test
    fun `a missing Clerk token fails before any request`() = runTest {
        var called = false
        val engine = MockEngine {
            called = true
            respond("{}", HttpStatusCode.OK)
        }

        val failure = client(engine, token = null).link("token").exceptionOrNull()

        assertTrue(failure is DiscordLinkException)
        assertTrue(failure!!.message!!.contains("Sign in with Clerk"))
        assertTrue(!called)
    }

    private fun client(
        engine: MockEngine,
        baseUrl: String = "https://eivoid.example.test",
        token: String? = "clerk-token",
    ): DiscordLinkClient {
        val http = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; explicitNulls = false })
            }
        }
        return DiscordLinkClient(http, baseUrl, tokenProvider = { token })
    }
}
