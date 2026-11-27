package com.nexustools.genericui.core

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentMetadata
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.FragmentApplication
import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.resources.ResourceResolver
import kotlin.reflect.KClass

/**
 * Shared startup and bootstrap coordinator invoked by generated platform entrypoints
 * across all 8 GenericUI backend targets (`windows`, `macos`, `gtk`, `kde`, `swing`, `android`, `ios`, `web`).
 */
object GenericUiLaunchSequence {

    /**
     * Initializes [GenericUI], installs resources and extension providers, registers discovered
     * fragments into [FragmentRegistry], constructs the root [IApplication] / `@MainAppFragment`
     * (forwarding command-line [args] inside a [FragmentBundle] on system targets), and configures
     * the primary window and theme.
     *
     * On Desktop targets (`windows`, `macos`, `gtk`, `kde`, `swing`), if [GenericUiAppDescriptor.applicationFactory]
     * is provided (`@AppApplication`), the custom [Application] is instantiated and its overridable
     * [Application.onCreateMainFragment] / [Application.createMainFragment] routine is invoked.
     * Mobile (`android`, `ios`) and Web (`web`) targets ignore [GenericUiAppDescriptor.applicationFactory]
     * for fragment creation.
     */
    fun prepareApplication(
        factory: NativeFactory? = GenericUI.activeFactory,
        args: Array<String> = emptyArray(),
        descriptor: GenericUiAppDescriptor,
        isSystemTarget: Boolean = factory?.backendTarget?.isSystem ?: true,
        initialLaunchArgs: FragmentBundle? = null
    ): IApplication {
        GenericUI.applicationName = descriptor.appName
        GenericUI.appVersion = descriptor.appVersion
        GenericUI.appGitHash = descriptor.gitHash
        GenericUI.appCopyright = descriptor.copyright
        GenericUI.appleIconExplicitlyProvided = descriptor.appleIconExplicitlyProvided
        if (factory != null) {
            GenericUI.initialize(factory, descriptor.appName)
        }

        ResourceResolver.ensureAllModulesInitialized()
        descriptor.resourceRegistryInstaller?.invoke()
        for (installer in descriptor.providerInstallers) {
            installer.invoke()
        }
        GenericUiExtensionRegistry.dispatchBeforeAppPrepare(descriptor)

        for ((fragClass, fragId) in descriptor.fragmentIds) {
            if (!descriptor.fragmentMetadata.containsKey(fragClass)) {
                val route = descriptor.fragmentRoutes[fragClass].orEmpty()
                registerFragmentMetadata(fragClass, fragId, route)
            }
        }
        for ((fragClass, route) in descriptor.fragmentRoutes) {
            if (!descriptor.fragmentIds.containsKey(fragClass) && !descriptor.fragmentMetadata.containsKey(fragClass)) {
                val resolvedId = FragmentRegistry.resolveFragmentId(fragClass)
                registerFragmentMetadata(fragClass, resolvedId, route)
            }
        }
        for ((fragClass, metadata) in descriptor.fragmentMetadata) {
            registerFragmentMetadata(fragClass, metadata)
        }
        for ((fragClass, fragFactory) in descriptor.fragmentRegistrations) {
            registerFragment(fragClass, fragFactory)
        }
        if (descriptor.mainFragmentClass != null && descriptor.mainFragmentFactory != null) {
            registerFragment(descriptor.mainFragmentClass, descriptor.mainFragmentFactory)
        }

        val launchBundle: FragmentBundle? = if (isSystemTarget) {
            FragmentBundle().apply {
                if (initialLaunchArgs != null && initialLaunchArgs.keys().isNotEmpty()) {
                    putAll(initialLaunchArgs)
                }
                putAll(FragmentBundle.fromCommandLineArgs(args))
            }
        } else {
            initialLaunchArgs?.takeIf { it.keys().isNotEmpty() }
        }

        val requestedFragmentId = launchBundle
            ?.getString(FragmentBundle.KEY_LAUNCH_FRAGMENT_ID)
            ?.takeIf { it.isNotBlank() }
        val requestedRoute = launchBundle
            ?.getString(FragmentBundle.KEY_LAUNCH_ROUTE)
            ?.takeIf { it.isNotBlank() }
        val requestedTargetClass: KClass<out Fragment>? =
            requestedFragmentId?.let { FragmentRegistry.resolveFragmentClass(it) }
                ?: requestedRoute?.let { FragmentRegistry.resolveFragmentClass(it) }
        val requestedLaunchMode = launchBundle
            ?.getString(FragmentBundle.KEY_LAUNCH_MODE)
            ?.trim()
            ?.uppercase()
        val requestedOpenAdditionalWindow =
            launchBundle?.getBoolean(FragmentBundle.KEY_LAUNCH_OPEN_ADDITIONAL_WINDOW, false) == true ||
                requestedLaunchMode == "NEW_WINDOW" ||
                requestedLaunchMode == "NEW_PANEL_WINDOW" ||
                requestedLaunchMode == "NEW_DIALOG_WINDOW"
        val isTargetDialog = requestedTargetClass != null && FragmentRegistry.isDialogFragment(requestedTargetClass)
        val isSecondaryTarget = requestedTargetClass != null &&
            descriptor.mainFragmentClass != null &&
            requestedTargetClass != descriptor.mainFragmentClass
        val suppressInitialMainWindow = isSecondaryTarget &&
            requestedLaunchMode != "SAME_WINDOW" &&
            (isTargetDialog || requestedOpenAdditionalWindow || factory?.supportsMultipleWindows != false)

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
            if (createdApp is Application) {
                createdApp.applyDescriptorThemeDefaults(
                    defaultThemeConfig = effectiveThemeConfig,
                    defaultThemeMode = resolvedThemeMode,
                    defaultThemeColor = descriptor.themeColor
                )
                GenericUI.defaultThemeMode = createdApp.getThemeMode()
                GenericUI.defaultThemeConfig = createdApp.getThemeConfig()
            } else {
                createdApp.setThemeConfig(effectiveThemeConfig)
                createdApp.setThemeMode(resolvedThemeMode)
                if (descriptor.themeColor.isNotBlank()) {
                    createdApp.setThemeColor(descriptor.themeColor)
                }
            }
            val mainWindow = createdApp.mainWindow
            if (descriptor.windowTitle.isNotBlank() &&
                (mainWindow.getTitle().isBlank() || mainWindow.getTitle() == "GenericUI Application")
            ) {
                mainWindow.setTitle(descriptor.windowTitle)
            }
            if (descriptor.windowAlwaysOnTop) {
                mainWindow.setAlwaysOnTop(true)
            }
            if (mainWindow.getMinWindowSize() == null &&
                (descriptor.minWindowWidth != 320 || descriptor.minWindowHeight != 240)
            ) {
                mainWindow.setMinWindowSize(Size(descriptor.minWindowWidth, descriptor.minWindowHeight))
            }
            descriptor.windowAspectRatio?.let { ratio ->
                if (mainWindow.getAspectRatio() == null) {
                    mainWindow.setAspectRatio(ratio)
                }
            }
            if (descriptor.windowSizeExplicitlySet) {
                createdApp.setDefaultMainWindowGeometry(descriptor.windowWidth, descriptor.windowHeight)
                mainWindow.setWindowSize(Size(descriptor.windowWidth, descriptor.windowHeight))
            }
            if (descriptor.fullscreen || descriptor.fullscreenExplicitlySet) {
                createdApp.defaultMainWindowFullscreen = descriptor.fullscreen
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
                createdApp.mainWindow.fragmentContainer?.currentFragment?.let { active ->
                    if (launchBundle != null && active.arguments == null) {
                        active.arguments = launchBundle
                    }
                }
                descriptor.mainFragmentFactory?.let { fragFactory ->
                    createdApp.bindDefaultMainFragmentFactory { rawArgs ->
                        fragFactory(FragmentBundle.fromCommandLineArgs(rawArgs))
                    }
                }
                if (suppressInitialMainWindow) {
                    createdApp.suppressInitialMainWindowShow = true
                    createdApp.mainWindow.fragmentContainer?.clearCurrentFragment()
                } else if (isSecondaryTarget && requestedTargetClass != null) {
                    val targetFragment = FragmentRegistry.instantiate(requestedTargetClass, launchBundle)
                    createdApp.setFragment(targetFragment)
                } else if (mainWindow.getContent() == null && mainWindow.fragmentContainer?.currentFragment == null) {
                    val frag = createdApp.onCreateMainFragment(launchBundle)
                    if (frag != null && mainWindow.fragmentContainer?.currentFragment == null && mainWindow.getContent() == null) {
                        createdApp.setFragment(frag)
                    }
                }
                createdApp.onLaunchArgumentsApplied(launchBundle)
            }
            return createdApp.also {
                GenericUiExtensionRegistry.dispatchAppPrepared(it, descriptor)
            }
        }

