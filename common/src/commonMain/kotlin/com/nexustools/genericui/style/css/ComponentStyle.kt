package com.nexustools.genericui.style.css

import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextOverflow
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.enums.css.CssAlignContent
import com.nexustools.genericui.enums.css.CssBackgroundAttachment
import com.nexustools.genericui.enums.css.CssBackgroundClip
import com.nexustools.genericui.enums.css.CssBackgroundOrigin
import com.nexustools.genericui.enums.css.CssBackgroundRepeat
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.css.CssBoxSizing
import com.nexustools.genericui.enums.css.CssClear
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.enums.css.CssFloat
import com.nexustools.genericui.enums.css.CssFontStyle
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.enums.css.CssUnit
import com.nexustools.genericui.enums.css.CssUserSelect
import com.nexustools.genericui.enums.css.CssVerticalAlign
import com.nexustools.genericui.enums.css.CssVisibility
import com.nexustools.genericui.enums.css.CssWhiteSpace
import com.nexustools.genericui.enums.css.CssWordBreak
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.RgbaColor
import com.nexustools.genericui.style.gradient.Gradient
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionSpec
import kotlin.jvm.JvmName

/**
 * Strongly-typed inline CSS `style` object (`component.getStyle()`) modeled after W3C CSS inline declarations.
 *
 * - Strictly represents inline element styles (`style="..."`): pseudo-states (`:hover`, `:focus`,
 *   `:active`, `:disabled`, `:checked`) are NOT part of inline style and belong in stylesheets.
 * - Every setter and mutable property accepts `null` to clear/unset that CSS property.
 * - Supports per-property `!important` flags via `important = true` and [setImportant].
 */
