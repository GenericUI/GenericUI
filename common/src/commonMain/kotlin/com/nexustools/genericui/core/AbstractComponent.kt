package com.nexustools.genericui.core

import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.event.InputFocusManager
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerDragManager
import com.nexustools.genericui.event.PointerDragSession
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.PopupMouseCaptureManager
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCursorValue
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssProperty
import com.nexustools.genericui.style.css.CssRule
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.mapper.BootstrapClassMapper
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.transition.CssTransitionController
import com.nexustools.genericui.threading.GenericUiThreading
import com.nexustools.genericui.threading.PlatformReentrantLock
import com.nexustools.genericui.threading.withLock
import kotlin.concurrent.Volatile

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
    internal var lastAppliedStyleSheet: CssStyleSheet? = null
    internal var scopedStyleSheet: CssStyleSheet? = null
    internal var lastStyleSheetViewportWidthPx: Int = 1280
    private var lastStyleSheetMediaBucket: Int = Int.MIN_VALUE
    private var lastStyleSheetDarkTheme: Boolean? = null
    private var lastStyleSheetBackendTarget: BackendTarget? = null
    private var cachedSheetBaseStyle: ComputedStyle? = null
    var cachedSheetHasMediaRules: Boolean = true
    var cachedSelectorClasses: LinkedHashSet<String>? = null
    private var suppressComponentStyleCallback: Boolean = false

    internal fun invalidateCachedSheetBaseStyle() {
        cachedSheetBaseStyle = null
        cachedSheetHasMediaRules = true
        cachedSelectorClasses = null
        invalidateDescendantSheetBaseStyles(
            wholeSubtree = true,
            clearSelectorClasses = true,
            targetClasses = emptySet(),
            targetIds = emptySet(),
            targetTags = emptySet()
        )
    }

    internal fun hasCachedSheetBaseStyle(): Boolean = cachedSheetBaseStyle != null

    internal fun collectSelectorClassSnapshot(): Set<String> {
        cachedSelectorClasses?.let { return it }
        val classes = LinkedHashSet<String>()
        classes.addAll(styleSnapshot.customClasses)
        classes.addAll(getBootstrap().toEnumClassSet(componentClassPrefix))
        if (componentClassPrefix.isNotBlank()) {
            for (prefixTok in splitClassTokens(componentClassPrefix)) {
                classes.add(prefixTok)
            }
        }
        cachedSelectorClasses = classes
        return classes
    }

    private fun matchesInvalidationTargets(
        targetClasses: Set<String>,
        targetIds: Set<String>,
        targetTags: Set<String>,
        isDark: Boolean = false
    ): Boolean {
        if (targetIds.isNotEmpty() && id != null && id in targetIds) return true
        val normalizedTag = if (targetTags.isNotEmpty() || (isDark && "bs-theme-dark" in targetClasses)) {
            CssRule.normalizeSelectorTag(htmlTag.ifEmpty { "genericui:component" })
        } else {
            ""
        }
        if (targetTags.isNotEmpty() && normalizedTag in targetTags) return true
        if (targetClasses.isNotEmpty()) {
            if (isDark && "bs-theme-dark" in targetClasses && normalizedTag == "genericui:app") {
                return true
            }
            val allClasses = collectSelectorClassSnapshot()
            for (tc in targetClasses) {
                if (tc in allClasses) return true
            }
        }
        return false
    }

    private fun invalidateDescendantSheetBaseStyles(
        wholeSubtree: Boolean,
        clearSelectorClasses: Boolean,
        targetClasses: Set<String>,
        targetIds: Set<String>,
        targetTags: Set<String>
    ) {
        if (this is AbstractContainer) {
            var i = 0
            while (i < children.size) {
                children.getOrNull(i)?.invalidateSubtreeForAncestorChange(
                    wholeSubtree,
                    clearSelectorClasses,
                    targetClasses,
                    targetIds,
                    targetTags
                )
                i++
            }
        }
        if (this is Composite) {
            val compChildren = internalCompositeChildren()
            var i = 0
            while (i < compChildren.size) {
                compChildren.getOrNull(i)?.invalidateSubtreeForAncestorChange(
                    wholeSubtree,
                    clearSelectorClasses,
                    targetClasses,
                    targetIds,
                    targetTags
                )
                i++
            }
        }
    }

    private fun invalidateSubtreeForAncestorChange(
        wholeSubtree: Boolean,
        clearSelectorClasses: Boolean,
        targetClasses: Set<String>,
        targetIds: Set<String>,
        targetTags: Set<String>
    ) {
        if (clearSelectorClasses) {
            cachedSelectorClasses = null
        }
        if (wholeSubtree || matchesInvalidationTargets(targetClasses, targetIds, targetTags)) {
            cachedSheetBaseStyle = null
            cachedSheetHasMediaRules = true
        }
        invalidateDescendantSheetBaseStyles(
            wholeSubtree,
            clearSelectorClasses,
            targetClasses,
            targetIds,
            targetTags
        )
    }

    internal fun resolveEffectiveAncestorStyleSheet(): CssStyleSheet? {
        lastAppliedStyleSheet?.let { return it }
        var cur = parent
        while (cur != null) {
            cur.lastAppliedStyleSheet?.let { return it }
            cur = cur.parent
        }
        return null
    }

    /**
     * Resolves the media-query breakpoint bucket for [viewportWidthPx] against the active stylesheet,
     * returning `null` if no stylesheet has been applied to this component or its ancestors yet.
     */
    fun resolveActiveMediaQueryBucket(viewportWidthPx: Int): Int? =
        resolveEffectiveAncestorStyleSheet()?.resolveMediaQueryBucket(viewportWidthPx)

    fun isStyledForCurrentTheme(): Boolean {
        if (lastAppliedStyleSheet == null) return false
        val isDark = com.nexustools.genericui.component.shell.Application.current?.getResolvedTheme() ==
            com.nexustools.genericui.enums.ui.ThemeMode.DARK
        return lastStyleSheetDarkTheme == isDark
    }

    fun isStyledAtViewportAndTheme(viewportWidthPx: Int): Boolean {
        val sheet = lastAppliedStyleSheet ?: return false
        val isDark = com.nexustools.genericui.component.shell.Application.current?.getResolvedTheme() ==
            com.nexustools.genericui.enums.ui.ThemeMode.DARK
        if (lastStyleSheetDarkTheme != isDark) return false
        return lastStyleSheetMediaBucket == sheet.resolveMediaQueryBucket(viewportWidthPx)
    }

    private fun resolveOrGetCachedSheetBaseStyle(): ComputedStyle? {
        val sheet = lastAppliedStyleSheet ?: return null
        cachedSheetBaseStyle?.let { return it }
        return sheet.resolveSheetStyleForComponent(this, viewportWidthPx = lastStyleSheetViewportWidthPx).also {
            cachedSheetBaseStyle = it
        }
    }

    protected open fun defaultHtmlTag(): String = initialHtmlTag

    open var htmlTag: String = initialHtmlTag
        set(value) {
            val normalized = value.trim().ifEmpty { defaultHtmlTag() }
            if (field == normalized) return
            field = normalized
            invalidateCachedSheetBaseStyle()
            nativeImpl?.setAttribute("data-genericui-tag", normalized)
            val sheet = lastAppliedStyleSheet
            if (sheet != null) {
                applyStyleSheet(sheet, lastStyleSheetViewportWidthPx)
            }
        }
    open var id: String? = null
        set(value) {
            if (field == value) return
            field = value
            invalidateCachedSheetBaseStyle()
            nativeImpl?.setAttribute("id", value)
        }
    var parent: AbstractComponent? = null
        internal set(value) {
            if (field === value) return
            field = value
            recomputeInheritedFocusableFromParent()
        }

    /**
     * `true` when this component is currently mounted inside a [com.nexustools.genericui.window.TitleBar]'s
     * leading, center, or trailing slots on a host that supports custom titlebar components
     * (`TitleCapabilities.canEmbedComponentsInTitleBar`), ensuring it is rendered only once and skipped
     * by standard body/container layout walkers.
     */
    var mountedInTitleBar: Boolean = false

    @PublishedApi
    @Volatile
    internal var nativeImpl: NativeImpl<*>? = null

    @Volatile
    internal var nativeImplInitialized: Boolean = false

    private val nativeRealizeLock: PlatformReentrantLock = PlatformReentrantLock()
    private val attributesLock: PlatformReentrantLock = PlatformReentrantLock()
    private var inInitialEnsureAttach: Boolean = false

    open val activeFactory: NativeFactory?
        get() = resolveActiveFactory()

    open val isTextInputComponent: Boolean
        get() = false

    var ignoreKeyEventsWhenTypingInTextInput: Boolean = true

    var hookMouseWhenPopupVisible: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            syncPopupMouseCaptureRegistration()
        }

    var onPopupOutsideClickDismiss: (() -> Unit)? = null

    private fun syncPopupMouseCaptureRegistration() {
        if (hookMouseWhenPopupVisible && visibleState) {
            PopupMouseCaptureManager.registerPopup(this) {
                isVisible = false
                onPopupOutsideClickDismiss?.invoke()
            }
        } else {
            PopupMouseCaptureManager.unregisterPopup(this)
        }
    }

    // Lightweight pre-attachment Kotlin state
    private var visibleState: Boolean = true
    private var enabledState: Boolean = true
    private var lastAppliedEnabledForComputedStyle: Boolean = true
    private var hoveredState: Boolean = false
    private var focusedState: Boolean = false
    private var focusableState: Boolean = true
    internal var inheritedFocusableState: Boolean = true
    private var activePressedState: Boolean = false
    private var suppressVisualResyncOnComputedStyleChange: Boolean = false

    open val supportsFocus: Boolean
        get() = false
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
            componentStyleInstance.setMinWidth(value?.let { CssLength.px(it) })
        }
    var minHeightPx: Int? = null
        set(value) {
            field = value
            componentStyleInstance.setMinHeight(value?.let { CssLength.px(it) })
        }
    var maxWidthPx: Int? = null
        set(value) {
            field = value
            componentStyleInstance.setMaxWidth(value?.let { CssLength.px(it) })
        }
    var maxHeightPx: Int? = null
        set(value) {
            field = value
            componentStyleInstance.setMaxHeight(value?.let { CssLength.px(it) })
        }

    var padding: EdgeInsets = EdgeInsets.ZERO
        private set
    var margin: EdgeInsets = EdgeInsets.ZERO
        private set
    private var computedStyleState: ComputedStyle? = null
    val transitionController: CssTransitionController = CssTransitionController(this)
    private val activeAnimationsLock = PlatformReentrantLock()
    private val activeAnimations = mutableSetOf<com.nexustools.genericui.animation.AnimationHandle>()

    val isTransitioning: Boolean
        get() = transitionController.isTransitioning

    private var styleSnapshot: BootstrapStyleSnapshot = BootstrapStyleSnapshot(componentPrefix = componentClassPrefix)

    private val componentStyleInstance: ComponentStyle = ComponentStyle(
        onChange = { onComponentStyleChanged() },
        onPropertyChange = { prop -> onSingleInlineStylePropertyMutated(prop) }
    )

    private val componentBootstrapInstance: ComponentBootstrap = ComponentBootstrap(
        owner = this,
        onChange = { onComponentBootstrapChanged(it) }
    )

    open val acceptsTypographyComponentStyles: Boolean
        get() = com.nexustools.genericui.style.policy.AestheticStylePolicy.isExplicitlyStyledComponent(this)

    open val acceptsAestheticSkinComponentStyles: Boolean
        get() = com.nexustools.genericui.style.policy.AestheticStylePolicy.isExplicitlyStyledComponent(this)

    open val acceptsCssFilterComponentStyles: Boolean
        get() = acceptsAestheticSkinComponentStyles

    open fun isHostedInCustomStyleEngine(): Boolean {
        val factory = resolveActiveFactory() ?: GenericUI.activeFactory
        if (factory != null && !factory.ignoresAestheticStylesOnStandardComponents) {
            return true
        }
        var curr: AbstractComponent? = this
        while (curr != null) {
            val prefix = curr.componentClassPrefix
            if (prefix.contains("unalien", ignoreCase = true)) {
                return false
            }
            if (curr.getAttribute("data-style-engine") == "custom" ||
                prefix.contains("alien", ignoreCase = true)
            ) {
                return true
            }
            curr = curr.parent
        }
        return false
    }

    internal fun shouldFilterAestheticStylesForSystemBackend(
        resolvedFactory: NativeFactory? = resolveActiveFactory() ?: GenericUI.activeFactory
    ): Boolean {
        val factory = resolvedFactory ?: return false
        if (!factory.ignoresAestheticStylesOnStandardComponents) return false
        return !isHostedInCustomStyleEngine()
    }

    internal fun effectiveComponentStyle(
        filterSystem: Boolean = shouldFilterAestheticStylesForSystemBackend()
    ): ComponentStyle {
        return if (filterSystem) {
            componentStyleInstance.copyFilteredForSystemComponent(
                allowTypography = acceptsTypographyComponentStyles,
                allowAestheticSkin = acceptsAestheticSkinComponentStyles,
                allowFilter = acceptsCssFilterComponentStyles
            )
        } else {
            componentStyleInstance
        }
    }

    open fun onSingleInlineStylePropertyMutated(property: CssProperty) {
        if (suppressComponentStyleCallback) return
        val activeFactory = resolveActiveFactory() ?: GenericUI.activeFactory
        val engineEnabled = activeFactory?.isStyleSheetEngineEnabled ?: true
        val rawSheetBase = if (engineEnabled) resolveOrGetCachedSheetBaseStyle() else null
        val filterSystem = shouldFilterAestheticStylesForSystemBackend(activeFactory)
        val sheetBase = if (filterSystem && rawSheetBase != null) {
            rawSheetBase.copyFilteredForSystemComponent(
                allowTypography = acceptsTypographyComponentStyles,
                allowAestheticSkin = acceptsAestheticSkinComponentStyles,
                allowFilter = acceptsCssFilterComponentStyles
            )
        } else {
            rawSheetBase
        }
        val effectiveStyle = effectiveComponentStyle(filterSystem)
        val currentBase = computedStyleState ?: sheetBase ?: ComputedStyle.EMPTY
        val updatedRaw = effectiveStyle.applySinglePropertyToComputed(property, currentBase, sheetBase)
        val updated = if (filterSystem) {
            updatedRaw.copyFilteredForSystemComponent(
                allowTypography = acceptsTypographyComponentStyles,
                allowAestheticSkin = acceptsAestheticSkinComponentStyles,
                allowFilter = acceptsCssFilterComponentStyles
            )
        } else {
            updatedRaw
        }
        suppressVisualResyncOnComputedStyleChange = isPurePositionProperty(property)
        try {
            applyComputedStyle(updated)
        } finally {
            suppressVisualResyncOnComputedStyleChange = false
        }
        runOnNativeUi {
            it.applyComponentStyle(effectiveStyle)
            if (property == CssProperty.BACKGROUND_IMAGE) {
                it.setBackgroundGradient(updated.backgroundGradient)
            } else if (property == CssProperty.CURSOR) {
                it.setCustomCursor(updated.customCursor)
            }
        }
    }

    private fun isPurePositionProperty(property: CssProperty): Boolean =
        property == CssProperty.LEFT ||
            property == CssProperty.TOP ||
            property == CssProperty.RIGHT ||
            property == CssProperty.BOTTOM ||
            property == CssProperty.Z_INDEX

    private fun onComponentStyleChanged() {
        if (suppressComponentStyleCallback) return
        val activeFactory = resolveActiveFactory() ?: GenericUI.activeFactory
        val engineEnabled = activeFactory?.isStyleSheetEngineEnabled ?: true
        val rawBase = if (engineEnabled) {
            resolveOrGetCachedSheetBaseStyle() ?: computedStyleState
        } else {
            computedStyleState
        }
        val filterSystem = shouldFilterAestheticStylesForSystemBackend(activeFactory)
        val base = if (filterSystem && rawBase != null) {
            rawBase.copyFilteredForSystemComponent(
                allowTypography = acceptsTypographyComponentStyles,
                allowAestheticSkin = acceptsAestheticSkinComponentStyles,
                allowFilter = acceptsCssFilterComponentStyles
            )
        } else {
            rawBase
        }
        val effectiveStyle = effectiveComponentStyle(filterSystem)
        val updated = effectiveStyle.toComputedStyle(base)
        applyComputedStyle(updated)
        runOnNativeUi {
            it.applyComponentStyle(effectiveStyle)
            it.setBackgroundGradient(updated.backgroundGradient)
            it.setCustomCursor(updated.customCursor)
        }
    }

    internal open fun onAfterBootstrapChanged() {}

    internal open fun onComponentOpacityChanged() {}

    internal open fun onComponentEnabledChanged(enabled: Boolean) {}

    internal open fun onComponentPaddingChanged() {}

    protected open fun onAncestorHierarchyChanged() {}

    internal fun dispatchAncestorHierarchyChanged() {
        if (isDisposed) return
        onAncestorHierarchyChanged()
        if (this is AbstractContainer) {
            for (child in children) {
                child.dispatchAncestorHierarchyChanged()
            }
        }
    }

    private fun onComponentBootstrapChanged(bootstrap: ComponentBootstrap) {
        val nextSnapshot = bootstrap.applyToSnapshot(styleSnapshot)
        if (nextSnapshot != styleSnapshot) {
            styleSnapshot = nextSnapshot
            invalidateCachedSheetBaseStyle()
        }
        notifyParentCacheDirty()
        runOnNativeUi { impl ->
            syncBootstrapEnumsToNative(impl)
            impl.applyComponentBootstrap(bootstrap)
        }
        onAfterBootstrapChanged()
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
        var cur = parent
        while (cur != null) {
            if (cur is AbstractContainer) {
                cur.markRenderCacheDirty()
                return
            }
            cur = cur.parent
        }
    }

    /**
     * Steps any active CSS transitions on this component and (if this is an [AbstractContainer] or
     * [Composite]) recursively on all child components by [deltaMs] milliseconds.
     */
    fun stepTransitions(deltaMs: Float) {
        transitionController.step(deltaMs)
        if (this is AbstractContainer) {
            for (child in children) {
                child.stepTransitions(deltaMs)
            }
        }
        if (this is Composite) {
            for (child in internalCompositeChildren()) {
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
        if (this is Composite) {
            for (child in internalCompositeChildren()) {
                child.finishTransitions()
            }
        }
    }

    /**
     * Runs a native/VBlank-backed eased `0f -> 1f` animation scoped to this component's lifetime
     * (automatically cancelled on [dispose]).
     */
    fun animate(
        durationMs: Int,
        delayMs: Int = 0,
        timingFunction: com.nexustools.genericui.style.transition.CssTimingFunction =
            com.nexustools.genericui.style.transition.CssTimingFunction.EASE_IN_OUT,
        onUpdate: (progress: Float) -> Unit,
        onComplete: ((cancelled: Boolean) -> Unit)? = null
    ): com.nexustools.genericui.animation.AnimationHandle {
        lateinit var handle: com.nexustools.genericui.animation.AnimationHandle
        var finishedEarly = false
        handle = com.nexustools.genericui.animation.Animator.animate(
            durationMs = durationMs,
            delayMs = delayMs,
            timingFunction = timingFunction,
            target = nativeImpl,
            onUpdate = onUpdate,
            onComplete = { cancelled ->
                finishedEarly = true
                activeAnimationsLock.withLock { activeAnimations.remove(handle) }
                onComplete?.invoke(cancelled)
            }
        )
        if (!finishedEarly && handle.isRunning) {
            activeAnimationsLock.withLock { activeAnimations.add(handle) }
        }
        return handle
    }

    fun cancelAnimations() {
        val snapshot = activeAnimationsLock.withLock {
            val copy = activeAnimations.toList()
            activeAnimations.clear()
            copy
        }
        for (handle in snapshot) {
            handle.cancel()
        }
        transitionController.cancelAllTransitions(disposing = false)
    }

    // Event listeners
    internal var internalClickAction: (() -> Unit)? = null
        set(value) {
            field = value
            runOnNativeUi { it.setOnClick(buildEffectiveClickListener()) }
        }

    open var onClick: (() -> Unit)? = null
        set(value) {
            field = value
            runOnNativeUi { it.setOnClick(buildEffectiveClickListener()) }
        }

    private fun buildEffectiveClickListener(): (() -> Unit)? {
        val internal = internalClickAction
        val raw = onClick
        if (internal == null && raw == null) return null
        return {
            if (!PopupMouseCaptureManager.interceptClick(target = this)) {
                internalClickAction?.invoke()
                val userHandler = onClick
                if (userHandler != null) {
                    GenericUiThreading.runOnLogic {
                        userHandler()
                    }
                }
            }
        }
    }

    open fun triggerClick() {
        if (!isEnabled) return
        if (PopupMouseCaptureManager.interceptClick(target = this)) return
        internalClickAction?.invoke()
        val raw = onClick ?: return
        GenericUiThreading.runOnLogic {
            raw()
        }
    }
    var onDoubleClick: (() -> Unit)? = null
        set(value) {
            val wrapped: (() -> Unit)? = if (value == null) null else {
                {
                    GenericUiThreading.runOnLogic {
                        value()
                    }
                }
            }
            field = wrapped
            runOnNativeUi { it.setOnDoubleClick(wrapped) }
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
        val handler = onContextMenu ?: return
        GenericUiThreading.runOnLogic {
            handler(event)
        }
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
            val handler = onContextMenu
            if (handler != null) {
                GenericUiThreading.runOnLogic {
                    handler(event)
                }
            }
        }
    }

    private fun syncContextMenuListener() {
        runOnNativeUi { it.setOnContextMenu(buildEffectiveContextMenuListener()) }
    }

    private fun wrapPointerListener(listener: ((PointerEvent) -> Unit)?): ((PointerEvent) -> Unit)? {
        if (listener == null) return null
        return { ev ->
            if (GenericUiThreading.isOnLogicThread()) {
                listener(ev)
            } else {
                GenericUiThreading.runOnLogic {
                    listener(ev)
                }
            }
        }
    }

    var onPointerDown: ((PointerEvent) -> Unit)? = null
        set(value) {
            val wrapped = wrapPointerListener(value)
            field = wrapped
            runOnNativeUi { it.setOnPointerDown(wrapped) }
        }
    var onPointerMove: ((PointerEvent) -> Unit)? = null
        set(value) {
            val wrapped = wrapPointerListener(value)
            field = wrapped
            runOnNativeUi { it.setOnPointerMove(wrapped) }
        }
    var onPointerUp: ((PointerEvent) -> Unit)? = null
        set(value) {
            val wrapped = wrapPointerListener(value)
            field = wrapped
            runOnNativeUi { it.setOnPointerUp(wrapped) }
        }
    var onPointerEnter: ((PointerEvent) -> Unit)? = null
        set(value) {
            field = wrapPointerListener(value)
            runOnNativeUi { it.setOnPointerEnter(buildEffectivePointerEnterListener()) }
        }
    var onPointerLeave: ((PointerEvent) -> Unit)? = null
        set(value) {
            field = wrapPointerListener(value)
            runOnNativeUi { it.setOnPointerLeave(buildEffectivePointerLeaveListener()) }
        }

    private fun buildEffectivePointerEnterListener(): (PointerEvent) -> Unit = { ev ->
        GenericUiThreading.runOnLogic {
            updateHoveredStateInternal(true, pushToNative = false)
            onPointerEnter?.invoke(ev)
        }
    }

    private fun buildEffectivePointerLeaveListener(): (PointerEvent) -> Unit = { ev ->
        GenericUiThreading.runOnLogic {
            updateHoveredStateInternal(false, pushToNative = false)
            onPointerLeave?.invoke(ev)
        }
    }
    var onWheel: ((WheelEvent) -> Unit)? = null
        set(value) {
            val wrapped: ((WheelEvent) -> Unit)? = if (value == null) null else { ev ->
                GenericUiThreading.runOnLogic {
                    value(ev)
                }
            }
            field = wrapped
            runOnNativeUi { it.setOnWheel(wrapped) }
        }
    var onKeyDown: ((KeyEvent) -> Unit)? = null
        set(value) {
            val wrapped: ((KeyEvent) -> Unit)? = if (value == null) null else { ev ->
                if (!InputFocusManager.shouldIgnoreShortcutKeyEvent(ev, this)) {
                    GenericUiThreading.runOnLogic {
                        value(ev)
                    }
                }
            }
            field = wrapped
            runOnNativeUi { it.setOnKeyDown(wrapped) }
        }
    var onKeyUp: ((KeyEvent) -> Unit)? = null
        set(value) {
            val wrapped: ((KeyEvent) -> Unit)? = if (value == null) null else { ev ->
                if (!InputFocusManager.shouldIgnoreShortcutKeyEvent(ev, this)) {
                    GenericUiThreading.runOnLogic {
                        value(ev)
                    }
                }
            }
            field = wrapped
            runOnNativeUi { it.setOnKeyUp(wrapped) }
        }

    open fun triggerKeyDown(ev: KeyEvent) {
        if (!isEnabled) return
        if (InputFocusManager.shouldIgnoreShortcutKeyEvent(ev, this)) return
        onKeyDown?.invoke(ev)
    }

    open fun triggerKeyUp(ev: KeyEvent) {
        if (!isEnabled) return
        if (InputFocusManager.shouldIgnoreShortcutKeyEvent(ev, this)) return
        onKeyUp?.invoke(ev)
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

    protected inline fun <reified T : NativeImpl<*>> requireInitializedNativeImpl(operationName: String): T {
        val impl = nativeImpl as? T
        if (impl != null) return impl
        val targetName = this::class.simpleName ?: htmlTag
        throw com.nexustools.genericui.exception.NativeUninitializedException(targetName, operationName)
    }

    protected inline fun runOnNativeUi(crossinline block: (NativeImpl<*>) -> Unit) {
        val impl = nativeImpl ?: return
        if (GenericUiThreading.isOnLogicThread() && GenericUiThreading.isSplitThreadingEnabled) {
            GenericUiThreading.runOnUi {
                block(impl)
            }
        } else {
            block(impl)
        }
    }

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

    open fun ensureNativeImpl(): NativeImpl<*>? {
        if (!visibleState) return null
        val fastExisting = nativeImpl
        if (fastExisting != null && nativeImplInitialized) return fastExisting

        return nativeRealizeLock.withLock {
            if (!visibleState) return@withLock null
            val existing = nativeImpl
            if (existing != null) {
                return@withLock existing
            }

            val factory = resolveActiveFactory() ?: return@withLock null
            val created = createNativeImpl(factory)
            nativeImplInitialized = false
            nativeImpl = created
            inInitialEnsureAttach = true
            created.beginBatchAttach()
            try {
                onAttachNative(created)
                syncStateToNative()
                nativeImplInitialized = true
            } catch (t: Throwable) {
                if (nativeImpl === created) {
                    nativeImpl = null
                    nativeImplInitialized = false
                }
                runCatching { created.dispose() }
                throw t
            } finally {
                inInitialEnsureAttach = false
                created.endBatchAttach()
            }
            created
        }
    }

    protected open fun syncStateToNative() {
        if (!inInitialEnsureAttach) {
            nativeImpl?.let { syncBaseStateToNative(it) }
        }
    }

    internal fun syncStateToNativeInternal() = syncStateToNative()

    protected open fun resolveInitialNativeVisibility(): Boolean = visibleState

    protected open fun onAttachNative(impl: NativeImpl<*>) {
        syncBaseStateToNative(impl)
    }

    protected fun syncBaseStateToNative(impl: NativeImpl<*>) {
        syncStyleSnapshotToNative(impl)
        impl.setVisible(resolveInitialNativeVisibility())
        impl.setEnabled(enabledState)
        val effectiveFocusable = isFocusable
        if (supportsFocus && this !is Composite) {
            impl.setFocusable(effectiveFocusable)
        }
        impl.setHoveredState(hoveredState)
        impl.setFocusedState(if (supportsFocus && effectiveFocusable) focusedState else false)
        impl.setActivePressedState(activePressedState)
        impl.setOpacity(opacityState)
        impl.setTooltipText(tooltipTextState)
        impl.setAttribute("data-genericui-tag", htmlTag)
        id?.let { impl.setAttribute("id", it) }
        val attrSnapshot = attributesLock.withLock {
            customAttributesState.entries.map { it.key to it.value }
        }
        for ((attrKey, attrVal) in attrSnapshot) {
            impl.setAttribute(attrKey, attrVal)
        }
        impl.setPreferredSize(preferredSizeState)
        suppressComponentStyleCallback = true
        try {
            minWidthPx?.let {
                componentStyleInstance.setMinWidth(CssLength.px(it))
            }
            minHeightPx?.let {
                componentStyleInstance.setMinHeight(CssLength.px(it))
            }
            maxWidthPx?.let {
                componentStyleInstance.setMaxWidth(CssLength.px(it))
            }
            maxHeightPx?.let {
                componentStyleInstance.setMaxHeight(CssLength.px(it))
            }
        } finally {
            suppressComponentStyleCallback = false
        }
        impl.setPadding(padding)
        impl.setMargin(margin)
        val filterSystem = shouldFilterAestheticStylesForSystemBackend()
        computedStyleState?.let {
            val effectiveComputed = if (filterSystem) {
                it.copyFilteredForSystemComponent(
                    allowTypography = acceptsTypographyComponentStyles,
                    allowAestheticSkin = acceptsAestheticSkinComponentStyles,
                    allowFilter = acceptsCssFilterComponentStyles
                )
            } else {
                it
            }
            impl.applyComputedStyle(effectiveComputed)
            impl.setBackgroundGradient(effectiveComputed.backgroundGradient)
            impl.setCustomCursor(effectiveComputed.customCursor)
        }
        if (!componentStyleInstance.isEmpty()) {
            impl.applyComponentStyle(effectiveComponentStyle())
        }
        impl.applyComponentBootstrap(componentBootstrapInstance)
        impl.setOnClick(buildEffectiveClickListener())
        impl.setOnDoubleClick(onDoubleClick)
        impl.setOnContextMenu(buildEffectiveContextMenuListener())
        impl.setOnPointerDown(onPointerDown)
        impl.setOnPointerMove(onPointerMove)
        impl.setOnPointerUp(onPointerUp)
        impl.setOnPointerEnter(buildEffectivePointerEnterListener())
        impl.setOnPointerLeave(buildEffectivePointerLeaveListener())
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
        if (styleSnapshot.customClasses.isNotEmpty()) {
            impl.setClass(styleSnapshot.customClasses.joinToString(" "))
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

    private fun splitClassTokens(className: String): List<String> {
        val trimmed = className.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!trimmed.contains(' ') && !trimmed.contains('\t') && !trimmed.contains('\n')) {
            return listOf(trimmed)
        }
        val out = ArrayList<String>(4)
        var start = -1
        for (i in trimmed.indices) {
            val ch = trimmed[i]
            if (ch.isWhitespace()) {
                if (start >= 0) {
                    out.add(trimmed.substring(start, i))
                    start = -1
                }
            } else if (start < 0) {
                start = i
            }
        }
        if (start >= 0) {
            out.add(trimmed.substring(start))
        }
        return out
    }

    fun setClass(className: String) {
        val oldCustom = styleSnapshot.customClasses
        val customSet = splitClassTokens(className).toSet()
        if (oldCustom == customSet) return
        val changedTokens = ArrayList<String>(oldCustom.size + customSet.size).apply {
            for (c in oldCustom) if (c !in customSet) add(c)
            for (c in customSet) if (c !in oldCustom) add(c)
        }
        styleSnapshot = styleSnapshot.copy(customClasses = customSet)
        val joined = customSet.joinToString(" ")
        handleCustomClassesChanged(changedTokens) { it.setClass(joined) }
    }

    fun setClasses(classNames: Collection<String>) {
        setClass(classNames.joinToString(" "))
    }

    fun getClass(): String = styleSnapshot.customClasses.joinToString(" ")

    fun addClass(className: String) {
        val tokens = splitClassTokens(className)
        if (tokens.isEmpty()) return
        val updated = LinkedHashSet(styleSnapshot.customClasses)
        if (!updated.addAll(tokens)) return
        styleSnapshot = styleSnapshot.copy(customClasses = updated)
        handleCustomClassesChanged(tokens) { impl ->
            for (tok in tokens) {
                impl.addClass(tok)
            }
        }
    }

    fun addClass(vararg classNames: String) {
        for (cls in classNames) {
            addClass(cls)
        }
    }

    fun removeClass(className: String) {
        val tokens = splitClassTokens(className)
        if (tokens.isEmpty()) return
        val updated = LinkedHashSet(styleSnapshot.customClasses)
        var changed = false
        for (tok in tokens) {
            if (updated.remove(tok)) changed = true
        }
        if (!changed) return
        styleSnapshot = styleSnapshot.copy(customClasses = updated)
        handleCustomClassesChanged(tokens) { impl ->
            for (tok in tokens) {
                impl.removeClass(tok)
            }
        }
    }

    fun removeClass(vararg classNames: String) {
        for (cls in classNames) {
            removeClass(cls)
        }
    }

    fun toggleClass(className: String, force: Boolean? = null): Boolean {
        val tokens = splitClassTokens(className)
        if (tokens.isEmpty()) return false
        val shouldAdd = force ?: !hasClass(className)
        if (shouldAdd) {
            addClass(className)
        } else {
            removeClass(className)
        }
        return shouldAdd
    }

    private fun handleCustomClassesChanged(
        changedTokens: List<String>,
        notifyNative: (NativeImpl<*>) -> Unit
    ) {
        cachedSelectorClasses = null
        val sheet = resolveEffectiveAncestorStyleSheet()
        if (sheet == null) {
            invalidateCachedSheetBaseStyle()
            notifyParentCacheDirty()
            runOnNativeUi(notifyNative)
            return
        }

        var affectsSelf = false
        for (cls in changedTokens) {
            if (sheet.isClassReferencedInSubject(cls)) {
                affectsSelf = true
                break
            }
        }
        val targetClasses = linkedSetOf<String>()
        val targetIds = linkedSetOf<String>()
        val targetTags = linkedSetOf<String>()
        val descendantMode = sheet.collectDescendantInvalidation(
            component = this,
            changedClasses = changedTokens,
            outSubjectClasses = targetClasses,
            outSubjectIds = targetIds,
            outSubjectTags = targetTags
        )
        val affectsDescendants = descendantMode != CssStyleSheet.DESCENDANT_INVALIDATION_NONE

        if (affectsSelf || affectsDescendants) {
            cachedSheetBaseStyle = null
            cachedSheetHasMediaRules = true
        }
        if (affectsDescendants) {
            invalidateDescendantSheetBaseStyles(
                wholeSubtree = descendantMode == CssStyleSheet.DESCENDANT_INVALIDATION_WHOLE_SUBTREE,
                clearSelectorClasses = false,
                targetClasses = targetClasses,
                targetIds = targetIds,
                targetTags = targetTags
            )
        }
        if (affectsSelf || affectsDescendants) {
            notifyParentCacheDirty()
        }
        runOnNativeUi(notifyNative)
        if (affectsDescendants) {
            applyStyleSheetForInvalidatedDescendants(
                styleSheet = sheet,
                viewportWidthPx = lastStyleSheetViewportWidthPx,
                wholeSubtree = descendantMode == CssStyleSheet.DESCENDANT_INVALIDATION_WHOLE_SUBTREE,
                targetClasses = targetClasses,
                targetIds = targetIds,
                targetTags = targetTags
            )
        } else if (affectsSelf) {
            applyStyleSheetSelfOnly(sheet, lastStyleSheetViewportWidthPx)
        }
    }

    fun hasClass(className: String): Boolean {
        val tokens = splitClassTokens(className)
        if (tokens.isEmpty()) return false
        return styleSnapshot.customClasses.containsAll(tokens)
    }

    // ComponentBootstrap API & Convenience Delegation
    fun getBootstrap(): ComponentBootstrap = componentBootstrapInstance

    @get:kotlin.jvm.JvmName("obtainComponentBootstrap")
    val bootstrap: ComponentBootstrap
        get() = componentBootstrapInstance

    // Bootstrap Enum Helper Properties & Methods
    open var type: BootstrapType?
        get() = getType()
        set(value) = setType(value)

    @kotlin.jvm.JvmName("assignType")
    fun setType(type: BootstrapType?) {
        componentBootstrapInstance.setType(type)
    }

    @kotlin.jvm.JvmName("obtainType")
    fun getType(): BootstrapType? = componentBootstrapInstance.getType()

    open var outline: Boolean
        get() = isOutline()
        set(value) = setOutline(value)

    @kotlin.jvm.JvmName("assignOutline")
    fun setOutline(outline: Boolean) {
        componentBootstrapInstance.setOutline(outline)
    }

    fun isOutline(): Boolean = componentBootstrapInstance.isOutline()

    open var size: BootstrapSize?
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

    @get:kotlin.jvm.JvmName("getFlexWrapProp")
    @set:kotlin.jvm.JvmName("setFlexWrapProp")
    var flexWrap: FlexWrap?
        get() = getFlexWrap()
        set(value) = setFlexWrap(value)

    open fun setFlexWrap(wrap: FlexWrap?) {
        componentBootstrapInstance.setFlexWrap(wrap)
    }

    open fun getFlexWrap(): FlexWrap? = componentBootstrapInstance.getFlexWrap()

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
            val prevVisible = visibleState
            visibleState = value
            if (hookMouseWhenPopupVisible || !value) {
                syncPopupMouseCaptureRegistration()
            }
            if (prevVisible == value && (!value || (nativeImpl != null && nativeImplInitialized))) return
            if (value) {
                var anc = parent
                while (anc != null) {
                    if (anc is AbstractContainer) {
                        anc.markChildrenReconcileDirty()
                        break
                    }
                    anc = anc.parent
                }
                val impl = ensureNativeImpl()
                if (impl != null) {
                    runOnNativeUi {
                        impl.setVisible(true)
                        (parent as? AbstractContainer)?.attachRealizedChildToNative(this, impl)
                        (parent as? Composite)?.attachRealizedCompositeChildToNative(this, impl)
                    }
                }
            } else {
                runOnNativeUi { it.setVisible(false) }
            }
        }

    open var isEnabled: Boolean
        get() = enabledState
        set(value) {
            val changed = enabledState != value
            enabledState = value
            if (!value) {
                hoveredState = false
                focusedState = false
                activePressedState = false
                InputFocusManager.onComponentFocusChanged(this, false)
                runOnNativeUi {
                    it.setHoveredState(false)
                    it.setFocusedState(false)
                    it.setActivePressedState(false)
                }
            }
            runOnNativeUi { it.setEnabled(value) }
            if (changed) {
                invalidateCachedSheetBaseStyle()
                resolveEffectiveAncestorStyleSheet()?.let { sheet ->
                    applyStyleSheet(sheet, lastStyleSheetViewportWidthPx)
                }
                onComponentEnabledChanged(value)
            }
        }

    var isHovered: Boolean
        get() = if (!enabledState) false else (nativeImpl?.isHoveredState() ?: hoveredState)
        set(value) {
            updateHoveredStateInternal(value, pushToNative = true)
        }

    private fun updateHoveredStateInternal(value: Boolean, pushToNative: Boolean) {
        val coerced = if (!enabledState) false else value
        val changed = hoveredState != coerced
        hoveredState = coerced
        if (pushToNative) {
            runOnNativeUi { it.setHoveredState(coerced) }
        }
        if (changed) {
            invalidateDescendantsForPseudoChange(listOf("pseudo:hover"))
        } else {
            notifyParentCacheDirty()
        }
    }

    private fun invalidateDescendantsForPseudoChange(changedTokens: List<String>) {
        val sheet = resolveEffectiveAncestorStyleSheet()
        if (sheet == null) {
            notifyParentCacheDirty()
            return
        }
        val targetClasses = linkedSetOf<String>()
        val targetIds = linkedSetOf<String>()
        val targetTags = linkedSetOf<String>()
        val descendantMode = sheet.collectDescendantInvalidation(
            component = this,
            changedClasses = changedTokens,
            outSubjectClasses = targetClasses,
            outSubjectIds = targetIds,
            outSubjectTags = targetTags
        )
        val affectsDescendants = descendantMode != CssStyleSheet.DESCENDANT_INVALIDATION_NONE
        if (affectsDescendants) {
            invalidateDescendantSheetBaseStyles(
                wholeSubtree = descendantMode == CssStyleSheet.DESCENDANT_INVALIDATION_WHOLE_SUBTREE,
                clearSelectorClasses = false,
                targetClasses = targetClasses,
                targetIds = targetIds,
                targetTags = targetTags
            )
            notifyParentCacheDirty()
            applyStyleSheetForInvalidatedDescendants(
                styleSheet = sheet,
                viewportWidthPx = lastStyleSheetViewportWidthPx,
                wholeSubtree = descendantMode == CssStyleSheet.DESCENDANT_INVALIDATION_WHOLE_SUBTREE,
                targetClasses = targetClasses,
                targetIds = targetIds,
                targetTags = targetTags
            )
        } else {
            notifyParentCacheDirty()
        }
    }

    @set:kotlin.jvm.JvmName("setFocusableProp")
    var isFocusable: Boolean
        get() = isFocusableStateInternal()
        set(value) = setFocusable(value)

    internal open fun isFocusableStateInternal(): Boolean =
        if (supportsFocus) focusableState && inheritedFocusableState else false

    open fun setFocusable(focusable: Boolean) {
        if (!supportsFocus) return
        if (focusableState == focusable) return
        val prevEffective = focusableState && inheritedFocusableState
        focusableState = focusable
        val nextEffective = focusableState && inheritedFocusableState
        if (prevEffective != nextEffective) {
            onEffectiveFocusableChanged(nextEffective)
        }
    }

    internal open fun effectiveDescendantFocusAllowed(): Boolean = inheritedFocusableState

    internal open fun onEffectiveFocusableChanged(effectiveFocusable: Boolean) {
        if (!effectiveFocusable) {
            focusedState = false
            InputFocusManager.onComponentFocusChanged(this, false)
            runOnNativeUi {
                it.setFocusedState(false)
                it.setFocusable(false)
            }
            notifyParentCacheDirty()
        } else {
            runOnNativeUi {
                it.setFocusable(true)
            }
        }
    }

    internal open fun updateInheritedFocusable(ancestorAllowsFocus: Boolean) {
        if (!supportsFocus) {
            inheritedFocusableState = ancestorAllowsFocus
            return
        }
        val prevEffective = focusableState && inheritedFocusableState
        inheritedFocusableState = ancestorAllowsFocus
        val nextEffective = focusableState && inheritedFocusableState
        if (prevEffective != nextEffective) {
            onEffectiveFocusableChanged(nextEffective)
        }
    }

    internal fun recomputeInheritedFocusableFromParent() {
        val allow = parent?.effectiveDescendantFocusAllowed() ?: true
        updateInheritedFocusable(allow)
    }

    internal open fun canAcceptFocusForIsFocused(): Boolean = supportsFocus && isFocusable

    internal fun clearFocusedFromAncestorSuppression() {
        focusedState = false
        InputFocusManager.onComponentFocusChanged(this, false)
        runOnNativeUi { it.setFocusedState(false) }
        notifyParentCacheDirty()
    }

    open var isFocused: Boolean
        get() = if (!enabledState || !canAcceptFocusForIsFocused()) false else (nativeImpl?.isFocusedState() ?: focusedState)
        set(value) {
            val coerced = if (!enabledState || !canAcceptFocusForIsFocused()) false else value
            focusedState = coerced
            InputFocusManager.onComponentFocusChanged(this, coerced)
            runOnNativeUi { it.setFocusedState(coerced) }
            notifyParentCacheDirty()
        }

    var isActivePressed: Boolean
        get() = if (!enabledState) false else (nativeImpl?.isActivePressedState() ?: activePressedState)
        set(value) {
            val coerced = if (!enabledState) false else value
            activePressedState = coerced
            runOnNativeUi { it.setActivePressedState(coerced) }
            notifyParentCacheDirty()
        }

    var opacity: Float
        get() = opacityState
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (kotlin.math.abs(opacityState - clamped) < 1e-4f && computedStyleState != null && componentStyleInstance.opacity == clamped) return
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setOpacity(clamped)
            } finally {
                suppressComponentStyleCallback = false
            }
            val prevStyle = computedStyleState ?: ComputedStyle(opacity = opacityState)
            opacityState = clamped
            val nextStyle = prevStyle.copy(
                opacity = clamped
            )
            computedStyleState = nextStyle
            transitionController.onStyleChanged(prevStyle, nextStyle)
            runOnNativeUi {
                if (!transitionController.hasActiveSoftwareTransition("opacity")) {
                    it.applyComponentStyle(effectiveComponentStyle())
                    it.setOpacity(opacityState)
                    it.applyComputedStyle(nextStyle)
                } else {
                    val frameAlpha = transitionController.currentFrameState.opacity
                    it.setOpacity(frameAlpha)
                    it.applyComputedStyle(nextStyle.copy(opacity = frameAlpha))
                }
            }
            if (!transitionController.hasActiveSoftwareTransition("opacity")) {
                onComponentOpacityChanged()
            }
        }

    val animatedOpacity: Float
        get() = if (transitionController.hasActiveSoftwareTransition("opacity")) {
            transitionController.currentFrameState.opacity
        } else {
            opacityState
        }

    internal fun applyAnimatedOpacityForTransition(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        runOnNativeUi {
            it.setOpacity(clamped)
        }
    }

    open var tooltipText: String?
        get() = tooltipTextState
        set(value) {
            tooltipTextState = value
            runOnNativeUi { it.setTooltipText(value) }
        }

    open fun setAttribute(name: String, value: String) {
        val changed = attributesLock.withLock {
            if (customAttributesState[name] == value) {
                false
            } else {
                customAttributesState[name] = value
                true
            }
        }
        if (!changed) return
        runOnNativeUi { it.setAttribute(name, value) }
    }

    fun getAttribute(name: String): String? = attributesLock.withLock { customAttributesState[name] }

    fun getAttributes(): Map<String, String> = attributesLock.withLock { customAttributesState.toMap() }

    fun removeAttribute(name: String) {
        attributesLock.withLock {
            customAttributesState.remove(name)
        }
        runOnNativeUi { it.setAttribute(name, null) }
    }

    open fun setPreferredSize(size: Size?) {
        preferredSizeState = size
        runOnNativeUi { it.setPreferredSize(size) }
    }

    fun getPreferredSize(): Size? = preferredSizeState

    private var boundsState: Rect? = null

    fun getBounds(): Rect = nativeImpl?.getBounds() ?: boundsState ?: Rect(0, 0, preferredSizeState?.width ?: 0, preferredSizeState?.height ?: 0)

    fun setBounds(rect: Rect) {
        boundsState = rect
        setPreferredSize(Size(rect.width, rect.height))
    }

    @get:kotlin.jvm.JvmName("obtainBoundsRect")
    @set:kotlin.jvm.JvmName("assignBoundsRect")
    var bounds: Rect
        get() = getBounds()
        set(value) = setBounds(value)

    @kotlin.jvm.JvmName("assignPadding")
    fun setPadding(padding: EdgeInsets) {
        this.padding = padding
        runOnNativeUi { it.setPadding(padding) }
        onComponentPaddingChanged()
    }

    @kotlin.jvm.JvmName("assignMargin")
    fun setMargin(margin: EdgeInsets) {
        this.margin = margin
        runOnNativeUi { it.setMargin(margin) }
    }

    open fun applyComputedStyle(style: ComputedStyle) {
        val previous = computedStyleState
        if ((previous === style || previous == style) && lastAppliedEnabledForComputedStyle == enabledState) {
            computedStyleState = style
            return
        }
        computedStyleState = style
        lastAppliedEnabledForComputedStyle = enabledState
        val effectiveForState = if (!enabledState && style.disabledStyle != null) {
            style.mergeWith(style.disabledStyle)
        } else {
            style
        }
        opacityState = effectiveForState.opacity.coerceIn(0f, 1f)
        when {
            style.transitions != null -> transitionController.updateTransitionSpecs(style.transitions)
            style.transition != null -> transitionController.updateTransitionCss(style.transition)
            previous?.transitions != null || previous?.transition != null -> transitionController.updateTransitionSpecs(emptyList())
        }
        transitionController.onStyleChanged(previous, style)
        val softwareOpacityAnimating = transitionController.hasActiveSoftwareTransition("opacity")
        val nativeOpacity = if (softwareOpacityAnimating) transitionController.currentFrameState.opacity else opacityState
        runOnNativeUi {
            it.applyComputedStyle(style)
            it.setOpacity(nativeOpacity)
            it.setBackgroundGradient(effectiveForState.backgroundGradient)
            it.setCustomCursor(effectiveForState.customCursor)
        }
        systemCssAnimationHook?.invoke(this, previous, style, lastAppliedStyleSheet)
        if (!suppressVisualResyncOnComputedStyleChange) {
            onComputedStyleChanged(previous, style)
        }
    }

    internal open fun onComputedStyleChanged(previous: ComputedStyle?, current: ComputedStyle) {}

    internal fun applyCssAnimationFrameInternal(animatedStyle: ComputedStyle) {
        computedStyleState = animatedStyle
        val effectiveAlpha = animatedStyle.opacity.coerceIn(0f, 1f)
        opacityState = effectiveAlpha
        runOnNativeUi {
            it.applyComputedStyle(animatedStyle)
            it.setOpacity(effectiveAlpha)
            it.setBackgroundGradient(animatedStyle.backgroundGradient)
            it.setCustomCursor(animatedStyle.customCursor)
        }
        onComponentOpacityChanged()
        notifyParentCacheDirty()
    }

    val computedStyle: ComputedStyle?
        get() = computedStyleState

    var customBackgroundColor: RgbaColor?
        get() = computedStyleState?.backgroundColor
        set(value) {
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setBackgroundColor(value)
            } finally {
                suppressComponentStyleCallback = false
            }
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    backgroundColor = value
                )
            )
            runOnNativeUi { it.applyComponentStyle(effectiveComponentStyle()) }
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
                    backgroundGradient = value
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
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setColor(value)
            } finally {
                suppressComponentStyleCallback = false
            }
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    textColor = value
                )
            )
            runOnNativeUi { it.applyComponentStyle(effectiveComponentStyle()) }
        }

    var borderColor: RgbaColor?
        get() = computedStyleState?.borderColor
        set(value) {
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setBorderColor(value)
            } finally {
                suppressComponentStyleCallback = false
            }
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    borderColor = value
                )
            )
            runOnNativeUi { it.applyComponentStyle(effectiveComponentStyle()) }
        }

    var borderRadiusPx: Float?
        get() = computedStyleState?.borderRadiusPx
        set(value) {
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setBorderRadius(value?.let { com.nexustools.genericui.style.css.CssCornerRadii.px(it) })
            } finally {
                suppressComponentStyleCallback = false
            }
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    borderRadiusPx = value,
                    borderRadius = value?.let { com.nexustools.genericui.style.css.CssCornerRadii.px(it) }
                )
            )
            runOnNativeUi { it.applyComponentStyle(effectiveComponentStyle()) }
        }

    open var backgroundImageUrl: String?
        get() = computedStyleState?.backgroundImageUrl
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    backgroundImageUrl = value
                )
            )
        }

    var textShadow: String?
        get() = computedStyleState?.textShadow
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    textShadow = value
                )
            )
        }

    var boxShadow: String?
        get() = computedStyleState?.boxShadow
        set(value) {
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    boxShadow = value
                )
            )
        }

    var transform: String?
        get() = computedStyleState?.transform
        set(value) {
            val parsed = com.nexustools.genericui.style.transition.CssTransform2D.parse(value)
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setTransform(parsed)
            } finally {
                suppressComponentStyleCallback = false
            }
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    transform = value,
                    transform2D = parsed
                )
            )
            runOnNativeUi { it.applyComponentStyle(effectiveComponentStyle()) }
        }

    var transition: String?
        get() = computedStyleState?.transition
        set(value) {
            val parsed = value?.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
                ?.let { com.nexustools.genericui.style.transition.CssTransitionSpec.parseList(it) }
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setTransitions(parsed)
            } finally {
                suppressComponentStyleCallback = false
            }
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    transition = value,
                    transitions = parsed
                )
            )
            runOnNativeUi { it.applyComponentStyle(effectiveComponentStyle()) }
        }

    var zIndex: Int?
        get() = computedStyleState?.zIndex
        set(value) {
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setZIndex(value)
            } finally {
                suppressComponentStyleCallback = false
            }
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    zIndex = value
                )
            )
            runOnNativeUi { it.applyComponentStyle(effectiveComponentStyle()) }
        }

    var aspectRatio: Float?
        get() = computedStyleState?.aspectRatio
        set(value) {
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setAspectRatio(value?.let { com.nexustools.genericui.style.css.CssAspectRatio(it, 1f) })
            } finally {
                suppressComponentStyleCallback = false
            }
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    aspectRatio = value
                )
            )
            runOnNativeUi { it.applyComponentStyle(effectiveComponentStyle()) }
        }

    var pointerEventsEnabled: Boolean
        get() = if (computedStyleState != null) resolveEffectiveStyle().pointerEventsEnabled else true
        set(value) {
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setPointerEvents(
                    if (value) com.nexustools.genericui.enums.css.CssPointerEvents.AUTO
                    else com.nexustools.genericui.enums.css.CssPointerEvents.NONE
                )
            } finally {
                suppressComponentStyleCallback = false
            }
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    pointerEventsEnabled = value
                )
            )
            runOnNativeUi { it.applyComponentStyle(effectiveComponentStyle()) }
        }

    var cursor: String?
        get() = computedStyleState?.cursor ?: componentStyleInstance.cursor?.toCssString()
        set(value) {
            val mappedCursor = com.nexustools.genericui.enums.css.CssCursor.fromCss(value)
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setCursor(mappedCursor)
            } finally {
                suppressComponentStyleCallback = false
            }
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    cursor = value,
                    customCursor = mappedCursor
                )
            )
            nativeImpl?.applyComponentStyle(effectiveComponentStyle())
            nativeImpl?.setCustomCursor(mappedCursor)
        }

    var leftOffset: String?
        get() = computedStyleState?.left
        set(value) {
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setLeft(CssLength.parse(value))
            } finally {
                suppressComponentStyleCallback = false
            }
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    left = value
                )
            )
            runOnNativeUi { it.applyComponentStyle(effectiveComponentStyle()) }
        }

    var topOffset: String?
        get() = computedStyleState?.top
        set(value) {
            suppressComponentStyleCallback = true
            try {
                componentStyleInstance.setTop(CssLength.parse(value))
            } finally {
                suppressComponentStyleCallback = false
            }
            val base = computedStyleState ?: ComputedStyle()
            applyComputedStyle(
                base.copy(
                    top = value
                )
            )
            runOnNativeUi { it.applyComponentStyle(effectiveComponentStyle()) }
        }

    private fun resolveEffectiveStyleSheetForSelf(styleSheet: CssStyleSheet): CssStyleSheet {
        val localScoped = scopedStyleSheet
        return if (localScoped != null && localScoped !== styleSheet) {
            CssStyleSheet(
                rules = styleSheet.rules + localScoped.rules,
                keyframes = styleSheet.keyframes + localScoped.keyframes,
                isPrecompiled = false
            )
        } else {
            styleSheet
        }
    }

    private fun resolveActiveStyleBackendTarget(activeFactory: NativeFactory?): BackendTarget =
        com.nexustools.genericui.style.css.BackendStyleMask.resolveRuntimeBackendTarget(
            com.nexustools.genericui.component.shell.Application.current?.backendTarget,
            activeFactory?.backendTarget
        )

    private fun applyStyleSheetSelfResolved(
        effectiveSheet: CssStyleSheet,
        viewportWidthPx: Int,
        mediaBucket: Int,
        isDark: Boolean,
        activeFactory: NativeFactory?
    ) {
        lastAppliedStyleSheet = effectiveSheet
        lastStyleSheetViewportWidthPx = viewportWidthPx
        lastStyleSheetMediaBucket = mediaBucket
        lastStyleSheetDarkTheme = isDark
        lastStyleSheetBackendTarget = resolveActiveStyleBackendTarget(activeFactory)
        val rawResolved = effectiveSheet.resolveSheetStyleForComponent(this, viewportWidthPx = viewportWidthPx)
        cachedSheetBaseStyle = rawResolved
        val filterSystem = shouldFilterAestheticStylesForSystemBackend(activeFactory)
        val resolved = if (filterSystem) {
            rawResolved.copyFilteredForSystemComponent(
                allowTypography = acceptsTypographyComponentStyles,
                allowAestheticSkin = acceptsAestheticSkinComponentStyles,
                allowFilter = acceptsCssFilterComponentStyles
            )
        } else {
            rawResolved
        }
        val effectiveStyle = effectiveComponentStyle(filterSystem)
        val effective = if (effectiveStyle.isEmpty()) {
            resolved
        } else {
            effectiveStyle.toComputedStyle(resolved)
        }
        applyComputedStyle(effective)
    }

    private fun applyStyleSheetSelfOnly(styleSheet: CssStyleSheet, viewportWidthPx: Int = 1280): CssStyleSheet? {
        val activeFactory = resolveActiveFactory() ?: GenericUI.activeFactory
        if (activeFactory != null && !activeFactory.isStyleSheetEngineEnabled) return null
        val effectiveSheet = resolveEffectiveStyleSheetForSelf(styleSheet)
        val mediaBucket = effectiveSheet.resolveMediaQueryBucket(viewportWidthPx)
        val isDark = com.nexustools.genericui.component.shell.Application.current?.getResolvedTheme() ==
            com.nexustools.genericui.enums.ui.ThemeMode.DARK
        val activeBackend = resolveActiveStyleBackendTarget(activeFactory)
        if (lastAppliedStyleSheet !== effectiveSheet ||
            cachedSheetBaseStyle == null ||
            lastStyleSheetDarkTheme != isDark ||
            lastStyleSheetBackendTarget != activeBackend ||
            (lastStyleSheetMediaBucket != mediaBucket && cachedSheetHasMediaRules)
        ) {
            applyStyleSheetSelfResolved(effectiveSheet, viewportWidthPx, mediaBucket, isDark, activeFactory)
        } else {
            lastStyleSheetViewportWidthPx = viewportWidthPx
            lastStyleSheetMediaBucket = mediaBucket
        }
        return effectiveSheet
    }

    private fun applyStyleSheetForInvalidatedDescendants(
        styleSheet: CssStyleSheet,
        viewportWidthPx: Int,
        wholeSubtree: Boolean,
        targetClasses: Set<String>,
        targetIds: Set<String>,
        targetTags: Set<String>
    ) {
        val effectiveSheet = applyStyleSheetSelfOnly(styleSheet, viewportWidthPx) ?: return
        walkDescendantsForInvalidatedStyleSheet(
            effectiveSheet = effectiveSheet,
            viewportWidthPx = viewportWidthPx,
            wholeSubtree = wholeSubtree,
            targetClasses = targetClasses,
            targetIds = targetIds,
            targetTags = targetTags
        )
    }

    private fun walkDescendantsForInvalidatedStyleSheet(
        effectiveSheet: CssStyleSheet,
        viewportWidthPx: Int,
        wholeSubtree: Boolean,
        targetClasses: Set<String>,
        targetIds: Set<String>,
        targetTags: Set<String>
    ) {
        if (this is AbstractContainer) {
            var i = 0
            while (i < children.size) {
                children.getOrNull(i)?.applyStyleSheetIfInvalidated(
                    styleSheet = effectiveSheet,
                    viewportWidthPx = viewportWidthPx,
                    wholeSubtree = wholeSubtree,
                    targetClasses = targetClasses,
                    targetIds = targetIds,
                    targetTags = targetTags
                )
                i++
            }
        }
        if (this is Composite) {
            val compChildren = internalCompositeChildren()
            var i = 0
            while (i < compChildren.size) {
                compChildren.getOrNull(i)?.applyStyleSheetIfInvalidated(
                    styleSheet = effectiveSheet,
                    viewportWidthPx = viewportWidthPx,
                    wholeSubtree = wholeSubtree,
                    targetClasses = targetClasses,
                    targetIds = targetIds,
                    targetTags = targetTags
                )
                i++
            }
        }
    }

    private fun applyStyleSheetIfInvalidated(
        styleSheet: CssStyleSheet,
        viewportWidthPx: Int,
        wholeSubtree: Boolean,
        targetClasses: Set<String>,
        targetIds: Set<String>,
        targetTags: Set<String>
    ) {
        val nextEffectiveSheet = if (wholeSubtree || cachedSheetBaseStyle == null || matchesInvalidationTargets(targetClasses, targetIds, targetTags)) {
            applyStyleSheetSelfOnly(styleSheet, viewportWidthPx) ?: return
        } else {
            val activeFactory = resolveActiveFactory() ?: GenericUI.activeFactory
            if (activeFactory != null && !activeFactory.isStyleSheetEngineEnabled) return
            resolveEffectiveStyleSheetForSelf(styleSheet)
        }
        walkDescendantsForInvalidatedStyleSheet(
            effectiveSheet = nextEffectiveSheet,
            viewportWidthPx = viewportWidthPx,
            wholeSubtree = wholeSubtree,
            targetClasses = targetClasses,
            targetIds = targetIds,
            targetTags = targetTags
        )
    }

    internal open fun applyStyleSheetWalk(
        styleSheet: CssStyleSheet,
        viewportWidthPx: Int,
        walkSheet: CssStyleSheet,
        activeFactory: NativeFactory?,
        isDark: Boolean,
        mediaBucket: Int,
        refOldWidthPx: Int,
        mediaMode: Int,
        targetClasses: Set<String>,
        targetIds: Set<String>,
        targetTags: Set<String>,
        targetAncestorClasses: Set<String> = emptySet(),
        targetAncestorIds: Set<String> = emptySet(),
        targetAncestorTags: Set<String> = emptySet(),
        inInvalidatedAncestorSubtree: Boolean = false
    ) {
        if (scopedStyleSheet == null && styleSheet === walkSheet) {
            val activeBackend = resolveActiveStyleBackendTarget(activeFactory)
            if (lastAppliedStyleSheet === walkSheet &&
                cachedSheetBaseStyle != null &&
                lastStyleSheetDarkTheme == isDark &&
                lastStyleSheetBackendTarget == activeBackend
            ) {
                if (lastStyleSheetMediaBucket == mediaBucket) {
                    lastStyleSheetViewportWidthPx = viewportWidthPx
                } else if (lastStyleSheetViewportWidthPx == refOldWidthPx &&
                    mediaMode != CssStyleSheet.DESCENDANT_INVALIDATION_WHOLE_SUBTREE
                ) {
                    if (mediaMode == CssStyleSheet.DESCENDANT_INVALIDATION_NONE ||
                        (!inInvalidatedAncestorSubtree && !matchesInvalidationTargets(targetClasses, targetIds, targetTags, isDark))
                    ) {
                        lastStyleSheetViewportWidthPx = viewportWidthPx
                        lastStyleSheetMediaBucket = mediaBucket
                    } else {
                        applyStyleSheetSelfResolved(walkSheet, viewportWidthPx, mediaBucket, isDark, activeFactory)
                    }
                } else if (!cachedSheetHasMediaRules) {
                    lastStyleSheetViewportWidthPx = viewportWidthPx
                    lastStyleSheetMediaBucket = mediaBucket
                } else {
                    applyStyleSheetSelfResolved(walkSheet, viewportWidthPx, mediaBucket, isDark, activeFactory)
                }
            } else {
                applyStyleSheetSelfResolved(walkSheet, viewportWidthPx, mediaBucket, isDark, activeFactory)
            }
            val nextInInvalidatedAncestorSubtree = inInvalidatedAncestorSubtree ||
                (mediaMode == CssStyleSheet.DESCENDANT_INVALIDATION_TARGETED &&
                    matchesInvalidationTargets(targetAncestorClasses, targetAncestorIds, targetAncestorTags, isDark))
            if (this is AbstractContainer) {
                var i = 0
                while (i < children.size) {
                    children.getOrNull(i)?.applyStyleSheetWalk(
                        styleSheet = walkSheet,
                        viewportWidthPx = viewportWidthPx,
                        walkSheet = walkSheet,
                        activeFactory = activeFactory,
                        isDark = isDark,
                        mediaBucket = mediaBucket,
                        refOldWidthPx = refOldWidthPx,
                        mediaMode = mediaMode,
                        targetClasses = targetClasses,
                        targetIds = targetIds,
                        targetTags = targetTags,
                        targetAncestorClasses = targetAncestorClasses,
                        targetAncestorIds = targetAncestorIds,
                        targetAncestorTags = targetAncestorTags,
                        inInvalidatedAncestorSubtree = nextInInvalidatedAncestorSubtree
                    )
                    i++
                }
            }
            if (this is Composite) {
                val compChildren = internalCompositeChildren()
                var i = 0
                while (i < compChildren.size) {
                    compChildren.getOrNull(i)?.applyStyleSheetWalk(
                        styleSheet = walkSheet,
                        viewportWidthPx = viewportWidthPx,
                        walkSheet = walkSheet,
                        activeFactory = activeFactory,
                        isDark = isDark,
                        mediaBucket = mediaBucket,
                        refOldWidthPx = refOldWidthPx,
                        mediaMode = mediaMode,
                        targetClasses = targetClasses,
                        targetIds = targetIds,
                        targetTags = targetTags,
                        targetAncestorClasses = targetAncestorClasses,
                        targetAncestorIds = targetAncestorIds,
                        targetAncestorTags = targetAncestorTags,
                        inInvalidatedAncestorSubtree = nextInInvalidatedAncestorSubtree
                    )
                    i++
                }
            }
        } else {
            val effectiveSheet = applyStyleSheetSelfOnly(styleSheet, viewportWidthPx) ?: return
            if (this is AbstractContainer) {
                for (child in children) {
                    child.applyStyleSheet(effectiveSheet, viewportWidthPx)
                }
            }
            if (this is Composite) {
                for (child in internalCompositeChildren()) {
                    child.applyStyleSheet(effectiveSheet, viewportWidthPx)
                }
            }
        }
    }

    /**
     * Resolves and applies [styleSheet] rules to this component and (if this is an [AbstractContainer]
     * or [Composite]) recursively to all child components.
     */
    open fun applyStyleSheet(styleSheet: CssStyleSheet, viewportWidthPx: Int = 1280) {
        val activeFactory = resolveActiveFactory() ?: GenericUI.activeFactory
        if (activeFactory != null && !activeFactory.isStyleSheetEngineEnabled) return
        val isDark = com.nexustools.genericui.component.shell.Application.current?.getResolvedTheme() ==
            com.nexustools.genericui.enums.ui.ThemeMode.DARK
        val mediaBucket = styleSheet.resolveMediaQueryBucket(viewportWidthPx)
        val activeBackend = resolveActiveStyleBackendTarget(activeFactory)
        val refOldWidthPx = if (lastAppliedStyleSheet === styleSheet &&
            lastStyleSheetDarkTheme == isDark &&
            lastStyleSheetBackendTarget == activeBackend
        ) {
            lastStyleSheetViewportWidthPx
        } else {
            -1
        }
        val targetClasses = linkedSetOf<String>()
        val targetIds = linkedSetOf<String>()
        val targetTags = linkedSetOf<String>()
        val targetAncestorClasses = linkedSetOf<String>()
        val targetAncestorIds = linkedSetOf<String>()
        val targetAncestorTags = linkedSetOf<String>()
        val mediaMode = if (refOldWidthPx > 0 && refOldWidthPx != viewportWidthPx && lastStyleSheetMediaBucket != mediaBucket) {
            styleSheet.collectMediaInvalidation(
                oldViewportWidthPx = refOldWidthPx,
                newViewportWidthPx = viewportWidthPx,
                outSubjectClasses = targetClasses,
                outSubjectIds = targetIds,
                outSubjectTags = targetTags,
                outAncestorClasses = targetAncestorClasses,
                outAncestorIds = targetAncestorIds,
                outAncestorTags = targetAncestorTags
            )
        } else {
            CssStyleSheet.DESCENDANT_INVALIDATION_NONE
        }
        applyStyleSheetWalk(
            styleSheet = styleSheet,
            viewportWidthPx = viewportWidthPx,
            walkSheet = styleSheet,
            activeFactory = activeFactory,
            isDark = isDark,
            mediaBucket = mediaBucket,
            refOldWidthPx = refOldWidthPx,
            mediaMode = mediaMode,
            targetClasses = targetClasses,
            targetIds = targetIds,
            targetTags = targetTags,
            targetAncestorClasses = targetAncestorClasses,
            targetAncestorIds = targetAncestorIds,
            targetAncestorTags = targetAncestorTags
        )
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
        PopupMouseCaptureManager.unregisterPopup(this)
        InputFocusManager.onComponentFocusChanged(this, false)
        clearAllTimers()
        cancelAnimations()
        transitionController.cancelAllTransitions(disposing = true)
        systemCssAnimationDisposeHook?.invoke(this)
        nativeRealizeLock.withLock {
            nativeImplInitialized = false
            nativeImpl?.dispose()
            nativeImpl = null
        }
    }

    companion object {
        private val labelTargetIdLock: PlatformReentrantLock = PlatformReentrantLock()
        private var nextLabelTargetSeq: Long = 0L

        internal fun allocateLabelTargetId(): String = labelTargetIdLock.withLock {
            nextLabelTargetSeq += 1L
            "genericui-label-target-$nextLabelTargetSeq"
        }

        internal var systemCssAnimationHook: ((
            component: AbstractComponent,
            previous: ComputedStyle?,
            current: ComputedStyle,
            sheet: CssStyleSheet?
        ) -> Unit)? = null

        internal var systemCssAnimationDisposeHook: ((component: AbstractComponent) -> Unit)? = null
    }
}
