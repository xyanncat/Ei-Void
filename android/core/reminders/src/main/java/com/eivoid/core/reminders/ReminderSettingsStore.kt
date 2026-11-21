package com.eivoid.core.reminders

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The single DataStore file for reminder preferences. It is top-level because
 * a DataStore delegate must be created once per process, and it keeps
 * reminder settings separate from the rest of the app's settings.
 */
private val Context.reminderSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "eivoid_reminder_settings",
)

/**
 * Persists [ReminderSettings]. The store is deliberately small: feature
 * screens observe [settings] and call a setter, nothing else.
 */
class ReminderSettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.reminderSettingsDataStore)

    private object Keys {
        val leadDays = intPreferencesKey("lead_days")
        val notifyHour = intPreferencesKey("notify_hour")
        val notifyMinute = intPreferencesKey("notify_minute")
        val emailEnabled = booleanPreferencesKey("email_enabled")
    }

    val settings: Flow<ReminderSettings> = dataStore.data.map { prefs ->
        ReminderSettings(
            defaultLeadDays = (prefs[Keys.leadDays] ?: ReminderSettings.DEFAULT_LEAD_DAYS)
                .coerceIn(ReminderSettings.MIN_LEAD_DAYS, ReminderSettings.MAX_LEAD_DAYS),
            notifyHour = (prefs[Keys.notifyHour] ?: ReminderSettings.DEFAULT_NOTIFY_HOUR)
                .coerceIn(0, 23),
            notifyMinute = (prefs[Keys.notifyMinute] ?: ReminderSettings.DEFAULT_NOTIFY_MINUTE)
                .coerceIn(0, 59),
            emailRemindersEnabled = prefs[Keys.emailEnabled] ?: true,
        )
    }

    suspend fun current(): ReminderSettings = settings.first()

    suspend fun setDefaultLeadDays(days: Int) {
        dataStore.edit {
            it[Keys.leadDays] = days.coerceIn(
                ReminderSettings.MIN_LEAD_DAYS,
                ReminderSettings.MAX_LEAD_DAYS,
            )
        }
    }

    suspend fun setNotifyTime(hour: Int, minute: Int) {
        dataStore.edit {
            it[Keys.notifyHour] = hour.coerceIn(0, 23)
            it[Keys.notifyMinute] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setEmailRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.emailEnabled] = enabled }
    }

    /** Restores every preference to its default. */
    suspend fun reset() {
        dataStore.edit { it.clear() }
    }
}
