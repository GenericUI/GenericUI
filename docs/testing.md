# GenericUI Testing Suite Guide (`:tests` & `JestElectricRunner`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Tests Module README (`../tests/README.md`)](../tests/README.md) · [`JestElectricRunner` API (`../tests/docs/jest-electric-runner.md`)](../tests/docs/jest-electric-runner.md) · [Platform Test Runners (`../tests/docs/platform-test-runners.md`)](../tests/docs/platform-test-runners.md) · [Hierarchical SourceSets (`sourcesets.md`)](./sourcesets.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md)

`GenericUI` includes a first-party cross-platform UI testing framework—**`JestElectricRunner`**—consolidated into the single multiplatform **`:tests`** module ([`tests/build.gradle.kts`](../tests/build.gradle.kts)). It combines **Jest-style BDD syntax** (`describe`, `beforeEach`, `afterEach`, `test`, `it`, `expect`, `runSuite`) with **Robolectric-style UI controllers** (`RoboAppController`, `RoboFragmentController`, `RoboNodeInteraction`, `RoboComponentExpectation`) across all 10 Kotlin targets and all 9 platform backends.

---

## 1. Consolidated `:tests` Module Architecture (10 Kotlin Targets, Zero Child Subprojects)

In the 45-project architecture, `:tests` is a single multiplatform KMP module whose internal source sets mirror GenericUI's [SourceSet Hierarchy](./sourcesets.md):

| `:tests` Source Set | Linked Core Module | Key Runner & Verification Classes |
| :--- | :--- | :--- |
| **`commonMain` / `commonTest`** | `:common` | [`JestElectricRunner`](../tests/src/commonMain/kotlin/com/nexustools/genericui/tests/JestElectricRunner.kt), `JestSuiteScope`, `JestExpectation`, `RoboAppController`, `RoboFragmentController`, `RoboNodeInteraction`, `RoboComponentTreeFinder`, and the 12 modular `JestElectric*RenderUtil` snapshot utilities (`JestElectricRenderContext`, `JestElectricButtonRenderUtil`, `JestElectricContainerChromeRenderUtil`, `JestElectricControlTintRenderUtil`, `JestElectricDataDisplayRenderUtil`, `JestElectricInputFieldRenderUtil`, `JestElectricMediaCanvasRenderUtil`, `JestElectricNavigationRenderUtil`, `JestElectricSliderProgressRenderUtil`, `JestElectricTextRenderUtil`, `JestElectricToggleSelectionRenderUtil`, `JestElectricWorkspaceWindowRenderUtil`). |
| **`systemMain`** | `:system` | Non-web Blink style/layout verification & shared system test helpers. |
| **`desktopMain` / `nativeDesktopMain`** | `:desktop` | [`DesktopSharedRunnerNativeImpl`](../tests/src/nativeDesktopMain/kotlin/com/nexustools/genericui/tests/desktop/DesktopSharedRunnerNativeImpl.kt) shared native desktop window & event pumping harness. |
| **`jvmAndAndroidMain`** | `:jvm` | Shared JVM & Android screenshot/filesystem host test helpers ([`JestScreenshotHostIo`](../tests/src/jvmAndAndroidMain/kotlin/com/nexustools/genericui/tests/JestScreenshotHostIo.kt)). |
| **`mingwMain` / `mingwX64Test`** | `:desktop` (`mingwMain`) | `WindowsVisualAppRunnerNativeImpl` — launches a **real onscreen Win32 window** when `testing { visualOnWindows = true }` (`windowsVisualTest`), or runs fast headless Win32/UxTheme/Direct2D verification by default. |
| **`macosMain` / `macosArm64Test`** | `:desktop` + `:apple` | `MacosJestElectricRunnerNativeImpl` for AppKit `NSWindow` / `NSView` & runtime squircle icon verification. |
| **`linuxMain` / `linuxTest`** | `:desktop` (`linuxMain`) | Co-hosts both **`GtkJestElectricRunnerNativeImpl`** (GTK4 / Libadwaita) and **`QtJestElectricRunnerNativeImpl`** (Qt6 `QMainWindow`) cleanly inside a single binary (`LinuxGtkAndQtRunnerTest`). |
| **`jvmMain` / `jvmTest`** | `:desktop` (`jvmMain`) + `:jvm` | `SwingJUnitRunnerNativeImpl` & `JestElectricSwingTestBase` (pumps AWT/Swing EDT `EventQueue`). |
| **`mobileMain`** | `:mobile` | [`MobileSharedRunnerNativeImpl`](../tests/src/mobileMain/kotlin/com/nexustools/genericui/tests/mobile/MobileSharedRunnerNativeImpl.kt) shared posture & mobile lifecycle harness. |
| **`androidMain` / `androidHostTest`** | `:mobile` + `:jvm` | `AndroidRobolectricRunnerNativeImpl` & `JestElectricRobolectricTestBase`. |
| **`iosMain` / `iosTest`** | `:mobile` + `:apple` | Co-hosts both **`IosJestElectricRunnerNativeImpl`** (`BackendTarget.IOS`) and **`IpadosJestElectricRunnerNativeImpl`** (`BackendTarget.IPadOS` Stage Manager / `UIMenuSystem`, tested in `IosAndIpadosJestElectricRunnerTest`). |
| **`webMain` / `jsMain` / `wasmJsMain`** | `:web` | `WebJestElectricRunnerNativeImpl` & `WebTestHostBridge` compiled for both Kotlin/Wasm (`wasmJsTest`) and Kotlin/JS IR (`jsTest`). |

---

## 2. Single-`commonTest` Consumer Workflow & Headed Linux XQuartz Setup

Consumer apps write UI tests once in `src/commonTest/kotlin`. When `testing { enabled = true }` is set in `build.gradle.kts`, the Gradle plugin automatically generates backend test runners and registers convenience tasks (`testSwing`, `testWindows`, `testMac`, `testLinux`, `testLinuxGtk`, `testLinuxQt`, `testAndroid`, `testIos`, `testIpados`, `testWeb`):

```kotlin
genericui {
    testing {
        enabled = true
        visualOnWindows = true
    }
}
```

### Running Linux GTK4 / Qt6 Tests from macOS OrbStack (XQuartz)

Linux containers running under **OrbStack on macOS** render live GTK4 and Qt6 windows directly on the macOS desktop via **XQuartz** (rather than a headless framebuffer):

```bash
# In macOS terminal: ensure XQuartz is running with network connections allowed ("Allow connections from network clients")
# Inside OrbStack Linux container/VM (or via `orb`):
export DISPLAY=host.orb.internal:0
./gradlew :tests:linuxX64Test
```

### Common Test Commands (`GenericUI` Root Repo & Example Projects)

```bash
# Common, JVM Swing, Android host, Apple Darwin, Web, and consolidated :tests suites:
./gradlew :common:jvmTest :jvm:jvmTest :tests:jvmTest :tests:wasmJsTest :tests:jsTest
./gradlew :apple:macosArm64Test :tests:macosArm64Test :tests:iosSimulatorArm64Test

# Consumer example project convenience test tasks:
./gradlew testSwing testWindows testMac testLinux testIos testIpados testWeb windowsVisualTest
```

---

## 3. Quick-Start Test Example (`src/commonTest/kotlin`)

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
