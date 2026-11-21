package com.eivoid.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** How often a reminder repeats after it is paid. Stored as its name. */
enum class RepeatRule(val label: String) {
    NONE("One time"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly"),
}

/** What the payment is for. Stored as its name. */
enum class Category(val label: String) {
    BILL("Bill"),
    SUBSCRIPTION("Subscription"),
    EMI("EMI"),
    RENT("Rent"),
    INSURANCE("Insurance"),
    OTHER("Other"),
}

/**
 * A payment reminder.
 *
 * [leadDays] defaults to 4: a bill is announced four days before it is due.
 * Rows that the user customised keep their own lead time; the preference is
 * applied only to columns where [leadDaysCustomized] is false.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val merchant: String,
    val bank: String = "",
    val amountMinor: Long,
    val currency: String,
    val dueAt: Long,
    val repeatRule: RepeatRule = RepeatRule.NONE,
    val category: Category = Category.BILL,
    val notes: String = "",
    val leadDays: Int = DEFAULT_LEAD_DAYS,
    val leadDaysCustomized: Boolean = false,
    val isPaid: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val paidAt: Long? = null,
    /** Set to [dueAt] only after the notification for that due date actually posted. */
    val notifiedForDueAt: Long = 0L,
) {
    companion object {
        const val DEFAULT_LEAD_DAYS = 4
    }
}
