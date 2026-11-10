package id.trinsic.android.ui

enum class CredentialExchangeStage {
    VALIDATING_REQUEST,
    REQUESTING_CREDENTIAL,
    ENCODING_RESPONSE,
    COMPLETED
}

/** Metadata only: never contains request/response JSON, tokens, or exception messages/causes. */
class CredentialExchangeDiagnosticEvent internal constructor(
    val stage: CredentialExchangeStage,
    val exchangeId: String?,
    val errorType: CredentialExchangeErrorType?
) {
    val sdkVersion: String = BuildConfig.SDK_VERSION
}

/** Called on the main thread. Listener exceptions do not affect the exchange. */
fun interface CredentialExchangeDiagnosticListener {
    fun onEvent(event: CredentialExchangeDiagnosticEvent)
}
