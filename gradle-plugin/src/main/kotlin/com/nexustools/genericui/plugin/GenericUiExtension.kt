package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * Top-level `genericui { ... }` Gradle DSL extension registered by [GenericUiGradlePlugin].
 *
 * Configures Kotlin Multiplatform targets, the GenericUI sourceSet hierarchy (`common`, `commonMain`,
 * `desktopCommon`, `nativeDesktopCommon`, `windowsMain`, `macMain` / `macosMain`, `linuxCommon`,
 * `gtkMain`, `qtMain`, `swingMain`, `mobileCommon`, `androidMain`, `iosMain`, `webMain`),
 * Development/Production build target tasks (`windowsDevelopment`, `windowsProduction`,
 * `macDevelopment`, `macProduction`, `linuxDevelopmentGtk`, `linuxProductionGtk`,
 * `linuxDevelopmentQt`, `linuxProductionQt`, `swingDevelopment`, `swingProduction`,
 * `androidDevelopment`, `androidProduction`, `iosDevelopment`, `iosProduction`,
 * `webDevelopment`, `webProduction`), executable binaries, SASS->CSS/Kotlin compilation, and extensions.
 */
open class GenericUiExtension {
    /** Whether to automatically wire `:common` (`com.nexustools.genericui:common`) into `commonMainImplementation` / `implementation`. */
    var includeCommon: Boolean = true

    /**
     * Whether to automatically configure Kotlin Multiplatform targets, sourceSets (`webMain`, `gtkMain`,
     * `qtMain`, `windowsMain`, `macMain`, `nativeDesktopCommon`, `desktopCommon`, `swingMain`, `mobileCommon`,
     * `androidMain`, `iosMain`, `common`), and executable binaries based on [backends] and [application].
     */
    var configureMultiplatformTargets: Boolean = true

    /**
     * Whether to automatically include `org.jetbrains.kotlinx:kotlinx-coroutines-core` in `commonMain`
     * and `org.jetbrains.kotlin:kotlin-test` in `commonTest`.
     */
    var includeCoroutines: Boolean = true

    /** Version of `kotlinx-coroutines-core` added when [includeCoroutines] is `true`. */
    var coroutinesVersion: String = GenericUiMultiplatformConfigurator.DEFAULT_COROUTINES_VERSION

    /** Version string used when resolving GenericUI composite build or Maven coordinates. */
    var version: String = "0.1.0-SNAPSHOT"

    /** Application binary entry point, executable baseName, Windows GUI subsystem, and Android namespace config. */
    val application: GenericUiApplicationConfig = GenericUiApplicationConfig()

    /** Target platform backends configuration (`windows`, `macos`, `gtk`, `qt`, `swing`, `android`, `ios`, `web`). */
    val backends: GenericUiBackendsConfig = GenericUiBackendsConfig(webManifest = application.desktop.webManifest)

    /** Desktop client build configuration, Windows/macOS/Linux application icons, window settings, and app metadata. */
    val desktop: GenericUiDesktopConfig
        get() = application.desktop

    /** W3C / MDN Progressive Web App (PWA) Web App Manifest (`manifest.webmanifest`) configuration for the Web target. */
    val webManifest: GenericUiWebManifestConfig
        get() = application.desktop.webManifest

    /** GenericUI multiplatform sourceSets (`common`, `desktopCommon`, `nativeDesktopCommon`, `windowsMain`, `macMain`, `gtkMain`, `qtMain`, `swingMain`, `mobileCommon`, `androidMain`, `iosMain`, `webMain`). */
    val sourceSets: GenericUiSourceSetsConfig = GenericUiSourceSetsConfig()

    /** Native GPU drivers, ANGLE, MoltenVK, and Shaderc configuration. */
    val graphics: GenericUiGraphicsConfig = GenericUiGraphicsConfig()

    /** Optional first-party extension modules (`dsl`, `aiChat`, `threeJs`, `box2d`, `box3d`, etc.). */
    @get:JvmName("getExtensionsConfig")
    val extensions: GenericUiExtensionsConfig = GenericUiExtensionsConfig()

    /** SASS/CSS stylesheet compilation configuration. */
    val styles: GenericUiStylesConfig = GenericUiStylesConfig()

    /** Android-like `R` / `RC` cross-platform resource compiler & multi-backend translator configuration. */
    val resources: GenericUiResourcesConfig = GenericUiResourcesConfig()

    /** Cross-platform `JestElectricRunner` UI testing suite configuration (`:tests:*`). */
    val testing: GenericUiTestingConfig = GenericUiTestingConfig()

