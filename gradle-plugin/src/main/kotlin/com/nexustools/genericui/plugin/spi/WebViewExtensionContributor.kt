package com.nexustools.genericui.plugin.spi

import com.nexustools.genericui.plugin.GenericUiVariantScope
import com.nexustools.genericui.plugin.GenericUiWebViewExtensionSpec

/**
 * Built-in SPI contributor for `:extensions:ui:webview` on Desktop targets:
 * - Native Desktop (`WINDOWS`, `MACOS`, `GTK`, `KDE`): contributes a dedicated
 *   `${baseName}-webview` [SecondaryDesktopExecutableContribution] whose generated entrypoint
 *   (`mainWebViewWorker`) registers binary codecs, active Alien companion extensions, and runs
 *   `com.nexustools.genericui.ext.webview.desktop.process.WebViewWorkerMain.main(args)`.
 * - Swing (`SWING`): contributes `<genPkg>.GeneratedSwingWebViewWorkerMain` (`@JvmStatic fun main`)
 *   inside the main JVM JAR (no native executable or C stub).
 * - Runtime properties: records the Swing worker main class, native worker binary base name, and
 *   WebView capability flags into `GenericUiExtensionRegistry`.
 */
object WebViewExtensionContributor : GenericUiExtensionContributor {

    const val WEBVIEW_MODULE_PATH: String = ":extensions:ui:webview"
    const val WEBVIEW_DSL_MODULE_PATH: String = ":extensions:ui:webview:dsl"
    const val DEFAULT_GENERATED_PACKAGE: String = "com.nexustools.genericui.generated"

    private const val ALIEN_MODULE_PATH: String = ":extensions:ui:alien"
    private const val IPC_MODULE_PATH: String = ":extensions:system:ipc"
    private const val SVG_MODULE_PATH: String = ":extensions:graphics:svg"
    private const val SVG_ALIEN_MODULE_PATH: String = ":extensions:graphics:svg:alien"
    private const val CANVAS2D_ALIEN_MODULE_PATH: String = ":extensions:graphics:canvas2d:alien"
    private const val CANVAS3D_ALIEN_MODULE_PATH: String = ":extensions:graphics:canvas3d:alien"
    private const val MEDIA_ALIEN_MODULE_PATH: String = ":extensions:media:media:alien"

    private val NATIVE_DESKTOP_SCOPES = listOf(
        GenericUiVariantScope.WINDOWS,
        GenericUiVariantScope.MACOS,
        GenericUiVariantScope.GTK,
        GenericUiVariantScope.KDE
    )

    private val ALL_DESKTOP_SCOPES = NATIVE_DESKTOP_SCOPES + GenericUiVariantScope.SWING

    override val extensionModulePath: String = WEBVIEW_MODULE_PATH

    override val dslModulePath: String? = WEBVIEW_DSL_MODULE_PATH

    fun resolveWorkerBinaryBaseName(baseName: String): String =
        "${baseName.trim().ifEmpty { "app" }}-webview"

    fun resolveGeneratedPackage(context: ExtensionBuildContext): String {
        val explicit = context.generatedPackageName.trim()
        if (explicit.isNotEmpty()) return explicit

        val mainFragPkg = context.discoveredFragments.firstOrNull {
            it.isMainFragment && it.packageName.isNotBlank()
        }?.packageName?.trim()
        if (!mainFragPkg.isNullOrEmpty()) {
            return if (mainFragPkg.endsWith(".generated")) mainFragPkg else "$mainFragPkg.generated"
        }

        val appIdPkg = context.appId.trim().takeIf { it.isNotEmpty() }
        if (appIdPkg != null) {
            return if (appIdPkg.endsWith(".generated")) appIdPkg else "$appIdPkg.generated"
        }

        return DEFAULT_GENERATED_PACKAGE
    }

