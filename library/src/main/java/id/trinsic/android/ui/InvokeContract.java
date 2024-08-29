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
 * 
 * This class is used to handle the invocation and result-processing of `InvokeActivity`, which launches the actual Custom Tab activity.
 */
public class InvokeContract extends ActivityResultContract<AcceptanceSessionLaunchParams, AcceptanceSessionResult>
{
    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, AcceptanceSessionLaunchParams input) {
        Intent intent = new Intent(context, InvokeActivity.class);

        // TODO: Is SINGLE_TOP necessary for this intent? I think it's only necessary for the callback intent. -JCC 8/28/24
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.setAction(InvokeActivity.ACTION_INVOKE);
        intent.putExtra("sessionId", input.getSessionId());
        intent.putExtra("launchUrl", input.getLaunchUrl());
        intent.putExtra("redirectUrl", input.getRedirectUrl());
        return intent;
    }

    @Override
    public AcceptanceSessionResult parseResult(int resultCode, @Nullable Intent intent) {
        if (intent != null) {
            return new AcceptanceSessionResult(
                    intent.getStringExtra("sessionId"),
                    intent.getStringExtra("resultsAccessKey"),
                    intent.getBooleanExtra("success", false),
                    intent.getBooleanExtra("canceled", false)
            );
        }

        return new AcceptanceSessionResult(null, null, false, false);
    }
}