    /**
     * Tasks registered by companion plugins (e.g. `id("com.nexustools.njs")` `transpileNjsDependencies`)
     * that build or generate Kotlin dependencies prior to compiling GenericUI targets.
     */
    val dependencyBuildTasks: MutableSet<Any> = linkedSetOf()

    /**
     * Generated Kotlin source directories `(sourceSetName to srcDir)` registered by companion plugins.
     */
    val generatedKotlinDirs: MutableList<Pair<String, Any>> = mutableListOf()

    /**
     * Generated resource directories `(sourceSetName to resDir)` registered by companion plugins.
     */
    val generatedResourcesDirs: MutableList<Pair<String, Any>> = mutableListOf()

    /**
     * Callbacks registered by companion plugins to be invoked whenever GenericUI multiplatform targets are configured.
     */
    val externalTargetsConfiguredCallbacks: MutableList<Runnable> = mutableListOf()

    /** Callback invoked immediately when `backends { ... }` or `application { ... }` is configured during script evaluation. */
    internal var onConfiguredCallback: (() -> Unit)? = null

    /**
     * Registers a dependency-building task (by name, `Task`, or `TaskProvider`) so that all GenericUI
     * compile, resource, and platform target tasks (`windowsDevelopment`, `linuxDevelopmentGtk`, etc.)
     * automatically depend on it.
     */
    fun registerDependencyBuildTask(taskOrName: Any) {
        dependencyBuildTasks.add(taskOrName)
    }

    /**
     * Registers a generated Kotlin source directory into [sourceSetName] (e.g. `"commonMain"`, `"nativeDesktopMain"`).
     */
    fun registerGeneratedKotlinSrcDir(sourceSetName: String, srcDir: Any) {
        generatedKotlinDirs.add(sourceSetName to srcDir)
        sourceSets.maybeCreate(sourceSetName).kotlinSrcDir(srcDir)
    }

    /**
     * Registers a generated resources directory into [sourceSetName].
     */
    fun registerGeneratedResourcesSrcDir(sourceSetName: String, resDir: Any) {
        generatedResourcesDirs.add(sourceSetName to resDir)
        sourceSets.maybeCreate(sourceSetName).resourcesSrcDir(resDir)
    }

    /**
     * Registers an `implementation` dependency into [sourceSetName] from a companion plugin.
     */
    fun registerExternalDependency(sourceSetName: String, dependencyNotation: Any) {
        sourceSets.maybeCreate(sourceSetName).implementation(dependencyNotation)
    }

    /**
     * Registers a callback invoked after GenericUI configures its KMP targets and sourceSets.
     */
    fun onTargetsConfigured(callback: Runnable) {
        externalTargetsConfiguredCallbacks.add(callback)
    }

    /** Shorthand for [GenericUiApplicationConfig.entryPoint]. */
    var entryPoint: String
        get() = application.entryPoint
        set(value) {
            application.entryPoint = value
            onConfiguredCallback?.invoke()
        }

    /** Shorthand for [GenericUiApplicationConfig.baseName]. */
    var baseName: String
        get() = application.baseName
        set(value) {
            application.baseName = value
            onConfiguredCallback?.invoke()
        }

    /** Shorthand for [GenericUiApplicationConfig.mainClass]. */
    var mainClass: String
        get() = application.mainClass
        set(value) {
            application.mainClass = value
        }

    /** Shorthand for [GenericUiApplicationConfig.consoleApplication]. */
    var consoleApplication: Boolean
        get() = application.consoleApplication
        set(value) {
            application.consoleApplication = value
            onConfiguredCallback?.invoke()
        }

    /** Shorthand for [GenericUiApplicationConfig.androidNamespace]. */
    var androidNamespace: String
        get() = application.androidNamespace
        set(value) {
            application.androidNamespace = value
            onConfiguredCallback?.invoke()
        }

    /** Shorthand for [GenericUiApplicationConfig.themeColor]. */
    var themeColor: String
        get() = application.themeColor
        set(value) {
            application.themeColor = value
            onConfiguredCallback?.invoke()
        }

    /** Shorthand for [GenericUiApplicationConfig.allowInstallAsWebApp]. */
    var allowInstallAsWebApp: Boolean
        get() = application.allowInstallAsWebApp
        set(value) {
            application.allowInstallAsWebApp = value
            onConfiguredCallback?.invoke()
        }

    /** Configures target platform backends and immediately registers matching KMP targets & GenericUI sourceSets. */
    fun backends(action: Action<in GenericUiBackendsConfig>) {
        action.execute(backends)
        onConfiguredCallback?.invoke()
    }

