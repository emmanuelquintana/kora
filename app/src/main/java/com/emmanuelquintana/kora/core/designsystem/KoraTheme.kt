package com.emmanuelquintana.kora.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/**
 * Tema raíz de Kora: Material 3 con la paleta, tipografía y formas de la
 * dirección "Hoja del día", más los tokens propios (estados clínicos,
 * numerales, espaciado y movimiento) accesibles mediante [Kora].
 */
@Composable
fun KoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tones = if (darkTheme) KoraPalette.Dark else KoraPalette.Light
    val colorScheme = remember(tones) { tones.toColorScheme() }
    val extendedColors = remember(tones) { tones.toExtendedColors() }
    val reducedMotion = rememberReducedMotion()
    val motion = remember(reducedMotion) { KoraMotionScheme(reducedMotion) }

    CompositionLocalProvider(
        LocalKoraColors provides extendedColors,
        LocalKoraNumerals provides KoraNumerals,
        LocalKoraSpacing provides KoraSpacing(),
        LocalKoraMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KoraTypography,
            shapes = KoraShapes,
            content = content,
        )
    }
}

/** Acceso a los tokens de Kora que Material 3 no cubre. */
object Kora {
    val colors: KoraExtendedColors
        @Composable @ReadOnlyComposable get() = LocalKoraColors.current

    val numerals: KoraNumeralStyles
        @Composable @ReadOnlyComposable get() = LocalKoraNumerals.current

    val spacing: KoraSpacing
        @Composable @ReadOnlyComposable get() = LocalKoraSpacing.current

    val motion: KoraMotionScheme
        @Composable @ReadOnlyComposable get() = LocalKoraMotion.current
}
