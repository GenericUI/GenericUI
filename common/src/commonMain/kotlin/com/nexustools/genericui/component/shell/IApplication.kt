package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.fragment.FragmentLaunchMode
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import kotlin.reflect.KClass

/**
 * Contract implemented by GenericUI [Application] (and inherited by [FragmentApplication]), providing
 * system target identification ([BackendTarget]), multi-backend group checks, runtime capability
 * queries, and top-level window/theme/fragment management.
 *
 * Notes for the implementer ([Application]):
 * - Overrides must not re-declare default parameter values: remove `= GenericUI.requireFactory()` from
 *   `Application.run` and `= 0` from `Application.exit` (the identical defaults declared here are inherited).
 * - [backendName] / [backendTarget] default to the active [GenericUI.activeFactory]; override only if needed.
 */
interface IApplication : Disposable {
    /** Primary application window. */
    val mainWindow: Window

    /**
     * [mainWindow] viewed as an [IWindow].
     * (Cast kept until `Window : IWindow` lands; it can then simply return [mainWindow].)
     */
    @Suppress("USELESS_CAST")
    val mainIWindow: IWindow
        get() = mainWindow as IWindow

    /** Descriptive backend name reported by the active [NativeFactory] (empty if no factory is active). */
    val backendName: String
        get() = GenericUI.activeFactory?.backendName.orEmpty()

    /** Active platform [BackendTarget] (`Windows`, `MacOS`, `Gtk`, `Qt`, `Swing`, `Android`, `IOS`, `Web`). */
    val backendTarget: BackendTarget
        get() = GenericUI.activeFactory?.backendTarget ?: BackendTarget.fromBackendName(backendName)

    /** Operating system family name (e.g., `"Windows"`, `"macOS"`, `"Linux"`, `"Android"`, `"iOS"`, `"Web Browser"`). */
    val systemOsName: String
        get() = backendTarget.osFamilyName

    // Multi-Backend Target Group Checks (delegated to backendTarget)
    val isSystem: Boolean
        get() = backendTarget.isSystem
    val isDesktop: Boolean
        get() = backendTarget.isDesktop
    val isNativeDesktop: Boolean
        get() = backendTarget.isNativeDesktop
    val isLinux: Boolean
        get() = backendTarget.isLinux
    val isMobile: Boolean
        get() = backendTarget.isMobile
    val isApple: Boolean
        get() = backendTarget.isApple
    val isUnix: Boolean
        get() = backendTarget.isUnix
    val isPosix: Boolean
        get() = backendTarget.isPosix
    val isNative: Boolean
        get() = backendTarget.isNative
    val isJvm: Boolean
        get() = backendTarget.isJvm
    val isTouchPrimary: Boolean
        get() = backendTarget.isTouchPrimary
    val isBrowser: Boolean
        get() = backendTarget.isBrowser

    // Application & System Capability Checks
    /** [TitleCapabilities] of the application's [mainWindow]. */
    val titleCapabilities: TitleCapabilities
        get() = mainIWindow.titleCapabilities

    /** `true` if the application can spawn multiple native OS top-level windows. */
    val supportsMultipleWindows: Boolean
        get() = backendTarget.isDesktop

    /** `true` if the application supports floating tool-modal utility windows (`PanelWindow`). */
    val supportsToolModalWindows: Boolean
        get() = backendTarget.isDesktop

    /** `true` if the platform supports long-running background services (Android `Service`). */
    val supportsServices: Boolean
        get() = backendTarget == BackendTarget.Android

    /** `true` if custom [TitleBar] configuration is supported on [mainWindow]. */
    val supportsCustomTitleBar: Boolean
        get() = titleCapabilities.supportsCustomTitleBar

    /** `true` if a [MenuBar] can be embedded directly inside [mainWindow]'s titlebar. */
    val supportsEmbeddedMenuBarInTitleBar: Boolean
        get() = titleCapabilities.supportsEmbeddedMenuBar

    /** `true` if the platform provides a global screen-top system menu bar (macOS `NSApplication.mainMenu`). */
    val supportsSystemMenuBar: Boolean
        get() = backendTarget == BackendTarget.MacOS

    /** `true` if the platform renders `MenuBar` as a top `Navbar` with dropdown trigger (`Android`, `IOS`). */
    val supportsNavbarMenuBar: Boolean
        get() = titleCapabilities.supportsNavbarMenuBar

    /** `true` if persistent OS system tray / status bar icons are supported. */
    val supportsSystemTray: Boolean
        get() = backendTarget.isDesktop

    /** `true` if native OS desktop/mobile notifications are supported. */
    val supportsNativeNotifications: Boolean
        get() = true

    /** `true` if native OS file open/save dialogs are supported. */
    val supportsNativeFileDialogs: Boolean
        get() = true

    /** `true` if native directory/folder chooser dialogs are supported. */
    val supportsDirectorySelection: Boolean
        get() = backendTarget.isSystem

    /** `true` if direct local filesystem path access is supported without browser sandbox restrictions. */
    val supportsDirectFileSystemAccess: Boolean
        get() = backendTarget.isSystem

