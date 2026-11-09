package com.nexustools.genericui.plugin

import org.gradle.api.Action
import org.gradle.kotlin.dsl.GradleDsl

/**
 * DSL configuration block (`theme { ... }`) for configuring theme mode (`"auto"`, `"dark"`, `"light"`),
 * Bootstrap 5 / Material Design semantic colors (`primaryColor`, `secondaryColor`, `accentColor`,
 * `successColor`, `dangerColor`, `warningColor`, `infoColor`, `lightColor`, `darkColor`,
 * `backgroundColor`, `surfaceColor`, `textPrimaryColor`, `textSecondaryColor`, `borderColor`),
 * and per-mode `light { ... }` / `dark { ... }` overrides at the root `genericui` level or inside any variant scope.
 */
@GradleDsl
open class GenericUiThemeConfig {
    internal var modeExplicitlySet: Boolean = false
    internal var primaryColorExplicitlySet: Boolean = false
    internal var secondaryColorExplicitlySet: Boolean = false
    internal var accentColorExplicitlySet: Boolean = false
    internal var successColorExplicitlySet: Boolean = false
    internal var dangerColorExplicitlySet: Boolean = false
    internal var warningColorExplicitlySet: Boolean = false
    internal var infoColorExplicitlySet: Boolean = false
    internal var lightColorExplicitlySet: Boolean = false
    internal var darkColorExplicitlySet: Boolean = false

    /** Theme mode (`"auto"`, `"dark"`, `"light"`), defaulting to `"auto"`. */
    var mode: String = "auto"
        set(value) {
            field = value
            modeExplicitlySet = true
        }

    var primaryColor: String = DEFAULT_PRIMARY_COLOR
        set(value) {
            field = value
            primaryColorExplicitlySet = true
            if (!accentColorExplicitlySet) {
                accentColorBacking = value
            }
            if (colorBacking == null) {
                colorBacking = value
            }
        }

    var secondaryColor: String = DEFAULT_SECONDARY_COLOR
        set(value) {
            field = value
            secondaryColorExplicitlySet = true
        }

    private var accentColorBacking: String = DEFAULT_ACCENT_COLOR

    var accentColor: String
        get() = accentColorBacking
        set(value) {
            accentColorBacking = value
            accentColorExplicitlySet = true
        }

    var successColor: String = DEFAULT_SUCCESS_COLOR
        set(value) {
            field = value
            successColorExplicitlySet = true
        }

    var dangerColor: String = DEFAULT_DANGER_COLOR
        set(value) {
            field = value
            dangerColorExplicitlySet = true
        }

    var warningColor: String = DEFAULT_WARNING_COLOR
        set(value) {
            field = value
            warningColorExplicitlySet = true
        }

    var infoColor: String = DEFAULT_INFO_COLOR
        set(value) {
            field = value
            infoColorExplicitlySet = true
        }

    var lightColor: String = DEFAULT_LIGHT_COLOR
        set(value) {
            field = value
            lightColorExplicitlySet = true
        }

    var darkColor: String = DEFAULT_DARK_COLOR
        set(value) {
            field = value
            darkColorExplicitlySet = true
        }

    var backgroundColor: String? = null
    var surfaceColor: String? = null
    var textPrimaryColor: String? = null
    var textSecondaryColor: String? = null
    var borderColor: String? = null

    private var colorBacking: String? = null

    /** Alias for [primaryColor] (`color = "#..."`). */
    var color: String?
        get() = colorBacking ?: if (primaryColorExplicitlySet) primaryColor else null
        set(value) {
            colorBacking = value
            if (!value.isNullOrBlank()) {
                primaryColor = value.trim()
            }
        }

    val light: GenericUiThemeModeColorsConfig = GenericUiThemeModeColorsConfig()
    val dark: GenericUiThemeModeColorsConfig = GenericUiThemeModeColorsConfig()

    fun light(action: Action<GenericUiThemeModeColorsConfig>) {
        action.execute(light)
    }

    fun dark(action: Action<GenericUiThemeModeColorsConfig>) {
        action.execute(dark)
    }

    /** Optional global stylesheet path (e.g. `"styles/main.scss"`) applied for this scope. */
    var styleSheet: String? = null

    /** Alias for [styleSheet]. */
    var globalStyleSheet: String?
        get() = styleSheet
        set(value) {
            styleSheet = value
        }

