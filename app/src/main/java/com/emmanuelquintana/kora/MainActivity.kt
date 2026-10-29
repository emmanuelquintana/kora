package com.emmanuelquintana.kora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.emmanuelquintana.kora.core.designsystem.KoraTheme
import com.emmanuelquintana.kora.ui.bootstrap.BootstrapScreen

/**
 * BOOTSTRAP de la Parte 1: arranca el tema y una pantalla de verificación.
 * En la Parte 3 este archivo se sustituye por la versión con navegación
 * adaptativa, ViewModel compartido y manejo de intents de notificaciones.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KoraTheme {
                BootstrapScreen()
            }
        }
    }
}
