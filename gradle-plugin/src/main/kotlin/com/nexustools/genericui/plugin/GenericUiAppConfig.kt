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

    /** Application version string (defaults to latest git tag when available, else `project.version` / `"1.0.0"`). */
    var version: String = "1.0.0"
        set(value) {
            field = value
            versionExplicitlySet = true
        }

    /** Optional build version string (overrides automatic git commit hash for `CFBundleVersion` / build metadata). */
    var buildVersion: String = ""

    /** When `true` (default) and [version] is not explicitly set, defaults app version to the latest git tag. */
    var useGitTagVersion: Boolean = true

    /** When `true` (default), automatically includes the short git commit hash on supported backends. */
    var includeGitHash: Boolean = true

    /** Root Kotlin package name for generated bootstrap, activity, and resource classes. */
    var packageName: String = ""

    /** Summary description of the application. */
    var description: String = ""

    /** Organization / publisher / vendor name. */
    var vendor: String = ""

    /** Copyright notice string (e.g. `"Copyright © Katelyn Slater"`). */
    var copyright: String = ""

    /** Optional global stylesheet path (e.g. `"styles/main.scss"`) applied at application startup. */
    var styleSheet: String? = null

    /** Alias for [styleSheet]. */
    var globalStyleSheet: String?
        get() = styleSheet
        set(value) {
            styleSheet = value
        }
}
