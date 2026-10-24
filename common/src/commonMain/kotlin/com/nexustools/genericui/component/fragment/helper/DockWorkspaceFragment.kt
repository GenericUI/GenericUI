package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.dock.*
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.shell.MenuBar
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.tabs.CloseableTab
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.enums.dock.StudioWindowMode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.window.TitleBar

/**
 * Helper [Fragment] that implements GIMP / Photoshop's **Single-Window Mode**, **Multi-Window Mode**,
 * and **Overlay-Over-Background Mode** (`overlayPanelsOverBackground = true`, e.g., for `DCMap` where
 * dockable/collapsible panels float over a full-bleed Three.js 3D canvas):
 *
 * - **`StudioWindowMode.SINGLE_WINDOW_MODE`**:
 *   All open [StudioDocument]s (`Main Canvas`, isolated `🧩 Shared Object` sub-canvases) are hosted as [CloseableTab]s
 *   inside the central [TabContainer] (`dockWorkspace.centralDocumentTabs`), surrounded by the docked
 *   [ToolPalette] and [DockPanel]s / [DockGroup]s inside a single window. Individual panels/groups can still be
 *   detached into floating utility [DockWindow]s and re-docked at any time.
 * - **`StudioWindowMode.MULTI_WINDOW_MODE`**:
 *   Each open [StudioDocument] is hosted in its own separate native OS [Window] (`document.detachedDocumentWindow`),
 *   automatically updating [activeDocument] when any document window gains focus, while the [ToolPalette] and
 *   [DockArea]s / [DockGroup]s are detached into standalone native utility [DockWindow]s!
 * - **Overlay Mode ([setBackgroundComponent] / [overlayPanelsOverBackground])**:
 *   Mounts a full-viewport [backgroundComponent] (such as a Three.js 3D `GLCanvas`/`VKCanvas` or 2D viewport)
 *   as the base layer and positions all [DockArea]s and [DockPanel]s on top of it (`z-index: 10`), passing
 *   pointer/wheel events through uncovered regions directly to the background component.
 */
