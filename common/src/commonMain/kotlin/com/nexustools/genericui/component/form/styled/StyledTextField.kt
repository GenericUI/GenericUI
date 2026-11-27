package com.nexustools.genericui.component.form.styled

import com.nexustools.genericui.component.form.ITextField
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.nativeimpl.form.StyledTextInputNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssFontFamily
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.theme.SystemThemeDetector

/**
 * Cross-platform styled single-line (or multi-line) text input extending [TextField] with consistent
 * support across all 9 backends (`:windows`, `:macos`, `:gtk`, `:kde`, `:swing`, `:android`, `:ios`,
 * `:ipados`, `:web`) for:
 * - Solid, RGBA alpha-transparent, and linear/radial gradient backgrounds
 * - Custom borders (`borderWidthPx`, `borderStyle`, `borderColor`, `borderRadiusPx`, pill radii)
 * - Foreground text & placeholder colors, padding, and typography (`fontFamily`, `fontSizePx`, `fontWeightNumeric`)
 * - Interactive `:hover`, `:focus`, `:active`, and `:disabled` visual state overrides
 *
 * Follows [com.nexustools.genericui.style.policy.AestheticStylePolicy] and never applies `BootstrapType`
 * semantic coloring.
 */
open class StyledTextField(
    value: String = "",
    placeholder: String = "",
    multilineMode: Boolean = false,
    passwordMode: Boolean = false,
    label: String? = null,
    private val styledClassPrefix: String = if (multilineMode) DEFAULT_MULTILINE_CLASS_PREFIX else DEFAULT_CLASS_PREFIX
) : TextField(
    value = value,
    placeholder = placeholder,
    multilineMode = multilineMode,
    passwordMode = passwordMode,
    label = label,
    classPrefix = styledClassPrefix
), ITextField {

    override val acceptsTypographyComponentStyles: Boolean
        get() = true

    override val acceptsAestheticSkinComponentStyles: Boolean
        get() = true

    // -------------------------------------------------------------------------
    // Base Visual Properties
    // -------------------------------------------------------------------------

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

    var placeholderColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var fontSizePx: Float?
        get() = getStyle().fontSizePx ?: computedStyle?.fontSizePx
        set(value) {
            getStyle().setFontSizePx(value)
            syncVisualsToNative()
        }

    var fontFamily: CssFontFamily?
        get() = getStyle().fontFamily ?: CssFontFamily.parse(computedStyle?.fontFamily)
        set(value) {
            getStyle().setFontFamily(value)
            syncVisualsToNative()
        }

    var fontWeightNumeric: Int?
        get() = getStyle().fontWeightNumeric ?: computedStyle?.fontWeightNumeric ?: fontWeight?.numericWeight
        set(value) {
            getStyle().fontWeightNumeric = value
            syncVisualsToNative()
        }

    var backdropFilter: CssFilter?
        get() = getStyle().backdropFilter ?: computedStyle?.backdropFilter
        set(value) {
            getStyle().setBackdropFilter(value)
            syncVisualsToNative()
        }

    fun setBackdropBlur(radiusPx: Number) {
        val px = radiusPx.toFloat().coerceAtLeast(0f)
        backdropFilter = if (px > 0f) CssFilter.blur(CssLength.px(px)) else null
    }

    fun setPill(pill: Boolean = true) {
        if (pill) {
            borderRadiusStyle = BorderRadiusStyle.PILL
        } else {
            borderRadiusStyle = null
            if (borderRadiusPx == 999f) {
                borderRadiusPx = null
            }
        }
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

    fun setFont(
        family: CssFontFamily? = this.fontFamily,
        sizePx: Float? = this.fontSizePx,
        weight: FontWeight? = getStyle().fontWeight
    ) {
        getStyle().setFontFamily(family)
        getStyle().setFontSizePx(sizePx)
        getStyle().setFontWeight(weight)
        syncVisualsToNative()
    }

    // -------------------------------------------------------------------------
    // Hover State Overrides
    // -------------------------------------------------------------------------

    var hoverBackgroundColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var hoverBackgroundGradient: Gradient? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var hoverBorderColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var hoverBorderWidthPx: Float? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var hoverBorderStyle: CssBorderStyle? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var hoverBorderRadiusPx: Float? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var hoverTextColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var hoverPlaceholderColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var hoverOpacity: Float? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    // -------------------------------------------------------------------------
    // Focus State Overrides
    // -------------------------------------------------------------------------

    var focusBackgroundColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var focusBackgroundGradient: Gradient? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var focusBorderColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var focusBorderWidthPx: Float? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var focusBorderStyle: CssBorderStyle? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var focusBorderRadiusPx: Float? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var focusTextColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var focusPlaceholderColor: RgbaColor? = null
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

    // -------------------------------------------------------------------------
    // Active & Disabled State Overrides
    // -------------------------------------------------------------------------

    var activeBackgroundColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var activeBackgroundGradient: Gradient? = null
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

    var activePlaceholderColor: RgbaColor? = null
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

    var disabledPlaceholderColor: RgbaColor? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    var disabledOpacity: Float? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    fun setHoverStyle(
        backgroundColor: RgbaColor? = this.hoverBackgroundColor,
        backgroundGradient: Gradient? = this.hoverBackgroundGradient,
        borderColor: RgbaColor? = this.hoverBorderColor,
        borderWidthPx: Float? = this.hoverBorderWidthPx,
        borderRadiusPx: Float? = this.hoverBorderRadiusPx,
        textColor: RgbaColor? = this.hoverTextColor,
        placeholderColor: RgbaColor? = this.hoverPlaceholderColor,
        opacity: Float? = this.hoverOpacity
    ) {
        this.hoverBackgroundColor = backgroundColor
        this.hoverBackgroundGradient = backgroundGradient
        this.hoverBorderColor = borderColor
        this.hoverBorderWidthPx = borderWidthPx
        this.hoverBorderRadiusPx = borderRadiusPx
        this.hoverTextColor = textColor
        this.hoverPlaceholderColor = placeholderColor
        this.hoverOpacity = opacity
        syncVisualsToNative()
    }

    fun setFocusStyle(
        backgroundColor: RgbaColor? = this.focusBackgroundColor,
        backgroundGradient: Gradient? = this.focusBackgroundGradient,
        borderColor: RgbaColor? = this.focusBorderColor,
        borderWidthPx: Float? = this.focusBorderWidthPx,
        borderRadiusPx: Float? = this.focusBorderRadiusPx,
        textColor: RgbaColor? = this.focusTextColor,
        placeholderColor: RgbaColor? = this.focusPlaceholderColor,
        focusRingColor: RgbaColor? = this.focusRingColor,
        focusRingWidthPx: Float? = this.focusRingWidthPx
    ) {
        this.focusBackgroundColor = backgroundColor
        this.focusBackgroundGradient = backgroundGradient
        this.focusBorderColor = borderColor
        this.focusBorderWidthPx = borderWidthPx
        this.focusBorderRadiusPx = borderRadiusPx
        this.focusTextColor = textColor
        this.focusPlaceholderColor = placeholderColor
        this.focusRingColor = focusRingColor
        this.focusRingWidthPx = focusRingWidthPx
        syncVisualsToNative()
    }

    fun resolveBaseVisualSpec(): StyledVisualSpec {
        val effectiveTheme = Application.current?.getResolvedTheme() ?: SystemThemeDetector.detectSystemTheme()
        val isDark = effectiveTheme != ThemeMode.LIGHT
        val snapshot = getBootstrap()
            .applyToSnapshot(BootstrapStyleSnapshot(componentPrefix = componentClassPrefix))
            .copy(type = null, backgroundType = null, textColorType = null, borderType = null)
        var baseSpec = StyledVisualSpec.fromComponentAndComputed(
            componentStyle = getStyle(),
            computedStyle = computedStyle,
            snapshot = snapshot,
            darkTheme = isDark
        )
        if (baseSpec.padding == null && padding != EdgeInsets.ZERO) {
            baseSpec = baseSpec.copy(padding = padding)
        }
        if (baseSpec.fontWeightNumeric == null && fontWeight != null) {
            baseSpec = baseSpec.copy(fontWeightNumeric = fontWeight?.numericWeight)
        }
        if (placeholderColor != null) {
            baseSpec = baseSpec.copy(placeholderColor = placeholderColor)
        }
        return baseSpec
    }

    fun resolveHoverVisualSpec(): StyledVisualSpec? {
        val fromSheet = StyledVisualSpec.fromComputedStyle(computedStyle?.hoverStyle)
        val hasExplicit = hoverBackgroundColor != null ||
            hoverBackgroundGradient != null ||
            hoverBorderColor != null ||
            hoverBorderWidthPx != null ||
            hoverBorderStyle != null ||
            hoverBorderRadiusPx != null ||
            hoverTextColor != null ||
            hoverPlaceholderColor != null ||
            hoverOpacity != null
        if (!hasExplicit) return fromSheet
        val explicit = StyledVisualSpec(
            backgroundColor = hoverBackgroundColor,
            backgroundGradient = hoverBackgroundGradient,
            borderColor = hoverBorderColor,
            borderWidthPx = hoverBorderWidthPx,
            borderStyle = hoverBorderStyle,
            borderRadiusPx = hoverBorderRadiusPx,
            textColor = hoverTextColor,
            placeholderColor = hoverPlaceholderColor,
            opacity = hoverOpacity
        )
        return (fromSheet ?: StyledVisualSpec.EMPTY).mergeWith(explicit)
    }

    fun resolveFocusVisualSpec(): StyledVisualSpec? {
        val fromSheet = StyledVisualSpec.fromComputedStyle(computedStyle?.focusStyle)
        val hasExplicit = focusBackgroundColor != null ||
            focusBackgroundGradient != null ||
            focusBorderColor != null ||
            focusBorderWidthPx != null ||
            focusBorderStyle != null ||
            focusBorderRadiusPx != null ||
            focusTextColor != null ||
            focusPlaceholderColor != null ||
            focusRingColor != null ||
            focusRingWidthPx != null
        if (!hasExplicit) return fromSheet
        val explicit = StyledVisualSpec(
            backgroundColor = focusBackgroundColor,
            backgroundGradient = focusBackgroundGradient,
            borderColor = focusBorderColor,
            borderWidthPx = focusBorderWidthPx,
            borderStyle = focusBorderStyle,
            borderRadiusPx = focusBorderRadiusPx,
            textColor = focusTextColor,
            placeholderColor = focusPlaceholderColor,
            focusRingColor = focusRingColor,
            focusRingWidthPx = focusRingWidthPx
        )
        return (fromSheet ?: StyledVisualSpec.EMPTY).mergeWith(explicit)
    }

    fun resolveActiveVisualSpec(): StyledVisualSpec? {
        val fromSheet = StyledVisualSpec.fromComputedStyle(computedStyle?.activeStyle)
        val hasExplicit = activeBackgroundColor != null ||
            activeBackgroundGradient != null ||
            activeBorderColor != null ||
            activeTextColor != null ||
            activePlaceholderColor != null
        if (!hasExplicit) return fromSheet
        val explicit = StyledVisualSpec(
            backgroundColor = activeBackgroundColor,
            backgroundGradient = activeBackgroundGradient,
            borderColor = activeBorderColor,
            textColor = activeTextColor,
            placeholderColor = activePlaceholderColor
        )
        return (fromSheet ?: StyledVisualSpec.EMPTY).mergeWith(explicit)
    }

    fun resolveDisabledVisualSpec(): StyledVisualSpec? {
        val fromSheet = StyledVisualSpec.fromComputedStyle(computedStyle?.disabledStyle)
        val hasExplicit = disabledBackgroundColor != null ||
            disabledBorderColor != null ||
            disabledTextColor != null ||
            disabledPlaceholderColor != null ||
            disabledOpacity != null
        if (!hasExplicit) return fromSheet
        val explicit = StyledVisualSpec(
            backgroundColor = disabledBackgroundColor,
            borderColor = disabledBorderColor,
            textColor = disabledTextColor,
            placeholderColor = disabledPlaceholderColor,
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
        if (isHovered) {
            resolved = resolved.mergeWith(resolveHoverVisualSpec())
        }
        if (isFocused) {
            resolved = resolved.mergeWith(resolveFocusVisualSpec())
        }
        if (isActivePressed) {
            resolved = resolved.mergeWith(resolveActiveVisualSpec())
        }
        return resolved
    }

    private var visualsSyncedForImpl: StyledTextInputNativeImpl<*>? = null
    private var lastSyncedBaseVisuals: StyledVisualSpec? = null
    private var lastSyncedHoverVisuals: StyledVisualSpec? = null
    private var lastSyncedFocusVisuals: StyledVisualSpec? = null
    private var lastSyncedActiveVisuals: StyledVisualSpec? = null
    private var lastSyncedDisabledVisuals: StyledVisualSpec? = null

    protected fun syncVisualsToNative() {
        val impl = typedNative<StyledTextInputNativeImpl<*>>() ?: return
        val forceAll = visualsSyncedForImpl !== impl
        val base = resolveBaseVisualSpec()
        if (forceAll || base != lastSyncedBaseVisuals) {
            lastSyncedBaseVisuals = base
            impl.setBaseVisuals(base)
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

    internal override fun onComponentPaddingChanged() {
        syncVisualsToNative()
    }

    internal override fun onAfterBootstrapChanged() {
        syncVisualsToNative()
    }

    internal open fun onThemeChanged() {
        syncVisualsToNative()
    }

    protected override fun createNativeImpl(factory: NativeFactory): StyledTextInputNativeImpl<*> =
        factory.createStyledTextInput(multilineMode, styledClassPrefix)

    override fun syncStateToNative() {
        visualsSyncedForImpl = null
        super.syncStateToNative()
        syncVisualsToNative()
    }

    companion object {
        const val DEFAULT_CLASS_PREFIX: String = "styled-textfield"
        const val DEFAULT_MULTILINE_CLASS_PREFIX: String = "styled-textarea"
    }
}
