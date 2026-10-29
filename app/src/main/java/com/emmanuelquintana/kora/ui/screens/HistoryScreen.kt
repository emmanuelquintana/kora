package com.emmanuelquintana.kora.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.repository.DataState
import com.emmanuelquintana.kora.data.repository.valueOrNull
import com.emmanuelquintana.kora.domain.usecase.BloodPressureClassifier
import com.emmanuelquintana.kora.domain.usecase.GlucoseClassifier
import com.emmanuelquintana.kora.domain.usecase.MeasurementKind
import com.emmanuelquintana.kora.ui.charts.BloodPressureChart
import com.emmanuelquintana.kora.ui.charts.ChartPeriod
import com.emmanuelquintana.kora.ui.charts.GlucoseChart
import com.emmanuelquintana.kora.ui.charts.KoraChartCard
import com.emmanuelquintana.kora.ui.charts.glucoseValueRows
import com.emmanuelquintana.kora.ui.charts.pressureValueRows
import com.emmanuelquintana.kora.ui.components.KoraEmptyState
import com.emmanuelquintana.kora.ui.components.KoraLoadingState
import com.emmanuelquintana.kora.ui.components.StatusPill
import com.emmanuelquintana.kora.ui.components.labelRes
import com.emmanuelquintana.kora.ui.components.visual
import com.emmanuelquintana.kora.ui.navigation.KoraActions
import com.emmanuelquintana.kora.ui.state.KoraUiState
import java.time.LocalDate
import java.time.ZoneId

/** Lectura del historial, sin importar el tipo. */
sealed interface HistoryItem {
    val measuredAt: java.time.Instant

    data class Glucose(val reading: GlucoseReading) : HistoryItem {
        override val measuredAt: java.time.Instant get() = reading.measuredAt
    }

