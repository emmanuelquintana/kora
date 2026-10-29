package com.emmanuelquintana.kora.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.emmanuelquintana.kora.core.util.KoraClock
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.domain.usecase.DoseSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Qué tan puntuales pueden ser los recordatorios en este dispositivo. */
enum class AlarmPrecision {
    /** setExactAndAllowWhileIdle permitido. */
    EXACT,

    /** El usuario no concedió alarmas exactas: Android puede retrasarlas varios minutos. */
    INEXACT,
}

/**
 * Programa los recordatorios de medicamentos con AlarmManager.
 *
 * - Una alarma por (medicamento, hora), con requestCode estable derivado de la
 *   clave: reprogramar reemplaza en lugar de duplicar.
 * - Exacta (`setExactAndAllowWhileIdle`) solo si `canScheduleExactAlarms()`;
 *   en caso contrario `setAndAllowWhileIdle`, y la UI lo explica.
 * - Las alarmas son de un solo disparo: el receptor programa la siguiente.
 */
class AlarmScheduler(
    context: Context,
    private val clock: KoraClock,
    private val registry: AlarmRegistry,
) {
    private val appContext = context.applicationContext
    private val alarmManager: AlarmManager = appContext.getSystemService(AlarmManager::class.java)

    val precision: AlarmPrecision
        get() = if (canScheduleExact()) AlarmPrecision.EXACT else AlarmPrecision.INEXACT

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Pantalla del sistema para conceder "Alarmas y recordatorios" (Android 12+). */
    fun exactAlarmSettingsIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${appContext.packageName}"))
        } else {
            null
        }

    /** Sincroniza las alarmas con la lista actual de medicamentos. */
    @Synchronized
    fun sync(medications: List<Medication>) {
        val specs = medications.filter { it.active && it.times.isNotEmpty() && it.daysOfWeek.isNotEmpty() }.map(AlarmSpec::from)
        val newKeys = specs.flatMap { spec -> spec.times.map(spec::timeKey) }.toSet()
        registry.load()
            .flatMap { spec -> spec.times.map { spec.timeKey(it) } }
            .filterNot { it in newKeys }
            .forEach(::cancelKey)
        registry.save(specs)
        specs.forEach(::scheduleSpec)
    }

    /** Tras reinicio, cambio de hora/zona o actualización de la app. */
    @Synchronized
    fun rescheduleAll() {
        registry.load().forEach(::scheduleSpec)
    }

    fun scheduleNext(medicationId: String, time: LocalTime) {
        val spec = registry.find(medicationId) ?: return
        if (time in spec.times) scheduleTime(spec, time)
    }

    /** Recordatorio pospuesto: alarma de un solo uso separada de la diaria. */
    fun scheduleSnooze(spec: AlarmSpec, date: LocalDate, time: LocalTime, at: Instant) {
        val intent = MedicationAlarmReceiver.intent(appContext, spec, date, time, snoozed = true)
        set(at.toEpochMilli(), pendingIntent(snoozeRequestCode(spec.timeKey(time)), intent))
    }

    @Synchronized
    fun cancelAll() {
        registry.load().forEach { spec ->
            spec.times.forEach { time ->
                cancelKey(spec.timeKey(time))
                cancelPending(snoozeRequestCode(spec.timeKey(time)), MedicationAlarmReceiver.baseIntent(appContext))
            }
        }
        registry.clear()
    }

    private fun scheduleSpec(spec: AlarmSpec) {
        spec.times.forEach { scheduleTime(spec, it) }
    }

    private fun scheduleTime(spec: AlarmSpec, time: LocalTime) {
        val next = DoseSchedule.nextOccurrence(spec.days, time, clock.zonedNow()) ?: return
        val intent = MedicationAlarmReceiver.intent(appContext, spec, next.toLocalDate(), time, snoozed = false)
        set(next.toInstant().toEpochMilli(), pendingIntent(requestCode(spec.timeKey(time)), intent))
    }

    private fun cancelKey(key: String) {
        cancelPending(requestCode(key), MedicationAlarmReceiver.baseIntent(appContext))
    }

    private fun cancelPending(code: Int, intent: Intent) {
        val existing = PendingIntent.getBroadcast(
            appContext,
            code,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }

    private fun pendingIntent(code: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(appContext, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun set(triggerAtMillis: Long, operation: PendingIntent) {
        setAlarm(alarmManager, AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
    }

    companion object {
        fun requestCode(timeKey: String): Int = "alarm:$timeKey".hashCode()
        fun snoozeRequestCode(timeKey: String): Int = "snooze:$timeKey".hashCode()

        /**
         * Exacta si el sistema lo permite; si el permiso se revocó entre la
         * comprobación y la llamada, cae a una alarma inexacta.
         */
        fun setAlarm(alarmManager: AlarmManager, type: Int, triggerAtMillis: Long, operation: PendingIntent) {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (exactAllowed) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(type, triggerAtMillis, operation)
                    return
                } catch (_: SecurityException) {
                    // Permiso revocado: se usa la alternativa inexacta.
                }
            }
            alarmManager.setAndAllowWhileIdle(type, triggerAtMillis, operation)
        }
    }
}

/**
 * Temporizador de reposo de 5 minutos (modo Flex). Usa el reloj de arranque
 * (elapsedRealtime) para no verse afectado por cambios de hora.
 */
class RestTimerScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager: AlarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(endsAtElapsedRealtime: Long) {
        if (endsAtElapsedRealtime <= SystemClock.elapsedRealtime()) return
        AlarmScheduler.setAlarm(alarmManager, AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAtElapsedRealtime, pendingIntent())
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
        KoraNotifications.cancelRestTimer(appContext)
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        REQUEST_CODE,
        Intent(appContext, RestTimerReceiver::class.java).setAction(RestTimerReceiver.ACTION_REST_FINISHED),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 0x0D_0100
    }
}
