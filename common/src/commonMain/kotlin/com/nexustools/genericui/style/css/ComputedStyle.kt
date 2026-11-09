package com.nexustools.genericui.style.css

import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.TextOverflow
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.enums.css.CssAlignContent
import com.nexustools.genericui.enums.css.CssBackgroundClip
import com.nexustools.genericui.enums.css.CssBackgroundOrigin
import com.nexustools.genericui.enums.css.CssBoxSizing
import com.nexustools.genericui.enums.css.CssFontStyle
import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.enums.css.CssUserSelect
import com.nexustools.genericui.enums.css.CssVisibility
import com.nexustools.genericui.enums.css.CssWordBreak
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionSpec

/**
 * Strongly-typed resolved CSS style for a component after cascading matching rules and inline styles.
 */
data class ComputedStyle(
    val backgroundColor: RgbaColor? = null,
    val textColor: RgbaColor? = null,
    val borderColor: RgbaColor? = null,
    val borderTopColor: RgbaColor? = null,
    val borderRightColor: RgbaColor? = null,
    val borderBottomColor: RgbaColor? = null,
    val borderLeftColor: RgbaColor? = null,
    val accentColor: RgbaColor? = null,
    val outlineColor: RgbaColor? = null,
    val focusRingColor: RgbaColor? = null,
    val focusRingWidthPx: Float? = null,
    val backgroundGradient: Gradient? = null,
    val backgroundImageUrl: String? = null,
    val backgroundRepeat: String? = null,
    val backgroundAttachment: String? = null,
    val backgroundPosition: String? = null,
    val backgroundSize: String? = null,
    val backgroundClip: CssBackgroundClip? = null,
    val backgroundOrigin: CssBackgroundOrigin? = null,
    val borderRadiusPx: Float? = null,
    val borderRadiusPercent: Float? = null,
    val borderRadiusTopLeftPx: Float? = null,
    val borderRadiusTopRightPx: Float? = null,
    val borderRadiusBottomRightPx: Float? = null,
    val borderRadiusBottomLeftPx: Float? = null,
    val borderRadius: CssCornerRadii? = null,
    val borderWidthPx: Float? = null,
    val borderTopWidthPx: Float? = null,
    val borderRightWidthPx: Float? = null,
    val borderBottomWidthPx: Float? = null,
    val borderLeftWidthPx: Float? = null,
    val borderStyle: String? = null,
    val outlineWidthPx: Float? = null,
    val outlineStyle: String? = null,
    val outlineOffsetPx: Float? = null,
    val padding: CssEdgeLengths? = null,
    val paddingTop: CssLength? = null,
    val paddingRight: CssLength? = null,
    val paddingBottom: CssLength? = null,
    val paddingLeft: CssLength? = null,
    val margin: CssEdgeLengths? = null,
    val marginTop: CssLength? = null,
    val marginRight: CssLength? = null,
    val marginBottom: CssLength? = null,
    val marginLeft: CssLength? = null,
    val boxSizing: CssBoxSizing? = null,
    val fontSizePx: Float? = null,
    val fontSizePercent: Float? = null,
    val fontFamily: String? = null,
    val fontWeightNumeric: Int? = null,
    val fontStyle: CssFontStyle? = null,
    val lineHeight: CssLength? = null,
    val letterSpacing: CssLength? = null,
    val textDecoration: CssTextDecoration? = null,
    val textTransform: TextTransform? = null,
    val textOverflow: TextOverflow? = null,
    val wordBreak: CssWordBreak? = null,
    val opacity: Float = 1.0f,
    val cursor: String? = null,
    val customCursor: CssCursorValue? = null,
    val filter: CssFilter? = null,
    val backdropFilter: CssFilter? = null,
    val boxShadow: String? = null,
    val boxShadowList: CssShadowList? = null,
    val textShadow: String? = null,
    val textShadowList: CssShadowList? = null,
    val transform: String? = null,
    val transform2D: CssTransform2D? = null,
    val transformOrigin: CssBackgroundPosition? = null,
    val transition: String? = null,
    val transitions: List<CssTransitionSpec>? = null,
    val zIndex: Int? = null,
    val aspectRatio: Float? = null,
    val width: String? = null,
    val height: String? = null,
    val minWidth: String? = null,
    val minHeight: String? = null,
    val maxWidth: String? = null,
    val maxHeight: String? = null,
    val top: String? = null,
    val left: String? = null,
    val right: String? = null,
    val bottom: String? = null,
    val display: String? = null,
    val visibility: CssVisibility? = null,
    val position: String? = null,
    val overflow: String? = null,
    val overflowX: OverflowBehavior? = null,
    val overflowY: OverflowBehavior? = null,
    val flexDirection: FlexDirection? = null,
    val flexWrap: FlexWrap? = null,
    val justifyContent: JustifyContent? = null,
    val alignItems: AlignItems? = null,
    val alignSelf: AlignSelf? = null,
    val alignContent: CssAlignContent? = null,
    val flexGrow: Float? = null,
    val flexShrink: Float? = null,
    val flexBasis: CssLength? = null,
    val order: Int? = null,
    val gap: CssLength? = null,
    val rowGap: CssLength? = null,
    val columnGap: CssLength? = null,
    val textAlign: String? = null,
    val verticalAlign: String? = null,
    val whiteSpace: String? = null,
    val floatSide: String? = null,
    val clearSide: String? = null,
    val userSelect: CssUserSelect? = null,
    val objectFit: ObjectFit? = null,
    val pointerEventsEnabled: Boolean = true,
    val hoverStyle: ComputedStyle? = null,
    val focusStyle: ComputedStyle? = null,
    val activeStyle: ComputedStyle? = null,
    val disabledStyle: ComputedStyle? = null,
    val checkedStyle: ComputedStyle? = null,
    val activeBitmaskLow: Long = 0L,
    val activeBitmaskHigh: Long = 0L,
    val importantBitmaskLow: Long = 0L,
    val importantBitmaskHigh: Long = 0L
) {
    fun hasProperty(property: CssProperty): Boolean {
        val ord = property.ordinal
        return if (ord < 64) {
            (activeBitmaskLow and (1L shl ord)) != 0L
        } else {
            (activeBitmaskHigh and (1L shl (ord - 64))) != 0L
        }
    }

    fun isImportant(property: CssProperty): Boolean {
        val ord = property.ordinal
        return if (ord < 64) {
            (importantBitmaskLow and (1L shl ord)) != 0L
        } else {
            (importantBitmaskHigh and (1L shl (ord - 64))) != 0L
        }
    }

    /**
     * Filters out aesthetic skinning (and optionally typography) properties for standard components
     * on system backends.
     */
    fun copyFilteredForSystemComponent(allowTypography: Boolean, allowAestheticSkin: Boolean): ComputedStyle {
        if (allowAestheticSkin || this === EMPTY) return this
        val (maskLow, maskHigh) = systemFilterBitmasks(allowTypography)
        return ComputedStyle(
            backgroundColor = null,
            textColor = if (allowTypography) textColor else null,
            borderColor = null,
            borderTopColor = null,
            borderRightColor = null,
            borderBottomColor = null,
            borderLeftColor = null,
            accentColor = null,
            outlineColor = null,
            focusRingColor = null,
            focusRingWidthPx = null,
            backgroundGradient = null,
            backgroundImageUrl = null,
            backgroundRepeat = null,
            backgroundAttachment = null,
            backgroundPosition = null,
            backgroundSize = null,
            backgroundClip = null,
            backgroundOrigin = null,
            borderRadiusPx = null,
            borderRadiusPercent = null,
            borderRadiusTopLeftPx = null,
            borderRadiusTopRightPx = null,
            borderRadiusBottomRightPx = null,
            borderRadiusBottomLeftPx = null,
            borderRadius = null,
            borderWidthPx = null,
            borderTopWidthPx = null,
            borderRightWidthPx = null,
            borderBottomWidthPx = null,
            borderLeftWidthPx = null,
            borderStyle = null,
            outlineWidthPx = null,
            outlineStyle = null,
            outlineOffsetPx = null,
            padding = padding,
            paddingTop = paddingTop,
            paddingRight = paddingRight,
            paddingBottom = paddingBottom,
            paddingLeft = paddingLeft,
            margin = margin,
            marginTop = marginTop,
            marginRight = marginRight,
            marginBottom = marginBottom,
            marginLeft = marginLeft,
            boxSizing = boxSizing,
            fontSizePx = if (allowTypography) fontSizePx else null,
            fontSizePercent = if (allowTypography) fontSizePercent else null,
            fontFamily = if (allowTypography) fontFamily else null,
            fontWeightNumeric = if (allowTypography) fontWeightNumeric else null,
            fontStyle = if (allowTypography) fontStyle else null,
            lineHeight = if (allowTypography) lineHeight else null,
            letterSpacing = if (allowTypography) letterSpacing else null,
            textDecoration = if (allowTypography) textDecoration else null,
            textTransform = if (allowTypography) textTransform else null,
            textOverflow = if (allowTypography) textOverflow else null,
            wordBreak = if (allowTypography) wordBreak else null,
            opacity = opacity,
            cursor = cursor,
            customCursor = customCursor,
            filter = null,
            backdropFilter = null,
            boxShadow = null,
            boxShadowList = null,
            textShadow = null,
            textShadowList = null,
            transform = transform,
            transform2D = transform2D,
            transformOrigin = transformOrigin,
            transition = transition,
            transitions = transitions,
            zIndex = zIndex,
            aspectRatio = aspectRatio,
            width = width,
            height = height,
            minWidth = minWidth,
            minHeight = minHeight,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            top = top,
            left = left,
            right = right,
            bottom = bottom,
            display = display,
            visibility = visibility,
            position = position,
            overflow = overflow,
            overflowX = overflowX,
            overflowY = overflowY,
            flexDirection = flexDirection,
            flexWrap = flexWrap,
            justifyContent = justifyContent,
            alignItems = alignItems,
            alignSelf = alignSelf,
            alignContent = alignContent,
            flexGrow = flexGrow,
            flexShrink = flexShrink,
            flexBasis = flexBasis,
            order = order,
            gap = gap,
            rowGap = rowGap,
            columnGap = columnGap,
            textAlign = if (allowTypography) textAlign else null,
            verticalAlign = verticalAlign,
            whiteSpace = if (allowTypography) whiteSpace else null,
            floatSide = floatSide,
            clearSide = clearSide,
            userSelect = userSelect,
            objectFit = objectFit,
            pointerEventsEnabled = pointerEventsEnabled,
            hoverStyle = hoverStyle?.copyFilteredForSystemComponent(allowTypography, false),
            focusStyle = focusStyle?.copyFilteredForSystemComponent(allowTypography, false),
            activeStyle = activeStyle?.copyFilteredForSystemComponent(allowTypography, false),
            disabledStyle = disabledStyle?.copyFilteredForSystemComponent(allowTypography, false),
            checkedStyle = checkedStyle?.copyFilteredForSystemComponent(allowTypography, false),
            activeBitmaskLow = activeBitmaskLow and maskLow,
            activeBitmaskHigh = activeBitmaskHigh and maskHigh,
            importantBitmaskLow = importantBitmaskLow and maskLow,
            importantBitmaskHigh = importantBitmaskHigh and maskHigh
        )
    }

    /**
     * Resolves the effective style for a given interactive widget state.
     */
    fun resolveForState(
        hovered: Boolean = false,
        focused: Boolean = false,
        active: Boolean = false,
        enabled: Boolean = true,
        checked: Boolean = false
    ): ComputedStyle {
        var current = this
        if (!enabled) {
            disabledStyle?.let { current = current.mergeWith(it) }
            return current
        }
        if (checked) {
            checkedStyle?.let { current = current.mergeWith(it) }
        }
        if (hovered) {
            hoverStyle?.let { current = current.mergeWith(it) }
        }
        if (focused) {
            focusStyle?.let { current = current.mergeWith(it) }
        }
        if (active) {
            activeStyle?.let { current = current.mergeWith(it) }
        }
        return current
    }

    /**
     * Merges [other] on top of `this`, with non-null properties in [other] taking precedence.
     */
    fun mergeWith(other: ComputedStyle): ComputedStyle {
        if (other === EMPTY) return this
        if (this === EMPTY) return other
        return ComputedStyle(
            backgroundColor = other.backgroundColor ?: backgroundColor,
            textColor = other.textColor ?: textColor,
            borderColor = other.borderColor ?: borderColor,
            borderTopColor = other.borderTopColor ?: borderTopColor,
            borderRightColor = other.borderRightColor ?: borderRightColor,
            borderBottomColor = other.borderBottomColor ?: borderBottomColor,
            borderLeftColor = other.borderLeftColor ?: borderLeftColor,
            accentColor = other.accentColor ?: accentColor,
            outlineColor = other.outlineColor ?: outlineColor,
            focusRingColor = other.focusRingColor ?: focusRingColor,
            focusRingWidthPx = other.focusRingWidthPx ?: focusRingWidthPx,
            backgroundGradient = other.backgroundGradient ?: backgroundGradient,
            backgroundImageUrl = other.backgroundImageUrl ?: backgroundImageUrl,
            backgroundRepeat = other.backgroundRepeat ?: backgroundRepeat,
            backgroundAttachment = other.backgroundAttachment ?: backgroundAttachment,
            backgroundPosition = other.backgroundPosition ?: backgroundPosition,
            backgroundSize = other.backgroundSize ?: backgroundSize,
            backgroundClip = other.backgroundClip ?: backgroundClip,
            backgroundOrigin = other.backgroundOrigin ?: backgroundOrigin,
            borderRadiusPx = other.borderRadiusPx ?: borderRadiusPx,
            borderRadiusPercent = other.borderRadiusPercent ?: borderRadiusPercent,
            borderRadiusTopLeftPx = other.borderRadiusTopLeftPx ?: borderRadiusTopLeftPx,
            borderRadiusTopRightPx = other.borderRadiusTopRightPx ?: borderRadiusTopRightPx,
            borderRadiusBottomRightPx = other.borderRadiusBottomRightPx ?: borderRadiusBottomRightPx,
            borderRadiusBottomLeftPx = other.borderRadiusBottomLeftPx ?: borderRadiusBottomLeftPx,
            borderRadius = other.borderRadius ?: borderRadius,
            borderWidthPx = other.borderWidthPx ?: borderWidthPx,
            borderTopWidthPx = other.borderTopWidthPx ?: borderTopWidthPx,
            borderRightWidthPx = other.borderRightWidthPx ?: borderRightWidthPx,
            borderBottomWidthPx = other.borderBottomWidthPx ?: borderBottomWidthPx,
            borderLeftWidthPx = other.borderLeftWidthPx ?: borderLeftWidthPx,
            borderStyle = other.borderStyle ?: borderStyle,
            outlineWidthPx = other.outlineWidthPx ?: outlineWidthPx,
            outlineStyle = other.outlineStyle ?: outlineStyle,
            outlineOffsetPx = other.outlineOffsetPx ?: outlineOffsetPx,
            padding = other.padding ?: padding,
            paddingTop = other.paddingTop ?: paddingTop,
            paddingRight = other.paddingRight ?: paddingRight,
            paddingBottom = other.paddingBottom ?: paddingBottom,
            paddingLeft = other.paddingLeft ?: paddingLeft,
            margin = other.margin ?: margin,
            marginTop = other.marginTop ?: marginTop,
            marginRight = other.marginRight ?: marginRight,
            marginBottom = other.marginBottom ?: marginBottom,
            marginLeft = other.marginLeft ?: marginLeft,
            boxSizing = other.boxSizing ?: boxSizing,
            fontSizePx = other.fontSizePx ?: fontSizePx,
            fontSizePercent = other.fontSizePercent ?: fontSizePercent,
            fontFamily = other.fontFamily ?: fontFamily,
            fontWeightNumeric = other.fontWeightNumeric ?: fontWeightNumeric,
            fontStyle = other.fontStyle ?: fontStyle,
            lineHeight = other.lineHeight ?: lineHeight,
            letterSpacing = other.letterSpacing ?: letterSpacing,
            textDecoration = other.textDecoration ?: textDecoration,
            textTransform = other.textTransform ?: textTransform,
            textOverflow = other.textOverflow ?: textOverflow,
            wordBreak = other.wordBreak ?: wordBreak,
            opacity = if (other.hasProperty(CssProperty.OPACITY) || other.opacity != 1.0f) other.opacity else opacity,
            cursor = other.cursor ?: cursor,
            customCursor = other.customCursor ?: customCursor,
            filter = other.filter ?: filter,
            backdropFilter = other.backdropFilter ?: backdropFilter,
            boxShadow = other.boxShadow ?: boxShadow,
            boxShadowList = other.boxShadowList ?: boxShadowList,
            textShadow = other.textShadow ?: textShadow,
            textShadowList = other.textShadowList ?: textShadowList,
            transform = other.transform ?: transform,
            transform2D = other.transform2D ?: transform2D,
            transformOrigin = other.transformOrigin ?: transformOrigin,
            transition = other.transition ?: transition,
            transitions = other.transitions ?: transitions,
            zIndex = other.zIndex ?: zIndex,
            aspectRatio = other.aspectRatio ?: aspectRatio,
            width = other.width ?: width,
            height = other.height ?: height,
            minWidth = other.minWidth ?: minWidth,
            minHeight = other.minHeight ?: minHeight,
            maxWidth = other.maxWidth ?: maxWidth,
            maxHeight = other.maxHeight ?: maxHeight,
            top = other.top ?: top,
            left = other.left ?: left,
            right = other.right ?: right,
            bottom = other.bottom ?: bottom,
            display = other.display ?: display,
            visibility = other.visibility ?: visibility,
            position = other.position ?: position,
            overflow = other.overflow ?: overflow,
            overflowX = other.overflowX ?: overflowX,
            overflowY = other.overflowY ?: overflowY,
            flexDirection = other.flexDirection ?: flexDirection,
            flexWrap = other.flexWrap ?: flexWrap,
            justifyContent = other.justifyContent ?: justifyContent,
            alignItems = other.alignItems ?: alignItems,
            alignSelf = other.alignSelf ?: alignSelf,
            alignContent = other.alignContent ?: alignContent,
            flexGrow = other.flexGrow ?: flexGrow,
            flexShrink = other.flexShrink ?: flexShrink,
            flexBasis = other.flexBasis ?: flexBasis,
            order = other.order ?: order,
            gap = other.gap ?: gap,
            rowGap = other.rowGap ?: rowGap,
            columnGap = other.columnGap ?: columnGap,
            textAlign = other.textAlign ?: textAlign,
            verticalAlign = other.verticalAlign ?: verticalAlign,
            whiteSpace = other.whiteSpace ?: whiteSpace,
            floatSide = other.floatSide ?: floatSide,
            clearSide = other.clearSide ?: clearSide,
            userSelect = other.userSelect ?: userSelect,
            objectFit = other.objectFit ?: objectFit,
            pointerEventsEnabled = if (other.hasProperty(CssProperty.POINTER_EVENTS) || !other.pointerEventsEnabled) {
                other.pointerEventsEnabled
            } else {
                pointerEventsEnabled
            },
            hoverStyle = when {
                hoverStyle != null && other.hoverStyle != null -> hoverStyle.mergeWith(other.hoverStyle)
                else -> other.hoverStyle ?: hoverStyle
            },
            focusStyle = when {
                focusStyle != null && other.focusStyle != null -> focusStyle.mergeWith(other.focusStyle)
                else -> other.focusStyle ?: focusStyle
            },
            activeStyle = when {
                activeStyle != null && other.activeStyle != null -> activeStyle.mergeWith(other.activeStyle)
                else -> other.activeStyle ?: activeStyle
            },
            disabledStyle = when {
                disabledStyle != null && other.disabledStyle != null -> disabledStyle.mergeWith(other.disabledStyle)
                else -> other.disabledStyle ?: disabledStyle
            },
            checkedStyle = when {
                checkedStyle != null && other.checkedStyle != null -> checkedStyle.mergeWith(other.checkedStyle)
                else -> other.checkedStyle ?: checkedStyle
            },
            activeBitmaskLow = activeBitmaskLow or other.activeBitmaskLow,
            activeBitmaskHigh = activeBitmaskHigh or other.activeBitmaskHigh,
            importantBitmaskLow = importantBitmaskLow or other.importantBitmaskLow,
            importantBitmaskHigh = importantBitmaskHigh or other.importantBitmaskHigh
        )
    }

    companion object {
        val EMPTY = ComputedStyle()

        internal val LAYOUT_ONLY_PROPERTIES = setOf(
            CssProperty.DISPLAY,
            CssProperty.VISIBILITY,
            CssProperty.POSITION,
            CssProperty.TOP,
            CssProperty.RIGHT,
            CssProperty.BOTTOM,
            CssProperty.LEFT,
            CssProperty.WIDTH,
            CssProperty.HEIGHT,
            CssProperty.MIN_WIDTH,
            CssProperty.MIN_HEIGHT,
            CssProperty.MAX_WIDTH,
            CssProperty.MAX_HEIGHT,
            CssProperty.ASPECT_RATIO,
            CssProperty.BOX_SIZING,
            CssProperty.PADDING,
            CssProperty.PADDING_TOP,
            CssProperty.PADDING_RIGHT,
            CssProperty.PADDING_BOTTOM,
            CssProperty.PADDING_LEFT,
            CssProperty.MARGIN,
            CssProperty.MARGIN_TOP,
            CssProperty.MARGIN_RIGHT,
            CssProperty.MARGIN_BOTTOM,
            CssProperty.MARGIN_LEFT,
            CssProperty.FLOAT,
            CssProperty.CLEAR,
            CssProperty.VERTICAL_ALIGN,
            CssProperty.FLEX_DIRECTION,
            CssProperty.FLEX_WRAP,
            CssProperty.JUSTIFY_CONTENT,
            CssProperty.ALIGN_ITEMS,
            CssProperty.ALIGN_SELF,
            CssProperty.ALIGN_CONTENT,
            CssProperty.FLEX_GROW,
            CssProperty.FLEX_SHRINK,
            CssProperty.FLEX_BASIS,
            CssProperty.ORDER,
            CssProperty.GAP,
            CssProperty.ROW_GAP,
            CssProperty.COLUMN_GAP,
            CssProperty.OVERFLOW,
            CssProperty.OVERFLOW_X,
            CssProperty.OVERFLOW_Y,
            CssProperty.Z_INDEX,
            CssProperty.OPACITY,
            CssProperty.CURSOR,
            CssProperty.POINTER_EVENTS,
            CssProperty.USER_SELECT,
            CssProperty.OBJECT_FIT,
            CssProperty.TRANSFORM,
            CssProperty.TRANSFORM_ORIGIN,
            CssProperty.TRANSITION
        )

        internal val TYPOGRAPHY_PROPERTIES = setOf(
            CssProperty.COLOR,
            CssProperty.FONT_FAMILY,
            CssProperty.FONT_SIZE,
            CssProperty.FONT_WEIGHT,
            CssProperty.FONT_STYLE,
            CssProperty.LINE_HEIGHT,
            CssProperty.LETTER_SPACING,
            CssProperty.TEXT_ALIGN,
            CssProperty.TEXT_DECORATION,
            CssProperty.TEXT_TRANSFORM,
            CssProperty.TEXT_OVERFLOW,
            CssProperty.WHITE_SPACE,
            CssProperty.WORD_BREAK
        )

        private val LAYOUT_MASK_LOW: Long
        private val LAYOUT_MASK_HIGH: Long
        private val LAYOUT_AND_TYPO_MASK_LOW: Long
        private val LAYOUT_AND_TYPO_MASK_HIGH: Long

        init {
            var lLow = 0L
            var lHigh = 0L
            for (p in LAYOUT_ONLY_PROPERTIES) {
                val ord = p.ordinal
                if (ord < 64) lLow = lLow or (1L shl ord) else lHigh = lHigh or (1L shl (ord - 64))
            }
            LAYOUT_MASK_LOW = lLow
            LAYOUT_MASK_HIGH = lHigh

            var tLow = lLow
            var tHigh = lHigh
            for (p in TYPOGRAPHY_PROPERTIES) {
                val ord = p.ordinal
                if (ord < 64) tLow = tLow or (1L shl ord) else tHigh = tHigh or (1L shl (ord - 64))
            }
            LAYOUT_AND_TYPO_MASK_LOW = tLow
            LAYOUT_AND_TYPO_MASK_HIGH = tHigh
        }

        internal fun systemFilterBitmasks(allowTypography: Boolean): Pair<Long, Long> =
            if (allowTypography) {
                LAYOUT_AND_TYPO_MASK_LOW to LAYOUT_AND_TYPO_MASK_HIGH
            } else {
                LAYOUT_MASK_LOW to LAYOUT_MASK_HIGH
            }
    }
}