    /** `true` if Picture-in-Picture (PiP) mode is supported. */
    val supportsPictureInPicture: Boolean
        get() = true

    /** `true` if fullscreen window/viewport mode is supported. */
    val supportsFullscreen: Boolean
        get() = true

    /** `true` if `ThemeMode.SYSTEM` automatically tracks the operating system dark/light theme. */
    val supportsSystemThemeDetection: Boolean
        get() = true

    /** `true` if custom accent/chrome color (`setThemeColor`) is supported. */
    val supportsThemeAccentColor: Boolean
        get() = true

    /** `true` if hardware-accelerated GPU rendering/compute (Vulkan / OpenGL ES / Metal / WebGPU / WebGL2) is supported. */
    val supportsHardwareGpuAcceleration: Boolean
        get() = true

    // Content, Chrome, Fragment, StyleSheet & Theme Management
    fun setContent(container: Container)
    fun getContent(): Container?
    fun setMenuBar(menuBar: MenuBar?)
    fun getMenuBar(): MenuBar?
    fun setTitleBar(titleBar: TitleBar?)
    fun getTitleBar(): TitleBar?
    fun setThemeMode(mode: ThemeMode)
    fun getThemeMode(): ThemeMode
    fun setTheme(theme: ThemeMode) = setThemeMode(theme)
    fun getTheme(): ThemeMode = getThemeMode()
    fun getResolvedTheme(): ThemeMode = SystemThemeDetector.resolveTheme(getTheme())
    fun addThemeChangeListener(listener: (ThemeMode) -> Unit) {}
    fun removeThemeChangeListener(listener: (ThemeMode) -> Unit) {}
    fun setThemeColor(colorHex: String?)
    fun getThemeColor(): String?

    /**
     * Registers and activates a global precompiled stylesheet by its source file path
     * (e.g., `"styles/app.scss"` or `"app.css"`).
     */
    fun addStyleSheet(styleSheetPath: String) {}

    /**
     * Registers and activates a global precompiled stylesheet by its compiled [ResourceID]
     * (e.g., `R.style.app_theme`).
     */
    fun addStyleSheet(styleSheetResourceId: ResourceID) {}

    /**
     * Registers and activates a [PrecompiledStyleSheet] instance on the application.
     */
    fun addStyleSheet(styleSheet: PrecompiledStyleSheet) {}

    /**
     * Removes a previously added global stylesheet by its source file path.
     *
     * @return `true` if the stylesheet was active and removed.
     */
    fun removeStyleSheet(styleSheetPath: String): Boolean = false

    /**
     * Removes a previously added global stylesheet by its [ResourceID].
     *
     * @return `true` if the stylesheet was active and removed.
     */
    fun removeStyleSheet(styleSheetResourceId: ResourceID): Boolean = false

    /**
     * Removes a previously added [PrecompiledStyleSheet] instance.
     *
     * @return `true` if the stylesheet was active and removed.
     */
    fun removeStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean = false

    /** Returns `true` if a stylesheet with [styleSheetPath] is currently active on this application. */
    fun hasStyleSheet(styleSheetPath: String): Boolean = false

    /** Returns `true` if a stylesheet with [styleSheetResourceId] is currently active on this application. */
    fun hasStyleSheet(styleSheetResourceId: ResourceID): Boolean = false

    /** Returns `true` if [styleSheet] is currently active on this application. */
    fun hasStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean =
        hasStyleSheet(styleSheet.sourcePath) || getStyleSheets().any { it === styleSheet || it.sourcePath == styleSheet.sourcePath }

    /** Returns the ordered list of currently active [PrecompiledStyleSheet] instances on this application. */
    fun getStyleSheets(): List<PrecompiledStyleSheet> = emptyList()

    /** Returns the ordered list of source file paths for all active stylesheets on this application. */
    fun getStyleSheetPaths(): List<String> = emptyList()

    /** Launches [fragmentClass] into [mainWindow] (see [IWindow.launchFragment]). */
    fun <T : IFragment> launchFragment(
        fragmentClass: KClass<T>,
        launchArgs: FragmentBundle? = null,
        addToBackStack: Boolean = true,
        launchMode: FragmentLaunchMode = FragmentLaunchMode.SAME_WINDOW
    ): T = mainIWindow.launchFragment(fragmentClass, launchArgs, addToBackStack, launchMode)

    /** Launches an already-constructed [fragment] into [mainWindow] (see [IWindow.launchFragment]). */
    fun <T : IFragment> launchFragment(
        fragment: T,
        launchArgs: FragmentBundle? = null,
        addToBackStack: Boolean = true,
        launchMode: FragmentLaunchMode = FragmentLaunchMode.SAME_WINDOW
    ): T = mainIWindow.launchFragment(fragment, launchArgs, addToBackStack, launchMode)

    // Execution & Lifecycle
    fun run(factory: NativeFactory = GenericUI.requireFactory())
    fun exit(exitCode: Int = 0)
}
