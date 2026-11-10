package id.trinsic.android.ui

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import java.util.concurrent.CancellationException

/** Stable categories for native credential exchange failures. */
enum class CredentialExchangeErrorType {
    CANCELLED,
    NO_MATCHING_CREDENTIAL,
    INVALID_CHALLENGE,
    PROVIDER_FAILURE,
    UNEXPECTED_RESPONSE,
    UNSUPPORTED_CHALLENGE_VERSION
}

/**
 * An SDK error with a nonblank message. [cause] retains the original Android/parser exception.
 * Provider messages and causes may contain sensitive data; do not use them as diagnostic events.
 */
class CredentialExchangeException internal constructor(
    val errorType: CredentialExchangeErrorType,
    message: String? = null,
    cause: Throwable? = null,
    val exchangeId: String? = null
) : Exception(message?.takeIf { it.isNotBlank() } ?: defaultMessage(errorType), cause) {
    internal companion object {
        fun fromException(exception: Throwable, exchangeId: String? = null): CredentialExchangeException {
            if (exception is CredentialExchangeException) return exception
            val type = when (exception) {
                is GetCredentialCancellationException, is CancellationException -> CredentialExchangeErrorType.CANCELLED
                is NoCredentialException -> CredentialExchangeErrorType.NO_MATCHING_CREDENTIAL
                else -> CredentialExchangeErrorType.PROVIDER_FAILURE
            }
            return CredentialExchangeException(type, exception.message, exception, exchangeId)
        }

        private fun defaultMessage(type: CredentialExchangeErrorType): String = when (type) {
            CredentialExchangeErrorType.CANCELLED -> "The credential exchange was cancelled."
            CredentialExchangeErrorType.NO_MATCHING_CREDENTIAL -> "No credential matching this request is available."
            CredentialExchangeErrorType.INVALID_CHALLENGE -> "The credential exchange request is invalid."
            CredentialExchangeErrorType.UNSUPPORTED_CHALLENGE_VERSION -> "The credential exchange request has no supported version."
            CredentialExchangeErrorType.PROVIDER_FAILURE -> "The Android credential provider could not complete the exchange."
            CredentialExchangeErrorType.UNEXPECTED_RESPONSE -> "The Android credential provider returned an unexpected response."
        }
    }
}
