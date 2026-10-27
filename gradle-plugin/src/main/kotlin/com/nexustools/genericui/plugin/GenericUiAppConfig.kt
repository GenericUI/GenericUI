package com.nexustools.genericui.plugin

/**
 * DSL configuration block (`genericui { app { ... } }`) for declaring core application identity
 * and metadata across all target platforms.
 */
open class GenericUiAppConfig {
    /** Reverse-DNS application / bundle identifier (e.g. `"com.example.myapp"`). */
    var id: String = ""

    /** Human-readable application display name (e.g. `"My App"`). */
    var name: String = ""

    internal var versionExplicitlySet: Boolean = false

    /** Application version string (defaults to `"1.0.0"`). */
    var version: String = "1.0.0"
        set(value) {
            field = value
            versionExplicitlySet = true
        }

    /** Root Kotlin package name for generated bootstrap, activity, and resource classes. */
    var packageName: String = ""

    /** Summary description of the application. */
    var description: String = ""

    /** Organization / publisher / vendor name. */
    var vendor: String = ""

    /** Optional global stylesheet path (e.g. `"styles/main.scss"`) applied at application startup. */
    var styleSheet: String? = null

    /** Alias for [styleSheet]. */
    var globalStyleSheet: String?
        get() = styleSheet
        set(value) {
            styleSheet = value
        }
}
