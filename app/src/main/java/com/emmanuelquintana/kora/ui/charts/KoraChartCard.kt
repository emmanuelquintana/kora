package com.emmanuelquintana.kora.ui.charts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.model.HealthRanges
import com.emmanuelquintana.kora.ui.components.labelRes
import com.emmanuelquintana.kora.ui.components.whenLabel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Selector de periodo 7 / 30 / 90 días (SegmentedButton de Material 3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelector(period: ChartPeriod, onPeriodChange: (ChartPeriod) -> Unit, modifier: Modifier = Modifier) {
    val options = ChartPeriod.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == period,
                onClick = { onPeriodChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(R.string.period_days, option.days))
            }
        }
    }
}

/**
 * Contenedor de gráfica: título, periodo, la gráfica y su alternativa textual
 * ("Ver valores"). El cambio de periodo es un fundido corto, sin desplazamiento.
 */
@Composable
fun KoraChartCard(
    title: String,
    period: ChartPeriod,
    onPeriodChange: (ChartPeriod) -> Unit,
    values: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    chart: @Composable (ChartPeriod) -> Unit,
) {
    val motion = Kora.motion
    var showValues by rememberSaveable(title) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Kora.spacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        PeriodSelector(period, onPeriodChange)
        AnimatedContent(
            targetState = period,
            transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.exit()) },
            label = "chartPeriod",
        ) { shown -> chart(shown) }
        if (values.isNotEmpty()) {
            TextButton(onClick = { showValues = !showValues }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (showValues) R.string.chart_hide_values else R.string.chart_show_values))
            }
            AnimatedVisibility(
                visible = showValues,
                enter = fadeIn(motion.fade()) + expandVertically(motion.enter()),
                exit = fadeOut(motion.exit()) + shrinkVertically(motion.exit()),
            ) {
                Column {
                    values.forEachIndexed { index, (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Kora.spacing.xs)
                                .semantics(mergeDescendants = true) {},
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(value, style = Kora.numerals.valueSmall, color = MaterialTheme.colorScheme.onBackground)
                        }
                        if (index < values.lastIndex) HorizontalDivider(color = Kora.colors.rule)
                    }
                }
            }
        }
    }
}

/** Filas de texto para la alternativa accesible de la gráfica de glucosa (más recientes primero). */
@Composable
fun glucoseValueRows(readings: List<GlucoseReading>, period: ChartPeriod, now: Instant, zone: ZoneId): List<Pair<String, String>> {
    val today = now.atZone(zone).toLocalDate()
    return glucoseInPeriod(readings, period, now).asReversed().take(MAX_ROWS).map {
        (whenLabel(it.measuredAt, today, zone) + " · " + stringResource(it.context.labelRes())) to
            "${KoraFormats.number(it.valueMgDl)} ${stringResource(R.string.unit_mg_dl)}"
    }
}

@Composable
fun pressureValueRows(readings: List<BloodPressureReading>, period: ChartPeriod, now: Instant, zone: ZoneId): List<Pair<String, String>> {
    val today = now.atZone(zone).toLocalDate()
    return pressureInPeriod(readings, period, now).asReversed().take(MAX_ROWS).map {
        val pulse = it.pulse?.let { bpm -> " · ${KoraFormats.number(bpm)} ${stringResource(R.string.unit_bpm)}" }.orEmpty()
        whenLabel(it.measuredAt, today, zone) to "${it.systolic}/${it.diastolic}$pulse"
    }
}

private const val MAX_ROWS = 40

/**
 * Lectura descriptiva de tendencias del periodo. Describe los datos; no
 * interpreta ni recomienda.
 */
@Composable
fun TrendSummary(
    glucose: List<GlucoseReading>,
    pressure: List<BloodPressureReading>,
    ranges: HealthRanges,
    period: ChartPeriod,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val current = glucoseInPeriod(glucose, period, now)
    val currentPressure = pressureInPeriod(pressure, period, now)
    val previousPressure = pressureInPeriod(pressure, period, now.minus(Duration.ofDays(period.days.toLong())))
    val g = TrendAnalyzer.glucose(current, ranges)
    val p = TrendAnalyzer.pressure(currentPressure, previousPressure)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Kora.spacing.xs)) {
        Text(
            stringResource(R.string.trend_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        val enoughGlucose = g != null && g.count >= TrendAnalyzer.MIN_READINGS
        val enoughPressure = p != null && p.count >= TrendAnalyzer.MIN_READINGS
        if (!enoughGlucose && !enoughPressure) {
            Text(stringResource(R.string.trend_not_enough), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (g != null && enoughGlucose) {
            Text(
                stringResource(R.string.trend_glucose_in_range, g.inRange * 100 / g.count, g.inRange, g.count),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(
                    R.string.trend_glucose_average,
                    g.average,
                    g.fastingAverage?.let { "$it ${stringResource(R.string.unit_mg_dl)}" } ?: stringResource(R.string.no_data_dash),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (p != null && enoughPressure) {
            Text(
                stringResource(R.string.trend_pressure_average, p.systolicAverage, p.diastolicAverage, p.count),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            p.systolicDeltaVsPrevious?.let { delta ->
                Text(
                    text = when {
                        delta >= 3 -> stringResource(R.string.trend_vs_previous_up, delta)
                        delta <= -3 -> stringResource(R.string.trend_vs_previous_down, -delta)
                        else -> stringResource(R.string.trend_vs_previous_same)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Text(stringResource(R.string.trend_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
