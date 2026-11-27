package com.nexustools.genericui.component.surface.styled

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot

/**
 * Non-container, non-button cross-platform CSS-styled visual leaf (`<genericui:styled-component>`),
 * driven uniformly by `style.scss` / `theme.scss` and resolved [StyledVisualSpec] state overrides
 * (`:hover`, `:focus`, `:active`, `:disabled`), supporting `opacity`, `pointer-events`, `cursor`,
 * `z-index`, and [com.nexustools.genericui.style.css.CssTransform].
 */
open class StyledComponent(
    classPrefix: String = DEFAULT_CLASS_PREFIX,
    tagName: String = DEFAULT_TAG_NAME
) : AbstractComponent(classPrefix, tagName) {

    init {
        if (classPrefix.isNotBlank() && classPrefix != DEFAULT_CLASS_PREFIX) {
            addClass(classPrefix)
        }
    }

    override val acceptsTypographyComponentStyles: Boolean
        get() = true

    override val acceptsAestheticSkinComponentStyles: Boolean
        get() = true

    internal override fun canAcceptFocusForIsFocused(): Boolean = inheritedFocusableState

    internal override fun updateInheritedFocusable(ancestorAllowsFocus: Boolean) {
        super.updateInheritedFocusable(ancestorAllowsFocus)
        if (!ancestorAllowsFocus && isFocused) {
            clearFocusedFromAncestorSuppression()
            syncVisualsToNative()
        }
    }

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

    var backdropFilter: CssFilter?
        get() = getStyle().backdropFilter ?: computedStyle?.backdropFilter
        set(value) {
            getStyle().setBackdropFilter(value)
            syncVisualsToNative()
        }

    var hoverBackgroundColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var hoverBorderColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var hoverTextColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var focusBorderColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var focusRingColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var focusRingWidthPx: Float? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var activeBackgroundColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var activeBorderColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var activeTextColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var disabledBackgroundColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var disabledBorderColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var disabledTextColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var disabledOpacity: Float? = null
        set(value) {
            field = value
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

    fun resolveBaseVisualSpec(): StyledVisualSpec {
        val isDark = Application.current?.getResolvedTheme() != ThemeMode.LIGHT
        val snapshot = getBootstrap().applyToSnapshot(BootstrapStyleSnapshot(componentPrefix = componentClassPrefix))
        return StyledVisualSpec.fromComponentAndComputed(
            componentStyle = getStyle(),
            computedStyle = computedStyle,
            snapshot = snapshot,
            darkTheme = isDark
        )
    }

    fun resolveHoverVisualSpec(): StyledVisualSpec? {
        val fromSheet = StyledVisualSpec.fromComputedStyle(computedStyle?.hoverStyle)
        val hasExplicit = hoverBackgroundColor != null || hoverBorderColor != null || hoverTextColor != null
        if (!hasExplicit) return fromSheet
        val explicit = StyledVisualSpec(
            backgroundColor = hoverBackgroundColor,
            borderColor = hoverBorderColor,
            textColor = hoverTextColor
        )
        return (fromSheet ?: StyledVisualSpec.EMPTY).mergeWith(explicit)
    }

    fun resolveFocusVisualSpec(): StyledVisualSpec? {
        val fromSheet = StyledVisualSpec.fromComputedStyle(computedStyle?.focusStyle)
        val hasExplicit = focusBorderColor != null || focusRingColor != null || focusRingWidthPx != null
        if (!hasExplicit) return fromSheet
        val explicit = StyledVisualSpec(
            borderColor = focusBorderColor,
            focusRingColor = focusRingColor,
            focusRingWidthPx = focusRingWidthPx
        )
        return (fromSheet ?: StyledVisualSpec.EMPTY).mergeWith(explicit)
    }

    fun resolveActiveVisualSpec(): StyledVisualSpec? {
        val fromSheet = StyledVisualSpec.fromComputedStyle(computedStyle?.activeStyle)
        val hasExplicit = activeBackgroundColor != null || activeBorderColor != null || activeTextColor != null
        if (!hasExplicit) return fromSheet
        val explicit = StyledVisualSpec(
            backgroundColor = activeBackgroundColor,
            borderColor = activeBorderColor,
            textColor = activeTextColor
        )
        return (fromSheet ?: StyledVisualSpec.EMPTY).mergeWith(explicit)
    }

    fun resolveDisabledVisualSpec(): StyledVisualSpec? {
        val fromSheet = StyledVisualSpec.fromComputedStyle(computedStyle?.disabledStyle)
        val hasExplicit = disabledBackgroundColor != null ||
            disabledBorderColor != null ||
            disabledTextColor != null ||
            disabledOpacity != null
        if (!hasExplicit) return fromSheet
        val explicit = StyledVisualSpec(
            backgroundColor = disabledBackgroundColor,
            borderColor = disabledBorderColor,
            textColor = disabledTextColor,
            opacity = disabledOpacity
        )
        return (fromSheet ?: StyledVisualSpec.EMPTY).mergeWith(explicit)
    }

    /**
     * Resolves the effective [StyledVisualSpec] for the current interactive state
     * ([isHovered], [isFocused], [isActivePressed], [isEnabled]).
     */
    fun resolveCurrentVisualSpec(): StyledVisualSpec {
        var resolved = resolveBaseVisualSpec()
        if (!isEnabled) {
            return resolved.mergeWith(resolveDisabledVisualSpec())
        }
        if (isFocused) {
            resolved = resolved.mergeWith(resolveFocusVisualSpec())
        }
        if (isHovered) {
            resolved = resolved.mergeWith(resolveHoverVisualSpec())
        }
        if (isActivePressed) {
            resolved = resolved.mergeWith(resolveActiveVisualSpec())
        }
        return resolved
    }

    private var visualsSyncedForImpl: StyledComponentNativeImpl<*>? = null
    private var lastSyncedBaseVisuals: StyledVisualSpec? = null
    private var lastSyncedHoverVisuals: StyledVisualSpec? = null
    private var lastSyncedFocusVisuals: StyledVisualSpec? = null
    private var lastSyncedActiveVisuals: StyledVisualSpec? = null
    private var lastSyncedDisabledVisuals: StyledVisualSpec? = null

    protected fun syncVisualsToNative() {
        val impl = typedNative<StyledComponentNativeImpl<*>>() ?: return
        val forceAll = visualsSyncedForImpl !== impl
        val base = resolveBaseVisualSpec()
        if (forceAll || base != lastSyncedBaseVisuals) {
            lastSyncedBaseVisuals = base
            impl.setStyledVisuals(base)
        }
        val hover = resolveHoverVisualSpec()
        if (forceAll || hover != lastSyncedHoverVisuals) {
            lastSyncedHoverVisuals = hover
            impl.setHoverVisuals(hover)
        }
        val focus = resolveFocusVisualSpec()
        if (forceAll || focus != lastSyncedFocusVisuals) {
            lastSyncedFocusVisuals = focus
            impl.setFocusVisuals(focus)
        }
        val active = resolveActiveVisualSpec()
        if (forceAll || active != lastSyncedActiveVisuals) {
            lastSyncedActiveVisuals = active
            impl.setActiveVisuals(active)
        }
        val disabled = resolveDisabledVisualSpec()
        if (forceAll || disabled != lastSyncedDisabledVisuals) {
            lastSyncedDisabledVisuals = disabled
            impl.setDisabledVisuals(disabled)
        }
        visualsSyncedForImpl = impl
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
        syncVisualsToNative()
    }

    internal open fun onThemeChanged() {
        syncVisualsToNative()
    }

    protected override fun createNativeImpl(factory: NativeFactory): StyledComponentNativeImpl<*> =
        factory.createStyledComponent(componentClassPrefix, htmlTag)

    override fun syncStateToNative() {
        visualsSyncedForImpl = null
        super.syncStateToNative()
        syncVisualsToNative()
    }

    companion object {
        const val DEFAULT_CLASS_PREFIX: String = "styled-component"
        const val DEFAULT_TAG_NAME: String = "genericui:styled-component"
    }
}
