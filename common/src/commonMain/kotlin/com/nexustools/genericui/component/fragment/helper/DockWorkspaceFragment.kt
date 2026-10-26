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
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [Fragment] that implements GIMP / Photoshop's **Single-Window Mode**, **Multi-Window Mode**,
 * and **Overlay-Over-Background Mode** (`overlayPanelsOverBackground = true`, e.g., for `DCMap` where
 * dockable/collapsible panels float over a full-bleed Three.js 3D canvas):
 *
 * - **`StudioWindowMode.SINGLE_WINDOW_MODE`**:
 *   All open [StudioDocument]s (`Main Canvas`, isolated `🧩 Shared Object` sub-canvases) are hosted as [CloseableTab]s
 *   inside the central [TabContainer] (`dockWorkspace.centralDocumentTabs`), surrounded by the docked
 *   [ToolPalette] and [DockPanel]s / [DockGroup]s inside a single window. Individual panels can still be
 *   detached into floating utility [PanelWindow]s and re-docked at any time.
 * - **`StudioWindowMode.MULTI_WINDOW_MODE`**:
 *   Each open [StudioDocument] is hosted in its own separate native OS [Window] (`document.detachedDocumentWindow`),
 *   automatically updating [activeDocument] when any document window gains focus, while each [DockPanel] is
 *   detached into its own standalone native utility [PanelWindow]!
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

    var mobileDrawerPolicy: DockMobileDrawerPolicy
        get() = dockWorkspace.mobileDrawerPolicy
        set(value) {
            dockWorkspace.mobileDrawerPolicy = value
        }

    var compactDrawerBreakpointDp: Int
        get() = dockWorkspace.compactDrawerBreakpointDp
        set(value) {
            dockWorkspace.compactDrawerBreakpointDp = value
        }

    val isCompactDrawerMode: Boolean
        get() = dockWorkspace.isCompactDrawerMode

    val activeMobileDrawerPosition: DockPosition?
        get() = dockWorkspace.activeMobileDrawerPosition

    val activeMobileDrawerOpenFraction: Float
        get() = dockWorkspace.activeMobileDrawerOpenFraction

    fun openMobileDockDrawer(position: DockPosition): Boolean =
        dockWorkspace.openMobileDockDrawer(position)

    fun closeMobileDockDrawer(): Boolean =
        dockWorkspace.closeMobileDockDrawer()

    fun toggleMobileDockDrawer(position: DockPosition): Boolean =
        dockWorkspace.toggleMobileDockDrawer(position)

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

    private val _detachedPanelWindows = mutableListOf<PanelWindow>()
    val detachedPanelWindows: List<PanelWindow>
        get() = _detachedPanelWindows
    val detachedDockWindows: List<PanelWindow>
        get() = _detachedPanelWindows

    private var documentMenuBarFactory: ((StudioDocument?) -> MenuBar)? = null
    private var documentTitleBarFactory: ((StudioDocument?) -> TitleBar)? = null
    private val activeDocListeners = mutableListOf<(StudioDocument?) -> Unit>()
    private val windowModeListeners = mutableListOf<(StudioWindowMode) -> Unit>()

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        addChild(dockWorkspace)
        syncAttachedPanelWindowsToMainWindow()
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

    override fun onResume() {
        super.onResume()
        syncAttachedPanelWindowsToMainWindow()
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        dockWorkspace.updateViewportMetrics(viewportWidth, viewportHeight)
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        dockWorkspace.applyWindowConfiguration(newConfig)
    }

    override fun onBackPressed(): Boolean {
        if (dockWorkspace.isCompactDrawerMode && dockWorkspace.activeMobileDrawerPosition != null) {
            dockWorkspace.closeMobileDockDrawer()
            return true
        }
        return super.onBackPressed()
    }

    fun syncAttachedPanelWindowsToMainWindow() {
        val mainWin = attachedWindow ?: dockWorkspace.findOwnerWindow() ?: return
        dockWorkspace.syncFloatingWindowsWithOwnerWindow(mainWin)
        for (panelWin in _detachedPanelWindows) {
            panelWin.attachToMainWindow(mainWin)
        }
        mainWin.bringAttachedPanelWindowsToFront()
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
        dockWorkspace.windowMode = targetMode

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
        if (windowMode != StudioWindowMode.SINGLE_WINDOW_MODE && _documents.size > 1) {
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

    fun detachDockPanel(panel: DockPanel, bounds: Rect = Rect(100, 100, 320, 480)): PanelWindow {
        (panel.parent as? com.nexustools.genericui.core.AbstractContainer)?.removeChild(panel)
        panel.setFloating(true)
        val mainWin = attachedWindow ?: dockWorkspace.findOwnerWindow()
        val panelWin = PanelWindow(
            title = panel.title,
            panel = panel,
            initialBounds = bounds,
            ownerWindow = mainWin
        )
        panel.getTitleBar()?.let { panelWin.setTitleBar(it) }
        if (panel.parent != panelWin) {
            panelWin.addChild(panel)
        }
        panelWin.setOnCloseRequested {
            reattachPanelWindow(panelWin, panel.preferredDockPosition)
        }
        _detachedPanelWindows.add(panelWin)
        resolveActiveFactory()?.let { panelWin.show(it) }
        mainWin?.bringAttachedPanelWindowsToFront()
        dockWorkspace.syncCompactDrawerLayout()
        return panelWin
    }

    fun reattachPanelWindow(panelWindow: PanelWindow, position: DockPosition = DockPosition.RIGHT) {
        _detachedPanelWindows.remove(panelWindow)
        val childrenCopy = panelWindow.children.toList()
        panelWindow.clearChildren()
        panelWindow.hide()
        for (child in childrenCopy) {
            when (child) {
                is DockPanel -> {
                    child.setFloating(false)
                    dockWorkspace.addDockPanel(child, position)
                }
                else -> {
                    dockWorkspace.getDockArea(position).addChild(child)
                }
            }
        }
        dockWorkspace.syncCompactDrawerLayout()
    }

    fun reattachDockWindow(panelWindow: PanelWindow, position: DockPosition = DockPosition.RIGHT) =
        reattachPanelWindow(panelWindow, position)

    private fun migrateToMultiWindowMode() {
        dockWorkspace.windowMode = StudioWindowMode.MULTI_WINDOW_MODE
        syncAttachedPanelWindowsToMainWindow()
        for ((idx, doc) in _documents.withIndex()) {
            if (idx > 0 && doc.detachedDocumentWindow == null) {
                spawnDocumentWindow(doc)
            }
        }
    }

    private fun migrateToSingleWindowMode() {
        dockWorkspace.windowMode = StudioWindowMode.SINGLE_WINDOW_MODE
        for (panelWin in _detachedPanelWindows.toList()) {
            val firstPanel = panelWin.children.filterIsInstance<DockPanel>().firstOrNull() ?: panelWin.panel
            reattachPanelWindow(panelWin, firstPanel?.preferredDockPosition ?: DockPosition.RIGHT)
        }
        for (doc in _documents) {
            doc.detachedDocumentWindow?.removeChild(doc.content)
            doc.detachedDocumentWindow?.close()
            doc.detachedDocumentWindow = null
            if (documentTabContainer.tabs.none { it.tabId == doc.documentId }) {
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
