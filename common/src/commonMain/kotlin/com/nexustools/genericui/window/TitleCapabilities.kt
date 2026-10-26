package com.nexustools.genericui.window

/**
 * Describes the titlebar capabilities of an [IWindow] (or [com.nexustools.genericui.component.dock.DockPanel])
 * on the active backend and operating system, including whether the titlebar is native or emulated.
 */
data class TitleCapabilities(
    /** `true` if the window displays a titlebar or header bar (`false` on top-level `:web` browser tab or fullscreen). */
    val isSupported: Boolean = true,
    /**
     * `true` when the titlebar is emulated/custom-rendered by GenericUI or the UI toolkit (e.g., `MdiWindow`,
     * docked `DockPanel` header, `DialogWindow` overlay, `:qt` frameless custom titlebar, `:android`/`:ios`
     * synthesized top `Navbar`, or Windows fallback when DWM/`UxTheme` is unavailable);
     * `false` when using or extending the native OS window manager titlebar (`Win32` DWM/`UxTheme` `HTCAPTION`,
     * macOS `NSWindow` titlebar, GTK `GtkHeaderBar` / `AdwHeaderBar`).
     */
    val isEmulated: Boolean = false,
    /** `true` if custom [TitleBar] instructions are supported on this window. */
    val supportsCustomTitleBar: Boolean = true,
    /** `true` if arbitrary [com.nexustools.genericui.core.AbstractComponent] controls can be embedded in the titlebar. */
    val supportsCustomComponents: Boolean = true,
    /** `true` if [TitleBar.leadingComponents] can be placed in the titlebar. */
    val supportsLeadingComponents: Boolean = true,
    /** `true` if [TitleBar.centerComponents] can be placed in the titlebar. */
    val supportsCenterComponents: Boolean = true,
    /** `true` if [TitleBar.trailingComponents] can be placed in the titlebar. */
    val supportsTrailingComponents: Boolean = true,
    /** `true` if a `MenuBar` can be embedded directly inside the titlebar (`TitleBar.embedMenuBar = true`). */
    val supportsEmbeddedMenuBar: Boolean = false,
    /** `true` if the `MenuBar` and [TitleBar] components are hosted via a synthesized top `Navbar` (Android / iOS). */
    val supportsNavbarMenuBar: Boolean = false,
    /** `true` if displaying a window icon in the titlebar is supported (`false` on tool windows and mobile). */
    val supportsWindowIcon: Boolean = true,
    /** `true` if displaying the window title text caption is supported. */
    val supportsWindowTitle: Boolean = true,
    /** `true` if the titlebar supports a minimize caption button (`false` on tool/dialog windows and mobile). */
    val supportsMinimizeButton: Boolean = true,
    /** `true` if the titlebar supports a maximize/restore caption button (`false` on tool/dialog windows and mobile). */
    val supportsMaximizeButton: Boolean = true,
    /** `true` if the titlebar supports a close caption button. */
    val supportsCloseButton: Boolean = true,
    /** `true` if clicking/toggling the titlebar can collapse/expand the body (e.g. docked `DockPanel` header). */
    val supportsCollapseToggle: Boolean = false,
    /** `true` if repositioning window caption controls (`START` vs `END`) is supported. */
    val supportsControlsPlacement: Boolean = true,
    /** `true` if customizing the titlebar pixel height (`TitleBar.heightPx`) is supported. */
    val supportsCustomHeight: Boolean = true,
    /** `true` if native OS Aero Glass / DWM Mica / macOS vibrancy backdrop is supported on the titlebar. */
    val supportsSystemAeroOrBackdrop: Boolean = false,
    /** `true` if this window uses compact tool-window titlebar metrics (`SM_CYSMCAPTION` / `WS_EX_TOOLWINDOW` / `NSPanel`). */
    val isToolWindowTitleBar: Boolean = false,
    /**
     * `true` when the window always shows a titlebar regardless of app configuration (native desktop windows);
     * `false` on Android, iOS, and Web, where window chrome only appears if the app sets a [TitleBar] and/or `MenuBar`.
     */
    val isTitleBarRequired: Boolean = true,
    /**
     * `true` when setting a [TitleBar] creates a navbar instead of a classic titlebar (Android toolbar navbar,
     * iOS native navigation-bar layout, Web Bootstrap navbar).
     */
    val titleBarRendersAsNavbar: Boolean = false,
    /**
     * `true` when a `MenuBar` alone (without a [TitleBar]) still creates a navbar with the menu dropdown on the
     * right (Android / iOS); `false` on Web, where a lone `MenuBar` renders as just a menubar.
     */
    val menuBarRendersAsNavbar: Boolean = false,
    /**
     * `true` when, with both a [TitleBar] and a `MenuBar` set, a single navbar shows the title/icon on the left
     * and the menu on the right (Android / iOS / Web).
     */
    val menuBarMergesIntoTitleNavbar: Boolean = false
) {
    /**
     * `true` when components added to [TitleBar] (`leadingComponents`, `centerComponents`, `trailingComponents`)
     * will be rendered inside the titlebar (or mobile navbar) and therefore MUST NOT be duplicated inside the
     * window's content body.
     */
    val canEmbedComponentsInTitleBar: Boolean
        get() = isSupported && supportsCustomComponents &&
            (supportsLeadingComponents || supportsCenterComponents || supportsTrailingComponents)

    /**
     * Returns `true` when any window chrome (titlebar or navbar) must be displayed:
     * `isTitleBarRequired || hasTitleBar || hasMenuBar` (e.g. Web with neither a [TitleBar] nor a `MenuBar`
     * returns `false`, so no navbar is rendered at all).
     */
    fun requiresChrome(hasTitleBar: Boolean, hasMenuBar: Boolean): Boolean =
        isTitleBarRequired || hasTitleBar || hasMenuBar

    companion object {
        /** Capabilities reported when no titlebar is available at all (e.g. top-level `:web` browser tab). */
        val UNSUPPORTED: TitleCapabilities = TitleCapabilities(
            isSupported = false,
            isEmulated = false,
            supportsCustomTitleBar = false,
            supportsCustomComponents = false,
            supportsLeadingComponents = false,
            supportsCenterComponents = false,
            supportsTrailingComponents = false,
            supportsEmbeddedMenuBar = false,
            supportsNavbarMenuBar = false,
            supportsWindowIcon = false,
            supportsWindowTitle = false,
            supportsMinimizeButton = false,
            supportsMaximizeButton = false,
            supportsCloseButton = false,
            supportsCollapseToggle = false,
            supportsControlsPlacement = false,
            supportsCustomHeight = false,
            supportsSystemAeroOrBackdrop = false,
            isToolWindowTitleBar = false,
            isTitleBarRequired = false,
            titleBarRendersAsNavbar = false,
            menuBarRendersAsNavbar = false,
            menuBarMergesIntoTitleNavbar = false
        )
    }
}
