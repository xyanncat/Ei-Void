package com.eivoid.feature.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.core.database.Category
import com.eivoid.core.database.RepeatRule
import com.eivoid.core.reminders.ReminderSettings
import com.eivoid.designsystem.component.EiCard
import com.eivoid.designsystem.component.EiDivider
import com.eivoid.designsystem.component.EiNotice
import com.eivoid.designsystem.component.EiPill
import com.eivoid.designsystem.component.EiPrimaryButton
import com.eivoid.designsystem.component.EiScreen
import com.eivoid.designsystem.component.EiSecondaryButton
import com.eivoid.designsystem.component.EiSectionLabel
import com.eivoid.designsystem.component.EiSegmented
import com.eivoid.designsystem.component.EiTextField
import com.eivoid.designsystem.component.EiTopBar
import com.eivoid.designsystem.theme.VoidColors

private val EDITOR_AMOUNT_PATTERN = Regex("\\d{0,9}(\\.\\d{0,2})?")

/**
 * The reminder editor, rebuilt on the Ei OLED kit.
 *
 * A single generous form: name, merchant, amount, due date and time, repeat,
 * category, lead time and notes. Validation and saving stay in the ViewModel;
 * this screen only surfaces the error and the progress state.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderEditorScreen(
    reminderId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditReminderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    val isNew = if (state.loading) reminderId == 0L else state.isNew

    EiScreen(modifier = modifier) {
        EiTopBar(
            title = if (isNew) "New reminder" else "Edit reminder",
            subtitle = if (isNew) "Add a payment to track" else "Update this payment",
            onBack = onBack,
        )

        if (state.loading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = VoidColors.oledAccentBlue,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            val draft = state.draft

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 14.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(key = "title") {
                    TitleField(
                        value = draft.title,
                        onValueChange = { value ->
                            viewModel.update { it.copy(title = value) }
                        },
                    )
                }

                item(key = "merchant") {
                    EiTextField(
                        value = draft.merchant,
                        onValueChange = { value ->
                            viewModel.update { it.copy(merchant = value) }
                        },
                        label = "Merchant or biller",
                        placeholder = "To whom the payment goes",
                    )
                }

                item(key = "amount") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EiTextField(
                            value = draft.amountText,
                            onValueChange = { value ->
                                if (value.isEmpty() || EDITOR_AMOUNT_PATTERN.matches(value)) {
                                    viewModel.update { it.copy(amountText = value) }
                                }
                            },
                            label = "Amount",
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        EiSegmented(
                            options = EditReminderViewModel.SUPPORTED_CURRENCIES,
                            selected = draft.currency,
                            onSelect = viewModel::setCurrency,
                            label = { currency -> currency },
                            modifier = Modifier.width(132.dp),
                        )
                    }
                }

                item(key = "when") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PickerTile(
                            label = "Due date",
                            value = ReminderFormat.weekdayDate(draft.dueAt),
                            icon = Icons.Filled.DateRange,
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f),
                        )
                        PickerTile(
                            label = "Remind at",
                            value = ReminderFormat.time(draft.dueAt),
                            icon = Icons.Filled.Notifications,
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item(key = "repeat") {
                    Column {
                        EiSectionLabel("Repeat")
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RepeatRule.entries.forEach { rule ->
                                EiPill(
                                    text = rule.label,
                                    selected = draft.repeatRule == rule,
                                    onClick = { viewModel.update { it.copy(repeatRule = rule) } },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                }

                item(key = "category") {
                    Column {
                        EiSectionLabel("Category")
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Category.entries.forEach { category ->
                                EiPill(
                                    text = category.label,
                                    selected = draft.category == category,
                                    onClick = { viewModel.update { it.copy(category = category) } },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                }

                item(key = "lead") {
                    Column {
                        EiSectionLabel("Remind me")
                        Spacer(Modifier.height(8.dp))
                        EiCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = leadDaysLabel(draft.leadDays),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = VoidColors.oledTextPrimary,
                                    )
                                    Text(
                                        text = if (draft.leadDays == 0) {
                                            "A notification on the due date"
                                        } else {
                                            "A notification before it is due"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VoidColors.oledTextSecondary,
                                    )
                                }
                                val canDecrease = draft.leadDays > ReminderSettings.MIN_LEAD_DAYS
                                val canIncrease = draft.leadDays < ReminderSettings.MAX_LEAD_DAYS
                                StepButton(
                                    contentDescription = "Fewer lead days",
                                    enabled = canDecrease,
                                    onClick = { viewModel.setLeadDays(draft.leadDays - 1) },
                                ) {
                                    Text(
                                        text = "−",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (canDecrease) {
                                            VoidColors.oledTextPrimary
                                        } else {
                                            VoidColors.oledTextSecondary.copy(alpha = 0.4f)
                                        },
                                        modifier = Modifier.clearAndSetSemantics { },
                                    )
                                }
                                Text(
                                    text = draft.leadDays.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = VoidColors.oledTextPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(36.dp),
                                )
                                StepButton(
                                    contentDescription = "More lead days",
                                    enabled = canIncrease,
                                    onClick = { viewModel.setLeadDays(draft.leadDays + 1) },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = if (canIncrease) {
                                            VoidColors.oledTextPrimary
                                        } else {
                                            VoidColors.oledTextSecondary.copy(alpha = 0.4f)
                                        },
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "notes") {
                    EiTextField(
                        value = draft.notes,
                        onValueChange = { value ->
                            viewModel.update { it.copy(notes = value) }
                        },
                        label = "Notes",
                        placeholder = "Reference or plan name. Do not store card numbers.",
                        singleLine = false,
                        minLines = 4,
                        maxLines = 6,
                    )
                }

                state.error?.let { error ->
                    item(key = "error") {
                        EiNotice(message = error, danger = true)
                    }
                }

                if (!state.isNew) {
                    item(key = "delete") {
                        EiSecondaryButton(
                            text = "Delete reminder",
                            onClick = { confirmDelete = true },
                            enabled = !state.saving,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                EiPrimaryButton(
                    text = "Save reminder",
                    onClick = viewModel::save,
                    enabled = !state.saving,
                    loading = state.saving,
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = ReminderFormat.toPickerMillis(state.draft.dueAt),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let(viewModel::setDate)
                        showDatePicker = false
                    },
                ) {
                    Text("Set date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val due = java.time.Instant.ofEpochMilli(state.draft.dueAt)
            .atZone(java.time.ZoneId.systemDefault())
        val timeState = rememberTimePickerState(
            initialHour = due.hour,
            initialMinute = due.minute,
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
                ) {
                    Text("Set time")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this reminder?") },
            text = {
                Text(
                    "This removes it from this phone and cancels its alarm. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) {
                    Text("Delete", color = VoidColors.oledDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun TitleField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        EiSectionLabel("Name")
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                color = VoidColors.oledTextPrimary,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(VoidColors.oledAccentBlue),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .semantics { contentDescription = "Reminder name" },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Netflix, electricity, rent…",
                            style = MaterialTheme.typography.headlineSmall,
                            color = VoidColors.oledTextSecondary,
                        )
                    }
                    innerTextField()
                }
            },
        )
        EiDivider(modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun PickerTile(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EiCard(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label, $value"
        },
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VoidColors.oledAccentBlue,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = VoidColors.oledTextSecondary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = VoidColors.oledTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StepButton(
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(VoidColors.oledSurfaceHigh)
            .border(1.dp, VoidColors.oledBorder, CircleShape)
            .clickable(
                enabled = enabled,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun leadDaysLabel(days: Int): String = when (days) {
    0 -> "On the due date"
    1 -> "1 day before"
    else -> "$days days before"
}
