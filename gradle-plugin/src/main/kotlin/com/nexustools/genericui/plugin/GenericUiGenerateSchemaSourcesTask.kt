package com.nexustools.genericui.plugin

import com.nexustools.genericui.plugin.spi.SchemaExtensionContributor
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
 * Gradle codegen task scanning consumer Kotlin sources for `@SchemaData` classes and emitting
 * reflection-free `<ClassName>SchemaDescriptor.kt` files plus `GeneratedSchemaRegistrar.kt`
 * into `build/generated/genericui/schema/commonMain/kotlin`.
 */
abstract class GenericUiGenerateSchemaSourcesTask : DefaultTask() {

    @get:Input
    abstract val schemaEnabled: Property<Boolean>

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

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generateSchemas() {
        val outDir = outputDirectory.get().asFile
        if (!schemaEnabled.getOrElse(false)) {
            if (outDir.exists()) {
                outDir.deleteRecursively()
            }
            outDir.mkdirs()
            return
        }

        val dirs = sourceDirectories.orNull.orEmpty()
            .map { File(it) }
            .filter { it.exists() && it.isDirectory }
        val registrarPkg = SchemaExtensionContributor.resolveRegistrarPackageFromSettings(
            explicitRegistrarPackage = configuredRegistrarPackage.orNull,
            basePackageName = basePackageName.orNull
        )
        SchemaExtensionContributor.generateSchemaSources(
            sourceDirectories = dirs,
            outputDirectory = outDir,
            registrarPackageName = registrarPkg
        )
    }
}
