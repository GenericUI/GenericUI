package com.nexustools.genericui.plugin.nativebuild

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Path

/**
 * Gradle task (`compileGenericUiJniLibraries`) that resolves Android and host C/C++ toolchains,
 * compiles consumer C/C++ JNI libraries and optional CMake targets with content-addressed per-file
 * `.o` caching, links 16KB-page-aligned Android `lib<name>.so` binaries and host JVM/Swing shared
 * libraries, and emits a bundle manifest for Swing JAR packaging.
 */
abstract class GenericUiCompileJniTask : DefaultTask() {

    @get:Input
    abstract val projectRootPath: Property<String>

    @get:Input
    abstract val gradleUserHomePath: Property<String>

    @get:Input
    @get:Optional
    abstract val konanHomePath: Property<String>

    @get:Input
    @get:Optional
    abstract val konanDataDirPath: Property<String>

    @get:Input
    abstract val androidEnabled: Property<Boolean>

    @get:Input
    abstract val swingEnabled: Property<Boolean>

    @get:Input
    abstract val offlineMode: Property<Boolean>

    @get:Input
    abstract val libraries: ListProperty<GenericUiJniLibrarySpec>

    @get:Input
    abstract val cmakes: ListProperty<GenericUiJniCmakeSpec>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourcesManifestFile: RegularFileProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputSourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val androidOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val hostOutputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val hostBundleManifestFile: RegularFileProperty

