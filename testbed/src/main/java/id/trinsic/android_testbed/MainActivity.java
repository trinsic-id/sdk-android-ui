package id.trinsic.android_testbed;

import android.os.Bundle;

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
        trinsicUi = new TrinsicUI(this, (result) -> {
            if (result.getCanceled()) {
                // This happens if the user closed the Android Custom Tabs activity by hitting the "X" button or by hitting Back
                Toast.makeText(MainActivity.this, "User canceled", Toast.LENGTH_SHORT).show();
            } else if (!result.getSuccess()) {
                // This happens if the flow fails for any other reason
                Toast.makeText(MainActivity.this, "Failed", Toast.LENGTH_SHORT).show();
            } else {
                // This happens if the user's identity has been verified
                Toast.makeText(MainActivity.this, "ResultsAccessKey: " + result.getResultsAccessKey(), Toast.LENGTH_SHORT).show();
            }
        });

        binding.buttonLaunch.setOnClickListener((View v) -> {
            Log.d("OnClick", "Launching Trinsic");
            String launchUrl;
            try {
                launchUrl = createLaunchUrl();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Failed to create launch URL: " + e.getMessage(), Toast.LENGTH_LONG);
                throw new RuntimeException(e);
            }

            trinsicUi.LaunchSession(MainActivity.this, launchUrl);
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

    private String createLaunchUrl() throws Exception {
        // Horrible hack to allow us to do networking on the UI thread since this is a simple sample
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