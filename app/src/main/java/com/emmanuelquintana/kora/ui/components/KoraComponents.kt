package com.emmanuelquintana.kora.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.designsystem.KoraStatusColors
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.ui.state.SyncStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun StatusShape.icon(): ImageVector = when (this) {
    StatusShape.CHECK -> Icons.Rounded.Check
    StatusShape.UP_ONE -> Icons.Rounded.KeyboardArrowUp
    StatusShape.UP_TWO -> Icons.Rounded.KeyboardDoubleArrowUp
    StatusShape.ALERT -> Icons.Rounded.PriorityHigh
    StatusShape.WARNING -> Icons.Rounded.Warning
    StatusShape.DOWN_ONE -> Icons.Rounded.KeyboardArrowDown
    StatusShape.DOWN_TWO -> Icons.Rounded.KeyboardDoubleArrowDown
    StatusShape.NONE -> Icons.Rounded.Remove
}

/** Forma del estado. Decorativa para lectores de pantalla: la palabra va al lado. */
@Composable
fun StatusMark(shape: StatusShape, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Icon(imageVector = shape.icon(), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Etiqueta de estado: forma + palabra sobre su contenedor. */
@Composable
fun StatusPill(visual: StatusVisual, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(visual.colors.container, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusMark(visual.shape, visual.colors.onContainer, size = 16.dp)
        Text(
            text = visual.label,
            style = MaterialTheme.typography.labelMedium,
            color = visual.colors.onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Encabezado de sección. Sin eyebrow: el título se sostiene solo. */
@Composable
fun KoraSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Estado vacío: qué falta y cómo resolverlo, en una frase. */
@Composable
fun KoraEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .padding(Kora.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Kora.spacing.xs),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
        }
    }
}

/**
 * Carga: esqueleto con la misma geometría que la hoja del día (sin spinner a
 * pantalla completa). Con movimiento reducido el esqueleto queda estático.
 */
@Composable
fun KoraLoadingState(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    val reduced = Kora.motion.reduced
    val alpha = if (reduced) {
        0.7f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val animated by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
        animated
    }
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = label }
            .graphicsLayer { this.alpha = alpha },
        verticalArrangement = Arrangement.spacedBy(Kora.spacing.sm),
    ) {
        listOf(0.35f, 0.6f, 1f, 1f, 0.8f).forEachIndexed { index, fraction ->
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(if (index == 1) 72.dp else 28.dp)
                    .background(block, MaterialTheme.shapes.small),
            )
        }
    }
}

/** Aviso destacado (estado crítico, permisos faltantes). Contenedor del estado, sin borde lateral. */
@Composable
fun KoraAlertBanner(
    colors: KoraStatusColors,
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.container, MaterialTheme.shapes.medium)
            .padding(Kora.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Kora.spacing.xs),
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(Kora.spacing.sm),
        ) {
            Icon(icon, contentDescription = null, tint = colors.onContainer, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(Kora.spacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onContainer)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onContainer)
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier
                    .align(Alignment.End)
                    .heightIn(min = 48.dp),
            ) {
                Text(actionLabel, color = colors.onContainer)
            }
        }
    }
}

/** Acceso rápido de registro: 56dp, verbo + objeto, retroalimentación de presión. */
@Composable
fun KoraQuickEntryButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressedScale(interaction)
    FilledTonalButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Kora.spacing.xs))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Estado de sincronización en palabras, junto a la fecha. */
@Composable
fun SyncBadge(status: SyncStatus, modifier: Modifier = Modifier) {
    val (icon, label) = when (status) {
        SyncStatus.DEMO -> Icons.Rounded.Science to R.string.sync_demo
        SyncStatus.SYNCED -> Icons.Rounded.CloudDone to R.string.sync_synced
        SyncStatus.PENDING -> Icons.Rounded.CloudUpload to R.string.sync_pending
        SyncStatus.OFFLINE -> Icons.Rounded.CloudOff to R.string.sync_offline
        SyncStatus.ERROR -> Icons.Rounded.ErrorOutline to R.string.sync_error
        SyncStatus.LOADING -> Icons.Rounded.Sync to R.string.sync_loading
    }
    val tint = if (status == SyncStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Banda de encuadernado con perforaciones del calendario de taco. */
@Composable
fun SheetBinding(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    val paper = MaterialTheme.colorScheme.background
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val bandHeight = size.height * 0.62f
        drawRoundRect(color = ink, size = Size(size.width, bandHeight), cornerRadius = CornerRadius(3.dp.toPx()))
        val radius = 2.5.dp.toPx()
        val step = 14.dp.toPx()
        var x = step / 2f
        while (x < size.width) {
            drawCircle(color = paper, radius = radius, center = Offset(x, bandHeight))
            x += step
        }
    }
}

/**
 * Cabecera de la hoja del día: el gesto distintivo de Kora. [compact] reduce
 * el numeral para la pantalla exterior en horizontal o el riel expandido.
 */
@Composable
fun DaySheetHead(
    today: LocalDate,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val locale = KoraFormats.locale
    val weekday = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE", locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val monthYear = remember(today) {
        today.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val spoken = stringResource(R.string.today_content_description, KoraFormats.longDate(today))
    Column(modifier = modifier.fillMaxWidth()) {
        SheetBinding()
        Spacer(Modifier.height(Kora.spacing.sm))
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = spoken },
            ) {
                Text(weekday, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    text = today.dayOfMonth.toString(),
                    style = if (compact) Kora.numerals.valueLarge else Kora.numerals.dayNumeral,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(monthYear, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.padding(top = 6.dp)) { trailing() }
        }
    }
}