open class DockWorkspaceFragment(
    initialMode: StudioWindowMode = StudioWindowMode.SINGLE_WINDOW_MODE,
    initialBackgroundComponent: AbstractComponent? = null,
    overlayPanelsOverBackground: Boolean = initialBackgroundComponent != null
) : Fragment() {

    val dockWorkspace: DockWorkspace = DockWorkspace(
        windowMode = initialMode,
        overlayMode = overlayPanelsOverBackground
    )
    val documentTabContainer: TabContainer
        get() = dockWorkspace.centralDocumentTabs

    val backgroundComponent: AbstractComponent?
        get() = dockWorkspace.backgroundComponent

    var isOverlayMode: Boolean
        get() = dockWorkspace.overlayMode
        set(value) {
            dockWorkspace.overlayMode = value
        }

    init {
        if (initialBackgroundComponent != null) {
            dockWorkspace.setBackgroundComponent(initialBackgroundComponent, overlayPanelsOverBackground)
        }
    }

    fun setBackgroundComponent(
        component: AbstractComponent?,
        enableOverlay: Boolean = component != null
    ): DockWorkspaceFragment = apply {
        dockWorkspace.setBackgroundComponent(component, enableOverlay)
    }

    fun addOverlayPanel(
        panel: DockPanel,
        position: DockPosition = DockPosition.LEFT,
        xOffsetPx: Int? = null,
        yOffsetPx: Int? = null
    ): DockWorkspaceFragment = apply {
        dockWorkspace.overlayMode = true
        if (xOffsetPx != null) panel.leftOffset = "${xOffsetPx}px"
        if (yOffsetPx != null) panel.topOffset = "${yOffsetPx}px"
        panel.zIndex = 20
        addDockPanel(panel, position)
    }

    var windowMode: StudioWindowMode = initialMode
        private set

    private val _documents = mutableListOf<StudioDocument>()
    val documents: List<StudioDocument>
        get() = _documents

    var activeDocument: StudioDocument? = null
        private set

    private val _detachedDockWindows = mutableListOf<DockWindow>()
    val detachedDockWindows: List<DockWindow>
        get() = _detachedDockWindows

    private var documentMenuBarFactory: ((StudioDocument?) -> MenuBar)? = null
    private var documentTitleBarFactory: ((StudioDocument?) -> TitleBar)? = null
    private val activeDocListeners = mutableListOf<(StudioDocument?) -> Unit>()
    private val windowModeListeners = mutableListOf<(StudioWindowMode) -> Unit>()

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        addChild(dockWorkspace)
        documentTabContainer.setOnTabSelected { tabId ->
            val matched = _documents.firstOrNull { it.documentId == tabId }
            if (matched != null && matched !== activeDocument) {
                setActiveStudioDocument(matched)
            }
        }
        documentTabContainer.setOnTabCloseRequested { tabId ->
            closeDocument(tabId)
        }
    }

    val supportsMultiWindowMode: Boolean
        get() = (attachedWindow?.nativeImpl as? com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl<*>)?.supportsMultiWindow ?: true

    fun setWindowMode(mode: StudioWindowMode) {
        val targetMode = if (mode == StudioWindowMode.MULTI_WINDOW_MODE && !supportsMultiWindowMode) {
            StudioWindowMode.SINGLE_WINDOW_MODE
        } else {
            mode
        }
        if (targetMode == windowMode) return
        windowMode = targetMode

        if (targetMode == StudioWindowMode.MULTI_WINDOW_MODE) {
            migrateToMultiWindowMode()
        } else {
            migrateToSingleWindowMode()
        }
        windowModeListeners.forEach { it(windowMode) }
    }

    fun toggleWindowMode() {
        setWindowMode(
            if (windowMode == StudioWindowMode.SINGLE_WINDOW_MODE) {
                StudioWindowMode.MULTI_WINDOW_MODE
            } else {
                StudioWindowMode.SINGLE_WINDOW_MODE
            }
        )
    }

    fun openDocument(document: StudioDocument, activate: Boolean = true) {
        if (_documents.any { it.documentId == document.documentId }) {
            if (activate) activateDocument(document.documentId)
            return
        }
        _documents.add(document)
        document.onMetadataChanged = { doc ->
            if (windowMode == StudioWindowMode.SINGLE_WINDOW_MODE) {
                documentTabContainer.updateTabTitle(doc.documentId, doc.displayTitle, doc.icon)
            } else {
                doc.detachedDocumentWindow?.setTitle(doc.displayTitle)
            }
        }

        if (windowMode == StudioWindowMode.SINGLE_WINDOW_MODE) {
            documentTabContainer.addTab(
                tab = CloseableTab(
                    tabId = document.documentId,
                    title = document.title,
                    icon = document.icon,
                    isCloseable = document.isClosable,
                    isDirty = document.isDirty
                ),
                content = document.content
            )
        } else {
            spawnDocumentWindow(document)
        }

        if (activate || activeDocument == null) {
            activateDocument(document.documentId)
        }
    }

    fun closeDocument(documentId: String): Boolean {
        val doc = _documents.firstOrNull { it.documentId == documentId } ?: return false
        val allowed = doc.onCloseRequested?.invoke() ?: true
        if (!allowed) return false

        _documents.remove(doc)
        if (windowMode == StudioWindowMode.SINGLE_WINDOW_MODE) {
            documentTabContainer.removeTab(doc.documentId)
        } else {
            doc.detachedDocumentWindow?.close()
            doc.detachedDocumentWindow = null
        }

        if (activeDocument === doc) {
            setActiveStudioDocument(_documents.lastOrNull())
        }
        return true
    }

    fun activateDocument(documentId: String) {
        val doc = _documents.firstOrNull { it.documentId == documentId } ?: return
        if (windowMode == StudioWindowMode.SINGLE_WINDOW_MODE) {
            documentTabContainer.setActiveTab(documentId)
        }
        setActiveStudioDocument(doc)
    }

    private fun setActiveStudioDocument(doc: StudioDocument?) {
        activeDocument = doc
        activeDocListeners.forEach { it(doc) }
    }

    fun addOnActiveDocumentChangedListener(listener: (StudioDocument?) -> Unit) {
        activeDocListeners.add(listener)
    }

    fun addOnWindowModeChangedListener(listener: (StudioWindowMode) -> Unit) {
        windowModeListeners.add(listener)
    }

    fun setDocumentMenuBarFactory(factory: (StudioDocument?) -> MenuBar) {
        documentMenuBarFactory = factory
    }

    fun setDocumentTitleBarFactory(factory: (StudioDocument?) -> TitleBar) {
        documentTitleBarFactory = factory
    }

    fun setToolPalette(palette: ToolPalette) {
        dockWorkspace.setToolPalette(palette)
    }

    fun addDockPanel(panel: DockPanel, position: DockPosition, groupId: String? = null) {
        dockWorkspace.addDockPanel(panel, position, groupId)
    }

    fun detachDockPanel(panel: DockPanel, bounds: Rect = Rect(100, 100, 320, 480)): DockWindow {
        (panel.parent as? com.nexustools.genericui.core.AbstractContainer)?.removeChild(panel)
        panel.setFloating(true)
        val dockWin = DockWindow(title = panel.title, initialBounds = bounds)
        dockWin.addChild(panel)
        dockWin.setOnCloseRequested {
            reattachDockWindow(dockWin, panel.preferredDockPosition)
        }
        _detachedDockWindows.add(dockWin)
        resolveActiveFactory()?.let { dockWin.show(it) }
        return dockWin
    }

    fun detachDockArea(position: DockPosition, bounds: Rect = Rect(80, 80, 340, 640)): DockWindow {
        val area = dockWorkspace.getDockArea(position)
        dockWorkspace.removeChild(area)
        val dockWin = DockWindow(title = "${position.name} Dock", initialBounds = bounds)
        dockWin.addChild(area)
        dockWin.setOnCloseRequested {
            _detachedDockWindows.remove(dockWin)
            dockWin.removeChild(area)
            dockWin.hide()
            dockWorkspace.attachDockArea(position, area)
        }
        _detachedDockWindows.add(dockWin)
        resolveActiveFactory()?.let { dockWin.show(it) }
        return dockWin
    }

    fun reattachDockWindow(dockWindow: DockWindow, position: DockPosition = DockPosition.RIGHT) {
        _detachedDockWindows.remove(dockWindow)
        val childrenCopy = dockWindow.children.toList()
        dockWindow.clearChildren()
        dockWindow.hide()
        for (child in childrenCopy) {
            when (child) {
                is DockPanel -> {
                    child.setFloating(false)
                    dockWorkspace.addDockPanel(child, position)
                }
                is DockArea -> {
                    dockWorkspace.attachDockArea(position, child)
                }
                else -> {
                    dockWorkspace.getDockArea(position).addChild(child)
                }
            }
        }
    }

    private fun migrateToMultiWindowMode() {
        for (doc in _documents) {
            documentTabContainer.removeTab(doc.documentId)
            spawnDocumentWindow(doc)
        }
        detachDockArea(DockPosition.LEFT, Rect(20, 80, 110, 680))
        detachDockArea(DockPosition.RIGHT, Rect(1120, 80, 380, 720))
    }

    private fun migrateToSingleWindowMode() {
        for (dockWin in _detachedDockWindows.toList()) {
            val firstArea = dockWin.children.filterIsInstance<DockArea>().firstOrNull()
            reattachDockWindow(dockWin, firstArea?.position ?: DockPosition.RIGHT)
        }
        for (doc in _documents) {
            doc.detachedDocumentWindow?.removeChild(doc.content)
            doc.detachedDocumentWindow?.close()
            doc.detachedDocumentWindow = null
            documentTabContainer.addTab(
                tab = CloseableTab(
                    tabId = doc.documentId,
                    title = doc.title,
                    icon = doc.icon,
                    isCloseable = doc.isClosable,
                    isDirty = doc.isDirty
                ),
                content = doc.content
            )
        }
        activeDocument?.let { documentTabContainer.setActiveTab(it.documentId) }
    }

    private fun spawnDocumentWindow(doc: StudioDocument) {
        val win = Window(title = doc.displayTitle)
        documentTitleBarFactory?.invoke(doc)?.let { win.setTitleBar(it) }
        documentMenuBarFactory?.invoke(doc)?.let { win.setMenuBar(it) }
        val containerContent = (doc.content as? com.nexustools.genericui.component.layout.Container)
            ?: com.nexustools.genericui.component.layout.Container().apply { addChild(doc.content) }
        win.setContent(containerContent)
        win.setOnWindowFocusChanged { focused ->
            if (focused) {
                setActiveStudioDocument(doc)
            }
        }
        win.setOnWindowCloseRequested {
            closeDocument(doc.documentId)
        }
        doc.detachedDocumentWindow = win
        resolveActiveFactory()?.let { win.show(it) }
    }
}
