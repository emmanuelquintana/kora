package com.emmanuelquintana.kora.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.emmanuelquintana.kora.core.designsystem.Kora

/**
 * Retroalimentación de presión: escala 0.97 con resorte sin rebote. Úsese con
 * `Modifier.graphicsLayer { scaleX = s; scaleY = s }`. Con "Quitar animaciones"
 * devuelve siempre 1 (la pulsación conserva el ripple).
 */
@Composable
fun rememberPressedScale(interactionSource: InteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = Kora.motion
    val scale by animateFloatAsState(
        targetValue = if (pressed) motion.pressedScale else 1f,
        animationSpec = motion.responsive(),
        label = "pressScale",
    )
    return scale
}

/** Háptica con moderación: confirmar al guardar, rechazar en validación fallida. */
class KoraHaptics(private val view: View) {
    fun confirm() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }

    fun reject() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberKoraHaptics(): KoraHaptics {
    val view = LocalView.current
    return remember(view) { KoraHaptics(view) }
}
