package com.nexustools.genericui.style.state

import com.nexustools.genericui.style.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.gradient.Gradient
import com.nexustools.genericui.style.gradient.GradientStop
import com.nexustools.genericui.style.gradient.LinearGradient
import com.nexustools.genericui.style.gradient.RadialGradient
import com.nexustools.genericui.style.gradient.RadialGradientShape

/**
 * Declarative style block for a GenericUI component (`style { ... }`), supporting native component
 * color tinting ([tint]), solid or [Gradient] backgrounds ([backgroundColor], [backgroundGradient], [background]),
 * borders, cursors, and nested CSS pseudo-class state blocks
 * ([hover], [focus], [active], [disabled], [checked], [focusVisible], [visited]).
 *
 * Example:
 * ```kotlin
 * button("Open") {
 *     style {
 *         tint = ColorRgba(0, 255, 0, 1.0)
 *         background = LinearGradient(135f, ColorRgba(37, 99, 235), ColorRgba(29, 78, 216))
 *         hover {
 *             tint = ColorRgba(255, 0, 0, 1.0)
 *             background = RadialGradient(ColorRgba(59, 130, 246), ColorRgba(30, 64, 175))
 *         }
 *     }
 * }
 * ```
 */
class ComponentStyleBlock {
    var tint: RgbaColor? = null
    var backgroundColor: RgbaColor? = null
    var backgroundGradient: Gradient? = null

    /**
     * Shorthand background property accepting an [RgbaColor], a [Gradient] ([LinearGradient] or [RadialGradient]),
     * or a CSS color/gradient string.
     */
    var background: Any?
        get() = backgroundGradient ?: backgroundColor
        set(value) {
            when (value) {
                null -> {
                    backgroundColor = null
                    backgroundGradient = null
                }
                is Gradient -> backgroundGradient = value
                is RgbaColor -> backgroundColor = value
                is String -> {
                    val grad = Gradient.fromCss(value)
                    if (grad != null) {
                        backgroundGradient = grad
                    } else {
                        RgbaColor.fromCss(value)?.let { backgroundColor = it }
                    }
                }
            }
        }

    var textColor: RgbaColor? = null
    var borderColor: RgbaColor? = null
    var borderWidthPx: Float? = null
    var borderStyle: String? = null
    var borderRadiusPx: Float? = null
    var fontSizePx: Float? = null
    var fontFamily: String? = null
    var fontWeightNumeric: Int? = null
    var opacity: Float? = null
    var cursor: String? = null
    var boxShadow: String? = null
    var textShadow: String? = null
    var transform: String? = null
    var transition: String? = null

    private var hoverBlock: ComponentStyleBlock? = null
    private var focusBlock: ComponentStyleBlock? = null
    private var activeBlock: ComponentStyleBlock? = null
    private var disabledBlock: ComponentStyleBlock? = null
    private var checkedBlock: ComponentStyleBlock? = null

    fun tint(color: RgbaColor) {
        this.tint = color
    }

    fun backgroundColor(color: RgbaColor) {
        this.backgroundColor = color
    }

    fun backgroundColor(gradient: Gradient) {
        this.backgroundGradient = gradient
    }

    fun backgroundGradient(gradient: Gradient) {
        this.backgroundGradient = gradient
    }

    fun background(color: RgbaColor) {
        this.backgroundColor = color
    }

    fun background(gradient: Gradient) {
        this.backgroundGradient = gradient
    }

    fun linearGradient(angleDegrees: Number = 180f, vararg colors: RgbaColor): LinearGradient =
        LinearGradient(angleDegrees, *colors).also { this.backgroundGradient = it }

    fun linearGradient(angleDegrees: Number = 180f, vararg stops: GradientStop, repeating: Boolean = false): LinearGradient =
        LinearGradient(angleDegrees, *stops, repeating = repeating).also { this.backgroundGradient = it }

    fun radialGradient(vararg colors: RgbaColor): RadialGradient =
        RadialGradient(*colors).also { this.backgroundGradient = it }

    fun radialGradient(shape: RadialGradientShape, vararg colors: RgbaColor): RadialGradient =
        RadialGradient(shape, *colors).also { this.backgroundGradient = it }

    fun radialGradient(vararg stops: GradientStop, repeating: Boolean = false): RadialGradient =
        RadialGradient(*stops, repeating = repeating).also { this.backgroundGradient = it }

    fun textColor(color: RgbaColor) {
        this.textColor = color
    }

