package com.nexustools.genericui.component.dock

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities

/**
 * Tool-modal utility window hosting an individual [DockPanel] in Multi-Window Mode
 * or when torn off from a [DockWorkspace].
 *
 * Strictly attached to its owning main [Window]:
 * - Has no taskbar entry (`hasTaskbarEntry == false`) and no minimize button (`isMinimizable == false`).
 * - Always remains on top of its attached main [Window] (`isAlwaysOnTopOfOwner == true`), so focusing
 *   the main [Window] automatically brings all attached [PanelWindow]s to the front as well.
 */
class PanelWindow(
    title: String = "Panel",
    val panel: DockPanel? = null,
    val initialBounds: Rect = panel?.floatingBounds ?: Rect(120, 120, 320, 480),
    ownerWindow: Window? = null
) : Window(
    title = title,
    initialSize = Size(
        width = initialBounds.width.coerceAtLeast(240),
        height = initialBounds.height.coerceAtLeast(200)
    )
) {
    val isToolModalWindow: Boolean = true
    val hasTaskbarEntry: Boolean = false
    override val isMinimizable: Boolean = false
    override val isMaximizable: Boolean = false
    override val isToolWindow: Boolean = true
    override val isAlwaysOnTop: Boolean = true
    val isAlwaysOnTopOfOwner: Boolean = true

    var attachedMainWindow: Window? = null
        private set

    override val ownerWindow: Window?
        get() = attachedMainWindow

    override val titleCapabilities: TitleCapabilities
        get() = super.titleCapabilities.copy(
            supportsWindowIcon = false,
            supportsMinimizeButton = false,
            supportsMaximizeButton = false,
            isToolWindowTitleBar = true
        )

    init {
        htmlTag = "genericui:panelwindow"
        windowBounds = Rect(
            x = initialBounds.x,
            y = initialBounds.y,
            width = getWindowSize().width,
            height = getWindowSize().height
        )
        val initialTitleBar = (panel?.getTitleBar() ?: TitleBar(
            showWindowIcon = false,
            showMinimizeButton = false,
            showMaximizeButton = false
        )).apply {
            showWindowIcon = false
            showMinimizeButton = false
            showMaximizeButton = false
        }
        super.setTitleBar(initialTitleBar)
        if (panel != null) {
            panel.panelWindow = this
            if (panel.parent == null) {
                addChild(panel)
            }
        }
        val resolvedOwner = ownerWindow ?: findAncestorMainWindow(panel)
        if (resolvedOwner != null) {
            attachToMainWindow(resolvedOwner)
        }
    }

    override fun setTitleBar(titleBar: TitleBar?) {
        val resolved = (titleBar ?: TitleBar(
            showWindowIcon = false,
            showMinimizeButton = false,
            showMaximizeButton = false
        )).apply {
            showWindowIcon = false
            showMinimizeButton = false
            showMaximizeButton = false
        }
        super.setTitleBar(resolved)
    }

    fun attachToMainWindow(mainWindow: Window?) {
        if (attachedMainWindow === mainWindow) {
            syncToolModalAttachmentToNative()
            return
        }
        attachedMainWindow?.unregisterAttachedPanelWindow(this)
        attachedMainWindow = mainWindow
        if (mainWindow != null && (parent == null || parent is Window)) {
            parent = mainWindow
        }
        mainWindow?.registerAttachedPanelWindow(this)
        syncToolModalAttachmentToNative()
    }

    internal fun syncToolModalAttachmentToNative() {
        val impl = nativeImpl as? WindowNativeImpl<*> ?: return
        val ownerImpl = attachedMainWindow?.nativeImpl as? WindowNativeImpl<*>
        impl.configureAsToolModalWindow(
            ownerWindow = ownerImpl,
            showInTaskbar = false,
            minimizable = false,
            alwaysOnTopOfOwner = true
        )
    }

    override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        syncToolModalAttachmentToNative()
    }

    override fun resolveActiveFactory(): NativeFactory? =
        super.resolveActiveFactory()
            ?: attachedMainWindow?.resolveActiveFactory()
            ?: panel?.resolveActiveFactory()
            ?: GenericUI.activeFactory

    override fun hide() {
        isVisible = false
    }

    override fun close() {
        attachedMainWindow?.unregisterAttachedPanelWindow(this)
        attachedMainWindow = null
        super.close()
    }

    fun setOnCloseRequested(listener: () -> Unit) {
        setOnWindowCloseRequested {
            listener()
            false
        }
    }

    private fun findAncestorMainWindow(start: AbstractComponent?): Window? {
        var curr: AbstractComponent? = start
        while (curr != null) {
            if (curr is Window && curr !is PanelWindow) {
                return curr
            }
            if (curr is Fragment && curr.attachedWindow != null) {
                return curr.attachedWindow
            }
            if (curr is FragmentContainer && curr.hostWindow != null) {
                return curr.hostWindow
            }
            curr = curr.parent
        }
        return null
    }
}