    @TaskAction
    fun compileAll() {
        val androidOutRoot = androidOutputDirectory.get().asFile.apply { mkdirs() }
        val hostOutRoot = hostOutputDirectory.get().asFile.apply { mkdirs() }
        val manifestOut = hostBundleManifestFile.get().asFile
        manifestOut.parentFile?.mkdirs()

        val libSpecs = libraries.getOrElse(emptyList())
        val cmakeSpecs = cmakes.getOrElse(emptyList())
        if (libSpecs.isEmpty() && cmakeSpecs.isEmpty()) {
            manifestOut.writeText("", StandardCharsets.UTF_8)
            return
        }

        val projectDir = File(projectRootPath.get())
        val buildDir = project.layout.buildDirectory.asFile.get()
        val gradleUserHomeDir = File(gradleUserHomePath.get())
        val konanHomeDir = konanHomePath.orNull?.takeIf { it.isNotBlank() }?.let { File(it) }
        val konanDataDir = konanDataDirPath.orNull?.takeIf { it.isNotBlank() }?.let { File(it) }
        val allowDownload = !(offlineMode.orNull ?: false)
        val isAndroidTargetActive = androidEnabled.orNull ?: false
        val isSwingTargetActive = swingEnabled.orNull ?: false

        val fetchedSourceRoots = parseSourcesManifest(sourcesManifestFile.orNull?.asFile)

        val needsAndroid = isAndroidTargetActive && (
            libSpecs.any { it.buildAndroidLibraries } || cmakeSpecs.any { it.buildAndroidLibraries }
            )
        val needsHost = libSpecs.any {
            it.buildHostLibraryForTests || (isSwingTargetActive && it.bundleInSwingJar)
        } || cmakeSpecs.any {
            it.buildHostLibraryForTests || (isSwingTargetActive && it.bundleInSwingJar)
        }

        val androidToolchain = if (needsAndroid) {
            GenericUiJniToolchainResolver.resolveAndroidToolchain(
                projectDir = projectDir,
                gradleUserHomeDir = gradleUserHomeDir,
                konanHomeDir = konanHomeDir,
                konanDataDir = konanDataDir,
                allowNetworkDownload = allowDownload
            )
        } else {
            null
        }

        val hostToolchain = if (needsHost) {
            GenericUiJniToolchainResolver.resolveHostToolchain(
                projectDir = projectDir,
                gradleUserHomeDir = gradleUserHomeDir,
                konanHomeDir = konanHomeDir,
                konanDataDir = konanDataDir
            )
        } else {
            null
        }

        val bundleManifestEntries = LinkedHashSet<String>()
        val expectedAndroidOutputs = HashSet<String>()
        val expectedHostOutputs = HashSet<String>()

        for (lib in libSpecs) {
            val safeName = sanitizeName(lib.name)
            val archiveRoot = fetchedSourceRoots[lib.name]
            val resolvedSources = resolveLibrarySources(projectDir, archiveRoot, lib)
            if (resolvedSources.isEmpty()) {
                throw GradleException(
                    "JNI library '${lib.name}' matched zero C/C++ source files " +
                        "(sources=${lib.sources}, archiveSources=${lib.archiveSources})."
                )
            }

            val cFiles = resolvedSources.filter { !isCppSource(it) }
            val cppFiles = resolvedSources.filter { isCppSource(it) }
            val hasCpp = cppFiles.isNotEmpty()

            val includeDirs = resolveLibraryIncludeDirs(projectDir, archiveRoot, lib).toMutableList()
            if (lib.generateJniHeaders) {
                val generatedHeaderDir = generateOptionalJniHeaders(
                    projectDir = projectDir,
                    buildDir = buildDir,
                    safeLibName = safeName,
                    lib = lib
                )
                if (generatedHeaderDir != null && generatedHeaderDir !in includeDirs) {
                    includeDirs.add(0, generatedHeaderDir)
                }
            }

            val defineFlags = buildDefineFlags(lib.defines)

            if (isAndroidTargetActive && lib.buildAndroidLibraries && androidToolchain != null) {
                val soName = GenericUiJniToolchainResolver.mapAndroidLibraryName(lib.name)
                for (abi in lib.abis) {
                    val objDir = File(buildDir, "intermediates/genericui_jni/$safeName/android-$abi/obj").apply {
                        mkdirs()
                    }
                    val abiOutDir = File(androidOutRoot, abi).apply { mkdirs() }
                    val outSo = File(abiOutDir, soName)
                    expectedAndroidOutputs.add("$abi/$soName")

                    val objFiles = mutableListOf<File>()
                    for (src in resolvedSources) {
                        val isCpp = isCppSource(src)
                        val compiler = if (isCpp) androidToolchain.clangPlusPlus else androidToolchain.clang
                        val compileArgs = buildList {
                            addAll(androidToolchain.buildCompileArgs(abi, lib.minApiLevel, isCpp))
                            add("-O2")
                            add("-std=${if (isCpp) lib.cppStandard else lib.cStandard}")
                            includeDirs.forEach { add("-I${it.absolutePath}") }
                            addAll(defineFlags)
                            addAll(if (isCpp) lib.cppFlags else lib.cFlags)
                        }
                        objFiles.add(
                            compileTranslationUnitIncremental(
                                compiler = compiler,
                                compileArgs = compileArgs,
                                sourceFile = src,
                                objDir = objDir
                            )
                        )
                    }

                    val linkCmd = androidToolchain.buildLinkCommand(
                        abi = abi,
                        minApiLevel = lib.minApiLevel,
                        soName = soName,
                        pageSize16Kb = lib.pageSize16Kb,
                        hasCpp = hasCpp,
                        objectFiles = objFiles,
                        userLinkerFlags = lib.linkerFlags,
                        userLinkLibraries = lib.androidLinkLibraries,
                        outputSo = outSo
                    )
                    linkIncremental(linkCmd, objFiles, outSo, objDir, "Android $abi ${lib.name}")
                }
            }

            val shouldBuildHost = lib.buildHostLibraryForTests || (isSwingTargetActive && lib.bundleInSwingJar)
            if (shouldBuildHost && hostToolchain != null) {
                val mappedName = GenericUiJniToolchainResolver.mapHostLibraryName(lib.name)
                val hostTag = hostToolchain.osArchTag
                val objDir = File(buildDir, "intermediates/genericui_jni/$safeName/host-$hostTag/obj").apply {
                    mkdirs()
                }
                val hostLibDir = File(hostOutRoot, hostTag).apply { mkdirs() }
                val outHostLib = File(hostLibDir, mappedName)
                expectedHostOutputs.add("$hostTag/$mappedName")

                val objFiles = mutableListOf<File>()
                for (src in resolvedSources) {
                    val isCpp = isCppSource(src)
                    val compiler = if (isCpp) hostToolchain.clangPlusPlus else hostToolchain.clang
                    val compileArgs = buildList {
                        addAll(hostToolchain.buildCompileArgs(isCpp))
                        add("-O2")
                        add("-std=${if (isCpp) lib.cppStandard else lib.cStandard}")
                        includeDirs.forEach { add("-I${it.absolutePath}") }
                        addAll(defineFlags)
                        addAll(if (isCpp) lib.cppFlags else lib.cFlags)
                    }
                    objFiles.add(
                        compileTranslationUnitIncremental(
                            compiler = compiler,
                            compileArgs = compileArgs,
                            sourceFile = src,
                            objDir = objDir
                        )
                    )
                }

                val linkCmd = hostToolchain.buildLinkCommand(
                    hasCpp = hasCpp,
                    objectFiles = objFiles,
                    userLinkerFlags = lib.linkerFlags,
                    userLinkLibraries = lib.hostLinkLibraries,
                    outputSharedLib = outHostLib
                )
                linkIncremental(linkCmd, objFiles, outHostLib, objDir, "Host $hostTag ${lib.name}")
                bundleManifestEntries.add("$hostTag\t${outHostLib.absolutePath}\t${lib.bundleInSwingJar}")
            }
        }

        for (cmakeSpec in cmakeSpecs) {
            compileCmakeSpec(
                projectDir = projectDir,
                buildDir = buildDir,
                cmakeSpec = cmakeSpec,
                isAndroidTargetActive = isAndroidTargetActive,
                isSwingTargetActive = isSwingTargetActive,
                androidToolchain = androidToolchain,
                hostToolchain = hostToolchain,
                androidOutRoot = androidOutRoot,
                hostOutRoot = hostOutRoot,
                expectedAndroidOutputs = expectedAndroidOutputs,
                expectedHostOutputs = expectedHostOutputs,
                bundleManifestEntries = bundleManifestEntries
            )
        }

        pruneStaleOutputs(androidOutRoot, expectedAndroidOutputs)
        pruneStaleOutputs(hostOutRoot, expectedHostOutputs)

        manifestOut.writeText(
            if (bundleManifestEntries.isEmpty()) "" else bundleManifestEntries.joinToString("\n") + "\n",
            StandardCharsets.UTF_8
        )
    }

