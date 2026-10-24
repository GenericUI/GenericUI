package com.nexustools.genericui.component.dock

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size

/**
 * Detached OS-level utility window hosting a floating [DockPanel], [DockGroup], or [DockArea]
 * in GIMP Multi-Window Mode or when torn off from a [DockWorkspace].
 */
class DockWindow(
    title: String = "Dock",
    val panel: DockPanel? = null,
    val initialBounds: Rect = panel?.floatingBounds ?: Rect(120, 120, 320, 480)
) : Window(
    title = title,
    initialSize = Size(
        width = initialBounds.width.coerceAtLeast(240),
        height = initialBounds.height.coerceAtLeast(200)
    )
) {
    init {
        if (panel != null) {
            addChild(panel)
        }
    }

    fun hide() {
        isVisible = false
    }

    fun setOnCloseRequested(listener: () -> Unit) {
        setOnWindowCloseRequested {
            listener()
            false
        }
    }
}
