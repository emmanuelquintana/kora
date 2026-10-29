package com.emmanuelquintana.kora.ui.compact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.ui.components.KoraQuickEntryButton
import com.emmanuelquintana.kora.ui.navigation.KoraActions
import com.emmanuelquintana.kora.ui.navigation.KoraDestination
import com.emmanuelquintana.kora.ui.navigation.KoraShellState
import com.emmanuelquintana.kora.ui.screens.HistoryScreen
import com.emmanuelquintana.kora.ui.screens.MedicationsList
import com.emmanuelquintana.kora.ui.screens.ProfileActions
import com.emmanuelquintana.kora.ui.screens.ProfileScreen
import com.emmanuelquintana.kora.ui.screens.TodayScreen
import com.emmanuelquintana.kora.ui.state.KoraUiState

/** Registro rápido en la zona del pulgar, sobre la barra de navegación. */
@Composable
fun QuickEntryBar(actions: KoraActions, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Kora.spacing.gutterCompact, vertical = Kora.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Kora.spacing.sm),
    ) {
        KoraQuickEntryButton(
            icon = Icons.Rounded.Bloodtype,
            label = stringResource(R.string.quick_glucose),
            onClick = { actions.openGlucoseEntry(null) },
            modifier = Modifier.weight(1f),
        )
        KoraQuickEntryButton(
            icon = Icons.Rounded.MonitorHeart,
            label = stringResource(R.string.quick_pressure),
            onClick = { actions.openPressureEntry(null) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Pantalla exterior (y teléfonos): NavigationBar inferior, una sección a la vez
 * y el registro rápido siempre al alcance del pulgar en "Hoy".
 * Con [useRail] (ventana mediana) la navegación pasa a un riel lateral.
 */
@Composable
fun CompactScreen(
    state: KoraUiState,
    actions: KoraActions,
    shell: KoraShellState,
    profileActions: ProfileActions,
    installPermissionNeeded: Boolean,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    useRail: Boolean = false,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (shell.destination == KoraDestination.TODAY) QuickEntryBar(actions)
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        KoraDestination.entries.forEach { destination ->
                            val selected = destination == shell.destination
                            NavigationBarItem(
                                selected = selected,
                                onClick = { shell.destination = destination },
                                icon = { Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null) },
                                label = { Text(stringResource(destination.label)) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
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
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                val content = Modifier.widthIn(max = 720.dp)
                SectionContent(state, actions, shell, profileActions, installPermissionNeeded, padding, content)
            }
        }
    }
}

@Composable
private fun SectionContent(
    state: KoraUiState,
    actions: KoraActions,
    shell: KoraShellState,
    profileActions: ProfileActions,
    installPermissionNeeded: Boolean,
    padding: PaddingValues,
    modifier: Modifier,
) {
    when (shell.destination) {
        KoraDestination.TODAY -> TodayScreen(state, actions, modifier, contentPadding = padding)
        KoraDestination.HISTORY -> HistoryScreen(
            state = state,
            actions = actions,
            kind = shell.historyKind,
            onKindChange = { shell.historyKind = it },
            period = shell.period,
            onPeriodChange = { shell.period = it },
            listState = shell.historyListState,
            modifier = modifier,
            contentPadding = padding,
        )
        KoraDestination.MEDICATIONS -> MedicationsList(
            state = state,
            actions = actions,
            modifier = modifier.padding(horizontal = Kora.spacing.gutterCompact),
            contentPadding = padding,
        )
        KoraDestination.PROFILE -> ProfileScreen(state, profileActions, installPermissionNeeded, modifier, padding)
    }
}

