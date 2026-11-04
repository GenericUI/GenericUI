package com.nexustools.genericui.core

import com.nexustools.genericui.component.layout.DefaultLayoutFactory
import com.nexustools.genericui.component.layout.LayoutFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.nativeimpl.content.TableNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.dock.*
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl
import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl
import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl
import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl
import com.nexustools.genericui.nativeimpl.io.DefaultFileSystemNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileSystemNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl
import com.nexustools.genericui.nativeimpl.net.TcpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.UdpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.WebSocketNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import com.nexustools.genericui.nativeimpl.shell.*
import com.nexustools.genericui.nativeimpl.system.*
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl

/**
 * Abstract factory interface implemented by all 8 platform backends (`:qt`, `:gtk`, `:windows`, `:macos`,
 * `:swing`, `:android`, `:ios`, `:web`).
 *
 * Components only request their `NativeImpl` from [NativeFactory] when they are connected to a visible
 * on-screen `Window` / `Application` and have `isVisible == true`.
 */
interface NativeFactory {
    val backendTarget: BackendTarget

    val backendName: String
        get() = backendTarget.displayName

    val defaultTitleCapabilities: com.nexustools.genericui.window.TitleCapabilities
        get() = com.nexustools.genericui.window.TitleCapabilities()

    val usesBrowserManagedImageLoading: Boolean
        get() = false

    val supportsMultipleWindows: Boolean
        get() = true

    val supportsToolModalWindows: Boolean
        get() = true

    val supportsServices: Boolean
        get() = false

    val supportsSystemMenuBar: Boolean
        get() = false

    val supportsSystemTray: Boolean
        get() = true

    val supportsDirectorySelection: Boolean
        get() = true

    val supportsDirectFileSystemAccess: Boolean
        get() = true

    /**
     * `true` when the host environment itself already displays the window/page title and provides
     * Back/Forward navigation (e.g. a web browser's tab title, toolbar, and history buttons).
     * In that case in-app navigation chrome (such as the Back/Forward buttons, managed `TitleBar`,
     * and fallback `Navbar` of `FragmentHostFragment`) is redundant and is not created.
     */
    val providesNativeNavigationChrome: Boolean
        get() = false

    val isStyleSheetEngineEnabled: Boolean
        get() = true

    val defaultAntialiasing: Boolean
        get() = true

    val layoutFactory: LayoutFactory
        get() = DefaultLayoutFactory

    /**
     * `true` when the active backend supports the HTML5/Bootstrap Web Splash Screen (`:web`).
     * `false` on all other backends.
     */
    val isWebSplashSupported: Boolean
        get() = false

    /**
     * Updates the web splash screen status text and semantic [BootstrapType] styling.
     * When [type] is [BootstrapType.DANGER], the error dash icon (`bi-dash-circle-fill text-danger`)
     * is automatically displayed.
     * Safe no-op on backends where [isWebSplashSupported] is `false`.
     */
    fun setWebSplashStatus(statusText: String, type: BootstrapType = BootstrapType.NONE) {}

    /**
     * Updates the web splash screen progress bar percentage (`0..100`) and semantic [BootstrapType] color
     * (e.g., `bg-primary`, `bg-success`, `bg-warning`, `bg-danger`, `bg-info`).
     * Safe no-op on backends where [isWebSplashSupported] is `false`.
     */
    fun setWebSplashProgress(progressPercent: Int, type: BootstrapType = BootstrapType.PRIMARY) {}

    /**
     * Displays the error alert block (`#genericui-splash-error`) on the web splash screen,
     * automatically setting the status to `"Error"` with [BootstrapType.DANGER] (error dash icon)
     * and the progress bar to `100%` [BootstrapType.DANGER].
     * Safe no-op on backends where [isWebSplashSupported] is `false`.
     */
    fun setWebSplashError(message: String, stackTrace: String? = null, title: String = "Error") {}

    /**
     * Sets the web splash screen status text to `"Ready"`, sets progress to `100%`, and fades out
     * and destroys the `#genericui-splash` DOM element on `:web`.
     * Safe no-op on backends where [isWebSplashSupported] is `false`.
     */
    fun finishWebSplash() {}

    // Shell & Window
    fun createApplication(): ApplicationNativeImpl<*>
    fun createWindow(): WindowNativeImpl<*>
    fun createMenuBar(): MenuBarNativeImpl<*>
    fun createMenu(): MenuNativeImpl<*>
    fun createMenuItem(checkable: Boolean = false, radio: Boolean = false): MenuItemNativeImpl<*>
    fun createContextMenu(): ContextMenuNativeImpl<*>

    // Dockable Panel System (GIMP / Photoshop Studio)
    fun createDockWorkspace(): DockWorkspaceNativeImpl<*>
    fun createDockArea(): DockAreaNativeImpl<*>
    fun createDockGroup(): DockGroupNativeImpl<*>
    fun createDockPanel(): DockPanelNativeImpl<*>
    fun createDockWindow(): DockWindowNativeImpl<*>
    fun createToolPalette(): ToolPaletteNativeImpl<*>

