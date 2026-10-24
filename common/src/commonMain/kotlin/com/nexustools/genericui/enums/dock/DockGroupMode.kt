package com.nexustools.genericui.enums.dock

/**
 * Grouping layout mode for a [com.nexustools.genericui.component.dock.DockGroup].
 */
enum class DockGroupMode {
    TABBED,
    VERTICAL_SPLIT,
    HORIZONTAL_SPLIT;

    companion object {
        val TABS: DockGroupMode get() = TABBED
    }
}
