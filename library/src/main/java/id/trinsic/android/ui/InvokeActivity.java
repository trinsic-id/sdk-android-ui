package id.trinsic.android.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;

public class InvokeActivity extends ComponentActivity {
    public static String ACTION_INVOKE = "invoke";
    public static String ACTION_CALLBACK = "callback";

    private ActivityResultLauncher<Uri> customTabLauncher;
    private String launchUrl;
    private String sessionId;
    private String redirectScheme;
    private final Handler sessionCanceledCallbackHandler = new Handler();
    private Runnable sessionCanceledCallbackRunnable;

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
                if (r == null) {
                    sessionCanceledCallbackRunnable = InvokeActivity.this::sessionCanceledCallback;
                    sessionCanceledCallbackHandler.postDelayed(sessionCanceledCallbackRunnable, 20);
                }
            }
        });

        handleInitializingIntent(getIntent());
    }

    /**
     * This is called when the activity is already alive, but is being re-invoked with a new intent.
     * 
     * Specifically, this occurs when the `CallbackActivity` invokes this activity with the results of the session.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleInitializingIntent(intent);
    }

    /**
     * Handle the intent that was used to launch this activity -- either a launch intent or a callback intent.
     */
    private void handleInitializingIntent(Intent intent) {
        if(intent.getAction() == null) {
            finishAndRemoveTask();
        }
        else if(intent.getAction().equals(ACTION_INVOKE)) {
            handleInvokeIntent(intent);
        } else if(intent.getAction().equals(ACTION_CALLBACK)) {
            handleCallbackIntent(intent);
        }
    }

    /**
     * Handle a launch/invocation intent -- launch the Custom Tab with the provided URL.
     */
    private void handleInvokeIntent(Intent intent) {
        launchUrl = intent.getStringExtra("launchUrl");
        sessionId = intent.getStringExtra("sessionId");
        redirectScheme = intent.getStringExtra("redirectScheme");

        Uri uri = Uri.parse(launchUrl + (launchUrl.contains("?") ? "&" : "?") + "scheme=" + redirectScheme);
        customTabLauncher.launch(uri);
    }

    /*
     * Handle a callback intent -- return the results of the session to the activity which invoked this one.
     */
    private void handleCallbackIntent(Intent intent) {
        if(!intent.hasExtra("sessionId") || !intent.hasExtra("success")) {
            return;
        }

        String sessionId = intent.getStringExtra("sessionId");
        String resultsAccessKey = intent.getStringExtra("resultsAccessKey");
        boolean success = intent.getBooleanExtra("success", false);
        boolean canceled = intent.getBooleanExtra("canceled", false);

        handleResult(sessionId, resultsAccessKey, success, canceled);
    }

    /**
     * Handle results of the session (either a )
     */
    private void handleResult(String sessionId, String resultsAccessKey, boolean success, boolean canceled) {
        // Clear cancelation callback if it still exists (see comments in `onCreate()` for context)
        if (sessionCanceledCallbackRunnable != null) {
            sessionCanceledCallbackHandler.removeCallbacks(r);
        }

        int resultCode = canceled ? RESULT_CANCELED : (success ? RESULT_OK : 2); // TODO: magic error number

        Intent intent = new Intent();
        intent.putExtra("sessionId", sessionId);
        intent.putExtra("resultsAccessKey", resultsAccessKey);
        intent.putExtra("success", success);
        intent.putExtra("canceled", canceled);

        setResult(resultCode, intent);
        finishAndRemoveTask();
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
