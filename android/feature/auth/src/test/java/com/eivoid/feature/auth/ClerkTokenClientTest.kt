package com.eivoid.feature.auth

import com.eivoid.core.common.auth.AuthConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClerkTokenClientTest {

    @Test
    fun `a successful exchange returns the GitHub token`() = runTest {
        val client = clientReturning(
            HttpStatusCode.OK,
            """{"token":"gho_test","scopes":["repo","read:user"],"expiresAt":123}""",
        )

        val result = client.fetchGithubToken("session")

        val token = result.getOrThrow()
        assertEquals("gho_test", token.token)
        assertEquals(listOf("repo", "read:user"), token.scopes)
        assertEquals(123L, token.expiresAt)
    }

    @Test
    fun `a missing GitHub connection has an actionable message`() = runTest {
        val client = clientReturning(
            HttpStatusCode.NotFound,
            """{"error":"github_not_connected"}""",
        )

        val error = client.fetchGithubToken("session").exceptionOrNull()

        assertTrue(error is ClerkAuthException)
        assertTrue(error?.message.orEmpty().contains("Connect GitHub"))
    }

    @Test
    fun `an expired session is reported`() = runTest {
        val client = clientReturning(
            HttpStatusCode.Unauthorized,
            """{"error":"invalid_session_token"}""",
        )

        val error = client.fetchGithubToken("session").exceptionOrNull()

        assertTrue(error is ClerkAuthException)
        assertTrue(error?.message.orEmpty().contains("expired"))
    }

    private fun clientReturning(status: HttpStatusCode, body: String): ClerkTokenClient {
        val engine = MockEngine { request ->
            assertEquals("Bearer session", request.headers[HttpHeaders.Authorization])
            assertEquals("https://gateway.test/github-token", request.url.toString())
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return ClerkTokenClient(
            client = http,
            configuration = AuthConfiguration(
                clerkPublishableKey = "pk_test_example",
                backendUrl = "https://gateway.test",
            ),
        )
    }
}
