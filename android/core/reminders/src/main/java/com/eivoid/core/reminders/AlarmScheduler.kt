package com.eivoid.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.eivoid.core.database.ReminderEntity
import java.time.Instant
import java.time.ZoneId

/**
 * Schedules one alarm per active reminder.
 *
 * Exact alarms need the SCHEDULE_EXACT_ALARM permission (declared in this
 * module's manifest; USE_EXACT_ALARM is deliberately not used because it is
 * reserved for alarm-clock apps). When the user has not granted it, or the
 * platform throws while setting an exact alarm, scheduling falls back to
 * [AlarmManager.setAndAllowWhileIdle], which may be delayed but still fires.
 */
class AlarmScheduler(private val context: Context) {

    private val manager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    fun scheduleReminder(reminder: ReminderEntity, settings: ReminderSettings) {
        cancel(reminder.id)
        if (!reminder.isActive || reminder.isPaid) return

        val now = System.currentTimeMillis()
        val target = reminderNotifyAt(reminder, settings)
        val alreadyNotified = reminder.notifiedForDueAt == reminder.dueAt

        val trigger = when {
            target > now -> target
            alreadyNotified -> return
            // Due already and never announced: post shortly so the user sees it.
            else -> now + IMMEDIATE_DELAY_MILLIS
        }

        val pendingIntent = reminderPendingIntent(reminder.id)
        runCatching {
            if (canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    trigger,
                    pendingIntent,
                )
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
            }
        }.onFailure {
            // The permission can be revoked between the check and the call.
            runCatching {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    trigger,
                    pendingIntent,
                )
            }
        }
    }

    fun cancel(reminderId: Long) {
        manager.cancel(reminderPendingIntent(reminderId))
    }

    /** True when the platform will honour an exact alarm right now. */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    /**
     * The instant the notification should fire: [ReminderSettings.notifyHour]
     * on the day [ReminderEntity.leadDays] before the due date, in the device
     * time zone.
     */
    fun reminderNotifyAt(reminder: ReminderEntity, settings: ReminderSettings): Long =
        notifyAt(reminder, settings)

    private fun reminderPendingIntent(reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_REMINDER = "com.eivoid.core.reminders.action.REMINDER"
        const val ACTION_MARK_PAID = "com.eivoid.core.reminders.action.MARK_PAID"
        const val ACTION_SNOOZE = "com.eivoid.core.reminders.action.SNOOZE"
        const val EXTRA_ID = "reminder_id"

        private const val IMMEDIATE_DELAY_MILLIS = 5_000L

        /**
         * Pure schedule calculation, so the same result is used by the
         * scheduler, the sync payload, and tests.
         */
        fun notifyAt(
            reminder: ReminderEntity,
            settings: ReminderSettings,
            zone: ZoneId = ZoneId.systemDefault(),
        ): Long {
            val dueDate = Instant.ofEpochMilli(reminder.dueAt).atZone(zone).toLocalDate()
            val notifyDate = dueDate.minusDays(reminder.leadDays.coerceAtLeast(0).toLong())
            return notifyDate
                .atTime(
                    settings.notifyHour.coerceIn(0, 23),
                    settings.notifyMinute.coerceIn(0, 59),
                )
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }
    }
}
