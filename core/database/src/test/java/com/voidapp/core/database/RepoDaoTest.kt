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
class RepoDaoTest {

    private lateinit var db: VoidDatabase
    private lateinit var dao: RepoDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VoidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.repoDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert and observe repos`() = runTest {
        dao.insertAll(listOf(repo("1", "owner", "repo")))

        val result = dao.observeAll().first()

        assertEquals(1, result.size)
        assertEquals("owner/repo", result[0].fullName)
    }

    @Test
    fun `search filters by name`() = runTest {
        dao.insertAll(listOf(repo("1", "owner", "alpha"), repo("2", "owner", "beta")))

        val result = dao.search("alp").first()

        assertEquals(listOf("alpha"), result.map { it.name })
    }

    @Test
    fun `search matches the owner prefix in the full name`() = runTest {
        dao.insertAll(listOf(repo("1", "acme", "alpha"), repo("2", "other", "beta")))

        val result = dao.search("acme/").first()

        assertEquals(listOf("alpha"), result.map { it.name })
    }

    @Test
    fun `insert replaces an existing repo`() = runTest {
        dao.insertAll(listOf(repo("1", "owner", "repo", updatedAt = 10L)))
        dao.insertAll(listOf(repo("1", "owner", "repo", updatedAt = 20L)))

        val result = dao.observeAll().first()

        assertEquals(1, result.size)
        assertEquals(20L, result[0].updatedAt)
    }

    @Test
    fun `deleteAll empties the table`() = runTest {
        dao.insertAll(listOf(repo("1", "owner", "repo")))

        dao.deleteAll()

        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `deleteNotIn removes only the repos that are gone`() = runTest {
        dao.insertAll(listOf(repo("1", "owner", "alpha"), repo("2", "owner", "beta")))

        dao.deleteNotIn(listOf("2"))

        assertEquals(listOf("owner/beta"), dao.observeAll().first().map { it.fullName })
    }

    private fun repo(
        id: String,
        owner: String,
        name: String,
        updatedAt: Long = 0L,
    ) = RepoEntity(
        id = id,
        owner = owner,
        name = name,
        fullName = "$owner/$name",
        isPrivate = false,
        defaultBranch = "main",
        description = null,
        updatedAt = updatedAt,
    )
}
