package com.nexustools.genericui.style.bootstrap

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.style.mapper.BootstrapClassMapper
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import kotlin.jvm.JvmName

/**
 * Strongly-typed Bootstrap 5 design-system utility object (`component.getBootstrap()`).
 *
 * - All Bootstrap enum properties set here are strictly isolated from user-added custom CSS classes
 *   (`addClass`, `removeClass`, `hasClass`, `getClasses()`, `getClass()`).
 * - Every setter and mutable property accepts `null` to clear that Bootstrap utility.
 */
class ComponentBootstrap(
    internal val owner: AbstractComponent? = null,
    internal var onChange: ((ComponentBootstrap) -> Unit)? = null
) {
    private var _type: BootstrapType? = BootstrapType.NONE
    private var _size: BootstrapSize? = null
    private var _outline: Boolean = false
    private var _width: BootstrapWidth? = null
    private var _height: BootstrapHeight? = null
    private var _display: DisplayMode? = null
    private var _flexDirection: FlexDirection? = null
    private var _flexWrap: FlexWrap? = null
    private var _justifyContent: JustifyContent? = null
    private var _alignItems: AlignItems? = null
    private var _alignSelf: AlignSelf? = null
    private var _flexGrow: BootstrapFlexGrow? = null
    private var _flexShrink: BootstrapFlexShrink? = null
    private var _flexFill: Boolean = false
    private var _gap: SpacingScale? = null
    private var _rowGap: SpacingScale? = null
    private var _columnGap: SpacingScale? = null
    private var _margin: SpacingScale? = null
    private var _marginTop: SpacingScale? = null
    private var _marginBottom: SpacingScale? = null
    private var _marginStart: SpacingScale? = null
    private var _marginEnd: SpacingScale? = null
    private var _marginX: SpacingScale? = null
    private var _marginY: SpacingScale? = null
    private var _padding: SpacingScale? = null
    private var _paddingTop: SpacingScale? = null
    private var _paddingBottom: SpacingScale? = null
    private var _paddingStart: SpacingScale? = null
    private var _paddingEnd: SpacingScale? = null
    private var _paddingX: SpacingScale? = null
    private var _paddingY: SpacingScale? = null
    private var _backgroundType: BootstrapType? = null
    private var _textColorType: BootstrapType? = null
    private var _borderType: BootstrapType? = null
    private var _borderWidth: BootstrapBorderWidth? = null
    private var _borderRadius: BorderRadiusStyle? = null
    private var _shadow: ShadowElevation? = null
    private var _opacity: BootstrapOpacity? = null
    private var _textAlignment: TextAlignment? = null
    private var _fontWeight: FontWeight? = null
    private var _fontSize: BootstrapFontSize? = null
    private var _lineHeight: BootstrapLineHeight? = null
    private var _textTransform: TextTransform? = null
    private var _textOverflow: TextOverflow? = null
    private var _position: PositionMode? = null
    private var _overflow: OverflowBehavior? = null
    private var _floatSide: BootstrapFloat? = null
    private var _userSelect: BootstrapUserSelect? = null
    private var _pointerEvents: BootstrapPointerEvents? = null
    private var _objectFit: BootstrapObjectFit? = null
    private var _aspectRatio: BootstrapAspectRatio? = null
    private var _visibility: BootstrapVisibility? = null

    private fun notifyChanged() {
        onChange?.invoke(this)
    }

    // -------------------------------------------------------------------------
    // Component Variant, Sizing, & Outline
    // -------------------------------------------------------------------------

    fun getType(): BootstrapType? = _type
    fun setType(type: BootstrapType?) {
        _type = type ?: BootstrapType.NONE
        notifyChanged()
    }

    var type: BootstrapType?
        @JvmName("obtainType") get() = _type
        @JvmName("assignType") set(value) = setType(value)

    fun getSize(): BootstrapSize? = _size
    fun setSize(size: BootstrapSize?) {
        _size = size
        notifyChanged()
    }

    var size: BootstrapSize?
        @JvmName("obtainSize") get() = _size
        @JvmName("assignSize") set(value) = setSize(value)

    fun isOutline(): Boolean = _outline
    fun setOutline(outline: Boolean) {
        _outline = outline
        notifyChanged()
    }

    var outline: Boolean
        @JvmName("obtainOutline") get() = _outline
        @JvmName("assignOutline") set(value) = setOutline(value)

    fun getWidth(): BootstrapWidth? = _width
    fun setWidth(width: BootstrapWidth?) {
        _width = width
        notifyChanged()
    }

    var width: BootstrapWidth?
        @JvmName("obtainWidth") get() = _width
        @JvmName("assignWidth") set(value) = setWidth(value)

    fun getHeight(): BootstrapHeight? = _height
    fun setHeight(height: BootstrapHeight?) {
        _height = height
        notifyChanged()
    }

    var height: BootstrapHeight?
        @JvmName("obtainHeight") get() = _height
        @JvmName("assignHeight") set(value) = setHeight(value)

    // -------------------------------------------------------------------------
    // Display & Flexbox
    // -------------------------------------------------------------------------

    fun getDisplay(): DisplayMode? = _display
    fun setDisplay(display: DisplayMode?) {
        _display = display
        notifyChanged()
    }

    var display: DisplayMode?
        @JvmName("obtainDisplay") get() = _display
        @JvmName("assignDisplay") set(value) = setDisplay(value)

    fun getFlexDirection(): FlexDirection? = _flexDirection
    fun setFlexDirection(flexDirection: FlexDirection?) {
        _flexDirection = flexDirection
        notifyChanged()
    }

    var flexDirection: FlexDirection?
        @JvmName("obtainFlexDirection") get() = _flexDirection
        @JvmName("assignFlexDirection") set(value) = setFlexDirection(value)

    fun getFlexWrap(): FlexWrap? = _flexWrap
    fun setFlexWrap(flexWrap: FlexWrap?) {
        _flexWrap = flexWrap
        notifyChanged()
    }

    var flexWrap: FlexWrap?
        @JvmName("obtainFlexWrap") get() = _flexWrap
        @JvmName("assignFlexWrap") set(value) = setFlexWrap(value)

    fun getJustifyContent(): JustifyContent? = _justifyContent
    fun setJustifyContent(justifyContent: JustifyContent?) {
        _justifyContent = justifyContent
        notifyChanged()
    }

    var justifyContent: JustifyContent?
        @JvmName("obtainJustifyContent") get() = _justifyContent
        @JvmName("assignJustifyContent") set(value) = setJustifyContent(value)

    fun getAlignItems(): AlignItems? = _alignItems
    fun setAlignItems(alignItems: AlignItems?) {
        _alignItems = alignItems
        notifyChanged()
    }

    var alignItems: AlignItems?
        @JvmName("obtainAlignItems") get() = _alignItems
        @JvmName("assignAlignItems") set(value) = setAlignItems(value)

    fun getAlignSelf(): AlignSelf? = _alignSelf
    fun setAlignSelf(alignSelf: AlignSelf?) {
        _alignSelf = alignSelf
        notifyChanged()
    }

    var alignSelf: AlignSelf?
        @JvmName("obtainAlignSelf") get() = _alignSelf
        @JvmName("assignAlignSelf") set(value) = setAlignSelf(value)

    fun getFlexGrow(): BootstrapFlexGrow? = _flexGrow
    fun setFlexGrow(flexGrow: BootstrapFlexGrow?) {
        _flexGrow = flexGrow
        notifyChanged()
    }

    var flexGrow: BootstrapFlexGrow?
        @JvmName("obtainFlexGrow") get() = _flexGrow
        @JvmName("assignFlexGrow") set(value) = setFlexGrow(value)

    fun getFlexShrink(): BootstrapFlexShrink? = _flexShrink
    fun setFlexShrink(flexShrink: BootstrapFlexShrink?) {
        _flexShrink = flexShrink
        notifyChanged()
    }

    var flexShrink: BootstrapFlexShrink?
        @JvmName("obtainFlexShrink") get() = _flexShrink
        @JvmName("assignFlexShrink") set(value) = setFlexShrink(value)

    fun isFlexFill(): Boolean = _flexFill
    fun setFlexFill(flexFill: Boolean) {
        _flexFill = flexFill
        notifyChanged()
    }

    var flexFill: Boolean
        @JvmName("obtainFlexFill") get() = _flexFill
        @JvmName("assignFlexFill") set(value) = setFlexFill(value)

    fun getGap(): SpacingScale? = _gap
    fun setGap(gap: SpacingScale?) {
        _gap = gap
        notifyChanged()
    }

    var gap: SpacingScale?
        @JvmName("obtainGap") get() = _gap
        @JvmName("assignGap") set(value) = setGap(value)

    fun getRowGap(): SpacingScale? = _rowGap
    fun setRowGap(rowGap: SpacingScale?) {
        _rowGap = rowGap
        notifyChanged()
    }

    var rowGap: SpacingScale?
        @JvmName("obtainRowGap") get() = _rowGap
        @JvmName("assignRowGap") set(value) = setRowGap(value)

    fun getColumnGap(): SpacingScale? = _columnGap
    fun setColumnGap(columnGap: SpacingScale?) {
        _columnGap = columnGap
        notifyChanged()
    }

    var columnGap: SpacingScale?
        @JvmName("obtainColumnGap") get() = _columnGap
        @JvmName("assignColumnGap") set(value) = setColumnGap(value)

    // -------------------------------------------------------------------------
    // Spacing (Margins & Paddings)
    // -------------------------------------------------------------------------

    fun getMargin(): SpacingScale? = _margin
    fun setMargin(margin: SpacingScale?) {
        _margin = margin
        notifyChanged()
    }

    var margin: SpacingScale?
        @JvmName("obtainMargin") get() = _margin
        @JvmName("assignMargin") set(value) = setMargin(value)

    fun getMarginTop(): SpacingScale? = _marginTop
    fun setMarginTop(marginTop: SpacingScale?) {
        _marginTop = marginTop
        notifyChanged()
    }

    var marginTop: SpacingScale?
        @JvmName("obtainMarginTop") get() = _marginTop
        @JvmName("assignMarginTop") set(value) = setMarginTop(value)

    fun getMarginBottom(): SpacingScale? = _marginBottom
    fun setMarginBottom(marginBottom: SpacingScale?) {
        _marginBottom = marginBottom
        notifyChanged()
    }

    var marginBottom: SpacingScale?
        @JvmName("obtainMarginBottom") get() = _marginBottom
        @JvmName("assignMarginBottom") set(value) = setMarginBottom(value)

    fun getMarginStart(): SpacingScale? = _marginStart
    fun setMarginStart(marginStart: SpacingScale?) {
        _marginStart = marginStart
        notifyChanged()
    }

    var marginStart: SpacingScale?
        @JvmName("obtainMarginStart") get() = _marginStart
        @JvmName("assignMarginStart") set(value) = setMarginStart(value)

    fun getMarginEnd(): SpacingScale? = _marginEnd
    fun setMarginEnd(marginEnd: SpacingScale?) {
        _marginEnd = marginEnd
        notifyChanged()
    }

    var marginEnd: SpacingScale?
        @JvmName("obtainMarginEnd") get() = _marginEnd
        @JvmName("assignMarginEnd") set(value) = setMarginEnd(value)

    fun getMarginX(): SpacingScale? = _marginX
    fun setMarginX(marginX: SpacingScale?) {
        _marginX = marginX
        notifyChanged()
    }

    var marginX: SpacingScale?
        @JvmName("obtainMarginX") get() = _marginX
        @JvmName("assignMarginX") set(value) = setMarginX(value)

    fun getMarginY(): SpacingScale? = _marginY
    fun setMarginY(marginY: SpacingScale?) {
        _marginY = marginY
        notifyChanged()
    }

    var marginY: SpacingScale?
        @JvmName("obtainMarginY") get() = _marginY
        @JvmName("assignMarginY") set(value) = setMarginY(value)

    fun getPadding(): SpacingScale? = _padding
    fun setPadding(padding: SpacingScale?) {
        _padding = padding
        notifyChanged()
    }

    var padding: SpacingScale?
        @JvmName("obtainPadding") get() = _padding
        @JvmName("assignPadding") set(value) = setPadding(value)

    fun getPaddingTop(): SpacingScale? = _paddingTop
    fun setPaddingTop(paddingTop: SpacingScale?) {
        _paddingTop = paddingTop
        notifyChanged()
    }

    var paddingTop: SpacingScale?
        @JvmName("obtainPaddingTop") get() = _paddingTop
        @JvmName("assignPaddingTop") set(value) = setPaddingTop(value)

    fun getPaddingBottom(): SpacingScale? = _paddingBottom
    fun setPaddingBottom(paddingBottom: SpacingScale?) {
        _paddingBottom = paddingBottom
        notifyChanged()
    }

    var paddingBottom: SpacingScale?
        @JvmName("obtainPaddingBottom") get() = _paddingBottom
        @JvmName("assignPaddingBottom") set(value) = setPaddingBottom(value)

    fun getPaddingStart(): SpacingScale? = _paddingStart
    fun setPaddingStart(paddingStart: SpacingScale?) {
        _paddingStart = paddingStart
        notifyChanged()
    }

    var paddingStart: SpacingScale?
        @JvmName("obtainPaddingStart") get() = _paddingStart
        @JvmName("assignPaddingStart") set(value) = setPaddingStart(value)

    fun getPaddingEnd(): SpacingScale? = _paddingEnd
    fun setPaddingEnd(paddingEnd: SpacingScale?) {
        _paddingEnd = paddingEnd
        notifyChanged()
    }

    var paddingEnd: SpacingScale?
        @JvmName("obtainPaddingEnd") get() = _paddingEnd
        @JvmName("assignPaddingEnd") set(value) = setPaddingEnd(value)

    fun getPaddingX(): SpacingScale? = _paddingX
    fun setPaddingX(paddingX: SpacingScale?) {
        _paddingX = paddingX
        notifyChanged()
    }

    var paddingX: SpacingScale?
        @JvmName("obtainPaddingX") get() = _paddingX
        @JvmName("assignPaddingX") set(value) = setPaddingX(value)

    fun getPaddingY(): SpacingScale? = _paddingY
    fun setPaddingY(paddingY: SpacingScale?) {
        _paddingY = paddingY
        notifyChanged()
    }

    var paddingY: SpacingScale?
        @JvmName("obtainPaddingY") get() = _paddingY
        @JvmName("assignPaddingY") set(value) = setPaddingY(value)

    // -------------------------------------------------------------------------
    // Contextual Colors, Borders, Radius, Shadow, & Opacity
    // -------------------------------------------------------------------------

    fun getBackgroundType(): BootstrapType? = _backgroundType
    fun setBackgroundType(backgroundType: BootstrapType?) {
        _backgroundType = backgroundType
        notifyChanged()
    }

    var backgroundType: BootstrapType?
        @JvmName("obtainBackgroundType") get() = _backgroundType
        @JvmName("assignBackgroundType") set(value) = setBackgroundType(value)

    fun getTextColorType(): BootstrapType? = _textColorType
    fun setTextColorType(textColorType: BootstrapType?) {
        _textColorType = textColorType
        notifyChanged()
    }

    var textColorType: BootstrapType?
        @JvmName("obtainTextColorType") get() = _textColorType
        @JvmName("assignTextColorType") set(value) = setTextColorType(value)

    fun getBorderType(): BootstrapType? = _borderType
    fun setBorderType(borderType: BootstrapType?) {
        _borderType = borderType
        notifyChanged()
    }

    var borderType: BootstrapType?
        @JvmName("obtainBorderType") get() = _borderType
        @JvmName("assignBorderType") set(value) = setBorderType(value)

    fun getBorderWidth(): BootstrapBorderWidth? = _borderWidth
    fun setBorderWidth(borderWidth: BootstrapBorderWidth?) {
        _borderWidth = borderWidth
        notifyChanged()
    }

    var borderWidth: BootstrapBorderWidth?
        @JvmName("obtainBorderWidth") get() = _borderWidth
        @JvmName("assignBorderWidth") set(value) = setBorderWidth(value)

    fun getBorderRadius(): BorderRadiusStyle? = _borderRadius
    fun setBorderRadius(borderRadius: BorderRadiusStyle?) {
        _borderRadius = borderRadius
        notifyChanged()
    }

    var borderRadius: BorderRadiusStyle?
        @JvmName("obtainBorderRadius") get() = _borderRadius
        @JvmName("assignBorderRadius") set(value) = setBorderRadius(value)

    fun getShadow(): ShadowElevation? = _shadow
    fun setShadow(shadow: ShadowElevation?) {
        _shadow = shadow
        notifyChanged()
    }

    var shadow: ShadowElevation?
        @JvmName("obtainShadow") get() = _shadow
        @JvmName("assignShadow") set(value) = setShadow(value)

    fun getOpacity(): BootstrapOpacity? = _opacity
    fun setOpacity(opacity: BootstrapOpacity?) {
        _opacity = opacity
        notifyChanged()
    }

    var opacity: BootstrapOpacity?
        @JvmName("obtainOpacity") get() = _opacity
        @JvmName("assignOpacity") set(value) = setOpacity(value)

    // -------------------------------------------------------------------------
    // Typography
    // -------------------------------------------------------------------------

    fun getTextAlignment(): TextAlignment? = _textAlignment
    fun setTextAlignment(textAlignment: TextAlignment?) {
        _textAlignment = textAlignment
        notifyChanged()
    }

    var textAlignment: TextAlignment?
        @JvmName("obtainTextAlignment") get() = _textAlignment
        @JvmName("assignTextAlignment") set(value) = setTextAlignment(value)

    var textAlign: TextAlignment?
        @JvmName("obtainTextAlign") get() = _textAlignment
        @JvmName("assignTextAlign") set(value) = setTextAlignment(value)

    fun getFontWeight(): FontWeight? = _fontWeight
    fun setFontWeight(fontWeight: FontWeight?) {
        _fontWeight = fontWeight
        notifyChanged()
    }

    var fontWeight: FontWeight?
        @JvmName("obtainFontWeight") get() = _fontWeight
        @JvmName("assignFontWeight") set(value) = setFontWeight(value)

    fun getFontSize(): BootstrapFontSize? = _fontSize
    fun setFontSize(fontSize: BootstrapFontSize?) {
        _fontSize = fontSize
        notifyChanged()
    }

    var fontSize: BootstrapFontSize?
        @JvmName("obtainFontSize") get() = _fontSize
        @JvmName("assignFontSize") set(value) = setFontSize(value)

    fun getLineHeight(): BootstrapLineHeight? = _lineHeight
    fun setLineHeight(lineHeight: BootstrapLineHeight?) {
        _lineHeight = lineHeight
        notifyChanged()
    }

    var lineHeight: BootstrapLineHeight?
        @JvmName("obtainLineHeight") get() = _lineHeight
        @JvmName("assignLineHeight") set(value) = setLineHeight(value)

    fun getTextTransform(): TextTransform? = _textTransform
    fun setTextTransform(textTransform: TextTransform?) {
        _textTransform = textTransform
        notifyChanged()
    }

    var textTransform: TextTransform?
        @JvmName("obtainTextTransform") get() = _textTransform
        @JvmName("assignTextTransform") set(value) = setTextTransform(value)

    fun getTextOverflow(): TextOverflow? = _textOverflow
    fun setTextOverflow(textOverflow: TextOverflow?) {
        _textOverflow = textOverflow
        notifyChanged()
    }

    var textOverflow: TextOverflow?
        @JvmName("obtainTextOverflow") get() = _textOverflow
        @JvmName("assignTextOverflow") set(value) = setTextOverflow(value)

    // -------------------------------------------------------------------------
    // Position, Overflow, Float, Interaction, Media Fit, & Visibility
    // -------------------------------------------------------------------------

    fun getPosition(): PositionMode? = _position
    fun setPosition(position: PositionMode?) {
        _position = position
        notifyChanged()
    }

    var position: PositionMode?
        @JvmName("obtainPosition") get() = _position
        @JvmName("assignPosition") set(value) = setPosition(value)

    fun getOverflow(): OverflowBehavior? = _overflow
    fun setOverflow(overflow: OverflowBehavior?) {
        _overflow = overflow
        notifyChanged()
    }

    var overflow: OverflowBehavior?
        @JvmName("obtainOverflow") get() = _overflow
        @JvmName("assignOverflow") set(value) = setOverflow(value)

    fun getFloatSide(): BootstrapFloat? = _floatSide
    fun setFloatSide(floatSide: BootstrapFloat?) {
        _floatSide = floatSide
        notifyChanged()
    }

    var floatSide: BootstrapFloat?
        @JvmName("obtainFloatSide") get() = _floatSide
        @JvmName("assignFloatSide") set(value) = setFloatSide(value)

    var float: BootstrapFloat?
        @JvmName("obtainFloat") get() = _floatSide
        @JvmName("assignFloat") set(value) = setFloatSide(value)

    fun getUserSelect(): BootstrapUserSelect? = _userSelect
    fun setUserSelect(userSelect: BootstrapUserSelect?) {
        _userSelect = userSelect
        notifyChanged()
    }

    var userSelect: BootstrapUserSelect?
        @JvmName("obtainUserSelect") get() = _userSelect
        @JvmName("assignUserSelect") set(value) = setUserSelect(value)

    fun getPointerEvents(): BootstrapPointerEvents? = _pointerEvents
    fun setPointerEvents(pointerEvents: BootstrapPointerEvents?) {
        _pointerEvents = pointerEvents
        notifyChanged()
    }

    var pointerEvents: BootstrapPointerEvents?
        @JvmName("obtainPointerEvents") get() = _pointerEvents
        @JvmName("assignPointerEvents") set(value) = setPointerEvents(value)

    fun getObjectFit(): BootstrapObjectFit? = _objectFit
    fun setObjectFit(objectFit: BootstrapObjectFit?) {
        _objectFit = objectFit
        notifyChanged()
    }

    var objectFit: BootstrapObjectFit?
        @JvmName("obtainObjectFit") get() = _objectFit
        @JvmName("assignObjectFit") set(value) = setObjectFit(value)

    fun getAspectRatio(): BootstrapAspectRatio? = _aspectRatio
    fun setAspectRatio(aspectRatio: BootstrapAspectRatio?) {
        _aspectRatio = aspectRatio
        notifyChanged()
    }

    var aspectRatio: BootstrapAspectRatio?
        @JvmName("obtainAspectRatio") get() = _aspectRatio
        @JvmName("assignAspectRatio") set(value) = setAspectRatio(value)

    fun getVisibility(): BootstrapVisibility? = _visibility
    fun setVisibility(visibility: BootstrapVisibility?) {
        _visibility = visibility
        notifyChanged()
    }

    var visibility: BootstrapVisibility?
        @JvmName("obtainVisibility") get() = _visibility
        @JvmName("assignVisibility") set(value) = setVisibility(value)

    // -------------------------------------------------------------------------
    // Snapshot Synchronization & Enum Class Extraction
    // -------------------------------------------------------------------------

    fun applyToSnapshot(base: BootstrapStyleSnapshot): BootstrapStyleSnapshot = base.copy(
        type = _type,
        outline = _outline,
        size = _size,
        width = _width,
        height = _height,
        backgroundType = _backgroundType,
        textColorType = _textColorType,
        borderType = _borderType,
        borderWidth = _borderWidth,
        textAlignment = _textAlignment,
        fontWeight = _fontWeight,
        fontSize = _fontSize,
        lineHeight = _lineHeight,
        textTransform = _textTransform,
        textOverflow = _textOverflow,
        borderRadius = _borderRadius,
        shadow = _shadow,
        opacity = _opacity,
        display = _display,
        flexDirection = _flexDirection,
        flexWrap = _flexWrap,
        justifyContent = _justifyContent,
        alignItems = _alignItems,
        alignSelf = _alignSelf,
        flexGrow = _flexGrow,
        flexShrink = _flexShrink,
        flexFill = _flexFill,
        gap = _gap,
        rowGap = _rowGap,
        columnGap = _columnGap,
        overflow = _overflow,
        position = _position,
        floatSide = _floatSide,
        userSelect = _userSelect,
        pointerEvents = _pointerEvents,
        objectFit = _objectFit,
        aspectRatio = _aspectRatio,
        visibility = _visibility,
        marginAll = _margin,
        marginTop = _marginTop,
        marginBottom = _marginBottom,
        marginStart = _marginStart,
        marginEnd = _marginEnd,
        marginX = _marginX,
        marginY = _marginY,
        paddingAll = _padding,
        paddingTop = _paddingTop,
        paddingBottom = _paddingBottom,
        paddingStart = _paddingStart,
        paddingEnd = _paddingEnd,
        paddingX = _paddingX,
        paddingY = _paddingY
    )

    internal fun syncFromSnapshot(snapshot: BootstrapStyleSnapshot) {
        val prev = onChange
        onChange = null
        try {
            _type = snapshot.type
            _outline = snapshot.outline
            _size = snapshot.size
            _width = snapshot.width
            _height = snapshot.height
            _backgroundType = snapshot.backgroundType
            _textColorType = snapshot.textColorType
            _borderType = snapshot.borderType
            _borderWidth = snapshot.borderWidth
            _textAlignment = snapshot.textAlignment
            _fontWeight = snapshot.fontWeight
            _fontSize = snapshot.fontSize
            _lineHeight = snapshot.lineHeight
            _textTransform = snapshot.textTransform
            _textOverflow = snapshot.textOverflow
            _borderRadius = snapshot.borderRadius
            _shadow = snapshot.shadow
            _opacity = snapshot.opacity
            _display = snapshot.display
            _flexDirection = snapshot.flexDirection
            _flexWrap = snapshot.flexWrap
            _justifyContent = snapshot.justifyContent
            _alignItems = snapshot.alignItems
            _alignSelf = snapshot.alignSelf
            _flexGrow = snapshot.flexGrow
            _flexShrink = snapshot.flexShrink
            _flexFill = snapshot.flexFill
            _gap = snapshot.gap
            _rowGap = snapshot.rowGap
            _columnGap = snapshot.columnGap
            _overflow = snapshot.overflow
            _position = snapshot.position
            _floatSide = snapshot.floatSide
            _userSelect = snapshot.userSelect
            _pointerEvents = snapshot.pointerEvents
            _objectFit = snapshot.objectFit
            _aspectRatio = snapshot.aspectRatio
            _visibility = snapshot.visibility
            _margin = snapshot.marginAll
            _marginTop = snapshot.marginTop
            _marginBottom = snapshot.marginBottom
            _marginStart = snapshot.marginStart
            _marginEnd = snapshot.marginEnd
            _marginX = snapshot.marginX
            _marginY = snapshot.marginY
            _padding = snapshot.paddingAll
            _paddingTop = snapshot.paddingTop
            _paddingBottom = snapshot.paddingBottom
            _paddingStart = snapshot.paddingStart
            _paddingEnd = snapshot.paddingEnd
            _paddingX = snapshot.paddingX
            _paddingY = snapshot.paddingY
        } finally {
            onChange = prev
        }
    }

    fun toStyleSnapshot(componentPrefix: String = owner?.componentClassPrefix ?: ""): BootstrapStyleSnapshot =
        applyToSnapshot(BootstrapStyleSnapshot(componentPrefix = componentPrefix))

    fun toEnumClassSet(componentPrefix: String = owner?.componentClassPrefix ?: "btn"): Set<String> =
        BootstrapClassMapper.toEnumClassSet(
            applyToSnapshot(BootstrapStyleSnapshot(componentPrefix = componentPrefix))
        )
}
