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
import android.os.Parcel;

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
    private static final String CALLBACK = "https://example.com/session-complete";
    private static final String LAUNCH = "https://verify.trinsic.id/?sessionId=123";
    private static final String CLIENT_TOKEN_LAUNCH = "https://verify.trinsic.id/api/session/launch?clientToken=example-token";
    private static final String FULL_CALLBACK = CALLBACK
            + "?sessionId=123&redirectToken=a%2Bb%2fc&resultsAccessKey=legacy&success=false&extra=x%20y#complete";
    private static final String[] LAUNCH_URLS = {
            LAUNCH, CLIENT_TOKEN_LAUNCH, "https://verify.trinsic.id/",
            "https://verify.trinsic.id/?sessionId=&sessionId=ignored&launchMode=web&launchMode=&clientToken=a%2Bb%2fc+d#fragment"
    };
    private static final String[][] OPTIONAL_CALLBACK_FIELDS = {
            {CALLBACK, null, null},
            {CALLBACK + "?sessionId=&redirectToken=", null, null},
            {CALLBACK + "?sessionId&redirectToken", null, null},
            {CALLBACK + "?redirectToken=token", null, "token"},
            {CALLBACK + "?sessionId=123", "123", null},
            {CALLBACK + "?sessionId=&redirectToken=token", null, "token"},
            {CALLBACK + "?sessionId=123&redirectToken=", "123", null}
    };

    private Intent invocation() {
        return invocation(LAUNCH);
    }

    private Intent invocation(String launchUrl) {
        return new InvokeContract().createIntent(RuntimeEnvironment.getApplication(),
                new AcceptanceSessionLaunchParams(launchUrl));
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

    @Test public void activityResultApiOpensLaunchUrlUnchanged() {
        for (String launchUrl : LAUNCH_URLS) {
            try (ActivityController<ResultActivity> host = Robolectric.buildActivity(ResultActivity.class).setup()) {
                host.get().trinsicUi.LaunchSession(host.get(), launchUrl);
                Intent launch = shadowOf(host.get()).getNextStartedActivityForResult().intent;
                assertBrowserUrlUnchanged(launch, launchUrl);
            }
        }
    }

    @Test public void pendingIntentApiOpensLaunchUrlUnchanged() {
        for (String launchUrl : LAUNCH_URLS) {
            try (ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup()) {
                ResultReceiver receiver = new ResultReceiver();
                PendingIntent pending = registerResultReceiver(host.get(), receiver);
                try {
                    TrinsicUI.LaunchSessionWithPendingIntent(host.get(), launchUrl, pending);
                    Intent launch = shadowOf(host.get()).getNextStartedActivityForResult().intent;
                    assertBrowserUrlUnchanged(launch, launchUrl);
                } finally {
                    host.get().unregisterReceiver(receiver);
                }
            }
        }
    }

    private void assertBrowserUrlUnchanged(Intent launch, String launchUrl) {
        try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, launch).setup()) {
            Intent browser = shadowOf(controller.get()).getNextStartedActivityForResult().intent;
            assertEquals(launchUrl, browser.getDataString());
        }
    }

    @Test public void httpsCallbackCompletesActivityResultWithToken() {
        try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, invocation()).setup()) {
            controller.newIntent(callback(FULL_CALLBACK));
            ShadowActivity shadow = shadowOf(controller.get());
            assertEquals(Activity.RESULT_OK, shadow.getResultCode());
            AcceptanceSessionResult result = new InvokeContract().parseResult(shadow.getResultCode(), shadow.getResultIntent());
            assertEquals("123", result.getSessionId());
            assertEquals("a+b/c", result.getRedirectToken());
            assertEquals(FULL_CALLBACK, result.getCallbackUrl());
            assertFalse(result.getCanceled());
        }
    }

    @Test public void activityResultApiReturnsNullForMissingOrEmptyCallbackFields() {
        for (String[] values : OPTIONAL_CALLBACK_FIELDS) {
            try (ActivityController<ResultActivity> host = Robolectric.buildActivity(ResultActivity.class).setup()) {
                host.get().trinsicUi.LaunchSession(host.get(), CLIENT_TOKEN_LAUNCH);
                ShadowActivity hostShadow = shadowOf(host.get());
                Intent launch = hostShadow.getNextStartedActivityForResult().intent;
                try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, launch).setup()) {
                    controller.newIntent(callback(values[0]));
                    ShadowActivity shadow = shadowOf(controller.get());
                    assertEquals(Activity.RESULT_OK, shadow.getResultCode());
                    hostShadow.receiveResult(launch, shadow.getResultCode(), shadow.getResultIntent());
                    assertEquals(1, host.get().resultCount);
                    assertCallbackFields(host.get().result, values);
                }
            }
        }
    }

    @Test public void pendingIntentApiReturnsNullForMissingOrEmptyCallbackFields() {
        for (String[] values : OPTIONAL_CALLBACK_FIELDS) {
            try (ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup()) {
                ResultReceiver receiver = new ResultReceiver();
                PendingIntent pending = registerResultReceiver(host.get(), receiver);
                try {
                    TrinsicUI.LaunchSessionWithPendingIntent(host.get(), "https://verify.trinsic.id/?sessionId=", pending);
                    Intent launch = shadowOf(host.get()).getNextStartedActivityForResult().intent;
                    try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, launch).setup()) {
                        controller.newIntent(callback(values[0]));
                        shadowOf(Looper.getMainLooper()).idle();
                        assertEquals(1, ResultReceiver.count.get());
                        assertCallbackFields(ResultReceiver.result, values);
                    }
                } finally {
                    host.get().unregisterReceiver(receiver);
                }
            }
        }
    }

    private void assertCallbackFields(AcceptanceSessionResult result, String[] values) {
        assertNotNull(result);
        assertEquals(values[0], result.getCallbackUrl());
        assertEquals(values[1], result.getSessionId());
        assertEquals(values[2], result.getRedirectToken());
        assertFalse(result.getCanceled());
    }

    @Test public void activityResultApiRequiresMatchingSessionId() {
        try (ActivityController<ResultActivity> host = Robolectric.buildActivity(ResultActivity.class).setup()) {
            host.get().trinsicUi.LaunchSession(host.get(), LAUNCH);
            ShadowActivity hostShadow = shadowOf(host.get());
            Intent launch = hostShadow.getNextStartedActivityForResult().intent;
            try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, launch).setup()) {
                assertUnmatchedCallbacksIgnored(controller);
                assertEquals(0, host.get().resultCount);
                controller.newIntent(callback(FULL_CALLBACK));
                ShadowActivity shadow = shadowOf(controller.get());
                hostShadow.receiveResult(launch, shadow.getResultCode(), shadow.getResultIntent());
                assertEquals(1, host.get().resultCount);
                assertCallbackFields(host.get().result, new String[] {FULL_CALLBACK, "123", "a+b/c"});
            }
        }
    }

    @Test public void pendingIntentApiRequiresMatchingSessionIdAfterRecreation() {
        try (ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup()) {
            ResultReceiver receiver = new ResultReceiver();
            PendingIntent pending = registerResultReceiver(host.get(), receiver);
            try {
                TrinsicUI.LaunchSessionWithPendingIntent(host.get(), LAUNCH, pending);
                Intent launch = shadowOf(host.get()).getNextStartedActivityForResult().intent;
                try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, launch).setup()) {
                    controller.recreate();
                    assertUnmatchedCallbacksIgnored(controller);
                    shadowOf(Looper.getMainLooper()).idle();
                    assertEquals(0, ResultReceiver.count.get());
                    controller.newIntent(callback(FULL_CALLBACK));
                    shadowOf(Looper.getMainLooper()).idle();
                    assertEquals(1, ResultReceiver.count.get());
                    assertCallbackFields(ResultReceiver.result, new String[] {FULL_CALLBACK, "123", "a+b/c"});
                }
            } finally {
                host.get().unregisterReceiver(receiver);
            }
        }
    }

    private void assertUnmatchedCallbacksIgnored(ActivityController<InvokeActivity> controller) {
        for (String url : new String[] {CALLBACK + "?sessionId=456", CALLBACK + "?sessionId=", CALLBACK}) {
            controller.newIntent(callback(url));
            assertNull(url, shadowOf(controller.get()).getResultIntent());
            assertFalse(url, controller.get().isFinishing());
        }
    }

    @Test public void wrapperLaunchUrlMatchesDecodedSessionIdWithoutChangingUrl() {
        String launchUrl = "https://your-app.example/start?sessionId=session%2F123%2Babc&target=https%3A%2F%2Fverify.trinsic.id%2F";
        String callbackUrl = CALLBACK + "?sessionId=session%2f123%2babc";
        try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, invocation(launchUrl)).setup()) {
            ShadowActivity shadow = shadowOf(controller.get());
            assertEquals(launchUrl, shadow.getNextStartedActivityForResult().intent.getDataString());
            controller.newIntent(callback(callbackUrl));
            assertEquals(Activity.RESULT_OK, shadow.getResultCode());
            assertCallbackFields(TrinsicPendingIntentHelper.GetAcceptanceSessionResult(shadow.getResultIntent()),
                    new String[] {callbackUrl, "session/123+abc", null});
        }
    }

    @Test public void parcelRoundTripPreservesFullCallbackUrlAndNullFields() {
        for (AcceptanceSessionResult original : new AcceptanceSessionResult[] {
                new AcceptanceSessionResult("123", "a+b/c", FULL_CALLBACK, false),
                new AcceptanceSessionResult(null, null, CALLBACK, false),
                new AcceptanceSessionResult(null, null, null, true)
        }) {
            Parcel parcel = Parcel.obtain();
            try {
                original.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                AcceptanceSessionResult restored = AcceptanceSessionResult.CREATOR.createFromParcel(parcel);
                assertEquals(original.getSessionId(), restored.getSessionId());
                assertEquals(original.getRedirectToken(), restored.getRedirectToken());
                assertEquals(original.getCallbackUrl(), restored.getCallbackUrl());
                assertEquals(original.getCanceled(), restored.getCanceled());
            } finally {
                parcel.recycle();
            }
        }
    }

    @Test public void rejectsDuplicateSessionId() {
        try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, invocation()).setup()) {
            for (String url : new String[] {
                    CALLBACK + "?sessionId=123&sessionId=123",
                    CALLBACK + "?sessionId=123&sessionId=456"
            }) {
                controller.newIntent(callback(url));
                assertNull(url, shadowOf(controller.get()).getResultIntent());
                assertFalse(url, controller.get().isFinishing());
            }
        }
    }

    @Test public void customSchemeCallbackCompletesActivityResult() {
        try (ActivityController<InvokeActivity> controller = Robolectric.buildActivity(InvokeActivity.class, invocation()).setup()) {
            String url = "example-app://session-complete?sessionId=123&redirectToken=token";
            controller.newIntent(callback(url));
            ShadowActivity shadow = shadowOf(controller.get());
            assertEquals(Activity.RESULT_OK, shadow.getResultCode());
            assertEquals(url, TrinsicPendingIntentHelper.GetAcceptanceSessionResult(shadow.getResultIntent()).getCallbackUrl());
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
            assertUnmatchedCallbacksIgnored(restored);
            restored.newIntent(callback(CALLBACK + "?sessionId=123"));
            ShadowActivity shadow = shadowOf(restored.get());
            assertEquals(Activity.RESULT_OK, shadow.getResultCode());
            assertEquals("123", TrinsicPendingIntentHelper.GetAcceptanceSessionResult(shadow.getResultIntent()).getSessionId());
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
            assertNull(result.getRedirectToken());
            assertNull(result.getCallbackUrl());
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
                        Intent result = callback(FULL_CALLBACK);
                        controller.newIntent(result);
                        controller.newIntent(result);
                        shadowOf(Looper.getMainLooper()).idle();
                        assertEquals(1, ResultReceiver.count.get());
                        assertEquals("123", ResultReceiver.result.getSessionId());
                        assertEquals("a+b/c", ResultReceiver.result.getRedirectToken());
                        assertEquals(FULL_CALLBACK, ResultReceiver.result.getCallbackUrl());
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
                String browserUrl = shadowOf(controller.get()).getNextStartedActivityForResult().intent.getDataString();
                assertEquals(CLIENT_TOKEN_LAUNCH, browserUrl);
                controller.recreate();
                ShadowActivity shadow = shadowOf(controller.get());
                assertNull(shadow.getNextStartedActivityForResult());
                controller.newIntent(callback(CALLBACK + "?sessionId=123&sessionId=123"));
                assertNull(shadow.getResultIntent());
                assertFalse(controller.get().isFinishing());
                controller.newIntent(callback(FULL_CALLBACK));
                hostShadow.receiveResult(launch, shadow.getResultCode(), shadow.getResultIntent());
                assertEquals(1, host.get().resultCount);
                assertEquals("123", host.get().result.getSessionId());
                assertEquals("a+b/c", host.get().result.getRedirectToken());
                assertEquals(FULL_CALLBACK, host.get().result.getCallbackUrl());
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
                    assertEquals(LAUNCH.equals(launchUrl) ? "123" : null, host.get().result.getSessionId());
                    assertNull(host.get().result.getRedirectToken());
                    assertNull(host.get().result.getCallbackUrl());
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
            String expectedSessionId = LAUNCH.equals(launchUrl) ? "123" : null;
            String completionUrl = expectedSessionId == null ? CALLBACK : CALLBACK + "?sessionId=123";
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
                            controller.newIntent(callback(completionUrl));
                        }
                        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30));
                        assertEquals(1, ResultReceiver.count.get());
                        assertEquals(!completes, ResultReceiver.result.getCanceled());
                        assertEquals(expectedSessionId, ResultReceiver.result.getSessionId());
                        assertNull(ResultReceiver.result.getRedirectToken());
                        assertEquals(completes ? completionUrl : null, ResultReceiver.result.getCallbackUrl());
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
