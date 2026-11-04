package com.nexustools.genericui.plugin

/**
 * Configuration block (`genericUi { testing { ... } }`) for the **`JestElectricRunner`**
 * cross-platform UI testing suite in GenericUI.
 */
open class GenericUiTestingConfig {

    /**
     * When `true` (default), automatically wires `:tests:common` and the matching platform
     * `:tests:*` modules into the project's test source sets.
     */
    var enabled: Boolean = true

    /**
     * When `true` (default), running `windowsVisualTest` on `:desktop:windows` launches the real
     * Win32 native window on screen so developers can watch the automated UI tests execute live.
     */
    var visualOnWindows: Boolean = true

    /**
     * Delay in milliseconds pumped between visual UI interactions on `:desktop:windows`.
     */
    var visualStepDelayMs: Long = 24L

    /**
     * Optional custom entry-point function or binary task name for `windowsVisualTest`.
     */
    var windowsVisualTestTaskName: String = "mingwX64Test"

    /**
     * Maximum duration in seconds allowed for any Gradle test task (`jvmTest`, `jsTest`, `jsNodeTest`,
     * `jsBrowserTest`, `wasmJsTest`, `mingwX64Test`, `test`, etc.) before it times out instead of hanging.
     */
    var testTimeoutSeconds: Long = 120L

    /**
     * Maximum duration in milliseconds allowed for an individual test case in `JestElectricRunner`
     * and JS/Wasm Mocha/Karma test runners.
     */
    var perTestTimeoutMs: Long = 15_000L
}
