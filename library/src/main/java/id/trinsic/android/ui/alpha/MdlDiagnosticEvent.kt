package id.trinsic.android.ui.alpha

import id.trinsic.android.ui.BuildConfig

enum class MdlExchangeStage {
    VALIDATING_REQUEST,
    REQUESTING_CREDENTIAL,
    ENCODING_RESPONSE,
    COMPLETED
}

/** Metadata only: never contains request/response JSON, tokens, or exception messages/causes. */
class MdlDiagnosticEvent internal constructor(
    val stage: MdlExchangeStage,
    val exchangeId: String?,
    val errorType: MdlExchangeErrorType?
) {
    val sdkVersion: String = BuildConfig.SDK_VERSION
}

/** Called on the main thread. Listener exceptions do not affect the exchange. */
fun interface MdlDiagnosticListener {
    fun onEvent(event: MdlDiagnosticEvent)
}
