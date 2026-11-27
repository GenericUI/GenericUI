package com.nexustools.genericui.plugin

import com.nexustools.genericui.plugin.spi.BinaryCodecCodegen
import com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
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
 * Gradle codegen task (`generateGenericUiBinaryCodecs`) scanning Kotlin source roots for
 * `@BinarySerializable` `data class` declarations (and any custom annotations contributed via
 * [GenericUiExtensionContributorRegistry.allBinaryCodecScanSpecs]), emitting 1-type-per-file
 * `<Class>BinaryCodec.kt` implementations of `DataClassCodec<T>` plus
 * `<genPkg>.GeneratedBinaryCodecRegistrar`.
 */
abstract class GenericUiGenerateBinaryCodecsTask : DefaultTask() {

    @get:Input
    abstract val binaryCodecsEnabled: Property<Boolean>

    @get:Input
    abstract val projectRootPath: Property<String>

    @get:Input
    abstract val sourceDirectories: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Input
    abstract val generatedPackageName: Property<String>

    @get:Input
    @get:Optional
    abstract val enabledExtensionModulePaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val extraAnnotationSimpleNames: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val extraAnnotationQualifiedNames: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        binaryCodecsEnabled.convention(true)
        enabledExtensionModulePaths.convention(emptyList())
        extraAnnotationSimpleNames.convention(emptyList())
        extraAnnotationQualifiedNames.convention(emptyList())
    }

    @TaskAction
    fun generateBinaryCodecs() {
        val outDir = outputDirectory.get().asFile
        if (!binaryCodecsEnabled.getOrElse(true)) {
            if (outDir.exists()) {
                outDir.deleteRecursively()
            }
            outDir.mkdirs()
            return
        }

        val rootDir = File(projectRootPath.orNull?.takeIf { it.isNotBlank() } ?: ".")
        val configuredDirs = sourceDirectories.orNull.orEmpty()
            .map { File(it) }
            .filter { it.exists() && it.isDirectory }
        val dirs = if (configuredDirs.isNotEmpty()) {
            configuredDirs
        } else {
            listOf(
                File(rootDir, "src/commonMain/kotlin"),
                File(rootDir, "src/common/kotlin"),
                File(rootDir, "src/main/kotlin")
            ).filter { it.exists() && it.isDirectory }
        }

        val enabledExts = enabledExtensionModulePaths.getOrElse(emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val spiSpecs = GenericUiExtensionContributorRegistry.allBinaryCodecScanSpecs(enabledExts, rootDir)
        val extraSimple = (extraAnnotationSimpleNames.getOrElse(emptyList()) +
            spiSpecs.flatMap { it.simpleAnnotationNames }).toSet()
        val extraQualified = (extraAnnotationQualifiedNames.getOrElse(emptyList()) +
            spiSpecs.flatMap { it.qualifiedAnnotationNames }).toSet()

        val genPkg = generatedPackageName.orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: GenericUiGenerateLaunchersTask.DEFAULT_GENERATED_PACKAGE

        BinaryCodecCodegen.generateCodecSources(
            sourceDirectories = dirs,
            outputDirectory = outDir,
            registrarPackageName = genPkg,
            extraSimpleAnnotationNames = extraSimple,
            extraQualifiedAnnotationNames = extraQualified
        )
    }
}
