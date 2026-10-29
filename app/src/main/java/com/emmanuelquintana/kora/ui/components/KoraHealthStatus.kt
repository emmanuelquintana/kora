package com.emmanuelquintana.kora.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.designsystem.KoraDurations
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.domain.usecase.HealthStatus
import com.emmanuelquintana.kora.domain.usecase.MeasurementKind
import com.emmanuelquintana.kora.domain.usecase.StatusReason
import java.time.LocalDate
import java.time.ZoneId

/**
 * Semáforo de la hoja del día. Dice el nivel en palabras, qué lectura lo
 * origina y qué datos faltan. Nunca afirma "todo bien" con datos incompletos.
 */
@Composable
fun KoraHealthStatus(
    status: HealthStatus,
    today: LocalDate,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    onReasonClick: ((StatusReason) -> Unit)? = null,
) {
    val visual = status.level.visual()
    val motion = Kora.motion
    val tint by animateColorAsState(visual.colors.accent, motion.fade(KoraDurations.STANDARD), label = "statusTint")
    val windowDays = status.window.toDays().toInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Kora.spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusMark(visual.shape, tint, size = 28.dp)
            Spacer(Modifier.width(Kora.spacing.xs))
            AnimatedContent(
                targetState = visual.label,
                transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.exit()) },
                label = "statusLabel",
            ) { label ->
                Text(label, style = MaterialTheme.typography.headlineSmall, color = tint)
            }
        }
        status.reasons.forEach { reason ->
            val text = when (reason) {
                is StatusReason.Glucose -> stringResource(
                    R.string.status_reason_glucose,
                    KoraFormats.number(reason.reading.valueMgDl),
                    stringResource(reason.category.labelRes()),
                    whenLabel(reason.measuredAt, today, zone),
                )
                is StatusReason.BloodPressure -> stringResource(
                    R.string.status_reason_pressure,
                    KoraFormats.number(reason.reading.systolic),
                    KoraFormats.number(reason.reading.diastolic),
                    stringResource(reason.assessment.category.labelRes()),
                    whenLabel(reason.measuredAt, today, zone),
                )
            }
            val clickable = onReasonClick?.let { handler -> Modifier.clickable { handler(reason) } } ?: Modifier
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = clickable.heightIn(min = 24.dp),
            )
        }
        status.missing.sortedBy { it.ordinal }.forEach { kind ->
            Text(
                text = when (kind) {
                    MeasurementKind.GLUCOSE -> stringResource(R.string.status_missing_glucose, windowDays)
                    MeasurementKind.BLOOD_PRESSURE -> stringResource(R.string.status_missing_pressure, windowDays)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.status_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
