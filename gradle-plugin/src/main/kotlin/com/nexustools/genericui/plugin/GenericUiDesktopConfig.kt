package com.nexustools.genericui.plugin

/**
 * DSL configuration block (`genericui { desktop { ... } }` and synced with `genericui { application { ... } }`)
 * for configuring desktop client builds across Windows (`mingwX64`), macOS (`macosArm64` / `macosX64`),
 * Linux (`gtk` / `qt`), and Swing (`jvm`), including platform icons (`windowsIcon`, `macIcon`, `linuxIcon`),
 * OS version/copyright/vendor resource metadata, window dimensions, macOS `.app` bundle `Info.plist` settings,
 * Windows PE `.rc`/`.res` version & icon resources, and Linux `.desktop` entry metadata.
 */
open class GenericUiDesktopConfig {

    /** Human-readable application display name (e.g. `"NXTConverter"`). */
    var appName: String = ""

    /** Reverse-DNS application / bundle identifier (e.g. `"com.nexustools.convert"`). */
    var appId: String = ""

    /** Marketing / display version string (defaults to `project.version`, e.g. `"1.0.0"`). */
    var appVersion: String = ""

    /** Organization / publisher / vendor name (e.g. `"NexusTools"`). */
    var vendor: String = "NexusTools"

    /** Application summary description embedded in Windows `FileDescription` and Linux `.desktop` `Comment`. */
    var description: String = ""

    /** Legal copyright notice embedded in Windows `LegalCopyright` and macOS `NSHumanReadableCopyright`. */
    var copyright: String = ""

    /** SPDX license identifier or license name (e.g. `"GPL-3.0"` or `"MIT"`). */
    var license: String = ""

    /** Project homepage or repository URL. */
    var homepageUrl: String = ""

    /** Issue tracker URL (e.g. `"https://github.com/NexusTools/convert/issues"`). */
    var issuesUrl: String = ""

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

    /** Linux `.png` or `.svg` icon path relative to project root (e.g. `"icon.png"`). */
    var linuxIcon: String = ""

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

    /** Configures platform icons in a single call. */
    fun icons(
        windows: String = this.windowsIcon,
        mac: String = this.macIcon,
        linux: String = this.linuxIcon,
        common: String = this.icon
    ) {
        if (windows.isNotBlank()) this.windowsIcon = windows
        if (mac.isNotBlank()) this.macIcon = mac
        if (linux.isNotBlank()) this.linuxIcon = linux
        if (common.isNotBlank()) this.icon = common
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
        issuesUrl: String = this.issuesUrl
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
