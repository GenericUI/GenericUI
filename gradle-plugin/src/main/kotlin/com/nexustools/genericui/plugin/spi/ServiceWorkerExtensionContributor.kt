package com.nexustools.genericui.plugin.spi

import com.nexustools.genericui.plugin.GenericUiAndroidIntentFilterSpec
import com.nexustools.genericui.plugin.GenericUiDiscoveredFragment
import com.nexustools.genericui.plugin.GenericUiVariantScope
import org.gradle.api.GradleException
import java.io.File

/**
 * First-party SPI contributor for `:extensions:system:serviceworker` (`@MainServiceWorker`,
 * `@ServiceWorkerMessage`, Desktop background secondary executable & Swing main, Android foreground
 * service & optional boot receiver, and Web Service Worker binary wiring).
 *
 * Strictly enforces:
 * - Zero-Default Footprint: emits ZERO Android services/receivers/permissions, ZERO desktop
 *   secondary binaries/autostart, and ZERO Web worker scripts when no `@MainServiceWorker`
 *   applies to the target.
 * - Compile-time validation failing with [GradleException] when >1 `@MainServiceWorker` ties
 *   for any enabled backend, doesn't extend `ServiceWorker`, or lacks a zero-arg/all-default constructor.
 * - Honest iOS/iPadOS behavior: never instantiates `@MainServiceWorker` on iOS/iPadOS.
 */
object ServiceWorkerExtensionContributor : GenericUiExtensionContributor {

    const val SERVICE_WORKER_MODULE_PATH: String = ":extensions:system:serviceworker"
    const val SERVICE_WORKER_DSL_MODULE_PATH: String = ":extensions:system:serviceworker:dsl"
    const val OPTION_AUTOSTART: String = "serviceWorker.autostart"
    const val OPTION_TRAY_ICON: String = "serviceWorker.trayIcon"
    const val OPTION_OPEN_WINDOW_ON_LAUNCH: String = "serviceWorker.openWindowOnLaunch"
    const val OPTION_ANDROID_FGS_TYPE: String = "serviceWorker.androidForegroundServiceType"
    const val OPTION_ANDROID_SPECIAL_USE_SUBTYPE: String = "serviceWorker.androidSpecialUseFgsSubtype"
    const val OPTION_ENABLED_BACKENDS: String = "genericui.enabledBackends"
    const val OPTION_REGISTRAR_PACKAGE: String = "serviceWorkerRegistrarPackage"
    const val DEFAULT_REGISTRAR_PACKAGE: String = "com.nexustools.genericui.generated"

    private val DESKTOP_SCOPES = listOf(
        GenericUiVariantScope.WINDOWS,
        GenericUiVariantScope.MACOS,
        GenericUiVariantScope.GTK,
        GenericUiVariantScope.QT,
        GenericUiVariantScope.SWING
    )

    override val extensionModulePath: String = SERVICE_WORKER_MODULE_PATH

    override val dslModulePath: String = SERVICE_WORKER_DSL_MODULE_PATH

    override val annotationScanSpecs: List<ExtensionAnnotationScanSpec> = listOf(
        ExtensionAnnotationScanSpec(
            extensionModulePath = SERVICE_WORKER_MODULE_PATH,
            simpleAnnotationNames = setOf("MainServiceWorker"),
            qualifiedAnnotationNames = setOf("com.nexustools.genericui.ext.serviceworker.MainServiceWorker"),
            targetKinds = setOf(ExtensionAnnotationScanSpec.SymbolTargetKind.CLASS)
        ),
        ExtensionAnnotationScanSpec(
            extensionModulePath = SERVICE_WORKER_MODULE_PATH,
            simpleAnnotationNames = setOf("ServiceWorkerMessage"),
            qualifiedAnnotationNames = setOf("com.nexustools.genericui.ext.serviceworker.ServiceWorkerMessage"),
            targetKinds = setOf(ExtensionAnnotationScanSpec.SymbolTargetKind.CLASS)
        )
    )

    fun resolveRegistrarPackage(context: ExtensionBuildContext): String {
        val explicit = context.extensionOptions[OPTION_REGISTRAR_PACKAGE]?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) return explicit

        val genPkg = context.generatedPackageName.trim()
        if (genPkg.isNotEmpty()) return genPkg

        val mainFragPkg = context.discoveredFragments.firstOrNull {
            it.isMainFragment && it.packageName.isNotBlank()
        }?.packageName?.trim()
        if (!mainFragPkg.isNullOrEmpty()) return mainFragPkg

