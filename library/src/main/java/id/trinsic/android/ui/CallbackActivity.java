package id.trinsic.android.ui;

import android.app.ActivityManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;

import java.util.List;

/**
 * Activity that handles the redirect from the Acceptance Session.
 * 
 * The Custom Tab will launch this activity when the redirect to the user-provided redirect scheme occurs, 
 * assuming the customer has properly added this activity to their android manifest and configured the scheme/redirect URL appropriately.
 * 
 * This activity then invokes the `InvokeActivity`, which -- when done with the right intent flags -- will close out both the custom tab activity and this activity.
 */
public class CallbackActivity extends ComponentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data == null) {
            return;
        }

        String sessionId = data.getQueryParameter("sessionId");
        String resultsAccessKey = data.getQueryParameter("resultsAccessKey");
        boolean success = data.getBooleanQueryParameter("success", false);

        Intent callbackIntent = new Intent(this, InvokeActivity.class);
        callbackIntent.setAction(InvokeActivity.ACTION_CALLBACK);

        // Use SINGLE_TOP to try to latch onto the InvokeActivity if it's the top of an existing task (assuming we're in a different task),
        // and CLEAR_TOP to try to latch onto the InvokeActivity if it's somewhere under this current activity (assuming we're in the same task).
        callbackIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        callbackIntent.putExtra("sessionId", sessionId);
        callbackIntent.putExtra("resultsAccessKey", resultsAccessKey);
        callbackIntent.putExtra("success", success);
        callbackIntent.putExtra("canceled", false);
        startActivity(callbackIntent);
        finish(); // Shouldn't be necessary but just in case
    }
}
