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
class UsageDaoTest {

    private lateinit var db: VoidDatabase
    private lateinit var dao: UsageDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VoidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.usageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `tokens are summed per tier since a moment`() = runTest {
        dao.insert(entry("RED_DWARF", 100, at = 1_000))
        dao.insert(entry("RED_DWARF", 50, at = 2_000))
        dao.insert(entry("NEUTRON_STAR", 30, at = 2_000))
        dao.insert(entry("RED_DWARF", 999, at = 500)) // before the window

        assertEquals(150L, dao.tokensSince("RED_DWARF", since = 900))
        assertEquals(30L, dao.tokensSince("NEUTRON_STAR", since = 900))
    }

    @Test
    fun `a tier with no usage reads as zero`() = runTest {
        assertEquals(0L, dao.tokensSince("WHITE_DWARF", since = 0))
    }

    @Test
    fun `totals are grouped by tier`() = runTest {
        dao.insert(entry("RED_DWARF", 100, at = 1_000))
        dao.insert(entry("NEUTRON_STAR", 30, at = 1_000))
        dao.insert(entry("NEUTRON_STAR", 20, at = 2_000))

        val totals = dao.totalsSince(since = 0).first().associate { it.tier to it.tokens }

        assertEquals(100L, totals["RED_DWARF"])
        assertEquals(50L, totals["NEUTRON_STAR"])
    }

    @Test
    fun `old rows can be pruned`() = runTest {
        dao.insert(entry("RED_DWARF", 100, at = 1_000))
        dao.insert(entry("RED_DWARF", 50, at = 5_000))

        dao.deleteBefore(before = 3_000)

        assertEquals(50L, dao.tokensSince("RED_DWARF", since = 0))
    }

    @Test
    fun `entries of the current month are kept`() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(entry("WHITE_DWARF", 42, at = now))

        val totals = dao.totalsSince(since = now - 1_000).first()

        assertTrue(totals.any { it.tier == "WHITE_DWARF" && it.tokens == 42L })
    }

    private fun entry(tier: String, tokens: Int, at: Long) = UsageEntryEntity(
        tier = tier,
        tokens = tokens,
        createdAt = at,
    )
}
