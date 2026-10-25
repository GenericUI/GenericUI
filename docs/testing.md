# GenericUI Testing Suite Guide (`JestElectricRunner`)

`GenericUI` includes a first-party cross-platform UI testing framework—**`JestElectricRunner`**—located in the [`tests/` submodule](../tests/README.md). It combines a **Jest-style BDD syntax** (`describe`, `beforeEach`, `afterEach`, `beforeAll`, `afterAll`, `test`, `it`, `expect`) with **Robolectric-style UI controllers** (`RoboAppController`, `RoboFragmentController`, `RoboNodeInteraction`, `RoboComponentExpectation`).

---

## Navigation & Related Documentation

- **Root Repository**: [GenericUI README](../README.md) | [Master Documentation Index](README.md)
- **Tests Submodule**: [Tests Submodule README (`tests/README.md`)](../tests/README.md)
- **Dedicated Testing Guides**:
  - [JestElectricRunner BDD & Robolectric API Guide (`tests/docs/jest-electric-runner.md`)](../tests/docs/jest-electric-runner.md)
  - [Platform Test Runners & Live Visual Windows Testing (`tests/docs/platform-test-runners.md`)](../tests/docs/platform-test-runners.md)
- **SourceSets & Build**: [Hierarchical SourceSet Setup (`sourcesets.md`)](sourcesets.md) | [Gradle Plugin Guide (`gradle-plugin.md`)](gradle-plugin.md)

---

## 1. Submodule Architecture (`:tests`)

The `:tests` Project-of-Projects mirrors GenericUI's [SourceSet Hierarchy](sourcesets.md) and is wired automatically into `commonTest` and platform `*Test` source sets when `id("com.nexustools.genericui")` is applied:

| Module Coordinate | SourceSet Wiring | Description | Dedicated Guide |
| :--- | :--- | :--- | :--- |
| **`:tests:common`** | `commonTest` | Core `JestElectricRunner`, `JestSuiteScope`, `JestTestScope`, `JestExpectation`, `RoboAppController`, `RoboFragmentController`, `RoboNodeInteraction`, `RoboComponentExpectation`, `RoboComponentTreeFinder`. | [`tests/docs/jest-electric-runner.md`](../tests/docs/jest-electric-runner.md) |
| **`:tests:desktop:shared`** | `desktopNativeTest` (`nativeDesktopTest`) | `DesktopSharedRunnerNativeImpl` for native desktop test event pumping. | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:desktop:windows`** | `windowsTest` (`mingwX64Test`) | `WindowsVisualAppRunnerNativeImpl` — launches a **real onscreen Win32 window** during tests, updates a live visual test banner, and pumps real Win32 GDI/Direct2D repaints! | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:desktop:macos`** | `macTest` (`macosTest`) | `MacosJestElectricRunnerNativeImpl` for AppKit test execution. | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:desktop:gtk`** | `gtkTest` (`linuxTest`) | `GtkJestElectricRunnerNativeImpl` for GTK4 test execution. | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:desktop:qt`** | `qtTest` (`linuxTest`) | `QtJestElectricRunnerNativeImpl` for Qt6 test execution. | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:desktop:swing`** | `swingTest` (`jvmTest`) | `SwingJUnitRunnerNativeImpl` & `JestElectricSwingTestBase` (pumps Swing EDT `EventQueue`). | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:mobile:shared`** | `mobileTest` | `MobileSharedRunnerNativeImpl` for shared mobile lifecycle & gesture simulation. | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:mobile:android`** | `androidTest` (`androidUnitTest`) | `AndroidRobolectricRunnerNativeImpl` & `JestElectricRobolectricTestBase` (integrates with Robolectric main looper idling). | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:mobile:ios`** | `iosTest` | `IosJestElectricRunnerNativeImpl` for UIKit simulator tests. | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |
| **`:tests:web`** | `webTest` (`wasmJsTest`, `jsTest`) | `WebJestElectricRunnerNativeImpl` (compiled for both Kotlin/Wasm and Kotlin/JS IR). | [`tests/docs/platform-test-runners.md`](../tests/docs/platform-test-runners.md) |

---

## 2. Quick-Start Example (`src/commonTest/kotlin`)

```kotlin
package com.example.app

import com.nexustools.genericui.tests.JestElectricRunner
import kotlin.test.Test

class MainWorkspaceUiTest {

    @Test
    fun verifyWorkspaceNavigationAndActions() {
        JestElectricRunner.describe("MainWorkspaceFragment UI Suite") {
            test("renders heading, types into search field, and clicks primary action button") {
                val controller = launchApp { MyStudioApplication() }

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
    }
}
```
