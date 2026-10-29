package com.emmanuelquintana.kora.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Escala de espaciado (base 4dp). Grupos apretados, separaciones generosas:
 * más espacio encima de un encabezado que debajo.
 */
@Immutable
data class KoraSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val section: Dp = 40.dp,
    /** Margen lateral en pantalla compacta. */
    val gutterCompact: Dp = 16.dp,
    /** Margen lateral en pantallas medianas y expandidas. */
    val gutterExpanded: Dp = 24.dp,
    /** Objetivo táctil mínimo (Material / WCAG). */
    val minTouchTarget: Dp = 48.dp,
    /** Ancho máximo de una columna de lectura. */
    val readingMaxWidth: Dp = 640.dp,
)

internal val LocalKoraSpacing = staticCompositionLocalOf { KoraSpacing() }

/**
 * Esquinas: suaves en controles, más amplias en hojas inferiores.
 * Las hojas de contenido son planas y se apoyan en pasos de tono, sin sombra.
 */
val KoraShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
