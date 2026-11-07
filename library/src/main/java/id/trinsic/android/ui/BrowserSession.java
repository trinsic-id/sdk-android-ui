package id.trinsic.android.ui;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Owns one browser binding across activity recreation without retaining an Activity. */
@MainThread
final class BrowserSession extends ViewModel {
    static final long BIND_TIMEOUT_MILLIS = 2000;
    private static final String TAG = "TrinsicBrowser";
    // Binder calls cannot reliably be interrupted. Bound concurrency and avoid a queue so a
    // broken browser cannot accumulate threads or block every subsequent verification attempt.
    private static final Executor SESSION_WORKER = new ThreadPoolExecutor(
            0, 2, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "TrinsicBrowserSession");
                thread.setDaemon(true);
                return thread;
            });

    private final Context context;
    private final Executor sessionWorker;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<CustomTabContract.Launch> pendingLaunch = new MutableLiveData<>();
    private Attempt current;

    BrowserSession(Context context) {
        this(context, SESSION_WORKER);
    }

    BrowserSession(Context context, Executor sessionWorker) {
        this.context = context.getApplicationContext();
        this.sessionWorker = sessionWorker;
    }

    static BrowserSession get(ComponentActivity activity) {
        Context application = activity.getApplicationContext();
        return new ViewModelProvider(activity, new ViewModelProvider.Factory() {
            @NonNull
            @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return modelClass.cast(new BrowserSession(application));
            }
        }).get(BrowserSession.class);
    }

    LiveData<CustomTabContract.Launch> pendingLaunch() {
        return pendingLaunch;
    }

    void prepare(Uri url) {
        close();
        current = new Attempt(url);
        current.bind();
    }

    void restorePending(Uri url) {
        if (current == null) prepare(url);
    }

    @Nullable CustomTabContract.Launch takeLaunch() {
        CustomTabContract.Launch launch = pendingLaunch.getValue();
        if (current == null || current.consumed || launch == null) return null;
        current.consumed = true;
        pendingLaunch.setValue(null);
        return launch;
    }

    void close() {
        Attempt previous = current;
        current = null;
        if (previous != null) previous.close();
        pendingLaunch.setValue(null);
    }

    @Override protected void onCleared() {
        close();
    }

    private final class Attempt extends CustomTabsServiceConnection {
        final Uri url;
        final Runnable timeout = this::fallback;
        boolean binding;
        boolean ready;
        boolean consumed;
        boolean closed;
        boolean sessionRequested;
        @Nullable FutureTask<Void> sessionTask;
        @Nullable CustomTabsSession session;

        Attempt(Uri url) {
            this.url = url;
        }

        void bind() {
            try {
                // The VIEW query in AndroidManifest.xml keeps defaults without Custom Tabs visible.
                // With that visibility, a null candidate list avoids switching to another browser
                // with a separate cookie jar or different user preferences.
                String browser = CustomTabsClient.getPackageName(context, null);
                if (browser == null) {
                    fallback();
                    return;
                }
                binding = true;
                if (!CustomTabsClient.bindCustomTabsService(context, browser, this)) {
                    fallback();
                } else if (!ready && !closed) {
                    handler.postDelayed(timeout, BIND_TIMEOUT_MILLIS);
                }
            } catch (SecurityException exception) {
                Log.w(TAG, "Browser service binding unavailable; opening without a session");
                fallback();
            }
        }

        @Override public void onCustomTabsServiceConnected(
                @NonNull ComponentName name, @NonNull CustomTabsClient client) {
            if (current != this || closed || ready || sessionRequested) return;
            sessionRequested = true;
            sessionTask = new FutureTask<>(() -> {
                CustomTabsSession connectedSession;
                try {
                    connectedSession = client.newSession(new CustomTabsCallback());
                } catch (SecurityException exception) {
                    connectedSession = null;
                }
                CustomTabsSession result = connectedSession;
                handler.post(() -> sessionCreated(result));
                return null;
            });
            try {
                sessionWorker.execute(sessionTask);
            } catch (RejectedExecutionException exception) {
                fallback();
            }
        }

        private void sessionCreated(@Nullable CustomTabsSession result) {
            if (current != this || closed || ready) return;
            sessionTask = null;
            session = result;
            if (session == null) {
                fallback();
                return;
            }
            ready = true;
            handler.removeCallbacks(timeout);
            pendingLaunch.setValue(new CustomTabContract.Launch(url, session));
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            disconnected();
        }

        @Override public void onBindingDied(ComponentName name) {
            disconnected();
        }

        @Override public void onNullBinding(ComponentName name) {
            disconnected();
        }

        private void disconnected() {
            if (current != this || closed) return;
            if (!consumed) fallback();
            else releaseBinding(); // An open tab must never be relaunched by a service event.
        }

        private void fallback() {
            if (current != this || closed || consumed) return;
            ready = true;
            handler.removeCallbacks(timeout);
            releaseBinding();
            pendingLaunch.setValue(new CustomTabContract.Launch(url, null));
        }

        private void releaseBinding() {
            if (sessionTask != null) {
                sessionTask.cancel(true);
                sessionTask = null;
            }
            session = null;
            if (!binding) return;
            binding = false;
            try {
                context.unbindService(this);
            } catch (IllegalArgumentException | SecurityException ignored) {
                // Failed binds and browser removal may already have released the registration.
            }
        }

        void close() {
            closed = true;
            handler.removeCallbacks(timeout);
            releaseBinding();
        }
    }
}
