package com.eivoid.core.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eivoid.core.database.Category
import com.eivoid.core.database.ReminderEntity
import com.eivoid.core.database.RepeatRule
import com.eivoid.core.database.VoidDatabase
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device checks for the reminder domain. These run on a real API 33+
 * device and exercise the pieces a JVM test cannot: the Room schema in a real
 * SQLite, AlarmManager (including its exact-alarm fallback), and a posted
 * notification in the "reminders" channel.
 */
@RunWith(AndroidJUnit4::class)
class ReminderDeviceTest {

    private lateinit var db: VoidDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VoidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun reminderDao_roundTripsOnDeviceSqlite() = runBlocking {
        val id = db.reminderDao().insert(reminder())
        val stored = db.reminderDao().get(id)

        assertNotNull(stored)
        assertEquals("Rent", stored!!.title)
        assertEquals(RepeatRule.MONTHLY, stored.repeatRule)
        assertEquals(Category.RENT, stored.category)
        assertEquals(ReminderEntity.DEFAULT_LEAD_DAYS, stored.leadDays)
    }

    @Test
    fun alarmScheduler_schedulesAndCancelsWithoutThrowing() {
        val scheduler = AlarmScheduler(context)
        val reminder = reminder(dueAt = System.currentTimeMillis() + 3_600_000L)

        // On a device that has not granted SCHEDULE_EXACT_ALARM this must use
        // the inexact fallback rather than throw.
        scheduler.scheduleReminder(reminder, ReminderSettings())
        scheduler.cancel(reminder.id)

        val expected = AlarmScheduler.notifyAt(
            reminder.copy(dueAt = epoch(2026, 3, 10, 18, 0), leadDays = 4),
            ReminderSettings(notifyHour = 9, notifyMinute = 30),
        )
        assertEquals(epoch(2026, 3, 6, 9, 30), expected)
    }

    @Test
    fun notificationHelper_postsInTheRemindersChannel() {
        grantNotificationPermission()
        val helper = NotificationHelper(context)
        helper.createChannels()

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_REMINDERS)
        assertNotNull("the reminders channel must exist", channel)

        val reminder = reminder(id = 42L)
        val shown = helper.showReminder(reminder)
        assertTrue("the notification must post when permission is granted", shown)

        val posted = manager.activeNotifications.any { it.id == 20_000 + 42 }
        assertTrue("the reminder notification id must be active", posted)

        helper.cancelReminder(reminder.id)
    }

    private fun grantNotificationPermission() {
        val permissionGranted = context.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) return

        val pfd = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
            )
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    private fun reminder(
        id: Long = 0L,
        dueAt: Long = 1_800_000_000_000L,
    ) = ReminderEntity(
        id = id,
        title = "Rent",
        merchant = "Landlord",
        bank = "HDFC",
        amountMinor = 50_000L,
        currency = "INR",
        dueAt = dueAt,
        repeatRule = RepeatRule.MONTHLY,
        category = Category.RENT,
        leadDays = 4,
    )

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDate.of(year, month, day)
            .atTime(hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
