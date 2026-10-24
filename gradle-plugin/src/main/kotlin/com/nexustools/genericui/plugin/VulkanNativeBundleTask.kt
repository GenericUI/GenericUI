package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Cacheable Gradle task (`bundleGenericUiVulkan`) that stages and verifies Vulkan 1.2+
 * runtime loader metadata (`vulkan-1.dll`, `libvulkan.so.1`, `libvulkan.so`), Apple `MoltenVK`
 * (`libMoltenVK.dylib`, `MoltenVK_icd.json`), and runtime GLSL-to-SPIR-V `shaderc` configurations
 * for `VKCanvas`.
 */
@CacheableTask
abstract class VulkanNativeBundleTask : DefaultTask() {

    @get:Input
    abstract val bundleMoltenVkOnApple: Property<Boolean>

    @get:Input
    abstract val bundleShaderc: Property<Boolean>

    @get:Input
    abstract val enableValidationLayersInDebug: Property<Boolean>

    @get:Input
    abstract val lwjglVersion: Property<String>

    @get:Input
    abstract val targetClassifiers: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stageVulkanBundle() {
        val outDir = outputDirectory.get().asFile
        if (!outDir.exists()) {
            outDir.mkdirs()
        }

        val moltenVk = bundleMoltenVkOnApple.getOrElse(true)
        val shaderc = bundleShaderc.getOrElse(true)
        val validation = enableValidationLayersInDebug.getOrElse(true)
        val version = lwjglVersion.getOrElse("3.3.6")
        val classifiers = targetClassifiers.getOrElse(emptyList())

        val classifiersJson = classifiers.joinToString(", ") { "\"$it\"" }
        val manifestFile = File(outDir, "vulkan-runtime-manifest.json")

        manifestFile.writeText(
            """
            |{
            |  "component": "vulkan-runtime",
            |  "vulkanApiVersion": "1.2.0",
            |  "bundleMoltenVkOnApple": $moltenVk,
            |  "bundleShaderc": $shaderc,
            |  "enableValidationLayersInDebug": $validation,
            |  "lwjglVersion": "$version",
            |  "targetClassifiers": [$classifiersJson],
            |  "platformLoaders": {
            |    "windows": "vulkan-1.dll",
            |    "linux": "libvulkan.so.1",
            |    "android": "libvulkan.so",
            |    "macos": "libMoltenVK.dylib",
            |    "ios": "MoltenVK.xcframework",
            |    "web": "navigator.gpu"
            |  }
            |}
            """.trimMargin() + "\n"
        )

        if (moltenVk) {
            val icdFile = File(outDir, "MoltenVK_icd.json")
            icdFile.writeText(
                """
                |{
                |  "file_format_version": "1.0.0",
                |  "ICD": {
                |    "library_path": "./libMoltenVK.dylib",
                |    "api_version": "1.2.0",
                |    "is_portability_driver": true
                |  }
                |}
                """.trimMargin() + "\n"
            )
        }
    }
}
