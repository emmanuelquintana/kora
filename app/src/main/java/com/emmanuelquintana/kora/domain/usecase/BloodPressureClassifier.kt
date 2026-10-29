package com.emmanuelquintana.kora.domain.usecase

/**
 * Categorías de referencia (orientativas, no diagnósticas). El orden importa:
 * cuando sistólica y diastólica caen en categorías distintas gana la más alta.
 */
enum class BloodPressureCategory {
    NORMAL,
    ELEVATED,
    STAGE_1,
    STAGE_2,

    /** Lectura especialmente alta que requiere atención (> 180 y/o > 120). */
    SEVERE,
}

/** Indicación prudente asociada a una lectura. */
enum class BloodPressureAdvice {
    NONE,

    /** Confirmar con otra medición en otro momento y comentarlo con su médico. */
    CONFIRM_AND_DISCUSS,

    /**
     * Esperar 5 minutos en reposo y repetir la medición. Si sigue igual de alta
     * o hay síntomas de alarma (dolor de pecho, falta de aire, dolor de espalda,
     * debilidad o entumecimiento, cambios en la visión, dificultad para hablar),
     * buscar atención médica urgente.
     */
    REMEASURE_AND_WATCH_SYMPTOMS,
}

data class BloodPressureAssessment(
    val category: BloodPressureCategory,
    val systolicCategory: BloodPressureCategory,
    val diastolicCategory: BloodPressureCategory,
    val advice: BloodPressureAdvice,
)

object BloodPressureClassifier {
    const val SEVERE_SYSTOLIC_ABOVE = 180
    const val SEVERE_DIASTOLIC_ABOVE = 120

    fun classify(systolic: Int, diastolic: Int): BloodPressureCategory = assess(systolic, diastolic).category

    fun assess(systolic: Int, diastolic: Int): BloodPressureAssessment {
        val bySystolic = when {
            systolic > SEVERE_SYSTOLIC_ABOVE -> BloodPressureCategory.SEVERE
            systolic >= 140 -> BloodPressureCategory.STAGE_2
            systolic >= 130 -> BloodPressureCategory.STAGE_1
            systolic >= 120 -> BloodPressureCategory.ELEVATED
            else -> BloodPressureCategory.NORMAL
        }
        // La diastólica no tiene categoría "Elevada": 80–89 ya es nivel 1.
        val byDiastolic = when {
            diastolic > SEVERE_DIASTOLIC_ABOVE -> BloodPressureCategory.SEVERE
            diastolic >= 90 -> BloodPressureCategory.STAGE_2
            diastolic >= 80 -> BloodPressureCategory.STAGE_1
            else -> BloodPressureCategory.NORMAL
        }
        val category = maxOf(bySystolic, byDiastolic)
        val advice = when (category) {
            BloodPressureCategory.SEVERE -> BloodPressureAdvice.REMEASURE_AND_WATCH_SYMPTOMS
            BloodPressureCategory.STAGE_2 -> BloodPressureAdvice.CONFIRM_AND_DISCUSS
            else -> BloodPressureAdvice.NONE
        }
        return BloodPressureAssessment(category, bySystolic, byDiastolic, advice)
    }
}
