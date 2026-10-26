package com.nexustools.genericui.enums.fragment

/**
 * Controls how `launchFragment(...)` (on [com.nexustools.genericui.component.fragment.IFragment],
 * [com.nexustools.genericui.window.IWindow], or [com.nexustools.genericui.component.shell.IApplication])
 * mounts and displays the newly created fragment relative to the current
 * [com.nexustools.genericui.window.IWindow].
 */
enum class FragmentLaunchMode {
    /**
     * Mounts the launched fragment inside the current [com.nexustools.genericui.window.IWindow]'s
     * [com.nexustools.genericui.component.fragment.FragmentContainer] (pushing the previous fragment
     * onto the back stack when `addToBackStack = true`) and synchronizes the window's `TitleBar`,
     * `MenuBar`, `Navbar`, `BottomNavigationBar`, and fullscreen state.
     */
    SAME_WINDOW,

    /**
     * Clears the current window's fragment back stack and mounts the launched fragment as the new root
     * fragment of the current [com.nexustools.genericui.window.IWindow].
     */
    CLEAR_TASK,

    /**
     * Spawns a new top-level [com.nexustools.genericui.component.shell.Window] (or falls back to
     * [SAME_WINDOW] on single-window mobile/web backends), mounts the launched fragment inside it,
     * synchronizes the fragment's chrome with the new window, and shows the window.
     */
    NEW_WINDOW,

    /**
     * Spawns a floating [com.nexustools.genericui.component.dock.PanelWindow] owned by the current
     * window and mounts the launched fragment inside it.
     */
    NEW_PANEL_WINDOW,

    /**
     * Spawns a modal [com.nexustools.genericui.component.dialog.DialogWindow] and mounts the launched
     * fragment inside it.
     */
    NEW_DIALOG_WINDOW
}