    /** Configures executable entry point, binary baseName, Windows GUI subsystem, and Android namespace. */
    fun application(action: Action<in GenericUiApplicationConfig>) {
        action.execute(application)
        onConfiguredCallback?.invoke()
    }

    /** Configures desktop client builds, icons (`windowsIcon`, `macIcon`, `linuxIcon`), app metadata, and window settings. */
    fun desktop(action: Action<in GenericUiDesktopConfig>) {
        action.execute(application.desktop)
        onConfiguredCallback?.invoke()
    }

    /** Sets the default fallback icon path across all desktop platforms (`icon.png` / `icon.ico` / `icon.icns`). */
    fun icon(path: String) {
        application.icon(path)
        onConfiguredCallback?.invoke()
    }

    /** Sets the Windows desktop executable & titlebar icon path (`.ico`). */
    fun windowsIcon(path: String) {
        application.windowsIcon(path)
        onConfiguredCallback?.invoke()
    }

    /** Sets the macOS application bundle icon path (`.icns` or `.png`). */
    fun macIcon(path: String) {
        application.macIcon(path)
        onConfiguredCallback?.invoke()
    }

    /** Alias for [macIcon]. */
    fun macosIcon(path: String) {
        application.macosIcon(path)
        onConfiguredCallback?.invoke()
    }

    /** Sets the Linux GTK/Qt desktop application icon path (`.png` or `.svg`). */
    fun linuxIcon(path: String) {
        application.linuxIcon(path)
        onConfiguredCallback?.invoke()
    }

    /** Sets the Web icon path overriding the default project icon for the Web splash screen and `index.html` favicon. */
    fun webIcon(path: String) {
        application.webIcon(path)
        onConfiguredCallback?.invoke()
    }

    /** Sets the application theme / accent color (e.g. `"#0d6efd"`) across all backends that support it. */
    fun themeColor(color: String) {
        application.themeColor(color)
        onConfiguredCallback?.invoke()
    }

    /** Configures the W3C / MDN Progressive Web App (PWA) `manifest.webmanifest` and standalone WebApp installation (`allowInstallAsWebApp`). */
    fun webManifest(action: Action<in GenericUiWebManifestConfig>) {
        application.webManifest(action)
        onConfiguredCallback?.invoke()
    }

    /** Configures Web-specific build settings, including Web icon, `manifest.webmanifest`, and `allowInstallAsWebApp`. */
    fun webBuild(
        icon: String = application.desktop.webIcon,
        generateManifest: Boolean = application.desktop.webManifest.enabled || application.desktop.webManifest.allowInstallAsWebApp,
        allowInstallAsWebApp: Boolean = application.desktop.webManifest.allowInstallAsWebApp,
        shortName: String = application.desktop.webManifest.shortName,
        themeColor: String = application.desktop.themeColor,
        backgroundColor: String = application.desktop.webManifest.backgroundColor,
        display: String = application.desktop.webManifest.display,
        orientation: String = application.desktop.webManifest.orientation,
        startUrl: String = application.desktop.webManifest.startUrl,
        scope: String = application.desktop.webManifest.scope,
        categories: List<String> = application.desktop.webManifest.categories
    ) {
        application.webBuild(
            icon = icon,
            generateManifest = generateManifest,
            allowInstallAsWebApp = allowInstallAsWebApp,
            shortName = shortName,
            themeColor = themeColor,
            backgroundColor = backgroundColor,
            display = display,
            orientation = orientation,
            startUrl = startUrl,
            scope = scope,
            categories = categories
        )
        onConfiguredCallback?.invoke()
    }

    /** Configures platform-specific desktop and web icons in a single call. */
    fun icons(
        windows: String = application.desktop.windowsIcon,
        mac: String = application.desktop.macIcon,
        linux: String = application.desktop.linuxIcon,
        web: String = application.desktop.webIcon,
        common: String = application.desktop.icon
    ) {
        application.icons(windows = windows, mac = mac, linux = linux, web = web, common = common)
        onConfiguredCallback?.invoke()
    }

    /** Configures application metadata and OS resource fields (`VERSIONINFO`, `Info.plist`, `.desktop`). */
    fun metadata(
        appName: String = application.desktop.appName,
        appId: String = application.desktop.appId,
        appVersion: String = application.desktop.appVersion,
        vendor: String = application.desktop.vendor,
        description: String = application.desktop.description,
        copyright: String = application.desktop.copyright,
        license: String = application.desktop.license,
        homepageUrl: String = application.desktop.homepageUrl,
        issuesUrl: String = application.desktop.issuesUrl,
        themeColor: String = application.desktop.themeColor
    ) {
        application.metadata(
            appName = appName,
            appId = appId,
            appVersion = appVersion,
            vendor = vendor,
            description = description,
            copyright = copyright,
            license = license,
            homepageUrl = homepageUrl,
            issuesUrl = issuesUrl,
            themeColor = themeColor
        )
        onConfiguredCallback?.invoke()
    }

