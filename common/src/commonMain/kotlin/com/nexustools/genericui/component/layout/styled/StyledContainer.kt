package com.nexustools.genericui.component.layout.styled

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.policy.AestheticStylePolicy

/**
 * Cross-platform styled container (`<genericui:styled-container>`) that renders consistent
 * backgrounds (solid, RGBA alpha-transparent, linear & radial gradients), borders (width, style,
 * color, corner radius), padding, opacity, and box shadows across all 8 backends (`:windows`,
 * `:macos`, `:gtk`, `:kde`, `:swing`, `:android`, `:ios`, `:web`).
 *
 * Unlike [com.nexustools.genericui.component.action.styled.StyledButton], [StyledContainer] does not
 * apply interactive `:hover` or `:focus` state overrides.
 */
open class StyledContainer(
    classPrefix: String = "styled-container",
    tagName: String = "genericui:styled-container"
) : Container(tagName, classPrefix) {

    override val acceptsTypographyComponentStyles: Boolean
        get() = true

    override val acceptsAestheticSkinComponentStyles: Boolean
        get() = true

    var backgroundColor: RgbaColor?
        get() = getStyle().backgroundColor ?: computedStyle?.backgroundColor
        set(value) {
            getStyle().setBackgroundColor(value)
            customBackgroundColor = value
            syncVisualsToNative()
        }

    var borderWidthPx: Float?
        get() = getStyle().borderWidthPx ?: computedStyle?.borderWidthPx
        set(value) {
            getStyle().setBorderWidth(value?.let { CssEdgeLengths.px(it) })
            if (value != null && value > 0f && getStyle().borderStyle == null) {
                getStyle().setBorderStyle(CssBorderStyle.SOLID)
            }
            syncVisualsToNative()
        }

    var borderStyle: CssBorderStyle?
        get() = getStyle().borderStyle ?: CssBorderStyle.fromCss(computedStyle?.borderStyle)
        set(value) {
            getStyle().setBorderStyle(value)
            syncVisualsToNative()
        }

    var gapPx: Int?
        get() = getStyle().gap?.value?.toInt()
        set(value) {
            getStyle().setGap(value?.let { CssLength.px(it) })
        }

    var backdropFilter: CssFilter?
        get() = getStyle().backdropFilter ?: computedStyle?.backdropFilter
        set(value) {
            getStyle().setBackdropFilter(value)
            syncVisualsToNative()
        }

    fun setBackdropBlur(radiusPx: Number) {
        val px = radiusPx.toFloat().coerceAtLeast(0f)
        getStyle().setBackdropFilter(if (px > 0f) CssFilter.blur(CssLength.px(px)) else null)
        syncVisualsToNative()
    }

    fun setBorder(
        widthPx: Number,
        color: RgbaColor,
        style: CssBorderStyle = CssBorderStyle.SOLID,
        radiusPx: Number? = null
    ) {
        getStyle().setBorderWidth(CssEdgeLengths.px(widthPx))
        getStyle().setBorderStyle(style)
        getStyle().setBorderColor(color)
        this.borderColor = color
        if (radiusPx != null) {
            getStyle().setBorderRadius(CssCornerRadii.px(radiusPx))
            this.borderRadiusPx = radiusPx.toFloat()
        }
        syncVisualsToNative()
    }

    fun setBackground(color: RgbaColor?) {
        this.backgroundColor = color
    }

    fun setBackground(gradient: Gradient?) {
        getStyle().background = gradient
        this.backgroundGradient = gradient
        syncVisualsToNative()
    }

    /**
     * Resolves the static [StyledVisualSpec] for this container (ignoring `:hover` / `:focus` pseudo-states).
     */
    fun resolveVisualSpec(): StyledVisualSpec {
        val isDark = Application.current?.getResolvedTheme() != ThemeMode.LIGHT
        val snapshot = getBootstrap().applyToSnapshot(BootstrapStyleSnapshot(componentPrefix = componentClassPrefix))
        val raw = StyledVisualSpec.fromComponentAndComputed(
            componentStyle = getStyle(),
            computedStyle = resolveEffectiveStyle(),
            snapshot = snapshot,
            darkTheme = isDark
        )
        return if (AestheticStylePolicy.isStyledContainerOnly(this)) raw else raw.withoutBackdropFilter()
    }

    private var lastSyncedVisualSpec: StyledVisualSpec? = null

    protected fun syncVisualsToNative() {
        val impl = typedNative<StyledContainerNativeImpl<*>>() ?: return
        val spec = resolveVisualSpec()
        if (spec == lastSyncedVisualSpec) return
        lastSyncedVisualSpec = spec
        impl.setStyledVisuals(spec)
    }

    internal override fun onComputedStyleChanged(previous: ComputedStyle?, current: ComputedStyle) {
        syncVisualsToNative()
    }

    internal override fun onComponentOpacityChanged() {
        syncVisualsToNative()
    }

    internal override fun onComponentEnabledChanged(enabled: Boolean) {
        syncVisualsToNative()
    }

    internal override fun onAfterBootstrapChanged() {
        super.onAfterBootstrapChanged()
        syncVisualsToNative()
    }

    internal open fun onThemeChanged() {
        syncVisualsToNative()
    }

    protected override fun createNativeImpl(factory: NativeFactory): StyledContainerNativeImpl<*> =
        factory.createStyledContainer(componentClassPrefix, htmlTag)

    override fun syncStateToNative() {
        lastSyncedVisualSpec = null
        super.syncStateToNative()
        syncVisualsToNative()
    }
}
