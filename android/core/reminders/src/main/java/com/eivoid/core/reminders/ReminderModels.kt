package com.eivoid.core.reminders

import com.eivoid.core.database.Category as CategoryEntity
import com.eivoid.core.database.RepeatRule as RepeatRuleEntity
import java.time.Instant
import java.time.ZoneId

/**
 * The reminder domain models live in :core:database because
 * [com.eivoid.core.database.ReminderEntity] stores them through Room
 * converters. Re-exporting them here keeps the reminder feature reading from
 * one package while the database module stays free of a dependency cycle.
 */
typealias RepeatRule = RepeatRuleEntity

typealias Category = CategoryEntity

/**
 * Returns the next occurrence after [from], or null for a one-time reminder.
 * Monthly and yearly rules use java.time arithmetic, which clamps a due date
 * (for example 31 January plus one month is the last day of February).
 */
fun RepeatRule.advance(from: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val at = Instant.ofEpochMilli(from).atZone(zone)
    val next = when (this) {
        RepeatRule.NONE -> return null
        RepeatRule.WEEKLY -> at.plusWeeks(1)
        RepeatRule.MONTHLY -> at.plusMonths(1)
        RepeatRule.QUARTERLY -> at.plusMonths(3)
        RepeatRule.YEARLY -> at.plusYears(1)
    }
    return next.toInstant().toEpochMilli()
}

/**
 * Advances [from] until the occurrence is after [now]. A reminder that was
 * paid long after it was due must not create a chain of past occurrences.
 * The guard keeps a bad clock from looping forever.
 */
fun RepeatRule.advanceUntilFuture(
    from: Long,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): Long? {
    var next = advance(from, zone) ?: return null
    var guard = 0
    while (next <= now) {
        // ~190 years of weekly cycles. Past this the data is pathological
        // (a corrupt clock or due date), so reporting "no next occurrence" is
        // safer than returning another date in the past.
        if (guard++ >= MAX_ADVANCES) return null
        next = advance(next, zone) ?: return null
    }
    return next
}

private const val MAX_ADVANCES = 10_000
