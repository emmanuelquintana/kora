package com.emmanuelquintana.kora.core.database

import android.content.Context
import com.emmanuelquintana.kora.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.tasks.await

/** ¿Hay un proyecto de Firebase configurado en este build? */
object FirebaseSupport {
    fun isAvailable(context: Context): Boolean =
        BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context.applicationContext).isNotEmpty()
}

/**
 * Acceso a Firestore con caché persistente (offline). Después de
 * [clearLocalData] la instancia anterior queda terminada y la siguiente
 * llamada a [firestore] crea una nueva.
 */
class FirestoreProvider(private val available: Boolean) {

    @Volatile
    private var configured = false

    fun firestore(): FirebaseFirestore {
        check(available) { "Firebase no está configurado en este build" }
        val instance = FirebaseFirestore.getInstance()
        if (!configured) {
            synchronized(this) {
                if (!configured) {
                    runCatching {
                        instance.firestoreSettings = FirebaseFirestoreSettings.Builder()
                            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(CACHE_BYTES).build())
                            .build()
                    }
                    configured = true
                }
            }
        }
        return instance
    }

    /**
     * Cierre de sesión o cambio de cuenta: detiene listeners y borra la caché
     * local de Firestore para que un dispositivo compartido no conserve datos
     * clínicos de la cuenta anterior.
     */
    suspend fun clearLocalData() {
        if (!available) return
        val instance = FirebaseFirestore.getInstance()
        runCatching { instance.terminate().await() }
        runCatching { instance.clearPersistence().await() }
        configured = false
    }

    private companion object {
        const val CACHE_BYTES = 50L * 1024 * 1024
    }
}
