package id.trinsic.android.ui.alpha

/**
 * Exactly one of [token] and [error] is non-null. Send a successful token to Trinsic unchanged.
 * [exchangeId] is null when the request could not be decoded or its ID was missing/invalid.
 */
class MdlExchangeResult private constructor(
    val exchangeId: String?,
    val token: String?,
    val error: MdlExchangeException?
) {
    val success: Boolean get() = error == null

    /** Compatibility accessor. Use [error] for its typed category and [Throwable.cause]. */
    val exception: Throwable? get() = error

    constructor(exchangeId: String?, token: String) : this(exchangeId, token, null)

    constructor(exchangeId: String?, exception: Throwable) :
        this(exchangeId, null, MdlExchangeException.fromException(exception, exchangeId))
}
