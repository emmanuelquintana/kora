package com.emmanuelquintana.kora.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.BuildConfig
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.core.notifications.AlarmPrecision
import com.emmanuelquintana.kora.core.update.UpdateInfo
import com.emmanuelquintana.kora.core.update.UpdateState
import com.emmanuelquintana.kora.data.model.HealthRanges
import com.emmanuelquintana.kora.data.repository.Session
import com.emmanuelquintana.kora.domain.usecase.FormField
import com.emmanuelquintana.kora.domain.usecase.MeasurementValidator
import com.emmanuelquintana.kora.ui.components.KoraAlertBanner
import com.emmanuelquintana.kora.ui.components.KoraNumericInput
import com.emmanuelquintana.kora.ui.components.KoraSectionHeader
import com.emmanuelquintana.kora.ui.components.messageRes
import com.emmanuelquintana.kora.ui.state.KoraUiState
import kotlinx.coroutines.delay
import java.io.File

class ProfileActions(
    val onSaveRanges: (HealthRanges) -> Unit,
    val onRequestNotifications: () -> Unit,
    val onOpenExactAlarmSettings: () -> Unit,
    val onCheckUpdates: () -> Unit,
    val onDownloadUpdate: (UpdateInfo) -> Unit,
    val onCancelDownload: () -> Unit,
    val onInstall: (File) -> Unit,
    val onSignOut: () -> Unit,
)

