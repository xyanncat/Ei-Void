package com.eivoid.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires at the scheduled time. The notification is posted first; the delivery
 * marker is written only when that post actually happened, so a denied
 * permission or a muted channel does not permanently suppress retries.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        if (reminderId <= 0L) return

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    appContext,
                    ReminderEntryPoint::class.java,
                )
                val repository = entryPoint.reminderRepository()
                val reminder = repository.get(reminderId)
                if (reminder != null && reminder.isActive && !reminder.isPaid) {
                    val shown = entryPoint.notificationHelper().showReminder(reminder)
                    if (shown) {
                        repository.markNotified(reminder.id, reminder.dueAt)
                    }
                }
            } catch (_: Throwable) {
                // The in-app list remains the source of truth; retry next alarm.
            } finally {
                pending.finish()
            }
        }
    }
}