    data class Pressure(val reading: BloodPressureReading) : HistoryItem {
        override val measuredAt: java.time.Instant get() = reading.measuredAt
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KindSelector(kind: MeasurementKind, onKindChange: (MeasurementKind) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(MeasurementKind.GLUCOSE to R.string.glucose, MeasurementKind.BLOOD_PRESSURE to R.string.blood_pressure)
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = option == kind,
                onClick = { onKindChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
}

/** Gráfica del tipo seleccionado con su periodo y alternativa textual. */
@Composable
fun HistoryChart(
    state: KoraUiState,
    kind: MeasurementKind,
    period: ChartPeriod,
    onPeriodChange: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    when (kind) {
        MeasurementKind.GLUCOSE -> {
            val readings = state.glucose.valueOrNull().orEmpty()
            KoraChartCard(
                title = stringResource(R.string.chart_glucose_title),
                period = period,
                onPeriodChange = onPeriodChange,
                values = glucoseValueRows(readings, period, state.now, zone),
                modifier = modifier,
            ) { shown -> GlucoseChart(readings, state.ranges, shown, state.now, zone) }
        }
        MeasurementKind.BLOOD_PRESSURE -> {
            val readings = state.bloodPressure.valueOrNull().orEmpty()
            KoraChartCard(
                title = stringResource(R.string.chart_pressure_title),
                period = period,
                onPeriodChange = onPeriodChange,
                values = pressureValueRows(readings, period, state.now, zone),
                modifier = modifier,
            ) { shown -> BloodPressureChart(readings, shown, state.now, zone) }
        }
    }
}

/**
 * Historial cronológico agrupado por día. Tocar una lectura abre sus acciones
 * (editar / eliminar). La posición de scroll vive arriba en el árbol: no se
 * pierde al plegar o desplegar.
 */
@Composable
fun ReadingsList(
    state: KoraUiState,
    kind: MeasurementKind,
    listState: LazyListState,
    actions: KoraActions,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val zone = ZoneId.systemDefault()
    val loading = when (kind) {
        MeasurementKind.GLUCOSE -> state.glucose is DataState.Loading
        MeasurementKind.BLOOD_PRESSURE -> state.bloodPressure is DataState.Loading
    }
    val items: List<HistoryItem> = remember(state.glucose, state.bloodPressure, kind) {
        when (kind) {
            MeasurementKind.GLUCOSE -> state.glucose.valueOrNull().orEmpty().map { HistoryItem.Glucose(it) }
            MeasurementKind.BLOOD_PRESSURE -> state.bloodPressure.valueOrNull().orEmpty().map { HistoryItem.Pressure(it) }
        }.sortedByDescending { it.measuredAt }
    }
    val grouped = remember(items) { items.groupBy { it.measuredAt.atZone(zone).toLocalDate() } }
    var actionsFor by remember { mutableStateOf<HistoryItem?>(null) }

    LazyColumn(modifier = modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        if (header != null) item(key = "header") { header() }
        when {
            loading -> item(key = "loading") { KoraLoadingState(Modifier.padding(vertical = Kora.spacing.md)) }
            items.isEmpty() -> item(key = "empty") {
                KoraEmptyState(stringResource(R.string.history_empty), Modifier.padding(vertical = Kora.spacing.md))
            }
            else -> grouped.forEach { (date, dayItems) ->
                item(key = "day-$date") { DayHeader(date, state.today) }
                items(dayItems, key = { it.key() }) { item ->
                    Column(Modifier.animateItem()) {
                        HistoryRow(item, state, zone) { actionsFor = item }
                        HorizontalDivider(color = Kora.colors.rule)
                    }
                }
            }
        }
    }

    actionsFor?.let { item ->
        ReadingActionsDialog(
            title = stringResource(R.string.reading_actions_title, KoraFormats.shortDate(item.measuredAt.atZone(zone).toLocalDate())),
            onEdit = {
                actionsFor = null
                when (item) {
                    is HistoryItem.Glucose -> actions.openGlucoseEntry(item.reading)
                    is HistoryItem.Pressure -> actions.openPressureEntry(item.reading)
                }
            },
            onDelete = {
                actionsFor = null
                when (item) {
                    is HistoryItem.Glucose -> actions.deleteGlucose(item.reading)
                    is HistoryItem.Pressure -> actions.deletePressure(item.reading)
                }
            },
            onDismiss = { actionsFor = null },
        )
    }
}

private fun HistoryItem.key(): String = when (this) {
    is HistoryItem.Glucose -> "g-${reading.id}"
    is HistoryItem.Pressure -> "p-${reading.id}"
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    Text(
        text = if (date == today) stringResource(R.string.nav_today) else KoraFormats.longDate(date),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = Kora.spacing.lg, bottom = Kora.spacing.xxs)
            .semantics { heading() },
    )
}

@Composable
private fun HistoryRow(item: HistoryItem, state: KoraUiState, zone: ZoneId, onClick: () -> Unit) {
    val time = KoraFormats.time(item.measuredAt.atZone(zone).toLocalTime())
    val (value, unit, detail, visual) = when (item) {
        is HistoryItem.Glucose -> Quad(
            KoraFormats.number(item.reading.valueMgDl),
            stringResource(R.string.unit_mg_dl),
            stringResource(item.reading.context.labelRes()),
            GlucoseClassifier.classify(item.reading.valueMgDl, state.ranges).visual(),
        )
        is HistoryItem.Pressure -> Quad(
            "${item.reading.systolic}/${item.reading.diastolic}",
            stringResource(R.string.unit_mmhg),
            listOfNotNull(
                item.reading.pulse?.let { stringResource(R.string.pulse_value, it.toString()) },
                stringResource(item.reading.arm.labelRes(short = true)),
            ).joinToString(" · "),
            BloodPressureClassifier.classify(item.reading.systolic, item.reading.diastolic).visual(),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Kora.spacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Kora.spacing.md),
    ) {
        Text(time, style = Kora.numerals.valueSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Kora.spacing.xxs)) {
                Text(value, style = Kora.numerals.valueMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(unit, style = Kora.numerals.unit, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        StatusPill(visual)
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/** Historial en pantalla compacta: selector, gráfica y lista. */
@Composable
fun HistoryScreen(
    state: KoraUiState,
    actions: KoraActions,
    kind: MeasurementKind,
    onKindChange: (MeasurementKind) -> Unit,
    period: ChartPeriod,
    onPeriodChange: (ChartPeriod) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = Kora.spacing
    ReadingsList(
        state = state,
        kind = kind,
        listState = listState,
        actions = actions,
        modifier = modifier.padding(horizontal = spacing.gutterCompact),
        contentPadding = contentPadding,
        header = {
            Column(
                modifier = Modifier.padding(top = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(
                    stringResource(R.string.nav_history),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                KindSelector(kind, onKindChange)
                HistoryChart(state, kind, period, onPeriodChange)
            }
        },
    )
}

/** Ayuda para recordar la última pestaña del historial entre posturas. */
@Composable
fun rememberHistoryKind() = rememberSaveable { mutableStateOf(MeasurementKind.GLUCOSE) }
