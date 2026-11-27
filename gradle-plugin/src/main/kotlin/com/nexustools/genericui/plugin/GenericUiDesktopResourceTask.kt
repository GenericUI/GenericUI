package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Generates and compiles native desktop application resources for Windows (`.rc` -> `.res` via `windres`
 * with embedded `.ico` icon + `VS_VERSION_INFO`), macOS (`Info.plist` + bundle icon staging),
 * and Linux (`.desktop` entry + icon staging).
 */
@CacheableTask
abstract class GenericUiDesktopResourceTask : DefaultTask() {

    @get:Input
    abstract val projectRootPath: Property<String>

    @get:Input
    abstract val baseName: Property<String>

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val appId: Property<String>

    @get:Input
    abstract val appVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val buildVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val gitHash: Property<String>

    @get:Input
    abstract val vendor: Property<String>

    @get:Input
    @get:Optional
    abstract val appDescription: Property<String>

    @get:Input
    @get:Optional
    abstract val copyright: Property<String>

    @get:Input
    @get:Optional
    abstract val license: Property<String>

    @get:Input
    @get:Optional
    abstract val homepageUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val issuesUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val themeColor: Property<String>

    @get:Input
    @get:Optional
    abstract val windowsIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val appleIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val macIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val macAppleIconExplicitlyProvided: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val linuxIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val fallbackIconPath: Property<String>

    @get:Input
    abstract val embedWindowsResources: Property<Boolean>

    @get:Input
    abstract val macMinimumSystemVersion: Property<String>

    @get:Input
    abstract val macCategory: Property<String>

    @get:Input
    abstract val macHighResolutionCapable: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val macMultipleInstancesProhibited: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val macLocalNetworkUsageDescription: Property<String>

    @get:Input
    @get:Optional
    abstract val macBonjourServices: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val macosTrayIconPath: Property<String>

    @get:Input
    abstract val linuxCategories: Property<String>

    @get:Input
    @get:Optional
    abstract val linuxStartupWmClass: Property<String>

    @get:Input
    abstract val linuxTerminal: Property<Boolean>

