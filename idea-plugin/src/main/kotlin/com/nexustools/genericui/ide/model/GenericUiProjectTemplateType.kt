package com.nexustools.genericui.ide.model

/**
 * Starter project template types available in the GenericUI New Project Wizard.
 */
enum class GenericUiProjectTemplateType(
    val id: String,
    val displayName: String,
    val description: String,
    val defaultExtensions: Set<GenericUiExtensionModule>
) {
    STANDARD_FRAGMENT(
        id = "standard_fragment",
        displayName = "Standard Fragment Application",
        description = "Multiplatform application with a responsive Fragment, MenuBar, ToolBar, and Bootstrap/SASS styling.",
        defaultExtensions = emptySet()
    ),
    DOCK_WORKSPACE(
        id = "dock_workspace",
        displayName = "DockWorkspaceFragment (GIMP Single/Multi-Window IDE)",
        description = "GIMP-style dockable workspace supporting single-window docked panels and multi-window floating tear-offs.",
        defaultExtensions = linkedSetOf(
            GenericUiExtensionModule.CANVAS_2D,
            GenericUiExtensionModule.CODE_EDITOR,
            GenericUiExtensionModule.AI_CHAT
        )
    ),
    OVERLAY_DOCK_3D(
        id = "overlay_dock_3d",
        displayName = "OverlayDockWorkspaceFragment (3D Three.js Canvas with Overlay Panels)",
        description = "Full-bleed 3D Three.js viewport with translucent dockable overlay panels and HUD controls.",
        defaultExtensions = linkedSetOf(
            GenericUiExtensionModule.GPU,
            GenericUiExtensionModule.CANVAS_2D,
            GenericUiExtensionModule.CANVAS_3D,
            GenericUiExtensionModule.THREE_JS
        )
    ),
    ADAPTIVE_SPLIT_TAB(
        id = "adaptive_split_tab",
        displayName = "HybridColumnFragment (Desktop/Mobile Responsive Media App)",
        description = "Adaptive master-detail split view on Desktop/Tablet that automatically transitions to tabbed navigation on Mobile.",
        defaultExtensions = linkedSetOf(
            GenericUiExtensionModule.MEDIA
        )
    ),
    GAME_2D(
        id = "game_2d",
        displayName = "2D Game Stage (Engine2D + Box2D + Gamepad DSL)",
        description = "Hardware-accelerated 2D game loop with Game2DCanvasHost, Box2D physics, tilemaps, sprites, and gamepad navigation.",
        defaultExtensions = linkedSetOf(
            GenericUiExtensionModule.ENGINE_2D,
            GenericUiExtensionModule.DSL
        )
    ),
    GAME_3D(
        id = "game_3d",
        displayName = "3D Game Stage (Engine3D + Three.js + Box3D + Gamepad DSL)",
        description = "Hardware-accelerated 3D game loop with Game3DCanvasHost, Three.js scene graph, Box3D physics, and gamepad navigation.",
        defaultExtensions = linkedSetOf(
            GenericUiExtensionModule.ENGINE_3D,
            GenericUiExtensionModule.DSL
        )
    ),
    WIZARD_PAGE_SYSTEM(
        id = "wizard_page_system",
        displayName = "CenteredMenuPageFragment & ValidatedStepPageFragment (Multi-Step Wizard)",
        description = "Multi-page application with a centered launch menu and a step-by-step validated wizard flow.",
        defaultExtensions = emptySet()
    );

    override fun toString(): String = displayName
}
