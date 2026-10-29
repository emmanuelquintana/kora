package com.emmanuelquintana.kora.data.repository

import android.content.Context
import com.emmanuelquintana.kora.core.auth.AuthManager
import com.emmanuelquintana.kora.core.auth.AuthState
import com.emmanuelquintana.kora.core.auth.SignOutReason
import com.emmanuelquintana.kora.core.database.FirestoreProvider
import com.emmanuelquintana.kora.core.database.KoraPreferences
import com.emmanuelquintana.kora.core.notifications.AlarmScheduler
import com.emmanuelquintana.kora.core.notifications.KoraNotifications
import com.emmanuelquintana.kora.core.util.KoraClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Quién usa la app y con qué fuente de datos. */
sealed interface Session {
    data object Initializing : Session

    /** Firebase no configurado: datos ficticios en memoria. */
    data class Demo(val repository: KoraRepository) : Session

    data class SignedOut(val reason: SignOutReason) : Session

    data class SignedIn(
        val uid: String,
        val email: String?,
        val displayName: String?,
        val repository: KoraRepository,
    ) : Session
}

val Session.repository: KoraRepository?
    get() = when (this) {
        is Session.Demo -> repository
        is Session.SignedIn -> repository
        else -> null
    }

/**
 * Elige el repositorio según el estado de autenticación y gestiona el cierre
 * de sesión seguro (alarmas, notificaciones, caché local y preferencias).
 */
class SessionManager(
    private val context: Context,
    private val authManager: AuthManager,
    private val firestoreProvider: FirestoreProvider,
    private val preferences: KoraPreferences,
    private val alarmScheduler: AlarmScheduler,
    private val clock: KoraClock,
    private val scope: CoroutineScope,
) {
    private val demoRepository by lazy { DemoRepository(clock) }

    private var cachedUid: String? = null
    private var cachedRepository: FirestoreRepository? = null

    val session: StateFlow<Session> = authManager.state
        .map { state ->
            when (state) {
                AuthState.Unavailable -> Session.Demo(demoRepository)
                AuthState.Initializing -> Session.Initializing
                is AuthState.SignedOut -> Session.SignedOut(state.reason)
                is AuthState.SignedIn -> Session.SignedIn(state.uid, state.email, state.displayName, repositoryFor(state.uid))
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, Session.Initializing)

    /** Repositorio activo de forma síncrona (receptores de notificaciones y alarmas). */
    fun currentRepository(): KoraRepository? {
        session.value.repository?.let { return it }
        // El proceso pudo arrancar por una alarma antes de que el listener de Auth emitiera.
        if (authManager.state.value == AuthState.Unavailable) return demoRepository
        val uid = authManager.currentUid ?: return null
        return repositoryFor(uid)
    }

    @Synchronized
    private fun repositoryFor(uid: String): FirestoreRepository {
        cachedRepository?.takeIf { cachedUid == uid }?.let { return it }
        return FirestoreRepository(firestoreProvider.firestore(), uid, clock, scope).also {
            cachedUid = uid
            cachedRepository = it
        }
    }

    /**
     * Cierre de sesión: nada de la cuenta anterior debe quedar en el dispositivo.
     * Cancela recordatorios, retira notificaciones, borra la caché de Firestore y
     * restablece preferencias de la cuenta.
     */
    suspend fun signOut() {
        alarmScheduler.cancelAll()
        KoraNotifications.cancelAll(context)
        authManager.signOut()
        synchronized(this) {
            cachedUid = null
            cachedRepository = null
        }
        firestoreProvider.clearLocalData()
        preferences.clearUserScoped()
    }
}
