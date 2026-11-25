package com.nexustools.genericui.core

import com.nexustools.genericui.component.fragment.FragmentLaunchCoordinator
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.exception.NativeUninitializedException
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.theme.ThemeConfig
import com.nexustools.genericui.threading.GenericUiThreading
import kotlin.reflect.KClass

/**
 * Global entry point for registering and retrieving the active platform [NativeFactory].
 */
object GenericUI {
    var activeFactory: NativeFactory? = null
    var applicationName: String = "GenericUI"
    var defaultThemeMode: ThemeMode = ThemeMode.AUTO
    var defaultThemeConfig: ThemeConfig = ThemeConfig.DEFAULT
    var appleIconExplicitlyProvided: Boolean = false

    val isInitialized: Boolean
        get() = activeFactory != null

    val backendTarget: BackendTarget
        get() = requireFactory().backendTarget

    val backendName: String
        get() = requireFactory().backendName

    val supportsMultipleWindows: Boolean
        get() = requireFactory().supportsMultipleWindows

    val supportsDialogWindows: Boolean
        get() = requireFactory().supportsDialogWindows

    val supportsToolModalWindows: Boolean
        get() = requireFactory().supportsToolModalWindows

    val supportsSystemTray: Boolean
        get() = requireFactory().supportsSystemTray

    val supportsSystemMenuBar: Boolean
        get() = requireFactory().supportsSystemMenuBar

    val supportsDirectorySelection: Boolean
        get() = requireFactory().supportsDirectorySelection

    val supportsDirectFileSystemAccess: Boolean
        get() = requireFactory().supportsDirectFileSystemAccess

    val supportsServices: Boolean
        get() = requireFactory().supportsServices

    val GENERICUI: ResourceID
        get() = Resources.GENERICUI
    val BOOTSTRAP: ResourceID
        get() = Resources.BOOTSTRAP
    val BOOTSTRAP_JS: ResourceID
        get() = Resources.BOOTSTRAP_JS
    val BOOTSTRAP_ICONS: ResourceID
        get() = Resources.BOOTSTRAP_ICONS
    val GENERICUI_NAMESPACE: ResourceID
        get() = Resources.GENERICUI_NAMESPACE
    val LOADING_IMAGE: ResourceID
        get() = Resources.LOADING_IMAGE

    fun initialize(factory: NativeFactory, appName: String? = null) {
        activeFactory = factory
        GenericUiThreading.onFactoryInitialized(factory)
        if (!appName.isNullOrBlank()) {
            applicationName = appName.trim()
        }
    }

    fun requireFactory(): NativeFactory =
        activeFactory ?: throw NativeUninitializedException("GenericUI", "requireFactory")

    fun canOpenAdditionalWindow(fragmentClass: KClass<out IFragment>): Boolean =
        requireFactory().supportsMultipleWindows &&
            !FragmentRegistry.isDialogFragment(fragmentClass) &&
            FragmentRegistry.supportsMultipleWindows(fragmentClass)

    inline fun <reified T : IFragment> canOpenAdditionalWindow(): Boolean =
        canOpenAdditionalWindow(T::class)

    /** Resets global factory, window/application tracking, and fragment coordinator state for unit tests. */
    fun resetForTests() {
        activeFactory = null
        Application.current = null
        Window.clearShownWindowsForTests()
        FragmentLaunchCoordinator.resetForTests()
        FragmentRegistry.clear()
        applicationName = "GenericUI"
        defaultThemeMode = ThemeMode.AUTO
        defaultThemeConfig = ThemeConfig.DEFAULT
        appleIconExplicitlyProvided = false
    }
}
