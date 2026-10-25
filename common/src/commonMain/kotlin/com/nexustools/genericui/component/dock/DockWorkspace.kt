package com.nexustools.genericui.component.dock

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.tabs.TabBarStyle
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.enums.dock.StudioWindowMode
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWorkspaceNativeImpl

/**
 * Full-featured docking workspace supporting detachable [DockPanel]s, central document tabs,
 * optional full-bleed [backgroundComponent] with overlaid dock panels (`overlayMode = true`),
 * and dynamic switching between GIMP's Single-Window Mode and Multi-Window Mode.
 */
open class DockWorkspace(
    windowMode: StudioWindowMode = StudioWindowMode.SINGLE_WINDOW,
    overlayMode: Boolean = false
) : AbstractContainer() {
    var windowMode: StudioWindowMode = windowMode
        set(value) {
            if (field != value) {
                field = value
                typedNative<DockWorkspaceNativeImpl<*>>()?.setWindowModeNative(value.name)
                applyWindowModeTransition(value)
                onWindowModeChanged?.invoke(value)
            }
        }

    var overlayMode: Boolean = overlayMode
        set(value) {
            if (field != value) {
                field = value
                syncOverlayStyling()
            }
        }

    val backgroundContainer: Container = Container("div", "dock-workspace-background").apply {
        positionMode = PositionMode.ABSOLUTE
        leftOffset = "0px"
        topOffset = "0px"
        setStyleProperty("width", "100%")
        setStyleProperty("height", "100%")
        zIndex = 0
        isVisible = false
    }

    var backgroundComponent: AbstractComponent? = null
        private set

    var leftArea: DockArea = DockArea(DockPosition.LEFT)
        private set
    var rightArea: DockArea = DockArea(DockPosition.RIGHT)
        private set
    var topArea: DockArea = DockArea(DockPosition.TOP)
        private set
    var bottomArea: DockArea = DockArea(DockPosition.BOTTOM)
        private set
    var centerArea: DockArea = DockArea(DockPosition.CENTER)
        private set

    val centralDocumentTabs: TabContainer = TabContainer(tabBarStyle = TabBarStyle.DOCUMENT_STRIP)
    var toolPalette: ToolPalette? = null
        private set

    private val _panels = mutableMapOf<String, DockPanel>()
    val panels: Map<String, DockPanel> get() = _panels

    private val _floatingWindows = mutableMapOf<String, PanelWindow>()
    val floatingWindows: Map<String, PanelWindow> get() = _floatingWindows
    val panelWindows: Map<String, PanelWindow> get() = _floatingWindows

    private val _documents = mutableListOf<StudioDocument>()
    val documents: List<StudioDocument> get() = _documents

    var activeDocumentId: String? = null
        private set

    var onWindowModeChanged: ((StudioWindowMode) -> Unit)? = null
    var onActiveDocumentChanged: ((StudioDocument?) -> Unit)? = null

    init {
        htmlTag = "genericui:dockworkspace"
        positionMode = PositionMode.RELATIVE
        addChild(backgroundContainer)
        addChild(topArea)
        addChild(leftArea)
        addChild(centerArea)
        addChild(centralDocumentTabs)
        addChild(rightArea)
        addChild(bottomArea)
        syncOverlayStyling()
    }

    /**
     * Sets a full-bleed background component (e.g., a Three.js 3D `GLCanvas` / `VKCanvas` or 2D map)
     * underneath the workspace's dock areas and enables [overlayMode] so dock panels float on top
     * of the background component while passing pointer events through uncovered regions.
     */
    fun setBackgroundComponent(component: AbstractComponent?, enableOverlay: Boolean = component != null) {
        backgroundContainer.clearChildren()
        backgroundComponent = component
        if (component != null) {
            backgroundContainer.isVisible = true
            backgroundContainer.addChild(component)
        } else {
            backgroundContainer.isVisible = false
        }
        overlayMode = enableOverlay
        syncOverlayStyling()
    }

    private fun syncOverlayStyling() {
        if (overlayMode) {
            addClass("dock-workspace-overlay")
            centralDocumentTabs.isVisible = _documents.isNotEmpty()
            for (area in listOf(topArea, leftArea, rightArea, bottomArea)) {
                area.positionMode = PositionMode.ABSOLUTE
                area.zIndex = 10
                area.pointerEventsEnabled = true
            }
            topArea.leftOffset = "0px"
            topArea.topOffset = "0px"
            leftArea.leftOffset = "0px"
            leftArea.topOffset = "48px"
            rightArea.setStyleProperty("right", "0px")
            rightArea.topOffset = "48px"
            bottomArea.leftOffset = "0px"
            bottomArea.setStyleProperty("bottom", "0px")
        } else {
            removeClass("dock-workspace-overlay")
            centralDocumentTabs.isVisible = true
            for (area in listOf(topArea, leftArea, rightArea, bottomArea)) {
                area.positionMode = PositionMode.RELATIVE
                area.zIndex = null
            }
        }
    }

    fun setToolPalette(palette: ToolPalette) {
        this.toolPalette = palette
        leftArea.addChild(palette, 0)
    }

    fun addDockPanel(panel: DockPanel, position: DockPosition, groupId: String? = null): DockWorkspace =
        registerPanel(panel, position)

    fun getDockArea(position: DockPosition): DockArea = areaForPosition(position)

    fun attachDockArea(position: DockPosition, area: DockArea) {
        when (position) {
            DockPosition.LEFT -> leftArea = area
            DockPosition.RIGHT -> rightArea = area
            DockPosition.TOP -> topArea = area
            DockPosition.BOTTOM -> bottomArea = area
            DockPosition.CENTER, DockPosition.FLOATING -> centerArea = area
        }
        addChild(area)
    }

    fun registerPanel(panel: DockPanel, position: DockPosition = panel.preferredPosition): DockWorkspace = apply {
        panel.preferredPosition = position
        _panels[panel.panelId] = panel
        panel.onDetachHandler = { detached ->
            val ownerWin = findOwnerWindow()
            val panelWin = PanelWindow(
                title = detached.title,
                panel = detached,
                initialBounds = detached.floatingBounds,
                ownerWindow = ownerWin
            )
            detached.getTitleBar()?.let { panelWin.setTitleBar(it) }
            _floatingWindows[detached.panelId] = panelWin
            ownerWin?.bringAttachedPanelWindowsToFront()
        }
        panel.onReattachHandler = { reattached, targetPos ->
            reattached.panelWindow = null
            _floatingWindows.remove(reattached.panelId)?.close()
            if (reattached.parent == null) {
                areaForPosition(targetPos).addPanel(reattached)
            }
        }
        areaForPosition(position).addPanel(panel)
        if (windowMode == StudioWindowMode.MULTI_WINDOW && position != DockPosition.CENTER) {
            panel.detachToFloatingWindow()
        }
    }

    fun findOwnerWindow(): Window? {
        var curr: AbstractComponent? = this
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

    fun syncFloatingWindowsWithOwnerWindow(explicitOwner: Window? = null) {
        val ownerWin = explicitOwner ?: findOwnerWindow() ?: return
        for (panelWin in _floatingWindows.values) {
            panelWin.attachToMainWindow(ownerWin)
        }
        ownerWin.bringAttachedPanelWindowsToFront()
    }

    fun addDocument(document: StudioDocument): DockWorkspace = apply {
        _documents.add(document)
        if (activeDocumentId == null) {
            activeDocumentId = document.id
            onActiveDocumentChanged?.invoke(document)
        }
    }

    fun removeDocument(documentId: String): Boolean {
        val removed = _documents.removeAll { it.id == documentId }
        if (removed && activeDocumentId == documentId) {
            activeDocumentId = _documents.firstOrNull()?.id
            onActiveDocumentChanged?.invoke(_documents.firstOrNull())
        }
        return removed
    }

    fun selectDocument(documentId: String) {
        val doc = _documents.firstOrNull { it.id == documentId } ?: return
        activeDocumentId = doc.id
        onActiveDocumentChanged?.invoke(doc)
    }

    fun areaForPosition(position: DockPosition): DockArea = when (position) {
        DockPosition.LEFT -> leftArea
        DockPosition.RIGHT -> rightArea
        DockPosition.TOP -> topArea
        DockPosition.BOTTOM -> bottomArea
        DockPosition.CENTER, DockPosition.FLOATING -> centerArea
    }

    fun saveLayoutState(): DockLayoutState = DockLayoutState(
        windowMode = windowMode,
        panelPositions = _panels.mapValues { (_, p) -> if (p.isFloating) DockPosition.FLOATING else p.preferredPosition },
        floatingBounds = _panels.mapValues { (_, p) -> p.floatingBounds },
        collapsedPanels = _panels.filterValues { it.isCollapsed }.keys,
        activeDocumentId = activeDocumentId
    )

    fun restoreLayoutState(state: DockLayoutState) {
        for ((id, bounds) in state.floatingBounds) {
            _panels[id]?.floatingBounds = bounds
        }
        for ((id, panel) in _panels) {
            panel.isCollapsed = id in state.collapsedPanels
        }
        windowMode = state.windowMode
        state.activeDocumentId?.let { selectDocument(it) }
    }

    private fun applyWindowModeTransition(targetMode: StudioWindowMode) {
        when (targetMode) {
            StudioWindowMode.SINGLE_WINDOW_MODE -> {
                for (panel in _panels.values) {
                    if (panel.isFloating) {
                        panel.reattachToDock(panel.preferredPosition)
                    }
                }
            }
            StudioWindowMode.MULTI_WINDOW_MODE -> {
                for (panel in _panels.values) {
                    if (panel.preferredPosition != DockPosition.CENTER && !panel.isFloating) {
                        panel.detachToFloatingWindow(panel.floatingBounds)
                    }
                }
                syncFloatingWindowsWithOwnerWindow()
            }
        }
    }

    override fun createNativeImpl(factory: NativeFactory): DockWorkspaceNativeImpl<*> =
        factory.createDockWorkspace()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<DockWorkspaceNativeImpl<*>>() ?: return
        impl.setWindowModeNative(windowMode.name)
        (leftArea.nativeImpl as? DockAreaNativeImpl<*>)?.let { impl.attachDockAreaNative(DockPosition.LEFT, it) }
        (rightArea.nativeImpl as? DockAreaNativeImpl<*>)?.let { impl.attachDockAreaNative(DockPosition.RIGHT, it) }
        (topArea.nativeImpl as? DockAreaNativeImpl<*>)?.let { impl.attachDockAreaNative(DockPosition.TOP, it) }
        (bottomArea.nativeImpl as? DockAreaNativeImpl<*>)?.let { impl.attachDockAreaNative(DockPosition.BOTTOM, it) }
        impl.setCentralDocumentNative(centerArea.nativeImpl)
    }
}
