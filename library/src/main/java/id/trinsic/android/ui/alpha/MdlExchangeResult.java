package id.trinsic.android.ui.alpha;

/**
 * The result of an mDL exchange
 */
public class MdlExchangeResult {
    /**
     * Whether a credential was successfully exchanged.
     *
     * If false, `exception` is non-null. If true, `token` is non-null.
     */
    private boolean success;

    /**
     * The exception raised by the exchange, if any.
     */
    private Throwable exception;

    /**
     * The Exchange ID this callback is for.
     */
    private String exchangeId;

    /**
     * The resultant token from the exchange; send to Trinsic's API exactly as-is.
     */
    private String token;

    public MdlExchangeResult(String exchangeId, String token) {
        this.exchangeId = exchangeId;
        this.token = token;
        this.success = true;
        this.exception = null;
    }

    public MdlExchangeResult(String exchangeId, Throwable exception) {
        this.exchangeId = exchangeId;
        this.exception = exception;

        this.success = false;
        this.token = null;
    }

    public boolean getSuccess() {
        return success;
    }

    public Throwable getException() {
        return exception;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public String getToken() {
        return token;
    }
}
