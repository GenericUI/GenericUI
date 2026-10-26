package com.nexustools.genericui.component.dock

/**
 * Policy controlling when [DockWorkspace] and [com.nexustools.genericui.component.fragment.helper.DockWorkspaceFragment]
 * hide peripheral docks (`LEFT`, `RIGHT`, `TOP`, `BOTTOM`) off-canvas as edge-swipe slide-out drawers.
 */
enum class DockMobileDrawerPolicy {
    /**
     * Automatically uses normal onscreen docks on tablets (`smallestScreenWidthDp >= 600`), wide Web viewports (`>= 600dp`),
     * and desktop viewports (`>= 600dp`), and switches to off-canvas slide-out edge drawers on small-screen mobile (`< 600dp`)
     * and narrow Web/Desktop viewports (`< 600dp`).
     */
    AUTO,

    /**
     * Always hides peripheral docks off-canvas as slide-out edge drawers regardless of screen or viewport size.
     */
    ALWAYS_ENABLED,

    /**
     * Never uses slide-out edge drawers; always uses the standard onscreen dock layout.
     */
    DISABLED
}
