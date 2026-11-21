package com.eivoid.feature.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.eivoid.core.reminders.AlarmScheduler
import com.eivoid.core.reminders.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The three gates a reminder must pass before it can visibly fire. */
data class ReminderPermissions(
    val notificationPermissionGranted: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val exactAlarmsAllowed: Boolean = true,
) {
    /** Both notification gates are open. */
    val canPostNotifications: Boolean
        get() = notificationPermissionGranted && notificationsEnabled

    val summary: String
        get() = when {
            !notificationPermissionGranted -> "Notification permission is off"
            !notificationsEnabled -> "Notifications are muted for Ei Void"
            !exactAlarmsAllowed -> "Exact alarms are off; alarms may be delayed"
            else -> "Notifications are ready"
        }
}

/**
 * Reads the permission state from the platform. Extracted behind an interface
 * so the ViewModel can be unit tested without an Android context.
 */
interface ReminderPermissionSource {
    fun snapshot(): ReminderPermissions
}

class AndroidReminderPermissionSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationHelper: NotificationHelper,
    private val scheduler: AlarmScheduler,
) : ReminderPermissionSource {

    override fun snapshot(): ReminderPermissions {
        val permission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return ReminderPermissions(
            notificationPermissionGranted = permission,
            notificationsEnabled = notificationHelper.canNotify(),
            exactAlarmsAllowed = scheduler.canScheduleExactAlarms(),
        )
    }
}
