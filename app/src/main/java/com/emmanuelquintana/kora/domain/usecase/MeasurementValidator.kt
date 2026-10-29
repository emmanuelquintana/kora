package com.emmanuelquintana.kora.domain.usecase

import com.emmanuelquintana.kora.data.model.HealthRanges
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

enum class FormField {
    GLUCOSE,
    SYSTOLIC,
    DIASTOLIC,
    PULSE,
    MEASURED_AT,
    NOTE,
    MEDICATION_NAME,
    MEDICATION_DOSE,
    MEDICATION_UNIT,
    MEDICATION_TIMES,
    MEDICATION_DAYS,
    MEDICATION_STOCK,
    MEDICATION_UNITS_PER_DOSE,
    MEDICATION_LOW_STOCK_DAYS,
    RANGE_LOW,
    RANGE_HIGH,
}

enum class FieldError {
    REQUIRED,
    NOT_A_NUMBER,
    OUT_OF_RANGE,
    DIASTOLIC_NOT_BELOW_SYSTOLIC,
    IN_FUTURE,
    TOO_LONG,
    DUPLICATE_TIME,
    LOW_NOT_BELOW_HIGH,
}

/** Un error con los límites que la UI muestra junto al campo ("Entre 60 y 260"). */
data class FieldIssue(val error: FieldError, val min: Int? = null, val max: Int? = null)

data class ValidationResult(val issues: Map<FormField, FieldIssue>) {
    val isValid: Boolean get() = issues.isEmpty()
    operator fun get(field: FormField): FieldIssue? = issues[field]
}

/** Validación inmediata de capturas. Los límites son físicos/de medidor, no clínicos. */
object MeasurementValidator {
    val GLUCOSE_LIMITS = 20..600
    val SYSTOLIC_LIMITS = 60..260
    val DIASTOLIC_LIMITS = 30..160
    val PULSE_LIMITS = 30..220
    val STOCK_LIMITS = 0..9_999
    val UNITS_PER_DOSE_LIMITS = 1..20
    val LOW_STOCK_DAYS_LIMITS = 0..60
    val GLUCOSE_RANGE_LIMITS = 40..400
    const val NOTE_MAX_LENGTH = 500
    const val MEDICATION_NAME_MAX_LENGTH = 60
    const val MEDICATION_FIELD_MAX_LENGTH = 20
    const val MAX_TIMES_PER_DAY = 8

    /** Tolerancia para relojes ligeramente desfasados. */
    private val FUTURE_TOLERANCE: Duration = Duration.ofMinutes(5)

