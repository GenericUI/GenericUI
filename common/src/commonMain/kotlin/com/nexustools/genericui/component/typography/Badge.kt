package com.nexustools.genericui.component.typography

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.theme.ThemeConfig

/**
 * Bootstrap status pill/badge (`badge bg-primary`, etc.) with automatic dark/light surface
 * and pill background foreground-contrast resolution.
 */
class Badge(
    text: String = "",
    type: BootstrapType = BootstrapType.PRIMARY,
    pill: Boolean = false
) : Label(
    text = text,
    tagName = "span",
    classPrefix = if (pill) "badge rounded-pill" else "badge"
) {
    init {
        htmlTag = "span"
        setAttribute("componentRole", "badge")
        this.type = type
    }

    /**
     * Returns `true` when this badge sits on a dark container, dark theme, or video overlay surface.
     */
    fun isOnDarkSurface(): Boolean {
        var cur: AbstractComponent? = parent
        while (cur != null) {
            if (cur.getAttribute("data-overlay-root") == "true" ||
                cur.getAttribute("data-overlay-host-root") == "true" ||
                cur.getAttribute("data-overlay-host") == "true" ||
                cur.getAttribute("data-overlay-role") != null ||
                cur.getAttribute("stagePlaying") != null ||
                cur.htmlTag.contains("overlay", ignoreCase = true) ||
                cur.htmlTag.contains("video", ignoreCase = true)
            ) {
                return true
            }
            val bg = cur.getStyle().backgroundColor ?: cur.customBackgroundColor ?: cur.computedStyle?.backgroundColor
            if (bg != null && bg.a >= 120) {
                return ThemeConfig.relativeLuminance(bg) < 138
            }
            cur = cur.parent
        }
        return Application.current?.getResolvedTheme() != ThemeMode.LIGHT
    }

    /**
     * Resolves the effective pill background color, honoring explicit inline/computed styles,
     * semantic Bootstrap tinting, and dark/light `.badge` fallback surfaces.
     */
    fun resolveEffectivePillBackgroundColor(
        darkTheme: Boolean = isOnDarkSurface()
    ): RgbaColor {
        val explicitBg = getStyle().backgroundColor ?: customBackgroundColor ?: computedStyle?.backgroundColor
        if (explicitBg != null && explicitBg.a > 0) {
            return explicitBg
        }
        val activeType = backgroundType?.takeIf { it != BootstrapType.NONE }
            ?: type?.takeIf { it != BootstrapType.NONE }
        if (activeType != null) {
            return BootstrapColorPalette.resolveColor(activeType, darkTheme)
        }
        return if (darkTheme) RgbaColor(42, 48, 60, 220) else RgbaColor(108, 117, 125, 255)
    }

    /**
     * Resolves a legible high-contrast text color for this pill against [resolveEffectivePillBackgroundColor],
     * upgrading low-contrast dark text (e.g. inherited `#212529`) on dark or translucent video pills.
     */
    fun resolveEffectivePillForegroundColor(
        darkTheme: Boolean = isOnDarkSurface()
    ): RgbaColor {
        val backdrop = if (darkTheme) RgbaColor(14, 16, 22, 255) else RgbaColor(255, 255, 255, 255)
        val rawBg = resolveEffectivePillBackgroundColor(darkTheme)
        val compositedBg = ThemeConfig.compositeOver(rawBg, backdrop)
        val bgIsDark = ThemeConfig.relativeLuminance(compositedBg) < 150

        val explicitUserFg = getStyle().color ?: textColor
        if (explicitUserFg != null && explicitUserFg.a > 0) {
            val compositedExplicit = ThemeConfig.compositeOver(explicitUserFg, compositedBg)
            if (ThemeConfig.contrastRatio(compositedExplicit, compositedBg) >= 3.0) {
                return explicitUserFg
            }
            return if (bgIsDark) RgbaColor.WHITE else RgbaColor(33, 37, 41)
        }

        val computedFg = computedStyle?.textColor
        if (computedFg != null && computedFg.a > 0) {
            val compositedComputed = ThemeConfig.compositeOver(computedFg, compositedBg)
            if (ThemeConfig.contrastRatio(compositedComputed, compositedBg) >= 3.5) {
                return computedFg
            }
            return if (bgIsDark) RgbaColor.WHITE else RgbaColor(33, 37, 41)
        }

        val explicitRawBg = getStyle().backgroundColor ?: customBackgroundColor ?: computedStyle?.backgroundColor
        if (explicitRawBg == null || explicitRawBg.a == 0) {
            val activeType = backgroundType?.takeIf { it != BootstrapType.NONE }
                ?: type?.takeIf { it != BootstrapType.NONE }
            if (activeType != null) {
                val themeFg = BootstrapColorPalette.resolveForegroundOn(activeType, darkTheme)
                if (ThemeConfig.contrastRatio(themeFg, compositedBg) >= 3.0) {
                    return themeFg
                }
            }
        }
        return if (bgIsDark) RgbaColor.WHITE else RgbaColor(33, 37, 41)
    }

    private fun syncContrastAttributes(impl: NativeImpl<*>? = nativeImpl) {
        val darkTheme = isOnDarkSurface()
        val effectiveBg = resolveEffectivePillBackgroundColor(darkTheme)
        val effectiveFg = resolveEffectivePillForegroundColor(darkTheme)
        setAttribute("data-badge-bg", effectiveBg.toCssRgbaString())
        setAttribute("data-badge-fg", effectiveFg.toCssRgbaString())
        if (impl != null) {
            impl.setAttribute("componentRole", "badge")
            impl.setAttribute("data-badge-bg", effectiveBg.toCssRgbaString())
            impl.setAttribute("data-badge-fg", effectiveFg.toCssRgbaString())
            val effectiveStyle = ComponentStyle().apply { copyFrom(effectiveComponentStyle()) }
            val currentFg = effectiveStyle.getColor()
            val compositedBg = ThemeConfig.compositeOver(
                effectiveBg,
                if (darkTheme) RgbaColor(14, 16, 22) else RgbaColor.WHITE
            )
            if (currentFg == null ||
                ThemeConfig.contrastRatio(ThemeConfig.compositeOver(currentFg, compositedBg), compositedBg) < 3.0
            ) {
                effectiveStyle.setColor(effectiveFg)
                impl.applyComponentStyle(effectiveStyle)
            }
        }
    }

    internal override fun onComputedStyleChanged(previous: ComputedStyle?, current: ComputedStyle) {
        syncContrastAttributes()
    }

    internal override fun onAfterBootstrapChanged() {
        super.onAfterBootstrapChanged()
        syncContrastAttributes()
    }

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        syncContrastAttributes(impl)
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        syncContrastAttributes()
    }
}
