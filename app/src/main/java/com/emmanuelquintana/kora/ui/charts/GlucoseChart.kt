package com.emmanuelquintana.kora.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.model.HealthRanges
import com.emmanuelquintana.kora.domain.usecase.GlucoseCategory
import com.emmanuelquintana.kora.domain.usecase.GlucoseClassifier
import com.emmanuelquintana.kora.ui.components.labelRes
import com.emmanuelquintana.kora.ui.components.whenLabel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Lecturas de glucosa visibles en el periodo, en orden cronológico. */
fun glucoseInPeriod(readings: List<GlucoseReading>, period: ChartPeriod, now: Instant): List<GlucoseReading> {
    val start = now.minus(Duration.ofDays(period.days.toLong()))
    return readings.filter { !it.measuredAt.isBefore(start) && !it.measuredAt.isAfter(now) }.sortedBy { it.measuredAt }
}

/**
 * Gráfica de glucosa en Canvas: franja de TU rango configurado, curva por
 * tramos (no une huecos reales), puntos fuera de rango huecos y más grandes,
 * selección por toque y descripción textual para TalkBack.
 */
@Composable
fun GlucoseChart(
    readings: List<GlucoseReading>,
    ranges: HealthRanges,
    period: ChartPeriod,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val data = remember(readings, period, now) { glucoseInPeriod(readings, period, now) }
    if (data.isEmpty()) {
        ChartEmpty(modifier)
        return
    }
    val start = remember(now, period) { now.minus(Duration.ofDays(period.days.toLong())) }
    val scale = remember(data, ranges) {
        AxisScale.around(
            values = data.map { it.valueMgDl } + listOf(ranges.glucoseLowMgDl, ranges.glucoseHighMgDl),
            floorAt = 50,
            ceilingAt = 250,
            step = 50,
        )
    }
    val fractions = remember(data, start, now) { data.map { it.measuredAt.fractionIn(start, now) } }
    val segments = remember(data, period) { segmentsByGap(data, { it.measuredAt }, maxGapFor(period)) }
    val outOfRange = remember(data, ranges) {
        data.map { GlucoseClassifier.classify(it.valueMgDl, ranges) != GlucoseCategory.IN_RANGE }
    }
    var selected by rememberSaveable(data.size, period) { mutableIntStateOf(data.lastIndex) }
    val selectedIndex = selected.coerceIn(0, data.lastIndex)

    val series = Kora.colors.seriesPrimary
    val alert = Kora.colors.highStage2.accent
    val rule = Kora.colors.rule
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val bandFill = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = ink, fontFeatureSettings = "tnum")
    val bandLabel = stringResource(R.string.chart_reference_band, ranges.glucoseLowMgDl, ranges.glucoseHighMgDl)
    val summary = stringResource(
        R.string.chart_summary_glucose,
        period.days,
        data.size,
        data.minOf { it.valueMgDl },
        data.maxOf { it.valueMgDl },
        outOfRange.count { it },
    )

    val reading = data[selectedIndex]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Kora.spacing.xs)) {
        Text(
            text = stringResource(
                R.string.chart_selected_glucose,
                KoraFormats.number(reading.valueMgDl),
                stringResource(reading.context.labelRes()),
                whenLabel(reading.measuredAt, now.atZone(zone).toLocalDate(), zone),
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.heightIn(min = 24.dp),
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .semantics { contentDescription = summary }
                .pointerInput(fractions) {
                    detectTapGestures { tap ->
                        val area = Rectangles.plot(size.width.toFloat(), density)
                        nearestIndex(tap.x, fractions.map { area.xAt(it) })?.let { selected = it }
                    }
                },
        ) {
            val area = plotArea()
            drawYAxis(area, scale, measurer, axisStyle, rule)

            // Franja de referencia configurada por la persona.
            val bandTop = area.yAt(scale.normalize(ranges.glucoseHighMgDl.toFloat()))
            val bandBottom = area.yAt(scale.normalize(ranges.glucoseLowMgDl.toFloat()))
            drawRect(bandFill, topLeft = Offset(area.left, bandTop), size = androidx.compose.ui.geometry.Size(area.width, bandBottom - bandTop))
            val bandText = measurer.measure(bandLabel, axisStyle)
            drawText(bandText, topLeft = Offset(area.left + 6.dp.toPx(), bandTop + 4.dp.toPx()))

            val points = data.mapIndexed { i, r -> Offset(area.xAt(fractions[i]), area.yAt(scale.normalize(r.valueMgDl.toFloat()))) }
            drawSelection(points[selectedIndex].x, area, ink)
            drawSegments(points, segments, series)
            points.forEachIndexed { i, point ->
                drawSymbol(
                    symbol = PointSymbol.CIRCLE,
                    center = point,
                    color = if (outOfRange[i]) alert else series,
                    hollow = outOfRange[i],
                    enlarged = i == selectedIndex || outOfRange[i],
                )
            }
            drawXAxis(area, start, now, zone, KoraFormats::shortDate, measurer, axisStyle)
        }
    }
}

@Composable
internal fun ChartEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.chart_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Misma geometría que [plotArea] fuera de un DrawScope (para los toques). */
internal object Rectangles {
    fun plot(width: Float, density: Float): androidx.compose.ui.geometry.Rect =
        androidx.compose.ui.geometry.Rect(40f * density, 10f * density, width - 10f * density, 0f)
}
