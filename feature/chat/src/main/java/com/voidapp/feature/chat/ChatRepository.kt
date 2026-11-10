package com.voidapp.feature.chat

import com.voidapp.core.database.ChatDao
import com.voidapp.core.database.ChatMessageEntity
import com.voidapp.core.database.ChatSessionEntity
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ChatSession(
    val id: String,
    val repoFullName: String,
    val title: String,
    val createdAt: Long,
)

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val toolName: String?,
    val toolCallId: String?,
    val createdAt: Long,
)

/**
 * Conversations are stored so they survive the process: the agent context is
 * rebuilt from them, and the user sees what happened after a restart.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val dao: ChatDao,
) {

    fun observeSessions(repoFullName: String): Flow<List<ChatSession>> =
        dao.observeSessions(repoFullName).map { sessions -> sessions.map { it.toSession() } }

    fun observeMessages(sessionId: String): Flow<List<ChatMessage>> =
        dao.observeMessages(sessionId).map { messages -> messages.map { it.toMessage() } }

    suspend fun createSession(repoFullName: String, title: String): String {
        val id = UUID.randomUUID().toString()
        dao.insertSession(
            ChatSessionEntity(
                id = id,
                repoFullName = repoFullName,
                title = title.take(80),
                createdAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        toolName: String? = null,
        toolCallId: String? = null,
    ) {
        val now = System.currentTimeMillis()
        dao.insertMessages(
            listOf(
                ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = role,
                    content = content,
                    toolName = toolName,
                    toolCallId = toolCallId,
                    createdAt = now,
                    position = now,
                ),
            ),
        )
    }

    suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
    }
}

private fun ChatSessionEntity.toSession() = ChatSession(
    id = id,
    repoFullName = repoFullName,
    title = title,
    createdAt = createdAt,
)

private fun ChatMessageEntity.toMessage() = ChatMessage(
    id = id,
    sessionId = sessionId,
    role = role,
    content = content,
    toolName = toolName,
    toolCallId = toolCallId,
    createdAt = createdAt,
)
