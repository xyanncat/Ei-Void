package com.voidapp.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** One conversation about one repository. */
@Entity(tableName = "chat_sessions", indices = [Index("repoFullName")])
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val repoFullName: String,
    val title: String,
    val createdAt: Long,
)

/** One message of a conversation, including tool traffic. */
@Entity(tableName = "chat_messages", indices = [Index("sessionId")])
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    /** `user`, `assistant` or `tool`. */
    val role: String,
    val content: String,
    val toolName: String?,
    val toolCallId: String?,
    val createdAt: Long,
    /** Keeps the order when many messages share a timestamp. */
    val position: Long,
)

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("SELECT * FROM chat_sessions WHERE repoFullName = :repoFullName ORDER BY createdAt DESC")
    fun observeSessions(repoFullName: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, position ASC")
    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionRow(sessionId: String)

    /** Deleting a conversation deletes its messages. */
    @Transaction
    suspend fun deleteSession(sessionId: String) {
        deleteMessages(sessionId)
        deleteSessionRow(sessionId)
    }
}
