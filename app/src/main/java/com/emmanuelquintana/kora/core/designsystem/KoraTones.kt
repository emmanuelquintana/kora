package com.emmanuelquintana.kora.core.designsystem

/**
 * Tonos de Kora en ARGB (Long), sin dependencias de Compose para poder
 * verificarlos en pruebas JVM (contraste WCAG en KoraTonesContrastTest).
 *
 * Dirección "Hoja del día": papel blanco cálido, tinta casi negra, petróleo
 * para acciones y la serie principal, rojo de calendario solo para alertas.
 * Las superficies se separan por un paso de tono, nunca por sombras.
 */
data class StatusTone(
    /** Texto, íconos y marcas sobre papel (≥ 4.5:1 contra todas las superficies). */
    val accent: Long,
    /** Fondo de la etiqueta de estado. */
    val container: Long,
    /** Texto sobre [container] (≥ 4.5:1). */
    val onContainer: Long,
)

data class KoraTones(
    val isDark: Boolean,

    // Papel y tinta
    val paper: Long,
    val paperLow: Long,
    val paperContainer: Long,
    val paperHigh: Long,
    val paperHighest: Long,
    val ink: Long,
    val inkMuted: Long,
    val outline: Long,
    val rule: Long,

    // Roles de acción
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val secondary: Long,
    val onSecondary: Long,
    val secondaryContainer: Long,
    val onSecondaryContainer: Long,
    val tertiary: Long,
    val onTertiary: Long,
    val tertiaryContainer: Long,
    val onTertiaryContainer: Long,
    val error: Long,
    val onError: Long,
    val errorContainer: Long,
    val onErrorContainer: Long,
    val inverseSurface: Long,
    val inverseOnSurface: Long,
    val inversePrimary: Long,
    val scrim: Long,

    // Series de gráficas (≥ 3:1 sobre papel; además se distinguen por símbolo)
    val seriesPrimary: Long,
    val seriesSecondary: Long,
    val seriesTertiary: Long,

    // Estados clínicos de referencia (nunca se comunican solo por color)
    val statusNormal: StatusTone,
    val statusElevated: StatusTone,
    val statusHighStage1: StatusTone,
    val statusHighStage2: StatusTone,
    val statusCrisis: StatusTone,
    val statusLow: StatusTone,
    val statusNoData: StatusTone,
) {
    /** Superficies sobre las que puede aparecer texto. */
    val textSurfaces: List<Long>
        get() = listOf(paper, paperLow, paperContainer, paperHigh)

    val statuses: List<StatusTone>
        get() = listOf(
            statusNormal, statusElevated, statusHighStage1, statusHighStage2,
            statusCrisis, statusLow, statusNoData,
        )
}

object KoraPalette {

    val Light = KoraTones(
        isDark = false,
        paper = 0xFFFBFAF7,
        paperLow = 0xFFF4F2ED,
        paperContainer = 0xFFEDEAE3,
        paperHigh = 0xFFE6E2DA,
        paperHighest = 0xFFDFDBD2,
        ink = 0xFF1A1C1B,
        inkMuted = 0xFF4F5452,
        outline = 0xFF7A7F7C,
        rule = 0xFFD8D4CB,

        primary = 0xFF0E6B6B,
        onPrimary = 0xFFFFFFFF,
        primaryContainer = 0xFFCFE8E5,
        onPrimaryContainer = 0xFF003634,
        secondary = 0xFF4F5F5C,
        onSecondary = 0xFFFFFFFF,
        secondaryContainer = 0xFFDCE4E2,
        onSecondaryContainer = 0xFF0E1F1C,
        tertiary = 0xFF8A5A00,
        onTertiary = 0xFFFFFFFF,
        tertiaryContainer = 0xFFFCE7C2,
        onTertiaryContainer = 0xFF2E1D00,
        error = 0xFFB42318,
        onError = 0xFFFFFFFF,
        errorContainer = 0xFFFADBD7,
        onErrorContainer = 0xFF47090A,
        inverseSurface = 0xFF2F3130,
        inverseOnSurface = 0xFFF1EFEA,
        inversePrimary = 0xFF7FD3CC,
        scrim = 0xFF000000,

        seriesPrimary = 0xFF0E6B6B,
        seriesSecondary = 0xFF7A4E2D,
        seriesTertiary = 0xFF5A6130,

        statusNormal = StatusTone(0xFF2F6B3B, 0xFFDDEFD9, 0xFF0E2A14),
        statusElevated = StatusTone(0xFF8A5A00, 0xFFFCE7C2, 0xFF2E1D00),
        statusHighStage1 = StatusTone(0xFFA4410E, 0xFFFBDCCB, 0xFF3A1503),
        statusHighStage2 = StatusTone(0xFFB42318, 0xFFFADBD7, 0xFF47090A),
        statusCrisis = StatusTone(0xFF7F1209, 0xFF7F1209, 0xFFFFFFFF),
        statusLow = StatusTone(0xFF1F4E8C, 0xFFDCE6F6, 0xFF0A1E3D),
        statusNoData = StatusTone(0xFF5F6461, 0xFFECEAE5, 0xFF2B2F2D),
    )

    val Dark = KoraTones(
        isDark = true,
        paper = 0xFF121514,
        paperLow = 0xFF181C1A,
        paperContainer = 0xFF1D2120,
        paperHigh = 0xFF252A28,
        paperHighest = 0xFF2E3431,
        ink = 0xFFE6E3DC,
        inkMuted = 0xFFB8BDB9,
        outline = 0xFF8A908C,
        rule = 0xFF3A403D,

        primary = 0xFF7FD3CC,
        onPrimary = 0xFF003735,
        primaryContainer = 0xFF0B4F4E,
        onPrimaryContainer = 0xFFBFEDE8,
        secondary = 0xFFB5C9C5,
        onSecondary = 0xFF20322F,
        secondaryContainer = 0xFF364946,
        onSecondaryContainer = 0xFFD1E6E1,
        tertiary = 0xFFF2C26B,
        onTertiary = 0xFF422C00,
        tertiaryContainer = 0xFF4A3300,
        onTertiaryContainer = 0xFFFFE3B5,
        error = 0xFFFF9A8E,
        onError = 0xFF5A0A05,
        errorContainer = 0xFF8C1D13,
        onErrorContainer = 0xFFFFDAD5,
        inverseSurface = 0xFFE6E3DC,
        inverseOnSurface = 0xFF2F3130,
        inversePrimary = 0xFF0E6B6B,
        scrim = 0xFF000000,

        seriesPrimary = 0xFF7FD3CC,
        seriesSecondary = 0xFFE0B08A,
        seriesTertiary = 0xFFB9C58A,

        statusNormal = StatusTone(0xFF8FD19A, 0xFF1E3D24, 0xFFCDEFD2),
        statusElevated = StatusTone(0xFFF2C26B, 0xFF4A3300, 0xFFFFE3B5),
        statusHighStage1 = StatusTone(0xFFFFAB80, 0xFF5C2507, 0xFFFFDCCB),
        statusHighStage2 = StatusTone(0xFFFF9A8E, 0xFF6E1510, 0xFFFFDAD5),
        statusCrisis = StatusTone(0xFFFF8A7D, 0xFFA3180D, 0xFFFFFFFF),
        statusLow = StatusTone(0xFFA9C7F5, 0xFF183863, 0xFFD9E6FF),
        statusNoData = StatusTone(0xFFA9AEAA, 0xFF2A2F2D, 0xFFDADDD9),
    )
}

/** Contraste WCAG 2.x entre dos colores ARGB opacos. */
object Contrast {
    fun ratio(foreground: Long, background: Long): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
