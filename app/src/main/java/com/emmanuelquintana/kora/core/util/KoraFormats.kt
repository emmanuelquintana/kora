package com.emmanuelquintana.kora.core.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatos regionales de México para toda la app. */
object KoraFormats {
    val locale: Locale = Locale.forLanguageTag("es-MX")

    private val integer: NumberFormat = NumberFormat.getIntegerInstance(locale)
    private val time: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val time24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
    private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale)
    private val fullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", locale)

    fun number(value: Int): String = integer.format(value)

    fun time(value: LocalTime): String = time.format(value)

    fun time(value: Instant, zone: ZoneId): String = time.format(value.atZone(zone))

    /** Formato de almacenamiento y de clave de alarma ("08:05"). */
    fun storageTime(value: LocalTime): String = time24.format(value)

    fun parseStorageTime(value: String): LocalTime? = runCatching { LocalTime.parse(value, time24) }.getOrNull()

    fun shortDate(value: LocalDate): String = shortDate.format(value)

    fun longDate(value: LocalDate): String = longDate.format(value).replaceFirstChar { it.titlecase(locale) }

    fun fullDate(value: LocalDate): String = fullDate.format(value)
}