    /**
     * Returns `true` if any custom theme color or mode-specific color override was configured.
     */
    fun hasCustomThemeOverrides(): Boolean =
        primaryColorExplicitlySet ||
            secondaryColorExplicitlySet ||
            accentColorExplicitlySet ||
            successColorExplicitlySet ||
            dangerColorExplicitlySet ||
            warningColorExplicitlySet ||
            infoColorExplicitlySet ||
            lightColorExplicitlySet ||
            darkColorExplicitlySet ||
            primaryColor != DEFAULT_PRIMARY_COLOR ||
            secondaryColor != DEFAULT_SECONDARY_COLOR ||
            accentColor != DEFAULT_ACCENT_COLOR ||
            successColor != DEFAULT_SUCCESS_COLOR ||
            dangerColor != DEFAULT_DANGER_COLOR ||
            warningColor != DEFAULT_WARNING_COLOR ||
            infoColor != DEFAULT_INFO_COLOR ||
            lightColor != DEFAULT_LIGHT_COLOR ||
            darkColor != DEFAULT_DARK_COLOR ||
            !backgroundColor.isNullOrBlank() ||
            !surfaceColor.isNullOrBlank() ||
            !textPrimaryColor.isNullOrBlank() ||
            !textSecondaryColor.isNullOrBlank() ||
            !borderColor.isNullOrBlank() ||
            !colorBacking.isNullOrBlank() ||
            light.hasCustomOverrides() ||
            dark.hasCustomOverrides()

    /**
     * Resolves the effective light-mode color map (`primary`, `secondary`, `accent`, `success`,
     * `danger`, `warning`, `info`, `light`, `dark`, `background`, `surface`, `textPrimary`,
     * `textSecondary`, `border`), falling back from `light.*` to top-level properties and
     * standard Bootstrap 5 light defaults.
     */
    fun resolveLightColors(): Map<String, String> {
        val effPrimary = nonBlankOrNull(light.primaryColor) ?: nonBlankOrNull(primaryColor) ?: DEFAULT_PRIMARY_COLOR
        val effAccent = nonBlankOrNull(light.accentColor)
            ?: (if (accentColorExplicitlySet) nonBlankOrNull(accentColor) else null)
            ?: effPrimary
        return linkedMapOf(
            KEY_PRIMARY to effPrimary,
            KEY_SECONDARY to (nonBlankOrNull(light.secondaryColor) ?: nonBlankOrNull(secondaryColor) ?: DEFAULT_SECONDARY_COLOR),
            KEY_ACCENT to effAccent,
            KEY_SUCCESS to (nonBlankOrNull(light.successColor) ?: nonBlankOrNull(successColor) ?: DEFAULT_SUCCESS_COLOR),
            KEY_DANGER to (nonBlankOrNull(light.dangerColor) ?: nonBlankOrNull(dangerColor) ?: DEFAULT_DANGER_COLOR),
            KEY_WARNING to (nonBlankOrNull(light.warningColor) ?: nonBlankOrNull(warningColor) ?: DEFAULT_WARNING_COLOR),
            KEY_INFO to (nonBlankOrNull(light.infoColor) ?: nonBlankOrNull(infoColor) ?: DEFAULT_INFO_COLOR),
            KEY_LIGHT to (nonBlankOrNull(light.lightColor) ?: nonBlankOrNull(lightColor) ?: DEFAULT_LIGHT_COLOR),
            KEY_DARK to (nonBlankOrNull(light.darkColor) ?: nonBlankOrNull(darkColor) ?: DEFAULT_DARK_COLOR),
            KEY_BACKGROUND to (nonBlankOrNull(light.backgroundColor) ?: nonBlankOrNull(backgroundColor) ?: DEFAULT_LIGHT_BACKGROUND),
            KEY_SURFACE to (nonBlankOrNull(light.surfaceColor) ?: nonBlankOrNull(surfaceColor) ?: DEFAULT_LIGHT_SURFACE),
            KEY_TEXT_PRIMARY to (nonBlankOrNull(light.textPrimaryColor) ?: nonBlankOrNull(textPrimaryColor) ?: DEFAULT_LIGHT_TEXT_PRIMARY),
            KEY_TEXT_SECONDARY to (nonBlankOrNull(light.textSecondaryColor) ?: nonBlankOrNull(textSecondaryColor) ?: DEFAULT_LIGHT_TEXT_SECONDARY),
            KEY_BORDER to (nonBlankOrNull(light.borderColor) ?: nonBlankOrNull(borderColor) ?: DEFAULT_LIGHT_BORDER)
        )
    }

