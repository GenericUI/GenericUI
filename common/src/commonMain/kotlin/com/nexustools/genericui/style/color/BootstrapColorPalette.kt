package com.nexustools.genericui.style.color

import com.nexustools.genericui.enums.bootstrap.BootstrapType

/**
 * Semantic color palette for emulating [BootstrapType] tinting on native controls (red for [BootstrapType.DANGER],
 * blue for [BootstrapType.PRIMARY], green for [BootstrapType.SUCCESS], etc.).
 *
 * When [type] is `null` or [BootstrapType.NONE], returns [RgbaColor.TRANSPARENT] (or `null` via [resolveTintOrNull])
 * so native backends render the genuine OS system control style without tinting.
 */
object BootstrapColorPalette {
    fun resolveTintOrNull(type: BootstrapType?, darkMode: Boolean = false): RgbaColor? {
        if (type == null || type == BootstrapType.NONE) return null
        return resolveColor(type, darkMode)
    }

    fun resolveColor(type: BootstrapType, darkMode: Boolean = false): RgbaColor = when (type) {
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

    fun resolveForegroundOn(type: BootstrapType): RgbaColor = when (type) {
        BootstrapType.NONE, BootstrapType.WARNING, BootstrapType.INFO, BootstrapType.LIGHT -> RgbaColor(33, 37, 41)
        else -> RgbaColor(255, 255, 255)
    }
}
