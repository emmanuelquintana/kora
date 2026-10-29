package com.emmanuelquintana.kora.ui.state

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emmanuelquintana.kora.AppContainer
import com.emmanuelquintana.kora.core.util.KoraError
import com.emmanuelquintana.kora.core.util.KoraResult
import com.emmanuelquintana.kora.data.model.Arm
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.domain.usecase.BloodPressureAssessment
import com.emmanuelquintana.kora.domain.usecase.BloodPressureClassifier
import com.emmanuelquintana.kora.domain.usecase.MeasurementValidator
import com.emmanuelquintana.kora.domain.usecase.ValidationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** Temporizador de reposo previo a la medición (modo Flex). */
sealed interface RestTimerState {
    data object Idle : RestTimerState
    data class Running(val remaining: Duration, val total: Duration) : RestTimerState {
        val progress: Float get() = 1f - remaining.toMillis().toFloat() / total.toMillis().coerceAtLeast(1)
    }

    /** Terminó el reposo. No implica que la medición sea válida: solo que pasaron 5 minutos. */
    data object Finished : RestTimerState
}

data class BloodPressureEntryUiState(
    val rawSystolic: String = "",
    val rawDiastolic: String = "",
    val rawPulse: String = "",
    val arm: Arm = Arm.LEFT,
    val note: String = "",
    val measuredAt: Instant? = null,
    val isEditing: Boolean = false,
    val validation: ValidationResult = ValidationResult(emptyMap()),
    val showErrors: Boolean = false,
    /** Clasificación de referencia mientras se escribe (null si aún no hay dos valores válidos). */
    val assessment: BloodPressureAssessment? = null,
    val isSaving: Boolean = false,
)