    // Layout & Printable Book Documents
    fun createContainer(classPrefix: String = "container"): ContainerNativeImpl<*>
    fun createContainer(classPrefix: String, tagName: String): ContainerNativeImpl<*> = createContainer(classPrefix)
    fun createStyledContainer(
        classPrefix: String = "styled-container",
        tagName: String = "genericui:styled-container"
    ): StyledContainerNativeImpl<*>
    fun createBookContainer(): BookContainerNativeImpl<*>
    fun createScrollView(): ScrollViewNativeImpl<*>
    fun createScrollView(tagName: String): ScrollViewNativeImpl<*> = createScrollView()
    fun createDivider(): DividerNativeImpl<*>

    // Typography & Content
    fun createText(tagName: String = "span", classPrefix: String = ""): TextNativeImpl<*>
    fun createMarkdownView(): MarkdownViewNativeImpl<*>
    fun createImage(): ImageNativeImpl<*>
    fun createTable(): TableNativeImpl<*>

    // Action & Form
    fun createButton(classPrefix: String = "btn"): ButtonNativeImpl<*>
    fun createStyledButton(classPrefix: String = "styled-btn"): StyledButtonNativeImpl<*>
    fun createDropdown(): DropdownNativeImpl<*>
    fun createTextInput(multiline: Boolean = false): TextInputNativeImpl<*>
    fun createSelect(): SelectNativeImpl<*>
    fun createCheckbox(switchStyle: Boolean = false, radioStyle: Boolean = false): CheckboxNativeImpl<*>
    fun createRangeSlider(): RangeSliderNativeImpl<*>

    // Surface, Tabs, Navigation, Feedback & Overlay
    fun createTab(closable: Boolean = false): TabNativeImpl<*>
    fun createTabContainer(): TabContainerNativeImpl<*>
    fun createNavbar(): NavbarNativeImpl<*>
    fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*>
    fun createProgressBar(): ProgressBarNativeImpl<*>
    fun createSpinner(): SpinnerNativeImpl<*>
    fun createPopover(): PopoverNativeImpl<*>

    // Dialogs & Native File/Folder Browser
    fun createDialogWindow(): DialogWindowNativeImpl<*>
    fun createFileBrowserDialog(): FileBrowserDialogNativeImpl
    fun createStandardDialogs(): StandardDialogsNativeImpl

    // Lazy Virtualized Lists, Grids & Trees
    fun createLazyList(): LazyListNativeImpl<*>
    fun createLazyGrid(): LazyGridNativeImpl<*>
    fun createTreeView(): TreeViewNativeImpl<*>

    // System Services & Async I/O / Database
    fun createUiTimerScheduler(): UiTimerNativeImpl = DefaultUiTimerNativeImpl()
    fun createPermissionManager(): PermissionManagerNativeImpl
    fun createSystemTray(): SystemTrayNativeImpl
    fun createNotificationManager(): NotificationManagerNativeImpl
    fun createNetworkMonitor(): NetworkMonitorNativeImpl
    fun createPreferenceStore(appName: String = GenericUI.applicationName): PreferenceStoreNativeImpl =
        DefaultPreferenceStoreNativeImpl(appName)
    fun createConfigDirectory(
        appName: String = GenericUI.applicationName,
        subdirectory: String = ""
    ): UserDirectoryNativeImpl = DefaultUserDirectoryNativeImpl(appName, subdirectory, "config")
    fun createCacheDirectory(
        appName: String = GenericUI.applicationName,
        subdirectory: String = ""
    ): UserDirectoryNativeImpl = DefaultUserDirectoryNativeImpl(appName, subdirectory, "cache")
    fun createFileSystem(): FileSystemNativeImpl = DefaultFileSystemNativeImpl()
    fun createDatabase(
        databaseName: String = GenericUI.applicationName,
        filePath: String? = null,
        version: Int = 1
    ): DatabaseNativeImpl = DefaultDatabaseNativeImpl(
        databaseName = databaseName,
        filePath = filePath ?: "$databaseName.db",
        version = version
    )
    fun createResourceResolver(): com.nexustools.genericui.resources.ResourceResolverNativeImpl =
        com.nexustools.genericui.resources.DefaultResourceResolverNativeImpl()

    // Async Networking (TCP, UDP, HTTP/HTTPS, WebSocket)
    fun supportsTcp(): Boolean
    fun supportsUdp(): Boolean
    fun supportsMulticastUdp(): Boolean = supportsUdp()
    fun createTcpSocket(): TcpSocketNativeImpl {
        if (!supportsTcp()) {
            throw UnsupportedOperationException("Raw TCP sockets are not supported on backend '$backendName'.")
        }
        return com.nexustools.genericui.nativeimpl.net.DefaultTcpSocketNativeImpl()
    }
    fun createUdpSocket(): UdpSocketNativeImpl {
        if (!supportsUdp()) {
            throw UnsupportedOperationException("Raw UDP sockets are not supported on backend '$backendName'.")
        }
        return com.nexustools.genericui.nativeimpl.net.DefaultUdpSocketNativeImpl()
    }
    fun createHttpClient(): HttpClientNativeImpl = com.nexustools.genericui.nativeimpl.net.DefaultHttpClientNativeImpl()
    fun createWebSocket(): WebSocketNativeImpl = com.nexustools.genericui.nativeimpl.net.DefaultWebSocketNativeImpl()
}
