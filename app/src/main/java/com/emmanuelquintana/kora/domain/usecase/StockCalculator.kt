package com.emmanuelquintana.kora.domain.usecase

import com.emmanuelquintana.kora.data.model.Medication

enum class StockLevel { OK, LOW, OUT, UNKNOWN }

data class StockStatus(
    val level: StockLevel,
    /** Unidades disponibles (nunca negativas en pantalla). */
    val units: Int,
    /** Días que alcanza el stock al ritmo del horario; null si no hay tomas programadas. */
    val daysRemaining: Int?,
)

object StockCalculator {

    fun dosesPerWeek(medication: Medication): Int = medication.times.size * medication.daysOfWeek.size

    fun daysRemaining(medication: Medication): Int? {
        val perWeek = dosesPerWeek(medication)
        if (perWeek == 0 || medication.unitsPerDose <= 0) return null
        // Aritmética entera para evitar errores de redondeo: unidades·7 / consumo semanal.
        return displayUnits(medication.stockUnits) * 7 / (medication.unitsPerDose * perWeek)
    }

    fun status(medication: Medication): StockStatus {
        val units = displayUnits(medication.stockUnits)
        val days = daysRemaining(medication)
        val level = when {
            units < medication.unitsPerDose -> StockLevel.OUT
            days == null -> StockLevel.UNKNOWN
            days <= medication.lowStockDays -> StockLevel.LOW
            else -> StockLevel.OK
        }
        return StockStatus(level, units, days)
    }

    /** El stock en la base puede quedar negativo por incrementos sin conexión; en pantalla es 0. */
    fun displayUnits(stockUnits: Int): Int = stockUnits.coerceAtLeast(0)

    /** Unidades que descontará una toma; 0 si ya no hay stock suficiente que descontar. */
    fun unitsToDeduct(medication: Medication): Int =
        if (displayUnits(medication.stockUnits) >= medication.unitsPerDose) medication.unitsPerDose else displayUnits(medication.stockUnits)
}
