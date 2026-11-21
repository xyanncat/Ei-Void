package com.eivoid.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles the "Mark paid" and "Snooze 1 day" notification actions. */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        val action = intent.action ?: return
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
                when (action) {
                    AlarmScheduler.ACTION_MARK_PAID -> repository.markPaid(reminderId)
                    AlarmScheduler.ACTION_SNOOZE -> repository.snooze(reminderId, 1)
                    else -> return@launch
                }
                entryPoint.notificationHelper().cancelReminder(reminderId)
            } catch (_: Throwable) {
                // Leave the notification so the user can try the action again.
            } finally {
                pending.finish()
            }
        }
    }
}
