package com.nexustools.genericui.core

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerDragManager
import com.nexustools.genericui.event.PointerDragSession
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCursorValue
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.mapper.BootstrapClassMapper
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
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
    val componentClassPrefix: String = "",
    htmlTag: String = "genericui:component"
) : Disposable {

    private val initialHtmlTag: String = htmlTag.trim().ifEmpty { "genericui:component" }
    private var lastAppliedStyleSheet: CssStyleSheet? = null
    private var lastStyleSheetViewportWidthPx: Int = 1280

    protected open fun defaultHtmlTag(): String = initialHtmlTag

    open var htmlTag: String = initialHtmlTag
        set(value) {
            val normalized = value.trim().ifEmpty { defaultHtmlTag() }
            if (field == normalized) return
            field = normalized
            nativeImpl?.setAttribute("data-genericui-tag", normalized)
            val sheet = lastAppliedStyleSheet
            if (sheet != null) {
                applyStyleSheet(sheet, lastStyleSheetViewportWidthPx)
            }
        }
    open var id: String? = null
    var parent: AbstractComponent? = null
        internal set

    /**
     * `true` when this component is currently mounted inside a [com.nexustools.genericui.window.TitleBar]'s
     * leading, center, or trailing slots on a host that supports custom titlebar components
     * (`TitleCapabilities.canEmbedComponentsInTitleBar`), ensuring it is rendered only once and skipped
     * by standard body/container layout walkers.
     */
    var mountedInTitleBar: Boolean = false

    @PublishedApi
    internal var nativeImpl: NativeImpl<*>? = null

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
    private val customAttributesState: MutableMap<String, String> = linkedMapOf()
    private var preferredSizeState: Size? = null
    var widthPx: Int? = null
        set(value) {
            field = value
            val h = heightPx
            when {
                value != null -> setPreferredSize(Size(value, h ?: preferredSizeState?.height ?: 0))
                h != null -> setPreferredSize(Size(0, h))
                else -> setPreferredSize(null)
            }
        }
    var heightPx: Int? = null
        set(value) {
            field = value
            val w = widthPx
            when {
                value != null -> setPreferredSize(Size(w ?: preferredSizeState?.width ?: 0, value))
                w != null -> setPreferredSize(Size(w, 0))
                else -> setPreferredSize(null)
            }
        }
    var minWidthPx: Int? = null
        set(value) {
            field = value
            if (value != null) setStyleProperty("min-width", "${value}px") else removeStyleProperty("min-width")
        }
    var minHeightPx: Int? = null
        set(value) {
            field = value
            if (value != null) setStyleProperty("min-height", "${value}px") else removeStyleProperty("min-height")
        }
    var maxWidthPx: Int? = null
        set(value) {
            field = value
            if (value != null) setStyleProperty("max-width", "${value}px") else removeStyleProperty("max-width")
        }
    var maxHeightPx: Int? = null
        set(value) {
            field = value
            if (value != null) setStyleProperty("max-height", "${value}px") else removeStyleProperty("max-height")
        }

    var padding: EdgeInsets = EdgeInsets.ZERO
        private set
    var margin: EdgeInsets = EdgeInsets.ZERO
        private set
    private var computedStyleState: ComputedStyle? = null
    val transitionController: CssTransitionController = CssTransitionController(this)

    val isTransitioning: Boolean
        get() = transitionController.isTransitioning

    private var styleSnapshot: BootstrapStyleSnapshot = BootstrapStyleSnapshot(componentPrefix = componentClassPrefix)

    private val componentStyleInstance: ComponentStyle = ComponentStyle(
        onChange = { onComponentStyleChanged() }
    )

    private val componentBootstrapInstance: ComponentBootstrap = ComponentBootstrap(
        owner = this,
        onChange = { onComponentBootstrapChanged(it) }
    )

    private fun onComponentStyleChanged() {
        val activeFactory = resolveActiveFactory() ?: GenericUI.activeFactory
        val engineEnabled = activeFactory?.isStyleSheetEngineEnabled ?: true
        val base = if (engineEnabled) {
            lastAppliedStyleSheet?.resolveStyleForComponent(this, viewportWidthPx = lastStyleSheetViewportWidthPx)
                ?: computedStyleState
        } else {
            computedStyleState
        }
        val updated = componentStyleInstance.toComputedStyle(base)
        applyComputedStyle(updated)
        nativeImpl?.applyComponentStyle(componentStyleInstance)
        nativeImpl?.setBackgroundGradient(updated.backgroundGradient)
        nativeImpl?.setCustomCursor(updated.customCursor)
    }

    private fun onComponentBootstrapChanged(bootstrap: ComponentBootstrap) {
        styleSnapshot = bootstrap.applyToSnapshot(styleSnapshot)
        notifyParentCacheDirty()
        val impl = nativeImpl
        if (impl != null) {
            syncBootstrapEnumsToNative(impl)
            impl.applyComponentBootstrap(bootstrap)
        }
    }

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
            syncContextMenuListener()
        }

    /**
     * Optional popup [ContextMenu] attached to this component.
     * Automatically opens at the pointer coordinates when a context-menu gesture occurs.
     */
    var contextMenu: ContextMenu? = null
        set(value) {
            field = value
            syncContextMenuListener()
        }

    @kotlin.jvm.JvmName("assignContextMenu")
    fun setContextMenu(menu: ContextMenu?) {
        contextMenu = menu
    }

    @kotlin.jvm.JvmName("obtainContextMenu")
    fun getContextMenu(): ContextMenu? = contextMenu

    fun onContextMenu(handler: (PointerEvent) -> Unit) {
        onContextMenu = handler
    }

    fun dispatchContextMenu(event: PointerEvent = PointerEvent(0f, 0f)) {
        contextMenu?.showAt(this, event.x.toInt(), event.y.toInt())
        onContextMenu?.invoke(event)
    }

    fun dispatchContextMenu(x: Float, y: Float) {
        dispatchContextMenu(PointerEvent(x = x, y = y))
    }

    private fun buildEffectiveContextMenuListener(): ((PointerEvent) -> Unit)? {
        val menu = contextMenu
        val userHandler = onContextMenu
        if (menu == null && userHandler == null) return null
        return { event ->
            contextMenu?.showAt(this, event.x.toInt(), event.y.toInt())
            onContextMenu?.invoke(event)
        }
    }

    private fun syncContextMenuListener() {
        nativeImpl?.setOnContextMenu(buildEffectiveContextMenuListener())
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
    var onPointerEnter: ((PointerEvent) -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnPointerEnter(value)
        }
    var onPointerLeave: ((PointerEvent) -> Unit)? = null
        set(value) {
            field = value
            nativeImpl?.setOnPointerLeave(value)
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

    private val activeComponentTimerIds = linkedSetOf<Int>()

    /**
     * Dispatches a pointer-enter event on this component, updating [isHovered] to `true`
     * and invoking [onPointerEnter].
     */
    fun dispatchPointerEnter(event: PointerEvent = PointerEvent()) {
        if (!isEnabled) return
        isHovered = true
        onPointerEnter?.invoke(event)
    }

    /**
     * Dispatches a pointer-leave event on this component, updating [isHovered] to `false`
     * and invoking [onPointerLeave].
     */
    fun dispatchPointerLeave(event: PointerEvent = PointerEvent()) {
        isHovered = false
        if (!isEnabled) return
        onPointerLeave?.invoke(event)
    }

    /**
     * Schedules a lifecycle-scoped one-shot UI timer that executes [callback] after [delayMs] milliseconds.
     * Automatically cancelled if this component is disposed before the timer fires.
     */
    open fun setTimeout(delayMs: Int, callback: () -> Unit): Int {
        var assignedId = -1
        val id = UiTimerScheduler.setTimeout(delayMs) {
            if (assignedId != -1) {
                activeComponentTimerIds.remove(assignedId)
            }
            if (!isDisposed) {
                callback()
            }
        }
        assignedId = id
        if (id > 0) {
            activeComponentTimerIds.add(id)
        }
        return id
    }

    /**
     * Schedules a lifecycle-scoped repeating UI timer that executes [callback] every [intervalMs] milliseconds.
     * Automatically cancelled when this component is disposed.
     */
    open fun setInterval(intervalMs: Int, callback: () -> Unit): Int {
        var assignedId = -1
        val id = UiTimerScheduler.setInterval(intervalMs) {
            if (!isDisposed) {
                callback()
            } else if (assignedId != -1) {
                clearInterval(assignedId)
            }
        }
        assignedId = id
        if (id > 0) {
            activeComponentTimerIds.add(id)
        }
        return id
    }

    /**
     * Cancels a lifecycle-scoped one-shot timer created via [setTimeout].
     */
    open fun clearTimeout(timerId: Int) {
        activeComponentTimerIds.remove(timerId)
        UiTimerScheduler.clearTimer(timerId)
    }

    /**
     * Cancels a lifecycle-scoped repeating timer created via [setInterval].
     */
    open fun clearInterval(timerId: Int) {
        activeComponentTimerIds.remove(timerId)
        UiTimerScheduler.clearTimer(timerId)
    }

    /**
     * Cancels all active lifecycle-scoped timers created on this component via [setTimeout] or [setInterval].
     */
    open fun clearAllTimers() {
        val snapshot = activeComponentTimerIds.toList()
        activeComponentTimerIds.clear()
        for (timerId in snapshot) {
            UiTimerScheduler.clearTimer(timerId)
        }
    }

    protected inline fun <reified T : NativeImpl<*>> typedNative(): T? = nativeImpl as? T

    open fun resolveActiveFactory(): NativeFactory? {
        if (!visibleState) return null
        return parent?.resolveChildFactory()
    }

    /**
     * Returns the [NativeFactory] to provide to direct and descendant children of this component.
     * Defaults to [resolveActiveFactory], allowing specialized containers to override the factory
     * provided to their children.
     */
    open fun resolveChildFactory(): NativeFactory? = resolveActiveFactory()

    protected abstract fun createNativeImpl(factory: NativeFactory): NativeImpl<*>

    internal open fun ensureNativeImpl(): NativeImpl<*>? {
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
        impl.setAttribute("data-genericui-tag", htmlTag)
        for ((attrKey, attrVal) in customAttributesState) {
            impl.setAttribute(attrKey, attrVal)
        }
        impl.setPreferredSize(preferredSizeState)
        minWidthPx?.let {
            inlineStyleMap["min-width"] = "${it}px"
            componentStyleInstance.setRawDeclaration("min-width", "${it}px")
        }
        minHeightPx?.let {
            inlineStyleMap["min-height"] = "${it}px"
            componentStyleInstance.setRawDeclaration("min-height", "${it}px")
        }
        maxWidthPx?.let {
            inlineStyleMap["max-width"] = "${it}px"
            componentStyleInstance.setRawDeclaration("max-width", "${it}px")
        }
        maxHeightPx?.let {
            inlineStyleMap["max-height"] = "${it}px"
            componentStyleInstance.setRawDeclaration("max-height", "${it}px")
        }
        impl.setPadding(padding)
        impl.setMargin(margin)
        computedStyleState?.let {
            impl.applyComputedStyle(it)
            impl.setBackgroundGradient(it.backgroundGradient)
            impl.setCustomCursor(it.customCursor)
        }
        if (!componentStyleInstance.isEmpty()) {
            impl.applyComponentStyle(componentStyleInstance)
        }
        impl.applyComponentBootstrap(componentBootstrapInstance)
        impl.setOnClick(onClick)
        impl.setOnDoubleClick(onDoubleClick)
        impl.setOnContextMenu(buildEffectiveContextMenuListener())
        impl.setOnPointerDown(onPointerDown)
        impl.setOnPointerMove(onPointerMove)
        impl.setOnPointerUp(onPointerUp)
        impl.setOnPointerEnter(onPointerEnter)
        impl.setOnPointerLeave(onPointerLeave)
        impl.setOnWheel(onWheel)
        impl.setOnKeyDown(onKeyDown)
        impl.setOnKeyUp(onKeyUp)
    }

    private fun syncBootstrapEnumsToNative(impl: NativeImpl<*>) {
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
    }

    private fun syncStyleSnapshotToNative(impl: NativeImpl<*>) {
        syncBootstrapEnumsToNative(impl)
        for (custom in styleSnapshot.customClasses) {
            impl.addClass(custom)
        }
    }

    // String-based CSS Class API (strictly isolated to user-added custom classes)
    var cssClass: String
        get() = getClass()
        set(value) = setClass(value)

    @get:kotlin.jvm.JvmName("obtainCustomClasses")
    val classes: Set<String>
        get() = getClasses()

    fun getClasses(): Set<String> = styleSnapshot.customClasses.toSet()

    fun setClass(className: String) {
        val customSet = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        styleSnapshot = styleSnapshot.copy(customClasses = customSet)
        notifyParentCacheDirty()
        nativeImpl?.setClass(customSet.joinToString(" "))
    }

    fun getClass(): String = styleSnapshot.customClasses.joinToString(" ")

    fun addClass(className: String) {
        val tokens = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        val updated = LinkedHashSet(styleSnapshot.customClasses)
        updated.addAll(tokens)
        styleSnapshot = styleSnapshot.copy(customClasses = updated)
        notifyParentCacheDirty()
        for (tok in tokens) {
            nativeImpl?.addClass(tok)
        }
    }

    fun addClass(vararg classNames: String) {
        for (cls in classNames) {
            addClass(cls)
        }
    }

    fun removeClass(className: String) {
        val tokens = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        val updated = LinkedHashSet(styleSnapshot.customClasses)
        updated.removeAll(tokens.toSet())
        styleSnapshot = styleSnapshot.copy(customClasses = updated)
        notifyParentCacheDirty()
        for (tok in tokens) {
            nativeImpl?.removeClass(tok)
        }
    }

    fun removeClass(vararg classNames: String) {
        for (cls in classNames) {
            removeClass(cls)
        }
    }

    fun hasClass(className: String): Boolean {
        val tokens = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        return styleSnapshot.customClasses.containsAll(tokens)
    }

    // ComponentBootstrap API & Convenience Delegation
    fun getBootstrap(): ComponentBootstrap = componentBootstrapInstance

    @get:kotlin.jvm.JvmName("obtainComponentBootstrap")
    val bootstrap: ComponentBootstrap
        get() = componentBootstrapInstance

    // Bootstrap Enum Helper Properties & Methods
    var type: BootstrapType?
        get() = getType()
        set(value) = setType(value)

    @kotlin.jvm.JvmName("assignType")
    fun setType(type: BootstrapType?) {
        componentBootstrapInstance.setType(type)
    }

    @kotlin.jvm.JvmName("obtainType")
    fun getType(): BootstrapType? = componentBootstrapInstance.getType()

    var outline: Boolean
        get() = isOutline()
        set(value) = setOutline(value)

    @kotlin.jvm.JvmName("assignOutline")
    fun setOutline(outline: Boolean) {
        componentBootstrapInstance.setOutline(outline)
    }

    fun isOutline(): Boolean = componentBootstrapInstance.isOutline()

    var size: BootstrapSize?
        get() = getSize()
        set(value) = setSize(value)

    @kotlin.jvm.JvmName("assignSize")
    fun setSize(size: BootstrapSize?) {
        componentBootstrapInstance.setSize(size)
    }

    @kotlin.jvm.JvmName("obtainSize")
    fun getSize(): BootstrapSize? = componentBootstrapInstance.getSize()

    var backgroundType: BootstrapType?
        get() = getBackgroundType()
        set(value) = setBackgroundType(value)

    @kotlin.jvm.JvmName("assignBackgroundType")
    fun setBackgroundType(type: BootstrapType?) {
        componentBootstrapInstance.setBackgroundType(type)
    }

    @kotlin.jvm.JvmName("obtainBackgroundType")
    fun getBackgroundType(): BootstrapType? = componentBootstrapInstance.getBackgroundType()

    var textColorType: BootstrapType?
        get() = getTextColorType()
        set(value) = setTextColorType(value)

    @kotlin.jvm.JvmName("assignTextColorType")
    fun setTextColorType(type: BootstrapType?) {
        componentBootstrapInstance.setTextColorType(type)
    }

    @kotlin.jvm.JvmName("obtainTextColorType")
    fun getTextColorType(): BootstrapType? = componentBootstrapInstance.getTextColorType()

    var borderType: BootstrapType?
        get() = getBorderType()
        set(value) = setBorderType(value)

    @kotlin.jvm.JvmName("assignBorderType")
    fun setBorderType(type: BootstrapType?) {
        componentBootstrapInstance.setBorderType(type)
    }

    @kotlin.jvm.JvmName("obtainBorderType")
    fun getBorderType(): BootstrapType? = componentBootstrapInstance.getBorderType()

    var textAlignment: TextAlignment?
        get() = getTextAlignment()
        set(value) = setTextAlignment(value)

    @kotlin.jvm.JvmName("assignTextAlignment")
    fun setTextAlignment(alignment: TextAlignment?) {
        componentBootstrapInstance.setTextAlignment(alignment)
    }

    @kotlin.jvm.JvmName("obtainTextAlignment")
    fun getTextAlignment(): TextAlignment? = componentBootstrapInstance.getTextAlignment()

    var fontWeight: FontWeight?
        get() = getFontWeight()
        set(value) = setFontWeight(value)

    @kotlin.jvm.JvmName("assignFontWeight")
    fun setFontWeight(weight: FontWeight?) {
        componentBootstrapInstance.setFontWeight(weight)
    }

    @kotlin.jvm.JvmName("obtainFontWeight")
    fun getFontWeight(): FontWeight? = componentBootstrapInstance.getFontWeight()

    var textTransform: TextTransform?
        get() = getTextTransform()
        set(value) = setTextTransform(value)

    @kotlin.jvm.JvmName("assignTextTransform")
    fun setTextTransform(transform: TextTransform?) {
        componentBootstrapInstance.setTextTransform(transform)
    }

    @kotlin.jvm.JvmName("obtainTextTransform")
    fun getTextTransform(): TextTransform? = componentBootstrapInstance.getTextTransform()

    var textOverflow: TextOverflow?
        get() = getTextOverflow()
        set(value) = setTextOverflow(value)

    @kotlin.jvm.JvmName("assignTextOverflow")
    fun setTextOverflow(overflow: TextOverflow?) {
        componentBootstrapInstance.setTextOverflow(overflow)
    }

    @kotlin.jvm.JvmName("obtainTextOverflow")
    fun getTextOverflow(): TextOverflow? = componentBootstrapInstance.getTextOverflow()

    var borderRadiusStyle: BorderRadiusStyle?
        get() = getBorderRadius()
        set(value) = setBorderRadius(value)

    fun setBorderRadius(radius: BorderRadiusStyle?) {
        componentBootstrapInstance.setBorderRadius(radius)
    }

    fun getBorderRadius(): BorderRadiusStyle? = componentBootstrapInstance.getBorderRadius()

    var shadowElevation: ShadowElevation?
        get() = getShadow()
        set(value) = setShadow(value)

    fun setShadow(shadow: ShadowElevation?) {
        componentBootstrapInstance.setShadow(shadow)
    }

    fun getShadow(): ShadowElevation? = componentBootstrapInstance.getShadow()

    var displayMode: DisplayMode?
        get() = getDisplay()
        set(value) = setDisplay(value)

    fun setDisplay(display: DisplayMode?) {
        componentBootstrapInstance.setDisplay(display)
    }

    fun getDisplay(): DisplayMode? = componentBootstrapInstance.getDisplay()

    var flexDirection: FlexDirection?
        get() = getFlexDirection()
        set(value) = setFlexDirection(value)

    @kotlin.jvm.JvmName("assignFlexDirection")
    fun setFlexDirection(direction: FlexDirection?) {
        componentBootstrapInstance.setFlexDirection(direction)
    }

    @kotlin.jvm.JvmName("obtainFlexDirection")
    fun getFlexDirection(): FlexDirection? = componentBootstrapInstance.getFlexDirection()

    var flexWrap: FlexWrap?
        get() = getFlexWrap()
        set(value) = setFlexWrap(value)

    @kotlin.jvm.JvmName("assignFlexWrap")
    fun setFlexWrap(wrap: FlexWrap?) {
        componentBootstrapInstance.setFlexWrap(wrap)
    }

    @kotlin.jvm.JvmName("obtainFlexWrap")
    fun getFlexWrap(): FlexWrap? = componentBootstrapInstance.getFlexWrap()

    var justifyContent: JustifyContent?
        get() = getJustifyContent()
        set(value) = setJustifyContent(value)

    @kotlin.jvm.JvmName("assignJustifyContent")
    fun setJustifyContent(justify: JustifyContent?) {
        componentBootstrapInstance.setJustifyContent(justify)
    }

    @kotlin.jvm.JvmName("obtainJustifyContent")
    fun getJustifyContent(): JustifyContent? = componentBootstrapInstance.getJustifyContent()

    var alignItems: AlignItems?
        get() = getAlignItems()
        set(value) = setAlignItems(value)

    @kotlin.jvm.JvmName("assignAlignItems")
    fun setAlignItems(align: AlignItems?) {
        componentBootstrapInstance.setAlignItems(align)
    }

    @kotlin.jvm.JvmName("obtainAlignItems")
    fun getAlignItems(): AlignItems? = componentBootstrapInstance.getAlignItems()

    var alignSelf: AlignSelf?
        get() = getAlignSelf()
        set(value) = setAlignSelf(value)

    @kotlin.jvm.JvmName("assignAlignSelf")
    fun setAlignSelf(align: AlignSelf?) {
        componentBootstrapInstance.setAlignSelf(align)
    }

    @kotlin.jvm.JvmName("obtainAlignSelf")
    fun getAlignSelf(): AlignSelf? = componentBootstrapInstance.getAlignSelf()

    var overflowBehavior: OverflowBehavior?
        get() = getOverflow()
        set(value) = setOverflow(value)

    fun setOverflow(overflow: OverflowBehavior?) {
        componentBootstrapInstance.setOverflow(overflow)
    }

    fun getOverflow(): OverflowBehavior? = componentBootstrapInstance.getOverflow()

    var positionMode: PositionMode?
        get() = getPosition()
        set(value) = setPosition(value)

    fun setPosition(position: PositionMode?) {
        componentBootstrapInstance.setPosition(position)
    }

    fun getPosition(): PositionMode? = componentBootstrapInstance.getPosition()

    var marginAll: SpacingScale?
        get() = getMarginAll()
        set(value) = setMarginAll(value)

    @kotlin.jvm.JvmName("assignMarginAll")
    fun setMarginAll(scale: SpacingScale?) {
        componentBootstrapInstance.setMargin(scale)
    }

    @kotlin.jvm.JvmName("obtainMarginAll")
    fun getMarginAll(): SpacingScale? = componentBootstrapInstance.getMargin()

    var paddingAll: SpacingScale?
        get() = getPaddingAll()
        set(value) = setPaddingAll(value)

    @kotlin.jvm.JvmName("assignPaddingAll")
    fun setPaddingAll(scale: SpacingScale?) {
        componentBootstrapInstance.setPadding(scale)
    }

    @kotlin.jvm.JvmName("obtainPaddingAll")
    fun getPaddingAll(): SpacingScale? = componentBootstrapInstance.getPadding()

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
            if (!value) {
                hoveredState = false
                focusedState = false
                activePressedState = false
                nativeImpl?.setHoveredState(false)
                nativeImpl?.setFocusedState(false)
                nativeImpl?.setActivePressedState(false)
            }
            nativeImpl?.setEnabled(value)
        }

    var isHovered: Boolean
        get() = if (!enabledState) false else (nativeImpl?.isHoveredState() ?: hoveredState)
        set(value) {
            val coerced = if (!enabledState) false else value
            hoveredState = coerced
            nativeImpl?.setHoveredState(coerced)
            notifyParentCacheDirty()
        }

    var isFocused: Boolean
        get() = if (!enabledState) false else (nativeImpl?.isFocusedState() ?: focusedState)
        set(value) {
            val coerced = if (!enabledState) false else value
            focusedState = coerced
            nativeImpl?.setFocusedState(coerced)
            notifyParentCacheDirty()
        }

    var isActivePressed: Boolean
        get() = if (!enabledState) false else (nativeImpl?.isActivePressedState() ?: activePressedState)
        set(value) {
            val coerced = if (!enabledState) false else value
            activePressedState = coerced
            nativeImpl?.setActivePressedState(coerced)
            notifyParentCacheDirty()
        }

    var opacity: Float
        get() = opacityState
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped >= 1.0f) inlineStyleMap.remove("opacity") else inlineStyleMap["opacity"] = clamped.toString()
            val prevStyle = computedStyleState ?: ComputedStyle(opacity = opacityState)
            opacityState = clamped
            nativeImpl?.setOpacity(opacityState)
            val nextStyle = prevStyle.copy(
                opacity = clamped,
                rawDeclarations = syncRawDeclarations(prevStyle, "opacity", if (clamped >= 1.0f) null else clamped.toString())
            )
            computedStyleState = nextStyle
            transitionController.onStyleChanged(prevStyle, nextStyle)
        }

    var tooltipText: String?
        get() = tooltipTextState
        set(value) {
            tooltipTextState = value
            nativeImpl?.setTooltipText(value)
        }

    open fun setAttribute(name: String, value: String) {
        customAttributesState[name] = value
        nativeImpl?.setAttribute(name, value)
    }

    fun getAttribute(name: String): String? = customAttributesState[name]

    fun removeAttribute(name: String) {
        customAttributesState.remove(name)
        nativeImpl?.setAttribute(name, null)
    }

    open fun setPreferredSize(size: Size?) {
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

    open fun applyComputedStyle(style: ComputedStyle) {
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
        get() {
            val combined = linkedMapOf<String, String>()
            combined.putAll(inlineStyleMap)
            for ((prop, pair) in componentStyleInstance.toCssPropertyMap()) {
                val (v, imp) = pair
                combined[prop] = if (imp) "$v !important" else v
            }
            return combined.entries.joinToString("; ") { "${it.key}: ${it.value}" }
        }
        set(value) {
            val previousKeys = (inlineStyleMap.keys + componentStyleInstance.toCssPropertyMap().keys).toSet()
            inlineStyleMap.clear()
            componentStyleInstance.clear()
            var cleanedBase = computedStyleState
            if (cleanedBase != null) {
                for (oldKey in previousKeys) {
                    cleanedBase = removeKeyFromComputedStyle(cleanedBase!!, oldKey)
                }
                computedStyleState = cleanedBase
            }
            if (value.isNotBlank()) {
                for (decl in value.split(';')) {
                    val colon = decl.indexOf(':')
                    if (colon > 0) {
                        val k = decl.substring(0, colon).trim().lowercase()
                        val v = decl.substring(colon + 1).trim()
                        if (k.isNotEmpty() && v.isNotEmpty()) {
                            inlineStyleMap[k] = v
                            componentStyleInstance.setRawDeclaration(k, v)
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
            componentStyleInstance.setRawDeclaration(key, null)
            computedStyleState = computedStyleState?.let { removeKeyFromComputedStyle(it, key) }
        } else {
            inlineStyleMap[key] = trimmedValue
            componentStyleInstance.setRawDeclaration(key, trimmedValue)
        }
        rebuildComputedStyleFromInlineMap()
    }

    fun removeStyleProperty(property: String) {
        setStyleProperty(property, "")
    }

    fun getStyleProperty(property: String): String? {
        val key = property.trim().lowercase()
        inlineStyleMap[key]?.let { return it }
        val pair = componentStyleInstance.toCssPropertyMap()[key] ?: return null
        return if (pair.second) "${pair.first} !important" else pair.first
    }

    fun clearStyleProperties() {
        val previousKeys = (inlineStyleMap.keys + componentStyleInstance.toCssPropertyMap().keys).toSet()
        inlineStyleMap.clear()
        componentStyleInstance.clear()
        var cleanedBase = computedStyleState
        if (cleanedBase != null) {
            for (oldKey in previousKeys) {
                cleanedBase = removeKeyFromComputedStyle(cleanedBase!!, oldKey)
            }
            computedStyleState = cleanedBase
        }
        rebuildComputedStyleFromInlineMap()
    }

    private fun rebuildComputedStyleFromInlineMap() {
        val activeFactory = resolveActiveFactory() ?: GenericUI.activeFactory
        val engineEnabled = activeFactory?.isStyleSheetEngineEnabled ?: true
        val sheetBase = if (engineEnabled) {
            lastAppliedStyleSheet?.resolveStyleForComponent(
                this,
                viewportWidthPx = lastStyleSheetViewportWidthPx
            )
        } else {
            null
        }
        val parsed = CssStyleSheet.parseInlineStyle(inlineStyle)
        val base = sheetBase ?: (computedStyleState ?: ComputedStyle())
        val merged = componentStyleInstance.toComputedStyle(base.mergeWith(parsed))
        applyComputedStyle(merged)
        nativeImpl?.applyComponentStyle(componentStyleInstance)
    }

    private fun removeKeyFromComputedStyle(base: ComputedStyle, key: String): ComputedStyle {
        val updatedRaw = base.rawDeclarations - key
        val updatedImportant = base.importantProperties - key
        return when (key) {
            "width" -> base.copy(width = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "height" -> base.copy(height = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "min-width" -> base.copy(minWidth = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "min-height" -> base.copy(minHeight = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "max-width" -> base.copy(maxWidth = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "max-height" -> base.copy(maxHeight = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "flex" -> base.copy(
                rawDeclarations = updatedRaw - "flex-grow" - "flex-shrink" - "flex-basis",
                importantProperties = updatedImportant - "flex-grow" - "flex-shrink" - "flex-basis"
            )
            "aspect-ratio" -> base.copy(aspectRatio = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "display" -> base.copy(display = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "position" -> base.copy(position = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "left" -> base.copy(left = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "top" -> base.copy(top = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "right" -> base.copy(right = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "bottom" -> base.copy(bottom = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "z-index" -> base.copy(zIndex = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "overflow" -> base.copy(overflow = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "background-color" -> base.copy(backgroundColor = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "color" -> base.copy(textColor = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "border-color" -> base.copy(borderColor = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            "border-radius" -> base.copy(borderRadiusPx = null, borderRadiusPercent = null, rawDeclarations = updatedRaw, importantProperties = updatedImportant)
            else -> base.copy(rawDeclarations = updatedRaw, importantProperties = updatedImportant)
        }
    }

    private fun syncRawDeclarations(base: ComputedStyle, cssKey: String, cssValue: String?): Map<String, String> =
        if (cssValue.isNullOrBlank()) base.rawDeclarations - cssKey else base.rawDeclarations + (cssKey to cssValue)

    var customBackgroundColor: RgbaColor?
        get() = computedStyleState?.backgroundColor
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    backgroundColor = value,
                    rawDeclarations = syncRawDeclarations(base, "background-color", value?.toCssRgbaString())
                )
            )
        }

    /**
     * Component background gradient ([com.nexustools.genericui.style.gradient.Gradient.LinearGradient] or
     * [com.nexustools.genericui.style.gradient.Gradient.RadialGradient]) configured via `style { ... }` or CSS.
     */
    var backgroundGradient: Gradient?
        get() = nativeImpl?.getBackgroundGradient() ?: computedStyleState?.backgroundGradient
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    backgroundGradient = value,
                    rawDeclarations = syncRawDeclarations(base, "background-image", value?.toCssString())
                )
            )
            nativeImpl?.setBackgroundGradient(value)
        }

    @kotlin.jvm.JvmName("assignBackgroundGradient")
    fun setBackgroundGradient(gradient: Gradient?) {
        this.backgroundGradient = gradient
    }

    @kotlin.jvm.JvmName("obtainBackgroundGradient")
    fun getBackgroundGradient(): Gradient? = this.backgroundGradient

    /**
     * Returns this component's inline [ComponentStyle] (`style="..."`), modeled after Web `HTMLElement.style`.
     */
    fun getStyle(): ComponentStyle = componentStyleInstance

    @get:kotlin.jvm.JvmName("obtainComponentStyle")
    val style: ComponentStyle
        get() = componentStyleInstance

    /**
     * Resolves the effective [ComputedStyle] for this component's current interactive state
     * ([isHovered], [isFocused], [isActivePressed], [isEnabled]).
     */
    fun resolveEffectiveStyle(checked: Boolean = false): ComputedStyle =
        (computedStyleState ?: ComputedStyle()).resolveForState(
            hovered = isEnabled && isHovered,
            focused = isEnabled && isFocused,
            active = isEnabled && isActivePressed,
            enabled = isEnabled,
            checked = checked
        )

    var textColor: RgbaColor?
        get() = computedStyleState?.textColor
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    textColor = value,
                    rawDeclarations = syncRawDeclarations(base, "color", value?.toCssRgbaString())
                )
            )
        }

    var borderColor: RgbaColor?
        get() = computedStyleState?.borderColor
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    borderColor = value,
                    rawDeclarations = syncRawDeclarations(base, "border-color", value?.toCssRgbaString())
                )
            )
        }

    var borderRadiusPx: Float?
        get() = computedStyleState?.borderRadiusPx
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    borderRadiusPx = value,
                    rawDeclarations = syncRawDeclarations(base, "border-radius", value?.let { "${it}px" })
                )
            )
        }

    open var backgroundImageUrl: String?
        get() = computedStyleState?.backgroundImageUrl
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    backgroundImageUrl = value,
                    rawDeclarations = syncRawDeclarations(base, "background-image", value?.let { "url('$it')" })
                )
            )
        }

    var textShadow: String?
        get() = computedStyleState?.textShadow
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    textShadow = value,
                    rawDeclarations = syncRawDeclarations(base, "text-shadow", value)
                )
            )
        }

    var boxShadow: String?
        get() = computedStyleState?.boxShadow
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    boxShadow = value,
                    rawDeclarations = syncRawDeclarations(base, "box-shadow", value)
                )
            )
        }

    var transform: String?
        get() = computedStyleState?.transform
        set(value) {
            if (value == null) inlineStyleMap.remove("transform") else inlineStyleMap["transform"] = value
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    transform = value,
                    rawDeclarations = syncRawDeclarations(base, "transform", value)
                )
            )
        }

    var transition: String?
        get() = computedStyleState?.transition
        set(value) {
            if (value == null) inlineStyleMap.remove("transition") else inlineStyleMap["transition"] = value
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    transition = value,
                    rawDeclarations = syncRawDeclarations(base, "transition", value)
                )
            )
        }

    var zIndex: Int?
        get() = computedStyleState?.zIndex
        set(value) {
            if (value == null) inlineStyleMap.remove("z-index") else inlineStyleMap["z-index"] = value.toString()
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    zIndex = value,
                    rawDeclarations = syncRawDeclarations(base, "z-index", value?.toString())
                )
            )
        }

    var aspectRatio: Float?
        get() = computedStyleState?.aspectRatio
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    aspectRatio = value,
                    rawDeclarations = syncRawDeclarations(base, "aspect-ratio", value?.toString())
                )
            )
        }

    var pointerEventsEnabled: Boolean
        get() = computedStyleState?.pointerEventsEnabled ?: true
        set(value) {
            if (value) inlineStyleMap.remove("pointer-events") else inlineStyleMap["pointer-events"] = "none"
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    pointerEventsEnabled = value,
                    rawDeclarations = syncRawDeclarations(base, "pointer-events", if (value) null else "none")
                )
            )
        }

    var cursor: String?
        get() = computedStyleState?.cursor
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    cursor = value,
                    rawDeclarations = syncRawDeclarations(base, "cursor", value)
                )
            )
        }

    var leftOffset: String?
        get() = computedStyleState?.left
        set(value) {
            if (value == null) inlineStyleMap.remove("left") else inlineStyleMap["left"] = value
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    left = value,
                    rawDeclarations = syncRawDeclarations(base, "left", value)
                )
            )
        }

    var topOffset: String?
        get() = computedStyleState?.top
        set(value) {
            if (value == null) inlineStyleMap.remove("top") else inlineStyleMap["top"] = value
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    top = value,
                    rawDeclarations = syncRawDeclarations(base, "top", value)
                )
            )
        }

    /**
     * Resolves and applies [styleSheet] rules to this component and (if this is an [AbstractContainer])
     * recursively to all child components.
     */
    open fun applyStyleSheet(styleSheet: CssStyleSheet, viewportWidthPx: Int = 1280) {
        val activeFactory = resolveActiveFactory() ?: GenericUI.activeFactory
        if (activeFactory != null && !activeFactory.isStyleSheetEngineEnabled) return
        lastAppliedStyleSheet = styleSheet
        lastStyleSheetViewportWidthPx = viewportWidthPx
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
    open fun applyPrecompiledStyleSheet(precompiled: PrecompiledStyleSheet, viewportWidthPx: Int = 1280) {
        val activeFactory = resolveActiveFactory() ?: GenericUI.activeFactory
        if (activeFactory != null && !activeFactory.isStyleSheetEngineEnabled) return
        applyStyleSheet(precompiled.styleSheet, viewportWidthPx)
    }

    // Element-Level Fullscreen API
    private var fullscreenElementState: Boolean = false
    private var fullscreenCutoutModeState: DisplayCutoutMode = DisplayCutoutMode.SHORT_EDGES

    var onFullscreenChanged: ((Boolean) -> Unit)? = null

    /**
     * Returns `true` when this component (or window/fragment) is currently displayed in fullscreen mode.
     */
    open fun isFullscreen(): Boolean {
        val hostWin = resolveHostingWindow()
        if (hostWin != null && hostWin !== this) {
            return hostWin.fullscreenElement === this || fullscreenElementState
        }
        return fullscreenElementState
    }

    /**
     * Requests that this component (and all of its child components, such as a video player + overlay stack)
     * enter fullscreen mode across the hosting window/viewport.
     */
    open fun requestFullscreen(cutoutMode: DisplayCutoutMode = DisplayCutoutMode.SHORT_EDGES) {
        fullscreenCutoutModeState = cutoutMode
        val hostWin = resolveHostingWindow()
        if (hostWin != null && hostWin !== this) {
            hostWin.requestElementFullscreen(this, cutoutMode)
        } else {
            updateFullscreenElementState(true, cutoutMode)
        }
    }

    /**
     * Exits fullscreen mode if this component (or its hosting window) is currently fullscreen.
     */
    open fun exitFullscreen() {
        val hostWin = resolveHostingWindow()
        if (hostWin != null && hostWin !== this) {
            if (hostWin.fullscreenElement === this || hostWin.isFullscreen()) {
                hostWin.exitElementFullscreen()
            } else {
                updateFullscreenElementState(false, fullscreenCutoutModeState)
            }
        } else {
            updateFullscreenElementState(false, fullscreenCutoutModeState)
        }
    }

    /**
     * Toggles fullscreen state for this component and returns the new fullscreen state.
     */
    open fun toggleFullscreen(cutoutMode: DisplayCutoutMode = DisplayCutoutMode.SHORT_EDGES): Boolean {
        if (isFullscreen()) {
            exitFullscreen()
        } else {
            requestFullscreen(cutoutMode)
        }
        return isFullscreen()
    }

    /**
     * Sets whether this component is in fullscreen mode.
     */
    open fun setFullscreen(fullscreen: Boolean) {
        setFullscreen(fullscreen, DisplayCutoutMode.SHORT_EDGES)
    }

    /**
     * Sets whether this component is in fullscreen mode with the given [cutoutMode].
     */
    open fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        if (fullscreen) {
            requestFullscreen(cutoutMode)
        } else {
            exitFullscreen()
        }
    }

    internal fun updateFullscreenElementState(
        fullscreen: Boolean,
        cutoutMode: DisplayCutoutMode = fullscreenCutoutModeState
    ) {
        val changed = fullscreenElementState != fullscreen
        fullscreenElementState = fullscreen
        fullscreenCutoutModeState = cutoutMode
        if (fullscreen) {
            setAttribute("data-genericui-fullscreen-element", "true")
        } else {
            removeAttribute("data-genericui-fullscreen-element")
        }
        if (changed) {
            onFullscreenChanged?.invoke(fullscreen)
        }
    }

    internal fun resolveHostingWindow(): com.nexustools.genericui.component.shell.Window? {
        var cur: AbstractComponent? = this
        while (cur != null) {
            if (cur is com.nexustools.genericui.component.shell.Window) return cur
            if (cur is com.nexustools.genericui.component.fragment.Fragment) {
                cur.attachedWindow?.let { return it }
                (cur.hostingWindow as? com.nexustools.genericui.component.shell.Window)?.let { return it }
            }
            if (cur is com.nexustools.genericui.component.fragment.FragmentContainer) {
                cur.hostWindow?.let { return it }
            }
            cur = cur.parent
        }
        return com.nexustools.genericui.component.shell.Application.current?.mainWindow
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
    @kotlin.jvm.JvmName("assignOnPointerEnter")
    fun setOnPointerEnter(listener: ((PointerEvent) -> Unit)?) { onPointerEnter = listener }
    @kotlin.jvm.JvmName("assignOnPointerLeave")
    fun setOnPointerLeave(listener: ((PointerEvent) -> Unit)?) { onPointerLeave = listener }
    @kotlin.jvm.JvmName("assignOnWheel")
    fun setOnWheel(listener: ((WheelEvent) -> Unit)?) { onWheel = listener }
    @kotlin.jvm.JvmName("assignOnKeyDown")
    fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) { onKeyDown = listener }
    @kotlin.jvm.JvmName("assignOnKeyUp")
    fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) { onKeyUp = listener }

    val isPointerDragging: Boolean
        get() = PointerDragManager.isDragging(this)

    /**
     * Starts a global pointer drag session via [PointerDragManager] so dragging continues
     * when the pointer leaves this component's bounds and pointer release (`pointerup` / `mouseup`)
     * is handled even outside the boundary of this component.
     */
    fun startPointerDrag(
        initialEvent: PointerEvent = PointerEvent(),
        onDragMove: (PointerEvent) -> Unit,
        onDragEnd: (event: PointerEvent, cancelled: Boolean) -> Unit = { _, _ -> }
    ): PointerDragSession = PointerDragManager.startDrag(
        target = this,
        initialEvent = initialEvent,
        onDragMove = onDragMove,
        onDragEnd = onDragEnd
    )

    override fun dispose() {
        if (PointerDragManager.isDragging(this)) {
            PointerDragManager.cancelActiveDrag()
        }
        clearAllTimers()
        nativeImpl?.dispose()
        nativeImpl = null
    }
}
