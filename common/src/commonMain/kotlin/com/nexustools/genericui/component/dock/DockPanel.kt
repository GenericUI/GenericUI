package com.nexustools.genericui.component.dock

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl

/**
 * Detachable, collapsible dock panel with custom header actions.
 *
 * Supports docking into a [DockArea] / [DockGroup] or detaching into a floating [DockWindow]
 * in both GIMP Single-Window Mode and Multi-Window Mode.
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

    var isCollapsed: Boolean = false
        set(value) {
            field = value
            typedNative<DockPanelNativeImpl<*>>()?.setCollapsed(value)
        }

    var isFloating: Boolean = false
        private set

    fun setFloating(floating: Boolean) {
        isFloating = floating
        typedNative<DockPanelNativeImpl<*>>()?.setFloating(floating)
    }

    var floatingBounds: Rect = Rect(120, 120, 320, 480)

    private val _headerActions = mutableListOf<DockHeaderAction>()
    val headerActions: List<DockHeaderAction> get() = _headerActions

    var onDetachHandler: ((DockPanel) -> Unit)? = null
    var onReattachHandler: ((DockPanel, DockPosition) -> Unit)? = null
    var onCloseHandler: ((DockPanel) -> Unit)? = null

    fun addHeaderAction(action: DockHeaderAction): DockPanel = apply {
        _headerActions.add(action)
        typedNative<DockPanelNativeImpl<*>>()?.setHeaderActions(_headerActions)
    }

    /**
     * Detaches this panel from its current dock area into a floating [DockWindow].
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
        impl.setFloating(isFloating)
        impl.setHeaderActions(_headerActions)
    }
}
