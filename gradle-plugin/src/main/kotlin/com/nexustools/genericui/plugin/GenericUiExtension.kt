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

    /** Core application identity and metadata (`genericui { app { ... } }`). */
    val app: GenericUiAppConfig = GenericUiAppConfig()

    /** Top-level window geometry and chrome defaults (`genericui { window { ... } }`). */
    val windowConfig: GenericUiWindowConfig = GenericUiWindowConfig()

    /** Top-level theme mode and color defaults (`genericui { theme { ... } }`). */
    val themeConfig: GenericUiThemeConfig = GenericUiThemeConfig()

    /** Variant scope configuration for [GenericUiVariantScope.COMMON]. */
    val common: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.COMMON)

    /** Variant scope configuration for [GenericUiVariantScope.SYSTEM_COMMON]. */
    val systemCommon: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.SYSTEM_COMMON)

    /** Variant scope configuration for [GenericUiVariantScope.DESKTOP_COMMON]. */
    val desktopCommon: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.DESKTOP_COMMON)

    /** Variant scope configuration for [GenericUiVariantScope.NATIVE_DESKTOP_COMMON]. */
    val nativeDesktopCommon: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.NATIVE_DESKTOP_COMMON)

    /** Deprecated alias for [nativeDesktopCommon]. */
    @Deprecated("Use nativeDesktopCommon", ReplaceWith("nativeDesktopCommon"))
    val desktopNativeCommon: GenericUiVariantConfig
        get() = nativeDesktopCommon

    /** Variant scope configuration for [GenericUiVariantScope.LINUX_COMMON]. */
    val linuxCommon: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.LINUX_COMMON)

    /** Variant scope configuration for [GenericUiVariantScope.MOBILE_COMMON]. */
    val mobileCommon: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.MOBILE_COMMON)

    /** Variant scope configuration for [GenericUiVariantScope.WINDOWS]. */
    val windows: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.WINDOWS)

    /** Variant scope configuration for [GenericUiVariantScope.MACOS]. */
    val macos: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.MACOS)

    /** Alias for [macos]. */
    val mac: GenericUiVariantConfig
        get() = macos

    /** Variant scope configuration for [GenericUiVariantScope.GTK]. */
    val gtk: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.GTK)

    /** Variant scope configuration for [GenericUiVariantScope.QT]. */
    val qt: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.QT)

    /** Variant scope configuration for [GenericUiVariantScope.SWING]. */
    val swing: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.SWING)

    /** Android variant scope configuration (`manifest { ... }`, `services { ... }`, `window { ... }`, `theme { ... }`). */
    val android: GenericUiAndroidConfig = GenericUiAndroidConfig()

    /** Variant scope configuration for [GenericUiVariantScope.IOS]. */
    val ios: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.IOS)

    /** Variant scope configuration for [GenericUiVariantScope.WEB]. */
    val web: GenericUiVariantConfig = GenericUiVariantConfig(GenericUiVariantScope.WEB)

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

    /**
     * Deprecated legacy shorthand retained only so unmigrated build scripts compile during multi-project evaluation.
     */
    @Deprecated(
        message = "Manual entryPoint is replaced by @MainAppFragment discovery and generated entrypoints."
    )
    @Suppress("DEPRECATION")
    var entryPoint: String
        get() = application.entryPoint
        set(value) {
            application.entryPoint = value
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

    /** Shorthand for [GenericUiStylesConfig.globalStyleSheet]. */
    var globalStyleSheet: String?
        get() = styles.globalStyleSheet ?: themeConfig.styleSheet ?: app.styleSheet
        set(value) {
            styles.globalStyleSheet = value
            onConfiguredCallback?.invoke()
        }

    /** Alias for [globalStyleSheet]. */
    var styleSheet: String?
        get() = globalStyleSheet
        set(value) {
            globalStyleSheet = value
        }

    /** Sets the global stylesheet path for this application. */
    fun styleSheet(path: String) {
        styles.globalStyleSheet = path
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

    /** Configures core application identity and metadata (`genericui { app { ... } }`). */
    fun app(action: Action<in GenericUiAppConfig>) {
        action.execute(app)
        if (app.id.isNotBlank()) application.appId = app.id
        if (app.name.isNotBlank()) application.appName = app.name
        if (app.versionExplicitlySet && app.version.isNotBlank()) application.appVersion = app.version
        if (app.description.isNotBlank()) application.description = app.description
        if (app.vendor.isNotBlank()) application.vendor = app.vendor
        if (app.packageName.isNotBlank() && application.androidNamespace.isBlank()) {
            application.androidNamespace = app.packageName
        }
        onConfiguredCallback?.invoke()
    }

    /** Configures top-level window settings (`genericui { window { ... } }`). */
    fun window(action: Action<in GenericUiWindowConfig>) {
        action.execute(windowConfig)
        onConfiguredCallback?.invoke()
    }

    /** Configures top-level theme mode and primary accent color (`genericui { theme { ... } }`). */
    fun theme(action: Action<in GenericUiThemeConfig>) {
        action.execute(themeConfig)
        themeConfig.color?.takeIf { it.isNotBlank() }?.let { application.themeColor = it }
        onConfiguredCallback?.invoke()
    }

    /** Configures the `common` scope (`genericui { common { ... } }`). */
    fun common(action: Action<in GenericUiVariantConfig>) {
        action.execute(common)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `systemCommon` scope (`genericui { systemCommon { ... } }`). */
    fun systemCommon(action: Action<in GenericUiVariantConfig>) {
        action.execute(systemCommon)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `desktopCommon` scope (`genericui { desktopCommon { ... } }`). */
    fun desktopCommon(action: Action<in GenericUiVariantConfig>) {
        action.execute(desktopCommon)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `nativeDesktopCommon` scope (`genericui { nativeDesktopCommon { ... } }`). */
    fun nativeDesktopCommon(action: Action<in GenericUiVariantConfig>) {
        action.execute(nativeDesktopCommon)
        onConfiguredCallback?.invoke()
    }

    /** Deprecated alias for [nativeDesktopCommon] (`genericui { desktopNativeCommon { ... } }`). */
    @Deprecated("Use nativeDesktopCommon", ReplaceWith("nativeDesktopCommon(action)"))
    fun desktopNativeCommon(action: Action<in GenericUiVariantConfig>) {
        action.execute(nativeDesktopCommon)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `linuxCommon` scope (`genericui { linuxCommon { ... } }`). */
    fun linuxCommon(action: Action<in GenericUiVariantConfig>) {
        action.execute(linuxCommon)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `mobileCommon` scope (`genericui { mobileCommon { ... } }`). */
    fun mobileCommon(action: Action<in GenericUiVariantConfig>) {
        action.execute(mobileCommon)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `windows` scope (`genericui { windows { ... } }`). */
    fun windows(action: Action<in GenericUiVariantConfig>) {
        action.execute(windows)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `macos` scope (`genericui { macos { ... } }`). */
    fun macos(action: Action<in GenericUiVariantConfig>) {
        action.execute(macos)
        onConfiguredCallback?.invoke()
    }

    /** Alias for [macos] (`genericui { mac { ... } }`). */
    fun mac(action: Action<in GenericUiVariantConfig>) {
        action.execute(macos)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `gtk` scope (`genericui { gtk { ... } }`). */
    fun gtk(action: Action<in GenericUiVariantConfig>) {
        action.execute(gtk)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `qt` scope (`genericui { qt { ... } }`). */
    fun qt(action: Action<in GenericUiVariantConfig>) {
        action.execute(qt)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `swing` scope (`genericui { swing { ... } }`). */
    fun swing(action: Action<in GenericUiVariantConfig>) {
        action.execute(swing)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `android` scope (`genericui { android { manifest { ... }; services { ... }; window { ... } } }`). */
    fun android(action: Action<in GenericUiAndroidConfig>) {
        action.execute(android)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `ios` scope (`genericui { ios { ... } }`). */
    fun ios(action: Action<in GenericUiVariantConfig>) {
        action.execute(ios)
        onConfiguredCallback?.invoke()
    }

    /** Configures the `web` scope (`genericui { web { ... } }`). */
    fun web(action: Action<in GenericUiVariantConfig>) {
        action.execute(web)
        onConfiguredCallback?.invoke()
    }

    /** Returns the [GenericUiVariantConfig] corresponding to [scope]. */
    fun variantConfigFor(scope: GenericUiVariantScope): GenericUiVariantConfig = when (scope) {
        GenericUiVariantScope.COMMON -> common
        GenericUiVariantScope.SYSTEM_COMMON -> systemCommon
        GenericUiVariantScope.DESKTOP_COMMON -> desktopCommon
        GenericUiVariantScope.MOBILE_COMMON -> mobileCommon
        GenericUiVariantScope.NATIVE_DESKTOP_COMMON -> nativeDesktopCommon
        GenericUiVariantScope.LINUX_COMMON -> linuxCommon
        GenericUiVariantScope.WINDOWS -> windows
        GenericUiVariantScope.MACOS -> macos
        GenericUiVariantScope.GTK -> gtk
        GenericUiVariantScope.QT -> qt
        GenericUiVariantScope.SWING -> swing
        GenericUiVariantScope.ANDROID -> android
        GenericUiVariantScope.IOS -> ios
        GenericUiVariantScope.WEB -> web
    }

    /** Resolves the effective [GenericUiResolvedVariantConfig] for [scope] along its specificity chain. */
    fun resolveVariantConfig(scope: GenericUiVariantScope): GenericUiResolvedVariantConfig =
        GenericUiVariantResolver.resolve(this, scope)
}