    /**
     * Resolves the effective dark-mode color map (`primary`, `secondary`, `accent`, `success`,
     * `danger`, `warning`, `info`, `light`, `dark`, `background`, `surface`, `textPrimary`,
     * `textSecondary`, `border`), falling back from `dark.*` to top-level properties and
     * standard Bootstrap 5 dark defaults.
     */
    fun resolveDarkColors(): Map<String, String> {
        val effPrimary = nonBlankOrNull(dark.primaryColor) ?: nonBlankOrNull(primaryColor) ?: DEFAULT_PRIMARY_COLOR
        val effAccent = nonBlankOrNull(dark.accentColor)
            ?: (if (accentColorExplicitlySet) nonBlankOrNull(accentColor) else null)
            ?: effPrimary
        return linkedMapOf(
            KEY_PRIMARY to effPrimary,
            KEY_SECONDARY to (nonBlankOrNull(dark.secondaryColor) ?: nonBlankOrNull(secondaryColor) ?: DEFAULT_SECONDARY_COLOR),
            KEY_ACCENT to effAccent,
            KEY_SUCCESS to (nonBlankOrNull(dark.successColor) ?: nonBlankOrNull(successColor) ?: DEFAULT_SUCCESS_COLOR),
            KEY_DANGER to (nonBlankOrNull(dark.dangerColor) ?: nonBlankOrNull(dangerColor) ?: DEFAULT_DANGER_COLOR),
            KEY_WARNING to (nonBlankOrNull(dark.warningColor) ?: nonBlankOrNull(warningColor) ?: DEFAULT_WARNING_COLOR),
            KEY_INFO to (nonBlankOrNull(dark.infoColor) ?: nonBlankOrNull(infoColor) ?: DEFAULT_INFO_COLOR),
            KEY_LIGHT to (nonBlankOrNull(dark.lightColor) ?: nonBlankOrNull(lightColor) ?: DEFAULT_LIGHT_COLOR),
            KEY_DARK to (nonBlankOrNull(dark.darkColor) ?: nonBlankOrNull(darkColor) ?: DEFAULT_DARK_COLOR),
            KEY_BACKGROUND to (nonBlankOrNull(dark.backgroundColor) ?: nonBlankOrNull(backgroundColor) ?: DEFAULT_DARK_BACKGROUND),
            KEY_SURFACE to (nonBlankOrNull(dark.surfaceColor) ?: nonBlankOrNull(surfaceColor) ?: DEFAULT_DARK_SURFACE),
            KEY_TEXT_PRIMARY to (nonBlankOrNull(dark.textPrimaryColor) ?: nonBlankOrNull(textPrimaryColor) ?: DEFAULT_DARK_TEXT_PRIMARY),
            KEY_TEXT_SECONDARY to (nonBlankOrNull(dark.textSecondaryColor) ?: nonBlankOrNull(textSecondaryColor) ?: DEFAULT_DARK_TEXT_SECONDARY),
            KEY_BORDER to (nonBlankOrNull(dark.borderColor) ?: nonBlankOrNull(borderColor) ?: DEFAULT_DARK_BORDER)
        )
    }

    fun resolveEffectiveLightColors(): Map<String, String> = resolveLightColors()

    fun resolveEffectiveDarkColors(): Map<String, String> = resolveDarkColors()

