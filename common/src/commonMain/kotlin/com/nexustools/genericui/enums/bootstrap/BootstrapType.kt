package com.nexustools.genericui.enums.bootstrap

/**
 * Primary Bootstrap 5 semantic contextual types used across Buttons, Alerts, Badges,
 * ProgressBars, ListGroups, Borders, Backgrounds, and Text components.
 */
enum class BootstrapType(val cssSuffix: String) {
    PRIMARY("primary"),
    SECONDARY("secondary"),
    SUCCESS("success"),
    DANGER("danger"),
    WARNING("warning"),
    INFO("info"),
    LIGHT("light"),
    DARK("dark"),
    LINK("link");

    companion object {
        fun fromCssSuffix(suffix: String): BootstrapType? =
            entries.firstOrNull { it.cssSuffix.equals(suffix.trim(), ignoreCase = true) }
    }
}
