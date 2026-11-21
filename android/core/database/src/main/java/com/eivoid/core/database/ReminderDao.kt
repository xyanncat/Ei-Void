package com.eivoid.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE isActive = 1 ORDER BY dueAt ASC")
    fun observeActive(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY dueAt DESC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE isActive = 1 ORDER BY dueAt ASC")
    suspend fun getActive(): List<ReminderEntity>

    @Query("SELECT * FROM reminders ORDER BY dueAt ASC")
    suspend fun getAll(): List<ReminderEntity>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Query("UPDATE reminders SET leadDays = :leadDays WHERE leadDaysCustomized = 0")
    suspend fun applyDefaultLeadDays(leadDays: Int)

    /**
     * Records that the notification for [dueAt] was actually posted. A denied
     * permission or a muted channel must not write this, or retries stop.
     */
    @Query("UPDATE reminders SET notifiedForDueAt = :dueAt WHERE id = :id")
    suspend fun updateNotified(id: Long, dueAt: Long)
}
