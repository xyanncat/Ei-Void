package com.eivoid.app

import android.app.Application
import com.clerk.api.Clerk
import com.eivoid.core.reminders.NotificationHelper
import com.eivoid.core.reminders.ReminderRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class VoidApp : Application() {

    /**
     * Reminder startup is best-effort. The database and alarms are local, so a
     * failure here must never stop the app from launching; the list screen
     * retries on its next refresh.
     */
    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var reminderRepository: ReminderRepository

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY
        if (publishableKey.startsWith("pk_") && !Clerk.isInitialized.value) {
            // A malformed key must not take the whole app down: the root gate
            // reports that Clerk is not ready instead.
            runCatching { Clerk.initialize(this, publishableKey) }
        }

        // A fresh install has no channel yet; creating it here lets the
        // reminder settings screen report the true notification state.
        runCatching { notificationHelper.createChannels() }
        // Alarms do not survive process death, so rebuild the active set from
        // Room on every cold start (boot/time changes are covered by the
        // receiver, but a force-stop is not).
        startupScope.launch {
            runCatching { reminderRepository.rescheduleAll() }
        }
    }
}
