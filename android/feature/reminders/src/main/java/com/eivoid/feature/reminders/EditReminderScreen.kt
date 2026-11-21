package com.eivoid.feature.reminders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.core.database.Category
import com.eivoid.core.database.RepeatRule
import com.eivoid.designsystem.component.VoidCard
import com.eivoid.designsystem.component.VoidTopBar
import com.eivoid.designsystem.component.reminderAccent

private val AMOUNT_PATTERN = Regex("\\d{0,9}(\\.\\d{0,2})?")

/**
 * One form for new and existing reminders. Validation lives in the ViewModel
 * so it also runs in unit tests; the screen only surfaces the error string.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditReminderScreen(
    reminderId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: EditReminderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = reminderAccent()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
        }
        return
    }

    val draft = state.draft
    val isNew = reminderId == 0L

    Column(modifier = Modifier.fillMaxSize()) {
        VoidTopBar(
            title = if (isNew) "New reminder" else "Edit reminder",
            subtitle = if (isNew) "Add a payment to track" else "Update this payment",
            onBack = onBack,
            actions = {
                TextButton(
                    onClick = viewModel::save,
                    enabled = !state.saving,
                ) {
                    Text(
                        text = if (state.saving) "Saving…" else "Save",
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { value -> viewModel.update { it.copy(title = value) } },
                    label = { Text("Name") },
                    placeholder = { Text("Netflix, electricity bill, rent…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedTextField(
                    value = draft.merchant,
                    onValueChange = { value -> viewModel.update { it.copy(merchant = value) } },
                    label = { Text("Merchant or biller") },
                    placeholder = { Text("To whom the payment goes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedTextField(
                    value = draft.bank,
                    onValueChange = { value -> viewModel.update { it.copy(bank = value) } },
                    label = { Text("Bank (optional)") },
                    placeholder = { Text("Bank or payment account") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draft.amountText,
                        onValueChange = { value ->
                            if (value.isEmpty() || AMOUNT_PATTERN.matches(value)) {
                                viewModel.update { it.copy(amountText = value) }
                            }
                        },
                        label = { Text("Amount") },
                        prefix = { Text(ReminderFormat.currencySymbol(draft.currency)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        EditReminderViewModel.SUPPORTED_CURRENCIES.forEach { currency ->
                            FilterChip(
                                selected = draft.currency == currency,
                                onClick = { viewModel.setCurrency(currency) },
                                shape = RoundedCornerShape(50),
                                label = { Text(ReminderFormat.currencySymbol(currency)) },
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerTile(
                        label = "Due date",
                        value = ReminderFormat.weekdayDate(draft.dueAt),
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                    )
                    PickerTile(
                        label = "Remind at",
                        value = ReminderFormat.time(draft.dueAt),
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                FieldLabel("Repeat")
                ChoiceChips(
                    options = RepeatRule.entries,
                    selected = draft.repeatRule,
                    label = { it.label },
                    onSelect = { value -> viewModel.update { it.copy(repeatRule = value) } },
                )
            }

            item {
                FieldLabel("Category")
                ChoiceChips(
                    options = Category.entries,
                    selected = draft.category,
                    label = { it.label },
                    onSelect = { value -> viewModel.update { it.copy(category = value) } },
                )
            }

            item {
                FieldLabel("Remind me")
                val options = remember(draft.leadDays) {
                    (listOf(0, 1, 2, 3, 7) + draft.leadDays).distinct().sorted()
                }
                ChoiceChips(
                    options = options,
                    selected = draft.leadDays,
                    label = { days ->
                        when (days) {
                            0 -> "On the day"
                            1 -> "1 day before"
                            else -> "$days days before"
                        }
                    },
                    onSelect = viewModel::setLeadDays,
                )
            }

            item {
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { value -> viewModel.update { it.copy(notes = value) } },
                    label = { Text("Notes") },
                    placeholder = { Text("Plan name or reference. Do not store card numbers.") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let { error ->
                item {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (!isNew) {
                item {
                    OutlinedButton(
                        onClick = viewModel::delete,
                        enabled = !state.saving,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                        ),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Delete this reminder")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = ReminderFormat.toPickerMillis(draft.dueAt),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let(viewModel::setDate)
                        showDatePicker = false
                    },
                ) { Text("Set date") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val hour = java.time.Instant.ofEpochMilli(draft.dueAt)
            .atZone(java.time.ZoneId.systemDefault())
            .hour
        val minute = java.time.Instant.ofEpochMilli(draft.dueAt)
            .atZone(java.time.ZoneId.systemDefault())
            .minute
        val timeState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Remind at") },
            text = { TimeInput(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setTime(timeState.hour, timeState.minute)
                        showTimePicker = false
                    },
                ) { Text("Set time") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
    )
}

@Composable
private fun PickerTile(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = value, style = MaterialTheme.typography.titleSmall)
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = RoundedCornerShape(50),
                label = { Text(label(option)) },
            )
        }
    }
}
