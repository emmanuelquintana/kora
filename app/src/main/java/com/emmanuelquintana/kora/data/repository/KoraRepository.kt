package com.emmanuelquintana.kora.data.repository

import com.emmanuelquintana.kora.core.util.KoraError
import com.emmanuelquintana.kora.core.util.KoraResult
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.data.model.DoseStatus
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.data.model.MedicationLog
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** De dónde viene lo que se muestra. */
enum class DataOrigin { SERVER, CACHE, DEMO }

/** Estado de una colección observada. */
sealed interface DataState<out T> {
    data object Loading : DataState<Nothing>

    data class Ready<T>(
        val value: T,
        val origin: DataOrigin,
        /** Hay cambios locales aún no confirmados por el servidor. */
        val hasPendingWrites: Boolean,
    ) : DataState<T>

    data class Failed(val error: KoraError) : DataState<Nothing>
}

fun <T> DataState<T>.valueOrNull(): T? = (this as? DataState.Ready<T>)?.value

/**
 * Contrato de datos de Kora. La UI y los ViewModels dependen solo de esta
 * interfaz; hay dos implementaciones: [FirestoreRepository] y [DemoRepository].
 *
 * Las escrituras regresan en cuanto el cambio queda aplicado localmente (también
 * sin conexión). Los fallos posteriores del servidor llegan por [writeErrors].
 */
interface KoraRepository {
    val isDemo: Boolean

    fun glucose(): Flow<DataState<List<GlucoseReading>>>
    fun bloodPressure(): Flow<DataState<List<BloodPressureReading>>>
    fun medications(): Flow<DataState<List<Medication>>>
    fun medicationLogs(since: LocalDate): Flow<DataState<List<MedicationLog>>>

    /** Errores de escrituras que el servidor rechazó después de aplicarse localmente. */
    val writeErrors: Flow<KoraError>

    /** Genera un ID de documento en el cliente (permite reintentar sin duplicar). */
    fun newId(): String

    suspend fun saveGlucose(reading: GlucoseReading): KoraResult<Unit>
    suspend fun deleteGlucose(id: String): KoraResult<Unit>

    suspend fun saveBloodPressure(reading: BloodPressureReading): KoraResult<Unit>
    suspend fun deleteBloodPressure(id: String): KoraResult<Unit>

    suspend fun saveMedication(medication: Medication): KoraResult<Unit>
    suspend fun deleteMedication(id: String): KoraResult<Unit>
    suspend fun getMedication(id: String): Medication?

    /**
     * Registra el estado de una toma programada. Idempotente: repetir la misma
     * acción no duplica el registro ni descuenta stock dos veces.
     */
    suspend fun recordDose(
        medication: Medication,
        date: LocalDate,
        time: LocalTime,
        status: DoseStatus,
        snoozedUntil: Instant? = null,
    ): KoraResult<MedicationLog>

    /** Deshace un registro accidental y devuelve el stock descontado. */
    suspend fun undoDose(logId: String): KoraResult<Unit>

    suspend fun getLog(id: String): MedicationLog?
}