    internal fun applyOverridesFrom(other: GenericUiThemeConfig) {
        if (other.modeExplicitlySet && other.mode.isNotBlank()) {
            mode = other.mode.trim()
        }
        if (other.primaryColorExplicitlySet || other.primaryColor != DEFAULT_PRIMARY_COLOR) {
            primaryColor = other.primaryColor.trim()
        } else if (!other.colorBacking.isNullOrBlank()) {
            primaryColor = other.colorBacking!!.trim()
        }
        if (other.secondaryColorExplicitlySet || other.secondaryColor != DEFAULT_SECONDARY_COLOR) {
            secondaryColor = other.secondaryColor.trim()
        }
        if (other.accentColorExplicitlySet || other.accentColor != DEFAULT_ACCENT_COLOR) {
            accentColor = other.accentColor.trim()
        }
        if (other.successColorExplicitlySet || other.successColor != DEFAULT_SUCCESS_COLOR) {
            successColor = other.successColor.trim()
        }
        if (other.dangerColorExplicitlySet || other.dangerColor != DEFAULT_DANGER_COLOR) {
            dangerColor = other.dangerColor.trim()
        }
        if (other.warningColorExplicitlySet || other.warningColor != DEFAULT_WARNING_COLOR) {
            warningColor = other.warningColor.trim()
        }
        if (other.infoColorExplicitlySet || other.infoColor != DEFAULT_INFO_COLOR) {
            infoColor = other.infoColor.trim()
        }
        if (other.lightColorExplicitlySet || other.lightColor != DEFAULT_LIGHT_COLOR) {
            lightColor = other.lightColor.trim()
        }
        if (other.darkColorExplicitlySet || other.darkColor != DEFAULT_DARK_COLOR) {
            darkColor = other.darkColor.trim()
        }
        other.backgroundColor?.trim()?.takeIf { it.isNotEmpty() }?.let { backgroundColor = it }
        other.surfaceColor?.trim()?.takeIf { it.isNotEmpty() }?.let { surfaceColor = it }
        other.textPrimaryColor?.trim()?.takeIf { it.isNotEmpty() }?.let { textPrimaryColor = it }
        other.textSecondaryColor?.trim()?.takeIf { it.isNotEmpty() }?.let { textSecondaryColor = it }
        other.borderColor?.trim()?.takeIf { it.isNotEmpty() }?.let { borderColor = it }
        other.styleSheet?.trim()?.takeIf { it.isNotEmpty() }?.let { styleSheet = it }
        light.applyOverridesFrom(other.light)
        dark.applyOverridesFrom(other.dark)
    }

    private fun nonBlankOrNull(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val KEY_PRIMARY = "primary"
        const val KEY_SECONDARY = "secondary"
        const val KEY_ACCENT = "accent"
        const val KEY_SUCCESS = "success"
        const val KEY_DANGER = "danger"
        const val KEY_WARNING = "warning"
        const val KEY_INFO = "info"
        const val KEY_LIGHT = "light"
        const val KEY_DARK = "dark"
        const val KEY_BACKGROUND = "background"
        const val KEY_SURFACE = "surface"
        const val KEY_TEXT_PRIMARY = "textPrimary"
        const val KEY_TEXT_SECONDARY = "textSecondary"
        const val KEY_BORDER = "border"

        const val DEFAULT_PRIMARY_COLOR = "#0d6efd"
        const val DEFAULT_SECONDARY_COLOR = "#6c757d"
        const val DEFAULT_ACCENT_COLOR = "#0d6efd"
        const val DEFAULT_SUCCESS_COLOR = "#198754"
        const val DEFAULT_DANGER_COLOR = "#dc3545"
        const val DEFAULT_WARNING_COLOR = "#ffc107"
        const val DEFAULT_INFO_COLOR = "#0dcaf0"
        const val DEFAULT_LIGHT_COLOR = "#f8f9fa"
        const val DEFAULT_DARK_COLOR = "#212529"

        const val DEFAULT_LIGHT_BACKGROUND = "#ffffff"
        const val DEFAULT_LIGHT_SURFACE = "#f8f9fa"
        const val DEFAULT_LIGHT_TEXT_PRIMARY = "#212529"
        const val DEFAULT_LIGHT_TEXT_SECONDARY = "#6c757d"
        const val DEFAULT_LIGHT_BORDER = "#dee2e6"

        const val DEFAULT_DARK_BACKGROUND = "#212529"
        const val DEFAULT_DARK_SURFACE = "#2b3035"
        const val DEFAULT_DARK_TEXT_PRIMARY = "#f8f9fa"
        const val DEFAULT_DARK_TEXT_SECONDARY = "#adb5bd"
        const val DEFAULT_DARK_BORDER = "#495057"

        fun defaultLightColors(primaryOverride: String? = null): Map<String, String> =
            GenericUiThemeConfig().apply {
                if (!primaryOverride.isNullOrBlank() && primaryOverride.trim() != DEFAULT_PRIMARY_COLOR) {
                    primaryColor = primaryOverride.trim()
                }
            }.resolveLightColors()

        fun defaultDarkColors(primaryOverride: String? = null): Map<String, String> =
            GenericUiThemeConfig().apply {
                if (!primaryOverride.isNullOrBlank() && primaryOverride.trim() != DEFAULT_PRIMARY_COLOR) {
                    primaryColor = primaryOverride.trim()
                }
            }.resolveDarkColors()
    }
}