        val initialFragment: Fragment? = when {
            suppressInitialMainWindow -> null
            isSecondaryTarget && requestedTargetClass != null ->
                FragmentRegistry.instantiate(requestedTargetClass, launchBundle)
            else -> descriptor.mainFragmentFactory?.invoke(launchBundle)
                ?: descriptor.mainFragmentClass?.let { FragmentRegistry.instantiate(it, launchBundle) }
                ?: Fragment()
        }
        if (initialFragment != null && launchBundle != null && initialFragment.arguments == null) {
            initialFragment.arguments = launchBundle
        }
        val app: FragmentApplication = FragmentApplication(
            initialFragment = initialFragment,
            title = descriptor.windowTitle,
            initialThemeColor = descriptor.themeColor.takeIf { it.isNotBlank() }
        ).also { createdApp ->
            createdApp.mainFragmentClass = descriptor.mainFragmentClass
            createdApp.mainFragmentFactory = descriptor.mainFragmentFactory
            createdApp.launchArguments = launchBundle
            if (suppressInitialMainWindow) {
                createdApp.suppressInitialMainWindowShow = true
                createdApp.mainWindow.fragmentContainer?.clearCurrentFragment()
            }
            createdApp.setThemeConfig(effectiveThemeConfig)
            createdApp.setThemeMode(resolvedThemeMode)
            if (descriptor.themeColor.isNotBlank()) {
                createdApp.setThemeColor(descriptor.themeColor)
            }
            createdApp.onLaunchArgumentsApplied(launchBundle)
        }

