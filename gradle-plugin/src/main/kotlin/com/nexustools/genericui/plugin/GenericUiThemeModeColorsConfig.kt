package com.nexustools.genericui.plugin

import org.gradle.kotlin.dsl.GradleDsl

/**
 * Per-mode (`light { ... }` or `dark { ... }`) semantic theme color overrides inside
 * `genericui { theme { ... } }` or any variant scope's `theme { ... }` block.
 */
@GradleDsl
open class GenericUiThemeModeColorsConfig {
    var primaryColor: String? = null
    var secondaryColor: String? = null
    var accentColor: String? = null
    var successColor: String? = null
    var dangerColor: String? = null
    var warningColor: String? = null
    var infoColor: String? = null
    var lightColor: String? = null
    var darkColor: String? = null
    var backgroundColor: String? = null
    var surfaceColor: String? = null
    var textPrimaryColor: String? = null
    var textSecondaryColor: String? = null
    var borderColor: String? = null

    /**
     * Returns `true` if any color override property has been configured on this mode block.
     */
    fun hasCustomOverrides(): Boolean =
        !primaryColor.isNullOrBlank() ||
            !secondaryColor.isNullOrBlank() ||
            !accentColor.isNullOrBlank() ||
            !successColor.isNullOrBlank() ||
            !dangerColor.isNullOrBlank() ||
            !warningColor.isNullOrBlank() ||
            !infoColor.isNullOrBlank() ||
            !lightColor.isNullOrBlank() ||
            !darkColor.isNullOrBlank() ||
            !backgroundColor.isNullOrBlank() ||
            !surfaceColor.isNullOrBlank() ||
            !textPrimaryColor.isNullOrBlank() ||
            !textSecondaryColor.isNullOrBlank() ||
            !borderColor.isNullOrBlank()

    internal fun applyOverridesFrom(other: GenericUiThemeModeColorsConfig) {
        other.primaryColor?.trim()?.takeIf { it.isNotEmpty() }?.let { primaryColor = it }
        other.secondaryColor?.trim()?.takeIf { it.isNotEmpty() }?.let { secondaryColor = it }
        other.accentColor?.trim()?.takeIf { it.isNotEmpty() }?.let { accentColor = it }
        other.successColor?.trim()?.takeIf { it.isNotEmpty() }?.let { successColor = it }
        other.dangerColor?.trim()?.takeIf { it.isNotEmpty() }?.let { dangerColor = it }
        other.warningColor?.trim()?.takeIf { it.isNotEmpty() }?.let { warningColor = it }
        other.infoColor?.trim()?.takeIf { it.isNotEmpty() }?.let { infoColor = it }
        other.lightColor?.trim()?.takeIf { it.isNotEmpty() }?.let { lightColor = it }
        other.darkColor?.trim()?.takeIf { it.isNotEmpty() }?.let { darkColor = it }
        other.backgroundColor?.trim()?.takeIf { it.isNotEmpty() }?.let { backgroundColor = it }
        other.surfaceColor?.trim()?.takeIf { it.isNotEmpty() }?.let { surfaceColor = it }
        other.textPrimaryColor?.trim()?.takeIf { it.isNotEmpty() }?.let { textPrimaryColor = it }
        other.textSecondaryColor?.trim()?.takeIf { it.isNotEmpty() }?.let { textSecondaryColor = it }
        other.borderColor?.trim()?.takeIf { it.isNotEmpty() }?.let { borderColor = it }
    }
}
