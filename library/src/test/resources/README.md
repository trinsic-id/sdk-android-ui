# Native challenge version fixtures

`native-challenge-versions.json` contains the same 19 selection scenarios as
Swift's `Tests/TrinsicUITests/Fixtures/native-challenge-versions.json`, adapted
to `google-wallet` with an OpenID4VP request. The valid representations preserve
the same exchange, nonce, claims, age threshold and retention intent. Scenario
names and selection/error expectations must stay in sync across the SDKs.

`NativeChallengeVersionTest` runs in the normal library unit test target. It checks
the production parser against all fixtures with the SDK's actual v1-only support,
and the production version selector with each fixture's hypothetical supported
versions. The shared fixtures retain their original expectations for cross-SDK
comparison. Both tests explicitly expect legacy requests (including those with
only non-version keys) to fail with `unsupported_challenge_version` on Android.
The parser test also accounts for the SDK supporting only v1 rather than v2/v10.

Run `./gradlew :library:testDebugUnitTest --tests '*NativeChallengeVersionTest'`.
