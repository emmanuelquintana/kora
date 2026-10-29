package com.emmanuelquintana.kora.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Motivos de fallo que la UI explica con palabras (sin detalles técnicos crudos). */
enum class UpdateFailure {
    OFFLINE,
    RATE_LIMITED,
    NO_RELEASES,
    NO_APK,
    INVALID_VERSION,
    HTTP_ERROR,
    PARSE_ERROR,
    DOWNLOAD_INCOMPLETE,
    CHECKSUM_MISMATCH,
    NOT_AN_APK,
    NOT_NEWER,
    SIGNATURE_MISMATCH,
    FILE_MISSING,
    STORAGE,
    CANCELLED,
    UNKNOWN,
}

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data class UpToDate(val latest: SemVer?) : UpdateCheckResult
    data class Failed(val reason: UpdateFailure, val retryAfter: Instant? = null) : UpdateCheckResult
}

/**
 * Consulta el último Release publicado en GitHub y decide si hay una versión
 * más nueva que la instalada ([currentVersionName] = BuildConfig.VERSION_NAME).
 * No bloquea la UI: todo corre en Dispatchers.IO y respeta la cancelación.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val repository: String,
    private val currentVersionName: String,
) {
    private val endpoint = "https://api.github.com/repos/$repository/releases/latest"

    val currentVersion: SemVer? = SemVer.parse(currentVersionName.removeSuffix("-debug"))

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val current = currentVersion ?: return@withContext UpdateCheckResult.Failed(UpdateFailure.INVALID_VERSION)
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Kora-Android/$currentVersionName")
            .get()
            .build()

        val response = try {
            client.newCall(request).await()
        } catch (_: IOException) {
            return@withContext UpdateCheckResult.Failed(UpdateFailure.OFFLINE)
        }

        response.use { res ->
            when {
                res.code == 404 -> UpdateCheckResult.Failed(UpdateFailure.NO_RELEASES)
                res.code == 403 || res.code == 429 -> {
                    val reset = res.header("X-RateLimit-Reset")?.toLongOrNull()?.let(Instant::ofEpochSecond)
                    UpdateCheckResult.Failed(UpdateFailure.RATE_LIMITED, reset)
                }
                !res.isSuccessful -> UpdateCheckResult.Failed(UpdateFailure.HTTP_ERROR)
                else -> {
                    val body = try {
                        res.body?.string()
                    } catch (_: IOException) {
                        null
                    } ?: return@use UpdateCheckResult.Failed(UpdateFailure.OFFLINE)
                    val release = GitHubReleaseParser.parse(body)
                        ?: return@use UpdateCheckResult.Failed(UpdateFailure.PARSE_ERROR)
                    when (val selection = ReleaseSelector.select(release, current)) {
                        is ReleaseSelection.Available -> UpdateCheckResult.Available(selection.info)
                        is ReleaseSelection.UpToDate -> UpdateCheckResult.UpToDate(selection.latest)
                        ReleaseSelection.InvalidVersion -> UpdateCheckResult.Failed(UpdateFailure.INVALID_VERSION)
                        ReleaseSelection.NoApk -> UpdateCheckResult.Failed(UpdateFailure.NO_APK)
                    }
                }
            }
        }
    }
}

/** Ejecuta una llamada OkHttp de forma suspendida y cancelable. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        },
    )
    continuation.invokeOnCancellation { runCatching { cancel() } }
}