    private fun compileCmakeSpec(
        projectDir: File,
        buildDir: File,
        cmakeSpec: GenericUiJniCmakeSpec,
        isAndroidTargetActive: Boolean,
        isSwingTargetActive: Boolean,
        androidToolchain: GenericUiAndroidToolchain?,
        hostToolchain: GenericUiHostToolchain?,
        androidOutRoot: File,
        hostOutRoot: File,
        expectedAndroidOutputs: MutableSet<String>,
        expectedHostOutputs: MutableSet<String>,
        bundleManifestEntries: MutableSet<String>
    ) {
        val rawLists = cmakeSpec.listsFile?.toString()?.trim()
            ?: throw GradleException("CMake target '${cmakeSpec.name}' requires listsFile(...) to be set.")
        val listsCandidate = File(rawLists).let { if (it.isAbsolute) it else File(projectDir, it.path) }
        val sourceDir = when {
            listsCandidate.isFile -> listsCandidate.parentFile
            listsCandidate.isDirectory && File(listsCandidate, "CMakeLists.txt").isFile -> listsCandidate
            else -> throw GradleException(
                "CMakeLists.txt not found for '${cmakeSpec.name}' at '${listsCandidate.absolutePath}'."
            )
        }
        val safeName = sanitizeName(cmakeSpec.name)

        if (isAndroidTargetActive && cmakeSpec.buildAndroidLibraries && androidToolchain != null) {
            val cmakeExe = androidToolchain.cmakeExecutable
                ?: throw GradleException(
                    "CMake target '${cmakeSpec.name}' requires 'cmake' to be installed in Android SDK or on PATH."
                )
            for (abi in cmakeSpec.abis) {
                val cmBuildDir = File(buildDir, "intermediates/genericui_jni/$safeName/cmake-android-$abi").apply {
                    mkdirs()
                }
                val configureCmd = buildList {
                    add(cmakeExe.absolutePath)
                    add("-S")
                    add(sourceDir.absolutePath)
                    add("-B")
                    add(cmBuildDir.absolutePath)
                    add("-DCMAKE_BUILD_TYPE=${cmakeSpec.buildType}")
                    if (androidToolchain.isFullNdk && androidToolchain.ndkRoot != null) {
                        add("-DCMAKE_TOOLCHAIN_FILE=${File(androidToolchain.ndkRoot, "build/cmake/android.toolchain.cmake").absolutePath}")
                        add("-DANDROID_ABI=$abi")
                        add("-DANDROID_PLATFORM=android-${cmakeSpec.minApiLevel}")
                    } else {
                        val toolchainFile = writeZeroNdkCmakeToolchainFile(
                            toolchain = androidToolchain,
                            abi = abi,
                            minApiLevel = cmakeSpec.minApiLevel,
                            pageSize16Kb = cmakeSpec.pageSize16Kb,
                            destFile = File(cmBuildDir, "genericui-android-$abi.cmake")
                        )
                        add("-DCMAKE_TOOLCHAIN_FILE=${toolchainFile.absolutePath}")
                    }
                    if (cmakeSpec.pageSize16Kb) {
                        add("-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
                    }
                    addAll(cmakeSpec.arguments)
                }
                runCheckedCommand(configureCmd, "CMake configure Android $abi (${cmakeSpec.name})")
                val buildCmd = buildList {
                    add(cmakeExe.absolutePath)
                    add("--build")
                    add(cmBuildDir.absolutePath)
                    if (cmakeSpec.targets.isNotEmpty()) {
                        add("--target")
                        addAll(cmakeSpec.targets)
                    }
                }
                runCheckedCommand(buildCmd, "CMake build Android $abi (${cmakeSpec.name})")

                val abiOutDir = File(androidOutRoot, abi).apply { mkdirs() }
                cmBuildDir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("so", ignoreCase = true) }
                    .forEach { soFile ->
                        val dest = File(abiOutDir, soFile.name)
                        soFile.copyTo(dest, overwrite = true)
                        expectedAndroidOutputs.add("$abi/${soFile.name}")
                    }
            }
        }

        val shouldBuildHost = cmakeSpec.buildHostLibraryForTests || (isSwingTargetActive && cmakeSpec.bundleInSwingJar)
        if (shouldBuildHost && hostToolchain != null) {
            val cmakeExe = hostToolchain.cmakeExecutable
                ?: throw GradleException(
                    "CMake target '${cmakeSpec.name}' requires 'cmake' to be installed in Android SDK or on PATH."
                )
            val hostTag = hostToolchain.osArchTag
            val cmBuildDir = File(buildDir, "intermediates/genericui_jni/$safeName/cmake-host-$hostTag").apply {
                mkdirs()
            }
            val configureCmd = buildList {
                add(cmakeExe.absolutePath)
                add("-S")
                add(sourceDir.absolutePath)
                add("-B")
                add(cmBuildDir.absolutePath)
                add("-DCMAKE_BUILD_TYPE=${cmakeSpec.buildType}")
                add("-DCMAKE_C_COMPILER=${hostToolchain.clang.absolutePath}")
                add("-DCMAKE_CXX_COMPILER=${hostToolchain.clangPlusPlus.absolutePath}")
                addAll(cmakeSpec.arguments)
            }
            runCheckedCommand(configureCmd, "CMake configure Host $hostTag (${cmakeSpec.name})")
            val buildCmd = buildList {
                add(cmakeExe.absolutePath)
                add("--build")
                add(cmBuildDir.absolutePath)
                if (cmakeSpec.targets.isNotEmpty()) {
                    add("--target")
                    addAll(cmakeSpec.targets)
                }
            }
            runCheckedCommand(buildCmd, "CMake build Host $hostTag (${cmakeSpec.name})")

            val sharedExt = when (hostToolchain.osName) {
                "windows" -> "dll"
                "macos" -> "dylib"
                else -> "so"
            }
            val hostOutDir = File(hostOutRoot, hostTag).apply { mkdirs() }
            cmBuildDir.walkTopDown()
                .filter { it.isFile && it.extension.equals(sharedExt, ignoreCase = true) }
                .forEach { libFile ->
                    val dest = File(hostOutDir, libFile.name)
                    libFile.copyTo(dest, overwrite = true)
                    expectedHostOutputs.add("$hostTag/${libFile.name}")
                    bundleManifestEntries.add("$hostTag\t${dest.absolutePath}\t${cmakeSpec.bundleInSwingJar}")
                }
        }
    }

