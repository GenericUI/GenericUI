# GenericUI Testing Suite Guide (`JestElectricRunner`)

`GenericUI` includes a first-party cross-platform UI testing framework—**`JestElectricRunner`**—located in the [`tests/` submodule](../tests/README.md). It combines a **Jest-style BDD syntax** (`describe`, `beforeEach`, `afterEach`, `beforeAll`, `afterAll`, `test`, `it`, `expect`, `runSuite`) with **Robolectric-style UI controllers** (`RoboAppController`, `RoboFragmentController`, `RoboNodeInteraction`, `RoboComponentExpectation`).

---

## Navigation & Related Documentation

- **Root Repository**: [GenericUI README](../README.md) | [Master Documentation Index](README.md)
- **Tests Submodule**: [Tests Submodule README (`tests/README.md`)](../tests/README.md)
- **Dedicated Testing Guides**:
  - [JestElectricRunner BDD & Robolectric API Guide (`tests/docs/jest-electric-runner.md`)](../tests/docs/jest-electric-runner.md)
  - [Platform Test Runners & Live Visual Windows Testing (`tests/docs/platform-test-runners.md`)](../tests/docs/platform-test-runners.md)
- **SourceSets, Fragments, & Build**: [Hierarchical SourceSet Setup (`sourcesets.md`)](sourcesets.md) | [Fragments & Windows Guide (`fragments-and-windows.md`)](fragments-and-windows.md) | [Gradle Plugin Guide (`gradle-plugin.md`)](gradle-plugin.md)

---

## 1. Submodule Architecture (`:tests`)

The `:tests` Project-of-Projects mirrors GenericUI's [SourceSet Hierarchy](sourcesets.md) and is wired automatically into `commonTest` and platform `*Test` source sets when `id("com.nexustools.genericui")` is applied with `testing { enabled = true }`:

| Module Coordinate | SourceSet Wiring | Description | Dedicated Guide |
| :--- | :--- | :--- | :--- |
| **`:tests:common`** | `commonTest` | Core `JestElectricRunner`, `JestSuiteScope`, `JestTestScope`, `JestExpectation`, `RoboAppController`, `RoboFragmentController`, `RoboNodeInteraction`, `RoboComponentExpectation`, `RoboComponentTreeFinder`. | [`tests/docs/jest-electric-runner.md`](../tests/docs/jest-electric-runner.md) |
| **`:tests:desktop:nativeCommon`** | `desktopNativeTest` (`nativeDesktopTest`) | Shared native desktop test event pumping and window lifecycle harness. | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:desktop:windows`** | `windowsTest` (`mingwX64Test`) | `WindowsVisualAppRunnerNativeImpl` — launches a **real onscreen Win32 window** during tests (`visualOnWindows = true`), updates a live visual test banner, and pumps real Win32 GDI/Direct2D repaints! | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:desktop:macos`** | `macTest` (`macosTest`) | `MacosJestElectricRunnerNativeImpl` for AppKit test execution. | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:desktop:gtk`** | `gtkTest` (`linuxTest`) | `GtkJestElectricRunnerNativeImpl` for GTK4 test execution. | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:desktop:qt`** | `qtTest` (`linuxTest`) | `QtJestElectricRunnerNativeImpl` for Qt6 test execution. | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:desktop:swing`** | `swingTest` (`jvmTest`) | `SwingJUnitRunnerNativeImpl` & `JestElectricSwingTestBase` (pumps Swing EDT `EventQueue`). | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:mobile:common`** | `mobileTest` | Shared mobile lifecycle, posture, and gesture simulation harness. | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:mobile:android`** | `androidTest` (`androidUnitTest`) | `AndroidRobolectricRunnerNativeImpl` & `JestElectricRobolectricTestBase` (integrates with Robolectric main looper idling). | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:mobile:ios`** | `iosTest` | `IosJestElectricRunnerNativeImpl` for UIKit simulator tests. | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:web`** | `webTest` (`wasmJsTest`, `jsTest`) | `WebJestElectricRunnerNativeImpl` (compiled for both Kotlin/Wasm and Kotlin/JS IR). | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |

---

## 2. Single-`commonTest` Workflow & Generated Visual Test Harnesses

Consumer applications write UI tests once in `src/commonTest/kotlin`. When `testing { enabled = true; visualOnWindows = true }` is configured in `genericui { ... }`, the Gradle plugin automatically registers platform test runners and wires the native/JVM/Web test harnesses at build time without requiring hand-written platform test boilerplate:

```kotlin
genericui {
    testing {
        enabled = true
        visualOnWindows = true
    }
}
```

---

## 3. Quick-Start Example (`src/commonTest/kotlin`)

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
