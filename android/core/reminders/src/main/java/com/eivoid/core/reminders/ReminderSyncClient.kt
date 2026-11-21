package com.eivoid.core.reminders

import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.eivoid.core.database.ReminderEntity
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import java.io.IOException
import java.time.ZoneId

/** The body posted to the reminder backend. Field names match the server's JSON. */
@Serializable
data class ReminderSyncProfile(
    val timezone: String,
    val notifyHour: Int,
    val notifyMinute: Int,
    val name: String,
    val email: String,
    val emailEnabled: Boolean,
)

@Serializable
data class ReminderSyncItem(
    val localId: Long,
    val title: String,
    val merchant: String,
    val bank: String,
    val amountMinor: Long,
    val currency: String,
    val dueAt: Long,
    val notifyAt: Long,
    val repeatRule: String,
    val category: String,
    val notes: String,
    val leadDays: Int,
    val isPaid: Boolean,
    val isActive: Boolean,
    val paidAt: Long? = null,
)

@Serializable
data class ReminderSyncPayload(
    val profile: ReminderSyncProfile,
    val reminders: List<ReminderSyncItem>,
)

/**
 * A sync failure that carries the HTTP status. 401 means the Clerk session
 * expired; 409 means the server copy won and the client should refresh.
 */
class ReminderSyncException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    val isUnauthorized: Boolean get() = statusCode == 401
    val isConflict: Boolean get() = statusCode == 409
}

/**
 * Sends the local reminder snapshot to the hosted backend with the Clerk
 * session token. The backend verifies that token, so one device cannot write
 * another user's reminders.
 *
 * The class is split from [ReminderSyncTransport] so the HTTP contract can be
 * tested with a MockEngine without a DataStore, Room, or a signed-in Clerk.
 */
class ReminderSyncClient(
    private val transport: ReminderSyncTransport,
    private val settingsProvider: suspend () -> ReminderSettings,
    private val remindersProvider: suspend () -> List<ReminderEntity>,
    private val notifyAt: (ReminderEntity, ReminderSettings) -> Long,
    private val tokenProvider: suspend () -> String? = { clerkToken() },
    private val profileProvider: suspend (ReminderSettings) -> ReminderSyncProfile =
        { settings -> profileFromClerk(settings) },
) {

    private val mutex = Mutex()

    /**
     * Uploads the current snapshot. Returns a failure instead of throwing, so
     * background callers can ignore it and the UI can show a retry.
     */
    suspend fun syncNow(): Result<Unit> {
        mutex.lock()
        return try {
            val settings = settingsProvider()
            val payload = buildPayload(
                settings = settings,
                reminders = remindersProvider(),
                notifyAt = notifyAt,
                profile = profileProvider(settings),
            )
            transport.post(payload, tokenProvider())
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        /** The backend route; the base URL is `EI_VOID_API_URL`. */
        const val SYNC_PATH = "/api/v1/reminders/sync"

        fun buildPayload(
            settings: ReminderSettings,
            reminders: List<ReminderEntity>,
            notifyAt: (ReminderEntity, ReminderSettings) -> Long,
            profile: ReminderSyncProfile,
            zone: ZoneId = ZoneId.systemDefault(),
        ): ReminderSyncPayload = ReminderSyncPayload(
            profile = profile.copy(timezone = zone.id),
            reminders = reminders.map { reminder ->
                ReminderSyncItem(
                    localId = reminder.id,
                    title = reminder.title,
                    merchant = reminder.merchant,
                    bank = reminder.bank,
                    amountMinor = reminder.amountMinor,
                    currency = reminder.currency,
                    dueAt = reminder.dueAt,
                    notifyAt = notifyAt(reminder, settings),
                    repeatRule = reminder.repeatRule.name,
                    category = reminder.category.name,
                    notes = reminder.notes,
                    leadDays = reminder.leadDays,
                    isPaid = reminder.isPaid,
                    isActive = reminder.isActive,
                    paidAt = reminder.paidAt,
                )
            },
        )

        suspend fun clerkToken(): String? {
            val result = Clerk.auth.getToken()
            return (result as? ClerkResult.Success)?.value
        }

        fun profileFromClerk(
            settings: ReminderSettings,
            zone: ZoneId = ZoneId.systemDefault(),
        ): ReminderSyncProfile {
            val user = Clerk.user
            val email = user?.primaryEmailAddress?.emailAddress.orEmpty()
            return ReminderSyncProfile(
                timezone = zone.id,
                notifyHour = settings.notifyHour,
                notifyMinute = settings.notifyMinute,
                name = clerkDisplayName(
                    user?.firstName,
                    user?.lastName,
                    user?.username,
                    email,
                ),
                email = email,
                emailEnabled = settings.emailRemindersEnabled,
            )
        }

        private fun clerkDisplayName(
            firstName: String?,
            lastName: String?,
            username: String?,
            email: String?,
        ): String {
            val fullName = listOfNotNull(
                firstName?.trim()?.takeIf { it.isNotBlank() },
                lastName?.trim()?.takeIf { it.isNotBlank() },
            ).joinToString(" ")
            if (fullName.isNotBlank()) return fullName
            return username?.trim()?.takeIf { it.isNotBlank() }
                ?: email?.trim()?.takeIf { it.isNotBlank() }
                ?: "Ei Void user"
        }
    }
}

/**
 * The HTTP half: POST the payload, attach the bearer token, and turn status
 * codes into a [ReminderSyncException] with a user-facing message.
 */
class ReminderSyncTransport(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {

    fun endpoint(): String = baseUrl.trimEnd('/') + ReminderSyncClient.SYNC_PATH

    suspend fun post(payload: ReminderSyncPayload, token: String?): Result<Unit> {
        if (baseUrl.isBlank()) {
            return Result.failure(
                ReminderSyncException(0, "EI_VOID_API_URL is not configured."),
            )
        }
        if (token.isNullOrBlank()) {
            return Result.failure(
                ReminderSyncException(401, "Sign in with Clerk first."),
            )
        }

        return try {
            val response = httpClient.post(endpoint()) {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            val status = response.status.value
            if (status in 200..299) {
                Result.success(Unit)
            } else {
                val detail = runCatching { response.bodyAsText() }.getOrDefault("")
                Result.failure(
                    ReminderSyncException(status, messageFor(status, detail)),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun messageFor(status: Int, body: String): String = when (status) {
        401 -> "Your Clerk session expired. Sign in again."
        409 -> "These reminders changed on another device. Refresh and try again."
        else -> {
            val suffix = if (body.isBlank()) "" else ": ${body.take(180)}"
            "Reminder sync failed (HTTP $status)$suffix"
        }
    }
}
