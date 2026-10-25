package com.nexustools.genericui.component.dock

import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.res.ResourceId

/**
 * Descriptor for a selectable tool inside a [ToolPalette] (e.g. Brush, Bezier Pen, Move, Eraser).
 *
 * Supports both string/SVG icon identifiers ([iconSvgOrPath]) and typed `R.drawable.*` / `RC.drawable.*`
 * [ResourceId]s via the secondary constructor.
 */
data class ToolPaletteItem(
    val id: String,
    val name: String,
    val iconSvgOrPath: String,
    val tooltip: String = name,
    val shortcut: KeyShortcut? = null,
    val category: String = "Default"
) {
    constructor(
        id: String,
        name: String,
        iconRes: ResourceId,
        tooltip: String = name,
        shortcut: KeyShortcut? = null,
        category: String = "Default"
    ) : this(
        id = id,
        name = name,
        iconSvgOrPath = iconRes.toResourceUri(),
        tooltip = tooltip,
        shortcut = shortcut,
        category = category
    )
}
