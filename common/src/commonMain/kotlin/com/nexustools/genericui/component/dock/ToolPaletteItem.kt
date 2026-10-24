package com.nexustools.genericui.component.dock

import com.nexustools.genericui.event.KeyShortcut

/**
 * Descriptor for a selectable tool inside a [ToolPalette] (e.g. Brush, Bezier Pen, Move, Eraser).
 */
data class ToolPaletteItem(
    val id: String,
    val name: String,
    val iconSvgOrPath: String,
    val tooltip: String = name,
    val shortcut: KeyShortcut? = null,
    val category: String = "Default"
)
