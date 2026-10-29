package com.emmanuelquintana.kora.core.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.emmanuelquintana.kora.core.database.KoraPreferences
import com.emmanuelquintana.kora.core.util.KoraClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Estado del actualizador que ve la UI (Perfil → Actualizaciones). */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: SemVer?, val checkedAt: Instant) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo?, val version: String, val percent: Int?, val downloadedBytes: Long) : UpdateState
    data class ReadyToInstall(val version: String, val file: File, val checksumVerified: Boolean, val info: UpdateInfo?) : UpdateState
    data class Failed(val reason: UpdateFailure, val info: UpdateInfo?, val retryAfter: Instant? = null) : UpdateState
}

class UpdateManager(
    context: Context,
    private val checker: UpdateChecker,
    private val preferences: KoraPreferences,
    private val clock: KoraClock,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val checkState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val checkMutex = Mutex()

    val state: StateFlow<UpdateState> =
        combine(checkState, workManager.getWorkInfosForUniqueWorkFlow(UpdateDownloadWorker.UNIQUE_WORK_NAME)) { check, infos ->
            merge(check, infos.firstOrNull())
        }.stateIn(scope, SharingStarted.Eagerly, UpdateState.Idle)

    /** Al iniciar la app: como máximo una consulta cada [AUTO_CHECK_INTERVAL]. No bloquea. */
    fun checkOnLaunchIfDue() {
        scope.launch {
            val last = preferences.lastUpdateCheck()
            if (last == null || Duration.between(last, clock.now()) >= AUTO_CHECK_INTERVAL) performCheck()
        }
    }

    /** Búsqueda manual desde Perfil. */
    fun checkNow() {
        scope.launch { performCheck() }
    }

    private suspend fun performCheck() = checkMutex.withLock {
        if (state.value is UpdateState.Downloading) return@withLock
        checkState.value = UpdateState.Checking
        val result = checker.check()
        preferences.setLastUpdateCheck(clock.now())
        checkState.value = when (result) {
            is UpdateCheckResult.Available -> UpdateState.Available(result.info)
            is UpdateCheckResult.UpToDate -> UpdateState.UpToDate(checker.currentVersion, clock.now())
            is UpdateCheckResult.Failed -> UpdateState.Failed(result.reason, null, result.retryAfter)
        }
    }

    fun startDownload(info: UpdateInfo) {
        checkState.value = UpdateState.Available(info)
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(
                workDataOf(
                    UpdateDownloadWorker.KEY_APK_URL to info.apk.downloadUrl,
                    UpdateDownloadWorker.KEY_CHECKSUM_URL to info.checksum?.downloadUrl,
                    UpdateDownloadWorker.KEY_EXPECTED_SIZE to info.apk.sizeBytes,
                    UpdateDownloadWorker.KEY_VERSION to info.version.toString(),
                ),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UpdateDownloadWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelDownload() {
        workManager.cancelUniqueWork(UpdateDownloadWorker.UNIQUE_WORK_NAME)
    }

    fun prepareInstall(file: File): InstallLaunch = ApkInstaller.prepare(appContext, file)

    private fun merge(check: UpdateState, work: WorkInfo?): UpdateState {
        val pendingInfo = (check as? UpdateState.Available)?.info
        if (work == null) return check
        val version = work.outputData.getString(UpdateDownloadWorker.KEY_VERSION)
            ?: pendingInfo?.version?.toString()
            ?: return check
        return when (work.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> UpdateState.Downloading(
                info = pendingInfo,
                version = version,
                percent = work.progress.getInt(UpdateDownloadWorker.KEY_PROGRESS, -1).takeIf { it >= 0 },
                downloadedBytes = work.progress.getLong(UpdateDownloadWorker.KEY_DOWNLOADED, 0L),
            )
            WorkInfo.State.SUCCEEDED -> {
                val path = work.outputData.getString(UpdateDownloadWorker.KEY_FILE)
                val file = path?.let(::File)
                val newer = SemVer.parse(version)?.let { v -> checker.currentVersion?.let { v > it } } ?: false
                // Un APK de una actualización ya instalada (o borrado) no se ofrece de nuevo.
                if (file != null && file.isFile && newer) {
                    UpdateState.ReadyToInstall(
                        version = version,
                        file = file,
                        checksumVerified = work.outputData.getBoolean(UpdateDownloadWorker.KEY_CHECKSUM_VERIFIED, false),
                        info = pendingInfo,
                    )
                } else {
                    check
                }
            }
            WorkInfo.State.FAILED -> {
                val reason = work.outputData.getString(UpdateDownloadWorker.KEY_FAILURE)
                    ?.let { name -> UpdateFailure.entries.firstOrNull { it.name == name } }
                    ?: UpdateFailure.UNKNOWN
                UpdateState.Failed(reason, pendingInfo)
            }
            WorkInfo.State.CANCELLED -> if (pendingInfo != null) UpdateState.Available(pendingInfo) else check
        }
    }

    /** Borra APKs ya instalados o abandonados. */
    fun cleanUpOldDownloads() {
        val current = checker.currentVersion ?: return
        File(appContext.filesDir, UpdateDownloadWorker.UPDATES_DIR).listFiles()?.forEach { file ->
            val version = SemVer.parse(file.name.removePrefix("Kora-v").substringBefore("-descarga").removeSuffix(".apk"))
            if (version == null || version <= current) file.delete()
        }
    }

    companion object {
        val AUTO_CHECK_INTERVAL: Duration = Duration.ofHours(6)
    }
}
