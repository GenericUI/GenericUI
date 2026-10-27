package com.nexustools.genericui.plugin

/**
 * DSL configuration block (`theme { ... }`) for configuring theme mode (`"auto"`, `"dark"`, `"light"`)
 * and primary accent color (`"#0d6efd"`) at the root `genericui` level or inside any variant scope.
 */
open class GenericUiThemeConfig {
    internal var modeExplicitlySet: Boolean = false

    /** Theme mode (`"auto"`, `"dark"`, `"light"`), defaulting to `"auto"`. */
    var mode: String = "auto"
        set(value) {
            field = value
            modeExplicitlySet = true
        }

    var color: String? = null

    /** Optional global stylesheet path (e.g. `"styles/main.scss"`) applied for this scope. */
    var styleSheet: String? = null

    /** Alias for [styleSheet]. */
    var globalStyleSheet: String?
        get() = styleSheet
        set(value) {
            styleSheet = value
        }
}
