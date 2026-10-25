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
}
