package com.emmanuelquintana.kora.domain.usecase

import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.model.HealthRanges
import java.time.Duration
import java.time.Instant

/** Semáforo de referencia. El orden importa: gana el nivel más alto. */
enum class StatusLevel {
    /** No hay lecturas recientes suficientes para decir que todo está en rango. */
    INSUFFICIENT_DATA,
    OK,
    CAUTION,
    ALERT,
    URGENT,
}

enum class MeasurementKind { GLUCOSE, BLOOD_PRESSURE }

/** Qué lectura origina el estado: la UI lo explica con palabras, nunca solo con color. */
sealed interface StatusReason {
    val level: StatusLevel
    val measuredAt: Instant

    data class Glucose(val reading: GlucoseReading, val category: GlucoseCategory, override val level: StatusLevel) :
        StatusReason {
        override val measuredAt: Instant get() = reading.measuredAt
    }

    data class BloodPressure(
        val reading: BloodPressureReading,
        val assessment: BloodPressureAssessment,
        override val level: StatusLevel,
    ) : StatusReason {
        override val measuredAt: Instant get() = reading.measuredAt
    }
}

data class HealthStatus(
    val level: StatusLevel,
    /** Lecturas que determinan el nivel, de mayor a menor gravedad. */
    val reasons: List<StatusReason>,
    /** Tipos de medición sin lecturas dentro de la ventana. */
    val missing: Set<MeasurementKind>,
    val window: Duration,
)

/**
 * Calcula el semáforo a partir de la ÚLTIMA lectura de cada tipo dentro de una
 * ventana reciente. Reglas:
 * - Una lectura fuera de rango fija el nivel aunque falten otros datos.
 * - Si todo lo que hay está en rango pero falta algún tipo, el nivel es
 *   INSUFFICIENT_DATA: no se afirma un "todo bien" con datos incompletos.
 */
object HealthStatusEvaluator {
    val DEFAULT_WINDOW: Duration = Duration.ofDays(3)

    fun levelFor(category: GlucoseCategory): StatusLevel = when (category) {
        GlucoseCategory.VERY_LOW -> StatusLevel.URGENT
        GlucoseCategory.LOW -> StatusLevel.ALERT
        GlucoseCategory.VERY_HIGH -> StatusLevel.ALERT
        GlucoseCategory.HIGH -> StatusLevel.CAUTION
        GlucoseCategory.IN_RANGE -> StatusLevel.OK
    }

    fun levelFor(category: BloodPressureCategory): StatusLevel = when (category) {
        BloodPressureCategory.SEVERE -> StatusLevel.URGENT
        BloodPressureCategory.STAGE_2 -> StatusLevel.ALERT
        BloodPressureCategory.STAGE_1 -> StatusLevel.CAUTION
        BloodPressureCategory.ELEVATED -> StatusLevel.CAUTION
        BloodPressureCategory.NORMAL -> StatusLevel.OK
    }

    fun evaluate(
        glucose: List<GlucoseReading>,
        bloodPressure: List<BloodPressureReading>,
        ranges: HealthRanges,
        now: Instant,
        window: Duration = DEFAULT_WINDOW,
    ): HealthStatus {
        val since = now.minus(window)
        val latestGlucose = glucose.filter { it.measuredAt.isAfter(since) }.maxByOrNull { it.measuredAt }
        val latestPressure = bloodPressure.filter { it.measuredAt.isAfter(since) }.maxByOrNull { it.measuredAt }

        val reasons = buildList<StatusReason> {
            latestGlucose?.let {
                val category = GlucoseClassifier.classify(it.valueMgDl, ranges)
                add(StatusReason.Glucose(it, category, levelFor(category)))
            }
            latestPressure?.let {
                val assessment = BloodPressureClassifier.assess(it.systolic, it.diastolic)
                add(StatusReason.BloodPressure(it, assessment, levelFor(assessment.category)))
            }
        }.sortedByDescending { it.level }

        val missing = buildSet {
            if (latestGlucose == null) add(MeasurementKind.GLUCOSE)
            if (latestPressure == null) add(MeasurementKind.BLOOD_PRESSURE)
        }

        val worst = reasons.maxOfOrNull { it.level } ?: StatusLevel.INSUFFICIENT_DATA
        val level = if (worst <= StatusLevel.OK && missing.isNotEmpty()) StatusLevel.INSUFFICIENT_DATA else worst
        return HealthStatus(level, reasons, missing, window)
    }
}
