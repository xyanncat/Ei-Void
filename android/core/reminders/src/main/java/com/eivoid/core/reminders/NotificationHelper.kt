package com.eivoid.core.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eivoid.core.database.ReminderEntity
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

/**
 * Builds and posts the reminder notification.
 *
 * Content intent: the host app's launch activity, carrying both the reminder
 * id extra and an `eivoid://reminders/{id}` data URI so the app router can
 * open the right screen without this module depending on the app's classes.
 *
 * Actions: "Mark paid" and "Snooze 1 day" are received by
 * [ReminderActionReceiver]. The notification is private on the lock screen.
 */
class NotificationHelper(private val context: Context) {

    private val manager: NotificationManagerCompat = NotificationManagerCompat.from(context)

    fun createChannels() {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.eivoid_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.eivoid_reminder_channel_description)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts the notification and returns whether it was actually handed to
     * the system. Callers must only record delivery when this returns true.
     */
    fun showReminder(reminder: ReminderEntity): Boolean {
        createChannels()
        if (!canNotify()) return false

        val contentIntent = contentPendingIntent(reminder)
        val markPaid = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt() * 31 + 1,
            actionIntent(AlarmScheduler.ACTION_MARK_PAID, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt() * 31 + 2,
            actionIntent(AlarmScheduler.ACTION_SNOOZE, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val money = if (reminder.amountMinor > 0) {
            "${formatMoney(reminder.amountMinor, reminder.currency)} · "
        } else {
            ""
        }
        val whenText = relativeDue(reminder.dueAt)
        val detail = "$whenText · ${weekday(reminder.dueAt)}"

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_eivoid_reminder)
            .setContentTitle("$money${reminder.title}")
            .setContentText(detail)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$detail\n${reminder.notes}".trim()),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Mark paid", markPaid)
            .addAction(0, "Snooze 1 day", snooze)
        if (contentIntent != null) builder.setContentIntent(contentIntent)

        return runCatching { manager.notify(notificationId(reminder.id), builder.build()) }
            .isSuccess
    }

    fun cancelReminder(reminderId: Long) {
        manager.cancel(notificationId(reminderId))
    }

    /** Notification permission, the global switch, and the channel must all allow it. */
    fun canNotify(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL_REMINDERS) ?: return false
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun contentPendingIntent(reminder: ReminderEntity): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        val deepLink = "$DEEP_LINK_SCHEME${reminder.id}"
        launch.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse(deepLink)
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_DEEP_LINK, deepLink)
        }
        return PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(action: String, reminderId: Long): Intent =
        Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmScheduler.EXTRA_ID, reminderId)
        }

    private fun notificationId(reminderId: Long): Int =
        NOTIFICATION_ID_BASE + (reminderId % NOTIFICATION_ID_RANGE).toInt()

    private fun weekday(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(WEEKDAY_FORMAT)

    private fun relativeDue(epochMillis: Long): String {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val due = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, due)
        return when {
            days < -1 -> "Overdue by ${-days} days"
            days == -1L -> "Overdue by 1 day"
            days == 0L -> "Due today"
            days == 1L -> "Due tomorrow"
            days <= 7 -> "Due in $days days"
            else -> "Due ${due.format(SHORT_DATE_FORMAT)}"
        }
    }

    private fun formatMoney(amountMinor: Long, currency: String): String {
        val amount = amountMinor / 100.0
        val locale = if (currency.equals("INR", ignoreCase = true)) {
            Locale.forLanguageTag("en-IN")
        } else {
            Locale.US
        }
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
        val code = runCatching { Currency.getInstance(currency.uppercase()) }.getOrNull()
        val symbol = code?.getSymbol(locale) ?: currency.uppercase()
        return "$symbol${number.format(amount)}"
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_DEEP_LINK = "eivoid_deep_link"
        const val DEEP_LINK_SCHEME = "eivoid://reminders/"

        private const val NOTIFICATION_ID_BASE = 20_000
        private const val NOTIFICATION_ID_RANGE = 40_000L

        private val WEEKDAY_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
        private val SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    }
}
