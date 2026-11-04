package com.nexustools.genericui.component.dock

import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.resources.ResourceID

/**
 * Descriptor for a selectable tool inside a [ToolPalette] (e.g. Brush, Bezier Pen, Move, Eraser).
 *
 * Supports both string/SVG icon identifiers ([iconSvgOrPath]) and typed `R.drawable.*` / `RC.drawable.*`
 * [ResourceID]s via the secondary constructor.
 */
data class ToolPaletteItem(
    val id: String,
    val name: String,
    val iconSvgOrPath: String,
    val tooltip: String? = null,
    val shortcut: KeyShortcut? = null,
    val category: String = "Default"
) {
    val htmlTag: String = "genericui:toolpaletteitem"

    constructor(
        id: String,
        name: String,
        iconRes: ResourceID,
        tooltip: String? = null,
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
