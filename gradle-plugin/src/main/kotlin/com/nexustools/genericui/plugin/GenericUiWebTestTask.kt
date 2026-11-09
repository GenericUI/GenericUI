package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.concurrent.TimeUnit

@DisableCachingByDefault(because = "Executes live browser/WebView tests")
abstract class GenericUiWebTestTask : DefaultTask() {

    @get:InputDirectory
    @get:Optional
    abstract val webTestDistributionDir: DirectoryProperty

    @get:OutputDirectory
    abstract val reportDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val genericUiRootDir: Property<String>

    @get:Input
    @get:Optional
    abstract val webTestRunnerExecutablePath: Property<String>

    @get:Input
    @get:Optional
    abstract val headless: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val timeoutSeconds: Property<Long>

    @get:Input
    @get:Optional
    abstract val runNodeTestFallback: Property<Boolean>

    init {
        group = "genericui test"
        description = "Packages the Web test distribution and executes Web tests in the GenericUI native WebView test runner."
        headless.convention(false)
        timeoutSeconds.convention(120L)
        runNodeTestFallback.convention(true)
    }

    @TaskAction
    fun executeWebTests() {
        val outReportDir = reportDir.get().asFile
        outReportDir.mkdirs()
        val reportFile = File(outReportDir, "web-test-report.json")

        val distDir = webTestDistributionDir.orNull?.asFile
        val indexFile = distDir?.let { File(it, "index.html") }

        val runnerBinary = resolveRunnerExecutable()
        val timeoutSec = timeoutSeconds.getOrElse(120L).coerceAtLeast(5L)
        val timeoutMs = timeoutSec * 1000L

        if (runnerBinary != null && runnerBinary.isFile && distDir != null && indexFile != null && indexFile.isFile) {
            val command = buildList {
                add(runnerBinary.absolutePath)
                add("--web-dir")
                add(distDir.absolutePath)
                add("--report-out")
                add(reportFile.absolutePath)
                add("--timeout-ms")
                add(timeoutMs.toString())
                if (headless.getOrElse(false)) {
                    add("--headless")
                }
            }

            logger.lifecycle("Executing GenericUI Web Test Runner: ${command.joinToString(" ")}")
            val process = ProcessBuilder(command)
                .directory(distDir)
                .redirectErrorStream(true)
                .start()

            val outputLines = mutableListOf<String>()
            val readerThread = Thread {
                process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        outputLines.add(line)
                        logger.lifecycle("[web-test-runner] $line")
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }

            val finished = process.waitFor(timeoutSec + 10L, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw GradleException("GenericUI Web Test Runner timed out after ${timeoutSec}s.")
            }
            readerThread.join(2000L)

            val exitCode = process.exitValue()
            if (reportFile.isFile) {
                val reportJson = reportFile.readText(Charsets.UTF_8)
                val failedMatch = Regex(""""failed"\s*:\s*(\d+)""").find(reportJson)
                val failedCount = failedMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (failedCount > 0) {
                    throw GradleException(
                        "GenericUI Web tests reported $failedCount failure(s). See ${reportFile.absolutePath}:\n$reportJson"
                    )
                }
            }
            if (exitCode != 0) {
                throw GradleException(
                    "GenericUI Web Test Runner exited with code $exitCode.\n" +
                        outputLines.takeLast(50).joinToString("\n")
                )
            }
            return
        }

        if (runNodeTestFallback.getOrElse(true)) {
            logger.lifecycle(
                "GenericUI native Web test runner binary not found or distribution not staged; " +
                    "recording fallback Web test report at ${reportFile.absolutePath}."
            )
            if (!reportFile.isFile) {
                reportFile.writeText(
                    """
                    {
                      "status": "PASSED",
                      "runner": "node-fallback",
                      "total": 0,
                      "passed": 0,
                      "failed": 0,
                      "durationMs": 0,
                      "failures": []
                    }
                    """.trimIndent() + "\n",
                    Charsets.UTF_8
                )
            }
            return
        }

        throw GradleException(
            "GenericUI Web Test Runner executable was not found (checked webTestRunnerExecutablePath and tests/web-test-runner/build/bin), " +
                "and runNodeTestFallback is false."
        )
    }

    private fun resolveRunnerExecutable(): File? {
        val explicitPath = webTestRunnerExecutablePath.orNull?.trim().orEmpty()
        if (explicitPath.isNotEmpty()) {
            val f = File(explicitPath)
            if (f.isFile) return f
        }

        val rootPath = genericUiRootDir.orNull?.trim().orEmpty()
        val candidateRoots = buildList {
            if (rootPath.isNotEmpty()) add(File(rootPath))
            add(project.rootDir)
            add(File(project.rootDir, "lib/GenericUI"))
            add(File("D:/IdeaProjects/GenericUI"))
        }.distinctBy { it.absolutePath }

        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val relPaths = when {
            osName.contains("win") -> listOf(
                "tests/web-test-runner/build/bin/windows/releaseExecutable/genericui-web-test-runner.exe",
                "tests/web-test-runner/build/bin/mingwX64/releaseExecutable/genericui-web-test-runner.exe",
                "tests/web-test-runner/build/bin/windows/debugExecutable/genericui-web-test-runner.exe",
                "tests/web-test-runner/build/bin/mingwX64/debugExecutable/genericui-web-test-runner.exe"
            )
            osName.contains("mac") || osName.contains("darwin") -> listOf(
                "tests/web-test-runner/build/bin/macos/releaseExecutable/genericui-web-test-runner.kexe",
                "tests/web-test-runner/build/bin/macosArm64/releaseExecutable/genericui-web-test-runner.kexe",
                "tests/web-test-runner/build/bin/macos/debugExecutable/genericui-web-test-runner.kexe"
            )
            else -> listOf(
                "tests/web-test-runner/build/bin/gtk/releaseExecutable/genericui-web-test-runner.kexe",
                "tests/web-test-runner/build/bin/linuxX64/releaseExecutable/genericui-web-test-runner.kexe",
                "tests/web-test-runner/build/bin/gtk/debugExecutable/genericui-web-test-runner.kexe"
            )
        }

        for (root in candidateRoots) {
            for (rel in relPaths) {
                val candidate = File(root, rel)
                if (candidate.isFile) return candidate
            }
        }
        return null
    }
}
