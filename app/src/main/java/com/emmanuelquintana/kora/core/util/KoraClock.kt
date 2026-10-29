package com.emmanuelquintana.kora.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Reloj inyectable: permite probar horarios y cambios de zona horaria. */
interface KoraClock {
    fun now(): Instant
    fun zone(): ZoneId

    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
    fun zonedNow(): ZonedDateTime = ZonedDateTime.ofInstant(now(), zone())
}

object SystemKoraClock : KoraClock {
    override fun now(): Instant = Instant.now()

    /** Se lee en cada llamada: respeta cambios de zona horaria sin reiniciar. */
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
