package com.nexustools.genericui.enums.bootstrap

/**
 * Primary Bootstrap 5 semantic contextual types used across Buttons, Alerts, Badges,
 * ProgressBars, ListGroups, Borders, Backgrounds, and Text components.
 *
 * [NONE] ([None]) is the default on components and indicates that native system backends
 * should render the genuine OS system control style without semantic tinting, while semantic
 * types ([PRIMARY], [DANGER], [SUCCESS], etc.) tint native controls on non-web backends
 * and apply Bootstrap classes on `:web` / `:extensions:alien`.
 */
enum class BootstrapType(val cssSuffix: String) {
    NONE(""),
    PRIMARY("primary"),
    SECONDARY("secondary"),
    SUCCESS("success"),
    DANGER("danger"),
    WARNING("warning"),
    INFO("info"),
    LIGHT("light"),
    DARK("dark"),
    LINK("link");

    val isNone: Boolean
        get() = this == NONE

    val isSemantic: Boolean
        get() = this != NONE

    companion object {
        val None: BootstrapType
            get() = NONE

        fun fromCssSuffix(suffix: String): BootstrapType? {
            val trimmed = suffix.trim()
            if (trimmed.isEmpty()) return NONE
            return entries.firstOrNull { it != NONE && it.cssSuffix.equals(trimmed, ignoreCase = true) }
        }
    }
}
