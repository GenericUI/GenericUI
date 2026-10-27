package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task (`generateGenericUiLaunchers`) that scans the project for `@MainAppFragment`,
 * `@AppFragment`, and `@AppApplication` annotations using [GenericUiFragmentScanner] and generates
 * per-backend `Generated<Target>AppDescriptor.kt` and `Generated<Target>LauncherMain.kt` source files
 * under `build/generated/genericui/launchers/<target>/kotlin`.
 */
abstract class GenericUiGenerateLaunchersTask : DefaultTask() {

    @get:Input
    abstract val projectRootPath: Property<String>

    @get:Input
    @get:Optional
    abstract val extraSourceDirectories: ListProperty<String>

    @get:Input
    abstract val enabledBackendIds: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val generatedPackageName: Property<String>

    @get:Input
    abstract val resourcesEnabled: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val resourcePackageName: Property<String>

    @get:Input
    @get:Optional
    abstract val backendAppIds: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val backendAppNames: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val backendAppVersions: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val backendWindowTitles: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val backendWindowWidths: MapProperty<String, Int>

    @get:Input
    @get:Optional
    abstract val backendWindowHeights: MapProperty<String, Int>

    @get:Input
    @get:Optional
    abstract val backendMinWindowWidths: MapProperty<String, Int>

    @get:Input
    @get:Optional
    abstract val backendMinWindowHeights: MapProperty<String, Int>

    @get:Input
    @get:Optional
    abstract val backendWindowResizables: MapProperty<String, Boolean>

    @get:Input
    @get:Optional
    abstract val backendFullscreens: MapProperty<String, Boolean>

    @get:Input
    @get:Optional
    abstract val backendThemeModes: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val backendThemeColors: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val backendIconPaths: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val backendGlobalStyleSheets: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val backendProviderInstallerCalls: MapProperty<String, List<String>>

