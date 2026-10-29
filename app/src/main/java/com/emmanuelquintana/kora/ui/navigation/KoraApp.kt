package com.emmanuelquintana.kora.ui.navigation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.foldable.KoraPosture
import com.emmanuelquintana.kora.core.foldable.rememberKoraPosture
import com.emmanuelquintana.kora.core.update.InstallLaunch
import com.emmanuelquintana.kora.data.model.DoseStatus
import com.emmanuelquintana.kora.data.repository.Session
import com.emmanuelquintana.kora.data.repository.valueOrNull
import com.emmanuelquintana.kora.domain.usecase.BloodPressureCategory
import com.emmanuelquintana.kora.domain.usecase.BloodPressureClassifier
import com.emmanuelquintana.kora.domain.usecase.DoseState
import com.emmanuelquintana.kora.domain.usecase.GlucoseCategory
import com.emmanuelquintana.kora.domain.usecase.GlucoseClassifier
import com.emmanuelquintana.kora.domain.usecase.MeasurementKind
import com.emmanuelquintana.kora.ui.charts.ChartPeriod
import com.emmanuelquintana.kora.ui.compact.CompactScreen
import com.emmanuelquintana.kora.ui.components.KoraLoadingState
import com.emmanuelquintana.kora.ui.components.messageRes
import com.emmanuelquintana.kora.ui.components.rememberKoraHaptics
import com.emmanuelquintana.kora.ui.expanded.ExpandedDualPaneScreen
import com.emmanuelquintana.kora.ui.flex.FlexModeScreen
import com.emmanuelquintana.kora.ui.screens.AuthActions
import com.emmanuelquintana.kora.ui.screens.AuthScreen
import com.emmanuelquintana.kora.ui.screens.ClinicalAdviceDialog
import com.emmanuelquintana.kora.ui.screens.GlucoseEntryForm
import com.emmanuelquintana.kora.ui.screens.GlucoseFormActions
import com.emmanuelquintana.kora.ui.screens.MedicationEditorForm
import com.emmanuelquintana.kora.ui.screens.MedicationFormActions
import com.emmanuelquintana.kora.ui.screens.PressureEntryForm
import com.emmanuelquintana.kora.ui.screens.PressureFormActions
import com.emmanuelquintana.kora.ui.screens.ProfileActions
import com.emmanuelquintana.kora.ui.state.AuthViewModel
import com.emmanuelquintana.kora.ui.state.BloodPressureEntryViewModel
import com.emmanuelquintana.kora.ui.state.GlucoseEntryViewModel
import com.emmanuelquintana.kora.ui.state.KoraEvent
import com.emmanuelquintana.kora.ui.state.KoraViewModel
import com.emmanuelquintana.kora.ui.state.KoraViewModelFactory
import com.emmanuelquintana.kora.ui.state.MedicationEditorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Estado de navegación compartido por TODAS las posturas. Vive por encima
 * del cambio de layout, así que plegar o desplegar conserva sección,
 * pestaña del historial, periodo y posición de scroll.
 */
class KoraShellState(
    destination: KoraDestination,
    historyKind: MeasurementKind,
    period: ChartPeriod,
    val historyListState: LazyListState,
) {
    var destination by mutableStateOf(destination)
    var historyKind by mutableStateOf(historyKind)
    var period by mutableStateOf(period)
}

@Composable
fun rememberKoraShellState(): KoraShellState {
    var destination by rememberSaveable { mutableStateOf(KoraDestination.TODAY) }
    var kind by rememberSaveable { mutableStateOf(MeasurementKind.GLUCOSE) }
    var period by rememberSaveable { mutableStateOf(ChartPeriod.MONTH) }
    val listState = rememberLazyListState()
    val shell = remember(listState) { KoraShellState(destination, kind, period, listState) }
    // Sincroniza con el estado guardado (sobrevive a la recreación y a la muerte del proceso).
    LaunchedEffect(shell.destination) { destination = shell.destination }
    LaunchedEffect(shell.historyKind) { kind = shell.historyKind }
    LaunchedEffect(shell.period) { period = shell.period }
    return shell
}

/** Raíz de la UI: decide entre carga, inicio de sesión o la app, y la postura. */
@Composable
fun KoraApp(requestedDestination: KoraDestination?, onRequestHandled: () -> Unit) {
    val viewModel: KoraViewModel = viewModel(factory = KoraViewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshCapabilities() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        when (state.session) {
            Session.Initializing -> Box(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(Kora.spacing.md),
            ) { KoraLoadingState() }
            is Session.SignedOut -> AuthRoute(sessionExpired = state.sessionExpired)
            is Session.Demo, is Session.SignedIn -> MainShell(viewModel, requestedDestination, onRequestHandled)
        }
    }
}

