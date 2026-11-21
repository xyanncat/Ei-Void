package com.eivoid.core.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eivoid.core.database.Category
import com.eivoid.core.database.ReminderEntity
import com.eivoid.core.database.RepeatRule
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlarmSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scheduler = AlarmScheduler(context)
    private val zone: ZoneId = ZoneId.systemDefault()

    @Test
    fun `notify time is lead days before the due date at the configured hour`() {
        val reminder = reminder(
            dueAt = epoch(2026, 3, 10, 18, 0),
            leadDays = 4,
        )
        val settings = ReminderSettings(notifyHour = 9, notifyMinute = 30)

        val actual = scheduler.reminderNotifyAt(reminder, settings)

        assertEquals(epoch(2026, 3, 6, 9, 30), actual)
    }

    @Test
    fun `a zero day lead notifies on the due date itself`() {
        val reminder = reminder(
            dueAt = epoch(2026, 3, 10, 18, 0),
            leadDays = 0,
        )

        val actual = scheduler.reminderNotifyAt(reminder, ReminderSettings())

        assertEquals(epoch(2026, 3, 10, 9, 0), actual)
    }

    @Test
    fun `scheduling an inactive reminder is a no-op that does not throw`() {
        scheduler.scheduleReminder(
            reminder(dueAt = epoch(2026, 3, 10, 18, 0), isActive = false),
            ReminderSettings(),
        )
    }

    private fun reminder(
        dueAt: Long,
        leadDays: Int = ReminderEntity.DEFAULT_LEAD_DAYS,
        isActive: Boolean = true,
    ) = ReminderEntity(
        title = "Rent",
        merchant = "Landlord",
        amountMinor = 50_000L,
        currency = "INR",
        dueAt = dueAt,
        repeatRule = RepeatRule.MONTHLY,
        category = Category.RENT,
        leadDays = leadDays,
        isActive = isActive,
    )

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDate.of(year, month, day)
            .atTime(hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
}
