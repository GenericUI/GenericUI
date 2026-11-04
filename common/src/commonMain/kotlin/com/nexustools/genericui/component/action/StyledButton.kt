package com.nexustools.genericui.component.action

import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolver
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot

/**
 * Cross-platform styled button (`<button class="styled-btn">`) extending [Button] with consistent
 * support across all 8 backends (`:windows`, `:macos`, `:gtk`, `:qt`, `:swing`, `:android`, `:ios`, `:web`)
 * for:
 * - Solid, RGBA alpha-transparent, and linear/radial gradient backgrounds
 * - Custom borders (`borderWidthPx`, `borderStyle`, `borderColor`, `borderRadiusPx`)
 * - Foreground text & monocolor SVG/image icon tinting
 * - Interactive `:hover`, `:focus`, `:active` (pressed), and `:disabled` visual state overrides
 */
open class StyledButton(
    text: String = "",
    icon: String? = null,
    type: BootstrapType? = BootstrapType.NONE,
    size: BootstrapSize = BootstrapSize.MD,
    outline: Boolean = false,
    private val styledClassPrefix: String = "styled-btn"
) : Button(
    text = text,
    icon = icon,
    type = type,
    size = size,
    outline = outline,
    classPrefix = styledClassPrefix
) {

    constructor(
        text: String,
        type: BootstrapType?,
        size: BootstrapSize = BootstrapSize.MD,
        outline: Boolean = false,
        classPrefix: String = "styled-btn"
    ) : this(
        text = text,
        icon = null,
        type = type,
        size = size,
        outline = outline,
        styledClassPrefix = classPrefix
    )

    constructor(
        image: Image,
        text: String = "",
        type: BootstrapType? = BootstrapType.NONE,
        size: BootstrapSize = BootstrapSize.MD,
        outline: Boolean = false,
        classPrefix: String = "styled-btn"
    ) : this(
        text = text,
        icon = image.resourceId?.toResourceUri() ?: image.sourceUrl,
        type = type,
        size = size,
        outline = outline,
        styledClassPrefix = classPrefix
    ) {
        this.iconRes = image.resourceId
        this.image = image
    }

    constructor(
        textResId: ResourceID,
        iconResId: ResourceID? = null
    ) : this(
        text = if (textResId.type == ResourceType.DRAWABLE && iconResId == null) "" else ResourceResolver.getString(textResId),
        icon = if (textResId.type == ResourceType.DRAWABLE && iconResId == null) textResId.toResourceUri() else iconResId?.toResourceUri()
    ) {
        if (textResId.type == ResourceType.DRAWABLE && iconResId == null) {
            this.iconRes = textResId
        } else {
            this.textRes = textResId
            this.iconRes = iconResId
        }
    }

    constructor(
        iconRes: ResourceID,
        tooltipText: String?,
        type: BootstrapType? = null,
        size: BootstrapSize = BootstrapSize.SM,
        outline: Boolean = false
    ) : this(
        text = "",
        icon = iconRes.toResourceUri(),
        type = type,
        size = size,
        outline = outline
    ) {
        this.iconRes = iconRes
        this.tooltipText = tooltipText
    }

    constructor(
        textRes: ResourceID,
        iconRes: ResourceID?,
        type: BootstrapType?,
        size: BootstrapSize = BootstrapSize.MD,
        outline: Boolean = false
    ) : this(
        text = ResourceResolver.getString(textRes),
        icon = iconRes?.toResourceUri(),
        type = type,
        size = size,
        outline = outline
    ) {
        this.textRes = textRes
        this.iconRes = iconRes
    }

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

    // -------------------------------------------------------------------------
    // Hover State Overrides (StyledButton Only)
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

    var hoverOpacity: Float? = null
        set(value) {
            field = value
            syncVisualsToNative()
        }

    // -------------------------------------------------------------------------
    // Focus State Overrides (StyledButton Only)
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

    var focusTextColor: RgbaColor? = null
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
    // Active (Pressed) & Disabled State Overrides (StyledButton Only)
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

    fun setHoverStyle(
        backgroundColor: RgbaColor? = this.hoverBackgroundColor,
        backgroundGradient: Gradient? = this.hoverBackgroundGradient,
        borderColor: RgbaColor? = this.hoverBorderColor,
        borderWidthPx: Float? = this.hoverBorderWidthPx,
        textColor: RgbaColor? = this.hoverTextColor,
        opacity: Float? = this.hoverOpacity
    ) {
        this.hoverBackgroundColor = backgroundColor
        this.hoverBackgroundGradient = backgroundGradient
        this.hoverBorderColor = borderColor
        this.hoverBorderWidthPx = borderWidthPx
        this.hoverTextColor = textColor
        this.hoverOpacity = opacity
        syncVisualsToNative()
    }

    fun setFocusStyle(
        backgroundColor: RgbaColor? = this.focusBackgroundColor,
        backgroundGradient: Gradient? = this.focusBackgroundGradient,
        borderColor: RgbaColor? = this.focusBorderColor,
        borderWidthPx: Float? = this.focusBorderWidthPx,
        textColor: RgbaColor? = this.focusTextColor,
        focusRingColor: RgbaColor? = this.focusRingColor,
        focusRingWidthPx: Float? = this.focusRingWidthPx
    ) {
        this.focusBackgroundColor = backgroundColor
        this.focusBackgroundGradient = backgroundGradient
        this.focusBorderColor = borderColor
        this.focusBorderWidthPx = borderWidthPx
        this.focusTextColor = textColor
        this.focusRingColor = focusRingColor
        this.focusRingWidthPx = focusRingWidthPx
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
        val hasExplicit = hoverBackgroundColor != null ||
            hoverBackgroundGradient != null ||
            hoverBorderColor != null ||
            hoverBorderWidthPx != null ||
            hoverBorderStyle != null ||
            hoverBorderRadiusPx != null ||
            hoverTextColor != null ||
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
            focusTextColor != null ||
            focusRingColor != null ||
            focusRingWidthPx != null
        if (!hasExplicit) return fromSheet
        val explicit = StyledVisualSpec(
            backgroundColor = focusBackgroundColor,
            backgroundGradient = focusBackgroundGradient,
            borderColor = focusBorderColor,
            borderWidthPx = focusBorderWidthPx,
            borderStyle = focusBorderStyle,
            textColor = focusTextColor,
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
            activeTextColor != null
        if (!hasExplicit) return fromSheet
        val explicit = StyledVisualSpec(
            backgroundColor = activeBackgroundColor,
            backgroundGradient = activeBackgroundGradient,
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

    protected fun syncVisualsToNative() {
        val impl = typedNative<StyledButtonNativeImpl<*>>() ?: return
        impl.setBaseVisuals(resolveBaseVisualSpec())
        impl.setHoverVisuals(resolveHoverVisualSpec())
        impl.setFocusVisuals(resolveFocusVisualSpec())
        impl.setActiveVisuals(resolveActiveVisualSpec())
        impl.setDisabledVisuals(resolveDisabledVisualSpec())
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        super.applyComputedStyle(style)
        syncVisualsToNative()
    }

    protected override fun createNativeImpl(factory: NativeFactory): StyledButtonNativeImpl<*> =
        factory.createStyledButton(styledClassPrefix)

    override fun syncStateToNative() {
        super.syncStateToNative()
        syncVisualsToNative()
    }
}
