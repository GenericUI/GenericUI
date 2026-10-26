package com.nexustools.genericui.plugin

/**
 * DSL configuration block (`theme { ... }`) for configuring theme mode (`"dark"`, `"light"`, `"system"`)
 * and primary accent color (`"#0d6efd"`) at the root `genericui` level or inside any variant scope.
 */
open class GenericUiThemeConfig {
    var mode: String? = null
    var color: String? = null
}
