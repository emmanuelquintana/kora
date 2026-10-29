package com.emmanuelquintana.kora.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.emmanuelquintana.kora.R

/**
 * Barlow (texto) + Barlow Condensed (numerales y valores clínicos), SIL OFL,
 * empaquetadas en res/font para no depender de Google Play Services.
 * Todas las medidas en sp: respetan el tamaño de fuente del sistema.
 */
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

val BarlowCondensed = FontFamily(
    Font(R.font.barlowcondensed_medium, FontWeight.Medium),
    Font(R.font.barlowcondensed_semibold, FontWeight.SemiBold),
)

/** Cifras tabulares: los valores que se actualizan no "bailan". */
private const val TabularFigures = "tnum"

private fun text(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = Barlow,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
    )

/** Escala Material 3 mapeada a Kora. El cuerpo base es 17sp para lectura cómoda. */
val KoraTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 64.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = TabularFigures,
    ),
    displayMedium = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp,
        lineHeight = 54.sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = TabularFigures,
    ),
    displaySmall = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.01).em,
        fontFeatureSettings = TabularFigures,
    ),
    headlineLarge = text(32, 38, FontWeight.SemiBold, -0.01),
    headlineMedium = text(28, 34, FontWeight.SemiBold, -0.01),
    headlineSmall = text(24, 30, FontWeight.SemiBold),
    titleLarge = text(22, 28, FontWeight.SemiBold),
    titleMedium = text(18, 24, FontWeight.SemiBold),
    titleSmall = text(16, 22, FontWeight.SemiBold),
    bodyLarge = text(17, 25),
    bodyMedium = text(16, 23),
    bodySmall = text(14, 20),
    labelLarge = text(16, 20, FontWeight.SemiBold),
    labelMedium = text(14, 18, FontWeight.Medium),
    labelSmall = text(13, 16, FontWeight.Medium),
)

/**
 * Estilos para cifras clínicas y la fecha de la hoja del día.
 * Siempre condensadas y tabulares; la unidad va aparte en [unit].
 */
@Immutable
data class KoraNumeralStyles(
    /** Numeral del día en la cabecera de la hoja. */
    val dayNumeral: TextStyle,
    /** Valor protagonista: última presión o glucosa en el Inicio. */
    val valueLarge: TextStyle,
    /** Valores en listas, historial y paneles. */
    val valueMedium: TextStyle,
    /** Valores compactos: ejes, tablas densas, stock. */
    val valueSmall: TextStyle,
    /** Unidad junto a un valor (mg/dL, mmHg, lpm). */
    val unit: TextStyle,
)

private fun numeral(size: Int, line: Int, weight: FontWeight = FontWeight.SemiBold, tracking: Double = -0.01) =
    TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
        fontFeatureSettings = TabularFigures,
    )

val KoraNumerals = KoraNumeralStyles(
    dayNumeral = numeral(112, 104, tracking = -0.03),
    valueLarge = numeral(56, 58, tracking = -0.02),
    valueMedium = numeral(34, 38),
    valueSmall = numeral(22, 26, FontWeight.Medium, 0.0),
    unit = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
)

internal val LocalKoraNumerals = staticCompositionLocalOf { KoraNumerals }
