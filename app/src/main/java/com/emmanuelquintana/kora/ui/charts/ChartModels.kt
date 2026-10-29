package com.emmanuelquintana.kora.ui.charts

import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.data.model.GlucoseContext
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.model.HealthRanges
import com.emmanuelquintana.kora.domain.usecase.GlucoseCategory
import com.emmanuelquintana.kora.domain.usecase.GlucoseClassifier
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Periodos de las gráficas. */
enum class ChartPeriod(val days: Int) { WEEK(7), MONTH(30), QUARTER(90) }

/** Punto ya normalizado: x e y en [0, 1] dentro del área de trazado. */
data class ChartPoint(val x: Float, val y: Float, val index: Int)

/**
 * Tramos de línea: las mediciones solo se unen si están a menos de
 * [maxGap] entre sí. Un hueco real se dibuja como hueco, no como línea.
 */
fun <T> segmentsByGap(items: List<T>, at: (T) -> Instant, maxGap: Duration): List<List<Int>> {
    if (items.isEmpty()) return emptyList()
    val segments = mutableListOf(mutableListOf(0))
    for (i in 1 until items.size) {
        if (Duration.between(at(items[i - 1]), at(items[i])) > maxGap) segments += mutableListOf<Int>()
        segments.last() += i
    }
    return segments
}

/** Escala del eje Y redondeada a múltiplos de [step]. */
data class AxisScale(val min: Float, val max: Float, val step: Float) {
    fun normalize(value: Float): Float = ((value - min) / (max - min)).coerceIn(0f, 1f)
    val ticks: List<Float> get() = generateSequence(min) { it + step }.takeWhile { it <= max + 0.01f }.toList()

    companion object {
        fun around(values: List<Int>, floorAt: Int, ceilingAt: Int, step: Int): AxisScale {
            val low = minOf(values.minOrNull() ?: floorAt, floorAt)
            val high = maxOf(values.maxOrNull() ?: ceilingAt, ceilingAt)
            val min = floor(low.toFloat() / step) * step
            val max = ceil(high.toFloat() / step) * step
            return AxisScale(min, if (max <= min) min + step else max, step.toFloat())
        }
    }
}

/** Distancia máxima entre mediciones unidas por línea, según el periodo. */
fun maxGapFor(period: ChartPeriod): Duration = when (period) {
    ChartPeriod.WEEK -> Duration.ofHours(36)
    ChartPeriod.MONTH -> Duration.ofDays(3)
    ChartPeriod.QUARTER -> Duration.ofDays(6)
}

fun Instant.fractionIn(start: Instant, end: Instant): Float {
    val total = Duration.between(start, end).toMillis().coerceAtLeast(1)
    return (Duration.between(start, this).toMillis().toFloat() / total).coerceIn(0f, 1f)
}

data class GlucoseTrend(
    val count: Int,
    val inRange: Int,
    val average: Int,
    val fastingAverage: Int?,
    val min: Int,
    val max: Int,
    val outOfRange: Int,
)

data class PressureTrend(
    val count: Int,
    val systolicAverage: Int,
    val diastolicAverage: Int,
    val systolicMin: Int,
    val systolicMax: Int,
    val diastolicMin: Int,
    val diastolicMax: Int,
    /** Diferencia de la sistólica media frente al periodo anterior de igual duración; null sin datos previos. */
    val systolicDeltaVsPrevious: Int?,
)

/** Resúmenes descriptivos (no interpretaciones clínicas) de un periodo. */
object TrendAnalyzer {
    const val MIN_READINGS = 3

    fun glucose(readings: List<GlucoseReading>, ranges: HealthRanges): GlucoseTrend? {
        if (readings.isEmpty()) return null
        val values = readings.map { it.valueMgDl }
        val inRange = readings.count { GlucoseClassifier.classify(it.valueMgDl, ranges) == GlucoseCategory.IN_RANGE }
        val fasting = readings.filter { it.context == GlucoseContext.FASTING }.map { it.valueMgDl }
        return GlucoseTrend(
            count = readings.size,
            inRange = inRange,
            average = values.average().roundToInt(),
            fastingAverage = fasting.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            min = values.min(),
            max = values.max(),
            outOfRange = readings.size - inRange,
        )
    }

    fun pressure(current: List<BloodPressureReading>, previous: List<BloodPressureReading>): PressureTrend? {
        if (current.isEmpty()) return null
        val systolicAvg = current.map { it.systolic }.average()
        val previousAvg = previous.takeIf { it.size >= MIN_READINGS }?.map { it.systolic }?.average()
        return PressureTrend(
            count = current.size,
            systolicAverage = systolicAvg.roundToInt(),
            diastolicAverage = current.map { it.diastolic }.average().roundToInt(),
            systolicMin = current.minOf { it.systolic },
            systolicMax = current.maxOf { it.systolic },
            diastolicMin = current.minOf { it.diastolic },
            diastolicMax = current.maxOf { it.diastolic },
            systolicDeltaVsPrevious = previousAvg?.let { (systolicAvg - it).roundToInt() },
        )
    }
}
