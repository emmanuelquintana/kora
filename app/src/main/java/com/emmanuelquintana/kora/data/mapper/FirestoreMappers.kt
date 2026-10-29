package com.emmanuelquintana.kora.data.mapper

import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.Arm
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.data.model.DoseStatus
import com.emmanuelquintana.kora.data.model.GlucoseContext
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.data.model.MedicationLog
import com.emmanuelquintana.kora.data.model.SyncState
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Conversión manual modelo ⇄ documento. Sin reflexión: compatible con R8 y
 * tolerante a documentos incompletos (un documento inválido se descarta, no
 * tumba la lista). Los nombres de campo coinciden con firestore.rules.
 */
object FirestoreMappers {

    internal fun Instant.toTimestamp(): Timestamp = Timestamp(epochSecond, nano)

    private fun Any?.toInstantOrNull(): Instant? =
        (this as? Timestamp)?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }

    private fun Any?.toIntOrNull(): Int? = (this as? Number)?.toInt()

    private fun Map<String, Any?>.string(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

    private fun syncState(pending: Boolean) = if (pending) SyncState.PENDING else SyncState.SYNCED

    // ── Glucosa ──────────────────────────────────────────────────────────────
    fun toMap(reading: GlucoseReading): Map<String, Any?> = mapOf(
        "valueMgDl" to reading.valueMgDl.toLong(),
        "context" to reading.context.storageKey,
        "measuredAt" to reading.measuredAt.toTimestamp(),
        "note" to reading.note?.takeIf { it.isNotBlank() },
        "createdAt" to reading.createdAt.toTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun glucose(id: String, data: Map<String, Any?>, pending: Boolean): GlucoseReading? {
        val value = data["valueMgDl"].toIntOrNull() ?: return null
        val context = GlucoseContext.fromStorage(data["context"] as? String) ?: return null
        val measuredAt = data["measuredAt"].toInstantOrNull() ?: return null
        return GlucoseReading(
            id = id,
            valueMgDl = value,
            context = context,
            measuredAt = measuredAt,
            note = data.string("note"),
            createdAt = data["createdAt"].toInstantOrNull() ?: measuredAt,
            syncState = syncState(pending),
        )
    }

    // ── Presión arterial ─────────────────────────────────────────────────────
    fun toMap(reading: BloodPressureReading): Map<String, Any?> = mapOf(
        "systolic" to reading.systolic.toLong(),
        "diastolic" to reading.diastolic.toLong(),
        "pulse" to reading.pulse?.toLong(),
        "arm" to reading.arm.storageKey,
        "measuredAt" to reading.measuredAt.toTimestamp(),
        "note" to reading.note?.takeIf { it.isNotBlank() },
        "createdAt" to reading.createdAt.toTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun bloodPressure(id: String, data: Map<String, Any?>, pending: Boolean): BloodPressureReading? {
        val systolic = data["systolic"].toIntOrNull() ?: return null
        val diastolic = data["diastolic"].toIntOrNull() ?: return null
        val arm = Arm.fromStorage(data["arm"] as? String) ?: return null
        val measuredAt = data["measuredAt"].toInstantOrNull() ?: return null
        return BloodPressureReading(
            id = id,
            systolic = systolic,
            diastolic = diastolic,
            pulse = data["pulse"].toIntOrNull(),
            arm = arm,
            measuredAt = measuredAt,
            note = data.string("note"),
            createdAt = data["createdAt"].toInstantOrNull() ?: measuredAt,
            syncState = syncState(pending),
        )
    }

    // ── Medicamentos ─────────────────────────────────────────────────────────
    fun toMap(medication: Medication): Map<String, Any?> = mapOf(
        "name" to medication.name.trim(),
        "dose" to medication.dose.trim(),
        "unit" to medication.unit.trim(),
        "times" to medication.times.sorted().map(KoraFormats::storageTime),
        "daysOfWeek" to medication.daysOfWeek.map { it.value.toLong() }.sorted(),
        "stockUnits" to medication.stockUnits.toLong(),
        "unitsPerDose" to medication.unitsPerDose.toLong(),
        "lowStockDays" to medication.lowStockDays.toLong(),
        "active" to medication.active,
        "createdAt" to medication.createdAt.toTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun medication(id: String, data: Map<String, Any?>, pending: Boolean): Medication? {
        val name = data.string("name") ?: return null
        val times = (data["times"] as? List<*>)
            ?.mapNotNull { (it as? String)?.let(KoraFormats::parseStorageTime) }
            ?.sorted()
            ?: return null
        val days = (data["daysOfWeek"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt()?.takeIf { d -> d in 1..7 }?.let(DayOfWeek::of) }
            ?.toSet()
            ?: DayOfWeek.values().toSet()
        return Medication(
            id = id,
            name = name,
            dose = data.string("dose").orEmpty(),
            unit = data.string("unit").orEmpty(),
            times = times,
            daysOfWeek = days,
            stockUnits = data["stockUnits"].toIntOrNull() ?: 0,
            unitsPerDose = data["unitsPerDose"].toIntOrNull()?.coerceAtLeast(1) ?: 1,
            lowStockDays = data["lowStockDays"].toIntOrNull() ?: 7,
            active = data["active"] as? Boolean ?: true,
            createdAt = data["createdAt"].toInstantOrNull() ?: Instant.EPOCH,
            syncState = syncState(pending),
        )
    }

    // ── Registro de tomas ────────────────────────────────────────────────────
    fun toMap(log: MedicationLog): Map<String, Any?> = mapOf(
        "medicationId" to log.medicationId,
        "scheduledDate" to log.scheduledDate.toString(),
        "scheduledTime" to KoraFormats.storageTime(log.scheduledTime),
        "status" to log.status.storageKey,
        "recordedAt" to log.recordedAt.toTimestamp(),
        "snoozedUntil" to log.snoozedUntil?.toTimestamp(),
        "unitsApplied" to log.unitsApplied.toLong(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun medicationLog(id: String, data: Map<String, Any?>, pending: Boolean): MedicationLog? {
        val medicationId = data.string("medicationId") ?: return null
        val date = data.string("scheduledDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val time = data.string("scheduledTime")?.let(KoraFormats::parseStorageTime) ?: return null
        val status = DoseStatus.fromStorage(data["status"] as? String) ?: return null
        return MedicationLog(
            id = id,
            medicationId = medicationId,
            scheduledDate = date,
            scheduledTime = time,
            status = status,
            recordedAt = data["recordedAt"].toInstantOrNull() ?: Instant.EPOCH,
            snoozedUntil = data["snoozedUntil"].toInstantOrNull(),
            unitsApplied = data["unitsApplied"].toIntOrNull() ?: 0,
            syncState = syncState(pending),
        )
    }
}
