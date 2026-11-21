package com.eivoid.core.reminders

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderModelsTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    fun `one-time reminders never advance`() {
        assertNull(RepeatRule.NONE.advance(at(2026, 1, 1), zone))
        assertNull(RepeatRule.NONE.advanceUntilFuture(at(2026, 1, 1), now = 0L, zone = zone))
    }

    @Test
    fun `weekly advances by seven days`() {
        val next = RepeatRule.WEEKLY.advance(at(2026, 1, 1, 9, 0), zone)

        assertEquals(at(2026, 1, 8, 9, 0), next)
    }

    @Test
    fun `monthly clamps a month-end date`() {
        val next = RepeatRule.MONTHLY.advance(at(2026, 1, 31, 9, 0), zone)

        assertEquals(at(2026, 2, 28, 9, 0), next)
    }

    @Test
    fun `quarterly advances three months and yearly twelve`() {
        assertEquals(
            at(2026, 4, 1, 9, 0),
            RepeatRule.QUARTERLY.advance(at(2026, 1, 1, 9, 0), zone),
        )
        assertEquals(
            at(2027, 1, 1, 9, 0),
            RepeatRule.YEARLY.advance(at(2026, 1, 1, 9, 0), zone),
        )
    }

    @Test
    fun `advanceUntilFuture skips occurrences that are already past`() {
        val from = at(2026, 1, 1, 9, 0)
        val now = at(2026, 1, 20, 12, 0)

        val next = RepeatRule.WEEKLY.advanceUntilFuture(from, now, zone)

        assertEquals(at(2026, 1, 22, 9, 0), next)
    }

    @Test
    fun `advanceUntilFuture returns the first future occurrence from a past date`() {
        val next = RepeatRule.WEEKLY.advanceUntilFuture(
            from = at(2020, 1, 1, 9, 0),
            now = at(2026, 1, 1, 0, 0),
            zone = zone,
        )

        // 2020-01-01 + a whole number of weeks lands on 2025-12-31, so the
        // first occurrence strictly after 2026-01-01 00:00 is 2026-01-07.
        assertEquals(at(2026, 1, 7, 9, 0), next)
    }

    @Test
    fun `settings default to a four day lead and 9am`() {
        val settings = ReminderSettings()

        assertEquals(4, settings.defaultLeadDays)
        assertEquals(9, settings.notifyHour)
        assertEquals(0, settings.notifyMinute)
        assertEquals(true, settings.emailRemindersEnabled)
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): Long = LocalDate.of(year, month, day)
        .atTime(LocalTime.of(hour, minute))
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
