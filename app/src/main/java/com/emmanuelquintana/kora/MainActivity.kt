package com.emmanuelquintana.kora

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmanuelquintana.kora.core.designsystem.KoraTheme
import com.emmanuelquintana.kora.core.notifications.KoraNotifications
import com.emmanuelquintana.kora.ui.navigation.KoraApp
import com.emmanuelquintana.kora.ui.navigation.KoraDestination
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Única Activity. Edge-to-edge, tema Kora y la raíz adaptativa. Los cambios de
 * tamaño y postura se manejan en Compose (ver configChanges en el manifest);
 * el estado vive en ViewModels y sobrevive también a la recreación.
 */
class MainActivity : ComponentActivity() {

    /** Destino pedido por una notificación (recordatorio, fin del reposo). */
    private val requestedDestination = MutableStateFlow<KoraDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            KoraTheme {
                val destination by requestedDestination.collectAsStateWithLifecycle()
                KoraApp(
                    requestedDestination = destination,
                    onRequestHandled = { requestedDestination.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        KoraDestination.fromNotification(intent?.getStringExtra(KoraNotifications.EXTRA_OPEN_DESTINATION))
            ?.let { requestedDestination.value = it }
    }
}
