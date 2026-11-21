package com.eivoid.core.reminders

/**
 * User preferences for reminders. [defaultLeadDays] is four days so a bill is
 * announced well before it is due; [notifyHour]/[notifyMinute] are the local
 * time a reminder's notification is posted.
 */
data class ReminderSettings(
    val defaultLeadDays: Int = DEFAULT_LEAD_DAYS,
    val notifyHour: Int = DEFAULT_NOTIFY_HOUR,
    val notifyMinute: Int = DEFAULT_NOTIFY_MINUTE,
    val emailRemindersEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_LEAD_DAYS = 4
        const val DEFAULT_NOTIFY_HOUR = 9
        const val DEFAULT_NOTIFY_MINUTE = 0

        const val MIN_LEAD_DAYS = 0
        const val MAX_LEAD_DAYS = 30

        val Default = ReminderSettings()
    }
}
