package com.emmanuelquintana.kora.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.DoseStatus
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.data.model.MedicationLog
import com.emmanuelquintana.kora.data.repository.DataState
import com.emmanuelquintana.kora.data.repository.valueOrNull
import com.emmanuelquintana.kora.domain.usecase.DoseSchedule
import com.emmanuelquintana.kora.domain.usecase.FieldIssue
import com.emmanuelquintana.kora.domain.usecase.FormField
import com.emmanuelquintana.kora.domain.usecase.StockCalculator
import com.emmanuelquintana.kora.ui.components.KoraEmptyState
import com.emmanuelquintana.kora.ui.components.KoraLoadingState
import com.emmanuelquintana.kora.ui.components.KoraMedicationCard
import com.emmanuelquintana.kora.ui.components.message
import com.emmanuelquintana.kora.ui.navigation.KoraActions
import com.emmanuelquintana.kora.ui.state.KoraUiState
import com.emmanuelquintana.kora.ui.state.MedicationEditorUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

/** Tomas registradas frente a programadas en los últimos 7 días (sin contar hoy). */
fun adherence(medication: Medication, logs: List<MedicationLog>, today: LocalDate): Pair<Int, Int> {
    val days = (1L..7L).map { today.minusDays(it) }
    val scheduled = days.flatMap { DoseSchedule.dosesOn(medication, it) }
    val taken = logs.count { log ->
        log.medicationId == medication.id && log.status == DoseStatus.TAKEN && log.scheduledDate in days
    }
    return taken to scheduled.size
}

@Composable
fun MedicationsList(
    state: KoraUiState,
    actions: KoraActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    showTitle: Boolean = true,
) {
    val medications = state.medications.valueOrNull().orEmpty()
    val logs = state.logs.valueOrNull().orEmpty()
    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            Column(Modifier.padding(top = Kora.spacing.md), verticalArrangement = Arrangement.spacedBy(Kora.spacing.sm)) {
                if (showTitle) {
                    Text(
                        stringResource(R.string.medications_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                FilledTonalButton(
                    onClick = { actions.openMedicationEditor(null) },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.padding(start = Kora.spacing.xs))
                    Text(stringResource(R.string.medication_add))
                }
            }
        }
        when {
            state.medications is DataState.Loading -> item(key = "loading") { KoraLoadingState(Modifier.padding(top = Kora.spacing.md)) }
            medications.isEmpty() -> item(key = "empty") {
                KoraEmptyState(stringResource(R.string.medications_empty), Modifier.padding(top = Kora.spacing.md))
            }
            else -> itemsIndexed(medications, key = { _, m -> m.id }) { index, medication ->
                Column(Modifier.animateItem()) {
                    KoraMedicationCard(
                        medication = medication,
                        stock = StockCalculator.status(medication),
                        adherence = adherence(medication, logs, state.today),
                        onClick = { actions.openMedicationEditor(medication) },
                    )
                    if (index < medications.lastIndex) HorizontalDivider(color = Kora.colors.rule)
                }
            }
        }
        item(key = "note") {
            Text(
                stringResource(R.string.medication_no_dosing_advice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Kora.spacing.lg),
            )
        }
    }
}

class MedicationFormActions(
    val onNameChange: (String) -> Unit,
    val onDoseChange: (String) -> Unit,
    val onUnitChange: (String) -> Unit,
    val onAddTime: (LocalTime) -> Unit,
    val onRemoveTime: (LocalTime) -> Unit,
    val onToggleDay: (DayOfWeek) -> Unit,
    val onStockChange: (String) -> Unit,
    val onAddStock: () -> Unit,
    val onUnitsPerDoseChange: (String) -> Unit,
    val onLowStockDaysChange: (String) -> Unit,
    val onActiveChange: (Boolean) -> Unit,
    val onSubmit: () -> Unit,
    val onDelete: (() -> Unit)?,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationEditorForm(state: MedicationEditorUiState, actions: MedicationFormActions, modifier: Modifier = Modifier) {
    val spacing = Kora.spacing
    val draft = state.draft
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    fun issue(field: FormField): FieldIssue? = state.validation[field]?.takeIf { state.showErrors }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            stringResource(if (state.isEditing) R.string.medication_edit_title else R.string.medication_new_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        TextInput(draft.name, actions.onNameChange, stringResource(R.string.medication_name), issue(FormField.MEDICATION_NAME), capitalize = true)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            TextInput(draft.dose, actions.onDoseChange, stringResource(R.string.medication_dose), issue(FormField.MEDICATION_DOSE), Modifier.weight(1f))
            TextInput(draft.unit, actions.onUnitChange, stringResource(R.string.medication_unit), issue(FormField.MEDICATION_UNIT), Modifier.weight(1f))
        }

        Text(stringResource(R.string.medication_times), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            draft.times.forEach { time ->
                val label = KoraFormats.time(time)
                InputChip(
                    selected = false,
                    onClick = { actions.onRemoveTime(time) },
                    label = { Text(label, style = Kora.numerals.valueSmall) },
                    trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.medication_remove_time, label)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.medication_add_time))
            }
        }
        issue(FormField.MEDICATION_TIMES)?.let { Text(it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Text(stringResource(R.string.medication_days), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            DayOfWeek.values().forEach { day ->
                FilterChip(
                    selected = day in draft.days,
                    onClick = { actions.onToggleDay(day) },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, KoraFormats.locale).trimEnd('.')) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        issue(FormField.MEDICATION_DAYS)?.let { Text(it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            TextInput(draft.rawStock, actions.onStockChange, stringResource(R.string.medication_stock), issue(FormField.MEDICATION_STOCK), Modifier.weight(1f), numeric = true)
            TextButton(onClick = actions.onAddStock, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.medication_add_stock)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            TextInput(draft.rawUnitsPerDose, actions.onUnitsPerDoseChange, stringResource(R.string.medication_units_per_dose), issue(FormField.MEDICATION_UNITS_PER_DOSE), Modifier.weight(1f), numeric = true)
            TextInput(draft.rawLowStockDays, actions.onLowStockDaysChange, stringResource(R.string.medication_low_stock_days), issue(FormField.MEDICATION_LOW_STOCK_DAYS), Modifier.weight(1f), numeric = true)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.medication_active), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = draft.active, onCheckedChange = actions.onActiveChange)
        }
        Text(stringResource(R.string.medication_no_dosing_advice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SaveButton(saving = state.isSaving, onClick = actions.onSubmit)
        if (actions.onDelete != null) {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(spacing.md))
    }

    if (pickingTime) {
        KoraTimePickerDialog(
            initial = draft.times.lastOrNull()?.plusHours(12) ?: LocalTime.of(8, 0),
            title = stringResource(R.string.medication_add_time),
            onConfirm = {
                pickingTime = false
                actions.onAddTime(it)
            },
            onDismiss = { pickingTime = false },
        )
    }
    if (confirmDelete && actions.onDelete != null) {
        ConfirmDialog(
            message = stringResource(R.string.confirm_delete_medication, draft.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                actions.onDelete.invoke()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    issue: FieldIssue?,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    capitalize: Boolean = false,
) {
    val supporting: (@Composable () -> Unit)? = issue?.let { { Text(it.message()) } }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = issue != null,
        supportingText = supporting,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
        ),
    )
}
