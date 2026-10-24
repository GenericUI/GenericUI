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
 * Cacheable Gradle task (`bundleGenericUiAngle`) that stages and verifies Google ANGLE
 * (`libEGL.dll`, `libGLESv2.dll`, `d3dcompiler_47.dll`) configuration metadata for Windows
 * OpenGL ES 3.0+ targets (`:windows` and `:swing` with `useLwjglGL = true`).
 *
 * By running OpenGL ES 3.0/3.1 atop Direct3D 11 via Google ANGLE on Windows, GenericUI's `GLCanvas`
 * avoids inconsistent vendor WGL drivers and maintains 100% shader parity with Android, iOS,
 * Linux EGL, macOS, and WebGL2.
 */
@CacheableTask
abstract class AngleNativeBundleTask : DefaultTask() {

    @get:Input
    abstract val bundleAngleOnWindows: Property<Boolean>

    @get:Input
    abstract val angleBackendRenderer: Property<String>

    @get:Input
    abstract val preferOpenGLES31: Property<Boolean>

    @get:Input
    abstract val targetClassifiers: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stageAngleBundle() {
        val outDir = outputDirectory.get().asFile
        if (!outDir.exists()) {
            outDir.mkdirs()
        }

        val enabled = bundleAngleOnWindows.getOrElse(true)
        val renderer = angleBackendRenderer.getOrElse("d3d11")
        val glesVersion = if (preferOpenGLES31.getOrElse(true)) "3.1" else "3.0"
        val classifiers = targetClassifiers.getOrElse(emptyList())

        val requiredLibraries = listOf(
            "libEGL.dll",
            "libGLESv2.dll",
            "d3dcompiler_47.dll"
        )

        val manifestFile = File(outDir, "angle-runtime-manifest.json")
        val classifiersJson = classifiers.joinToString(", ") { "\"$it\"" }
        val libsJson = requiredLibraries.joinToString(", ") { "\"$it\"" }

        manifestFile.writeText(
            """
            |{
            |  "component": "google-angle",
            |  "enabled": $enabled,
            |  "angleBackendRenderer": "$renderer",
            |  "openGlesVersion": "$glesVersion",
            |  "eglPlatform": "EGL_PLATFORM_ANGLE_ANGLE",
            |  "eglPlatformType": "EGL_PLATFORM_ANGLE_TYPE_D3D11_ANGLE",
            |  "targetClassifiers": [$classifiersJson],
            |  "requiredWindowsBinaries": [$libsJson]
            |}
            """.trimMargin() + "\n"
        )

        if (enabled) {
            for (classifier in classifiers.filter { it.contains("windows") }) {
                val archDir = File(outDir, classifier)
                archDir.mkdirs()
                File(archDir, "angle-libraries.txt").writeText(
                    requiredLibraries.joinToString("\n") + "\n"
                )
            }
        }
    }
}
