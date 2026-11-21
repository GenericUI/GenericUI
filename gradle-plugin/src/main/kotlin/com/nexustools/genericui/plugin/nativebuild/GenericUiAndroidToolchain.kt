package com.nexustools.genericui.plugin.nativebuild

import java.io.File

/**
 * Resolved Android C/C++ native toolchain descriptor, representing either a full installed
 * Android NDK or the zero-NDK fallback backed by Kotlin/Native's LLVM + Android sysroot.
 */
data class GenericUiAndroidToolchain(
    val isFullNdk: Boolean,
    val clang: File,
    val clangPlusPlus: File,
    val lld: File,
    val archiver: File,
    val sysrootDir: File,
    val konanSysrootRoot: File? = null,
    val konanAndroidToolchainRoot: File? = null,
    val ndkRoot: File? = null,
    val cmakeExecutable: File? = null,
    val fallbackJniHeadersDir: File? = null,
    val hostOsTag: String = ""
) {

    /**
     * Resolves the highest available Android API directory `<= max(minApiLevel, 21)` inside
     * the sysroot, or the lowest available if `minApiLevel` is older.
     */
    fun resolveAvailableApiLevel(requestedApiLevel: Int): Int {
        val searchDirs = buildList {
            val archLib = File(sysrootDir, "usr/lib/aarch64-linux-android")
            if (archLib.isDirectory) {
                archLib.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name.toIntOrNull() }?.let { addAll(it) }
            }
            konanSysrootRoot?.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("android-") }
                ?.mapNotNull { it.name.removePrefix("android-").toIntOrNull() }
                ?.let { addAll(it) }
        }.distinct().sorted()

        if (searchDirs.isEmpty()) return requestedApiLevel.coerceAtLeast(21)
        return searchDirs.lastOrNull { it <= requestedApiLevel } ?: searchDirs.first()
    }

    /** Returns the Clang `--target` triple for [abi] and [minApiLevel]. */
    fun targetTriple(abi: String, minApiLevel: Int): String {
        val api = minApiLevel.coerceAtLeast(21)
        return when (abi) {
            "arm64-v8a" -> "aarch64-linux-android$api"
            "armeabi-v7a" -> "armv7a-linux-androideabi$api"
            "x86_64" -> "x86_64-linux-android$api"
            "x86" -> "i686-linux-android$api"
            else -> throw IllegalArgumentException("Unsupported Android ABI '$abi'")
        }
    }

    /** Returns the sysroot arch triple directory name (`aarch64-linux-android`, etc.) for [abi]. */
    fun archTripleDirName(abi: String): String = when (abi) {
        "arm64-v8a" -> "aarch64-linux-android"
        "armeabi-v7a" -> "arm-linux-androideabi"
        "x86_64" -> "x86_64-linux-android"
        "x86" -> "i686-linux-android"
        else -> throw IllegalArgumentException("Unsupported Android ABI '$abi'")
    }

    /** Builds compiler flags (excluding input/output paths) for compiling a C or C++ translation unit. */
    fun buildCompileArgs(abi: String, minApiLevel: Int, isCpp: Boolean): List<String> {
        val api = minApiLevel.coerceAtLeast(21)
        val triple = targetTriple(abi, api)
        val archDirName = archTripleDirName(abi)

        return buildList {
            add("--target=$triple")
            add("--sysroot=${sysrootDir.absolutePath}")
            if (isCpp) {
                val cxxInc = File(sysrootDir, "usr/include/c++/v1")
                if (cxxInc.isDirectory) {
                    add("-isystem")
                    add(cxxInc.absolutePath)
                }
            }
            val archInc = File(sysrootDir, "usr/include/$archDirName")
            if (archInc.isDirectory) {
                add("-isystem")
                add(archInc.absolutePath)
            }
            val usrInc = File(sysrootDir, "usr/include")
            if (usrInc.isDirectory) {
                add("-isystem")
                add(usrInc.absolutePath)
            }
            val jniHeaderInSysroot = File(sysrootDir, "usr/include/jni.h")
            if (!jniHeaderInSysroot.isFile && fallbackJniHeadersDir != null) {
                add("-isystem")
                add(fallbackJniHeadersDir.absolutePath)
                val linuxMd = File(fallbackJniHeadersDir, "linux")
                if (linuxMd.isDirectory) {
                    add("-isystem")
                    add(linuxMd.absolutePath)
                }
            }
            add("-D__ANDROID_API__=$api")
            add("-fPIC")
        }
    }

    /** Builds linker command arguments to link [objectFiles] into [outputSo]. */
    fun buildLinkCommand(
        abi: String,
        minApiLevel: Int,
        soName: String,
        pageSize16Kb: Boolean,
        hasCpp: Boolean,
        objectFiles: List<File>,
        userLinkerFlags: List<String>,
        userLinkLibraries: List<String>,
        outputSo: File
    ): List<String> {
        val api = minApiLevel.coerceAtLeast(21)
        val triple = targetTriple(abi, api)
        val driver = if (hasCpp) clangPlusPlus else clang

        if (isFullNdk) {
            return buildList {
                add(driver.absolutePath)
                add("--target=$triple")
                add("--sysroot=${sysrootDir.absolutePath}")
                add("-shared")
                add("-Wl,-soname,$soName")
                if (pageSize16Kb) {
                    add("-Wl,-z,max-page-size=16384")
                    add("-Wl,-z,common-page-size=16384")
                }
                objectFiles.forEach { add(it.absolutePath) }
                addAll(userLinkerFlags)
                userLinkLibraries.forEach { add("-l$it") }
                add("-lm")
                add("-o")
                add(outputSo.absolutePath)
            }
        }

        val archDirName = archTripleDirName(abi)
        val resolvedApi = resolveAvailableApiLevel(api)
        val crtDir = resolveCrtLibDir(abi, resolvedApi, archDirName)
        val archLibDir = File(sysrootDir, "usr/lib/$archDirName").takeIf { it.isDirectory }
        val builtinsLib = resolveCompilerRtBuiltins(abi)

        return buildList {
            add(driver.absolutePath)
            add("--target=$triple")
            add("--sysroot=${sysrootDir.absolutePath}")
            add("-fuse-ld=${lld.absolutePath}")
            add("-shared")
            add("-nostdlib")
            if (crtDir != null) {
                add("-L${crtDir.absolutePath}")
            }
            if (archLibDir != null) {
                add("-L${archLibDir.absolutePath}")
            }
            val crtBegin = crtDir?.let { File(it, "crtbegin_so.o") }?.takeIf { it.isFile }
            if (crtBegin != null) {
                add(crtBegin.absolutePath)
            }
            objectFiles.forEach { add(it.absolutePath) }
            addAll(userLinkerFlags)
            userLinkLibraries.forEach { add("-l$it") }
            if (hasCpp) {
                add("-lc++_static")
                add("-lc++abi")
                if (abi == "armeabi-v7a" || abi == "x86") {
                    if (archLibDir != null && File(archLibDir, "libandroid_support.a").isFile) {
                        add("-landroid_support")
                    }
                }
            }
            if (builtinsLib != null && builtinsLib.isFile) {
                add(builtinsLib.absolutePath)
            }
            if (crtDir != null && File(crtDir, "libcompiler_rt-extras.a").isFile) {
                add("-lcompiler_rt-extras")
            }
            if (abi == "armeabi-v7a" && archLibDir != null && File(archLibDir, "libunwind.a").isFile) {
                add("-lunwind")
            }
            add("-lm")
            add("-lc")
            add("-ldl")
            val crtEnd = crtDir?.let { File(it, "crtend_so.o") }?.takeIf { it.isFile }
            if (crtEnd != null) {
                add(crtEnd.absolutePath)
            }
            add("-Wl,-soname,$soName")
            if (pageSize16Kb) {
                add("-Wl,-z,max-page-size=16384")
                add("-Wl,-z,common-page-size=16384")
            }
            add("-o")
            add(outputSo.absolutePath)
        }
    }

    private fun resolveCrtLibDir(abi: String, apiLevel: Int, archDirName: String): File? {
        val toolchainApiDir = File(sysrootDir, "usr/lib/$archDirName/$apiLevel")
        if (toolchainApiDir.isDirectory && File(toolchainApiDir, "crtbegin_so.o").isFile) {
            return toolchainApiDir
        }
        val konanArchDir = when (abi) {
            "arm64-v8a" -> "arch-arm64"
            "armeabi-v7a" -> "arch-arm"
            "x86_64" -> "arch-x86_64"
            "x86" -> "arch-x86"
            else -> return null
        }
        val libSubDir = if (abi == "x86_64") "usr/lib64" else "usr/lib"
        val konanApiDir = konanSysrootRoot?.let { File(it, "android-$apiLevel/$konanArchDir/$libSubDir") }
        if (konanApiDir != null && konanApiDir.isDirectory && File(konanApiDir, "crtbegin_so.o").isFile) {
            return konanApiDir
        }
        return null
    }

    private fun resolveCompilerRtBuiltins(abi: String): File? {
        val root = konanAndroidToolchainRoot ?: return null
        val rtArch = when (abi) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "i686"
            else -> return null
        }
        val clangRoot = File(root, "lib/clang")
        if (!clangRoot.isDirectory) return null
        val versionDirs = clangRoot.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }.orEmpty()
        for (verDir in versionDirs) {
            val candidate = File(verDir, "lib/linux/libclang_rt.builtins-$rtArch-android.a")
            if (candidate.isFile) {
                return candidate
            }
        }
        return null
    }
}
