package com.emmanuelquintana.kora.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emmanuelquintana.kora.AppContainer
import com.emmanuelquintana.kora.core.auth.SignOutReason
import com.emmanuelquintana.kora.core.notifications.AlarmPrecision
import com.emmanuelquintana.kora.core.notifications.AlarmSpec
import com.emmanuelquintana.kora.core.notifications.KoraNotifications
import com.emmanuelquintana.kora.core.notifications.NotificationActionsReceiver
import com.emmanuelquintana.kora.core.update.InstallLaunch
import com.emmanuelquintana.kora.core.update.UpdateInfo
import com.emmanuelquintana.kora.core.update.UpdateState
import com.emmanuelquintana.kora.core.util.KoraError
import com.emmanuelquintana.kora.core.util.KoraResult
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.data.model.DoseStatus
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.model.HealthRanges
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.data.model.MedicationLog
import com.emmanuelquintana.kora.data.repository.DataOrigin
import com.emmanuelquintana.kora.data.repository.DataState
import com.emmanuelquintana.kora.data.repository.KoraRepository
import com.emmanuelquintana.kora.data.repository.Session
import com.emmanuelquintana.kora.data.repository.repository
import com.emmanuelquintana.kora.data.repository.valueOrNull
import com.emmanuelquintana.kora.domain.usecase.DoseEntry
import com.emmanuelquintana.kora.domain.usecase.DoseSchedule
import com.emmanuelquintana.kora.domain.usecase.HealthStatus
import com.emmanuelquintana.kora.domain.usecase.HealthStatusEvaluator
import com.emmanuelquintana.kora.domain.usecase.MeasurementKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Estado de sincronización agregado que muestra la UI. */
enum class SyncStatus {
    LOADING,

    /** Modo demostración: nada se sincroniza. */
    DEMO,
    SYNCED,

    /** Hay cambios guardados en el dispositivo pendientes de subir. */
    PENDING,

    /** Mostrando datos de la caché local (sin conexión). */
    OFFLINE,
    ERROR,
}

data class KoraUiState(
    val session: Session = Session.Initializing,
    val glucose: DataState<List<GlucoseReading>> = DataState.Loading,
    val bloodPressure: DataState<List<BloodPressureReading>> = DataState.Loading,
    val medications: DataState<List<Medication>> = DataState.Loading,
    val logs: DataState<List<MedicationLog>> = DataState.Loading,
    val now: Instant = Instant.EPOCH,
    val today: LocalDate = LocalDate.MIN,
    /** Tomas de hoy con su estado. */
    val agenda: List<DoseEntry> = emptyList(),
    /** Semáforo; null mientras cargan las lecturas. */
    val healthStatus: HealthStatus? = null,
    val ranges: HealthRanges = HealthRanges(),
    val sync: SyncStatus = SyncStatus.LOADING,
    val update: UpdateState = UpdateState.Idle,
    val alarmPrecision: AlarmPrecision = AlarmPrecision.EXACT,
    val notificationsAllowed: Boolean = true,
) {
    val isDemo: Boolean get() = session is Session.Demo
    val sessionExpired: Boolean get() = (session as? Session.SignedOut)?.reason == SignOutReason.SESSION_EXPIRED
}

sealed interface KoraEvent {
    data class ReadingSaved(val kind: MeasurementKind) : KoraEvent
    data class GlucoseDeleted(val reading: GlucoseReading) : KoraEvent
    data class BloodPressureDeleted(val reading: BloodPressureReading) : KoraEvent
    data class MedicationDeleted(val medication: Medication) : KoraEvent

    /** Toma registrada; la UI ofrece "Deshacer" con [log]. */
    data class DoseRecorded(val log: MedicationLog, val medicationName: String) : KoraEvent
    data object DoseUndone : KoraEvent
    data class Failure(val error: KoraError) : KoraEvent
}

private data class DataBundle(
    val session: Session,
    val glucose: DataState<List<GlucoseReading>>,
    val bloodPressure: DataState<List<BloodPressureReading>>,
    val medications: DataState<List<Medication>>,
    val logs: DataState<List<MedicationLog>>,
)

private data class Environment(
    val ranges: HealthRanges,
    val now: Instant,
    val update: UpdateState,
    val alarmPrecision: AlarmPrecision,
    val notificationsAllowed: Boolean,
)

