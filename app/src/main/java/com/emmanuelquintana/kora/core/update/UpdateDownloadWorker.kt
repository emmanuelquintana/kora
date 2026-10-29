package com.emmanuelquintana.kora.core.update

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.emmanuelquintana.kora.KoraApplication
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.notifications.KoraNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Descarga el APK en almacenamiento privado (filesDir/updates/) sin depender
 * de que la Activity siga abierta. Verifica tamaño, SHA-256 publicado,
 * applicationId, versionCode y certificado antes de declararlo listo.
 */
class UpdateDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(progressPercent = 0)

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_APK_URL) ?: return failure(UpdateFailure.UNKNOWN)
        val version = inputData.getString(KEY_VERSION) ?: return failure(UpdateFailure.UNKNOWN)
        val checksumUrl = inputData.getString(KEY_CHECKSUM_URL)
        val expectedSize = inputData.getLong(KEY_EXPECTED_SIZE, -1L)
        val client = (applicationContext as KoraApplication).container.okHttpClient

        // En Android 12+ el inicio en primer plano puede rechazarse si la app está en segundo plano;
        // la descarga continúa igualmente como trabajo normal.
        runCatching { setForeground(foregroundInfo(0)) }

        val dir = File(applicationContext.filesDir, UPDATES_DIR)
        if (!dir.exists() && !dir.mkdirs()) return failure(UpdateFailure.STORAGE)
        val target = File(dir, "Kora-v$version.apk")
        val temp = File(dir, "Kora-v$version-descarga.apk")
        dir.listFiles()?.filter { it != target && it != temp }?.forEach { it.delete() }

        return try {
            val expectedSha = checksumUrl?.let { fetchChecksum(client, it) ?: return failure(UpdateFailure.CHECKSUM_MISMATCH) }
            val actualSha = download(client, url, temp, expectedSize)
                ?: return failure(UpdateFailure.DOWNLOAD_INCOMPLETE).also { temp.delete() }
            if (expectedSha != null && !expectedSha.equals(actualSha, ignoreCase = true)) {
                temp.delete()
                return failure(UpdateFailure.CHECKSUM_MISMATCH)
            }
            ApkVerifier.verify(applicationContext, temp)?.let { reason ->
                temp.delete()
                return failure(reason)
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) return failure(UpdateFailure.STORAGE)
            Result.success(
                workDataOf(
                    KEY_FILE to target.absolutePath,
                    KEY_VERSION to version,
                    KEY_CHECKSUM_VERIFIED to (expectedSha != null),
                ),
            )
        } catch (e: CancellationException) {
            temp.delete()
            throw e
        } catch (_: IOException) {
            temp.delete()
            if (runAttemptCount < MAX_RETRIES) Result.retry() else failure(UpdateFailure.OFFLINE)
        }
    }

    private suspend fun fetchChecksum(client: okhttp3.OkHttpClient, url: String): String? = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()?.let(ReleaseSelector::parseChecksum)
        }
    }

    /** Descarga a [file] calculando SHA-256 al vuelo; devuelve el hash o null si quedó incompleta. */
    private suspend fun download(client: okhttp3.OkHttpClient, url: String, file: File, expectedSize: Long): String? =
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Respuesta vacía")
                val total = body.contentLength().takeIf { it > 0 } ?: expectedSize
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            if (isStopped) throw CancellationException("Descarga cancelada")
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val percent = (downloaded * 100 / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    setProgress(workDataOf(KEY_PROGRESS to percent, KEY_DOWNLOADED to downloaded, KEY_TOTAL to total))
                                    if (percent % 5 == 0) runCatching { setForeground(foregroundInfo(percent)) }
                                }
                            }
                        }
                    }
                }
                val complete = when {
                    expectedSize > 0 -> downloaded == expectedSize
                    total > 0 -> downloaded == total
                    else -> downloaded > 0
                }
                if (complete) digest.digest().joinToString("") { "%02x".format(it) } else null
            }
        }

    private fun foregroundInfo(progressPercent: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, KoraNotifications.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_kora)
            .setContentTitle(applicationContext.getString(R.string.update_download_title))
            .setContentText(applicationContext.getString(R.string.update_download_progress, progressPercent))
            .setProgress(100, progressPercent, progressPercent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(KoraNotifications.ID_UPDATE_DOWNLOAD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(KoraNotifications.ID_UPDATE_DOWNLOAD, notification)
        }
    }

    private fun failure(reason: UpdateFailure): Result = Result.failure(workDataOf(KEY_FAILURE to reason.name))

    companion object {
        const val UNIQUE_WORK_NAME = "kora_update_download"
        const val UPDATES_DIR = "updates"

        const val KEY_APK_URL = "apk_url"
        const val KEY_CHECKSUM_URL = "checksum_url"
        const val KEY_EXPECTED_SIZE = "expected_size"
        const val KEY_VERSION = "version"
        const val KEY_FILE = "file"
        const val KEY_CHECKSUM_VERIFIED = "checksum_verified"
        const val KEY_FAILURE = "failure"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"

        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_RETRIES = 2
    }
}
