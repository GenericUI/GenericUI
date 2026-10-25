package com.nexustools.genericui.enums.dock

/**
 * GIMP / Photoshop workspace window modes managed by [com.nexustools.genericui.component.fragment.helper.DockWorkspaceFragment].
 */
enum class StudioWindowMode {
    /**
     * All StudioDocuments are displayed as closable tabs inside the central ClosableTabContainer,
     * and all DockPanels/ToolPalette are docked inside the main window's DockWorkspace (unless torn off).
     */
    SINGLE_WINDOW_MODE,

    /**
     * Each StudioDocument is displayed in its own separate native OS Window, and each DockPanel
     * is displayed in its own standalone detached native utility PanelWindow.
     */
    MULTI_WINDOW_MODE;

    companion object {
        val SINGLE_WINDOW: StudioWindowMode get() = SINGLE_WINDOW_MODE
        val MULTI_WINDOW: StudioWindowMode get() = MULTI_WINDOW_MODE
    }
}
