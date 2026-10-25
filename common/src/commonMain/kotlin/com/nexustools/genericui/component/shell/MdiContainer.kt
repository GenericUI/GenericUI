package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.geometry.Rect

/**
 * Multi-Document Interface (MDI) workspace container (`<genericui:mdicontainer class="mdi-container">`)
 * managing multiple movable, minimizable, maximizable, and tileable [MdiWindow] instances.
 *
 * Includes:
 * - [toolbarBar]: Built-in window management controls (`Cascade`, `Tile Grid`, `Minimize All`, `Restore All`,
 *   and [activeWindowBadge]).
 * - [desktopSurface]: Relative/clipped MDI canvas (`<genericui:mdidesktop class="mdi-desktop-surface">`)
 *   hosting positioned [MdiWindow] frames.
 * - [taskbarStrip]: Bottom taskbar (`class="mdi-taskbar"`) with interactive window buttons showing each
 *   window's state and allowing 1-click focus or restore.
 */
open class MdiContainer(
    showWindowToolbar: Boolean = true,
    showTaskbar: Boolean = true
) : Container(tagName = "genericui:mdicontainer", classPrefix = "mdi-container") {

    val cascadeButton: Button = Button("Cascade", BootstrapType.PRIMARY, BootstrapSize.SM).apply {
        id = "mdi-btn-cascade"
    }
    val tileButton: Button = Button("Tile Grid", BootstrapType.SECONDARY, BootstrapSize.SM).apply {
        id = "mdi-btn-tile"
    }
    val minimizeAllButton: Button = Button("Minimize All", BootstrapType.WARNING, BootstrapSize.SM, outline = true).apply {
        id = "mdi-btn-minimize-all"
    }
    val restoreAllButton: Button = Button("Restore All", BootstrapType.SUCCESS, BootstrapSize.SM, outline = true).apply {
        id = "mdi-btn-restore-all"
    }
    val activeWindowBadge: Badge = Badge("Active: None", BootstrapType.INFO, pill = true).apply {
        id = "mdi-badge-active-window"
    }

    val toolbarBar: HStack = HStack(gap = SpacingScale.S2).apply {
        addClass("mdi-toolbar")
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.BETWEEN
        isVisible = showWindowToolbar
    }

    val desktopSurface: Container = Container("genericui:mdidesktop", "mdi-desktop-surface").apply {
        positionMode = PositionMode.RELATIVE
        overflowBehavior = OverflowBehavior.HIDDEN
    }

    val taskbarStrip: HStack = HStack(gap = SpacingScale.S2).apply {
        addClass("mdi-taskbar")
        alignItems = AlignItems.CENTER
        flexWrap = FlexWrap.WRAP
        isVisible = showTaskbar
    }

    private val _windows = mutableListOf<MdiWindow>()
    val windows: List<MdiWindow>
        get() = _windows

    private val taskbarButtons = linkedMapOf<String, Button>()

    var activeWindow: MdiWindow? = null
        private set

    private var topZIndexCounter: Int = 100
    private var cascadeCounter: Int = 0

    var onActiveWindowChanged: ((MdiWindow?) -> Unit)? = null
    var onWindowAdded: ((MdiWindow) -> Unit)? = null
    var onWindowRemoved: ((MdiWindow) -> Unit)? = null

    init {
        htmlTag = "genericui:mdicontainer"
        displayMode = DisplayMode.FLEX
        flexDirection = FlexDirection.COLUMN

        val actionsGroup = HStack(gap = SpacingScale.S2).apply {
            alignItems = AlignItems.CENTER
            addChild(cascadeButton)
            addChild(tileButton)
            addChild(minimizeAllButton)
            addChild(restoreAllButton)
        }
        toolbarBar.addChild(actionsGroup)
        toolbarBar.addChild(activeWindowBadge)

        addChild(toolbarBar)
        addChild(desktopSurface)
        addChild(taskbarStrip)

        cascadeButton.setOnClick { cascadeWindows() }
        tileButton.setOnClick { tileWindows() }
        minimizeAllButton.setOnClick { minimizeAll() }
        restoreAllButton.setOnClick { restoreAll() }
    }

    fun nextCascadeBounds(
        startX: Int = 24,
        startY: Int = 24,
        stepX: Int = 34,
        stepY: Int = 34,
        width: Int = 480,
        height: Int = 360
    ): Rect {
        val idx = cascadeCounter++
        val offset = idx % 8
        return Rect(
            x = startX + offset * stepX,
            y = startY + offset * stepY,
            width = width,
            height = height
        )
    }

    fun addWindow(window: MdiWindow): MdiWindow {
        if (_windows.any { it.windowId == window.windowId }) {
            return findWindow(window.windowId) ?: window
        }
        window.parentMdiContainer = this
        _windows.add(window)
        desktopSurface.addChild(window)

        val taskBtn = Button(
            text = formatTaskbarCaption(window),
            type = BootstrapType.SECONDARY,
            size = BootstrapSize.SM,
            outline = true
        ).apply {
            id = "mdi-task-${window.windowId}"
            addClass("mdi-taskbar-button")
            setOnClick {
                if (window.windowState == MdiWindowState.MINIMIZED) {
                    restoreWindow(window)
                } else if (activeWindow === window && window.isMinimizable) {
                    minimizeWindow(window)
                } else {
                    activateWindow(window)
                }
            }
        }
        taskbarButtons[window.windowId] = taskBtn
        taskbarStrip.addChild(taskBtn)

        activateWindow(window)
        onWindowAdded?.invoke(window)
        return window
    }

    fun createWindow(
        windowId: String,
        title: String,
        category: String = "",
        bounds: Rect = nextCascadeBounds(),
        icon: String? = null,
        block: (Container.() -> Unit)? = null
    ): MdiWindow {
        val win = MdiWindow(
            windowId = windowId,
            title = title,
            category = category,
            initialBounds = bounds,
            icon = icon
        )
        if (block != null) {
            win.bodyContainer.block()
        }
        return addWindow(win)
    }

    fun createFragmentWindow(
        windowId: String,
        title: String,
        fragment: Fragment,
        category: String = "",
        bounds: Rect = nextCascadeBounds(),
        icon: String? = null
    ): MdiWindow {
        val win = MdiWindow(
            windowId = windowId,
            title = title,
            category = category,
            initialBounds = bounds,
            icon = icon
        )
        win.setFragment(fragment)
        return addWindow(win)
    }

    fun removeWindow(window: MdiWindow): Boolean {
        val removed = _windows.remove(window)
        if (!removed) return false
        window.hostedFragment?.performDestroy()
        window.parentMdiContainer = null
        desktopSurface.removeChild(window)
        taskbarButtons.remove(window.windowId)?.let { taskbarStrip.removeChild(it) }
        window.onClosed?.invoke(window)
        onWindowRemoved?.invoke(window)

        if (activeWindow === window) {
            val nextCandidate = _windows.lastOrNull { it.windowState != MdiWindowState.MINIMIZED }
                ?: _windows.lastOrNull()
            if (nextCandidate != null) {
                activateWindow(nextCandidate)
            } else {
                activeWindow = null
                activeWindowBadge.text = "Active: None"
                onActiveWindowChanged?.invoke(null)
            }
        }
        return true
    }

    fun findWindow(windowId: String): MdiWindow? =
        _windows.firstOrNull { it.windowId == windowId }

    fun activateWindow(window: MdiWindow) {
        if (window !in _windows) return
        if (window.windowState == MdiWindowState.MINIMIZED) {
            window.isVisible = true
            window.windowState = MdiWindowState.NORMAL
            window.windowBounds = window.normalBounds
            window.hostedFragment?.performResume()
            window.onStateChanged?.invoke(window, window.windowState)
        }
        topZIndexCounter += 1
        window.zIndex = topZIndexCounter
        for (w in _windows) {
            val isTarget = (w === window)
            w.isActiveWindow = isTarget
            syncTaskbarButton(w)
        }
        activeWindow = window
        activeWindowBadge.text = "Active: ${window.title}"
        window.onActivated?.invoke(window)
        onActiveWindowChanged?.invoke(window)
    }

    fun minimizeWindow(window: MdiWindow) {
        if (window !in _windows || !window.isMinimizable) return
        if (window.windowState == MdiWindowState.NORMAL) {
            window.normalBounds = window.windowBounds
        }
        window.windowState = MdiWindowState.MINIMIZED
        window.isVisible = false
        window.isActiveWindow = false
        window.hostedFragment?.performPause()
        syncTaskbarButton(window)
        window.onStateChanged?.invoke(window, MdiWindowState.MINIMIZED)

        if (activeWindow === window) {
            val nextVisible = _windows.lastOrNull { it.windowState != MdiWindowState.MINIMIZED }
            if (nextVisible != null) {
                activateWindow(nextVisible)
            } else {
                activeWindow = null
                activeWindowBadge.text = "Active: None"
                onActiveWindowChanged?.invoke(null)
            }
        }
    }

    fun maximizeWindow(window: MdiWindow, desktopWidth: Int = 1160, desktopHeight: Int = 640) {
        if (window !in _windows || !window.isMaximizable) return
        if (window.windowState == MdiWindowState.NORMAL) {
            window.normalBounds = window.windowBounds
        }
        val surfaceW = desktopSurface.widthPx ?: desktopWidth
        val surfaceH = desktopSurface.heightPx ?: desktopHeight
        window.isVisible = true
        window.windowState = MdiWindowState.MAXIMIZED
        window.windowBounds = Rect(0, 0, surfaceW, surfaceH)
        window.hostedFragment?.performResume()
        activateWindow(window)
        window.onStateChanged?.invoke(window, MdiWindowState.MAXIMIZED)
    }

    fun restoreWindow(window: MdiWindow) {
        if (window !in _windows) return
        window.isVisible = true
        window.windowState = MdiWindowState.NORMAL
        window.windowBounds = window.normalBounds
        window.hostedFragment?.performResume()
        activateWindow(window)
        window.onStateChanged?.invoke(window, MdiWindowState.NORMAL)
    }

    fun cascadeWindows(
        startX: Int = 20,
        startY: Int = 20,
        stepX: Int = 34,
        stepY: Int = 34,
        width: Int = 480,
        height: Int = 360
    ) {
        _windows.forEachIndexed { idx, win ->
            val bounds = Rect(
                x = startX + idx * stepX,
                y = startY + idx * stepY,
                width = width,
                height = height
            )
            win.normalBounds = bounds
            win.windowBounds = bounds
            win.isVisible = true
            win.windowState = MdiWindowState.NORMAL
            topZIndexCounter += 1
            win.zIndex = topZIndexCounter
            syncTaskbarButton(win)
        }
        _windows.lastOrNull()?.let { activateWindow(it) }
    }

    fun tileWindows(
        columns: Int = 3,
        viewportWidth: Int = 1200,
        viewportHeight: Int = 680,
        gapPx: Int = 12
    ) {
        val visibleWindows = _windows.filter { it.windowState != MdiWindowState.MINIMIZED }
            .ifEmpty { _windows }
        if (visibleWindows.isEmpty()) return

        val cols = columns.coerceAtLeast(1).coerceAtMost(visibleWindows.size)
        val rows = (visibleWindows.size + cols - 1) / cols
        val cellWidth = ((viewportWidth - gapPx * (cols + 1)) / cols).coerceAtLeast(220)
        val cellHeight = ((viewportHeight - gapPx * (rows + 1)) / rows).coerceAtLeast(180)

        visibleWindows.forEachIndexed { index, win ->
            val col = index % cols
            val row = index / cols
            val x = gapPx + col * (cellWidth + gapPx)
            val y = gapPx + row * (cellHeight + gapPx)
            val bounds = Rect(x, y, cellWidth, cellHeight)
            win.normalBounds = bounds
            win.windowBounds = bounds
            win.isVisible = true
            win.windowState = MdiWindowState.NORMAL
            syncTaskbarButton(win)
        }
        visibleWindows.lastOrNull()?.let { activateWindow(it) }
    }

    fun minimizeAll() {
        for (win in _windows) {
            if (win.isMinimizable && win.windowState != MdiWindowState.MINIMIZED) {
                if (win.windowState == MdiWindowState.NORMAL) {
                    win.normalBounds = win.windowBounds
                }
                win.windowState = MdiWindowState.MINIMIZED
                win.isVisible = false
                win.isActiveWindow = false
                win.hostedFragment?.performPause()
                syncTaskbarButton(win)
                win.onStateChanged?.invoke(win, MdiWindowState.MINIMIZED)
            }
        }
        activeWindow = null
        activeWindowBadge.text = "Active: None"
        onActiveWindowChanged?.invoke(null)
    }

    fun restoreAll() {
        for (win in _windows) {
            win.isVisible = true
            win.windowState = MdiWindowState.NORMAL
            win.windowBounds = win.normalBounds
            win.hostedFragment?.performResume()
            syncTaskbarButton(win)
            win.onStateChanged?.invoke(win, MdiWindowState.NORMAL)
        }
        _windows.lastOrNull()?.let { activateWindow(it) }
    }

    internal fun syncTaskbarButton(window: MdiWindow) {
        val btn = taskbarButtons[window.windowId] ?: return
        btn.text = formatTaskbarCaption(window)
        btn.type = when {
            window.isActiveWindow -> BootstrapType.PRIMARY
            window.windowState == MdiWindowState.MINIMIZED -> BootstrapType.WARNING
            else -> BootstrapType.SECONDARY
        }
        btn.outline = !window.isActiveWindow
    }

    private fun formatTaskbarCaption(window: MdiWindow): String {
        val stateSuffix = when (window.windowState) {
            MdiWindowState.MINIMIZED -> " [Min]"
            MdiWindowState.MAXIMIZED -> " [Max]"
            MdiWindowState.NORMAL -> ""
        }
        return "${window.title}$stateSuffix"
    }
}