/**
 * ViewModel de sesión con alcance de la Activity: un único estado para los
 * layouts compacto, Flex y expandido. Cambiar de postura no reinicia
 * consultas ni pierde la sección seleccionada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KoraViewModel(private val container: AppContainer) : ViewModel() {

    private val clock = container.clock
    private val events = Channel<KoraEvent>(Channel.BUFFERED)
    val eventFlow: Flow<KoraEvent> = events.receiveAsFlow()

    private val capabilities = MutableStateFlow(readCapabilities())

    private val repository: Flow<KoraRepository?> =
        container.session.session.map { it.repository }.distinctUntilChanged()

    /** Pulso de reloj alineado al minuto: actualiza "pendiente/atrasada" sin recargar datos. */
    private val minuteTicker: Flow<Instant> = flow {
        while (true) {
            val now = clock.now()
            emit(now)
            delay(MINUTE_MS - now.toEpochMilli() % MINUTE_MS)
        }
    }

    private val todayFlow: Flow<LocalDate> = minuteTicker.map { clock.today() }.distinctUntilChanged()

    private fun <T> fromRepository(select: (KoraRepository) -> Flow<DataState<T>>): Flow<DataState<T>> =
        repository.flatMapLatest { repo -> repo?.let(select) ?: flowOf(DataState.Loading) }

    private val logsFlow: Flow<DataState<List<MedicationLog>>> =
        combine(repository, todayFlow) { repo, today -> repo to today }
            .flatMapLatest { (repo, today) ->
                repo?.medicationLogs(today.minusDays(LOG_HISTORY_DAYS)) ?: flowOf(DataState.Loading)
            }

    private val dataBundle: Flow<DataBundle> = combine(
        container.session.session,
        fromRepository { it.glucose() },
        fromRepository { it.bloodPressure() },
        fromRepository { it.medications() },
        logsFlow,
    ) { session, glucose, pressure, medications, logs -> DataBundle(session, glucose, pressure, medications, logs) }

    private val environment: Flow<Environment> = combine(
        container.preferences.ranges,
        minuteTicker,
        container.updateManager.state,
        capabilities,
    ) { ranges, now, update, caps -> Environment(ranges, now, update, caps.first, caps.second) }

    val uiState: StateFlow<KoraUiState> = combine(dataBundle, environment) { data, env -> buildState(data, env) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), KoraUiState())

    init {
        container.updateManager.checkOnLaunchIfDue()
        viewModelScope.launch {
            repository.flatMapLatest { it?.writeErrors ?: emptyFlow() }.collect { events.send(KoraEvent.Failure(it)) }
        }
    }

    private fun buildState(data: DataBundle, env: Environment): KoraUiState {
        val zone = clock.zone()
        val today = env.now.atZone(zone).toLocalDate()
        val medications = data.medications.valueOrNull().orEmpty()
        val logs = data.logs.valueOrNull().orEmpty()
        val glucose = data.glucose.valueOrNull()
        val pressure = data.bloodPressure.valueOrNull()
        val status = if (glucose != null && pressure != null) {
            HealthStatusEvaluator.evaluate(glucose, pressure, env.ranges, env.now)
        } else {
            null
        }
        return KoraUiState(
            session = data.session,
            glucose = data.glucose,
            bloodPressure = data.bloodPressure,
            medications = data.medications,
            logs = data.logs,
            now = env.now,
            today = today,
            agenda = DoseSchedule.agenda(medications, logs, today, env.now, zone),
            healthStatus = status,
            ranges = env.ranges,
            sync = syncStatus(data),
            update = env.update,
            alarmPrecision = env.alarmPrecision,
            notificationsAllowed = env.notificationsAllowed,
        )
    }

    private fun syncStatus(data: DataBundle): SyncStatus {
        if (data.session is Session.Demo) return SyncStatus.DEMO
        val states = listOf(data.glucose, data.bloodPressure, data.medications, data.logs)
        return when {
            states.any { it is DataState.Failed } -> SyncStatus.ERROR
            states.any { it is DataState.Loading } -> SyncStatus.LOADING
            states.any { (it as DataState.Ready<*>).hasPendingWrites } -> SyncStatus.PENDING
            states.any { (it as DataState.Ready<*>).origin == DataOrigin.CACHE } -> SyncStatus.OFFLINE
            else -> SyncStatus.SYNCED
        }
    }

    // ── Acciones ─────────────────────────────────────────────────────────────

    private fun withRepository(block: suspend (KoraRepository) -> Unit) {
        viewModelScope.launch {
            val repo = container.session.currentRepository()
            if (repo == null) {
                events.send(KoraEvent.Failure(KoraError.SESSION_EXPIRED))
            } else {
                block(repo)
            }
        }
    }

    private suspend fun <T> KoraResult<T>.report(onSuccess: suspend (T) -> Unit) {
        when (this) {
            is KoraResult.Success -> onSuccess(value)
            is KoraResult.Failure -> events.send(KoraEvent.Failure(error))
        }
    }

    fun markDose(entry: DoseEntry, status: DoseStatus) = withRepository { repo ->
        val dose = entry.dose
        repo.recordDose(dose.medication, dose.date, dose.time, status).report { log ->
            KoraNotifications.cancelDose(container.application, AlarmSpec.doseKey(dose.medication.id, dose.date, dose.time))
            events.send(KoraEvent.DoseRecorded(log, dose.medication.name))
        }
    }

    fun snoozeDose(entry: DoseEntry) = withRepository { repo ->
        val dose = entry.dose
        val until = clock.now().plus(NotificationActionsReceiver.SNOOZE)
        repo.recordDose(dose.medication, dose.date, dose.time, DoseStatus.SNOOZED, until).report { log ->
            container.alarmScheduler.scheduleSnooze(AlarmSpec.from(dose.medication), dose.date, dose.time, until)
            events.send(KoraEvent.DoseRecorded(log, dose.medication.name))
        }
    }

    fun undoDose(logId: String) = withRepository { repo ->
        repo.undoDose(logId).report { events.send(KoraEvent.DoseUndone) }
    }

    fun deleteGlucose(reading: GlucoseReading) = withRepository { repo ->
        repo.deleteGlucose(reading.id).report { events.send(KoraEvent.GlucoseDeleted(reading)) }
    }

    fun restoreGlucose(reading: GlucoseReading) = withRepository { repo -> repo.saveGlucose(reading).report {} }

    fun deleteBloodPressure(reading: BloodPressureReading) = withRepository { repo ->
        repo.deleteBloodPressure(reading.id).report { events.send(KoraEvent.BloodPressureDeleted(reading)) }
    }

    fun restoreBloodPressure(reading: BloodPressureReading) = withRepository { repo -> repo.saveBloodPressure(reading).report {} }

    fun deleteMedication(medication: Medication) = withRepository { repo ->
        repo.deleteMedication(medication.id).report { events.send(KoraEvent.MedicationDeleted(medication)) }
    }

    fun restoreMedication(medication: Medication) = withRepository { repo -> repo.saveMedication(medication).report {} }

    fun setRanges(ranges: HealthRanges) {
        viewModelScope.launch { container.preferences.setRanges(ranges) }
    }

    fun checkForUpdates() = container.updateManager.checkNow()
    fun downloadUpdate(info: UpdateInfo) = container.updateManager.startDownload(info)
    fun cancelUpdateDownload() = container.updateManager.cancelDownload()
    fun prepareInstall(file: File): InstallLaunch = container.updateManager.prepareInstall(file)

    fun signOut() {
        viewModelScope.launch { container.session.signOut() }
    }

    /** Llamar al volver a primer plano: el usuario pudo cambiar permisos en Ajustes. */
    fun refreshCapabilities() {
        capabilities.value = readCapabilities()
        if (capabilities.value.first == AlarmPrecision.EXACT) container.alarmScheduler.rescheduleAll()
    }

    fun exactAlarmSettingsIntent() = container.alarmScheduler.exactAlarmSettingsIntent()

    private fun readCapabilities(): Pair<AlarmPrecision, Boolean> =
        container.alarmScheduler.precision to KoraNotifications.canPost(container.application)

    private companion object {
        const val MINUTE_MS = 60_000L
        const val STOP_TIMEOUT_MS = 5_000L
        const val LOG_HISTORY_DAYS = 30L
    }
}
