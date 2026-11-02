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

Use a dedicated callback URL, such as `https://pocketrides.app/session-complete`, as the Session's `redirectUrl` when creating it on your backend. Register that exact address in the Trinsic redirect allowlist for the relevant environment. The SDK does not change the Session's redirect URL.

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
              android:host="pocketrides.app"
              android:path="/session-complete" />
    </intent-filter>
    <!-- Keep your custom-scheme intent filter here if you also use it. -->
</activity>
```

Serve `https://pocketrides.app/.well-known/assetlinks.json` as JSON without a redirect. Replace the package and fingerprint with the values for the installed app (use the Play App Signing certificate for Play-distributed builds):

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

Android routes callbacks using your manifest configuration and, for HTTPS App Links, the verified domain association. Both APIs support HTTPS and custom-scheme callbacks. When the launch URL contains a Session ID, the SDK requires the returned ID to match it. The SDK does not validate the callback's scheme, host or path.

Pass the backend's `launchUrl` directly to the SDK. The [Create Hosted Provider Session response](https://docs.trinsic.id/reference/createhostedprovidersession) can contain a `clientToken` launch URL without a Session ID. For these URLs, the SDK takes the ID from the completion callback and cannot independently match it to the launched Session. Retain the separately returned `sessionId` in your app/backend to correlate and verify results. If the user closes the browser before a callback arrives, the cancellation result's Session ID is `null` for these URLs.

The existing Activity Result and PendingIntent result APIs, including `redirectToken` and cancellation, work unchanged. Active invocation state, any known Session ID, and pending cancellation are retained when Android recreates the activity. A callback opened without a pending SDK invocation cannot resume a lost flow; your app must persist pending verification state and recover through your backend. `InvokeActivity` is internal and no longer exported; external links must target `CallbackActivity`.

Keep incoming launch links separate from SDK completion links. For example, PocketRides' `/demo/pocketrides` belongs on its main activity, while `/session-complete` belongs on `CallbackActivity`. Registering the same callback on both activities makes Android routing ambiguous. Do not hard-code a customer's domain in the library manifest.

### Validation

Run `./gradlew :library:testDebugUnitTest :library:lintDebug :library:assembleRelease` for session-ID validation, activity recreation, result delivery and cancellation checks. Then install a signed app after publishing its association document:

```sh
adb shell pm verify-app-links --re-verify YOUR_APPLICATION_ID
adb shell pm get-app-links YOUR_APPLICATION_ID
```

Require the domain to show `verified`, then complete a real Session from a Custom Tab and check cancellation and the existing custom scheme. Explicitly targeting a component with `adb am start` does not prove domain verification. See [Android App Link setup](https://developer.android.com/training/app-links/add-applinks) and [verification](https://developer.android.com/training/app-links/verify-applinks).
