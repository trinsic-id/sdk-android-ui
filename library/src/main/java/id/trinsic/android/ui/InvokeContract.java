package id.trinsic.android.ui;

import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import id.trinsic.android.ui.models.AcceptanceSessionLaunchParams;
import id.trinsic.android.ui.models.AcceptanceSessionResult;

/**
 * Internal class for Trinsic SDK usage.
 * <p>
 * This class is used to handle the invocation and result-processing of `InvokeActivity`, which launches the actual Custom Tab activity.
 */
public class InvokeContract extends ActivityResultContract<AcceptanceSessionLaunchParams, AcceptanceSessionResult> {
    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, AcceptanceSessionLaunchParams input) {
        Intent intent = new Intent(context, InvokeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.setAction(InvokeActivity.ACTION_INVOKE);
        intent.putExtra("sessionId", input.getSessionId());
        intent.putExtra("launchUrl", input.getLaunchUrl());
        return intent;
    }

    @Override
    public AcceptanceSessionResult parseResult(int resultCode, @Nullable Intent intent) {
        if (intent == null) {
            return new AcceptanceSessionResult(null, false);
        }

        return TrinsicPendingIntentHelper.GetAcceptanceSessionResult(intent);
    }
}
