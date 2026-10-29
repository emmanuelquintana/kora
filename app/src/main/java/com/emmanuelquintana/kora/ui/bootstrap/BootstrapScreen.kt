package com.emmanuelquintana.kora.ui.bootstrap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import com.emmanuelquintana.kora.BuildConfig
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * BOOTSTRAP de la Parte 1. Verifica en el dispositivo que tema, fuentes,
 * modo claro/oscuro, firma y clase de ventana funcionan.
 * Se sustituye en la Parte 3 por la navegación adaptativa real
 * (CompactScreen / FlexModeScreen / ExpandedDualPaneScreen).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun BootstrapScreen(modifier: Modifier = Modifier) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val widthClass = adaptiveInfo.windowSizeClass.windowWidthSizeClass
    val posture = adaptiveInfo.windowPosture
    val spacing = Kora.spacing
    val gutter = if (widthClass == WindowWidthSizeClass.COMPACT) spacing.gutterCompact else spacing.gutterExpanded

    val windowLabel = stringResource(
        when (widthClass) {
            WindowWidthSizeClass.EXPANDED -> R.string.window_expanded
            WindowWidthSizeClass.MEDIUM -> R.string.window_medium
            else -> R.string.window_compact
        },
    )
    val postureLabel = stringResource(
        when {
            posture.isTabletop -> R.string.posture_tabletop
            posture.hingeList.any { it.isVertical && !it.isFlat } -> R.string.posture_book
            else -> R.string.posture_flat
        },
    )
    val signingLabel = stringResource(
        when (BuildConfig.SIGNING_KIND) {
            "persistent" -> R.string.signing_persistent
            "ephemeral" -> R.string.signing_ephemeral
            else -> R.string.signing_unsigned
        },
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = spacing.readingMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = gutter, vertical = spacing.md),
            ) {
                DaySheetHead()

                Spacer(Modifier.height(spacing.lg))

                if (BuildConfig.FIREBASE_CONFIGURED) {
                    Notice(
                        icon = Icons.Outlined.CloudDone,
                        title = stringResource(R.string.connected_mode_title),
                        body = stringResource(R.string.connected_mode_body),
                        container = Kora.colors.normal.container,
                        content = Kora.colors.normal.onContainer,
                    )
                } else {
                    Notice(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.demo_mode_title),
                        body = stringResource(R.string.demo_mode_body),
                        container = Kora.colors.noData.container,
                        content = Kora.colors.noData.onContainer,
                    )
                }

                if (BuildConfig.SIGNING_KIND == "ephemeral") {
                    Spacer(Modifier.height(spacing.xs))
                    Notice(
                        icon = Icons.Outlined.WarningAmber,
                        title = signingLabel,
                        body = stringResource(R.string.signing_ephemeral_warning),
                        container = Kora.colors.elevated.container,
                        content = Kora.colors.elevated.onContainer,
                    )
                }

                Spacer(Modifier.height(spacing.section))

                Text(
                    text = stringResource(R.string.bootstrap_heading),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = stringResource(R.string.bootstrap_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(Modifier.height(spacing.section))

                Text(
                    text = stringResource(R.string.build_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(spacing.xs))
                InfoRow(stringResource(R.string.build_version), BuildConfig.VERSION_NAME, numeric = true)
                InfoRow(stringResource(R.string.build_commit), BuildConfig.GIT_SHA, numeric = true)
                InfoRow(stringResource(R.string.build_signing), signingLabel)
                InfoRow(stringResource(R.string.build_window), windowLabel)
                InfoRow(stringResource(R.string.build_posture), postureLabel, showDivider = false)

                Spacer(Modifier.height(spacing.xl))

                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

/**
 * Cabecera de la hoja del día: encuadernado perforado, día de la semana,
 * numeral grande y mes. Es el gesto distintivo de Kora y se reutilizará en el Inicio.
 */
@Composable
private fun DaySheetHead() {
    val locale = remember { Locale.forLanguageTag("es-MX") }
    val today = remember { LocalDate.now() }
    val weekday = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE", locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val monthYear = remember(today) {
        today.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val spoken = stringResource(
        R.string.today_content_description,
        today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", locale)),
    )

    val ink = MaterialTheme.colorScheme.onBackground
    val paper = MaterialTheme.colorScheme.background

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        SheetBinding(ink = ink, perforation = paper)
        Spacer(Modifier.height(Kora.spacing.md))
        Text(
            text = weekday,
            style = MaterialTheme.typography.titleLarge,
            color = ink,
        )
        Text(
            text = today.dayOfMonth.toString(),
            style = Kora.numerals.dayNumeral,
            color = ink,
        )
        Text(
            text = monthYear,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Banda de encuadernado con perforaciones, como el taco de un calendario. */
@Composable
private fun SheetBinding(ink: Color, perforation: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val bandHeight = size.height * 0.62f
        drawRect(color = ink, size = size.copy(height = bandHeight))
        val radius = 2.5.dp.toPx()
        val step = 14.dp.toPx()
        var x = step / 2f
        while (x < size.width) {
            drawCircle(color = perforation, radius = radius, center = Offset(x, bandHeight))
            x += step
        }
    }
}

@Composable
private fun Notice(
    icon: ImageVector,
    title: String,
    body: String,
    container: Color,
    content: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, MaterialTheme.shapes.medium)
            .padding(Kora.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Kora.spacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(24.dp),
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = content)
            Spacer(Modifier.height(Kora.spacing.xxs))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    numeric: Boolean = false,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Kora.spacing.minTouchTarget)
                .padding(vertical = Kora.spacing.sm)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Kora.spacing.md),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.4f),
            )
            Text(
                text = value,
                style = if (numeric) Kora.numerals.valueSmall else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.6f),
            )
        }
        if (showDivider) {
            HorizontalDivider(color = Kora.colors.rule)
        }
    }
}
