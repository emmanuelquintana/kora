package com.emmanuelquintana.kora.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Estado clínico listo para Compose. */
@Immutable
data class KoraStatusColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

/** Colores de Kora que Material 3 no modela: estados clínicos, series y reglas. */
@Immutable
data class KoraExtendedColors(
    val rule: Color,
    val seriesPrimary: Color,
    val seriesSecondary: Color,
    val seriesTertiary: Color,
    val normal: KoraStatusColors,
    val elevated: KoraStatusColors,
    val highStage1: KoraStatusColors,
    val highStage2: KoraStatusColors,
    val crisis: KoraStatusColors,
    val low: KoraStatusColors,
    val noData: KoraStatusColors,
)

internal val LocalKoraColors = staticCompositionLocalOf { KoraPalette.Light.toExtendedColors() }

private fun Long.c(): Color = Color(this.toInt())

private fun StatusTone.toColors() = KoraStatusColors(
    accent = accent.c(),
    container = container.c(),
    onContainer = onContainer.c(),
)

/**
 * Sin Dynamic Color a propósito: los colores de estado clínico deben
 * significar lo mismo en cualquier teléfono, y la identidad de Kora no
 * depende del fondo de pantalla.
 */
internal fun KoraTones.toColorScheme(): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = primary.c(),
            onPrimary = onPrimary.c(),
            primaryContainer = primaryContainer.c(),
            onPrimaryContainer = onPrimaryContainer.c(),
            inversePrimary = inversePrimary.c(),
            secondary = secondary.c(),
            onSecondary = onSecondary.c(),
            secondaryContainer = secondaryContainer.c(),
            onSecondaryContainer = onSecondaryContainer.c(),
            tertiary = tertiary.c(),
            onTertiary = onTertiary.c(),
            tertiaryContainer = tertiaryContainer.c(),
            onTertiaryContainer = onTertiaryContainer.c(),
            background = paper.c(),
            onBackground = ink.c(),
            surface = paper.c(),
            onSurface = ink.c(),
            surfaceVariant = paperHigh.c(),
            onSurfaceVariant = inkMuted.c(),
            surfaceTint = paper.c(),
            inverseSurface = inverseSurface.c(),
            inverseOnSurface = inverseOnSurface.c(),
            error = error.c(),
            onError = onError.c(),
            errorContainer = errorContainer.c(),
            onErrorContainer = onErrorContainer.c(),
            outline = outline.c(),
            outlineVariant = rule.c(),
            scrim = scrim.c(),
            surfaceBright = paperHighest.c(),
            surfaceDim = paper.c(),
            surfaceContainerLowest = paper.c(),
            surfaceContainerLow = paperLow.c(),
            surfaceContainer = paperContainer.c(),
            surfaceContainerHigh = paperHigh.c(),
            surfaceContainerHighest = paperHighest.c(),
        )
    } else {
        lightColorScheme(
            primary = primary.c(),
            onPrimary = onPrimary.c(),
            primaryContainer = primaryContainer.c(),
            onPrimaryContainer = onPrimaryContainer.c(),
            inversePrimary = inversePrimary.c(),
            secondary = secondary.c(),
            onSecondary = onSecondary.c(),
            secondaryContainer = secondaryContainer.c(),
            onSecondaryContainer = onSecondaryContainer.c(),
            tertiary = tertiary.c(),
            onTertiary = onTertiary.c(),
            tertiaryContainer = tertiaryContainer.c(),
            onTertiaryContainer = onTertiaryContainer.c(),
            background = paper.c(),
            onBackground = ink.c(),
            surface = paper.c(),
            onSurface = ink.c(),
            surfaceVariant = paperHigh.c(),
            onSurfaceVariant = inkMuted.c(),
            // surfaceTint = paper: anula la elevación tonal teñida de primario;
            // la profundidad la dan los pasos surfaceContainer*.
            surfaceTint = paper.c(),
            inverseSurface = inverseSurface.c(),
            inverseOnSurface = inverseOnSurface.c(),
            error = error.c(),
            onError = onError.c(),
            errorContainer = errorContainer.c(),
            onErrorContainer = onErrorContainer.c(),
            outline = outline.c(),
            outlineVariant = rule.c(),
            scrim = scrim.c(),
            surfaceBright = paper.c(),
            surfaceDim = paperHighest.c(),
            surfaceContainerLowest = 0xFFFFFFFF.c(),
            surfaceContainerLow = paperLow.c(),
            surfaceContainer = paperContainer.c(),
            surfaceContainerHigh = paperHigh.c(),
            surfaceContainerHighest = paperHighest.c(),
        )
    }
}

internal fun KoraTones.toExtendedColors() = KoraExtendedColors(
    rule = rule.c(),
    seriesPrimary = seriesPrimary.c(),
    seriesSecondary = seriesSecondary.c(),
    seriesTertiary = seriesTertiary.c(),
    normal = statusNormal.toColors(),
    elevated = statusElevated.toColors(),
    highStage1 = statusHighStage1.toColors(),
    highStage2 = statusHighStage2.toColors(),
    crisis = statusCrisis.toColors(),
    low = statusLow.toColors(),
    noData = statusNoData.toColors(),
)
