package com.emmanuelquintana.kora

import android.app.Application
import com.emmanuelquintana.kora.core.auth.AuthManager
import com.emmanuelquintana.kora.core.database.FirebaseSupport
import com.emmanuelquintana.kora.core.database.FirestoreProvider
import com.emmanuelquintana.kora.core.database.KoraPreferences
import com.emmanuelquintana.kora.core.notifications.AlarmRegistry
import com.emmanuelquintana.kora.core.notifications.AlarmScheduler
import com.emmanuelquintana.kora.core.notifications.KoraNotifications
import com.emmanuelquintana.kora.core.notifications.RestTimerScheduler
import com.emmanuelquintana.kora.core.update.UpdateChecker
import com.emmanuelquintana.kora.core.update.UpdateManager
import com.emmanuelquintana.kora.core.util.KoraClock
import com.emmanuelquintana.kora.core.util.SystemKoraClock
import com.emmanuelquintana.kora.data.repository.DataState
import com.emmanuelquintana.kora.data.repository.SessionManager
import com.emmanuelquintana.kora.data.repository.repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Contenedor de dependencias manual (sin Hilt; ver ARCHITECTURE.md § 6).
 * Vive lo mismo que el proceso.
 */
class AppContainer(val application: Application) {

    /** Alcance del proceso: listeners compartidos y sincronización de alarmas. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val clock: KoraClock = SystemKoraClock
    val firebaseAvailable: Boolean = FirebaseSupport.isAvailable(application)

    val preferences = KoraPreferences(application)
    val firestoreProvider = FirestoreProvider(firebaseAvailable)
    val authManager = AuthManager(application, firebaseAvailable, appScope)

    val alarmRegistry = AlarmRegistry(application)
    val alarmScheduler = AlarmScheduler(application, clock, alarmRegistry)
    val restTimerScheduler = RestTimerScheduler(application)

    val session = SessionManager(application, authManager, firestoreProvider, preferences, alarmScheduler, clock, appScope)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    val updateManager = UpdateManager(
        context = application,
        checker = UpdateChecker(okHttpClient, BuildConfig.UPDATE_REPOSITORY, BuildConfig.VERSION_NAME),
        preferences = preferences,
        clock = clock,
        scope = appScope,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        KoraNotifications.ensureChannels(application)
        updateManager.cleanUpOldDownloads()

        // Mantiene las alarmas alineadas con los medicamentos de la sesión activa.
        // Solo con datos listos: un error o una carga no borran recordatorios.
        appScope.launch {
            session.session
                .flatMapLatest { it.repository?.medications() ?: emptyFlow() }
                .collect { state ->
                    if (state is DataState.Ready) alarmScheduler.sync(state.value)
                }
        }
    }
}
