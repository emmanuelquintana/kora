package com.emmanuelquintana.kora.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.notifications.AlarmPrecision
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.data.repository.DataState
import com.emmanuelquintana.kora.data.repository.valueOrNull
import com.emmanuelquintana.kora.domain.usecase.BloodPressureCategory
import com.emmanuelquintana.kora.domain.usecase.DoseState
import com.emmanuelquintana.kora.domain.usecase.GlucoseCategory
import com.emmanuelquintana.kora.domain.usecase.StatusReason
import com.emmanuelquintana.kora.domain.usecase.StockCalculator
import com.emmanuelquintana.kora.domain.usecase.StockLevel
import com.emmanuelquintana.kora.ui.components.DaySheetHead
import com.emmanuelquintana.kora.ui.components.GlucoseVital
import com.emmanuelquintana.kora.ui.components.KoraAlertBanner
import com.emmanuelquintana.kora.ui.components.KoraHealthStatus
import com.emmanuelquintana.kora.ui.components.KoraLoadingState
import com.emmanuelquintana.kora.ui.components.KoraMedicationTimeline
import com.emmanuelquintana.kora.ui.components.KoraSectionHeader
import com.emmanuelquintana.kora.ui.components.KoraStockIndicator
import com.emmanuelquintana.kora.ui.components.PressureVital
import com.emmanuelquintana.kora.ui.components.SyncBadge
import com.emmanuelquintana.kora.ui.navigation.KoraActions
import com.emmanuelquintana.kora.ui.navigation.KoraDestination
import com.emmanuelquintana.kora.ui.state.KoraUiState
import java.time.ZoneId

/** Inicio en pantalla compacta: la hoja de hoy, de arriba abajo. */
@Composable
fun TodayScreen(
    state: KoraUiState,
    actions: KoraActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = Kora.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = spacing.gutterCompact, vertical = spacing.md),
    ) {
        DaySheetHead(today = state.today, trailing = { SyncBadge(state.sync) })
        Spacer(Modifier.height(spacing.lg))
        TodayStatusSection(state, actions)
        Spacer(Modifier.height(spacing.section))
        LatestReadingsSection(state, actions)
        Spacer(Modifier.height(spacing.section))
        DoseAgendaSection(state, actions)
        LowStockSection(state, actions)
        Spacer(Modifier.height(spacing.xl))
        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Semáforo + avisos críticos + permisos faltantes + modo demostración. */
@Composable
fun TodayStatusSection(state: KoraUiState, actions: KoraActions, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Kora.spacing.sm)) {
        if (state.isDemo) {
            KoraAlertBanner(
                colors = Kora.colors.noData,
                icon = Icons.Rounded.Science,
                title = stringResource(R.string.sync_demo),
                body = stringResource(R.string.demo_banner),
            )
        }
        val status = state.healthStatus
        if (status == null) {
            KoraLoadingState()
        } else {
            KoraHealthStatus(
                status = status,
                today = state.today,
                zone = zone,
                onReasonClick = { actions.navigate(KoraDestination.HISTORY) },
            )
            CriticalAdviceBanner(status.reasons, actions)
        }
        if (!state.notificationsAllowed && state.agenda.isNotEmpty()) {
            KoraAlertBanner(
                colors = Kora.colors.elevated,
                icon = Icons.Rounded.NotificationsOff,
                title = stringResource(R.string.profile_reminders),
                body = stringResource(R.string.profile_notifications_off),
                actionLabel = stringResource(R.string.profile_notifications_enable),
                onAction = actions.requestNotifications,
            )
        } else if (state.alarmPrecision == AlarmPrecision.INEXACT && state.agenda.isNotEmpty()) {
            KoraAlertBanner(
                colors = Kora.colors.noData,
                icon = Icons.Rounded.AlarmOff,
                title = stringResource(R.string.profile_reminders),
                body = stringResource(R.string.profile_exact_off),
                actionLabel = stringResource(R.string.profile_exact_enable),
                onAction = actions.openExactAlarmSettings,
            )
        }
    }
}

