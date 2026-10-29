package com.emmanuelquintana.kora.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.model.HealthRanges
import com.emmanuelquintana.kora.domain.usecase.BloodPressureClassifier
import com.emmanuelquintana.kora.domain.usecase.GlucoseClassifier
import java.time.LocalDate
import java.time.ZoneId

/**
 * Signo vital como fila editorial (no tarjeta flotante): etiqueta, valor
 * grande condensado con su unidad, estado en palabra + forma y contexto.
 */
@Composable
fun KoraVitalCard(
    title: String,
    value: String?,
    unit: String,
    detail: String,
    visual: StatusVisual?,
    spokenDescription: String,
    modifier: Modifier = Modifier,
    emptyText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val motion = Kora.motion
    val clickModifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = 72.dp)
            .padding(vertical = Kora.spacing.sm)
            .clearAndSetSemantics { contentDescription = spokenDescription },
        verticalArrangement = Arrangement.spacedBy(Kora.spacing.xxs),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value == null) {
            Text(
                text = emptyText ?: stringResource(R.string.no_data_dash),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Kora.spacing.xs)) {
            // La lectura nueva entra con una escala mínima (0.95 → 1); el número no "salta".
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (fadeIn(motion.enter()) + scaleIn(motion.enter(), initialScale = 0.95f)) togetherWith fadeOut(motion.exit())
                },
                label = "vitalValue",
            ) { shown ->
                Text(shown, style = Kora.numerals.valueLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = unit,
                style = Kora.numerals.unit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            if (visual != null) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    StatusPill(visual, Modifier.padding(bottom = 8.dp))
                }
            }
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Fila de la última glucosa. */
@Composable
fun GlucoseVital(
    reading: GlucoseReading?,
    ranges: HealthRanges,
    today: LocalDate,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val title = stringResource(R.string.glucose)
    val unit = stringResource(R.string.unit_mg_dl)
    if (reading == null) {
        val empty = stringResource(R.string.no_glucose_yet)
        KoraVitalCard(title, null, unit, "", null, "$title. $empty", modifier, empty, onClick)
        return
    }
    val visual = GlucoseClassifier.classify(reading.valueMgDl, ranges).visual()
    val value = KoraFormats.number(reading.valueMgDl)
    val detail = stringResource(reading.context.labelRes()) + " · " + whenLabel(reading.measuredAt, today, zone)
    KoraVitalCard(
        title = title,
        value = value,
        unit = unit,
        detail = detail,
        visual = visual,
        spokenDescription = "$title $value $unit, ${visual.label}. $detail",
        modifier = modifier,
        onClick = onClick,
    )
}

/** Fila de la última presión arterial (sistólica/diastólica y pulso diferenciados). */
@Composable
fun PressureVital(
    reading: BloodPressureReading?,
    today: LocalDate,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val title = stringResource(R.string.blood_pressure)
    val unit = stringResource(R.string.unit_mmhg)
    if (reading == null) {
        val empty = stringResource(R.string.no_pressure_yet)
        KoraVitalCard(title, null, unit, "", null, "$title. $empty", modifier, empty, onClick)
        return
    }
    val visual = BloodPressureClassifier.classify(reading.systolic, reading.diastolic).visual()
    val value = "${KoraFormats.number(reading.systolic)}/${KoraFormats.number(reading.diastolic)}"
    val pulse = reading.pulse?.let { stringResource(R.string.pulse_value, KoraFormats.number(it)) }
    val detail = listOfNotNull(
        pulse,
        stringResource(reading.arm.labelRes(short = true)),
        whenLabel(reading.measuredAt, today, zone),
    ).joinToString(" · ")
    KoraVitalCard(
        title = title,
        value = value,
        unit = unit,
        detail = detail,
        visual = visual,
        spokenDescription = "$title: ${reading.systolic} sobre ${reading.diastolic} milímetros de mercurio, ${visual.label}. $detail",
        modifier = modifier,
        onClick = onClick,
    )
}
