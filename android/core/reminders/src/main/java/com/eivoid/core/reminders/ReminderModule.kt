package com.eivoid.core.reminders

import android.content.Context
import com.eivoid.core.database.ReminderDao
import com.eivoid.core.database.VoidDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

/**
 * The reminder feature's Hilt graph.
 *
 * :core:database owns the entity and DAO, and the app module already provides
 * [VoidDatabase] and [HttpClient] in the singleton component, so this module
 * only adds reminder-specific bindings. Feature screens inject
 * [ReminderRepository] and [ReminderSettingsStore]; nothing else is public.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    @Binds
    @Singleton
    abstract fun bindReminderRepository(impl: DefaultReminderRepository): ReminderRepository

    companion object {

        @Provides
        @Singleton
        fun provideReminderDao(database: VoidDatabase): ReminderDao = database.reminderDao()

        @Provides
        @Singleton
        fun provideAlarmScheduler(@ApplicationContext context: Context): AlarmScheduler =
            AlarmScheduler(context)

        @Provides
        @Singleton
        fun provideNotificationHelper(@ApplicationContext context: Context): NotificationHelper =
            NotificationHelper(context)

        @Provides
        @Singleton
        fun provideReminderSettingsStore(
            @ApplicationContext context: Context,
        ): ReminderSettingsStore = ReminderSettingsStore(context)

        @Provides
        @Singleton
        fun provideReminderSyncTransport(httpClient: HttpClient): ReminderSyncTransport =
            ReminderSyncTransport(httpClient, BuildConfig.EI_VOID_API_URL)

        @Provides
        @Singleton
        fun provideReminderSyncClient(
            transport: ReminderSyncTransport,
            settingsStore: ReminderSettingsStore,
            reminderDao: ReminderDao,
            scheduler: AlarmScheduler,
        ): ReminderSyncClient = ReminderSyncClient(
            transport = transport,
            settingsProvider = { settingsStore.current() },
            remindersProvider = { reminderDao.getAll() },
            notifyAt = { reminder, settings -> scheduler.reminderNotifyAt(reminder, settings) },
        )
    }
}
