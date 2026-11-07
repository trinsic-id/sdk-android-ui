package id.trinsic.android.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsSession;

/**
 * This class is used to handle the invocation and result-processing of a Custom Tab.
 */
class CustomTabContract extends ActivityResultContract<CustomTabContract.Launch, Uri>
{
    static final class Launch {
        final Uri url;
        @Nullable final CustomTabsSession session;

        Launch(Uri url, @Nullable CustomTabsSession session) {
            this.url = url;
            this.session = session;
        }
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, Launch input) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(input.session);
        if (input.session != null) {
            // A session pins the browser package. Preserve redirects to provider apps rather
            // than letting that package be interpreted as a request to stay in the browser.
            builder.setSendToExternalDefaultHandlerEnabled(true);
        }
        CustomTabsIntent customTabsIntent = builder.build();
        Intent keepAliveIntent = new Intent(context, KeepAliveService.class);
        Intent intent = customTabsIntent.intent;
        intent.putExtra("android.support.customtabs.extra.KEEP_ALIVE", keepAliveIntent);
        intent.setData(input.url);
        return intent;
    }

    @Override
    public Uri parseResult(int resultCode, @Nullable Intent intent) {
        if (intent != null) {
            return intent.getData();
        }
        return null;
    }
}
