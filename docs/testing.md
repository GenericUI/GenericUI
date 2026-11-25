# GenericUI Testing Suite Guide (`:tests` & `JestElectricRunner`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Tests Module README (`../tests/README.md`)](../tests/README.md) · [`JestElectricRunner` API (`../tests/docs/jest-electric-runner.md`)](../tests/docs/jest-electric-runner.md) · [Platform Test Runners (`../tests/docs/platform-test-runners.md`)](../tests/docs/platform-test-runners.md) · [Hierarchical SourceSets (`sourcesets.md`)](./sourcesets.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md)

`GenericUI` includes a first-party cross-platform UI testing framework—**`JestElectricRunner`**—consolidated into the single multiplatform **`:tests`** module ([`tests/build.gradle.kts`](../tests/build.gradle.kts)). It combines **Jest-style BDD syntax** (`describe`, `beforeEach`, `afterEach`, `test`, `it`, `expect`, `runSuite`) with **Robolectric-style UI controllers** (`RoboAppController`, `RoboFragmentController`, `RoboNodeInteraction`, `RoboComponentExpectation`) across all 10 Kotlin targets and all 9 platform backends.

---

## 1. Consolidated `:tests` Module Architecture (10 Kotlin Targets, Zero Child Subprojects)

`:tests` is a single multiplatform KMP module whose internal source sets mirror GenericUI's [SourceSet Hierarchy](./sourcesets.md):

| `:tests` Source Set | Linked Core Module | Key Runner & Verification Classes |
| :--- | :--- | :--- |
| **`commonMain` / `commonTest`** | `:common` | [`JestElectricRunner`](../tests/src/commonMain/kotlin/com/nexustools/genericui/tests/JestElectricRunner.kt), [`JestSuiteScope`](../tests/src/commonMain/kotlin/com/nexustools/genericui/tests/JestSuiteScope.kt), `JestSuspendRunner`, `JestExpectation`, [`RoboAppController`](../tests/src/commonMain/kotlin/com/nexustools/genericui/tests/RoboAppController.kt), [`RoboFragmentController`](../tests/src/commonMain/kotlin/com/nexustools/genericui/tests/RoboFragmentController.kt), [`RoboNodeInteraction`](../tests/src/commonMain/kotlin/com/nexustools/genericui/tests/RoboNodeInteraction.kt), `RoboComponentTreeFinder`, and the 12 modular `JestElectric*RenderUtil` snapshot utilities (`JestElectricRenderContext`, `JestElectricButtonRenderUtil`, `JestElectricContainerChromeRenderUtil`, `JestElectricControlTintRenderUtil`, `JestElectricDataDisplayRenderUtil`, `JestElectricInputFieldRenderUtil`, `JestElectricMediaCanvasRenderUtil`, `JestElectricNavigationRenderUtil`, `JestElectricSliderProgressRenderUtil`, `JestElectricTextRenderUtil`, `JestElectricToggleSelectionRenderUtil`, `JestElectricWorkspaceWindowRenderUtil`). |
| **`systemMain`** | `:system` | Non-web Blink style/layout verification & shared system test helpers. |
| **`desktopMain` / `nativeDesktopMain`** | `:desktop` | [`DesktopSharedRunnerNativeImpl`](../tests/src/nativeDesktopMain/kotlin/com/nexustools/genericui/tests/desktop/DesktopSharedRunnerNativeImpl.kt) shared native desktop window & event pumping harness. |
| **`jvmAndAndroidMain`** | `:jvm` | Shared JVM & Android screenshot/filesystem host test helpers ([`JestScreenshotHostIo`](../tests/src/jvmAndAndroidMain/kotlin/com/nexustools/genericui/tests/JestScreenshotHostIo.kt)). |
| **`mingwMain` / `mingwX64Test`** | `:desktop` (`mingwMain`) | `WindowsVisualAppRunnerNativeImpl` — launches a **real onscreen Win32 window** (`PrintWindow(..., PW_RENDERFULLCONTENT)` retained `HWND` capture) when `testing { visualOnWindows = true }` (`windowsVisualTest`), or runs fast headless Win32/`ComCtl32 v6`/Direct2D verification (including `WindowsBackdropBlurTest`). |
| **`macosMain` / `macosArm64Test`** | `:desktop` + `:apple` | `MacosJestElectricRunnerNativeImpl` for AppKit `NSWindow` / `NSView` retained widget capture (`cacheDisplayInRect:toBitmapImageRep:`) & runtime squircle icon verification. |
| **`linuxMain` / `linuxTest`** | `:desktop` (`linuxMain`) | Co-hosts both **`GtkJestElectricRunnerNativeImpl`** (`GtkJestElectricBootstrap`, capturing retained GTK4 `GtkWidget*` surfaces via `GtkWidgetPaintable` + `GskRenderer`) and **`KdeJestElectricRunnerNativeImpl`** (`KdeJestElectricBootstrap`, capturing retained KDE `KF6` + `Breeze` `QWidget*` surfaces via `QWidget::render`) inside [`LinuxGtkAndKdeRunnerTest`](../tests/src/linuxTest/kotlin/com/nexustools/genericui/tests/linux/LinuxGtkAndKdeRunnerTest.kt). |
| **`jvmMain` / `jvmTest`** | `:desktop` (`jvmMain`) + `:jvm` | `SwingJUnitRunnerNativeImpl` & `JestElectricSwingTestBase` (pumps AWT/Swing EDT `EventQueue`). |
| **`mobileMain`** | `:mobile` | [`MobileSharedRunnerNativeImpl`](../tests/src/mobileMain/kotlin/com/nexustools/genericui/tests/mobile/MobileSharedRunnerNativeImpl.kt) shared posture & mobile lifecycle harness. |
| **`androidMain` / `androidHostTest`** | `:mobile` + `:jvm` | `AndroidRobolectricRunnerNativeImpl` & `JestElectricRobolectricTestBase`. |
| **`iosMain` / `iosTest`** | `:mobile` + `:apple` | Co-hosts both **`IosJestElectricRunnerNativeImpl`** (`BackendTarget.IOS`) and **`IpadosJestElectricRunnerNativeImpl`** (`BackendTarget.IPadOS` Stage Manager / `UIMenuSystem`, tested in `IosAndIpadosJestElectricRunnerTest`). |
| **`webMain` / `jsMain` / `wasmJsMain`** | `:web` | `WebJestElectricRunnerNativeImpl` & `WebTestHostBridge` compiled for both Kotlin/Wasm (`wasmJsTest`) and Kotlin/JS IR (`jsTest`). |

