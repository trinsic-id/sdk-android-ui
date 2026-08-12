package id.trinsic.android.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Consumer;

import id.trinsic.android.ui.models.AcceptanceSessionLaunchParams;
import id.trinsic.android.ui.models.AcceptanceSessionResult;

/**
 * Client to launch Trinsic in an Android application.
 * <br/><br/>
 * Use the constructor of this class, paired with `LaunchSession()`, to use the (recommended) Activity Results API.
 * <br/>
 * Use the static `LaunchSessionWithPendingIntent()` method if you cannot use the Activity Results API.
 * <br/><br/>
 * See Trinsic's documentation for setup instructions, including <b>required</b> changes to your app's AndroidManifest.xml file.
 */
public class TrinsicUI {
    private final ActivityResultLauncher<AcceptanceSessionLaunchParams> invokeLauncher;

    /**
     * Instantiate a TrinsicUI and register the event callback for session execution results.
     * <br/><br/>
     * Must be called in an Activity's constructor or a Fragment's initialization logic (eg `onInitialize()`).
     * <br/><br/>
     * If you cannot unconditionally instantiate this object during an Activity or Fragment initialization,
     * use the static `TrinsicUI.LaunchSessionWithPendingIntent()` method as an alternative.
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
     * @param context   The context from which to launch the session
     * @param launchUrl The `launchUrl` returned in the Session creation backend API
     */
    public void LaunchSession(Context context, String launchUrl) {
        invokeLauncher.launch(createLaunchParams(launchUrl));
    }

    /**
     * Invoke an Acceptance Session without registering an Activity Result callback in advance.
     * <br/><br/>
     * When the session finishes, the supplied PendingIntent is sent with an `AcceptanceSessionResult` contained within it.
     * <br/><br/>
     * Use `TrinsicPendingIntentHelper` to assist with creating the PendingIntent and processing the result Intent contents.
     *
     * @param activity              The Activity Context from which to launch the session
     * @param launchUrl             The `launchUrl` returned by the Session creation backend API
     * @param callbackPendingIntent A mutable PendingIntent to receive the session result callback
     * @throws IllegalArgumentException If an immutable PendingIntent is supplied
     */
    public static void LaunchSessionWithPendingIntent(
            @NonNull Activity activity,
            @NonNull String launchUrl,
            @NonNull PendingIntent callbackPendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && callbackPendingIntent.isImmutable()) {
            throw new IllegalArgumentException("callbackPendingIntent must be mutable");
        }

        Intent intent = new InvokeContract().createIntent(activity, createLaunchParams(launchUrl));
        intent.putExtra(InvokeActivity.INVOCATION_CALLBACK_PENDING_INTENT, callbackPendingIntent);

        activity.startActivity(intent);
    }

    private static AcceptanceSessionLaunchParams createLaunchParams(String launchUrl) {
        Uri parsedUrl = Uri.parse(launchUrl);
        String sessionId = parsedUrl.getQueryParameter("sessionId");

        return new AcceptanceSessionLaunchParams(sessionId, launchUrl);
    }
}
