package com.emmanuelquintana.kora.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.auth.AuthError
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.ui.components.KoraAlertBanner
import com.emmanuelquintana.kora.ui.components.SheetBinding
import com.emmanuelquintana.kora.ui.state.AuthMode
import com.emmanuelquintana.kora.ui.state.AuthUiState

class AuthActions(
    val onModeChange: (AuthMode) -> Unit,
    val onEmailChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onNameChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onGoogle: () -> Unit,
)

private fun AuthError.messageRes(): Int = when (this) {
    AuthError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
    AuthError.INVALID_EMAIL -> R.string.auth_error_invalid_email
    AuthError.EMAIL_IN_USE -> R.string.auth_error_email_in_use
    AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthError.NETWORK -> R.string.auth_error_network
    AuthError.TOO_MANY_REQUESTS -> R.string.auth_error_too_many
    AuthError.NO_GOOGLE_ACCOUNT -> R.string.auth_error_no_google
    AuthError.GOOGLE_NOT_CONFIGURED -> R.string.auth_error_google_not_configured
    AuthError.NOT_CONFIGURED -> R.string.error_not_configured
    AuthError.CANCELLED, AuthError.UNKNOWN -> R.string.auth_error_generic
}

/** Inicio de sesión, registro y recuperación en una sola hoja, sin pantallas de marketing. */
@Composable
fun AuthScreen(state: AuthUiState, actions: AuthActions, sessionExpired: Boolean, modifier: Modifier = Modifier) {
    val spacing = Kora.spacing
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutterCompact, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            SheetBinding()
            Text(stringResource(R.string.app_name), style = Kora.numerals.valueLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(
                    when (state.mode) {
                        AuthMode.SIGN_IN -> R.string.auth_title
                        AuthMode.REGISTER -> R.string.auth_register_title
                        AuthMode.RESET_PASSWORD -> R.string.auth_reset_title
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (sessionExpired) {
                KoraAlertBanner(
                    colors = Kora.colors.elevated,
                    icon = Icons.Rounded.Warning,
                    title = stringResource(R.string.sync_error),
                    body = stringResource(R.string.auth_session_expired),
                )
            }
            if (state.mode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = actions.onNameChange,
                    label = { Text(stringResource(R.string.auth_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = state.email,
                onValueChange = actions.onEmailChange,
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.mode != AuthMode.RESET_PASSWORD) {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = actions.onPasswordChange,
                    label = { Text(stringResource(R.string.auth_password)) },
                    supportingText = { Text(stringResource(R.string.auth_password_hint)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.error?.let { error ->
                Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.resetEmailSent) {
                Text(stringResource(R.string.auth_reset_sent), color = Kora.colors.normal.accent, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = actions.onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Text(
                    stringResource(
                        when (state.mode) {
                            AuthMode.SIGN_IN -> R.string.auth_sign_in
                            AuthMode.REGISTER -> R.string.auth_register
                            AuthMode.RESET_PASSWORD -> R.string.auth_send_reset
                        },
                    ),
                )
            }
            if (state.mode != AuthMode.RESET_PASSWORD) {
                OutlinedButton(
                    onClick = actions.onGoogle,
                    enabled = !state.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.auth_google)) }
            }
            Spacer(Modifier.height(spacing.xs))
            when (state.mode) {
                AuthMode.SIGN_IN -> {
                    TextButton(onClick = { actions.onModeChange(AuthMode.REGISTER) }) { Text(stringResource(R.string.auth_to_register)) }
                    TextButton(onClick = { actions.onModeChange(AuthMode.RESET_PASSWORD) }) { Text(stringResource(R.string.auth_to_reset)) }
                }
                else -> TextButton(onClick = { actions.onModeChange(AuthMode.SIGN_IN) }) { Text(stringResource(R.string.auth_to_sign_in)) }
            }
            Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
