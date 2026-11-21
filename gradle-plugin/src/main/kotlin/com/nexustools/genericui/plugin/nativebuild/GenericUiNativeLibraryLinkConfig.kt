package com.nexustools.genericui.plugin.nativebuild

import java.io.File
import java.io.Serializable
import java.nio.file.Path

/**
 * DSL container (`nativeLibraries { link(...) }`) for declaring native library linker options
 * and library search directories applied to Kotlin/Native desktop and Apple binaries.
 */
open class GenericUiNativeLibraryLinkConfig : Serializable {

    val linkerOpts: MutableList<String> = mutableListOf()
    val libraryPaths: MutableList<Any> = mutableListOf()
    val libraries: MutableList<String> = mutableListOf()

    /**
     * Links one or more native libraries, static/shared archive files, or raw linker flags
     * (e.g. `"sqlite3"`, `"-lz"`, `file("native/libfoo.a")`).
     */
    fun link(vararg targets: Any) {
        for (target in targets) {
            when (target) {
                is File -> linkerOpts.add(target.absolutePath)
                is Path -> linkerOpts.add(target.toAbsolutePath().toString())
                else -> {
                    val str = target.toString().trim()
                    if (str.isEmpty()) continue
                    if (str.startsWith("-")) {
                        linkerOpts.add(str)
                    } else if (str.endsWith(".a", ignoreCase = true) ||
                        str.endsWith(".so", ignoreCase = true) ||
                        str.endsWith(".dylib", ignoreCase = true) ||
                        str.endsWith(".lib", ignoreCase = true) ||
                        str.endsWith(".dll", ignoreCase = true) ||
                        str.contains('/') ||
                        str.contains('\\')
                    ) {
                        libraryPaths.add(str)
                    } else {
                        libraries.add(str)
                    }
                }
            }
        }
    }

    /** Appends raw linker options directly (`"-framework"`, `"AudioToolbox"`, etc.). */
    fun linkerOpts(vararg opts: String) {
        opts.map { it.trim() }.filter { it.isNotEmpty() }.forEach { linkerOpts.add(it) }
    }

    /** Appends `-L<dir>` library search paths. */
    fun libraryDirs(vararg dirs: Any) {
        for (dir in dirs) {
            libraryPaths.add(dir)
        }
    }

    /** Resolves all configured entries into concrete linker arguments relative to [projectDir]. */
    fun resolveLinkerOpts(projectDir: File): List<String> {
        val result = mutableListOf<String>()
        for (pathObj in libraryPaths) {
            val file = when (pathObj) {
                is File -> pathObj
                is Path -> pathObj.toFile()
                else -> {
                    val raw = pathObj.toString().trim()
                    val candidate = File(raw)
                    if (candidate.isAbsolute) candidate else File(projectDir, raw)
                }
            }
            if (file.isDirectory) {
                result.add("-L${file.absolutePath}")
            } else {
                result.add(file.absolutePath)
            }
        }
        for (lib in libraries) {
            result.add("-l$lib")
        }
        result.addAll(linkerOpts)
        return result.distinct()
    }
}
