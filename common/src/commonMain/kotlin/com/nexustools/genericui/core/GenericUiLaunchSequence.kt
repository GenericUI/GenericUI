package com.nexustools.genericui.core

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.shell.Application
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
     *
     * On Desktop targets (`windows`, `macos`, `gtk`, `qt`, `swing`), if [GenericUiAppDescriptor.applicationFactory]
     * is provided (`@AppApplication`), the custom [Application] is instantiated and its overridable
     * [Application.onCreateMainFragment] / [Application.createMainFragment] routine is invoked.
     * Mobile (`android`, `ios`) and Web (`web`) targets ignore [GenericUiAppDescriptor.applicationFactory]
     * for fragment creation.
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
        val effectiveThemeConfig = descriptor.themeConfig
            .withMode(resolvedThemeMode)
            .let { cfg ->
                if (cfg.colors.primary == null && cfg.colors.accent == null && descriptor.themeColor.isNotBlank()) {
                    cfg.withPrimaryAccentColor(descriptor.themeColor)
                } else {
                    cfg
                }
            }
        GenericUI.defaultThemeMode = resolvedThemeMode
        GenericUI.defaultThemeConfig = effectiveThemeConfig
        val isDesktopTarget = factory?.backendTarget?.isDesktop ?: isSystemTarget

        if (isDesktopTarget && descriptor.applicationFactory != null) {
            val createdApp = descriptor.applicationFactory.invoke(launchBundle)
            createdApp.setThemeConfig(effectiveThemeConfig)
            createdApp.setThemeMode(resolvedThemeMode)
            if (descriptor.themeColor.isNotBlank()) {
                createdApp.setThemeColor(descriptor.themeColor)
            }
            val mainWindow = createdApp.mainWindow
            if (descriptor.windowTitle.isNotBlank() &&
                (mainWindow.getTitle().isBlank() || mainWindow.getTitle() == "GenericUI Application")
            ) {
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
            descriptor.globalStyleSheetResourceId?.let { createdApp.addStyleSheet(it) }
            descriptor.globalStyleSheetPath?.takeIf { it.isNotBlank() }?.let { createdApp.addStyleSheet(it) }
            descriptor.precompiledStyleSheetInstaller?.invoke(createdApp)

            if (createdApp is Application) {
                createdApp.mainFragmentClass = descriptor.mainFragmentClass
                createdApp.mainFragmentFactory = descriptor.mainFragmentFactory
                createdApp.launchArguments = launchBundle
                descriptor.mainFragmentFactory?.let { fragFactory ->
                    createdApp.bindDefaultMainFragmentFactory { rawArgs ->
                        fragFactory(FragmentBundle.fromCommandLineArgs(rawArgs))
                    }
                }
                if (mainWindow.getContent() == null && mainWindow.fragmentContainer?.currentFragment == null) {
                    val frag = createdApp.onCreateMainFragment(launchBundle)
                    if (frag != null && mainWindow.fragmentContainer?.currentFragment == null && mainWindow.getContent() == null) {
                        createdApp.setFragment(frag)
                    }
                }
            }
            return createdApp
        }

        val mainFragment = descriptor.mainFragmentFactory?.invoke(launchBundle)
            ?: descriptor.mainFragmentClass?.let { FragmentRegistry.instantiate(it, launchBundle) }
            ?: Fragment()
        if (launchBundle != null && mainFragment.arguments == null) {
            mainFragment.arguments = launchBundle
        }
        val app: IApplication = FragmentApplication(
            initialFragment = mainFragment,
            title = descriptor.windowTitle,
            initialThemeColor = descriptor.themeColor.takeIf { it.isNotBlank() }
        ).also { createdApp ->
            createdApp.setThemeConfig(effectiveThemeConfig)
            createdApp.setThemeMode(resolvedThemeMode)
            if (descriptor.themeColor.isNotBlank()) {
                createdApp.setThemeColor(descriptor.themeColor)
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

    /**
     * Desktop-specific launch helper that binds [defaultFragmentFactory] onto the [Application] returned by
     * [appProvider], invokes [Application.onCreateMainFragment] (unless content or a fragment was already set),
     * and starts the desktop event loop via [Application.run].
     */
    fun launchDesktop(
        factory: NativeFactory,
        appProvider: () -> Application,
        defaultFragmentFactory: (Array<String>) -> Fragment,
        title: String,
        args: Array<String> = emptyArray()
    ): Application {
        GenericUI.initialize(factory)
        val launchBundle = FragmentBundle.fromCommandLineArgs(args)
        val app = appProvider()
        app.launchArguments = launchBundle
        app.bindDefaultMainFragmentFactory(defaultFragmentFactory)
        app.mainFragmentFactory = { bundle ->
            defaultFragmentFactory(bundle?.getCommandLineArgsArray() ?: args)
        }
        if (title.isNotBlank() && (app.mainWindow.getTitle().isBlank() || app.mainWindow.getTitle() == "GenericUI Application")) {
            app.mainWindow.setTitle(title)
        }
        if (app.mainWindow.getContent() == null && app.mainWindow.fragmentContainer?.currentFragment == null) {
            val fragment = app.onCreateMainFragment(args)
            if (fragment != null && app.mainWindow.fragmentContainer?.currentFragment == null && app.mainWindow.getContent() == null) {
                app.setFragment(fragment)
            }
        }
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
