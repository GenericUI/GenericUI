package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 component sizing variants (e.g., `btn-sm`, `btn-lg`, `form-control-sm`, `modal-xl`).
 */
enum class BootstrapSize(val cssSuffix: String) {
    EXTRA_SMALL("xs"),
    SMALL("sm"),
    MEDIUM("md"),
    LARGE("lg"),
    EXTRA_LARGE("xl"),
    EXTRA_EXTRA_LARGE("xxl");

    companion object {
        val XS: BootstrapSize = EXTRA_SMALL
        val SM: BootstrapSize = SMALL
        val MD: BootstrapSize = MEDIUM
        val LG: BootstrapSize = LARGE
        val XL: BootstrapSize = EXTRA_LARGE
        val XXL: BootstrapSize = EXTRA_EXTRA_LARGE

        fun fromCssSuffix(suffix: String): BootstrapSize? =
            entries.firstOrNull { it.cssSuffix.equals(suffix.trim(), ignoreCase = true) }
    }
}
