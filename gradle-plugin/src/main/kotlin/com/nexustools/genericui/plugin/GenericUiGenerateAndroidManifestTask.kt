package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Gradle task (`generateGenericUiAndroidManifest`) that generates `AndroidManifest.xml` and companion
 * Android `res/` XMLs from:
 * - Resolved `appId`, `appName`, `appVersion`, and `packageName`
 * - Serialized [GenericUiAndroidManifestModel] ([manifestModelJson])
 * - Discovered Android `@MainAppFragment` and `@AppFragment` classes
 * - Auto-detected `src/androidMain/AndroidManifest.xml` and configured `mergeManifest(...)` overlays
 *   via Google's `ManifestMerger2` ([GenericUiAndroidManifestMergerRunner]).
 */
@DisableCachingByDefault(because = "Generates AndroidManifest.xml and companion Android XML resources")
abstract class GenericUiGenerateAndroidManifestTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val androidEnabled: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val projectRootPath: Property<String>

    @get:Input
    @get:Optional
    abstract val sourceDirectories: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val sourceFiles: ConfigurableFileCollection

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
    abstract val manifestModelJson: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestMergerVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val overlayManifestPaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val enabledExtensionModulePaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val extensionOptions: org.gradle.api.provider.MapProperty<String, String>

    @get:OutputDirectory
    @get:Optional
    abstract val outputDirectory: DirectoryProperty

    @get:OutputFile
    @get:Optional
    abstract val outputManifestFile: RegularFileProperty

    @get:OutputDirectory
    @get:Optional
    abstract val outputResDirectory: DirectoryProperty

    init {
        manifestMergerVersion.convention(GenericUiAndroidManifestMergerRunner.DEFAULT_MERGER_VERSION)
        extensionOptions.convention(emptyMap())
    }

    @TaskAction
    fun generate() {
        val targetFile: File = when {
            outputManifestFile.isPresent -> outputManifestFile.get().asFile
            outputDirectory.isPresent -> File(outputDirectory.get().asFile, "AndroidManifest.xml")
            else -> File(
                project.layout.buildDirectory.asFile.get(),
                "generated/genericui/android-manifest/AndroidManifest.xml"
            )
        }
        val baseOutDir = outputDirectory.orNull?.asFile ?: targetFile.parentFile
        val targetResDir: File = outputResDirectory.orNull?.asFile ?: File(baseOutDir, "res")

        if (androidEnabled.orNull == false) {
            targetFile.parentFile?.mkdirs()
            targetResDir.mkdirs()
            return
        }

        val rootDir = projectRootPath.orNull?.takeIf { it.isNotBlank() }?.let { File(it) } ?: project.projectDir
        val customDirs = sourceDirectories.orNull.orEmpty().map { File(it) }.filter { it.exists() }
        val discovered = if (customDirs.isNotEmpty()) {
            GenericUiFragmentScanner.scanSourceDirectories(customDirs)
        } else {
            GenericUiFragmentScanner.scanProject(rootDir)
        }

        val (winningMain, androidFragments) = GenericUiGenerateAndroidActivitiesTask.resolveAndroidFragments(discovered)

        val resolvedAppId = appId.orNull?.takeIf { it.isNotBlank() } ?: "com.nexustools.genericui.app"
        val resolvedAppName = appName.orNull?.takeIf { it.isNotBlank() } ?: "GenericUI"
        val resolvedAppVersion = appVersion.orNull?.takeIf { it.isNotBlank() } ?: "1.0.0"
        val resolvedPkg = packageName.orNull?.takeIf { it.isNotBlank() } ?: resolvedAppId

        val rawJson = manifestModelJson.orNull?.trim().orEmpty()
        val model = if (rawJson.isNotEmpty()) {
            GenericUiAndroidManifestModelSerializer.fromJson(rawJson)
        } else {
            GenericUiAndroidManifestModel()
        }

        val mergerVersion = manifestMergerVersion.orNull?.trim()?.ifEmpty { null }
            ?: GenericUiAndroidManifestMergerRunner.DEFAULT_MERGER_VERSION
        val enabledExtensions = enabledExtensionModulePaths.orNull.orEmpty()
        val scanSpecs = com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry.allAnnotationScanSpecs(
            enabledExtensionModulePaths = enabledExtensions.toSet(),
            projectRootDir = rootDir
        )
        val discoveredSymbols = GenericUiFragmentScanner.scanProjectSymbols(
            projectDir = rootDir,
            scanSpecs = scanSpecs,
            extraDirectories = customDirs
        )

        val extOpts = extensionOptions.getOrElse(emptyMap())
        val baseOutput = GenericUiAndroidManifestXmlGenerator.generate(
            appId = resolvedAppId,
            appName = resolvedAppName,
            appVersion = resolvedAppVersion,
            model = model,
            fragments = androidFragments,
            winningMainFragment = winningMain,
            defaultPackageName = resolvedPkg,
            enabledExtensionModulePaths = enabledExtensions,
            projectRootDir = rootDir,
            discoveredSymbols = discoveredSymbols,
            extensionOptions = extOpts
        )

        val baseOverlays = collectOverlayManifestFiles(rootDir, model.mergeManifestPaths)
        val finalManifestXml = mergeWithOverlaysIfPresent(
            baseXml = baseOutput.manifestXml,
            overlayFiles = baseOverlays,
            placeholders = baseOutput.resolvedPlaceholders,
            mergerVersion = mergerVersion,
            minSdk = model.minSdk,
            targetSdk = model.targetSdk,
            stagingBaseFile = File(baseOutDir, "intermediates/base-AndroidManifest.xml")
        )

        targetFile.parentFile?.mkdirs()
        targetFile.writeText(finalManifestXml)
        baseOutput.writeResFiles(targetResDir, cleanExisting = true)

        val variantsRootDir = File(baseOutDir, "variants")
        if (model.variantOverrides.isEmpty()) {
            if (variantsRootDir.exists()) {
                variantsRootDir.deleteRecursively()
            }
        } else {
            if (variantsRootDir.exists()) {
                variantsRootDir.deleteRecursively()
            }
            for ((variantName, variantModel) in model.variantOverrides) {
                val variantAppId = resolvedAppId + variantModel.applicationIdSuffix
                val variantAppVersion = resolvedAppVersion + variantModel.versionNameSuffix
                val variantOutput = GenericUiAndroidManifestXmlGenerator.generate(
                    appId = variantAppId,
                    appName = resolvedAppName,
                    appVersion = variantAppVersion,
                    model = variantModel.manifest,
                    fragments = androidFragments,
                    winningMainFragment = winningMain,
                    defaultPackageName = resolvedPkg,
                    enabledExtensionModulePaths = enabledExtensions,
                    projectRootDir = rootDir,
                    discoveredSymbols = discoveredSymbols,
                    extensionOptions = extOpts
                )
                val variantDir = File(variantsRootDir, variantName).apply { mkdirs() }
                val variantOverlays = collectVariantOverlayFiles(
                    rootDir = rootDir,
                    variantName = variantName,
                    configuredPaths = variantModel.manifest.mergeManifestPaths
                )
                val variantManifestXml = mergeWithOverlaysIfPresent(
                    baseXml = variantOutput.manifestXml,
                    overlayFiles = variantOverlays,
                    placeholders = variantOutput.resolvedPlaceholders,
                    mergerVersion = mergerVersion,
                    minSdk = variantModel.manifest.minSdk,
                    targetSdk = variantModel.manifest.targetSdk,
                    stagingBaseFile = File(variantDir, "base-AndroidManifest.xml")
                )
                File(variantDir, "AndroidManifest.xml").writeText(variantManifestXml)
                variantOutput.writeResFiles(File(variantDir, "res"), cleanExisting = true)
            }
        }
    }

    private fun mergeWithOverlaysIfPresent(
        baseXml: String,
        overlayFiles: List<File>,
        placeholders: Map<String, String>,
        mergerVersion: String,
        minSdk: Int,
        targetSdk: Int,
        stagingBaseFile: File
    ): String {
        if (overlayFiles.isEmpty()) {
            return baseXml
        }
        stagingBaseFile.parentFile?.mkdirs()
        stagingBaseFile.writeText(baseXml)
        return try {
            GenericUiAndroidManifestMergerRunner.merge(
                baseManifestFile = stagingBaseFile,
                overlayFiles = overlayFiles,
                placeholders = placeholders,
                mergerVersion = mergerVersion,
                minSdk = minSdk,
                targetSdk = targetSdk,
                project = project,
                logger = logger
            )
        } finally {
            runCatching { stagingBaseFile.delete() }
        }
    }

    private fun collectOverlayManifestFiles(rootDir: File, configuredPaths: List<String>): List<File> {
        val result = LinkedHashMap<String, File>()
        val autoAndroidMain = File(rootDir, "src/androidMain/AndroidManifest.xml")
        if (autoAndroidMain.isFile) {
            result[autoAndroidMain.canonicalPath] = autoAndroidMain
        }
        for (rawPath in configuredPaths + overlayManifestPaths.orNull.orEmpty()) {
            val trimmed = rawPath.trim()
            if (trimmed.isEmpty()) continue
            val candidate = File(trimmed).let { if (it.isAbsolute) it else File(rootDir, trimmed) }
            if (!candidate.isFile) {
                throw GradleException(
                    "Configured Android mergeManifest file does not exist: ${candidate.absolutePath}"
                )
            }
            result[candidate.canonicalPath] = candidate
        }
        return result.values.toList()
    }

    private fun collectVariantOverlayFiles(
        rootDir: File,
        variantName: String,
        configuredPaths: List<String>
    ): List<File> {
        val result = LinkedHashMap<String, File>()
        for (f in collectOverlayManifestFiles(rootDir, configuredPaths)) {
            result[f.canonicalPath] = f
        }
        val capVariant = variantName.replaceFirstChar { it.uppercase() }
        val variantManifest = File(rootDir, "src/android$capVariant/AndroidManifest.xml")
        if (variantManifest.isFile) {
            result[variantManifest.canonicalPath] = variantManifest
        }
        return result.values.toList()
    }

    companion object {
        /**
         * Generates the complete `AndroidManifest.xml` content directly from DSL configuration specs.
         */
        fun generateManifestXml(
            appId: String,
            appName: String,
            manifestConfig: GenericUiAndroidManifestConfig,
            services: List<GenericUiAndroidServiceSpec>,
            fragments: List<GenericUiDiscoveredFragment>,
            winningMainFragment: GenericUiDiscoveredFragment? = fragments.firstOrNull { it.isMainFragment },
            defaultPackageName: String = appId,
            appVersion: String = "1.0.0",
            enabledExtensionModulePaths: List<String> = emptyList()
        ): String {
            val model = GenericUiAndroidManifestModelSerializer.toModel(
                manifestConfig = manifestConfig,
                services = services
            )
            return GenericUiAndroidManifestXmlGenerator.generate(
                appId = appId,
                appName = appName,
                appVersion = appVersion,
                model = model,
                fragments = fragments,
                winningMainFragment = winningMainFragment,
                defaultPackageName = defaultPackageName,
                enabledExtensionModulePaths = enabledExtensionModulePaths
            ).manifestXml
        }
    }
}
