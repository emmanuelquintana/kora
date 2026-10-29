package com.emmanuelquintana.kora.ui.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emmanuelquintana.kora.AppContainer
import com.emmanuelquintana.kora.core.util.KoraError
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.core.util.KoraResult
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.domain.usecase.MeasurementValidator
import com.emmanuelquintana.kora.domain.usecase.ValidationResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

data class MedicationDraft(
    val name: String = "",
    val dose: String = "",
    val unit: String = "mg",
    val times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val days: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    val rawStock: String = "",
    val rawUnitsPerDose: String = "1",
    val rawLowStockDays: String = "7",
    val active: Boolean = true,
)

data class MedicationEditorUiState(
    val draft: MedicationDraft = MedicationDraft(),
    /** ID del medicamento en edición; null al crear uno nuevo. */
    val editingId: String? = null,
    val validation: ValidationResult = ValidationResult(emptyMap()),
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
) {
    val isEditing: Boolean get() = editingId != null
}

/** Alta y edición de medicamentos. El borrador sobrevive a cambios de postura. */
class MedicationEditorViewModel(
    private val handle: SavedStateHandle,
    private val container: AppContainer,
) : ViewModel() {

    private val saving = MutableStateFlow(false)
    private val _saved = MutableSharedFlow<Medication>(extraBufferCapacity = 1)
    val saved: SharedFlow<Medication> = _saved.asSharedFlow()
    private val _errors = MutableSharedFlow<KoraError>(extraBufferCapacity = 1)
    val errors: SharedFlow<KoraError> = _errors.asSharedFlow()

    val uiState: StateFlow<MedicationEditorUiState> = combine(
        combine(
            handle.getStateFlow(KEY_NAME, ""),
            handle.getStateFlow(KEY_DOSE, ""),
            handle.getStateFlow(KEY_UNIT, "mg"),
        ) { name, dose, unit -> Triple(name, dose, unit) },
        combine(
            handle.getStateFlow(KEY_TIMES, "08:00"),
            handle.getStateFlow(KEY_DAYS, ALL_DAYS),
            handle.getStateFlow(KEY_ACTIVE, true),
        ) { times, days, active -> Triple(times, days, active) },
        combine(
            handle.getStateFlow(KEY_STOCK, ""),
            handle.getStateFlow(KEY_UNITS_PER_DOSE, "1"),
            handle.getStateFlow(KEY_LOW_STOCK_DAYS, "7"),
        ) { stock, perDose, lowDays -> Triple(stock, perDose, lowDays) },
        handle.getStateFlow(KEY_SHOW_ERRORS, false),
        saving,
    ) { (name, dose, unit), (times, days, active), (stock, perDose, lowDays), showErrors, isSaving ->
        val draft = MedicationDraft(
            name = name,
            dose = dose,
            unit = unit,
            times = decodeTimes(times),
            days = decodeDays(days),
            rawStock = stock,
            rawUnitsPerDose = perDose,
            rawLowStockDays = lowDays,
            active = active,
        )
        MedicationEditorUiState(
            draft = draft,
            editingId = handle.get<String>(KEY_EDITING_ID),
            validation = MeasurementValidator.validateMedication(
                draft.name, draft.dose, draft.unit, draft.times, draft.days,
                draft.rawStock, draft.rawUnitsPerDose, draft.rawLowStockDays,
            ),
            showErrors = showErrors,
            isSaving = isSaving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicationEditorUiState())

    fun onNameChange(value: String) { handle[KEY_NAME] = value.take(MeasurementValidator.MEDICATION_NAME_MAX_LENGTH) }
    fun onDoseChange(value: String) { handle[KEY_DOSE] = value.take(MeasurementValidator.MEDICATION_FIELD_MAX_LENGTH) }
    fun onUnitChange(value: String) { handle[KEY_UNIT] = value.take(MeasurementValidator.MEDICATION_FIELD_MAX_LENGTH) }
    fun onStockChange(value: String) { handle[KEY_STOCK] = value.filter(Char::isDigit).take(4) }
    fun onUnitsPerDoseChange(value: String) { handle[KEY_UNITS_PER_DOSE] = value.filter(Char::isDigit).take(2) }
    fun onLowStockDaysChange(value: String) { handle[KEY_LOW_STOCK_DAYS] = value.filter(Char::isDigit).take(2) }
    fun onActiveChange(active: Boolean) { handle[KEY_ACTIVE] = active }

    fun addTime(time: LocalTime) {
        val times = (uiState.value.draft.times + time.withSecond(0).withNano(0)).distinct().sorted()
        handle[KEY_TIMES] = encodeTimes(times)
    }

    fun removeTime(time: LocalTime) {
        handle[KEY_TIMES] = encodeTimes(uiState.value.draft.times - time)
    }

    fun toggleDay(day: DayOfWeek) {
        val days = uiState.value.draft.days
        handle[KEY_DAYS] = encodeDays(if (day in days) days - day else days + day)
    }

    /** Agregar existencias (p. ej. compra de una caja nueva). */
    fun addStock(units: Int) {
        val current = MeasurementValidator.parseNumber(uiState.value.draft.rawStock) ?: 0
        handle[KEY_STOCK] = (current + units).coerceIn(MeasurementValidator.STOCK_LIMITS).toString()
    }

    fun edit(medication: Medication) {
        handle[KEY_EDITING_ID] = medication.id
        handle[KEY_CREATED_AT] = medication.createdAt.toEpochMilli()
        handle[KEY_NAME] = medication.name
        handle[KEY_DOSE] = medication.dose
        handle[KEY_UNIT] = medication.unit
        handle[KEY_TIMES] = encodeTimes(medication.times)
        handle[KEY_DAYS] = encodeDays(medication.daysOfWeek)
        handle[KEY_STOCK] = medication.stockUnits.coerceAtLeast(0).toString()
        handle[KEY_UNITS_PER_DOSE] = medication.unitsPerDose.toString()
        handle[KEY_LOW_STOCK_DAYS] = medication.lowStockDays.toString()
        handle[KEY_ACTIVE] = medication.active
        handle[KEY_SHOW_ERRORS] = false
    }

    fun submit() {
        if (saving.value) return
        val state = uiState.value
        if (!state.validation.isValid) {
            handle[KEY_SHOW_ERRORS] = true
            return
        }
        val draft = state.draft
        viewModelScope.launch {
            val repository = container.session.currentRepository()
            if (repository == null) {
                _errors.emit(KoraError.SESSION_EXPIRED)
                return@launch
            }
            saving.value = true
            val id = handle.get<String>(KEY_EDITING_ID)
                ?: handle.get<String>(KEY_SUBMISSION_ID)
                ?: repository.newId().also { handle[KEY_SUBMISSION_ID] = it }
            val medication = Medication(
                id = id,
                name = draft.name.trim(),
                dose = draft.dose.trim(),
                unit = draft.unit.trim(),
                times = draft.times,
                daysOfWeek = draft.days,
                stockUnits = MeasurementValidator.parseNumber(draft.rawStock) ?: 0,
                unitsPerDose = MeasurementValidator.parseNumber(draft.rawUnitsPerDose) ?: 1,
                lowStockDays = MeasurementValidator.parseNumber(draft.rawLowStockDays) ?: 7,
                active = draft.active,
                createdAt = handle.get<Long>(KEY_CREATED_AT)?.let(Instant::ofEpochMilli) ?: container.clock.now(),
            )
            when (val result = repository.saveMedication(medication)) {
                is KoraResult.Success -> {
                    reset()
                    _saved.emit(medication)
                }
                is KoraResult.Failure -> _errors.emit(result.error)
            }
            saving.value = false
        }
    }

    fun reset() {
        listOf(
            KEY_NAME, KEY_DOSE, KEY_UNIT, KEY_TIMES, KEY_DAYS, KEY_STOCK, KEY_UNITS_PER_DOSE,
            KEY_LOW_STOCK_DAYS, KEY_ACTIVE, KEY_EDITING_ID, KEY_SUBMISSION_ID, KEY_CREATED_AT, KEY_SHOW_ERRORS,
        ).forEach { handle.remove<Any>(it) }
    }

    private companion object {
        const val KEY_NAME = "med_name"
        const val KEY_DOSE = "med_dose"
        const val KEY_UNIT = "med_unit"
        const val KEY_TIMES = "med_times"
        const val KEY_DAYS = "med_days"
        const val KEY_STOCK = "med_stock"
        const val KEY_UNITS_PER_DOSE = "med_units_per_dose"
        const val KEY_LOW_STOCK_DAYS = "med_low_stock_days"
        const val KEY_ACTIVE = "med_active"
        const val KEY_EDITING_ID = "med_editing_id"
        const val KEY_SUBMISSION_ID = "med_submission_id"
        const val KEY_CREATED_AT = "med_created_at"
        const val KEY_SHOW_ERRORS = "med_show_errors"
        const val ALL_DAYS = "1,2,3,4,5,6,7"

        fun encodeTimes(times: List<LocalTime>) = times.sorted().joinToString(",", transform = KoraFormats::storageTime)

        fun decodeTimes(raw: String) =
            raw.split(',').mapNotNull { KoraFormats.parseStorageTime(it.trim()) }.distinct().sorted()

        fun encodeDays(days: Set<DayOfWeek>) = days.map { it.value }.sorted().joinToString(",")

        fun decodeDays(raw: String) =
            raw.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { d -> d in 1..7 }?.let(DayOfWeek::of) }.toSet()
    }
}