    override fun providerInstallerCalls(
        backendScope: GenericUiVariantScope,
        context: ExtensionBuildContext
    ): List<String> {
        if (WEBVIEW_MODULE_PATH !in context.enabledExtensionModulePaths) return emptyList()
        if (!isDesktopScope(backendScope)) return emptyList()

        val genPkg = resolveGeneratedPackage(context)
        val workerBaseName = resolveWorkerBinaryBaseName(
            context.baseName.trim().ifEmpty {
                context.projectName.trim().ifEmpty { context.appName.trim() }
            }
        )
        return buildDesktopRuntimePropertyCalls(
            backendScope = backendScope,
            genPkg = genPkg,
            workerBaseName = workerBaseName,
            options = context.extensionOptions
        )
    }

    override fun contributeDesktop(context: ExtensionBuildContext): DesktopExtensionContribution? {
        if (WEBVIEW_MODULE_PATH !in context.enabledExtensionModulePaths) return null

        val enabledDesktop = resolveEnabledDesktopScopes(context)
        if (enabledDesktop.isEmpty()) return null

        val genPkg = resolveGeneratedPackage(context)
        val pkgDir = genPkg.replace('.', '/')
        val workerBaseName = resolveWorkerBinaryBaseName(
            context.baseName.trim().ifEmpty {
                context.projectName.trim().ifEmpty { context.appName.trim() }
            }
        )

        val secondaryExes = mutableListOf<SecondaryDesktopExecutableContribution>()
        val generatedSources = mutableListOf<ExtensionGeneratedSource>()
        val initStatements = resolveWorkerInitStatements(
            context = context,
            genPkg = genPkg,
            workerBaseName = workerBaseName
        )

        val hasNativeDesktop = NATIVE_DESKTOP_SCOPES.any { it in enabledDesktop }
        if (hasNativeDesktop) {
            secondaryExes.add(
                SecondaryDesktopExecutableContribution(
                    binaryBaseName = workerBaseName,
                    entryPointFqn = "$genPkg.mainWebViewWorker",
                    windowsSubsystem = "windows",
                    macosExtraLinkerOpts = listOf("-Wl,-rpath,@executable_path/../../../../Frameworks"),
                    macosHelperBundleSuffix = "WebView",
                    macosHelperBundleIdSuffix = "webview",
                    macosHelperLsUiElement = true,
                    legacyBinaryBaseNames = listOf("genericui-webview-process")
                )
            )
            for (scope in NATIVE_DESKTOP_SCOPES) {
                if (scope !in enabledDesktop) continue
                val scopePascal = scope.id.replaceFirstChar { it.uppercaseChar() }
                generatedSources.add(
                    ExtensionGeneratedSource(
                        relativeFilePath = "$pkgDir/Generated${scopePascal}WebViewWorkerMain.kt",
                        sourceCode = buildNativeDesktopWorkerMainSource(
                            genPkg = genPkg,
                            initStatements = initStatements
                        ),
                        targetScope = scope
                    )
                )
            }
        }

        if (GenericUiVariantScope.SWING in enabledDesktop) {
            generatedSources.add(
                ExtensionGeneratedSource(
                    relativeFilePath = "$pkgDir/GeneratedSwingWebViewWorkerMain.kt",
                    sourceCode = buildSwingWorkerMainSource(
                        genPkg = genPkg,
                        initStatements = initStatements
                    ),
                    targetScope = GenericUiVariantScope.SWING
                )
            )
        }

        return DesktopExtensionContribution(
            secondaryExecutables = secondaryExes,
            generatedSources = generatedSources
        )
    }

