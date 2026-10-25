package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * DSL configuration block (`genericui { desktop { ... } }` and synced with `genericui { application { ... } }`)
 * for configuring desktop, mobile, and web application metadata, `themeColor`, platform icons
 * (`windowsIcon`, `macIcon`, `linuxIcon`, `webIcon`), W3C Progressive Web App manifest (`webManifest`),
 * OS version/copyright/vendor resource metadata, window dimensions, macOS `.app` bundle `Info.plist` settings,
 * Windows PE `.rc`/`.res` version & icon resources, and Linux `.desktop` entry metadata.
 */
open class GenericUiDesktopConfig(
    val webManifest: GenericUiWebManifestConfig = GenericUiWebManifestConfig()
) {

    /** Human-readable application display name (e.g. `"NXTConverter"`). */
    var appName: String = ""

    /** Reverse-DNS application / bundle identifier (e.g. `"com.nexustools.convert"`). */
    var appId: String = ""

    /** Marketing / display version string (defaults to `project.version`, e.g. `"1.0.0"`). */
    var appVersion: String = ""

    /** Organization / publisher / vendor name (e.g. `"NexusTools"`). */
    var vendor: String = "NexusTools"

    /** Application summary description embedded in Windows `FileDescription`, Web `manifest.webmanifest`, and Linux `.desktop` `Comment`. */
    var description: String = ""

    /** Legal copyright notice embedded in Windows `LegalCopyright` and macOS `NSHumanReadableCopyright`. */
    var copyright: String = ""

    /** SPDX license identifier or license name (e.g. `"GPL-3.0"` or `"MIT"`). */
    var license: String = ""

    /** Project homepage or repository URL. */
    var homepageUrl: String = ""

    /** Issue tracker URL (e.g. `"https://github.com/NexusTools/convert/issues"`). */
    var issuesUrl: String = ""

    /**
     * Application theme / accent color (e.g. `"#0d6efd"` or `"#2A80FF"`), propagated across all
     * backends that support it:
     * - **Web**: `<meta id="genericui-theme-color" name="theme-color" content="...">` in `index.html`,
     *   `theme_color` in `manifest.webmanifest`, and `--genericui-theme-color` CSS custom property.
     * - **Android**: `<color name="theme_color">` and `<color name="colorPrimary">` in `res/values/colors.xml`
     *   plus `RC.color.theme_color` in `R.kt` / `RC.kt` for status bar, TaskDescription, and Material primary tinting.
     * - **iOS & macOS**: `AccentColor.colorset` and `theme_color.colorset` in `Assets.xcassets`, plus `GenericUIThemeColor` in `Info.plist`.
     * - **Windows & Linux Desktop**: `VALUE "ThemeColor"` in Windows `.rc` `VERSIONINFO`, `X-GenericUI-ThemeColor` in Linux `.desktop`,
     *   and `--rc-color-theme-color` / `--genericui-theme-color` in `genericui-resource-vars.css`.
     */
    var themeColor: String = ""

    /**
     * Whether to enable HTML `<head>` tags and Web App Manifest settings allowing the Web target
     * to be installed as a standalone WebApp on platforms like Android via Chrome.
     */
    var allowInstallAsWebApp: Boolean
        get() = webManifest.allowInstallAsWebApp
        set(value) {
            webManifest.allowInstallAsWebApp = value
            if (value) {
                webManifest.enabled = true
            }
        }

    /** General fallback icon path relative to project root (e.g. `"icon.png"` or `"icon.ico"`). */
    var icon: String = ""

    /** Windows `.ico` icon path relative to project root (e.g. `"icon.ico"`). */
    var windowsIcon: String = ""

    /** macOS `.icns` or `.png` icon path relative to project root (e.g. `"icon.icns"` or `"icon.png"`). */
    var macIcon: String = ""

    /** Alias for [macIcon]. */
    var macosIcon: String
        get() = macIcon
        set(value) {
            macIcon = value
        }

    /** Linux `.png` or `.svg` icon path relative to project root (e.g. `"icon.png"` or `"icon.svg"`). */
    var linuxIcon: String = ""

    /**
     * Web icon path relative to project root used for both the `#genericui-splash` splash screen
     * and the `<link rel="icon">` favicon of the generated `index.html`.
     *
     * Note: `.svg` vector icons (`image/svg+xml`, e.g. `"icon.svg"`) are preferred for the Web icon
     * for crisp resolution-independent scaling across both the favicon and the splash screen.
     * Falls back to [icon], [linuxIcon], [macIcon], or [windowsIcon] when not explicitly set.
     */
    var webIcon: String = ""

    /** Default initial window title (defaults to [appName] when blank). */
    var windowTitle: String = ""

    /** Default initial window width in pixels. */
    var windowWidth: Int = 1280

    /** Default initial window height in pixels. */
    var windowHeight: Int = 820

    /** Minimum window width in pixels. */
    var minWindowWidth: Int = 960

    /** Minimum window height in pixels. */
    var minWindowHeight: Int = 640

    /** Whether the main desktop window is resizable. */
    var resizable: Boolean = true

    /** Whether Per-Monitor V2 High-DPI awareness is enabled on Windows and Retina (`NSHighResolutionCapable`) on macOS. */
    var highDpiAware: Boolean = true

    /** Whether the Windows executable manifest requests administrator elevation (`requireAdministrator`). */
    var requireAdminOnWindows: Boolean = false

    /** Windows installer subdirectory (e.g. `"NexusTools\\NXTConvert"`). */
    var windowsInstallSubdir: String = ""

    /** Windows MSI upgrade UUID string. */
    var windowsMsiUpgradeCode: String = ""

    /** macOS `LSApplicationCategoryType` for `Info.plist`. */
    var macCategory: String = "public.app-category.utilities"

    /** macOS `LSMinimumSystemVersion` for `Info.plist`. */
    var macMinimumSystemVersion: String = "11.0"

    /** Linux freedesktop.org `.desktop` categories string. */
    var linuxCategories: String = "Utility;AudioVideo;"

    /** Whether dark mode titlebar (`DWMWA_USE_IMMERSIVE_DARK_MODE`) is enabled by default on Windows. */
    var darkTitleBar: Boolean = true

    /** Whether to compile and link `.rc` -> `.res` (Windows PE icon + `VERSIONINFO`) via `windres`. */
    var embedWindowsResources: Boolean = true

    /** Whether `NSHighResolutionCapable` is enabled in macOS `Info.plist`. */
    var macHighResolutionCapable: Boolean = true

    /** Linux `.desktop` `StartupWMClass` identifier. */
    var linuxStartupWmClass: String = ""

    /** Whether Linux `.desktop` entry runs in a terminal. */
    var linuxTerminal: Boolean = false

    /** Custom key-value pairs added to OS resource metadata (`VERSIONINFO`, `Info.plist`, `.desktop`). */
    val customResourceEntries: MutableMap<String, String> = linkedMapOf()

    /** Additional relative file/directory paths copied next to compiled desktop executables. */
    val bundledResourcePaths: MutableList<String> = mutableListOf()

    /** Adds a custom key-value entry to the generated OS resource metadata. */
    fun resource(key: String, value: String) {
        if (key.isNotBlank()) {
            customResourceEntries[key.trim()] = value
        }
    }

    /** Stages an additional asset or resource directory/file alongside the compiled desktop binaries. */
    fun bundleResource(relativePath: String) {
        if (relativePath.isNotBlank()) {
            bundledResourcePaths.add(relativePath.trim())
        }
    }

    /** Sets the general fallback icon path. */
    fun icon(path: String) {
        this.icon = path
    }

    /** Sets the Windows `.ico` icon path (e.g. `"icon.ico"`). */
    fun windowsIcon(path: String) {
        this.windowsIcon = path
    }

    /** Sets the macOS `.icns` / `.png` icon path (e.g. `"icon.icns"` or `"icon.png"`). */
    fun macIcon(path: String) {
        this.macIcon = path
    }

    /** Alias for [macIcon]. */
    fun macosIcon(path: String) {
        this.macIcon = path
    }

    /** Sets the Linux `.png` / `.svg` icon path (e.g. `"icon.png"`). */
    fun linuxIcon(path: String) {
        this.linuxIcon = path
    }

    /**
     * Sets the Web icon path overriding the default project icon for the Web splash screen and
     * `index.html` favicon. `.svg` icons (e.g. `"icon.svg"`) are preferred for the Web target.
     */
    fun webIcon(path: String) {
        this.webIcon = path
    }

    /**
     * Resolves the effective icon path for the Web splash screen and `index.html` favicon,
     * preferring [webIcon] first, falling back to the project's configured icons
     * ([icon], [linuxIcon], [macIcon], [windowsIcon]), and finally defaulting to
     * GenericUI's built-in `icon.svg` when no icons are provided by `build.gradle.kts`.
     */
    fun resolveEffectiveWebIconPath(): String =
        listOf(webIcon, icon, linuxIcon, macIcon, windowsIcon)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: GenericUiDefaultIconProvider.DEFAULT_ICON_SVG_FILE_NAME

    /** Configures platform icons in a single call. */
    fun icons(
        windows: String = this.windowsIcon,
        mac: String = this.macIcon,
        linux: String = this.linuxIcon,
        web: String = this.webIcon,
        common: String = this.icon
    ) {
        if (windows.isNotBlank()) this.windowsIcon = windows
        if (mac.isNotBlank()) this.macIcon = mac
        if (linux.isNotBlank()) this.linuxIcon = linux
        if (web.isNotBlank()) this.webIcon = web
        if (common.isNotBlank()) this.icon = common
    }

    /**
     * Sets the application theme / accent color (e.g. `"#0d6efd"` or `"#2A80FF"`) across all
     * backends that support it (Web `<meta name="theme-color">` & `manifest.webmanifest`,
     * Android `colors.xml` `theme_color` / `colorPrimary`, iOS/macOS `AccentColor.colorset`,
     * Windows `VERSIONINFO`, macOS `Info.plist`, Linux `.desktop`, and `R.color.theme_color`).
     */
    fun themeColor(color: String) {
        this.themeColor = color
    }

    /**
     * Enables and configures the W3C / MDN Progressive Web App (PWA) `manifest.webmanifest`
     * and standalone WebApp installation (`allowInstallAsWebApp`) for the Web target.
     */
    fun webManifest(action: Action<in GenericUiWebManifestConfig>) {
        webManifest.enabled = true
        action.execute(webManifest)
    }

    /**
     * Configures Web-specific build settings, including the `.svg`-preferred Web icon,
     * `manifest.webmanifest` generation, and standalone WebApp installation on Android via Chrome (`allowInstallAsWebApp`).
     */
    fun webBuild(
        icon: String = this.webIcon,
        generateManifest: Boolean = webManifest.enabled || webManifest.allowInstallAsWebApp,
        allowInstallAsWebApp: Boolean = webManifest.allowInstallAsWebApp,
        shortName: String = webManifest.shortName,
        themeColor: String = this.themeColor,
        backgroundColor: String = webManifest.backgroundColor,
        display: String = webManifest.display,
        orientation: String = webManifest.orientation,
        startUrl: String = webManifest.startUrl,
        scope: String = webManifest.scope,
        categories: List<String> = webManifest.categories
    ) {
        if (icon.isNotBlank()) this.webIcon = icon
        if (themeColor.isNotBlank()) {
            this.themeColor = themeColor
            if (webManifest.themeColor.isBlank()) {
                webManifest.themeColor = themeColor
            }
        }
        webManifest.enabled = generateManifest || allowInstallAsWebApp
        webManifest.allowInstallAsWebApp = allowInstallAsWebApp
        if (shortName.isNotBlank()) webManifest.shortName = shortName
        if (backgroundColor.isNotBlank()) webManifest.backgroundColor = backgroundColor
        if (display.isNotBlank()) webManifest.display = display
        if (orientation.isNotBlank()) webManifest.orientation = orientation
        if (startUrl.isNotBlank()) webManifest.startUrl = startUrl
        if (scope.isNotBlank()) webManifest.scope = scope
        if (categories.isNotEmpty() && categories !== webManifest.categories) {
            webManifest.categories.clear()
            webManifest.categories.addAll(categories)
        }
    }

    /** Configures desktop application identity and OS resource metadata. */
    fun metadata(
        appName: String = this.appName,
        appId: String = this.appId,
        appVersion: String = this.appVersion,
        vendor: String = this.vendor,
        description: String = this.description,
        copyright: String = this.copyright,
        license: String = this.license,
        homepageUrl: String = this.homepageUrl,
        issuesUrl: String = this.issuesUrl,
        themeColor: String = this.themeColor
    ) {
        if (appName.isNotBlank()) this.appName = appName
        if (appId.isNotBlank()) this.appId = appId
        if (appVersion.isNotBlank()) this.appVersion = appVersion
        if (vendor.isNotBlank()) this.vendor = vendor
        if (description.isNotBlank()) this.description = description
        if (copyright.isNotBlank()) this.copyright = copyright
        if (license.isNotBlank()) this.license = license
        if (homepageUrl.isNotBlank()) this.homepageUrl = homepageUrl
        if (issuesUrl.isNotBlank()) this.issuesUrl = issuesUrl
        if (themeColor.isNotBlank()) this.themeColor = themeColor
    }

    /** Configures initial and minimum desktop window geometry. */
    fun window(
        title: String = this.windowTitle,
        width: Int = this.windowWidth,
        height: Int = this.windowHeight,
        minWidth: Int = this.minWindowWidth,
        minHeight: Int = this.minWindowHeight,
        resizable: Boolean = this.resizable
    ) {
        if (title.isNotBlank()) this.windowTitle = title
        this.windowWidth = width
        this.windowHeight = height
        this.minWindowWidth = minWidth
        this.minWindowHeight = minHeight
        this.resizable = resizable
    }

    /** Configures Windows-specific desktop build, icon, and installer resource settings. */
    fun windowsBuild(
        icon: String = this.windowsIcon,
        installSubdir: String = this.windowsInstallSubdir,
        msiUpgradeCode: String = this.windowsMsiUpgradeCode,
        requireAdmin: Boolean = this.requireAdminOnWindows,
        highDpiAware: Boolean = this.highDpiAware
    ) {
        if (icon.isNotBlank()) this.windowsIcon = icon
        if (installSubdir.isNotBlank()) this.windowsInstallSubdir = installSubdir
        if (msiUpgradeCode.isNotBlank()) this.windowsMsiUpgradeCode = msiUpgradeCode
        this.requireAdminOnWindows = requireAdmin
        this.highDpiAware = highDpiAware
    }

    /** Configures macOS-specific desktop `.app` bundle and icon settings. */
    fun macBuild(
        icon: String = this.macIcon,
        bundleId: String = this.appId,
        category: String = this.macCategory,
        minimumSystemVersion: String = this.macMinimumSystemVersion
    ) {
        if (icon.isNotBlank()) this.macIcon = icon
        if (bundleId.isNotBlank()) this.appId = bundleId
        if (category.isNotBlank()) this.macCategory = category
        if (minimumSystemVersion.isNotBlank()) this.macMinimumSystemVersion = minimumSystemVersion
    }

    /** Configures Linux-specific desktop entry and icon settings. */
    fun linuxBuild(
        icon: String = this.linuxIcon,
        categories: String = this.linuxCategories
    ) {
        if (icon.isNotBlank()) this.linuxIcon = icon
        if (categories.isNotBlank()) this.linuxCategories = categories
    }
}
