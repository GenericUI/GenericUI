package com.nexustools.genericui.plugin.nativebuild

import java.io.File

/**
 * Resolved host C/C++ toolchain and JDK JNI header directories used to compile host JVM test
 * and Swing bundled shared libraries (`foo.dll`, `libfoo.dylib`, `libfoo.so`).
 */
data class GenericUiHostToolchain(
    val osName: String,
    val archName: String,
    val osArchTag: String,
    val clang: File,
    val clangPlusPlus: File,
    val lld: File?,
    val archiver: File?,
    val targetTriple: String?,
    val sysrootDir: File?,
    val jniIncludeDir: File,
    val jniMdIncludeDir: File,
    val cmakeExecutable: File? = null
) {

    /** Builds compiler flags (excluding input/output paths) for compiling a host C or C++ file. */
    fun buildCompileArgs(isCpp: Boolean): List<String> = buildList {
        if (targetTriple != null) {
            add("--target=$targetTriple")
        }
        if (sysrootDir != null) {
            add("--sysroot=${sysrootDir.absolutePath}")
        }
        if (osName != "windows") {
            add("-fPIC")
        }
        add("-I${jniIncludeDir.absolutePath}")
        add("-I${jniMdIncludeDir.absolutePath}")
        if (isCpp && osName == "windows" && sysrootDir != null) {
            val mingwInc = File(sysrootDir, "x86_64-w64-mingw32/include")
            if (mingwInc.isDirectory) {
                add("-isystem")
                add(mingwInc.absolutePath)
            }
        }
    }

    /** Builds linker command arguments to link [objectFiles] into [outputSharedLib]. */
    fun buildLinkCommand(
        hasCpp: Boolean,
        objectFiles: List<File>,
        userLinkerFlags: List<String>,
        userLinkLibraries: List<String>,
        outputSharedLib: File
    ): List<String> {
        val driver = if (hasCpp) clangPlusPlus else clang
        return buildList {
            add(driver.absolutePath)
            if (targetTriple != null) {
                add("--target=$targetTriple")
            }
            if (sysrootDir != null) {
                add("--sysroot=${sysrootDir.absolutePath}")
            }
            add("-shared")
            if (lld != null && (osName == "windows" || osName == "linux")) {
                add("-fuse-ld=${lld.absolutePath}")
            }
            objectFiles.forEach { add(it.absolutePath) }
            addAll(userLinkerFlags)
            userLinkLibraries.forEach { add("-l$it") }
            if (osName == "windows") {
                add("-static-libgcc")
                if (hasCpp) {
                    add("-static-libstdc++")
                }
            } else if (osName == "macos") {
                add("-Wl,-install_name,@rpath/${outputSharedLib.name}")
            } else {
                add("-Wl,-soname,${outputSharedLib.name}")
                add("-lm")
                add("-lpthread")
            }
            add("-o")
            add(outputSharedLib.absolutePath)
        }
    }
}
