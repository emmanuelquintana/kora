package com.emmanuelquintana.kora.domain.usecase

import com.emmanuelquintana.kora.domain.usecase.BloodPressureCategory.ELEVATED
import com.emmanuelquintana.kora.domain.usecase.BloodPressureCategory.NORMAL
import com.emmanuelquintana.kora.domain.usecase.BloodPressureCategory.SEVERE
import com.emmanuelquintana.kora.domain.usecase.BloodPressureCategory.STAGE_1
import com.emmanuelquintana.kora.domain.usecase.BloodPressureCategory.STAGE_2
import org.junit.Assert.assertEquals
import org.junit.Test

class BloodPressureClassifierTest {

    private fun assertCategory(expected: BloodPressureCategory, systolic: Int, diastolic: Int) =
        assertEquals("$systolic/$diastolic", expected, BloodPressureClassifier.classify(systolic, diastolic))

    @Test
    fun normalIsBelow120And80() {
        assertCategory(NORMAL, 119, 79)
        assertCategory(NORMAL, 95, 60)
    }

    @Test
    fun elevatedIsSystolic120To129WithDiastolicBelow80() {
        assertCategory(ELEVATED, 120, 79)
        assertCategory(ELEVATED, 129, 70)
    }

    @Test
    fun stage1IsSystolic130To139OrDiastolic80To89() {
        assertCategory(STAGE_1, 130, 70)
        assertCategory(STAGE_1, 139, 79)
        assertCategory(STAGE_1, 115, 80)
        assertCategory(STAGE_1, 118, 89)
    }

    @Test
    fun stage2IsSystolicFrom140OrDiastolicFrom90() {
        assertCategory(STAGE_2, 140, 70)
        assertCategory(STAGE_2, 118, 90)
        assertCategory(STAGE_2, 180, 120)
    }

    @Test
    fun theHigherCategoryWinsWhenValuesDisagree() {
        // Sistólica elevada + diastólica nivel 1 → nivel 1.
        assertCategory(STAGE_1, 125, 85)
        // Sistólica normal + diastólica nivel 2 → nivel 2.
        assertCategory(STAGE_2, 110, 95)
        // Sistólica nivel 1 + diastólica nivel 2 → nivel 2.
        assertCategory(STAGE_2, 135, 92)
    }

    @Test
    fun severeIsAbove180OrAbove120() {
        assertCategory(SEVERE, 181, 80)
        assertCategory(SEVERE, 150, 121)
    }

    @Test
    fun adviceMatchesCategory() {
        assertEquals(BloodPressureAdvice.NONE, BloodPressureClassifier.assess(118, 76).advice)
        assertEquals(BloodPressureAdvice.NONE, BloodPressureClassifier.assess(134, 78).advice)
        assertEquals(BloodPressureAdvice.CONFIRM_AND_DISCUSS, BloodPressureClassifier.assess(150, 95).advice)
        assertEquals(BloodPressureAdvice.REMEASURE_AND_WATCH_SYMPTOMS, BloodPressureClassifier.assess(185, 100).advice)
    }

    @Test
    fun assessmentKeepsPerValueCategories() {
        val assessment = BloodPressureClassifier.assess(125, 92)
        assertEquals(ELEVATED, assessment.systolicCategory)
        assertEquals(STAGE_2, assessment.diastolicCategory)
        assertEquals(STAGE_2, assessment.category)
    }
}
