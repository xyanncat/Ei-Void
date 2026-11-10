package com.voidapp.feature.chat

import com.voidapp.core.database.ChatDao
import com.voidapp.core.database.ChatMessageEntity
import com.voidapp.core.database.ChatSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryTest {

    @Test
    fun `a session is created and observed`() = runTest {
        val dao = FakeChatDao()
        val repository = ChatRepository(dao)

        val id = repository.createSession("owner/repo", "Fix the parser")

        val sessions = repository.observeSessions("owner/repo").first()
        assertEquals(listOf(id), sessions.map { it.id })
        assertEquals("Fix the parser", sessions.single().title)
    }

    @Test
    fun `a long title is trimmed`() = runTest {
        val repository = ChatRepository(FakeChatDao())

        repository.createSession("owner/repo", "x".repeat(200))

        assertEquals(80, repository.observeSessions("owner/repo").first().single().title.length)
    }

    @Test
    fun `messages are appended in order`() = runTest {
        val repository = ChatRepository(FakeChatDao())
        val id = repository.createSession("owner/repo", "t")

        repository.appendMessage(id, role = "user", content = "hello")
        repository.appendMessage(id, role = "assistant", content = "hi")
        repository.appendMessage(id, role = "tool", content = "ok", toolName = "read_file")

        val messages = repository.observeMessages(id).first()
        assertEquals(listOf("user", "assistant", "tool"), messages.map { it.role })
        assertEquals("read_file", messages.last().toolName)
    }

    @Test
    fun `deleting a session removes it`() = runTest {
        val repository = ChatRepository(FakeChatDao())
        val id = repository.createSession("owner/repo", "t")

        repository.deleteSession(id)

        assertTrue(repository.observeSessions("owner/repo").first().isEmpty())
    }

    private class FakeChatDao : ChatDao {
        private val sessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
        private val messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

        override suspend fun insertSession(session: ChatSessionEntity) {
            sessions.value = sessions.value.filterNot { it.id == session.id } + session
        }

        override suspend fun insertMessages(messages: List<ChatMessageEntity>) {
            this.messages.value = this.messages.value + messages
        }

        override fun observeSessions(repoFullName: String): Flow<List<ChatSessionEntity>> =
            sessions.map { all -> all.filter { it.repoFullName == repoFullName }.sortedByDescending { it.createdAt } }

        override fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>> =
            messages.map { all -> all.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.createdAt }, { it.position })) }

        override suspend fun deleteMessages(sessionId: String) {
            messages.value = messages.value.filterNot { it.sessionId == sessionId }
        }

        override suspend fun deleteSessionRow(sessionId: String) {
            sessions.value = sessions.value.filterNot { it.id == sessionId }
        }
    }
}
