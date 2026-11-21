package com.eivoid.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReminderDaoTest {

    private lateinit var db: VoidDatabase
    private lateinit var dao: ReminderDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VoidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.reminderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert returns an id and observeAll keeps newest due first`() = runTest {
        dao.insert(reminder(title = "Early", dueAt = 100L))
        dao.insert(reminder(title = "Late", dueAt = 300L))

        val all = dao.observeAll().first()

        assertEquals(listOf("Late", "Early"), all.map { it.title })
    }

    @Test
    fun `observeActive and getActive exclude paid and inactive rows`() = runTest {
        dao.insert(reminder(title = "Active", dueAt = 200L))
        dao.insert(reminder(title = "Paid", dueAt = 100L, isPaid = true, isActive = false))
        dao.insert(reminder(title = "Archived", dueAt = 300L, isActive = false))

        val active = dao.observeActive().first()

        assertEquals(listOf("Active"), active.map { it.title })
        assertEquals(listOf("Active"), dao.getActive().map { it.title })
        assertEquals(3, dao.getAll().size)
    }

    @Test
    fun `get returns null for an unknown id`() = runTest {
        assertNull(dao.get(404L))
    }

    @Test
    fun `insert and update round-trip the enums and nullable paidAt`() = runTest {
        val id = dao.insert(
            reminder(
                title = "Rent",
                repeatRule = RepeatRule.MONTHLY,
                category = Category.RENT,
            ),
        )

        val inserted = dao.get(id)!!
        assertEquals(RepeatRule.MONTHLY, inserted.repeatRule)
        assertEquals(Category.RENT, inserted.category)

        dao.update(inserted.copy(isPaid = true, paidAt = 9_000L, isActive = false))

        val updated = dao.get(id)!!
        assertTrue(updated.isPaid)
        assertEquals(9_000L, updated.paidAt)
        assertFalse(updated.isActive)
    }

    @Test
    fun `default lead days is four`() = runTest {
        val id = dao.insert(reminder(title = "Default lead"))

        assertEquals(ReminderEntity.DEFAULT_LEAD_DAYS, dao.get(id)!!.leadDays)
    }

    @Test
    fun `applyDefaultLeadDays leaves customised rows alone`() = runTest {
        val plain = dao.insert(reminder(title = "Plain", leadDays = 2))
        val custom = dao.insert(
            reminder(title = "Custom", leadDays = 10, leadDaysCustomized = true),
        )

        dao.applyDefaultLeadDays(7)

        assertEquals(7, dao.get(plain)!!.leadDays)
        assertEquals(10, dao.get(custom)!!.leadDays)
    }

    @Test
    fun `updateNotified records only the due date it was given`() = runTest {
        val id = dao.insert(reminder(title = "Notify", dueAt = 5_000L))

        dao.updateNotified(id, 5_000L)

        val updated = dao.get(id)!!
        assertEquals(5_000L, updated.notifiedForDueAt)
        // It is not a paid flag: the row stays active.
        assertTrue(updated.isActive)
        assertFalse(updated.isPaid)
    }

    @Test
    fun `deleteById removes one row and deleteAll empties the table`() = runTest {
        val first = dao.insert(reminder(title = "First"))
        dao.insert(reminder(title = "Second"))

        dao.deleteById(first)
        assertEquals(listOf("Second"), dao.getAll().map { it.title })

        dao.deleteAll()
        assertTrue(dao.getAll().isEmpty())
    }

    private fun reminder(
        title: String,
        dueAt: Long = 1_000L,
        leadDays: Int = ReminderEntity.DEFAULT_LEAD_DAYS,
        leadDaysCustomized: Boolean = false,
        repeatRule: RepeatRule = RepeatRule.NONE,
        category: Category = Category.BILL,
        isPaid: Boolean = false,
        isActive: Boolean = true,
    ) = ReminderEntity(
        title = title,
        merchant = "Merchant",
        amountMinor = 12_500L,
        currency = "INR",
        dueAt = dueAt,
        repeatRule = repeatRule,
        category = category,
        leadDays = leadDays,
        leadDaysCustomized = leadDaysCustomized,
        isPaid = isPaid,
        isActive = isActive,
    )
}
