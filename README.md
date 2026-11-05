# Trinsic Android UI Library

The Trinsic Android UI Library provides ways to launch verification sessions directly from your Android application.

This library must be paired with an [api library](https://github.com/trinsic-id/sdk#api-libraries) as part of a full integration.

## Documentation

See the [Trinsic docs](https://docs.trinsic.id/docs/) for more detailed information on how to start integrating with our identity acceptance network.

## Supported Use Cases

### Hosted Provider Sessions

This library supports launching both [Hosted Provider](https://docs.trinsic.id/docs/hosted-provider-session) Sessions.

You _must_ specify a `redirectUrl` when creating either kind of Session, and that `redirectUrl` must be either a verified HTTPS App Link or a custom-scheme URL registered against your app (see below).

### Direct Provider Sessions

This library provides **only minimal** support for [Direct Provider Sessions](https://docs.trinsic.id/docs/direct-provider-session).

Specifically, it can be used only to execute Sessions which return a `launchMethod` of `LaunchBrowser` and a `collectionMethod` of `CaptureRedirect`.

All other interaction methods are not supported by this library and will require additional effort to implement; please contact Trinsic for guidance.

### Native credential exchanges (alpha)

`TrinsicMdl` launches Android Credential Manager using a request object from Trinsic's CreateMdlExchange API. See [Native API](#native-api-alpha) for Java callbacks, Kotlin coroutines, cancellation, and errors. This flow does not use browser redirects or the `CallbackActivity` setup below.

## Installation (Gradle)

This library is delivered via Jitpack.

### 1. Add Jitpack to Gradle Repositories

Add jitpack to the `repositories` block in your Gradle build file:

```
repositories {
    google()
    mavenCentral()
    // Add jitpack below
    maven {
        url = URI("https://jitpack.io")
    }
}
```

### 2. Add library as dependency

Add the library as a Gradle dependency:

```
dependencies {
    ...
    implementation("com.github.trinsic-id:sdk-android-ui")
}
```

## Installation (others)

See the [library's page on Jitpack](https://jitpack.io/#trinsic-id/sdk-android-ui) for installation instructions for Maven and others.

## Setup

### 1. Choose a Custom Scheme

First, choose a custom scheme to register against your app. This is necessary for the library to hook into the final session redirect and pass the results back to your application.

The custom scheme can be any valid URL scheme, but it **must be globally unique**. No other app -- including any of your own -- should register against the same scheme.

An example of a good scheme is `acme-corp-shopping-app-trinsic-redirect`.

### 2. Register the `CallbackActivity` in your app's `AndroidManifest.xml`

Place the following snippet in your app's `AndroidManifest.xml`, replacing `[YOURCUSTOMSCHEME]` with the scheme you chose in step 1

```xml
<activity
    android:name="id.trinsic.android.ui.CallbackActivity"
    android:exported="true">
    <intent-filter android:label="trinsic">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="[YOURCUSTOMSCHEME]" />
    </intent-filter>
</activity>
```

### 3. (Optional) Setup Task Affinity

If your app's manifest specifies the `android:taskAffinity` property on any activity which calls this library, you must make some adjustments.

#### Custom Task Affinity

If your activity specifies a custom task affinity (which is _not_ an empty string), simply make the following changes:

**1. Change the `CallbackActivity` entry**

Modify the snippet above (which you pasted into your `AndroidManifest.xml`) to specify the _same_ `android:taskAffinity` property as you specify on your own app's activity.

**2. Add an entry for `InvokeActivity`**

Normally, your app does not need to add a manifest entry for `InvokeActivity` (which is provided by this library); however, to align task affinities, you will need to do so.

Simply paste the following snippet next to where you pasted the above snippet, replacing `[your.custom.affinity]` with the custom affinity you specified on your own app's activity.

```xml
<activity
    android:name="id.trinsic.android.ui.InvokeActivity"
    android:taskAffinity="whatever.custom" />
```

#### Empty Task Affinity

> [!WARNING]  
> A [security vulnerability](https://developer.android.com/privacy-and-security/risks/strandhogg) -- affecting all versions of Android below Android 9.0 / API 28 -- is partially mitigated by explicitly specifying an empty `taskAffinity`.
> Although the official recommendation is to change your app's minimum SDK version to `28` or higher, setting an empty `taskAffinity` offers some mitigation for older devices.
> However, since an empty `taskAffinity` breaks this library, think carefully about how you proceed.
>
> Note that this vulnerability is **not related to this library**: if your app's minimum SDK version is less than 28 then it is inherently vulnerable, as the issue exists within Android itself. This library has no effect on your app's security.
>
> Your options are:
>
> 1. (**Recommended**) Increase your app's minimum SDK version to `28` or higher
>    1. Unless your app requires it (which is uncommon), you can safely remove the `android:taskAffinity=""` property after this change
>    2. This will prevent your app from running on [roughly 9%](https://gs.statcounter.com/os-version-market-share/android) of Android devices
>    3. This is the only way to fully mitigate the aforementioned security issue.
> 2. Remove the `android:taskAffinity=""` field without changing your app's minimum SDK version
>    1. Your app may be vulnerable to activity hijacking on older Android devices

If you specify a custom task affinity of `""` (an empty string), this library cannot function.

This is because cross-task activity communication is not possible in the way this library requires, and an empty task affinity will cause one or more of the library's vital activities to launch in a separate task from your original activity.

## Usage

This library can be invoked in two ways:

1. (Recommended) Using Android's Activity Result API
2. Using `PendingIntent`

### 1. Activity Result API

This method is the simplest to implement, but requires that you can call the SDK *unconditionally* during `Activity` / `Fragment` initialization.

Create `TrinsicUI` while your Activity or Fragment is initializing, then use that instance to launch a Session:

```java
private TrinsicUI trinsicUi;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // You must ALWAYS instantiate TrinsicUI unconditionally during `onCreate`
    trinsicUi = new TrinsicUI(this, result -> {
        // This callback will be called when a launched Session completes or is canceled by the user
        String sessionId = result.getSessionId();
        String redirectToken = result.getRedirectToken();
        String callbackUrl = result.getCallbackUrl();
        boolean canceled = result.getCanceled();
        
        // Call your backend to handle Session results
    });
}

private void launchSession(String launchUrl) {
    trinsicUi.LaunchSession(this, launchUrl);
}
```

### 2. PendingIntent API

Use the `PendingIntent`-based API when you cannot instantiate `TrinsicUI` unconditionally during Activity/Fragment initialization.

You will need some way to receive the callback, eg a `BroadcastReceiver` defined in your Manifest XML:

```xml
<receiver
    android:name=".TrinsicResultReceiver"
    android:exported="false" />
```

Define the Receiver class:
```java
public final class TrinsicResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AcceptanceSessionResult result = TrinsicPendingIntentHelper.GetAcceptanceSessionResult(intent);

        if (result != null) {
            String sessionId = result.getSessionId();
            String redirectToken = result.getRedirectToken();
            String callbackUrl = result.getCallbackUrl();
            boolean canceled = result.getCanceled();
            
            // Handle results
        }
    }
}
```

Finally, create a `PendingIntent` against the Receiver and the current Activity context when you need to launch a Trinsic session:

```java
PendingIntent callbackPendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        new Intent(context, TrinsicResultReceiver.class),
        TrinsicPendingIntentHelper.GetCallbackPendingIntentFlags()
);

TrinsicUI.LaunchSessionWithPendingIntent(context, launchUrl, callbackPendingIntent);
```


## HTTPS callbacks (Android App Links)

Use a dedicated callback URL, such as `https://example.com/session-complete`, as the Session's `redirectUrl` when creating it on your backend. Register that exact address in the Trinsic redirect allowlist for the relevant environment. The SDK does not change the Session's redirect URL.

Register the HTTPS callback on the SDK's **CallbackActivity**, using a separate intent filter from the custom scheme:

```xml
<activity
    android:name="id.trinsic.android.ui.CallbackActivity"
    android:exported="true">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https"
              android:host="example.com"
              android:path="/session-complete" />
    </intent-filter>
    <!-- Keep your custom-scheme intent filter here if you also use it. -->
</activity>
```

Serve `https://example.com/.well-known/assetlinks.json` as JSON without a redirect. Replace the package and fingerprint with the values for the installed app (use the Play App Signing certificate for Play-distributed builds):

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "YOUR_APPLICATION_ID",
    "sha256_cert_fingerprints": ["YOUR_SIGNING_CERTIFICATE_SHA256"]
  }
}]
```

Launch the Session using either existing API:

```java
trinsicUi.LaunchSession(this, launchUrl);

TrinsicUI.LaunchSessionWithPendingIntent(
    this, launchUrl, callbackPendingIntent
);
```

Android routes callbacks using your manifest configuration and, for HTTPS App Links, the verified domain association. Both APIs support HTTPS and custom-scheme callbacks. When the launch URL contains a non-empty `sessionId`, the SDK requires the callback to return the same ID; callbacks with a missing, empty, or different ID are ignored. Callbacks containing duplicate `sessionId` parameters are always ignored. The SDK does not validate the callback's scheme, host or path.

The `launchUrl` parameter on both launch APIs should contain a non-empty `sessionId` query parameter for callback matching. If you wrap the launch flow in your own URL, expose the original Session ID on the URL passed to the SDK, for example `https://your-app.example/start?sessionId=123`. An ID appearing only inside a nested URL or a later browser redirect is not available for matching. Both APIs open the supplied URL unchanged; the SDK reads `sessionId` but does not add or inspect `launchMode`.

Launch URLs without a non-empty `sessionId` remain supported, including `clientToken` URLs. In that case, the SDK cannot match the callback to the launched Session; it takes the ID from the callback, or returns `null` if that ID is missing or empty. Retain the separately returned `sessionId` in your app/backend to correlate and verify results.

Both APIs return an `AcceptanceSessionResult` with `sessionId`, `redirectToken`, `callbackUrl`, and `canceled`. Accepted completion callbacks return their `sessionId` and `redirectToken`, with missing or empty values represented as `null`. `callbackUrl` contains the complete callback URL as received, including all query parameters and any fragment. Browser cancellation returns `canceled=true`, the launch URL's Session ID when known (otherwise `null`), and `null` for `redirectToken` and `callbackUrl`. The deprecated `resultsAccessKey` and `success` properties have been removed; use your backend to determine the Session's outcome.

Active invocation state, the launch Session ID, and pending cancellation are retained when Android recreates the activity. A callback opened without a pending SDK invocation cannot resume a lost flow; your app must persist pending verification state and recover through your backend. `InvokeActivity` is internal and no longer exported; external links must target `CallbackActivity`.

Keep your app’s incoming links separate from verification completion links. Register the completion URL on `CallbackActivity`, and route other links to the appropriate activity in your app. Registering the same completion URL on multiple activities makes Android routing ambiguous.

### Validation

Run `./gradlew :library:testDebugUnitTest :library:lintDebug :library:assembleRelease` for session-ID validation, activity recreation, result delivery and cancellation checks. Then install a signed app after publishing its association document:

```sh
adb shell pm verify-app-links --re-verify YOUR_APPLICATION_ID
adb shell pm get-app-links YOUR_APPLICATION_ID
```

Require the domain to show `verified`, then complete a real Session from a Custom Tab and check cancellation and the existing custom scheme. Explicitly targeting a component with `adb am start` does not prove domain verification. See [Android App Link setup](https://developer.android.com/training/app-links/add-applinks) and [verification](https://developer.android.com/training/app-links/verify-applinks).

## Native API (alpha)

The native API lives in `id.trinsic.android.ui.alpha`. Create an exchange on your backend and pass its base64url request object to the SDK unchanged. Use an **Activity context** (or `Fragment.requireActivity()`) so Credential Manager can launch wallet UI. The library supports API 26+, but completing an exchange requires a compatible credential provider and matching credential on the device.

The SDK validates the base64url/UTF-8 envelope, required nonblank string fields (`exchangeId`, `type`, `platform`, `exchangeMechanism`, `requestObject`), and inner JSON object. The required routing values are `mdlRequest`, `google-wallet`, and `NativeApp`. Apps do not need to duplicate these checks. Invalid input produces `INVALID_CHALLENGE` through the same result path as provider failures, without throwing synchronously from the callback API.

The inner `requestObject` string goes to Android unchanged, including whitespace, unknown fields, document types, and signed request data. The SDK does not build document-specific descriptors, resolve request URLs, verify signatures, or validate document/protocol-specific fields. Those remain the responsibility of the Trinsic backend and credential provider. A successful response token is the **padded base64url encoding of the exact UTF-8 credential JSON** returned by Android, with no line breaks. Send the token and exchange ID to your backend without decoding or re-encoding them.

### Java: cancellable callback

```java
import id.trinsic.android.ui.alpha.MdlExchangeCall;
import id.trinsic.android.ui.alpha.TrinsicMdl;

private MdlExchangeCall exchangeCall;

private void exchange(String requestObject) {
    exchangeCall = TrinsicMdl.performMdlExchangeAsync(this, requestObject, result -> {
        exchangeCall = null;
        if (isDestroyed()) return;
        if (result.getSuccess()) {
            sendToBackend(result.getExchangeId(), result.getToken());
        } else {
            // Stable category, nonblank text, and the original cause when available.
            showError(result.getError().getErrorType(), result.getError().getMessage());
            Throwable originalCause = result.getError().getCause();
        }
    });
}

@Override
protected void onDestroy() {
    if (exchangeCall != null) {
        exchangeCall.cancel();
        exchangeCall = null;
    }
    super.onDestroy();
}
```

Start one exchange at a time per screen. The handle's `cancel()` is thread safe and idempotent. It cancels Android's `CancellationSignal` and delivers one `CANCELLED` result even if the provider never acknowledges cancellation. Cancellation requested before result delivery wins over a queued provider response; cancellation after completion does nothing. Late or duplicate provider callbacks are ignored.

Results and diagnostic events are posted to the main thread, including validation errors. Completion is delivered once per call. Exceptions thrown by your result callback remain application exceptions; they are not converted into another result. The original `TrinsicMdl.performMdlExchange(context, requestObject, callback)` method remains available with the same result getters and now uses this completion path, but does not expose a cancellation handle.

### Kotlin: suspending API

```kotlin
import androidx.lifecycle.lifecycleScope
import id.trinsic.android.ui.alpha.TrinsicMdl
import kotlinx.coroutines.launch

// Inside an Activity. lifecycleScope cancels when the Activity is destroyed.
lifecycleScope.launch {
    val result = TrinsicMdl.awaitMdlExchange(this@MainActivity, requestObject)
    if (result.success) {
        sendToBackend(result.exchangeId, result.token)
    } else {
        val error = requireNotNull(result.error)
        showError(error.errorType, error.message)
    }
}
```

Wallet dismissal returns a result with `CANCELLED`. Cancelling the calling coroutine (including a timeout or lifecycle cancellation) cancels the Android operation and **propagates `CancellationException`**, following normal structured concurrency; it does not return a failure result to a cancelled coroutine. Do not swallow `CancellationException` in a broad `catch`. Android UI work runs on the main thread and the suspending call resumes on the caller's dispatcher.

### Result and error contract

On success, `success` is true, `token` is non-null, and `error`/`exception` are null. On failure, `success` is false, `token` is null, and `error` is an `MdlExchangeException`. `getException()` is retained for compatibility and returns that same SDK exception; the original Android exception is now available through `getCause()`. The exchange ID is retained once parsed, including on validation failures; it can be null when input cannot be decoded, its ID is invalid, or cancellation happens before parsing.

| `MdlExchangeErrorType` | Meaning |
| --- | --- |
| `CANCELLED` | Wallet dismissal or explicit callback-handle cancellation. |
| `NO_MATCHING_CREDENTIAL` | Android returned `NoCredentialException`. |
| `INVALID_CHALLENGE` | Invalid envelope, unsupported routing values, or invalid inner JSON/request construction. |
| `PROVIDER_FAILURE` | Other Android/provider failures, including unavailable/unsupported providers, interruption, and synchronous provider exceptions. |
| `UNEXPECTED_RESPONSE` | A non-digital credential or failure while converting the credential response. |

Provider exceptions are preserved as the cause, including their Android type and message. The SDK supplies useful fallback text when a provider message is null, empty, or whitespace. Use the enum for program logic, not message matching. Causes and provider messages can contain sensitive information; they are not included in SDK diagnostic events.

### Optional diagnostics

Pass an `MdlDiagnosticListener` as the fourth argument to `performMdlExchangeAsync`, or as the `diagnostics` argument to `awaitMdlExchange`:

```java
exchangeCall = TrinsicMdl.performMdlExchangeAsync(this, requestObject, result -> {
    // Handle the result as above.
}, event -> {
    recordDiagnostic(event.getStage(), event.getExchangeId(),
            event.getSdkVersion(), event.getErrorType());
});
```

Events contain only `stage`, nullable `exchangeId`, `sdkVersion` (the published library version), and nullable `errorType`. Stages progress through `VALIDATING_REQUEST`, `REQUESTING_CREDENTIAL`, `ENCODING_RESPONSE`, and `COMPLETED`; failures/cancellation can skip stages. Every operation emits one terminal `COMPLETED` event, whose error type is null on success. The validation event has no exchange ID yet. Coroutine cancellation after an operation has already completed does not replace its terminal event.

Diagnostics are disabled unless a listener is supplied, stay local to that listener, and contain no request JSON, credential contents, tokens, exception messages, or causes. Listener exceptions are ignored so diagnostics cannot prevent completion. Listeners run on the main thread and should return quickly.

The implementation uses Android's [callback API with CancellationSignal](https://developer.android.com/reference/androidx/credentials/CredentialManager) and Kotlin's [suspendCancellableCoroutine](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/suspend-cancellable-coroutine.html).