        val symPkg = context.discoveredSymbols.firstOrNull {
            it.extensionModulePath == SERVICE_WORKER_MODULE_PATH && it.packageName.isNotBlank()
        }?.packageName?.trim()
        if (!symPkg.isNullOrEmpty()) return symPkg

        val appIdPkg = context.appId.trim().takeIf { it.isNotEmpty() }
        if (appIdPkg != null) return appIdPkg

        return DEFAULT_REGISTRAR_PACKAGE
    }

    fun resolveRegistrarPackageFromSettings(
        explicitRegistrarPackage: String?,
        basePackageName: String?
    ): String {
        val explicit = explicitRegistrarPackage?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) return explicit
        return basePackageName?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_REGISTRAR_PACKAGE
    }

    override fun validate(
        context: ExtensionBuildContext,
        symbols: List<GenericUiDiscoveredSymbol>
    ) {
        val mainWorkers = collectMainServiceWorkers(symbols, context.discoveredSymbols)
        if (mainWorkers.isEmpty()) return

        val inheritanceIndex = buildInheritanceIndex(context.projectRootDir, mainWorkers)
        for (sym in mainWorkers) {
            if (!sym.hasNoArgConstructor) {
                throw GradleException(
                    "@MainServiceWorker class '${sym.qualifiedName}' must declare a zero-arg constructor " +
                        "(or default values for all primary constructor parameters) so GenericUI can instantiate it."
                )
            }
            verifyExtendsServiceWorker(sym, inheritanceIndex)
        }

        val enabledBackends = resolveEnabledBackends(context)
        for (backend in enabledBackends) {
            resolveMainServiceWorkerForBackend(mainWorkers, backend)
        }
    }

    override fun providerInstallerCalls(
        backendScope: GenericUiVariantScope,
        context: ExtensionBuildContext
    ): List<String> {
        val workers = collectMainServiceWorkers(context.discoveredSymbols, emptyList())
        val worker = resolveMainServiceWorkerForBackend(workers, backendScope) ?: return emptyList()
        val registrarPkg = resolveRegistrarPackage(context)
        val genPkg = context.generatedPackageName.trim().ifEmpty { registrarPkg }
        val optionsExpr = buildServiceWorkerOptionsExpr(
            context = context,
            hasWorker = backendScope != GenericUiVariantScope.IOS && backendScope != GenericUiVariantScope.IPADOS,
            swingWorkerMainClass = if (backendScope == GenericUiVariantScope.SWING) {
                "$genPkg.GeneratedSwingServiceWorkerMain"
            } else {
                ""
            }
        )
        return buildList {
            add("$registrarPkg.GeneratedServiceWorkerCodecRegistrar.registerAll()")
            if (backendScope == GenericUiVariantScope.IOS || backendScope == GenericUiVariantScope.IPADOS) {
                // Honest iOS/iPadOS behavior: never register or instantiate @MainServiceWorker.
                return@buildList
            }
            add("com.nexustools.genericui.ext.serviceworker.ServiceWorkerExtension.install($optionsExpr)")
            add("com.nexustools.genericui.ext.serviceworker.ServiceWorkerExtension.registerWorkerFactory { ${worker.qualifiedName}() }")
        }
    }

    override fun generatedLauncherSources(
        backendScope: GenericUiVariantScope,
        context: ExtensionBuildContext
    ): List<ExtensionGeneratedSource> = emptyList()

    override fun contributeAndroid(
        context: ExtensionBuildContext,
        fragment: GenericUiDiscoveredFragment?
    ): AndroidExtensionContribution? {
        if (fragment != null) return null
        val workers = collectMainServiceWorkers(context.discoveredSymbols, emptyList())
        validateWorkerListSingle(workers, GenericUiVariantScope.ANDROID, context.projectRootDir)
        val worker = resolveMainServiceWorkerForBackend(workers, GenericUiVariantScope.ANDROID) ?: return null

        val autostart = context.extensionOptions[OPTION_AUTOSTART]?.toBooleanStrictOrNull() ?: false
        val fgsType = context.extensionOptions[OPTION_ANDROID_FGS_TYPE]?.trim()?.takeIf { it.isNotEmpty() }
            ?: "specialUse"
        val specialUseSubtype = context.extensionOptions[OPTION_ANDROID_SPECIAL_USE_SUBTYPE]?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "GenericUI background ServiceWorker execution"

        val manifestPkg = context.appId.trim()
            .ifEmpty { context.generatedPackageName.trim().removeSuffix(".generated") }
            .ifEmpty { DEFAULT_REGISTRAR_PACKAGE }
        val pkgDir = manifestPkg.replace('.', '/')

        val permissions = buildList {
            add("android.permission.FOREGROUND_SERVICE")
            add("android.permission.POST_NOTIFICATIONS")
            for (token in parseFgsTokens(fgsType)) {
                mapFgsPermission(token)?.let { add(it) }
            }
            if (autostart) {
                add("android.permission.RECEIVE_BOOT_COMPLETED")
            }
        }.distinct()

        val fgsTokens = parseFgsTokens(fgsType)
        val serviceProperties = if ("specialUse" in fgsTokens) {
            mapOf("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" to specialUseSubtype)
        } else {
            emptyMap()
        }

        val serviceContribution = AndroidServiceContribution(
            className = ".GeneratedAndroidServiceWorkerService",
            exported = false,
            enabled = true,
            foregroundServiceType = fgsType,
            properties = serviceProperties
        )

        val receivers = if (autostart) {
            listOf(
                AndroidReceiverContribution(
                    className = ".GeneratedAndroidServiceWorkerBootReceiver",
                    exported = false,
                    enabled = true,
                    intentFilters = listOf(
                        GenericUiAndroidIntentFilterSpec().apply {
                            action("android.intent.action.BOOT_COMPLETED")
                            action("android.intent.action.MY_PACKAGE_REPLACED")
                        }
                    )
                )
            )
        } else {
            emptyList()
        }

        val registrarPkg = resolveRegistrarPackage(context)
        val optionsExpr = buildServiceWorkerOptionsExpr(
            context = context,
            hasWorker = true,
            swingWorkerMainClass = ""
        )
        val generatedFiles = mutableListOf<ExtensionGeneratedSource>()
        generatedFiles.add(
            ExtensionGeneratedSource(
                relativeFilePath = "$pkgDir/GeneratedAndroidServiceWorkerService.kt",
                sourceCode = buildAndroidServiceSource(
                    manifestPkg = manifestPkg,
                    registrarPkg = registrarPkg,
                    workerQualifiedName = worker.qualifiedName,
                    optionsExpr = optionsExpr
                )
            )
        )
        if (autostart) {
            generatedFiles.add(
                ExtensionGeneratedSource(
                    relativeFilePath = "$pkgDir/GeneratedAndroidServiceWorkerBootReceiver.kt",
                    sourceCode = buildAndroidBootReceiverSource(manifestPkg)
                )
            )
        }

        return AndroidExtensionContribution(
            manifestContribution = AndroidManifestContribution(
                permissions = permissions,
                services = listOf(serviceContribution),
                receivers = receivers
            ),
            generatedSources = generatedFiles,
            applicationOnCreateStatements = listOf(
                "com.nexustools.genericui.ext.serviceworker.AndroidServiceWorkerController.registerServiceClass($manifestPkg.GeneratedAndroidServiceWorkerService::class.java)",
                "com.nexustools.genericui.ext.serviceworker.AndroidServiceWorkerController.installLifecycleObserverIfNeeded()"
            )
        )
    }

    override fun contributeDesktop(context: ExtensionBuildContext): DesktopExtensionContribution? {
        val workers = collectMainServiceWorkers(context.discoveredSymbols, emptyList())
        if (workers.isEmpty()) return null

        val enabledDesktop = resolveEnabledBackends(context).filter { it in DESKTOP_SCOPES }
        val scopesToCheck = enabledDesktop.ifEmpty { DESKTOP_SCOPES }
        val resolvedByScope = LinkedHashMap<GenericUiVariantScope, GenericUiDiscoveredSymbol>()
        for (scope in scopesToCheck) {
            val w = resolveMainServiceWorkerForBackend(workers, scope)
            if (w != null) {
                verifyExtendsServiceWorker(w, buildInheritanceIndex(context.projectRootDir, listOf(w)))
                if (!w.hasNoArgConstructor) {
                    throw GradleException(
                        "@MainServiceWorker class '${w.qualifiedName}' must declare a zero-arg constructor."
                    )
                }
                resolvedByScope[scope] = w
            }
        }
        if (resolvedByScope.isEmpty()) return null

        val genPkg = context.generatedPackageName.trim().ifEmpty { resolveRegistrarPackage(context) }
        val registrarPkg = resolveRegistrarPackage(context)
        val pkgDir = genPkg.replace('.', '/')
        val autostart = context.extensionOptions[OPTION_AUTOSTART]?.toBooleanStrictOrNull() ?: false

        val secondaryExes = mutableListOf<SecondaryDesktopExecutableContribution>()
        val generatedSources = mutableListOf<ExtensionGeneratedSource>()

        val nativeDesktopScopes = listOf(
            GenericUiVariantScope.WINDOWS,
            GenericUiVariantScope.MACOS,
            GenericUiVariantScope.GTK,
            GenericUiVariantScope.QT
        )
        val hasAnyNativeDesktopWorker = nativeDesktopScopes.any { it in resolvedByScope }
        if (hasAnyNativeDesktopWorker) {
            secondaryExes.add(
                SecondaryDesktopExecutableContribution(
                    binaryBaseName = "serviceworker",
                    entryPointFqn = "$genPkg.mainServiceWorker",
                    windowsSubsystem = "windows"
                )
            )
            for (scope in nativeDesktopScopes) {
                val worker = resolvedByScope[scope] ?: continue
                generatedSources.add(
                    ExtensionGeneratedSource(
                        relativeFilePath = "$pkgDir/Generated${scope.id.replaceFirstChar { it.uppercaseChar() }}ServiceWorkerMain.kt",
                        sourceCode = buildNativeDesktopServiceWorkerMainSource(
                            genPkg = genPkg,
                            registrarPkg = registrarPkg,
                            scope = scope,
                            workerQualifiedName = worker.qualifiedName,
                            optionsExpr = buildServiceWorkerOptionsExpr(context, hasWorker = true, swingWorkerMainClass = ""),
                            mainBaseName = context.baseName
                        ),
                        targetScope = scope
                    )
                )
            }
        }

        val swingWorker = resolvedByScope[GenericUiVariantScope.SWING]
        if (swingWorker != null) {
            generatedSources.add(
                ExtensionGeneratedSource(
                    relativeFilePath = "$pkgDir/GeneratedSwingServiceWorkerMain.kt",
                    sourceCode = buildSwingServiceWorkerMainSource(
                        genPkg = genPkg,
                        registrarPkg = registrarPkg,
                        workerQualifiedName = swingWorker.qualifiedName,
                        optionsExpr = buildServiceWorkerOptionsExpr(
                            context = context,
                            hasWorker = true,
                            swingWorkerMainClass = "$genPkg.GeneratedSwingServiceWorkerMain"
                        ),
                        mainBaseName = context.baseName
                    ),
                    targetScope = GenericUiVariantScope.SWING
                )
            )
        }

        val autostartEntries = if (autostart) {
            listOf(
                DesktopAutostartContribution(
                    identifier = "${context.appId}.serviceworker",
                    executableName = "serviceworker",
                    displayName = context.appName,
                    runAtStartup = true
                )
            )
        } else {
            emptyList()
        }

        return DesktopExtensionContribution(
            secondaryExecutables = secondaryExes,
            autostartEntries = autostartEntries,
            generatedSources = generatedSources
        )
    }

    override fun contributeAppleMobile(context: ExtensionBuildContext): AppleMobileExtensionContribution? = null

    override fun contributeWebServiceWorker(context: ExtensionBuildContext): WebServiceWorkerContribution? {
        val workers = collectMainServiceWorkers(context.discoveredSymbols, emptyList())
        val webWorker = resolveMainServiceWorkerForBackend(workers, GenericUiVariantScope.WEB) ?: return null
        verifyExtendsServiceWorker(webWorker, buildInheritanceIndex(context.projectRootDir, listOf(webWorker)))
        if (!webWorker.hasNoArgConstructor) {
            throw GradleException(
                "@MainServiceWorker class '${webWorker.qualifiedName}' must declare a zero-arg constructor."
            )
        }
        val genPkg = context.generatedPackageName.trim().ifEmpty { resolveRegistrarPackage(context) }
        val base = context.baseName.trim().ifEmpty { "app" }
        return WebServiceWorkerContribution(
            enableServiceWorker = true,
            workerBinaries = listOf(
                WebWorkerBinarySpec(
                    binaryBaseName = base,
                    entryPointFqn = "$genPkg.mainWebServiceWorker",
                    serviceWorker = true
                )
            )
        )
    }

    fun hasResolvedWorkerForBackend(
        context: ExtensionBuildContext,
        backendScope: GenericUiVariantScope
    ): Boolean = resolveWorkerQualifiedNameForBackend(context, backendScope) != null

    fun resolveWorkerQualifiedNameForBackend(
        context: ExtensionBuildContext,
        backendScope: GenericUiVariantScope
    ): String? {
        val workers = collectMainServiceWorkers(context.discoveredSymbols, emptyList())
        return resolveMainServiceWorkerForBackend(workers, backendScope)?.qualifiedName
    }

    fun buildOptionsExpressionForBackend(
        context: ExtensionBuildContext,
        backendScope: GenericUiVariantScope
    ): String {
        val registrarPkg = resolveRegistrarPackage(context)
        val genPkg = context.generatedPackageName.trim().ifEmpty { registrarPkg }
        val hasWorker = backendScope != GenericUiVariantScope.IOS &&
            backendScope != GenericUiVariantScope.IPADOS &&
            hasResolvedWorkerForBackend(context, backendScope)
        return buildServiceWorkerOptionsExpr(
            context = context,
            hasWorker = hasWorker,
            swingWorkerMainClass = if (backendScope == GenericUiVariantScope.SWING && hasWorker) {
                "$genPkg.GeneratedSwingServiceWorkerMain"
            } else {
                ""
            }
        )
    }

    private fun validateWorkerListSingle(
        workers: List<GenericUiDiscoveredSymbol>,
        backend: GenericUiVariantScope,
        projectRootDir: File
    ) {
        if (workers.isEmpty()) return
        val index = buildInheritanceIndex(projectRootDir, workers)
        for (w in workers) {
            if (!w.hasNoArgConstructor) {
                throw GradleException(
                    "@MainServiceWorker class '${w.qualifiedName}' must declare a zero-arg constructor."
                )
            }
            verifyExtendsServiceWorker(w, index)
        }
        resolveMainServiceWorkerForBackend(workers, backend)
    }

    private fun collectMainServiceWorkers(
        primary: List<GenericUiDiscoveredSymbol>,
        secondary: List<GenericUiDiscoveredSymbol>
    ): List<GenericUiDiscoveredSymbol> =
        (primary + secondary)
            .filter {
                it.annotationName == "MainServiceWorker" &&
                    (it.extensionModulePath == SERVICE_WORKER_MODULE_PATH ||
                        it.qualifiedAnnotationName == "com.nexustools.genericui.ext.serviceworker.MainServiceWorker")
            }
            .distinctBy { it.qualifiedName }

    private fun resolveEnabledBackends(context: ExtensionBuildContext): List<GenericUiVariantScope> {
        val raw = context.extensionOptions[OPTION_ENABLED_BACKENDS]?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            val parsed = raw.split(',').mapNotNull { GenericUiVariantScope.fromNameOrNull(it) }.distinct()
            if (parsed.isNotEmpty()) return parsed
        }
        return GenericUiVariantScope.ALL_BACKENDS
    }

    private fun resolveMainServiceWorkerForBackend(
        workers: List<GenericUiDiscoveredSymbol>,
        backend: GenericUiVariantScope
    ): GenericUiDiscoveredSymbol? {
        data class RankedWorker(val symbol: GenericUiDiscoveredSymbol, val rank: Int)
        val candidates = workers.mapNotNull { sym ->
            val scopes = sym.targetScopes.ifEmpty { listOf(GenericUiVariantScope.COMMON) }
            val matching = scopes.filter { it.matchesBackend(backend) }
            val maxRank = matching.maxOfOrNull { it.specificityRank } ?: return@mapNotNull null
            RankedWorker(sym, maxRank)
        }
        if (candidates.isEmpty()) return null
        val highestRank = candidates.maxOf { it.rank }
        val winners = candidates.filter { it.rank == highestRank }
        if (winners.size > 1) {
            val details = winners.joinToString("; ") { w ->
                val scopeStr = w.symbol.targetScopes.ifEmpty { listOf(GenericUiVariantScope.COMMON) }
                    .joinToString(", ") { it.id }
                "${w.symbol.qualifiedName} (targets = [$scopeStr])"
            }
            throw GradleException(
                "Conflicting @MainServiceWorker declarations apply to target '${backend.id}' " +
                    "at specificity rank $highestRank: $details. At most one @MainServiceWorker class may " +
                    "apply to any enabled backend."
            )
        }
        return winners.single().symbol
    }

    private fun buildInheritanceIndex(
        projectRootDir: File,
        workers: List<GenericUiDiscoveredSymbol>
    ): Map<String, List<String>> {
        val superMap = LinkedHashMap<String, List<String>>()
        val files = linkedSetOf<File>()
        if (projectRootDir.exists() && projectRootDir.isDirectory) {
            val srcDir = File(projectRootDir, "src")
            if (srcDir.exists() && srcDir.isDirectory) {
                srcDir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("kt", ignoreCase = true) }
                    .forEach { files.add(it) }
            }
        }
        for (w in workers) {
            if (w.sourceFilePath.isNotBlank()) {
                val f = File(w.sourceFilePath)
                if (f.isFile) files.add(f)
            }
        }
        val classRegex = Regex("""class\s+([A-Za-z_][A-Za-z0-9_]*)(?:<[^>]*>)?(?:\s*(?:(?:public|internal|private|protected)\s+)?constructor)?(?:\s*\([^)]*\))?\s*(?::\s*([^{]+))?""")
        for (f in files) {
            val text = runCatching { f.readText(Charsets.UTF_8) }.getOrNull() ?: continue
            val pkg = Regex("""(?m)^\s*package\s+([A-Za-z0-9_.]+)""").find(text)?.groupValues?.get(1)?.trim().orEmpty()
            for (m in classRegex.findAll(text)) {
                val name = m.groupValues[1]
                val rawSupers = m.groupValues.getOrNull(2).orEmpty().trim()
                val supers = if (rawSupers.isEmpty()) {
                    emptyList()
                } else {
                    rawSupers.split(',')
                        .map { it.trim().substringBefore('(').substringBefore('<').trim() }
                        .filter { it.isNotEmpty() }
                }
                val qName = if (pkg.isEmpty()) name else "$pkg.$name"
                superMap[qName] = supers
                superMap.putIfAbsent(name, supers)
            }
        }
        return superMap
    }

    private fun verifyExtendsServiceWorker(
        sym: GenericUiDiscoveredSymbol,
        inheritanceIndex: Map<String, List<String>>
    ) {
        val explicitFlag = sym.attributes["extendsServiceWorker"]?.trim()?.lowercase()
        if (explicitFlag == "false") {
            throw GradleException(
                "@MainServiceWorker class '${sym.qualifiedName}' must extend " +
                    "com.nexustools.genericui.ext.serviceworker.ServiceWorker."
            )
        }
        if (explicitFlag == "true") return

        val explicitSupers = sym.attributes["superTypes"]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
        val directSupers = explicitSupers ?: inheritanceIndex[sym.qualifiedName] ?: inheritanceIndex[sym.symbolName]
        if (directSupers == null) {
            if (sym.sourceFilePath.isBlank() && "superTypes" !in sym.attributes) {
                return
            }
            throw GradleException(
                "@MainServiceWorker class '${sym.qualifiedName}' must extend " +
                    "com.nexustools.genericui.ext.serviceworker.ServiceWorker."
            )
        }

        val visited = linkedSetOf<String>()
        val queue = ArrayDeque(directSupers)
        while (queue.isNotEmpty()) {
            val candidate = queue.removeFirst()
            if (candidate == "ServiceWorker" || candidate == "com.nexustools.genericui.ext.serviceworker.ServiceWorker") {
                return
            }
            if (visited.add(candidate)) {
                inheritanceIndex[candidate]?.forEach { queue.addLast(it) }
            }
        }
        throw GradleException(
            "@MainServiceWorker class '${sym.qualifiedName}' must extend " +
                "com.nexustools.genericui.ext.serviceworker.ServiceWorker."
        )
    }

    private fun parseFgsTokens(rawType: String): List<String> =
        rawType.split('|', ',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }

    private fun mapFgsPermission(token: String): String? = when (token) {
        "specialUse" -> "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
        "mediaPlayback" -> "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"
        "dataSync" -> "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
        "microphone" -> "android.permission.FOREGROUND_SERVICE_MICROPHONE"
        "camera" -> "android.permission.FOREGROUND_SERVICE_CAMERA"
        "location" -> "android.permission.FOREGROUND_SERVICE_LOCATION"
        "connectedDevice" -> "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"
        "mediaProjection" -> "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
        "phoneCall" -> "android.permission.FOREGROUND_SERVICE_PHONE_CALL"
        "health" -> "android.permission.FOREGROUND_SERVICE_HEALTH"
        "remoteMessaging" -> "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"
        "systemExempted" -> "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
        else -> null
    }

    private fun buildServiceWorkerOptionsExpr(
        context: ExtensionBuildContext,
        hasWorker: Boolean,
        swingWorkerMainClass: String
    ): String {
        val autostart = context.extensionOptions[OPTION_AUTOSTART]?.toBooleanStrictOrNull() ?: false
        val openWindowOnLaunch = context.extensionOptions[OPTION_OPEN_WINDOW_ON_LAUNCH]?.toBooleanStrictOrNull() ?: true
        val trayIcon = context.extensionOptions[OPTION_TRAY_ICON]?.trim()?.takeIf { it.isNotEmpty() }
        val fgsType = context.extensionOptions[OPTION_ANDROID_FGS_TYPE]?.trim()?.takeIf { it.isNotEmpty() } ?: "specialUse"
        val specialSubtype = context.extensionOptions[OPTION_ANDROID_SPECIAL_USE_SUBTYPE]?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "GenericUI background ServiceWorker execution"
        return "com.nexustools.genericui.ext.serviceworker.ServiceWorkerOptions(" +
            "appId = ${quoteKotlin(context.appId)}, " +
            "appName = ${quoteKotlin(context.appName)}, " +
            "baseName = ${quoteKotlin(context.baseName)}, " +
            "autostart = $autostart, " +
            "trayIcon = ${trayIcon?.let { quoteKotlin(it) } ?: "null"}, " +
            "openWindowOnLaunch = $openWindowOnLaunch, " +
            "androidForegroundServiceType = ${quoteKotlin(fgsType)}, " +
            "androidSpecialUseFgsSubtype = ${quoteKotlin(specialSubtype)}, " +
            "hasWorker = $hasWorker, " +
            "swingWorkerMainClass = ${quoteKotlin(swingWorkerMainClass)})"
    }

    private fun buildAndroidServiceSource(
        manifestPkg: String,
        registrarPkg: String,
        workerQualifiedName: String,
        optionsExpr: String
    ): String = """package $manifestPkg

import android.app.Service
import android.content.Intent
import android.os.IBinder

open class GeneratedAndroidServiceWorkerService : Service() {

    override fun onCreate() {
        super.onCreate()
        $registrarPkg.GeneratedServiceWorkerCodecRegistrar.registerAll()
        com.nexustools.genericui.ext.serviceworker.ServiceWorkerExtension.install($optionsExpr)
        com.nexustools.genericui.ext.serviceworker.ServiceWorkerExtension.registerWorkerFactory { $workerQualifiedName() }
        com.nexustools.genericui.ext.serviceworker.AndroidServiceWorkerController.updateOptions($optionsExpr)
        com.nexustools.genericui.ext.serviceworker.AndroidServiceWorkerController.onServiceCreated(this) { $workerQualifiedName() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        com.nexustools.genericui.ext.serviceworker.AndroidServiceWorkerController.onServiceStartCommand(this, intent, flags, startId)

    override fun onBind(intent: Intent?): IBinder =
        com.nexustools.genericui.ext.serviceworker.AndroidServiceWorkerController.onServiceBind(this, intent)

    override fun onDestroy() {
        com.nexustools.genericui.ext.serviceworker.AndroidServiceWorkerController.onServiceDestroyed(this)
        super.onDestroy()
    }
}
"""

    private fun buildAndroidBootReceiverSource(manifestPkg: String): String = """package $manifestPkg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

open class GeneratedAndroidServiceWorkerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            com.nexustools.genericui.ext.serviceworker.AndroidServiceWorkerController.onBootCompleted(
                context,
                GeneratedAndroidServiceWorkerService::class.java
            )
        }
    }
}
"""

    private fun buildNativeDesktopServiceWorkerMainSource(
        genPkg: String,
        registrarPkg: String,
        scope: GenericUiVariantScope,
        workerQualifiedName: String,
        optionsExpr: String,
        mainBaseName: String
    ): String {
        val (backendInitCode, eventLoopCode, stopLoopCode) = when (scope) {
            GenericUiVariantScope.WINDOWS -> Triple(
                """            com.nexustools.genericui.windows.WindowsApplicationNativeImpl.installWindowsThemeDetector()
            com.nexustools.genericui.windows.WindowsApplicationNativeImpl.interactiveWin32GuiEnabled = true
            com.nexustools.genericui.core.GenericUI.initialize(com.nexustools.genericui.windows.WindowsNativeFactory())""",
                "            com.nexustools.genericui.windows.Win32NativeWindowHost.runMessageLoop()",
                "            com.nexustools.genericui.windows.WindowsHeadlessTrayLoop.requestQuit()"
            )
            GenericUiVariantScope.MACOS -> Triple(
                """            com.nexustools.genericui.macos.MacosApplicationNativeImpl.installMacosThemeDetector()
            com.nexustools.genericui.macos.MacosApplicationNativeImpl.interactiveMacosGuiEnabled = true
            com.nexustools.genericui.core.GenericUI.initialize(com.nexustools.genericui.macos.MacosNativeFactory())
            com.nexustools.genericui.macos.MacosNativeWindowHost.prepareEventLoop()""",
                "            com.nexustools.genericui.macos.MacosNativeWindowHost.runEventLoop()",
                "            com.nexustools.genericui.macos.MacosNativeWindowHost.terminateEventLoop()"
            )
            GenericUiVariantScope.GTK -> Triple(
                """            com.nexustools.genericui.gtk.GtkApplicationNativeImpl.installGtkThemeDetector()
            com.nexustools.genericui.gtk.GtkApplicationNativeImpl.interactiveGtkGuiEnabled = true
            com.nexustools.genericui.core.GenericUI.initialize(com.nexustools.genericui.gtk.GtkNativeFactory())
            com.nexustools.genericui.gtk.GtkNativeWindowHost.prepareEventLoop()""",
                "            com.nexustools.genericui.gtk.GtkNativeWindowHost.runEventLoop()",
                "            com.nexustools.genericui.gtk.GtkNativeWindowHost.terminateEventLoop()"
            )
            GenericUiVariantScope.QT -> Triple(
                """            com.nexustools.genericui.qt.QtApplicationNativeImpl.installQtThemeDetector()
            com.nexustools.genericui.qt.QtApplicationNativeImpl.interactiveQtGuiEnabled = true
            com.nexustools.genericui.core.GenericUI.initialize(com.nexustools.genericui.qt.QtNativeFactory())
            com.nexustools.genericui.qt.QtNativeWindowHost.prepareEventLoop()""",
                "            com.nexustools.genericui.qt.QtNativeWindowHost.runEventLoop()",
                "            com.nexustools.genericui.qt.QtNativeWindowHost.terminateEventLoop()"
            )
            else -> Triple("", "", "")
        }

        return """package $genPkg

fun mainServiceWorker(args: Array<String>) {
    $registrarPkg.GeneratedServiceWorkerCodecRegistrar.registerAll()
    com.nexustools.genericui.ext.serviceworker.ServiceWorkerExtension.configure($optionsExpr)
    com.nexustools.genericui.ext.serviceworker.ServiceWorkerExtension.runDesktopWorkerMain(
        args = args,
        backendInit = {
$backendInitCode
        },
        eventLoopRunner = {
$eventLoopCode
        },
        eventLoopStopper = {
$stopLoopCode
        },
        workerFactory = { $workerQualifiedName() },
        mainBinaryBaseName = ${quoteKotlin(mainBaseName)}
    )
}
"""
    }

    private fun buildSwingServiceWorkerMainSource(
        genPkg: String,
        registrarPkg: String,
        workerQualifiedName: String,
        optionsExpr: String,
        mainBaseName: String
    ): String = """@file:JvmName("GeneratedSwingServiceWorkerMain")

package $genPkg

fun main(args: Array<String>) {
    $registrarPkg.GeneratedServiceWorkerCodecRegistrar.registerAll()
    com.nexustools.genericui.ext.serviceworker.ServiceWorkerExtension.configure($optionsExpr)
    var swingApp: com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl<*>? = null
    com.nexustools.genericui.ext.serviceworker.ServiceWorkerExtension.runDesktopWorkerMain(
        args = args,
        backendInit = {
            com.nexustools.genericui.swing.SwingApplicationImpl.interactiveSwingGuiEnabled = true
            com.nexustools.genericui.swing.SwingApplicationImpl.installSwingThemeDetector()
            val factory = com.nexustools.genericui.swing.SwingNativeFactory()
            com.nexustools.genericui.core.GenericUI.initialize(factory)
            swingApp = factory.createApplication()
        },
        eventLoopRunner = {
            swingApp?.runEventLoop {}
        },
        eventLoopStopper = {
            swingApp?.exitApplication(0)
        },
        workerFactory = { $workerQualifiedName() },
        mainBinaryBaseName = ${quoteKotlin(mainBaseName)}
    )
}
"""

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
