package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * DSL configuration block (`genericui { application { ... } }`) for configuring executable binaries,
 * entry points, desktop client builds, platform icons (`windowsIcon`, `macIcon`, `linuxIcon`, `icon`),
 * OS version/vendor/copyright metadata, Windows GUI `WinMain` subsystem linker flags, JVM main class,
 * and Android target settings.
 */
open class GenericUiApplicationConfig(
    val desktop: GenericUiDesktopConfig = GenericUiDesktopConfig()
) {
    /**
     * Entry point function for native desktop executables (`mingwX64`, `linuxX64`, `linuxArm64`,
     * `macosArm64`, `macosX64`), e.g. `"com.nexustools.convert.main"`.
     */
    var entryPoint: String = ""

    /**
     * Binary base name for compiled executables, e.g. `"NXTConverter"` or `"haeditor"`.
     * Defaults to `project.name` when [entryPoint] is specified and [baseName] is left blank.
     */
    var baseName: String = ""

    /**
     * Optional JVM / Swing main class name (e.g. `"com.nexustools.convert.MainKt"`).
     */
    var mainClass: String = ""

    /**
     * Whether the native executable is a headless console/daemon process (`true`) or a GUI desktop
     * application (`false`, default). When `false`, Windows `mingwX64` executables are automatically
     * linked with `-Wl,--subsystem,windows` so `WinMain` is used and no `cmd.exe` console window opens.
     */
    var consoleApplication: Boolean = false

    /**
     * Whether to configure `binaries.executable { ... }` on enabled native targets when [entryPoint]
     * is non-empty, and `binaries.executable()` on the `js(IR)` Web target when `web()` is enabled.
     */
    var createExecutableBinaries: Boolean = true

    /**
     * Additional linker flags appended to Windows `mingwX64` executable binaries (on top of standard
     * Win32 GUI libraries `-lgdi32`, `-luser32`, `-lcomdlg32`, `-lshell32`, `-lole32`, `-ldwmapi`).
     */
    val extraWindowsLinkerOpts: MutableList<String> = mutableListOf()

    /**
     * Additional linker flags appended to POSIX (`linuxX64`, `linuxArm64`, `macosArm64`, `macosX64`)
     * executable binaries.
     */
    val extraPosixLinkerOpts: MutableList<String> = mutableListOf()

    /**
     * Android target namespace (e.g. `"com.giftedvision.dungeoncrawler"`).
     * Defaults to `project.group.toString()` if left blank when the Android plugin is applied.
     */
    var androidNamespace: String = ""

    /** Android target `compileSdk` version (defaults to `37`). */
    var androidCompileSdk: Int = 37

    /** Android target `minSdk` version (defaults to `24`). */
    var androidMinSdk: Int = 24

    // --- Desktop Metadata & Icon Properties (delegated to [desktop]) ---

    /** Human-readable application display name (e.g. `"NXTConverter"`). */
    var appName: String
        get() = desktop.appName
        set(value) {
            desktop.appName = value
        }

    /** Reverse-DNS application / bundle identifier (e.g. `"com.nexustools.convert"`). */
    var appId: String
        get() = desktop.appId
        set(value) {
            desktop.appId = value
        }

    /** Marketing / display version string (e.g. `"1.0.0"`). */
    var appVersion: String
        get() = desktop.appVersion
        set(value) {
            desktop.appVersion = value
        }

    /** Organization / publisher / vendor name (e.g. `"NexusTools"`). */
    var vendor: String
        get() = desktop.vendor
        set(value) {
            desktop.vendor = value
        }

    /** Application summary description embedded in OS executable metadata. */
    var description: String
        get() = desktop.description
        set(value) {
            desktop.description = value
        }

    /** Legal copyright notice embedded in OS executable metadata. */
    var copyright: String
        get() = desktop.copyright
        set(value) {
            desktop.copyright = value
        }

    /** SPDX license identifier or license name (e.g. `"GPL-3.0"`). */
    var license: String
        get() = desktop.license
        set(value) {
            desktop.license = value
        }

    /** Project homepage or repository URL. */
    var homepageUrl: String
        get() = desktop.homepageUrl
        set(value) {
            desktop.homepageUrl = value
        }

    /** Issue tracker URL (e.g. `"https://github.com/NexusTools/convert/issues"`). */
    var issuesUrl: String
        get() = desktop.issuesUrl
        set(value) {
            desktop.issuesUrl = value
        }

    /** General fallback icon path relative to project root (e.g. `"icon.png"` or `"icon.ico"`). */
    var icon: String
        get() = desktop.icon
        set(value) {
            desktop.icon = value
        }

    /** Windows `.ico` icon path relative to project root (e.g. `"icon.ico"`). */
    var windowsIcon: String
        get() = desktop.windowsIcon
        set(value) {
            desktop.windowsIcon = value
        }

    /** macOS `.icns` or `.png` icon path relative to project root (e.g. `"icon.icns"` or `"icon.png"`). */
    var macIcon: String
        get() = desktop.macIcon
        set(value) {
            desktop.macIcon = value
        }

    /** Alias for [macIcon]. */
    var macosIcon: String
        get() = desktop.macosIcon
        set(value) {
            desktop.macosIcon = value
        }

    /** Linux `.png` or `.svg` icon path relative to project root (e.g. `"icon.png"`). */
    var linuxIcon: String
        get() = desktop.linuxIcon
        set(value) {
            desktop.linuxIcon = value
        }

    /** Default initial window width in pixels. */
    var windowWidth: Int
        get() = desktop.windowWidth
        set(value) {
            desktop.windowWidth = value
        }

    /** Default initial window height in pixels. */
    var windowHeight: Int
        get() = desktop.windowHeight
        set(value) {
            desktop.windowHeight = value
        }

    // --- DSL Functions ---

    /** Configures desktop client build, icon, and OS resource settings. */
    fun desktop(action: Action<in GenericUiDesktopConfig>) {
        action.execute(desktop)
    }

    /** Sets the general fallback icon path. */
    fun icon(path: String) {
        desktop.icon(path)
    }

    /** Sets the Windows `.ico` icon path (e.g. `"icon.ico"`). */
    fun windowsIcon(path: String) {
        desktop.windowsIcon(path)
    }

    /** Sets the macOS `.icns` / `.png` icon path (e.g. `"icon.icns"` or `"icon.png"`). */
    fun macIcon(path: String) {
        desktop.macIcon(path)
    }

    /** Alias for [macIcon]. */
    fun macosIcon(path: String) {
        desktop.macosIcon(path)
    }

    /** Sets the Linux `.png` / `.svg` icon path (e.g. `"icon.png"`). */
    fun linuxIcon(path: String) {
        desktop.linuxIcon(path)
    }

    /** Configures platform icons in a single call. */
    fun icons(
        windows: String = desktop.windowsIcon,
        mac: String = desktop.macIcon,
        linux: String = desktop.linuxIcon,
        common: String = desktop.icon
    ) {
        desktop.icons(windows = windows, mac = mac, linux = linux, common = common)
    }

    /** Configures desktop application identity and OS resource metadata. */
    fun metadata(
        appName: String = desktop.appName,
        appId: String = desktop.appId,
        appVersion: String = desktop.appVersion,
        vendor: String = desktop.vendor,
        description: String = desktop.description,
        copyright: String = desktop.copyright,
        license: String = desktop.license,
        homepageUrl: String = desktop.homepageUrl,
        issuesUrl: String = desktop.issuesUrl
    ) {
        desktop.metadata(
            appName = appName,
            appId = appId,
            appVersion = appVersion,
            vendor = vendor,
            description = description,
            copyright = copyright,
            license = license,
            homepageUrl = homepageUrl,
            issuesUrl = issuesUrl
        )
    }

    /** Configures initial and minimum desktop window geometry. */
    fun window(
        title: String = desktop.windowTitle,
        width: Int = desktop.windowWidth,
        height: Int = desktop.windowHeight,
        minWidth: Int = desktop.minWindowWidth,
        minHeight: Int = desktop.minWindowHeight,
        resizable: Boolean = desktop.resizable
    ) {
        desktop.window(
            title = title,
            width = width,
            height = height,
            minWidth = minWidth,
            minHeight = minHeight,
            resizable = resizable
        )
    }

    /** Configures Windows-specific desktop build, icon, and installer resource settings. */
    fun windowsBuild(
        icon: String = desktop.windowsIcon,
        installSubdir: String = desktop.windowsInstallSubdir,
        msiUpgradeCode: String = desktop.windowsMsiUpgradeCode,
        requireAdmin: Boolean = desktop.requireAdminOnWindows,
        highDpiAware: Boolean = desktop.highDpiAware
    ) {
        desktop.windowsBuild(
            icon = icon,
            installSubdir = installSubdir,
            msiUpgradeCode = msiUpgradeCode,
            requireAdmin = requireAdmin,
            highDpiAware = highDpiAware
        )
    }

    /** Configures macOS-specific desktop `.app` bundle and icon settings. */
    fun macBuild(
        icon: String = desktop.macIcon,
        bundleId: String = desktop.appId,
        category: String = desktop.macCategory,
        minimumSystemVersion: String = desktop.macMinimumSystemVersion
    ) {
        desktop.macBuild(
            icon = icon,
            bundleId = bundleId,
            category = category,
            minimumSystemVersion = minimumSystemVersion
        )
    }

    /** Configures Linux-specific desktop entry and icon settings. */
    fun linuxBuild(
        icon: String = desktop.linuxIcon,
        categories: String = desktop.linuxCategories
    ) {
        desktop.linuxBuild(icon = icon, categories = categories)
    }

    /** Adds a custom key-value entry to the generated OS resource metadata (`VERSIONINFO`, `Info.plist`, `.desktop`). */
    fun resource(key: String, value: String) {
        desktop.resource(key, value)
    }

    /** Stages an additional asset or resource directory/file alongside the compiled desktop binaries. */
    fun bundleResource(relativePath: String) {
        desktop.bundleResource(relativePath)
    }

    /** Adds additional Windows `mingwX64` linker options (e.g. `"-lws2_32"`). */
    fun windowsLinkerOpts(vararg opts: String) {
        extraWindowsLinkerOpts.addAll(opts)
    }

    /** Adds additional Linux / macOS linker options. */
    fun posixLinkerOpts(vararg opts: String) {
        extraPosixLinkerOpts.addAll(opts)
    }
}