/** Aviso de seguridad para lecturas que requieren atención (presión muy alta, glucosa muy baja). */
@Composable
private fun CriticalAdviceBanner(reasons: List<StatusReason>, actions: KoraActions) {
    val severePressure = reasons.filterIsInstance<StatusReason.BloodPressure>()
        .firstOrNull { it.assessment.category == BloodPressureCategory.SEVERE }
    val veryLowGlucose = reasons.filterIsInstance<StatusReason.Glucose>()
        .firstOrNull { it.category == GlucoseCategory.VERY_LOW }
    if (veryLowGlucose != null) {
        KoraAlertBanner(
            colors = Kora.colors.crisis,
            icon = Icons.Rounded.Warning,
            title = stringResource(R.string.advice_very_low_title),
            body = stringResource(R.string.advice_very_low_body),
            actionLabel = stringResource(R.string.advice_remeasure),
            onAction = { actions.openGlucoseEntry(null) },
        )
    }
    if (severePressure != null) {
        KoraAlertBanner(
            colors = Kora.colors.crisis,
            icon = Icons.Rounded.Warning,
            title = stringResource(R.string.advice_severe_title),
            body = stringResource(R.string.advice_severe_body),
            actionLabel = stringResource(R.string.advice_remeasure),
            onAction = { actions.openPressureEntry(null) },
        )
    }
}

@Composable
fun LatestReadingsSection(state: KoraUiState, actions: KoraActions, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    Column(modifier.fillMaxWidth()) {
        KoraSectionHeader(stringResource(R.string.section_latest))
        if (state.glucose is DataState.Loading || state.bloodPressure is DataState.Loading) {
            KoraLoadingState()
            return@Column
        }
        val glucose = state.glucose.valueOrNull()?.firstOrNull()
        GlucoseVital(glucose, state.ranges, state.today, zone, onClick = { actions.openGlucoseEntry(null) })
        HorizontalDivider(color = Kora.colors.rule)
        val pressure = state.bloodPressure.valueOrNull()?.firstOrNull()
        PressureVital(pressure, state.today, zone, onClick = { actions.openPressureEntry(null) })
    }
}

@Composable
fun DoseAgendaSection(state: KoraUiState, actions: KoraActions, modifier: Modifier = Modifier) {
    val pending = state.agenda.count { it.state == DoseState.DUE || it.state == DoseState.UPCOMING }
    Column(modifier.fillMaxWidth()) {
        KoraSectionHeader(
            title = stringResource(R.string.section_doses_today),
            trailing = when {
                state.agenda.isEmpty() -> null
                pending == 0 -> stringResource(R.string.doses_all_done)
                else -> stringResource(R.string.doses_pending, pending)
            },
        )
        if (state.medications is DataState.Loading) {
            KoraLoadingState()
        } else {
            KoraMedicationTimeline(
                entries = state.agenda,
                onToggleTaken = actions.toggleDose,
                onSnooze = actions.snoozeDose,
                onSkip = actions.skipDose,
            )
        }
    }
}

/** Solo aparece si algún medicamento está por agotarse: espacio para lo que importa. */
@Composable
fun LowStockSection(state: KoraUiState, actions: KoraActions, modifier: Modifier = Modifier, alwaysShow: Boolean = false) {
    val medications = state.medications.valueOrNull().orEmpty().filter { it.active }
    val flagged: List<Medication> = if (alwaysShow) {
        medications
    } else {
        medications.filter { StockCalculator.status(it).level in setOf(StockLevel.LOW, StockLevel.OUT) }
    }
    if (flagged.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(Kora.spacing.section))
        KoraSectionHeader(stringResource(R.string.section_stock))
        flagged.forEachIndexed { index, medication ->
            Column(Modifier.padding(vertical = Kora.spacing.xs)) {
                Text(
                    text = "${medication.name} ${medication.dose} ${medication.unit}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                KoraStockIndicator(StockCalculator.status(medication))
            }
            if (index < flagged.lastIndex) HorizontalDivider(color = Kora.colors.rule)
        }
        androidx.compose.material3.TextButton(onClick = { actions.navigate(KoraDestination.MEDICATIONS) }) {
            Text(stringResource(R.string.medications_title))
        }
    }
}