---

## 2. Headless `backdropFilter`, Thread Dispatching, & `ServiceWorker` Test Harnesses

1. **`JestElectricButtonRenderUtil` & Headless `backdropFilter` / `Composite` Snapshots**:
   - [`JestElectricButtonRenderUtil`](../tests/src/commonMain/kotlin/com/nexustools/genericui/test/jestelectric/render/JestElectricButtonRenderUtil.kt) and [`JestElectricContainerChromeRenderUtil`](../tests/src/commonMain/kotlin/com/nexustools/genericui/test/jestelectric/render/JestElectricContainerChromeRenderUtil.kt) resolve `CssFilter.backdropBlurOrFilter(styledSpec.backdropBlurPx, styledSpec.backdropFilter)` (all 10 ops) across `StyledComponent`, `StyledButton`, `StyledContainer`, and `Composite` components (`StyledSlider`, `SeekBar` with zero `SeekBarNativeImpl`, `ExpandingVolumeSlider`) onto the headless `:tests` snapshot `RGBA_8888` buffer via `FastBackdropBlurKernel` / `CssFilterColorMatrix`, while native desktop visual runners capture genuine retained OS widget hierarchies (`cacheDisplayInRect:toBitmapImageRep:`, `PrintWindow(..., PW_RENDERFULLCONTENT)`, `GtkWidgetPaintable` + `GskRenderer`, `QWidget::render`), evaluating consumer/test theme rules via `ThemeStyleSheetRegistry` and CSS `@keyframes` via `SystemCssAnimationController` (`Animator`) + `BrowserLayoutEngine` proximal `position: relative` / `Window` parity.
   - When `effBackdropFilter != null` and no opaque background/tint is explicitly configured on `StyledButton`, `fillBg` defaults to `null` (transparent glass) instead of falling back to opaque `ctx.bgElevated`. Verified by `JestElectricRunnerTest.testSnapshotRendererAppliesBackdropBlurToStyledContainerAndStyledButton` and hardware backend tests such as `WindowsBackdropBlurTest` (`desktop/src/mingwX64Test/.../WindowsBackdropBlurTest.kt`).
2. **`DefaultUiThreadDispatcherNativeImpl`, `JvmUiThreadDispatcherBase`, & Coroutines in Tests**:
   - [`DefaultUiThreadDispatcherNativeImpl`](../common/src/commonMain/kotlin/com/nexustools/genericui/threading/DefaultUiThreadDispatcherNativeImpl.kt) enforces dual-queue UI vs. Logic thread separation when `isSplitThreadingEnabled = true` (`dispatchToUi`, `dispatchToLogic`, `runOnUiBlocking`).
   - [`JvmUiThreadDispatcherBase`](../jvm/src/jvmSharedMain/kotlin/com/nexustools/genericui/jvm/JvmUiThreadDispatcherBase.kt) backs Swing and Android using `Executors.newSingleThreadExecutor()` for the Logic thread and synchronous `CountDownLatch` blocking for `runOnUiBlocking`, while falling back cleanly to inline synchronous execution inside headless unit/Robolectric test processes where no AWT `EventQueue` or Android Main `Looper` pump is running.
   - `JestSuspendRunner` adapts suspend tests to blocking dispatchers on JVM, Android, and Kotlin/Native, and completes synchronously/via promises on Web.
