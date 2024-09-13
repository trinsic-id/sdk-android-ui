package id.trinsic.android.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;

import java.util.List;
import java.util.function.Consumer;

import id.trinsic.android.ui.models.AcceptanceSessionLaunchParams;
import id.trinsic.android.ui.models.AcceptanceSessionResult;

/**
 * Client to launch Trinsic in an Android application.
 * <br/><br/>
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
     *
     * @param launchUrl   The `launchUrl` returned in the Session creation backend API
     * @param redirectUrl A URL with the scheme registered in your application's manifest, in accordance with Trinsic's documentation
     */
    public void LaunchSession(Context context, String launchUrl, String redirectUrl) {
        ValidateRedirectUrl(context, redirectUrl);

        Uri parsedUrl = Uri.parse(launchUrl);
        String sessionId = parsedUrl.getQueryParameter("sessionId");

        invokeLauncher.launch(new AcceptanceSessionLaunchParams(sessionId, launchUrl, redirectUrl));
    }

    private void ValidateRedirectUrl(Context context, String redirectUrl) throws IllegalArgumentException {
        Uri parsedUri = Uri.parse(redirectUrl);
        if (parsedUri == null || parsedUri.getScheme() == null) {
            throw new IllegalArgumentException("Invalid redirectUrl -- malformed URL");
        }

        if (parsedUri.getScheme().equalsIgnoreCase("https")
                || parsedUri.getScheme().equalsIgnoreCase("http")) {
            throw new IllegalArgumentException("Invalid redirectUrl -- HTTP and HTTPS schemes cannot be used");
        }

        PackageManager packageManager = context.getPackageManager();
        Intent testIntent = new Intent(Intent.ACTION_VIEW, parsedUri);

        List<ResolveInfo> activities = packageManager.queryIntentActivities(testIntent, 0);

        // If no activity is registered, they likely forgot to register it
        if (activities.isEmpty()) {
            throw new IllegalArgumentException("Scheme " + parsedUri.getScheme()
                    + " is not registered to launch any activity. Did you forget to update your " +
                    "AndroidManifest.xml in accordance with Trinsic's documentation?");
        }

        // If multiple activities are registered, the developer is likely reusing the scheme
        if(activities.stream().count() > 1) {
            throw new IllegalArgumentException("Scheme " + parsedUri.getScheme()
                    + " is registered to launch multiple different activities on this device." +
                    " Are you using the same redirect scheme for multiple apps which use the Trinsic library?");
        }

        ResolveInfo activity = activities.get(0);
        ActivityInfo info = activity.activityInfo;
        String activityName = info.name;

        // Something is registered against this scheme, but it's not the library's provided activity
        if(!activityName.equals("id.trinsic.android.ui.CallbackActivity")) {
            throw new IllegalArgumentException("Scheme " + parsedUri.getScheme() + " is registered to launch activity '"
                    + activityName + "', which is not the expected 'id.trinsic.android.ui.CallbackActivity'. " +
                    "Did you register the scheme against an activity other than what the Trinsic documentation contained?");
        }
    }
}
