package com.emmanuelquintana.kora.data.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Estado de sincronización de un registro individual. */
enum class SyncState {
    /** Confirmado por el servidor. */
    SYNCED,

    /** Guardado en el dispositivo, pendiente de subir. */
    PENDING,

    /** Modo demostración: solo existe en memoria, nunca se sincroniza. */
    DEMO,
}

enum class GlucoseContext(val storageKey: String) {
    FASTING("fasting"),
    PRE_MEAL("pre_meal"),
    POST_MEAL_2H("post_meal_2h"),
    BEDTIME("bedtime"),
    ;

    companion object {
        fun fromStorage(key: String?): GlucoseContext? = entries.firstOrNull { it.storageKey == key }
    }
}

data class GlucoseReading(
    val id: String,
    val valueMgDl: Int,
    val context: GlucoseContext,
    val measuredAt: Instant,
    val note: String? = null,
    val createdAt: Instant = measuredAt,
    val syncState: SyncState = SyncState.SYNCED,
)

enum class Arm(val storageKey: String) {
    LEFT("left"),
    RIGHT("right"),
    ;

    companion object {
        fun fromStorage(key: String?): Arm? = entries.firstOrNull { it.storageKey == key }
    }
}

data class BloodPressureReading(
    val id: String,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val arm: Arm,
    val measuredAt: Instant,
    val note: String? = null,
    val createdAt: Instant = measuredAt,
    val syncState: SyncState = SyncState.SYNCED,
)

/**
 * Un medicamento con su horario diario.
 *
 * @property dose texto de la dosis tal como la indica la receta ("500", "1/2").
 * @property unit unidad de la dosis ("mg", "UI", "tableta").
 * @property times horas del día en que se toma.
 * @property daysOfWeek días en que aplica; los 7 días = diario.
 * @property stockUnits unidades restantes (tabletas, cápsulas, mL…).
 * @property unitsPerDose unidades que se descuentan del stock en cada toma.
 * @property lowStockDays avisar cuando el stock alcance para estos días o menos.
 */
data class Medication(
    val id: String,
    val name: String,
    val dose: String,
    val unit: String,
    val times: List<LocalTime>,
    val daysOfWeek: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    val stockUnits: Int,
    val unitsPerDose: Int = 1,
    val lowStockDays: Int = 7,
    val active: Boolean = true,
    val createdAt: Instant,
    val syncState: SyncState = SyncState.SYNCED,
) {
    val isDaily: Boolean get() = daysOfWeek.size == 7
}

enum class DoseStatus(val storageKey: String) {
    TAKEN("taken"),
    SKIPPED("skipped"),
    SNOOZED("snoozed"),
    ;

    companion object {
        fun fromStorage(key: String?): DoseStatus? = entries.firstOrNull { it.storageKey == key }
    }
}

/**
 * Registro de una toma programada. Su ID es determinista
 * ([doseId]), por lo que tocar "Tomada" dos veces escribe el mismo documento.
 *
 * @property unitsApplied unidades descontadas del stock por este registro (0 si no aplica).
 */
data class MedicationLog(
    val id: String,
    val medicationId: String,
    val scheduledDate: LocalDate,
    val scheduledTime: LocalTime,
    val status: DoseStatus,
    val recordedAt: Instant,
    val snoozedUntil: Instant? = null,
    val unitsApplied: Int = 0,
    val syncState: SyncState = SyncState.SYNCED,
) {
    companion object {
        /** ID estable de una toma: `{medicationId}_{yyyyMMdd}_{HHmm}`. */
        fun doseId(medicationId: String, date: LocalDate, time: LocalTime): String =
            String.format(
                Locale.ROOT,
                "%s_%04d%02d%02d_%02d%02d",
                medicationId, date.year, date.monthValue, date.dayOfMonth, time.hour, time.minute,
            )
    }
}

/**
 * Rangos configurables por la persona (idealmente con su médico).
 * Los valores por defecto son una referencia inicial, no un objetivo universal.
 */
data class HealthRanges(
    val glucoseLowMgDl: Int = DEFAULT_GLUCOSE_LOW,
    val glucoseHighMgDl: Int = DEFAULT_GLUCOSE_HIGH,
) {
    companion object {
        const val DEFAULT_GLUCOSE_LOW = 70
        const val DEFAULT_GLUCOSE_HIGH = 180
    }
}
