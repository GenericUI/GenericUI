package com.nexustools.genericui.style.color

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.style.theme.ThemeConfig

/**
 * Semantic color palette for emulating [BootstrapType] tinting on native controls (red for [BootstrapType.DANGER],
 * blue for [BootstrapType.PRIMARY], green for [BootstrapType.SUCCESS], etc.), backed by the active application's
 * [ThemeConfig] (or [GenericUI.defaultThemeConfig]).
 *
 * When [type] is `null` or [BootstrapType.NONE], returns [RgbaColor.TRANSPARENT] (or `null` via [resolveTintOrNull])
 * so native backends render the genuine OS system control style without tinting.
 */
object BootstrapColorPalette {
    val activeThemeConfig: ThemeConfig
        get() = Application.current?.getThemeConfig() ?: GenericUI.defaultThemeConfig

    fun resolveTintOrNull(
        type: BootstrapType?,
        darkMode: Boolean = false,
        themeConfig: ThemeConfig = activeThemeConfig
    ): RgbaColor? {
        if (type == null || type == BootstrapType.NONE) return null
        return resolveColor(type, darkMode, themeConfig)
    }

    fun resolveColor(
        type: BootstrapType,
        darkMode: Boolean = false,
        themeConfig: ThemeConfig = activeThemeConfig
    ): RgbaColor {
        if (type == BootstrapType.NONE) return RgbaColor.TRANSPARENT
        themeConfig.resolveBootstrapColor(type, darkMode)?.let { return it }
        return when (type) {
            BootstrapType.NONE -> RgbaColor.TRANSPARENT
            BootstrapType.PRIMARY -> RgbaColor(13, 110, 253)
            BootstrapType.SECONDARY -> RgbaColor(108, 117, 125)
            BootstrapType.SUCCESS -> RgbaColor(25, 135, 84)
            BootstrapType.DANGER -> RgbaColor(220, 53, 69)
            BootstrapType.WARNING -> RgbaColor(255, 193, 7)
            BootstrapType.INFO -> RgbaColor(13, 202, 240)
            BootstrapType.LIGHT -> if (darkMode) RgbaColor(43, 48, 53) else RgbaColor(248, 249, 250)
            BootstrapType.DARK -> if (darkMode) RgbaColor(222, 226, 230) else RgbaColor(33, 37, 41)
            BootstrapType.LINK -> RgbaColor(13, 110, 253)
        }
    }

    fun resolveForegroundOn(
        type: BootstrapType,
        darkMode: Boolean = false,
        themeConfig: ThemeConfig = activeThemeConfig
    ): RgbaColor {
        if (type == BootstrapType.NONE) {
            val effective = themeConfig.resolveEffectiveColors(darkMode)
            return effective.onSurface
                ?: effective.onBackground
                ?: if (darkMode) RgbaColor(222, 226, 230) else RgbaColor(33, 37, 41)
        }
        themeConfig.resolveBootstrapForeground(type, darkMode)?.let { return it }
        val bg = resolveColor(type, darkMode, themeConfig)
        val luminance = (bg.r * 299 + bg.g * 587 + bg.b * 114) / 1000
        return if (luminance >= 150) {
            RgbaColor(33, 37, 41)
        } else {
            RgbaColor(255, 255, 255)
        }
    }
}
