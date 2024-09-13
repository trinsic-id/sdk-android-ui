package id.trinsic.android.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.List;

/**
 * Internal utility class to enable Flutter/React Native functionality.
 */
public class PlatformUtil {
    public static void ValidateRedirectUrl(Context context, String redirectUrl) throws IllegalArgumentException {
        Uri parsedUri = Uri.parse(redirectUrl);
        if (parsedUri == null || parsedUri.getScheme() == null) {
            throw new IllegalArgumentException("Invalid redirectUrl -- malformed URL");
        }

        if (parsedUri.getScheme().equalsIgnoreCase("https")
                || parsedUri.getScheme().equalsIgnoreCase("http")) {
            throw new IllegalArgumentException("Invalid redirectUrl -- HTTP and HTTPS schemes cannot be used");
        }

        PackageManager packageManager = context.getPackageManager();
        Intent testIntent = new Intent(Intent.ACTION_VIEW, parsedUri);

        List<ResolveInfo> activities = packageManager.queryIntentActivities(testIntent, 0);

        // If no activity is registered, they likely forgot to register it
        if (activities.isEmpty()) {
            throw new IllegalArgumentException("Scheme " + parsedUri.getScheme()
                    + " is not registered to launch any activity. Did you forget to update your " +
                    "AndroidManifest.xml in accordance with Trinsic's documentation?");
        }

        // If multiple activities are registered, the developer is likely reusing the scheme
        if(activities.stream().count() > 1) {
            throw new IllegalArgumentException("Scheme " + parsedUri.getScheme()
                    + " is registered to launch multiple different activities on this device." +
                    " Are you using the same redirect scheme for multiple apps which use the Trinsic library?");
        }

        ResolveInfo activity = activities.get(0);
        ActivityInfo info = activity.activityInfo;
        String activityName = info.name;

        // Something is registered against this scheme, but it's not the library's provided activity
        if(!activityName.equals("id.trinsic.android.ui.CallbackActivity")) {
            throw new IllegalArgumentException("Scheme " + parsedUri.getScheme() + " is registered to launch activity '"
                    + activityName + "', which is not the expected 'id.trinsic.android.ui.CallbackActivity'. " +
                    "Did you register the scheme against an activity other than what the Trinsic documentation contained?");
        }
    }
}
