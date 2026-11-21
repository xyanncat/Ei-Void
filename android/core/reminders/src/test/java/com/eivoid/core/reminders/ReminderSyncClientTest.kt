package com.eivoid.core.reminders

import com.eivoid.core.database.Category
import com.eivoid.core.database.ReminderEntity
import com.eivoid.core.database.RepeatRule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSyncClientTest {

    @Test
    fun `sync posts the profile and reminders to the backend route`() = runTest {
        var path: String? = null
        var authorization: String? = null
        var body: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            authorization = request.headers[HttpHeaders.Authorization]
            body = (request.body as TextContent).text
            respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = client(engine).syncNow()

        assertTrue(result.isSuccess)
        assertEquals("/api/v1/reminders/sync", path)
        assertEquals("Bearer clerk-token", authorization)

        assertNotNull(body)
        val payload = Json.parseToJsonElement(body!!).jsonObject
        val profile = payload["profile"]!!.jsonObject
        assertEquals("Test User", profile["name"]!!.jsonPrimitive.content)
        assertEquals("test@example.com", profile["email"]!!.jsonPrimitive.content)
        assertEquals("9", profile["notifyHour"]!!.jsonPrimitive.content)

        val items = payload["reminders"]!!.jsonArray
        assertEquals(1, items.size)
        val item = items[0].jsonObject
        assertEquals("Rent", item["title"]!!.jsonPrimitive.content)
        assertEquals("MONTHLY", item["repeatRule"]!!.jsonPrimitive.content)
        assertEquals("RENT", item["category"]!!.jsonPrimitive.content)
        assertEquals(1_234L, item["notifyAt"]!!.jsonPrimitive.long)
    }

    @Test
    fun `a 401 is mapped to an unauthorized sync failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"invalid_clerk_token"}""",
                status = HttpStatusCode.Unauthorized,
            )
        }

        val result = client(engine).syncNow()

        val failure = result.exceptionOrNull()
        assertTrue(failure is ReminderSyncException)
        failure as ReminderSyncException
        assertEquals(401, failure.statusCode)
        assertTrue(failure.isUnauthorized)
        assertEquals("Your Clerk session expired. Sign in again.", failure.message)
    }

    @Test
    fun `a 409 is mapped to a conflict failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"conflict"}""",
                status = HttpStatusCode.Conflict,
            )
        }

        val result = client(engine).syncNow()

        val failure = result.exceptionOrNull()
        assertTrue(failure is ReminderSyncException)
        failure as ReminderSyncException
        assertEquals(409, failure.statusCode)
        assertTrue(failure.isConflict)
    }

    @Test
    fun `a missing base url fails without a request`() = runTest {
        val engine = MockEngine { error("the transport must not call the network") }

        val result = client(engine, baseUrl = "").syncNow()

        val failure = result.exceptionOrNull()
        assertTrue(failure is ReminderSyncException)
        assertEquals(0, (failure as ReminderSyncException).statusCode)
    }

    @Test
    fun `a missing clerk token fails as unauthorized`() = runTest {
        val engine = MockEngine { error("the transport must not call the network") }

        val result = client(engine, token = null).syncNow()

        val failure = result.exceptionOrNull()
        assertTrue(failure is ReminderSyncException)
        assertEquals(401, (failure as ReminderSyncException).statusCode)
    }

    private fun client(
        engine: MockEngine,
        baseUrl: String = "https://api.example.test",
        token: String? = "clerk-token",
    ): ReminderSyncClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return ReminderSyncClient(
            transport = ReminderSyncTransport(httpClient, baseUrl),
            settingsProvider = { ReminderSettings() },
            remindersProvider = { listOf(reminder()) },
            notifyAt = { _, _ -> 1_234L },
            tokenProvider = { token },
            profileProvider = { settings ->
                ReminderSyncProfile(
                    timezone = "UTC",
                    notifyHour = settings.notifyHour,
                    notifyMinute = settings.notifyMinute,
                    name = "Test User",
                    email = "test@example.com",
                    emailEnabled = settings.emailRemindersEnabled,
                )
            },
        )
    }

    private fun reminder() = ReminderEntity(
        id = 7L,
        title = "Rent",
        merchant = "Landlord",
        bank = "HDFC",
        amountMinor = 50_000L,
        currency = "INR",
        dueAt = 1_800_000_000_000L,
        repeatRule = RepeatRule.MONTHLY,
        category = Category.RENT,
        notes = "Pay by UPI",
        leadDays = 4,
    )
}
