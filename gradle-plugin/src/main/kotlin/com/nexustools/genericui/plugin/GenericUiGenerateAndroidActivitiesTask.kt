package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
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
    abstract val providerInstallers: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val resourceRegistryInstallerExpression: Property<String>

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
            themeMode = themeMode.orNull?.takeIf { it.isNotBlank() } ?: "auto",
            themeColor = resolvedPrimary,
            lightThemeColors = lightThemeColors.orNull?.takeIf { it.isNotEmpty() }
                ?: GenericUiThemeConfig.defaultLightColors(resolvedPrimary),
            darkThemeColors = darkThemeColors.orNull?.takeIf { it.isNotEmpty() }
                ?: GenericUiThemeConfig.defaultDarkColors(resolvedPrimary),
            hasCustomThemeOverrides = hasCustomThemeOverrides.orNull ?: (resolvedPrimary != "#0d6efd"),
            iconPath = iconPath.orNull?.takeIf { it.isNotBlank() },
            globalStyleSheet = globalStyleSheet.orNull?.takeIf { it.isNotBlank() }
        )

        generateAndroidActivities(
            outputDir = outDir,
            discoveredFragments = discovered,
            resolvedConfig = resolvedConfig,
            providerInstallerExpressions = providerInstallers.orNull.orEmpty(),
            resourceRegistryInstallerExpr = resourceRegistryInstallerExpression.orNull?.takeIf { it.isNotBlank() },
            precompiledStyleSheetObjectFqn = precompiledStyleSheetObjectFqn.orNull?.takeIf { it.isNotBlank() }
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
            precompiledStyleSheetObjectFqn: String? = null
        ): List<File> {
            if (outputDir.exists()) {
                outputDir.deleteRecursively()
            }
            outputDir.mkdirs()

            val (winningMain, androidFragments) = resolveAndroidFragments(discoveredFragments)
            val generatedFiles = mutableListOf<File>()

            for (fragment in androidFragments) {
                val pkgDir = if (fragment.packageName.isEmpty()) {
                    outputDir
                } else {
                    File(outputDir, fragment.packageName.replace('.', '/'))
                }
                pkgDir.mkdirs()
                val activityFile = File(pkgDir, "${fragment.generatedActivitySimpleName}.kt")
                activityFile.writeText(renderActivitySource(fragment))
                generatedFiles.add(activityFile)
            }

            val appPackage = resolvedConfig.packageName.trim()
                .ifEmpty { resolvedConfig.appId.trim() }
                .ifEmpty { winningMain?.packageName.orEmpty() }
                .ifEmpty { "com.nexustools.genericui.generated" }

            val appDir = File(outputDir, appPackage.replace('.', '/'))
            appDir.mkdirs()
            val appFile = File(appDir, "$GENERATED_APPLICATION_SIMPLE_NAME.kt")
            appFile.writeText(
                renderApplicationSource(
                    appPackage = appPackage,
                    mainFragment = winningMain,
                    fragments = androidFragments,
                    resolvedConfig = resolvedConfig,
                    providerInstallerExpressions = providerInstallerExpressions,
                    resourceRegistryInstallerExpr = resourceRegistryInstallerExpr,
                    applicationClassName = GENERATED_APPLICATION_SIMPLE_NAME,
                    precompiledStyleSheetObjectFqn = precompiledStyleSheetObjectFqn
                )
            )
            generatedFiles.add(appFile)

            return generatedFiles
        }

        /**
         * Renders the Kotlin source for a single concrete `<FragmentName>Activity.kt` file (1 class per file).
         */
        fun renderActivitySource(fragment: GenericUiDiscoveredFragment): String = buildString {
            if (fragment.packageName.isNotEmpty()) {
                appendLine("package ${fragment.packageName}")
                appendLine()
            }
            appendLine("import com.nexustools.genericui.android.GenericUiFragmentActivity")
            appendLine("import com.nexustools.genericui.component.fragment.Fragment")
            appendLine()
            appendLine("class ${fragment.generatedActivitySimpleName} : GenericUiFragmentActivity() {")
            appendLine("    override fun createFragment(): Fragment = ${fragment.qualifiedName}()")
            appendLine("}")
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
            precompiledStyleSheetObjectFqn: String? = null
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
            appendLine("import com.nexustools.genericui.android.AndroidActivityRegistry")
            appendLine("import com.nexustools.genericui.android.GenericUiAndroidApplication")
            appendLine("import com.nexustools.genericui.core.GenericUI")
            appendLine("import com.nexustools.genericui.core.GenericUiAppDescriptor")
            appendLine("import com.nexustools.genericui.enums.ui.ThemeMode")
            appendLine("import com.nexustools.genericui.style.color.RgbaColor")
            appendLine("import com.nexustools.genericui.style.theme.ThemeColorSet")
            appendLine("import com.nexustools.genericui.style.theme.ThemeConfig")
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
            if (styleSheetPath != null) {
                appendLine("        globalStyleSheetPath = ${quoteKotlin(styleSheetPath)},")
                if (resolvedStyleSheetObjectFqn != null) {
                    appendLine("        precompiledStyleSheetInstaller = { app ->")
                    appendLine("            $resolvedStyleSheetObjectFqn.install(app)")
                    appendLine("            app.addStyleSheet(${quoteKotlin(styleSheetPath)})")
                    appendLine("        },")
                } else {
                    appendLine("        precompiledStyleSheetInstaller = { app -> app.addStyleSheet(${quoteKotlin(styleSheetPath)}) },")
                }
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
                appendLine("        resourceRegistryInstaller = $lambdaExpr")
            } else {
                appendLine("        resourceRegistryInstaller = null")
            }
            appendLine("    )")
            appendLine()
            appendLine("    fun configureTheme(app: com.nexustools.genericui.component.shell.IApplication) {")
            appendLine("        val descriptor = createAppDescriptor()")
            appendLine("        GenericUI.defaultThemeConfig = descriptor.themeConfig")
            appendLine("        app.setThemeConfig(descriptor.themeConfig)")
            appendLine("    }")
            if (styleSheetPath != null) {
                appendLine()
                appendLine("    fun configureGlobalStyleSheet(app: com.nexustools.genericui.component.shell.IApplication) {")
                if (resolvedStyleSheetObjectFqn != null) {
                    appendLine("        $resolvedStyleSheetObjectFqn.install(app)")
                }
                appendLine("        app.addStyleSheet(${quoteKotlin(styleSheetPath)})")
                appendLine("    }")
            }
            appendLine()
            appendLine("    override fun registerActivities() {")
            for (frag in fragments) {
                val isMain = mainFragment != null && frag.qualifiedName == mainFragment.qualifiedName
                appendLine(
                    "        AndroidActivityRegistry.register(" +
                        "fragmentClass = ${frag.qualifiedName}::class, " +
                        "activityClass = ${frag.generatedActivityQualifiedName}::class.java, " +
                        "isMain = $isMain)"
                )
            }
            appendLine("    }")
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
