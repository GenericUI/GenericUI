package com.nexustools.genericui.style.theme

import com.nexustools.genericui.style.color.RgbaColor

/**
 * Material Design + Bootstrap semantic theme color palette (`ThemeColorSet`).
 *
 * All fields are optional (`null` = fall back to the default Bootstrap / OS theme palette).
 * Can be specified globally on [ThemeConfig.colors] or per theme mode via [ThemeConfig.lightColors]
 * and [ThemeConfig.darkColors].
 */
data class ThemeColorSet(
    val primary: RgbaColor? = null,
    val onPrimary: RgbaColor? = null,
    val primaryVariant: RgbaColor? = null,
    val secondary: RgbaColor? = null,
    val onSecondary: RgbaColor? = null,
    val secondaryVariant: RgbaColor? = null,
    val accent: RgbaColor? = null,
    val info: RgbaColor? = null,
    val onInfo: RgbaColor? = null,
    val success: RgbaColor? = null,
    val onSuccess: RgbaColor? = null,
    val warning: RgbaColor? = null,
    val onWarning: RgbaColor? = null,
    val danger: RgbaColor? = null,
    val onDanger: RgbaColor? = null,
    val light: RgbaColor? = null,
    val onLight: RgbaColor? = null,
    val dark: RgbaColor? = null,
    val onDark: RgbaColor? = null,
    val background: RgbaColor? = null,
    val onBackground: RgbaColor? = null,
    val surface: RgbaColor? = null,
    val onSurface: RgbaColor? = null,
    val statusBar: RgbaColor? = null
) {
    val isEmpty: Boolean
        get() = primary == null &&
            onPrimary == null &&
            primaryVariant == null &&
            secondary == null &&
            onSecondary == null &&
            secondaryVariant == null &&
            accent == null &&
            info == null &&
            onInfo == null &&
            success == null &&
            onSuccess == null &&
            warning == null &&
            onWarning == null &&
            danger == null &&
            onDanger == null &&
            light == null &&
            onLight == null &&
            dark == null &&
            onDark == null &&
            background == null &&
            onBackground == null &&
            surface == null &&
            onSurface == null &&
            statusBar == null

    /**
     * Merges [overrideSet] on top of this [ThemeColorSet], preferring non-null colors from [overrideSet].
     */
    fun mergeWith(overrideSet: ThemeColorSet): ThemeColorSet = ThemeColorSet(
        primary = overrideSet.primary ?: primary,
        onPrimary = overrideSet.onPrimary ?: onPrimary,
        primaryVariant = overrideSet.primaryVariant ?: primaryVariant,
        secondary = overrideSet.secondary ?: secondary,
        onSecondary = overrideSet.onSecondary ?: onSecondary,
        secondaryVariant = overrideSet.secondaryVariant ?: secondaryVariant,
        accent = overrideSet.accent ?: accent,
        info = overrideSet.info ?: info,
        onInfo = overrideSet.onInfo ?: onInfo,
        success = overrideSet.success ?: success,
        onSuccess = overrideSet.onSuccess ?: onSuccess,
        warning = overrideSet.warning ?: warning,
        onWarning = overrideSet.onWarning ?: onWarning,
        danger = overrideSet.danger ?: danger,
        onDanger = overrideSet.onDanger ?: onDanger,
        light = overrideSet.light ?: light,
        onLight = overrideSet.onLight ?: onLight,
        dark = overrideSet.dark ?: dark,
        onDark = overrideSet.onDark ?: onDark,
        background = overrideSet.background ?: background,
        onBackground = overrideSet.onBackground ?: onBackground,
        surface = overrideSet.surface ?: surface,
        onSurface = overrideSet.onSurface ?: onSurface,
        statusBar = overrideSet.statusBar ?: statusBar
    )

    companion object {
        val EMPTY = ThemeColorSet()
    }
}