        val mainWindow = app.mainWindow
        if (descriptor.windowTitle.isNotBlank()) {
            mainWindow.setTitle(descriptor.windowTitle)
        }
        if (descriptor.windowAlwaysOnTop) {
            mainWindow.setAlwaysOnTop(true)
        }
        if (descriptor.minWindowWidth != 320 || descriptor.minWindowHeight != 240) {
            mainWindow.setMinWindowSize(Size(descriptor.minWindowWidth, descriptor.minWindowHeight))
        }
        descriptor.windowAspectRatio?.let { ratio ->
            mainWindow.setAspectRatio(ratio)
        }
        if (descriptor.windowSizeExplicitlySet) {
            app.setDefaultMainWindowGeometry(descriptor.windowWidth, descriptor.windowHeight)
            mainWindow.setWindowSize(Size(descriptor.windowWidth, descriptor.windowHeight))
        }
        if (descriptor.fullscreen || descriptor.fullscreenExplicitlySet) {
            app.defaultMainWindowFullscreen = descriptor.fullscreen
        }
        val iconUriOrPath = descriptor.iconPath?.takeIf { it.isNotBlank() }
            ?: descriptor.iconResourceId?.toResourceUri()
        if (iconUriOrPath != null) {
            mainWindow.setWindowIcon(iconUriOrPath)
        }

        descriptor.globalStyleSheetResourceId?.let { app.addStyleSheet(it) }
        descriptor.globalStyleSheetPath?.takeIf { it.isNotBlank() }?.let { app.addStyleSheet(it) }
        descriptor.precompiledStyleSheetInstaller?.invoke(app)
        GenericUiExtensionRegistry.dispatchAppPrepared(app, descriptor)
        return app
    }

    /**
     * Prepares the [IApplication] via [prepareApplication] and starts its platform event loop via [IApplication.run].
     */
    fun launch(
        factory: NativeFactory,
        args: Array<String> = emptyArray(),
        descriptor: GenericUiAppDescriptor,
        isSystemTarget: Boolean = factory.backendTarget.isSystem,
        initialLaunchArgs: FragmentBundle? = null
    ): IApplication {
        val app = prepareApplication(factory, args, descriptor, isSystemTarget, initialLaunchArgs)
        GenericUiExtensionRegistry.dispatchBeforeAppRun(app, factory)
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

    @Suppress("UNCHECKED_CAST")
    private fun registerFragmentMetadata(
        fragClass: KClass<out Fragment>,
        fragmentId: String,
        route: String
    ) {
        FragmentRegistry.registerMetadata(fragClass as KClass<Fragment>, fragmentId, route)
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerFragmentMetadata(
        fragClass: KClass<out Fragment>,
        metadata: FragmentMetadata
    ) {
        FragmentRegistry.registerMetadata(fragClass as KClass<Fragment>, metadata)
    }
}
