package com.emmanuelquintana.kora.core.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

enum class SignOutReason { NONE, USER_REQUESTED, SESSION_EXPIRED }

sealed interface AuthState {
    /** Firebase no está configurado: la app funciona en modo demostración. */
    data object Unavailable : AuthState
    data object Initializing : AuthState
    data class SignedOut(val reason: SignOutReason) : AuthState
    data class SignedIn(val uid: String, val email: String?, val displayName: String?) : AuthState
}

enum class AuthError {
    INVALID_CREDENTIALS,
    INVALID_EMAIL,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    NETWORK,
    TOO_MANY_REQUESTS,
    CANCELLED,
    NO_GOOGLE_ACCOUNT,
    GOOGLE_NOT_CONFIGURED,
    NOT_CONFIGURED,
    UNKNOWN,
}

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

/**
 * Autenticación con Firebase Auth: correo/contraseña, recuperación de
 * contraseña e inicio con Google mediante Credential Manager
 * (GoogleIdTokenCredential → GoogleAuthProvider). No usa la API obsoleta de
 * Google Sign-In.
 */
class AuthManager(
    context: Context,
    private val available: Boolean,
    scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val auth: FirebaseAuth? = if (available) FirebaseAuth.getInstance() else null

    @Volatile
    private var userInitiatedSignOut = false

    @Volatile
    private var lastUid: String? = null

    val state: StateFlow<AuthState> = run {
        val source = if (auth == null) {
            flowOf<AuthState>(AuthState.Unavailable)
        } else {
            callbackFlow {
                val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                    trySend(mapUser(firebaseAuth.currentUser))
                }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }
        }
        source.stateIn(scope, SharingStarted.Eagerly, if (auth == null) AuthState.Unavailable else AuthState.Initializing)
    }

    /** UID actual de forma síncrona (receptores de alarmas). */
    val currentUid: String? get() = auth?.currentUser?.uid

    private fun mapUser(user: FirebaseUser?): AuthState {
        if (user != null) {
            lastUid = user.uid
            userInitiatedSignOut = false
            return AuthState.SignedIn(user.uid, user.email, user.displayName)
        }
        val reason = when {
            userInitiatedSignOut -> SignOutReason.USER_REQUESTED
            lastUid != null -> SignOutReason.SESSION_EXPIRED
            else -> SignOutReason.NONE
        }
        lastUid = null
        return AuthState.SignedOut(reason)
    }

    suspend fun signInWithEmail(email: String, password: String): AuthResult = guarded { auth ->
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(email: String, password: String, displayName: String?): AuthResult = guarded { auth ->
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
        if (name != null) {
            result.user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())?.await()
        }
    }

    /**
     * Envía el correo de recuperación. Por privacidad, un correo no registrado
     * se reporta como éxito: la UI no revela qué cuentas existen.
     */
    suspend fun sendPasswordReset(email: String): AuthResult {
        val result = guarded { auth -> auth.sendPasswordResetEmail(email.trim()).await() }
        return if (result is AuthResult.Failure && result.error == AuthError.INVALID_CREDENTIALS) AuthResult.Success else result
    }

    /**
     * Inicio de sesión con Google. [activityContext] debe ser una Activity: el
     * selector de cuentas de Credential Manager se muestra sobre ella.
     */
    suspend fun signInWithGoogle(activityContext: Context): AuthResult {
        val firebaseAuth = auth ?: return AuthResult.Failure(AuthError.NOT_CONFIGURED)
        val serverClientId = webClientId() ?: return AuthResult.Failure(AuthError.GOOGLE_NOT_CONFIGURED)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        return try {
            val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val credential = response.credential
            val isGoogleIdToken = credential is CustomCredential && (
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
                )
            if (!isGoogleIdToken) return AuthResult.Failure(AuthError.UNKNOWN)
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            AuthResult.Success
        } catch (_: GetCredentialCancellationException) {
            AuthResult.Failure(AuthError.CANCELLED)
        } catch (_: NoCredentialException) {
            AuthResult.Failure(AuthError.NO_GOOGLE_ACCOUNT)
        } catch (_: GoogleIdTokenParsingException) {
            AuthResult.Failure(AuthError.UNKNOWN)
        } catch (_: GetCredentialException) {
            AuthResult.Failure(AuthError.UNKNOWN)
        } catch (e: Exception) {
            AuthResult.Failure(e.toAuthError())
        }
    }

    /** Cierra la sesión de Firebase y olvida la credencial elegida en Credential Manager. */
    suspend fun signOut() {
        val firebaseAuth = auth ?: return
        userInitiatedSignOut = true
        firebaseAuth.signOut()
        runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** `default_web_client_id` lo genera el plugin google-services; sin Firebase no existe. */
    private fun webClientId(): String? {
        @Suppress("DiscouragedApi")
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        return if (id == 0) null else appContext.getString(id).takeIf { it.isNotBlank() }
    }

    private suspend fun guarded(block: suspend (FirebaseAuth) -> Unit): AuthResult {
        val firebaseAuth = auth ?: return AuthResult.Failure(AuthError.NOT_CONFIGURED)
        return try {
            block(firebaseAuth)
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Failure(e.toAuthError())
        }
    }

    private fun Exception.toAuthError(): AuthError = when (this) {
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is FirebaseAuthInvalidCredentialsException ->
            if (errorCode == "ERROR_INVALID_EMAIL") AuthError.INVALID_EMAIL else AuthError.INVALID_CREDENTIALS
        // Usuario inexistente o deshabilitado: mismo mensaje que credenciales inválidas.
        is FirebaseAuthInvalidUserException -> AuthError.INVALID_CREDENTIALS
        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_IN_USE
        is FirebaseNetworkException -> AuthError.NETWORK
        is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_REQUESTS
        else -> AuthError.UNKNOWN
    }
}
