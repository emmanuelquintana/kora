package com.emmanuelquintana.kora.core.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.emmanuelquintana.kora.MainActivity
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.util.KoraFormats
import java.time.LocalDate
import java.time.LocalTime

/**
 * Canales, permisos y construcción de notificaciones. Las notificaciones de
 * medicamentos son privadas en la pantalla de bloqueo: el nombre del
 * medicamento solo se ve con el teléfono desbloqueado.
 */
object KoraNotifications {
    const val CHANNEL_MEDICATIONS = "medication_reminders"
    const val CHANNEL_REST_TIMER = "rest_timer"
    const val CHANNEL_UPDATES = "updates"

    const val ID_UPDATE_DOWNLOAD = 0x0D_0001
    const val ID_REST_TIMER = 0x0D_0002

    const val EXTRA_OPEN_DESTINATION = "com.emmanuelquintana.kora.extra.OPEN_DESTINATION"
    const val DESTINATION_MEDICATIONS = "medications"
    const val DESTINATION_BLOOD_PRESSURE = "blood_pressure"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_MEDICATIONS,
                    context.getString(R.string.channel_medications_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_medications_description)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(
                    CHANNEL_REST_TIMER,
                    context.getString(R.string.channel_rest_timer_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.channel_rest_timer_description) },
                NotificationChannel(
                    CHANNEL_UPDATES,
                    context.getString(R.string.channel_updates_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.channel_updates_description) },
            ),
        )
    }

    /** POST_NOTIFICATIONS concedido (Android 13+) y notificaciones activas para la app. */
    fun canPost(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** ID estable por toma: la misma toma nunca genera dos notificaciones. */
    fun doseNotificationId(doseKey: String): Int = ("dose:$doseKey").hashCode()

    fun showDoseReminder(context: Context, spec: AlarmSpec, date: LocalDate, time: LocalTime) {
        if (!canPost(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val doseKey = AlarmSpec.doseKey(spec.medicationId, date, time)
        val notificationId = doseNotificationId(doseKey)
        val timeLabel = KoraFormats.time(time)

        val publicVersion = NotificationCompat.Builder(context, CHANNEL_MEDICATIONS)
            .setSmallIcon(R.drawable.ic_stat_kora)
            .setContentTitle(context.getString(R.string.dose_reminder_public_title))
            .setContentText(context.getString(R.string.dose_reminder_public_text))
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_MEDICATIONS)
            .setSmallIcon(R.drawable.ic_stat_kora)
            .setContentTitle(context.getString(R.string.dose_reminder_title, spec.name))
            .setContentText(context.getString(R.string.dose_reminder_text, spec.doseLabel, timeLabel))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context, DESTINATION_MEDICATIONS, notificationId))
            .addAction(
                0,
                context.getString(R.string.dose_action_taken),
                NotificationActionsReceiver.pendingIntent(context, NotificationActionsReceiver.ACTION_TAKE, spec.medicationId, date, time),
            )
            .addAction(
                0,
                context.getString(R.string.dose_action_snooze),
                NotificationActionsReceiver.pendingIntent(context, NotificationActionsReceiver.ACTION_SNOOZE, spec.medicationId, date, time),
            )
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showRestTimerFinished(context: Context) {
        if (!canPost(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_REST_TIMER)
            .setSmallIcon(R.drawable.ic_stat_kora)
            .setContentTitle(context.getString(R.string.rest_timer_done_title))
            .setContentText(context.getString(R.string.rest_timer_done_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.rest_timer_done_text)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, DESTINATION_BLOOD_PRESSURE, ID_REST_TIMER))
            .build()
        NotificationManagerCompat.from(context).notify(ID_REST_TIMER, notification)
    }

    fun cancelDose(context: Context, doseKey: String) {
        NotificationManagerCompat.from(context).cancel(doseNotificationId(doseKey))
    }

    fun cancelRestTimer(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_REST_TIMER)
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun openAppIntent(context: Context, destination: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_DESTINATION, destination)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
