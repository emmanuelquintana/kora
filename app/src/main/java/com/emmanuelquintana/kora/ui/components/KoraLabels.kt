package com.emmanuelquintana.kora.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.designsystem.KoraStatusColors
import com.emmanuelquintana.kora.core.update.UpdateFailure
import com.emmanuelquintana.kora.core.util.KoraError
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.Arm
import com.emmanuelquintana.kora.data.model.GlucoseContext
import com.emmanuelquintana.kora.domain.usecase.BloodPressureCategory
import com.emmanuelquintana.kora.domain.usecase.DoseState
import com.emmanuelquintana.kora.domain.usecase.FieldError
import com.emmanuelquintana.kora.domain.usecase.FieldIssue
import com.emmanuelquintana.kora.domain.usecase.GlucoseCategory
import com.emmanuelquintana.kora.domain.usecase.StatusLevel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Forma que acompaña a cada estado: el color nunca va solo. */
enum class StatusShape { CHECK, UP_ONE, UP_TWO, ALERT, WARNING, DOWN_ONE, DOWN_TWO, NONE }

/** Estado clínico listo para dibujar: palabra + color + forma. */
data class StatusVisual(val label: String, val colors: KoraStatusColors, val shape: StatusShape)

@StringRes
fun GlucoseContext.labelRes(): Int = when (this) {
    GlucoseContext.FASTING -> R.string.context_fasting
    GlucoseContext.PRE_MEAL -> R.string.context_pre_meal
    GlucoseContext.POST_MEAL_2H -> R.string.context_post_meal
    GlucoseContext.BEDTIME -> R.string.context_bedtime
}

@StringRes
fun Arm.labelRes(short: Boolean = false): Int = when (this) {
    Arm.LEFT -> if (short) R.string.arm_left_short else R.string.arm_left
    Arm.RIGHT -> if (short) R.string.arm_right_short else R.string.arm_right
}

@StringRes
fun BloodPressureCategory.labelRes(): Int = when (this) {
    BloodPressureCategory.NORMAL -> R.string.bp_normal
    BloodPressureCategory.ELEVATED -> R.string.bp_elevated
    BloodPressureCategory.STAGE_1 -> R.string.bp_stage1
    BloodPressureCategory.STAGE_2 -> R.string.bp_stage2
    BloodPressureCategory.SEVERE -> R.string.bp_severe
}

@StringRes
fun GlucoseCategory.labelRes(): Int = when (this) {
    GlucoseCategory.VERY_LOW -> R.string.glucose_very_low
    GlucoseCategory.LOW -> R.string.glucose_low
    GlucoseCategory.IN_RANGE -> R.string.glucose_in_range
    GlucoseCategory.HIGH -> R.string.glucose_high
    GlucoseCategory.VERY_HIGH -> R.string.glucose_very_high
}

@StringRes
fun StatusLevel.labelRes(): Int = when (this) {
    StatusLevel.INSUFFICIENT_DATA -> R.string.status_insufficient
    StatusLevel.OK -> R.string.status_ok
    StatusLevel.CAUTION -> R.string.status_caution
    StatusLevel.ALERT -> R.string.status_alert
    StatusLevel.URGENT -> R.string.status_urgent
}

@StringRes
fun DoseState.labelRes(): Int = when (this) {
    DoseState.UPCOMING -> R.string.dose_state_upcoming
    DoseState.DUE -> R.string.dose_state_due
    DoseState.TAKEN -> R.string.dose_state_taken
    DoseState.SKIPPED -> R.string.dose_state_skipped
    DoseState.SNOOZED -> R.string.dose_state_snoozed
}

@StringRes
fun KoraError.messageRes(): Int = when (this) {
    KoraError.NETWORK -> R.string.error_network
    KoraError.PERMISSION_DENIED -> R.string.error_permission
    KoraError.SESSION_EXPIRED -> R.string.error_session
    KoraError.NOT_FOUND -> R.string.error_not_found
    KoraError.VALIDATION -> R.string.error_validation
    KoraError.NOT_CONFIGURED -> R.string.error_not_configured
    KoraError.UNKNOWN -> R.string.error_unknown
}

