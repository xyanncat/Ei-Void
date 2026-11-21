package com.eivoid.feature.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eivoid.core.database.Category
import com.eivoid.core.database.ReminderEntity
import com.eivoid.core.database.RepeatRule
import com.eivoid.core.reminders.ReminderRepository
import com.eivoid.core.reminders.ReminderSettings
import com.eivoid.core.reminders.ReminderSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReminderDraft(
    val id: Long = 0L,
    val title: String = "",
    val merchant: String = "",
    val bank: String = "",
    val amountText: String = "",
    val currency: String = "INR",
    val dueAt: Long = System.currentTimeMillis(),
    val repeatRule: RepeatRule = RepeatRule.NONE,
    val category: Category = Category.BILL,
    val notes: String = "",
    val leadDays: Int = ReminderSettings.DEFAULT_LEAD_DAYS,
    val leadDaysCustomized: Boolean = false,
)

data class EditReminderUiState(
    val draft: ReminderDraft = ReminderDraft(),
    val isNew: Boolean = true,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
)

/**
 * Owns one reminder form. [reminderId] arrives from the navigation argument;
 * 0 means a new reminder, which is prefilled with the user's default lead time
 * and tomorrow's notification time.
 */
@HiltViewModel
class EditReminderViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val settingsStore: ReminderSettingsStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val reminderId: Long = savedStateHandle.get<Long>(ARG_ID) ?: 0L
    private var original: ReminderEntity? = null

    private val _state = MutableStateFlow(EditReminderUiState())
    val state: StateFlow<EditReminderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsStore.current()
            val existing = if (reminderId > 0L) repository.get(reminderId) else null
            original = existing
            val draft = existing?.toDraft() ?: ReminderDraft(
                dueAt = ReminderFormat.defaultDueAt(settings.notifyHour, settings.notifyMinute),
                leadDays = settings.defaultLeadDays,
            )
            _state.value = EditReminderUiState(
                draft = draft,
                isNew = reminderId == 0L,
                loading = false,
                error = if (reminderId > 0L && existing == null) {
                    "This reminder no longer exists on this device."
                } else {
                    null
                },
            )
        }
    }

    fun update(transform: (ReminderDraft) -> ReminderDraft) {
        _state.update { it.copy(draft = transform(it.draft), error = null) }
    }

    fun setCurrency(currency: String) {
        if (currency !in SUPPORTED_CURRENCIES) return
        update { it.copy(currency = currency) }
    }

    fun setLeadDays(days: Int) {
        update {
            it.copy(
                leadDays = days.coerceIn(ReminderSettings.MIN_LEAD_DAYS, ReminderSettings.MAX_LEAD_DAYS),
                leadDaysCustomized = true,
            )
        }
    }

    fun setDate(pickedDateMillis: Long) {
        update { it.copy(dueAt = ReminderFormat.withDate(it.dueAt, pickedDateMillis)) }
    }

    fun setTime(hour: Int, minute: Int) {
        update { it.copy(dueAt = ReminderFormat.withTime(it.dueAt, hour, minute)) }
    }

    fun save() {
        val current = _state.value
        if (current.loading || current.saving) return
        val draft = current.draft
        val validation = validate(draft)
        if (validation != null) {
            _state.update { it.copy(error = validation) }
            return
        }
        val amount = parseAmount(draft.amountText) ?: return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val entity = toEntity(draft, amount)
            runCatching { repository.save(entity) }
                .onSuccess {
                    _state.update { it.copy(saving = false, finished = true) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            saving = false,
                            error = error.message ?: "Could not save the reminder.",
                        )
                    }
                }
        }
    }

    fun delete() {
        if (reminderId <= 0L) return
        val current = _state.value
        if (current.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.delete(reminderId) }
                .onSuccess { _state.update { it.copy(saving = false, finished = true) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            saving = false,
                            error = error.message ?: "Could not delete the reminder.",
                        )
                    }
                }
        }
    }

    private fun toEntity(draft: ReminderDraft, amountMinor: Long): ReminderEntity {
        val base = original
        return ReminderEntity(
            id = if (draft.id > 0L) draft.id else 0L,
            title = draft.title.trim(),
            merchant = draft.merchant.trim(),
            bank = draft.bank.trim(),
            amountMinor = amountMinor,
            currency = draft.currency,
            dueAt = draft.dueAt,
            repeatRule = draft.repeatRule,
            category = draft.category,
            notes = draft.notes.trim(),
            leadDays = draft.leadDays,
            leadDaysCustomized = draft.leadDaysCustomized,
            isPaid = base?.isPaid ?: false,
            isActive = base?.isActive ?: true,
            createdAt = base?.createdAt ?: System.currentTimeMillis(),
            paidAt = base?.paidAt,
            notifiedForDueAt = base?.notifiedForDueAt ?: 0L,
        )
    }

    internal fun validate(draft: ReminderDraft): String? = when {
        draft.title.isBlank() -> "Add a name for this payment."
        draft.merchant.isBlank() -> "Add the merchant or biller."
        parseAmount(draft.amountText) == null -> "Enter an amount greater than zero."
        draft.notes.length > MAX_NOTES -> "Notes are limited to $MAX_NOTES characters."
        else -> null
    }

    internal fun parseAmount(text: String): Long? {
        val value = text.trim().toBigDecimalOrNull() ?: return null
        if (value <= BigDecimal.ZERO) return null
        return runCatching {
            value.multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrNull()?.takeIf { it > 0L }
    }

    private fun ReminderEntity.toDraft(): ReminderDraft = ReminderDraft(
        id = id,
        title = title,
        merchant = merchant,
        bank = bank,
        amountText = amountToText(amountMinor),
        currency = currency,
        dueAt = dueAt,
        repeatRule = repeatRule,
        category = category,
        notes = notes,
        leadDays = leadDays,
        leadDaysCustomized = leadDaysCustomized,
    )

    private fun amountToText(minor: Long): String {
        val whole = minor / 100L
        val cents = (minor % 100L).toInt()
        return if (cents == 0) whole.toString() else "$whole.${cents.toString().padStart(2, '0')}"
    }

    companion object {
        const val ARG_ID = "id"
        const val MAX_NOTES = 500
        val SUPPORTED_CURRENCIES = listOf("INR", "USD")
    }
}
