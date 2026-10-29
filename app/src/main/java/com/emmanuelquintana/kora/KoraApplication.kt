package com.emmanuelquintana.kora

import android.app.Application
import android.os.StrictMode

/**
 * Punto de entrada del proceso: crea el contenedor de dependencias, registra
 * los canales de notificación y arranca la sincronización de alarmas.
 */
class KoraApplication : Application() {

    lateinit var container: AppContainer
        private set

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
        container = AppContainer(this)
        container.start()
    }
}
