package com.emmanuelquintana.kora.data.repository

import com.emmanuelquintana.kora.core.util.KoraClock
import com.emmanuelquintana.kora.core.util.KoraError
import com.emmanuelquintana.kora.core.util.KoraResult
import com.emmanuelquintana.kora.data.mapper.FirestoreMappers
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.data.model.DoseStatus
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.data.model.MedicationLog
import com.emmanuelquintana.kora.data.model.SyncState
import com.emmanuelquintana.kora.domain.usecase.StockCalculator
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Datos del usuario en Cloud Firestore bajo `users/{uid}/…`.
 *
 * - Listeners en tiempo real con metadatos (caché / pendiente de subir).
 * - Persistencia offline de Firestore: las escrituras se aplican localmente al
 *   instante y se sincronizan al recuperar la conexión. Por eso no se espera a
 *   que el servidor confirme; los rechazos llegan por [writeErrors].
 * - Un solo listener por colección, compartido entre todos los observadores
 *   (cambiar de postura del Fold no abre consultas nuevas).
 * - Nunca se registran valores clínicos en logs.
 */
class FirestoreRepository(
    private val firestore: FirebaseFirestore,
    private val uid: String,
    private val clock: KoraClock,
    private val scope: CoroutineScope,
) : KoraRepository {

    override val isDemo: Boolean = false

    private val userDoc: DocumentReference get() = firestore.collection(USERS).document(uid)
    private val glucoseCol: CollectionReference get() = userDoc.collection(GLUCOSE)
    private val pressureCol: CollectionReference get() = userDoc.collection(BLOOD_PRESSURE)
    private val medicationsCol: CollectionReference get() = userDoc.collection(MEDICATIONS)
    private val logsCol: CollectionReference get() = userDoc.collection(MEDICATION_LOGS)

    private val _writeErrors = MutableSharedFlow<KoraError>(extraBufferCapacity = 8)
    override val writeErrors: SharedFlow<KoraError> = _writeErrors.asSharedFlow()

    private val doseMutex = Mutex()

    private val glucoseFlow by lazy {
        glucoseCol.orderBy("measuredAt", Query.Direction.DESCENDING).limit(READINGS_LIMIT)
            .observe { doc, pending -> doc.data?.let { FirestoreMappers.glucose(doc.id, it, pending) } }
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)
    }

    private val pressureFlow by lazy {
        pressureCol.orderBy("measuredAt", Query.Direction.DESCENDING).limit(READINGS_LIMIT)
            .observe { doc, pending -> doc.data?.let { FirestoreMappers.bloodPressure(doc.id, it, pending) } }
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)
    }

    private val medicationsFlow by lazy {
        medicationsCol.orderBy("name")
            .observe { doc, pending -> doc.data?.let { FirestoreMappers.medication(doc.id, it, pending) } }
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)
    }

    private val logFlows = mutableMapOf<LocalDate, Flow<DataState<List<MedicationLog>>>>()

    override fun glucose(): Flow<DataState<List<GlucoseReading>>> = glucoseFlow
    override fun bloodPressure(): Flow<DataState<List<BloodPressureReading>>> = pressureFlow
    override fun medications(): Flow<DataState<List<Medication>>> = medicationsFlow

    override fun medicationLogs(since: LocalDate): Flow<DataState<List<MedicationLog>>> = synchronized(logFlows) {
        logFlows.getOrPut(since) {
            logsCol.whereGreaterThanOrEqualTo("scheduledDate", since.toString())
                .observe { doc, pending -> doc.data?.let { FirestoreMappers.medicationLog(doc.id, it, pending) } }
                .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)
        }
    }

    override fun newId(): String = glucoseCol.document().id

    override suspend fun saveGlucose(reading: GlucoseReading) = write(glucoseCol.document(reading.id).set(FirestoreMappers.toMap(reading)))
    override suspend fun deleteGlucose(id: String) = write(glucoseCol.document(id).delete())

    override suspend fun saveBloodPressure(reading: BloodPressureReading) =
        write(pressureCol.document(reading.id).set(FirestoreMappers.toMap(reading)))

    override suspend fun deleteBloodPressure(id: String) = write(pressureCol.document(id).delete())

    override suspend fun saveMedication(medication: Medication) =
        write(medicationsCol.document(medication.id).set(FirestoreMappers.toMap(medication)))

    override suspend fun deleteMedication(id: String) = write(medicationsCol.document(id).delete())

    override suspend fun getMedication(id: String): Medication? =
        readDocument(medicationsCol.document(id))?.let { doc ->
            doc.data?.let { FirestoreMappers.medication(doc.id, it, doc.metadata.hasPendingWrites()) }
        }

    override suspend fun getLog(id: String): MedicationLog? =
        readDocument(logsCol.document(id))?.let { doc ->
            doc.data?.let { FirestoreMappers.medicationLog(doc.id, it, doc.metadata.hasPendingWrites()) }
        }

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
        val previouslyApplied = existing?.unitsApplied ?: 0
        val units = when {
            status != DoseStatus.TAKEN -> 0
            previouslyApplied > 0 -> previouslyApplied
            else -> StockCalculator.unitsToDeduct(medication)
        }
        // Positivo = devolver stock (p. ej. de "Tomada" a "Omitida"); negativo = descontar.
        val stockDelta = previouslyApplied - units
        val log = MedicationLog(
            id = id,
            medicationId = medication.id,
            scheduledDate = date,
            scheduledTime = time,
            status = status,
            recordedAt = clock.now(),
            snoozedUntil = snoozedUntil,
            unitsApplied = units,
            syncState = SyncState.PENDING,
        )
        val batch = firestore.batch()
        batch.set(logsCol.document(id), FirestoreMappers.toMap(log))
        if (stockDelta != 0) {
            batch.update(
                medicationsCol.document(medication.id),
                mapOf("stockUnits" to FieldValue.increment(stockDelta.toLong()), "updatedAt" to FieldValue.serverTimestamp()),
            )
        }
        when (val result = write(batch.commit())) {
            is KoraResult.Success -> KoraResult.Success(log)
            is KoraResult.Failure -> result
        }
    }

    override suspend fun undoDose(logId: String): KoraResult<Unit> = doseMutex.withLock {
        val existing = getLog(logId) ?: return@withLock KoraResult.Success(Unit)
        val batch = firestore.batch()
        batch.delete(logsCol.document(logId))
        if (existing.unitsApplied > 0) {
            batch.update(
                medicationsCol.document(existing.medicationId),
                mapOf("stockUnits" to FieldValue.increment(existing.unitsApplied.toLong()), "updatedAt" to FieldValue.serverTimestamp()),
            )
        }
        write(batch.commit())
    }

    /**
     * Lanza la escritura sin esperar al servidor: con persistencia offline el
     * cambio ya es visible localmente. Si el servidor la rechaza después, se avisa
     * por [writeErrors].
     */
    private fun write(task: Task<Void>): KoraResult<Unit> {
        task.addOnFailureListener { error -> _writeErrors.tryEmit(error.toKoraError()) }
        return KoraResult.Success(Unit)
    }

    /** Lee primero del servidor con un límite corto y, si no hay red, de la caché local. */
    private suspend fun readDocument(ref: DocumentReference): DocumentSnapshot? {
        val fromServer = withTimeoutOrNull(READ_TIMEOUT_MS) {
            runCatching { ref.get(Source.DEFAULT).await() }.getOrNull()
        }
        val snapshot = fromServer ?: runCatching { ref.get(Source.CACHE).await() }.getOrNull()
        return snapshot?.takeIf { it.exists() }
    }

    private fun <T> Query.observe(map: (DocumentSnapshot, Boolean) -> T?): Flow<DataState<List<T>>> = callbackFlow {
        trySend(DataState.Loading)
        val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                trySend(DataState.Failed(error.toKoraError()))
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            val items = snapshot.documents.mapNotNull { doc -> map(doc, doc.metadata.hasPendingWrites()) }
            trySend(
                DataState.Ready(
                    value = items,
                    origin = if (snapshot.metadata.isFromCache) DataOrigin.CACHE else DataOrigin.SERVER,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    companion object {
        const val USERS = "users"
        const val GLUCOSE = "glucose"
        const val BLOOD_PRESSURE = "blood_pressure"
        const val MEDICATIONS = "medications"
        const val MEDICATION_LOGS = "medication_logs"

        private const val READINGS_LIMIT = 1_000L
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val READ_TIMEOUT_MS = 2_500L
    }
}

internal fun Throwable.toKoraError(): KoraError = when (this) {
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> KoraError.PERMISSION_DENIED
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> KoraError.SESSION_EXPIRED
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        -> KoraError.NETWORK
        FirebaseFirestoreException.Code.NOT_FOUND -> KoraError.NOT_FOUND
        FirebaseFirestoreException.Code.INVALID_ARGUMENT,
        FirebaseFirestoreException.Code.FAILED_PRECONDITION,
        -> KoraError.VALIDATION
        else -> KoraError.UNKNOWN
    }
    is FirebaseNetworkException -> KoraError.NETWORK
    else -> KoraError.UNKNOWN
}
