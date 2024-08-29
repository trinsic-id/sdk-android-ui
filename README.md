# Trinsic Android Library

This repository holds the Trinsic Android UI Library, which can be used to invoke the Trinsic identity verification flow in a native Android app.

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

## Usage

Coming soon -- see `testbed` app for example usage