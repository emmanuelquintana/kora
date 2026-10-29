package com.emmanuelquintana.kora.domain.usecase

import com.emmanuelquintana.kora.data.model.HealthRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

class MeasurementValidatorTest {

    private val now = Instant.parse("2026-10-29T14:00:00Z")

    @Test
    fun parsesOnlyPlainDigits() {
        assertEquals(128, MeasurementValidator.parseNumber("128"))
        assertEquals(128, MeasurementValidator.parseNumber(" 128 "))
        assertNull(MeasurementValidator.parseNumber("12.8"))
        assertNull(MeasurementValidator.parseNumber("-5"))
        assertNull(MeasurementValidator.parseNumber("1e3"))
        assertNull(MeasurementValidator.parseNumber(""))
        assertNull(MeasurementValidator.parseNumber("12345"))
    }

    @Test
    fun glucoseRequiresAValueWithinMeterLimits() {
        assertTrue(MeasurementValidator.validateGlucose("110", now, null, now).isValid)
        assertEquals(FieldError.REQUIRED, MeasurementValidator.validateGlucose("", now, null, now)[FormField.GLUCOSE]?.error)
        assertEquals(FieldError.NOT_A_NUMBER, MeasurementValidator.validateGlucose("1o0", now, null, now)[FormField.GLUCOSE]?.error)
        val tooHigh = MeasurementValidator.validateGlucose("750", now, null, now)[FormField.GLUCOSE]
        assertEquals(FieldError.OUT_OF_RANGE, tooHigh?.error)
        assertEquals(20, tooHigh?.min)
        assertEquals(600, tooHigh?.max)
    }

    @Test
    fun rejectsReadingsInTheFutureBeyondTolerance() {
        assertTrue(MeasurementValidator.validateGlucose("110", now.plus(Duration.ofMinutes(4)), null, now).isValid)
        val result = MeasurementValidator.validateGlucose("110", now.plus(Duration.ofHours(1)), null, now)
        assertEquals(FieldError.IN_FUTURE, result[FormField.MEASURED_AT]?.error)
    }

    @Test
    fun rejectsLongNotes() {
        val note = "a".repeat(MeasurementValidator.NOTE_MAX_LENGTH + 1)
        assertEquals(FieldError.TOO_LONG, MeasurementValidator.validateGlucose("110", now, note, now)[FormField.NOTE]?.error)
    }

    @Test
    fun bloodPressureValidatesEachFieldAndTheirRelation() {
        assertTrue(MeasurementValidator.validateBloodPressure("128", "82", "70", now, null, now).isValid)
        assertTrue("el pulso es opcional", MeasurementValidator.validateBloodPressure("128", "82", "", now, null, now).isValid)

        val missing = MeasurementValidator.validateBloodPressure("", "", "", now, null, now)
        assertEquals(FieldError.REQUIRED, missing[FormField.SYSTOLIC]?.error)
        assertEquals(FieldError.REQUIRED, missing[FormField.DIASTOLIC]?.error)
        assertNull(missing[FormField.PULSE])

        val inverted = MeasurementValidator.validateBloodPressure("80", "120", "", now, null, now)
        assertEquals(FieldError.DIASTOLIC_NOT_BELOW_SYSTOLIC, inverted[FormField.DIASTOLIC]?.error)

        val outOfRange = MeasurementValidator.validateBloodPressure("300", "20", "250", now, null, now)
        assertEquals(FieldError.OUT_OF_RANGE, outOfRange[FormField.SYSTOLIC]?.error)
        assertEquals(FieldError.OUT_OF_RANGE, outOfRange[FormField.DIASTOLIC]?.error)
        assertEquals(FieldError.OUT_OF_RANGE, outOfRange[FormField.PULSE]?.error)
    }

    @Test
    fun medicationNeedsNameDoseTimesAndDays() {
        val valid = MeasurementValidator.validateMedication(
            name = "Metformina", dose = "850", unit = "mg",
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            days = DayOfWeek.values().toSet(), rawStock = "60", rawUnitsPerDose = "1", rawLowStockDays = "7",
        )
        assertTrue(valid.isValid)

        val invalid = MeasurementValidator.validateMedication(
            name = " ", dose = "", unit = "", times = listOf(LocalTime.of(8, 0), LocalTime.of(8, 0)),
            days = emptySet(), rawStock = "-1", rawUnitsPerDose = "0", rawLowStockDays = "7",
        )
        assertEquals(FieldError.REQUIRED, invalid[FormField.MEDICATION_NAME]?.error)
        assertEquals(FieldError.REQUIRED, invalid[FormField.MEDICATION_DOSE]?.error)
        assertEquals(FieldError.DUPLICATE_TIME, invalid[FormField.MEDICATION_TIMES]?.error)
        assertEquals(FieldError.REQUIRED, invalid[FormField.MEDICATION_DAYS]?.error)
        assertEquals(FieldError.NOT_A_NUMBER, invalid[FormField.MEDICATION_STOCK]?.error)
        assertEquals(FieldError.OUT_OF_RANGE, invalid[FormField.MEDICATION_UNITS_PER_DOSE]?.error)
    }

    @Test
    fun rangesRequireLowBelowHigh() {
        assertEquals(HealthRanges(80, 160), MeasurementValidator.rangesFrom("80", "160"))
        assertFalse(MeasurementValidator.validateRanges("180", "70").isValid)
        assertNull(MeasurementValidator.rangesFrom("180", "70"))
    }
}