    private fun writeZeroNdkCmakeToolchainFile(
        toolchain: GenericUiAndroidToolchain,
        abi: String,
        minApiLevel: Int,
        pageSize16Kb: Boolean,
        destFile: File
    ): File {
        val triple = toolchain.targetTriple(abi, minApiLevel)
        val archName = when (abi) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "i686"
            else -> abi
        }
        val linkerFlags = buildString {
            append("-fuse-ld=").append(toolchain.lld.invariantSeparatorsPath)
            if (pageSize16Kb) {
                append(" -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
            }
        }
        destFile.parentFile?.mkdirs()
        destFile.writeText(
            """
            set(CMAKE_SYSTEM_NAME Android)
            set(CMAKE_SYSTEM_VERSION ${minApiLevel.coerceAtLeast(21)})
            set(CMAKE_SYSTEM_PROCESSOR $archName)
            set(CMAKE_ANDROID_ARCH_ABI $abi)
            set(CMAKE_SYSROOT "${toolchain.sysrootDir.invariantSeparatorsPath}")
            set(CMAKE_C_COMPILER "${toolchain.clang.invariantSeparatorsPath}")
            set(CMAKE_CXX_COMPILER "${toolchain.clangPlusPlus.invariantSeparatorsPath}")
            set(CMAKE_C_COMPILER_TARGET "$triple")
            set(CMAKE_CXX_COMPILER_TARGET "$triple")
            set(CMAKE_AR "${toolchain.archiver.invariantSeparatorsPath}")
            set(CMAKE_SHARED_LINKER_FLAGS_INIT "$linkerFlags")
            set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8
        )
        return destFile
    }

    private fun compileTranslationUnitIncremental(
        compiler: File,
        compileArgs: List<String>,
        sourceFile: File,
        objDir: File
    ): File {
        val tuKey = GenericUiArchiveExtractor.sha256Hex(
            sourceFile.canonicalPath.toByteArray(StandardCharsets.UTF_8)
        ).take(8)
        val objFile = File(objDir, "${tuKey}_${sourceFile.name}.o")
        val depFile = File(objDir, "${tuKey}_${sourceFile.name}.d")
        val hashFile = File(objDir, "${tuKey}_${sourceFile.name}.hash")

        val preHash = computeCompileFingerprint(compiler, compileArgs, sourceFile, depFile)
        if (objFile.isFile &&
            objFile.length() > 0L &&
            hashFile.isFile &&
            runCatching { hashFile.readText(StandardCharsets.UTF_8) }.getOrNull() == preHash
        ) {
            return objFile
        }

        val cmd = buildList {
            add(compiler.absolutePath)
            addAll(compileArgs)
            add("-MMD")
            add("-MF")
            add(depFile.absolutePath)
            add("-c")
            add(sourceFile.absolutePath)
            add("-o")
            add(objFile.absolutePath)
        }
        runCheckedCommand(cmd, "Compile ${sourceFile.name}")

        val postHash = computeCompileFingerprint(compiler, compileArgs, sourceFile, depFile)
        hashFile.writeText(postHash, StandardCharsets.UTF_8)
        return objFile
    }

    private fun linkIncremental(
        linkCommand: List<String>,
        objectFiles: List<File>,
        outputSharedLib: File,
        objDir: File,
        stepLabel: String
    ) {
        val hashFile = File(objDir, "${outputSharedLib.name}.linkhash")
        val currentHash = computeLinkFingerprint(linkCommand, objectFiles)
        if (outputSharedLib.isFile &&
            outputSharedLib.length() > 0L &&
            hashFile.isFile &&
            runCatching { hashFile.readText(StandardCharsets.UTF_8) }.getOrNull() == currentHash
        ) {
            return
        }

        outputSharedLib.parentFile?.mkdirs()
        if (outputSharedLib.exists()) {
            outputSharedLib.delete()
        }
        runCheckedCommand(linkCommand, "Link $stepLabel")
        if (!outputSharedLib.isFile || outputSharedLib.length() == 0L) {
            throw GradleException("Linker succeeded but output library is missing: ${outputSharedLib.absolutePath}")
        }
        hashFile.writeText(currentHash, StandardCharsets.UTF_8)
    }

    private fun computeCompileFingerprint(
        compiler: File,
        compileArgs: List<String>,
        sourceFile: File,
        depFile: File
    ): String {
        val sb = StringBuilder()
        sb.append("compiler=").append(compiler.absolutePath)
            .append('|').append(compiler.length())
            .append('|').append(compiler.lastModified()).append('\n')
        for (arg in compileArgs) {
            sb.append("arg=").append(arg).append('\n')
        }
        sb.append("src=").append(sourceFile.canonicalPath)
            .append('|').append(GenericUiArchiveExtractor.sha256Hex(sourceFile)).append('\n')
        if (depFile.isFile) {
            for (hdr in parseDepfileHeaders(depFile)) {
                if (!hdr.isFile) {
                    sb.append("dep-missing=").append(hdr.path).append('\n')
                } else {
                    sb.append("dep=").append(hdr.canonicalPath)
                        .append('|').append(GenericUiArchiveExtractor.sha256Hex(hdr)).append('\n')
                }
            }
        }
        return GenericUiArchiveExtractor.sha256Hex(sb.toString().toByteArray(StandardCharsets.UTF_8))
    }

    private fun computeLinkFingerprint(
        linkCommand: List<String>,
        objectFiles: List<File>
    ): String {
        val sb = StringBuilder()
        for (arg in linkCommand) {
            sb.append("arg=").append(arg).append('\n')
        }
        for (obj in objectFiles) {
            sb.append("obj=").append(obj.canonicalPath)
                .append('|').append(GenericUiArchiveExtractor.sha256Hex(obj)).append('\n')
        }
        return GenericUiArchiveExtractor.sha256Hex(sb.toString().toByteArray(StandardCharsets.UTF_8))
    }

    private fun parseDepfileHeaders(depFile: File): List<File> {
        val raw = runCatching { depFile.readText(StandardCharsets.UTF_8) }.getOrNull().orEmpty()
        val unfolded = raw.replace("\\\r\n", " ").replace("\\\n", " ")
        val colonIdx = unfolded.indexOf(':')
        if (colonIdx < 0) return emptyList()
        val body = unfolded.substring(colonIdx + 1)
        val tokens = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < body.length) {
            val ch = body[i]
            if (ch == '\\' && i + 1 < body.length && body[i + 1] == ' ') {
                cur.append(' ')
                i += 2
                continue
            }
            if (ch.isWhitespace()) {
                if (cur.isNotEmpty()) {
                    tokens.add(cur.toString())
                    cur.clear()
                }
            } else {
                cur.append(ch)
            }
            i++
        }
        if (cur.isNotEmpty()) {
            tokens.add(cur.toString())
        }
        return tokens.drop(1).map { File(it) }.distinctBy { it.path }
    }

    private fun generateOptionalJniHeaders(
        projectDir: File,
        buildDir: File,
        safeLibName: String,
        lib: GenericUiJniLibrarySpec
    ): File? {
        val javaFiles = LinkedHashSet<File>()
        for (raw in lib.jniHeaderSources) {
            val f = File(raw.toString()).let { if (it.isAbsolute) it else File(projectDir, it.path) }
            if (f.isFile && f.extension.equals("java", ignoreCase = true)) {
                javaFiles.add(f)
            } else if (f.isDirectory) {
                f.walkTopDown().filter { it.isFile && it.extension.equals("java", ignoreCase = true) }
                    .forEach { javaFiles.add(it) }
            }
        }
        if (javaFiles.isEmpty()) {
            val defaultJavaDirs = listOf(
                File(projectDir, "src/commonMain/java"),
                File(projectDir, "src/androidMain/java"),
                File(projectDir, "src/jvmMain/java"),
                File(projectDir, "src/swingMain/java"),
                File(projectDir, "src/main/java")
            )
            val nativeRegex = Regex("\\bnative\\s+")
            for (dir in defaultJavaDirs) {
                if (!dir.isDirectory) continue
                dir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("java", ignoreCase = true) }
                    .filter { nativeRegex.containsMatchIn(it.readText(StandardCharsets.UTF_8)) }
                    .forEach { javaFiles.add(it) }
            }
        }
        if (javaFiles.isEmpty()) return null

        val javac = resolveJavacExecutable() ?: return null
        val headerOutDir = File(buildDir, "intermediates/genericui_jni/$safeLibName/jni-headers").apply { mkdirs() }
        val classOutDir = File(buildDir, "intermediates/genericui_jni/$safeLibName/jni-classes").apply { mkdirs() }
        val cmd = buildList {
            add(javac.absolutePath)
            add("-h")
            add(headerOutDir.absolutePath)
            add("-d")
            add(classOutDir.absolutePath)
            javaFiles.forEach { add(it.absolutePath) }
        }
        val proc = runCatching {
            ProcessBuilder(cmd).redirectErrorStream(true).start().also { p ->
                p.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
                p.waitFor()
            }
        }.getOrNull()
        val hasHeaders = headerOutDir.listFiles()?.any { it.isFile && it.extension.equals("h", ignoreCase = true) } == true
        return if (proc != null && hasHeaders) headerOutDir else null
    }

    private fun resolveLibrarySources(
        projectDir: File,
        archiveRoot: File?,
        lib: GenericUiJniLibrarySpec
    ): List<File> {
        val collected = LinkedHashMap<String, File>()
        val excludeMatchers = buildGlobMatchers(lib.excludes)

        for (raw in lib.sources) {
            for (file in matchPatternOrPath(projectDir, raw.toString())) {
                if (!isExcluded(file, listOf(projectDir), excludeMatchers)) {
                    collected.putIfAbsent(file.canonicalPath, file)
                }
            }
        }

        if (archiveRoot != null && archiveRoot.isDirectory) {
            val effectiveArchiveRoots = resolveEffectiveArchiveRoots(archiveRoot)
            for (pattern in lib.archiveSources) {
                var matchedAny = false
                for (root in effectiveArchiveRoots) {
                    val matches = matchPatternOrPath(root, pattern)
                    if (matches.isNotEmpty()) {
                        matchedAny = true
                        for (file in matches) {
                            if (!isExcluded(file, effectiveArchiveRoots, excludeMatchers)) {
                                collected.putIfAbsent(file.canonicalPath, file)
                            }
                        }
                        break
                    }
                }
                if (!matchedAny) {
                    logger.debug("archiveSources pattern '{}' matched no files under {}", pattern, archiveRoot)
                }
            }
        }

        return collected.values.sortedBy { it.invariantSeparatorsPath }
    }

    private fun resolveLibraryIncludeDirs(
        projectDir: File,
        archiveRoot: File?,
        lib: GenericUiJniLibrarySpec
    ): List<File> {
        val dirs = LinkedHashMap<String, File>()
        for (raw in lib.includeDirs) {
            val candidate = File(raw.toString()).let { if (it.isAbsolute) it else File(projectDir, it.path) }
            if (candidate.isDirectory) {
                dirs.putIfAbsent(candidate.canonicalPath, candidate)
            }
        }
        if (archiveRoot != null && archiveRoot.isDirectory) {
            val effectiveRoots = resolveEffectiveArchiveRoots(archiveRoot)
            for (relDir in lib.archiveIncludeDirs) {
                val normalized = relDir.trim().trimStart('/')
                if (normalized.isEmpty()) continue
                for (root in effectiveRoots) {
                    val candidate = if (normalized == ".") root else File(root, normalized)
                    if (candidate.isDirectory) {
                        dirs.putIfAbsent(candidate.canonicalPath, candidate)
                        break
                    }
                }
            }
        }
        return dirs.values.toList()
    }

    private fun resolveEffectiveArchiveRoots(archiveRoot: File): List<File> {
        val subdirs = archiveRoot.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            .orEmpty()
        val topFiles = archiveRoot.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") }
            .orEmpty()
        return if (subdirs.size == 1 && topFiles.isEmpty()) {
            listOf(archiveRoot, subdirs.first())
        } else {
            listOf(archiveRoot)
        }
    }

    private fun matchPatternOrPath(baseDir: File, rawPattern: String): List<File> {
        val trimmed = rawPattern.trim()
        if (trimmed.isEmpty()) return emptyList()
        val hasGlob = trimmed.contains('*') || trimmed.contains('?') || trimmed.contains('[')
        if (!hasGlob) {
            val candidate = File(trimmed).let { if (it.isAbsolute) it else File(baseDir, it.path) }
            if (candidate.isFile) {
                return if (isSupportedSourceFile(candidate)) listOf(candidate) else emptyList()
            }
            if (candidate.isDirectory) {
                return candidate.walkTopDown()
                    .filter { it.isFile && isSupportedSourceFile(it) }
                    .toList()
            }
            return emptyList()
        }

        val normalizedGlob = trimmed.replace('\\', '/').trimStart('/')
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$normalizedGlob")
        if (!baseDir.isDirectory) return emptyList()
        return baseDir.walkTopDown()
            .filter { it.isFile && isSupportedSourceFile(it) }
            .filter { file ->
                val rel = file.relativeTo(baseDir).invariantSeparatorsPath
                matcher.matches(Path.of(rel))
            }
            .toList()
    }

    private fun buildGlobMatchers(patterns: List<String>): List<java.nio.file.PathMatcher> {
        val fs = FileSystems.getDefault()
        return patterns.flatMap { raw ->
            val norm = raw.replace('\\', '/').trim().trimStart('/')
            if (norm.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    fs.getPathMatcher("glob:$norm"),
                    fs.getPathMatcher("glob:**/$norm")
                )
            }
        }
    }

    private fun isExcluded(
        file: File,
        baseDirs: List<File>,
        matchers: List<java.nio.file.PathMatcher>
    ): Boolean {
        if (matchers.isEmpty()) return false
        val namePath = Path.of(file.name)
        for (matcher in matchers) {
            if (matcher.matches(namePath)) return true
            for (base in baseDirs) {
                val rel = runCatching { file.relativeTo(base).invariantSeparatorsPath }.getOrNull() ?: continue
                if (!rel.startsWith("..") && matcher.matches(Path.of(rel))) {
                    return true
                }
            }
        }
        return false
    }

    private fun isSupportedSourceFile(file: File): Boolean =
        file.extension.lowercase() in SOURCE_EXTENSIONS

    private fun isCppSource(file: File): Boolean =
        file.extension.lowercase() in CPP_EXTENSIONS

    private fun buildDefineFlags(defines: Map<String, String?>): List<String> = buildList {
        for ((k, v) in defines) {
            if (v == null) add("-D$k") else add("-D$k=$v")
        }
    }

    private fun parseSourcesManifest(manifestFile: File?): Map<String, File> {
        if (manifestFile == null || !manifestFile.isFile) return emptyMap()
        val result = linkedMapOf<String, File>()
        for (line in manifestFile.readLines(StandardCharsets.UTF_8)) {
            val parts = line.split('\t', limit = 2)
            if (parts.size == 2) {
                val dir = File(parts[1].trim())
                if (dir.isDirectory) {
                    result[parts[0].trim()] = dir
                }
            }
        }
        return result
    }

    private fun pruneStaleOutputs(rootDir: File, expectedRelPaths: Set<String>) {
        if (!rootDir.isDirectory) return
        rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.relativeTo(rootDir).invariantSeparatorsPath
            if (rel !in expectedRelPaths) {
                file.delete()
            }
        }
    }

    private fun runCheckedCommand(command: List<String>, stepName: String) {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            throw GradleException(
                buildString {
                    appendLine("JNI native build step '$stepName' failed (exit code $exitCode):")
                    appendLine("  ${command.joinToString(" ")}")
                    if (out.isNotBlank()) {
                        appendLine(out.trimEnd())
                    }
                }
            )
        }
    }

    private fun resolveJavacExecutable(): File? {
        val exeName = if (GenericUiJniToolchainResolver.normalizedHostOs() == "windows") "javac.exe" else "javac"
        val javaHome = System.getProperty("java.home").orEmpty()
        if (javaHome.isNotEmpty()) {
            File(javaHome, "bin/$exeName").takeIf { it.isFile }?.let { return it }
        }
        System.getenv("JAVA_HOME")?.trim()?.takeIf { it.isNotEmpty() }?.let { envHome ->
            File(envHome, "bin/$exeName").takeIf { it.isFile }?.let { return it }
        }
        return null
    }

    private fun sanitizeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        private val SOURCE_EXTENSIONS = setOf("c", "cc", "cpp", "cxx")
        private val CPP_EXTENSIONS = setOf("cc", "cpp", "cxx")
    }
}
