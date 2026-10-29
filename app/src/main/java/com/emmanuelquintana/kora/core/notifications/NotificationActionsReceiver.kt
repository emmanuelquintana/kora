package com.emmanuelquintana.kora.core.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.emmanuelquintana.kora.KoraApplication
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.DoseStatus
import com.emmanuelquintana.kora.data.model.Medication
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Acciones rápidas del recordatorio: "Tomada" y "Posponer 15 min".
 * Ambas son idempotentes (ID de toma estable): tocar dos veces no duplica.
 */
class NotificationActionsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_TAKE && action != ACTION_SNOOZE) return
        val medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID) ?: return
        val time = intent.getStringExtra(EXTRA_TIME)?.let(KoraFormats::parseStorageTime) ?: return
        val date = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val container = (context.applicationContext as KoraApplication).container
        val doseKey = AlarmSpec.doseKey(medicationId, date, time)

        // Se retira de inmediato para que un segundo toque no tenga dónde caer.
        KoraNotifications.cancelDose(context, doseKey)

        goAsyncSafely {
            val repository = container.session.currentRepository() ?: return@goAsyncSafely
            val spec = container.alarmRegistry.find(medicationId)
            val medication = repository.getMedication(medicationId)
                ?: spec?.let { fallbackMedication(it, container.clock.now()) }
                ?: return@goAsyncSafely

            when (action) {
                ACTION_TAKE -> repository.recordDose(medication, date, time, DoseStatus.TAKEN)
                ACTION_SNOOZE -> {
                    val until = container.clock.now().plus(SNOOZE)
                    repository.recordDose(medication, date, time, DoseStatus.SNOOZED, snoozedUntil = until)
                    container.alarmScheduler.scheduleSnooze(spec ?: AlarmSpec.from(medication), date, time, until)
                }
            }
        }
    }

    /**
     * Si la caché no tiene el medicamento, se registra la toma con los datos de
     * la alarma. Stock 0 → no se descuenta nada: actualizar un medicamento que
     * quizá ya no existe haría fallar el lote completo y se perdería la toma.
     */
    private fun fallbackMedication(spec: AlarmSpec, now: java.time.Instant) = Medication(
        id = spec.medicationId,
        name = spec.name,
        dose = spec.doseLabel,
        unit = "",
        times = spec.times,
        daysOfWeek = spec.days,
        stockUnits = 0,
        unitsPerDose = spec.unitsPerDose,
        createdAt = now,
    )

    companion object {
        const val ACTION_TAKE = "com.emmanuelquintana.kora.action.DOSE_TAKEN"
        const val ACTION_SNOOZE = "com.emmanuelquintana.kora.action.DOSE_SNOOZE"
        val SNOOZE: Duration = Duration.ofMinutes(15)

        private const val EXTRA_MEDICATION_ID = "medication_id"
        private const val EXTRA_TIME = "time"
        private const val EXTRA_DATE = "date"

        fun pendingIntent(context: Context, action: String, medicationId: String, date: LocalDate, time: LocalTime): PendingIntent {
            val intent = Intent(context, NotificationActionsReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_MEDICATION_ID, medicationId)
                .putExtra(EXTRA_DATE, date.toString())
                .putExtra(EXTRA_TIME, KoraFormats.storageTime(time))
            val requestCode = "$action:${AlarmSpec.doseKey(medicationId, date, time)}".hashCode()
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