    @get:Input
    abstract val generateVisualWindowsTestHelper: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = GenericUiGradlePlugin.TASK_GROUP
        description = "Generates per-backend GenericUiAppDescriptor and launcher main() entrypoints for @MainAppFragment."
        resourcesEnabled.convention(true)
        generateVisualWindowsTestHelper.convention(false)
    }

    @TaskAction
    fun generateLaunchers() {
        val outRoot = outputDirectory.get().asFile
        outRoot.deleteRecursively()
        outRoot.mkdirs()

        val projectDir = File(projectRootPath.get())
        val extraDirs = extraSourceDirectories.getOrElse(emptyList()).map { path ->
            val f = File(path)
            if (f.isAbsolute) f else File(projectDir, path)
        }
        val discoveredFromProject = GenericUiFragmentScanner.scanProject(projectDir)
        val discoveredFromExtra = if (extraDirs.isNotEmpty()) {
            GenericUiFragmentScanner.scanSourceDirectories(extraDirs)
        } else {
            emptyList()
        }
        val discovered = (discoveredFromProject + discoveredFromExtra)
            .distinctBy { "${it.qualifiedName}:${it.isMainFragment}:${it.isApplicationClass}" }

        val mainFragments = discovered.filter { it.isMainFragment && !it.isApplicationClass }
        if (mainFragments.isEmpty()) {
            return
        }

        val enabledScopes = enabledBackendIds.getOrElse(emptyList())
            .mapNotNull { GenericUiVariantScope.fromNameOrNull(it) }
            .filter { it.isConcreteBackend && it != GenericUiVariantScope.ANDROID }
            .distinct()

        val configuredPkg = generatedPackageName.orNull?.trim().orEmpty()
        val effectiveGenPkg = configuredPkg.ifEmpty {
            val firstMainPkg = mainFragments.firstOrNull { it.packageName.isNotBlank() }?.packageName.orEmpty()
            if (firstMainPkg.isNotEmpty()) "$firstMainPkg.generated" else DEFAULT_GENERATED_PACKAGE
        }

        val resEnabled = resourcesEnabled.getOrElse(true)
        val resPkg = resourcePackageName.orNull?.trim().orEmpty()
            .ifEmpty { "com.nexustools.genericui.generated.res" }

        val appIdsMap = backendAppIds.getOrElse(emptyMap())
        val appNamesMap = backendAppNames.getOrElse(emptyMap())
        val appVersionsMap = backendAppVersions.getOrElse(emptyMap())
        val winTitlesMap = backendWindowTitles.getOrElse(emptyMap())
        val winWidthsMap = backendWindowWidths.getOrElse(emptyMap())
        val winHeightsMap = backendWindowHeights.getOrElse(emptyMap())
        val minWidthsMap = backendMinWindowWidths.getOrElse(emptyMap())
        val minHeightsMap = backendMinWindowHeights.getOrElse(emptyMap())
        val resizablesMap = backendWindowResizables.getOrElse(emptyMap())
        val fullscreensMap = backendFullscreens.getOrElse(emptyMap())
        val themeModesMap = backendThemeModes.getOrElse(emptyMap())
        val themeColorsMap = backendThemeColors.getOrElse(emptyMap())
        val iconPathsMap = backendIconPaths.getOrElse(emptyMap())
        val globalStyleSheetsMap = backendGlobalStyleSheets.getOrElse(emptyMap())
        val providerCallsMap = backendProviderInstallerCalls.getOrElse(emptyMap())

        for (backendScope in enabledScopes) {
            val key = backendScope.id
            val variantConfig = GenericUiResolvedVariantConfig(
                targetScope = backendScope,
                appId = appIdsMap[key] ?: "com.nexustools.genericui.app",
                appName = appNamesMap[key] ?: "GenericUI",
                appVersion = appVersionsMap[key] ?: "1.0.0",
                windowTitle = winTitlesMap[key] ?: (appNamesMap[key] ?: "GenericUI"),
                windowWidth = winWidthsMap[key] ?: 1280,
                windowHeight = winHeightsMap[key] ?: 800,
                minWindowWidth = minWidthsMap[key] ?: 320,
                minWindowHeight = minHeightsMap[key] ?: 240,
                windowResizable = resizablesMap[key] ?: true,
                fullscreen = fullscreensMap[key] ?: false,
                themeMode = themeModesMap[key] ?: "auto",
                themeColor = themeColorsMap[key] ?: "#0d6efd",
                iconPath = iconPathsMap[key]?.takeIf { it.isNotBlank() },
                globalStyleSheet = globalStyleSheetsMap[key]?.takeIf { it.isNotBlank() }
            )
            val providerCalls = providerCallsMap[key].orEmpty()

            generateBackendLauncherFiles(
                outputRootDir = outRoot,
                generatedPackage = effectiveGenPkg,
                backendScope = backendScope,
                discoveredFragments = discovered,
                variantConfig = variantConfig,
                providerInstallerCalls = providerCalls,
                resourcesEnabled = resEnabled,
                resourcePackage = resPkg
            )
        }

        if (generateVisualWindowsTestHelper.getOrElse(false) &&
            GenericUiVariantScope.WINDOWS in enabledScopes &&
            !hasHandWrittenVisualTestMain(projectDir)
        ) {
            emitWindowsVisualTestHelper(
                outputRootDir = outRoot,
                generatedPackage = effectiveGenPkg
            )
        }
    }

    companion object {
        const val DEFAULT_GENERATED_PACKAGE: String = "com.nexustools.genericui.generated"

        val LAUNCHER_BACKEND_SCOPES: List<GenericUiVariantScope> = listOf(
            GenericUiVariantScope.WINDOWS,
            GenericUiVariantScope.MACOS,
            GenericUiVariantScope.GTK,
            GenericUiVariantScope.QT,
            GenericUiVariantScope.SWING,
            GenericUiVariantScope.IOS,
            GenericUiVariantScope.WEB
        )

        /**
         * Resolves the generated launcher package (`<appPackage>.generated` or
         * `com.nexustools.genericui.generated`).
         */
        fun resolveGeneratedLauncherPackage(
            extension: GenericUiExtension,
            projectGroup: String = "",
            discoveredFragments: List<GenericUiDiscoveredFragment> = emptyList()
        ): String {
            val basePkg = extension.app.packageName.trim()
                .ifEmpty { extension.application.androidNamespace.trim() }
                .ifEmpty {
                    discoveredFragments.firstOrNull {
                        it.isMainFragment && !it.isApplicationClass && it.packageName.isNotBlank()
                    }?.packageName.orEmpty()
                }
                .ifEmpty {
                    projectGroup.trim().takeIf { it.isNotEmpty() && it != "unspecified" }.orEmpty()
                }
                .ifEmpty {
                    extension.app.id.trim().ifEmpty { extension.application.appId.trim() }
                }

            if (basePkg.isEmpty()) return DEFAULT_GENERATED_PACKAGE
            return if (basePkg.endsWith(".generated")) basePkg else "$basePkg.generated"
        }

        /**
         * Returns the list of enabled launcher backend scopes from [backends].
         */
        fun enabledLauncherBackendScopes(backends: GenericUiBackendsConfig): List<GenericUiVariantScope> = buildList {
            if (backends.windowsEnabled) add(GenericUiVariantScope.WINDOWS)
            if (backends.macosEnabled) add(GenericUiVariantScope.MACOS)
            if (backends.gtkEnabled) add(GenericUiVariantScope.GTK)
            if (backends.qtEnabled) add(GenericUiVariantScope.QT)
            if (backends.swingConfig.enabled) add(GenericUiVariantScope.SWING)
            if (backends.iosEnabled) add(GenericUiVariantScope.IOS)
            if (backends.webEnabled) add(GenericUiVariantScope.WEB)
        }

        /**
         * Returns all enabled backend scopes (including `ANDROID`) from [backends].
         */
        fun enabledAllBackendScopes(backends: GenericUiBackendsConfig): List<GenericUiVariantScope> = buildList {
            if (backends.windowsEnabled) add(GenericUiVariantScope.WINDOWS)
            if (backends.macosEnabled) add(GenericUiVariantScope.MACOS)
            if (backends.gtkEnabled) add(GenericUiVariantScope.GTK)
            if (backends.qtEnabled) add(GenericUiVariantScope.QT)
            if (backends.swingConfig.enabled) add(GenericUiVariantScope.SWING)
            if (backends.androidEnabled) add(GenericUiVariantScope.ANDROID)
            if (backends.iosEnabled) add(GenericUiVariantScope.IOS)
            if (backends.webEnabled) add(GenericUiVariantScope.WEB)
        }

        /**
         * Scans [projectDir] and generates `Generated<Target>AppDescriptor.kt` and
         * `Generated<Target>LauncherMain.kt` for every enabled backend in [extension].
         */
        fun generateForProject(
            projectDir: File,
            outputRootDir: File,
            extension: GenericUiExtension,
            projectGroup: String = "",
            enabledBackends: List<GenericUiVariantScope>? = null
        ): List<File> {
            val discovered = GenericUiFragmentScanner.scanProject(projectDir)
            if (discovered.none { it.isMainFragment && !it.isApplicationClass }) {
                return emptyList()
            }
            val generatedPkg = resolveGeneratedLauncherPackage(extension, projectGroup, discovered)
            val resEnabled = extension.resources.enabled
            val resPkg = extension.resources.packageName.trim().ifEmpty {
                val grp = projectGroup.trim()
                if (grp.isNotEmpty() && grp != "unspecified") "$grp.res" else "com.nexustools.genericui.generated.res"
            }
            val targetScopes = (enabledBackends ?: enabledLauncherBackendScopes(extension.backends))
                .filter { it.isConcreteBackend && it != GenericUiVariantScope.ANDROID }
                .distinct()

            val generatedFiles = mutableListOf<File>()
            for (scope in targetScopes) {
                val variantConfig = GenericUiVariantResolver.resolve(extension, scope)
                val providerCalls = extension.extensions.providerInstallerCallsForBackend(
                    backendScope = scope,
                    extensionOverrides = variantConfig.extensionOverrides
                )
                generatedFiles.addAll(
                    generateBackendLauncherFiles(
                        outputRootDir = outputRootDir,
                        generatedPackage = generatedPkg,
                        backendScope = scope,
                        discoveredFragments = discovered,
                        variantConfig = variantConfig,
                        providerInstallerCalls = providerCalls,
                        resourcesEnabled = resEnabled,
                        resourcePackage = resPkg
                    )
                )
            }
            if (extension.testing.enabled &&
                extension.testing.visualOnWindows &&
                GenericUiVariantScope.WINDOWS in targetScopes &&
                !hasHandWrittenVisualTestMain(projectDir)
            ) {
                generatedFiles.add(
                    emitWindowsVisualTestHelper(
                        outputRootDir = outputRootDir,
                        generatedPackage = generatedPkg
                    )
                )
            }
            return generatedFiles
        }

        /**
         * Generates `Generated<Target>AppDescriptor.kt` and `Generated<Target>LauncherMain.kt`
         * for a single [backendScope].
         */
        fun generateBackendLauncherFiles(
            outputRootDir: File,
            generatedPackage: String,
            backendScope: GenericUiVariantScope,
            discoveredFragments: List<GenericUiDiscoveredFragment>,
            variantConfig: GenericUiResolvedVariantConfig,
            providerInstallerCalls: List<String>,
            resourcesEnabled: Boolean,
            resourcePackage: String
        ): List<File> {
            val winningMain = GenericUiMainFragmentResolver.resolveMainFragmentForBackend(
                discoveredFragments = discoveredFragments,
                targetBackend = backendScope
            )
            val applicableFragments = GenericUiMainFragmentResolver.resolveApplicableFragmentsForBackend(
                discoveredFragments = discoveredFragments,
                targetBackend = backendScope
            )
            val customApplication = GenericUiMainFragmentResolver.resolveApplicationForBackendOrNull(
                discoveredFragments = discoveredFragments,
                targetBackend = backendScope
            )

            val targetCap = targetPascalCase(backendScope)
            val pkgRelPath = generatedPackage.replace('.', '/')
            val targetOutDir = File(outputRootDir, "${backendScope.id}/kotlin/$pkgRelPath")
            targetOutDir.mkdirs()

            val descriptorObjectName = "Generated${targetCap}AppDescriptor"
            val descriptorFile = File(targetOutDir, "$descriptorObjectName.kt")
            descriptorFile.writeText(
                buildAppDescriptorKotlin(
                    generatedPackage = generatedPackage,
                    descriptorObjectName = descriptorObjectName,
                    backendScope = backendScope,
                    winningMain = winningMain,
                    applicableFragments = applicableFragments,
                    customApplication = customApplication,
                    variantConfig = variantConfig,
                    providerInstallerCalls = providerInstallerCalls,
                    resourcesEnabled = resourcesEnabled,
                    resourcePackage = resourcePackage
                )
            )

            val launcherMainFile = File(targetOutDir, "Generated${targetCap}LauncherMain.kt")
            launcherMainFile.writeText(
                buildLauncherMainKotlin(
                    generatedPackage = generatedPackage,
                    descriptorObjectName = descriptorObjectName,
                    backendScope = backendScope,
                    globalStyleSheet = variantConfig.globalStyleSheet
                )
            )

            return listOf(descriptorFile, launcherMainFile)
        }

        private fun targetPascalCase(scope: GenericUiVariantScope): String = when (scope) {
            GenericUiVariantScope.WINDOWS -> "Windows"
            GenericUiVariantScope.MACOS -> "Macos"
            GenericUiVariantScope.GTK -> "Gtk"
            GenericUiVariantScope.QT -> "Qt"
            GenericUiVariantScope.SWING -> "Swing"
            GenericUiVariantScope.ANDROID -> "Android"
            GenericUiVariantScope.IOS -> "Ios"
            GenericUiVariantScope.WEB -> "Web"
            else -> scope.id.replaceFirstChar { it.uppercaseChar() }
        }

        private fun buildAppDescriptorKotlin(
            generatedPackage: String,
            descriptorObjectName: String,
            backendScope: GenericUiVariantScope,
            winningMain: GenericUiDiscoveredFragment,
            applicableFragments: List<GenericUiDiscoveredFragment>,
            customApplication: GenericUiDiscoveredFragment?,
            variantConfig: GenericUiResolvedVariantConfig,
            providerInstallerCalls: List<String>,
            resourcesEnabled: Boolean,
            resourcePackage: String
        ): String = buildString {
            appendLine("package $generatedPackage")
            appendLine()
            appendLine("import com.nexustools.genericui.core.GenericUiAppDescriptor")
            appendLine()
            appendLine("/**")
            appendLine(" * Auto-generated [GenericUiAppDescriptor] provider for the `${backendScope.id}` backend target.")
            appendLine(" */")
            appendLine("object $descriptorObjectName {")
            if (resourcesEnabled && resourcePackage.isNotBlank()) {
                appendLine("    @Suppress(\"UnusedPrivateMember\")")
                appendLine("    private fun $resourcePackage.GeneratedResourceRegistry.registerAll() {")
                appendLine("        ensureInitialized()")
                appendLine("    }")
                appendLine()
            }
            appendLine("    fun create(): GenericUiAppDescriptor = GenericUiAppDescriptor(")
            appendLine("        appId = ${escapeKotlinString(variantConfig.appId)},")
            appendLine("        appName = ${escapeKotlinString(variantConfig.appName)},")
            appendLine("        appVersion = ${escapeKotlinString(variantConfig.appVersion)},")
            appendLine("        windowTitle = ${escapeKotlinString(variantConfig.windowTitle)},")
            appendLine("        windowWidth = ${variantConfig.windowWidth},")
            appendLine("        windowHeight = ${variantConfig.windowHeight},")
            appendLine("        minWindowWidth = ${variantConfig.minWindowWidth},")
            appendLine("        minWindowHeight = ${variantConfig.minWindowHeight},")
            appendLine("        windowResizable = ${variantConfig.windowResizable},")
            appendLine("        fullscreen = ${variantConfig.fullscreen},")
            appendLine("        themeMode = ${escapeKotlinString(variantConfig.themeMode)},")
            appendLine("        themeColor = ${escapeKotlinString(variantConfig.themeColor)},")
            val iconLiteral = variantConfig.iconPath?.takeIf { it.isNotBlank() }?.let { escapeKotlinString(it) } ?: "null"
            appendLine("        iconPath = $iconLiteral,")
            val styleSheetPath = variantConfig.globalStyleSheet?.trim()?.takeIf { it.isNotEmpty() }
            if (styleSheetPath != null) {
                appendLine("        globalStyleSheetPath = ${escapeKotlinString(styleSheetPath)},")
                appendLine("        precompiledStyleSheetInstaller = { app -> app.addStyleSheet(${escapeKotlinString(styleSheetPath)}) },")
            }
            appendLine("        mainFragmentClass = ${winningMain.qualifiedName}::class,")
            appendLine("        mainFragmentFactory = { _: com.nexustools.genericui.component.fragment.FragmentBundle? -> ${winningMain.qualifiedName}() },")
            if (customApplication != null) {
                appendLine("        applicationFactory = { _: com.nexustools.genericui.component.fragment.FragmentBundle? -> ${customApplication.qualifiedName}() },")
            }
            if (applicableFragments.isEmpty()) {
                appendLine("        fragmentRegistrations = emptyList(),")
            } else {
                appendLine("        fragmentRegistrations = listOf(")
                applicableFragments.forEachIndexed { idx, frag ->
                    val comma = if (idx < applicableFragments.lastIndex) "," else ""
                    appendLine("            ${frag.qualifiedName}::class to { _: com.nexustools.genericui.component.fragment.FragmentBundle? -> ${frag.qualifiedName}() }$comma")
                }
                appendLine("        ),")
            }
            if (providerInstallerCalls.isEmpty()) {
                appendLine("        providerInstallers = emptyList(),")
            } else {
                appendLine("        providerInstallers = listOf(")
                providerInstallerCalls.forEachIndexed { idx, expr ->
                    val comma = if (idx < providerInstallerCalls.lastIndex) "," else ""
                    appendLine("            { $expr }$comma")
                }
                appendLine("        ),")
            }
            if (resourcesEnabled && resourcePackage.isNotBlank()) {
                appendLine("        resourceRegistryInstaller = { $resourcePackage.GeneratedResourceRegistry.registerAll() }")
            } else {
                appendLine("        resourceRegistryInstaller = null")
            }
            appendLine("    )")
            appendLine("}")
        }

        private fun buildLauncherMainKotlin(
            generatedPackage: String,
            descriptorObjectName: String,
            backendScope: GenericUiVariantScope,
            globalStyleSheet: String? = null
        ): String = buildString {
            if (backendScope == GenericUiVariantScope.SWING) {
                appendLine("@file:JvmName(\"GeneratedSwingMain\")")
                appendLine()
            }
            appendLine("package $generatedPackage")
            appendLine()
            val cleanStyleSheet = globalStyleSheet?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanStyleSheet != null) {
                appendLine("fun configureGlobalStyleSheet(app: com.nexustools.genericui.component.shell.IApplication) {")
                appendLine("    app.addStyleSheet(${escapeKotlinString(cleanStyleSheet)})")
                appendLine("}")
                appendLine()
            }
            when (backendScope) {
                GenericUiVariantScope.WINDOWS -> {
                    appendLine("fun main(args: Array<String>) {")
                    appendLine("    com.nexustools.genericui.windows.WindowsLauncher.launch(args, $descriptorObjectName.create())")
                    appendLine("}")
                }
                GenericUiVariantScope.MACOS -> {
                    appendLine("fun main(args: Array<String>) {")
                    appendLine("    com.nexustools.genericui.macos.MacosLauncher.launch(args, $descriptorObjectName.create())")
                    appendLine("}")
                }
                GenericUiVariantScope.GTK -> {
                    appendLine("fun main(args: Array<String>) {")
                    appendLine("    com.nexustools.genericui.gtk.GtkLauncher.launch(args, $descriptorObjectName.create())")
                    appendLine("}")
                }
                GenericUiVariantScope.QT -> {
                    appendLine("fun main(args: Array<String>) {")
                    appendLine("    com.nexustools.genericui.qt.QtLauncher.launch(args, $descriptorObjectName.create())")
                    appendLine("}")
                }
                GenericUiVariantScope.SWING -> {
                    appendLine("fun main(args: Array<String>) {")
                    appendLine("    com.nexustools.genericui.swing.SwingLauncher.launch(args, $descriptorObjectName.create())")
                    appendLine("}")
                }
                GenericUiVariantScope.IOS -> {
                    appendLine("fun MainViewController() = com.nexustools.genericui.ios.IosLauncher.createRootViewController($descriptorObjectName.create())")
                    appendLine()
                    appendLine("fun main(args: Array<String>) {")
                    appendLine("    com.nexustools.genericui.ios.IosLauncher.launch(args, $descriptorObjectName.create())")
                    appendLine("}")
                }
                GenericUiVariantScope.WEB -> {
                    appendLine("fun main() {")
                    appendLine("    com.nexustools.genericui.web.WebLauncher.launch($descriptorObjectName.create())")
                    appendLine("}")
                }
                else -> Unit
            }
        }

        private fun hasHandWrittenVisualTestMain(projectDir: File): Boolean {
            val srcDir = File(projectDir, "src")
            if (!srcDir.exists() || !srcDir.isDirectory) return false
            return srcDir.walkTopDown().any { file ->
                file.isFile && file.name.endsWith("VisualTestMain.kt", ignoreCase = true)
            }
        }

        private fun emitWindowsVisualTestHelper(
            outputRootDir: File,
            generatedPackage: String
        ): File {
            val pkgRelPath = generatedPackage.replace('.', '/')
            val testOutDir = File(outputRootDir, "windowsTest/kotlin/$pkgRelPath")
            testOutDir.mkdirs()
            val helperFile = File(testOutDir, "GeneratedWindowsVisualTestLauncher.kt")
            helperFile.writeText(
                buildString {
                    appendLine("package $generatedPackage")
                    appendLine()
                    appendLine("/**")
                    appendLine(" * Auto-generated visual test launcher helper for Windows (`mingwX64Test`).")
                    appendLine(" */")
                    appendLine("object GeneratedWindowsVisualTestLauncher {")
                    appendLine("    fun launch(args: Array<String> = emptyArray()) =")
                    appendLine("        com.nexustools.genericui.windows.WindowsLauncher.launch(args, GeneratedWindowsAppDescriptor.create())")
                    appendLine("}")
                }
            )
            return helperFile
        }

        private fun escapeKotlinString(value: String): String {
            val sb = StringBuilder(value.length + 2)
            sb.append('"')
            for (ch in value) {
                when (ch) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '$' -> sb.append("\\$")
                    else -> sb.append(ch)
                }
            }
            sb.append('"')
            return sb.toString()
        }
    }
}
