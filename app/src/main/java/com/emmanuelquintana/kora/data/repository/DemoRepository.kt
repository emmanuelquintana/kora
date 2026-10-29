package com.emmanuelquintana.kora.data.repository

import com.emmanuelquintana.kora.core.util.KoraClock
import com.emmanuelquintana.kora.core.util.KoraError
import com.emmanuelquintana.kora.core.util.KoraResult
import com.emmanuelquintana.kora.data.model.Arm
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.data.model.DoseStatus
import com.emmanuelquintana.kora.data.model.GlucoseContext
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.data.model.MedicationLog
import com.emmanuelquintana.kora.data.model.SyncState
import com.emmanuelquintana.kora.domain.usecase.DoseSchedule
import com.emmanuelquintana.kora.domain.usecase.StockCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.random.Random

/**
 * Modo demostración: datos FICTICIOS en memoria para evaluar la interfaz sin
 * Firebase. No se guarda nada en disco, nada se sincroniza y todo registro
 * lleva [SyncState.DEMO]. Al cerrar la app los cambios se pierden.
 */
class DemoRepository(private val clock: KoraClock) : KoraRepository {

    override val isDemo: Boolean = true

    private val glucoseState = MutableStateFlow<List<GlucoseReading>>(emptyList())
    private val pressureState = MutableStateFlow<List<BloodPressureReading>>(emptyList())
    private val medicationState = MutableStateFlow<List<Medication>>(emptyList())
    private val logState = MutableStateFlow<List<MedicationLog>>(emptyList())
    private val doseMutex = Mutex()

    init {
        seed()
    }

    override val writeErrors: Flow<KoraError> = emptyFlow()

    private fun <T> ready(value: T): DataState<T> = DataState.Ready(value, DataOrigin.DEMO, hasPendingWrites = false)

    override fun glucose(): Flow<DataState<List<GlucoseReading>>> =
        glucoseState.map { list -> ready(list.sortedByDescending { it.measuredAt }) }

    override fun bloodPressure(): Flow<DataState<List<BloodPressureReading>>> =
        pressureState.map { list -> ready(list.sortedByDescending { it.measuredAt }) }

    override fun medications(): Flow<DataState<List<Medication>>> =
        medicationState.map { list -> ready(list.sortedBy { it.name.lowercase() }) }

    override fun medicationLogs(since: LocalDate): Flow<DataState<List<MedicationLog>>> =
        logState.map { list -> ready(list.filter { !it.scheduledDate.isBefore(since) }) }

    override fun newId(): String = UUID.randomUUID().toString()

    override suspend fun saveGlucose(reading: GlucoseReading): KoraResult<Unit> {
        glucoseState.update { list -> list.filterNot { it.id == reading.id } + reading.copy(syncState = SyncState.DEMO) }
        return KoraResult.Success(Unit)
    }

    override suspend fun deleteGlucose(id: String): KoraResult<Unit> {
        glucoseState.update { list -> list.filterNot { it.id == id } }
        return KoraResult.Success(Unit)
    }

    override suspend fun saveBloodPressure(reading: BloodPressureReading): KoraResult<Unit> {
        pressureState.update { list -> list.filterNot { it.id == reading.id } + reading.copy(syncState = SyncState.DEMO) }
        return KoraResult.Success(Unit)
    }

    override suspend fun deleteBloodPressure(id: String): KoraResult<Unit> {
        pressureState.update { list -> list.filterNot { it.id == id } }
        return KoraResult.Success(Unit)
    }

    override suspend fun saveMedication(medication: Medication): KoraResult<Unit> {
        medicationState.update { list -> list.filterNot { it.id == medication.id } + medication.copy(syncState = SyncState.DEMO) }
        return KoraResult.Success(Unit)
    }

    override suspend fun deleteMedication(id: String): KoraResult<Unit> {
        medicationState.update { list -> list.filterNot { it.id == id } }
        return KoraResult.Success(Unit)
    }

    override suspend fun getMedication(id: String): Medication? = medicationState.value.firstOrNull { it.id == id }

    override suspend fun getLog(id: String): MedicationLog? = logState.value.firstOrNull { it.id == id }

    override suspend fun recordDose(
        medication: Medication,
        date: LocalDate,
        time: LocalTime,
        status: DoseStatus,
        snoozedUntil: Instant?,
    ): KoraResult<MedicationLog> = doseMutex.withLock {
        val id = MedicationLog.doseId(medication.id, date, time)
        val existing = getLog(id)
        if (existing != null && existing.status == status && status != DoseStatus.SNOOZED) {
            return@withLock KoraResult.Success(existing)
        }
        val current = getMedication(medication.id) ?: medication
        val previouslyApplied = existing?.unitsApplied ?: 0
        val units = when {
            status != DoseStatus.TAKEN -> 0
            previouslyApplied > 0 -> previouslyApplied
            else -> StockCalculator.unitsToDeduct(current)
        }
        val log = MedicationLog(id, medication.id, date, time, status, clock.now(), snoozedUntil, units, SyncState.DEMO)
        logState.update { list -> list.filterNot { it.id == id } + log }
        adjustStock(medication.id, previouslyApplied - units)
        KoraResult.Success(log)
    }