class ComponentStyle(
    internal var onChange: (() -> Unit)? = null
) {
    private val importantSet = linkedSetOf<CssProperty>()
    private val rawImportantCustomProps = linkedSetOf<String>()
    private val rawCustomDeclarations = linkedMapOf<String, String>()
    private var defaultImportantScope: Boolean = false

    private var _backgroundColor: RgbaColor? = null
    private var _backgroundImage: CssBackgroundImage? = null
    private var _backgroundSize: CssBackgroundSize? = null
    private var _backgroundPosition: CssBackgroundPosition? = null
    private var _backgroundRepeat: CssBackgroundRepeat? = null
    private var _backgroundAttachment: CssBackgroundAttachment? = null
    private var _backgroundClip: CssBackgroundClip? = null
    private var _backgroundOrigin: CssBackgroundOrigin? = null
    private var _textColor: RgbaColor? = null
    private var _accentColor: RgbaColor? = null
    private var _opacity: Float? = null

    private var _display: CssDisplay? = null
    private var _visibility: CssVisibility? = null
    private var _position: CssPosition? = null
    private var _top: CssLength? = null
    private var _right: CssLength? = null
    private var _bottom: CssLength? = null
    private var _left: CssLength? = null
    private var _width: CssLength? = null
    private var _height: CssLength? = null
    private var _minWidth: CssLength? = null
    private var _minHeight: CssLength? = null
    private var _maxWidth: CssLength? = null
    private var _maxHeight: CssLength? = null
    private var _margin: CssEdgeLengths? = null
    private var _marginTop: CssLength? = null
    private var _marginRight: CssLength? = null
    private var _marginBottom: CssLength? = null
    private var _marginLeft: CssLength? = null
    private var _padding: CssEdgeLengths? = null
    private var _paddingTop: CssLength? = null
    private var _paddingRight: CssLength? = null
    private var _paddingBottom: CssLength? = null
    private var _paddingLeft: CssLength? = null
    private var _boxSizing: CssBoxSizing? = null
    private var _floatSide: CssFloat? = null
    private var _clearSide: CssClear? = null

    private var _borderWidth: CssEdgeLengths? = null
    private var _borderStyle: CssBorderStyle? = null
    private var _borderColor: RgbaColor? = null
    private var _borderRadius: CssCornerRadii? = null
    private var _boxShadow: CssShadowList? = null

    private var _fontFamily: CssFontFamily? = null
    private var _fontSize: CssLength? = null
    private var _fontWeight: FontWeight? = null
    private var _fontWeightNumericOverride: Int? = null
    private var _fontStyle: CssFontStyle? = null
    private var _lineHeight: CssLength? = null
    private var _letterSpacing: CssLength? = null
    private var _textAlign: TextAlignment? = null
    private var _textDecoration: CssTextDecoration? = null
    private var _textTransform: TextTransform? = null
    private var _textOverflow: TextOverflow? = null
    private var _whiteSpace: CssWhiteSpace? = null
    private var _wordBreak: CssWordBreak? = null
    private var _verticalAlign: CssVerticalAlign? = null
    private var _textShadow: CssShadowList? = null

    private var _flexDirection: FlexDirection? = null
    private var _flexWrap: FlexWrap? = null
    private var _justifyContent: JustifyContent? = null
    private var _alignItems: AlignItems? = null
    private var _alignSelf: AlignSelf? = null
    private var _alignContent: CssAlignContent? = null
    private var _flexGrow: Float? = null
    private var _flexShrink: Float? = null
    private var _flexBasis: CssLength? = null
    private var _gap: CssLength? = null
    private var _rowGap: CssLength? = null
    private var _columnGap: CssLength? = null

    private var _overflow: OverflowBehavior? = null
    private var _overflowX: OverflowBehavior? = null
    private var _overflowY: OverflowBehavior? = null
    private var _cursor: CssCursorValue? = null
    private var _pointerEvents: CssPointerEvents? = null
    private var _userSelect: CssUserSelect? = null
    private var _objectFit: ObjectFit? = null
    private var _aspectRatio: CssAspectRatio? = null
    private var _zIndex: Int? = null
    private var _transform: CssTransform2D? = null
    private var _transformOrigin: CssBackgroundPosition? = null
    private var _transitions: List<CssTransitionSpec>? = null
    private var _filter: CssFilter? = null
    private var _backdropFilter: CssFilter? = null

    private fun updateImportance(property: CssProperty, hasValue: Boolean, explicitImportant: Boolean) {
        if (!hasValue) {
            importantSet.remove(property)
        } else if (explicitImportant || defaultImportantScope) {
            importantSet.add(property)
        } else {
            importantSet.remove(property)
        }
    }

    private fun notifyChanged() {
        onChange?.invoke()
    }

    // -------------------------------------------------------------------------
    // !important Support
    // -------------------------------------------------------------------------

    fun isImportant(property: CssProperty): Boolean = property in importantSet

    fun setImportant(property: CssProperty, important: Boolean) {
        val changed = if (important) importantSet.add(property) else importantSet.remove(property)
        if (changed) notifyChanged()
    }

    fun getImportantProperties(): Set<CssProperty> = importantSet.toSet()

    fun isDefaultImportant(): Boolean = defaultImportantScope

    fun setDefaultImportant(important: Boolean) {
        defaultImportantScope = important
    }

    // -------------------------------------------------------------------------
    // 1. Colors, Gradients, Backgrounds, & Opacity
    // -------------------------------------------------------------------------

    fun getBackgroundColor(): RgbaColor? = _backgroundColor
    fun setBackgroundColor(color: RgbaColor?, important: Boolean = false) {
        _backgroundColor = color
        updateImportance(CssProperty.BACKGROUND_COLOR, color != null, important)
        notifyChanged()
    }

    var backgroundColor: RgbaColor?
        @JvmName("obtainBackgroundColor") get() = _backgroundColor
        @JvmName("assignBackgroundColor") set(value) = setBackgroundColor(value)

    fun getBackgroundImage(): CssBackgroundImage? = _backgroundImage
    fun setBackgroundImage(image: CssBackgroundImage?, important: Boolean = false) {
        _backgroundImage = image
        updateImportance(CssProperty.BACKGROUND_IMAGE, image != null, important)
        notifyChanged()
    }

    var backgroundImage: CssBackgroundImage?
        @JvmName("obtainBackgroundImage") get() = _backgroundImage
        @JvmName("assignBackgroundImage") set(value) = setBackgroundImage(value)

    /**
     * Convenience property for gradient backgrounds (`style { background = LinearGradient(...) }`).
     */
    var background: Gradient?
        @JvmName("obtainBackgroundGradient") get() = _backgroundImage?.gradient
        @JvmName("assignBackgroundGradient") set(value) {
            setBackgroundImage(value?.let { CssBackgroundImage.fromGradient(it) })
        }

    fun getBackgroundSize(): CssBackgroundSize? = _backgroundSize
    fun setBackgroundSize(size: CssBackgroundSize?, important: Boolean = false) {
        _backgroundSize = size
        updateImportance(CssProperty.BACKGROUND_SIZE, size != null, important)
        notifyChanged()
    }

    var backgroundSize: CssBackgroundSize?
        @JvmName("obtainBackgroundSize") get() = _backgroundSize
        @JvmName("assignBackgroundSize") set(value) = setBackgroundSize(value)

    fun getBackgroundPosition(): CssBackgroundPosition? = _backgroundPosition
    fun setBackgroundPosition(position: CssBackgroundPosition?, important: Boolean = false) {
        _backgroundPosition = position
        updateImportance(CssProperty.BACKGROUND_POSITION, position != null, important)
        notifyChanged()
    }

    var backgroundPosition: CssBackgroundPosition?
        @JvmName("obtainBackgroundPosition") get() = _backgroundPosition
        @JvmName("assignBackgroundPosition") set(value) = setBackgroundPosition(value)

    fun getBackgroundRepeat(): CssBackgroundRepeat? = _backgroundRepeat
    fun setBackgroundRepeat(repeat: CssBackgroundRepeat?, important: Boolean = false) {
        _backgroundRepeat = repeat
        updateImportance(CssProperty.BACKGROUND_REPEAT, repeat != null, important)
        notifyChanged()
    }

    var backgroundRepeat: CssBackgroundRepeat?
        @JvmName("obtainBackgroundRepeat") get() = _backgroundRepeat
        @JvmName("assignBackgroundRepeat") set(value) = setBackgroundRepeat(value)

    fun getBackgroundAttachment(): CssBackgroundAttachment? = _backgroundAttachment
    fun setBackgroundAttachment(attachment: CssBackgroundAttachment?, important: Boolean = false) {
        _backgroundAttachment = attachment
        updateImportance(CssProperty.BACKGROUND_ATTACHMENT, attachment != null, important)
        notifyChanged()
    }

    var backgroundAttachment: CssBackgroundAttachment?
        @JvmName("obtainBackgroundAttachment") get() = _backgroundAttachment
        @JvmName("assignBackgroundAttachment") set(value) = setBackgroundAttachment(value)

    fun getBackgroundClip(): CssBackgroundClip? = _backgroundClip
    fun setBackgroundClip(clip: CssBackgroundClip?, important: Boolean = false) {
        _backgroundClip = clip
        updateImportance(CssProperty.BACKGROUND_CLIP, clip != null, important)
        notifyChanged()
    }

    var backgroundClip: CssBackgroundClip?
        @JvmName("obtainBackgroundClip") get() = _backgroundClip
        @JvmName("assignBackgroundClip") set(value) = setBackgroundClip(value)

    fun getBackgroundOrigin(): CssBackgroundOrigin? = _backgroundOrigin
    fun setBackgroundOrigin(origin: CssBackgroundOrigin?, important: Boolean = false) {
        _backgroundOrigin = origin
        updateImportance(CssProperty.BACKGROUND_ORIGIN, origin != null, important)
        notifyChanged()
    }

    var backgroundOrigin: CssBackgroundOrigin?
        @JvmName("obtainBackgroundOrigin") get() = _backgroundOrigin
        @JvmName("assignBackgroundOrigin") set(value) = setBackgroundOrigin(value)

    fun getTextColor(): RgbaColor? = _textColor
    fun setTextColor(color: RgbaColor?, important: Boolean = false) {
        _textColor = color
        updateImportance(CssProperty.COLOR, color != null, important)
        notifyChanged()
    }

    fun getColor(): RgbaColor? = _textColor
    fun setColor(color: RgbaColor?, important: Boolean = false) = setTextColor(color, important)

    var textColor: RgbaColor?
        @JvmName("obtainTextColor") get() = _textColor
        @JvmName("assignTextColor") set(value) = setTextColor(value)

    var color: RgbaColor?
        @JvmName("obtainColor") get() = _textColor
        @JvmName("assignColor") set(value) = setTextColor(value)

    fun getAccentColor(): RgbaColor? = _accentColor
    fun setAccentColor(color: RgbaColor?, important: Boolean = false) {
        _accentColor = color
        updateImportance(CssProperty.ACCENT_COLOR, color != null, important)
        notifyChanged()
    }

    var accentColor: RgbaColor?
        @JvmName("obtainAccentColor") get() = _accentColor
        @JvmName("assignAccentColor") set(value) = setAccentColor(value)

    fun getOpacity(): Float? = _opacity
    fun setOpacity(value: Float?, important: Boolean = false) {
        _opacity = value?.coerceIn(0f, 1f)
        updateImportance(CssProperty.OPACITY, _opacity != null, important)
        notifyChanged()
    }

    var opacity: Float?
        @JvmName("obtainOpacity") get() = _opacity
        @JvmName("assignOpacity") set(value) = setOpacity(value)

    // -------------------------------------------------------------------------
    // 2. Box Model, Dimensions, & Positioning
    // -------------------------------------------------------------------------

    fun getDisplay(): CssDisplay? = _display
    fun setDisplay(display: CssDisplay?, important: Boolean = false) {
        _display = display
        updateImportance(CssProperty.DISPLAY, display != null, important)
        notifyChanged()
    }

    var display: CssDisplay?
        @JvmName("obtainDisplay") get() = _display
        @JvmName("assignDisplay") set(value) = setDisplay(value)

    fun getVisibility(): CssVisibility? = _visibility
    fun setVisibility(visibility: CssVisibility?, important: Boolean = false) {
        _visibility = visibility
        updateImportance(CssProperty.VISIBILITY, visibility != null, important)
        notifyChanged()
    }

    var visibility: CssVisibility?
        @JvmName("obtainVisibility") get() = _visibility
        @JvmName("assignVisibility") set(value) = setVisibility(value)

    fun getPosition(): CssPosition? = _position
    fun setPosition(position: CssPosition?, important: Boolean = false) {
        _position = position
        updateImportance(CssProperty.POSITION, position != null, important)
        notifyChanged()
    }

    var position: CssPosition?
        @JvmName("obtainPosition") get() = _position
        @JvmName("assignPosition") set(value) = setPosition(value)

    fun getTop(): CssLength? = _top
    fun setTop(top: CssLength?, important: Boolean = false) {
        _top = top
        updateImportance(CssProperty.TOP, top != null, important)
        notifyChanged()
    }

    var top: CssLength?
        @JvmName("obtainTop") get() = _top
        @JvmName("assignTop") set(value) = setTop(value)

    fun getRight(): CssLength? = _right
    fun setRight(right: CssLength?, important: Boolean = false) {
        _right = right
        updateImportance(CssProperty.RIGHT, right != null, important)
        notifyChanged()
    }

    var right: CssLength?
        @JvmName("obtainRight") get() = _right
        @JvmName("assignRight") set(value) = setRight(value)

    fun getBottom(): CssLength? = _bottom
    fun setBottom(bottom: CssLength?, important: Boolean = false) {
        _bottom = bottom
        updateImportance(CssProperty.BOTTOM, bottom != null, important)
        notifyChanged()
    }

    var bottom: CssLength?
        @JvmName("obtainBottom") get() = _bottom
        @JvmName("assignBottom") set(value) = setBottom(value)

    fun getLeft(): CssLength? = _left
    fun setLeft(left: CssLength?, important: Boolean = false) {
        _left = left
        updateImportance(CssProperty.LEFT, left != null, important)
        notifyChanged()
    }

    var left: CssLength?
        @JvmName("obtainLeft") get() = _left
        @JvmName("assignLeft") set(value) = setLeft(value)

    fun getWidth(): CssLength? = _width
    fun setWidth(width: CssLength?, important: Boolean = false) {
        _width = width
        updateImportance(CssProperty.WIDTH, width != null, important)
        notifyChanged()
    }

    var width: CssLength?
        @JvmName("obtainWidth") get() = _width
        @JvmName("assignWidth") set(value) = setWidth(value)

    fun getHeight(): CssLength? = _height
    fun setHeight(height: CssLength?, important: Boolean = false) {
        _height = height
        updateImportance(CssProperty.HEIGHT, height != null, important)
        notifyChanged()
    }

    var height: CssLength?
        @JvmName("obtainHeight") get() = _height
        @JvmName("assignHeight") set(value) = setHeight(value)

    fun getMinWidth(): CssLength? = _minWidth
    fun setMinWidth(minWidth: CssLength?, important: Boolean = false) {
        _minWidth = minWidth
        updateImportance(CssProperty.MIN_WIDTH, minWidth != null, important)
        notifyChanged()
    }

    var minWidth: CssLength?
        @JvmName("obtainMinWidth") get() = _minWidth
        @JvmName("assignMinWidth") set(value) = setMinWidth(value)

    fun getMinHeight(): CssLength? = _minHeight
    fun setMinHeight(minHeight: CssLength?, important: Boolean = false) {
        _minHeight = minHeight
        updateImportance(CssProperty.MIN_HEIGHT, minHeight != null, important)
        notifyChanged()
    }

    var minHeight: CssLength?
        @JvmName("obtainMinHeight") get() = _minHeight
        @JvmName("assignMinHeight") set(value) = setMinHeight(value)

    fun getMaxWidth(): CssLength? = _maxWidth
    fun setMaxWidth(maxWidth: CssLength?, important: Boolean = false) {
        _maxWidth = maxWidth
        updateImportance(CssProperty.MAX_WIDTH, maxWidth != null, important)
        notifyChanged()
    }

    var maxWidth: CssLength?
        @JvmName("obtainMaxWidth") get() = _maxWidth
        @JvmName("assignMaxWidth") set(value) = setMaxWidth(value)

    fun getMaxHeight(): CssLength? = _maxHeight
    fun setMaxHeight(maxHeight: CssLength?, important: Boolean = false) {
        _maxHeight = maxHeight
        updateImportance(CssProperty.MAX_HEIGHT, maxHeight != null, important)
        notifyChanged()
    }

    var maxHeight: CssLength?
        @JvmName("obtainMaxHeight") get() = _maxHeight
        @JvmName("assignMaxHeight") set(value) = setMaxHeight(value)

    fun getMargin(): CssEdgeLengths? = _margin
    fun setMargin(margin: CssEdgeLengths?, important: Boolean = false) {
        _margin = margin
        updateImportance(CssProperty.MARGIN, margin != null, important)
        notifyChanged()
    }

    var margin: CssEdgeLengths?
        @JvmName("obtainMargin") get() = _margin
        @JvmName("assignMargin") set(value) = setMargin(value)

    var marginPx: EdgeInsets?
        @JvmName("obtainMarginPx") get() = _margin?.toEdgeInsets()
        @JvmName("assignMarginPx") set(value) = setMargin(value?.let { CssEdgeLengths.fromEdgeInsets(it) })

    fun getMarginTop(): CssLength? = _marginTop
    fun setMarginTop(marginTop: CssLength?, important: Boolean = false) {
        _marginTop = marginTop
        updateImportance(CssProperty.MARGIN_TOP, marginTop != null, important)
        notifyChanged()
    }

    var marginTop: CssLength?
        @JvmName("obtainMarginTop") get() = _marginTop
        @JvmName("assignMarginTop") set(value) = setMarginTop(value)

    fun getMarginRight(): CssLength? = _marginRight
    fun setMarginRight(marginRight: CssLength?, important: Boolean = false) {
        _marginRight = marginRight
        updateImportance(CssProperty.MARGIN_RIGHT, marginRight != null, important)
        notifyChanged()
    }

    var marginRight: CssLength?
        @JvmName("obtainMarginRight") get() = _marginRight
        @JvmName("assignMarginRight") set(value) = setMarginRight(value)

    fun getMarginBottom(): CssLength? = _marginBottom
    fun setMarginBottom(marginBottom: CssLength?, important: Boolean = false) {
        _marginBottom = marginBottom
        updateImportance(CssProperty.MARGIN_BOTTOM, marginBottom != null, important)
        notifyChanged()
    }

    var marginBottom: CssLength?
        @JvmName("obtainMarginBottom") get() = _marginBottom
        @JvmName("assignMarginBottom") set(value) = setMarginBottom(value)

    fun getMarginLeft(): CssLength? = _marginLeft
    fun setMarginLeft(marginLeft: CssLength?, important: Boolean = false) {
        _marginLeft = marginLeft
        updateImportance(CssProperty.MARGIN_LEFT, marginLeft != null, important)
        notifyChanged()
    }

    var marginLeft: CssLength?
        @JvmName("obtainMarginLeft") get() = _marginLeft
        @JvmName("assignMarginLeft") set(value) = setMarginLeft(value)

    fun getPadding(): CssEdgeLengths? = _padding
    fun setPadding(padding: CssEdgeLengths?, important: Boolean = false) {
        _padding = padding
        updateImportance(CssProperty.PADDING, padding != null, important)
        notifyChanged()
    }

    var padding: CssEdgeLengths?
        @JvmName("obtainPadding") get() = _padding
        @JvmName("assignPadding") set(value) = setPadding(value)

    var paddingPx: EdgeInsets?
        @JvmName("obtainPaddingPx") get() = _padding?.toEdgeInsets()
        @JvmName("assignPaddingPx") set(value) = setPadding(value?.let { CssEdgeLengths.fromEdgeInsets(it) })

    fun getPaddingTop(): CssLength? = _paddingTop
    fun setPaddingTop(paddingTop: CssLength?, important: Boolean = false) {
        _paddingTop = paddingTop
        updateImportance(CssProperty.PADDING_TOP, paddingTop != null, important)
        notifyChanged()
    }

    var paddingTop: CssLength?
        @JvmName("obtainPaddingTop") get() = _paddingTop
        @JvmName("assignPaddingTop") set(value) = setPaddingTop(value)

    fun getPaddingRight(): CssLength? = _paddingRight
    fun setPaddingRight(paddingRight: CssLength?, important: Boolean = false) {
        _paddingRight = paddingRight
        updateImportance(CssProperty.PADDING_RIGHT, paddingRight != null, important)
        notifyChanged()
    }

    var paddingRight: CssLength?
        @JvmName("obtainPaddingRight") get() = _paddingRight
        @JvmName("assignPaddingRight") set(value) = setPaddingRight(value)

    fun getPaddingBottom(): CssLength? = _paddingBottom
    fun setPaddingBottom(paddingBottom: CssLength?, important: Boolean = false) {
        _paddingBottom = paddingBottom
        updateImportance(CssProperty.PADDING_BOTTOM, paddingBottom != null, important)
        notifyChanged()
    }

    var paddingBottom: CssLength?
        @JvmName("obtainPaddingBottom") get() = _paddingBottom
        @JvmName("assignPaddingBottom") set(value) = setPaddingBottom(value)

    fun getPaddingLeft(): CssLength? = _paddingLeft
    fun setPaddingLeft(paddingLeft: CssLength?, important: Boolean = false) {
        _paddingLeft = paddingLeft
        updateImportance(CssProperty.PADDING_LEFT, paddingLeft != null, important)
        notifyChanged()
    }

    var paddingLeft: CssLength?
        @JvmName("obtainPaddingLeft") get() = _paddingLeft
        @JvmName("assignPaddingLeft") set(value) = setPaddingLeft(value)

    fun getBoxSizing(): CssBoxSizing? = _boxSizing
    fun setBoxSizing(boxSizing: CssBoxSizing?, important: Boolean = false) {
        _boxSizing = boxSizing
        updateImportance(CssProperty.BOX_SIZING, boxSizing != null, important)
        notifyChanged()
    }

    var boxSizing: CssBoxSizing?
        @JvmName("obtainBoxSizing") get() = _boxSizing
        @JvmName("assignBoxSizing") set(value) = setBoxSizing(value)

    fun getFloatSide(): CssFloat? = _floatSide
    fun setFloatSide(floatSide: CssFloat?, important: Boolean = false) {
        _floatSide = floatSide
        updateImportance(CssProperty.FLOAT, floatSide != null, important)
        notifyChanged()
    }

    var floatSide: CssFloat?
        @JvmName("obtainFloatSide") get() = _floatSide
        @JvmName("assignFloatSide") set(value) = setFloatSide(value)

    fun getClearSide(): CssClear? = _clearSide
    fun setClearSide(clearSide: CssClear?, important: Boolean = false) {
        _clearSide = clearSide
        updateImportance(CssProperty.CLEAR, clearSide != null, important)
        notifyChanged()
    }

    var clearSide: CssClear?
        @JvmName("obtainClearSide") get() = _clearSide
        @JvmName("assignClearSide") set(value) = setClearSide(value)

    // -------------------------------------------------------------------------
    // 3. Borders, Corner Radii, & Shadows
    // -------------------------------------------------------------------------

    fun getBorderWidth(): CssEdgeLengths? = _borderWidth
    fun setBorderWidth(borderWidth: CssEdgeLengths?, important: Boolean = false) {
        _borderWidth = borderWidth
        updateImportance(CssProperty.BORDER_WIDTH, borderWidth != null, important)
        notifyChanged()
    }

    var borderWidth: CssEdgeLengths?
        @JvmName("obtainBorderWidth") get() = _borderWidth
        @JvmName("assignBorderWidth") set(value) = setBorderWidth(value)

    var borderWidthPx: Float?
        @JvmName("obtainBorderWidthPx") get() = _borderWidth?.top?.value
        @JvmName("assignBorderWidthPx") set(value) = setBorderWidth(value?.let { CssEdgeLengths.px(it) })

    fun getBorderStyle(): CssBorderStyle? = _borderStyle
    fun setBorderStyle(borderStyle: CssBorderStyle?, important: Boolean = false) {
        _borderStyle = borderStyle
        updateImportance(CssProperty.BORDER_STYLE, borderStyle != null, important)
        notifyChanged()
    }

    var borderStyle: CssBorderStyle?
        @JvmName("obtainBorderStyle") get() = _borderStyle
        @JvmName("assignBorderStyle") set(value) = setBorderStyle(value)

    fun getBorderColor(): RgbaColor? = _borderColor
    fun setBorderColor(borderColor: RgbaColor?, important: Boolean = false) {
        _borderColor = borderColor
        updateImportance(CssProperty.BORDER_COLOR, borderColor != null, important)
        notifyChanged()
    }

    var borderColor: RgbaColor?
        @JvmName("obtainBorderColor") get() = _borderColor
        @JvmName("assignBorderColor") set(value) = setBorderColor(value)

    fun getBorderRadius(): CssCornerRadii? = _borderRadius
    fun setBorderRadius(borderRadius: CssCornerRadii?, important: Boolean = false) {
        _borderRadius = borderRadius
        updateImportance(CssProperty.BORDER_RADIUS, borderRadius != null, important)
        notifyChanged()
    }

    var borderRadius: CssCornerRadii?
        @JvmName("obtainBorderRadius") get() = _borderRadius
        @JvmName("assignBorderRadius") set(value) = setBorderRadius(value)

    var borderRadiusPx: Float?
        @JvmName("obtainBorderRadiusPx") get() = _borderRadius?.topLeft?.takeIf { it.unit == CssUnit.PX }?.value
        @JvmName("assignBorderRadiusPx") set(value) = setBorderRadius(value?.let { CssCornerRadii.px(it) })

    fun getBoxShadow(): CssShadowList? = _boxShadow
    fun setBoxShadow(boxShadow: CssShadowList?, important: Boolean = false) {
        _boxShadow = boxShadow
        updateImportance(CssProperty.BOX_SHADOW, boxShadow != null, important)
        notifyChanged()
    }

    var boxShadow: CssShadowList?
        @JvmName("obtainBoxShadow") get() = _boxShadow
        @JvmName("assignBoxShadow") set(value) = setBoxShadow(value)

    // -------------------------------------------------------------------------
    // 4. Typography
    // -------------------------------------------------------------------------

    fun getFontFamily(): CssFontFamily? = _fontFamily
    fun setFontFamily(fontFamily: CssFontFamily?, important: Boolean = false) {
        _fontFamily = fontFamily
        updateImportance(CssProperty.FONT_FAMILY, fontFamily != null, important)
        notifyChanged()
    }

    var fontFamily: CssFontFamily?
        @JvmName("obtainFontFamily") get() = _fontFamily
        @JvmName("assignFontFamily") set(value) = setFontFamily(value)

    fun getFontSize(): CssLength? = _fontSize
    fun setFontSize(fontSize: CssLength?, important: Boolean = false) {
        _fontSize = fontSize
        updateImportance(CssProperty.FONT_SIZE, fontSize != null, important)
        notifyChanged()
    }

    var fontSize: CssLength?
        @JvmName("obtainFontSize") get() = _fontSize
        @JvmName("assignFontSize") set(value) = setFontSize(value)

    var fontSizePx: Float?
        @JvmName("obtainFontSizePx") get() = _fontSize?.value
        @JvmName("assignFontSizePx") set(value) = setFontSize(value?.let { CssLength.px(it) })

    fun getFontWeight(): FontWeight? = _fontWeight
    fun setFontWeight(fontWeight: FontWeight?, important: Boolean = false) {
        _fontWeight = fontWeight
        _fontWeightNumericOverride = fontWeight?.numericWeight
        updateImportance(CssProperty.FONT_WEIGHT, fontWeight != null, important)
        notifyChanged()
    }

    var fontWeight: FontWeight?
        @JvmName("obtainFontWeight") get() = _fontWeight
        @JvmName("assignFontWeight") set(value) = setFontWeight(value)

    var fontWeightNumeric: Int?
        @JvmName("obtainFontWeightNumeric") get() = _fontWeightNumericOverride ?: _fontWeight?.numericWeight
        @JvmName("assignFontWeightNumeric") set(value) {
            _fontWeightNumericOverride = value
            _fontWeight = value?.let { w -> FontWeight.entries.firstOrNull { it.numericWeight == w } }
            updateImportance(CssProperty.FONT_WEIGHT, value != null, false)
            notifyChanged()
        }

    fun getFontStyle(): CssFontStyle? = _fontStyle
    fun setFontStyle(fontStyle: CssFontStyle?, important: Boolean = false) {
        _fontStyle = fontStyle
        updateImportance(CssProperty.FONT_STYLE, fontStyle != null, important)
        notifyChanged()
    }

    var fontStyle: CssFontStyle?
        @JvmName("obtainFontStyle") get() = _fontStyle
        @JvmName("assignFontStyle") set(value) = setFontStyle(value)

    fun getLineHeight(): CssLength? = _lineHeight
    fun setLineHeight(lineHeight: CssLength?, important: Boolean = false) {
        _lineHeight = lineHeight
        updateImportance(CssProperty.LINE_HEIGHT, lineHeight != null, important)
        notifyChanged()
    }

    var lineHeight: CssLength?
        @JvmName("obtainLineHeight") get() = _lineHeight
        @JvmName("assignLineHeight") set(value) = setLineHeight(value)

    fun getLetterSpacing(): CssLength? = _letterSpacing
    fun setLetterSpacing(letterSpacing: CssLength?, important: Boolean = false) {
        _letterSpacing = letterSpacing
        updateImportance(CssProperty.LETTER_SPACING, letterSpacing != null, important)
        notifyChanged()
    }

    var letterSpacing: CssLength?
        @JvmName("obtainLetterSpacing") get() = _letterSpacing
        @JvmName("assignLetterSpacing") set(value) = setLetterSpacing(value)

    fun getTextAlign(): TextAlignment? = _textAlign
    fun setTextAlign(textAlign: TextAlignment?, important: Boolean = false) {
        _textAlign = textAlign
        updateImportance(CssProperty.TEXT_ALIGN, textAlign != null, important)
        notifyChanged()
    }

    var textAlign: TextAlignment?
        @JvmName("obtainTextAlign") get() = _textAlign
        @JvmName("assignTextAlign") set(value) = setTextAlign(value)

    fun getTextDecoration(): CssTextDecoration? = _textDecoration
    fun setTextDecoration(textDecoration: CssTextDecoration?, important: Boolean = false) {
        _textDecoration = textDecoration
        updateImportance(CssProperty.TEXT_DECORATION, textDecoration != null, important)
        notifyChanged()
    }

    var textDecoration: CssTextDecoration?
        @JvmName("obtainTextDecoration") get() = _textDecoration
        @JvmName("assignTextDecoration") set(value) = setTextDecoration(value)

    fun getTextTransform(): TextTransform? = _textTransform
    fun setTextTransform(textTransform: TextTransform?, important: Boolean = false) {
        _textTransform = textTransform
        updateImportance(CssProperty.TEXT_TRANSFORM, textTransform != null, important)
        notifyChanged()
    }

    var textTransform: TextTransform?
        @JvmName("obtainTextTransform") get() = _textTransform
        @JvmName("assignTextTransform") set(value) = setTextTransform(value)

    fun getTextOverflow(): TextOverflow? = _textOverflow
    fun setTextOverflow(textOverflow: TextOverflow?, important: Boolean = false) {
        _textOverflow = textOverflow
        updateImportance(CssProperty.TEXT_OVERFLOW, textOverflow != null, important)
        notifyChanged()
    }

    var textOverflow: TextOverflow?
        @JvmName("obtainTextOverflow") get() = _textOverflow
        @JvmName("assignTextOverflow") set(value) = setTextOverflow(value)

    fun getWhiteSpace(): CssWhiteSpace? = _whiteSpace
    fun setWhiteSpace(whiteSpace: CssWhiteSpace?, important: Boolean = false) {
        _whiteSpace = whiteSpace
        updateImportance(CssProperty.WHITE_SPACE, whiteSpace != null, important)
        notifyChanged()
    }

    var whiteSpace: CssWhiteSpace?
        @JvmName("obtainWhiteSpace") get() = _whiteSpace
        @JvmName("assignWhiteSpace") set(value) = setWhiteSpace(value)

    fun getWordBreak(): CssWordBreak? = _wordBreak
    fun setWordBreak(wordBreak: CssWordBreak?, important: Boolean = false) {
        _wordBreak = wordBreak
        updateImportance(CssProperty.WORD_BREAK, wordBreak != null, important)
        notifyChanged()
    }

    var wordBreak: CssWordBreak?
        @JvmName("obtainWordBreak") get() = _wordBreak
        @JvmName("assignWordBreak") set(value) = setWordBreak(value)

    fun getVerticalAlign(): CssVerticalAlign? = _verticalAlign
    fun setVerticalAlign(verticalAlign: CssVerticalAlign?, important: Boolean = false) {
        _verticalAlign = verticalAlign
        updateImportance(CssProperty.VERTICAL_ALIGN, verticalAlign != null, important)
        notifyChanged()
    }

    var verticalAlign: CssVerticalAlign?
        @JvmName("obtainVerticalAlign") get() = _verticalAlign
        @JvmName("assignVerticalAlign") set(value) = setVerticalAlign(value)

    fun getTextShadow(): CssShadowList? = _textShadow
    fun setTextShadow(textShadow: CssShadowList?, important: Boolean = false) {
        _textShadow = textShadow
        updateImportance(CssProperty.TEXT_SHADOW, textShadow != null, important)
        notifyChanged()
    }

    var textShadow: CssShadowList?
        @JvmName("obtainTextShadow") get() = _textShadow
        @JvmName("assignTextShadow") set(value) = setTextShadow(value)

    // -------------------------------------------------------------------------
    // 5. Flexbox & Grid Alignment
    // -------------------------------------------------------------------------

    fun getFlexDirection(): FlexDirection? = _flexDirection
    fun setFlexDirection(flexDirection: FlexDirection?, important: Boolean = false) {
        _flexDirection = flexDirection
        updateImportance(CssProperty.FLEX_DIRECTION, flexDirection != null, important)
        notifyChanged()
    }

    var flexDirection: FlexDirection?
        @JvmName("obtainFlexDirection") get() = _flexDirection
        @JvmName("assignFlexDirection") set(value) = setFlexDirection(value)

    fun getFlexWrap(): FlexWrap? = _flexWrap
    fun setFlexWrap(flexWrap: FlexWrap?, important: Boolean = false) {
        _flexWrap = flexWrap
        updateImportance(CssProperty.FLEX_WRAP, flexWrap != null, important)
        notifyChanged()
    }

    var flexWrap: FlexWrap?
        @JvmName("obtainFlexWrap") get() = _flexWrap
        @JvmName("assignFlexWrap") set(value) = setFlexWrap(value)

    fun getJustifyContent(): JustifyContent? = _justifyContent
    fun setJustifyContent(justifyContent: JustifyContent?, important: Boolean = false) {
        _justifyContent = justifyContent
        updateImportance(CssProperty.JUSTIFY_CONTENT, justifyContent != null, important)
        notifyChanged()
    }

    var justifyContent: JustifyContent?
        @JvmName("obtainJustifyContent") get() = _justifyContent
        @JvmName("assignJustifyContent") set(value) = setJustifyContent(value)

    fun getAlignItems(): AlignItems? = _alignItems
    fun setAlignItems(alignItems: AlignItems?, important: Boolean = false) {
        _alignItems = alignItems
        updateImportance(CssProperty.ALIGN_ITEMS, alignItems != null, important)
        notifyChanged()
    }

    var alignItems: AlignItems?
        @JvmName("obtainAlignItems") get() = _alignItems
        @JvmName("assignAlignItems") set(value) = setAlignItems(value)

    fun getAlignSelf(): AlignSelf? = _alignSelf
    fun setAlignSelf(alignSelf: AlignSelf?, important: Boolean = false) {
        _alignSelf = alignSelf
        updateImportance(CssProperty.ALIGN_SELF, alignSelf != null, important)
        notifyChanged()
    }

    var alignSelf: AlignSelf?
        @JvmName("obtainAlignSelf") get() = _alignSelf
        @JvmName("assignAlignSelf") set(value) = setAlignSelf(value)

    fun getAlignContent(): CssAlignContent? = _alignContent
    fun setAlignContent(alignContent: CssAlignContent?, important: Boolean = false) {
        _alignContent = alignContent
        updateImportance(CssProperty.ALIGN_CONTENT, alignContent != null, important)
        notifyChanged()
    }

    var alignContent: CssAlignContent?
        @JvmName("obtainAlignContent") get() = _alignContent
        @JvmName("assignAlignContent") set(value) = setAlignContent(value)

    fun getFlexGrow(): Float? = _flexGrow
    fun setFlexGrow(flexGrow: Float?, important: Boolean = false) {
        _flexGrow = flexGrow
        updateImportance(CssProperty.FLEX_GROW, flexGrow != null, important)
        notifyChanged()
    }

    var flexGrow: Float?
        @JvmName("obtainFlexGrow") get() = _flexGrow
        @JvmName("assignFlexGrow") set(value) = setFlexGrow(value)

    fun getFlexShrink(): Float? = _flexShrink
    fun setFlexShrink(flexShrink: Float?, important: Boolean = false) {
        _flexShrink = flexShrink
        updateImportance(CssProperty.FLEX_SHRINK, flexShrink != null, important)
        notifyChanged()
    }

    var flexShrink: Float?
        @JvmName("obtainFlexShrink") get() = _flexShrink
        @JvmName("assignFlexShrink") set(value) = setFlexShrink(value)

    fun getFlexBasis(): CssLength? = _flexBasis
    fun setFlexBasis(flexBasis: CssLength?, important: Boolean = false) {
        _flexBasis = flexBasis
        updateImportance(CssProperty.FLEX_BASIS, flexBasis != null, important)
        notifyChanged()
    }

    var flexBasis: CssLength?
        @JvmName("obtainFlexBasis") get() = _flexBasis
        @JvmName("assignFlexBasis") set(value) = setFlexBasis(value)

    fun getGap(): CssLength? = _gap
    fun setGap(gap: CssLength?, important: Boolean = false) {
        _gap = gap
        updateImportance(CssProperty.GAP, gap != null, important)
        notifyChanged()
    }

    var gap: CssLength?
        @JvmName("obtainGap") get() = _gap
        @JvmName("assignGap") set(value) = setGap(value)

    fun getRowGap(): CssLength? = _rowGap
    fun setRowGap(rowGap: CssLength?, important: Boolean = false) {
        _rowGap = rowGap
        updateImportance(CssProperty.ROW_GAP, rowGap != null, important)
        notifyChanged()
    }

    var rowGap: CssLength?
        @JvmName("obtainRowGap") get() = _rowGap
        @JvmName("assignRowGap") set(value) = setRowGap(value)

    fun getColumnGap(): CssLength? = _columnGap
    fun setColumnGap(columnGap: CssLength?, important: Boolean = false) {
        _columnGap = columnGap
        updateImportance(CssProperty.COLUMN_GAP, columnGap != null, important)
        notifyChanged()
    }

    var columnGap: CssLength?
        @JvmName("obtainColumnGap") get() = _columnGap
        @JvmName("assignColumnGap") set(value) = setColumnGap(value)

    // -------------------------------------------------------------------------
    // 6. Overflow, Interaction, Cursor, Media Fit, Transform, Transition, & Filter
    // -------------------------------------------------------------------------

    fun getOverflow(): OverflowBehavior? = _overflow
    fun setOverflow(overflow: OverflowBehavior?, important: Boolean = false) {
        _overflow = overflow
        updateImportance(CssProperty.OVERFLOW, overflow != null, important)
        notifyChanged()
    }

    var overflow: OverflowBehavior?
        @JvmName("obtainOverflow") get() = _overflow
        @JvmName("assignOverflow") set(value) = setOverflow(value)

    fun getOverflowX(): OverflowBehavior? = _overflowX
    fun setOverflowX(overflowX: OverflowBehavior?, important: Boolean = false) {
        _overflowX = overflowX
        updateImportance(CssProperty.OVERFLOW_X, overflowX != null, important)
        notifyChanged()
    }

    var overflowX: OverflowBehavior?
        @JvmName("obtainOverflowX") get() = _overflowX
        @JvmName("assignOverflowX") set(value) = setOverflowX(value)

    fun getOverflowY(): OverflowBehavior? = _overflowY
    fun setOverflowY(overflowY: OverflowBehavior?, important: Boolean = false) {
        _overflowY = overflowY
        updateImportance(CssProperty.OVERFLOW_Y, overflowY != null, important)
        notifyChanged()
    }

    var overflowY: OverflowBehavior?
        @JvmName("obtainOverflowY") get() = _overflowY
        @JvmName("assignOverflowY") set(value) = setOverflowY(value)

    fun getCursor(): CssCursorValue? = _cursor
    fun setCursor(cursor: CssCursorValue?, important: Boolean = false) {
        _cursor = cursor
        updateImportance(CssProperty.CURSOR, cursor != null, important)
        notifyChanged()
    }

    var cursor: CssCursorValue?
        @JvmName("obtainCursor") get() = _cursor
        @JvmName("assignCursor") set(value) = setCursor(value)

    fun getPointerEvents(): CssPointerEvents? = _pointerEvents
    fun setPointerEvents(pointerEvents: CssPointerEvents?, important: Boolean = false) {
        _pointerEvents = pointerEvents
        updateImportance(CssProperty.POINTER_EVENTS, pointerEvents != null, important)
        notifyChanged()
    }

    var pointerEvents: CssPointerEvents?
        @JvmName("obtainPointerEvents") get() = _pointerEvents
        @JvmName("assignPointerEvents") set(value) = setPointerEvents(value)

    fun getUserSelect(): CssUserSelect? = _userSelect
    fun setUserSelect(userSelect: CssUserSelect?, important: Boolean = false) {
        _userSelect = userSelect
        updateImportance(CssProperty.USER_SELECT, userSelect != null, important)
        notifyChanged()
    }

    var userSelect: CssUserSelect?
        @JvmName("obtainUserSelect") get() = _userSelect
        @JvmName("assignUserSelect") set(value) = setUserSelect(value)

    fun getObjectFit(): ObjectFit? = _objectFit
    fun setObjectFit(objectFit: ObjectFit?, important: Boolean = false) {
        _objectFit = objectFit
        updateImportance(CssProperty.OBJECT_FIT, objectFit != null, important)
        notifyChanged()
    }

    var objectFit: ObjectFit?
        @JvmName("obtainObjectFit") get() = _objectFit
        @JvmName("assignObjectFit") set(value) = setObjectFit(value)

    fun getAspectRatio(): CssAspectRatio? = _aspectRatio
    fun setAspectRatio(aspectRatio: CssAspectRatio?, important: Boolean = false) {
        _aspectRatio = aspectRatio
        updateImportance(CssProperty.ASPECT_RATIO, aspectRatio != null, important)
        notifyChanged()
    }

    var aspectRatio: CssAspectRatio?
        @JvmName("obtainAspectRatio") get() = _aspectRatio
        @JvmName("assignAspectRatio") set(value) = setAspectRatio(value)

    fun getZIndex(): Int? = _zIndex
    fun setZIndex(zIndex: Int?, important: Boolean = false) {
        _zIndex = zIndex
        updateImportance(CssProperty.Z_INDEX, zIndex != null, important)
        notifyChanged()
    }

    var zIndex: Int?
        @JvmName("obtainZIndex") get() = _zIndex
        @JvmName("assignZIndex") set(value) = setZIndex(value)

    fun getTransform(): CssTransform2D? = _transform
    fun setTransform(transform: CssTransform2D?, important: Boolean = false) {
        _transform = transform
        updateImportance(CssProperty.TRANSFORM, transform != null, important)
        notifyChanged()
    }

    var transform: CssTransform2D?
        @JvmName("obtainTransform") get() = _transform
        @JvmName("assignTransform") set(value) = setTransform(value)

    fun getTransformOrigin(): CssBackgroundPosition? = _transformOrigin
    fun setTransformOrigin(origin: CssBackgroundPosition?, important: Boolean = false) {
        _transformOrigin = origin
        updateImportance(CssProperty.TRANSFORM_ORIGIN, origin != null, important)
        notifyChanged()
    }

    var transformOrigin: CssBackgroundPosition?
        @JvmName("obtainTransformOrigin") get() = _transformOrigin
        @JvmName("assignTransformOrigin") set(value) = setTransformOrigin(value)

    fun getTransitions(): List<CssTransitionSpec>? = _transitions
    fun setTransitions(transitions: List<CssTransitionSpec>?, important: Boolean = false) {
        _transitions = transitions?.takeIf { it.isNotEmpty() }
        updateImportance(CssProperty.TRANSITION, _transitions != null, important)
        notifyChanged()
    }

    var transitions: List<CssTransitionSpec>?
        @JvmName("obtainTransitions") get() = _transitions
        @JvmName("assignTransitions") set(value) = setTransitions(value)

    fun getFilter(): CssFilter? = _filter
    fun setFilter(filter: CssFilter?, important: Boolean = false) {
        _filter = filter
        updateImportance(CssProperty.FILTER, filter != null, important)
        notifyChanged()
    }

    var filter: CssFilter?
        @JvmName("obtainFilter") get() = _filter
        @JvmName("assignFilter") set(value) = setFilter(value)

    fun getBackdropFilter(): CssFilter? = _backdropFilter
    fun setBackdropFilter(backdropFilter: CssFilter?, important: Boolean = false) {
        _backdropFilter = backdropFilter
        updateImportance(CssProperty.BACKDROP_FILTER, backdropFilter != null, important)
        notifyChanged()
    }

    var backdropFilter: CssFilter?
        @JvmName("obtainBackdropFilter") get() = _backdropFilter
        @JvmName("assignBackdropFilter") set(value) = setBackdropFilter(value)

    // -------------------------------------------------------------------------
    // Raw / Precompiler Declaration Support
    // -------------------------------------------------------------------------

    /**
     * Parses and applies a CSS declaration (`property: value`) into the corresponding typed property
     * on this [ComponentStyle], while also retaining any unrecognized/custom CSS property in
     * `rawCustomDeclarations`.
     */
    fun applyCssDeclaration(property: String, value: String, important: Boolean = false) {
        setRawDeclaration(property, value, important)
    }

    fun setRawDeclaration(property: String, value: String?, important: Boolean = false) {
        val propName = property.trim().lowercase()
        if (propName.isEmpty()) return
        if (value == null) {
            rawCustomDeclarations.remove(propName)
            rawImportantCustomProps.remove(propName)
            CssProperty.fromCssName(propName)?.let { importantSet.remove(it) }
            notifyChanged()
            return
        }

        val hasBangImportant = value.contains("!important", ignoreCase = true)
        val cleanVal = value.replace(Regex("!\\s*important", RegexOption.IGNORE_CASE), "").trim()
        val isImp = important || hasBangImportant || defaultImportantScope

        val prevCallback = onChange
        onChange = null
        try {
            when (propName) {
                "background-color" -> RgbaColor.fromCss(cleanVal)?.let { setBackgroundColor(it, isImp) }
                "background" -> {
                    val grad = Gradient.fromCss(cleanVal)
                    if (grad != null) {
                        setBackgroundImage(CssBackgroundImage.fromGradient(grad), isImp)
                    } else {
                        val color = RgbaColor.fromCss(cleanVal)
                        if (color != null) {
                            setBackgroundColor(color, isImp)
                        } else {
                            CssBackgroundImage.parse(cleanVal)?.let { setBackgroundImage(it, isImp) }
                        }
                    }
                }
                "background-image" -> CssBackgroundImage.parse(cleanVal)?.let { setBackgroundImage(it, isImp) }
                "background-size" -> CssBackgroundSize.parse(cleanVal)?.let { setBackgroundSize(it, isImp) }
                "background-position" -> CssBackgroundPosition.parse(cleanVal)?.let { setBackgroundPosition(it, isImp) }
                "background-repeat" -> CssBackgroundRepeat.fromCss(cleanVal)?.let { setBackgroundRepeat(it, isImp) }
                "background-attachment" -> CssBackgroundAttachment.fromCss(cleanVal)?.let { setBackgroundAttachment(it, isImp) }
                "background-clip" -> CssBackgroundClip.fromCss(cleanVal)?.let { setBackgroundClip(it, isImp) }
                "background-origin" -> CssBackgroundOrigin.fromCss(cleanVal)?.let { setBackgroundOrigin(it, isImp) }
                "color" -> RgbaColor.fromCss(cleanVal)?.let { setTextColor(it, isImp) }
                "accent-color" -> RgbaColor.fromCss(cleanVal)?.let { setAccentColor(it, isImp) }
                "opacity" -> cleanVal.toFloatOrNull()?.let { setOpacity(it, isImp) }
                "display" -> CssDisplay.fromCss(cleanVal)?.let { setDisplay(it, isImp) }
                "visibility" -> CssVisibility.fromCss(cleanVal)?.let { setVisibility(it, isImp) }
                "position" -> CssPosition.fromCss(cleanVal)?.let { setPosition(it, isImp) }
                "top" -> CssLength.parse(cleanVal)?.let { setTop(it, isImp) }
                "right" -> CssLength.parse(cleanVal)?.let { setRight(it, isImp) }
                "bottom" -> CssLength.parse(cleanVal)?.let { setBottom(it, isImp) }
                "left" -> CssLength.parse(cleanVal)?.let { setLeft(it, isImp) }
                "width" -> CssLength.parse(cleanVal)?.let { setWidth(it, isImp) }
                "height" -> CssLength.parse(cleanVal)?.let { setHeight(it, isImp) }
                "min-width" -> CssLength.parse(cleanVal)?.let { setMinWidth(it, isImp) }
                "min-height" -> CssLength.parse(cleanVal)?.let { setMinHeight(it, isImp) }
                "max-width" -> CssLength.parse(cleanVal)?.let { setMaxWidth(it, isImp) }
                "max-height" -> CssLength.parse(cleanVal)?.let { setMaxHeight(it, isImp) }
                "margin" -> CssEdgeLengths.parse(cleanVal)?.let { setMargin(it, isImp) }
                "margin-top" -> CssLength.parse(cleanVal)?.let { setMarginTop(it, isImp) }
                "margin-right" -> CssLength.parse(cleanVal)?.let { setMarginRight(it, isImp) }
                "margin-bottom" -> CssLength.parse(cleanVal)?.let { setMarginBottom(it, isImp) }
                "margin-left" -> CssLength.parse(cleanVal)?.let { setMarginLeft(it, isImp) }
                "padding" -> CssEdgeLengths.parse(cleanVal)?.let { setPadding(it, isImp) }
                "padding-top" -> CssLength.parse(cleanVal)?.let { setPaddingTop(it, isImp) }
                "padding-right" -> CssLength.parse(cleanVal)?.let { setPaddingRight(it, isImp) }
                "padding-bottom" -> CssLength.parse(cleanVal)?.let { setPaddingBottom(it, isImp) }
                "padding-left" -> CssLength.parse(cleanVal)?.let { setPaddingLeft(it, isImp) }
                "box-sizing" -> CssBoxSizing.fromCss(cleanVal)?.let { setBoxSizing(it, isImp) }
                "float" -> CssFloat.fromCss(cleanVal)?.let { setFloatSide(it, isImp) }
                "clear" -> CssClear.fromCss(cleanVal)?.let { setClearSide(it, isImp) }
                "border-width" -> CssEdgeLengths.parse(cleanVal)?.let { setBorderWidth(it, isImp) }
                "border-style" -> CssBorderStyle.fromCss(cleanVal)?.let { setBorderStyle(it, isImp) }
                "border-color" -> RgbaColor.fromCss(cleanVal)?.let { setBorderColor(it, isImp) }
                "border" -> parseBorderShorthand(cleanVal, isImp)
                "border-radius" -> CssCornerRadii.parse(cleanVal)?.let { setBorderRadius(it, isImp) }
                "box-shadow", "-webkit-box-shadow" -> CssShadowList.parse(cleanVal)?.let { setBoxShadow(it, isImp) }
                "text-shadow" -> CssShadowList.parse(cleanVal)?.let { setTextShadow(it, isImp) }
                "font-family" -> CssFontFamily.parse(cleanVal)?.let { setFontFamily(it, isImp) }
                "font-size" -> CssLength.parse(cleanVal)?.let { setFontSize(it, isImp) }
                "font-weight" -> parseFontWeightDeclaration(cleanVal, isImp)
                "font-style" -> CssFontStyle.fromCss(cleanVal)?.let { setFontStyle(it, isImp) }
                "line-height" -> CssLength.parse(cleanVal)?.let { setLineHeight(it, isImp) }
                "letter-spacing" -> CssLength.parse(cleanVal)?.let { setLetterSpacing(it, isImp) }
                "text-align" -> parseTextAlignDeclaration(cleanVal, isImp)
                "text-decoration" -> CssTextDecoration.fromCss(cleanVal)?.let { setTextDecoration(it, isImp) }
                "text-transform" -> parseTextTransformDeclaration(cleanVal, isImp)
                "text-overflow" -> parseTextOverflowDeclaration(cleanVal, isImp)
                "white-space" -> CssWhiteSpace.fromCss(cleanVal)?.let { setWhiteSpace(it, isImp) }
                "word-break" -> CssWordBreak.fromCss(cleanVal)?.let { setWordBreak(it, isImp) }
                "vertical-align" -> CssVerticalAlign.fromCss(cleanVal)?.let { setVerticalAlign(it, isImp) }
                "flex-direction" -> parseFlexDirectionDeclaration(cleanVal, isImp)
                "flex-wrap" -> parseFlexWrapDeclaration(cleanVal, isImp)
                "justify-content" -> parseJustifyContentDeclaration(cleanVal, isImp)
                "align-items" -> parseAlignItemsDeclaration(cleanVal, isImp)
                "align-self" -> parseAlignSelfDeclaration(cleanVal, isImp)
                "align-content" -> CssAlignContent.fromCss(cleanVal)?.let { setAlignContent(it, isImp) }
                "flex-grow" -> cleanVal.toFloatOrNull()?.let { setFlexGrow(it, isImp) }
                "flex-shrink" -> cleanVal.toFloatOrNull()?.let { setFlexShrink(it, isImp) }
                "flex-basis" -> CssLength.parse(cleanVal)?.let { setFlexBasis(it, isImp) }
                "gap" -> CssLength.parse(cleanVal)?.let { setGap(it, isImp) }
                "row-gap" -> CssLength.parse(cleanVal)?.let { setRowGap(it, isImp) }
                "column-gap" -> CssLength.parse(cleanVal)?.let { setColumnGap(it, isImp) }
                "overflow" -> parseOverflowDeclaration(cleanVal)?.let { setOverflow(it, isImp) }
                "overflow-x" -> parseOverflowDeclaration(cleanVal)?.let { setOverflowX(it, isImp) }
                "overflow-y" -> parseOverflowDeclaration(cleanVal)?.let { setOverflowY(it, isImp) }
                "cursor" -> CssCustomCursor.parse(cleanVal)?.let { setCursor(it, isImp) }
                "pointer-events" -> CssPointerEvents.fromCss(cleanVal)?.let { setPointerEvents(it, isImp) }
                "user-select" -> CssUserSelect.fromCss(cleanVal)?.let { setUserSelect(it, isImp) }
                "object-fit" -> parseObjectFitDeclaration(cleanVal)?.let { setObjectFit(it, isImp) }
                "aspect-ratio" -> CssAspectRatio.parse(cleanVal)?.let { setAspectRatio(it, isImp) }
                "z-index" -> cleanVal.toIntOrNull()?.let { setZIndex(it, isImp) }
                "transform" -> setTransform(CssTransform2D.parse(cleanVal), isImp)
                "transform-origin" -> CssBackgroundPosition.parse(cleanVal)?.let { setTransformOrigin(it, isImp) }
                "transition" -> setTransitions(CssTransitionSpec.parseList(cleanVal), isImp)
                "filter" -> CssFilter.parse(cleanVal)?.let { setFilter(it, isImp) }
                "backdrop-filter", "-webkit-backdrop-filter" -> CssFilter.parse(cleanVal)?.let { setBackdropFilter(it, isImp) }
            }
            rawCustomDeclarations[propName] = cleanVal
            if (isImp) {
                rawImportantCustomProps.add(propName)
                CssProperty.fromCssName(propName)?.let { importantSet.add(it) }
            }
        } finally {
            onChange = prevCallback
        }
        notifyChanged()
    }

    private fun parseBorderShorthand(cleanVal: String, isImp: Boolean) {
        for (token in CssShadow.tokenizePreservingParens(cleanVal)) {
            val styleCandidate = CssBorderStyle.fromCss(token)
            if (styleCandidate != null) {
                setBorderStyle(styleCandidate, isImp)
                continue
            }
            val colorCandidate = RgbaColor.fromCss(token)
            if (colorCandidate != null) {
                setBorderColor(colorCandidate, isImp)
                continue
            }
            val lenCandidate = CssLength.parse(token)
            if (lenCandidate != null) {
                setBorderWidth(CssEdgeLengths.all(lenCandidate), isImp)
            }
        }
    }

    private fun parseFontWeightDeclaration(cleanVal: String, isImp: Boolean) {
        val numeric = when (cleanVal.lowercase()) {
            "normal" -> 400
            "bold" -> 700
            "lighter" -> 300
            "bolder" -> 800
            else -> cleanVal.toIntOrNull()
        }
        if (numeric != null) {
            _fontWeightNumericOverride = numeric
            _fontWeight = FontWeight.entries.firstOrNull { it.numericWeight == numeric }
            updateImportance(CssProperty.FONT_WEIGHT, true, isImp)
        }
    }

    private fun parseTextAlignDeclaration(cleanVal: String, isImp: Boolean) {
        val aligned = when (cleanVal.lowercase()) {
            "left", "start" -> TextAlignment.START
            "center" -> TextAlignment.CENTER
            "right", "end" -> TextAlignment.END
            "justify" -> TextAlignment.JUSTIFY
            else -> null
        }
        if (aligned != null) setTextAlign(aligned, isImp)
    }

    private fun parseTextTransformDeclaration(cleanVal: String, isImp: Boolean) {
        val tt = when (cleanVal.lowercase()) {
            "none" -> TextTransform.NONE
            "lowercase" -> TextTransform.LOWERCASE
            "uppercase" -> TextTransform.UPPERCASE
            "capitalize" -> TextTransform.CAPITALIZE
            else -> null
        }
        if (tt != null) setTextTransform(tt, isImp)
    }

    private fun parseTextOverflowDeclaration(cleanVal: String, isImp: Boolean) {
        val to = when (cleanVal.lowercase()) {
            "ellipsis", "truncate" -> TextOverflow.ELLIPSIS
            "nowrap" -> TextOverflow.NOWRAP
            "clip", "wrap" -> TextOverflow.WRAP
            else -> null
        }
        if (to != null) setTextOverflow(to, isImp)
    }

    private fun parseFlexDirectionDeclaration(cleanVal: String, isImp: Boolean) {
        val dir = when (cleanVal.lowercase()) {
            "row" -> FlexDirection.ROW
            "row-reverse" -> FlexDirection.ROW_REVERSE
            "column" -> FlexDirection.COLUMN
            "column-reverse" -> FlexDirection.COLUMN_REVERSE
            else -> null
        }
        if (dir != null) setFlexDirection(dir, isImp)
    }

    private fun parseFlexWrapDeclaration(cleanVal: String, isImp: Boolean) {
        val wrap = when (cleanVal.lowercase()) {
            "nowrap" -> FlexWrap.NOWRAP
            "wrap" -> FlexWrap.WRAP
            "wrap-reverse" -> FlexWrap.WRAP_REVERSE
            else -> null
        }
        if (wrap != null) setFlexWrap(wrap, isImp)
    }

    private fun parseJustifyContentDeclaration(cleanVal: String, isImp: Boolean) {
        val jc = when (cleanVal.lowercase()) {
            "flex-start", "start", "left" -> JustifyContent.START
            "flex-end", "end", "right" -> JustifyContent.END
            "center" -> JustifyContent.CENTER
            "space-between", "between" -> JustifyContent.BETWEEN
            "space-around", "around" -> JustifyContent.AROUND
            "space-evenly", "evenly" -> JustifyContent.EVENLY
            else -> null
        }
        if (jc != null) setJustifyContent(jc, isImp)
    }

    private fun parseAlignItemsDeclaration(cleanVal: String, isImp: Boolean) {
        val ai = when (cleanVal.lowercase()) {
            "flex-start", "start" -> AlignItems.START
            "flex-end", "end" -> AlignItems.END
            "center" -> AlignItems.CENTER
            "baseline" -> AlignItems.BASELINE
            "stretch" -> AlignItems.STRETCH
            else -> null
        }
        if (ai != null) setAlignItems(ai, isImp)
    }

    private fun parseAlignSelfDeclaration(cleanVal: String, isImp: Boolean) {
        val asf = when (cleanVal.lowercase()) {
            "auto" -> AlignSelf.AUTO
            "flex-start", "start" -> AlignSelf.START
            "flex-end", "end" -> AlignSelf.END
            "center" -> AlignSelf.CENTER
            "baseline" -> AlignSelf.BASELINE
            "stretch" -> AlignSelf.STRETCH
            else -> null
        }
        if (asf != null) setAlignSelf(asf, isImp)
    }

    private fun parseOverflowDeclaration(cleanVal: String): OverflowBehavior? = when (cleanVal.lowercase()) {
        "auto" -> OverflowBehavior.AUTO
        "hidden" -> OverflowBehavior.HIDDEN
        "visible" -> OverflowBehavior.VISIBLE
        "scroll" -> OverflowBehavior.SCROLL
        else -> null
    }

    private fun parseObjectFitDeclaration(cleanVal: String): ObjectFit? = when (cleanVal.lowercase()) {
        "contain" -> ObjectFit.CONTAIN
        "cover" -> ObjectFit.COVER
        "fill" -> ObjectFit.FILL
        "scale-down", "scale" -> ObjectFit.SCALE_DOWN
        "none" -> ObjectFit.NONE
        else -> null
    }

    // -------------------------------------------------------------------------
    // Serialization & ComputedStyle Conversion
    // -------------------------------------------------------------------------

    /**
     * Returns all non-null properties mapped to `cssPropertyName -> (cssValueString to isImportant)`.
     */
    fun toCssPropertyMap(): Map<String, Pair<String, Boolean>> {
        val out = linkedMapOf<String, Pair<String, Boolean>>()
        for ((k, v) in rawCustomDeclarations) {
            out[k] = v to (k in rawImportantCustomProps)
        }

        fun putProp(prop: CssProperty, value: String?) {
            if (value != null) {
                out[prop.cssName] = value to isImportant(prop)
            } else {
                out.remove(prop.cssName)
            }
        }

        putProp(CssProperty.BACKGROUND_COLOR, _backgroundColor?.toCssRgba())
        putProp(CssProperty.BACKGROUND_IMAGE, _backgroundImage?.toCssString())
        putProp(CssProperty.BACKGROUND_SIZE, _backgroundSize?.toCssString())
        putProp(CssProperty.BACKGROUND_POSITION, _backgroundPosition?.toCssString())
        putProp(CssProperty.BACKGROUND_REPEAT, _backgroundRepeat?.toCssString())
        putProp(CssProperty.BACKGROUND_ATTACHMENT, _backgroundAttachment?.toCssString())
        putProp(CssProperty.BACKGROUND_CLIP, _backgroundClip?.toCssString())
        putProp(CssProperty.BACKGROUND_ORIGIN, _backgroundOrigin?.toCssString())
        putProp(CssProperty.COLOR, _textColor?.toCssRgba())
        putProp(CssProperty.ACCENT_COLOR, _accentColor?.toCssRgba())
        putProp(CssProperty.OPACITY, _opacity?.toString())
        putProp(CssProperty.DISPLAY, _display?.toCssString())
        putProp(CssProperty.VISIBILITY, _visibility?.toCssString())
        putProp(CssProperty.POSITION, _position?.toCssString())
        putProp(CssProperty.TOP, _top?.toCssString())
        putProp(CssProperty.RIGHT, _right?.toCssString())
        putProp(CssProperty.BOTTOM, _bottom?.toCssString())
        putProp(CssProperty.LEFT, _left?.toCssString())
        putProp(CssProperty.WIDTH, _width?.toCssString())
        putProp(CssProperty.HEIGHT, _height?.toCssString())
        putProp(CssProperty.MIN_WIDTH, _minWidth?.toCssString())
        putProp(CssProperty.MIN_HEIGHT, _minHeight?.toCssString())
        putProp(CssProperty.MAX_WIDTH, _maxWidth?.toCssString())
        putProp(CssProperty.MAX_HEIGHT, _maxHeight?.toCssString())
        putProp(CssProperty.MARGIN, _margin?.toCssString())
        putProp(CssProperty.MARGIN_TOP, _marginTop?.toCssString())
        putProp(CssProperty.MARGIN_RIGHT, _marginRight?.toCssString())
        putProp(CssProperty.MARGIN_BOTTOM, _marginBottom?.toCssString())
        putProp(CssProperty.MARGIN_LEFT, _marginLeft?.toCssString())
        putProp(CssProperty.PADDING, _padding?.toCssString())
        putProp(CssProperty.PADDING_TOP, _paddingTop?.toCssString())
        putProp(CssProperty.PADDING_RIGHT, _paddingRight?.toCssString())
        putProp(CssProperty.PADDING_BOTTOM, _paddingBottom?.toCssString())
        putProp(CssProperty.PADDING_LEFT, _paddingLeft?.toCssString())
        putProp(CssProperty.BOX_SIZING, _boxSizing?.toCssString())
        putProp(CssProperty.FLOAT, _floatSide?.toCssString())
        putProp(CssProperty.CLEAR, _clearSide?.toCssString())
        putProp(CssProperty.BORDER_WIDTH, _borderWidth?.toCssString())
        putProp(CssProperty.BORDER_STYLE, _borderStyle?.toCssString())
        putProp(CssProperty.BORDER_COLOR, _borderColor?.toCssRgba())
        putProp(CssProperty.BORDER_RADIUS, _borderRadius?.toCssString())
        putProp(CssProperty.BOX_SHADOW, _boxShadow?.toCssString())
        putProp(CssProperty.TEXT_SHADOW, _textShadow?.toCssString())
        putProp(CssProperty.FONT_FAMILY, _fontFamily?.toCssString())
        putProp(CssProperty.FONT_SIZE, _fontSize?.toCssString())
        putProp(CssProperty.FONT_WEIGHT, (_fontWeightNumericOverride ?: _fontWeight?.numericWeight)?.toString())
        putProp(CssProperty.FONT_STYLE, _fontStyle?.toCssString())
        putProp(CssProperty.LINE_HEIGHT, _lineHeight?.toCssString())
        putProp(CssProperty.LETTER_SPACING, _letterSpacing?.toCssString())
        putProp(
            CssProperty.TEXT_ALIGN,
            when (_textAlign) {
                TextAlignment.START -> "start"
                TextAlignment.CENTER -> "center"
                TextAlignment.END -> "end"
                TextAlignment.JUSTIFY -> "justify"
                null -> null
            }
        )
        putProp(CssProperty.TEXT_DECORATION, _textDecoration?.toCssString())
        putProp(
            CssProperty.TEXT_TRANSFORM,
            when (_textTransform) {
                TextTransform.NONE -> "none"
                TextTransform.LOWERCASE -> "lowercase"
                TextTransform.UPPERCASE -> "uppercase"
                TextTransform.CAPITALIZE -> "capitalize"
                null -> null
            }
        )
        putProp(
            CssProperty.TEXT_OVERFLOW,
            when (_textOverflow) {
                TextOverflow.ELLIPSIS -> "ellipsis"
                TextOverflow.NOWRAP -> "clip"
                TextOverflow.WRAP -> "clip"
                null -> null
            }
        )
        putProp(CssProperty.WHITE_SPACE, _whiteSpace?.toCssString())
        putProp(CssProperty.WORD_BREAK, _wordBreak?.toCssString())
        putProp(CssProperty.VERTICAL_ALIGN, _verticalAlign?.toCssString())
        putProp(
            CssProperty.FLEX_DIRECTION,
            when (_flexDirection) {
                FlexDirection.ROW -> "row"
                FlexDirection.ROW_REVERSE -> "row-reverse"
                FlexDirection.COLUMN -> "column"
                FlexDirection.COLUMN_REVERSE -> "column-reverse"
                null -> null
            }
        )
        putProp(
            CssProperty.FLEX_WRAP,
            when (_flexWrap) {
                FlexWrap.NOWRAP -> "nowrap"
                FlexWrap.WRAP -> "wrap"
                FlexWrap.WRAP_REVERSE -> "wrap-reverse"
                null -> null
            }
        )
        putProp(
            CssProperty.JUSTIFY_CONTENT,
            when (_justifyContent) {
                JustifyContent.START -> "flex-start"
                JustifyContent.END -> "flex-end"
                JustifyContent.CENTER -> "center"
                JustifyContent.BETWEEN -> "space-between"
                JustifyContent.AROUND -> "space-around"
                JustifyContent.EVENLY -> "space-evenly"
                null -> null
            }
        )
        putProp(
            CssProperty.ALIGN_ITEMS,
            when (_alignItems) {
                AlignItems.START -> "flex-start"
                AlignItems.END -> "flex-end"
                AlignItems.CENTER -> "center"
                AlignItems.BASELINE -> "baseline"
                AlignItems.STRETCH -> "stretch"
                null -> null
            }
        )
        putProp(
            CssProperty.ALIGN_SELF,
            when (_alignSelf) {
                AlignSelf.AUTO -> "auto"
                AlignSelf.START -> "flex-start"
                AlignSelf.END -> "flex-end"
                AlignSelf.CENTER -> "center"
                AlignSelf.BASELINE -> "baseline"
                AlignSelf.STRETCH -> "stretch"
                null -> null
            }
        )
        putProp(CssProperty.ALIGN_CONTENT, _alignContent?.toCssString())
        putProp(CssProperty.FLEX_GROW, _flexGrow?.toString())
        putProp(CssProperty.FLEX_SHRINK, _flexShrink?.toString())
        putProp(CssProperty.FLEX_BASIS, _flexBasis?.toCssString())
        putProp(CssProperty.GAP, _gap?.toCssString())
        putProp(CssProperty.ROW_GAP, _rowGap?.toCssString())
        putProp(CssProperty.COLUMN_GAP, _columnGap?.toCssString())
        putProp(
            CssProperty.OVERFLOW,
            when (_overflow) {
                OverflowBehavior.AUTO -> "auto"
                OverflowBehavior.HIDDEN -> "hidden"
                OverflowBehavior.VISIBLE -> "visible"
                OverflowBehavior.SCROLL -> "scroll"
                null -> null
            }
        )
        putProp(
            CssProperty.OVERFLOW_X,
            when (_overflowX) {
                OverflowBehavior.AUTO -> "auto"
                OverflowBehavior.HIDDEN -> "hidden"
                OverflowBehavior.VISIBLE -> "visible"
                OverflowBehavior.SCROLL -> "scroll"
                null -> null
            }
        )
        putProp(
            CssProperty.OVERFLOW_Y,
            when (_overflowY) {
                OverflowBehavior.AUTO -> "auto"
                OverflowBehavior.HIDDEN -> "hidden"
                OverflowBehavior.VISIBLE -> "visible"
                OverflowBehavior.SCROLL -> "scroll"
                null -> null
            }
        )
        putProp(CssProperty.CURSOR, _cursor?.toCssString())
        putProp(CssProperty.POINTER_EVENTS, _pointerEvents?.toCssString())
        putProp(CssProperty.USER_SELECT, _userSelect?.toCssString())
        putProp(
            CssProperty.OBJECT_FIT,
            when (_objectFit) {
                ObjectFit.CONTAIN -> "contain"
                ObjectFit.COVER -> "cover"
                ObjectFit.FILL -> "fill"
                ObjectFit.SCALE_DOWN -> "scale-down"
                ObjectFit.NONE -> "none"
                null -> null
            }
        )
        putProp(CssProperty.ASPECT_RATIO, _aspectRatio?.toCssString())
        putProp(CssProperty.Z_INDEX, _zIndex?.toString())
        putProp(CssProperty.TRANSFORM, _transform?.toCssString())
        putProp(CssProperty.TRANSFORM_ORIGIN, _transformOrigin?.toCssString())
        putProp(CssProperty.TRANSITION, _transitions?.joinToString(", ") { it.toCssString() })
        putProp(CssProperty.FILTER, _filter?.toCssString())
        putProp(CssProperty.BACKDROP_FILTER, _backdropFilter?.toCssString())

        return out
    }

    /**
     * Serializes this [ComponentStyle] into a standard inline CSS string (`prop: value [!important]; ...`).
     */
    fun toInlineCssString(): String =
        toCssPropertyMap().entries.joinToString("; ") { (prop, pair) ->
            val (value, isImp) = pair
            if (isImp) "$prop: $value !important" else "$prop: $value"
        }

    fun isEmpty(): Boolean = toCssPropertyMap().isEmpty()

    fun clear() {
        importantSet.clear()
        rawImportantCustomProps.clear()
        rawCustomDeclarations.clear()
        _backgroundColor = null
        _backgroundImage = null
        _backgroundSize = null
        _backgroundPosition = null
        _backgroundRepeat = null
        _backgroundAttachment = null
        _backgroundClip = null
        _backgroundOrigin = null
        _textColor = null
        _accentColor = null
        _opacity = null
        _display = null
        _visibility = null
        _position = null
        _top = null
        _right = null
        _bottom = null
        _left = null
        _width = null
        _height = null
        _minWidth = null
        _minHeight = null
        _maxWidth = null
        _maxHeight = null
        _margin = null
        _marginTop = null
        _marginRight = null
        _marginBottom = null
        _marginLeft = null
        _padding = null
        _paddingTop = null
        _paddingRight = null
        _paddingBottom = null
        _paddingLeft = null
        _boxSizing = null
        _floatSide = null
        _clearSide = null
        _borderWidth = null
        _borderStyle = null
        _borderColor = null
        _borderRadius = null
        _boxShadow = null
        _fontFamily = null
        _fontSize = null
        _fontWeight = null
        _fontWeightNumericOverride = null
        _fontStyle = null
        _lineHeight = null
        _letterSpacing = null
        _textAlign = null
        _textDecoration = null
        _textTransform = null
        _textOverflow = null
        _whiteSpace = null
        _wordBreak = null
        _verticalAlign = null
        _textShadow = null
        _flexDirection = null
        _flexWrap = null
        _justifyContent = null
        _alignItems = null
        _alignSelf = null
        _alignContent = null
        _flexGrow = null
        _flexShrink = null
        _flexBasis = null
        _gap = null
        _rowGap = null
        _columnGap = null
        _overflow = null
        _overflowX = null
        _overflowY = null
        _cursor = null
        _pointerEvents = null
        _userSelect = null
        _objectFit = null
        _aspectRatio = null
        _zIndex = null
        _transform = null
        _transformOrigin = null
        _transitions = null
        _filter = null
        _backdropFilter = null
        notifyChanged()
    }

    /**
     * Merges this [ComponentStyle] on top of an optional [base] [ComputedStyle], respecting
     * `!important` flags on [base] vs this [ComponentStyle].
     */
    fun toComputedStyle(base: ComputedStyle? = null): ComputedStyle {
        val baseStyle = base ?: ComputedStyle.EMPTY
        val propMap = toCssPropertyMap()
        if (propMap.isEmpty()) return baseStyle

        val mergedRaw = LinkedHashMap(baseStyle.rawDeclarations)
        val mergedImportant = LinkedHashSet(baseStyle.importantProperties)

        fun canOverride(propName: String, propEnum: CssProperty?): Boolean {
            val baseHasImportant = propName in baseStyle.importantProperties
            val selfHasImportant = (propEnum != null && isImportant(propEnum)) || (propName in rawImportantCustomProps)
            return !baseHasImportant || selfHasImportant
        }

        for ((propName, pair) in propMap) {
            val (cssVal, isImp) = pair
            val propEnum = CssProperty.fromCssName(propName)
            if (canOverride(propName, propEnum)) {
                mergedRaw[propName] = cssVal
                if (isImp) mergedImportant.add(propName)
            }
        }

        val resolvedBgColor = if (_backgroundColor != null && canOverride("background-color", CssProperty.BACKGROUND_COLOR)) {
            _backgroundColor
        } else {
            baseStyle.backgroundColor
        }
        val resolvedBgGradient = if (_backgroundImage?.gradient != null && canOverride("background-image", CssProperty.BACKGROUND_IMAGE)) {
            _backgroundImage?.gradient
        } else {
            baseStyle.backgroundGradient
        }
        val resolvedBgImageUrl = if (_backgroundImage != null && canOverride("background-image", CssProperty.BACKGROUND_IMAGE)) {
            _backgroundImage?.urlOrPath ?: baseStyle.backgroundImageUrl
        } else {
            baseStyle.backgroundImageUrl
        }
        val resolvedTextColor = if (_textColor != null && canOverride("color", CssProperty.COLOR)) {
            _textColor
        } else {
            baseStyle.textColor
        }
        val resolvedAccentColor = if (_accentColor != null && canOverride("accent-color", CssProperty.ACCENT_COLOR)) {
            _accentColor
        } else {
            baseStyle.accentColor
        }
        val resolvedBorderColor = if (_borderColor != null && canOverride("border-color", CssProperty.BORDER_COLOR)) {
            _borderColor
        } else {
            baseStyle.borderColor
        }
        val resolvedBorderWidthPx = if (_borderWidth != null && canOverride("border-width", CssProperty.BORDER_WIDTH)) {
            _borderWidth?.top?.resolvePx()
        } else {
            baseStyle.borderWidthPx
        }
        val resolvedBorderStyle = if (_borderStyle != null && canOverride("border-style", CssProperty.BORDER_STYLE)) {
            _borderStyle?.toCssString()
        } else {
            baseStyle.borderStyle
        }
        val resolvedRadiusPx = if (_borderRadius != null && canOverride("border-radius", CssProperty.BORDER_RADIUS)) {
            _borderRadius?.topLeft?.takeIf { it.unit != CssUnit.PERCENT }?.resolvePx()
        } else {
            baseStyle.borderRadiusPx
        }
        val resolvedRadiusPct = if (_borderRadius != null && canOverride("border-radius", CssProperty.BORDER_RADIUS)) {
            _borderRadius?.topLeft?.takeIf { it.unit == CssUnit.PERCENT }?.value
        } else {
            baseStyle.borderRadiusPercent
        }
        val resolvedFontSizePx = if (_fontSize != null && canOverride("font-size", CssProperty.FONT_SIZE)) {
            _fontSize?.takeIf { it.unit != CssUnit.PERCENT }?.resolvePx()
        } else {
            baseStyle.fontSizePx
        }
        val resolvedFontSizePct = if (_fontSize != null && canOverride("font-size", CssProperty.FONT_SIZE)) {
            _fontSize?.takeIf { it.unit == CssUnit.PERCENT }?.value
        } else {
            baseStyle.fontSizePercent
        }
        val resolvedFontFamily = if (_fontFamily != null && canOverride("font-family", CssProperty.FONT_FAMILY)) {
            _fontFamily?.resolvedPrimaryName()
        } else {
            baseStyle.fontFamily
        }
        val resolvedFontWeight = if ((_fontWeightNumericOverride != null || _fontWeight != null) && canOverride("font-weight", CssProperty.FONT_WEIGHT)) {
            _fontWeightNumericOverride ?: _fontWeight?.numericWeight
        } else {
            baseStyle.fontWeightNumeric
        }
        val resolvedOpacity = if (_opacity != null && canOverride("opacity", CssProperty.OPACITY)) {
            _opacity ?: 1.0f
        } else {
            baseStyle.opacity
        }
        val resolvedCursor = if (_cursor != null && canOverride("cursor", CssProperty.CURSOR)) {
            _cursor?.toCssString()
        } else {
            baseStyle.cursor
        }
        val resolvedCustomCursor = if (_cursor != null && canOverride("cursor", CssProperty.CURSOR)) {
            _cursor
        } else {
            baseStyle.customCursor
        }
        val resolvedPointerEvents = if (_pointerEvents != null && canOverride("pointer-events", CssProperty.POINTER_EVENTS)) {
            _pointerEvents != CssPointerEvents.NONE
        } else {
            baseStyle.pointerEventsEnabled
        }

        return baseStyle.copy(
            backgroundColor = resolvedBgColor,
            backgroundGradient = resolvedBgGradient,
            textColor = resolvedTextColor,
            accentColor = resolvedAccentColor,
            borderColor = resolvedBorderColor,
            borderWidthPx = resolvedBorderWidthPx,
            borderStyle = resolvedBorderStyle,
            borderRadiusPx = resolvedRadiusPx,
            borderRadiusPercent = resolvedRadiusPct,
            fontSizePx = resolvedFontSizePx,
            fontSizePercent = resolvedFontSizePct,
            fontFamily = resolvedFontFamily,
            fontWeightNumeric = resolvedFontWeight,
            opacity = resolvedOpacity,
            backgroundImageUrl = resolvedBgImageUrl,
            backgroundRepeat = if (_backgroundRepeat != null && canOverride("background-repeat", CssProperty.BACKGROUND_REPEAT)) _backgroundRepeat?.toCssString() else baseStyle.backgroundRepeat,
            backgroundAttachment = if (_backgroundAttachment != null && canOverride("background-attachment", CssProperty.BACKGROUND_ATTACHMENT)) _backgroundAttachment?.toCssString() else baseStyle.backgroundAttachment,
            backgroundPosition = if (_backgroundPosition != null && canOverride("background-position", CssProperty.BACKGROUND_POSITION)) _backgroundPosition?.toCssString() else baseStyle.backgroundPosition,
            backgroundSize = if (_backgroundSize != null && canOverride("background-size", CssProperty.BACKGROUND_SIZE)) _backgroundSize?.toCssString() else baseStyle.backgroundSize,
            textShadow = if (_textShadow != null && canOverride("text-shadow", CssProperty.TEXT_SHADOW)) _textShadow?.toCssString() else baseStyle.textShadow,
            boxShadow = if (_boxShadow != null && canOverride("box-shadow", CssProperty.BOX_SHADOW)) _boxShadow?.toCssString() else baseStyle.boxShadow,
            transform = if (_transform != null && canOverride("transform", CssProperty.TRANSFORM)) _transform?.toCssString() else baseStyle.transform,
            transition = if (_transitions != null && canOverride("transition", CssProperty.TRANSITION)) _transitions?.joinToString(", ") { it.toCssString() } else baseStyle.transition,
            zIndex = if (_zIndex != null && canOverride("z-index", CssProperty.Z_INDEX)) _zIndex else baseStyle.zIndex,
            aspectRatio = if (_aspectRatio != null && canOverride("aspect-ratio", CssProperty.ASPECT_RATIO)) _aspectRatio?.numericRatio else baseStyle.aspectRatio,
            pointerEventsEnabled = resolvedPointerEvents,
            cursor = resolvedCursor,
            customCursor = resolvedCustomCursor,
            position = if (_position != null && canOverride("position", CssProperty.POSITION)) _position?.toCssString() else baseStyle.position,
            display = if (_display != null && canOverride("display", CssProperty.DISPLAY)) _display?.toCssString() else baseStyle.display,
            overflow = if (_overflow != null && canOverride("overflow", CssProperty.OVERFLOW)) mergedRaw["overflow"] else baseStyle.overflow,
            textAlign = if (_textAlign != null && canOverride("text-align", CssProperty.TEXT_ALIGN)) mergedRaw["text-align"] else baseStyle.textAlign,
            verticalAlign = if (_verticalAlign != null && canOverride("vertical-align", CssProperty.VERTICAL_ALIGN)) _verticalAlign?.toCssString() else baseStyle.verticalAlign,
            whiteSpace = if (_whiteSpace != null && canOverride("white-space", CssProperty.WHITE_SPACE)) _whiteSpace?.toCssString() else baseStyle.whiteSpace,
            floatSide = if (_floatSide != null && canOverride("float", CssProperty.FLOAT)) _floatSide?.toCssString() else baseStyle.floatSide,
            clearSide = if (_clearSide != null && canOverride("clear", CssProperty.CLEAR)) _clearSide?.toCssString() else baseStyle.clearSide,
            left = if (_left != null && canOverride("left", CssProperty.LEFT)) _left?.toCssString() else baseStyle.left,
            top = if (_top != null && canOverride("top", CssProperty.TOP)) _top?.toCssString() else baseStyle.top,
            right = if (_right != null && canOverride("right", CssProperty.RIGHT)) _right?.toCssString() else baseStyle.right,
            bottom = if (_bottom != null && canOverride("bottom", CssProperty.BOTTOM)) _bottom?.toCssString() else baseStyle.bottom,
            width = if (_width != null && canOverride("width", CssProperty.WIDTH)) _width?.toCssString() else baseStyle.width,
            height = if (_height != null && canOverride("height", CssProperty.HEIGHT)) _height?.toCssString() else baseStyle.height,
            minWidth = if (_minWidth != null && canOverride("min-width", CssProperty.MIN_WIDTH)) _minWidth?.toCssString() else baseStyle.minWidth,
            minHeight = if (_minHeight != null && canOverride("min-height", CssProperty.MIN_HEIGHT)) _minHeight?.toCssString() else baseStyle.minHeight,
            maxWidth = if (_maxWidth != null && canOverride("max-width", CssProperty.MAX_WIDTH)) _maxWidth?.toCssString() else baseStyle.maxWidth,
            maxHeight = if (_maxHeight != null && canOverride("max-height", CssProperty.MAX_HEIGHT)) _maxHeight?.toCssString() else baseStyle.maxHeight,
            rawDeclarations = mergedRaw,
            importantProperties = mergedImportant
        )
    }

    companion object {
        fun fromDeclarations(declarations: Map<String, String>): ComponentStyle {
            val style = ComponentStyle()
            for ((k, v) in declarations) {
                style.setRawDeclaration(k, v)
            }
            return style
        }
    }
}
