package com.eivoid.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(repos: List<RepoEntity>)

    @Query("SELECT * FROM repos ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RepoEntity>>

    @Query(
        """
        SELECT * FROM repos
        WHERE name LIKE '%' || :query || '%'
           OR fullName LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """,
    )
    fun search(query: String): Flow<List<RepoEntity>>

    @Query("DELETE FROM repos")
    suspend fun deleteAll()

    /** Removes cached repositories that the account no longer has. */
    @Query("DELETE FROM repos WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    /** The repositories the user added as projects. */
    @Query("SELECT * FROM repos WHERE isLinked = 1 ORDER BY updatedAt DESC")
    fun observeLinked(): Flow<List<RepoEntity>>

    @Query("SELECT COUNT(*) FROM repos WHERE isLinked = 1")
    suspend fun linkedCount(): Int

    /** Ids of the projects the user linked, read before a cache refresh. */
    @Query("SELECT id FROM repos WHERE isLinked = 1")
    suspend fun linkedIds(): List<String>

    @Query("UPDATE repos SET isLinked = :linked WHERE id = :id")
    suspend fun setLinked(id: String, linked: Boolean)
}
