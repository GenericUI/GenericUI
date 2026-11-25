package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Gradle task (`generateGenericUiAndroidActivities`) that generates:
 * 1. One concrete `AppCompatActivity` Kotlin source file per discovered Android `@MainAppFragment`
 *    and `@AppFragment` (`<FragmentName>Activity.kt`, strictly 1 class per file).
 * 2. `GeneratedAndroidApplication.kt` (strictly 1 class per file) subclassing `GenericUiAndroidApplication`,
 *    building the [GenericUiResolvedVariantConfig] into `GenericUiAppDescriptor`, and registering every
 *    `<FragmentQualifiedName>::class -> <FragmentName>Activity::class.java` in `AndroidActivityRegistry`.
 */
@DisableCachingByDefault(because = "Generates Android Activity and Application Kotlin source files")
abstract class GenericUiGenerateAndroidActivitiesTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val androidEnabled: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val projectRootPath: Property<String>

    @get:Input
    @get:Optional
    abstract val sourceDirectories: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val appId: Property<String>

    @get:Input
    @get:Optional
    abstract val appName: Property<String>

    @get:Input
    @get:Optional
    abstract val appVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val packageName: Property<String>

    @get:Input
    @get:Optional
    abstract val windowTitle: Property<String>

    @get:Input
    @get:Optional
    abstract val windowWidth: Property<Int>

    @get:Input
    @get:Optional
    abstract val windowHeight: Property<Int>

    @get:Input
    @get:Optional
    abstract val minWindowWidth: Property<Int>

    @get:Input
    @get:Optional
    abstract val minWindowHeight: Property<Int>

    @get:Input
    @get:Optional
    abstract val windowResizable: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val fullscreen: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val windowAlwaysOnTop: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val windowAspectRatio: Property<Double>

    @get:Input
    @get:Optional
    abstract val appleIconExplicitlyProvided: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val themeMode: Property<String>

    @get:Input
    @get:Optional
    abstract val themeColor: Property<String>

    @get:Input
    @get:Optional
    abstract val lightThemeColors: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val darkThemeColors: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val hasCustomThemeOverrides: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val iconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val globalStyleSheet: Property<String>

    @get:Input
    @get:Optional
    abstract val precompiledStyleSheetObjectFqn: Property<String>

    @get:Input
    @get:Optional
    abstract val themeStyleSheetObjectFqn: Property<String>

    @get:Input
    @get:Optional
    abstract val providerInstallers: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val resourceRegistryInstallerExpression: Property<String>

    @get:Input
    @get:Optional
    abstract val enabledExtensionModulePaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val extensionOptions: org.gradle.api.provider.MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val generatedRegistrarPackageName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outDir = outputDirectory.get().asFile
        if (androidEnabled.orNull == false) {
            outDir.mkdirs()
            return
        }

        val rootDir = projectRootPath.orNull?.takeIf { it.isNotBlank() }?.let { File(it) } ?: project.projectDir
        val customDirs = sourceDirectories.orNull.orEmpty().map { File(it) }.filter { it.exists() }
        val discovered = if (customDirs.isNotEmpty()) {
            GenericUiFragmentScanner.scanSourceDirectories(customDirs)
        } else {
            GenericUiFragmentScanner.scanProject(rootDir)
        }

        val resolvedAppId = appId.orNull?.takeIf { it.isNotBlank() } ?: "com.nexustools.genericui.app"
        val resolvedAppName = appName.orNull?.takeIf { it.isNotBlank() } ?: "GenericUI"
        val resolvedPkg = packageName.orNull?.takeIf { it.isNotBlank() } ?: resolvedAppId
        val resolvedPrimary = themeColor.orNull?.takeIf { it.isNotBlank() } ?: "#0d6efd"
        val resolvedConfig = GenericUiResolvedVariantConfig(
            targetScope = GenericUiVariantScope.ANDROID,
            appId = resolvedAppId,
            appName = resolvedAppName,
            appVersion = appVersion.orNull?.takeIf { it.isNotBlank() } ?: "1.0.0",
            packageName = resolvedPkg,
            windowTitle = windowTitle.orNull?.takeIf { it.isNotBlank() } ?: resolvedAppName,
            windowWidth = windowWidth.orNull ?: 1280,
            windowHeight = windowHeight.orNull ?: 800,
            minWindowWidth = minWindowWidth.orNull ?: 320,
            minWindowHeight = minWindowHeight.orNull ?: 240,
            windowResizable = windowResizable.orNull ?: true,
            fullscreen = fullscreen.orNull ?: false,
            windowAlwaysOnTop = windowAlwaysOnTop.orNull ?: false,
            windowAspectRatio = windowAspectRatio.orNull?.takeIf { it.isFinite() && it > 0.0 },
            themeMode = themeMode.orNull?.takeIf { it.isNotBlank() } ?: "auto",
            themeColor = resolvedPrimary,
            lightThemeColors = lightThemeColors.orNull?.takeIf { it.isNotEmpty() }
                ?: GenericUiThemeConfig.defaultLightColors(resolvedPrimary),
            darkThemeColors = darkThemeColors.orNull?.takeIf { it.isNotEmpty() }
                ?: GenericUiThemeConfig.defaultDarkColors(resolvedPrimary),
            hasCustomThemeOverrides = hasCustomThemeOverrides.orNull ?: (resolvedPrimary != "#0d6efd"),
            iconPath = iconPath.orNull?.takeIf { it.isNotBlank() },
            appleIconExplicitlyProvided = appleIconExplicitlyProvided.orNull ?: false,
            globalStyleSheet = globalStyleSheet.orNull?.takeIf { it.isNotBlank() }
        )

        generateAndroidActivities(
            outputDir = outDir,
            discoveredFragments = discovered,
            resolvedConfig = resolvedConfig,
            providerInstallerExpressions = providerInstallers.orNull.orEmpty(),
            resourceRegistryInstallerExpr = resourceRegistryInstallerExpression.orNull?.takeIf { it.isNotBlank() },
            precompiledStyleSheetObjectFqn = precompiledStyleSheetObjectFqn.orNull?.takeIf { it.isNotBlank() },
            themeStyleSheetObjectFqn = themeStyleSheetObjectFqn.orNull?.takeIf { it.isNotBlank() },
            enabledExtensionModulePaths = enabledExtensionModulePaths.orNull.orEmpty(),
            projectRootDir = rootDir,
            extraSourceDirs = customDirs,
            extensionOptions = extensionOptions.orNull.orEmpty(),
            generatedRegistrarPackageName = generatedRegistrarPackageName.orNull.orEmpty()
        )
    }

    companion object {
        const val GENERATED_APPLICATION_SIMPLE_NAME: String = "GeneratedAndroidApplication"

        /**
         * Resolves the winning Android `@MainAppFragment` (if any) and the ordered list of all
         * Android-targeted `@MainAppFragment` and `@AppFragment` classes from [allDiscovered].
         */
        fun resolveAndroidFragments(
            allDiscovered: List<GenericUiDiscoveredFragment>
        ): Pair<GenericUiDiscoveredFragment?, List<GenericUiDiscoveredFragment>> {
            val androidCandidates = allDiscovered.filter { frag ->
                !frag.isApplicationClass &&
                    GenericUiVariantScope.resolveHighestSpecificityRank(frag.targets, GenericUiVariantScope.ANDROID) != null
            }

            for (frag in androidCandidates) {
                if (frag.hasIllegalMainDialogFlag) {
                    throw GradleException(
                        "@MainAppFragment class '${frag.qualifiedName}' must not declare 'dialog': " +
                            "the 'dialog' flag is only supported on @AppFragment, not @MainAppFragment."
                    )
                }
                if (!frag.hasNoArgConstructor) {
                    throw GradleException(
                        "Fragment '${frag.qualifiedName}' targeted at Android must declare a zero-argument constructor " +
                            "(or default values for all constructor parameters) so its generated Activity can instantiate it."
                    )
                }
            }

            val mainCandidates = androidCandidates.filter { it.isMainFragment }
            val winningMain: GenericUiDiscoveredFragment? = if (mainCandidates.isEmpty()) {
                null
            } else {
                val maxRank = mainCandidates.maxOf {
                    GenericUiVariantScope.resolveHighestSpecificityRank(it.targets, GenericUiVariantScope.ANDROID) ?: -1
                }
                val topRanked = mainCandidates
                    .filter {
                        GenericUiVariantScope.resolveHighestSpecificityRank(it.targets, GenericUiVariantScope.ANDROID) == maxRank
                    }
                    .distinctBy { it.qualifiedName }
                if (topRanked.size > 1) {
                    throw GradleException(
                        "Ambiguous @MainAppFragment for target 'android' at specificity rank $maxRank: " +
                            topRanked.joinToString(", ") { it.qualifiedName }
                    )
                }
                topRanked.single()
            }

            val orderedFragments = LinkedHashMap<String, GenericUiDiscoveredFragment>()
            if (winningMain != null) {
                orderedFragments[winningMain.qualifiedName] = winningMain
            }
            for (frag in androidCandidates) {
                if (!frag.isMainFragment || frag.qualifiedName == winningMain?.qualifiedName) {
                    orderedFragments.putIfAbsent(frag.qualifiedName, frag)
                }
            }

            return winningMain to orderedFragments.values.toList()
        }

        /**
         * Generates `<FragmentName>Activity.kt` files (1 class per file) and `GeneratedAndroidApplication.kt`
         * into [outputDir] and returns all generated files.
         */
        fun generateAndroidActivities(
            outputDir: File,
            discoveredFragments: List<GenericUiDiscoveredFragment>,
            resolvedConfig: GenericUiResolvedVariantConfig,
            providerInstallerExpressions: List<String> = emptyList(),
            resourceRegistryInstallerExpr: String? = null,
            precompiledStyleSheetObjectFqn: String? = null,
            themeStyleSheetObjectFqn: String? = null,
            enabledExtensionModulePaths: List<String> = emptyList(),
            projectRootDir: File? = null,
            extraSourceDirs: List<File> = emptyList(),
            extensionOptions: Map<String, String> = emptyMap(),
            generatedRegistrarPackageName: String = ""
        ): List<File> {
            if (outputDir.exists()) {
                outputDir.deleteRecursively()
            }
            outputDir.mkdirs()

            val (winningMain, androidFragments) = resolveAndroidFragments(discoveredFragments)
            val generatedFiles = mutableListOf<File>()
            val enabledExtensionsSet = enabledExtensionModulePaths.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val appPackage = resolvedConfig.packageName.trim()
                .ifEmpty { resolvedConfig.appId.trim() }
                .ifEmpty { winningMain?.packageName.orEmpty() }
                .ifEmpty { "com.nexustools.genericui.generated" }
            val effectiveGeneratedPackage = generatedRegistrarPackageName.trim().ifEmpty {
                if (appPackage.endsWith(".generated")) appPackage else "$appPackage.generated"
            }
            val scanSpecs = com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
                .allAnnotationScanSpecs(enabledExtensionsSet, projectRootDir)
            val discoveredSymbols = if (projectRootDir != null) {
                GenericUiFragmentScanner.scanProjectSymbols(projectRootDir, scanSpecs, extraSourceDirs)
            } else {
                emptyList()
            }
            val extContext = com.nexustools.genericui.plugin.spi.ExtensionBuildContext(
                projectRootDir = projectRootDir ?: File("."),
                projectName = resolvedConfig.appName,
                appId = resolvedConfig.appId.trim().ifEmpty { appPackage },
                appName = resolvedConfig.appName,
                appVersion = resolvedConfig.appVersion,
                baseName = resolvedConfig.appName,
                generatedPackageName = effectiveGeneratedPackage,
                enabledExtensionModulePaths = enabledExtensionsSet,
                discoveredFragments = discoveredFragments,
                discoveredSymbols = discoveredSymbols,
                extensionOptions = extensionOptions
            )
            com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
                .validateActiveContributors(extContext, discoveredSymbols)
            val globalSpiContributions = com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
                .collectAndroidContributions(extContext, null)

            for (fragment in androidFragments) {
                val pkgDir = if (fragment.packageName.isEmpty()) {
                    outputDir
                } else {
                    File(outputDir, fragment.packageName.replace('.', '/'))
                }
                pkgDir.mkdirs()
                val activityFile = File(pkgDir, "${fragment.generatedActivitySimpleName}.kt")
                val fragSpiContributions = com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
                    .collectAndroidContributions(extContext, fragment)
                activityFile.writeText(
                    renderActivitySource(
                        fragment = fragment,
                        enabledExtensionModulePaths = enabledExtensionModulePaths,
                        spiContributions = fragSpiContributions
                    )
                )
                generatedFiles.add(activityFile)
                for (contrib in fragSpiContributions) {
                    for (src in contrib.generatedSources) {
                        val rel = src.relativeFilePath.replace('\\', '/').trimStart('/')
                        if (rel.isNotEmpty()) {
                            val dst = File(outputDir, rel)
                            dst.parentFile?.mkdirs()
                            dst.writeText(src.sourceCode, Charsets.UTF_8)
                            generatedFiles.add(dst)
                        }
                    }
                }
            }

            for (contrib in globalSpiContributions) {
                for (src in contrib.generatedSources) {
                    val rel = src.relativeFilePath.replace('\\', '/').trimStart('/')
                    if (rel.isNotEmpty()) {
                        val dst = File(outputDir, rel)
                        dst.parentFile?.mkdirs()
                        dst.writeText(src.sourceCode, Charsets.UTF_8)
                        generatedFiles.add(dst)
                    }
                }
            }

            val mergedInstallers = (
                providerInstallerExpressions +
                    globalSpiContributions.flatMap { it.providerInstallerCalls }
                ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val extraAppImports = globalSpiContributions.flatMap { it.applicationImports }
                .map { it.trim().removePrefix("import ").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val extraOnCreateStatements = globalSpiContributions.flatMap { it.applicationOnCreateStatements }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            val appDir = File(outputDir, appPackage.replace('.', '/'))
            appDir.mkdirs()
            val appFile = File(appDir, "$GENERATED_APPLICATION_SIMPLE_NAME.kt")
            appFile.writeText(
                renderApplicationSource(
                    appPackage = appPackage,
                    mainFragment = winningMain,
                    fragments = androidFragments,
                    resolvedConfig = resolvedConfig,
                    providerInstallerExpressions = mergedInstallers,
                    resourceRegistryInstallerExpr = resourceRegistryInstallerExpr,
                    applicationClassName = GENERATED_APPLICATION_SIMPLE_NAME,
                    precompiledStyleSheetObjectFqn = precompiledStyleSheetObjectFqn,
                    themeStyleSheetObjectFqn = themeStyleSheetObjectFqn,
                    extraApplicationImports = extraAppImports,
                    extraApplicationOnCreateStatements = extraOnCreateStatements
                )
            )
            generatedFiles.add(appFile)

            return generatedFiles
        }

        /**
         * Renders the Kotlin source for a single concrete `<FragmentName>Activity.kt` file (1 class per file).
         */
        fun renderActivitySource(
            fragment: GenericUiDiscoveredFragment,
            enabledExtensionModulePaths: List<String> = emptyList(),
            spiContributions: List<com.nexustools.genericui.plugin.spi.AndroidExtensionContribution> = emptyList()
        ): String = buildString {
            val enabledExtensionsSet = enabledExtensionModulePaths
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            val contributions = AndroidActivityExtensionRegistry.resolveContributions(fragment, enabledExtensionsSet)

            val properties = (
                contributions.flatMap { it.properties } +
                    spiContributions.flatMap { it.activityProperties }
                )
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val onCreateStatements = (
                contributions.flatMap { it.onCreateAfterSuperStatements } +
                    spiContributions.flatMap { it.activityOnCreateAfterSuperStatements }
                )
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val onDestroyStatements = (
                contributions.flatMap { it.onDestroyBeforeSuperStatements } +
                    spiContributions.flatMap { it.activityOnDestroyBeforeSuperStatements }
                )
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val methods = (
                contributions.flatMap { it.methods } +
                    spiContributions.flatMap { it.activityMethods }
                )
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            val imports = linkedSetOf(
                "com.nexustools.genericui.android.GenericUiFragmentActivity",
                "com.nexustools.genericui.component.fragment.Fragment"
            )
            if (onCreateStatements.isNotEmpty()) {
                imports.add("android.os.Bundle")
            }
            for (contribution in contributions) {
                for (imp in contribution.imports) {
                    val normalized = imp.trim().removePrefix("import ").trim()
                    if (normalized.isNotEmpty()) {
                        imports.add(normalized)
                    }
                }
            }
            for (contribution in spiContributions) {
                for (imp in contribution.activityImports) {
                    val normalized = imp.trim().removePrefix("import ").trim()
                    if (normalized.isNotEmpty()) {
                        imports.add(normalized)
                    }
                }
            }

            if (fragment.packageName.isNotEmpty()) {
                appendLine("package ${fragment.packageName}")
                appendLine()
            }
            for (imp in imports.sorted()) {
                appendLine("import $imp")
            }
            appendLine()
            appendLine("open class ${fragment.generatedActivitySimpleName} : GenericUiFragmentActivity() {")
            if (properties.isNotEmpty()) {
                for (prop in properties) {
                    appendIndentedBlock(this, prop, "    ")
                }
                appendLine()
            }
            appendLine("    override fun createFragment(): Fragment = ${fragment.qualifiedName}()")
            if (onCreateStatements.isNotEmpty()) {
                appendLine()
                appendLine("    override fun onCreate(savedInstanceState: Bundle?) {")
                appendLine("        super.onCreate(savedInstanceState)")
                for (stmt in onCreateStatements) {
                    appendIndentedBlock(this, stmt, "        ")
                }
                appendLine("    }")
            }
            if (onDestroyStatements.isNotEmpty()) {
                appendLine()
                appendLine("    override fun onDestroy() {")
                for (stmt in onDestroyStatements) {
                    appendIndentedBlock(this, stmt, "        ")
                }
                appendLine("        super.onDestroy()")
                appendLine("    }")
            }
            for (method in methods) {
                appendLine()
                appendIndentedBlock(this, method, "    ")
            }
            appendLine("}")
        }

        private fun appendIndentedBlock(sb: StringBuilder, source: String, indent: String) {
            for (line in source.lines()) {
                if (line.isBlank()) {
                    sb.appendLine()
                } else {
                    sb.appendLine("$indent$line")
                }
            }
        }

        /**
         * Renders the Kotlin source for `GeneratedAndroidApplication.kt` (1 class per file).
         */
        fun renderApplicationSource(
            appPackage: String,
            mainFragment: GenericUiDiscoveredFragment?,
            fragments: List<GenericUiDiscoveredFragment>,
            resolvedConfig: GenericUiResolvedVariantConfig,
            providerInstallerExpressions: List<String> = emptyList(),
            resourceRegistryInstallerExpr: String? = null,
            applicationClassName: String = GENERATED_APPLICATION_SIMPLE_NAME,
            precompiledStyleSheetObjectFqn: String? = null,
            themeStyleSheetObjectFqn: String? = null,
            extraApplicationImports: List<String> = emptyList(),
            extraApplicationOnCreateStatements: List<String> = emptyList()
        ): String = buildString {
            val lightMap = GenericUiThemeConfig.defaultLightColors(resolvedConfig.themeColor).toMutableMap().apply {
                putAll(resolvedConfig.lightThemeColors.filterValues { it.isNotBlank() })
            }
            val darkMap = GenericUiThemeConfig.defaultDarkColors(resolvedConfig.themeColor).toMutableMap().apply {
                putAll(resolvedConfig.darkThemeColors.filterValues { it.isNotBlank() })
            }
            if (appPackage.isNotEmpty()) {
                appendLine("package $appPackage")
                appendLine()
            }
            val appImports = linkedSetOf(
                "com.nexustools.genericui.android.AndroidActivityRegistry",
                "com.nexustools.genericui.android.GenericUiAndroidApplication",
                "com.nexustools.genericui.core.GenericUI",
                "com.nexustools.genericui.core.GenericUiAppDescriptor",
                "com.nexustools.genericui.enums.ui.ThemeMode",
                "com.nexustools.genericui.style.color.RgbaColor",
                "com.nexustools.genericui.style.theme.ThemeColorSet",
                "com.nexustools.genericui.style.theme.ThemeConfig"
            )
            extraApplicationImports.forEach { appImports.add(it) }
            appImports.sorted().forEach { appendLine("import $it") }
            appendLine()
            appendLine("open class $applicationClassName : GenericUiAndroidApplication() {")
            appendLine("    @Suppress(\"EXTENSION_SHADOWED_BY_MEMBER\", \"UnusedPrivateMember\")")
            appendLine("    private fun ThemeColorSet.Companion.fromHex(")
            appendLine("        primary: String = \"#0d6efd\",")
            appendLine("        secondary: String = \"#6c757d\",")
            appendLine("        accent: String = primary,")
            appendLine("        success: String = \"#198754\",")
            appendLine("        danger: String = \"#dc3545\",")
            appendLine("        warning: String = \"#ffc107\",")
            appendLine("        info: String = \"#0dcaf0\",")
            appendLine("        light: String = \"#f8f9fa\",")
            appendLine("        dark: String = \"#212529\",")
            appendLine("        background: String = \"#ffffff\",")
            appendLine("        surface: String = \"#f8f9fa\",")
            appendLine("        textPrimary: String = \"#212529\",")
            appendLine("        textSecondary: String = \"#6c757d\",")
            appendLine("        border: String = \"#dee2e6\"")
            appendLine("    ): ThemeColorSet = ThemeColorSet(")
            appendLine("        primary = RgbaColor.fromHex(primary),")
            appendLine("        secondary = RgbaColor.fromHex(secondary),")
            appendLine("        accent = RgbaColor.fromHex(accent),")
            appendLine("        success = RgbaColor.fromHex(success),")
            appendLine("        danger = RgbaColor.fromHex(danger),")
            appendLine("        warning = RgbaColor.fromHex(warning),")
            appendLine("        info = RgbaColor.fromHex(info),")
            appendLine("        light = RgbaColor.fromHex(light),")
            appendLine("        dark = RgbaColor.fromHex(dark),")
            appendLine("        background = RgbaColor.fromHex(background),")
            appendLine("        onBackground = RgbaColor.fromHex(textPrimary),")
            appendLine("        surface = RgbaColor.fromHex(surface),")
            appendLine("        onSurface = RgbaColor.fromHex(textSecondary)")
            appendLine("    )")
            appendLine()
            appendLine("    @Suppress(\"FunctionName\", \"UnusedPrivateMember\")")
            appendLine("    private fun ThemeConfig(")
            appendLine("        light: ThemeColorSet,")
            appendLine("        dark: ThemeColorSet,")
            appendLine("        mode: ThemeMode = ThemeMode.fromString(${quoteKotlin(resolvedConfig.themeMode)})")
            appendLine("    ): ThemeConfig = com.nexustools.genericui.style.theme.ThemeConfig(")
            appendLine("        mode = mode,")
            appendLine("        colors = light,")
            appendLine("        lightColors = light,")
            appendLine("        darkColors = dark")
            appendLine("    )")
            appendLine()
            appendLine("    override fun createAppDescriptor(): GenericUiAppDescriptor = GenericUiAppDescriptor(")
            appendLine("        appId = ${quoteKotlin(resolvedConfig.appId)},")
            appendLine("        appName = ${quoteKotlin(resolvedConfig.appName)},")
            appendLine("        appVersion = ${quoteKotlin(resolvedConfig.appVersion)},")
            appendLine("        windowTitle = ${quoteKotlin(resolvedConfig.windowTitle)},")
            appendLine("        windowWidth = ${resolvedConfig.windowWidth},")
            appendLine("        windowHeight = ${resolvedConfig.windowHeight},")
            appendLine("        minWindowWidth = ${resolvedConfig.minWindowWidth},")
            appendLine("        minWindowHeight = ${resolvedConfig.minWindowHeight},")
            appendLine("        windowResizable = ${resolvedConfig.windowResizable},")
            appendLine("        fullscreen = ${resolvedConfig.fullscreen},")
            appendLine("        themeMode = ${quoteKotlin(resolvedConfig.themeMode)},")
            appendLine("        themeColor = ${quoteKotlin(resolvedConfig.themeColor)},")
            appendLine("        themeConfig = ThemeConfig(")
            appendLine("            light = ThemeColorSet.fromHex(")
            appendLine("                primary = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_PRIMARY))},")
            appendLine("                secondary = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_SECONDARY))},")
            appendLine("                accent = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_ACCENT))},")
            appendLine("                success = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_SUCCESS))},")
            appendLine("                danger = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_DANGER))},")
            appendLine("                warning = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_WARNING))},")
            appendLine("                info = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_INFO))},")
            appendLine("                light = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_LIGHT))},")
            appendLine("                dark = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_DARK))},")
            appendLine("                background = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_BACKGROUND))},")
            appendLine("                surface = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_SURFACE))},")
            appendLine("                textPrimary = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_TEXT_PRIMARY))},")
            appendLine("                textSecondary = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_TEXT_SECONDARY))},")
            appendLine("                border = ${quoteKotlin(lightMap.getValue(GenericUiThemeConfig.KEY_BORDER))}")
            appendLine("            ),")
            appendLine("            dark = ThemeColorSet.fromHex(")
            appendLine("                primary = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_PRIMARY))},")
            appendLine("                secondary = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_SECONDARY))},")
            appendLine("                accent = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_ACCENT))},")
            appendLine("                success = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_SUCCESS))},")
            appendLine("                danger = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_DANGER))},")
            appendLine("                warning = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_WARNING))},")
            appendLine("                info = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_INFO))},")
            appendLine("                light = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_LIGHT))},")
            appendLine("                dark = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_DARK))},")
            appendLine("                background = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_BACKGROUND))},")
            appendLine("                surface = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_SURFACE))},")
            appendLine("                textPrimary = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_TEXT_PRIMARY))},")
            appendLine("                textSecondary = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_TEXT_SECONDARY))},")
            appendLine("                border = ${quoteKotlin(darkMap.getValue(GenericUiThemeConfig.KEY_BORDER))}")
            appendLine("            )")
            appendLine("        ),")
            appendLine("        iconPath = ${resolvedConfig.iconPath?.let { quoteKotlin(it) } ?: "null"},")
            val styleSheetPath = resolvedConfig.globalStyleSheet?.trim()?.takeIf { it.isNotEmpty() }
            val resolvedStyleSheetObjectFqn = precompiledStyleSheetObjectFqn?.trim()?.takeIf { it.isNotEmpty() }
            val resolvedThemeStyleSheetFqn = themeStyleSheetObjectFqn?.trim()?.takeIf { it.isNotEmpty() }
            if (styleSheetPath != null) {
                appendLine("        globalStyleSheetPath = ${quoteKotlin(styleSheetPath)},")
                if (resolvedStyleSheetObjectFqn != null || resolvedThemeStyleSheetFqn != null) {
                    appendLine("        precompiledStyleSheetInstaller = { app ->")
                    if (resolvedStyleSheetObjectFqn != null) {
                        appendLine("            $resolvedStyleSheetObjectFqn.install(app)")
                    }
                    if (resolvedThemeStyleSheetFqn != null && resolvedThemeStyleSheetFqn != resolvedStyleSheetObjectFqn) {
                        appendLine("            $resolvedThemeStyleSheetFqn.install(app)")
                    }
                    appendLine("            app.addStyleSheet(${quoteKotlin(styleSheetPath)})")
                    appendLine("        },")
                } else {
                    appendLine("        precompiledStyleSheetInstaller = { app -> app.addStyleSheet(${quoteKotlin(styleSheetPath)}) },")
                }
            } else if (resolvedThemeStyleSheetFqn != null) {
                appendLine("        precompiledStyleSheetInstaller = { app -> $resolvedThemeStyleSheetFqn.install(app) },")
            }
            if (mainFragment != null) {
                appendLine("        mainFragmentClass = ${mainFragment.qualifiedName}::class,")
                appendLine("        mainFragmentFactory = { _: com.nexustools.genericui.component.fragment.FragmentBundle? -> ${mainFragment.qualifiedName}() },")
            } else {
                appendLine("        mainFragmentClass = null,")
                appendLine("        mainFragmentFactory = null,")
            }

            if (fragments.isEmpty()) {
                appendLine("        fragmentRegistrations = emptyList(),")
            } else {
                appendLine("        fragmentRegistrations = listOf(")
                fragments.forEachIndexed { index, frag ->
                    val comma = if (index < fragments.lastIndex) "," else ""
                    appendLine("            ${frag.qualifiedName}::class to { _: com.nexustools.genericui.component.fragment.FragmentBundle? -> ${frag.qualifiedName}() }$comma")
                }
                appendLine("        ),")
            }
            val metadataFragments = (listOfNotNull(mainFragment) + fragments)
                .distinctBy { it.qualifiedName }
            if (metadataFragments.isEmpty()) {
                appendLine("        fragmentIds = emptyMap(),")
            } else {
                appendLine("        fragmentIds = mapOf(")
                metadataFragments.forEachIndexed { index, frag ->
                    val comma = if (index < metadataFragments.lastIndex) "," else ""
                    appendLine("            ${frag.qualifiedName}::class to ${quoteKotlin(frag.qualifiedName)}$comma")
                }
                appendLine("        ),")
            }
            val routedFragments = metadataFragments.filter { it.route.trim().isNotEmpty() }
            if (routedFragments.isEmpty()) {
                appendLine("        fragmentRoutes = emptyMap(),")
            } else {
                appendLine("        fragmentRoutes = mapOf(")
                routedFragments.forEachIndexed { index, frag ->
                    val comma = if (index < routedFragments.lastIndex) "," else ""
                    appendLine("            ${frag.qualifiedName}::class to ${quoteKotlin(frag.route.trim())}$comma")
                }
                appendLine("        ),")
            }
            if (metadataFragments.isEmpty()) {
                appendLine("        fragmentMetadata = emptyMap(),")
            } else {
                appendLine("        fragmentMetadata = mapOf(")
                metadataFragments.forEachIndexed { index, frag ->
                    val comma = if (index < metadataFragments.lastIndex) "," else ""
                    val isMain = mainFragment != null && frag.qualifiedName == mainFragment.qualifiedName
                    val effectiveDialog = if (isMain || frag.isMainFragment) false else frag.dialog
                    appendLine(
                        "            ${frag.qualifiedName}::class to com.nexustools.genericui.component.fragment.FragmentMetadata(" +
                            "fragmentId = ${quoteKotlin(frag.qualifiedName)}, " +
                            "route = ${quoteKotlin(frag.route.trim())}, " +
                            "label = ${quoteKotlin(frag.label.trim())}, " +
                            "isMainFragment = $isMain, " +
                            "multipleWindows = ${frag.multipleWindows}, " +
                            "dialog = $effectiveDialog)$comma"
                    )
                }
                appendLine("        ),")
            }

            val installers = providerInstallerExpressions.map { it.trim() }.filter { it.isNotEmpty() }
            if (installers.isEmpty()) {
                appendLine("        providerInstallers = emptyList(),")
            } else {
                appendLine("        providerInstallers = listOf(")
                installers.forEachIndexed { index, expr ->
                    val comma = if (index < installers.lastIndex) "," else ""
                    val lambdaExpr = if (expr.startsWith("{") || expr.contains("::")) expr else "{ $expr }"
                    appendLine("            $lambdaExpr$comma")
                }
                appendLine("        ),")
            }

            val resExpr = resourceRegistryInstallerExpr?.trim()?.takeIf { it.isNotEmpty() }
            if (resExpr != null) {
                val lambdaExpr = if (resExpr.startsWith("{") || resExpr.contains("::")) resExpr else "{ $resExpr }"
                appendLine("        resourceRegistryInstaller = $lambdaExpr,")
            } else {
                appendLine("        resourceRegistryInstaller = null,")
            }
            appendLine("        windowAlwaysOnTop = ${resolvedConfig.windowAlwaysOnTop},")
            appendLine("        windowAspectRatio = ${resolvedConfig.windowAspectRatio?.toString() ?: "null"},")
            appendLine("        appleIconExplicitlyProvided = ${resolvedConfig.appleIconExplicitlyProvided}")
            appendLine("    )")
            appendLine()
            appendLine("    fun configureTheme(app: com.nexustools.genericui.component.shell.IApplication) {")
            appendLine("        val descriptor = createAppDescriptor()")
            appendLine("        GenericUI.defaultThemeConfig = descriptor.themeConfig")
            appendLine("        app.setThemeConfig(descriptor.themeConfig)")
            if (resolvedThemeStyleSheetFqn != null) {
                appendLine("        com.nexustools.genericui.style.theme.ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet(")
                appendLine("            $resolvedThemeStyleSheetFqn.name,")
                appendLine("            $resolvedThemeStyleSheetFqn.toCssStyleSheet()")
                appendLine("        )")
                appendLine("        $resolvedThemeStyleSheetFqn.install(app)")
            }
            appendLine("    }")
            if (styleSheetPath != null || resolvedThemeStyleSheetFqn != null) {
                appendLine()
                appendLine("    fun configureGlobalStyleSheet(app: com.nexustools.genericui.component.shell.IApplication) {")
                if (resolvedStyleSheetObjectFqn != null) {
                    appendLine("        $resolvedStyleSheetObjectFqn.install(app)")
                }
                if (resolvedThemeStyleSheetFqn != null && resolvedThemeStyleSheetFqn != resolvedStyleSheetObjectFqn) {
                    appendLine("        $resolvedThemeStyleSheetFqn.install(app)")
                }
                if (styleSheetPath != null) {
                    appendLine("        app.addStyleSheet(${quoteKotlin(styleSheetPath)})")
                }
                appendLine("    }")
            }
            appendLine()
            appendLine("    override fun registerActivities() {")
            for (frag in metadataFragments) {
                val isMain = mainFragment != null && frag.qualifiedName == mainFragment.qualifiedName
                appendLine(
                    "        AndroidActivityRegistry.register(" +
                        "fragmentClass = ${frag.qualifiedName}::class, " +
                        "activityClass = ${frag.generatedActivityQualifiedName}::class.java, " +
                        "isMain = $isMain)"
                )
                appendLine(
                    "        AndroidActivityRegistry.registerFragmentId(" +
                        "fragmentId = ${quoteKotlin(frag.qualifiedName)}, " +
                        "activityClass = ${frag.generatedActivityQualifiedName}::class.java)"
                )
            }
            appendLine("    }")
            if (extraApplicationOnCreateStatements.isNotEmpty()) {
                appendLine()
                appendLine("    override fun onCreate() {")
                appendLine("        super.onCreate()")
                for (stmt in extraApplicationOnCreateStatements) {
                    appendIndentedBlock(this, stmt, "        ")
                }
                appendLine("    }")
            }
            appendLine("}")
        }

        private fun quoteKotlin(value: String): String {
            val escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("$", "\\$")
            return "\"$escaped\""
        }
    }
}