    @get:Input
    abstract val customResourceEntries: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val bundledResourcePaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val enabledExtensionModulePaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val extensionOptions: org.gradle.api.provider.MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        macAppleIconExplicitlyProvided.convention(false)
        enabledExtensionModulePaths.convention(emptyList())
        extensionOptions.convention(emptyMap())
    }

    @TaskAction
    fun generateDesktopResources() {
        val outDir = outputDirectory.get().asFile
        outDir.mkdirs()

        val rootDir = File(projectRootPath.get())
        val resolvedBase = baseName.get().trim().ifEmpty { "GenericUiApp" }
        val resolvedAppName = appName.get().trim().ifEmpty { resolvedBase }
        val resolvedAppId = appId.get().trim().ifEmpty { "com.nexustools.${resolvedBase.lowercase()}" }
        val resolvedVersion = appVersion.get().trim().ifEmpty { "1.0.0" }
        val resolvedBuildVersion = buildVersion.orNull?.trim().orEmpty()
        val resolvedGitHash = gitHash.orNull?.trim().orEmpty()
        val resolvedWindowsDisplayVersion = GenericUiGitVersionResolver.formatVersionWithGitHash(
            version = resolvedVersion,
            gitHash = resolvedGitHash
        )
        val resolvedMacBundleVersion = GenericUiGitVersionResolver.resolveMacosBundleVersion(
            version = resolvedVersion,
            buildVersion = resolvedBuildVersion,
            gitHash = resolvedGitHash
        )
        val resolvedVendor = vendor.get().trim().ifEmpty { "NexusTools" }
        val resolvedDesc = appDescription.orNull?.trim().orEmpty().ifEmpty { resolvedAppName }
        val resolvedCopyright = copyright.orNull?.trim().orEmpty().ifEmpty {
            GenericUiVariantResolver.deriveDefaultCopyright(
                packageName = null,
                appId = resolvedAppId,
                fallbackVendor = resolvedVendor
            )
        }
        val resolvedThemeColor = themeColor.orNull?.trim()?.takeIf { it.isNotEmpty() }

        // 1. Resolve icon files on disk (falling back to the default GenericUI icon when none are provided)
        val defaultIconsDir = File(outDir, "default-icons").apply { mkdirs() }
        val defaultSvgIcon = GenericUiDefaultIconProvider.ensureDefaultSvgIcon(rootDir, defaultIconsDir)

        val primaryFallbackCandidate = resolveIconFile(
            rootDir,
            fallbackIconPath.orNull,
            null,
            listOf(
                "src/commonMain/resources/icon.svg",
                "src/commonMain/resources/icon.png",
                "src/commonMain/resources/icon.ico",
                "src/commonMain/resources/favicon.ico",
                "icon.svg",
                "icon.png",
                "icon.ico",
                "favicon.ico"
            )
        )
        val defaultPngIcon = GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(primaryFallbackCandidate, rootDir, defaultIconsDir)
        val defaultIcoIcon = GenericUiDefaultIconProvider.ensureWindowsIcoFromSourceOrDefault(primaryFallbackCandidate, rootDir, defaultIconsDir)
        val defaultIcnsIcon = GenericUiDefaultIconProvider.ensureAppleIcnsFromSourceOrDefault(primaryFallbackCandidate, rootDir, defaultIconsDir)

        val rawWinIconCandidate = resolveIconFile(
            rootDir,
            windowsIconPath.orNull,
            fallbackIconPath.orNull,
            listOf(
                "src/windowsMain/resources/icon.ico",
                "src/commonMain/resources/icon.svg",
                "src/commonMain/resources/icon.png",
                "src/commonMain/resources/icon.ico",
                "src/commonMain/resources/favicon.ico",
                "icon.ico",
                "icon.svg",
                "icon.png"
            )
        )
        val winIconFile = GenericUiDefaultIconProvider.ensureWindowsIcoFromSourceOrDefault(
            rawWinIconCandidate,
            rootDir,
            defaultIconsDir
        ).takeIf { it.exists() } ?: defaultIcoIcon

        val explicitMacFile = resolveIconFile(
            rootDir,
            macIconPath.orNull,
            appleIconPath.orNull,
            emptyList()
        )
        val macExplicitlyProvided = macAppleIconExplicitlyProvided.orNull ?: (explicitMacFile != null)
        val rawMacCandidate = if (macExplicitlyProvided) {
            explicitMacFile ?: resolveIconFile(
                rootDir,
                macIconPath.orNull,
                appleIconPath.orNull,
                listOf("icon.icns", "src/macMain/resources/icon.icns")
            )
        } else {
            resolveIconFile(
                rootDir,
                fallbackIconPath.orNull,
                null,
                listOf(
                    "src/commonMain/resources/icon.svg",
                    "src/commonMain/resources/icon.png",
                    "src/commonMain/resources/favicon.ico",
                    "icon.svg",
                    "icon.png",
                    "icon.icns"
                )
            )
        }
        val wrapMacTile = !macExplicitlyProvided
        val macDir = File(outDir, "macos").apply { mkdirs() }
        val macIconFile = if (wrapMacTile) {
            GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(
                sourceIconFile = rawMacCandidate ?: primaryFallbackCandidate,
                projectRootDir = rootDir,
                outputDir = macDir,
                fileName = "icon.png",
                wrapInAppleTile = true,
                fullBleedSquare = false
            )
        } else if (rawMacCandidate != null && rawMacCandidate.extension.lowercase() in setOf("ico", "svg", "icns")) {
            GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(rawMacCandidate, rootDir, defaultIconsDir)
        } else {
            rawMacCandidate ?: defaultPngIcon
        }
        val macIcnsFileName = if (rawMacCandidate?.extension?.equals("icns", ignoreCase = true) == true && !wrapMacTile) {
            rawMacCandidate.name
        } else {
            "icon.icns"
        }
        val customTrayPath = extensionOptions.getOrElse(emptyMap())["serviceWorker.trayIcon"]?.trim()?.takeIf { it.isNotEmpty() }
        val explicitSwTrayFile = if (customTrayPath != null) {
            resolveIconFile(rootDir, customTrayPath, null, emptyList())
        } else {
            null
        }
        val macTraySource = explicitSwTrayFile ?: rawMacCandidate ?: primaryFallbackCandidate
        GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(
            sourceIconFile = macTraySource,
            projectRootDir = rootDir,
            outputDir = macDir,
            fileName = "tray_icon.png",
            wrapInAppleTile = false,
            fullBleedSquare = false,
            requestedSizeOverride = 64
        )
        if (macTraySource != null && macTraySource.isFile && macTraySource.extension.equals("svg", ignoreCase = true)) {
            macTraySource.copyTo(File(macDir, "tray_icon.svg"), overwrite = true)
        }
        val explicitMacosTrayPath = macosTrayIconPath.orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: extensionOptions.getOrElse(emptyMap())["serviceWorker.macosTrayIcon"]?.trim()?.takeIf { it.isNotEmpty() }
        val explicitMacosTrayFile = if (explicitMacosTrayPath != null) {
            resolveIconFile(rootDir, explicitMacosTrayPath, null, emptyList())
        } else {
            null
        }
        if (explicitMacosTrayFile != null && explicitMacosTrayFile.isFile) {
            GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(
                sourceIconFile = explicitMacosTrayFile,
                projectRootDir = rootDir,
                outputDir = macDir,
                fileName = "macos_tray_icon.png",
                wrapInAppleTile = false,
                fullBleedSquare = false,
                requestedSizeOverride = 64
            )
            if (explicitMacosTrayFile.extension.equals("svg", ignoreCase = true)) {
                explicitMacosTrayFile.copyTo(File(macDir, "macos_tray_icon.svg"), overwrite = true)
            }
        }
        val macIcnsFile = GenericUiDefaultIconProvider.ensureAppleIcnsFromSourceOrDefault(
            sourceIconFile = rawMacCandidate ?: primaryFallbackCandidate,
            projectRootDir = rootDir,
            outputDir = defaultIconsDir,
            fileName = macIcnsFileName,
            wrapInAppleTile = wrapMacTile
        ).takeIf { it.exists() } ?: GenericUiDefaultIconProvider.ensureDefaultIcnsIcon(
            projectRootDir = rootDir,
            outputDir = defaultIconsDir,
            fileName = "icon.icns",
            wrapInAppleTile = wrapMacTile
        )

        val rawLinuxCandidate = resolveIconFile(
            rootDir,
            linuxIconPath.orNull,
            fallbackIconPath.orNull,
            listOf(
                "icon.png",
                "icon.svg",
                "src/linuxCommon/resources/icon.png",
                "src/linuxCommon/resources/icon.svg",
                "src/commonMain/resources/icon.png",
                "src/commonMain/resources/favicon.ico",
                "src/commonMain/resources/icon.svg"
            )
        )
        val linuxIconFile = if (rawLinuxCandidate != null && rawLinuxCandidate.extension.lowercase() == "ico") {
            GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(rawLinuxCandidate, rootDir, defaultIconsDir)
        } else {
            rawLinuxCandidate ?: defaultPngIcon
        }

        // Stage runtime assets into outDir/runtime-assets so they can be copied next to executables
        val runtimeAssetsDir = File(outDir, "runtime-assets").apply { mkdirs() }
        defaultSvgIcon.copyTo(File(runtimeAssetsDir, "icon.svg"), overwrite = true)
        defaultPngIcon.copyTo(File(runtimeAssetsDir, "icon.png"), overwrite = true)
        if (winIconFile.exists()) {
            winIconFile.copyTo(File(runtimeAssetsDir, "icon.ico"), overwrite = true)
            if (winIconFile.name != "icon.ico") {
                winIconFile.copyTo(File(runtimeAssetsDir, winIconFile.name), overwrite = true)
            }
        }
        if (macIconFile.exists()) {
            val targetName = if (wrapMacTile && macIconFile.name == "icon.png") "macos-icon.png" else macIconFile.name
            macIconFile.copyTo(File(runtimeAssetsDir, targetName), overwrite = true)
        }
        if (linuxIconFile.exists()) {
            linuxIconFile.copyTo(File(runtimeAssetsDir, linuxIconFile.name), overwrite = true)
        }
        for (relPath in bundledResourcePaths.getOrElse(emptyList())) {
            val src = File(rootDir, relPath)
            if (Files.exists(src.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                val dst = File(runtimeAssetsDir, src.name)
                GenericUiSymlinkPreservingCopy.copyEntryPreservingSymlinks(
                    src = src,
                    dst = dst,
                    sourceRoot = src.parentFile ?: rootDir
                )
            }
        }

        // 2. Generate Windows .rc and compile .res via windres.exe if available
        val winDir = File(outDir, "windows").apply { mkdirs() }
        if (winIconFile.exists()) {
            winIconFile.copyTo(File(winDir, "icon.ico"), overwrite = true)
        }
        val rcFile = File(winDir, "app_resources.rc")
        val resFile = File(winDir, "app_resources.res")
        val versionParts = parseFourPartVersion(resolvedVersion)
        val rcContent = buildString {
            appendLine("#pragma code_page(65001)")
            if (winIconFile.exists() && winIconFile.extension.equals("ico", ignoreCase = true)) {
                val escapedIconPath = winIconFile.absolutePath.replace("\\", "\\\\")
                appendLine("1 ICON \"$escapedIconPath\"")
                appendLine("IDI_APPICON ICON \"$escapedIconPath\"")
            }
            appendLine("1 VERSIONINFO")
            appendLine("FILEVERSION ${versionParts.joinToString(",")}")
            appendLine("PRODUCTVERSION ${versionParts.joinToString(",")}")
            appendLine("FILEFLAGSMASK 0x3fL")
            appendLine("FILEFLAGS 0x0L")
            appendLine("FILEOS 0x40004L")
            appendLine("FILETYPE 0x1L")
            appendLine("FILESUBTYPE 0x0L")
            appendLine("BEGIN")
            appendLine("    BLOCK \"StringFileInfo\"")
            appendLine("    BEGIN")
            appendLine("        BLOCK \"040904b0\"")
            appendLine("        BEGIN")
            appendLine("            VALUE \"CompanyName\", \"${escapeRcString(resolvedVendor)}\\0\"")
            appendLine("            VALUE \"FileDescription\", \"${escapeRcString(resolvedDesc)}\\0\"")
            appendLine("            VALUE \"FileVersion\", \"${escapeRcString(resolvedWindowsDisplayVersion)}\\0\"")
            appendLine("            VALUE \"InternalName\", \"${escapeRcString(resolvedBase)}\\0\"")
            appendLine("            VALUE \"LegalCopyright\", \"${escapeRcString(resolvedCopyright)}\\0\"")
            appendLine("            VALUE \"OriginalFilename\", \"${escapeRcString(resolvedBase)}.exe\\0\"")
            appendLine("            VALUE \"ProductName\", \"${escapeRcString(resolvedAppName)}\\0\"")
            appendLine("            VALUE \"ProductVersion\", \"${escapeRcString(resolvedWindowsDisplayVersion)}\\0\"")
            if (resolvedGitHash.isNotEmpty()) {
                appendLine("            VALUE \"GitCommit\", \"${escapeRcString(resolvedGitHash)}\\0\"")
            }
            val customEntries = customResourceEntries.getOrElse(emptyMap())
            if (resolvedThemeColor != null && "ThemeColor" !in customEntries) {
                appendLine("            VALUE \"ThemeColor\", \"${escapeRcString(resolvedThemeColor)}\\0\"")
            }
            for ((k, v) in customEntries) {
                appendLine("            VALUE \"${escapeRcString(k)}\", \"${escapeRcString(v)}\\0\"")
            }
            appendLine("        END")
            appendLine("    END")
            appendLine("    BLOCK \"VarFileInfo\"")
            appendLine("    BEGIN")
            appendLine("        VALUE \"Translation\", 0x409, 1200")
            appendLine("    END")
            appendLine("END")
        }
        rcFile.writeText(rcContent, Charsets.UTF_8)

        if (embedWindowsResources.getOrElse(true)) {
            compileWindowsRcToRes(rcFile, resFile)
        }

        // 3. Generate macOS Info.plist and icon.icns
        if (macIcnsFile.exists()) {
            macIcnsFile.copyTo(File(macDir, macIcnsFile.name), overwrite = true)
            if (macIcnsFile.name != "icon.icns") {
                macIcnsFile.copyTo(File(macDir, "icon.icns"), overwrite = true)
            }
        }
        if (macIconFile.exists()) {
            val targetPng = File(macDir, "icon.png")
            if (macIconFile.canonicalFile != targetPng.canonicalFile) {
                macIconFile.copyTo(targetPng, overwrite = true)
            }
        }
        val infoPlistFile = File(macDir, "Info.plist")
        val macIconName = if (macIcnsFile.exists()) macIcnsFile.name else (macIconFile?.name ?: "icon.icns")
        val extOpts = extensionOptions.getOrElse(emptyMap())
        val bonjourServices = buildList {
            addAll(macBonjourServices.getOrElse(emptyList()))
            extOpts["serviceWorker.macBonjourServices"]?.split(',')?.let { addAll(it) }
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val localNetworkDesc = macLocalNetworkUsageDescription.orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: extOpts["serviceWorker.macLocalNetworkUsageDescription"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (bonjourServices.isNotEmpty()) {
                "$resolvedAppName accesses devices on your local network."
            } else {
                null
            }
        val prohibitMultipleInstances = macMultipleInstancesProhibited.getOrElse(true)
        infoPlistFile.writeText(
            buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                appendLine("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">")
                appendLine("<plist version=\"1.0\">")
                appendLine("<dict>")
                appendLine("    <key>CFBundleName</key>")
                appendLine("    <string>${escapeXml(resolvedAppName)}</string>")
                appendLine("    <key>CFBundleDisplayName</key>")
                appendLine("    <string>${escapeXml(resolvedAppName)}</string>")
                appendLine("    <key>CFBundleIdentifier</key>")
                appendLine("    <string>${escapeXml(resolvedAppId)}</string>")
                appendLine("    <key>CFBundleVersion</key>")
                appendLine("    <string>${escapeXml(resolvedMacBundleVersion)}</string>")
                appendLine("    <key>CFBundleShortVersionString</key>")
                appendLine("    <string>${escapeXml(resolvedVersion)}</string>")
                if (resolvedGitHash.isNotEmpty()) {
                    appendLine("    <key>GenericUIGitCommit</key>")
                    appendLine("    <string>${escapeXml(resolvedGitHash)}</string>")
                }
                appendLine("    <key>CFBundleExecutable</key>")
                appendLine("    <string>${escapeXml(resolvedBase)}</string>")
                appendLine("    <key>CFBundleIconFile</key>")
                appendLine("    <string>${escapeXml(macIconName)}</string>")
                appendLine("    <key>CFBundlePackageType</key>")
                appendLine("    <string>APPL</string>")
                appendLine("    <key>LSMinimumSystemVersion</key>")
                appendLine("    <string>${escapeXml(macMinimumSystemVersion.getOrElse("12.0"))}</string>")
                appendLine("    <key>LSApplicationCategoryType</key>")
                appendLine("    <string>${escapeXml(macCategory.getOrElse("public.app-category.utilities"))}</string>")
                appendLine("    <key>LSMultipleInstancesProhibited</key>")
                appendLine("    <${if (prohibitMultipleInstances) "true" else "false"}/>")
                appendLine("    <key>NSHighResolutionCapable</key>")
                appendLine("    <${if (macHighResolutionCapable.getOrElse(true)) "true" else "false"}/>")
                appendLine("    <key>NSHumanReadableCopyright</key>")
                appendLine("    <string>${escapeXml(resolvedCopyright)}</string>")
                if (localNetworkDesc != null) {
                    appendLine("    <key>NSLocalNetworkUsageDescription</key>")
                    appendLine("    <string>${escapeXml(localNetworkDesc)}</string>")
                }
                if (bonjourServices.isNotEmpty()) {
                    appendLine("    <key>NSBonjourServices</key>")
                    appendLine("    <array>")
                    for (svc in bonjourServices) {
                        appendLine("        <string>${escapeXml(svc)}</string>")
                    }
                    appendLine("    </array>")
                }
                if (resolvedThemeColor != null) {
                    appendLine("    <key>NSAccentColorName</key>")
                    appendLine("    <string>AccentColor</string>")
                    appendLine("    <key>GenericUIThemeColor</key>")
                    appendLine("    <string>${escapeXml(resolvedThemeColor)}</string>")
                }
                appendLine("</dict>")
                appendLine("</plist>")
            },
            Charsets.UTF_8
        )

        // 4. Generate Linux .desktop entry
        val linuxDir = File(outDir, "linux").apply { mkdirs() }
        val desktopEntryFile = File(linuxDir, "$resolvedBase.desktop")
        val wmClass = linuxStartupWmClass.orNull?.trim().orEmpty().ifEmpty { resolvedBase }
        desktopEntryFile.writeText(
            buildString {
                appendLine("[Desktop Entry]")
                appendLine("Version=1.0")
                appendLine("Type=Application")
                appendLine("Name=$resolvedAppName")
                appendLine("Comment=$resolvedDesc")
                appendLine("Exec=$resolvedBase")
                appendLine("Icon=${linuxIconFile?.nameWithoutExtension ?: resolvedBase.lowercase()}")
                appendLine("Terminal=${linuxTerminal.getOrElse(false)}")
                appendLine("Categories=${linuxCategories.getOrElse("Utility;Application;")}")
                appendLine("StartupWMClass=$wmClass")
                appendLine("X-GenericUI-AppVersion=$resolvedVersion")
                if (resolvedGitHash.isNotEmpty()) {
                    appendLine("X-GenericUI-GitCommit=$resolvedGitHash")
                }
                if (resolvedThemeColor != null) {
                    appendLine("X-GenericUI-ThemeColor=$resolvedThemeColor")
                }
            },
            Charsets.UTF_8
        )

        // 5. Emit desktop autostart entries contributed by SPI extensions (if any)
        val enabledExtSet = enabledExtensionModulePaths.getOrElse(emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (enabledExtSet.isNotEmpty()) {
            val discoveredFrags = GenericUiFragmentScanner.scanProject(rootDir)
            val scanSpecs = com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
                .allAnnotationScanSpecs(enabledExtSet, rootDir)
            val discoveredSyms = GenericUiFragmentScanner.scanProjectSymbols(rootDir, scanSpecs)
            val desktopCtx = com.nexustools.genericui.plugin.spi.ExtensionBuildContext(
                projectRootDir = rootDir,
                projectName = rootDir.name,
                appId = resolvedAppId,
                appName = resolvedAppName,
                appVersion = resolvedVersion,
                baseName = resolvedBase,
                generatedPackageName = "",
                enabledExtensionModulePaths = enabledExtSet,
                discoveredFragments = discoveredFrags,
                discoveredSymbols = discoveredSyms,
                extensionOptions = extensionOptions.getOrElse(emptyMap())
            )
            val autostartEntries = com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
                .collectDesktopContributions(desktopCtx)
                .flatMap { it.autostartEntries }
            if (autostartEntries.isNotEmpty()) {
                val autostartRoot = File(outDir, "autostart").apply { mkdirs() }
                val linuxAutostartDir = File(autostartRoot, "linux").apply { mkdirs() }
                val macosAutostartDir = File(autostartRoot, "macos").apply { mkdirs() }
                for (entry in autostartEntries) {
                    val safeId = entry.identifier.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val execLine = if (entry.arguments.isEmpty()) entry.executableName else "${entry.executableName} ${entry.arguments.joinToString(" ")}"
                    File(linuxAutostartDir, "$safeId.desktop").writeText(
                        buildString {
                            appendLine("[Desktop Entry]")
                            appendLine("Type=Application")
                            appendLine("Name=${entry.displayName}")
                            appendLine("Exec=$execLine")
                            appendLine("X-GNOME-Autostart-enabled=${entry.runAtStartup}")
                        },
                        Charsets.UTF_8
                    )
                    val helperSuffix = entry.macosHelperBundleSuffix?.trim()?.takeIf { it.isNotEmpty() }
                    val macProgramPath = if (helperSuffix != null) {
                        "/Applications/$resolvedAppName.app/Contents/Helpers/$resolvedAppName $helperSuffix.app/Contents/MacOS/${entry.executableName}"
                    } else {
                        entry.executableName
                    }
                    File(macosAutostartDir, "$safeId.plist").writeText(
                        buildString {
                            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
                            appendLine("""<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">""")
                            appendLine("""<plist version="1.0"><dict>""")
                            appendLine("""    <key>Label</key><string>${escapeXml(entry.identifier)}</string>""")
                            appendLine("""    <key>ProgramArguments</key><array>""")
                            appendLine("""        <string>${escapeXml(macProgramPath)}</string>""")
                            for (arg in entry.arguments) {
                                appendLine("""        <string>${escapeXml(arg)}</string>""")
                            }
                            appendLine("""    </array>""")
                            appendLine("""    <key>MachServices</key>""")
                            appendLine("""    <dict>""")
                            appendLine("""        <key>${escapeXml(entry.identifier)}</key>""")
                            appendLine("""        <true/>""")
                            appendLine("""    </dict>""")
                            appendLine("""    <key>RunAtLoad</key>${if (entry.runAtStartup) "<true/>" else "<false/>"}""")
                            appendLine("""</dict></plist>""")
                        },
                        Charsets.UTF_8
                    )
                }
            }
        }
    }

    private fun resolveIconFile(
        rootDir: File,
        primaryPath: String?,
        fallbackPath: String?,
        defaultCandidates: List<String>
    ): File? {
        val candidates = buildList {
            if (!primaryPath.isNullOrBlank()) add(primaryPath.trim())
            if (!fallbackPath.isNullOrBlank()) add(fallbackPath.trim())
            addAll(defaultCandidates)
        }
        for (rel in candidates) {
            val f = File(rel).let { if (it.isAbsolute) it else File(rootDir, rel) }
            if (f.exists() && f.isFile) {
                return f
            }
        }
        return null
    }

    private fun parseFourPartVersion(versionStr: String): List<Int> {
        val numericParts = versionStr
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('+')
            .substringBefore('-')
            .split('.')
            .mapNotNull { segment ->
                val digits = segment.trim().takeWhile { it.isDigit() }
                digits.toIntOrNull()
            }
        return List(4) { idx -> numericParts.getOrElse(idx) { 0 } }
    }

    private fun escapeRcString(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun compileWindowsRcToRes(rcFile: File, resFile: File) {
        val windresExe = findWindresExecutable() ?: return
        if (resFile.exists() && resFile.length() == 0L) {
            resFile.delete()
        }
        val clangExe = findClangPreprocessorExecutable()
        val attempts = buildList {
            if (clangExe != null) {
                add(
                    listOf(
                        windresExe.absolutePath,
                        "--preprocessor=${clangExe.absolutePath}",
                        "--preprocessor-arg=-E",
                        "--preprocessor-arg=-xc-header",
                        "--preprocessor-arg=-DRC_INVOKED",
                        "-i",
                        rcFile.absolutePath,
                        "-o",
                        resFile.absolutePath,
                        "-O",
                        "coff"
                    )
                )
            }
            add(
                listOf(
                    windresExe.absolutePath,
                    "-i",
                    rcFile.absolutePath,
                    "-o",
                    resFile.absolutePath,
                    "-O",
                    "coff"
                )
            )
        }
        var lastError = ""
        for (cmd in attempts) {
            if (resFile.exists() && resFile.length() == 0L) {
                resFile.delete()
            }
            val result = runCatching {
                val process = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val exit = process.waitFor()
                exit to output
            }
            result.onSuccess { (exit, output) ->
                if (exit == 0 && resFile.isFile && resFile.length() > 0L) {
                    return
                }
                lastError = "exit=$exit output=${output.trim()}"
            }.onFailure { err ->
                lastError = err.message ?: err.javaClass.simpleName
            }
        }
        if (!resFile.isFile || resFile.length() == 0L) {
            throw GradleException(
                "Failed to compile Windows resources ${rcFile.absolutePath} using ${windresExe.absolutePath}: $lastError"
            )
        }
    }

    companion object {
        private fun isWindowsHostOs(): Boolean =
            System.getProperty("os.name").orEmpty().lowercase().contains("win")

        fun findClangPreprocessorExecutable(): File? {
            val isWinHost = isWindowsHostOs()
            val clangRelPath = if (isWinHost) "bin/clang.exe" else "bin/clang"
            val userHome = System.getProperty("user.home").orEmpty()
            val konanDepsDir = File(userHome, ".konan/dependencies")
            if (konanDepsDir.exists() && konanDepsDir.isDirectory) {
                val llvmDirs = konanDepsDir.listFiles { f ->
                    f.isDirectory && f.name.startsWith("llvm-")
                }?.sortedByDescending { it.name }.orEmpty()
                for (dir in llvmDirs) {
                    val candidate = File(dir, clangRelPath)
                    if (candidate.isFile && candidate.canExecute()) {
                        return candidate
                    }
                }
            }
            return null
        }

        fun findWindresExecutable(): File? {
            val isWinHost = isWindowsHostOs()
            if (isWinHost) {
                val userHome = System.getProperty("user.home").orEmpty()
                val konanDepsDir = File(userHome, ".konan/dependencies")
                if (konanDepsDir.exists() && konanDepsDir.isDirectory) {
                    val mingwDirs = konanDepsDir.listFiles { f ->
                        f.isDirectory && f.name.startsWith("msys2-mingw-w64-x86_64")
                    }?.sortedByDescending { it.name }.orEmpty()
                    for (dir in mingwDirs) {
                        val candidate = File(dir, "bin/windres.exe")
                        if (candidate.isFile && candidate.canExecute()) {
                            return candidate
                        }
                    }
                }
            }
            val searchDirs = buildList {
                val pathEnv = System.getenv("PATH").orEmpty()
                for (entry in pathEnv.split(File.pathSeparator)) {
                    if (entry.isNotBlank()) add(File(entry))
                }
                if (!isWinHost) {
                    add(File("/opt/homebrew/bin"))
                    add(File("/usr/local/bin"))
                }
            }.distinct()
            val names = if (isWinHost) {
                listOf("windres.exe", "x86_64-w64-mingw32-windres.exe", "windres")
            } else {
                listOf("x86_64-w64-mingw32-windres", "windres")
            }
            for (dir in searchDirs) {
                if (!dir.isDirectory) continue
                for (name in names) {
                    val candidate = File(dir, name)
                    if (candidate.isFile && candidate.canExecute()) {
                        return candidate
                    }
                }
            }
            return null
        }
    }
}
