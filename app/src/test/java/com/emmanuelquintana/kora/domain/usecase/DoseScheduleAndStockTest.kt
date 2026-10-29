package com.emmanuelquintana.kora.domain.usecase

import com.emmanuelquintana.kora.data.model.DoseStatus
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.data.model.MedicationLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DoseScheduleAndStockTest {

    private val mexico = ZoneId.of("America/Mexico_City")

    private fun medication(
        times: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        days: Set<DayOfWeek> = DayOfWeek.values().toSet(),
        stock: Int = 60,
        unitsPerDose: Int = 1,
        lowStockDays: Int = 7,
        active: Boolean = true,
    ) = Medication(
        id = "med1", name = "Metformina", dose = "850", unit = "mg", times = times, daysOfWeek = days,
        stockUnits = stock, unitsPerDose = unitsPerDose, lowStockDays = lowStockDays, active = active,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun doseIdIsStableAndZeroPadded() {
        assertEquals("med1_20261029_0805", MedicationLog.doseId("med1", LocalDate.of(2026, 10, 29), LocalTime.of(8, 5)))
    }

    @Test
    fun nextOccurrenceIsLaterTodayOrTomorrow() {
        val med = medication()
        val morning = ZonedDateTime.of(2026, 10, 29, 7, 30, 0, 0, mexico)
        assertEquals(
            ZonedDateTime.of(2026, 10, 29, 8, 0, 0, 0, mexico),
            DoseSchedule.nextOccurrence(med, LocalTime.of(8, 0), morning),
        )
        val afterDose = ZonedDateTime.of(2026, 10, 29, 8, 0, 0, 0, mexico)
        assertEquals(
            "exactamente a la hora ya no cuenta: pasa al día siguiente",
            ZonedDateTime.of(2026, 10, 30, 8, 0, 0, 0, mexico),
            DoseSchedule.nextOccurrence(med, LocalTime.of(8, 0), afterDose),
        )
    }

    @Test
    fun nextOccurrenceRespectsDaysOfWeek() {
        val mondaysOnly = medication(days = setOf(DayOfWeek.MONDAY))
        // 2026-10-29 es jueves → siguiente lunes 2026-11-02.
        val thursday = ZonedDateTime.of(2026, 10, 29, 9, 0, 0, 0, mexico)
        assertEquals(
            ZonedDateTime.of(2026, 11, 2, 8, 0, 0, 0, mexico),
            DoseSchedule.nextOccurrence(mondaysOnly, LocalTime.of(8, 0), thursday),
        )
    }

    @Test
    fun nextOccurrenceHandlesDaylightSavingGap() {
        val newYork = ZoneId.of("America/New_York")
        // 2027-03-14 02:30 no existe en Nueva York (se adelanta a 03:30).
        val before = ZonedDateTime.of(2027, 3, 14, 1, 0, 0, 0, newYork)
        val next = DoseSchedule.nextOccurrence(medication(times = listOf(LocalTime.of(2, 30))), LocalTime.of(2, 30), before)
        assertEquals(LocalDate.of(2027, 3, 14), next?.toLocalDate())
        assertEquals(LocalTime.of(3, 30), next?.toLocalTime())
    }

    @Test
    fun inactiveMedicationHasNoSchedule() {
        val now = ZonedDateTime.of(2026, 10, 29, 7, 0, 0, 0, mexico)
        assertNull(DoseSchedule.nextOccurrence(medication(active = false), LocalTime.of(8, 0), now))
        assertEquals(emptyList<ScheduledDose>(), DoseSchedule.dosesOn(medication(active = false), now.toLocalDate()))
    }

    @Test
    fun agendaCombinesScheduleAndLogs() {
        val med = medication()
        val date = LocalDate.of(2026, 10, 29)
        val now = ZonedDateTime.of(2026, 10, 29, 12, 0, 0, 0, mexico).toInstant()
        val takenMorning = MedicationLog(
            id = MedicationLog.doseId("med1", date, LocalTime.of(8, 0)), medicationId = "med1",
            scheduledDate = date, scheduledTime = LocalTime.of(8, 0), status = DoseStatus.TAKEN, recordedAt = now,
        )
        val agenda = DoseSchedule.agenda(listOf(med), listOf(takenMorning), date, now, mexico)
        assertEquals(listOf(DoseState.TAKEN, DoseState.UPCOMING), agenda.map { it.state })

        val evening = ZonedDateTime.of(2026, 10, 29, 21, 0, 0, 0, mexico).toInstant()
        assertEquals(DoseState.DUE, DoseSchedule.agenda(listOf(med), emptyList(), date, evening, mexico)[1].state)
    }

    @Test
    fun expiredSnoozeFallsBackToDue() {
        val date = LocalDate.of(2026, 10, 29)
        val dose = ScheduledDose(medication(), date, LocalTime.of(8, 0))
        val snoozeEnd = ZonedDateTime.of(2026, 10, 29, 8, 15, 0, 0, mexico).toInstant()
        val log = MedicationLog(dose.id, "med1", date, LocalTime.of(8, 0), DoseStatus.SNOOZED, snoozeEnd, snoozedUntil = snoozeEnd)
        assertEquals(DoseState.SNOOZED, DoseSchedule.stateOf(dose, log, snoozeEnd.minusSeconds(60), mexico))
        assertEquals(DoseState.DUE, DoseSchedule.stateOf(dose, log, snoozeEnd.plusSeconds(60), mexico))
    }

    @Test
    fun stockDaysFollowTheSchedule() {
        // 2 tomas diarias de 1 unidad, 60 unidades → 30 días.
        assertEquals(30, StockCalculator.daysRemaining(medication(stock = 60)))
        // 1 toma solo lunes a viernes (5/semana), 10 unidades → 14 días.
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        assertEquals(14, StockCalculator.daysRemaining(medication(times = listOf(LocalTime.of(8, 0)), days = weekdays, stock = 10)))
    }

    @Test
    fun stockLevels() {
        assertEquals(StockLevel.OK, StockCalculator.status(medication(stock = 60)).level)
        assertEquals(StockLevel.LOW, StockCalculator.status(medication(stock = 14, lowStockDays = 7)).level)
        assertEquals(StockLevel.OUT, StockCalculator.status(medication(stock = 0)).level)
        assertEquals(StockLevel.OUT, StockCalculator.status(medication(stock = 1, unitsPerDose = 2)).level)
        val negative = StockCalculator.status(medication(stock = -3))
        assertEquals(0, negative.units)
        assertEquals(StockLevel.OUT, negative.level)
    }

    @Test
    fun deductionNeverExceedsAvailableStock() {
        assertEquals(2, StockCalculator.unitsToDeduct(medication(stock = 10, unitsPerDose = 2)))
        assertEquals(1, StockCalculator.unitsToDeduct(medication(stock = 1, unitsPerDose = 2)))
        assertEquals(0, StockCalculator.unitsToDeduct(medication(stock = 0, unitsPerDose = 2)))
    }
}
