package com.eivoid.feature.reminders

import com.eivoid.core.database.ReminderEntity
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

/** Date, money and greeting formatting shared by the three reminder screens. */
internal object ReminderFormat {

    private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val WEEKDAY_DATE = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val LONG_DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun money(amountMinor: Long, currency: String): String {
        val amount = amountMinor / 100.0
        val locale = localeFor(currency)
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = if (amountMinor % 100L == 0L) 0 else 2
            maximumFractionDigits = 2
        }
        val symbol = runCatching {
            Currency.getInstance(currency.uppercase(Locale.US)).getSymbol(locale)
        }.getOrDefault(currency.uppercase(Locale.US))
        return "$symbol${number.format(amount)}"
    }

    fun moneyCompact(amountMinor: Long, currency: String): String {
        val amount = amountMinor / 100.0
        val locale = localeFor(currency)
        val number = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 1
        }
        val symbol = runCatching {
            Currency.getInstance(currency.uppercase(Locale.US)).getSymbol(locale)
        }.getOrDefault(currency.uppercase(Locale.US))
        return when {
            amount >= 10_000_000 -> "$symbol${number.format(amount / 10_000_000)}Cr"
            amount >= 100_000 -> "$symbol${number.format(amount / 100_000)}L"
            else -> "$symbol${number.format(amount)}"
        }
    }

    fun currencySymbol(currency: String): String = when (currency.uppercase(Locale.US)) {
        "INR" -> "₹"
        "USD" -> "$"
        else -> currency.uppercase(Locale.US)
    }

    fun relativeDue(dueAt: Long, now: Long = System.currentTimeMillis()): String {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val due = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, due)
        return when {
            days < -1 -> "Overdue by ${-days} days"
            days == -1L -> "Overdue by 1 day"
            days == 0L -> "Due today"
            days == 1L -> "Due tomorrow"
            days <= 7 -> "Due in $days days"
            else -> "Due ${due.format(SHORT_DATE)}"
        }
    }

    fun weekdayDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(WEEKDAY_DATE)

    fun shortDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(SHORT_DATE)

    fun time(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(TIME)

    fun time(hour: Int, minute: Int): String =
        LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).format(TIME)

    fun todayLong(): String = LocalDate.now().format(LONG_DATE)

    fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }

    /**
     * Sums reminders due in the current month per currency. Paid rows are
     * excluded: the tiles answer "what is still owed this month".
     */
    fun monthTotals(
        reminders: List<ReminderEntity>,
        now: Long = System.currentTimeMillis(),
    ): MonthTotals {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = today.withDayOfMonth(1)
        val end = start.plusMonths(1)
        val inMonth = reminders.filter { reminder ->
            val due = Instant.ofEpochMilli(reminder.dueAt).atZone(zone).toLocalDate()
            !reminder.isPaid && reminder.isActive &&
                !due.isBefore(start) && due.isBefore(end)
        }
        return MonthTotals(
            inrMinor = inMonth
                .filter { it.currency.equals("INR", ignoreCase = true) }
                .sumOf { it.amountMinor },
            usdMinor = inMonth
                .filter { it.currency.equals("USD", ignoreCase = true) }
                .sumOf { it.amountMinor },
        )
    }

    /** New reminders default to tomorrow at the user's notification time. */
    fun defaultDueAt(
        notifyHour: Int,
        notifyMinute: Int,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): Long = today.plusDays(1)
        .atTime(notifyHour.coerceIn(0, 23), notifyMinute.coerceIn(0, 59))
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    /** Replaces the date of [epochMillis] while keeping its wall-clock time. */
    fun withDate(epochMillis: Long, pickedDateMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        val date = Instant.ofEpochMilli(pickedDateMillis).atZone(ZoneId.of("UTC")).toLocalDate()
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    /** Replaces the time of [epochMillis] while keeping its date. */
    fun withTime(epochMillis: Long, hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return date.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    /** DatePicker works in UTC midnight; convert for its initial selection. */
    fun toPickerMillis(epochMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    }

    fun startOfDay(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun localeFor(currency: String): Locale =
        if (currency.equals("INR", ignoreCase = true)) Locale.forLanguageTag("en-IN") else Locale.US
}

internal data class MonthTotals(val inrMinor: Long, val usdMinor: Long)
