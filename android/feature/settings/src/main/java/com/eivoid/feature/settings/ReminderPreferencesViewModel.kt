package com.eivoid.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eivoid.core.reminders.ReminderRepository
import com.eivoid.core.reminders.ReminderSettings
import com.eivoid.core.reminders.ReminderSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The notification half of Settings. Kept separate from [SettingsViewModel]
 * so the reminder DataStore stays an implementation detail of the feature.
 */
@HiltViewModel
class ReminderPreferencesViewModel @Inject constructor(
    private val store: ReminderSettingsStore,
    private val repository: ReminderRepository,
) : ViewModel() {

    val settings: StateFlow<ReminderSettings> = store.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReminderSettings.Default,
    )

    fun setLeadDays(days: Int) {
        viewModelScope.launch {
            store.setDefaultLeadDays(days)
            repository.applyDefaultLeadDays(days)
        }
    }

    fun setNotifyTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            store.setNotifyTime(hour, minute)
            repository.rescheduleAll()
        }
    }

    fun setEmailEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setEmailRemindersEnabled(enabled) }
    }

    /** Used by "Delete data"; alarm rows are removed by the repository. */
    fun deleteAllReminders() {
        viewModelScope.launch { runCatching { repository.deleteAll() } }
    }
}
