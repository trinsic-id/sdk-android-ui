package id.trinsic.android.ui.alpha

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import java.util.concurrent.CancellationException

/** Stable categories for native credential exchange failures. */
enum class MdlExchangeErrorType {
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
class MdlExchangeException internal constructor(
    val errorType: MdlExchangeErrorType,
    message: String? = null,
    cause: Throwable? = null,
    val exchangeId: String? = null
) : Exception(message?.takeIf { it.isNotBlank() } ?: defaultMessage(errorType), cause) {
    internal companion object {
        fun fromException(exception: Throwable, exchangeId: String? = null): MdlExchangeException {
            if (exception is MdlExchangeException) return exception
            val type = when (exception) {
                is GetCredentialCancellationException, is CancellationException -> MdlExchangeErrorType.CANCELLED
                is NoCredentialException -> MdlExchangeErrorType.NO_MATCHING_CREDENTIAL
                else -> MdlExchangeErrorType.PROVIDER_FAILURE
            }
            return MdlExchangeException(type, exception.message, exception, exchangeId)
        }

        private fun defaultMessage(type: MdlExchangeErrorType): String = when (type) {
            MdlExchangeErrorType.CANCELLED -> "The credential exchange was cancelled."
            MdlExchangeErrorType.NO_MATCHING_CREDENTIAL -> "No credential matching this request is available."
            MdlExchangeErrorType.INVALID_CHALLENGE -> "The credential exchange request is invalid."
            MdlExchangeErrorType.UNSUPPORTED_CHALLENGE_VERSION -> "The credential exchange request has no supported version."
            MdlExchangeErrorType.PROVIDER_FAILURE -> "The Android credential provider could not complete the exchange."
            MdlExchangeErrorType.UNEXPECTED_RESPONSE -> "The Android credential provider returned an unexpected response."
        }
    }
}
