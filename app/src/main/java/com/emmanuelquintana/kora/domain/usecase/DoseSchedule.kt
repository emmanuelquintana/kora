package com.emmanuelquintana.kora.domain.usecase

import com.emmanuelquintana.kora.data.model.DoseStatus
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.data.model.MedicationLog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ScheduledDose(
    val medication: Medication,
    val date: LocalDate,
    val time: LocalTime,
) {
    val id: String get() = MedicationLog.doseId(medication.id, date, time)
}

enum class DoseState {
    /** Aún no es la hora. */
    UPCOMING,

    /** Ya pasó la hora y no hay registro. */
    DUE,

    TAKEN,
    SKIPPED,

    /** Pospuesta y el aplazamiento sigue vigente. */
    SNOOZED,
}

data class DoseEntry(
    val dose: ScheduledDose,
    val state: DoseState,
    val log: MedicationLog?,
)

/** Cálculo de horarios: qué toca hoy y cuándo es la siguiente alarma. */
object DoseSchedule {

    fun dosesOn(medication: Medication, date: LocalDate): List<ScheduledDose> {
        if (!medication.active || date.dayOfWeek !in medication.daysOfWeek) return emptyList()
        return medication.times.sorted().map { ScheduledDose(medication, date, it) }
    }

    /**
     * Siguiente instante (estrictamente posterior a [after]) en que suena la toma
     * de las [time] de [medication]. Respeta días de la semana y cambios de horario:
     * si la hora no existe por el adelanto de horario, `ZonedDateTime.of` la mueve
     * a la primera hora válida.
     */
    fun nextOccurrence(medication: Medication, time: LocalTime, after: ZonedDateTime): ZonedDateTime? {
        if (!medication.active) return null
        return nextOccurrence(medication.daysOfWeek, time, after)
    }

    fun nextOccurrence(days: Set<DayOfWeek>, time: LocalTime, after: ZonedDateTime): ZonedDateTime? {
        if (days.isEmpty()) return null
        val zone = after.zone
        for (offset in 0L..7L) {
            val date = after.toLocalDate().plusDays(offset)
            if (date.dayOfWeek !in days) continue
            val candidate = ZonedDateTime.of(date, time, zone)
            if (candidate.isAfter(after)) return candidate
        }
        return null
    }

    /** Agenda de un día combinando horario y registros. */
    fun agenda(
        medications: List<Medication>,
        logs: List<MedicationLog>,
        date: LocalDate,
        now: Instant,
        zone: ZoneId,
    ): List<DoseEntry> {
        val logsById = logs.associateBy { it.id }
        return medications
            .flatMap { dosesOn(it, date) }
            .sortedWith(compareBy({ it.time }, { it.medication.name.lowercase() }))
            .map { dose ->
                val log = logsById[dose.id]
                DoseEntry(dose, stateOf(dose, log, now, zone), log)
            }
    }

    fun stateOf(dose: ScheduledDose, log: MedicationLog?, now: Instant, zone: ZoneId): DoseState {
        when (log?.status) {
            DoseStatus.TAKEN -> return DoseState.TAKEN
            DoseStatus.SKIPPED -> return DoseState.SKIPPED
            DoseStatus.SNOOZED -> if (log.snoozedUntil?.isAfter(now) == true) return DoseState.SNOOZED
            null -> Unit
        }
        val scheduledAt = ZonedDateTime.of(dose.date, dose.time, zone).toInstant()
        return if (now.isBefore(scheduledAt)) DoseState.UPCOMING else DoseState.DUE
    }
}
