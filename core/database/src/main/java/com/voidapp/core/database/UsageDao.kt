package com.voidapp.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One token charge against one tier. Rows are kept for the current billing
 * period only; older rows are pruned when the month turns over.
 */
@Entity(tableName = "usage_entries", indices = [Index("createdAt"), Index("tier")])
data class UsageEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tier: String,
    val tokens: Int,
    val createdAt: Long,
)

@Dao
interface UsageDao {

    @Insert
    suspend fun insert(entry: UsageEntryEntity)

    @Query(
        "SELECT COALESCE(SUM(tokens), 0) FROM usage_entries " +
            "WHERE tier = :tier AND createdAt >= :since",
    )
    suspend fun tokensSince(tier: String, since: Long): Long

    @Query(
        "SELECT tier, COALESCE(SUM(tokens), 0) AS tokens FROM usage_entries " +
            "WHERE createdAt >= :since GROUP BY tier",
    )
    fun totalsSince(since: Long): Flow<List<TierUsage>>

    @Query("DELETE FROM usage_entries WHERE createdAt < :before")
    suspend fun deleteBefore(before: Long)
}

/** A tier and how many tokens it has used in the queried window. */
data class TierUsage(
    val tier: String,
    val tokens: Long,
)
