package com.nexustools.genericui.plugin.spi

import com.nexustools.genericui.plugin.GenericUiModuleResourceManifestParser
import com.nexustools.genericui.plugin.GenericUiVariantScope
import java.io.File
import java.net.URLDecoder
import java.util.ServiceLoader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.jar.JarFile

/**
 * Discovers, caches, and queries [GenericUiExtensionContributor] implementations across:
 * 1. Built-in core-safe SPI contributors ([PipExtensionContributor])
 * 2. Java `ServiceLoader<GenericUiExtensionContributor>` entries
 * 3. Declarative `META-INF/genericui/extensions/<name>.json` descriptors on the classpath
 *    or inside a project's `META-INF/genericui/extensions/` directories
 * 4. Programmatically registered contributors (for DSL plugins and tests)
 */
object GenericUiExtensionContributorRegistry {

    private val customContributors = CopyOnWriteArrayList<GenericUiExtensionContributor>()

    @Volatile
    private var cachedClasspathContributors: List<GenericUiExtensionContributor>? = null

    fun register(contributor: GenericUiExtensionContributor) {
        if (customContributors.none {
                it.extensionModulePath == contributor.extensionModulePath &&
                    it::class.java == contributor::class.java
            }
        ) {
            customContributors.add(contributor)
        }
    }

    fun unregister(contributor: GenericUiExtensionContributor) {
        customContributors.remove(contributor)
    }

    fun clearCustomContributors() {
        customContributors.clear()
    }

    fun allContributors(
        projectRootDir: File? = null,
        additionalClasspathDirs: Collection<File> = emptyList()
    ): List<GenericUiExtensionContributor> {
        val classLoader = GenericUiExtensionContributorRegistry::class.java.classLoader
        val discovered = linkedMapOf<String, GenericUiExtensionContributor>()

        fun addContributor(c: GenericUiExtensionContributor) {
            val key = "${c.extensionModulePath}#${c::class.java.name}"
            discovered.putIfAbsent(key, c)
        }

        addContributor(PipExtensionContributor)
        addContributor(SchemaExtensionContributor)
        addContributor(ServiceWorkerExtensionContributor)

        val loaded = cachedClasspathContributors ?: loadClasspathContributors(classLoader).also {
            cachedClasspathContributors = it
        }
        loaded.forEach(::addContributor)

        if (projectRootDir != null) {
            val extJsonDir = File(projectRootDir, "META-INF/genericui/extensions")
            if (extJsonDir.isDirectory) {
                loadJsonContributorsFromDirectory(extJsonDir).forEach(::addContributor)
            }
            val srcResExtDir = File(projectRootDir, "src/commonMain/resources/META-INF/genericui/extensions")
            if (srcResExtDir.isDirectory) {
                loadJsonContributorsFromDirectory(srcResExtDir).forEach(::addContributor)
            }
        }
        for (dir in additionalClasspathDirs) {
            val sub = File(dir, "META-INF/genericui/extensions")
            if (sub.isDirectory) {
                loadJsonContributorsFromDirectory(sub).forEach(::addContributor)
            } else if (dir.isDirectory && dir.name == "extensions") {
                loadJsonContributorsFromDirectory(dir).forEach(::addContributor)
            }
        }

        customContributors.forEach(::addContributor)
        return discovered.values.toList()
    }

    fun activeContributors(
        enabledExtensionModulePaths: Set<String>,
        projectRootDir: File? = null,
        additionalClasspathDirs: Collection<File> = emptyList()
    ): List<GenericUiExtensionContributor> {
        if (enabledExtensionModulePaths.isEmpty()) return emptyList()
        val normalized = enabledExtensionModulePaths.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return allContributors(projectRootDir, additionalClasspathDirs)
            .filter { it.extensionModulePath.trim() in normalized }
    }

    fun allAnnotationScanSpecs(
        enabledExtensionModulePaths: Set<String>,
        projectRootDir: File? = null
    ): List<ExtensionAnnotationScanSpec> =
        activeContributors(enabledExtensionModulePaths, projectRootDir)
            .flatMap { it.annotationScanSpecs }

