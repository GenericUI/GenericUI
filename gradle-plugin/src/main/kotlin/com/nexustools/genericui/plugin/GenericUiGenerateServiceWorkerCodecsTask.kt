package com.nexustools.genericui.plugin

import com.nexustools.genericui.plugin.spi.ExtensionBuildContext
import com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
import com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor
import com.nexustools.genericui.plugin.spi.ServiceWorkerMessageCodegen
import org.gradle.api.DefaultTask
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
import java.io.File

/**
 * Gradle codegen & compile-time validation task scanning consumer Kotlin sources for:
 * - `@MainServiceWorker` classes (enforcing <=1 per enabled backend, `ServiceWorker` inheritance,
 *   and zero-arg/all-default constructors)
 * - `@ServiceWorkerMessage` data classes (emitting 1-type-per-file `<Msg>ServiceWorkerCodec.kt`
 *   plus `GeneratedServiceWorkerCodecRegistrar.kt`).
 */
abstract class GenericUiGenerateServiceWorkerCodecsTask : DefaultTask() {

    @get:Input
    abstract val serviceWorkerEnabled: Property<Boolean>

    @get:Input
    abstract val projectRootPath: Property<String>

    @get:Input
    abstract val projectNameValue: Property<String>

    @get:Input
    abstract val sourceDirectories: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Input
    abstract val basePackageName: Property<String>

    @get:Input
    @get:Optional
    abstract val configuredRegistrarPackage: Property<String>

    @get:Input
    abstract val appId: Property<String>

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val appVersion: Property<String>

    @get:Input
    abstract val baseName: Property<String>

    @get:Input
    abstract val enabledExtensionModulePaths: ListProperty<String>

    @get:Input
    abstract val extensionOptions: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        enabledExtensionModulePaths.convention(emptyList())
        extensionOptions.convention(emptyMap())
    }

    @TaskAction
    fun generateServiceWorkerCodecs() {
        val outDir = outputDirectory.get().asFile
        if (!serviceWorkerEnabled.getOrElse(false)) {
            if (outDir.exists()) {
                outDir.deleteRecursively()
            }
            outDir.mkdirs()
            return
        }

        val dirs = sourceDirectories.orNull.orEmpty()
            .map { File(it) }
            .filter { it.exists() && it.isDirectory }
        val rootDir = File(projectRootPath.orNull ?: ".")
        val enabledExts = enabledExtensionModulePaths.getOrElse(emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val scanSpecs = GenericUiExtensionContributorRegistry.allAnnotationScanSpecs(enabledExts, rootDir)
        val discoveredSymbols = GenericUiFragmentScanner.scanProjectSymbols(rootDir, scanSpecs, dirs)
        val discoveredFragments = (GenericUiFragmentScanner.scanProject(rootDir) +
            GenericUiFragmentScanner.scanSourceDirectories(dirs))
            .distinctBy { "${it.qualifiedName}:${it.isMainFragment}:${it.isApplicationClass}" }

        val registrarPkg = ServiceWorkerExtensionContributor.resolveRegistrarPackageFromSettings(
            explicitRegistrarPackage = configuredRegistrarPackage.orNull,
            basePackageName = basePackageName.orNull
        )
        val validationContext = ExtensionBuildContext(
            projectRootDir = rootDir,
            projectName = projectNameValue.orNull ?: rootDir.name,
            appId = appId.orNull ?: "com.nexustools.genericui.app",
            appName = appName.orNull ?: "GenericUI",
            appVersion = appVersion.orNull ?: "1.0.0",
            baseName = baseName.orNull ?: rootDir.name,
            generatedPackageName = registrarPkg,
            enabledExtensionModulePaths = enabledExts,
            discoveredFragments = discoveredFragments,
            discoveredSymbols = discoveredSymbols,
            extensionOptions = extensionOptions.getOrElse(emptyMap())
        )
        ServiceWorkerExtensionContributor.validate(validationContext, discoveredSymbols)

        ServiceWorkerMessageCodegen.generateCodecSources(
            sourceDirectories = dirs,
            outputDirectory = outDir,
            registrarPackageName = registrarPkg
        )
    }
}
