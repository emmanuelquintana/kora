package com.emmanuelquintana.kora.ui.expanded

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.foldable.HingeSnapshot
import com.emmanuelquintana.kora.data.repository.valueOrNull
import com.emmanuelquintana.kora.domain.usecase.MeasurementKind
import com.emmanuelquintana.kora.ui.charts.ChartPeriod
import com.emmanuelquintana.kora.ui.charts.TrendSummary
import com.emmanuelquintana.kora.ui.components.DaySheetHead
import com.emmanuelquintana.kora.ui.components.SyncBadge
import com.emmanuelquintana.kora.ui.navigation.KoraActions
import com.emmanuelquintana.kora.ui.navigation.KoraDestination
import com.emmanuelquintana.kora.ui.navigation.KoraShellState
import com.emmanuelquintana.kora.ui.screens.DoseAgendaSection
import com.emmanuelquintana.kora.ui.screens.HistoryChart
import com.emmanuelquintana.kora.ui.screens.KindSelector
import com.emmanuelquintana.kora.ui.screens.LatestReadingsSection
import com.emmanuelquintana.kora.ui.screens.LowStockSection
import com.emmanuelquintana.kora.ui.screens.MedicationsList
import com.emmanuelquintana.kora.ui.screens.ProfileActions
import com.emmanuelquintana.kora.ui.screens.ProfileScreen
import com.emmanuelquintana.kora.ui.screens.ReadingsList
import com.emmanuelquintana.kora.ui.screens.TodayStatusSection
import com.emmanuelquintana.kora.ui.state.KoraUiState

private val RailWidth = 88.dp
private val PaneGutter = 24.dp

/**
 * Pantalla interior desplegada: NavigationRail + escritorio en dos paneles.
 * Izquierda (~45 %): agenda, existencias, últimas lecturas e historial.
 * Derecha (~55 %): gráficas, periodo y lectura de tendencias.
 * Si WindowManager reporta una bisagra vertical, la división cae exactamente
 * sobre ella; si no, se usa la proporción 45/55.
 */
@Composable
fun ExpandedDualPaneScreen(
    state: KoraUiState,
    actions: KoraActions,
    shell: KoraShellState,
    hinge: HingeSnapshot?,
    profileActions: ProfileActions,
    installPermissionNeeded: Boolean,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(Modifier.fillMaxSize()) {
            KoraRail(shell, actions)
            when (shell.destination) {
                KoraDestination.PROFILE -> Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    ProfileScreen(state, profileActions, installPermissionNeeded, Modifier.widthIn(max = 720.dp))
                }
                else -> DualPane(hinge, Modifier.weight(1f), left = { LeftPane(state, actions, shell) }, right = { RightPane(state, shell) })
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun KoraRail(shell: KoraShellState, actions: KoraActions) {
    NavigationRail(
        modifier = Modifier.width(RailWidth),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Kora.spacing.xs)) {
                RailAction(Icons.Rounded.Bloodtype, stringResource(R.string.glucose), stringResource(R.string.quick_glucose)) { actions.openGlucoseEntry(null) }
                RailAction(Icons.Rounded.MonitorHeart, stringResource(R.string.pressure_short), stringResource(R.string.quick_pressure)) {
                    actions.openPressureEntry(null)
                }
                Spacer(Modifier.height(Kora.spacing.md))
            }
        },
    ) {
        KoraDestination.entries.forEach { destination ->
            val selected = destination == shell.destination
            NavigationRailItem(
                selected = selected,
                onClick = { shell.destination = destination },
                icon = { Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.label)) },
            )
        }
    }
}

/** Acción de registro en el riel: botón tonal de 56 dp con etiqueta corta debajo. */
@Composable
private fun RailAction(icon: ImageVector, shortLabel: String, description: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
        },
    ) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.padding(top = Kora.spacing.xs).size(56.dp)) {
            Icon(icon, contentDescription = null)
        }
        Text(shortLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Divide el espacio por la bisagra real o, si no existe, en 45/55. */
@Composable
private fun DualPane(
    hinge: HingeSnapshot?,
    modifier: Modifier,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val total = maxWidth
        val fromHinge: Dp? = hinge?.takeIf { !it.isHorizontal }?.let { h ->
            // Coordenadas de ventana → espacio tras el riel.
            with(density) { h.bounds.left.toDp() } - RailWidth
        }
        val leftWidth = fromHinge?.takeIf { it > total * 0.3f && it < total * 0.7f } ?: (total * 0.45f)
        val gap = hinge?.takeIf { !it.isHorizontal && it.isOccluding }?.let { with(density) { it.bounds.width.toDp() } } ?: 0.dp
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(leftWidth).fillMaxHeight()) { left() }
            if (gap > 0.dp) Spacer(Modifier.width(gap)) else VerticalDivider(color = Kora.colors.rule)
            Box(Modifier.weight(1f).fillMaxHeight()) { right() }
        }
    }
}

@Composable
private fun LeftPane(state: KoraUiState, actions: KoraActions, shell: KoraShellState) {
    val padding = PaddingValues(horizontal = PaneGutter)
    when (shell.destination) {
        KoraDestination.TODAY -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = Kora.spacing.md),
        ) {
            DaySheetHead(state.today, compact = true, trailing = { SyncBadge(state.sync) })
            Spacer(Modifier.height(Kora.spacing.md))
            TodayStatusSection(state, actions)
            Spacer(Modifier.height(Kora.spacing.lg))
            DoseAgendaSection(state, actions)
            LowStockSection(state, actions, alwaysShow = true)
            Spacer(Modifier.height(Kora.spacing.lg))
            LatestReadingsSection(state, actions)
            Spacer(Modifier.height(Kora.spacing.xl))
        }
        KoraDestination.HISTORY -> ReadingsList(
            state = state,
            kind = shell.historyKind,
            listState = shell.historyListState,
            actions = actions,
            modifier = Modifier.padding(padding),
            header = {
                Column(Modifier.padding(top = Kora.spacing.md), verticalArrangement = Arrangement.spacedBy(Kora.spacing.md)) {
                    Text(stringResource(R.string.nav_history), style = MaterialTheme.typography.headlineMedium)
                    KindSelector(shell.historyKind, { shell.historyKind = it })
                }
            },
        )
        KoraDestination.MEDICATIONS -> MedicationsList(state, actions, Modifier.padding(padding))
        KoraDestination.PROFILE -> Unit
    }
}

@Composable
private fun RightPane(state: KoraUiState, shell: KoraShellState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PaneGutter, vertical = Kora.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Kora.spacing.xl),
    ) {
        val period = shell.period
        val onPeriod: (ChartPeriod) -> Unit = { shell.period = it }
        when (shell.destination) {
            KoraDestination.HISTORY -> HistoryChart(state, shell.historyKind, period, onPeriod)
            else -> {
                HistoryChart(state, MeasurementKind.GLUCOSE, period, onPeriod)
                HistoryChart(state, MeasurementKind.BLOOD_PRESSURE, period, onPeriod)
            }
        }
        TrendSummary(
            glucose = state.glucose.valueOrNull().orEmpty(),
            pressure = state.bloodPressure.valueOrNull().orEmpty(),
            ranges = state.ranges,
            period = period,
            now = state.now,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Kora.spacing.lg))
    }
}
