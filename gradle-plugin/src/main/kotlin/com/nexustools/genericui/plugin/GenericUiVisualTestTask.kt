package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task (`windowsVisualTest` / `runJestElectricWindowsVisualTest`) that executes a
 * GenericUI `:desktop:windows` test binary in visual mode (`visualOnWindows = true`), opening
 * the live Win32 window (`Win32NativeWindowHost`) on screen so developers can watch the
 * automated `JestElectricRunner` interactions happen in real time.
 */
abstract class GenericUiVisualTestTask : DefaultTask() {

    @get:Input
    abstract val visualOnWindows: Property<Boolean>

    @get:Input
    abstract val visualStepDelayMs: Property<Long>

    @get:Input
    @get:Optional
    abstract val testBinaryPath: Property<String>

    init {
        group = "verification"
        description = "Runs GenericUI JestElectricRunner tests with a live visual Win32 window on Windows."
        visualOnWindows.convention(true)
        visualStepDelayMs.convention(24L)
    }

    @TaskAction
    fun runVisualTests() {
        val explicitBinary = testBinaryPath.orNull?.let { File(it) }
        val defaultDebugTestExe = project.layout.buildDirectory
            .file("bin/mingwX64/debugTest/test.exe")
            .get()
            .asFile
        val defaultDebugAppExe = project.layout.buildDirectory
            .file("bin/mingwX64/debugExecutable/${project.name}.exe")
            .get()
            .asFile

        val targetExe = when {
            explicitBinary != null && explicitBinary.exists() -> explicitBinary
            defaultDebugTestExe.exists() -> defaultDebugTestExe
            defaultDebugAppExe.exists() -> defaultDebugAppExe
            else -> null
        }

        if (targetExe == null) {
            logger.lifecycle(
                "[GenericUI JestElectricRunner] No compiled mingwX64 test binary found yet at ${defaultDebugTestExe.absolutePath}. " +
                    "Run 'linkDebugTestMingwX64' or 'mingwX64Test' to compile and execute Windows visual UI tests."
            )
            return
        }

        val args = mutableListOf<String>()
        if (!visualOnWindows.getOrElse(true)) {
            args.add("--headless")
        }

        logger.lifecycle(
            "[GenericUI JestElectricRunner] Launching Windows visual test binary: ${targetExe.absolutePath} " +
                "(visualOnWindows=${visualOnWindows.getOrElse(true)}, stepDelayMs=${visualStepDelayMs.getOrElse(24L)})"
        )

        val process = ProcessBuilder(listOf(targetExe.absolutePath) + args)
            .directory(project.projectDir)
            .inheritIO()
            .apply {
                environment()["GENERICUI_VISUAL_TEST"] = if (visualOnWindows.getOrElse(true)) "1" else "0"
                environment()["GENERICUI_VISUAL_STEP_DELAY_MS"] = visualStepDelayMs.getOrElse(24L).toString()
            }
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("JestElectricRunner Windows visual test exited with non-zero code: $exitCode")
        }
    }
}