@StringRes
fun UpdateFailure.messageRes(): Int = when (this) {
    UpdateFailure.OFFLINE -> R.string.update_fail_offline
    UpdateFailure.RATE_LIMITED -> R.string.update_fail_rate_limited
    UpdateFailure.NO_RELEASES -> R.string.update_fail_no_releases
    UpdateFailure.NO_APK -> R.string.update_fail_no_apk
    UpdateFailure.INVALID_VERSION -> R.string.update_fail_invalid_version
    UpdateFailure.DOWNLOAD_INCOMPLETE -> R.string.update_fail_incomplete
    UpdateFailure.CHECKSUM_MISMATCH -> R.string.update_fail_checksum
    UpdateFailure.NOT_AN_APK -> R.string.update_fail_not_apk
    UpdateFailure.NOT_NEWER -> R.string.update_fail_not_newer
    UpdateFailure.SIGNATURE_MISMATCH -> R.string.update_fail_signature
    UpdateFailure.FILE_MISSING -> R.string.update_fail_file
    UpdateFailure.HTTP_ERROR, UpdateFailure.PARSE_ERROR, UpdateFailure.STORAGE,
    UpdateFailure.CANCELLED, UpdateFailure.UNKNOWN,
    -> R.string.update_fail_generic
}

/** Mensaje de un error de campo con sus límites ("Entre 60 y 260"). */
@Composable
@ReadOnlyComposable
fun FieldIssue.message(): String = when (error) {
    FieldError.REQUIRED -> stringResource(R.string.error_required)
    FieldError.NOT_A_NUMBER -> stringResource(R.string.error_not_number)
    FieldError.OUT_OF_RANGE -> stringResource(R.string.error_range, min ?: 0, max ?: 0)
    FieldError.DIASTOLIC_NOT_BELOW_SYSTOLIC -> stringResource(R.string.error_diastolic_not_below)
    FieldError.IN_FUTURE -> stringResource(R.string.error_future)
    FieldError.TOO_LONG -> stringResource(R.string.error_too_long, max ?: 0)
    FieldError.DUPLICATE_TIME -> stringResource(R.string.error_duplicate_time)
    FieldError.LOW_NOT_BELOW_HIGH -> stringResource(R.string.error_low_not_below_high)
}

@Composable
@ReadOnlyComposable
fun BloodPressureCategory.visual(): StatusVisual {
    val c = Kora.colors
    return when (this) {
        BloodPressureCategory.NORMAL -> StatusVisual(stringResource(labelRes()), c.normal, StatusShape.CHECK)
        BloodPressureCategory.ELEVATED -> StatusVisual(stringResource(labelRes()), c.elevated, StatusShape.UP_ONE)
        BloodPressureCategory.STAGE_1 -> StatusVisual(stringResource(labelRes()), c.highStage1, StatusShape.UP_TWO)
        BloodPressureCategory.STAGE_2 -> StatusVisual(stringResource(labelRes()), c.highStage2, StatusShape.ALERT)
        BloodPressureCategory.SEVERE -> StatusVisual(stringResource(labelRes()), c.crisis, StatusShape.WARNING)
    }
}

@Composable
@ReadOnlyComposable
fun GlucoseCategory.visual(): StatusVisual {
    val c = Kora.colors
    return when (this) {
        GlucoseCategory.VERY_LOW -> StatusVisual(stringResource(labelRes()), c.crisis, StatusShape.WARNING)
        GlucoseCategory.LOW -> StatusVisual(stringResource(labelRes()), c.low, StatusShape.DOWN_ONE)
        GlucoseCategory.IN_RANGE -> StatusVisual(stringResource(labelRes()), c.normal, StatusShape.CHECK)
        GlucoseCategory.HIGH -> StatusVisual(stringResource(labelRes()), c.elevated, StatusShape.UP_ONE)
        GlucoseCategory.VERY_HIGH -> StatusVisual(stringResource(labelRes()), c.highStage2, StatusShape.ALERT)
    }
}

@Composable
@ReadOnlyComposable
fun StatusLevel.visual(): StatusVisual {
    val c = Kora.colors
    return when (this) {
        StatusLevel.INSUFFICIENT_DATA -> StatusVisual(stringResource(labelRes()), c.noData, StatusShape.NONE)
        StatusLevel.OK -> StatusVisual(stringResource(labelRes()), c.normal, StatusShape.CHECK)
        StatusLevel.CAUTION -> StatusVisual(stringResource(labelRes()), c.elevated, StatusShape.UP_ONE)
        StatusLevel.ALERT -> StatusVisual(stringResource(labelRes()), c.highStage2, StatusShape.ALERT)
        StatusLevel.URGENT -> StatusVisual(stringResource(labelRes()), c.crisis, StatusShape.WARNING)
    }
}

/** "hoy 8:10 a.m." o "28 oct, 8:10 a.m.". */
@Composable
@ReadOnlyComposable
fun whenLabel(at: Instant, today: LocalDate, zone: ZoneId): String {
    val zoned = at.atZone(zone)
    val time = KoraFormats.time(zoned.toLocalTime())
    return if (zoned.toLocalDate() == today) {
        stringResource(R.string.measured_today_at, time)
    } else {
        stringResource(R.string.measured_on, KoraFormats.shortDate(zoned.toLocalDate()), time)
    }
}
