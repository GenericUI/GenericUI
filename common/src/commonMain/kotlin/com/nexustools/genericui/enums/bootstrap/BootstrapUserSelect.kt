package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 user-select utilities (`user-select-all`, `user-select-auto`, `user-select-none`).
 */
enum class BootstrapUserSelect(val cssClass: String) {
    ALL("user-select-all"),
    AUTO("user-select-auto"),
    NONE("user-select-none");

    companion object {
        fun fromCssClass(cssClass: String): BootstrapUserSelect? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