@Composable
private fun AuthRoute(sessionExpired: Boolean) {
    val authViewModel: AuthViewModel = viewModel(factory = KoraViewModelFactory)
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AuthScreen(
        state = authState,
        sessionExpired = sessionExpired,
        actions = AuthActions(
            onModeChange = authViewModel::setMode,
            onEmailChange = authViewModel::onEmailChange,
            onPasswordChange = authViewModel::onPasswordChange,
            onNameChange = authViewModel::onDisplayNameChange,
            onSubmit = authViewModel::submit,
            onGoogle = { authViewModel.signInWithGoogle(context) },
        ),
    )
}

private enum class Advice { SEVERE_PRESSURE, VERY_LOW_GLUCOSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    viewModel: KoraViewModel,
    requestedDestination: KoraDestination?,
    onRequestHandled: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val glucoseVm: GlucoseEntryViewModel = viewModel(factory = KoraViewModelFactory)
    val pressureVm: BloodPressureEntryViewModel = viewModel(factory = KoraViewModelFactory)
    val medicationVm: MedicationEditorViewModel = viewModel(factory = KoraViewModelFactory)
    val glucoseState by glucoseVm.uiState.collectAsStateWithLifecycle()
    val pressureState by pressureVm.uiState.collectAsStateWithLifecycle()
    val medicationState by medicationVm.uiState.collectAsStateWithLifecycle()
    val restTimer by pressureVm.restTimer.collectAsStateWithLifecycle()

    val shell = rememberKoraShellState()
    val posture = rememberKoraPosture()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberKoraHaptics()
    val context = LocalContext.current
    val resources = context.resources
    var sheet by rememberSaveable { mutableStateOf<EntrySheet?>(null) }
    var advice by rememberSaveable { mutableStateOf<Advice?>(null) }
    var installPermissionNeeded by rememberSaveable { mutableStateOf(false) }
    val valueFocus = remember { FocusRequester() }

    LaunchedEffect(requestedDestination) {
        if (requestedDestination != null) {
            shell.destination = requestedDestination
            onRequestHandled()
        }
    }
    BackHandler(enabled = sheet == null && shell.destination != KoraDestination.TODAY) {
        shell.destination = KoraDestination.TODAY
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshCapabilities()
    }

