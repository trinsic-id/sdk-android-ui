package id.trinsic.android.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import id.trinsic.android.ui.models.AcceptanceSessionResult;

public class InvokeActivity extends ComponentActivity {
    private static final String TAG = "InvokeActivity"; // Log tag for Activity

    /**
     * The key where the Callback PendingIntent in the invocation Intent for this InvokeActivity should be stored.
     */
    static final String INVOCATION_CALLBACK_PENDING_INTENT = "TRINSIC_INVOCATION_CALLBACK_PENDING_INTENT";

    /**
     * Internal state string for the invoked Session ID
     */
    private static final String STATE_SESSION_ID = "TRINSIC_SESSION_ID";

    /**
     * Internal state string for the callback PendingIntent, if launched in PendingIntent mode.
     */
    private static final String STATE_CALLBACK_PENDING_INTENT = "TRINSIC_CALLBACK_PENDING_INTENT";

    /**
     * The "invoke" action signals that this activity should launch a Custom Tab to launch the session
     * when the activity is launched
     */
    public static String ACTION_INVOKE = "invoke";

    /**
     * The "callback" action signals that this activity is being re-invoked by `CallbackActivity`
     * with the session results
     */
    public static String ACTION_CALLBACK = "callback";

    /**
     * Activity launcher / result handler for the Custom Tab activity
     */
    private ActivityResultLauncher<Uri> customTabLauncher;

    /**
     * Session canceled callback handler -- used to call `sessionCanceledCallbackRunnable` after
     * a delay.
     * See comments in `onCreate()` for context
     */
    private final Handler sessionCanceledCallbackHandler = new Handler();

    /**
     * Session canceled callback -- always points to `sessionCanceledCallback()`.
     * See comments in `onCreate()` for context
     */
    private Runnable sessionCanceledCallbackRunnable;

    /**
     * The Session ID with which this Activity was invoked
     */
    private String sessionId;

    /**
     * The PendingIntent through which results should be delivered, or NULL when this Activity was
     * launched through the Activity Result API.
     */
    private PendingIntent resultPendingIntent;

    /**
     * This is called when the activity is first created, which is (almost always) when the session is being launched.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register for Custom Tabs activity result -- to catch the user closing the tab themselves (canceling) instead of finalizing the session
        customTabLauncher = registerForActivityResult(new CustomTabContract(),
        new ActivityResultCallback<Uri>() {
            @Override
            public void onActivityResult(Uri o) {
                // HACK: On a *successful* completion, the Custom Tab activity will be finalized, triggering this method (which is also triggered when the user manually cancels out of the Custom Tab).
                // Immediately after, `onNewIntent` will be triggered with the results of the session from `CallbackActivity`.
                // On an *unsuccessful* completion, this method will be triggered indistinguishably from a successful completion, except `onNewIntent` will *not* fire afterwards.
                // Therefore, to determine if this callback represents a cancellation or a success, we set a timeout for 20ms (which triggers the cancelation flow), and cancel the timeout if `onNewIntent` is called. 
                if (sessionCanceledCallbackRunnable == null) {
                    sessionCanceledCallbackRunnable = InvokeActivity.this::sessionCanceledCallback;
                    sessionCanceledCallbackHandler.postDelayed(sessionCanceledCallbackRunnable, 20);
                }
            }
        });

        /**
         * It is possible that Android killed this Activity while the user was performing their
         * verification. In such an event, when the user finishes and returns to this Activity,
         * Android calls `onCreate()` once more with a non-NULL `savedInstanceState` to un-kill the Activity.
         *
         * We only want to launch the Chrome Custom Tab once -- when this Activity is initialized for the FIRST time --
         * which this check ensures.
         */
        if (savedInstanceState == null) {
            handleInitializingIntent(getIntent());
        } else {
            sessionId = savedInstanceState.getString(STATE_SESSION_ID, sessionId);
            if (savedInstanceState.containsKey(STATE_CALLBACK_PENDING_INTENT)) {
                resultPendingIntent = getPendingIntent(savedInstanceState, STATE_CALLBACK_PENDING_INTENT);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_SESSION_ID, sessionId);
        outState.putParcelable(STATE_CALLBACK_PENDING_INTENT, resultPendingIntent);
        super.onSaveInstanceState(outState);
    }