/**
 * Borrador de presión arterial + temporizador de reposo. El fin del reposo se
 * guarda como instante de `elapsedRealtime`, no como contador: rotar, plegar
 * o perder el proceso no reinicia los 5 minutos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BloodPressureEntryViewModel(
    private val handle: SavedStateHandle,
    private val container: AppContainer,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

    private val clock = container.clock
    private val saving = MutableStateFlow(false)
    private val _saved = MutableSharedFlow<BloodPressureReading>(extraBufferCapacity = 1)
    val saved: SharedFlow<BloodPressureReading> = _saved.asSharedFlow()
    private val _errors = MutableSharedFlow<KoraError>(extraBufferCapacity = 1)
    val errors: SharedFlow<KoraError> = _errors.asSharedFlow()

    init {
        ensureSubmissionId()
    }

    val uiState: StateFlow<BloodPressureEntryUiState> = combine(
        combine(
            handle.getStateFlow(KEY_SYSTOLIC, ""),
            handle.getStateFlow(KEY_DIASTOLIC, ""),
            handle.getStateFlow(KEY_PULSE, ""),
        ) { s, d, p -> Triple(s, d, p) },
        handle.getStateFlow(KEY_ARM, Arm.LEFT.name),
        handle.getStateFlow(KEY_NOTE, ""),
        handle.getStateFlow(KEY_MEASURED_AT, NO_TIME),
        combine(handle.getStateFlow(KEY_SHOW_ERRORS, false), saving) { a, b -> a to b },
    ) { (systolic, diastolic, pulse), arm, note, measuredAt, (showErrors, isSaving) ->
        val at = measuredAt.takeIf { it != NO_TIME }?.let(Instant::ofEpochMilli)
        val validation = MeasurementValidator.validateBloodPressure(
            systolic, diastolic, pulse, at ?: clock.now(), note.ifBlank { null }, clock.now(),
        )
        val sys = MeasurementValidator.parseNumber(systolic)?.takeIf { it in MeasurementValidator.SYSTOLIC_LIMITS }
        val dia = MeasurementValidator.parseNumber(diastolic)?.takeIf { it in MeasurementValidator.DIASTOLIC_LIMITS }
        BloodPressureEntryUiState(
            rawSystolic = systolic,
            rawDiastolic = diastolic,
            rawPulse = pulse,
            arm = runCatching { Arm.valueOf(arm) }.getOrDefault(Arm.LEFT),
            note = note,
            measuredAt = at,
            isEditing = handle.get<String>(KEY_EDITING_ID) != null,
            validation = validation,
            showErrors = showErrors,
            assessment = if (sys != null && dia != null && dia < sys) BloodPressureClassifier.assess(sys, dia) else null,
            isSaving = isSaving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BloodPressureEntryUiState())

    val restTimer: StateFlow<RestTimerState> = handle.getStateFlow(KEY_REST_ENDS_AT, NO_TIME)
        .flatMapLatest { endsAt ->
            if (endsAt == NO_TIME) {
                flowOf<RestTimerState>(RestTimerState.Idle)
            } else {
                flow<RestTimerState> {
                    while (true) {
                        val remaining = endsAt - elapsedRealtime()
                        if (remaining <= 0) {
                            emit(RestTimerState.Finished)
                            break
                        }
                        emit(RestTimerState.Running(Duration.ofMillis(remaining), REST_DURATION))
                        delay(remaining.coerceAtMost(TICK_MS))
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RestTimerState.Idle)

    fun onSystolicChange(raw: String) {
        handle[KEY_SYSTOLIC] = raw.filter(Char::isDigit).take(3)
    }

    fun onDiastolicChange(raw: String) {
        handle[KEY_DIASTOLIC] = raw.filter(Char::isDigit).take(3)
    }

    fun onPulseChange(raw: String) {
        handle[KEY_PULSE] = raw.filter(Char::isDigit).take(3)
    }

    fun onArmChange(arm: Arm) {
        handle[KEY_ARM] = arm.name
    }

    fun onNoteChange(note: String) {
        handle[KEY_NOTE] = note.take(MeasurementValidator.NOTE_MAX_LENGTH)
    }

    fun onMeasuredAtChange(at: Instant?) {
        handle[KEY_MEASURED_AT] = at?.toEpochMilli() ?: NO_TIME
    }

    fun startRest() {
        val endsAt = elapsedRealtime() + REST_DURATION.toMillis()
        handle[KEY_REST_ENDS_AT] = endsAt
        container.restTimerScheduler.schedule(endsAt)
    }

    fun cancelRest() {
        handle[KEY_REST_ENDS_AT] = NO_TIME
        container.restTimerScheduler.cancel()
    }

    fun edit(reading: BloodPressureReading) {
        handle[KEY_EDITING_ID] = reading.id
        handle[KEY_SUBMISSION_ID] = reading.id
        handle[KEY_CREATED_AT] = reading.createdAt.toEpochMilli()
        handle[KEY_SYSTOLIC] = reading.systolic.toString()
        handle[KEY_DIASTOLIC] = reading.diastolic.toString()
        handle[KEY_PULSE] = reading.pulse?.toString().orEmpty()
        handle[KEY_ARM] = reading.arm.name
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
        val systolic = MeasurementValidator.parseNumber(state.rawSystolic) ?: return
        val diastolic = MeasurementValidator.parseNumber(state.rawDiastolic) ?: return
        viewModelScope.launch {
            val repository = container.session.currentRepository()
            if (repository == null) {
                _errors.emit(KoraError.SESSION_EXPIRED)
                return@launch
            }
            saving.value = true
            val id = ensureSubmissionId(repository.newId())
            val now = clock.now()
            val reading = BloodPressureReading(
                id = id,
                systolic = systolic,
                diastolic = diastolic,
                pulse = MeasurementValidator.parseNumber(state.rawPulse),
                arm = state.arm,
                measuredAt = state.measuredAt ?: now,
                note = state.note.trim().ifBlank { null },
                createdAt = handle.get<Long>(KEY_CREATED_AT)?.let(Instant::ofEpochMilli) ?: now,
            )
            when (val result = repository.saveBloodPressure(reading)) {
                is KoraResult.Success -> {
                    reset(keepArm = true)
                    _saved.emit(reading)
                }
                is KoraResult.Failure -> _errors.emit(result.error)
            }
            saving.value = false
        }
    }

    /** Descarta el borrador. El brazo se conserva: suele medirse siempre en el mismo. */
    fun reset(keepArm: Boolean = true) {
        val keys = mutableListOf(
            KEY_SYSTOLIC, KEY_DIASTOLIC, KEY_PULSE, KEY_NOTE, KEY_MEASURED_AT,
            KEY_EDITING_ID, KEY_SUBMISSION_ID, KEY_CREATED_AT, KEY_SHOW_ERRORS,
        )
        if (!keepArm) keys += KEY_ARM
        keys.forEach { handle.remove<Any>(it) }
        cancelRest()
        ensureSubmissionId()
    }

    private fun ensureSubmissionId(candidate: String? = null): String {
        handle.get<String>(KEY_SUBMISSION_ID)?.let { return it }
        val id = candidate ?: container.session.currentRepository()?.newId() ?: java.util.UUID.randomUUID().toString()
        handle[KEY_SUBMISSION_ID] = id
        return id
    }

    companion object {
        val REST_DURATION: Duration = Duration.ofMinutes(5)
        private const val TICK_MS = 250L
        private const val KEY_SYSTOLIC = "bp_systolic"
        private const val KEY_DIASTOLIC = "bp_diastolic"
        private const val KEY_PULSE = "bp_pulse"
        private const val KEY_ARM = "bp_arm"
        private const val KEY_NOTE = "bp_note"
        private const val KEY_MEASURED_AT = "bp_measured_at"
        private const val KEY_EDITING_ID = "bp_editing_id"
        private const val KEY_SUBMISSION_ID = "bp_submission_id"
        private const val KEY_CREATED_AT = "bp_created_at"
        private const val KEY_SHOW_ERRORS = "bp_show_errors"
        private const val KEY_REST_ENDS_AT = "bp_rest_ends_at"
        private const val NO_TIME = -1L
    }
}
