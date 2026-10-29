package com.emmanuelquintana.kora.core.designsystem

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garantiza que la paleta cumple WCAG 2.x en claro y oscuro:
 * texto ≥ 4.5:1 y gráficos/series ≥ 3:1 sobre todas las superficies de texto.
 * Si cambias un tono en KoraTones.kt y esta prueba falla, el build de CI falla.
 */
class KoraTonesContrastTest {

    private val palettes = mapOf("claro" to KoraPalette.Light, "oscuro" to KoraPalette.Dark)

    private fun assertContrast(name: String, fg: Long, bg: Long, minimum: Double) {
        val ratio = Contrast.ratio(fg, bg)
        assertTrue(
            "$name: contraste %.2f:1 < %.1f:1 (fg=%08X, bg=%08X)".format(ratio, minimum, fg, bg),
            ratio >= minimum,
        )
    }

    @Test
    fun bodyTextIsReadableOnEverySurface() {
        palettes.forEach { (theme, t) ->
            t.textSurfaces.forEach { surface ->
                assertContrast("$theme ink", t.ink, surface, 4.5)
                assertContrast("$theme inkMuted", t.inkMuted, surface, 4.5)
                assertContrast("$theme primary", t.primary, surface, 4.5)
                assertContrast("$theme error", t.error, surface, 4.5)
            }
        }
    }

    @Test
    fun statusAccentsAreReadableAsText() {
        palettes.forEach { (theme, t) ->
            val textStatuses = t.statuses - t.statusCrisis
            t.textSurfaces.forEach { surface ->
                textStatuses.forEachIndexed { i, status ->
                    assertContrast("$theme status[$i] accent", status.accent, surface, 4.5)
                }
            }
        }
    }

    @Test
    fun statusLabelsAreReadableOnTheirContainers() {
        palettes.forEach { (theme, t) ->
            t.statuses.forEachIndexed { i, status ->
                assertContrast("$theme status[$i] label", status.onContainer, status.container, 4.5)
            }
        }
    }

    @Test
    fun filledRolesKeepTheirLabelsReadable() {
        palettes.forEach { (theme, t) ->
            assertContrast("$theme onPrimary", t.onPrimary, t.primary, 4.5)
            assertContrast("$theme onPrimaryContainer", t.onPrimaryContainer, t.primaryContainer, 4.5)
            assertContrast("$theme onSecondaryContainer", t.onSecondaryContainer, t.secondaryContainer, 4.5)
            assertContrast("$theme onTertiaryContainer", t.onTertiaryContainer, t.tertiaryContainer, 4.5)
            assertContrast("$theme onError", t.onError, t.error, 4.5)
            assertContrast("$theme onErrorContainer", t.onErrorContainer, t.errorContainer, 4.5)
        }
    }

    @Test
    fun chartSeriesAndOutlinesMeetNonTextContrast() {
        palettes.forEach { (theme, t) ->
            t.textSurfaces.forEach { surface ->
                assertContrast("$theme seriesPrimary", t.seriesPrimary, surface, 3.0)
                assertContrast("$theme seriesSecondary", t.seriesSecondary, surface, 3.0)
                assertContrast("$theme seriesTertiary", t.seriesTertiary, surface, 3.0)
            }
            assertContrast("$theme outline", t.outline, t.paper, 3.0)
        }
    }
}
