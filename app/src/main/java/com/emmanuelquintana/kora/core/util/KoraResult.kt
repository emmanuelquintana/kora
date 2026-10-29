package com.emmanuelquintana.kora.core.util

/**
 * Errores que la UI sabe explicar. Nunca contienen valores clínicos ni
 * mensajes crudos del servidor: solo el tipo de fallo.
 */
enum class KoraError {
    NETWORK,
    PERMISSION_DENIED,
    SESSION_EXPIRED,
    NOT_FOUND,
    VALIDATION,
    NOT_CONFIGURED,
    UNKNOWN,
}

sealed interface KoraResult<out T> {
    data class Success<T>(val value: T) : KoraResult<T>
    data class Failure(val error: KoraError) : KoraResult<Nothing>
}

inline fun <T, R> KoraResult<T>.map(transform: (T) -> R): KoraResult<R> = when (this) {
    is KoraResult.Success -> KoraResult.Success(transform(value))
    is KoraResult.Failure -> this
}