    /** Convierte la captura del teclado numérico; acepta solo dígitos. */
    fun parseNumber(raw: String): Int? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > 4 || !trimmed.all { it in '0'..'9' }) return null
        return trimmed.toInt()
    }

    private fun numberIssue(raw: String, limits: IntRange, required: Boolean = true): FieldIssue? {
        if (raw.isBlank()) return if (required) FieldIssue(FieldError.REQUIRED) else null
        val value = parseNumber(raw) ?: return FieldIssue(FieldError.NOT_A_NUMBER)
        return if (value in limits) null else FieldIssue(FieldError.OUT_OF_RANGE, limits.first, limits.last)
    }

    private fun MutableMap<FormField, FieldIssue>.checkTime(measuredAt: Instant, now: Instant) {
        if (measuredAt.isAfter(now.plus(FUTURE_TOLERANCE))) put(FormField.MEASURED_AT, FieldIssue(FieldError.IN_FUTURE))
    }

    private fun MutableMap<FormField, FieldIssue>.checkNote(note: String?) {
        if (note != null && note.length > NOTE_MAX_LENGTH) {
            put(FormField.NOTE, FieldIssue(FieldError.TOO_LONG, max = NOTE_MAX_LENGTH))
        }
    }

    fun validateGlucose(rawValue: String, measuredAt: Instant, note: String?, now: Instant): ValidationResult {
        val issues = mutableMapOf<FormField, FieldIssue>()
        numberIssue(rawValue, GLUCOSE_LIMITS)?.let { issues[FormField.GLUCOSE] = it }
        issues.checkTime(measuredAt, now)
        issues.checkNote(note)
        return ValidationResult(issues)
    }

    fun validateBloodPressure(
        rawSystolic: String,
        rawDiastolic: String,
        rawPulse: String,
        measuredAt: Instant,
        note: String?,
        now: Instant,
    ): ValidationResult {
        val issues = mutableMapOf<FormField, FieldIssue>()
        numberIssue(rawSystolic, SYSTOLIC_LIMITS)?.let { issues[FormField.SYSTOLIC] = it }
        numberIssue(rawDiastolic, DIASTOLIC_LIMITS)?.let { issues[FormField.DIASTOLIC] = it }
        numberIssue(rawPulse, PULSE_LIMITS, required = false)?.let { issues[FormField.PULSE] = it }
        val systolic = parseNumber(rawSystolic)
        val diastolic = parseNumber(rawDiastolic)
        if (FormField.SYSTOLIC !in issues && FormField.DIASTOLIC !in issues &&
            systolic != null && diastolic != null && diastolic >= systolic
        ) {
            issues[FormField.DIASTOLIC] = FieldIssue(FieldError.DIASTOLIC_NOT_BELOW_SYSTOLIC)
        }
        issues.checkTime(measuredAt, now)
        issues.checkNote(note)
        return ValidationResult(issues)
    }

    fun validateMedication(
        name: String,
        dose: String,
        unit: String,
        times: List<LocalTime>,
        days: Set<DayOfWeek>,
        rawStock: String,
        rawUnitsPerDose: String,
        rawLowStockDays: String,
    ): ValidationResult {
        val issues = mutableMapOf<FormField, FieldIssue>()
        when {
            name.isBlank() -> issues[FormField.MEDICATION_NAME] = FieldIssue(FieldError.REQUIRED)
            name.trim().length > MEDICATION_NAME_MAX_LENGTH ->
                issues[FormField.MEDICATION_NAME] = FieldIssue(FieldError.TOO_LONG, max = MEDICATION_NAME_MAX_LENGTH)
        }
        when {
            dose.isBlank() -> issues[FormField.MEDICATION_DOSE] = FieldIssue(FieldError.REQUIRED)
            dose.trim().length > MEDICATION_FIELD_MAX_LENGTH ->
                issues[FormField.MEDICATION_DOSE] = FieldIssue(FieldError.TOO_LONG, max = MEDICATION_FIELD_MAX_LENGTH)
        }
        when {
            unit.isBlank() -> issues[FormField.MEDICATION_UNIT] = FieldIssue(FieldError.REQUIRED)
            unit.trim().length > MEDICATION_FIELD_MAX_LENGTH ->
                issues[FormField.MEDICATION_UNIT] = FieldIssue(FieldError.TOO_LONG, max = MEDICATION_FIELD_MAX_LENGTH)
        }
        when {
            times.isEmpty() -> issues[FormField.MEDICATION_TIMES] = FieldIssue(FieldError.REQUIRED)
            times.size > MAX_TIMES_PER_DAY ->
                issues[FormField.MEDICATION_TIMES] = FieldIssue(FieldError.OUT_OF_RANGE, 1, MAX_TIMES_PER_DAY)
            times.map { it.withSecond(0).withNano(0) }.toSet().size != times.size ->
                issues[FormField.MEDICATION_TIMES] = FieldIssue(FieldError.DUPLICATE_TIME)
        }
        if (days.isEmpty()) issues[FormField.MEDICATION_DAYS] = FieldIssue(FieldError.REQUIRED)
        numberIssue(rawStock, STOCK_LIMITS)?.let { issues[FormField.MEDICATION_STOCK] = it }
        numberIssue(rawUnitsPerDose, UNITS_PER_DOSE_LIMITS)?.let { issues[FormField.MEDICATION_UNITS_PER_DOSE] = it }
        numberIssue(rawLowStockDays, LOW_STOCK_DAYS_LIMITS)?.let { issues[FormField.MEDICATION_LOW_STOCK_DAYS] = it }
        return ValidationResult(issues)
    }

    fun validateRanges(rawLow: String, rawHigh: String): ValidationResult {
        val issues = mutableMapOf<FormField, FieldIssue>()
        numberIssue(rawLow, GLUCOSE_RANGE_LIMITS)?.let { issues[FormField.RANGE_LOW] = it }
        numberIssue(rawHigh, GLUCOSE_RANGE_LIMITS)?.let { issues[FormField.RANGE_HIGH] = it }
        val low = parseNumber(rawLow)
        val high = parseNumber(rawHigh)
        if (issues.isEmpty() && low != null && high != null && low >= high) {
            issues[FormField.RANGE_HIGH] = FieldIssue(FieldError.LOW_NOT_BELOW_HIGH)
        }
        return ValidationResult(issues)
    }

    fun rangesFrom(rawLow: String, rawHigh: String): HealthRanges? {
        if (!validateRanges(rawLow, rawHigh).isValid) return null
        return HealthRanges(parseNumber(rawLow)!!, parseNumber(rawHigh)!!)
    }
}