    /** Configures default desktop window title, initial size, minimum size, and dark titlebar settings. */
    fun window(
        title: String = application.desktop.windowTitle,
        width: Int = application.desktop.windowWidth,
        height: Int = application.desktop.windowHeight,
        minWidth: Int = application.desktop.minWindowWidth,
        minHeight: Int = application.desktop.minWindowHeight,
        resizable: Boolean = application.desktop.resizable
    ) {
        application.window(
            title = title,
            width = width,
            height = height,
            minWidth = minWidth,
            minHeight = minHeight,
            resizable = resizable
        )
        onConfiguredCallback?.invoke()
    }

    /** Configures Windows client build & resource settings (`.ico`, `VERSIONINFO`, DPI awareness). */
    fun windowsBuild(
        icon: String = application.desktop.windowsIcon,
        installSubdir: String = application.desktop.windowsInstallSubdir,
        msiUpgradeCode: String = application.desktop.windowsMsiUpgradeCode,
        requireAdmin: Boolean = application.desktop.requireAdminOnWindows,
        highDpiAware: Boolean = application.desktop.highDpiAware
    ) {
        application.windowsBuild(
            icon = icon,
            installSubdir = installSubdir,
            msiUpgradeCode = msiUpgradeCode,
            requireAdmin = requireAdmin,
            highDpiAware = highDpiAware
        )
        onConfiguredCallback?.invoke()
    }

    /** Configures macOS `.app` bundle & `Info.plist` settings (`.icns`/`.png` icon, bundle ID, minimum macOS version, category). */
    fun macBuild(
        icon: String = application.desktop.macIcon,
        bundleId: String = application.desktop.appId,
        category: String = application.desktop.macCategory,
        minimumSystemVersion: String = application.desktop.macMinimumSystemVersion
    ) {
        application.macBuild(
            icon = icon,
            bundleId = bundleId,
            category = category,
            minimumSystemVersion = minimumSystemVersion
        )
        onConfiguredCallback?.invoke()
    }

    /** Configures Linux GTK/Qt desktop entry & packaging settings (`.png`/`.svg` icon, `.desktop` categories). */
    fun linuxBuild(
        icon: String = application.desktop.linuxIcon,
        categories: String = application.desktop.linuxCategories
    ) {
        application.linuxBuild(
            icon = icon,
            categories = categories
        )
        onConfiguredCallback?.invoke()
    }

    /** Adds a custom key-value entry to the generated OS resource metadata (`VERSIONINFO`, `Info.plist`, `.desktop`). */
    fun resource(key: String, value: String) {
        application.resource(key, value)
    }

    /** Stages an additional asset or resource directory/file alongside the compiled desktop binaries. */
    fun bundleResource(relativePath: String) {
        application.bundleResource(relativePath)
    }

    /** Configures GenericUI multiplatform sourceSet dependencies (`common`, `desktopCommon`, `nativeDesktopCommon`, `windowsMain`, `macMain`, `gtkMain`, `qtMain`, `swingMain`, `mobileCommon`, `androidMain`, `iosMain`, `webMain`). */
    fun sourceSets(action: Action<in GenericUiSourceSetsConfig>) {
        onConfiguredCallback?.invoke()
        action.execute(sourceSets)
    }

    /** Configures GPU acceleration, ANGLE, MoltenVK, and Shaderc bundling. */
    fun graphics(action: Action<in GenericUiGraphicsConfig>) {
        action.execute(graphics)
    }

    /** Configures first-party extension modules (`:extensions:aichat`, `:extensions:threejs`, `:extensions:box2d`, `:extensions:box3d`, etc.). */
    fun extensions(action: Action<in GenericUiExtensionsConfig>) {
        action.execute(extensions)
    }

    /** Configures `.scss` / `.sass` compilation into `.css` bundles and precompiled Kotlin objects. */
    fun styles(action: Action<in GenericUiStylesConfig>) {
        action.execute(styles)
    }

    /** Configures Android-like `R` / `RC` cross-platform resource compilation and backend translation. */
    fun resources(action: Action<in GenericUiResourcesConfig>) {
        action.execute(resources)
    }

    /** Configures the `JestElectricRunner` UI testing suite (`:tests:*`) and Windows visual test runner. */
    fun testing(action: Action<in GenericUiTestingConfig>) {
        action.execute(testing)
    }
}
