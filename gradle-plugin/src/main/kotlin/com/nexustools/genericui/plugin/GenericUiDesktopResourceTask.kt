package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
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
    abstract val windowsIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val macIconPath: Property<String>

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
    abstract val linuxCategories: Property<String>

    @get:Input
    @get:Optional
    abstract val linuxStartupWmClass: Property<String>

    @get:Input
    abstract val linuxTerminal: Property<Boolean>

    @get:Input
    abstract val customResourceEntries: MapProperty<String, String>

    @get:Input
    abstract val bundledResourcePaths: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generateDesktopResources() {
        val outDir = outputDirectory.get().asFile
        outDir.mkdirs()

        val rootDir = File(projectRootPath.get())
        val resolvedBase = baseName.get().trim().ifEmpty { "GenericUiApp" }
        val resolvedAppName = appName.get().trim().ifEmpty { resolvedBase }
        val resolvedAppId = appId.get().trim().ifEmpty { "com.nexustools.${resolvedBase.lowercase()}" }
        val resolvedVersion = appVersion.get().trim().ifEmpty { "1.0.0" }
        val resolvedVendor = vendor.get().trim().ifEmpty { "NexusTools" }
        val resolvedDesc = appDescription.orNull?.trim().orEmpty().ifEmpty { resolvedAppName }
        val resolvedCopyright = copyright.orNull?.trim().orEmpty().ifEmpty { "Copyright (c) $resolvedVendor" }

        // 1. Resolve icon files on disk
        val winIconFile = resolveIconFile(
            rootDir,
            windowsIconPath.orNull,
            fallbackIconPath.orNull,
            listOf("icon.ico", "src/windowsMain/resources/icon.ico", "src/commonMain/resources/icon.ico")
        )
        val macIconFile = resolveIconFile(
            rootDir,
            macIconPath.orNull,
            fallbackIconPath.orNull,
            listOf("icon.icns", "icon.png", "src/macMain/resources/icon.icns", "src/commonMain/resources/icon.png")
        )
        val linuxIconFile = resolveIconFile(
            rootDir,
            linuxIconPath.orNull,
            fallbackIconPath.orNull,
            listOf("icon.png", "icon.svg", "src/linuxCommon/resources/icon.png", "src/commonMain/resources/icon.png")
        )

        // Stage runtime assets into outDir/runtime-assets so they can be copied next to executables
        val runtimeAssetsDir = File(outDir, "runtime-assets").apply { mkdirs() }
        if (winIconFile != null && winIconFile.exists()) {
            winIconFile.copyTo(File(runtimeAssetsDir, "icon.ico"), overwrite = true)
            if (winIconFile.name != "icon.ico") {
                winIconFile.copyTo(File(runtimeAssetsDir, winIconFile.name), overwrite = true)
            }
        }
        if (macIconFile != null && macIconFile.exists()) {
            macIconFile.copyTo(File(runtimeAssetsDir, macIconFile.name), overwrite = true)
        }
        if (linuxIconFile != null && linuxIconFile.exists()) {
            linuxIconFile.copyTo(File(runtimeAssetsDir, linuxIconFile.name), overwrite = true)
        }
        for (relPath in bundledResourcePaths.getOrElse(emptyList())) {
            val src = File(rootDir, relPath)
            if (src.exists()) {
                val dst = File(runtimeAssetsDir, src.name)
                if (src.isDirectory) {
                    src.copyRecursively(dst, overwrite = true)
                } else {
                    src.copyTo(dst, overwrite = true)
                }
            }
        }

        // 2. Generate Windows .rc and compile .res via windres.exe if available
        val winDir = File(outDir, "windows").apply { mkdirs() }
        val rcFile = File(winDir, "app_resources.rc")
        val resFile = File(winDir, "app_resources.res")
        val versionParts = parseFourPartVersion(resolvedVersion)
        val rcContent = buildString {
            appendLine("#pragma code_page(65001)")
            if (winIconFile != null && winIconFile.exists() && winIconFile.extension.equals("ico", ignoreCase = true)) {
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
            appendLine("            VALUE \"FileVersion\", \"${escapeRcString(resolvedVersion)}\\0\"")
            appendLine("            VALUE \"InternalName\", \"${escapeRcString(resolvedBase)}\\0\"")
            appendLine("            VALUE \"LegalCopyright\", \"${escapeRcString(resolvedCopyright)}\\0\"")
            appendLine("            VALUE \"OriginalFilename\", \"${escapeRcString(resolvedBase)}.exe\\0\"")
            appendLine("            VALUE \"ProductName\", \"${escapeRcString(resolvedAppName)}\\0\"")
            appendLine("            VALUE \"ProductVersion\", \"${escapeRcString(resolvedVersion)}\\0\"")
            for ((k, v) in customResourceEntries.getOrElse(emptyMap())) {
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

        // 3. Generate macOS Info.plist
        val macDir = File(outDir, "macos").apply { mkdirs() }
        val infoPlistFile = File(macDir, "Info.plist")
        val macIconName = macIconFile?.name ?: "icon.icns"
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
                appendLine("    <string>${escapeXml(resolvedVersion)}</string>")
                appendLine("    <key>CFBundleShortVersionString</key>")
                appendLine("    <string>${escapeXml(resolvedVersion)}</string>")
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
                appendLine("    <key>NSHighResolutionCapable</key>")
                appendLine("    <${if (macHighResolutionCapable.getOrElse(true)) "true" else "false"}/>")
                appendLine("    <key>NSHumanReadableCopyright</key>")
                appendLine("    <string>${escapeXml(resolvedCopyright)}</string>")
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
            },
            Charsets.UTF_8
        )
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
            .substringBefore('-')
            .split('.')
            .mapNotNull { it.trim().toIntOrNull() }
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
        val clangExe = findClangPreprocessorExecutable()
        runCatching {
            val cmd = mutableListOf(windresExe.absolutePath)
            if (clangExe != null) {
                cmd.add("--preprocessor=${clangExe.absolutePath}")
                cmd.add("--preprocessor-arg=-E")
                cmd.add("--preprocessor-arg=-xc-header")
                cmd.add("--preprocessor-arg=-DRC_INVOKED")
            }
            cmd.addAll(
                listOf(
                    "-i",
                    rcFile.absolutePath,
                    "-o",
                    resFile.absolutePath,
                    "-O",
                    "coff"
                )
            )
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            process.waitFor()
        }
    }

    companion object {
        fun findClangPreprocessorExecutable(): File? {
            val userHome = System.getProperty("user.home").orEmpty()
            val konanDepsDir = File(userHome, ".konan/dependencies")
            if (konanDepsDir.exists() && konanDepsDir.isDirectory) {
                val llvmDirs = konanDepsDir.listFiles { f ->
                    f.isDirectory && f.name.startsWith("llvm-")
                }?.sortedByDescending { it.name }.orEmpty()
                for (dir in llvmDirs) {
                    val candidate = File(dir, "bin/clang.exe")
                    if (candidate.exists() && candidate.isFile) {
                        return candidate
                    }
                }
            }
            return null
        }

        fun findWindresExecutable(): File? {
            val userHome = System.getProperty("user.home").orEmpty()
            val konanDepsDir = File(userHome, ".konan/dependencies")
            if (konanDepsDir.exists() && konanDepsDir.isDirectory) {
                val mingwDirs = konanDepsDir.listFiles { f ->
                    f.isDirectory && f.name.startsWith("msys2-mingw-w64-x86_64")
                }?.sortedByDescending { it.name }.orEmpty()
                for (dir in mingwDirs) {
                    val candidate = File(dir, "bin/windres.exe")
                    if (candidate.exists() && candidate.isFile) {
                        return candidate
                    }
                }
            }
            val pathEnv = System.getenv("PATH").orEmpty()
            for (entry in pathEnv.split(File.pathSeparator)) {
                if (entry.isBlank()) continue
                for (name in listOf("windres.exe", "windres")) {
                    val candidate = File(entry, name)
                    if (candidate.exists() && candidate.isFile) {
                        return candidate
                    }
                }
            }
            return null
        }
    }
}
