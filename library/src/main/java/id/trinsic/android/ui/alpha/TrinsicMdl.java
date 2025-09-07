package id.trinsic.android.ui.alpha;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.credentials.CredentialManager;
import androidx.credentials.DigitalCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.GetDigitalCredentialOption;

import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

import kotlin.Result;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

/**
 * Class which performs mDL Exchanges. Call this after creating an exchange via Trinsic's API.
 */
public class TrinsicMdl {
    /**
     * Perform an mDL Exchange
     * @param context The context within which to perform the exchange -- an Activity or Fragment
     * @param requestObjectBase64Url The request object string exactly as received from Trinsic's API
     * @param callback The callback which will be called when the exchange succeeds or fails
     */
    @androidx.annotation.OptIn(markerClass = androidx.credentials.ExperimentalDigitalCredentialApi.class)
    public static void performMdlExchange(@NonNull Context context, @NonNull String requestObjectBase64Url, @NonNull Consumer<MdlExchangeResult> callback) {
        // Create CredentialManager
        CredentialManager credentialManager = CredentialManager.create(context);

        // Parse outer request object
        MdlExchangeOuterRequest parsedRequest = MdlExchangeOuterRequest.Parse(requestObjectBase64Url);

        // Perform exchange
        GetDigitalCredentialOption getDigitalCredentialOption = new GetDigitalCredentialOption(parsedRequest.getRequestObject());
        GetCredentialRequest request = new GetCredentialRequest(List.of(getDigitalCredentialOption));

        credentialManager.getCredential(context, request, new Continuation<GetCredentialResponse>() {
            @NonNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NonNull Object responseObject) {
                // Handle expected error
                if (responseObject instanceof Result.Failure) {
                    MdlExchangeResult result = new MdlExchangeResult(parsedRequest.getExchangeId(), ((Result.Failure)responseObject).exception);
                    callback.accept(result);
                    return;
                }

                // Handle invalid response type
                if (!(responseObject instanceof GetCredentialResponse)) {
                    MdlExchangeResult result = new MdlExchangeResult(parsedRequest.getExchangeId(), new Exception("Invalid response type " + responseObject.getClass().getTypeName()));
                    callback.accept(result);
                    return;
                }

                // Transform response into something we understand
                GetCredentialResponse credentialResponse = (GetCredentialResponse) responseObject;
                if (!(credentialResponse.getCredential() instanceof DigitalCredential)) {
                    throw new RuntimeException("?!");
                }
                DigitalCredential digitalCredential = (DigitalCredential) credentialResponse.getCredential();

                // Turn the credential JSON into a token for the Trinsic API
                String credentialJson = digitalCredential.getCredentialJson();
                String credentialToken = Base64.getUrlEncoder().encodeToString(credentialJson.getBytes());

                // Return
                MdlExchangeResult result = new MdlExchangeResult(parsedRequest.getExchangeId(), credentialToken);
                callback.accept(result);
            }
        });
    }
}