@Composable
fun ProfileScreen(
    state: KoraUiState,
    actions: ProfileActions,
    installPermissionNeeded: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = Kora.spacing
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = spacing.gutterCompact, vertical = spacing.md)
            .widthIn(max = spacing.readingMaxWidth),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )

        KoraSectionHeader(stringResource(R.string.profile_account))
        when (val session = state.session) {
            is Session.SignedIn -> {
                Text(session.displayName ?: session.email.orEmpty(), style = MaterialTheme.typography.titleMedium)
                if (session.displayName != null && session.email != null) {
                    Text(session.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.profile_sign_out))
                }
            }
            else -> Text(stringResource(R.string.profile_demo_account), style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(spacing.lg))
        RangesSection(state.ranges, actions.onSaveRanges)

        Spacer(Modifier.height(spacing.lg))
        KoraSectionHeader(stringResource(R.string.profile_reminders))
        PermissionRow(
            ok = state.notificationsAllowed,
            okText = stringResource(R.string.profile_notifications_on),
            missingText = stringResource(R.string.profile_notifications_off),
            okIcon = { Icon(Icons.Rounded.Notifications, null, tint = Kora.colors.normal.accent) },
            missingIcon = { Icon(Icons.Rounded.NotificationsOff, null, tint = Kora.colors.elevated.accent) },
            actionLabel = stringResource(R.string.profile_notifications_enable),
            onAction = actions.onRequestNotifications,
        )
        PermissionRow(
            ok = state.alarmPrecision == AlarmPrecision.EXACT,
            okText = stringResource(R.string.profile_exact_on),
            missingText = stringResource(R.string.profile_exact_off),
            okIcon = { Icon(Icons.Rounded.Alarm, null, tint = Kora.colors.normal.accent) },
            missingIcon = { Icon(Icons.Rounded.AlarmOff, null, tint = Kora.colors.elevated.accent) },
            actionLabel = stringResource(R.string.profile_exact_enable),
            onAction = actions.onOpenExactAlarmSettings,
        )

        Spacer(Modifier.height(spacing.lg))
        KoraSectionHeader(stringResource(R.string.profile_updates))
        UpdateSection(state.update, actions, installPermissionNeeded)

        Spacer(Modifier.height(spacing.lg))
        KoraSectionHeader(stringResource(R.string.profile_build))
        Text(stringResource(R.string.profile_version, BuildConfig.VERSION_NAME), style = Kora.numerals.valueSmall)
        Text(
            stringResource(
                when (BuildConfig.SIGNING_KIND) {
                    "persistent" -> R.string.signing_persistent
                    "ephemeral" -> R.string.signing_ephemeral
                    else -> R.string.signing_unsigned
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (BuildConfig.SIGNING_KIND == "ephemeral") {
            Text(stringResource(R.string.signing_ephemeral_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(spacing.lg))
        HorizontalDivider(color = Kora.colors.rule)
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(spacing.xl))
    }

    if (confirmSignOut) {
        ConfirmDialog(
            message = stringResource(R.string.profile_sign_out_confirm),
            confirmLabel = stringResource(R.string.profile_sign_out),
            onConfirm = {
                confirmSignOut = false
                actions.onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

@Composable
private fun RangesSection(ranges: HealthRanges, onSave: (HealthRanges) -> Unit) {
    var low by rememberSaveable(ranges) { mutableStateOf(ranges.glucoseLowMgDl.toString()) }
    var high by rememberSaveable(ranges) { mutableStateOf(ranges.glucoseHighMgDl.toString()) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val validation = MeasurementValidator.validateRanges(low, high)
    val changed = low != ranges.glucoseLowMgDl.toString() || high != ranges.glucoseHighMgDl.toString()
    LaunchedEffect(saved) {
        if (saved) {
            delay(2_500)
            saved = false
        }
    }

    KoraSectionHeader(stringResource(R.string.profile_ranges))
    Text(stringResource(R.string.profile_ranges_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(Kora.spacing.sm)) {
        KoraNumericInput(
            value = low,
            onValueChange = { low = it.filter(Char::isDigit).take(3) },
            label = stringResource(R.string.profile_range_low),
            unit = stringResource(R.string.unit_mg_dl),
            issue = validation[FormField.RANGE_LOW],
            showError = showErrors,
            modifier = Modifier.weight(1f),
        )
        KoraNumericInput(
            value = high,
            onValueChange = { high = it.filter(Char::isDigit).take(3) },
            label = stringResource(R.string.profile_range_high),
            unit = stringResource(R.string.unit_mg_dl),
            issue = validation[FormField.RANGE_HIGH],
            showError = showErrors,
            modifier = Modifier.weight(1f),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Kora.spacing.sm)) {
        FilledTonalButton(
            enabled = changed,
            onClick = {
                val parsed = MeasurementValidator.rangesFrom(low, high)
                if (parsed == null) {
                    showErrors = true
                } else {
                    showErrors = false
                    saved = true
                    onSave(parsed)
                }
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.save)) }
        if (saved) {
            Text(stringResource(R.string.profile_ranges_saved), style = MaterialTheme.typography.bodyMedium, color = Kora.colors.normal.accent)
        }
    }
}

@Composable
private fun PermissionRow(
    ok: Boolean,
    okText: String,
    missingText: String,
    okIcon: @Composable () -> Unit,
    missingIcon: @Composable () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = Kora.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Kora.spacing.sm)) {
            if (ok) okIcon() else missingIcon()
            Text(if (ok) okText else missingText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        if (!ok) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
        }
    }
}

/** Máquina de estados del actualizador en palabras: buscar → descargar (cancelable) → instalar. */
@Composable
fun UpdateSection(update: UpdateState, actions: ProfileActions, installPermissionNeeded: Boolean) {
    val motion = Kora.motion
    AnimatedContent(
        targetState = update,
        contentKey = { it::class },
        transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.exit()) },
        label = "updateState",
    ) { state ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Kora.spacing.xs)) {
            when (state) {
                UpdateState.Idle -> CheckButton(actions.onCheckUpdates)
                UpdateState.Checking -> {
                    Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyLarge)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is UpdateState.UpToDate -> {
                    Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodyLarge)
                    CheckButton(actions.onCheckUpdates)
                }
                is UpdateState.Available -> {
                    Text(stringResource(R.string.update_available, state.info.version.toString()), style = MaterialTheme.typography.titleMedium)
                    ReleaseNotes(state.info.notes)
                    Button(onClick = { actions.onDownloadUpdate(state.info) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.update_download))
                    }
                }
                is UpdateState.Downloading -> {
                    Text(stringResource(R.string.update_downloading, state.version), style = MaterialTheme.typography.titleMedium)
                    val percent = state.percent
                    if (percent != null) {
                        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("$percent %", style = Kora.numerals.valueSmall)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = actions.onCancelDownload, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.update_cancel))
                    }
                }
                is UpdateState.ReadyToInstall -> {
                    Text(stringResource(R.string.update_ready, state.version), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (state.checksumVerified) R.string.update_verified else R.string.update_signature_only),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Kora.colors.normal.accent,
                    )
                    state.info?.let { ReleaseNotes(it.notes) }
                    if (installPermissionNeeded) {
                        Text(stringResource(R.string.update_permission_needed), style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { actions.onInstall(state.file) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.update_install))
                    }
                }
                is UpdateState.Failed -> {
                    KoraAlertBanner(
                        colors = Kora.colors.noData,
                        icon = Icons.Rounded.ErrorOutline,
                        title = stringResource(R.string.profile_updates),
                        body = stringResource(state.reason.messageRes()),
                        actionLabel = stringResource(R.string.retry),
                        onAction = { state.info?.let(actions.onDownloadUpdate) ?: actions.onCheckUpdates() },
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.update_check)) }
}

@Composable
private fun ReleaseNotes(notes: String) {
    if (notes.isBlank()) return
    Text(stringResource(R.string.update_notes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(notes.take(1_200), style = MaterialTheme.typography.bodyMedium)
}
