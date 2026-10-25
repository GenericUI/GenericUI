package com.nexustools.genericui.core

import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.gradient.Gradient
import com.nexustools.genericui.style.mapper.BootstrapClassMapper
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.state.ComponentStyleBlock
import com.nexustools.genericui.style.transition.CssTransitionController

/**
 * Base class for all GenericUI components (`AbstractComponent`).
 *
 * Implements **Deferred Upward `NativeImpl` Resolution**:
 * - [nativeImpl] is `null` when a component is constructed in Kotlin.
 * - Properties, Bootstrap Enums, CSS classes, and listeners are stored in lightweight Kotlin state on the component.
 * - [nativeImpl] is resolved upwards through [parent] (`resolveActiveFactory()`) **only** when the component
 *   is connected to a visible on-screen `Window` / `Application` AND [isVisible] is `true`.
 */
abstract class AbstractComponent(
    val componentClassPrefix: String = ""
) : Disposable {

    open var id: String? = null
    var parent: AbstractComponent? = null
        internal set

    var nativeImpl: NativeImpl<*>? = null
        internal set

    open val activeFactory: NativeFactory?
        get() = resolveActiveFactory()

    // Lightweight pre-attachment Kotlin state
    private var visibleState: Boolean = true
    private var enabledState: Boolean = true
    private var hoveredState: Boolean = false
    private var focusedState: Boolean = false
    private var activePressedState: Boolean = false
    private var opacityState: Float = 1.0f
    private var tooltipTextState: String? = null
    private var preferredSizeState: Size? = null
    var widthPx: Int? = null
        set(value) {
            field = value
            val h = heightPx ?: preferredSizeState?.height ?: 0
            if (value != null) setPreferredSize(Size(value, h))
        }
    var heightPx: Int? = null
        set(value) {
            field = value
            val w = widthPx ?: preferredSizeState?.width ?: 0
            if (value != null) setPreferredSize(Size(w, value))
        }
    var minWidthPx: Int? = null
    var minHeightPx: Int? = null
    var maxWidthPx: Int? = null
    var maxHeightPx: Int? = null

    var padding: EdgeInsets = EdgeInsets.ZERO
        private set
    var margin: EdgeInsets = EdgeInsets.ZERO
        private set
    private var computedStyleState: ComputedStyle? = null
    val transitionController: CssTransitionController = CssTransitionController(this)

    val isTransitioning: Boolean
        get() = transitionController.isTransitioning

    private var styleSnapshot: BootstrapStyleSnapshot = BootstrapStyleSnapshot(componentPrefix = componentClassPrefix)

    /**
     * Marks any ancestor [AbstractContainer] offscreen render caches as dirty because this
     * component's visual content changed (forcing a re-render on the next animation frame).
     */
    fun invalidateRenderCache() {
        (this as? AbstractContainer)?.renderCache?.markDirty()
        (parent as? AbstractContainer)?.markRenderCacheDirty()
    }

    internal fun notifyParentCacheDirty() {
        (parent as? AbstractContainer)?.markRenderCacheDirty()
    }

    /**
     * Steps any active CSS transitions on this component and (if this is an [AbstractContainer])
     * recursively on all child components by [deltaMs] milliseconds.
     */
    fun stepTransitions(deltaMs: Float) {
        transitionController.step(deltaMs)
        if (this is AbstractContainer) {
            for (child in children) {
                child.stepTransitions(deltaMs)
            }
        }
    }

    /**
     * Immediately completes any active CSS transitions on this component and its children.
     */
    fun finishTransitions() {
        transitionController.finishAllTransitions()
        if (this is AbstractContainer) {
            for (child in children) {
                child.finishTransitions()
            }
        }
    }

    // Event listeners
    var onClick: (() -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnClick(value)
        }
    var onDoubleClick: (() -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnDoubleClick(value)
        }
    var onContextMenu: ((PointerEvent) -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnContextMenu(value)
        }
    var onPointerDown: ((PointerEvent) -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnPointerDown(value)
        }
    var onPointerMove: ((PointerEvent) -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnPointerMove(value)
        }
    var onPointerUp: ((PointerEvent) -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnPointerUp(value)
        }
    var onWheel: ((WheelEvent) -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnWheel(value)
        }
    var onKeyDown: ((KeyEvent) -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnKeyDown(value)
        }
    var onKeyUp: ((KeyEvent) -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnKeyUp(value)
        }

    protected inline fun <reified T : NativeImpl<*>> typedNative(): T? = nativeImpl as? T

    open fun resolveActiveFactory(): NativeFactory? {
        if (!visibleState) return null
        return parent?.resolveActiveFactory()
    }

    protected abstract fun createNativeImpl(factory: NativeFactory): NativeImpl<*>

    open fun ensureNativeImpl(): NativeImpl<*>? {
        if (!visibleState) return null
        val existing = nativeImpl
        if (existing != null) return existing

        val factory = resolveActiveFactory() ?: return null
        val created = createNativeImpl(factory)
        nativeImpl = created
        onAttachNative(created)
        syncStateToNative()
        return created
    }

    protected open fun syncStateToNative() {
        // Subclasses override to push component-specific state to typedNative()
    }

    protected open fun onAttachNative(impl: NativeImpl<*>) {
        syncStyleSnapshotToNative(impl)
        impl.setVisible(visibleState)
        impl.setEnabled(enabledState)
        impl.setHoveredState(hoveredState)
        impl.setFocusedState(focusedState)
        impl.setActivePressedState(activePressedState)
        impl.setOpacity(opacityState)
        impl.setTooltipText(tooltipTextState)
        impl.setPreferredSize(preferredSizeState)
        impl.setPadding(padding)
        impl.setMargin(margin)
        computedStyleState?.let {
            impl.applyComputedStyle(it)
            impl.setTint(it.tint)
            impl.setBackgroundGradient(it.backgroundGradient)
        }
        impl.setOnClick(onClick)
        impl.setOnDoubleClick(onDoubleClick)
        impl.setOnContextMenu(onContextMenu)
        impl.setOnPointerDown(onPointerDown)
        impl.setOnPointerMove(onPointerMove)
        impl.setOnPointerUp(onPointerUp)
        impl.setOnWheel(onWheel)
        impl.setOnKeyDown(onKeyDown)
        impl.setOnKeyUp(onKeyUp)
    }

    private fun syncStyleSnapshotToNative(impl: NativeImpl<*>) {
        impl.setType(styleSnapshot.type)
        impl.setOutline(styleSnapshot.outline)
        impl.setSize(styleSnapshot.size)
        impl.setBackgroundType(styleSnapshot.backgroundType)
        impl.setTextColorType(styleSnapshot.textColorType)
        impl.setBorderType(styleSnapshot.borderType)
        impl.setTextAlignment(styleSnapshot.textAlignment)
        impl.setFontWeight(styleSnapshot.fontWeight)
        impl.setTextTransform(styleSnapshot.textTransform)
        impl.setTextOverflow(styleSnapshot.textOverflow)
        impl.setBorderRadius(styleSnapshot.borderRadius)
        impl.setShadow(styleSnapshot.shadow)
        impl.setDisplay(styleSnapshot.display)
        impl.setFlexDirection(styleSnapshot.flexDirection)
        impl.setFlexWrap(styleSnapshot.flexWrap)
        impl.setJustifyContent(styleSnapshot.justifyContent)
        impl.setAlignItems(styleSnapshot.alignItems)
        impl.setAlignSelf(styleSnapshot.alignSelf)
        impl.setOverflow(styleSnapshot.overflow)
        impl.setPosition(styleSnapshot.position)
        impl.setMarginAll(styleSnapshot.marginAll)
        impl.setPaddingAll(styleSnapshot.paddingAll)
        for (custom in styleSnapshot.customClasses) {
            impl.addClass(custom)
        }
    }

    // String-based CSS Class API
    var cssClass: String
        get() = getClass()
        set(value) = setClass(value)

    fun setClass(className: String) {
        styleSnapshot = BootstrapClassMapper.fromClassString(className, componentClassPrefix)
        notifyParentCacheDirty()
        nativeImpl?.setClass(className)
    }

    fun getClass(): String =
        nativeImpl?.getClass() ?: BootstrapClassMapper.toClassString(styleSnapshot)

    fun addClass(className: String) {
        val combined = (getClass() + " " + className).trim()
        styleSnapshot = BootstrapClassMapper.fromClassString(combined, componentClassPrefix)
        notifyParentCacheDirty()
        nativeImpl?.addClass(className)
    }

    fun removeClass(className: String) {
        val tokens = getClass().split(Regex("\\s+")).filter { it.isNotEmpty() && it != className }
        styleSnapshot = BootstrapClassMapper.fromClassString(tokens.joinToString(" "), componentClassPrefix)
        notifyParentCacheDirty()
        nativeImpl?.removeClass(className)
    }

    fun hasClass(className: String): Boolean =
        nativeImpl?.hasClass(className) ?: getClass().split(Regex("\\s+")).contains(className)

    // Bootstrap Enum Helper Properties & Methods
    var type: BootstrapType?
        get() = getType()
        set(value) = setType(value)

    @kotlin.jvm.JvmName("assignType")
    fun setType(type: BootstrapType?) {
        styleSnapshot = styleSnapshot.copy(type = type)
        nativeImpl?.setType(type)
    }

    @kotlin.jvm.JvmName("obtainType")
    fun getType(): BootstrapType? =
        nativeImpl?.getType() ?: styleSnapshot.type

    var outline: Boolean
        get() = isOutline()
        set(value) = setOutline(value)

    @kotlin.jvm.JvmName("assignOutline")
    fun setOutline(outline: Boolean) {
        styleSnapshot = styleSnapshot.copy(outline = outline)
        nativeImpl?.setOutline(outline)
    }

    fun isOutline(): Boolean =
        nativeImpl?.isOutline() ?: styleSnapshot.outline

    var size: BootstrapSize?
        get() = getSize()
        set(value) = setSize(value)

    @kotlin.jvm.JvmName("assignSize")
    fun setSize(size: BootstrapSize?) {
        styleSnapshot = styleSnapshot.copy(size = size)
        nativeImpl?.setSize(size)
    }

    @kotlin.jvm.JvmName("obtainSize")
    fun getSize(): BootstrapSize? =
        nativeImpl?.getSize() ?: styleSnapshot.size

    var backgroundType: BootstrapType?
        get() = getBackgroundType()
        set(value) = setBackgroundType(value)

    @kotlin.jvm.JvmName("assignBackgroundType")
    fun setBackgroundType(type: BootstrapType?) {
        styleSnapshot = styleSnapshot.copy(backgroundType = type)
        nativeImpl?.setBackgroundType(type)
    }

    @kotlin.jvm.JvmName("obtainBackgroundType")
    fun getBackgroundType(): BootstrapType? =
        nativeImpl?.getBackgroundType() ?: styleSnapshot.backgroundType

    var textColorType: BootstrapType?
        get() = getTextColorType()
        set(value) = setTextColorType(value)

    @kotlin.jvm.JvmName("assignTextColorType")
    fun setTextColorType(type: BootstrapType?) {
        styleSnapshot = styleSnapshot.copy(textColorType = type)
        nativeImpl?.setTextColorType(type)
    }

    @kotlin.jvm.JvmName("obtainTextColorType")
    fun getTextColorType(): BootstrapType? =
        nativeImpl?.getTextColorType() ?: styleSnapshot.textColorType

    var borderType: BootstrapType?
        get() = getBorderType()
        set(value) = setBorderType(value)

    @kotlin.jvm.JvmName("assignBorderType")
    fun setBorderType(type: BootstrapType?) {
        styleSnapshot = styleSnapshot.copy(borderType = type)
        nativeImpl?.setBorderType(type)
    }

    @kotlin.jvm.JvmName("obtainBorderType")
    fun getBorderType(): BootstrapType? =
        nativeImpl?.getBorderType() ?: styleSnapshot.borderType

    var textAlignment: TextAlignment?
        get() = getTextAlignment()
        set(value) = setTextAlignment(value)

    @kotlin.jvm.JvmName("assignTextAlignment")
    fun setTextAlignment(alignment: TextAlignment?) {
        styleSnapshot = styleSnapshot.copy(textAlignment = alignment)
        nativeImpl?.setTextAlignment(alignment)
    }

    @kotlin.jvm.JvmName("obtainTextAlignment")
    fun getTextAlignment(): TextAlignment? =
        nativeImpl?.getTextAlignment() ?: styleSnapshot.textAlignment

    var fontWeight: FontWeight?
        get() = getFontWeight()
        set(value) = setFontWeight(value)

    @kotlin.jvm.JvmName("assignFontWeight")
    fun setFontWeight(weight: FontWeight?) {
        styleSnapshot = styleSnapshot.copy(fontWeight = weight)
        nativeImpl?.setFontWeight(weight)
    }

    @kotlin.jvm.JvmName("obtainFontWeight")
    fun getFontWeight(): FontWeight? =
        nativeImpl?.getFontWeight() ?: styleSnapshot.fontWeight

    var textTransform: TextTransform?
        get() = getTextTransform()
        set(value) = setTextTransform(value)

    @kotlin.jvm.JvmName("assignTextTransform")
    fun setTextTransform(transform: TextTransform?) {
        styleSnapshot = styleSnapshot.copy(textTransform = transform)
        nativeImpl?.setTextTransform(transform)
    }

    @kotlin.jvm.JvmName("obtainTextTransform")
    fun getTextTransform(): TextTransform? =
        nativeImpl?.getTextTransform() ?: styleSnapshot.textTransform

    var textOverflow: TextOverflow?
        get() = getTextOverflow()
        set(value) = setTextOverflow(value)

    @kotlin.jvm.JvmName("assignTextOverflow")
    fun setTextOverflow(overflow: TextOverflow?) {
        styleSnapshot = styleSnapshot.copy(textOverflow = overflow)
        nativeImpl?.setTextOverflow(overflow)
    }

    @kotlin.jvm.JvmName("obtainTextOverflow")
    fun getTextOverflow(): TextOverflow? =
        nativeImpl?.getTextOverflow() ?: styleSnapshot.textOverflow

    var borderRadiusStyle: BorderRadiusStyle?
        get() = getBorderRadius()
        set(value) = setBorderRadius(value)

    fun setBorderRadius(radius: BorderRadiusStyle?) {
        styleSnapshot = styleSnapshot.copy(borderRadius = radius)
        nativeImpl?.setBorderRadius(radius)
    }

    fun getBorderRadius(): BorderRadiusStyle? =
        nativeImpl?.getBorderRadius() ?: styleSnapshot.borderRadius

    var shadowElevation: ShadowElevation?
        get() = getShadow()
        set(value) = setShadow(value)

    fun setShadow(shadow: ShadowElevation?) {
        styleSnapshot = styleSnapshot.copy(shadow = shadow)
        nativeImpl?.setShadow(shadow)
    }

    fun getShadow(): ShadowElevation? =
        nativeImpl?.getShadow() ?: styleSnapshot.shadow

    var displayMode: DisplayMode?
        get() = getDisplay()
        set(value) = setDisplay(value)

    fun setDisplay(display: DisplayMode?) {
        styleSnapshot = styleSnapshot.copy(display = display)
        nativeImpl?.setDisplay(display)
    }

    fun getDisplay(): DisplayMode? =
        nativeImpl?.getDisplay() ?: styleSnapshot.display

    var flexDirection: FlexDirection?
        get() = getFlexDirection()
        set(value) = setFlexDirection(value)

    @kotlin.jvm.JvmName("assignFlexDirection")
    fun setFlexDirection(direction: FlexDirection?) {
        styleSnapshot = styleSnapshot.copy(flexDirection = direction)
        nativeImpl?.setFlexDirection(direction)
    }

    @kotlin.jvm.JvmName("obtainFlexDirection")
    fun getFlexDirection(): FlexDirection? =
        nativeImpl?.getFlexDirection() ?: styleSnapshot.flexDirection

    var flexWrap: FlexWrap?
        get() = getFlexWrap()
        set(value) = setFlexWrap(value)

    @kotlin.jvm.JvmName("assignFlexWrap")
    fun setFlexWrap(wrap: FlexWrap?) {
        styleSnapshot = styleSnapshot.copy(flexWrap = wrap)
        nativeImpl?.setFlexWrap(wrap)
    }

    @kotlin.jvm.JvmName("obtainFlexWrap")
    fun getFlexWrap(): FlexWrap? =
        nativeImpl?.getFlexWrap() ?: styleSnapshot.flexWrap

    var justifyContent: JustifyContent?
        get() = getJustifyContent()
        set(value) = setJustifyContent(value)

    @kotlin.jvm.JvmName("assignJustifyContent")
    fun setJustifyContent(justify: JustifyContent?) {
        styleSnapshot = styleSnapshot.copy(justifyContent = justify)
        nativeImpl?.setJustifyContent(justify)
    }

    @kotlin.jvm.JvmName("obtainJustifyContent")
    fun getJustifyContent(): JustifyContent? =
        nativeImpl?.getJustifyContent() ?: styleSnapshot.justifyContent

    var alignItems: AlignItems?
        get() = getAlignItems()
        set(value) = setAlignItems(value)

    @kotlin.jvm.JvmName("assignAlignItems")
    fun setAlignItems(align: AlignItems?) {
        styleSnapshot = styleSnapshot.copy(alignItems = align)
        nativeImpl?.setAlignItems(align)
    }

    @kotlin.jvm.JvmName("obtainAlignItems")
    fun getAlignItems(): AlignItems? =
        nativeImpl?.getAlignItems() ?: styleSnapshot.alignItems

    var alignSelf: AlignSelf?
        get() = getAlignSelf()
        set(value) = setAlignSelf(value)

    @kotlin.jvm.JvmName("assignAlignSelf")
    fun setAlignSelf(align: AlignSelf?) {
        styleSnapshot = styleSnapshot.copy(alignSelf = align)
        nativeImpl?.setAlignSelf(align)
    }

    @kotlin.jvm.JvmName("obtainAlignSelf")
    fun getAlignSelf(): AlignSelf? =
        nativeImpl?.getAlignSelf() ?: styleSnapshot.alignSelf

    var overflowBehavior: OverflowBehavior?
        get() = getOverflow()
        set(value) = setOverflow(value)

    fun setOverflow(overflow: OverflowBehavior?) {
        styleSnapshot = styleSnapshot.copy(overflow = overflow)
        nativeImpl?.setOverflow(overflow)
    }

    fun getOverflow(): OverflowBehavior? =
        nativeImpl?.getOverflow() ?: styleSnapshot.overflow

    var positionMode: PositionMode?
        get() = getPosition()
        set(value) = setPosition(value)

    fun setPosition(position: PositionMode?) {
        styleSnapshot = styleSnapshot.copy(position = position)
        nativeImpl?.setPosition(position)
    }

    fun getPosition(): PositionMode? =
        nativeImpl?.getPosition() ?: styleSnapshot.position

    var marginAll: SpacingScale?
        get() = getMarginAll()
        set(value) = setMarginAll(value)

    @kotlin.jvm.JvmName("assignMarginAll")
    fun setMarginAll(scale: SpacingScale?) {
        styleSnapshot = styleSnapshot.copy(marginAll = scale)
        nativeImpl?.setMarginAll(scale)
    }

    @kotlin.jvm.JvmName("obtainMarginAll")
    fun getMarginAll(): SpacingScale? =
        nativeImpl?.getMarginAll() ?: styleSnapshot.marginAll

    var paddingAll: SpacingScale?
        get() = getPaddingAll()
        set(value) = setPaddingAll(value)

    @kotlin.jvm.JvmName("assignPaddingAll")
    fun setPaddingAll(scale: SpacingScale?) {
        styleSnapshot = styleSnapshot.copy(paddingAll = scale)
        nativeImpl?.setPaddingAll(scale)
    }

    @kotlin.jvm.JvmName("obtainPaddingAll")
    fun getPaddingAll(): SpacingScale? =
        nativeImpl?.getPaddingAll() ?: styleSnapshot.paddingAll

    // Visibility, State & Geometry
    open var isVisible: Boolean
        get() = visibleState
        set(value) {
            visibleState = value
            if (value) {
                val wasUnrealized = (nativeImpl == null)
                val impl = ensureNativeImpl()
                if (impl != null) {
                    impl.setVisible(true)
                    if (wasUnrealized) {
                        val parentContainer = parent as? AbstractContainer
                        val parentImpl = parentContainer?.nativeImpl as? AbstractContainerNativeImpl<*>
                        if (parentContainer != null && parentImpl != null) {
                            val idx = parentContainer.children.indexOf(this)
                            parentImpl.addChildNative(impl, idx)
                        }
                    }
                }
            } else {
                nativeImpl?.setVisible(false)
            }
        }

    var isEnabled: Boolean
        get() = enabledState
        set(value) {
            enabledState = value
            nativeImpl?.setEnabled(value)
        }

    var isHovered: Boolean
        get() = nativeImpl?.isHoveredState() ?: hoveredState
        set(value) {
            hoveredState = value
            nativeImpl?.setHoveredState(value)
            notifyParentCacheDirty()
        }

    var isFocused: Boolean
        get() = nativeImpl?.isFocusedState() ?: focusedState
        set(value) {
            focusedState = value
            nativeImpl?.setFocusedState(value)
            notifyParentCacheDirty()
        }

    var isActivePressed: Boolean
        get() = nativeImpl?.isActivePressedState() ?: activePressedState
        set(value) {
            activePressedState = value
            nativeImpl?.setActivePressedState(value)
            notifyParentCacheDirty()
        }

    var opacity: Float
        get() = opacityState
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            val prevStyle = computedStyleState ?: ComputedStyle(opacity = opacityState)
            opacityState = clamped
            nativeImpl?.setOpacity(opacityState)
            val nextStyle = prevStyle.copy(opacity = clamped)
            computedStyleState = nextStyle
            transitionController.onStyleChanged(prevStyle, nextStyle)
        }

    var tooltipText: String?
        get() = tooltipTextState
        set(value) {
            tooltipTextState = value
            nativeImpl?.setTooltipText(value)
        }

    fun setPreferredSize(size: Size?) {
        preferredSizeState = size
        nativeImpl?.setPreferredSize(size)
    }

    fun getPreferredSize(): Size? = preferredSizeState

    fun getBounds(): Rect = nativeImpl?.getBounds() ?: Rect(0, 0, preferredSizeState?.width ?: 0, preferredSizeState?.height ?: 0)

    @kotlin.jvm.JvmName("assignPadding")
    fun setPadding(padding: EdgeInsets) {
        this.padding = padding
        nativeImpl?.setPadding(padding)
    }

    @kotlin.jvm.JvmName("assignMargin")
    fun setMargin(margin: EdgeInsets) {
        this.margin = margin
        nativeImpl?.setMargin(margin)
    }

    fun applyComputedStyle(style: ComputedStyle) {
        val previous = computedStyleState
        computedStyleState = style
        opacityState = style.opacity.coerceIn(0f, 1f)
        nativeImpl?.setOpacity(opacityState)
        if (style.transition != null) {
            transitionController.updateTransitionCss(style.transition)
        }
        transitionController.onStyleChanged(previous, style)
        nativeImpl?.applyComputedStyle(style)
    }

    val computedStyle: ComputedStyle?
        get() = computedStyleState

    private val inlineStyleMap = linkedMapOf<String, String>()

    var inlineStyle: String
        get() = inlineStyleMap.entries.joinToString("; ") { "${it.key}: ${it.value}" }
        set(value) {
            inlineStyleMap.clear()
            if (value.isNotBlank()) {
                for (decl in value.split(';')) {
                    val colon = decl.indexOf(':')
                    if (colon > 0) {
                        val k = decl.substring(0, colon).trim().lowercase()
                        val v = decl.substring(colon + 1).trim()
                        if (k.isNotEmpty() && v.isNotEmpty()) {
                            inlineStyleMap[k] = v
                        }
                    }
                }
            }
            rebuildComputedStyleFromInlineMap()
        }

    fun setStyleProperty(property: String, value: String) {
        val key = property.trim().lowercase()
        val trimmedValue = value.trim()
        if (trimmedValue.isEmpty()) {
            inlineStyleMap.remove(key)
        } else {
            inlineStyleMap[key] = trimmedValue
        }
        rebuildComputedStyleFromInlineMap()
    }

    fun getStyleProperty(property: String): String? =
        inlineStyleMap[property.trim().lowercase()]

    fun clearStyleProperties() {
        inlineStyleMap.clear()
        rebuildComputedStyleFromInlineMap()
    }

    private fun rebuildComputedStyleFromInlineMap() {
        val parsed = CssStyleSheet.parseInlineStyle(inlineStyle)
        val merged = (computedStyleState ?: ComputedStyle()).mergeWith(parsed)
        applyComputedStyle(merged)
    }

    var customBackgroundColor: RgbaColor?
        get() = computedStyleState?.backgroundColor
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(backgroundColor = value))
        }

    /**
     * Component background gradient ([com.nexustools.genericui.style.gradient.LinearGradient] or
     * [com.nexustools.genericui.style.gradient.RadialGradient]) configured via `style { ... }` or CSS.
     */
    var backgroundGradient: Gradient?
        get() = nativeImpl?.getBackgroundGradient() ?: computedStyleState?.backgroundGradient
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(backgroundGradient = value))
            nativeImpl?.setBackgroundGradient(value)
        }

    @kotlin.jvm.JvmName("assignBackgroundGradient")
    fun setBackgroundGradient(gradient: Gradient?) {
        this.backgroundGradient = gradient
    }

    @kotlin.jvm.JvmName("obtainBackgroundGradient")
    fun getBackgroundGradient(): Gradient? = this.backgroundGradient

    /**
     * Native system component color tint (`tint: ColorRgba(...)`), which tints the native OS widget's
     * bevel/surface/chrome while still using the system native component when possible.
     */
    var tint: RgbaColor?
        get() = nativeImpl?.getTint() ?: computedStyleState?.tint
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(tint = value))
            nativeImpl?.setTint(value)
        }

    @kotlin.jvm.JvmName("assignTint")
    fun setTint(color: RgbaColor?) {
        this.tint = color
    }

    @kotlin.jvm.JvmName("obtainTint")
    fun getTint(): RgbaColor? = this.tint

    /**
     * Configures this component's [ComputedStyle] and CSS pseudo-class states (`hover { ... }`,
     * `focus { ... }`, `active { ... }`, `disabled { ... }`, `checked { ... }`) using the declarative style DSL.
     */
    fun style(block: ComponentStyleBlock.() -> Unit): AbstractComponent {
        val builder = ComponentStyleBlock()
        builder.block()
        val updated = builder.toComputedStyle(computedStyleState)
        applyComputedStyle(updated)
        nativeImpl?.setTint(updated.tint)
        nativeImpl?.setBackgroundGradient(updated.backgroundGradient)
        return this
    }

    /**
     * Resolves the effective [ComputedStyle] for this component's current interactive state
     * ([isHovered], [isFocused], [isActivePressed], [isEnabled]).
     */
    fun resolveEffectiveStyle(checked: Boolean = false): ComputedStyle =
        (computedStyleState ?: ComputedStyle()).resolveForState(
            hovered = isHovered,
            focused = isFocused,
            active = isActivePressed,
            enabled = isEnabled,
            checked = checked
        )

    var textColor: RgbaColor?
        get() = computedStyleState?.textColor
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(textColor = value))
        }

    var borderColor: RgbaColor?
        get() = computedStyleState?.borderColor
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(borderColor = value))
        }

    var borderRadiusPx: Float?
        get() = computedStyleState?.borderRadiusPx
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(borderRadiusPx = value))
        }

    var backgroundImageUrl: String?
        get() = computedStyleState?.backgroundImageUrl
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(backgroundImageUrl = value))
        }

    var textShadow: String?
        get() = computedStyleState?.textShadow
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(textShadow = value))
        }

    var boxShadow: String?
        get() = computedStyleState?.boxShadow
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(boxShadow = value))
        }

    var transform: String?
        get() = computedStyleState?.transform
        set(value) {
            if (value == null) inlineStyleMap.remove("transform") else inlineStyleMap["transform"] = value
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(transform = value))
        }

    var transition: String?
        get() = computedStyleState?.transition
        set(value) {
            if (value == null) inlineStyleMap.remove("transition") else inlineStyleMap["transition"] = value
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(transition = value))
        }

    var zIndex: Int?
        get() = computedStyleState?.zIndex
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(zIndex = value))
        }

    var aspectRatio: Float?
        get() = computedStyleState?.aspectRatio
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(aspectRatio = value))
        }

    var pointerEventsEnabled: Boolean
        get() = computedStyleState?.pointerEventsEnabled ?: true
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(pointerEventsEnabled = value))
        }

    var cursor: String?
        get() = computedStyleState?.cursor
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(cursor = value))
        }

    var leftOffset: String?
        get() = computedStyleState?.left
        set(value) {
            if (value == null) inlineStyleMap.remove("left") else inlineStyleMap["left"] = value
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(left = value))
        }

    var topOffset: String?
        get() = computedStyleState?.top
        set(value) {
            if (value == null) inlineStyleMap.remove("top") else inlineStyleMap["top"] = value
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(base.copy(top = value))
        }

    /**
     * Resolves and applies [styleSheet] rules to this component and (if this is an [AbstractContainer])
     * recursively to all child components.
     */
    fun applyStyleSheet(styleSheet: CssStyleSheet, viewportWidthPx: Int = 1280) {
        val resolved = styleSheet.resolveStyleForComponent(this, viewportWidthPx = viewportWidthPx)
        applyComputedStyle(resolved)
        if (this is AbstractContainer) {
            for (child in children) {
                child.applyStyleSheet(styleSheet, viewportWidthPx)
            }
        }
    }

    /**
     * Applies a build-time precompiled Kotlin stylesheet ([PrecompiledStyleSheet]) to this component
     * and all of its descendants with zero runtime SASS/CSS parsing overhead.
     */
    fun applyPrecompiledStyleSheet(precompiled: PrecompiledStyleSheet, viewportWidthPx: Int = 1280) {
        applyStyleSheet(precompiled.styleSheet, viewportWidthPx)
    }

    // Event Listeners
    @kotlin.jvm.JvmName("assignOnClick")
    fun setOnClick(listener: (() -> Unit)?) { onClick = listener }
    @kotlin.jvm.JvmName("assignOnDoubleClick")
    fun setOnDoubleClick(listener: (() -> Unit)?) { onDoubleClick = listener }
    @kotlin.jvm.JvmName("assignOnContextMenu")
    fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) { onContextMenu = listener }
    @kotlin.jvm.JvmName("assignOnPointerDown")
    fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) { onPointerDown = listener }
    @kotlin.jvm.JvmName("assignOnPointerMove")
    fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) { onPointerMove = listener }
    @kotlin.jvm.JvmName("assignOnPointerUp")
    fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) { onPointerUp = listener }
    @kotlin.jvm.JvmName("assignOnWheel")
    fun setOnWheel(listener: ((WheelEvent) -> Unit)?) { onWheel = listener }
    @kotlin.jvm.JvmName("assignOnKeyDown")
    fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) { onKeyDown = listener }
    @kotlin.jvm.JvmName("assignOnKeyUp")
    fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) { onKeyUp = listener }

    override fun dispose() {
        nativeImpl?.dispose()
        nativeImpl = null
    }
}
