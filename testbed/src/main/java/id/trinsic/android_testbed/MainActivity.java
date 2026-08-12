package id.trinsic.android_testbed;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import id.trinsic.android.ui.TrinsicPendingIntentHelper;
import id.trinsic.android.ui.alpha.TrinsicMdl;
import id.trinsic.android.ui.TrinsicUI;

import androidx.appcompat.app.AppCompatActivity;

import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import id.trinsic.android.ui.models.AcceptanceSessionResult;
import id.trinsic.android_testbed.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    /**
     * Session-creation endpoint for the demo, hosted on your own backend.
     *
     * Replace with a URL that, when called with a GET request, will return a session launch URL as the only text content of the response.
     * It will likely do so by using the Trinsic backend API SDK to create a session and return the launch URL.
     *
     * Specific to the Session sample.
     */
    private static String BACKEND_CREATE_SESSION_ENDPOINT = "{REPLACE_ME}";

    /**
     * A request object as retrieved from Trinsic's CreateMdlExchange API.
     *
     * Specific to the mDL sample.
     */
    private static String MDL_REQUEST_OBJECT_BASE64URL = "";

    /**
     * The TrinsicUI instance, which will be used to launch a Hosted or Widget session.
     *
     * Not used for Direct Sessions, or for mDL Exchanges.
     */
    private TrinsicUI trinsicUi;

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up Trinsic UI sample
        trinsicUi = new TrinsicUI(this, (result) -> showAcceptanceSessionResult(MainActivity.this, result));

        binding.buttonLaunch.setOnClickListener((View v) -> {
            Log.d("OnClick", "Launching Trinsic");
            String launchUrl;
            try {
                launchUrl = createLaunchUrl();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Failed to create launch URL: " + e.getMessage(), Toast.LENGTH_LONG).show();
                throw new RuntimeException(e);
            }

            trinsicUi.LaunchSession(MainActivity.this, launchUrl);
        });

        binding.buttonLaunchPendingIntent.setOnClickListener((View v) -> {
            Log.d("OnClick", "Launching Trinsic with PendingIntent");
            String launchUrl;
            try {
                launchUrl = createLaunchUrl();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Failed to create launch URL: " + e.getMessage(), Toast.LENGTH_LONG).show();
                throw new RuntimeException(e);
            }

            Intent callbackIntent = new Intent(MainActivity.this, AcceptanceSessionResultReceiver.class);
            PendingIntent resultPendingIntent = PendingIntent.getBroadcast(
                    MainActivity.this,
                    0,
                    callbackIntent,
                    TrinsicPendingIntentHelper.GetCallbackPendingIntentFlags()
            );

            TrinsicUI.LaunchSessionWithPendingIntent(
                    MainActivity.this,
                    launchUrl,
                    resultPendingIntent
            );
        });

        // Set up Trinsic mDL Sample
        if(MDL_REQUEST_OBJECT_BASE64URL.isEmpty()) {
            binding.buttonLaunchMdl.setVisibility(View.INVISIBLE);
        } else {
            binding.buttonLaunchMdl.setOnClickListener((View v) -> {
                // When the "Launch mDL Exchange" button is pressed, use the Trinsic mDL SDK to
                // perform an exchange using a requestObject received from Trinsic's API (or hardcoded, in this case).
                TrinsicMdl.performMdlExchange(MainActivity.this, MDL_REQUEST_OBJECT_BASE64URL, (result) -> {
                    if(result.getSuccess()) {
                        String token = result.getToken(); // Send this token to your backend to send it to Trinsic.
                        Toast.makeText(MainActivity.this, "Got mDL Callback for Exchange " + result.getExchangeId() + ": " + token, Toast.LENGTH_LONG).show();
                    } else {
                        String exceptionMessage = result.getException().getMessage();
                        Toast.makeText(MainActivity.this, "Got error: " + exceptionMessage, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }
    }

    /**
     * Receives results from sessions launched through the PendingIntent API.
     */
    public static class AcceptanceSessionResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            AcceptanceSessionResult result = TrinsicPendingIntentHelper.GetAcceptanceSessionResult(intent);
            if (result == null) {
                Toast.makeText(context, "Missing AcceptanceSessionResult", Toast.LENGTH_SHORT).show();
                return;
            }

            showAcceptanceSessionResult(context, result);
        }
    }

    private static void showAcceptanceSessionResult(Context context, AcceptanceSessionResult result) {
        if (result.getCanceled()) {
            // This happens if the user closed the Android Custom Tabs activity by hitting the "X" button or by hitting Back
            Toast.makeText(context, "User canceled", Toast.LENGTH_SHORT).show();
        } else {
            // This happens if the Session completed successfully or unsuccessfully
            Toast.makeText(context, "Session completed: " + result.getSessionId(), Toast.LENGTH_SHORT).show();
        }
    }

    private String createLaunchUrl() throws Exception {
        // Hack to allow us to do networking on the UI thread since this is a simple sample
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        StringBuilder result = new StringBuilder();
        HttpURLConnection conn = (HttpURLConnection) new URL(BACKEND_CREATE_SESSION_ENDPOINT).openConnection();
        conn.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }
}