    fun collectProviderInstallerCalls(
        backendScope: GenericUiVariantScope,
        context: ExtensionBuildContext
    ): List<String> =
        activeContributors(context.enabledExtensionModulePaths + context.customExtensionModulePaths, context.projectRootDir)
            .flatMap { it.providerInstallerCalls(backendScope, context) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun collectAndroidContributions(
        context: ExtensionBuildContext,
        fragment: com.nexustools.genericui.plugin.GenericUiDiscoveredFragment?
    ): List<AndroidExtensionContribution> =
        activeContributors(context.enabledExtensionModulePaths + context.customExtensionModulePaths, context.projectRootDir)
            .mapNotNull { it.contributeAndroid(context, fragment) }

    fun collectDesktopContributions(context: ExtensionBuildContext): List<DesktopExtensionContribution> =
        activeContributors(context.enabledExtensionModulePaths + context.customExtensionModulePaths, context.projectRootDir)
            .mapNotNull { it.contributeDesktop(context) }

    fun collectAppleMobileContributions(context: ExtensionBuildContext): List<AppleMobileExtensionContribution> =
        activeContributors(context.enabledExtensionModulePaths + context.customExtensionModulePaths, context.projectRootDir)
            .mapNotNull { it.contributeAppleMobile(context) }

    fun collectWebServiceWorkerContributions(context: ExtensionBuildContext): List<WebServiceWorkerContribution> =
        activeContributors(context.enabledExtensionModulePaths + context.customExtensionModulePaths, context.projectRootDir)
            .mapNotNull { it.contributeWebServiceWorker(context) }

    fun collectThemeScssContributions(context: ExtensionBuildContext): List<Pair<String, String>> =
        activeContributors(context.enabledExtensionModulePaths + context.customExtensionModulePaths, context.projectRootDir)
            .mapNotNull { contributor ->
                val raw = contributor.contributeThemeScss(context)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                contributor.extensionModulePath to raw
            }

    fun validateActiveContributors(
        context: ExtensionBuildContext,
        symbols: List<GenericUiDiscoveredSymbol> = context.discoveredSymbols
    ) {
        for (contributor in activeContributors(context.enabledExtensionModulePaths + context.customExtensionModulePaths, context.projectRootDir)) {
            contributor.validate(context, symbols)
        }
    }

    private fun loadClasspathContributors(classLoader: ClassLoader?): List<GenericUiExtensionContributor> {
        val result = mutableListOf<GenericUiExtensionContributor>()
        runCatching {
            val loader = ServiceLoader.load(GenericUiExtensionContributor::class.java, classLoader)
            for (item in loader) {
                result.add(item)
            }
        }
        runCatching {
            val urls = classLoader?.getResources("META-INF/genericui/extensions") ?: return@runCatching
            while (urls.hasMoreElements()) {
                val url = urls.nextElement()
                when (url.protocol) {
                    "file" -> {
                        val dir = File(URLDecoder.decode(url.path, "UTF-8"))
                        if (dir.isDirectory) {
                            result.addAll(loadJsonContributorsFromDirectory(dir))
                        }
                    }
                    "jar" -> {
                        val rawPath = url.path.substringAfter("file:").substringBefore("!")
                        val jarFile = File(URLDecoder.decode(rawPath, "UTF-8"))
                        if (jarFile.isFile) {
                            JarFile(jarFile).use { jar ->
                                val entries = jar.entries()
                                while (entries.hasMoreElements()) {
                                    val e = entries.nextElement()
                                    if (!e.isDirectory &&
                                        e.name.startsWith("META-INF/genericui/extensions/") &&
                                        e.name.endsWith(".json", ignoreCase = true)
                                    ) {
                                        val json = jar.getInputStream(e).bufferedReader(Charsets.UTF_8).readText()
                                        parseJsonContributor(json)?.let { result.add(it) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    private fun loadJsonContributorsFromDirectory(dir: File): List<GenericUiExtensionContributor> =
        dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.mapNotNull { file -> runCatching { parseJsonContributor(file.readText(Charsets.UTF_8)) }.getOrNull() }
            .orEmpty()

    internal fun parseJsonContributor(jsonText: String): GenericUiExtensionContributor? {
        val parsed = GenericUiModuleResourceManifestParser.parseJsonObject(jsonText)
        val modulePath = parsed.strings["modulePath"]
            ?: parsed.strings["extensionModulePath"]
            ?: return null
        val dslPath = parsed.strings["dslModulePath"] ?: "$modulePath:dsl"
        val scanSimple = parsed.stringArrays["simpleAnnotationNames"].orEmpty().toSet()
        val scanQualified = parsed.stringArrays["qualifiedAnnotationNames"].orEmpty().toSet()
        val providerInstallers = parsed.stringArrays["providerInstallers"].orEmpty()
        val permissions = parsed.stringArrays["androidPermissions"].orEmpty()
        val bgModes = parsed.stringArrays["appleBackgroundModes"].orEmpty()
        val enableSw = parsed.scalars["webServiceWorkerEnabled"]?.toBooleanStrictOrNull()
            ?: parsed.scalars["enableWebServiceWorker"]?.toBooleanStrictOrNull()
            ?: false
        val swPluginUrls = parsed.stringArrays["webServiceWorkerPlugins"].orEmpty()
        val swWasmBases = parsed.stringArrays["webServiceWorkerBinaries"].orEmpty()

        return JsonManifestExtensionContributor(
            extensionModulePath = modulePath,
            dslModulePath = dslPath,
            annotationScanSpecs = if (scanSimple.isNotEmpty() || scanQualified.isNotEmpty()) {
                listOf(
                    ExtensionAnnotationScanSpec(
                        extensionModulePath = modulePath,
                        simpleAnnotationNames = scanSimple,
                        qualifiedAnnotationNames = scanQualified
                    )
                )
            } else {
                emptyList()
            },
            providerInstallers = providerInstallers,
            androidPermissions = permissions,
            appleBackgroundModes = bgModes,
            enableWebServiceWorker = enableSw,
            webSwPluginPaths = swPluginUrls,
            webSwBinaryBaseNames = swWasmBases
        )
    }

    private class JsonManifestExtensionContributor(
        override val extensionModulePath: String,
        override val dslModulePath: String?,
        override val annotationScanSpecs: List<ExtensionAnnotationScanSpec>,
        private val providerInstallers: List<String>,
        private val androidPermissions: List<String>,
        private val appleBackgroundModes: List<String>,
        private val enableWebServiceWorker: Boolean,
        private val webSwPluginPaths: List<String>,
        private val webSwBinaryBaseNames: List<String>
    ) : GenericUiExtensionContributor {
        override fun providerInstallerCalls(
            backendScope: GenericUiVariantScope,
            context: ExtensionBuildContext
        ): List<String> = providerInstallers

        override fun contributeAndroid(
            context: ExtensionBuildContext,
            fragment: com.nexustools.genericui.plugin.GenericUiDiscoveredFragment?
        ): AndroidExtensionContribution? {
            if (androidPermissions.isEmpty()) return null
            return AndroidExtensionContribution(
                manifestContribution = AndroidManifestContribution(permissions = androidPermissions)
            )
        }

        override fun contributeAppleMobile(context: ExtensionBuildContext): AppleMobileExtensionContribution? {
            if (appleBackgroundModes.isEmpty()) return null
            return AppleMobileExtensionContribution(backgroundModes = appleBackgroundModes)
        }

        override fun contributeWebServiceWorker(context: ExtensionBuildContext): WebServiceWorkerContribution? {
            if (!enableWebServiceWorker && webSwPluginPaths.isEmpty() && webSwBinaryBaseNames.isEmpty()) {
                return null
            }
            return WebServiceWorkerContribution(
                enableServiceWorker = enableWebServiceWorker,
                pluginScripts = webSwPluginPaths.map { WebSwPluginScript(relativeUrlPath = it) },
                workerBinaries = webSwBinaryBaseNames.map { WebWorkerBinarySpec(binaryBaseName = it) }
            )
        }
    }
}
