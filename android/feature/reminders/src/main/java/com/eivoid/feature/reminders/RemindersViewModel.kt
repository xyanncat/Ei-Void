package com.eivoid.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eivoid.core.database.ReminderEntity
import com.eivoid.core.reminders.ReminderRepository
import com.eivoid.core.reminders.ReminderSettings
import com.eivoid.core.reminders.ReminderSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the reminder screens need in one state: the local list, the
 * user's settings, permission gates and transient sync feedback. The local
 * Room list is the source of truth; sync feedback never clears the list.
 */
data class RemindersUiState(
    val reminders: List<ReminderEntity> = emptyList(),
    val settings: ReminderSettings = ReminderSettings.Default,
    val permissions: ReminderPermissions = ReminderPermissions(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
) {
    /** Unpaid rows, soonest first. */
    val active: List<ReminderEntity>
        get() = reminders.filter { it.isActive && !it.isPaid }.sortedBy { it.dueAt }

    /** Paid rows, most recently due first. */
    val paid: List<ReminderEntity>
        get() = reminders.filter { it.isPaid }.sortedByDescending { it.paidAt ?: it.dueAt }

    val next: ReminderEntity?
        get() = active.firstOrNull()
}

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val settingsStore: ReminderSettingsStore,
    private val permissionSource: ReminderPermissionSource,
) : ViewModel() {

    private val permissionState = MutableStateFlow(permissionSource.snapshot())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val content = combine(
        repository.observeAll(),
        settingsStore.settings,
    ) { reminders, settings -> reminders to settings }

    val state: StateFlow<RemindersUiState> = combine(
        content,
        permissionState,
        busy,
        message,
    ) { (reminders, settings), permissions, isBusy, notice ->
        RemindersUiState(
            reminders = reminders,
            settings = settings,
            permissions = permissions,
            loading = false,
            busy = isBusy,
            message = notice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RemindersUiState(
            settings = ReminderSettings.Default,
            permissions = permissionState.value,
        ),
    )

    /** Re-reads the runtime permission after the user returns from settings. */
    fun refreshPermissions() {
        permissionState.value = permissionSource.snapshot()
    }

    fun save(reminder: ReminderEntity) {
        viewModelScope.launch {
            runCatching { repository.save(reminder) }
                .onFailure { message.value = it.message ?: "Could not save the reminder." }
        }
    }

    fun markPaid(id: Long) {
        viewModelScope.launch {
            runCatching { repository.markPaid(id) }
                .onFailure { message.value = it.message ?: "Could not mark it paid." }
        }
    }

    fun snooze(id: Long, days: Int = 1) {
        viewModelScope.launch {
            runCatching { repository.snooze(id, days) }
                .onFailure { message.value = it.message ?: "Could not snooze the reminder." }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onFailure { message.value = it.message ?: "Could not delete the reminder." }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            runCatching { repository.deleteAll() }
                .onSuccess { message.value = "All reminders deleted." }
                .onFailure { message.value = it.message ?: "Could not delete the reminders." }
        }
    }

    /** Awaited upload with a visible result; used by the Reminders screen. */
    fun syncNow() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            val result = repository.syncNow()
            busy.value = false
            message.value = result.fold(
                onSuccess = { "Reminders synced." },
                onFailure = { it.message ?: "Sync failed. Check your connection." },
            )
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    /** Applies the preference and re-schedules rows that were not customised. */
    fun setDefaultLeadDays(days: Int) {
        viewModelScope.launch {
            settingsStore.setDefaultLeadDays(days)
            repository.applyDefaultLeadDays(days)
        }
    }

    fun setNotifyTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsStore.setNotifyTime(hour, minute)
            repository.rescheduleAll()
        }
    }

    fun setEmailRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setEmailRemindersEnabled(enabled) }
    }
}
