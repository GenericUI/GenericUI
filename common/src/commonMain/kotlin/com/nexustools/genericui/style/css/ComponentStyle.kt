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
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionSpec
import kotlin.jvm.JvmName

/**
 * Strongly-typed CSS style object attached to every component (`component.style` / `component.getStyle()`).
 *
 * Uses 128-bit bitmasks (`activeBitmaskLow`/`activeBitmaskHigh` and `importantBitmaskLow`/`importantBitmaskHigh`)
 * indexed by [CssProperty.ordinal] with zero string-keyed declaration maps.
 */
class ComponentStyle(
    internal var onChange: (() -> Unit)? = null,
    internal var onPropertyChange: ((CssProperty) -> Unit)? = null
) {

    private var activeBitmaskLow: Long = 0L
    private var activeBitmaskHigh: Long = 0L
    private var importantBitmaskLow: Long = 0L
    private var importantBitmaskHigh: Long = 0L
    private var defaultImportantScope: Boolean = false
    private var suppressInternalNotifications: Boolean = false
    private var lastMarkedProperty: CssProperty? = null

    val activeBitmaskLowValue: Long
        get() = activeBitmaskLow

    val activeBitmaskHighValue: Long
        get() = activeBitmaskHigh

    val importantBitmaskLowValue: Long
        get() = importantBitmaskLow

    val importantBitmaskHighValue: Long
        get() = importantBitmaskHigh

    // --- 1. Background & Color ---
    private var _backgroundColor: RgbaColor? = null
    private var _backgroundImage: CssBackgroundImage? = null
    private var _backgroundSize: CssBackgroundSize? = null
    private var _backgroundPosition: CssBackgroundPosition? = null
    private var _backgroundRepeat: CssBackgroundRepeat? = null
    private var _backgroundAttachment: CssBackgroundAttachment? = null
    private var _backgroundClip: CssBackgroundClip? = null
    private var _backgroundOrigin: CssBackgroundOrigin? = null
    private var _color: RgbaColor? = null
    private var _accentColor: RgbaColor? = null
    private var _opacity: Float? = null

    // --- 2. Box Model, Sizing, & Positioning ---
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

    // --- 3. Borders, Corner Radii, Outlines, & Shadows ---
    private var _borderWidth: CssEdgeLengths? = null
    private var _borderStyle: CssBorderStyle? = null
    private var _borderColor: RgbaColor? = null
    private var _borderTopColor: RgbaColor? = null
    private var _borderRightColor: RgbaColor? = null
    private var _borderBottomColor: RgbaColor? = null
    private var _borderLeftColor: RgbaColor? = null
    private var _borderRadius: CssCornerRadii? = null
    private var _outlineWidth: CssLength? = null
    private var _outlineStyle: CssBorderStyle? = null
    private var _outlineColor: RgbaColor? = null
    private var _outlineOffset: CssLength? = null
    private var _focusRingColor: RgbaColor? = null
    private var _focusRingWidthPx: Float? = null
    private var _boxShadow: CssShadowList? = null
    private var _textShadow: CssShadowList? = null

    // --- 4. Typography ---
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

    // --- 5. Flexbox & Grid Alignment ---
    private var _flexDirection: FlexDirection? = null
    private var _flexWrap: FlexWrap? = null
    private var _justifyContent: JustifyContent? = null
    private var _alignItems: AlignItems? = null
    private var _alignSelf: AlignSelf? = null
    private var _alignContent: CssAlignContent? = null
    private var _flexGrow: Float? = null
    private var _flexShrink: Float? = null
    private var _flexBasis: CssLength? = null
    private var _order: Int? = null
    private var _gap: CssLength? = null
    private var _rowGap: CssLength? = null
    private var _columnGap: CssLength? = null

    // --- 6. Overflow, Interaction, Cursor, Media Fit, Transform, Transition, & Filter ---
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

    private fun setActiveBit(property: CssProperty, present: Boolean) {
        val ord = property.ordinal
        if (ord < 64) {
            val mask = 1L shl ord
            activeBitmaskLow = if (present) activeBitmaskLow or mask else activeBitmaskLow and mask.inv()
        } else {
            val mask = 1L shl (ord - 64)
            activeBitmaskHigh = if (present) activeBitmaskHigh or mask else activeBitmaskHigh and mask.inv()
        }
    }

    private fun setImportantBit(property: CssProperty, important: Boolean) {
        val ord = property.ordinal
        if (ord < 64) {
            val mask = 1L shl ord
            importantBitmaskLow = if (important) importantBitmaskLow or mask else importantBitmaskLow and mask.inv()
        } else {
            val mask = 1L shl (ord - 64)
            importantBitmaskHigh = if (important) importantBitmaskHigh or mask else importantBitmaskHigh and mask.inv()
        }
    }

    private fun markProperty(property: CssProperty, present: Boolean, important: Boolean) {
        setActiveBit(property, present)
        setImportantBit(property, present && (important || defaultImportantScope))
        lastMarkedProperty = property
    }

    private fun updateImportance(property: CssProperty, present: Boolean, important: Boolean) {
        markProperty(property, present, important)
    }

    private fun notifyChanged() {
        val changedProp = lastMarkedProperty
        lastMarkedProperty = null
        if (suppressInternalNotifications) return
        if (changedProp != null && onPropertyChange != null) {
            onPropertyChange?.invoke(changedProp)
        } else {
            onChange?.invoke()
        }
    }

    fun hasProperty(property: CssProperty): Boolean {
        val ord = property.ordinal
        return if (ord < 64) {
            (activeBitmaskLow and (1L shl ord)) != 0L
        } else {
            (activeBitmaskHigh and (1L shl (ord - 64))) != 0L
        }
    }

    /**
     * Checks whether [property] is currently marked `!important`.
     */
    fun isImportant(property: CssProperty): Boolean {
        val ord = property.ordinal
        return if (ord < 64) {
            (importantBitmaskLow and (1L shl ord)) != 0L
        } else {
            (importantBitmaskHigh and (1L shl (ord - 64))) != 0L
        }
    }

    /**
     * Explicitly marks or unmarks [property] as `!important`.
     */
    fun markImportant(property: CssProperty, important: Boolean = true) {
        setImportantBit(property, important)
        if (!suppressInternalNotifications) {
            if (onPropertyChange != null) {
                onPropertyChange?.invoke(property)
            } else {
                onChange?.invoke()
            }
        }
    }

    fun setImportant(property: CssProperty, important: Boolean = true) {
        markImportant(property, important)
    }

    fun isDefaultImportant(): Boolean = defaultImportantScope

    fun setDefaultImportant(value: Boolean) {
        defaultImportantScope = value
    }

    /**
     * Runs [block] where every property set inside the block is automatically marked `!important`.
     */
    fun important(block: ComponentStyle.() -> Unit): ComponentStyle {
        val prev = defaultImportantScope
        val prevSuppress = suppressInternalNotifications
        defaultImportantScope = true
        suppressInternalNotifications = true
        try {
            this.block()
        } finally {
            defaultImportantScope = prev
            suppressInternalNotifications = prevSuppress
            lastMarkedProperty = null
        }
        if (!suppressInternalNotifications) {
            onChange?.invoke()
        }
        return this
    }

    /**
     * Mutates multiple properties in a single batch and notifies listeners once.
     */
    fun update(block: ComponentStyle.() -> Unit): ComponentStyle {
        val prevSuppress = suppressInternalNotifications
        suppressInternalNotifications = true
        try {
            this.block()
        } finally {
            suppressInternalNotifications = prevSuppress
            lastMarkedProperty = null
        }
        if (!suppressInternalNotifications) {
            onChange?.invoke()
        }
        return this
    }

    // -------------------------------------------------------------------------
    // 1. Background & Color
    // -------------------------------------------------------------------------

    fun getBackgroundColor(): RgbaColor? = _backgroundColor
    fun setBackgroundColor(color: RgbaColor?, important: Boolean = false) {
        _backgroundColor = color
        updateImportance(CssProperty.BACKGROUND_COLOR, color != null, important)
        notifyChanged()
    }
    fun setBackgroundColorHex(hex: String?, important: Boolean = false) {
        setBackgroundColor(hex?.let { RgbaColor.fromCss(it) }, important)
    }

    var backgroundColor: RgbaColor?
        @JvmName("obtainBackgroundColor") get() = _backgroundColor
        @JvmName("assignBackgroundColor") set(value) = setBackgroundColor(value)

    var backgroundColorHex: String?
        @JvmName("obtainBackgroundColorHex") get() = _backgroundColor?.toHex()
        @JvmName("assignBackgroundColorHex") set(value) = setBackgroundColorHex(value)

    fun getBackgroundImage(): CssBackgroundImage? = _backgroundImage
    fun setBackgroundImage(image: CssBackgroundImage?, important: Boolean = false) {
        _backgroundImage = image
        updateImportance(CssProperty.BACKGROUND_IMAGE, image != null, important)
        notifyChanged()
    }
    fun setBackgroundGradient(gradient: Gradient?, important: Boolean = false) {
        setBackgroundImage(gradient?.let { CssBackgroundImage.fromGradient(it) }, important)
    }
    fun setBackgroundImageUrl(url: String?, important: Boolean = false) {
        setBackgroundImage(url?.takeIf { it.isNotBlank() }?.let { CssBackgroundImage.fromUrl(it) }, important)
    }

    var backgroundImage: CssBackgroundImage?
        @JvmName("obtainBackgroundImage") get() = _backgroundImage
        @JvmName("assignBackgroundImage") set(value) = setBackgroundImage(value)

    var backgroundGradient: Gradient?
        @JvmName("obtainBackgroundGradient") get() = _backgroundImage?.gradient
        @JvmName("assignBackgroundGradient") set(value) = setBackgroundGradient(value)

    var background: Gradient?
        @JvmName("obtainBackground") get() = _backgroundImage?.gradient
        @JvmName("assignBackground") set(value) = setBackgroundGradient(value)

    var backgroundImageUrl: String?
        @JvmName("obtainBackgroundImageUrl") get() = _backgroundImage?.urlOrPath
        @JvmName("assignBackgroundImageUrl") set(value) = setBackgroundImageUrl(value)

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

    fun getColor(): RgbaColor? = _color
    fun setColor(color: RgbaColor?, important: Boolean = false) {
        _color = color
        updateImportance(CssProperty.COLOR, color != null, important)
        notifyChanged()
    }
    fun getTextColor(): RgbaColor? = _color
    fun setTextColor(color: RgbaColor?, important: Boolean = false) = setColor(color, important)
    fun setTextColorHex(hex: String?, important: Boolean = false) {
        setColor(hex?.let { RgbaColor.fromCss(it) }, important)
    }

    var color: RgbaColor?
        @JvmName("obtainColor") get() = _color
        @JvmName("assignColor") set(value) = setColor(value)

    var textColor: RgbaColor?
        @JvmName("obtainTextColor") get() = _color
        @JvmName("assignTextColor") set(value) = setColor(value)

    var textColorHex: String?
        @JvmName("obtainTextColorHex") get() = _color?.toHex()
        @JvmName("assignTextColorHex") set(value) = setTextColorHex(value)

    fun getAccentColor(): RgbaColor? = _accentColor
    fun setAccentColor(color: RgbaColor?, important: Boolean = false) {
        _accentColor = color
        updateImportance(CssProperty.ACCENT_COLOR, color != null, important)
        notifyChanged()
    }
    fun setAccentColorHex(hex: String?, important: Boolean = false) {
        setAccentColor(hex?.let { RgbaColor.fromCss(it) }, important)
    }

    var accentColor: RgbaColor?
        @JvmName("obtainAccentColor") get() = _accentColor
        @JvmName("assignAccentColor") set(value) = setAccentColor(value)

    var accentColorHex: String?
        @JvmName("obtainAccentColorHex") get() = _accentColor?.toHex()
        @JvmName("assignAccentColorHex") set(value) = setAccentColorHex(value)

    fun getOpacity(): Float? = _opacity
    fun setOpacity(opacity: Float?, important: Boolean = false) {
        _opacity = opacity?.coerceIn(0f, 1f)
        updateImportance(CssProperty.OPACITY, _opacity != null, important)
        notifyChanged()
    }

    var opacity: Float?
        @JvmName("obtainOpacity") get() = _opacity
        @JvmName("assignOpacity") set(value) = setOpacity(value)

    // -------------------------------------------------------------------------
    // 2. Box Model, Sizing, & Positioning
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
    fun setWidthPx(px: Int?, important: Boolean = false) = setWidth(px?.let { CssLength.px(it) }, important)
    fun setWidthPercent(percent: Float?, important: Boolean = false) = setWidth(percent?.let { CssLength.percent(it) }, important)

    var width: CssLength?
        @JvmName("obtainWidth") get() = _width
        @JvmName("assignWidth") set(value) = setWidth(value)

    fun getHeight(): CssLength? = _height
    fun setHeight(height: CssLength?, important: Boolean = false) {
        _height = height
        updateImportance(CssProperty.HEIGHT, height != null, important)
        notifyChanged()
    }
    fun setHeightPx(px: Int?, important: Boolean = false) = setHeight(px?.let { CssLength.px(it) }, important)
    fun setHeightPercent(percent: Float?, important: Boolean = false) = setHeight(percent?.let { CssLength.percent(it) }, important)

    var height: CssLength?
        @JvmName("obtainHeight") get() = _height
        @JvmName("assignHeight") set(value) = setHeight(value)

    fun getMinWidth(): CssLength? = _minWidth
    fun setMinWidth(minWidth: CssLength?, important: Boolean = false) {
        _minWidth = minWidth
        updateImportance(CssProperty.MIN_WIDTH, minWidth != null, important)
        notifyChanged()
    }
    fun setMinWidthPx(px: Int?, important: Boolean = false) = setMinWidth(px?.let { CssLength.px(it) }, important)

    var minWidth: CssLength?
        @JvmName("obtainMinWidth") get() = _minWidth
        @JvmName("assignMinWidth") set(value) = setMinWidth(value)

    fun getMinHeight(): CssLength? = _minHeight
    fun setMinHeight(minHeight: CssLength?, important: Boolean = false) {
        _minHeight = minHeight
        updateImportance(CssProperty.MIN_HEIGHT, minHeight != null, important)
        notifyChanged()
    }
    fun setMinHeightPx(px: Int?, important: Boolean = false) = setMinHeight(px?.let { CssLength.px(it) }, important)

    var minHeight: CssLength?
        @JvmName("obtainMinHeight") get() = _minHeight
        @JvmName("assignMinHeight") set(value) = setMinHeight(value)

    fun getMaxWidth(): CssLength? = _maxWidth
    fun setMaxWidth(maxWidth: CssLength?, important: Boolean = false) {
        _maxWidth = maxWidth
        updateImportance(CssProperty.MAX_WIDTH, maxWidth != null, important)
        notifyChanged()
    }
    fun setMaxWidthPx(px: Int?, important: Boolean = false) = setMaxWidth(px?.let { CssLength.px(it) }, important)

    var maxWidth: CssLength?
        @JvmName("obtainMaxWidth") get() = _maxWidth
        @JvmName("assignMaxWidth") set(value) = setMaxWidth(value)

    fun getMaxHeight(): CssLength? = _maxHeight
    fun setMaxHeight(maxHeight: CssLength?, important: Boolean = false) {
        _maxHeight = maxHeight
        updateImportance(CssProperty.MAX_HEIGHT, maxHeight != null, important)
        notifyChanged()
    }
    fun setMaxHeightPx(px: Int?, important: Boolean = false) = setMaxHeight(px?.let { CssLength.px(it) }, important)

    var maxHeight: CssLength?
        @JvmName("obtainMaxHeight") get() = _maxHeight
        @JvmName("assignMaxHeight") set(value) = setMaxHeight(value)

    fun getMargin(): CssEdgeLengths? = _margin
    fun setMargin(margin: CssEdgeLengths?, important: Boolean = false) {
        _margin = margin
        updateImportance(CssProperty.MARGIN, margin != null, important)
        notifyChanged()
    }
    fun setMarginInsets(insets: EdgeInsets?, important: Boolean = false) {
        setMargin(insets?.let { CssEdgeLengths.fromEdgeInsets(it) }, important)
    }

    var margin: CssEdgeLengths?
        @JvmName("obtainMargin") get() = _margin
        @JvmName("assignMargin") set(value) = setMargin(value)

    fun getMarginTop(): CssLength? = _marginTop ?: _margin?.top
    fun setMarginTop(marginTop: CssLength?, important: Boolean = false) {
        _marginTop = marginTop
        updateImportance(CssProperty.MARGIN_TOP, marginTop != null, important)
        notifyChanged()
    }

    var marginTop: CssLength?
        @JvmName("obtainMarginTop") get() = _marginTop
        @JvmName("assignMarginTop") set(value) = setMarginTop(value)

    fun getMarginRight(): CssLength? = _marginRight ?: _margin?.right
    fun setMarginRight(marginRight: CssLength?, important: Boolean = false) {
        _marginRight = marginRight
        updateImportance(CssProperty.MARGIN_RIGHT, marginRight != null, important)
        notifyChanged()
    }

    var marginRight: CssLength?
        @JvmName("obtainMarginRight") get() = _marginRight
        @JvmName("assignMarginRight") set(value) = setMarginRight(value)

    fun getMarginBottom(): CssLength? = _marginBottom ?: _margin?.bottom
    fun setMarginBottom(marginBottom: CssLength?, important: Boolean = false) {
        _marginBottom = marginBottom
        updateImportance(CssProperty.MARGIN_BOTTOM, marginBottom != null, important)
        notifyChanged()
    }

    var marginBottom: CssLength?
        @JvmName("obtainMarginBottom") get() = _marginBottom
        @JvmName("assignMarginBottom") set(value) = setMarginBottom(value)

    fun getMarginLeft(): CssLength? = _marginLeft ?: _margin?.left
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
    fun setPaddingInsets(insets: EdgeInsets?, important: Boolean = false) {
        setPadding(insets?.let { CssEdgeLengths.fromEdgeInsets(it) }, important)
    }

    var padding: CssEdgeLengths?
        @JvmName("obtainPadding") get() = _padding
        @JvmName("assignPadding") set(value) = setPadding(value)

    fun getPaddingTop(): CssLength? = _paddingTop ?: _padding?.top
    fun setPaddingTop(paddingTop: CssLength?, important: Boolean = false) {
        _paddingTop = paddingTop
        updateImportance(CssProperty.PADDING_TOP, paddingTop != null, important)
        notifyChanged()
    }

    var paddingTop: CssLength?
        @JvmName("obtainPaddingTop") get() = _paddingTop
        @JvmName("assignPaddingTop") set(value) = setPaddingTop(value)

    fun getPaddingRight(): CssLength? = _paddingRight ?: _padding?.right
    fun setPaddingRight(paddingRight: CssLength?, important: Boolean = false) {
        _paddingRight = paddingRight
        updateImportance(CssProperty.PADDING_RIGHT, paddingRight != null, important)
        notifyChanged()
    }

    var paddingRight: CssLength?
        @JvmName("obtainPaddingRight") get() = _paddingRight
        @JvmName("assignPaddingRight") set(value) = setPaddingRight(value)

    fun getPaddingBottom(): CssLength? = _paddingBottom ?: _padding?.bottom
    fun setPaddingBottom(paddingBottom: CssLength?, important: Boolean = false) {
        _paddingBottom = paddingBottom
        updateImportance(CssProperty.PADDING_BOTTOM, paddingBottom != null, important)
        notifyChanged()
    }

    var paddingBottom: CssLength?
        @JvmName("obtainPaddingBottom") get() = _paddingBottom
        @JvmName("assignPaddingBottom") set(value) = setPaddingBottom(value)

    fun getPaddingLeft(): CssLength? = _paddingLeft ?: _padding?.left
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
    // 3. Borders, Corner Radii, Outlines, & Shadows
    // -------------------------------------------------------------------------

    fun getBorderWidth(): CssEdgeLengths? = _borderWidth
    fun setBorderWidth(borderWidth: CssEdgeLengths?, important: Boolean = false) {
        _borderWidth = borderWidth
        updateImportance(CssProperty.BORDER_WIDTH, borderWidth != null, important)
        notifyChanged()
    }
    fun setBorderWidthPx(px: Float?, important: Boolean = false) {
        setBorderWidth(px?.let { CssEdgeLengths.all(CssLength.px(it)) }, important)
    }

    var borderWidth: CssEdgeLengths?
        @JvmName("obtainBorderWidth") get() = _borderWidth
        @JvmName("assignBorderWidth") set(value) = setBorderWidth(value)

    var borderWidthPx: Float?
        @JvmName("obtainBorderWidthPx") get() = _borderWidth?.top?.resolvePx()
        @JvmName("assignBorderWidthPx") set(value) = setBorderWidthPx(value)

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
    fun setBorderColorHex(hex: String?, important: Boolean = false) {
        setBorderColor(hex?.let { RgbaColor.fromCss(it) }, important)
    }

    var borderColor: RgbaColor?
        @JvmName("obtainBorderColor") get() = _borderColor
        @JvmName("assignBorderColor") set(value) = setBorderColor(value)

    var borderColorHex: String?
        @JvmName("obtainBorderColorHex") get() = _borderColor?.toHex()
        @JvmName("assignBorderColorHex") set(value) = setBorderColorHex(value)

    fun getBorderTopColor(): RgbaColor? = _borderTopColor
    fun setBorderTopColor(color: RgbaColor?, important: Boolean = false) {
        _borderTopColor = color
        updateImportance(CssProperty.BORDER_COLOR, color != null || _borderColor != null, important)
        notifyChanged()
    }

    var borderTopColor: RgbaColor?
        @JvmName("obtainBorderTopColor") get() = _borderTopColor
        @JvmName("assignBorderTopColor") set(value) = setBorderTopColor(value)

    fun getBorderRightColor(): RgbaColor? = _borderRightColor
    fun setBorderRightColor(color: RgbaColor?, important: Boolean = false) {
        _borderRightColor = color
        updateImportance(CssProperty.BORDER_COLOR, color != null || _borderColor != null, important)
        notifyChanged()
    }

    var borderRightColor: RgbaColor?
        @JvmName("obtainBorderRightColor") get() = _borderRightColor
        @JvmName("assignBorderRightColor") set(value) = setBorderRightColor(value)

    fun getBorderBottomColor(): RgbaColor? = _borderBottomColor
    fun setBorderBottomColor(color: RgbaColor?, important: Boolean = false) {
        _borderBottomColor = color
        updateImportance(CssProperty.BORDER_COLOR, color != null || _borderColor != null, important)
        notifyChanged()
    }

    var borderBottomColor: RgbaColor?
        @JvmName("obtainBorderBottomColor") get() = _borderBottomColor
        @JvmName("assignBorderBottomColor") set(value) = setBorderBottomColor(value)

    fun getBorderLeftColor(): RgbaColor? = _borderLeftColor
    fun setBorderLeftColor(color: RgbaColor?, important: Boolean = false) {
        _borderLeftColor = color
        updateImportance(CssProperty.BORDER_COLOR, color != null || _borderColor != null, important)
        notifyChanged()
    }

    var borderLeftColor: RgbaColor?
        @JvmName("obtainBorderLeftColor") get() = _borderLeftColor
        @JvmName("assignBorderLeftColor") set(value) = setBorderLeftColor(value)

    fun setBorder(
        width: CssLength = CssLength.px(1f),
        style: CssBorderStyle = CssBorderStyle.SOLID,
        color: RgbaColor,
        important: Boolean = false
    ) {
        val prevSuppress = suppressInternalNotifications
        suppressInternalNotifications = true
        try {
            setBorderWidth(CssEdgeLengths.all(width), important)
            setBorderStyle(style, important)
            setBorderColor(color, important)
            updateImportance(CssProperty.BORDER, true, important)
        } finally {
            suppressInternalNotifications = prevSuppress
            lastMarkedProperty = null
        }
        if (!suppressInternalNotifications) {
            onChange?.invoke()
        }
    }

    fun getBorderRadius(): CssCornerRadii? = _borderRadius
    fun setBorderRadius(borderRadius: CssCornerRadii?, important: Boolean = false) {
        _borderRadius = borderRadius
        updateImportance(CssProperty.BORDER_RADIUS, borderRadius != null, important)
        notifyChanged()
    }
    fun setBorderRadiusPx(px: Float?, important: Boolean = false) {
        setBorderRadius(px?.let { CssCornerRadii.px(it) }, important)
    }

    var borderRadius: CssCornerRadii?
        @JvmName("obtainBorderRadius") get() = _borderRadius
        @JvmName("assignBorderRadius") set(value) = setBorderRadius(value)

    var borderRadiusPx: Float?
        @JvmName("obtainBorderRadiusPx") get() = _borderRadius?.topLeft?.resolvePx()
        @JvmName("assignBorderRadiusPx") set(value) = setBorderRadiusPx(value)

    fun getOutlineWidth(): CssLength? = _outlineWidth
    fun setOutlineWidth(width: CssLength?, important: Boolean = false) {
        _outlineWidth = width
        updateImportance(CssProperty.OUTLINE_WIDTH, width != null, important)
        notifyChanged()
    }

    var outlineWidth: CssLength?
        @JvmName("obtainOutlineWidth") get() = _outlineWidth
        @JvmName("assignOutlineWidth") set(value) = setOutlineWidth(value)

    fun getOutlineStyle(): CssBorderStyle? = _outlineStyle
    fun setOutlineStyle(style: CssBorderStyle?, important: Boolean = false) {
        _outlineStyle = style
        updateImportance(CssProperty.OUTLINE_STYLE, style != null, important)
        notifyChanged()
    }

    var outlineStyle: CssBorderStyle?
        @JvmName("obtainOutlineStyle") get() = _outlineStyle
        @JvmName("assignOutlineStyle") set(value) = setOutlineStyle(value)

    fun getOutlineColor(): RgbaColor? = _outlineColor
    fun setOutlineColor(color: RgbaColor?, important: Boolean = false) {
        _outlineColor = color
        updateImportance(CssProperty.OUTLINE_COLOR, color != null, important)
        notifyChanged()
    }

    var outlineColor: RgbaColor?
        @JvmName("obtainOutlineColor") get() = _outlineColor
        @JvmName("assignOutlineColor") set(value) = setOutlineColor(value)

    fun getOutlineOffset(): CssLength? = _outlineOffset
    fun setOutlineOffset(offset: CssLength?, important: Boolean = false) {
        _outlineOffset = offset
        updateImportance(CssProperty.OUTLINE, offset != null || _outlineWidth != null || _outlineStyle != null || _outlineColor != null, important)
        notifyChanged()
    }

    var outlineOffset: CssLength?
        @JvmName("obtainOutlineOffset") get() = _outlineOffset
        @JvmName("assignOutlineOffset") set(value) = setOutlineOffset(value)

    fun getFocusRingColor(): RgbaColor? = _focusRingColor
    fun setFocusRingColor(color: RgbaColor?, important: Boolean = false) {
        _focusRingColor = color
        updateImportance(CssProperty.OUTLINE_COLOR, color != null || _outlineColor != null, important)
        notifyChanged()
    }

    var focusRingColor: RgbaColor?
        @JvmName("obtainFocusRingColor") get() = _focusRingColor
        @JvmName("assignFocusRingColor") set(value) = setFocusRingColor(value)

    fun getFocusRingWidthPx(): Float? = _focusRingWidthPx
    fun setFocusRingWidthPx(widthPx: Float?, important: Boolean = false) {
        _focusRingWidthPx = widthPx
        updateImportance(CssProperty.OUTLINE_WIDTH, widthPx != null || _outlineWidth != null, important)
        notifyChanged()
    }

    var focusRingWidthPx: Float?
        @JvmName("obtainFocusRingWidthPx") get() = _focusRingWidthPx
        @JvmName("assignFocusRingWidthPx") set(value) = setFocusRingWidthPx(value)

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
    fun setFontSizePx(px: Float?, important: Boolean = false) = setFontSize(px?.let { CssLength.px(it) }, important)

    var fontSize: CssLength?
        @JvmName("obtainFontSize") get() = _fontSize
        @JvmName("assignFontSize") set(value) = setFontSize(value)

    var fontSizePx: Float?
        @JvmName("obtainFontSizePx") get() = _fontSize?.resolvePx()
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

    fun setFlex(
        grow: Float?,
        shrink: Float? = null,
        basis: CssLength? = null,
        important: Boolean = false
    ) {
        val prevSuppress = suppressInternalNotifications
        suppressInternalNotifications = true
        try {
            setFlexGrow(grow, important)
            setFlexShrink(shrink, important)
            setFlexBasis(basis, important)
        } finally {
            suppressInternalNotifications = prevSuppress
            lastMarkedProperty = null
        }
        if (!suppressInternalNotifications) {
            onChange?.invoke()
        }
    }

    fun getOrder(): Int? = _order
    fun setOrder(order: Int?, important: Boolean = false) {
        _order = order
        updateImportance(CssProperty.ORDER, order != null, important)
        notifyChanged()
    }

    var order: Int?
        @JvmName("obtainOrder") get() = _order
        @JvmName("assignOrder") set(value) = setOrder(value)

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
    // Copy, Merge, Filter, & Clear Operations
    // -------------------------------------------------------------------------

    fun clear() {
        val prevSuppress = suppressInternalNotifications
        suppressInternalNotifications = true
        try {
            activeBitmaskLow = 0L
            activeBitmaskHigh = 0L
            importantBitmaskLow = 0L
            importantBitmaskHigh = 0L
            _backgroundColor = null
            _backgroundImage = null
            _backgroundSize = null
            _backgroundPosition = null
            _backgroundRepeat = null
            _backgroundAttachment = null
            _backgroundClip = null
            _backgroundOrigin = null
            _color = null
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
            _borderTopColor = null
            _borderRightColor = null
            _borderBottomColor = null
            _borderLeftColor = null
            _borderRadius = null
            _outlineWidth = null
            _outlineStyle = null
            _outlineColor = null
            _outlineOffset = null
            _focusRingColor = null
            _focusRingWidthPx = null
            _boxShadow = null
            _textShadow = null
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
            _flexDirection = null
            _flexWrap = null
            _justifyContent = null
            _alignItems = null
            _alignSelf = null
            _alignContent = null
            _flexGrow = null
            _flexShrink = null
            _flexBasis = null
            _order = null
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
        } finally {
            suppressInternalNotifications = prevSuppress
            lastMarkedProperty = null
        }
        if (!suppressInternalNotifications) {
            onChange?.invoke()
        }
    }

    fun isEmpty(): Boolean = activeBitmaskLow == 0L && activeBitmaskHigh == 0L

    fun copyFrom(other: ComponentStyle) {
        val prevSuppress = suppressInternalNotifications
        suppressInternalNotifications = true
        try {
            activeBitmaskLow = other.activeBitmaskLow
            activeBitmaskHigh = other.activeBitmaskHigh
            importantBitmaskLow = other.importantBitmaskLow
            importantBitmaskHigh = other.importantBitmaskHigh
            _backgroundColor = other._backgroundColor
            _backgroundImage = other._backgroundImage
            _backgroundSize = other._backgroundSize
            _backgroundPosition = other._backgroundPosition
            _backgroundRepeat = other._backgroundRepeat
            _backgroundAttachment = other._backgroundAttachment
            _backgroundClip = other._backgroundClip
            _backgroundOrigin = other._backgroundOrigin
            _color = other._color
            _accentColor = other._accentColor
            _opacity = other._opacity
            _display = other._display
            _visibility = other._visibility
            _position = other._position
            _top = other._top
            _right = other._right
            _bottom = other._bottom
            _left = other._left
            _width = other._width
            _height = other._height
            _minWidth = other._minWidth
            _minHeight = other._minHeight
            _maxWidth = other._maxWidth
            _maxHeight = other._maxHeight
            _margin = other._margin
            _marginTop = other._marginTop
            _marginRight = other._marginRight
            _marginBottom = other._marginBottom
            _marginLeft = other._marginLeft
            _padding = other._padding
            _paddingTop = other._paddingTop
            _paddingRight = other._paddingRight
            _paddingBottom = other._paddingBottom
            _paddingLeft = other._paddingLeft
            _boxSizing = other._boxSizing
            _floatSide = other._floatSide
            _clearSide = other._clearSide
            _borderWidth = other._borderWidth
            _borderStyle = other._borderStyle
            _borderColor = other._borderColor
            _borderTopColor = other._borderTopColor
            _borderRightColor = other._borderRightColor
            _borderBottomColor = other._borderBottomColor
            _borderLeftColor = other._borderLeftColor
            _borderRadius = other._borderRadius
            _outlineWidth = other._outlineWidth
            _outlineStyle = other._outlineStyle
            _outlineColor = other._outlineColor
            _outlineOffset = other._outlineOffset
            _focusRingColor = other._focusRingColor
            _focusRingWidthPx = other._focusRingWidthPx
            _boxShadow = other._boxShadow
            _textShadow = other._textShadow
            _fontFamily = other._fontFamily
            _fontSize = other._fontSize
            _fontWeight = other._fontWeight
            _fontWeightNumericOverride = other._fontWeightNumericOverride
            _fontStyle = other._fontStyle
            _lineHeight = other._lineHeight
            _letterSpacing = other._letterSpacing
            _textAlign = other._textAlign
            _textDecoration = other._textDecoration
            _textTransform = other._textTransform
            _textOverflow = other._textOverflow
            _whiteSpace = other._whiteSpace
            _wordBreak = other._wordBreak
            _verticalAlign = other._verticalAlign
            _flexDirection = other._flexDirection
            _flexWrap = other._flexWrap
            _justifyContent = other._justifyContent
            _alignItems = other._alignItems
            _alignSelf = other._alignSelf
            _alignContent = other._alignContent
            _flexGrow = other._flexGrow
            _flexShrink = other._flexShrink
            _flexBasis = other._flexBasis
            _order = other._order
            _gap = other._gap
            _rowGap = other._rowGap
            _columnGap = other._columnGap
            _overflow = other._overflow
            _overflowX = other._overflowX
            _overflowY = other._overflowY
            _cursor = other._cursor
            _pointerEvents = other._pointerEvents
            _userSelect = other._userSelect
            _objectFit = other._objectFit
            _aspectRatio = other._aspectRatio
            _zIndex = other._zIndex
            _transform = other._transform
            _transformOrigin = other._transformOrigin
            _transitions = other._transitions
            _filter = other._filter
            _backdropFilter = other._backdropFilter
        } finally {
            suppressInternalNotifications = prevSuppress
            lastMarkedProperty = null
        }
        if (!suppressInternalNotifications) {
            onChange?.invoke()
        }
    }

    fun mergeFrom(other: ComponentStyle, respectImportant: Boolean = true) {
        if (other.isEmpty()) return
        val prevSuppress = suppressInternalNotifications
        suppressInternalNotifications = true
        try {
            fun canOverride(prop: CssProperty): Boolean {
                if (!other.hasProperty(prop)) return false
                if (!respectImportant) return true
                return !isImportant(prop) || other.isImportant(prop)
            }
            fun applyImp(prop: CssProperty) {
                setActiveBit(prop, true)
                if (other.isImportant(prop)) setImportantBit(prop, true)
            }

            if (canOverride(CssProperty.BACKGROUND_COLOR)) { _backgroundColor = other._backgroundColor; applyImp(CssProperty.BACKGROUND_COLOR) }
            if (canOverride(CssProperty.BACKGROUND_IMAGE)) { _backgroundImage = other._backgroundImage; applyImp(CssProperty.BACKGROUND_IMAGE) }
            if (canOverride(CssProperty.BACKGROUND_SIZE)) { _backgroundSize = other._backgroundSize; applyImp(CssProperty.BACKGROUND_SIZE) }
            if (canOverride(CssProperty.BACKGROUND_POSITION)) { _backgroundPosition = other._backgroundPosition; applyImp(CssProperty.BACKGROUND_POSITION) }
            if (canOverride(CssProperty.BACKGROUND_REPEAT)) { _backgroundRepeat = other._backgroundRepeat; applyImp(CssProperty.BACKGROUND_REPEAT) }
            if (canOverride(CssProperty.BACKGROUND_ATTACHMENT)) { _backgroundAttachment = other._backgroundAttachment; applyImp(CssProperty.BACKGROUND_ATTACHMENT) }
            if (canOverride(CssProperty.BACKGROUND_CLIP)) { _backgroundClip = other._backgroundClip; applyImp(CssProperty.BACKGROUND_CLIP) }
            if (canOverride(CssProperty.BACKGROUND_ORIGIN)) { _backgroundOrigin = other._backgroundOrigin; applyImp(CssProperty.BACKGROUND_ORIGIN) }
            if (canOverride(CssProperty.COLOR)) { _color = other._color; applyImp(CssProperty.COLOR) }
            if (canOverride(CssProperty.ACCENT_COLOR)) { _accentColor = other._accentColor; applyImp(CssProperty.ACCENT_COLOR) }
            if (canOverride(CssProperty.OPACITY)) { _opacity = other._opacity; applyImp(CssProperty.OPACITY) }
            if (canOverride(CssProperty.DISPLAY)) { _display = other._display; applyImp(CssProperty.DISPLAY) }
            if (canOverride(CssProperty.VISIBILITY)) { _visibility = other._visibility; applyImp(CssProperty.VISIBILITY) }
            if (canOverride(CssProperty.POSITION)) { _position = other._position; applyImp(CssProperty.POSITION) }
            if (canOverride(CssProperty.TOP)) { _top = other._top; applyImp(CssProperty.TOP) }
            if (canOverride(CssProperty.RIGHT)) { _right = other._right; applyImp(CssProperty.RIGHT) }
            if (canOverride(CssProperty.BOTTOM)) { _bottom = other._bottom; applyImp(CssProperty.BOTTOM) }
            if (canOverride(CssProperty.LEFT)) { _left = other._left; applyImp(CssProperty.LEFT) }
            if (canOverride(CssProperty.WIDTH)) { _width = other._width; applyImp(CssProperty.WIDTH) }
            if (canOverride(CssProperty.HEIGHT)) { _height = other._height; applyImp(CssProperty.HEIGHT) }
            if (canOverride(CssProperty.MIN_WIDTH)) { _minWidth = other._minWidth; applyImp(CssProperty.MIN_WIDTH) }
            if (canOverride(CssProperty.MIN_HEIGHT)) { _minHeight = other._minHeight; applyImp(CssProperty.MIN_HEIGHT) }
            if (canOverride(CssProperty.MAX_WIDTH)) { _maxWidth = other._maxWidth; applyImp(CssProperty.MAX_WIDTH) }
            if (canOverride(CssProperty.MAX_HEIGHT)) { _maxHeight = other._maxHeight; applyImp(CssProperty.MAX_HEIGHT) }
            if (canOverride(CssProperty.MARGIN)) { _margin = other._margin; applyImp(CssProperty.MARGIN) }
            if (canOverride(CssProperty.MARGIN_TOP)) { _marginTop = other._marginTop; applyImp(CssProperty.MARGIN_TOP) }
            if (canOverride(CssProperty.MARGIN_RIGHT)) { _marginRight = other._marginRight; applyImp(CssProperty.MARGIN_RIGHT) }
            if (canOverride(CssProperty.MARGIN_BOTTOM)) { _marginBottom = other._marginBottom; applyImp(CssProperty.MARGIN_BOTTOM) }
            if (canOverride(CssProperty.MARGIN_LEFT)) { _marginLeft = other._marginLeft; applyImp(CssProperty.MARGIN_LEFT) }
            if (canOverride(CssProperty.PADDING)) { _padding = other._padding; applyImp(CssProperty.PADDING) }
            if (canOverride(CssProperty.PADDING_TOP)) { _paddingTop = other._paddingTop; applyImp(CssProperty.PADDING_TOP) }
            if (canOverride(CssProperty.PADDING_RIGHT)) { _paddingRight = other._paddingRight; applyImp(CssProperty.PADDING_RIGHT) }
            if (canOverride(CssProperty.PADDING_BOTTOM)) { _paddingBottom = other._paddingBottom; applyImp(CssProperty.PADDING_BOTTOM) }
            if (canOverride(CssProperty.PADDING_LEFT)) { _paddingLeft = other._paddingLeft; applyImp(CssProperty.PADDING_LEFT) }
            if (canOverride(CssProperty.BOX_SIZING)) { _boxSizing = other._boxSizing; applyImp(CssProperty.BOX_SIZING) }
            if (canOverride(CssProperty.FLOAT)) { _floatSide = other._floatSide; applyImp(CssProperty.FLOAT) }
            if (canOverride(CssProperty.CLEAR)) { _clearSide = other._clearSide; applyImp(CssProperty.CLEAR) }
            if (canOverride(CssProperty.BORDER_WIDTH)) { _borderWidth = other._borderWidth; applyImp(CssProperty.BORDER_WIDTH) }
            if (canOverride(CssProperty.BORDER_STYLE)) { _borderStyle = other._borderStyle; applyImp(CssProperty.BORDER_STYLE) }
            if (canOverride(CssProperty.BORDER_COLOR)) {
                if (other._borderColor != null) _borderColor = other._borderColor
                if (other._borderTopColor != null) _borderTopColor = other._borderTopColor
                if (other._borderRightColor != null) _borderRightColor = other._borderRightColor
                if (other._borderBottomColor != null) _borderBottomColor = other._borderBottomColor
                if (other._borderLeftColor != null) _borderLeftColor = other._borderLeftColor
                applyImp(CssProperty.BORDER_COLOR)
            }
            if (canOverride(CssProperty.BORDER_RADIUS)) { _borderRadius = other._borderRadius; applyImp(CssProperty.BORDER_RADIUS) }
            if (canOverride(CssProperty.OUTLINE_WIDTH)) {
                if (other._outlineWidth != null) _outlineWidth = other._outlineWidth
                if (other._focusRingWidthPx != null) _focusRingWidthPx = other._focusRingWidthPx
                applyImp(CssProperty.OUTLINE_WIDTH)
            }
            if (canOverride(CssProperty.OUTLINE_STYLE)) { _outlineStyle = other._outlineStyle; applyImp(CssProperty.OUTLINE_STYLE) }
            if (canOverride(CssProperty.OUTLINE_COLOR)) {
                if (other._outlineColor != null) _outlineColor = other._outlineColor
                if (other._focusRingColor != null) _focusRingColor = other._focusRingColor
                applyImp(CssProperty.OUTLINE_COLOR)
            }
            if (canOverride(CssProperty.OUTLINE)) {
                if (other._outlineOffset != null) _outlineOffset = other._outlineOffset
                applyImp(CssProperty.OUTLINE)
            }
            if (canOverride(CssProperty.BOX_SHADOW)) { _boxShadow = other._boxShadow; applyImp(CssProperty.BOX_SHADOW) }
            if (canOverride(CssProperty.TEXT_SHADOW)) { _textShadow = other._textShadow; applyImp(CssProperty.TEXT_SHADOW) }
            if (canOverride(CssProperty.FONT_FAMILY)) { _fontFamily = other._fontFamily; applyImp(CssProperty.FONT_FAMILY) }
            if (canOverride(CssProperty.FONT_SIZE)) { _fontSize = other._fontSize; applyImp(CssProperty.FONT_SIZE) }
            if (canOverride(CssProperty.FONT_WEIGHT)) {
                _fontWeight = other._fontWeight
                _fontWeightNumericOverride = other._fontWeightNumericOverride
                applyImp(CssProperty.FONT_WEIGHT)
            }
            if (canOverride(CssProperty.FONT_STYLE)) { _fontStyle = other._fontStyle; applyImp(CssProperty.FONT_STYLE) }
            if (canOverride(CssProperty.LINE_HEIGHT)) { _lineHeight = other._lineHeight; applyImp(CssProperty.LINE_HEIGHT) }
            if (canOverride(CssProperty.LETTER_SPACING)) { _letterSpacing = other._letterSpacing; applyImp(CssProperty.LETTER_SPACING) }
            if (canOverride(CssProperty.TEXT_ALIGN)) { _textAlign = other._textAlign; applyImp(CssProperty.TEXT_ALIGN) }
            if (canOverride(CssProperty.TEXT_DECORATION)) { _textDecoration = other._textDecoration; applyImp(CssProperty.TEXT_DECORATION) }
            if (canOverride(CssProperty.TEXT_TRANSFORM)) { _textTransform = other._textTransform; applyImp(CssProperty.TEXT_TRANSFORM) }
            if (canOverride(CssProperty.TEXT_OVERFLOW)) { _textOverflow = other._textOverflow; applyImp(CssProperty.TEXT_OVERFLOW) }
            if (canOverride(CssProperty.WHITE_SPACE)) { _whiteSpace = other._whiteSpace; applyImp(CssProperty.WHITE_SPACE) }
            if (canOverride(CssProperty.WORD_BREAK)) { _wordBreak = other._wordBreak; applyImp(CssProperty.WORD_BREAK) }
            if (canOverride(CssProperty.VERTICAL_ALIGN)) { _verticalAlign = other._verticalAlign; applyImp(CssProperty.VERTICAL_ALIGN) }
            if (canOverride(CssProperty.FLEX_DIRECTION)) { _flexDirection = other._flexDirection; applyImp(CssProperty.FLEX_DIRECTION) }
            if (canOverride(CssProperty.FLEX_WRAP)) { _flexWrap = other._flexWrap; applyImp(CssProperty.FLEX_WRAP) }
            if (canOverride(CssProperty.JUSTIFY_CONTENT)) { _justifyContent = other._justifyContent; applyImp(CssProperty.JUSTIFY_CONTENT) }
            if (canOverride(CssProperty.ALIGN_ITEMS)) { _alignItems = other._alignItems; applyImp(CssProperty.ALIGN_ITEMS) }
            if (canOverride(CssProperty.ALIGN_SELF)) { _alignSelf = other._alignSelf; applyImp(CssProperty.ALIGN_SELF) }
            if (canOverride(CssProperty.ALIGN_CONTENT)) { _alignContent = other._alignContent; applyImp(CssProperty.ALIGN_CONTENT) }
            if (canOverride(CssProperty.FLEX_GROW)) { _flexGrow = other._flexGrow; applyImp(CssProperty.FLEX_GROW) }
            if (canOverride(CssProperty.FLEX_SHRINK)) { _flexShrink = other._flexShrink; applyImp(CssProperty.FLEX_SHRINK) }
            if (canOverride(CssProperty.FLEX_BASIS)) { _flexBasis = other._flexBasis; applyImp(CssProperty.FLEX_BASIS) }
            if (canOverride(CssProperty.ORDER)) { _order = other._order; applyImp(CssProperty.ORDER) }
            if (canOverride(CssProperty.GAP)) { _gap = other._gap; applyImp(CssProperty.GAP) }
            if (canOverride(CssProperty.ROW_GAP)) { _rowGap = other._rowGap; applyImp(CssProperty.ROW_GAP) }
            if (canOverride(CssProperty.COLUMN_GAP)) { _columnGap = other._columnGap; applyImp(CssProperty.COLUMN_GAP) }
            if (canOverride(CssProperty.OVERFLOW)) { _overflow = other._overflow; applyImp(CssProperty.OVERFLOW) }
            if (canOverride(CssProperty.OVERFLOW_X)) { _overflowX = other._overflowX; applyImp(CssProperty.OVERFLOW_X) }
            if (canOverride(CssProperty.OVERFLOW_Y)) { _overflowY = other._overflowY; applyImp(CssProperty.OVERFLOW_Y) }
            if (canOverride(CssProperty.CURSOR)) { _cursor = other._cursor; applyImp(CssProperty.CURSOR) }
            if (canOverride(CssProperty.POINTER_EVENTS)) { _pointerEvents = other._pointerEvents; applyImp(CssProperty.POINTER_EVENTS) }
            if (canOverride(CssProperty.USER_SELECT)) { _userSelect = other._userSelect; applyImp(CssProperty.USER_SELECT) }
            if (canOverride(CssProperty.OBJECT_FIT)) { _objectFit = other._objectFit; applyImp(CssProperty.OBJECT_FIT) }
            if (canOverride(CssProperty.ASPECT_RATIO)) { _aspectRatio = other._aspectRatio; applyImp(CssProperty.ASPECT_RATIO) }
            if (canOverride(CssProperty.Z_INDEX)) { _zIndex = other._zIndex; applyImp(CssProperty.Z_INDEX) }
            if (canOverride(CssProperty.TRANSFORM)) { _transform = other._transform; applyImp(CssProperty.TRANSFORM) }
            if (canOverride(CssProperty.TRANSFORM_ORIGIN)) { _transformOrigin = other._transformOrigin; applyImp(CssProperty.TRANSFORM_ORIGIN) }
            if (canOverride(CssProperty.TRANSITION)) { _transitions = other._transitions; applyImp(CssProperty.TRANSITION) }
            if (canOverride(CssProperty.FILTER)) { _filter = other._filter; applyImp(CssProperty.FILTER) }
            if (canOverride(CssProperty.BACKDROP_FILTER)) { _backdropFilter = other._backdropFilter; applyImp(CssProperty.BACKDROP_FILTER) }
        } finally {
            suppressInternalNotifications = prevSuppress
            lastMarkedProperty = null
        }
        if (!suppressInternalNotifications) {
            onChange?.invoke()
        }
    }

    /**
     * Returns a copy of this [ComponentStyle] filtered for a standard component on a system backend:
     * - If [allowAestheticSkin] is `true`, returns a full copy of `this`.
     * - If [allowAestheticSkin] is `false`, keeps layout/geometry properties (`display`, `position`,
     *   `top`, `right`, `bottom`, `left`, `width`, `height`, `minWidth`, `minHeight`, `maxWidth`,
     *   `maxHeight`, `aspectRatio`, `boxSizing`, `padding*`, `margin*`, `flex*`, `justifyContent`,
     *   `alignItems`, `alignSelf`, `alignContent`, `order`, `gap`, `rowGap`, `columnGap`, `overflow*`,
     *   `zIndex`, `opacity`, `visibility`, `cursor`, `pointerEvents`, `userSelect`, `objectFit`,
     *   `transform2D`, `transformOrigin`, `transitions`) and:
     *   - If [allowTypography] is `true`: also keeps `color`/`textColor`, `fontFamily`, `fontSize`,
     *     `fontWeight`, `fontStyle`, `lineHeight`, `letterSpacing`, `textAlign`, `textDecoration`,
     *     `textTransform`, `textOverflow`, `whiteSpace`, `wordBreak`.
     *   - If [allowTypography] is `false`: strips those typography/color properties as well, and
     *     always strips aesthetic skinning properties.
     */
    fun copyFilteredForSystemComponent(allowTypography: Boolean, allowAestheticSkin: Boolean): ComponentStyle {
        val copy = ComponentStyle()
        if (allowAestheticSkin) {
            copy.copyFrom(this)
            return copy
        }
        val (maskLow, maskHigh) = ComputedStyle.systemFilterBitmasks(allowTypography)
        copy.activeBitmaskLow = activeBitmaskLow and maskLow
        copy.activeBitmaskHigh = activeBitmaskHigh and maskHigh
        copy.importantBitmaskLow = importantBitmaskLow and maskLow
        copy.importantBitmaskHigh = importantBitmaskHigh and maskHigh

        // Layout / geometry properties
        copy._display = _display
        copy._visibility = _visibility
        copy._position = _position
        copy._top = _top
        copy._right = _right
        copy._bottom = _bottom
        copy._left = _left
        copy._width = _width
        copy._height = _height
        copy._minWidth = _minWidth
        copy._minHeight = _minHeight
        copy._maxWidth = _maxWidth
        copy._maxHeight = _maxHeight
        copy._aspectRatio = _aspectRatio
        copy._boxSizing = _boxSizing
        copy._padding = _padding
        copy._paddingTop = _paddingTop
        copy._paddingRight = _paddingRight
        copy._paddingBottom = _paddingBottom
        copy._paddingLeft = _paddingLeft
        copy._margin = _margin
        copy._marginTop = _marginTop
        copy._marginRight = _marginRight
        copy._marginBottom = _marginBottom
        copy._marginLeft = _marginLeft
        copy._floatSide = _floatSide
        copy._clearSide = _clearSide
        copy._verticalAlign = _verticalAlign
        copy._flexDirection = _flexDirection
        copy._flexWrap = _flexWrap
        copy._justifyContent = _justifyContent
        copy._alignItems = _alignItems
        copy._alignSelf = _alignSelf
        copy._alignContent = _alignContent
        copy._flexGrow = _flexGrow
        copy._flexShrink = _flexShrink
        copy._flexBasis = _flexBasis
        copy._order = _order
        copy._gap = _gap
        copy._rowGap = _rowGap
        copy._columnGap = _columnGap
        copy._overflow = _overflow
        copy._overflowX = _overflowX
        copy._overflowY = _overflowY
        copy._zIndex = _zIndex
        copy._opacity = _opacity
        copy._cursor = _cursor
        copy._pointerEvents = _pointerEvents
        copy._userSelect = _userSelect
        copy._objectFit = _objectFit
        copy._transform = _transform
        copy._transformOrigin = _transformOrigin
        copy._transitions = _transitions

        if (allowTypography) {
            copy._color = _color
            copy._fontFamily = _fontFamily
            copy._fontSize = _fontSize
            copy._fontWeight = _fontWeight
            copy._fontWeightNumericOverride = _fontWeightNumericOverride
            copy._fontStyle = _fontStyle
            copy._lineHeight = _lineHeight
            copy._letterSpacing = _letterSpacing
            copy._textAlign = _textAlign
            copy._textDecoration = _textDecoration
            copy._textTransform = _textTransform
            copy._textOverflow = _textOverflow
            copy._whiteSpace = _whiteSpace
            copy._wordBreak = _wordBreak
        }
        return copy
    }

    /**
     * Returns the serialized CSS value for a single [CssProperty] on this [ComponentStyle],
     * or `null` if that property is unset.
     */
    fun getSinglePropertyCssString(prop: CssProperty): String? = when (prop) {
        CssProperty.BACKGROUND -> _backgroundImage?.toCssString() ?: _backgroundColor?.toCssRgbaString()
        CssProperty.BACKGROUND_COLOR -> _backgroundColor?.toCssRgbaString()
        CssProperty.BACKGROUND_IMAGE -> _backgroundImage?.toCssString()
        CssProperty.BACKGROUND_SIZE -> _backgroundSize?.toCssString()
        CssProperty.BACKGROUND_POSITION -> _backgroundPosition?.toCssString()
        CssProperty.BACKGROUND_REPEAT -> _backgroundRepeat?.cssValue
        CssProperty.BACKGROUND_ATTACHMENT -> _backgroundAttachment?.cssValue
        CssProperty.BACKGROUND_CLIP -> _backgroundClip?.cssValue
        CssProperty.BACKGROUND_ORIGIN -> _backgroundOrigin?.cssValue
        CssProperty.COLOR -> _color?.toCssRgbaString()
        CssProperty.ACCENT_COLOR -> _accentColor?.toCssRgbaString()
        CssProperty.OPACITY -> _opacity?.toString()
        CssProperty.DISPLAY -> _display?.cssValue
        CssProperty.VISIBILITY -> _visibility?.cssValue
        CssProperty.POSITION -> _position?.cssValue
        CssProperty.TOP -> _top?.toCssString()
        CssProperty.RIGHT -> _right?.toCssString()
        CssProperty.BOTTOM -> _bottom?.toCssString()
        CssProperty.LEFT -> _left?.toCssString()
        CssProperty.WIDTH -> _width?.toCssString()
        CssProperty.HEIGHT -> _height?.toCssString()
        CssProperty.MIN_WIDTH -> _minWidth?.toCssString()
        CssProperty.MIN_HEIGHT -> _minHeight?.toCssString()
        CssProperty.MAX_WIDTH -> _maxWidth?.toCssString()
        CssProperty.MAX_HEIGHT -> _maxHeight?.toCssString()
        CssProperty.MARGIN -> _margin?.toCssString()
        CssProperty.MARGIN_TOP -> _marginTop?.toCssString()
        CssProperty.MARGIN_RIGHT -> _marginRight?.toCssString()
        CssProperty.MARGIN_BOTTOM -> _marginBottom?.toCssString()
        CssProperty.MARGIN_LEFT -> _marginLeft?.toCssString()
        CssProperty.PADDING -> _padding?.toCssString()
        CssProperty.PADDING_TOP -> _paddingTop?.toCssString()
        CssProperty.PADDING_RIGHT -> _paddingRight?.toCssString()
        CssProperty.PADDING_BOTTOM -> _paddingBottom?.toCssString()
        CssProperty.PADDING_LEFT -> _paddingLeft?.toCssString()
        CssProperty.BOX_SIZING -> _boxSizing?.cssValue
        CssProperty.FLOAT -> _floatSide?.cssValue
        CssProperty.CLEAR -> _clearSide?.cssValue
        CssProperty.BORDER -> {
            val w = _borderWidth?.toCssString()
            val s = _borderStyle?.cssValue
            val c = _borderColor?.toCssRgbaString()
            if (w != null || s != null || c != null) {
                listOfNotNull(w, s, c).joinToString(" ")
            } else {
                null
            }
        }
        CssProperty.BORDER_WIDTH -> _borderWidth?.toCssString()
        CssProperty.BORDER_STYLE -> _borderStyle?.cssValue
        CssProperty.BORDER_COLOR -> _borderColor?.toCssRgbaString()
        CssProperty.BORDER_RADIUS -> _borderRadius?.toCssString()
        CssProperty.OUTLINE -> {
            val w = _outlineWidth?.toCssString()
            val s = _outlineStyle?.cssValue
            val c = (_outlineColor ?: _focusRingColor)?.toCssRgbaString()
            if (w != null || s != null || c != null) {
                listOfNotNull(w, s, c).joinToString(" ")
            } else {
                null
            }
        }
        CssProperty.OUTLINE_WIDTH -> _outlineWidth?.toCssString() ?: _focusRingWidthPx?.let { "${it}px" }
        CssProperty.OUTLINE_STYLE -> _outlineStyle?.cssValue
        CssProperty.OUTLINE_COLOR -> (_outlineColor ?: _focusRingColor)?.toCssRgbaString()
        CssProperty.BOX_SHADOW -> _boxShadow?.toCssString()
        CssProperty.TEXT_SHADOW -> _textShadow?.toCssString()
        CssProperty.FONT_FAMILY -> _fontFamily?.toCssString()
        CssProperty.FONT_SIZE -> _fontSize?.toCssString()
        CssProperty.FONT_WEIGHT -> fontWeightNumeric?.toString()
        CssProperty.FONT_STYLE -> _fontStyle?.cssValue
        CssProperty.LINE_HEIGHT -> _lineHeight?.toCssString()
        CssProperty.LETTER_SPACING -> _letterSpacing?.toCssString()
        CssProperty.TEXT_ALIGN -> when (_textAlign) {
            TextAlignment.START -> "left"
            TextAlignment.END -> "right"
            TextAlignment.CENTER -> "center"
            TextAlignment.JUSTIFY -> "justify"
            null -> null
        }
        CssProperty.TEXT_DECORATION -> _textDecoration?.cssValue
        CssProperty.TEXT_TRANSFORM -> when (_textTransform) {
            TextTransform.NONE -> "none"
            TextTransform.LOWERCASE -> "lowercase"
            TextTransform.UPPERCASE -> "uppercase"
            TextTransform.CAPITALIZE -> "capitalize"
            null -> null
        }
        CssProperty.TEXT_OVERFLOW -> when (_textOverflow) {
            TextOverflow.ELLIPSIS -> "ellipsis"
            TextOverflow.WRAP -> "clip"
            TextOverflow.NOWRAP -> null
            null -> null
        }
        CssProperty.WHITE_SPACE -> _whiteSpace?.cssValue
        CssProperty.WORD_BREAK -> _wordBreak?.cssValue
        CssProperty.VERTICAL_ALIGN -> _verticalAlign?.cssValue
        CssProperty.FLEX_DIRECTION -> when (_flexDirection) {
            FlexDirection.ROW -> "row"
            FlexDirection.ROW_REVERSE -> "row-reverse"
            FlexDirection.COLUMN -> "column"
            FlexDirection.COLUMN_REVERSE -> "column-reverse"
            null -> null
        }
        CssProperty.FLEX_WRAP -> when (_flexWrap) {
            FlexWrap.WRAP -> "wrap"
            FlexWrap.NOWRAP -> "nowrap"
            FlexWrap.WRAP_REVERSE -> "wrap-reverse"
            null -> null
        }
        CssProperty.JUSTIFY_CONTENT -> when (_justifyContent) {
            JustifyContent.START -> "flex-start"
            JustifyContent.END -> "flex-end"
            JustifyContent.CENTER -> "center"
            JustifyContent.BETWEEN -> "space-between"
            JustifyContent.AROUND -> "space-around"
            JustifyContent.EVENLY -> "space-evenly"
            null -> null
        }
        CssProperty.ALIGN_ITEMS -> when (_alignItems) {
            AlignItems.START -> "flex-start"
            AlignItems.END -> "flex-end"
            AlignItems.CENTER -> "center"
            AlignItems.BASELINE -> "baseline"
            AlignItems.STRETCH -> "stretch"
            null -> null
        }
        CssProperty.ALIGN_SELF -> when (_alignSelf) {
            AlignSelf.AUTO -> "auto"
            AlignSelf.START -> "flex-start"
            AlignSelf.END -> "flex-end"
            AlignSelf.CENTER -> "center"
            AlignSelf.BASELINE -> "baseline"
            AlignSelf.STRETCH -> "stretch"
            null -> null
        }
        CssProperty.ALIGN_CONTENT -> _alignContent?.cssValue
        CssProperty.FLEX_GROW -> _flexGrow?.let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString() }
        CssProperty.FLEX_SHRINK -> _flexShrink?.let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString() }
        CssProperty.FLEX_BASIS -> _flexBasis?.toCssString()
        CssProperty.ORDER -> _order?.toString()
        CssProperty.GAP -> _gap?.toCssString()
        CssProperty.ROW_GAP -> _rowGap?.toCssString()
        CssProperty.COLUMN_GAP -> _columnGap?.toCssString()
        CssProperty.OVERFLOW -> _overflow?.name?.lowercase()
        CssProperty.OVERFLOW_X -> _overflowX?.name?.lowercase()
        CssProperty.OVERFLOW_Y -> _overflowY?.name?.lowercase()
        CssProperty.CURSOR -> _cursor?.toCssString()
        CssProperty.POINTER_EVENTS -> _pointerEvents?.cssValue
        CssProperty.USER_SELECT -> _userSelect?.cssValue
        CssProperty.OBJECT_FIT -> _objectFit?.name?.lowercase()?.replace('_', '-')
        CssProperty.ASPECT_RATIO -> _aspectRatio?.toCssString()
        CssProperty.Z_INDEX -> _zIndex?.toString()
        CssProperty.TRANSFORM -> _transform?.toCssString()
        CssProperty.TRANSFORM_ORIGIN -> _transformOrigin?.toCssString()
        CssProperty.TRANSITION -> _transitions?.takeIf { it.isNotEmpty() }?.joinToString(", ") {
            "${it.property} ${it.durationMs}ms ${it.timingFunction.toCssString()} ${it.delayMs}ms"
        }
        CssProperty.FILTER -> _filter?.toCssString()
        CssProperty.BACKDROP_FILTER -> _backdropFilter?.toCssString()
    }

    /**
     * Returns a map of CSS property name -> `(cssValue, isImportant)` for all set properties.
     */
    fun toCssPropertyMap(): Map<String, Pair<String, Boolean>> {
        if (isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Pair<String, Boolean>>()
        fun putProp(prop: CssProperty, value: String?) {
            if (value == null) return
            out[prop.cssName] = value to isImportant(prop)
        }
        for (prop in CssProperty.entries) {
            if (hasProperty(prop)) {
                putProp(prop, getSinglePropertyCssString(prop))
            }
        }
        return out
    }

    /**
     * Incrementally updates a single [CssProperty] on [current] from this [ComponentStyle],
     * falling back to [sheetBase] when the property was cleared (`null`).
     */
    fun applySinglePropertyToComputed(
        property: CssProperty,
        current: ComputedStyle,
        sheetBase: ComputedStyle? = null
    ): ComputedStyle {
        val imp = isImportant(property)
        val present = hasProperty(property)
        val ord = property.ordinal
        val baseHas = sheetBase?.hasProperty(property) == true
        val baseImp = sheetBase?.isImportant(property) == true
        val nextActLow = if (ord < 64) {
            val mask = 1L shl ord
            if (present || baseHas) current.activeBitmaskLow or mask else current.activeBitmaskLow and mask.inv()
        } else current.activeBitmaskLow
        val nextActHigh = if (ord >= 64) {
            val mask = 1L shl (ord - 64)
            if (present || baseHas) current.activeBitmaskHigh or mask else current.activeBitmaskHigh and mask.inv()
        } else current.activeBitmaskHigh
        val nextImpLow = if (ord < 64) {
            val mask = 1L shl ord
            if ((present && imp) || (!present && baseImp)) current.importantBitmaskLow or mask else current.importantBitmaskLow and mask.inv()
        } else current.importantBitmaskLow
        val nextImpHigh = if (ord >= 64) {
            val mask = 1L shl (ord - 64)
            if ((present && imp) || (!present && baseImp)) current.importantBitmaskHigh or mask else current.importantBitmaskHigh and mask.inv()
        } else current.importantBitmaskHigh

        val updated = when (property) {
            CssProperty.BACKGROUND_COLOR -> current.copy(backgroundColor = _backgroundColor ?: sheetBase?.backgroundColor)
            CssProperty.BACKGROUND_IMAGE -> current.copy(
                backgroundGradient = _backgroundImage?.gradient ?: sheetBase?.backgroundGradient,
                backgroundImageUrl = _backgroundImage?.urlOrPath ?: sheetBase?.backgroundImageUrl
            )
            CssProperty.BACKGROUND_SIZE -> current.copy(backgroundSize = _backgroundSize?.toCssString() ?: sheetBase?.backgroundSize)
            CssProperty.BACKGROUND_POSITION -> current.copy(backgroundPosition = _backgroundPosition?.toCssString() ?: sheetBase?.backgroundPosition)
            CssProperty.BACKGROUND_REPEAT -> current.copy(backgroundRepeat = _backgroundRepeat?.cssValue ?: sheetBase?.backgroundRepeat)
            CssProperty.BACKGROUND_ATTACHMENT -> current.copy(backgroundAttachment = _backgroundAttachment?.cssValue ?: sheetBase?.backgroundAttachment)
            CssProperty.BACKGROUND_CLIP -> current.copy(backgroundClip = _backgroundClip ?: sheetBase?.backgroundClip)
            CssProperty.BACKGROUND_ORIGIN -> current.copy(backgroundOrigin = _backgroundOrigin ?: sheetBase?.backgroundOrigin)
            CssProperty.COLOR -> current.copy(textColor = _color ?: sheetBase?.textColor)
            CssProperty.ACCENT_COLOR -> current.copy(accentColor = _accentColor ?: sheetBase?.accentColor)
            CssProperty.OPACITY -> current.copy(opacity = _opacity ?: sheetBase?.opacity ?: 1.0f)
            CssProperty.DISPLAY -> current.copy(display = _display?.cssValue ?: sheetBase?.display)
            CssProperty.VISIBILITY -> current.copy(visibility = _visibility ?: sheetBase?.visibility)
            CssProperty.POSITION -> current.copy(position = _position?.cssValue ?: sheetBase?.position)
            CssProperty.TOP -> current.copy(top = _top?.toCssString() ?: sheetBase?.top)
            CssProperty.RIGHT -> current.copy(right = _right?.toCssString() ?: sheetBase?.right)
            CssProperty.BOTTOM -> current.copy(bottom = _bottom?.toCssString() ?: sheetBase?.bottom)
            CssProperty.LEFT -> current.copy(left = _left?.toCssString() ?: sheetBase?.left)
            CssProperty.WIDTH -> current.copy(width = _width?.toCssString() ?: sheetBase?.width)
            CssProperty.HEIGHT -> current.copy(height = _height?.toCssString() ?: sheetBase?.height)
            CssProperty.MIN_WIDTH -> current.copy(minWidth = _minWidth?.toCssString() ?: sheetBase?.minWidth)
            CssProperty.MIN_HEIGHT -> current.copy(minHeight = _minHeight?.toCssString() ?: sheetBase?.minHeight)
            CssProperty.MAX_WIDTH -> current.copy(maxWidth = _maxWidth?.toCssString() ?: sheetBase?.maxWidth)
            CssProperty.MAX_HEIGHT -> current.copy(maxHeight = _maxHeight?.toCssString() ?: sheetBase?.maxHeight)
            CssProperty.MARGIN -> current.copy(margin = _margin ?: sheetBase?.margin)
            CssProperty.MARGIN_TOP -> current.copy(marginTop = _marginTop ?: sheetBase?.marginTop)
            CssProperty.MARGIN_RIGHT -> current.copy(marginRight = _marginRight ?: sheetBase?.marginRight)
            CssProperty.MARGIN_BOTTOM -> current.copy(marginBottom = _marginBottom ?: sheetBase?.marginBottom)
            CssProperty.MARGIN_LEFT -> current.copy(marginLeft = _marginLeft ?: sheetBase?.marginLeft)
            CssProperty.PADDING -> current.copy(padding = _padding ?: sheetBase?.padding)
            CssProperty.PADDING_TOP -> current.copy(paddingTop = _paddingTop ?: sheetBase?.paddingTop)
            CssProperty.PADDING_RIGHT -> current.copy(paddingRight = _paddingRight ?: sheetBase?.paddingRight)
            CssProperty.PADDING_BOTTOM -> current.copy(paddingBottom = _paddingBottom ?: sheetBase?.paddingBottom)
            CssProperty.PADDING_LEFT -> current.copy(paddingLeft = _paddingLeft ?: sheetBase?.paddingLeft)
            CssProperty.BOX_SIZING -> current.copy(boxSizing = _boxSizing ?: sheetBase?.boxSizing)
            CssProperty.FLOAT -> current.copy(floatSide = _floatSide?.cssValue ?: sheetBase?.floatSide)
            CssProperty.CLEAR -> current.copy(clearSide = _clearSide?.cssValue ?: sheetBase?.clearSide)
            CssProperty.BORDER_WIDTH -> {
                val bw = _borderWidth
                current.copy(
                    borderWidthPx = bw?.top?.resolvePx() ?: sheetBase?.borderWidthPx,
                    borderTopWidthPx = bw?.top?.resolvePx() ?: sheetBase?.borderTopWidthPx,
                    borderRightWidthPx = bw?.right?.resolvePx() ?: sheetBase?.borderRightWidthPx,
                    borderBottomWidthPx = bw?.bottom?.resolvePx() ?: sheetBase?.borderBottomWidthPx,
                    borderLeftWidthPx = bw?.left?.resolvePx() ?: sheetBase?.borderLeftWidthPx
                )
            }
            CssProperty.BORDER_STYLE -> current.copy(borderStyle = _borderStyle?.cssValue ?: sheetBase?.borderStyle)
            CssProperty.BORDER_COLOR -> current.copy(
                borderColor = _borderColor ?: sheetBase?.borderColor,
                borderTopColor = _borderTopColor ?: _borderColor ?: sheetBase?.borderTopColor,
                borderRightColor = _borderRightColor ?: _borderColor ?: sheetBase?.borderRightColor,
                borderBottomColor = _borderBottomColor ?: _borderColor ?: sheetBase?.borderBottomColor,
                borderLeftColor = _borderLeftColor ?: _borderColor ?: sheetBase?.borderLeftColor
            )
            CssProperty.BORDER_RADIUS -> {
                val br = _borderRadius
                val tl = br?.topLeft
                current.copy(
                    borderRadius = br ?: sheetBase?.borderRadius,
                    borderRadiusPx = tl?.takeIf { it.unit != CssUnit.PERCENT }?.resolvePx() ?: sheetBase?.borderRadiusPx,
                    borderRadiusPercent = tl?.takeIf { it.unit == CssUnit.PERCENT }?.value ?: sheetBase?.borderRadiusPercent,
                    borderRadiusTopLeftPx = br?.topLeft?.resolvePx() ?: sheetBase?.borderRadiusTopLeftPx,
                    borderRadiusTopRightPx = br?.topRight?.resolvePx() ?: sheetBase?.borderRadiusTopRightPx,
                    borderRadiusBottomRightPx = br?.bottomRight?.resolvePx() ?: sheetBase?.borderRadiusBottomRightPx,
                    borderRadiusBottomLeftPx = br?.bottomLeft?.resolvePx() ?: sheetBase?.borderRadiusBottomLeftPx
                )
            }
            CssProperty.OUTLINE_WIDTH -> current.copy(
                outlineWidthPx = _outlineWidth?.resolvePx() ?: sheetBase?.outlineWidthPx,
                focusRingWidthPx = _focusRingWidthPx ?: _outlineWidth?.resolvePx() ?: sheetBase?.focusRingWidthPx
            )
            CssProperty.OUTLINE_STYLE -> current.copy(outlineStyle = _outlineStyle?.cssValue ?: sheetBase?.outlineStyle)
            CssProperty.OUTLINE_COLOR -> current.copy(
                outlineColor = _outlineColor ?: sheetBase?.outlineColor,
                focusRingColor = _focusRingColor ?: _outlineColor ?: sheetBase?.focusRingColor
            )
            CssProperty.OUTLINE -> current.copy(
                outlineWidthPx = _outlineWidth?.resolvePx() ?: sheetBase?.outlineWidthPx,
                outlineStyle = _outlineStyle?.cssValue ?: sheetBase?.outlineStyle,
                outlineColor = _outlineColor ?: sheetBase?.outlineColor,
                outlineOffsetPx = _outlineOffset?.resolvePx() ?: sheetBase?.outlineOffsetPx
            )
            CssProperty.BOX_SHADOW -> current.copy(
                boxShadow = _boxShadow?.toCssString() ?: sheetBase?.boxShadow,
                boxShadowList = _boxShadow ?: sheetBase?.boxShadowList
            )
            CssProperty.TEXT_SHADOW -> current.copy(
                textShadow = _textShadow?.toCssString() ?: sheetBase?.textShadow,
                textShadowList = _textShadow ?: sheetBase?.textShadowList
            )
            CssProperty.FONT_FAMILY -> current.copy(fontFamily = _fontFamily?.resolvedPrimaryName() ?: sheetBase?.fontFamily)
            CssProperty.FONT_SIZE -> {
                val fs = _fontSize
                current.copy(
                    fontSizePx = fs?.takeIf { it.unit != CssUnit.PERCENT }?.resolvePx() ?: sheetBase?.fontSizePx,
                    fontSizePercent = fs?.takeIf { it.unit == CssUnit.PERCENT }?.value ?: sheetBase?.fontSizePercent
                )
            }
            CssProperty.FONT_WEIGHT -> current.copy(fontWeightNumeric = fontWeightNumeric ?: sheetBase?.fontWeightNumeric)
            CssProperty.FONT_STYLE -> current.copy(fontStyle = _fontStyle ?: sheetBase?.fontStyle)
            CssProperty.LINE_HEIGHT -> current.copy(lineHeight = _lineHeight ?: sheetBase?.lineHeight)
            CssProperty.LETTER_SPACING -> current.copy(letterSpacing = _letterSpacing ?: sheetBase?.letterSpacing)
            CssProperty.TEXT_ALIGN -> current.copy(textAlign = getSinglePropertyCssString(CssProperty.TEXT_ALIGN) ?: sheetBase?.textAlign)
            CssProperty.TEXT_DECORATION -> current.copy(textDecoration = _textDecoration ?: sheetBase?.textDecoration)
            CssProperty.TEXT_TRANSFORM -> current.copy(textTransform = _textTransform ?: sheetBase?.textTransform)
            CssProperty.TEXT_OVERFLOW -> current.copy(textOverflow = _textOverflow ?: sheetBase?.textOverflow)
            CssProperty.WHITE_SPACE -> current.copy(whiteSpace = _whiteSpace?.cssValue ?: sheetBase?.whiteSpace)
            CssProperty.WORD_BREAK -> current.copy(wordBreak = _wordBreak ?: sheetBase?.wordBreak)
            CssProperty.VERTICAL_ALIGN -> current.copy(verticalAlign = _verticalAlign?.cssValue ?: sheetBase?.verticalAlign)
            CssProperty.FLEX_DIRECTION -> current.copy(flexDirection = _flexDirection ?: sheetBase?.flexDirection)
            CssProperty.FLEX_WRAP -> current.copy(flexWrap = _flexWrap ?: sheetBase?.flexWrap)
            CssProperty.JUSTIFY_CONTENT -> current.copy(justifyContent = _justifyContent ?: sheetBase?.justifyContent)
            CssProperty.ALIGN_ITEMS -> current.copy(alignItems = _alignItems ?: sheetBase?.alignItems)
            CssProperty.ALIGN_SELF -> current.copy(alignSelf = _alignSelf ?: sheetBase?.alignSelf)
            CssProperty.ALIGN_CONTENT -> current.copy(alignContent = _alignContent ?: sheetBase?.alignContent)
            CssProperty.FLEX_GROW -> current.copy(flexGrow = _flexGrow ?: sheetBase?.flexGrow)
            CssProperty.FLEX_SHRINK -> current.copy(flexShrink = _flexShrink ?: sheetBase?.flexShrink)
            CssProperty.FLEX_BASIS -> current.copy(flexBasis = _flexBasis ?: sheetBase?.flexBasis)
            CssProperty.ORDER -> current.copy(order = _order ?: sheetBase?.order)
            CssProperty.GAP -> current.copy(gap = _gap ?: sheetBase?.gap)
            CssProperty.ROW_GAP -> current.copy(rowGap = _rowGap ?: sheetBase?.rowGap)
            CssProperty.COLUMN_GAP -> current.copy(columnGap = _columnGap ?: sheetBase?.columnGap)
            CssProperty.OVERFLOW -> current.copy(overflow = _overflow?.name?.lowercase() ?: sheetBase?.overflow)
            CssProperty.OVERFLOW_X -> current.copy(overflowX = _overflowX ?: sheetBase?.overflowX)
            CssProperty.OVERFLOW_Y -> current.copy(overflowY = _overflowY ?: sheetBase?.overflowY)
            CssProperty.CURSOR -> current.copy(
                cursor = (_cursor as? CssCursor)?.cssValue ?: sheetBase?.cursor,
                customCursor = _cursor ?: sheetBase?.customCursor
            )
            CssProperty.POINTER_EVENTS -> current.copy(
                pointerEventsEnabled = _pointerEvents?.let { it != CssPointerEvents.NONE } ?: sheetBase?.pointerEventsEnabled ?: true
            )
            CssProperty.USER_SELECT -> current.copy(userSelect = _userSelect ?: sheetBase?.userSelect)
            CssProperty.OBJECT_FIT -> current.copy(objectFit = _objectFit ?: sheetBase?.objectFit)
            CssProperty.ASPECT_RATIO -> current.copy(aspectRatio = _aspectRatio?.numericRatio ?: sheetBase?.aspectRatio)
            CssProperty.Z_INDEX -> current.copy(zIndex = _zIndex ?: sheetBase?.zIndex)
            CssProperty.TRANSFORM -> current.copy(
                transform = _transform?.toCssString() ?: sheetBase?.transform,
                transform2D = _transform ?: sheetBase?.transform2D
            )
            CssProperty.TRANSFORM_ORIGIN -> current.copy(transformOrigin = _transformOrigin ?: sheetBase?.transformOrigin)
            CssProperty.TRANSITION -> current.copy(
                transition = getSinglePropertyCssString(CssProperty.TRANSITION) ?: sheetBase?.transition,
                transitions = _transitions ?: sheetBase?.transitions
            )
            CssProperty.FILTER -> current.copy(filter = _filter ?: sheetBase?.filter)
            CssProperty.BACKDROP_FILTER -> current.copy(backdropFilter = _backdropFilter ?: sheetBase?.backdropFilter)
            else -> current
        }

        return updated.copy(
            activeBitmaskLow = nextActLow,
            activeBitmaskHigh = nextActHigh,
            importantBitmaskLow = nextImpLow,
            importantBitmaskHigh = nextImpHigh
        )
    }

    /**
     * Converts this [ComponentStyle] into a [ComputedStyle], optionally merging on top of [base]
     * while respecting `!important` flags on [base].
     */
    fun toComputedStyle(base: ComputedStyle? = null): ComputedStyle {
        val baseStyle = base ?: ComputedStyle.EMPTY
        if (isEmpty()) return baseStyle

        fun canOverride(prop: CssProperty): Boolean {
            if (!hasProperty(prop)) return false
            return !baseStyle.isImportant(prop) || isImportant(prop)
        }

        val radiusTl = _borderRadius?.topLeft
        val radiusPx = if (canOverride(CssProperty.BORDER_RADIUS) && radiusTl != null && radiusTl.unit != CssUnit.PERCENT) {
            radiusTl.resolvePx()
        } else {
            baseStyle.borderRadiusPx
        }
        val radiusPct = if (canOverride(CssProperty.BORDER_RADIUS) && radiusTl != null && radiusTl.unit == CssUnit.PERCENT) {
            radiusTl.value
        } else {
            baseStyle.borderRadiusPercent
        }

        val fontSizePxVal = if (canOverride(CssProperty.FONT_SIZE) && _fontSize != null && _fontSize?.unit != CssUnit.PERCENT) {
            _fontSize?.resolvePx()
        } else {
            baseStyle.fontSizePx
        }
        val fontSizePctVal = if (canOverride(CssProperty.FONT_SIZE) && _fontSize != null && _fontSize?.unit == CssUnit.PERCENT) {
            _fontSize?.value
        } else {
            baseStyle.fontSizePercent
        }

        return ComputedStyle(
            backgroundColor = if (canOverride(CssProperty.BACKGROUND_COLOR)) _backgroundColor ?: baseStyle.backgroundColor else baseStyle.backgroundColor,
            textColor = if (canOverride(CssProperty.COLOR)) _color ?: baseStyle.textColor else baseStyle.textColor,
            borderColor = if (canOverride(CssProperty.BORDER_COLOR)) _borderColor ?: baseStyle.borderColor else baseStyle.borderColor,
            borderTopColor = if (canOverride(CssProperty.BORDER_COLOR)) _borderTopColor ?: _borderColor ?: baseStyle.borderTopColor else baseStyle.borderTopColor,
            borderRightColor = if (canOverride(CssProperty.BORDER_COLOR)) _borderRightColor ?: _borderColor ?: baseStyle.borderRightColor else baseStyle.borderRightColor,
            borderBottomColor = if (canOverride(CssProperty.BORDER_COLOR)) _borderBottomColor ?: _borderColor ?: baseStyle.borderBottomColor else baseStyle.borderBottomColor,
            borderLeftColor = if (canOverride(CssProperty.BORDER_COLOR)) _borderLeftColor ?: _borderColor ?: baseStyle.borderLeftColor else baseStyle.borderLeftColor,
            accentColor = if (canOverride(CssProperty.ACCENT_COLOR)) _accentColor ?: baseStyle.accentColor else baseStyle.accentColor,
            outlineColor = if (canOverride(CssProperty.OUTLINE_COLOR)) _outlineColor ?: baseStyle.outlineColor else baseStyle.outlineColor,
            focusRingColor = if (canOverride(CssProperty.OUTLINE_COLOR)) _focusRingColor ?: _outlineColor ?: baseStyle.focusRingColor else baseStyle.focusRingColor,
            focusRingWidthPx = if (canOverride(CssProperty.OUTLINE_WIDTH)) _focusRingWidthPx ?: _outlineWidth?.resolvePx() ?: baseStyle.focusRingWidthPx else baseStyle.focusRingWidthPx,
            backgroundGradient = if (canOverride(CssProperty.BACKGROUND_IMAGE)) _backgroundImage?.gradient ?: baseStyle.backgroundGradient else baseStyle.backgroundGradient,
            backgroundImageUrl = if (canOverride(CssProperty.BACKGROUND_IMAGE)) _backgroundImage?.urlOrPath ?: baseStyle.backgroundImageUrl else baseStyle.backgroundImageUrl,
            backgroundRepeat = if (canOverride(CssProperty.BACKGROUND_REPEAT)) _backgroundRepeat?.cssValue ?: baseStyle.backgroundRepeat else baseStyle.backgroundRepeat,
            backgroundAttachment = if (canOverride(CssProperty.BACKGROUND_ATTACHMENT)) _backgroundAttachment?.cssValue ?: baseStyle.backgroundAttachment else baseStyle.backgroundAttachment,
            backgroundPosition = if (canOverride(CssProperty.BACKGROUND_POSITION)) _backgroundPosition?.toCssString() ?: baseStyle.backgroundPosition else baseStyle.backgroundPosition,
            backgroundSize = if (canOverride(CssProperty.BACKGROUND_SIZE)) _backgroundSize?.toCssString() ?: baseStyle.backgroundSize else baseStyle.backgroundSize,
            backgroundClip = if (canOverride(CssProperty.BACKGROUND_CLIP)) _backgroundClip ?: baseStyle.backgroundClip else baseStyle.backgroundClip,
            backgroundOrigin = if (canOverride(CssProperty.BACKGROUND_ORIGIN)) _backgroundOrigin ?: baseStyle.backgroundOrigin else baseStyle.backgroundOrigin,
            borderRadiusPx = radiusPx,
            borderRadiusPercent = radiusPct,
            borderRadiusTopLeftPx = if (canOverride(CssProperty.BORDER_RADIUS)) _borderRadius?.topLeft?.resolvePx() ?: baseStyle.borderRadiusTopLeftPx else baseStyle.borderRadiusTopLeftPx,
            borderRadiusTopRightPx = if (canOverride(CssProperty.BORDER_RADIUS)) _borderRadius?.topRight?.resolvePx() ?: baseStyle.borderRadiusTopRightPx else baseStyle.borderRadiusTopRightPx,
            borderRadiusBottomRightPx = if (canOverride(CssProperty.BORDER_RADIUS)) _borderRadius?.bottomRight?.resolvePx() ?: baseStyle.borderRadiusBottomRightPx else baseStyle.borderRadiusBottomRightPx,
            borderRadiusBottomLeftPx = if (canOverride(CssProperty.BORDER_RADIUS)) _borderRadius?.bottomLeft?.resolvePx() ?: baseStyle.borderRadiusBottomLeftPx else baseStyle.borderRadiusBottomLeftPx,
            borderRadius = if (canOverride(CssProperty.BORDER_RADIUS)) _borderRadius ?: baseStyle.borderRadius else baseStyle.borderRadius,
            borderWidthPx = if (canOverride(CssProperty.BORDER_WIDTH)) _borderWidth?.top?.resolvePx() ?: baseStyle.borderWidthPx else baseStyle.borderWidthPx,
            borderTopWidthPx = if (canOverride(CssProperty.BORDER_WIDTH)) _borderWidth?.top?.resolvePx() ?: baseStyle.borderTopWidthPx else baseStyle.borderTopWidthPx,
            borderRightWidthPx = if (canOverride(CssProperty.BORDER_WIDTH)) _borderWidth?.right?.resolvePx() ?: baseStyle.borderRightWidthPx else baseStyle.borderRightWidthPx,
            borderBottomWidthPx = if (canOverride(CssProperty.BORDER_WIDTH)) _borderWidth?.bottom?.resolvePx() ?: baseStyle.borderBottomWidthPx else baseStyle.borderBottomWidthPx,
            borderLeftWidthPx = if (canOverride(CssProperty.BORDER_WIDTH)) _borderWidth?.left?.resolvePx() ?: baseStyle.borderLeftWidthPx else baseStyle.borderLeftWidthPx,
            borderStyle = if (canOverride(CssProperty.BORDER_STYLE)) _borderStyle?.cssValue ?: baseStyle.borderStyle else baseStyle.borderStyle,
            outlineWidthPx = if (canOverride(CssProperty.OUTLINE_WIDTH)) _outlineWidth?.resolvePx() ?: baseStyle.outlineWidthPx else baseStyle.outlineWidthPx,
            outlineStyle = if (canOverride(CssProperty.OUTLINE_STYLE)) _outlineStyle?.cssValue ?: baseStyle.outlineStyle else baseStyle.outlineStyle,
            outlineOffsetPx = if (canOverride(CssProperty.OUTLINE)) _outlineOffset?.resolvePx() ?: baseStyle.outlineOffsetPx else baseStyle.outlineOffsetPx,
            padding = if (canOverride(CssProperty.PADDING)) _padding ?: baseStyle.padding else baseStyle.padding,
            paddingTop = if (canOverride(CssProperty.PADDING_TOP)) _paddingTop ?: baseStyle.paddingTop else baseStyle.paddingTop,
            paddingRight = if (canOverride(CssProperty.PADDING_RIGHT)) _paddingRight ?: baseStyle.paddingRight else baseStyle.paddingRight,
            paddingBottom = if (canOverride(CssProperty.PADDING_BOTTOM)) _paddingBottom ?: baseStyle.paddingBottom else baseStyle.paddingBottom,
            paddingLeft = if (canOverride(CssProperty.PADDING_LEFT)) _paddingLeft ?: baseStyle.paddingLeft else baseStyle.paddingLeft,
            margin = if (canOverride(CssProperty.MARGIN)) _margin ?: baseStyle.margin else baseStyle.margin,
            marginTop = if (canOverride(CssProperty.MARGIN_TOP)) _marginTop ?: baseStyle.marginTop else baseStyle.marginTop,
            marginRight = if (canOverride(CssProperty.MARGIN_RIGHT)) _marginRight ?: baseStyle.marginRight else baseStyle.marginRight,
            marginBottom = if (canOverride(CssProperty.MARGIN_BOTTOM)) _marginBottom ?: baseStyle.marginBottom else baseStyle.marginBottom,
            marginLeft = if (canOverride(CssProperty.MARGIN_LEFT)) _marginLeft ?: baseStyle.marginLeft else baseStyle.marginLeft,
            boxSizing = if (canOverride(CssProperty.BOX_SIZING)) _boxSizing ?: baseStyle.boxSizing else baseStyle.boxSizing,
            fontSizePx = fontSizePxVal,
            fontSizePercent = fontSizePctVal,
            fontFamily = if (canOverride(CssProperty.FONT_FAMILY)) _fontFamily?.resolvedPrimaryName() ?: baseStyle.fontFamily else baseStyle.fontFamily,
            fontWeightNumeric = if (canOverride(CssProperty.FONT_WEIGHT)) fontWeightNumeric ?: baseStyle.fontWeightNumeric else baseStyle.fontWeightNumeric,
            fontStyle = if (canOverride(CssProperty.FONT_STYLE)) _fontStyle ?: baseStyle.fontStyle else baseStyle.fontStyle,
            lineHeight = if (canOverride(CssProperty.LINE_HEIGHT)) _lineHeight ?: baseStyle.lineHeight else baseStyle.lineHeight,
            letterSpacing = if (canOverride(CssProperty.LETTER_SPACING)) _letterSpacing ?: baseStyle.letterSpacing else baseStyle.letterSpacing,
            textDecoration = if (canOverride(CssProperty.TEXT_DECORATION)) _textDecoration ?: baseStyle.textDecoration else baseStyle.textDecoration,
            textTransform = if (canOverride(CssProperty.TEXT_TRANSFORM)) _textTransform ?: baseStyle.textTransform else baseStyle.textTransform,
            textOverflow = if (canOverride(CssProperty.TEXT_OVERFLOW)) _textOverflow ?: baseStyle.textOverflow else baseStyle.textOverflow,
            wordBreak = if (canOverride(CssProperty.WORD_BREAK)) _wordBreak ?: baseStyle.wordBreak else baseStyle.wordBreak,
            opacity = if (canOverride(CssProperty.OPACITY)) _opacity ?: baseStyle.opacity else baseStyle.opacity,
            cursor = if (canOverride(CssProperty.CURSOR)) (_cursor as? CssCursor)?.cssValue ?: baseStyle.cursor else baseStyle.cursor,
            customCursor = if (canOverride(CssProperty.CURSOR)) _cursor ?: baseStyle.customCursor else baseStyle.customCursor,
            filter = if (canOverride(CssProperty.FILTER)) _filter ?: baseStyle.filter else baseStyle.filter,
            backdropFilter = if (canOverride(CssProperty.BACKDROP_FILTER)) _backdropFilter ?: baseStyle.backdropFilter else baseStyle.backdropFilter,
            boxShadow = if (canOverride(CssProperty.BOX_SHADOW)) _boxShadow?.toCssString() ?: baseStyle.boxShadow else baseStyle.boxShadow,
            boxShadowList = if (canOverride(CssProperty.BOX_SHADOW)) _boxShadow ?: baseStyle.boxShadowList else baseStyle.boxShadowList,
            textShadow = if (canOverride(CssProperty.TEXT_SHADOW)) _textShadow?.toCssString() ?: baseStyle.textShadow else baseStyle.textShadow,
            textShadowList = if (canOverride(CssProperty.TEXT_SHADOW)) _textShadow ?: baseStyle.textShadowList else baseStyle.textShadowList,
            transform = if (canOverride(CssProperty.TRANSFORM)) _transform?.toCssString() ?: baseStyle.transform else baseStyle.transform,
            transform2D = if (canOverride(CssProperty.TRANSFORM)) _transform ?: baseStyle.transform2D else baseStyle.transform2D,
            transformOrigin = if (canOverride(CssProperty.TRANSFORM_ORIGIN)) _transformOrigin ?: baseStyle.transformOrigin else baseStyle.transformOrigin,
            transition = if (canOverride(CssProperty.TRANSITION)) getSinglePropertyCssString(CssProperty.TRANSITION) ?: baseStyle.transition else baseStyle.transition,
            transitions = if (canOverride(CssProperty.TRANSITION)) _transitions ?: baseStyle.transitions else baseStyle.transitions,
            zIndex = if (canOverride(CssProperty.Z_INDEX)) _zIndex ?: baseStyle.zIndex else baseStyle.zIndex,
            aspectRatio = if (canOverride(CssProperty.ASPECT_RATIO)) _aspectRatio?.numericRatio ?: baseStyle.aspectRatio else baseStyle.aspectRatio,
            width = if (canOverride(CssProperty.WIDTH)) _width?.toCssString() ?: baseStyle.width else baseStyle.width,
            height = if (canOverride(CssProperty.HEIGHT)) _height?.toCssString() ?: baseStyle.height else baseStyle.height,
            minWidth = if (canOverride(CssProperty.MIN_WIDTH)) _minWidth?.toCssString() ?: baseStyle.minWidth else baseStyle.minWidth,
            minHeight = if (canOverride(CssProperty.MIN_HEIGHT)) _minHeight?.toCssString() ?: baseStyle.minHeight else baseStyle.minHeight,
            maxWidth = if (canOverride(CssProperty.MAX_WIDTH)) _maxWidth?.toCssString() ?: baseStyle.maxWidth else baseStyle.maxWidth,
            maxHeight = if (canOverride(CssProperty.MAX_HEIGHT)) _maxHeight?.toCssString() ?: baseStyle.maxHeight else baseStyle.maxHeight,
            top = if (canOverride(CssProperty.TOP)) _top?.toCssString() ?: baseStyle.top else baseStyle.top,
            left = if (canOverride(CssProperty.LEFT)) _left?.toCssString() ?: baseStyle.left else baseStyle.left,
            right = if (canOverride(CssProperty.RIGHT)) _right?.toCssString() ?: baseStyle.right else baseStyle.right,
            bottom = if (canOverride(CssProperty.BOTTOM)) _bottom?.toCssString() ?: baseStyle.bottom else baseStyle.bottom,
            display = if (canOverride(CssProperty.DISPLAY)) _display?.cssValue ?: baseStyle.display else baseStyle.display,
            visibility = if (canOverride(CssProperty.VISIBILITY)) _visibility ?: baseStyle.visibility else baseStyle.visibility,
            position = if (canOverride(CssProperty.POSITION)) _position?.cssValue ?: baseStyle.position else baseStyle.position,
            overflow = if (canOverride(CssProperty.OVERFLOW)) _overflow?.name?.lowercase() ?: baseStyle.overflow else baseStyle.overflow,
            overflowX = if (canOverride(CssProperty.OVERFLOW_X)) _overflowX ?: baseStyle.overflowX else baseStyle.overflowX,
            overflowY = if (canOverride(CssProperty.OVERFLOW_Y)) _overflowY ?: baseStyle.overflowY else baseStyle.overflowY,
            flexDirection = if (canOverride(CssProperty.FLEX_DIRECTION)) _flexDirection ?: baseStyle.flexDirection else baseStyle.flexDirection,
            flexWrap = if (canOverride(CssProperty.FLEX_WRAP)) _flexWrap ?: baseStyle.flexWrap else baseStyle.flexWrap,
            justifyContent = if (canOverride(CssProperty.JUSTIFY_CONTENT)) _justifyContent ?: baseStyle.justifyContent else baseStyle.justifyContent,
            alignItems = if (canOverride(CssProperty.ALIGN_ITEMS)) _alignItems ?: baseStyle.alignItems else baseStyle.alignItems,
            alignSelf = if (canOverride(CssProperty.ALIGN_SELF)) _alignSelf ?: baseStyle.alignSelf else baseStyle.alignSelf,
            alignContent = if (canOverride(CssProperty.ALIGN_CONTENT)) _alignContent ?: baseStyle.alignContent else baseStyle.alignContent,
            flexGrow = if (canOverride(CssProperty.FLEX_GROW)) _flexGrow ?: baseStyle.flexGrow else baseStyle.flexGrow,
            flexShrink = if (canOverride(CssProperty.FLEX_SHRINK)) _flexShrink ?: baseStyle.flexShrink else baseStyle.flexShrink,
            flexBasis = if (canOverride(CssProperty.FLEX_BASIS)) _flexBasis ?: baseStyle.flexBasis else baseStyle.flexBasis,
            order = if (canOverride(CssProperty.ORDER)) _order ?: baseStyle.order else baseStyle.order,
            gap = if (canOverride(CssProperty.GAP)) _gap ?: baseStyle.gap else baseStyle.gap,
            rowGap = if (canOverride(CssProperty.ROW_GAP)) _rowGap ?: baseStyle.rowGap else baseStyle.rowGap,
            columnGap = if (canOverride(CssProperty.COLUMN_GAP)) _columnGap ?: baseStyle.columnGap else baseStyle.columnGap,
            textAlign = if (canOverride(CssProperty.TEXT_ALIGN)) getSinglePropertyCssString(CssProperty.TEXT_ALIGN) ?: baseStyle.textAlign else baseStyle.textAlign,
            verticalAlign = if (canOverride(CssProperty.VERTICAL_ALIGN)) _verticalAlign?.cssValue ?: baseStyle.verticalAlign else baseStyle.verticalAlign,
            whiteSpace = if (canOverride(CssProperty.WHITE_SPACE)) _whiteSpace?.cssValue ?: baseStyle.whiteSpace else baseStyle.whiteSpace,
            floatSide = if (canOverride(CssProperty.FLOAT)) _floatSide?.cssValue ?: baseStyle.floatSide else baseStyle.floatSide,
            clearSide = if (canOverride(CssProperty.CLEAR)) _clearSide?.cssValue ?: baseStyle.clearSide else baseStyle.clearSide,
            userSelect = if (canOverride(CssProperty.USER_SELECT)) _userSelect ?: baseStyle.userSelect else baseStyle.userSelect,
            objectFit = if (canOverride(CssProperty.OBJECT_FIT)) _objectFit ?: baseStyle.objectFit else baseStyle.objectFit,
            pointerEventsEnabled = if (canOverride(CssProperty.POINTER_EVENTS)) {
                _pointerEvents?.let { it != CssPointerEvents.NONE } ?: baseStyle.pointerEventsEnabled
            } else {
                baseStyle.pointerEventsEnabled
            },
            hoverStyle = baseStyle.hoverStyle,
            focusStyle = baseStyle.focusStyle,
            activeStyle = baseStyle.activeStyle,
            disabledStyle = baseStyle.disabledStyle,
            checkedStyle = baseStyle.checkedStyle,
            activeBitmaskLow = baseStyle.activeBitmaskLow or activeBitmaskLow,
            activeBitmaskHigh = baseStyle.activeBitmaskHigh or activeBitmaskHigh,
            importantBitmaskLow = baseStyle.importantBitmaskLow or importantBitmaskLow,
            importantBitmaskHigh = baseStyle.importantBitmaskHigh or importantBitmaskHigh
        )
    }
}
