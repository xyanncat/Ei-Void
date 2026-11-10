package com.voidapp.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatDaoTest {

    private lateinit var db: VoidDatabase
    private lateinit var dao: ChatDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VoidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `sessions are listed newest first`() = runTest {
        dao.insertSession(session("1", createdAt = 10L))
        dao.insertSession(session("2", createdAt = 20L))

        val sessions = dao.observeSessions("owner/repo").first()

        assertEquals(listOf("2", "1"), sessions.map { it.id })
    }

    @Test
    fun `sessions are scoped to a repository`() = runTest {
        dao.insertSession(session("1", repo = "owner/repo"))
        dao.insertSession(session("2", repo = "owner/other"))

        val sessions = dao.observeSessions("owner/repo").first()

        assertEquals(listOf("1"), sessions.map { it.id })
    }

    @Test
    fun `messages are listed in order`() = runTest {
        dao.insertMessages(
            listOf(
                message("b", createdAt = 20L, position = 2L),
                message("a", createdAt = 10L, position = 1L),
            ),
        )

        val messages = dao.observeMessages("session").first()

        assertEquals(listOf("a", "b"), messages.map { it.id })
    }

    @Test
    fun `messages of other sessions are not returned`() = runTest {
        dao.insertMessages(
            listOf(
                message("a", sessionId = "one", createdAt = 1L),
                message("b", sessionId = "two", createdAt = 2L),
            ),
        )

        val messages = dao.observeMessages("one").first()

        assertEquals(listOf("a"), messages.map { it.id })
    }

    @Test
    fun `deleting a session deletes its messages`() = runTest {
        dao.insertSession(session("1"))
        dao.insertMessages(listOf(message("a", sessionId = "1")))
        dao.insertMessages(listOf(message("b", sessionId = "2")))

        dao.deleteSession("1")

        assertTrue(dao.observeSessions("owner/repo").first().isEmpty())
        assertTrue(dao.observeMessages("1").first().isEmpty())
        assertEquals(1, dao.observeMessages("2").first().size)
    }

    private fun session(
        id: String,
        repo: String = "owner/repo",
        createdAt: Long = 0L,
    ) = ChatSessionEntity(
        id = id,
        repoFullName = repo,
        title = "Session $id",
        createdAt = createdAt,
    )

    private fun message(
        id: String,
        sessionId: String = "session",
        createdAt: Long = 0L,
        position: Long = 0L,
    ) = ChatMessageEntity(
        id = id,
        sessionId = sessionId,
        role = "user",
        content = "hello",
        toolName = null,
        toolCallId = null,
        createdAt = createdAt,
        position = position,
    )
}
