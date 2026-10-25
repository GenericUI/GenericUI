package com.nexustools.genericui.style.css

import com.nexustools.genericui.style.RgbaColor
import com.nexustools.genericui.style.gradient.Gradient

/**
 * Resolved style properties computed from Bootstrap Enums, custom CSS classes, SASS stylesheets,
 * responsive `@media` queries, pseudo-classes (`:hover`, `:focus`, `:active`), and inline styles.
 */
data class ComputedStyle(
    val tint: RgbaColor? = null,
    val backgroundColor: RgbaColor? = null,
    val backgroundGradient: Gradient? = null,
    val textColor: RgbaColor? = null,
    val borderColor: RgbaColor? = null,
    val borderWidthPx: Float? = null,
    val borderStyle: String? = null,
    val borderRadiusPx: Float? = null,
    val borderRadiusPercent: Float? = null,
    val fontSizePx: Float? = null,
    val fontSizePercent: Float? = null,
    val fontFamily: String? = null,
    val fontWeightNumeric: Int? = null,
    val opacity: Float = 1.0f,
    val backgroundImageUrl: String? = null,
    val backgroundRepeat: String? = null,
    val backgroundAttachment: String? = null,
    val backgroundPosition: String? = null,
    val backgroundSize: String? = null,
    val textShadow: String? = null,
    val boxShadow: String? = null,
    val transform: String? = null,
    val transition: String? = null,
    val zIndex: Int? = null,
    val aspectRatio: Float? = null,
    val pointerEventsEnabled: Boolean = true,
    val cursor: String? = null,
    val position: String? = null,
    val display: String? = null,
    val overflow: String? = null,
    val textAlign: String? = null,
    val verticalAlign: String? = null,
    val whiteSpace: String? = null,
    val floatSide: String? = null,
    val clearSide: String? = null,
    val left: String? = null,
    val top: String? = null,
    val right: String? = null,
    val bottom: String? = null,
    val width: String? = null,
    val height: String? = null,
    val minWidth: String? = null,
    val minHeight: String? = null,
    val maxWidth: String? = null,
    val maxHeight: String? = null,
    val hoverStyle: ComputedStyle? = null,
    val focusStyle: ComputedStyle? = null,
    val activeStyle: ComputedStyle? = null,
    val disabledStyle: ComputedStyle? = null,
    val checkedStyle: ComputedStyle? = null,
    val rawDeclarations: Map<String, String> = emptyMap(),
    val importantProperties: Set<String> = emptySet()
) {
    /**
     * Resolves the effective [ComputedStyle] for the given interactive pseudo-class state flags.
     */
    fun resolveForState(
        hovered: Boolean = false,
        focused: Boolean = false,
        active: Boolean = false,
        enabled: Boolean = true,
        checked: Boolean = false
    ): ComputedStyle {
        var resolved = this
        if (checked && checkedStyle != null) {
            resolved = resolved.mergeWith(checkedStyle)
        }
        if (focused && focusStyle != null) {
            resolved = resolved.mergeWith(focusStyle)
        }
        if (hovered && hoverStyle != null) {
            resolved = resolved.mergeWith(hoverStyle)
        }
        if (active && activeStyle != null) {
            resolved = resolved.mergeWith(activeStyle)
        }
        if (!enabled && disabledStyle != null) {
            resolved = resolved.mergeWith(disabledStyle)
        }
        return resolved
    }

    /**
     * Merges [other] on top of this [ComputedStyle], preferring non-null properties from [other].
     */
    fun mergeWith(other: ComputedStyle): ComputedStyle {
        val combinedDecls = linkedMapOf<String, String>()
        combinedDecls.putAll(rawDeclarations)
        combinedDecls.putAll(other.rawDeclarations)
        return ComputedStyle(
            tint = other.tint ?: tint,
            backgroundColor = other.backgroundColor ?: backgroundColor,
            backgroundGradient = other.backgroundGradient ?: backgroundGradient,
            textColor = other.textColor ?: textColor,
            borderColor = other.borderColor ?: borderColor,
            borderWidthPx = other.borderWidthPx ?: borderWidthPx,
            borderStyle = other.borderStyle ?: borderStyle,
            borderRadiusPx = other.borderRadiusPx ?: borderRadiusPx,
            borderRadiusPercent = other.borderRadiusPercent ?: borderRadiusPercent,
            fontSizePx = other.fontSizePx ?: fontSizePx,
            fontSizePercent = other.fontSizePercent ?: fontSizePercent,
            fontFamily = other.fontFamily ?: fontFamily,
            fontWeightNumeric = other.fontWeightNumeric ?: fontWeightNumeric,
            opacity = if ("opacity" in other.rawDeclarations || other.opacity != 1.0f) other.opacity else opacity,
            backgroundImageUrl = other.backgroundImageUrl ?: backgroundImageUrl,
            backgroundRepeat = other.backgroundRepeat ?: backgroundRepeat,
            backgroundAttachment = other.backgroundAttachment ?: backgroundAttachment,
            backgroundPosition = other.backgroundPosition ?: backgroundPosition,
            backgroundSize = other.backgroundSize ?: backgroundSize,
            textShadow = other.textShadow ?: textShadow,
            boxShadow = other.boxShadow ?: boxShadow,
            transform = other.transform ?: transform,
            transition = other.transition ?: transition,
            zIndex = other.zIndex ?: zIndex,
            aspectRatio = other.aspectRatio ?: aspectRatio,
            pointerEventsEnabled = if ("pointer-events" in other.rawDeclarations || !other.pointerEventsEnabled) {
                other.pointerEventsEnabled
            } else {
                pointerEventsEnabled
            },
            cursor = other.cursor ?: cursor,
            position = other.position ?: position,
            display = other.display ?: display,
            overflow = other.overflow ?: overflow,
            textAlign = other.textAlign ?: textAlign,
            verticalAlign = other.verticalAlign ?: verticalAlign,
            whiteSpace = other.whiteSpace ?: whiteSpace,
            floatSide = other.floatSide ?: floatSide,
            clearSide = other.clearSide ?: clearSide,
            left = other.left ?: left,
            top = other.top ?: top,
            right = other.right ?: right,
            bottom = other.bottom ?: bottom,
            width = other.width ?: width,
            height = other.height ?: height,
            minWidth = other.minWidth ?: minWidth,
            minHeight = other.minHeight ?: minHeight,
            maxWidth = other.maxWidth ?: maxWidth,
            maxHeight = other.maxHeight ?: maxHeight,
            hoverStyle = other.hoverStyle ?: hoverStyle,
            focusStyle = other.focusStyle ?: focusStyle,
            activeStyle = other.activeStyle ?: activeStyle,
            disabledStyle = other.disabledStyle ?: disabledStyle,
            checkedStyle = other.checkedStyle ?: checkedStyle,
            rawDeclarations = combinedDecls,
            importantProperties = importantProperties + other.importantProperties
        )
    }
}
