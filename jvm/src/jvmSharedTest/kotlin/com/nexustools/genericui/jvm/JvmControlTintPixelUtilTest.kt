package com.nexustools.genericui.jvm

import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.theme.ThemeColorSet
import com.nexustools.genericui.style.theme.ThemeConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmControlTintPixelUtilTest {

    @Test
    fun synthesizesAndTintsFilledButtonPixelsPreservingBevel() {
        val width = 80
        val height = 32
        val pixels = IntArray(width * height)
        JvmControlTintPixelUtil.synthesizeFallbackNativeButtonSurface(
            pixels = pixels,
            width = width,
            height = height,
            isDark = false,
            borderRadiusStyle = null
        )

        val primary = RgbaColor(210, 45, 95, 255)
        JvmControlTintPixelUtil.tintNativeControlPixels(
            pixels = pixels,
            width = width,
            height = height,
            tint = primary,
            outline = false,
            hovered = false,
            pressed = false,
            disabled = false,
            isLink = false,
            darkMode = false,
            clipToRoundedShape = false
        )

        val centerArgb = pixels[16 * width + 40]
        val r = (centerArgb ushr 16) and 0xFF
        val g = (centerArgb ushr 8) and 0xFF
        val b = centerArgb and 0xFF
        assertTrue(r > 160 && g < 70 && b in 60..130, "Unexpected center pixel RGB=($r,$g,$b)")

        val borderArgb = pixels[0]
        val borderR = (borderArgb ushr 16) and 0xFF
        assertTrue(borderR < r, "Border pixel should preserve darker edge bevel than center")
    }

    @Test
    fun pillOutlineTintKeepsCornersTransparentAndInteriorLowAlpha() {
        val width = 80
        val height = 32
        val pixels = IntArray(width * height)
        JvmControlTintPixelUtil.synthesizeFallbackNativeButtonSurface(
            pixels = pixels,
            width = width,
            height = height,
            isDark = false,
            borderRadiusStyle = BorderRadiusStyle.PILL,
            density = 1f,
            roundedClip = true
        )

        val primary = RgbaColor(200, 40, 90, 255)
        JvmControlTintPixelUtil.tintNativeControlPixels(
            pixels = pixels,
            width = width,
            height = height,
            tint = primary,
            outline = true,
            hovered = false,
            pressed = false,
            disabled = false,
            isLink = false,
            darkMode = false,
            borderRadiusStyle = BorderRadiusStyle.PILL,
            density = 1f,
            clipToRoundedShape = true
        )

        val cornerAlpha = (pixels[0] ushr 24) and 0xFF
        assertEquals(0, cornerAlpha)

        val topEdgeArgb = pixels[40]
        val topAlpha = (topEdgeArgb ushr 24) and 0xFF
        val topR = (topEdgeArgb ushr 16) and 0xFF
        val topB = topEdgeArgb and 0xFF
        assertTrue(topAlpha >= 220, "Top outline stroke should be opaque (alpha=$topAlpha)")
        assertTrue(topR > 130 && topB > 35, "Top outline stroke should carry primary tint")

        val centerAlpha = (pixels[16 * width + 40] ushr 24) and 0xFF
        assertTrue(centerAlpha in 1..30, "Outline interior should remain low-alpha tinted (alpha=$centerAlpha)")
    }

    @Test
    fun linkModeClearsPixelsAndStateAdjustmentModulatesColor() {
        val pixels = IntArray(16) { 0xFFFFFFFF.toInt() }
        JvmControlTintPixelUtil.tintNativeControlPixels(
            pixels = pixels,
            width = 4,
            height = 4,
            tint = RgbaColor.PRIMARY_BLUE,
            isLink = true
        )
        assertTrue(pixels.all { it == 0 })

        val base = RgbaColor(100, 120, 140, 255)
        val hovered = JvmControlTintPixelUtil.adjustTintForState(base, hovered = true)
        val pressed = JvmControlTintPixelUtil.adjustTintForState(base, pressed = true)
        val disabled = JvmControlTintPixelUtil.adjustTintForState(base, disabled = true)
        assertTrue(hovered.r > base.r && hovered.g > base.g)
        assertTrue(pressed.r < base.r && pressed.g < base.g)
        assertTrue(disabled.a < base.a)

        val customPrimary = RgbaColor(10, 20, 200, 255)
        val cfg = ThemeConfig(lightColors = ThemeColorSet(primary = customPrimary))
        assertEquals(
            customPrimary,
            JvmControlTintPixelUtil.resolveContrastingForeground(
                type = BootstrapType.PRIMARY,
                darkMode = false,
                outline = true,
                hovered = false,
                pressed = false,
                themeConfig = cfg
            )
        )
        assertEquals(
            RgbaColor(255, 255, 255),
            JvmControlTintPixelUtil.resolveContrastingForeground(
                type = BootstrapType.PRIMARY,
                darkMode = false,
                outline = true,
                hovered = true,
                pressed = false,
                themeConfig = cfg
            )
        )
    }
}