    fun borderColor(color: RgbaColor) {
        this.borderColor = color
    }

    fun cursor(cursorName: String) {
        this.cursor = cursorName
    }

    fun hover(block: ComponentStyleBlock.() -> Unit) {
        val sub = hoverBlock ?: ComponentStyleBlock().also { hoverBlock = it }
        sub.block()
    }

    fun focus(block: ComponentStyleBlock.() -> Unit) {
        val sub = focusBlock ?: ComponentStyleBlock().also { focusBlock = it }
        sub.block()
    }

    fun active(block: ComponentStyleBlock.() -> Unit) {
        val sub = activeBlock ?: ComponentStyleBlock().also { activeBlock = it }
        sub.block()
    }

    fun disabled(block: ComponentStyleBlock.() -> Unit) {
        val sub = disabledBlock ?: ComponentStyleBlock().also { disabledBlock = it }
        sub.block()
    }

    fun checked(block: ComponentStyleBlock.() -> Unit) {
        val sub = checkedBlock ?: ComponentStyleBlock().also { checkedBlock = it }
        sub.block()
    }

    fun focusVisible(block: ComponentStyleBlock.() -> Unit) {
        focus(block)
    }

    fun visited(block: ComponentStyleBlock.() -> Unit) {
        val sub = hoverBlock ?: ComponentStyleBlock().also { hoverBlock = it }
        sub.block()
    }

    fun state(pseudo: CssPseudoState, block: ComponentStyleBlock.() -> Unit) {
        when (pseudo) {
            CssPseudoState.DEFAULT -> this.block()
            CssPseudoState.HOVER, CssPseudoState.VISITED -> hover(block)
            CssPseudoState.FOCUS, CssPseudoState.FOCUS_VISIBLE -> focus(block)
            CssPseudoState.ACTIVE -> active(block)
            CssPseudoState.DISABLED -> disabled(block)
            CssPseudoState.CHECKED -> checked(block)
        }
    }

    fun toComputedStyle(existing: ComputedStyle? = null): ComputedStyle {
        val decls = linkedMapOf<String, String>()
        if (existing != null) {
            decls.putAll(existing.rawDeclarations)
        }
        tint?.let { decls["tint"] = it.toCssRgbaString() }
        backgroundColor?.let { decls["background-color"] = it.toCssRgbaString() }
        backgroundGradient?.let { decls["background-image"] = it.toCssString() }
        textColor?.let { decls["color"] = it.toCssRgbaString() }
        borderColor?.let { decls["border-color"] = it.toCssRgbaString() }
        opacity?.let { decls["opacity"] = it.toString() }
        cursor?.let { decls["cursor"] = it }

        val base = ComputedStyle(
            tint = tint ?: existing?.tint,
            backgroundColor = backgroundColor ?: existing?.backgroundColor,
            backgroundGradient = backgroundGradient ?: existing?.backgroundGradient,
            textColor = textColor ?: existing?.textColor,
            borderColor = borderColor ?: existing?.borderColor,
            borderWidthPx = borderWidthPx ?: existing?.borderWidthPx,
            borderStyle = borderStyle ?: existing?.borderStyle,
            borderRadiusPx = borderRadiusPx ?: existing?.borderRadiusPx,
            fontSizePx = fontSizePx ?: existing?.fontSizePx,
            fontFamily = fontFamily ?: existing?.fontFamily,
            fontWeightNumeric = fontWeightNumeric ?: existing?.fontWeightNumeric,
            opacity = opacity ?: existing?.opacity ?: 1.0f,
            cursor = cursor ?: existing?.cursor,
            boxShadow = boxShadow ?: existing?.boxShadow,
            textShadow = textShadow ?: existing?.textShadow,
            transform = transform ?: existing?.transform,
            transition = transition ?: existing?.transition,
            hoverStyle = hoverBlock?.toComputedStyle(existing?.hoverStyle) ?: existing?.hoverStyle,
            focusStyle = focusBlock?.toComputedStyle(existing?.focusStyle) ?: existing?.focusStyle,
            activeStyle = activeBlock?.toComputedStyle(existing?.activeStyle) ?: existing?.activeStyle,
            disabledStyle = disabledBlock?.toComputedStyle(existing?.disabledStyle) ?: existing?.disabledStyle,
            checkedStyle = checkedBlock?.toComputedStyle(existing?.checkedStyle) ?: existing?.checkedStyle,
            rawDeclarations = decls
        )
        return if (existing != null) existing.mergeWith(base) else base
    }
}
