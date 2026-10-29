package com.emmanuelquintana.kora.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.domain.usecase.DoseEntry
import com.emmanuelquintana.kora.domain.usecase.DoseState
import com.emmanuelquintana.kora.domain.usecase.StockLevel
import com.emmanuelquintana.kora.domain.usecase.StockStatus
import java.time.DayOfWeek
import java.time.format.TextStyle

/**
 * Marca de toma en celda fija (tomada la idea del horario impreso):
 * llena = tomada, hueca = pendiente, anillo grueso = toca ahora,
 * tachada = omitida, punteada = pospuesta. Nunca depende solo del color.
 */
@Composable
fun DoseMark(state: DoseState, modifier: Modifier = Modifier) {
    val motion = Kora.motion
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val outline = MaterialTheme.colorScheme.outline
    val due = Kora.colors.elevated.accent
    val fill by animateFloatAsState(if (state == DoseState.TAKEN) 1f else 0f, motion.fade(), label = "doseFill")
    val ring by animateColorAsState(
        when (state) {
            DoseState.DUE -> due
            DoseState.TAKEN -> primary
            else -> outline
        },
        motion.fade(),
        label = "doseRing",
    )
    Canvas(modifier.size(28.dp)) {
        val radius = size.minDimension / 2f - 1.5.dp.toPx()
        val strokeWidth = if (state == DoseState.DUE) 3.dp.toPx() else 2.dp.toPx()
        val dashed = if (state == DoseState.SNOOZED) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
        drawCircle(color = ring, radius = radius, style = Stroke(width = strokeWidth, pathEffect = dashed))
        if (fill > 0f) {
            drawCircle(color = primary.copy(alpha = fill), radius = radius)
            val w = size.width
            val h = size.height
            drawLine(onPrimary.copy(alpha = fill), Offset(w * 0.30f, h * 0.52f), Offset(w * 0.44f, h * 0.66f), 2.5.dp.toPx(), StrokeCap.Round)
            drawLine(onPrimary.copy(alpha = fill), Offset(w * 0.44f, h * 0.66f), Offset(w * 0.71f, h * 0.36f), 2.5.dp.toPx(), StrokeCap.Round)
        }
        if (state == DoseState.SKIPPED) {
            drawLine(outline, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.78f, size.height * 0.22f), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/**
 * Agenda de tomas como columnas de horario: hora tabular | medicamento | marca.
 * Tocar la fila alterna "tomada"; el menú ofrece posponer u omitir.
 */
@Composable
fun KoraMedicationTimeline(
    entries: List<DoseEntry>,
    onToggleTaken: (DoseEntry) -> Unit,
    onSnooze: (DoseEntry) -> Unit,
    onSkip: (DoseEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.no_doses_today),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Kora.spacing.sm),
            )
            return@Column
        }
        entries.forEachIndexed { index, entry ->
            DoseRow(entry, onToggleTaken, onSnooze, onSkip)
            if (index < entries.lastIndex) HorizontalDivider(color = Kora.colors.rule)
        }
    }
}

@Composable
private fun DoseRow(
    entry: DoseEntry,
    onToggleTaken: (DoseEntry) -> Unit,
    onSnooze: (DoseEntry) -> Unit,
    onSkip: (DoseEntry) -> Unit,
) {
    val medication = entry.dose.medication
    val time = KoraFormats.time(entry.dose.time)
    val taken = entry.state == DoseState.TAKEN
    val stateLabel = stringResource(entry.state.labelRes())
    val actionLabel = if (taken) {
        stringResource(R.string.dose_undo_taken, medication.name, time)
    } else {
        stringResource(R.string.dose_mark_taken, medication.name, time)
    }
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressedScale(interaction)
    var menuOpen by rememberSaveable(entry.dose.id) { mutableStateOf(false) }
    val dimmed = entry.state == DoseState.SKIPPED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .toggleable(
                    value = taken,
                    interactionSource = interaction,
                    indication = ripple(),
                    role = Role.Checkbox,
                    onValueChange = { onToggleTaken(entry) },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = actionLabel
                    stateDescription = stateLabel
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = time,
                style = Kora.numerals.valueSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.width(96.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${medication.dose} ${medication.unit} · $stateLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (entry.state == DoseState.DUE) Kora.colors.elevated.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                DoseMark(entry.state)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.dose_more_actions, medication.name))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dose_snooze)) },
                    onClick = {
                        menuOpen = false
                        onSnooze(entry)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dose_skip)) },
                    onClick = {
                        menuOpen = false
                        onSkip(entry)
                    },
                )
            }
        }
    }
}

/**
 * Existencias como el grosor del taco: cada barra es ~3 días de tratamiento
 * (máximo 10 = un mes). Por agotarse en ámbar, sin existencias en rojo,
 * siempre con la cifra y la palabra al lado.
 */
@Composable
fun KoraStockIndicator(status: StockStatus, modifier: Modifier = Modifier) {
    val color = when (status.level) {
        StockLevel.OUT -> Kora.colors.highStage2.accent
        StockLevel.LOW -> Kora.colors.elevated.accent
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val empty = Kora.colors.rule
    val sheets = when {
        status.level == StockLevel.OUT -> 0
        status.daysRemaining == null -> 10
        else -> ((status.daysRemaining.coerceAtMost(30) + 2) / 3).coerceIn(1, 10)
    }
    val units = pluralStringResource(R.plurals.units_left, status.units, status.units)
    val days = status.daysRemaining?.let { pluralStringResource(R.plurals.days_left, it, it) }
    val levelWord = when (status.level) {
        StockLevel.OUT -> stringResource(R.string.stock_out)
        StockLevel.LOW -> stringResource(R.string.stock_low)
        else -> null
    }
    val text = listOfNotNull(units, days).joinToString(" · ")
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = listOfNotNull(levelWord, text).joinToString(". ") },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Kora.spacing.sm),
    ) {
        Canvas(Modifier.size(width = 28.dp, height = 30.dp)) {
            val barHeight = 2.dp.toPx()
            val gap = (size.height - barHeight * 10) / 9f
            for (i in 0 until 10) {
                val y = size.height - barHeight - i * (barHeight + gap)
                drawRoundRect(
                    color = if (i < sheets) color else empty,
                    topLeft = Offset(0f, y),
                    size = Size(size.width, barHeight),
                )
            }
        }
        Column {
            if (levelWord != null) {
                Text(levelWord, style = MaterialTheme.typography.labelLarge, color = color)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Texto del horario: "Diario · 8:00 a.m., 8:00 p.m." o "lun, mié, vie · 9:00 p.m.". */
@Composable
fun scheduleText(medication: Medication): String {
    val times = medication.times.joinToString(", ") { KoraFormats.time(it) }
    return if (medication.isDaily) {
        stringResource(R.string.medication_schedule_daily, times)
    } else {
        val days = DayOfWeek.values()
            .filter { it in medication.daysOfWeek }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, KoraFormats.locale).trimEnd('.') }
        stringResource(R.string.medication_schedule_days, days, times)
    }
}

/** Medicamento en la lista: nombre y dosis, horario, existencias y adherencia reciente. */
@Composable
fun KoraMedicationCard(
    medication: Medication,
    stock: StockStatus,
    adherence: Pair<Int, Int>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Kora.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Kora.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Kora.spacing.xs)) {
            Text(
                text = medication.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${medication.dose} ${medication.unit}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (medication.active) scheduleText(medication) else stringResource(R.string.medication_paused),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        KoraStockIndicator(stock)
        if (adherence != null && adherence.second > 0) {
            Text(
                text = stringResource(R.string.medication_adherence, adherence.first, adherence.second),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