    /**
     * This is called when the activity is already alive, but is being re-invoked with a new intent.
     * 
     * Specifically, this occurs when the `CallbackActivity` invokes this activity with the results of the session.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Preserve a replacement invocation Intent so its session state can be restored later.
        if (ACTION_INVOKE.equals(intent.getAction())) {
            setIntent(intent);
        }

        handleInitializingIntent(intent);
    }

    /**
     * Handle the intent that was used to launch this activity -- either a launch intent or a callback intent.
     */
    private void handleInitializingIntent(Intent intent) {
        if(intent.getAction() == null) {
            finishAndRemoveTask();
        }
        else if(ACTION_INVOKE.equals(intent.getAction())) {
            handleInvokeIntent(intent);
        } else if(ACTION_CALLBACK.equals(intent.getAction())) {
            handleCallbackIntent(intent);
        }
    }

    /**
     * Handle a launch/invocation intent -- launch the Custom Tab with the provided URL.
     */
    private void handleInvokeIntent(Intent intent) {
        sessionId = intent.getStringExtra("sessionId");
        resultPendingIntent = getPendingIntent(intent, INVOCATION_CALLBACK_PENDING_INTENT);

        String launchUrl = intent.getStringExtra("launchUrl");

        Uri parsedUrl = Uri.parse(launchUrl);
        if(!parsedUrl.getQueryParameterNames().contains("launchMode")) {
            launchUrl += "&launchMode=mobile";
        }

        Uri uri = Uri.parse(launchUrl);
        customTabLauncher.launch(uri);
    }

    /**
     * Handle a callback intent -- return the results of the session to the activity which invoked this one.
     *
     * This specifically handles the callback *from `CallbackActivity`* and then calls back to the original caller of TrinsicUI
     */
    private void handleCallbackIntent(Intent intent) {
        if(!intent.hasExtra("sessionId")) {
            return;
        }

        String sessionId = intent.getStringExtra("sessionId");
        String resultsAccessKey = intent.getStringExtra("resultsAccessKey");
        boolean success = intent.getBooleanExtra("success", false);

        handleResult(sessionId, resultsAccessKey, success, false);
    }

    /**
     * Handle results of the session
     */
    @SuppressWarnings("deprecation")
    private void handleResult(String sessionId, String resultsAccessKey, boolean success, boolean canceled) {
        // Clear cancellation callback if it still exists (see comments in `onCreate()` for context)
        if (sessionCanceledCallbackRunnable != null) {
            sessionCanceledCallbackHandler.removeCallbacks(sessionCanceledCallbackRunnable);
        }

        // Construct results and put them in an Intent
        AcceptanceSessionResult result = new AcceptanceSessionResult(
                sessionId,
                resultsAccessKey,
                success,
                canceled
        );

        Intent intent = new Intent();
        intent.putExtra(TrinsicPendingIntentHelper.EXTRA_ACCEPTANCE_SESSION_RESULT, result);

        // Deliver results depending on how we were launched
        // If launched via the Activity Results method, we just setResult and finish.
        // If launched via the PendingIntent method, we call the PendingIntent.
        int resultCode = canceled ? RESULT_CANCELED : RESULT_OK;
        if (resultPendingIntent == null) {
            setResult(resultCode, intent);
        } else {
            try {
                resultPendingIntent.send(this, resultCode, intent);
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "Unable to deliver Acceptance Session result: PendingIntent was canceled", e);
            }
        }

        finishAndRemoveTask();
    }

    @SuppressWarnings("deprecation")
    private static PendingIntent getPendingIntent(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(key, PendingIntent.class);
        }

        return intent.getParcelableExtra(key);
    }

    @SuppressWarnings("deprecation")
    private static PendingIntent getPendingIntent(Bundle bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return bundle.getParcelable(key, PendingIntent.class);
        }

        return bundle.getParcelable(key);
    }

    /**
     * Called by `sessionCanceledCallbackHandler` after a timeout (which is started when the Custom Tab activity finishes) to detect session cancelation.
     * 
     * See comments in `onCreate()` for context.
     */
    private void sessionCanceledCallback() {
        handleResult(sessionId, null, false, true);
    }
}