    fun startActivitySafely(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Sin pantalla del sistema disponible: el estado sigue visible en Perfil.
        }
    }

    // Eventos de la app → snackbars con "Deshacer" cuando aplica.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            val (message, undo) = when (event) {
                is KoraEvent.DoseRecorded -> {
                    val text = when (event.log.status) {
                        DoseStatus.TAKEN -> resources.getString(R.string.event_dose_taken, event.medicationName)
                        DoseStatus.SKIPPED -> resources.getString(R.string.event_dose_skipped, event.medicationName)
                        DoseStatus.SNOOZED -> resources.getString(R.string.event_dose_snoozed, event.medicationName)
                    }
                    text to { viewModel.undoDose(event.log.id) }
                }
                KoraEvent.DoseUndone -> resources.getString(R.string.event_dose_undone) to null
                is KoraEvent.GlucoseDeleted -> resources.getString(R.string.event_reading_deleted) to { viewModel.restoreGlucose(event.reading) }
                is KoraEvent.BloodPressureDeleted -> resources.getString(R.string.event_reading_deleted) to { viewModel.restoreBloodPressure(event.reading) }
                is KoraEvent.MedicationDeleted ->
                    resources.getString(R.string.event_medication_deleted, event.medication.name) to { viewModel.restoreMedication(event.medication) }
                is KoraEvent.ReadingSaved -> resources.getString(
                    if (event.kind == MeasurementKind.GLUCOSE) R.string.event_glucose_saved else R.string.event_pressure_saved,
                ) to null
                is KoraEvent.Failure -> resources.getString(event.error.messageRes()) to null
            }
            launch {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = if (undo != null) resources.getString(R.string.undo) else null,
                    duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) undo?.invoke()
            }
        }
    }

    // Lecturas guardadas: cierra la hoja, confirma con háptica y, si la lectura
    // requiere atención, muestra el aviso de seguridad.
    LaunchedEffect(glucoseVm) {
        glucoseVm.saved.collect { reading ->
            sheet = null
            haptics.confirm()
            if (GlucoseClassifier.classify(reading.valueMgDl, state.ranges) == GlucoseCategory.VERY_LOW) {
                advice = Advice.VERY_LOW_GLUCOSE
            }
            launch { snackbarHostState.showSnackbar(resources.getString(R.string.event_glucose_saved)) }
        }
    }
    LaunchedEffect(pressureVm) {
        pressureVm.saved.collect { reading ->
            sheet = null
            haptics.confirm()
            val category = BloodPressureClassifier.classify(reading.systolic, reading.diastolic)
            if (category == BloodPressureCategory.SEVERE) advice = Advice.SEVERE_PRESSURE
            val saved = resources.getString(R.string.event_pressure_saved)
            launch {
                if (category == BloodPressureCategory.STAGE_2) {
                    // Nivel 2: no interrumpe, pero recuerda confirmar con otra medición.
                    snackbarHostState.showSnackbar(
                        message = saved + ". " + resources.getString(R.string.advice_stage2_body),
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                } else {
                    snackbarHostState.showSnackbar(saved)
                }
            }
        }
    }
    LaunchedEffect(medicationVm) {
        medicationVm.saved.collect {
            sheet = null
            haptics.confirm()
            launch { snackbarHostState.showSnackbar(resources.getString(R.string.event_medication_saved)) }
        }
    }
    LaunchedEffect(Unit) {
        launch { glucoseVm.errors.collect { snackbarHostState.showSnackbar(resources.getString(it.messageRes())) } }
        launch { pressureVm.errors.collect { snackbarHostState.showSnackbar(resources.getString(it.messageRes())) } }
        launch { medicationVm.errors.collect { snackbarHostState.showSnackbar(resources.getString(it.messageRes())) } }
    }

    val actions = remember(viewModel) {
        KoraActions(
            openGlucoseEntry = { reading ->
                if (reading != null) glucoseVm.edit(reading) else if (glucoseVm.uiState.value.isEditing) glucoseVm.reset()
                sheet = EntrySheet.GLUCOSE
            },
            openPressureEntry = { reading ->
                if (reading != null) pressureVm.edit(reading) else if (pressureVm.uiState.value.isEditing) pressureVm.reset()
                sheet = EntrySheet.PRESSURE
            },
            openMedicationEditor = { medication ->
                if (medication != null) medicationVm.edit(medication) else if (medicationVm.uiState.value.isEditing) medicationVm.reset()
                sheet = EntrySheet.MEDICATION
            },
            toggleDose = { entry ->
                if (entry.state == DoseState.TAKEN) {
                    entry.log?.let { viewModel.undoDose(it.id) }
                } else {
                    viewModel.markDose(entry, DoseStatus.TAKEN)
                }
            },
            snoozeDose = viewModel::snoozeDose,
            skipDose = { viewModel.markDose(it, DoseStatus.SKIPPED) },
            deleteGlucose = viewModel::deleteGlucose,
            deletePressure = viewModel::deleteBloodPressure,
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startActivitySafely(
                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
            openExactAlarmSettings = { viewModel.exactAlarmSettingsIntent()?.let(::startActivitySafely) },
            navigate = { shell.destination = it },
        )
    }

    val profileActions = remember(viewModel) {
        ProfileActions(
            onSaveRanges = viewModel::setRanges,
            onRequestNotifications = actions.requestNotifications,
            onOpenExactAlarmSettings = actions.openExactAlarmSettings,
            onCheckUpdates = viewModel::checkForUpdates,
            onDownloadUpdate = viewModel::downloadUpdate,
            onCancelDownload = viewModel::cancelUpdateDownload,
            onInstall = { file ->
                when (val launch = viewModel.prepareInstall(file)) {
                    is InstallLaunch.NeedsPermission -> {
                        installPermissionNeeded = true
                        startActivitySafely(launch.settingsIntent)
                    }
                    is InstallLaunch.Ready -> {
                        installPermissionNeeded = false
                        startActivitySafely(launch.intent)
                    }
                    InstallLaunch.FileMissing -> viewModel.checkForUpdates()
                }
            },
            onSignOut = viewModel::signOut,
        )
    }

    val pressureActions = remember(pressureVm) {
        PressureFormActions(
            onSystolicChange = pressureVm::onSystolicChange,
            onDiastolicChange = pressureVm::onDiastolicChange,
            onPulseChange = pressureVm::onPulseChange,
            onArmChange = pressureVm::onArmChange,
            onNoteChange = pressureVm::onNoteChange,
            onMeasuredAtChange = pressureVm::onMeasuredAtChange,
            onSubmit = pressureVm::submit,
        )
    }

    when (posture) {
        is KoraPosture.Tabletop -> FlexModeScreen(
            hinge = posture.hinge,
            entryState = pressureState,
            entryActions = pressureActions,
            restTimer = restTimer,
            onStartRest = pressureVm::startRest,
            onCancelRest = pressureVm::cancelRest,
            lastReading = state.bloodPressure.valueOrNull()?.firstOrNull(),
            today = state.today,
            snackbarHostState = snackbarHostState,
        )
        is KoraPosture.Expanded -> ExpandedDualPaneScreen(
            state, actions, shell, posture.hinge, profileActions, installPermissionNeeded, snackbarHostState,
        )
        is KoraPosture.Book -> ExpandedDualPaneScreen(
            state, actions, shell, posture.hinge, profileActions, installPermissionNeeded, snackbarHostState,
        )
        KoraPosture.Medium -> CompactScreen(
            state, actions, shell, profileActions, installPermissionNeeded, snackbarHostState, useRail = true,
        )
        KoraPosture.Compact -> CompactScreen(
            state, actions, shell, profileActions, installPermissionNeeded, snackbarHostState,
        )
    }

    // Hoja de registro: el borrador vive en su ViewModel, así que cerrarla o
    // plegar el teléfono no pierde lo escrito.
    val current = sheet
    // En modo Flex el formulario de presión ya es la pantalla: no se abre como hoja.
    val showSheet = current != null && !(posture is KoraPosture.Tabletop && current == EntrySheet.PRESSURE)
    if (showSheet && current != null) {
        run {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = sheetState) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Kora.spacing.gutterCompact)
                        .navigationBarsPadding(),
                ) {
                    when (current) {
                        EntrySheet.GLUCOSE -> GlucoseEntryForm(
                            state = glucoseState,
                            actions = GlucoseFormActions(
                                onValueChange = glucoseVm::onValueChange,
                                onContextChange = glucoseVm::onContextChange,
                                onNoteChange = glucoseVm::onNoteChange,
                                onMeasuredAtChange = glucoseVm::onMeasuredAtChange,
                                onSubmit = glucoseVm::submit,
                            ),
                            valueFocus = valueFocus,
                        )
                        EntrySheet.PRESSURE -> PressureEntryForm(pressureState, pressureActions, systolicFocus = valueFocus)
                        EntrySheet.MEDICATION -> MedicationEditorForm(
                            state = medicationState,
                            actions = MedicationFormActions(
                                onNameChange = medicationVm::onNameChange,
                                onDoseChange = medicationVm::onDoseChange,
                                onUnitChange = medicationVm::onUnitChange,
                                onAddTime = medicationVm::addTime,
                                onRemoveTime = medicationVm::removeTime,
                                onToggleDay = medicationVm::toggleDay,
                                onStockChange = medicationVm::onStockChange,
                                onAddStock = { medicationVm.addStock(30) },
                                onUnitsPerDoseChange = medicationVm::onUnitsPerDoseChange,
                                onLowStockDaysChange = medicationVm::onLowStockDaysChange,
                                onActiveChange = medicationVm::onActiveChange,
                                onSubmit = medicationVm::submit,
                                onDelete = medicationState.editingId
                                    ?.let { id -> state.medications.valueOrNull()?.firstOrNull { it.id == id } }
                                    ?.let { medication ->
                                        {
                                            sheet = null
                                            medicationVm.reset()
                                            viewModel.deleteMedication(medication)
                                        }
                                    },
                            ),
                        )
                    }
                }
                // Enfoca el valor principal: el teclado numérico aparece sin un toque extra.
                if (current != EntrySheet.MEDICATION) {
                    LaunchedEffect(current) {
                        delay(250)
                        runCatching { valueFocus.requestFocus() }
                    }
                }
            }
        }
    }

    when (advice) {
        Advice.SEVERE_PRESSURE -> ClinicalAdviceDialog(
            title = stringResource(R.string.advice_severe_title),
            body = stringResource(R.string.advice_severe_body),
            onRemeasure = {
                advice = null
                pressureVm.startRest()
                sheet = EntrySheet.PRESSURE
            },
            onDismiss = { advice = null },
        )
        Advice.VERY_LOW_GLUCOSE -> ClinicalAdviceDialog(
            title = stringResource(R.string.advice_very_low_title),
            body = stringResource(R.string.advice_very_low_body),
            onRemeasure = {
                advice = null
                sheet = EntrySheet.GLUCOSE
            },
            onDismiss = { advice = null },
        )
        null -> Unit
    }
}
