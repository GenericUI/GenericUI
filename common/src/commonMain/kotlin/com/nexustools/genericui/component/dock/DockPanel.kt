package com.nexustools.genericui.component.dock

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl
import com.nexustools.genericui.window.TitleBar

/**
 * Detachable, collapsible dock panel with custom header actions and its own [TitleBar].
 *
 * Supports docking into a [DockArea] / [DockGroup] or detaching into a standalone [PanelWindow]
 * in both Single-Window Mode and Multi-Window Mode.
 */
open class DockPanel(
    val panelId: String,
    title: String = panelId,
    var preferredPosition: DockPosition = DockPosition.RIGHT
) : AbstractContainer() {
    var preferredDockPosition: DockPosition
        get() = preferredPosition
        set(value) { preferredPosition = value }

    var title: String = title
        set(value) {
            field = value
            typedNative<DockPanelNativeImpl<*>>()?.setTitle(value)
        }

    var iconSvgOrPath: String? = null
        set(value) {
            field = value
            typedNative<DockPanelNativeImpl<*>>()?.setIcon(value)
        }

    var isClosable: Boolean = true
    var isFloatable: Boolean = true
    var isCollapsible: Boolean = true

    private var isDispatchingCollapseChange: Boolean = false

    var isCollapsed: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!isDispatchingCollapseChange) {
                isDispatchingCollapseChange = true
                try {
                    typedNative<DockPanelNativeImpl<*>>()?.setCollapsed(value)
                    onCollapsedChanged?.invoke(value)
                } finally {
                    isDispatchingCollapseChange = false
                }
            }
        }

    var onCollapsedChanged: ((Boolean) -> Unit)? = null

    fun toggleCollapsed(): Boolean {
        if (!isCollapsible) return isCollapsed
        isCollapsed = !isCollapsed
        return isCollapsed
    }

    var isFloating: Boolean = false
        private set

    fun setFloating(floating: Boolean) {
        isFloating = floating
        typedNative<DockPanelNativeImpl<*>>()?.setFloating(floating)
    }

    var floatingBounds: Rect = Rect(120, 120, 320, 480)

    var panelWindow: PanelWindow? = null
        internal set

    private var titleBarInstructions: TitleBar? = TitleBar(customTitleBar = false)

    private val _headerActions = mutableListOf<DockHeaderAction>()
    val headerActions: List<DockHeaderAction> get() = _headerActions

    var onDetachHandler: ((DockPanel) -> Unit)? = null
    var onReattachHandler: ((DockPanel, DockPosition) -> Unit)? = null
    var onCloseHandler: ((DockPanel) -> Unit)? = null

    init {
        htmlTag = "genericui:dockpanel"
        titleBarInstructions?.onInstructionsChanged = {
            syncTitleBarToNative()
        }
    }

    /**
     * Sets the [TitleBar] configuration for this panel.
     *
     * - When this panel is docked inside a [DockArea], the [TitleBar] (`leadingComponents`,
     *   `centerComponents`, `trailingComponents`) is rendered in the panel's collapsible title header bar.
     * - When this panel is detached into a standalone [PanelWindow] (e.g. in Multi-Window Mode),
     *   the [TitleBar] is automatically applied to the [PanelWindow] as its window titlebar.
     */
    fun setTitleBar(titleBar: TitleBar?) {
        titleBarInstructions?.onInstructionsChanged = null
        titleBarInstructions = titleBar
        titleBar?.onInstructionsChanged = {
            syncTitleBarToNative()
        }
        syncTitleBarToNative()
    }

    /**
     * Returns the [TitleBar] configuration for this panel.
     */
    fun getTitleBar(): TitleBar? = titleBarInstructions

    internal fun syncTitleBarToNative() {
        val tb = titleBarInstructions
        if (tb != null) {
            for (comp in tb.leadingComponents + tb.centerComponents + tb.trailingComponents) {
                if (comp.parent == null) {
                    comp.parent = this
                }
                if (comp.isVisible && resolveActiveFactory() != null) {
                    comp.ensureNativeImpl()
                }
            }
        }
        typedNative<DockPanelNativeImpl<*>>()?.applyTitleBarInstructions(tb)
        panelWindow?.setTitleBar(tb)
    }

    fun addHeaderAction(action: DockHeaderAction): DockPanel = apply {
        _headerActions.add(action)
        typedNative<DockPanelNativeImpl<*>>()?.setHeaderActions(_headerActions)
    }

    /**
     * Detaches this panel from its current dock area into a standalone [PanelWindow].
     */
    fun detachToFloatingWindow(bounds: Rect = floatingBounds) {
        if (!isFloatable || isFloating) return
        floatingBounds = bounds
        isFloating = true
        typedNative<DockPanelNativeImpl<*>>()?.setFloating(true)
        onDetachHandler?.invoke(this)
    }

    /**
     * Re-attaches a floating panel back into its dock area at [position].
     */
    fun reattachToDock(position: DockPosition = preferredPosition) {
        preferredPosition = position
        isFloating = false
        typedNative<DockPanelNativeImpl<*>>()?.setFloating(false)
        onReattachHandler?.invoke(this, position)
    }

    override fun createNativeImpl(factory: NativeFactory): DockPanelNativeImpl<*> =
        factory.createDockPanel()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<DockPanelNativeImpl<*>>() ?: return
        impl.setTitle(title)
        impl.setIcon(iconSvgOrPath)
        impl.setCollapsed(isCollapsed)
        impl.setOnCollapsedChanged { collapsed -> isCollapsed = collapsed }
        impl.setFloating(isFloating)
        impl.setHeaderActions(_headerActions)
        syncTitleBarToNative()
    }
}
