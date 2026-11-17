package com.nexustools.genericui.plugin

import java.io.File

/**
 * Generated Android manifest XML and companion resource XML map (`"xml/..."`, `"values/..."`)
 * produced by [GenericUiAndroidManifestXmlGenerator].
 */
data class GenericUiAndroidManifestOutput(
    val manifestXml: String,
    val generatedResFiles: Map<String, String> = emptyMap(),
    val resolvedPlaceholders: Map<String, String> = emptyMap()
) {
    /** Writes all [generatedResFiles] beneath [resDir], cleaning stale generated files first if requested. */
    fun writeResFiles(resDir: File, cleanExisting: Boolean = true) {
        if (cleanExisting && resDir.exists()) {
            resDir.deleteRecursively()
        }
        if (generatedResFiles.isEmpty()) {
            resDir.mkdirs()
            return
        }
        resDir.mkdirs()
        for ((relPath, content) in generatedResFiles) {
            val outFile = File(resDir, relPath)
            outFile.parentFile?.mkdirs()
            outFile.writeText(content)
        }
    }
}
