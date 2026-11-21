package com.eivoid.feature.reminders

import com.eivoid.core.database.ReminderEntity
import com.eivoid.core.reminders.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ReminderRepository] used by the ViewModel tests. */
class FakeReminderRepository : ReminderRepository {

    val all = MutableStateFlow<List<ReminderEntity>>(emptyList())
    var rescheduleCount = 0
        private set
    var lastDefaultLeadDays: Int? = null
        private set
    var syncResult: Result<Unit> = Result.success(Unit)
    private var nextId = 1L

    val paidIds = mutableListOf<Long>()

    fun seed(vararg reminders: ReminderEntity) {
        all.value = reminders.toList()
        nextId = (reminders.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    override fun observeActive(): Flow<List<ReminderEntity>> =
        all.map { list -> list.filter { it.isActive && !it.isPaid } }

    override fun observeAll(): Flow<List<ReminderEntity>> = all

    override suspend fun get(id: Long): ReminderEntity? =
        all.value.firstOrNull { it.id == id }

    override suspend fun save(reminder: ReminderEntity): Long {
        val id = if (reminder.id == 0L) nextId++ else reminder.id
        upsert(reminder.copy(id = id))
        return id
    }

    override suspend fun markPaid(id: Long) {
        paidIds += id
        val reminder = get(id) ?: return
        upsert(reminder.copy(isPaid = true, isActive = false, paidAt = 1L))
    }

    override suspend fun snooze(id: Long, days: Int) {
        val reminder = get(id) ?: return
        upsert(
            reminder.copy(
                dueAt = reminder.dueAt + days.coerceAtLeast(1) * DAY,
                notifiedForDueAt = 0L,
            ),
        )
    }

    override suspend fun delete(id: Long) {
        all.value = all.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        all.value = emptyList()
    }

    override suspend fun rescheduleAll() {
        rescheduleCount++
    }

    override suspend fun applyDefaultLeadDays(days: Int) {
        lastDefaultLeadDays = days
    }

    override suspend fun markNotified(id: Long, dueAt: Long) {
        val reminder = get(id) ?: return
        upsert(reminder.copy(notifiedForDueAt = dueAt))
    }

    override suspend fun syncNow(): Result<Unit> = syncResult

    private fun upsert(reminder: ReminderEntity) {
        val merged = all.value.associateBy { it.id }.toMutableMap()
        merged[reminder.id] = reminder
        all.value = merged.values.sortedBy { it.id }
    }

    companion object {
        const val DAY = 86_400_000L
    }
}

class FakePermissionSource(var value: ReminderPermissions = ReminderPermissions()) :
    ReminderPermissionSource {
    override fun snapshot(): ReminderPermissions = value
}
