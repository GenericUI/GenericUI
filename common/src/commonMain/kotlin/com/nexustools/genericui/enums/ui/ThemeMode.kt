package com.nexustools.genericui.enums.ui

/**
 * Application and Window theme modes (`AUTO`, `DARK`, `LIGHT`).
 *
 * Defaults to [AUTO], which automatically detects and follows the host operating system's
 * or browser's active dark/light color scheme across all 8 GenericUI targets.
 */
enum class ThemeMode(val bsThemeValue: String) {
    AUTO("auto"),
    DARK("dark"),
    LIGHT("light");

    val isAuto: Boolean
        get() = this == AUTO

    val isDark: Boolean
        get() = this == DARK

    val isLight: Boolean
        get() = this == LIGHT

    companion object {
        /** Alias for [AUTO] preserved for source compatibility. */
        val SYSTEM: ThemeMode
            get() = AUTO

        fun fromString(raw: String?): ThemeMode {
            return when (raw?.trim()?.lowercase()) {
                "dark" -> DARK
                "light" -> LIGHT
                "auto", "system", null, "" -> AUTO
                else -> AUTO
            }
        }
    }
}
