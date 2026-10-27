package com.nexustools.genericui.core

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.shell.FragmentApplication
import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.Size
import kotlin.reflect.KClass

/**
 * Shared startup and bootstrap coordinator invoked by generated platform entrypoints
 * across all 8 GenericUI backend targets (`windows`, `macos`, `gtk`, `qt`, `swing`, `android`, `ios`, `web`).
 */
object GenericUiLaunchSequence {

    /**
     * Initializes [GenericUI], installs resources and extension providers, registers discovered
     * fragments into [FragmentRegistry], constructs the root [IApplication] / `@MainAppFragment`
     * (forwarding command-line [args] inside a [FragmentBundle] on system targets), and configures
     * the primary window and theme.
     */
    fun prepareApplication(
        factory: NativeFactory? = GenericUI.activeFactory,
        args: Array<String> = emptyArray(),
        descriptor: GenericUiAppDescriptor,
        isSystemTarget: Boolean = factory?.backendTarget?.isSystem ?: true
    ): IApplication {
        GenericUI.applicationName = descriptor.appName
        if (factory != null) {
            GenericUI.initialize(factory, descriptor.appName)
        }

        descriptor.resourceRegistryInstaller?.invoke()
        for (installer in descriptor.providerInstallers) {
            installer.invoke()
        }

        for ((fragClass, fragFactory) in descriptor.fragmentRegistrations) {
            registerFragment(fragClass, fragFactory)
        }
        if (descriptor.mainFragmentClass != null && descriptor.mainFragmentFactory != null) {
            registerFragment(descriptor.mainFragmentClass, descriptor.mainFragmentFactory)
        }

        val launchBundle: FragmentBundle? = if (isSystemTarget) {
            FragmentBundle.fromCommandLineArgs(args)
        } else {
            null
        }

        val resolvedThemeMode = ThemeMode.fromString(descriptor.themeMode)

        val app: IApplication = if (descriptor.applicationFactory != null) {
            descriptor.applicationFactory.invoke(launchBundle).also { createdApp ->
                createdApp.setThemeMode(resolvedThemeMode)
                if (descriptor.themeColor.isNotBlank()) {
                    createdApp.setThemeColor(descriptor.themeColor)
                }
            }
        } else {
            val mainFragment = descriptor.mainFragmentFactory?.invoke(launchBundle)
                ?: descriptor.mainFragmentClass?.let { FragmentRegistry.instantiate(it, launchBundle) }
                ?: Fragment()
            if (launchBundle != null && mainFragment.arguments == null) {
                mainFragment.arguments = launchBundle
            }
            FragmentApplication(
                initialFragment = mainFragment,
                title = descriptor.windowTitle,
                initialThemeColor = descriptor.themeColor.takeIf { it.isNotBlank() }
            ).also { createdApp ->
                createdApp.setThemeMode(resolvedThemeMode)
                if (descriptor.themeColor.isNotBlank()) {
                    createdApp.setThemeColor(descriptor.themeColor)
                }
            }
        }

        val mainWindow = app.mainWindow
        if (descriptor.windowTitle.isNotBlank()) {
            mainWindow.setTitle(descriptor.windowTitle)
        }
        mainWindow.setWindowSize(Size(descriptor.windowWidth, descriptor.windowHeight))
        if (descriptor.fullscreen) {
            mainWindow.setFullscreen(true)
        }
        val iconUriOrPath = descriptor.iconPath?.takeIf { it.isNotBlank() }
            ?: descriptor.iconResourceId?.toResourceUri()
        if (iconUriOrPath != null) {
            mainWindow.setWindowIcon(iconUriOrPath)
        }

        descriptor.globalStyleSheetResourceId?.let { app.addStyleSheet(it) }
        descriptor.globalStyleSheetPath?.takeIf { it.isNotBlank() }?.let { app.addStyleSheet(it) }
        descriptor.precompiledStyleSheetInstaller?.invoke(app)
        return app
    }

    /**
     * Prepares the [IApplication] via [prepareApplication] and starts its platform event loop via [IApplication.run].
     */
    fun launch(
        factory: NativeFactory,
        args: Array<String> = emptyArray(),
        descriptor: GenericUiAppDescriptor,
        isSystemTarget: Boolean = factory.backendTarget.isSystem
    ): IApplication {
        val app = prepareApplication(factory, args, descriptor, isSystemTarget)
        app.run(factory)
        return app
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerFragment(
        fragClass: KClass<out Fragment>,
        fragFactory: (FragmentBundle?) -> Fragment
    ) {
        FragmentRegistry.register(fragClass as KClass<Fragment>, fragFactory)
    }
}
