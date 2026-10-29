package com.emmanuelquintana.kora.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.domain.usecase.BloodPressureCategory
import com.emmanuelquintana.kora.domain.usecase.BloodPressureClassifier
import com.emmanuelquintana.kora.ui.components.labelRes
import com.emmanuelquintana.kora.ui.components.whenLabel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

fun pressureInPeriod(readings: List<BloodPressureReading>, period: ChartPeriod, now: Instant): List<BloodPressureReading> {
    val start = now.minus(Duration.ofDays(period.days.toLong()))
    return readings.filter { !it.measuredAt.isBefore(start) && !it.measuredAt.isAfter(now) }.sortedBy { it.measuredAt }
}

/**
 * Presión arterial: sistólica (círculos) y diastólica (cuadrados) en la misma
 * escala, con líneas de referencia en 130 y 80 mmHg. El pulso va en un panel
 * propio con su escala, para no mezclar unidades.
 */
@Composable
fun BloodPressureChart(
    readings: List<BloodPressureReading>,
    period: ChartPeriod,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val data = remember(readings, period, now) { pressureInPeriod(readings, period, now) }
    if (data.isEmpty()) {
        ChartEmpty(modifier)
        return
    }
    val start = remember(now, period) { now.minus(Duration.ofDays(period.days.toLong())) }
    val scale = remember(data) {
        AxisScale.around(data.flatMap { listOf(it.systolic, it.diastolic) }, floorAt = 60, ceilingAt = 160, step = 20)
    }
    val pulses = remember(data) { data.mapIndexedNotNull { i, r -> r.pulse?.let { i to it } } }
    val pulseScale = remember(pulses) { AxisScale.around(pulses.map { it.second }, floorAt = 50, ceilingAt = 110, step = 20) }
    val fractions = remember(data, start, now) { data.map { it.measuredAt.fractionIn(start, now) } }
    val segments = remember(data, period) { segmentsByGap(data, { it.measuredAt }, maxGapFor(period)) }
    val elevated = remember(data) {
        data.map { BloodPressureClassifier.classify(it.systolic, it.diastolic) >= BloodPressureCategory.STAGE_2 }
    }
    var selected by rememberSaveable(data.size, period) { mutableIntStateOf(data.lastIndex) }
    val selectedIndex = selected.coerceIn(0, data.lastIndex)

    val systolicColor = Kora.colors.seriesPrimary
    val diastolicColor = Kora.colors.seriesSecondary
    val pulseColor = Kora.colors.seriesTertiary
    val rule = Kora.colors.rule
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = ink, fontFeatureSettings = "tnum")
    val summary = stringResource(
        R.string.chart_summary_pressure,
        period.days,
        data.size,
        data.minOf { it.systolic },
        data.maxOf { it.systolic },
        data.minOf { it.diastolic },
        data.maxOf { it.diastolic },
    )
    val tapHandler: Modifier = Modifier.pointerInput(fractions) {
        detectTapGestures { tap ->
            val area = Rectangles.plot(size.width.toFloat(), density)
            nearestIndex(tap.x, fractions.map { area.xAt(it) })?.let { selected = it }
        }
    }

    val reading = data[selectedIndex]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Kora.spacing.xs)) {
        Text(
            text = stringResource(
                R.string.chart_selected_pressure,
                KoraFormats.number(reading.systolic),
                KoraFormats.number(reading.diastolic),
                stringResource(BloodPressureClassifier.classify(reading.systolic, reading.diastolic).labelRes()) +
                    " · " + whenLabel(reading.measuredAt, now.atZone(zone).toLocalDate(), zone),
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.heightIn(min = 24.dp),
        )
        Legend(systolicColor, diastolicColor)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .semantics { contentDescription = summary }
                .then(tapHandler),
        ) {
            val area = plotArea()
            drawYAxis(area, scale, measurer, axisStyle, rule)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            listOf(130f, 80f).forEach { reference ->
                val y = area.yAt(scale.normalize(reference))
                drawLine(ink.copy(alpha = 0.6f), Offset(area.left, y), Offset(area.right, y), 1.dp.toPx(), pathEffect = dash)
            }
            val sys = data.mapIndexed { i, r -> Offset(area.xAt(fractions[i]), area.yAt(scale.normalize(r.systolic.toFloat()))) }
            val dia = data.mapIndexed { i, r -> Offset(area.xAt(fractions[i]), area.yAt(scale.normalize(r.diastolic.toFloat()))) }
            drawSelection(sys[selectedIndex].x, area, ink)
            drawSegments(sys, segments, systolicColor)
            drawSegments(dia, segments, diastolicColor)
            data.indices.forEach { i ->
                val enlarged = i == selectedIndex || elevated[i]
                drawSymbol(PointSymbol.CIRCLE, sys[i], systolicColor, hollow = elevated[i], enlarged = enlarged)
                drawSymbol(PointSymbol.SQUARE, dia[i], diastolicColor, hollow = elevated[i], enlarged = enlarged)
            }
            drawXAxis(area, start, now, zone, KoraFormats::shortDate, measurer, axisStyle)
        }

        if (pulses.isNotEmpty()) {
            Text(
                text = stringResource(R.string.chart_pulse_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clearAndSetSemantics {}
                    .then(tapHandler),
            ) {
                val area = plotArea()
                drawYAxis(area, pulseScale, measurer, axisStyle, rule)
                val points = pulses.map { (i, bpm) -> Offset(area.xAt(fractions[i]), area.yAt(pulseScale.normalize(bpm.toFloat()))) }
                val pulseSegments = segmentsByGap(pulses, { data[it.first].measuredAt }, maxGapFor(period))
                drawSegments(points, pulseSegments, pulseColor.copy(alpha = 0.7f))
                pulses.forEachIndexed { p, (i, _) ->
                    drawSymbol(PointSymbol.DIAMOND, points[p], pulseColor, hollow = false, enlarged = i == selectedIndex)
                }
            }
        }
    }
}

@Composable
private fun Legend(systolic: Color, diastolic: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(Kora.spacing.md), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(PointSymbol.CIRCLE, systolic, stringResource(R.string.legend_systolic))
        LegendItem(PointSymbol.SQUARE, diastolic, stringResource(R.string.legend_diastolic))
    }
}

@Composable
private fun LegendItem(symbol: PointSymbol, color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(12.dp)) {
            drawSymbol(symbol, center, color, hollow = false, enlarged = false)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
