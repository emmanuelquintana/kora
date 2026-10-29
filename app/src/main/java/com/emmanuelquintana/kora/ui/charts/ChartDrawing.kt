package com.emmanuelquintana.kora.ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Margenes del área de trazado (dejan sitio a las etiquetas de los ejes). */
internal fun DrawScope.plotArea(): Rect {
    val left = 40.dp.toPx()
    val top = 10.dp.toPx()
    val right = 10.dp.toPx()
    val bottom = 26.dp.toPx()
    return Rect(left, top, size.width - right, size.height - bottom)
}

internal fun Rect.xAt(fraction: Float): Float = left + fraction * width
internal fun Rect.yAt(normalized: Float): Float = bottom - normalized * height

/** Líneas guía horizontales tenues con su valor a la izquierda. */
internal fun DrawScope.drawYAxis(area: Rect, scale: AxisScale, measurer: TextMeasurer, style: TextStyle, rule: Color) {
    scale.ticks.forEach { tick ->
        val y = area.yAt(scale.normalize(tick))
        drawLine(rule, Offset(area.left, y), Offset(area.right, y), strokeWidth = 1.dp.toPx())
        val label = measurer.measure(tick.toInt().toString(), style)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(area.left - label.size.width - 6.dp.toPx(), y - label.size.height / 2f),
        )
    }
}

/** Fechas de inicio, mitad y fin del periodo bajo el eje X. */
internal fun DrawScope.drawXAxis(
    area: Rect,
    start: Instant,
    end: Instant,
    zone: ZoneId,
    format: (LocalDate) -> String,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val middle = Instant.ofEpochMilli((start.toEpochMilli() + end.toEpochMilli()) / 2)
    listOf(0f to start, 0.5f to middle, 1f to end).forEach { (fraction, instant) ->
        val label = measurer.measure(format(instant.atZone(zone).toLocalDate()), style)
        val x = (area.xAt(fraction) - label.size.width * fraction).coerceIn(0f, size.width - label.size.width)
        drawText(textLayoutResult = label, topLeft = Offset(x, area.bottom + 6.dp.toPx()))
    }
}

/** Dibuja cada tramo continuo como una línea; los huecos quedan abiertos. */
internal fun DrawScope.drawSegments(points: List<Offset>, segments: List<List<Int>>, color: Color) {
    segments.filter { it.size >= 2 }.forEach { segment ->
        val path = Path().apply {
            moveTo(points[segment.first()].x, points[segment.first()].y)
            segment.drop(1).forEach { lineTo(points[it].x, points[it].y) }
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

internal enum class PointSymbol { CIRCLE, SQUARE, DIAMOND }

/** Símbolo del punto. [hollow] marca lecturas fuera de rango (forma, no solo color). */
internal fun DrawScope.drawSymbol(symbol: PointSymbol, center: Offset, color: Color, hollow: Boolean, enlarged: Boolean) {
    val r = (if (enlarged) 6.dp else 4.dp).toPx()
    val style = if (hollow) Stroke(width = 2.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill
    when (symbol) {
        PointSymbol.CIRCLE -> drawCircle(color, radius = r, center = center, style = style)
        PointSymbol.SQUARE -> drawRect(color, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), style = style)
        PointSymbol.DIAMOND -> {
            val path = Path().apply {
                moveTo(center.x, center.y - r * 1.2f)
                lineTo(center.x + r * 1.2f, center.y)
                lineTo(center.x, center.y + r * 1.2f)
                lineTo(center.x - r * 1.2f, center.y)
                close()
            }
            drawPath(path, color, style = style)
        }
    }
}

internal fun DrawScope.drawSelection(x: Float, area: Rect, color: Color) {
    drawLine(
        color = color,
        start = Offset(x, area.top),
        end = Offset(x, area.bottom),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
    )
}

/** Índice del punto más cercano en X a un toque. */
internal fun nearestIndex(tapX: Float, xs: List<Float>): Int? =
    xs.indices.minByOrNull { kotlin.math.abs(xs[it] - tapX) }
