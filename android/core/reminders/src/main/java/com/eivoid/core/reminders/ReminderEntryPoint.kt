package com.eivoid.core.reminders

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The narrow surface broadcast receivers need from the Hilt graph. Receivers
 * use [dagger.hilt.android.EntryPointAccessors] rather than field injection,
 * which keeps them usable from a process that Android started just to deliver
 * the broadcast.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun reminderRepository(): ReminderRepository
    fun notificationHelper(): NotificationHelper
}
