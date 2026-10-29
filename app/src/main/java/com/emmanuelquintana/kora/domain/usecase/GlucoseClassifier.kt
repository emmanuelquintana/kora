package com.emmanuelquintana.kora.domain.usecase

import com.emmanuelquintana.kora.data.model.HealthRanges

enum class GlucoseCategory {
    /** Por debajo de 54 mg/dL: requiere actuar de inmediato según su plan. */
    VERY_LOW,
    LOW,
    IN_RANGE,
    HIGH,

    /** Por encima de 250 mg/dL. */
    VERY_HIGH,
}

/**
 * Clasifica una lectura contra los rangos que configuró la persona.
 * 70–180 mg/dL es solo el valor inicial; no es un objetivo universal.
 */
object GlucoseClassifier {
    const val VERY_LOW_BELOW = 54
    const val VERY_HIGH_ABOVE = 250

    fun classify(valueMgDl: Int, ranges: HealthRanges): GlucoseCategory = when {
        valueMgDl < VERY_LOW_BELOW -> GlucoseCategory.VERY_LOW
        valueMgDl < ranges.glucoseLowMgDl -> GlucoseCategory.LOW
        valueMgDl > VERY_HIGH_ABOVE && ranges.glucoseHighMgDl < VERY_HIGH_ABOVE -> GlucoseCategory.VERY_HIGH
        valueMgDl > ranges.glucoseHighMgDl -> GlucoseCategory.HIGH
        else -> GlucoseCategory.IN_RANGE
    }
}
