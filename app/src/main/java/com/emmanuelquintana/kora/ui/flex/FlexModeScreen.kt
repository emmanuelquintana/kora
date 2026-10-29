package com.emmanuelquintana.kora.ui.flex

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.foldable.HingeSnapshot
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.ui.components.PressureVital
import com.emmanuelquintana.kora.ui.components.SheetBinding
import com.emmanuelquintana.kora.ui.screens.PressureEntryForm
import com.emmanuelquintana.kora.ui.screens.PressureFormActions
import com.emmanuelquintana.kora.ui.state.BloodPressureEntryUiState
import com.emmanuelquintana.kora.ui.state.RestTimerState
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** Margen de seguridad respecto a la bisagra: ningún control esencial se acerca a ella. */
private val HingeClearance = 16.dp

/**
 * Modo Flex (bisagra horizontal semiabierta): estación de medición.
 * Arriba (lo que se ve): reposo de 5 min, última lectura y preparación.
 * Abajo (lo que se toca): sistólica, diastólica, pulso, brazo y Guardar.
 * La división usa la posición REAL de la bisagra reportada por WindowManager.
 */
@Composable
fun FlexModeScreen(
    hinge: HingeSnapshot,
    entryState: BloodPressureEntryUiState,
    entryActions: PressureFormActions,
    restTimer: RestTimerState,
    onStartRest: () -> Unit,
    onCancelRest: () -> Unit,
    lastReading: BloodPressureReading?,
    today: LocalDate,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hingeTop: Dp = with(density) { hinge.bounds.top.toDp() }
    val hingeBottom: Dp = with(density) { hinge.bounds.bottom.toDp() }

    Box(modifier.fillMaxSize()) {
        val topHeight = (hingeTop - HingeClearance).coerceAtLeast(0.dp)
        val bottomStart = hingeBottom + HingeClearance
        Column(Modifier.fillMaxSize()) {
            // Panel superior: hasta la bisagra.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(topHeight)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            ) {
                TopPane(restTimer, onStartRest, onCancelRest, lastReading, today)
            }
            Spacer(Modifier.height((bottomStart - topHeight).coerceAtLeast(0.dp)))
            // Panel inferior: desde la bisagra hasta el borde.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Kora.spacing.gutterExpanded),
                ) {
                    PressureEntryForm(
                        state = entryState,
                        actions = entryActions,
                        showTitle = false,
                        showNoteAndTime = false,
                        saveLabel = stringResource(R.string.flex_save),
                    )
                    Spacer(Modifier.height(Kora.spacing.md))
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TopPane(
    restTimer: RestTimerState,
    onStartRest: () -> Unit,
    onCancelRest: () -> Unit,
    lastReading: BloodPressureReading?,
    today: LocalDate,
) {
    val spacing = Kora.spacing
    val motion = Kora.motion
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.gutterExpanded, vertical = spacing.sm),
    ) {
        SheetBinding()
        Spacer(Modifier.height(spacing.sm))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.xl)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    stringResource(R.string.flex_rest_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                AnimatedContent(
                    targetState = restTimer,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.exit()) },
                    label = "restTimer",
                ) { timer ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        when (timer) {
                            RestTimerState.Idle -> {
                                Text(stringResource(R.string.flex_prep), style = MaterialTheme.typography.bodyLarge)
                                Button(onClick = onStartRest, modifier = Modifier.heightIn(min = 56.dp)) {
                                    Text(stringResource(R.string.flex_rest_start))
                                }
                            }
                            is RestTimerState.Running -> {
                                val clock = formatClock(timer.remaining)
                                Text(
                                    text = clock,
                                    style = Kora.numerals.valueLarge,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                                LinearProgressIndicator(progress = { timer.progress }, modifier = Modifier.fillMaxWidth())
                                Text(stringResource(R.string.flex_rest_running), style = MaterialTheme.typography.bodyLarge)
                                OutlinedButton(onClick = onCancelRest, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.flex_rest_cancel))
                                }
                            }
                            RestTimerState.Finished -> {
                                Text(
                                    stringResource(R.string.flex_rest_done),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Kora.colors.normal.accent,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                                Text(stringResource(R.string.flex_prep), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = onStartRest, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.flex_rest_start))
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.flex_last_reading), style = MaterialTheme.typography.titleMedium)
                PressureVital(lastReading, today, ZoneId.systemDefault())
            }
        }
    }
}

/** "4:05" (minutos:segundos, redondeando hacia arriba para no mostrar 0:00 antes de tiempo). */
private fun formatClock(remaining: Duration): String {
    val totalSeconds = (remaining.toMillis() + 999) / 1000
    return "%d:%02d".format(java.util.Locale.ROOT, totalSeconds / 60, totalSeconds % 60)
}
