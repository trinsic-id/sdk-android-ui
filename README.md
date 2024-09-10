# Trinsic Android Library

This repository holds the Trinsic Android UI Library, which can be used to invoke the Trinsic identity verification flow in a native Android app.

## Requirements

This library specifies a minimum Android SDK version of `28`.

This requirement is due to [a security vulnerability in Android](https://developer.android.com/privacy-and-security/risks/strandhogg) which affects older SDK versions.
A mitigation exists for apps which support a lower minimum SDK version, but said mitigation (setting your activity's `taskAffinity` to an empty string) breaks the functionality of this library.

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

If your app's manifest specifies the `android:taskAffinity` property on any activity which will invoke this library, you must make some adjustments.

#### Custom Task Affinity

If your activity specifies a custom task affinity (which is _not_ an empty string), simply make the following changes:

**1. Change the `CallbackActivity` registration**

Modify the snippet above (which you pasted into your `AndroidManifest.xml`) to specify the _same_ `android:taskAffinity` property as you specify on your own app's activity.

**2. Add a registration for `InvokeActivity`**

Normally, your app does not need to add a manifest entry for `InvokeActivity` (which is provided by this library); however, to align task affinities, you will need to do so.

Simply paste the following snippet next to where you pasted the above snippet, replacing `[your.custom.affinity]` with the custom affinity you specified on your own app's activity.

```xml
<activity
    android:name="id.trinsic.android.ui.InvokeActivity"
    android:taskAffinity="whatever.custom" />
```

#### Empty Task Affinity

If you specify a custom task affinity of `""` (an empty string), this library cannot function.

This is because cross-task activity communication is not possible in the way this library requires, and an empty task affinity will cause one or more of the library's vital activities to launch in a separate task from your original activity.