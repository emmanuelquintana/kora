package com.emmanuelquintana.kora.core.designsystem

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Movimiento de Kora. Corto, con intención y nunca entre el usuario y su registro.
 *
 * - Entradas: ease-out fuerte (respuesta inmediata). Salidas: más rápidas que entradas.
 * - Movimiento en pantalla: ease-in-out fuerte. Nunca ease-in en UI.
 * - Nada aparece desde escala 0: mínimo 0.95 + opacidad.
 * - Acciones que se repiten decenas de veces al día (navegar, marcar una toma)
 *   no llevan coreografía, solo retroalimentación de presión.
 * - Con "Quitar animaciones" del sistema, el movimiento se vuelve corte o fundido.
 */
object KoraEasing {
    val Out: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val InOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    /** Hojas inferiores y paneles que se deslizan. */
    val Sheet: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
}

object KoraDurations {
    const val PRESS = 120
    const val QUICK = 180
    const val STANDARD = 240
    const val SHEET = 320
    const val EXIT = 160
}

@Immutable
class KoraMotionScheme(
    /** true si el sistema tiene las animaciones desactivadas. */
    val reduced: Boolean,
) {
    /** Escala al presionar un control (0.97, nunca menos de 0.95). */
    val pressedScale: Float get() = if (reduced) 1f else 0.97f

    fun <T> enter(durationMillis: Int = KoraDurations.STANDARD): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = KoraEasing.Out)

    fun <T> exit(): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(KoraDurations.EXIT, easing = KoraEasing.Out)

    fun <T> move(durationMillis: Int = KoraDurations.STANDARD): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = KoraEasing.InOut)

    /** Cambios de color o texto: se conservan aun con movimiento reducido (ayudan a entender). */
    fun <T> fade(durationMillis: Int = KoraDurations.QUICK): FiniteAnimationSpec<T> =
        tween(durationMillis, easing = KoraEasing.Out)

    /** Interacciones interrumpibles (arrastre, presión): resorte sin rebote. */
    fun <T> responsive(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

internal val LocalKoraMotion = staticCompositionLocalOf { KoraMotionScheme(reduced = false) }

private fun isAnimationDisabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Observa el ajuste "Quitar animaciones" y reacciona si cambia con la app abierta. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(isAnimationDisabled(context)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = isAnimationDisabled(context)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
