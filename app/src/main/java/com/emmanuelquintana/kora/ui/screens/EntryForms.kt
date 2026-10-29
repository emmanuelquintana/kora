package com.emmanuelquintana.kora.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.Arm
import com.emmanuelquintana.kora.data.model.GlucoseContext
import com.emmanuelquintana.kora.domain.usecase.FieldError
import com.emmanuelquintana.kora.domain.usecase.FormField
import com.emmanuelquintana.kora.ui.components.KoraNumericInput
import com.emmanuelquintana.kora.ui.components.StatusPill
import com.emmanuelquintana.kora.ui.components.labelRes
import com.emmanuelquintana.kora.ui.components.message
import com.emmanuelquintana.kora.ui.components.visual
import com.emmanuelquintana.kora.ui.state.BloodPressureEntryUiState
import com.emmanuelquintana.kora.ui.state.GlucoseEntryUiState
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Callbacks de un formulario de lectura (los implementa el ViewModel correspondiente). */
class GlucoseFormActions(
    val onValueChange: (String) -> Unit,
    val onContextChange: (GlucoseContext) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onMeasuredAtChange: (Instant?) -> Unit,
    val onSubmit: () -> Unit,
)

class PressureFormActions(
    val onSystolicChange: (String) -> Unit,
    val onDiastolicChange: (String) -> Unit,
    val onPulseChange: (String) -> Unit,
    val onArmChange: (Arm) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onMeasuredAtChange: (Instant?) -> Unit,
    val onSubmit: () -> Unit,
)

/** Un error de rango físico se muestra al instante; los demás tras intentar guardar. */
private fun showFor(showErrors: Boolean, error: FieldError?): Boolean =
    showErrors || error == FieldError.OUT_OF_RANGE || error == FieldError.NOT_A_NUMBER

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlucoseEntryForm(
    state: GlucoseEntryUiState,
    actions: GlucoseFormActions,
    modifier: Modifier = Modifier,
    valueFocus: FocusRequester = rememberFocusRequester(),
) {
    val spacing = Kora.spacing
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            stringResource(if (state.isEditing) R.string.entry_edit_glucose_title else R.string.entry_glucose_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        val issue = state.validation[FormField.GLUCOSE]
        KoraNumericInput(
            value = state.rawValue,
            onValueChange = actions.onValueChange,
            label = stringResource(R.string.field_value),
            unit = stringResource(R.string.unit_mg_dl),
            issue = issue,
            showError = showFor(state.showErrors, issue?.error),
            imeAction = ImeAction.Done,
            onImeAction = actions.onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(valueFocus),
        )
        state.category?.let { category ->
            val visual = category.visual()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                StatusPill(visual)
            }
        }
        Text(stringResource(R.string.field_context), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            GlucoseContext.entries.forEach { context ->
                FilterChip(
                    selected = state.context == context,
                    onClick = { actions.onContextChange(context) },
                    label = { Text(stringResource(context.labelRes())) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        MeasuredAtField(state.measuredAt, state.validation[FormField.MEASURED_AT]?.message()?.takeIf { state.showErrors }, actions.onMeasuredAtChange)
        NoteField(state.note, actions.onNoteChange)
        SaveButton(saving = state.isSaving, onClick = actions.onSubmit)
    }
}

@Composable
fun PressureEntryForm(
    state: BloodPressureEntryUiState,
    actions: PressureFormActions,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    showNoteAndTime: Boolean = true,
    saveLabel: String = stringResource(R.string.save),
    systolicFocus: FocusRequester = rememberFocusRequester(),
) {
    val spacing = Kora.spacing
    val diastolicFocus = rememberFocusRequester()
    val pulseFocus = rememberFocusRequester()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        if (showTitle) {
            Text(
                stringResource(if (state.isEditing) R.string.entry_edit_pressure_title else R.string.entry_pressure_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        val systolicIssue = state.validation[FormField.SYSTOLIC]
        val diastolicIssue = state.validation[FormField.DIASTOLIC]
        val pulseIssue = state.validation[FormField.PULSE]
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            KoraNumericInput(
                value = state.rawSystolic,
                onValueChange = actions.onSystolicChange,
                label = stringResource(R.string.field_systolic),
                unit = stringResource(R.string.unit_mmhg),
                issue = systolicIssue,
                showError = showFor(state.showErrors, systolicIssue?.error),
                onImeAction = { diastolicFocus.requestFocus() },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(systolicFocus),
            )
            KoraNumericInput(
                value = state.rawDiastolic,
                onValueChange = actions.onDiastolicChange,
                label = stringResource(R.string.field_diastolic),
                unit = stringResource(R.string.unit_mmhg),
                issue = diastolicIssue,
                showError = showFor(state.showErrors, diastolicIssue?.error),
                onImeAction = { pulseFocus.requestFocus() },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(diastolicFocus),
            )
        }
        KoraNumericInput(
            value = state.rawPulse,
            onValueChange = actions.onPulseChange,
            label = stringResource(R.string.field_pulse_optional),
            unit = stringResource(R.string.unit_bpm),
            issue = pulseIssue,
            showError = showFor(state.showErrors, pulseIssue?.error),
            imeAction = ImeAction.Done,
            onImeAction = actions.onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(pulseFocus),
        )
        state.assessment?.let { assessment -> StatusPill(assessment.category.visual()) }
        Text(stringResource(R.string.field_arm), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Arm.entries.forEach { arm ->
                FilterChip(
                    selected = state.arm == arm,
                    onClick = { actions.onArmChange(arm) },
                    label = { Text(stringResource(arm.labelRes())) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (showNoteAndTime) {
            MeasuredAtField(state.measuredAt, state.validation[FormField.MEASURED_AT]?.message()?.takeIf { state.showErrors }, actions.onMeasuredAtChange)
            NoteField(state.note, actions.onNoteChange)
        }
        SaveButton(saving = state.isSaving, onClick = actions.onSubmit, label = saveLabel)
    }
}

/** "Ahora" por defecto; se puede elegir otra hora de hoy. */
@Composable
private fun MeasuredAtField(measuredAt: Instant?, error: String?, onChange: (Instant?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.field_time), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = measuredAt?.let { KoraFormats.time(it, zone) } ?: stringResource(R.string.time_now),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (measuredAt != null) {
            TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.time_now)) }
        }
        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.time_change)) }
    }
    if (picking) {
        val initial = measuredAt?.atZone(zone)?.toLocalTime() ?: LocalTime.now(zone)
        KoraTimePickerDialog(
            initial = initial,
            title = stringResource(R.string.field_time),
            onConfirm = { time ->
                picking = false
                val base = measuredAt?.atZone(zone)?.toLocalDate() ?: java.time.LocalDate.now(zone)
                onChange(ZonedDateTime.of(base, time, zone).toInstant())
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun NoteField(note: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = note,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.field_note)) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        maxLines = 3,
    )
}

@Composable
fun SaveButton(saving: Boolean, onClick: () -> Unit, label: String = stringResource(R.string.save)) {
    Button(
        onClick = onClick,
        enabled = !saving,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Text(if (saving) stringResource(R.string.saving) else label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun rememberFocusRequester(): FocusRequester = androidx.compose.runtime.remember { FocusRequester() }