    override suspend fun undoDose(logId: String): KoraResult<Unit> = doseMutex.withLock {
        val existing = getLog(logId) ?: return@withLock KoraResult.Success(Unit)
        logState.update { list -> list.filterNot { it.id == logId } }
        adjustStock(existing.medicationId, existing.unitsApplied)
        KoraResult.Success(Unit)
    }

    private fun adjustStock(medicationId: String, delta: Int) {
        if (delta == 0) return
        medicationState.update { list ->
            list.map { if (it.id == medicationId) it.copy(stockUnits = it.stockUnits + delta) else it }
        }
    }

    /**
     * Genera ~90 días de lecturas ficticias con huecos irregulares (días sin
     * medir), algunas fuera de rango y una agenda de tomas de la última semana.
     * Semilla fija: la demostración es la misma en cada arranque.
     */
    private fun seed() {
        val random = Random(SEED)
        val zone = clock.zone()
        val today = clock.today()
        val now = clock.now()

        val metformina = Medication(
            id = "demo-metformina", name = "Metformina", dose = "850", unit = "mg",
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), stockUnits = 46, unitsPerDose = 1,
            lowStockDays = 7, createdAt = now.minusSeconds(DAY * 120), syncState = SyncState.DEMO,
        )
        val losartan = Medication(
            id = "demo-losartan", name = "Losartán", dose = "50", unit = "mg",
            times = listOf(LocalTime.of(9, 0)), stockUnits = 5, unitsPerDose = 1,
            lowStockDays = 7, createdAt = now.minusSeconds(DAY * 120), syncState = SyncState.DEMO,
        )
        val atorvastatina = Medication(
            id = "demo-atorvastatina", name = "Atorvastatina", dose = "20", unit = "mg",
            times = listOf(LocalTime.of(21, 30)),
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY),
            stockUnits = 22, unitsPerDose = 1, lowStockDays = 7,
            createdAt = now.minusSeconds(DAY * 60), syncState = SyncState.DEMO,
        )
        val medications = listOf(metformina, losartan, atorvastatina)
        medicationState.value = medications

        val glucose = mutableListOf<GlucoseReading>()
        val pressure = mutableListOf<BloodPressureReading>()
        for (daysAgo in 90 downTo 0) {
            val date = today.minusDays(daysAgo.toLong())
            // ~20 % de los días sin ninguna medición: los huecos son reales, no se interpolan.
            if (daysAgo > 0 && random.nextInt(100) < 20) continue

            val fastingAt = ZonedDateTime.of(date, LocalTime.of(7, random.nextInt(0, 50)), zone).toInstant()
            if (!fastingAt.isAfter(now)) {
                val value = 96 + random.nextInt(-12, 38) + if (random.nextInt(100) < 6) 70 else 0
                glucose += GlucoseReading("demo-g-$daysAgo-a", value, GlucoseContext.FASTING, fastingAt, syncState = SyncState.DEMO)
            }
            if (random.nextInt(100) < 55) {
                val postAt = ZonedDateTime.of(date, LocalTime.of(15, random.nextInt(0, 59)), zone).toInstant()
                if (!postAt.isAfter(now)) {
                    val value = 138 + random.nextInt(-25, 55) + if (random.nextInt(100) < 5) -80 else 0
                    glucose += GlucoseReading(
                        "demo-g-$daysAgo-b", value, GlucoseContext.POST_MEAL_2H, postAt,
                        note = if (value > 200) "Comida familiar" else null, syncState = SyncState.DEMO,
                    )
                }
            }
            if (random.nextInt(100) < 70) {
                val bpAt = ZonedDateTime.of(date, LocalTime.of(8, random.nextInt(10, 59)), zone).toInstant()
                if (!bpAt.isAfter(now)) {
                    val systolic = 124 + random.nextInt(-10, 22) + if (random.nextInt(100) < 4) 30 else 0
                    val diastolic = (systolic * 0.64).toInt() + random.nextInt(-4, 8)
                    pressure += BloodPressureReading(
                        "demo-p-$daysAgo", systolic, diastolic, 64 + random.nextInt(0, 22),
                        if (random.nextBoolean()) Arm.LEFT else Arm.RIGHT, bpAt, syncState = SyncState.DEMO,
                    )
                }
            }
        }
        glucoseState.value = glucose
        pressureState.value = pressure

        val logs = mutableListOf<MedicationLog>()
        for (daysAgo in 6 downTo 0) {
            val date = today.minusDays(daysAgo.toLong())
            medications.flatMap { DoseSchedule.dosesOn(it, date) }.forEach { dose ->
                val scheduledAt = ZonedDateTime.of(date, dose.time, zone).toInstant()
                if (scheduledAt.isAfter(now)) return@forEach
                // Hoy se dejan pendientes las tomas más recientes para que la agenda tenga trabajo.
                if (daysAgo == 0 && scheduledAt.isAfter(now.minusSeconds(3 * 3600))) return@forEach
                val status = if (random.nextInt(100) < 9) DoseStatus.SKIPPED else DoseStatus.TAKEN
                logs += MedicationLog(
                    dose.id, dose.medication.id, date, dose.time, status,
                    scheduledAt.plusSeconds(random.nextLong(0, 1800)),
                    unitsApplied = if (status == DoseStatus.TAKEN) 1 else 0, syncState = SyncState.DEMO,
                )
            }
        }
        logState.value = logs
    }

    private companion object {
        const val SEED = 20_261_029
        const val DAY = 86_400L
    }
}
