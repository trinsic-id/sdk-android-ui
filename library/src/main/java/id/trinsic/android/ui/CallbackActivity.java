package id.trinsic.android.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.ComponentActivity;

/**
 * Activity that handles the redirect from the Acceptance Session.
 * 
 * The Custom Tab will launch this activity for a registered custom scheme or verified HTTPS App Link.
 * 
 * This activity then invokes the `InvokeActivity`, which -- when done with the right intent flags -- will close out both the custom tab activity and this activity.
 */
public class CallbackActivity extends ComponentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri data = intent.getData();
        if (!Intent.ACTION_VIEW.equals(intent.getAction()) || data == null || !data.isHierarchical()) {
            finish();
            return;
        }

        Intent callbackIntent = new Intent(this, InvokeActivity.class);
        callbackIntent.setAction(InvokeActivity.ACTION_CALLBACK);
        // Preserve the complete address so the active invocation can validate it before parsing results.
        callbackIntent.setData(data);

        // Use SINGLE_TOP to try to latch onto the InvokeActivity if it's the top of an existing task (assuming we're in a different task),
        // and CLEAR_TOP to try to latch onto the InvokeActivity if it's somewhere under this current activity (assuming we're in the same task).
        callbackIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(callbackIntent);
        finish(); // Shouldn't be necessary but just in case
    }
}
