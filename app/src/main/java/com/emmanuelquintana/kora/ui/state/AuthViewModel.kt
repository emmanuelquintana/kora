package com.emmanuelquintana.kora.ui.state

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emmanuelquintana.kora.AppContainer
import com.emmanuelquintana.kora.core.auth.AuthError
import com.emmanuelquintana.kora.core.auth.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_IN, REGISTER, RESET_PASSWORD }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isBusy: Boolean = false,
    val error: AuthError? = null,
    /** Correo de recuperación enviado (mensaje neutro: no revela si la cuenta existe). */
    val resetEmailSent: Boolean = false,
) {
    val emailLooksValid: Boolean get() = EMAIL.matches(email.trim())
    val passwordLongEnough: Boolean get() = password.length >= MIN_PASSWORD_LENGTH
    val canSubmit: Boolean
        get() = !isBusy && emailLooksValid && when (mode) {
            AuthMode.RESET_PASSWORD -> true
            else -> passwordLongEnough
        }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

/**
 * Inicio de sesión, registro y recuperación. El correo y el nombre se
 * conservan en SavedStateHandle; la contraseña NO (no se escribe en el
 * estado guardado que Android persiste en disco).
 */
class AuthViewModel(
    private val handle: SavedStateHandle,
    private val container: AppContainer,
) : ViewModel() {

    private val password = MutableStateFlow("")
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<AuthError?>(null)
    private val resetSent = MutableStateFlow(false)

    val uiState: StateFlow<AuthUiState> = combine(
        combine(
            handle.getStateFlow(KEY_MODE, AuthMode.SIGN_IN.name),
            handle.getStateFlow(KEY_EMAIL, ""),
            handle.getStateFlow(KEY_NAME, ""),
        ) { mode, email, name -> Triple(mode, email, name) },
        password,
        busy,
        error,
        resetSent,
    ) { (mode, email, name), pass, isBusy, err, sent ->
        AuthUiState(
            mode = runCatching { AuthMode.valueOf(mode) }.getOrDefault(AuthMode.SIGN_IN),
            email = email,
            password = pass,
            displayName = name,
            isBusy = isBusy,
            error = err,
            resetEmailSent = sent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthUiState())

    fun setMode(mode: AuthMode) {
        handle[KEY_MODE] = mode.name
        error.value = null
        resetSent.value = false
    }

    fun onEmailChange(value: String) {
        handle[KEY_EMAIL] = value.trim().take(MAX_EMAIL)
        error.value = null
    }

    fun onPasswordChange(value: String) {
        password.value = value.take(MAX_PASSWORD)
        error.value = null
    }

    fun onDisplayNameChange(value: String) {
        handle[KEY_NAME] = value.take(MAX_NAME)
    }

    fun submit() {
        val state = uiState.value
        if (!state.canSubmit) return
        perform {
            when (state.mode) {
                AuthMode.SIGN_IN -> container.authManager.signInWithEmail(state.email, state.password)
                AuthMode.REGISTER -> container.authManager.register(state.email, state.password, state.displayName)
                AuthMode.RESET_PASSWORD -> container.authManager.sendPasswordReset(state.email).also {
                    if (it is AuthResult.Success) resetSent.value = true
                }
            }
        }
    }

    /** [activityContext] debe ser la Activity: Credential Manager muestra su selector encima. */
    fun signInWithGoogle(activityContext: Context) {
        if (busy.value) return
        perform { container.authManager.signInWithGoogle(activityContext) }
    }

    fun dismissError() {
        error.value = null
    }

    private fun perform(block: suspend () -> AuthResult) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            error.value = null
            when (val result = block()) {
                AuthResult.Success -> password.value = ""
                is AuthResult.Failure -> error.value = result.error.takeUnless { it == AuthError.CANCELLED }
            }
            busy.value = false
        }
    }

    private companion object {
        const val KEY_MODE = "auth_mode"
        const val KEY_EMAIL = "auth_email"
        const val KEY_NAME = "auth_name"
        const val MAX_EMAIL = 254
        const val MAX_PASSWORD = 128
        const val MAX_NAME = 60
    }
}
