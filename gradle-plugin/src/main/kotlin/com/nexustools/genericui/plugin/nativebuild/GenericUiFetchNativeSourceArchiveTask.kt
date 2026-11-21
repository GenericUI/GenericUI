package com.nexustools.genericui.plugin.nativebuild

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Gradle task (`fetchGenericUiNativeSources`) that downloads, SHA-256-verifies, and extracts
 * remote C/C++ source archives (`.tar.gz`, `.tgz`, `.zip`) declared in `genericui { jni { ... } }`
 * into `<gradleUserHome>/caches/genericui-native-sources/<name>-<sha12>/` with offline cached reuse.
 */
abstract class GenericUiFetchNativeSourceArchiveTask : DefaultTask() {

    @get:Input
    abstract val libraries: ListProperty<GenericUiJniLibrarySpec>

    @get:Input
    abstract val gradleUserHomePath: Property<String>

    @get:Input
    @get:Optional
    abstract val offlineMode: Property<Boolean>

    @get:OutputFile
    abstract val sourcesManifestFile: RegularFileProperty

    @TaskAction
    fun fetchSources() {
        val gradleUserHome = File(gradleUserHomePath.get())
        val cacheRoot = File(gradleUserHome, "caches/genericui-native-sources").apply { mkdirs() }
        val manifestLines = mutableListOf<String>()

        for (lib in libraries.getOrElse(emptyList())) {
            val url = lib.sourceArchiveUrl?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val sha12 = (lib.sourceArchiveSha256?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                ?: GenericUiArchiveExtractor.sha256Hex(url.toByteArray(StandardCharsets.UTF_8))).take(12)
            val safeName = lib.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val targetDir = File(cacheRoot, "$safeName-$sha12")
            val lockFile = File(cacheRoot, "$safeName-$sha12.lock")
            val expectedMarker = buildMarkerText(url, lib.sourceArchiveSha256, lib.sourceArchiveStripComponents)

            RandomAccessFile(lockFile, "rw").use { raf ->
                raf.channel.lock().use {
                    val markerFile = File(targetDir, MARKER_FILE_NAME)
                    val alreadyCached = targetDir.isDirectory &&
                        markerFile.isFile &&
                        runCatching { markerFile.readText(StandardCharsets.UTF_8) }.getOrNull() == expectedMarker

                    if (!alreadyCached) {
                        val stagingDir = File(cacheRoot, "$safeName-$sha12.tmp.${ProcessHandle.current().pid()}")
                        if (stagingDir.exists()) {
                            stagingDir.deleteRecursively()
                        }
                        stagingDir.mkdirs()

                        val archiveFileName = resolveArchiveFileName(url)
                        val archiveFile = File(stagingDir, archiveFileName)
                        val extractDir = File(stagingDir, "extracted").apply { mkdirs() }
                        try {
                            GenericUiArchiveExtractor.downloadFile(
                                url = url,
                                destFile = archiveFile,
                                expectedSha256 = lib.sourceArchiveSha256
                            )
                            GenericUiArchiveExtractor.extractArchive(
                                archiveFile = archiveFile,
                                destDir = extractDir,
                                stripComponents = lib.sourceArchiveStripComponents
                            )
                            File(extractDir, MARKER_FILE_NAME).writeText(expectedMarker, StandardCharsets.UTF_8)
                            if (targetDir.exists()) {
                                targetDir.deleteRecursively()
                            }
                            Files.move(
                                extractDir.toPath(),
                                targetDir.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        } finally {
                            if (stagingDir.exists()) {
                                stagingDir.deleteRecursively()
                            }
                        }
                    }
                }
            }

            manifestLines.add("${lib.name}\t${targetDir.absolutePath}")
        }

        val outFile = sourcesManifestFile.get().asFile
        outFile.parentFile?.mkdirs()
        outFile.writeText(
            if (manifestLines.isEmpty()) "" else manifestLines.joinToString("\n") + "\n",
            StandardCharsets.UTF_8
        )
    }

    private fun buildMarkerText(url: String, sha256: String?, stripComponents: Int): String =
        "url=$url\nsha256=${sha256.orEmpty().lowercase()}\nstrip=$stripComponents\n"

    private fun resolveArchiveFileName(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val name = clean.substringAfterLast('/').trim()
        return if (name.isNotEmpty()) name else "source.tar.gz"
    }

    companion object {
        private const val MARKER_FILE_NAME = ".genericui-source-ready"
    }
}
