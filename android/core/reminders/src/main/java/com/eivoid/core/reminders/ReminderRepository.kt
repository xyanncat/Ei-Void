package com.eivoid.core.reminders

import com.eivoid.core.database.ReminderDao
import com.eivoid.core.database.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The reminder feature's contract. The local Room database is always the
 * source of truth: a write is committed and alarms are rescheduled before any
 * network work starts, and backend sync never blocks the caller.
 */
interface ReminderRepository {

    fun observeActive(): Flow<List<ReminderEntity>>

    fun observeAll(): Flow<List<ReminderEntity>>

    suspend fun get(id: Long): ReminderEntity?

    /** Inserts a new reminder (id 0) or updates one, then reschedules it. */
    suspend fun save(reminder: ReminderEntity): Long

    /** Marks paid, cancels its alarm, and creates the next occurrence when repeating. */
    suspend fun markPaid(id: Long)

    /** Moves the due date forward; [days] is clamped to at least one. */
    suspend fun snooze(id: Long, days: Int = 1)

    suspend fun delete(id: Long)

    suspend fun deleteAll()

    /** Re-registers every active reminder after a boot or a settings change. */
    suspend fun rescheduleAll()

    /** Applies a new default to reminders the user has not customised. */
    suspend fun applyDefaultLeadDays(days: Int)

    /** Records delivery; callers pass this only when a notification posted. */
    suspend fun markNotified(id: Long, dueAt: Long)

    /** Explicit, awaited upload. Returns a failure instead of throwing. */
    suspend fun syncNow(): Result<Unit>
}

/**
 * Room-backed implementation. [syncQuietly] runs uploads on a private IO
 * scope, so Save and notification actions finish at database speed even when
 * the backend is unreachable.
 */
class DefaultReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val scheduler: AlarmScheduler,
    private val settingsStore: ReminderSettingsStore,
    private val syncClient: ReminderSyncClient,
) : ReminderRepository {

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeActive(): Flow<List<ReminderEntity>> = dao.observeActive()

    override fun observeAll(): Flow<List<ReminderEntity>> = dao.observeAll()

    override suspend fun get(id: Long): ReminderEntity? = dao.get(id)

    override suspend fun save(reminder: ReminderEntity): Long {
        val settings = settingsStore.current()
        val id = if (reminder.id == 0L) {
            dao.insert(reminder)
        } else {
            dao.update(reminder)
            reminder.id
        }
        scheduler.scheduleReminder(reminder.copy(id = id), settings)
        syncQuietly()
        return id
    }

    override suspend fun markPaid(id: Long) {
        val reminder = dao.get(id) ?: return
        if (reminder.isPaid) return

        val now = System.currentTimeMillis()
        dao.update(
            reminder.copy(
                isPaid = true,
                paidAt = now,
                isActive = false,
                notifiedForDueAt = 0L,
            ),
        )
        scheduler.cancel(id)

        if (reminder.repeatRule == RepeatRule.NONE) {
            syncQuietly()
            return
        }

        val nextDueAt = reminder.repeatRule.advanceUntilFuture(reminder.dueAt, now)
        if (nextDueAt == null) {
            syncQuietly()
            return
        }
        val next = reminder.copy(
            id = 0L,
            dueAt = nextDueAt,
            isPaid = false,
            isActive = true,
            paidAt = null,
            createdAt = now,
            notifiedForDueAt = 0L,
        )
        val nextId = dao.insert(next)
        scheduler.scheduleReminder(next.copy(id = nextId), settingsStore.current())
        syncQuietly()
    }

    override suspend fun snooze(id: Long, days: Int) {
        val reminder = dao.get(id) ?: return
        if (reminder.isPaid) return
        val updated = reminder.copy(
            dueAt = reminder.dueAt + days.coerceAtLeast(1) * DAY_MILLIS,
            notifiedForDueAt = 0L,
        )
        dao.update(updated)
        scheduler.scheduleReminder(updated, settingsStore.current())
        syncQuietly()
    }

    override suspend fun delete(id: Long) {
        scheduler.cancel(id)
        dao.deleteById(id)
        syncQuietly()
    }

    override suspend fun deleteAll() {
        dao.getActive().forEach { scheduler.cancel(it.id) }
        dao.deleteAll()
        syncQuietly()
    }

    override suspend fun rescheduleAll() {
        val settings = settingsStore.current()
        dao.getActive().forEach { scheduler.scheduleReminder(it, settings) }
        syncQuietly()
    }

    override suspend fun applyDefaultLeadDays(days: Int) {
        dao.applyDefaultLeadDays(days)
        rescheduleAll()
    }

    override suspend fun markNotified(id: Long, dueAt: Long) {
        dao.updateNotified(id, dueAt)
    }

    override suspend fun syncNow(): Result<Unit> = syncClient.syncNow()

    /** Fire-and-forget upload: the database write already succeeded. */
    private fun syncQuietly() {
        syncScope.launch { runCatching { syncClient.syncNow() } }
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