3. **`ServiceWorker` Test Harnesses Across Platforms**:
   - **Shared Lifecycle Isolation (`ServiceWorkerExtension.resetForTests()`)**: Resets registered worker state, options, and notification state cleanly between tests.
   - **Desktop Loopback & OS Helper Suite (`ServiceWorkerRuntimeAndIpcTest`)**: Exercises `DesktopServiceWorkerHost` bound to ephemeral `127.0.0.1:0` loopback TCP with 32-byte hex token auth + `DesktopServiceWorkerChannelClient` IPC, `DesktopOsHelpers.acquireSingleInstanceLock`, and `WindowsShortcutWriter` (`.lnk` generation) without needing persistent tray daemons.
   - **Android Robolectric Suite (`AndroidServiceWorkerRobolectricTest`)**: Runs under Robolectric SDK 35 driving `GeneratedAndroidServiceWorkerService` / `AndroidServiceWorkerController`, asserting `NotificationChannel` registration, `ServiceCompat.startForeground`, `setNotificationTitle`/`setNotificationText`/`setNotificationSubText` ongoing notification updates, default `"Stop"` action (`ServiceWorkerNotificationAction.DEFAULT_STOP_ACTION_ID`), `extendNotificationActions` / `replaceNotificationActions` `Intent` dispatch, and `AndroidServiceWorkerBinder` IPC.
   - **iOS & iPadOS Unsupported Suite (`AppleMobileServiceWorkerTest`)**: Verifies `ServiceWorkerExtension.isSupported == false`, `isAutostartSupported == false`, and zero `@MainServiceWorker` instantiation under UIKit.
   - **Web Node Bridge Suite (`WebServiceWorkerBridgeTest`) & Shared Suite (`ServiceWorkerCrossPlatformTest`)**: Verifies `WebServiceWorkerClientBridge` and `WebWorkerGlobalBridge` safely report `isServiceWorkerSupported == false` / `isWorkerGlobalScope == false` and degrade gracefully under headless Node.js test runs alongside cross-platform `ServiceWorker` contract tests.

---

## 3. Single-`commonTest` Consumer Workflow & Headed Linux XQuartz Setup

Consumer apps write UI tests once in `src/commonTest/kotlin`. When `testing { enabled = true }` is set in `build.gradle.kts`, the Gradle plugin automatically generates backend test runners and registers convenience tasks (`testSwing`, `testWindows`, `testMac`, `testLinux`, `testLinuxGtk`, `testLinuxKde`, `testAndroid`, `testIos`, `testIpados`, `testWeb`):

```kotlin
genericui {
    testing {
        enabled = true
        visualOnWindows = true
    }
}
```

### Running Linux GTK4 / KDE Tests from macOS OrbStack (XQuartz) or Offscreen Qt

```bash
export DISPLAY=host.orb.internal:0   # Or QT_QPA_PLATFORM=offscreen for headless KDE tests
./gradlew :tests:linuxX64Test
```

### Common Test Commands

```bash
./gradlew :common:jvmTest :jvm:jvmTest :tests:jvmTest :tests:wasmJsTest :tests:jsTest
./gradlew :apple:macosArm64Test :tests:macosArm64Test :tests:iosSimulatorArm64Test
./gradlew testSwing testWindows testMac testLinux testIos testIpados testWeb windowsVisualTest
```

---

## 4. Quick-Start Test Example (`src/commonTest/kotlin`)

```kotlin
package com.example.studio

import com.nexustools.genericui.tests.JestElectricRunner
import kotlin.test.Test

class MainWorkspaceUiTest {

    @Test
    fun verifyWorkspaceNavigationAndActions() {
        val result = JestElectricRunner.runSuite("MainWorkspaceFragment UI Suite") {
            test("renders heading, types into search field, and clicks primary action button") {
                val controller = launchFragment(MainWorkspaceFragment())

                controller.findByText("Welcome to Studio", substring = true)
                    .assertExists()
                    .assertIsVisible()

                controller.findById("search-input")
                    .assertExists()
                    .performTypeText("RadialGradient")

                controller.findById("apply-btn")
                    .assertIsEnabled()
                    .performClick()

                expect(controller.findById("status-badge").component).toBeNotNull()
                controller.destroy()
            }
        }
        result.assertAllPassed()
    }
}
```
