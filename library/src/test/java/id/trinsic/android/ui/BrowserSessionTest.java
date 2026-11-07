package id.trinsic.android.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsService;
import androidx.browser.customtabs.CustomTabsSessionToken;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import id.trinsic.android.ui.models.AcceptanceSessionResult;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BrowserSessionTest {
    private static final String BROWSER = "test.default.browser";
    private static final ComponentName SERVICE = new ComponentName(BROWSER, "CustomTabsService");
    private static final Uri URL = Uri.parse("https://verify.trinsic.id/start?sessionId=123&token=a%2Bb%2Fc#fragment");
    private RecordingContext context;
    private FakeBrowser service;
    private BrowserSession session;
    private QueuedExecutor worker;

    @Before public void setup() {
        context = new RecordingContext(RuntimeEnvironment.getApplication());
        service = new FakeBrowser();
        worker = new QueuedExecutor();
        session = new BrowserSession(context, worker);
        ResolveInfo browser = new ResolveInfo();
        browser.activityInfo = new ActivityInfo();
        browser.activityInfo.packageName = BROWSER;
        browser.activityInfo.name = "BrowserActivity";
        shadowOf(context.getPackageManager()).addResolveInfoForIntent(
                new Intent(Intent.ACTION_VIEW, Uri.parse("http://")), browser);
        registerService(BROWSER);
    }

    @After public void cleanup() {
        session.close();
        TestInvokeActivity.applicationContext = null;
        TestInvokeActivity.worker = null;
    }

    private void registerService(String browserPackage) {
        ResolveInfo info = new ResolveInfo();
        info.serviceInfo = new ServiceInfo();
        info.serviceInfo.packageName = browserPackage;
        info.serviceInfo.name = "CustomTabsService";
        shadowOf(context.getPackageManager()).addResolveInfoForIntent(
                new Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION).setPackage(browserPackage), info);
    }

    private void connect(ServiceConnection connection) {
        connectWithoutRunningWorker(connection);
        worker.runAll();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void connectWithoutRunningWorker(ServiceConnection connection) {
        connection.onServiceConnected(SERVICE, service.onBind(
                new Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION)));
    }

    private Intent takeBrowserIntent() {
        CustomTabContract.Launch launch = session.takeLaunch();
        assertNotNull(launch);
        return new CustomTabContract().createIntent(context, launch);
    }

    private void assertFallback() {
        Intent intent = takeBrowserIntent();
        assertEquals(URL.toString(), intent.getDataString());
        assertNull(intent.getPackage());
        assertNull(intent.getExtras().getBinder(CustomTabsIntent.EXTRA_SESSION));
        assertFalse(intent.getBooleanExtra(CustomTabsIntent.EXTRA_SEND_TO_EXTERNAL_DEFAULT_HANDLER, false));
        assertNull(session.takeLaunch());
    }

    @Test public void bindsDefaultBrowserAndKeepsSessionUntilCompletion() {
        session.prepare(URL);
        assertEquals(BROWSER, context.boundIntent.getPackage());
        assertNull(session.takeLaunch());
        connect(context.connections.get(0));
        Intent intent = takeBrowserIntent();
        assertEquals(URL.toString(), intent.getDataString());
        assertEquals(BROWSER, intent.getPackage());
        assertNotNull(intent.getExtras().getBinder(CustomTabsIntent.EXTRA_SESSION));
        assertTrue(intent.getBooleanExtra(CustomTabsIntent.EXTRA_SEND_TO_EXTERNAL_DEFAULT_HANDLER, false));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
        assertEquals(0, context.unbinds);
        assertNull(session.takeLaunch());
        session.close();
        session.close();
        assertEquals(1, context.unbinds);
    }

    @Test public void defaultBrowserWithoutCustomTabsIsNotReplaced() {
        shadowOf(context.getPackageManager()).removeResolveInfosForIntent(
                new Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION).setPackage(BROWSER), BROWSER);
        registerService("some.other.browser");
        session.prepare(URL);
        assertFallback();
        assertTrue(context.connections.isEmpty());
    }

    @Test public void failedOrDeniedBindingFallsBackOnce() {
        for (boolean denied : new boolean[] {false, true}) {
            context.bindResult = false;
            context.denyBinding = denied;
            session.prepare(URL);
            assertFallback();
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
            assertNull(session.takeLaunch());
        }
    }

    @Test public void timeoutReleasesConnectionAndIgnoresLateService() {
        session.prepare(URL);
        ServiceConnection connection = context.connections.get(0);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(BrowserSession.BIND_TIMEOUT_MILLIS));
        assertFallback();
        assertEquals(1, context.unbinds);
        connect(connection);
        assertEquals(0, service.sessions);
        assertNull(session.takeLaunch());
    }

    @Test public void cancellationBeforeBindingPreventsLateLaunch() {
        session.prepare(URL);
        ServiceConnection connection = context.connections.get(0);
        session.close();
        connect(connection);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertNull(session.takeLaunch());
        assertEquals(0, service.sessions);
        assertEquals(1, context.unbinds);
    }

    @Test public void replacementIgnoresOldConnection() {
        session.prepare(URL);
        ServiceConnection old = context.connections.get(0);
        Uri replacement = Uri.parse("https://verify.trinsic.id/start?sessionId=456");
        session.prepare(replacement);
        connect(old);
        assertNull(session.takeLaunch());
        connect(context.connections.get(1));
        assertEquals(replacement.toString(), takeBrowserIntent().getDataString());
        assertEquals(1, service.sessions);
        assertEquals(1, context.unbinds);
    }

    @Test public void refusedOrDeniedSessionFallsBack() {
        for (boolean denied : new boolean[] {false, true}) {
            service.acceptSession = false;
            service.denySession = denied;
            session.prepare(URL);
            connect(context.connections.get(context.connections.size() - 1));
            assertFallback();
        }
        assertEquals(2, context.unbinds);
    }

    @Test public void deadOrNullBindingDoesNotWaitForTimeout() {
        session.prepare(URL);
        context.connections.get(0).onBindingDied(SERVICE);
        assertFallback();
        session.prepare(URL);
        context.connections.get(1).onNullBinding(SERVICE);
        assertFallback();
        assertEquals(2, context.unbinds);
    }

    @Test public void disconnectBeforeLaunchDiscardsDeadSession() {
        session.prepare(URL);
        ServiceConnection connection = context.connections.get(0);
        connect(connection);
        connection.onServiceDisconnected(SERVICE);
        assertFallback();
        assertEquals(1, context.unbinds);
    }

    @Test public void disconnectAfterLaunchNeverOpensAnotherTab() {
        session.prepare(URL);
        ServiceConnection connection = context.connections.get(0);
        connect(connection);
        takeBrowserIntent();
        connection.onServiceDisconnected(SERVICE);
        connect(connection);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertNull(session.takeLaunch());
        assertEquals(1, context.unbinds);
        assertEquals(1, service.sessions);
    }

    private ActivityController<TestInvokeActivity> activity() {
        TestInvokeActivity.applicationContext = context;
        TestInvokeActivity.worker = worker;
        return Robolectric.buildActivity(TestInvokeActivity.class,
                new Intent(InvokeActivity.ACTION_INVOKE).putExtra("launchUrl", URL.toString())).setup();
    }

    @Test public void recreationWhileBindingRetainsConnectionAndLaunchesOnce() {
        try (ActivityController<TestInvokeActivity> activity = activity()) {
            assertNull(shadowOf(activity.get()).getNextStartedActivityForResult());
            BrowserSession original = BrowserSession.get(activity.get());
            activity.recreate();
            assertSame(original, BrowserSession.get(activity.get()));
            assertEquals(1, context.connections.size());
            connect(context.connections.get(0));
            assertNotNull(shadowOf(activity.get()).getNextStartedActivityForResult());
            activity.recreate();
            assertNull(shadowOf(activity.get()).getNextStartedActivityForResult());
            assertEquals(0, context.unbinds);
        }
        assertEquals(1, context.unbinds);
    }

    @Test public void stoppedActivityWaitsUntilStartedToOpenBrowser() {
        try (ActivityController<TestInvokeActivity> activity = activity()) {
            activity.pause().stop();
            connect(context.connections.get(0));
            assertNull(shadowOf(activity.get()).getNextStartedActivityForResult());
            activity.start().resume();
            assertNotNull(shadowOf(activity.get()).getNextStartedActivityForResult());
            assertEquals(0, context.unbinds);
        }
    }

    @Test public void pendingLaunchRestartsBindingAfterProcessStateIsRestored() {
        ActivityController<TestInvokeActivity> original = activity();
        Bundle state = new Bundle();
        original.pause().stop().saveInstanceState(state).destroy();
        assertEquals(1, context.unbinds);
        try (ActivityController<TestInvokeActivity> restored = Robolectric.buildActivity(
                TestInvokeActivity.class, new Intent(InvokeActivity.ACTION_INVOKE)
                        .putExtra("launchUrl", URL.toString())).create(state).start().resume()) {
            assertEquals(2, context.connections.size());
            connect(context.connections.get(0));
            assertNull(shadowOf(restored.get()).getNextStartedActivityForResult());
            connect(context.connections.get(1));
            assertEquals(URL.toString(), shadowOf(restored.get())
                    .getNextStartedActivityForResult().intent.getDataString());
            assertNull(shadowOf(restored.get()).getNextStartedActivityForResult());
        }
        assertEquals(2, context.unbinds);
    }

    @Test public void backWhileBindingCancelsAndPreventsLateLaunch() {
        try (ActivityController<TestInvokeActivity> activity = activity()) {
            activity.get().getOnBackPressedDispatcher().onBackPressed();
            connect(context.connections.get(0));
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
            assertNull(shadowOf(activity.get()).getNextStartedActivityForResult());
            assertTrue(activity.get().isFinishing());
            AcceptanceSessionResult result = TrinsicPendingIntentHelper.GetAcceptanceSessionResult(
                    shadowOf(activity.get()).getResultIntent());
            assertTrue(result.getCanceled());
            assertEquals("123", result.getSessionId());
            assertEquals(1, context.unbinds);
        }
    }

    @Test public void callbackReleasesBindingWithoutChangingResult() {
        try (ActivityController<TestInvokeActivity> activity = activity()) {
            connect(context.connections.get(0));
            String url = "https://example.com/session-complete?sessionId=123&redirectToken=token";
            activity.newIntent(new Intent(InvokeActivity.ACTION_CALLBACK).setData(Uri.parse(url)));
            assertEquals(Activity.RESULT_OK, shadowOf(activity.get()).getResultCode());
            assertEquals(url, TrinsicPendingIntentHelper.GetAcceptanceSessionResult(
                    shadowOf(activity.get()).getResultIntent()).getCallbackUrl());
            assertEquals(1, context.unbinds);
        }
    }

    @Test public void timeoutWhileSessionCreationIsStalledKeepsMainThreadResponsive() throws Exception {
        CountDownLatch workerFinished = new CountDownLatch(1);
        session = new BrowserSession(context, task -> new Thread(() -> {
            try { task.run(); }
            finally { workerFinished.countDown(); }
        }, "TestBrowserSession").start());
        service.blockSession = true;
        try {
            session.prepare(URL);
            connectWithoutRunningWorker(context.connections.get(0));
            assertTrue(service.entered.await(5, TimeUnit.SECONDS));
            assertNotSame(Looper.getMainLooper().getThread(), service.sessionThread);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(BrowserSession.BIND_TIMEOUT_MILLIS));
            assertFallback();
            assertEquals(1, context.unbinds);
            service.release.countDown();
            assertTrue(workerFinished.await(5, TimeUnit.SECONDS));
            shadowOf(Looper.getMainLooper()).idle();
            assertNull(session.takeLaunch());
        } finally {
            service.release.countDown();
        }
    }

    @Test public void cancellationBeforeWorkerRunsSkipsSessionCreation() {
        session.prepare(URL);
        connectWithoutRunningWorker(context.connections.get(0));
        session.close();
        worker.runAll();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, service.sessions);
        assertEquals(1, context.unbinds);
        assertNull(session.takeLaunch());
    }

    @Test public void lateWorkerResultCannotReplaceNewInvocation() {
        session.prepare(URL);
        connectWithoutRunningWorker(context.connections.get(0));
        worker.runAll(); // The old result is queued for the main thread.
        Uri replacement = Uri.parse("https://verify.trinsic.id/start?sessionId=456");
        session.prepare(replacement);
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(session.takeLaunch());
        connect(context.connections.get(1));
        assertEquals(replacement, takeBrowserIntent().getData());
        assertEquals(1, context.unbinds);
    }

    @Test public void busyWorkerFallsBackWithoutCallingBrowserOnMainThread() {
        session = new BrowserSession(context, command -> { throw new RejectedExecutionException(); });
        session.prepare(URL);
        connectWithoutRunningWorker(context.connections.get(0));
        assertFallback();
        assertEquals(0, service.sessions);
        assertEquals(1, context.unbinds);
    }

    @Test public void duplicateConnectionCallbackDoesNotCreateAnotherSession() {
        session.prepare(URL);
        ServiceConnection connection = context.connections.get(0);
        connectWithoutRunningWorker(connection);
        connectWithoutRunningWorker(connection);
        assertEquals(1, worker.tasks.size());
        worker.runAll();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, service.sessions);
        assertNotNull(takeBrowserIntent());
        assertNull(session.takeLaunch());
    }

    public static class TestInvokeActivity extends InvokeActivity {
        static Context applicationContext;
        static Executor worker;
        @Override public Context getApplicationContext() { return applicationContext; }
        @Override protected void onCreate(Bundle state) {
            new ViewModelProvider(this, new ViewModelProvider.Factory() {
                @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
                    return type.cast(new BrowserSession(applicationContext, worker));
                }
            }).get(BrowserSession.class);
            super.onCreate(state);
        }
    }

    private static class QueuedExecutor implements Executor {
        final List<Runnable> tasks = new ArrayList<>();
        @Override public void execute(Runnable task) { tasks.add(task); }
        void runAll() {
            for (Runnable task : new ArrayList<>(tasks)) task.run();
            tasks.clear();
        }
    }

    private static class RecordingContext extends ContextWrapper {
        final List<ServiceConnection> connections = new ArrayList<>();
        Intent boundIntent;
        int unbinds;
        boolean bindResult = true;
        boolean denyBinding;
        RecordingContext(Context base) { super(base); }
        @Override public Context getApplicationContext() { return this; }
        @Override public boolean bindService(Intent intent, ServiceConnection connection, int flags) {
            boundIntent = intent;
            connections.add(connection);
            if (denyBinding) throw new SecurityException("denied");
            return bindResult;
        }
        @Override public void unbindService(ServiceConnection connection) { unbinds++; }
    }

    private static class FakeBrowser extends CustomTabsService {
        int sessions;
        boolean acceptSession = true;
        boolean denySession;
        boolean blockSession;
        Thread sessionThread;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        @Override protected boolean newSession(CustomTabsSessionToken token) {
            sessions++;
            if (denySession) throw new SecurityException("denied");
            sessionThread = Thread.currentThread();
            entered.countDown();
            if (blockSession) {
                // Simulate Binder: interruption alone does not release a stuck remote call.
                boolean done = false;
                while (!done) {
                    try { done = release.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { }
                }
            }
            return acceptSession;
        }
        @Override protected boolean warmup(long flags) { return true; }
        @Override protected boolean mayLaunchUrl(CustomTabsSessionToken token, Uri uri, Bundle extras, List<Bundle> other) { return false; }
        @Override protected Bundle extraCommand(String command, Bundle args) { return null; }
        @Override protected boolean updateVisuals(CustomTabsSessionToken token, Bundle bundle) { return false; }
        @Override protected boolean requestPostMessageChannel(CustomTabsSessionToken token, Uri origin) { return false; }
        @Override protected int postMessage(CustomTabsSessionToken token, String message, Bundle extras) { return 0; }
        @Override protected boolean validateRelationship(CustomTabsSessionToken token, int relation, Uri origin, Bundle extras) { return false; }
        @Override protected boolean receiveFile(CustomTabsSessionToken token, Uri uri, int purpose, Bundle extras) { return false; }
    }
}
