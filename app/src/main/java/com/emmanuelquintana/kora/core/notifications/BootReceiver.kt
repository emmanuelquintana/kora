package com.emmanuelquintana.kora.core.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.emmanuelquintana.kora.KoraApplication

/**
 * Reprograma los recordatorios cuando Android los pierde o los vuelve
 * incorrectos: reinicio, cambio de hora o de zona horaria, actualización de
 * Kora y cambio del permiso de alarmas exactas.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val container = (context.applicationContext as KoraApplication).container
        goAsyncSafely {
            container.alarmScheduler.rescheduleAll()
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

/** Fin del reposo de 5 minutos: avisa sin afirmar que la medición quede validada. */
class RestTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REST_FINISHED) return
        KoraNotifications.showRestTimerFinished(context)
    }

    companion object {
        const val ACTION_REST_FINISHED = "com.emmanuelquintana.kora.action.REST_FINISHED"
    }
}
