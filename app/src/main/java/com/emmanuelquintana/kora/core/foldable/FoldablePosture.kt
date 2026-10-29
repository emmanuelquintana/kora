package com.emmanuelquintana.kora.core.foldable

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Clase de ancho independiente de la librería, para lógica pura y pruebas. */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/** Rectángulo en píxeles de ventana. */
@Immutable
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Lo que reporta Jetpack WindowManager de una bisagra física.
 *
 * @property bounds posición real en la ventana; en el Fold suele tener 0 px de
 *   alto/ancho (bisagra sin oclusión) y en otros plegables, el hueco físico.
 */
@Immutable
data class HingeSnapshot(
    val isHorizontal: Boolean,
    val isHalfOpened: Boolean,
    val isSeparating: Boolean,
    val isOccluding: Boolean,
    val bounds: PxRect,
)

/** Postura de la app. Un único estado de datos; solo cambia la composición. */
@Immutable
sealed interface KoraPosture {
    /** Pantalla exterior o teléfono convencional. */
    data object Compact : KoraPosture

    /** Ventana mediana (teléfono en horizontal, multiventana, tablet pequeña). */
    data object Medium : KoraPosture

    /** Pantalla interior desplegada o tablet. [hinge] presente si hay bisagra vertical que respetar. */
    data class Expanded(val hinge: HingeSnapshot?) : KoraPosture

    /** Modo Flex: bisagra horizontal semiabierta. Arriba se ve, abajo se captura. */
    data class Tabletop(val hinge: HingeSnapshot) : KoraPosture

    /** Semiabierto con bisagra vertical (como un libro). */
    data class Book(val hinge: HingeSnapshot) : KoraPosture
}

object PostureResolver {
    /**
     * Nunca deduce "plegable" solo por el ancho: Tabletop/Book exigen una
     * bisagra reportada en HALF_OPENED. Sin datos de bisagra se usa la clase
     * de tamaño de ventana.
     */
    fun resolve(width: WidthClass, hinges: List<HingeSnapshot>): KoraPosture {
        val halfOpened = hinges.firstOrNull { it.isHalfOpened }
        if (halfOpened != null) {
            return if (halfOpened.isHorizontal) KoraPosture.Tabletop(halfOpened) else KoraPosture.Book(halfOpened)
        }
        return when (width) {
            WidthClass.EXPANDED -> KoraPosture.Expanded(hinges.firstOrNull { !it.isHorizontal })
            WidthClass.MEDIUM -> KoraPosture.Medium
            WidthClass.COMPACT -> KoraPosture.Compact
        }
    }
}

internal fun FoldingFeature.toSnapshot() = HingeSnapshot(
    isHorizontal = orientation == FoldingFeature.Orientation.HORIZONTAL,
    isHalfOpened = state == FoldingFeature.State.HALF_OPENED,
    isSeparating = isSeparating,
    isOccluding = occlusionType == FoldingFeature.OcclusionType.FULL,
    bounds = PxRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
)

internal fun WindowWidthSizeClass.toWidthClass(): WidthClass = when (this) {
    WindowWidthSizeClass.EXPANDED -> WidthClass.EXPANDED
    WindowWidthSizeClass.MEDIUM -> WidthClass.MEDIUM
    else -> WidthClass.COMPACT
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Postura actual combinando WindowInfoTracker (bisagra real) y
 * currentWindowAdaptiveInfo (clase de tamaño). Se recalcula sola al plegar,
 * desplegar, rotar o cambiar el tamaño de ventana.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberKoraPosture(): KoraPosture {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val layoutFlow: Flow<WindowLayoutInfo?> = remember(activity) {
        activity?.let { WindowInfoTracker.getOrCreate(it).windowLayoutInfo(it) } ?: emptyFlow()
    }
    val layoutInfo by layoutFlow.collectAsState(initial = null)
    val width = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass.toWidthClass()
    val hinges = layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>()?.map { it.toSnapshot() }.orEmpty()
    return remember(width, hinges) { PostureResolver.resolve(width, hinges) }
}
