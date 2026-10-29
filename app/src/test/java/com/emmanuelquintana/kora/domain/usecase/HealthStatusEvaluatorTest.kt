package com.emmanuelquintana.kora.domain.usecase

import com.emmanuelquintana.kora.data.model.Arm
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.data.model.GlucoseContext
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.model.HealthRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HealthStatusEvaluatorTest {

    private val now = Instant.parse("2026-10-29T14:00:00Z")
    private val ranges = HealthRanges()

    private fun glucose(value: Int, ago: Duration = Duration.ofHours(1)) =
        GlucoseReading("g$value", value, GlucoseContext.FASTING, now.minus(ago))

    private fun pressure(sys: Int, dia: Int, ago: Duration = Duration.ofHours(2)) =
        BloodPressureReading("p$sys", sys, dia, 70, Arm.LEFT, now.minus(ago))

    @Test
    fun glucoseCategoriesFollowConfiguredRanges() {
        assertEquals(GlucoseCategory.IN_RANGE, GlucoseClassifier.classify(70, ranges))
        assertEquals(GlucoseCategory.IN_RANGE, GlucoseClassifier.classify(180, ranges))
        assertEquals(GlucoseCategory.LOW, GlucoseClassifier.classify(69, ranges))
        assertEquals(GlucoseCategory.VERY_LOW, GlucoseClassifier.classify(53, ranges))
        assertEquals(GlucoseCategory.HIGH, GlucoseClassifier.classify(181, ranges))
        assertEquals(GlucoseCategory.VERY_HIGH, GlucoseClassifier.classify(251, ranges))
        // Con un rango personalizado más estrecho, 150 ya es alta.
        assertEquals(GlucoseCategory.HIGH, GlucoseClassifier.classify(150, HealthRanges(80, 140)))
    }

    @Test
    fun noReadingsMeansInsufficientData() {
        val status = HealthStatusEvaluator.evaluate(emptyList(), emptyList(), ranges, now)
        assertEquals(StatusLevel.INSUFFICIENT_DATA, status.level)
        assertEquals(setOf(MeasurementKind.GLUCOSE, MeasurementKind.BLOOD_PRESSURE), status.missing)
    }

    @Test
    fun allInRangeButMissingAKindIsNotReportedAsOk() {
        val status = HealthStatusEvaluator.evaluate(listOf(glucose(110)), emptyList(), ranges, now)
        assertEquals(StatusLevel.INSUFFICIENT_DATA, status.level)
        assertEquals(setOf(MeasurementKind.BLOOD_PRESSURE), status.missing)
    }

    @Test
    fun allRecentAndInRangeIsOk() {
        val status = HealthStatusEvaluator.evaluate(listOf(glucose(110)), listOf(pressure(118, 76)), ranges, now)
        assertEquals(StatusLevel.OK, status.level)
        assertTrue(status.missing.isEmpty())
    }

    @Test
    fun anAbnormalReadingSetsTheLevelEvenWithMissingData() {
        val status = HealthStatusEvaluator.evaluate(emptyList(), listOf(pressure(150, 95)), ranges, now)
        assertEquals(StatusLevel.ALERT, status.level)
        assertTrue(status.reasons.first() is StatusReason.BloodPressure)
    }

    @Test
    fun onlyTheLatestReadingOfEachKindCounts() {
        val old = glucose(45, Duration.ofHours(10))
        val recent = glucose(120, Duration.ofHours(1))
        val status = HealthStatusEvaluator.evaluate(listOf(old, recent), listOf(pressure(118, 76)), ranges, now)
        assertEquals(StatusLevel.OK, status.level)
    }

    @Test
    fun readingsOutsideTheWindowAreIgnored() {
        val stale = pressure(190, 125, Duration.ofDays(5))
        val status = HealthStatusEvaluator.evaluate(listOf(glucose(110)), listOf(stale), ranges, now)
        assertEquals(StatusLevel.INSUFFICIENT_DATA, status.level)
    }

    @Test
    fun theWorstReasonComesFirst() {
        val status = HealthStatusEvaluator.evaluate(listOf(glucose(50)), listOf(pressure(135, 85)), ranges, now)
        assertEquals(StatusLevel.URGENT, status.level)
        assertTrue(status.reasons.first() is StatusReason.Glucose)
        assertEquals(StatusLevel.CAUTION, status.reasons[1].level)
    }
}
