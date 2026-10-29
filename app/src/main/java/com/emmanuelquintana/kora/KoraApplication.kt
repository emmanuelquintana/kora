package com.emmanuelquintana.kora

import android.app.Application
import android.os.StrictMode

/**
 * Punto de entrada del proceso. En la Parte 2 aquí se crea el contenedor de
 * dependencias (repositorios, AuthManager, AlarmScheduler, UpdateChecker) y se
 * registran los canales de notificación.
 */
class KoraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Detecta accesos a disco/red en el hilo principal durante el desarrollo.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyLog().build(),
            )
        }
    }
}