    internal fun buildDesktopRuntimePropertyCalls(
        backendScope: GenericUiVariantScope,
        genPkg: String,
        workerBaseName: String,
        options: Map<String, String>
    ): List<String> = buildList {
        val registryFqn = "com.nexustools.genericui.core.GenericUiExtensionRegistry"
        if (backendScope != GenericUiVariantScope.SWING) {
            add("$registryFqn.setExtensionProperty(${quoteKotlin("webview.desktop.workerBinaryBaseName")}, ${quoteKotlin(workerBaseName)})")
        }
        if (backendScope == GenericUiVariantScope.SWING || backendScope == GenericUiVariantScope.DESKTOP_COMMON) {
            add("$registryFqn.setExtensionProperty(${quoteKotlin("webview.swing.workerMainClass")}, ${quoteKotlin("$genPkg.GeneratedSwingWebViewWorkerMain")})")
        }
        add("$registryFqn.setExtensionProperty(${quoteKotlin("webview.capability.canvas2d")}, ${optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_CANVAS2D)})")
        add("$registryFqn.setExtensionProperty(${quoteKotlin("webview.capability.webgl")}, ${optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_WEBGL)})")
        add("$registryFqn.setExtensionProperty(${quoteKotlin("webview.capability.media")}, ${optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_MEDIA)})")
        add("$registryFqn.setExtensionProperty(${quoteKotlin("webview.capability.svg")}, ${optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_SVG)})")
        add("$registryFqn.setExtensionProperty(${quoteKotlin("webview.capability.webAudio")}, ${optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_WEBAUDIO)})")
        add("$registryFqn.setExtensionProperty(${quoteKotlin("webview.capability.webSockets")}, ${optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_WEBSOCKETS)})")
        add("$registryFqn.setExtensionProperty(${quoteKotlin("webview.capability.storage")}, ${optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_STORAGE)})")
        add("$registryFqn.setExtensionProperty(${quoteKotlin("webview.capability.shadowDom")}, ${optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_SHADOW_DOM)})")
        add("$registryFqn.setExtensionProperty(${quoteKotlin("webview.capability.iframes")}, ${optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_IFRAMES)})")
        add("$registryFqn.setExtensionProperty(${quoteKotlin("webview.capability.workers")}, ${optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_WORKERS)})")
    }

