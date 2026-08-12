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
 *
 */
public class TrinsicPendingIntentHelper {
    /**
     * Intent extra containing the `AcceptanceSessionResult` delivered through a callback Intent.
     */
    public static final String EXTRA_ACCEPTANCE_SESSION_RESULT = "TRINSIC_ACCEPTANCE_SESSION_RESULT";

    /**
     * Return the base flags for a PendingIntent supplied to `TrinsicUI.LaunchSessionWithPendingIntent()`
     * appropriate for the current Android SDK.
     */
    public static int GetCallbackPendingIntentFlags() {
        int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        return flags;
    }

    /**
     * Extract an Acceptance Session result from a fulfilled PendingIntent.
     * <br/><br/>
     * Relevant when using `TrinsicUI.LaunchSessionWithPendingIntent()` only.
     *
     * @param intent The Intent delivered to the callback PendingIntent provided to `TrinsicUI.LaunchSessionWithPendingIntent()`
     * @return The contained AcceptanceSessionResult
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static AcceptanceSessionResult GetAcceptanceSessionResult(@NonNull Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(
                    EXTRA_ACCEPTANCE_SESSION_RESULT,
                    AcceptanceSessionResult.class
            );
        }

        return intent.getParcelableExtra(EXTRA_ACCEPTANCE_SESSION_RESULT);
    }
}
