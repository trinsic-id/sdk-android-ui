package id.trinsic.android.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;

import androidx.activity.ComponentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import id.trinsic.android.ui.models.AcceptanceSessionLaunchParams;
import id.trinsic.android.ui.models.AcceptanceSessionResult;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CallbackFlowTest {
    private static final String CALLBACK = "https://pocketrides.app/session-complete";
    private static final String LAUNCH = "https://verify.trinsic.id/?sessionId=123";
    private static final String CLIENT_TOKEN_LAUNCH = "https://verify.trinsic.id/api/session/launch?clientToken=example-token";

    private Intent invocation() {
        return invocation(LAUNCH);
    }

    private Intent invocation(String launchUrl) {
        return new InvokeContract().createIntent(RuntimeEnvironment.getApplication(),
                new AcceptanceSessionLaunchParams(Uri.parse(launchUrl).getQueryParameter("sessionId"), launchUrl));
    }

    private Intent callback(String url) {
        return new Intent(InvokeActivity.ACTION_CALLBACK).setData(Uri.parse(url));
    }

    private PendingIntent registerResultReceiver(Activity host, ResultReceiver receiver) {
        ResultReceiver.count.set(0);
        ResultReceiver.result = null;
        host.registerReceiver(receiver, new IntentFilter("trinsic.TEST_RESULT"), Context.RECEIVER_NOT_EXPORTED);
        return PendingIntent.getBroadcast(host, 0, new Intent("trinsic.TEST_RESULT").setPackage(host.getPackageName()),
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Test public void httpsCallbackCompletesActivityResultWithToken() {
        try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, invocation()).setup()) {
            controller.newIntent(callback(CALLBACK + "?sessionId=123&redirectToken=token&resultsAccessKey=legacy"));
            ShadowActivity shadow = shadowOf(controller.get());
            assertEquals(Activity.RESULT_OK, shadow.getResultCode());
            AcceptanceSessionResult result = new InvokeContract().parseResult(shadow.getResultCode(), shadow.getResultIntent());
            assertEquals("123", result.getSessionId());
            assertEquals("token", result.getRedirectToken());
            assertEquals("legacy", result.getResultsAccessKey());
            assertTrue(result.getSuccess());
            assertFalse(result.getCanceled());
        }
    }

    @Test public void rejectsWrongMissingOrDuplicateSessionId() {
        try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, invocation()).setup()) {
            for (String url : new String[] {
                    CALLBACK + "?sessionId=wrong", CALLBACK + "?sessionId=123&sessionId=123",
                    CALLBACK + "?sessionId=", CALLBACK
            }) {
                controller.newIntent(callback(url));
                assertNull(url, shadowOf(controller.get()).getResultIntent());
                assertFalse(url, controller.get().isFinishing());
            }
        }
    }

    @Test public void customSchemeCallbackCompletesActivityResult() {
        try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, invocation()).setup()) {
            controller.newIntent(callback("pocketrides://session-complete?sessionId=123&redirectToken=token"));
            assertEquals(Activity.RESULT_OK, shadowOf(controller.get()).getResultCode());
        }
    }

    @Test public void callbackActivityForwardsOriginalUrl() {
        String url = CALLBACK + "?sessionId=123&redirectToken=a%2Bb";
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try (ActivityController<CallbackActivity> controller = Robolectric.buildActivity(CallbackActivity.class, view).setup()) {
            Intent forwarded = shadowOf(controller.get()).getNextStartedActivity();
            assertEquals(InvokeActivity.class.getName(), forwarded.getComponent().getClassName());
            assertEquals(InvokeActivity.ACTION_CALLBACK, forwarded.getAction());
            assertEquals(url, forwarded.getDataString());
            assertTrue(controller.get().isFinishing());
        }
    }

    @Test public void callbackActivityClosesInvalidIntents() {
        for (Intent intent : new Intent[] {new Intent(), new Intent(Intent.ACTION_VIEW), new Intent(Intent.ACTION_VIEW, Uri.parse("custom:opaque"))}) {
            try (ActivityController<CallbackActivity> controller = Robolectric.buildActivity(CallbackActivity.class, intent).setup()) {
                assertNull(shadowOf(controller.get()).getNextStartedActivity());
                assertTrue(controller.get().isFinishing());
            }
        }
    }

    @Test public void coldCallbackDoesNotManufactureResult() {
        try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, callback(CALLBACK + "?sessionId=123")).setup()) {
            assertNull(shadowOf(controller.get()).getResultIntent());
            assertTrue(controller.get().isFinishing());
        }
    }

    @Test public void sessionMatchingSurvivesActivityRecreation() {
        Intent launch = invocation();
        Bundle saved = new Bundle();
        ActivityController<InvokeActivity> original = Robolectric.buildActivity(InvokeActivity.class, launch).setup();
        shadowOf(original.get()).getNextStartedActivity();
        original.saveInstanceState(saved).pause().stop().destroy();
        try (ActivityController<InvokeActivity> restored = Robolectric.buildActivity(InvokeActivity.class, launch).create(saved).start().resume()) {
            assertNull(shadowOf(restored.get()).getNextStartedActivity()); // Never relaunch the browser.
            restored.newIntent(callback(CALLBACK + "?sessionId=wrong"));
            assertNull(shadowOf(restored.get()).getResultIntent());
            restored.newIntent(callback(CALLBACK + "?sessionId=123"));
            assertEquals(Activity.RESULT_OK, shadowOf(restored.get()).getResultCode());
        }
    }

    @Test public void restoredActivityCanReceiveCallbackAsInitialIntent() {
        for (String launchUrl : new String[] {LAUNCH, CLIENT_TOKEN_LAUNCH}) {
            Bundle saved = new Bundle();
            ActivityController<InvokeActivity> original = Robolectric.buildActivity(InvokeActivity.class, invocation(launchUrl)).setup();
            original.saveInstanceState(saved).pause().stop().destroy();
            try (ActivityController<InvokeActivity> restored = Robolectric.buildActivity(InvokeActivity.class,
                    callback(CALLBACK + "?sessionId=123")).create(saved).start().resume()) {
                ShadowActivity shadow = shadowOf(restored.get());
                assertEquals(Activity.RESULT_OK, shadow.getResultCode());
                assertEquals("123", TrinsicPendingIntentHelper.GetAcceptanceSessionResult(shadow.getResultIntent()).getSessionId());
            }
        }
    }

    @Test public void browserCloseReturnsCancellation() {
        try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, invocation()).setup()) {
            ShadowActivity shadow = shadowOf(controller.get());
            Intent browser = shadow.getNextStartedActivityForResult().intent;
            shadow.receiveResult(browser, Activity.RESULT_CANCELED, null);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30));
            AcceptanceSessionResult result = TrinsicPendingIntentHelper.GetAcceptanceSessionResult(shadow.getResultIntent());
            assertTrue(result.getCanceled());
            assertEquals("123", result.getSessionId());
        }
    }

    @Test public void pendingIntentApiDeliversHttpsResultOnce() {
        for (String launchUrl : new String[] {LAUNCH, CLIENT_TOKEN_LAUNCH}) {
            try (ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup()) {
                ResultReceiver receiver = new ResultReceiver();
                PendingIntent pending = registerResultReceiver(host.get(), receiver);
                try {
                    TrinsicUI.LaunchSessionWithPendingIntent(host.get(), launchUrl, pending);
                    Intent launch = shadowOf(host.get()).getNextStartedActivityForResult().intent;
                    try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, launch).setup()) {
                        Intent result = callback(CALLBACK + "?sessionId=123&redirectToken=token");
                        controller.newIntent(result);
                        controller.newIntent(result);
                        shadowOf(Looper.getMainLooper()).idle();
                        assertEquals(1, ResultReceiver.count.get());
                        assertEquals("123", ResultReceiver.result.getSessionId());
                        assertEquals("token", ResultReceiver.result.getRedirectToken());
                        assertFalse(ResultReceiver.result.getCanceled());
                    }
                } finally {
                    host.get().unregisterReceiver(receiver);
                }
            }
        }
    }

    @Test public void activityResultApiCompletesClientTokenLaunchAfterRecreation() {
        try (ActivityController<ResultActivity> host = Robolectric.buildActivity(ResultActivity.class).setup()) {
            host.get().trinsicUi.LaunchSession(host.get(), CLIENT_TOKEN_LAUNCH);
            ShadowActivity hostShadow = shadowOf(host.get());
            Intent launch = hostShadow.getNextStartedActivityForResult().intent;
            try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, launch).setup()) {
                Uri browserUrl = shadowOf(controller.get()).getNextStartedActivityForResult().intent.getData();
                assertEquals("example-token", browserUrl.getQueryParameter("clientToken"));
                assertEquals("mobile", browserUrl.getQueryParameter("launchMode"));
                controller.recreate();
                ShadowActivity shadow = shadowOf(controller.get());
                assertNull(shadow.getNextStartedActivityForResult());
                for (String url : new String[] {CALLBACK, CALLBACK + "?sessionId=", CALLBACK + "?sessionId=123&sessionId=123"}) {
                    controller.newIntent(callback(url));
                    assertNull(shadow.getResultIntent());
                    assertFalse(controller.get().isFinishing());
                }
                controller.newIntent(callback(CALLBACK + "?sessionId=123&redirectToken=token"));
                hostShadow.receiveResult(launch, shadow.getResultCode(), shadow.getResultIntent());
                assertEquals(1, host.get().resultCount);
                assertEquals("123", host.get().result.getSessionId());
                assertEquals("token", host.get().result.getRedirectToken());
                assertFalse(host.get().result.getCanceled());
            }
        }
    }

    @Test public void activityResultCancellationSurvivesRecreation() {
        for (String launchUrl : new String[] {LAUNCH, CLIENT_TOKEN_LAUNCH}) {
            try (ActivityController<ResultActivity> host = Robolectric.buildActivity(ResultActivity.class).setup()) {
                host.get().trinsicUi.LaunchSession(host.get(), launchUrl);
                ShadowActivity hostShadow = shadowOf(host.get());
                Intent launch = hostShadow.getNextStartedActivityForResult().intent;
                try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, launch).setup()) {
                    ShadowActivity shadow = shadowOf(controller.get());
                    Intent browser = shadow.getNextStartedActivityForResult().intent;
                    shadow.receiveResult(browser, Activity.RESULT_CANCELED, null);
                    controller.recreate();
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30));
                    ShadowActivity restored = shadowOf(controller.get());
                    assertNull(restored.getNextStartedActivityForResult());
                    assertTrue(controller.get().isFinishing());
                    hostShadow.receiveResult(launch, restored.getResultCode(), restored.getResultIntent());
                    assertEquals(1, host.get().resultCount);
                    assertTrue(host.get().result.getCanceled());
                    assertEquals(Uri.parse(launchUrl).getQueryParameter("sessionId"), host.get().result.getSessionId());
                }
            }
        }
    }

    @Test public void pendingIntentCancellationSurvivesRecreation() {
        checkPendingCancellationAfterRecreation(false);
    }

    @Test public void completionAfterRecreationClearsPendingCancellation() {
        checkPendingCancellationAfterRecreation(true);
    }

    private void checkPendingCancellationAfterRecreation(boolean completes) {
        for (String launchUrl : new String[] {LAUNCH, CLIENT_TOKEN_LAUNCH}) {
            try (ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup()) {
                ResultReceiver receiver = new ResultReceiver();
                PendingIntent pending = registerResultReceiver(host.get(), receiver);
                try {
                    TrinsicUI.LaunchSessionWithPendingIntent(host.get(), launchUrl, pending);
                    Intent launch = shadowOf(host.get()).getNextStartedActivityForResult().intent;
                    ActivityController<InvokeActivity> original = Robolectric.buildActivity(InvokeActivity.class, launch).setup();
                    ShadowActivity shadow = shadowOf(original.get());
                    Intent browser = shadow.getNextStartedActivityForResult().intent;
                    shadow.receiveResult(browser, Activity.RESULT_CANCELED, null);
                    assertEquals(0, ResultReceiver.count.get());
                    Bundle saved = new Bundle();
                    original.pause().stop().saveInstanceState(saved).destroy();
                    // Restore without rendering frames that advance Robolectric's clock past the cancellation delay.
                    try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, launch)
                            .create(saved).start().resume()) {
                        assertNull(shadowOf(controller.get()).getNextStartedActivityForResult());
                        assertEquals(0, ResultReceiver.count.get());
                        if (completes) {
                            controller.newIntent(callback(CALLBACK + "?sessionId=123&redirectToken=token"));
                        }
                        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30));
                        assertEquals(1, ResultReceiver.count.get());
                        assertEquals(!completes, ResultReceiver.result.getCanceled());
                        assertEquals(completes ? "123" : Uri.parse(launchUrl).getQueryParameter("sessionId"),
                                ResultReceiver.result.getSessionId());
                        assertEquals(completes ? "token" : null, ResultReceiver.result.getRedirectToken());
                        assertTrue(controller.get().isFinishing());
                    }
                } finally {
                    host.get().unregisterReceiver(receiver);
                }
            }
        }
    }

    public static class ResultActivity extends ComponentActivity {
        int resultCount;
        AcceptanceSessionResult result;
        final TrinsicUI trinsicUi = new TrinsicUI(this, received -> {
            resultCount++;
            result = received;
        });
    }

    public static class ResultReceiver extends BroadcastReceiver {
        static final AtomicInteger count = new AtomicInteger();
        static AcceptanceSessionResult result;
        @Override public void onReceive(Context context, Intent intent) {
            count.incrementAndGet();
            result = TrinsicPendingIntentHelper.GetAcceptanceSessionResult(intent);
        }
    }
}
