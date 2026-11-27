# NetShift

Native Android network quality tester with handoff tracking for CS 312 Mobile Computing.

**Current milestone: measurement and verdict implementation (0.1.0).** The Test screen is connected to the measurement engine, local history, and activity verdicts. Real-device acceptance is still in progress; see [PLAN.md](PLAN.md) for the remaining screen, comparison, and handoff work.

## Available in this build

- Kotlin/Jetpack Compose app with Live, Test, History, and Handoffs tabs, plus Results and Compare routes.
- Real default-network updates: Wi-Fi, cellular, VPN, other, or offline; assigned IPv4/IPv6 addresses; metering, validation, and captive-portal status.
- Room schema v1, entities, DAOs, and repositories for test results and handoff events. No example measurements are inserted into user history.
- Pure, tested calculations for median HTTP latency, adjacent-slot jitter, probe-loss proxy, and decimal Mbps.
- Test screen with Wi-Fi/mobile selection, location validation, mobile/metered data confirmation, live progress, cancellation, readable failure messages, and saved-result summaries.
- Per-activity Good/Fair/Poor/Unknown verdicts, with incomplete or low-confidence evidence shown as Unknown.
- Pinned Gradle wrapper, version catalog, Android CI, unit tests, Room instrumented tests, and a navigation smoke test.

The measurement can use up to 18 MB of payload per run, plus network overhead. Cellular or metered runs require confirmation. Background tracking and the connected comparison/history charts are still planned work. Device-specific mobile data behavior and real-network acceptance have not been verified by the automated checks.

## Requirements

| Component | Version |
| --- | --- |
| JDK / JVM target | 17 |
| Gradle wrapper | 8.11.1, distribution checksum verified |
| Android Gradle Plugin | 8.9.2 |
| Kotlin / Compose compiler plugin | 2.1.20 |
| KSP | 2.1.20-1.0.32, KSP2 |
| Android SDK | compile/target 35, Build Tools 35.0.0 |
| Minimum Android | Android 8.0 / API 26 |

Use an Android Studio version compatible with this toolchain. Newer Studio installations may bundle Java 24/25; select **JDK 17** for Gradle rather than relying on that bundled runtime.

## Open and build

1. Clone this repository and open its root folder in Android Studio.
2. Install Android SDK Platform 35 and Build Tools 35.0.0. Configure Gradle's JDK as 17.
3. Allow Android Studio to create `local.properties` for the local SDK. That file, IDE files, credentials, and generated build output are ignored by Git.
4. Sync Gradle and run the `app` configuration on an API 26+ device/emulator.

Windows PowerShell, with `JAVA_HOME` pointing at JDK 17:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

macOS/Linux:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

With a connected device/emulator, run the Room and navigation integration checks:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

CI builds the app, runs JVM tests and lint, and compiles the instrumented test APK. CI does not claim to run physical-network or device tests.

## Current verification

The current source was built with JDK 17 and SDK 35. Unit tests and Android lint passed, and both the debug APK and instrumented test APK compiled successfully. These checks do not replace a real-device test; Wi-Fi and mobile-network behavior still needs to be checked on the phone.

## Structure

The single `:app` module has four logical layers under `edu.cs312.netshift`:

- `ui/`: Compose screens, navigation, theme, and ViewModel state.
- `measurement/`: pure metric calculations; the network engine is the next step.
- `data/`: Room entities/DAOs and local repositories; exported schemas live in `app/schemas/`.
- `platform/`: lifecycle-owned Android connectivity callbacks.

`model/` contains the shared network/probe contracts and the versioned measurement limits. `AppContainer` performs manual dependency injection. No Hilt, backend, account, analytics, map SDK, or chart-library dependency is needed.

Only INTERNET and ACCESS_NETWORK_STATE are currently declared. Optional detail permissions and the tracking-service declaration will be added with their actual implementations. Local database backup/transfer is excluded; result data stays on the phone.

## Next steps

1. Run the Test screen on the Android phone with Wi-Fi: start a labeled test, observe progress and completion, then verify the result appears in History.
2. When mobile data is available, verify transport acquisition, the confirmation prompt, and the saved result on mobile data.
3. Finish the planned History/Compare UI and handoff tracking/service work described in [PLAN.md](PLAN.md).
4. Evaluate real devices and the four success targets before considering optional upload or maps.

HTTP probe loss is a proxy for failed requests, not raw packet loss. The test uses 20 probes and a capped 18 MB download in sequential requests under a 29-second engine deadline. See [PLAN.md](PLAN.md) for algorithms, permissions, ownership, schedule, risks, and evaluation criteria.
