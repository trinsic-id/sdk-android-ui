package id.trinsic.android.ui.alpha;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Base64;

/**
 * Inner class for processing a Trinsic-created request object.
 */
class MdlExchangeOuterRequest {
    /**
     * The Trinsic Exchange ID this request is for
     */
    private String exchangeId;

    /**
     * The type string of this request.
     *
     * Currently always "mdlRequest".
     */
    private String type;

    /**
     * The platform this request is for.
     *
     * Should be "apple-wallet" or "google-wallet".
     */
    private String platform;

    /**
     * The exchange mechanism.
     *
     * Should be "DigitalCredentialsApi" or "NativeApp".
     */
    private String exchangeMechanism;

    /**
     * The request object string.
     */
    private String requestObject;

    private MdlExchangeOuterRequest(String exchangeId, String type, String platform, String exchangeMechanism, String requestObject) {
        this.exchangeId = exchangeId;
        this.type = type;
        this.platform = platform;
        this.exchangeMechanism = exchangeMechanism;
        this.requestObject = requestObject;
    }

    public static MdlExchangeOuterRequest Parse(String requestObjectBase64Encoded) {
        // Decode outer JSON request object
        String outerRequestJson = new String(Base64.getUrlDecoder().decode(requestObjectBase64Encoded));
        JSONObject outerRequest;

        try {
            outerRequest = new JSONObject(outerRequestJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid request object: cannot deserialize");
        }

        // Extract each value
        String exchangeId, type, platform, exchangeMechanism, innerRequestObjectEncoded;
        try {
            exchangeId = outerRequest.getString("exchangeId");
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid request object: missing or invalid `exchangeId` field.");
        }

        try {
            type = outerRequest.getString("type");
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid request object: missing or invalid `type` field.");
        }

        try {
            platform = outerRequest.getString("platform");
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid request object: missing or invalid `platform` field.");
        }

        try {
            exchangeMechanism = outerRequest.getString("exchangeMechanism");
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid request object: missing or invalid `exchangeMechanism` field.");
        }

        try {
            innerRequestObjectEncoded = outerRequest.getString("requestObject");
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid request object: missing or invalid `requestObject` field.");
        }

        // Validate each value
        if (!type.equals("mdlRequest")) {
            throw new IllegalArgumentException("Invalid request object: `type` must have value 'mdlRequest'.");
        }

        if (!platform.equals("google-wallet")) {
            throw new IllegalArgumentException("This request object was created for the platform '" + platform + "'; only 'google-wallet' is supported by this SDK.");
        }

        if (exchangeMechanism.equals("DigitalCredentialsApi")) {
            throw new IllegalArgumentException("This request object was created for the 'DigitalCredentialsApi' exchange mechanism; only 'NativeApp' is supported by this SDK.");
        } else if (!exchangeMechanism.equals("NativeApp")) {
            throw new IllegalArgumentException("This request object was created for unknown exchange mechanism '" + exchangeMechanism + "'; only 'NativeApp' is supported by this SDK.");
        }

        if (innerRequestObjectEncoded.isEmpty()) {
            throw new IllegalArgumentException("Invalid request object: empty inner request");
        }

        return new MdlExchangeOuterRequest(exchangeId, type, platform, exchangeMechanism, innerRequestObjectEncoded);
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public String getType() {
        return type;
    }

    public String getPlatform() {
        return platform;
    }

    public String getExchangeMechanism() {
        return exchangeMechanism;
    }

    public String getRequestObject() {
        return requestObject;
    }
}