    private fun resolveWorkerInitStatements(
        context: ExtensionBuildContext,
        genPkg: String,
        workerBaseName: String
    ): List<String> = buildList {
        val binaryCodecsEnabled = context.extensionOptions[ServiceWorkerExtensionContributor.OPTION_BINARY_CODECS_ENABLED]
            ?.toBooleanStrictOrNull() ?: true
        if (binaryCodecsEnabled) {
            add("$genPkg.GeneratedBinaryCodecRegistrar.registerAll()")
        }

        val resourcesEnabled = context.extensionOptions[ServiceWorkerExtensionContributor.OPTION_RESOURCES_ENABLED]
            ?.toBooleanStrictOrNull() ?: false
        val resPkg = context.extensionOptions[ServiceWorkerExtensionContributor.OPTION_RESOURCE_PACKAGE]?.trim().orEmpty()
        if (resourcesEnabled && resPkg.isNotEmpty()) {
            add("$resPkg.GeneratedResourceRegistry.ensureInitialized()")
        }

        val schemaEnabled = SchemaExtensionContributor.SCHEMA_MODULE_PATH in context.enabledExtensionModulePaths ||
            (context.extensionOptions[ServiceWorkerExtensionContributor.OPTION_SCHEMA_ENABLED]?.toBooleanStrictOrNull() == true)
        if (schemaEnabled) {
            val schemaPkg = SchemaExtensionContributor.resolveRegistrarPackage(context)
            add("$schemaPkg.GeneratedSchemaRegistrar.ensureRegistered()")
        }

        val enabled = context.enabledExtensionModulePaths
        val options = context.extensionOptions
        val enableCanvas2D = optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_CANVAS2D)
        val enableWebGL = optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_WEBGL)
        val enableMedia = optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_MEDIA)
        val enableSvg = optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_SVG)
        val enableWebAudio = optionFlag(options, GenericUiWebViewExtensionSpec.OPTION_ENABLE_WEBAUDIO)

        if (IPC_MODULE_PATH in enabled || WEBVIEW_MODULE_PATH in enabled) {
            add("com.nexustools.genericui.ext.ipc.IpcExtension.registerProviders()")
        }
        if (SVG_MODULE_PATH in enabled || enableSvg) {
            add("com.nexustools.genericui.ext.svg.system.SystemSvgProvider.install()")
        }
        if (ALIEN_MODULE_PATH in enabled || WEBVIEW_MODULE_PATH in enabled) {
            add("com.nexustools.genericui.ext.alien.system.SystemAlienBootstrap.install()")
        }
        if (SVG_ALIEN_MODULE_PATH in enabled || enableSvg) {
            add("com.nexustools.genericui.ext.svg.alien.SvgAlienExtension.register()")
        }
        if (CANVAS2D_ALIEN_MODULE_PATH in enabled || enableCanvas2D || enableWebGL) {
            add("com.nexustools.genericui.ext.canvas2d.alien.Canvas2DAlienExtension.register()")
        }
        if (CANVAS3D_ALIEN_MODULE_PATH in enabled || enableWebGL) {
            add("com.nexustools.genericui.ext.canvas3d.alien.Canvas3DAlienExtension.register()")
        }
        if (MEDIA_ALIEN_MODULE_PATH in enabled || enableMedia || enableWebAudio) {
            add("com.nexustools.genericui.ext.media.alien.MediaAlienExtension.register()")
        }

        addAll(
            buildDesktopRuntimePropertyCalls(
                backendScope = GenericUiVariantScope.DESKTOP_COMMON,
                genPkg = genPkg,
                workerBaseName = workerBaseName,
                options = options
            )
        )
    }

    private fun buildNativeDesktopWorkerMainSource(
        genPkg: String,
        initStatements: List<String>
    ): String = buildString {
        appendLine("package $genPkg")
        appendLine()
        appendLine("fun mainWebViewWorker(args: Array<String>) {")
        for (stmt in initStatements) {
            appendLine("    $stmt")
        }
        appendLine("    com.nexustools.genericui.ext.webview.desktop.process.WebViewWorkerMain.main(args)")
        appendLine("}")
    }

    private fun buildSwingWorkerMainSource(
        genPkg: String,
        initStatements: List<String>
    ): String = buildString {
        appendLine("package $genPkg")
        appendLine()
        appendLine("object GeneratedSwingWebViewWorkerMain {")
        appendLine("    @JvmStatic")
        appendLine("    fun main(args: Array<String>) {")
        for (stmt in initStatements) {
            appendLine("        $stmt")
        }
        appendLine("        com.nexustools.genericui.ext.webview.desktop.process.WebViewWorkerMain.main(args)")
        appendLine("    }")
        appendLine("}")
    }

    private fun resolveEnabledDesktopScopes(context: ExtensionBuildContext): List<GenericUiVariantScope> {
        if (ServiceWorkerExtensionContributor.OPTION_ENABLED_BACKENDS in context.extensionOptions) {
            val raw = context.extensionOptions[ServiceWorkerExtensionContributor.OPTION_ENABLED_BACKENDS]?.trim().orEmpty()
            if (raw.isEmpty()) return emptyList()
            val parsed = raw.split(',').mapNotNull { GenericUiVariantScope.fromNameOrNull(it) }.toSet()
            return ALL_DESKTOP_SCOPES.filter { it in parsed }
        }
        return ALL_DESKTOP_SCOPES
    }

    private fun isDesktopScope(scope: GenericUiVariantScope): Boolean = when (scope) {
        GenericUiVariantScope.WINDOWS,
        GenericUiVariantScope.MACOS,
        GenericUiVariantScope.GTK,
        GenericUiVariantScope.KDE,
        GenericUiVariantScope.SWING,
        GenericUiVariantScope.LINUX_COMMON,
        GenericUiVariantScope.NATIVE_DESKTOP_COMMON,
        GenericUiVariantScope.DESKTOP_COMMON -> true
        else -> false
    }

    private fun optionFlag(options: Map<String, String>, key: String): Boolean =
        options[key]?.toBooleanStrictOrNull() ?: true

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
