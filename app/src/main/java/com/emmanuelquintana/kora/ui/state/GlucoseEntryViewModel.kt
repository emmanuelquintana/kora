package com.emmanuelquintana.kora.ui.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emmanuelquintana.kora.AppContainer
import com.emmanuelquintana.kora.core.util.KoraError
import com.emmanuelquintana.kora.core.util.KoraResult
import com.emmanuelquintana.kora.data.model.GlucoseContext
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.domain.usecase.GlucoseCategory
import com.emmanuelquintana.kora.domain.usecase.GlucoseClassifier
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
import java.time.Instant
import java.time.LocalTime

data class GlucoseEntryUiState(
    val rawValue: String = "",
    val context: GlucoseContext = GlucoseContext.FASTING,
    val note: String = "",
    /** null = "ahora" al guardar. */
    val measuredAt: Instant? = null,
    val isEditing: Boolean = false,
    val validation: ValidationResult = ValidationResult(emptyMap()),
    /** Los errores se muestran tras el primer intento de guardar (o al salir de rango). */
    val showErrors: Boolean = false,
    /** Vista previa de la categoría mientras se escribe. */
    val category: GlucoseCategory? = null,
    val isSaving: Boolean = false,
)

/**
 * Borrador del registro de glucosa. Vive en SavedStateHandle: sobrevive a
 * rotación, plegado y muerte del proceso. Cada borrador tiene un ID de envío
 * fijo, así que tocar "Guardar" dos veces escribe el mismo documento.
 */
