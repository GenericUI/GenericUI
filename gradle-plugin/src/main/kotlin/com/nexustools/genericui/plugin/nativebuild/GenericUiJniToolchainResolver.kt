package com.nexustools.genericui.plugin.nativebuild

import org.gradle.api.GradleException
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.Properties

/**
 * Resolves native C/C++ toolchains for Android JNI shared libraries (installed Android NDK or
 * automatic zero-NDK fallback using Kotlin/Native's LLVM + Android sysroot) and host JVM / Swing
 * shared libraries (including JDK `<jni.h>`/`<jni_md.h>` discovery with bundled header fallback).
 */
object GenericUiJniToolchainResolver {

    private const val DEFAULT_KONAN_DEPENDENCIES_URL = "https://download.jetbrains.com/kotlin/native"
    private const val DEFAULT_ANDROID_SYSROOT_NAME = "target-sysroot-1-android_ndk"
    private const val BUNDLED_JNI_HEADERS_VERSION = "v1"

    /** Resolves canonical host OS identifier (`"windows"`, `"macos"`, or `"linux"`). */
    fun normalizedHostOs(rawOsName: String = System.getProperty("os.name").orEmpty()): String {
        val lower = rawOsName.trim().lowercase(Locale.ROOT)
        return when {
            lower.contains("mac") || lower.contains("darwin") -> "macos"
            lower.contains("win") -> "windows"
            lower.contains("linux") -> "linux"
            else -> lower.replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "unknown" }
        }
    }

    /** Resolves canonical host CPU architecture (`"x86_64"`, `"aarch64"`, `"x86"`, or `"arm"`). */
    fun normalizedHostArch(rawArch: String = System.getProperty("os.arch").orEmpty()): String {
        val lower = rawArch.trim().lowercase(Locale.ROOT)
        return when (lower) {
            "amd64", "x86_64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            "x86", "i386", "i486", "i586", "i686" -> "x86"
            "arm", "armv7", "armv7l", "armeabi", "armeabi-v7a" -> "arm"
            else -> lower.replace(Regex("[^a-z0-9_]+"), "-").trim('-').ifEmpty { "unknown" }
        }
    }

    /** Returns `<os>-<arch>` host directory tag matching `JniLibraryLoader`. */
    fun hostOsArchTag(): String = "${normalizedHostOs()}-${normalizedHostArch()}"

    /** Maps [libraryName] to the host shared library file name (`foo.dll`, `libfoo.dylib`, `libfoo.so`). */
    fun mapHostLibraryName(libraryName: String): String {
        val trimmed = libraryName.trim()
        val os = normalizedHostOs()
        return when (os) {
            "windows" -> if (trimmed.endsWith(".dll", ignoreCase = true)) trimmed else "$trimmed.dll"
            "macos" -> when {
                trimmed.endsWith(".dylib", ignoreCase = true) -> trimmed
                trimmed.startsWith("lib") -> "$trimmed.dylib"
                else -> "lib$trimmed.dylib"
            }
            else -> when {
                trimmed.endsWith(".so", ignoreCase = true) -> trimmed
                trimmed.startsWith("lib") -> "$trimmed.so"
                else -> "lib$trimmed.so"
            }
        }
    }

    /** Maps [libraryName] to the Android shared library file name (`lib<name>.so`). */
    fun mapAndroidLibraryName(libraryName: String): String {
        val trimmed = libraryName.trim()
        return when {
            trimmed.endsWith(".so", ignoreCase = true) ->
                if (trimmed.startsWith("lib")) trimmed else "lib$trimmed"
            trimmed.startsWith("lib") -> "$trimmed.so"
            else -> "lib$trimmed.so"
        }
    }

    /** Resolves an Android NDK toolchain when installed, or automatically falls back to Konan LLVM + Android sysroot. */
    fun resolveAndroidToolchain(
        projectDir: File,
        gradleUserHomeDir: File,
        konanHomeDir: File? = null,
        konanDataDir: File? = null,
        allowNetworkDownload: Boolean = true
    ): GenericUiAndroidToolchain {
        val cmakeExe = findCmakeExecutable(projectDir)
        val installedNdk = findInstalledAndroidNdk(projectDir)
        if (installedNdk != null) {
            val hostTag = resolveNdkHostPrebuiltTag()
            val llvmPrebuilt = File(installedNdk, "toolchains/llvm/prebuilt/$hostTag")
            val exeSuffix = if (normalizedHostOs() == "windows") ".exe" else ""
            val clang = File(llvmPrebuilt, "bin/clang$exeSuffix")
            val clangPlusPlus = File(llvmPrebuilt, "bin/clang++$exeSuffix")
            val lld = File(llvmPrebuilt, "bin/ld.lld$exeSuffix")
            val ar = File(llvmPrebuilt, "bin/llvm-ar$exeSuffix")
            val sysroot = File(llvmPrebuilt, "sysroot")
            if (clang.isFile && sysroot.isDirectory) {
                return GenericUiAndroidToolchain(
                    isFullNdk = true,
                    clang = clang,
                    clangPlusPlus = if (clangPlusPlus.isFile) clangPlusPlus else clang,
                    lld = lld,
                    archiver = ar,
                    sysrootDir = sysroot,
                    ndkRoot = installedNdk,
                    cmakeExecutable = cmakeExe,
                    hostOsTag = hostTag
                )
            }
        }

        // Zero-NDK fallback using Kotlin/Native bundled LLVM + Konan Android sysroot/toolchain
        val resolvedKonanData = resolveKonanDataDir(konanDataDir)
        val resolvedKonanHome = resolveKonanHomeDir(konanHomeDir, resolvedKonanData)
        val konanProps = resolvedKonanHome?.let { loadKonanProperties(it) } ?: Properties()
        val depsDir = File(resolvedKonanData, "dependencies").apply { mkdirs() }
        val hostKonan = hostKonanTargetName()
        val dependenciesUrl = konanProps.getProperty("dependenciesUrl")?.trim()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_KONAN_DEPENDENCIES_URL

        val llvmDir = resolveKonanLlvmDir(depsDir, konanProps, hostKonan)
            ?: throw GradleException(
                "No Android NDK is installed and Kotlin/Native LLVM was not found under ${depsDir.absolutePath}."
            )
        val exeSuffix = if (normalizedHostOs() == "windows") ".exe" else ""
        val clang = File(llvmDir, "bin/clang$exeSuffix").takeIf { it.isFile }
            ?: throw GradleException("Konan clang not found under ${llvmDir.absolutePath}")
        val clangPlusPlus = File(llvmDir, "bin/clang++$exeSuffix").takeIf { it.isFile } ?: clang
        val lld = File(llvmDir, "bin/ld.lld$exeSuffix").takeIf { it.isFile }
            ?: throw GradleException("Konan ld.lld not found under ${llvmDir.absolutePath}")
        val ar = File(llvmDir, "bin/llvm-ar$exeSuffix").takeIf { it.isFile } ?: lld

        val sysrootDepName = konanProps.getProperty("targetSysRoot.android_arm64")?.trim()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_ANDROID_SYSROOT_NAME
        val toolchainDepName = konanProps.getProperty("targetToolchain.$hostKonan-android_arm64")?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultAndroidToolchainDepName()

        val sysrootDirExtracted = ensureKonanDependencyDir(
            depsDir = depsDir,
            depName = sysrootDepName,
            fallbackPrefix = "target-sysroot-",
            fallbackSuffix = "-android_ndk",
            dependenciesUrl = dependenciesUrl,
            allowNetworkDownload = allowNetworkDownload
        )
        val toolchainDirExtracted = ensureKonanDependencyDir(
            depsDir = depsDir,
            depName = toolchainDepName,
            fallbackPrefix = "target-toolchain-",
            fallbackSuffix = "-android_ndk",
            dependenciesUrl = dependenciesUrl,
            allowNetworkDownload = allowNetworkDownload
        )

        val effectiveSysroot = toolchainDirExtracted?.let { File(it, "sysroot") }?.takeIf { it.isDirectory }
            ?: sysrootDirExtracted
            ?: throw GradleException(
                "Unable to resolve Konan Android sysroot '$sysrootDepName' under ${depsDir.absolutePath}."
            )

        val (fallbackJniDir, _) = extractBundledJniHeaders(
            File(gradleUserHomeDir, "caches/genericui-jni-headers/$BUNDLED_JNI_HEADERS_VERSION")
        )

        return GenericUiAndroidToolchain(
            isFullNdk = false,
            clang = clang,
            clangPlusPlus = clangPlusPlus,
            lld = lld,
            archiver = ar,
            sysrootDir = effectiveSysroot,
            konanSysrootRoot = sysrootDirExtracted,
            konanAndroidToolchainRoot = toolchainDirExtracted,
            cmakeExecutable = cmakeExe,
            fallbackJniHeadersDir = fallbackJniDir,
            hostOsTag = hostKonan
        )
    }

    /** Resolves the host C/C++ compiler toolchain and JDK `<jni.h>` / `<jni_md.h>` headers. */
    fun resolveHostToolchain(
        projectDir: File,
        gradleUserHomeDir: File,
        konanHomeDir: File? = null,
        konanDataDir: File? = null
    ): GenericUiHostToolchain {
        val os = normalizedHostOs()
        val arch = normalizedHostArch()
        val (jniInc, jniMdInc) = resolveJniHeaderDirs(gradleUserHomeDir)
        val cmakeExe = findCmakeExecutable(projectDir)

        val resolvedKonanData = resolveKonanDataDir(konanDataDir)
        val resolvedKonanHome = resolveKonanHomeDir(konanHomeDir, resolvedKonanData)
        val konanProps = resolvedKonanHome?.let { loadKonanProperties(it) } ?: Properties()
        val depsDir = File(resolvedKonanData, "dependencies")
        val hostKonan = hostKonanTargetName()
        val exeSuffix = if (os == "windows") ".exe" else ""

        val llvmDir = resolveKonanLlvmDir(depsDir, konanProps, hostKonan)
        val konanClang = llvmDir?.let { File(it, "bin/clang$exeSuffix") }?.takeIf { it.isFile }
        val konanClangPlusPlus = llvmDir?.let { File(it, "bin/clang++$exeSuffix") }?.takeIf { it.isFile }
        val konanLld = llvmDir?.let { File(it, "bin/ld.lld$exeSuffix") }?.takeIf { it.isFile }
        val konanAr = llvmDir?.let { File(it, "bin/llvm-ar$exeSuffix") }?.takeIf { it.isFile }

        when (os) {
            "windows" -> {
                val mingwName = konanProps.getProperty("toolchainDependency.mingw_x64")?.trim()?.takeIf { it.isNotEmpty() }
                val mingwSysroot = mingwName?.let { File(depsDir, it) }?.takeIf { it.isDirectory }
                    ?: depsDir.listFiles()?.firstOrNull {
                        it.isDirectory && it.name.startsWith("msys2-mingw-w64-")
                    }
                if (konanClang != null && mingwSysroot != null) {
                    val triple = if (arch == "aarch64") "aarch64-pc-windows-gnu" else "x86_64-pc-windows-gnu"
                    return GenericUiHostToolchain(
                        osName = os,
                        archName = arch,
                        osArchTag = "$os-$arch",
                        clang = konanClang,
                        clangPlusPlus = konanClangPlusPlus ?: konanClang,
                        lld = konanLld,
                        archiver = konanAr,
                        targetTriple = triple,
                        sysrootDir = mingwSysroot,
                        jniIncludeDir = jniInc,
                        jniMdIncludeDir = jniMdInc,
                        cmakeExecutable = cmakeExe
                    )
                }
                val pathCompiler = findExecutableOnPath("clang.exe", "gcc.exe")
                    ?: throw GradleException(
                        "No host C compiler found on Windows (checked Konan LLVM+MinGW under ${depsDir.absolutePath} and PATH)."
                    )
                val pathCxx = findExecutableOnPath("clang++.exe", "g++.exe") ?: pathCompiler
                return GenericUiHostToolchain(
                    osName = os,
                    archName = arch,
                    osArchTag = "$os-$arch",
                    clang = pathCompiler,
                    clangPlusPlus = pathCxx,
                    lld = null,
                    archiver = findExecutableOnPath("llvm-ar.exe", "ar.exe"),
                    targetTriple = null,
                    sysrootDir = mingwSysroot,
                    jniIncludeDir = jniInc,
                    jniMdIncludeDir = jniMdInc,
                    cmakeExecutable = cmakeExe
                )
            }
            "macos" -> {
                val compiler = konanClang ?: findExecutableOnPath("clang", "cc")
                    ?: File("/usr/bin/clang").takeIf { it.isFile }
                    ?: throw GradleException("No C compiler (clang) found on macOS.")
                val cxx = konanClangPlusPlus ?: findExecutableOnPath("clang++", "c++")
                    ?: File("/usr/bin/clang++").takeIf { it.isFile }
                    ?: compiler
                val sdkPath = runProcessOutputOrNull(listOf("xcrun", "--sdk", "macosx", "--show-sdk-path"))
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && File(it).isDirectory }
                    ?.let { File(it) }
                val triple = if (arch == "aarch64") "arm64-apple-macos" else "x86_64-apple-macos"
                return GenericUiHostToolchain(
                    osName = os,
                    archName = arch,
                    osArchTag = "$os-$arch",
                    clang = compiler,
                    clangPlusPlus = cxx,
                    lld = null,
                    archiver = konanAr ?: findExecutableOnPath("ar"),
                    targetTriple = triple,
                    sysrootDir = sdkPath,
                    jniIncludeDir = jniInc,
                    jniMdIncludeDir = jniMdInc,
                    cmakeExecutable = cmakeExe
                )
            }
            else -> {
                val linuxSysroot = resolveKonanLinuxSysroot(depsDir, konanProps, arch)
                if (konanClang != null && linuxSysroot != null) {
                    val triple = if (arch == "aarch64") "aarch64-unknown-linux-gnu" else "x86_64-unknown-linux-gnu"
                    return GenericUiHostToolchain(
                        osName = os,
                        archName = arch,
                        osArchTag = "$os-$arch",
                        clang = konanClang,
                        clangPlusPlus = konanClangPlusPlus ?: konanClang,
                        lld = konanLld,
                        archiver = konanAr,
                        targetTriple = triple,
                        sysrootDir = linuxSysroot,
                        jniIncludeDir = jniInc,
                        jniMdIncludeDir = jniMdInc,
                        cmakeExecutable = cmakeExe
                    )
                }
                val compiler = konanClang ?: findExecutableOnPath("clang", "gcc", "cc")
                    ?: throw GradleException("No C compiler (clang or gcc) found on Linux.")
                val cxx = konanClangPlusPlus ?: findExecutableOnPath("clang++", "g++", "c++") ?: compiler
                return GenericUiHostToolchain(
                    osName = os,
                    archName = arch,
                    osArchTag = "$os-$arch",
                    clang = compiler,
                    clangPlusPlus = cxx,
                    lld = konanLld,
                    archiver = konanAr ?: findExecutableOnPath("ar"),
                    targetTriple = null,
                    sysrootDir = null,
                    jniIncludeDir = jniInc,
                    jniMdIncludeDir = jniMdInc,
                    cmakeExecutable = cmakeExe
                )
            }
        }
    }

    /**
     * Resolves `(jniIncludeDir, jniMdIncludeDir)` containing `<jni.h>` and `<jni_md.h>`, scanning
     * `$JAVA_HOME`, `java.home`, Gradle JDK toolchain caches, standard OS JDK directories, and
     * falling back to bundled OpenJDK-compatible JNI headers under `:gradle-plugin/src/main/resources/`.
     */
    fun resolveJniHeaderDirs(gradleUserHomeDir: File): Pair<File, File> {
        val mdSubdir = when (normalizedHostOs()) {
            "windows" -> "win32"
            "macos" -> "darwin"
            else -> "linux"
        }

        val candidates = LinkedHashSet<File>()
        fun addJdkHomeCandidate(home: File?) {
            if (home == null || !home.isDirectory) return
            candidates.add(File(home, "include"))
            home.parentFile?.let { candidates.add(File(it, "include")) }
        }

        System.getenv("JAVA_HOME")?.trim()?.takeIf { it.isNotEmpty() }?.let { addJdkHomeCandidate(File(it)) }
        System.getProperty("java.home")?.trim()?.takeIf { it.isNotEmpty() }?.let { addJdkHomeCandidate(File(it)) }

        val userHome = System.getProperty("user.home").orEmpty()
        val scanRoots = buildList {
            add(File(gradleUserHomeDir, "jdks"))
            if (userHome.isNotEmpty()) {
                add(File(userHome, ".jdks"))
                add(File(userHome, ".sdkman/candidates/java"))
            }
            if (normalizedHostOs() == "windows") {
                add(File("C:/Program Files/Java"))
                add(File("C:/Program Files/Eclipse Adoptium"))
                add(File("C:/Program Files/Microsoft"))
                add(File("C:/Program Files/Zulu"))
                add(File("C:/Program Files/BellSoft"))
            } else if (normalizedHostOs() == "macos") {
                add(File("/Library/Java/JavaVirtualMachines"))
            } else {
                add(File("/usr/lib/jvm"))
                add(File("/usr/java"))
            }
        }

        for (root in scanRoots) {
            if (!root.isDirectory) continue
            root.listFiles()?.sortedByDescending { it.name }?.forEach { entry ->
                if (!entry.isDirectory) return@forEach
                addJdkHomeCandidate(entry)
                val macHome = File(entry, "Contents/Home")
                if (macHome.isDirectory) {
                    addJdkHomeCandidate(macHome)
                }
            }
        }

        for (incDir in candidates) {
            val jniH = File(incDir, "jni.h")
            val mdDir = File(incDir, mdSubdir)
            val jniMdH = File(mdDir, "jni_md.h")
            if (jniH.isFile && jniMdH.isFile) {
                return incDir to mdDir
            }
        }

        return extractBundledJniHeaders(
            File(gradleUserHomeDir, "caches/genericui-jni-headers/$BUNDLED_JNI_HEADERS_VERSION")
        )
    }

    /**
     * Extracts the plugin's bundled OpenJDK-compatible `jni.h` and `<platform>/jni_md.h` into
     * [targetRootDir] and returns `(jniIncludeDir, jniMdIncludeDir)` for the current host OS.
     */
    fun extractBundledJniHeaders(targetRootDir: File): Pair<File, File> {
        val mdSubdir = when (normalizedHostOs()) {
            "windows" -> "win32"
            "macos" -> "darwin"
            else -> "linux"
        }
        val marker = File(targetRootDir, ".ready")
        val lockFile = File(targetRootDir.parentFile ?: targetRootDir, "${targetRootDir.name}.lock")
        lockFile.parentFile?.mkdirs()

        val relResources = listOf(
            "jni.h",
            "win32/jni_md.h",
            "linux/jni_md.h",
            "darwin/jni_md.h"
        )

        RandomAccessFile(lockFile, "rw").use { raf ->
            raf.channel.lock().use {
                val allPresent = marker.isFile && relResources.all { File(targetRootDir, it).isFile }
                if (!allPresent) {
                    targetRootDir.mkdirs()
                    for (rel in relResources) {
                        val resourcePath = "/genericui-jni-headers/$rel"
                        val stream = GenericUiJniToolchainResolver::class.java.getResourceAsStream(resourcePath)
                            ?: throw GradleException("Missing bundled JNI header resource: $resourcePath")
                        val dest = File(targetRootDir, rel)
                        dest.parentFile?.mkdirs()
                        stream.use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    marker.writeText("$BUNDLED_JNI_HEADERS_VERSION\n", StandardCharsets.UTF_8)
                }
            }
        }

        return targetRootDir to File(targetRootDir, mdSubdir)
    }

    /**
     * Resolves an existing Konan dependency under [depsDir] matching [depName] (or `[fallbackPrefix]*[fallbackSuffix]`),
     * downloading and caching `.zip` / `.tar.gz` from [dependenciesUrl] if missing and [allowNetworkDownload] is true.
     */
    internal fun ensureKonanDependencyDir(
        depsDir: File,
        depName: String,
        fallbackPrefix: String,
        fallbackSuffix: String,
        dependenciesUrl: String,
        allowNetworkDownload: Boolean
    ): File? {
        val exactDir = File(depsDir, depName)
        if (isPopulatedDepDir(exactDir)) {
            return exactDir
        }
        val existingMatch = depsDir.listFiles()
            ?.filter {
                it.isDirectory &&
                    it.name.startsWith(fallbackPrefix) &&
                    it.name.endsWith(fallbackSuffix) &&
                    isPopulatedDepDir(it)
            }
            ?.sortedByDescending { it.name }
            ?.firstOrNull()
        if (existingMatch != null) {
            return existingMatch
        }
        if (!allowNetworkDownload) {
            return null
        }

        val cacheDir = File(depsDir, "cache").apply { mkdirs() }
        val lockFile = File(cacheDir, "$depName.genericui.lock")
        val preferredExts = if (normalizedHostOs() == "windows") {
            listOf("zip", "tar.gz")
        } else {
            listOf("tar.gz", "zip")
        }
        val baseUrl = dependenciesUrl.trimEnd('/')

        RandomAccessFile(lockFile, "rw").use { raf ->
            raf.channel.lock().use {
                if (isPopulatedDepDir(exactDir)) {
                    return exactDir
                }
                var lastErr: Exception? = null
                for (ext in preferredExts) {
                    val archiveFile = File(cacheDir, "$depName.$ext")
                    val url = "$baseUrl/$depName.$ext"
                    try {
                        if (!archiveFile.isFile || archiveFile.length() == 0L) {
                            GenericUiArchiveExtractor.downloadFile(url, archiveFile)
                        }
                        val staging = File(depsDir, "$depName.tmp.${ProcessHandle.current().pid()}")
                        if (staging.exists()) {
                            staging.deleteRecursively()
                        }
                        staging.mkdirs()
                        try {
                            GenericUiArchiveExtractor.extractArchive(archiveFile, staging)
                            val nestedRoot = File(staging, depName)
                            val sourceTree = if (nestedRoot.isDirectory) nestedRoot else staging
                            if (exactDir.exists()) {
                                exactDir.deleteRecursively()
                            }
                            Files.move(
                                sourceTree.toPath(),
                                exactDir.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        } finally {
                            if (staging.exists()) {
                                staging.deleteRecursively()
                            }
                        }
                        if (isPopulatedDepDir(exactDir)) {
                            return exactDir
                        }
                    } catch (e: Exception) {
                        lastErr = e
                        if (archiveFile.exists()) {
                            archiveFile.delete()
                        }
                    }
                }
                if (lastErr != null) {
                    throw GradleException(
                        "Failed to download/extract Konan Android dependency '$depName' from '$baseUrl': ${lastErr.message}",
                        lastErr
                    )
                }
            }
        }
        return exactDir.takeIf { isPopulatedDepDir(it) }
    }

    private fun isPopulatedDepDir(dir: File): Boolean =
        dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)

    internal fun findInstalledAndroidNdk(projectDir: File): File? {
        val envNdks = listOfNotNull(
            System.getenv("ANDROID_NDK_HOME")?.trim()?.takeIf { it.isNotEmpty() },
            System.getenv("ANDROID_NDK_ROOT")?.trim()?.takeIf { it.isNotEmpty() },
            System.getenv("NDK_HOME")?.trim()?.takeIf { it.isNotEmpty() }
        ).map { File(it) }
        envNdks.firstOrNull { isValidNdkDir(it) }?.let { return it }

        readLocalProperty(projectDir, "ndk.dir")?.let { ndkPath ->
            val f = File(ndkPath)
            if (isValidNdkDir(f)) return f
        }

        val sdkDirs = findCandidateAndroidSdkDirs(projectDir)
        for (sdkDir in sdkDirs) {
            val ndkParent = File(sdkDir, "ndk")
            if (ndkParent.isDirectory) {
                val highest = ndkParent.listFiles()
                    ?.filter { isValidNdkDir(it) }
                    ?.sortedWith { a, b -> compareVersionStrings(b.name, a.name) }
                    ?.firstOrNull()
                if (highest != null) return highest
            }
            val bundle = File(sdkDir, "ndk-bundle")
            if (isValidNdkDir(bundle)) return bundle
        }
        return null
    }

    private fun isValidNdkDir(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val hostTag = resolveNdkHostPrebuiltTag()
        return File(dir, "toolchains/llvm/prebuilt/$hostTag/sysroot").isDirectory
    }

    private fun findCandidateAndroidSdkDirs(projectDir: File): List<File> {
        val dirs = LinkedHashSet<File>()
        listOfNotNull(
            System.getenv("ANDROID_HOME")?.trim()?.takeIf { it.isNotEmpty() },
            System.getenv("ANDROID_SDK_ROOT")?.trim()?.takeIf { it.isNotEmpty() }
        ).forEach { dirs.add(File(it)) }
        readLocalProperty(projectDir, "sdk.dir")?.let { dirs.add(File(it)) }

        val userHome = System.getProperty("user.home").orEmpty()
        val localAppData = System.getenv("LOCALAPPDATA").orEmpty()
        if (localAppData.isNotEmpty()) dirs.add(File(localAppData, "Android/Sdk"))
        if (userHome.isNotEmpty()) {
            dirs.add(File(userHome, "AppData/Local/Android/Sdk"))
            dirs.add(File(userHome, "Library/Android/sdk"))
            dirs.add(File(userHome, "Android/Sdk"))
        }
        dirs.add(File("/opt/android-sdk"))
        dirs.add(File("/usr/lib/android-sdk"))
        return dirs.filter { it.isDirectory }
    }

    private fun findCmakeExecutable(projectDir: File): File? {
        val exeName = if (normalizedHostOs() == "windows") "cmake.exe" else "cmake"
        for (sdkDir in findCandidateAndroidSdkDirs(projectDir)) {
            val cmakeParent = File(sdkDir, "cmake")
            if (cmakeParent.isDirectory) {
                val found = cmakeParent.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedWith { a, b -> compareVersionStrings(b.name, a.name) }
                    ?.map { File(it, "bin/$exeName") }
                    ?.firstOrNull { it.isFile }
                if (found != null) return found
            }
        }
        return findExecutableOnPath(exeName)
    }

    private fun readLocalProperty(projectDir: File, key: String): String? {
        val candidates = listOfNotNull(
            File(projectDir, "local.properties"),
            projectDir.parentFile?.let { File(it, "local.properties") },
            projectDir.parentFile?.parentFile?.let { File(it, "local.properties") }
        )
        for (file in candidates) {
            if (!file.isFile) continue
            val props = Properties()
            runCatching { FileInputStream(file).use { props.load(it) } }
            val v = props.getProperty(key)?.trim()
            if (!v.isNullOrEmpty()) return v
        }
        return null
    }

    private fun resolveKonanDataDir(explicitDir: File?): File {
        if (explicitDir != null && explicitDir.isDirectory) return explicitDir
        System.getenv("KONAN_DATA_DIR")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return File(it)
        }
        return File(System.getProperty("user.home"), ".konan")
    }

    private fun resolveKonanHomeDir(explicitDir: File?, konanDataDir: File): File? {
        if (explicitDir != null && File(explicitDir, "konan/konan.properties").isFile) {
            return explicitDir
        }
        return konanDataDir.listFiles()
            ?.filter {
                it.isDirectory &&
                    it.name.startsWith("kotlin-native-prebuilt-") &&
                    File(it, "konan/konan.properties").isFile
            }
            ?.sortedWith { a, b -> compareVersionStrings(b.name, a.name) }
            ?.firstOrNull()
    }

    private fun loadKonanProperties(konanHomeDir: File): Properties {
        val propsFile = File(konanHomeDir, "konan/konan.properties")
        return Properties().apply {
            if (propsFile.isFile) {
                propsFile.bufferedReader(StandardCharsets.UTF_8).use { load(it) }
            }
        }
    }

    private fun resolveKonanLlvmDir(
        depsDir: File,
        konanProps: Properties,
        hostKonan: String
    ): File? {
        val configured = resolveKonanProperty(konanProps, "llvmHome.$hostKonan")
        if (configured != null) {
            val dir = File(depsDir, configured)
            if (dir.isDirectory) return dir
        }
        val exeSuffix = if (normalizedHostOs() == "windows") ".exe" else ""
        return depsDir.listFiles()
            ?.filter {
                it.isDirectory &&
                    it.name.startsWith("llvm-") &&
                    File(it, "bin/clang$exeSuffix").isFile
            }
            ?.sortedWith { a, b -> compareVersionStrings(b.name, a.name) }
            ?.firstOrNull()
    }

    private fun resolveKonanLinuxSysroot(
        depsDir: File,
        konanProps: Properties,
        arch: String
    ): File? {
        val konanTarget = if (arch == "aarch64") "linux_arm64" else "linux_x64"
        val rel = resolveKonanProperty(konanProps, "targetSysRoot.$konanTarget")
        if (rel != null) {
            val f = File(depsDir, rel)
            if (f.isDirectory) return f
        }
        val prefix = if (arch == "aarch64") "aarch64-unknown-linux-gnu-gcc-" else "x86_64-unknown-linux-gnu-gcc-"
        val triplet = if (arch == "aarch64") "aarch64-unknown-linux-gnu" else "x86_64-unknown-linux-gnu"
        return depsDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(prefix) }
            ?.sortedWith { a, b -> compareVersionStrings(b.name, a.name) }
            ?.map { File(it, "$triplet/sysroot") }
            ?.firstOrNull { it.isDirectory }
    }

    private fun resolveKonanProperty(props: Properties, key: String, depth: Int = 0): String? {
        if (depth > 16) return null
        val raw = props.getProperty(key)?.trim() ?: return null
        val regex = Regex("\\$([A-Za-z0-9_.-]+)")
        return regex.replace(raw) { match ->
            resolveKonanProperty(props, match.groupValues[1], depth + 1) ?: match.value
        }
    }

    private fun hostKonanTargetName(): String = when (normalizedHostOs()) {
        "windows" -> "mingw_x64"
        "macos" -> if (normalizedHostArch() == "aarch64") "macos_arm64" else "macos_x64"
        "linux" -> if (normalizedHostArch() == "aarch64") "linux_arm64" else "linux_x64"
        else -> "linux_x64"
    }

    private fun defaultAndroidToolchainDepName(): String = when (normalizedHostOs()) {
        "windows" -> "target-toolchain-2-windows-android_ndk"
        "macos" -> "target-toolchain-2-osx-android_ndk"
        else -> "target-toolchain-2-linux-android_ndk"
    }

    private fun resolveNdkHostPrebuiltTag(): String = when (normalizedHostOs()) {
        "windows" -> "windows-x86_64"
        "macos" -> if (normalizedHostArch() == "aarch64") "darwin-arm64" else "darwin-x86_64"
        else -> "linux-x86_64"
    }

    private fun findExecutableOnPath(vararg names: String): File? {
        val pathEnv = System.getenv("PATH").orEmpty()
        for (dirPath in pathEnv.split(File.pathSeparatorChar)) {
            if (dirPath.isBlank()) continue
            val dir = File(dirPath)
            if (!dir.isDirectory) continue
            for (name in names) {
                val candidate = File(dir, name)
                if (candidate.isFile) return candidate
            }
        }
        return null
    }

    private fun runProcessOutputOrNull(command: List<String>): String? = runCatching {
        val proc = ProcessBuilder(command).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        if (proc.waitFor() == 0) out else null
    }.getOrNull()

    private fun compareVersionStrings(a: String, b: String): Int {
        val aParts = a.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val bParts = b.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val av = aParts.getOrElse(i) { 0 }
            val bv = bParts.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return a.compareTo(b)
    }
}
