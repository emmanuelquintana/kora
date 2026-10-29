package com.emmanuelquintana.kora.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.emmanuelquintana.kora.KoraApplication
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.DoseStatus
import com.emmanuelquintana.kora.data.model.MedicationLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime

/**
 * Suena la hora de una toma: programa la siguiente ocurrencia y muestra el
 * recordatorio, salvo que esa toma ya esté registrada (evita duplicados si la
 * persona la marcó desde la app antes de la alarma).
 */
class MedicationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DOSE_ALARM) return
        val medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID) ?: return
        val time = intent.getStringExtra(EXTRA_TIME)?.let(KoraFormats::parseStorageTime) ?: return
        val date = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val snoozed = intent.getBooleanExtra(EXTRA_SNOOZED, false)
        val container = (context.applicationContext as KoraApplication).container

        goAsyncSafely {
            // Las alarmas diarias son de un disparo: la siguiente se programa primero.
            if (!snoozed) container.alarmScheduler.scheduleNext(medicationId, time)

            val spec = container.alarmRegistry.find(medicationId) ?: specFromIntent(intent, medicationId, time) ?: return@goAsyncSafely
            val existing = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                container.session.currentRepository()?.getLog(MedicationLog.doseId(medicationId, date, time))
            }
            if (existing?.status == DoseStatus.TAKEN || existing?.status == DoseStatus.SKIPPED) return@goAsyncSafely
            KoraNotifications.showDoseReminder(context, spec, date, time)
        }
    }

    private fun specFromIntent(intent: Intent, medicationId: String, time: LocalTime): AlarmSpec? {
        val name = intent.getStringExtra(EXTRA_NAME) ?: return null
        return AlarmSpec(
            medicationId = medicationId,
            name = name,
            doseLabel = intent.getStringExtra(EXTRA_DOSE).orEmpty(),
            unitsPerDose = intent.getIntExtra(EXTRA_UNITS, 1),
            times = listOf(time),
            days = java.time.DayOfWeek.values().toSet(),
        )
    }

    companion object {
        const val ACTION_DOSE_ALARM = "com.emmanuelquintana.kora.action.DOSE_ALARM"
        private const val EXTRA_MEDICATION_ID = "medication_id"
        private const val EXTRA_TIME = "time"
        private const val EXTRA_DATE = "date"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_DOSE = "dose"
        private const val EXTRA_UNITS = "units"
        private const val EXTRA_SNOOZED = "snoozed"
        private const val LOOKUP_TIMEOUT_MS = 4_000L

        /** Intent sin extras: sirve para localizar y cancelar PendingIntents existentes. */
        fun baseIntent(context: Context): Intent =
            Intent(context, MedicationAlarmReceiver::class.java).setAction(ACTION_DOSE_ALARM)

        fun intent(context: Context, spec: AlarmSpec, date: LocalDate, time: LocalTime, snoozed: Boolean): Intent =
            baseIntent(context)
                .putExtra(EXTRA_MEDICATION_ID, spec.medicationId)
                .putExtra(EXTRA_TIME, KoraFormats.storageTime(time))
                .putExtra(EXTRA_DATE, date.toString())
                .putExtra(EXTRA_NAME, spec.name)
                .putExtra(EXTRA_DOSE, spec.doseLabel)
                .putExtra(EXTRA_UNITS, spec.unitsPerDose)
                .putExtra(EXTRA_SNOOZED, snoozed)
    }
}

/**
 * Ejecuta trabajo suspendido desde un BroadcastReceiver usando goAsync(),
 * con un límite de tiempo por debajo del máximo que permite Android (~10 s).
 */
internal fun BroadcastReceiver.goAsyncSafely(block: suspend CoroutineScope.() -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            withTimeoutOrNull(RECEIVER_TIMEOUT_MS) { block() }
        } finally {
            pending.finish()
        }
    }
}

private const val RECEIVER_TIMEOUT_MS = 9_000L