class GlucoseEntryViewModel(
    private val handle: SavedStateHandle,
    private val container: AppContainer,
) : ViewModel() {

    private val clock = container.clock
    private val saving = MutableStateFlow(false)
    private val _saved = MutableSharedFlow<GlucoseReading>(extraBufferCapacity = 1)
    val saved: SharedFlow<GlucoseReading> = _saved.asSharedFlow()
    private val _errors = MutableSharedFlow<KoraError>(extraBufferCapacity = 1)
    val errors: SharedFlow<KoraError> = _errors.asSharedFlow()

    init {
        if (!handle.contains(KEY_CONTEXT)) handle[KEY_CONTEXT] = suggestedContext().name
        ensureSubmissionId()
    }

    val uiState: StateFlow<GlucoseEntryUiState> = combine(
        handle.getStateFlow(KEY_VALUE, ""),
        handle.getStateFlow(KEY_CONTEXT, GlucoseContext.FASTING.name),
        handle.getStateFlow(KEY_NOTE, ""),
        handle.getStateFlow(KEY_MEASURED_AT, NO_TIME),
        combine(handle.getStateFlow(KEY_SHOW_ERRORS, false), saving, container.preferences.ranges) { a, b, c -> Triple(a, b, c) },
    ) { raw, context, note, measuredAt, (showErrors, isSaving, ranges) ->
        val at = measuredAt.takeIf { it != NO_TIME }?.let(Instant::ofEpochMilli)
        val validation = MeasurementValidator.validateGlucose(raw, at ?: clock.now(), note.ifBlank { null }, clock.now())
        GlucoseEntryUiState(
            rawValue = raw,
            context = runCatching { GlucoseContext.valueOf(context) }.getOrDefault(GlucoseContext.FASTING),
            note = note,
            measuredAt = at,
            isEditing = handle.get<String>(KEY_EDITING_ID) != null,
            validation = validation,
            showErrors = showErrors,
            category = MeasurementValidator.parseNumber(raw)
                ?.takeIf { it in MeasurementValidator.GLUCOSE_LIMITS }
                ?.let { GlucoseClassifier.classify(it, ranges) },
            isSaving = isSaving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlucoseEntryUiState())

    fun onValueChange(raw: String) {
        handle[KEY_VALUE] = raw.filter(Char::isDigit).take(3)
    }

    fun onContextChange(context: GlucoseContext) {
        handle[KEY_CONTEXT] = context.name
    }

    fun onNoteChange(note: String) {
        handle[KEY_NOTE] = note.take(MeasurementValidator.NOTE_MAX_LENGTH)
    }

    fun onMeasuredAtChange(at: Instant?) {
        handle[KEY_MEASURED_AT] = at?.toEpochMilli() ?: NO_TIME
    }

    /** Carga una lectura existente para editarla (conserva su ID). */
    fun edit(reading: GlucoseReading) {
        handle[KEY_EDITING_ID] = reading.id
        handle[KEY_SUBMISSION_ID] = reading.id
        handle[KEY_CREATED_AT] = reading.createdAt.toEpochMilli()
        handle[KEY_VALUE] = reading.valueMgDl.toString()
        handle[KEY_CONTEXT] = reading.context.name
        handle[KEY_NOTE] = reading.note.orEmpty()
        handle[KEY_MEASURED_AT] = reading.measuredAt.toEpochMilli()
        handle[KEY_SHOW_ERRORS] = false
    }

    fun submit() {
        if (saving.value) return
        val state = uiState.value
        if (!state.validation.isValid) {
            handle[KEY_SHOW_ERRORS] = true
            return
        }
        val value = MeasurementValidator.parseNumber(state.rawValue) ?: return
        viewModelScope.launch {
            val repository = container.session.currentRepository()
            if (repository == null) {
                _errors.emit(KoraError.SESSION_EXPIRED)
                return@launch
            }
            saving.value = true
            val id = ensureSubmissionId(repository.newId())
            val now = clock.now()
            val reading = GlucoseReading(
                id = id,
                valueMgDl = value,
                context = state.context,
                measuredAt = state.measuredAt ?: now,
                note = state.note.trim().ifBlank { null },
                createdAt = handle.get<Long>(KEY_CREATED_AT)?.let(Instant::ofEpochMilli) ?: now,
            )
            when (val result = repository.saveGlucose(reading)) {
                is KoraResult.Success -> {
                    reset()
                    _saved.emit(reading)
                }
                is KoraResult.Failure -> _errors.emit(result.error)
            }
            saving.value = false
        }
    }

    /** Descarta el borrador y prepara uno nuevo. */
    fun reset() {
        listOf(KEY_VALUE, KEY_NOTE, KEY_MEASURED_AT, KEY_EDITING_ID, KEY_SUBMISSION_ID, KEY_CREATED_AT, KEY_SHOW_ERRORS)
            .forEach { handle.remove<Any>(it) }
        handle[KEY_CONTEXT] = suggestedContext().name
        ensureSubmissionId()
    }

    private fun ensureSubmissionId(candidate: String? = null): String {
        handle.get<String>(KEY_SUBMISSION_ID)?.let { return it }
        val id = candidate ?: container.session.currentRepository()?.newId() ?: java.util.UUID.randomUUID().toString()
        handle[KEY_SUBMISSION_ID] = id
        return id
    }

    /** Contexto probable según la hora: reduce un paso en el registro habitual. */
    private fun suggestedContext(): GlucoseContext {
        val time = clock.zonedNow().toLocalTime()
        return when {
            time.isBefore(LocalTime.of(9, 30)) -> GlucoseContext.FASTING
            time.isAfter(LocalTime.of(21, 30)) -> GlucoseContext.BEDTIME
            else -> GlucoseContext.PRE_MEAL
        }
    }

    private companion object {
        const val KEY_VALUE = "glucose_value"
        const val KEY_CONTEXT = "glucose_context"
        const val KEY_NOTE = "glucose_note"
        const val KEY_MEASURED_AT = "glucose_measured_at"
        const val KEY_EDITING_ID = "glucose_editing_id"
        const val KEY_SUBMISSION_ID = "glucose_submission_id"
        const val KEY_CREATED_AT = "glucose_created_at"
        const val KEY_SHOW_ERRORS = "glucose_show_errors"
        const val NO_TIME = -1L
    }
}
