package id.trinsic.android.ui;

import android.net.Uri;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;

import java.util.function.Consumer;

import id.trinsic.android.ui.models.AcceptanceSessionLaunchParams;
import id.trinsic.android.ui.models.AcceptanceSessionResult;

/**
 * Client to launch Trinsic in an Android application.
 *<br/><br/>
 * Must be used in the context of an Activity or a Fragment.
 * <br/><br/>
 * See Trinsic's documentation for setup instructions, including <b>required</b> changes to your app's AndroidManifest.xml file.
 */
public class TrinsicUI {
    private final ActivityResultLauncher<AcceptanceSessionLaunchParams> invokeLauncher;

    /**
     * Instantiate a TrinsicUI and register the event callback for session execution results.
     * <br/><br/>
     * Must be called in an Activity's constructor or a Fragment's initialization logic (eg `onCreateView`).
     *
     * @param activity The Activity or Fragment this TrinsicUI instance belongs to
     * @param callback A callback to capture the results of session invocation
     */
    public TrinsicUI(@NonNull ActivityResultCaller activity, @NonNull Consumer<AcceptanceSessionResult> callback) {
        invokeLauncher = activity.registerForActivityResult(new InvokeContract(), callback::accept);
    }

    /**
     * Invoke an Acceptance Session, launching an Android Custom Tabs view and capturing the result.
     * <br/><br/>
     * The result of session invocation is delivered via the callback registered in the `TrinsicUI` constructor.
     * @param launchUrl      The `launchUrl` returned in the Session creation backend API
     * @param redirectUrl    A URL with the scheme registered in your application's manifest, in accordance with Trinsic's documentation
     */
    public void Invoke(String launchUrl, String redirectUrl) {
        Uri parsedUrl = Uri.parse(launchUrl);
        String sessionId = parsedUrl.getQueryParameter("sessionId");

        invokeLauncher.launch(new AcceptanceSessionLaunchParams(sessionId, launchUrl, redirectUrl));
    }
}
