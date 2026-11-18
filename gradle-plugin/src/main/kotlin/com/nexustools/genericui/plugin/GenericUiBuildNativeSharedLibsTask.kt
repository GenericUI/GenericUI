package com.nexustools.genericui.plugin

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipFile

/**
 * Compiles GenericUI core, backend (`macos`, `windows`, `gtk`, `qt`), `njs`, extension, DSL, and
 * test klibs into true dynamic shared libraries (`.dylib` / `.so` / `.dll`) plus Kotlin/Native
 * monolithic cache facades for a specific native desktop target and build mode.
 *
 * Outputs are cached across projects in `<gradleUserHome>/caches/genericui-native-shared/` keyed by
 * transitive klib content fingerprints, Konan compiler identity, and build flags.
 */
@DisableCachingByDefault(
    because = "Maintains a cross-project content-addressed shared library cache under Gradle user home"
)
abstract class GenericUiBuildNativeSharedLibsTask : DefaultTask() {

    @get:InputFiles
    @get:Classpath
    abstract val libraries: ConfigurableFileCollection

    @get:Input
    abstract val konanTargetName: Property<String>

    @get:Input
    abstract val backendVariant: Property<String>

    @get:Input
    abstract val optimized: Property<Boolean>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Input
    abstract val konanHomePath: Property<String>

    @get:Optional
    @get:Input
    abstract val konanDataDirPath: Property<String>

    @get:Input
    abstract val gradleUserHomePath: Property<String>

    @get:OutputDirectory
    abstract val sharedLibsOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val facadeCacheRootDir: DirectoryProperty

    @get:OutputFile
    abstract val cachedLibrariesManifestFile: RegularFileProperty

    @TaskAction
    fun buildSharedLibraries() {
        val target = konanTargetName.get()
        if (!GenericUiNativeSharedLibraryCatalog.isNativeDesktopKonanTarget(target)) {
            throw GradleException(
                "GenericUiBuildNativeSharedLibsTask invoked for non-desktop target '$target'"
            )
        }

        val variant = backendVariant.get()
        val isOpt = optimized.get()
        val isDbg = debuggable.get()
        val konanHomeDir = File(konanHomePath.get())
        val konanDataDir = konanDataDirPath.orNull?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: System.getenv("KONAN_DATA_DIR")?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: File(System.getProperty("user.home"), ".konan")

        val resolvedLibraryFiles = buildList {
            addAll(libraries.files.map { it.absoluteFile.normalize() })
            val stdlibDir = File(konanHomeDir, "klib/common/stdlib").absoluteFile.normalize()
            if (stdlibDir.isDirectory && none { it == stdlibDir }) {
                add(stdlibDir)
            }
        }
        val platformKlibsRoot = File(konanHomeDir, "klib/platform/$target").absoluteFile.normalize()
        val plan = GenericUiNativeSharedLibraryCatalog.planSharedLibraries(
            libraryFiles = resolvedLibraryFiles,
            konanTargetName = target,
            backendVariant = variant,
            platformKlibsRootDir = platformKlibsRoot
        )

        val konanProps = loadKonanProperties(konanHomeDir)
        val hostKonan = GenericUiKonanCompilerShim.hostKonanTargetName()
        val modeKey = "opt-$isOpt-dbg-$isDbg"
        val konanVersionKey = konanHomeDir.name
        val cacheRoot = File(
            gradleUserHomePath.get(),
            "caches/genericui-native-shared/$konanVersionKey/$target/$modeKey"
        )
        cacheRoot.mkdirs()

        val shimDir = File(gradleUserHomePath.get(), "caches/genericui-native-shared/shims/$konanVersionKey")
        val shimJar = GenericUiKonanCompilerShim.ensureShimJar(konanHomeDir, shimDir)
        val shimStampHash = sha256Hex(
            (GenericUiKonanCompilerShim.buildStamp(
                File(konanHomeDir, "konan/lib/kotlin-native-compiler-embeddable.jar")
            ) + "|$CACHE_SCHEMA_VERSION|$target|$modeKey").toByteArray(Charsets.UTF_8)
        )

        val klibByUniqueName = plan.cachedKlibsOrdered.associateBy { it.uniqueName }
        val klibHashes = plan.cachedKlibsOrdered.associate { klib ->
            klib.uniqueName to computeKlibFingerprint(klib.file, target)
        }
        val klibStageAFingerprints = mutableMapOf<String, String>()
        for (klib in plan.cachedKlibsOrdered) {
            val material = buildString {
                append(CACHE_SCHEMA_VERSION).append('|')
                append(shimStampHash).append('|')
                append(target).append('|')
                append(modeKey).append('|')
                append(klib.uniqueName).append('=').append(klibHashes.getValue(klib.uniqueName)).append('|')
                if (isStaticPlatformCacheKlib(klib.uniqueName, target)) {
                    append("platStaticV1|")
                }
                if (GenericUiNativeSharedLibraryCatalog.isLinuxKonanTarget(target) &&
                    klib.uniqueName == "com.nexustools.genericui:desktop"
                ) {
                    append("linuxVariant=").append(variant).append("|bridgeV2|")
                } else if (GenericUiNativeSharedLibraryCatalog.isLinuxKonanTarget(target) &&
                    klib.uniqueName == "com.nexustools.genericui:tests"
                ) {
                    append("linuxTestsVariant=").append(variant).append("|bridgeV2|")
                }
                for (dep in collectTransitiveCachedDependencies(klib, klibByUniqueName, klibStageAFingerprints)) {
                    append("dep:").append(dep.uniqueName).append('=').append(klibStageAFingerprints.getValue(dep.uniqueName)).append('|')
                }
            }
            klibStageAFingerprints[klib.uniqueName] = sha256Hex(material.toByteArray(Charsets.UTF_8)).take(24)
        }

        val groupFingerprints = mutableMapOf<String, String>()
        for (group in plan.groupsOrdered) {
            val material = buildString {
                append(CACHE_SCHEMA_VERSION).append('|')
                append(shimStampHash).append('|')
                append(target).append('|')
                append(modeKey).append('|')
                append(group.groupId).append('|')
                if (GenericUiNativeSharedLibraryCatalog.isLinuxKonanTarget(target) ||
                    GenericUiNativeSharedLibraryCatalog.isWindowsKonanTarget(target)
                ) {
                    append("platStaticGroupV1|")
                }
                if (GenericUiNativeSharedLibraryCatalog.isLinuxKonanTarget(target) &&
                    group.klibs.any { it.uniqueName == "com.nexustools.genericui:desktop" }
                ) {
                    append("linuxVariant=").append(variant).append('|')
                }
                for (up in group.upstreamGroupIds) {
                    append("up:").append(up).append('=').append(groupFingerprints.getValue(up)).append('|')
                }
                for (klib in group.klibs) {
                    append("klib:").append(klib.uniqueName).append('=').append(klibStageAFingerprints.getValue(klib.uniqueName)).append('|')
                }
            }
            groupFingerprints[group.groupId] = sha256Hex(material.toByteArray(Charsets.UTF_8)).take(24)
        }

        val sharedOutDir = sharedLibsOutputDir.get().asFile
        val facadeOutDir = facadeCacheRootDir.get().asFile
        cleanDirectory(sharedOutDir)
        cleanDirectory(facadeOutDir)

        val lockFile = File(cacheRoot, "build.lock")
        val facadeEntries = mutableListOf<Pair<File, File>>()

        RandomAccessFile(lockFile, "rw").use { raf ->
            raf.channel.lock().use {
                val cachedGroupDirs = mutableMapOf<String, File>()

                for (group in plan.groupsOrdered) {
                    val fp = groupFingerprints.getValue(group.groupId)
                    val persistentGroupDir = File(cacheRoot, "groups/${group.groupId}-$fp")
                    val completeMarker = File(persistentGroupDir, ".complete")

                    if (!completeMarker.isFile) {
                        buildGroupIntoCache(
                            group = group,
                            persistentGroupDir = persistentGroupDir,
                            cacheRoot = cacheRoot,
                            upstreamGroupDirs = cachedGroupDirs,
                            klibByUniqueName = klibByUniqueName,
                            klibStageAFingerprints = klibStageAFingerprints,
                            konanHomeDir = konanHomeDir,
                            konanDataDir = konanDataDir,
                            konanProps = konanProps,
                            hostKonan = hostKonan,
                            konanTarget = target,
                            backendVariant = variant,
                            optimized = isOpt,
                            debuggable = isDbg,
                            shimJar = shimJar
                        )
                    }
                    cachedGroupDirs[group.groupId] = persistentGroupDir

                    // Copy runtime shared library to task output dir (never copy .dll.a).
                    val cachedSharedLib = File(persistentGroupDir, "lib/${group.sharedLibraryFileName}")
                    if (!cachedSharedLib.isFile) {
                        throw GradleException(
                            "Expected cached shared library ${cachedSharedLib.absolutePath} is missing"
                        )
                    }
                    val targetSharedLib = File(sharedOutDir, group.sharedLibraryFileName)
                    if (targetSharedLib.exists()) {
                        targetSharedLib.delete()
                    }
                    cachedSharedLib.copyTo(targetSharedLib, overwrite = true)
                    if (!GenericUiNativeSharedLibraryCatalog.isWindowsKonanTarget(target)) {
                        targetSharedLib.setExecutable(true, false)
                    }
                    if (GenericUiNativeSharedLibraryCatalog.isMacOsKonanTarget(target)) {
                        runProcess(listOf("codesign", "--force", "--sign", "-", targetSharedLib.absolutePath))
                    }

                    // Populate task-local monolithic cache facades pointing at targetSharedLib
                    // (or static archive for Linux/Windows platform.* klibs).
                    for (klib in group.klibs) {
                        val facadeName = "${klib.uniqueName}-cache"
                        val cachedFacade = File(persistentGroupDir, "facades/${sanitizeFacadeDirName(klib.uniqueName)}")
                        val taskFacade = File(facadeOutDir, facadeName)
                        val taskFacadeBin = File(taskFacade, "bin")
                        taskFacadeBin.mkdirs()
                        copyFacadeSupportToCacheDir(cachedFacade, taskFacade)

                        if (isStaticPlatformCacheKlib(klib.uniqueName, target)) {
                            val staticArtifactName = "lib${klib.uniqueName}-cache.a"
                            val srcStatic = File(cachedFacade, staticArtifactName)
                            if (!srcStatic.isFile) {
                                throw GradleException(
                                    "Expected cached static platform archive ${srcStatic.absolutePath} is missing"
                                )
                            }
                            linkOrCopyArtifact(srcStatic, File(taskFacadeBin, staticArtifactName))
                        } else {
                            val artifactName = GenericUiNativeSharedLibraryCatalog.cacheArtifactFileName(
                                klib.uniqueName,
                                target
                            )
                            val artifactFile = File(taskFacadeBin, artifactName)
                            linkOrCopyArtifact(targetSharedLib, artifactFile)
                        }
                        facadeEntries.add(klib.file to taskFacade)
                    }
                }
            }
        }

        val manifestFile = cachedLibrariesManifestFile.get().asFile
        manifestFile.parentFile?.mkdirs()
        manifestFile.writeText(
            facadeEntries.joinToString("\n") { (klibFile, facadeDir) ->
                "${klibFile.absolutePath}\t${facadeDir.absolutePath}"
            } + if (facadeEntries.isEmpty()) "" else "\n",
            Charsets.UTF_8
        )
    }

    private fun buildGroupIntoCache(
        group: GenericUiNativeSharedLibraryCatalog.SharedLibraryGroupPlan,
        persistentGroupDir: File,
        cacheRoot: File,
        upstreamGroupDirs: Map<String, File>,
        klibByUniqueName: Map<String, GenericUiNativeSharedLibraryCatalog.KlibManifestInfo>,
        klibStageAFingerprints: Map<String, String>,
        konanHomeDir: File,
        konanDataDir: File,
        konanProps: Properties,
        hostKonan: String,
        konanTarget: String,
        backendVariant: String,
        optimized: Boolean,
        debuggable: Boolean,
        shimJar: File
    ) {
        val parentDir = persistentGroupDir.parentFile ?: error("Missing parent dir for $persistentGroupDir")
        parentDir.mkdirs()
        val stagingDir = File(parentDir, "${persistentGroupDir.name}.tmp.${ProcessHandle.current().pid()}")
        cleanDirectory(stagingDir)

        try {
            val stageADir = File(stagingDir, "stageA")
            val stageAFacadesRoot = File(stagingDir, "stageAFacades")
            val stageAObjectsDir = File(stagingDir, "objects")
            val extractedNativeDir = File(stagingDir, "nativeArchives")
            val weakExportsDir = File(stagingDir, "weakExports")
            val libDir = File(stagingDir, "lib")
            val implibDir = File(stagingDir, "implib")
            val facadesMetaDir = File(stagingDir, "facades")
            stageADir.mkdirs()
            stageAFacadesRoot.mkdirs()
            stageAObjectsDir.mkdirs()
            extractedNativeDir.mkdirs()
            weakExportsDir.mkdirs()
            libDir.mkdirs()
            implibDir.mkdirs()
            facadesMetaDir.mkdirs()

            // Pre-populate Stage A facades from already-built upstream groups so each klib compiles against
            // upstream dynamic_cache artifacts cleanly.
            val builtKlibStageAFacades = mutableMapOf<String, File>()
            val upstreamSharedLibs = mutableListOf<File>()
            val upstreamWindowsImplibs = mutableListOf<File>()
            val upstreamPlatformStaticArchives = mutableListOf<File>()
            val groupPlatformStaticArchives = mutableListOf<File>()
            for (upGroupId in group.upstreamGroupIds) {
                val upDir = upstreamGroupDirs.getValue(upGroupId)
                val upLibName = GenericUiNativeSharedLibraryCatalog.sharedLibraryFileName(upGroupId, konanTarget)
                val upSharedLib = File(upDir, "lib/$upLibName")
                upstreamSharedLibs.add(upSharedLib)
                if (GenericUiNativeSharedLibraryCatalog.isWindowsKonanTarget(konanTarget)) {
                    val upImplib = File(upDir, "implib/$upGroupId.dll.a")
                    upstreamWindowsImplibs.add(if (upImplib.isFile) upImplib else upSharedLib)
                }
                val upFacadesDir = File(upDir, "facades")
                upFacadesDir.listFiles()?.filter { it.isDirectory }?.forEach { metaDir ->
                    val uniqueNameFile = File(metaDir, "unique_name")
                    if (!uniqueNameFile.isFile) return@forEach
                    val uniqueName = uniqueNameFile.readText(Charsets.UTF_8).trim()
                    val stageFacade = File(stageAFacadesRoot, "$uniqueName-cache")
                    val binDir = File(stageFacade, "bin")
                    binDir.mkdirs()
                    copyFacadeSupportToCacheDir(metaDir, stageFacade)
                    if (isStaticPlatformCacheKlib(uniqueName, konanTarget)) {
                        val staticName = "lib$uniqueName-cache.a"
                        val staticArchive = File(metaDir, staticName)
                        if (!staticArchive.isFile) {
                            throw GradleException(
                                "Expected upstream static platform archive ${staticArchive.absolutePath} is missing"
                            )
                        }
                        linkOrCopyArtifact(staticArchive, File(binDir, staticName))
                        upstreamPlatformStaticArchives.add(staticArchive)
                    } else {
                        val artifactName = GenericUiNativeSharedLibraryCatalog.cacheArtifactFileName(
                            uniqueName,
                            konanTarget
                        )
                        linkOrCopyArtifact(upSharedLib, File(binDir, artifactName))
                    }
                    builtKlibStageAFacades[uniqueName] = stageFacade
                }
            }

            val groupObjectFiles = mutableListOf<File>()
            val groupStaticArchives = mutableListOf<File>()
            var weakExportsDefFile: File? = null
            val isWindows = GenericUiNativeSharedLibraryCatalog.isWindowsKonanTarget(konanTarget)
            val isLinux = GenericUiNativeSharedLibraryCatalog.isLinuxKonanTarget(konanTarget)
            val isMac = GenericUiNativeSharedLibraryCatalog.isMacOsKonanTarget(konanTarget)
            val isLinuxSingleBackendVariant = isLinux &&
                (backendVariant.equals("gtk", ignoreCase = true) || backendVariant.equals("qt", ignoreCase = true))
            val dependenciesDir = File(konanDataDir, "dependencies")
            val llvmHomeDir = resolveLlvmHomeDir(konanProps, dependenciesDir, hostKonan)

            val klibsCacheRoot = File(cacheRoot, "klibs")
            klibsCacheRoot.mkdirs()

            for ((klibIdx, klib) in group.klibs.withIndex()) {
                val sanitizedKlibName = sanitizeFacadeDirName(klib.uniqueName)
                val klibStageAFp = klibStageAFingerprints.getValue(klib.uniqueName)
                val persistentKlibDir = File(klibsCacheRoot, "$sanitizedKlibName-$klibStageAFp")
                val klibCompleteMarker = File(persistentKlibDir, ".complete")
                val cacheDirName = "${klib.uniqueName}-cache"
                val isStaticPlatform = isStaticPlatformCacheKlib(klib.uniqueName, konanTarget)
                val stageAArtifactName = if (isStaticPlatform) {
                    "lib${klib.uniqueName}-cache.a"
                } else {
                    GenericUiNativeSharedLibraryCatalog.cacheArtifactFileName(
                        klib.uniqueName,
                        konanTarget
                    )
                }

                if (!klibCompleteMarker.isFile) {
                    val klibStagingDir = File(klibsCacheRoot, "${persistentKlibDir.name}.tmp.${ProcessHandle.current().pid()}")
                    cleanDirectory(klibStagingDir)
                    try {
                        val objTmpDir = File(klibStagingDir, "workObjs")
                        val localStageADir = File(klibStagingDir, "stageAWork")
                        val stagedObjectsOut = File(klibStagingDir, "objects")
                        val stagedFacadeOut = File(klibStagingDir, "facade")
                        val stagedBinOut = File(klibStagingDir, "stageABin")
                        objTmpDir.mkdirs()
                        localStageADir.mkdirs()
                        stagedObjectsOut.mkdirs()
                        stagedFacadeOut.mkdirs()
                        stagedBinOut.mkdirs()

                        val transitiveDeps = collectTransitiveCachedDependencies(
                            rootKlib = klib,
                            klibByUniqueName = klibByUniqueName,
                            availableFacades = builtKlibStageAFacades
                        )

                        val linuxExcludedPkg = if (isLinuxSingleBackendVariant) {
                            linuxExcludedPackageForKlib(klib.uniqueName, backendVariant)
                        } else {
                            null
                        }
                        val isLinuxPerFilePartitioned = linuxExcludedPkg != null
                        val konancArgs = buildList {
                            add("-produce")
                            add(if (isStaticPlatform) "static_cache" else "dynamic_cache")
                            if (isLinuxPerFilePartitioned) {
                                add("-Xmake-per-file-cache")
                            }
                            add("-target")
                            add(konanTarget)
                            add("-Xadd-cache=${klib.file.absolutePath}")
                            add("-Xcache-directory=${localStageADir.absolutePath}")
                            add("-Xtemporary-files-dir=${objTmpDir.absolutePath}")
                            add("-Xoverride-konan-properties=${buildKonanOverrideArg(hostKonan, konanTarget, includeLinuxLinkerOverride = klib.uniqueName != "stdlib")}")

                            if (debuggable) {
                                add("-g")
                            }
                            if (optimized) {
                                add("-opt")
                                add("-Xbinary=enableReleaseBinaryCache=true")
                            }

                            for (dep in transitiveDeps) {
                                val depFacade = builtKlibStageAFacades.getValue(dep.uniqueName)
                                add("-Xcached-library=${dep.file.absolutePath},${depFacade.absolutePath}")
                                if (dep.uniqueName != "stdlib" &&
                                    !GenericUiNativeSharedLibraryCatalog.isPlatformKlibUniqueName(dep.uniqueName)
                                ) {
                                    add("-l")
                                    add(dep.file.absolutePath)
                                }
                            }

                            if (isWindows && !isLinuxPerFilePartitioned && !isStaticPlatform) {
                                add("-linker-option")
                                add("-Wl,--export-all-symbols")
                                add("-linker-option")
                                add("-Wl,--allow-multiple-definition")
                                add("-linker-option")
                                add("-Wl,--gc-sections")
                                for (winLib in WINDOWS_SYSTEM_LIBS) {
                                    add("-linker-option")
                                    add("-l$winLib")
                                }
                                val klibOpts = collectGroupAndPlatformLinkerOpts(
                                    groupKlibs = listOf(klib),
                                    konanHomeDir = konanHomeDir,
                                    konanTarget = konanTarget
                                )
                                for (opt in klibOpts) {
                                    add("-linker-option")
                                    add(opt)
                                }
                            }
                        }

                        GenericUiKonanCompilerShim.runKonanc(
                            konanHomeDir = konanHomeDir,
                            shimJar = shimJar,
                            konanDataDir = konanDataDir,
                            args = konancArgs
                        )

                        File(stagedFacadeOut, "unique_name").writeText(klib.uniqueName, Charsets.UTF_8)

                        if (isLinuxPerFilePartitioned) {
                            val perFileDir = File(localStageADir, "${klib.uniqueName}-per-file-cache")
                            if (!perFileDir.isDirectory) {
                                throw GradleException(
                                    "Expected per-file cache directory missing at ${perFileDir.absolutePath} " +
                                        "(available entries: ${localStageADir.list()?.joinToString()})"
                                )
                            }

                            val (keptDesktopObjs, excludedFileDirNames) = selectLinuxPerFileObjects(
                                objTmpDir = objTmpDir,
                                excludedPkg = linuxExcludedPkg!!,
                                klibUniqueName = klib.uniqueName,
                                backendVariant = backendVariant
                            )
                            val keptPerFileDirs = perFileDir.listFiles()
                                ?.filter { it.isDirectory && it.name !in excludedFileDirNames }
                                ?.sortedBy { it.name }
                                .orEmpty()
                            if (keptPerFileDirs.isEmpty()) {
                                throw GradleException(
                                    "No kept per-file cache directories in ${perFileDir.absolutePath} " +
                                        "for backendVariant='$backendVariant'"
                                )
                            }
                            val srcMetadata = File(perFileDir, "metadata.properties").takeIf { it.isFile }
                                ?: keptPerFileDirs.map { File(it, "metadata.properties") }.firstOrNull { it.isFile }
                                ?: throw GradleException(
                                    "Expected per-file metadata.properties missing under ${perFileDir.absolutePath}"
                                )
                            srcMetadata.copyTo(File(stagedFacadeOut, "metadata.properties"), overwrite = true)

                            val depLines = linkedSetOf<String>()
                            keptPerFileDirs.forEach { fileCacheDir ->
                                val depsFile = File(fileCacheDir, "bin/bitcode_deps")
                                if (!depsFile.isFile) return@forEach
                                depsFile.readLines(Charsets.UTF_8).forEach { rawLine ->
                                    val line = rawLine.trimEnd()
                                    if (line.isNotEmpty() && !line.startsWith("${klib.uniqueName}|")) {
                                        depLines.add(line)
                                    }
                                }
                            }
                            File(stagedFacadeOut, "bitcode_deps").writeText(
                                if (depLines.isEmpty()) "" else depLines.joinToString("\n") + "\n",
                                Charsets.UTF_8
                            )
                            mergePerFileIrFiles(
                                keptPerFileDirs = keptPerFileDirs,
                                destIrDir = File(stagedFacadeOut, "ir"),
                                konanHomeDir = konanHomeDir
                            )

                            val initBridgeObj = buildLinuxDesktopInitBridgeObject(
                                keptObjs = keptDesktopObjs,
                                workDir = objTmpDir,
                                llvmHomeDir = llvmHomeDir,
                                konanTarget = konanTarget,
                                desktopUniqueName = klib.uniqueName
                            )
                            (keptDesktopObjs + initBridgeObj).forEachIndexed { oIdx, objFile ->
                                val paddedIdx = oIdx.toString().padStart(4, '0')
                                objFile.copyTo(
                                    File(stagedObjectsOut, "${paddedIdx}_${objFile.name}"),
                                    overwrite = true
                                )
                            }
                        } else {
                            val monolithicOutDir = File(localStageADir, cacheDirName)
                            val srcMetadata = File(monolithicOutDir, "metadata.properties")
                            val srcBitcodeDeps = File(monolithicOutDir, "bin/bitcode_deps")
                            if (!srcMetadata.isFile || !srcBitcodeDeps.isFile) {
                                throw GradleException(
                                    "Expected Stage A facade files missing in ${monolithicOutDir.absolutePath} " +
                                        "(available entries: ${monolithicOutDir.list()?.joinToString()})"
                                )
                            }
                            srcMetadata.copyTo(File(stagedFacadeOut, "metadata.properties"), overwrite = true)
                            srcBitcodeDeps.copyTo(File(stagedFacadeOut, "bitcode_deps"), overwrite = true)
                            val srcIrDir = File(monolithicOutDir, "ir")
                            if (srcIrDir.isDirectory) {
                                copyIrDirectory(srcIrDir, File(stagedFacadeOut, "ir"))
                            }

                            val klibObjs = objTmpDir.walkTopDown()
                                .filter { it.isFile && it.extension == "o" }
                                .sortedBy { it.absolutePath }
                                .toList()
                            if (klibObjs.isEmpty()) {
                                throw GradleException(
                                    "No object files produced in ${objTmpDir.absolutePath} for klib '${klib.uniqueName}'"
                                )
                            }

                            val stageAArtifact = File(monolithicOutDir, "bin/$stageAArtifactName")
                            val cachedStageABin = File(stagedBinOut, stageAArtifactName)
                            if (isWindows && klib.uniqueName == "stdlib") {
                                val defFile = File(klibStagingDir, "weak_exports.def")
                                val weakCount = writeWindowsWeakExportsDef(klibObjs, defFile)
                                if (weakCount > 0) {
                                    val stageAStdlibImplib = File(weakExportsDir, "stdlib-stageA.dll.a")
                                    linkWindowsSharedLibrary(
                                        llvmHomeDir = llvmHomeDir,
                                        dependenciesDir = dependenciesDir,
                                        konanProps = konanProps,
                                        konanTarget = konanTarget,
                                        objectFiles = klibObjs,
                                        staticArchives = emptyList(),
                                        platformStaticArchives = emptyList(),
                                        upstreamLibraries = emptyList(),
                                        extraLinkerOpts = emptyList(),
                                        weakExportsDefFile = defFile,
                                        outDll = cachedStageABin,
                                        outImplib = stageAStdlibImplib,
                                        strip = !debuggable
                                    )
                                    if (stageAStdlibImplib.isFile) {
                                        stageAStdlibImplib.copyTo(File(klibStagingDir, "stdlib-stageA.dll.a"), overwrite = true)
                                    }
                                } else if (stageAArtifact.isFile) {
                                    stageAArtifact.copyTo(cachedStageABin, overwrite = true)
                                }
                            } else if (stageAArtifact.isFile) {
                                stageAArtifact.copyTo(cachedStageABin, overwrite = true)
                                if (!isWindows && !isStaticPlatform) {
                                    cachedStageABin.setExecutable(true, false)
                                }
                                if (isWindows && isStaticPlatform) {
                                    val probeUpstreamLibs = buildList {
                                        addAll(upstreamWindowsImplibs)
                                        File(weakExportsDir, "stdlib-stageA.dll.a")
                                            .takeIf { it.isFile }
                                            ?.let { add(it) }
                                    }.distinct()
                                    val builtPlatArchives = builtKlibStageAFacades.values
                                        .flatMap { facadeDir ->
                                            File(facadeDir, "bin")
                                                .listFiles()
                                                ?.filter { it.isFile && it.extension == "a" }
                                                .orEmpty()
                                        }
                                        .distinctBy { it.name }
                                    val klibOpts = collectGroupAndPlatformLinkerOpts(
                                        groupKlibs = listOf(klib),
                                        konanHomeDir = konanHomeDir,
                                        konanTarget = konanTarget
                                    )
                                    appendWindowsPlatformDeadIntrinsicStubs(
                                        llvmHomeDir = llvmHomeDir,
                                        dependenciesDir = dependenciesDir,
                                        konanProps = konanProps,
                                        konanTarget = konanTarget,
                                        objectFiles = klibObjs,
                                        alreadyBuiltPlatformArchives = builtPlatArchives,
                                        upstreamLibs = probeUpstreamLibs,
                                        klibOpts = klibOpts,
                                        targetArchive = cachedStageABin,
                                        workDir = objTmpDir
                                    )
                                }
                            } else if (isStaticPlatform) {
                                throw GradleException(
                                    "Expected Stage A static platform archive missing at ${stageAArtifact.absolutePath}"
                                )
                            }

                            klibObjs.forEachIndexed { oIdx, objFile ->
                                val paddedIdx = oIdx.toString().padStart(4, '0')
                                objFile.copyTo(
                                    File(stagedObjectsOut, "${paddedIdx}_${objFile.name}"),
                                    overwrite = true
                                )
                            }
                        }

                        objTmpDir.deleteRecursively()
                        localStageADir.deleteRecursively()
                        File(klibStagingDir, ".complete").writeText("ok\n", Charsets.UTF_8)
                        if (persistentKlibDir.exists()) {
                            persistentKlibDir.deleteRecursively()
                        }
                        Files.move(
                            klibStagingDir.toPath(),
                            persistentKlibDir.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    } finally {
                        if (klibStagingDir.exists()) {
                            klibStagingDir.deleteRecursively()
                        }
                    }
                }

                // Consume persistent per-klib Stage A outputs.
                val klibMetaDir = File(facadesMetaDir, sanitizedKlibName)
                klibMetaDir.mkdirs()
                val cachedKlibFacadeDir = File(persistentKlibDir, "facade")
                File(cachedKlibFacadeDir, "unique_name").copyTo(File(klibMetaDir, "unique_name"), overwrite = true)
                File(cachedKlibFacadeDir, "metadata.properties").copyTo(File(klibMetaDir, "metadata.properties"), overwrite = true)
                File(cachedKlibFacadeDir, "bitcode_deps").copyTo(File(klibMetaDir, "bitcode_deps"), overwrite = true)
                val cachedKlibIr = File(cachedKlibFacadeDir, "ir")
                if (cachedKlibIr.isDirectory) {
                    copyIrDirectory(cachedKlibIr, File(klibMetaDir, "ir"))
                }

                val stageABin = File(persistentKlibDir, "stageABin/$stageAArtifactName")
                if (isStaticPlatform) {
                    if (!stageABin.isFile) {
                        throw GradleException(
                            "Expected cached Stage A static platform archive missing at ${stageABin.absolutePath}"
                        )
                    }
                    val metaStaticArchive = File(klibMetaDir, stageAArtifactName)
                    stageABin.copyTo(metaStaticArchive, overwrite = true)
                    groupPlatformStaticArchives.add(metaStaticArchive)
                } else {
                    val cachedKlibObjs = File(persistentKlibDir, "objects")
                        .listFiles()
                        ?.filter { it.isFile && it.extension == "o" }
                        ?.sortedBy { it.name }
                        .orEmpty()
                    if (cachedKlibObjs.isEmpty()) {
                        throw GradleException(
                            "No cached Stage A object files found in ${persistentKlibDir.absolutePath}/objects"
                        )
                    }
                    groupObjectFiles.addAll(cachedKlibObjs)
                }

                val cachedWeakDef = File(persistentKlibDir, "weak_exports.def")
                if (isWindows && klib.uniqueName == "stdlib") {
                    if (cachedWeakDef.isFile) {
                        weakExportsDefFile = cachedWeakDef
                    }
                    val cachedStdlibImplib = File(persistentKlibDir, "stdlib-stageA.dll.a")
                    if (cachedStdlibImplib.isFile) {
                        cachedStdlibImplib.copyTo(File(weakExportsDir, "stdlib-stageA.dll.a"), overwrite = true)
                    }
                }

                val inGroupFacade = File(stageAFacadesRoot, cacheDirName)
                val inGroupBin = File(inGroupFacade, "bin")
                inGroupBin.mkdirs()
                copyFacadeSupportToCacheDir(klibMetaDir, inGroupFacade)
                if (stageABin.isFile) {
                    linkOrCopyArtifact(stageABin, File(inGroupBin, stageAArtifactName))
                }
                builtKlibStageAFacades[klib.uniqueName] = inGroupFacade

                // Extract any bundled cinterop static archives / native objects inside the klib.
                val extractedFromKlib = extractBundledKlibNativeBinaries(
                    klibFile = klib.file,
                    konanTarget = konanTarget,
                    destDir = File(extractedNativeDir, "${klibIdx}_${sanitizeFacadeDirName(klib.uniqueName)}")
                )
                for (nat in extractedFromKlib) {
                    if (nat.extension.equals("o", ignoreCase = true)) {
                        groupObjectFiles.add(nat)
                    } else {
                        groupStaticArchives.add(nat)
                    }
                }
            }

            // Stage B: Link the combined group shared library.
            val demandPlatformArchives = (upstreamPlatformStaticArchives + groupPlatformStaticArchives)
                .distinctBy { it.name }
                .reversed()
            val groupLinkerOpts = collectGroupAndPlatformLinkerOpts(
                groupKlibs = group.klibs,
                konanHomeDir = konanHomeDir,
                konanTarget = konanTarget
            )
            val outSharedLib = File(libDir, group.sharedLibraryFileName)
            when {
                isMac -> {
                    linkMacOsSharedLibrary(
                        konanProps = konanProps,
                        konanTarget = konanTarget,
                        objectFiles = groupObjectFiles,
                        staticArchives = groupStaticArchives,
                        upstreamDylibs = upstreamSharedLibs,
                        extraLinkerOpts = groupLinkerOpts,
                        installName = "@rpath/${group.sharedLibraryFileName}",
                        outDylib = outSharedLib,
                        strip = !debuggable,
                        workDir = stagingDir
                    )
                }
                isLinux -> {
                    linkLinuxSharedLibrary(
                        llvmHomeDir = llvmHomeDir,
                        dependenciesDir = dependenciesDir,
                        konanProps = konanProps,
                        konanTarget = konanTarget,
                        groupId = group.groupId,
                        soName = group.sharedLibraryFileName,
                        objectFiles = groupObjectFiles,
                        staticArchives = groupStaticArchives,
                        platformStaticArchives = demandPlatformArchives,
                        upstreamSos = upstreamSharedLibs,
                        extraLinkerOpts = groupLinkerOpts,
                        outSo = outSharedLib,
                        strip = !debuggable
                    )
                    if (group.groupId == "genericui-gtk" || group.groupId == "genericui-qt") {
                        val forbiddenPkg = if (group.groupId == "genericui-gtk") {
                            "com.nexustools.genericui.qt"
                        } else {
                            "com.nexustools.genericui.gtk"
                        }
                        verifyLinuxBackendIsolation(outSharedLib, forbiddenPkg)
                    } else if (isLinuxSingleBackendVariant &&
                        group.groupId == GenericUiNativeSharedLibraryCatalog.TESTS_GROUP_ID
                    ) {
                        val forbiddenTestsPkg = if (backendVariant.equals("qt", ignoreCase = true)) {
                            "com.nexustools.genericui.tests.gtk"
                        } else {
                            "com.nexustools.genericui.tests.qt"
                        }
                        verifyLinuxBackendIsolation(outSharedLib, forbiddenTestsPkg)
                    }
                }
                isWindows -> {
                    val outImplib = File(implibDir, "${group.groupId}.dll.a")
                    linkWindowsSharedLibrary(
                        llvmHomeDir = llvmHomeDir,
                        dependenciesDir = dependenciesDir,
                        konanProps = konanProps,
                        konanTarget = konanTarget,
                        objectFiles = groupObjectFiles,
                        staticArchives = groupStaticArchives,
                        platformStaticArchives = demandPlatformArchives,
                        upstreamLibraries = upstreamWindowsImplibs,
                        extraLinkerOpts = groupLinkerOpts,
                        weakExportsDefFile = weakExportsDefFile,
                        outDll = outSharedLib,
                        outImplib = outImplib,
                        strip = !debuggable
                    )
                }
            }

            File(stagingDir, ".complete").writeText("ok\n", Charsets.UTF_8)
            if (persistentGroupDir.exists()) {
                persistentGroupDir.deleteRecursively()
            }
            Files.move(stagingDir.toPath(), persistentGroupDir.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
        }
    }

    private fun collectTransitiveCachedDependencies(
        rootKlib: GenericUiNativeSharedLibraryCatalog.KlibManifestInfo,
        klibByUniqueName: Map<String, GenericUiNativeSharedLibraryCatalog.KlibManifestInfo>,
        availableFacades: Map<String, *>
    ): List<GenericUiNativeSharedLibraryCatalog.KlibManifestInfo> {
        val visited = linkedSetOf<String>()
        val result = mutableListOf<GenericUiNativeSharedLibraryCatalog.KlibManifestInfo>()

        fun dfs(info: GenericUiNativeSharedLibraryCatalog.KlibManifestInfo) {
            for (depName in info.depends) {
                if (depName == info.uniqueName || !visited.add(depName)) continue
                val depInfo = klibByUniqueName[depName] ?: continue
                if (!availableFacades.containsKey(depName)) continue
                dfs(depInfo)
                result.add(depInfo)
            }
        }
        dfs(rootKlib)
        return result
    }

    private fun collectGroupAndPlatformLinkerOpts(
        groupKlibs: List<GenericUiNativeSharedLibraryCatalog.KlibManifestInfo>,
        konanHomeDir: File,
        konanTarget: String
    ): List<String> {
        val result = mutableListOf<String>()
        for (klib in groupKlibs) {
            result.addAll(klib.linkerOpts)
        }

        val platformRoot = File(konanHomeDir, "klib/platform/$konanTarget")
        if (platformRoot.isDirectory) {
            val visitedPlatform = linkedSetOf<String>()
            val queue = ArrayDeque<String>()
            for (klib in groupKlibs) {
                for (dep in klib.depends) {
                    if (GenericUiNativeSharedLibraryCatalog.isPlatformKlibUniqueName(dep) && visitedPlatform.add(dep)) {
                        queue.addLast(dep)
                    }
                }
            }
            while (queue.isNotEmpty()) {
                val platName = queue.removeFirst()
                val platDir = File(platformRoot, platName)
                val platManifest = GenericUiNativeSharedLibraryCatalog.readManifest(platDir, konanTarget) ?: continue
                result.addAll(platManifest.linkerOpts)
                for (dep in platManifest.depends) {
                    if (GenericUiNativeSharedLibraryCatalog.isPlatformKlibUniqueName(dep) && visitedPlatform.add(dep)) {
                        queue.addLast(dep)
                    }
                }
            }
        }
        return result
    }

    private fun linuxExcludedPackageForKlib(uniqueName: String, backendVariant: String): String? {
        val isQt = backendVariant.equals("qt", ignoreCase = true)
        return when (uniqueName) {
            "com.nexustools.genericui:desktop" ->
                if (isQt) "com.nexustools.genericui.gtk" else "com.nexustools.genericui.qt"
            "com.nexustools.genericui:tests" ->
                if (isQt) "com.nexustools.genericui.tests.gtk" else "com.nexustools.genericui.tests.qt"
            else -> null
        }
    }

    private fun selectLinuxPerFileObjects(
        objTmpDir: File,
        excludedPkg: String,
        klibUniqueName: String,
        backendVariant: String
    ): Pair<List<File>, Set<String>> {
        val allObjs = objTmpDir.walkTopDown()
            .filter { it.isFile && it.extension == "o" }
            .sortedBy { it.absolutePath }
            .toList()
        if (allObjs.isEmpty()) {
            throw GradleException("No per-file .o objects found under ${objTmpDir.absolutePath}")
        }

        val kept = mutableListOf<File>()
        val excludedDirs = linkedSetOf<String>()
        for (obj in allObjs) {
            val relPath = obj.relativeTo(objTmpDir).path.replace('\\', '/')
            val firstSegment = relPath.substringBefore('/')
            if (firstSegment == excludedPkg ||
                firstSegment.startsWith("$excludedPkg.") ||
                relPath.contains("/$excludedPkg.")
            ) {
                excludedDirs.add(firstSegment)
                continue
            }
            val symbols = readElfDefinedSymbolNames(obj)
            val definesExcluded = symbols.any { sym ->
                sym.contains(":$excludedPkg.") ||
                    sym.contains(":$excludedPkg/") ||
                    sym.contains("$excludedPkg.") ||
                    sym.startsWith("kfun:$excludedPkg#")
            }
            if (definesExcluded) {
                excludedDirs.add(firstSegment)
            } else {
                kept.add(obj)
            }
        }
        if (kept.isEmpty()) {
            throw GradleException(
                "All $klibUniqueName .o files were filtered out for backendVariant='$backendVariant' in ${objTmpDir.absolutePath}"
            )
        }
        return kept to excludedDirs
    }

    private fun copyFacadeSupportToCacheDir(srcMetaDir: File, dstCacheDir: File) {
        val dstBinDir = File(dstCacheDir, "bin")
        dstBinDir.mkdirs()
        File(srcMetaDir, "metadata.properties").copyTo(
            File(dstCacheDir, "metadata.properties"),
            overwrite = true
        )
        File(srcMetaDir, "bitcode_deps").copyTo(
            File(dstBinDir, "bitcode_deps"),
            overwrite = true
        )
        val srcIrDir = File(srcMetaDir, "ir")
        if (srcIrDir.isDirectory) {
            copyIrDirectory(srcIrDir, File(dstCacheDir, "ir"))
        }
    }

    private fun copyIrDirectory(srcIrDir: File, dstIrDir: File) {
        dstIrDir.mkdirs()
        srcIrDir.listFiles()?.forEach { child ->
            if (child.isFile) {
                child.copyTo(File(dstIrDir, child.name), overwrite = true)
            }
        }
    }

    private fun mergePerFileIrFiles(
        keptPerFileDirs: List<File>,
        destIrDir: File,
        konanHomeDir: File
    ) {
        val serializerSpecs = listOf(
            "inline_bodies" to "org.jetbrains.kotlin.backend.konan.serialization.InlineFunctionBodyReferenceSerializer",
            "class_fields" to "org.jetbrains.kotlin.backend.konan.serialization.ClassFieldsSerializer",
            "eager_init" to "org.jetbrains.kotlin.backend.konan.serialization.EagerInitializedPropertySerializer",
            "trivial_getters" to "org.jetbrains.kotlin.backend.konan.serialization.TrivialGettersSerializer"
        )

        val embeddableJar = File(konanHomeDir, "konan/lib/kotlin-native-compiler-embeddable.jar")
        if (!embeddableJar.isFile) {
            throw GradleException("Missing embeddable JAR at ${embeddableJar.absolutePath}")
        }
        destIrDir.mkdirs()

        java.net.URLClassLoader(arrayOf(embeddableJar.toURI().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
            for ((fileName, serializerClassName) in serializerSpecs) {
                val perFileInputs = keptPerFileDirs
                    .map { File(it, "ir/$fileName") }
                    .filter { it.isFile }

                val cls = Class.forName(serializerClassName, true, loader)
                val instance = cls.getField("INSTANCE").get(null)
                val deserializeMethod = cls.methods.firstOrNull {
                    it.name == "deserializeTo" && it.parameterCount == 2
                } ?: error("Missing deserializeTo on $serializerClassName")
                val serializeMethod = cls.methods.firstOrNull {
                    it.name == "serialize" && it.parameterCount == 1
                } ?: error("Missing serialize on $serializerClassName")

                val merged = ArrayList<Any>()
                for (inputFile in perFileInputs) {
                    deserializeMethod.invoke(instance, inputFile.readBytes(), merged)
                }
                val mergedBytes = serializeMethod.invoke(instance, merged) as ByteArray
                File(destIrDir, fileName).writeBytes(mergedBytes)
            }
        }
    }

    private fun buildLinuxDesktopInitBridgeObject(
        keptObjs: List<File>,
        workDir: File,
        llvmHomeDir: File,
        konanTarget: String,
        desktopUniqueName: String
    ): File {
        val bridgeSymbol = "_Konan_init_$desktopUniqueName"
        val perFilePrefix = "$bridgeSymbol:"
        val ctors = linkedSetOf<String>()
        for (obj in keptObjs) {
            for (sym in readElfDefinedSymbolNames(obj, globalFunctionsOnly = true)) {
                if (sym.startsWith(perFilePrefix) && !sym.endsWith("_guard")) {
                    ctors.add(sym)
                }
            }
        }

        val sortedCtors = ctors.sorted()
        val llFile = File(workDir, "desktop_init_bridge.ll")
        llFile.writeText(
            buildString {
                for (ctor in sortedCtors) {
                    appendLine("declare void @\"$ctor\"()")
                }
                appendLine("define void @\"$bridgeSymbol\"() {")
                appendLine("entry:")
                for (ctor in sortedCtors) {
                    appendLine("  call void @\"$ctor\"()")
                }
                appendLine("  ret void")
                appendLine("}")
            },
            Charsets.UTF_8
        )

        val clangExe = resolveLlvmTool(llvmHomeDir, "clang")
        val targetTriple = when (konanTarget) {
            "linux_arm64" -> "aarch64-unknown-linux-gnu"
            "linux_x64" -> "x86_64-unknown-linux-gnu"
            else -> throw GradleException("Unsupported Linux target '$konanTarget' for init bridge")
        }
        val bridgeObj = File(workDir, "desktop_init_bridge.o")
        runProcess(
            listOf(
                clangExe.absolutePath,
                "--target=$targetTriple",
                "-fPIC",
                "-O2",
                "-c",
                "-x",
                "ir",
                llFile.absolutePath,
                "-o",
                bridgeObj.absolutePath
            )
        )
        return bridgeObj
    }

    private fun verifyLinuxBackendIsolation(soFile: File, forbiddenPkg: String) {
        val offending = readElfDefinedSymbolNames(soFile)
            .filter { it.contains("$forbiddenPkg.") || it.contains("$forbiddenPkg/") }
        if (offending.isNotEmpty()) {
            throw GradleException(
                "Backend isolation check failed for ${soFile.name}: found ${offending.size} forbidden '$forbiddenPkg' symbols (e.g. ${offending.take(5).joinToString()})"
            )
        }
    }

    private fun linkMacOsSharedLibrary(
        konanProps: Properties,
        konanTarget: String,
        objectFiles: List<File>,
        staticArchives: List<File>,
        upstreamDylibs: List<File>,
        extraLinkerOpts: List<String>,
        installName: String,
        outDylib: File,
        strip: Boolean,
        workDir: File
    ) {
        val localizedObjs = localizeDuplicateMacOsObjectSymbols(objectFiles, workDir)
        val arch = if (konanTarget == "macos_arm64") "arm64" else "x86_64"
        val minVersion = resolveKonanProperty(konanProps, "minVersion.macos") ?: "12.0"
        val sdkVersion = runProcessOutput(listOf("xcrun", "--sdk", "macosx", "--show-sdk-version")).trim()
            .ifEmpty { resolveKonanProperty(konanProps, "sdkVersion.macos") ?: "26.4" }
        val sdkPath = runProcessOutput(listOf("xcrun", "--sdk", "macosx", "--show-sdk-path")).trim()
        if (sdkPath.isEmpty() || !File(sdkPath).exists()) {
            throw GradleException("Unable to locate macOS SDK via xcrun --sdk macosx --show-sdk-path")
        }
        val clangRuntimeDir = File(runProcessOutput(listOf("xcrun", "clang", "--print-runtime-dir")).trim())
        val clangRt = File(clangRuntimeDir, "libclang_rt.osx.a")
        if (!clangRt.isFile) {
            throw GradleException("libclang_rt.osx.a not found at ${clangRt.absolutePath}")
        }

        val unwrappedOpts = normalizeDirectLdOptions(extraLinkerOpts, isMac = true)
        val cmd = buildList {
            add("xcrun")
            add("ld")
            add("-demangle")
            add("-dynamic")
            add("-arch")
            add(arch)
            add("-platform_version")
            add("macos")
            add(minVersion)
            add(sdkVersion)
            add("-syslibroot")
            add(sdkPath)
            if (strip) {
                add("-dead_strip")
                add("-S")
            }
            add("-dylib")
            add("-install_name")
            add(installName)
            add("-rpath")
            add("@executable_path/../Frameworks")
            add("-rpath")
            add("@loader_path")
            add("-o")
            add(outDylib.absolutePath)

            localizedObjs.forEach { add(it.absolutePath) }
            staticArchives.forEach {
                add("-force_load")
                add(it.absolutePath)
            }
            upstreamDylibs.forEach { add(it.absolutePath) }
            add("-lSystem")
            add("-lc++")
            add("-lobjc")
            add("-framework")
            add("Foundation")
            addAll(unwrappedOpts)
            add(clangRt.absolutePath)
        }
        if (outDylib.exists()) {
            outDylib.delete()
        }
        runProcess(cmd)
        runProcess(listOf("codesign", "--force", "--sign", "-", outDylib.absolutePath))
    }

    private fun localizeDuplicateMacOsObjectSymbols(
        objectFiles: List<File>,
        workDir: File
    ): List<File> {
        if (objectFiles.size <= 1) return objectFiles
        val copiesDir = File(workDir, "localizedMacObjs")
        copiesDir.mkdirs()

        val seenExterns = mutableSetOf<String>()
        val result = mutableListOf<File>()
        for ((idx, srcObj) in objectFiles.withIndex()) {
            val definedExterns = runProcessOutput(listOf("xcrun", "nm", "-gU", srcObj.absolutePath))
                .lines()
                .mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.lastOrNull() }
                .distinct()
            if (idx == 0) {
                seenExterns.addAll(definedExterns)
                result.add(srcObj)
                continue
            }
            val duplicates = definedExterns.filter { !seenExterns.add(it) }
            if (duplicates.isEmpty()) {
                result.add(srcObj)
                continue
            }
            val copyObj = File(copiesDir, "${idx}_${srcObj.name}")
            srcObj.copyTo(copyObj, overwrite = true)
            val symListFile = File(copiesDir, "${idx}_localize.txt")
            symListFile.writeText(duplicates.joinToString("\n") + "\n", Charsets.UTF_8)
            runProcess(listOf("xcrun", "nmedit", "-R", symListFile.absolutePath, copyObj.absolutePath))
            result.add(copyObj)
        }
        return result
    }

    private fun linkLinuxSharedLibrary(
        llvmHomeDir: File,
        dependenciesDir: File,
        konanProps: Properties,
        konanTarget: String,
        groupId: String,
        soName: String,
        objectFiles: List<File>,
        staticArchives: List<File>,
        platformStaticArchives: List<File>,
        upstreamSos: List<File>,
        extraLinkerOpts: List<String>,
        outSo: File,
        strip: Boolean
    ) {
        val ldLld = resolveLlvmTool(llvmHomeDir, "ld.lld")
        val sysRootRel = resolveTargetKonanProperty(konanProps, "targetSysRoot", host = null, target = konanTarget)
            ?: throw GradleException("targetSysRoot.$konanTarget missing in konan.properties")
        val sysRoot = File(dependenciesDir, sysRootRel)
        val libGccRel = resolveKonanProperty(konanProps, "libGcc.$konanTarget")
            ?: throw GradleException("libGcc.$konanTarget missing in konan.properties")
        val libGccDir = File(sysRoot, libGccRel).canonicalFile

        val crti = firstExistingFile(
            File(sysRoot, "usr/lib64/crti.o"),
            File(sysRoot, "usr/lib/crti.o")
        )
        val crtn = firstExistingFile(
            File(sysRoot, "usr/lib64/crtn.o"),
            File(sysRoot, "usr/lib/crtn.o")
        )
        val crtBeginS = File(libGccDir, "crtbeginS.o")
        val crtEndS = File(libGccDir, "crtendS.o")

        val archEmul = when (konanTarget) {
            "linux_arm64" -> "aarch64linux"
            "linux_x64" -> "elf_x86_64"
            else -> throw GradleException("Unsupported Linux target '$konanTarget'")
        }
        val unwrappedOpts = normalizeDirectLdOptions(extraLinkerOpts, isMac = false)

        val cmd = buildList {
            add(ldLld.absolutePath)
            add("--sysroot=${sysRoot.absolutePath}")
            add("--eh-frame-hdr")
            add("-m")
            add(archEmul)
            add("-shared")
            add("-soname")
            add(soName)
            add("-rpath")
            add("\$ORIGIN")
            add("-rpath")
            add("\$ORIGIN/lib")
            add("-z")
            add("muldefs")
            add("-export-dynamic")
            add("-z")
            add("relro")
            add("--build-id")
            add("--hash-style=gnu")
            add("--gc-sections")
            if (strip) {
                add("-S")
            }
            add("-o")
            add(outSo.absolutePath)
            add(crti.absolutePath)
            add(crtBeginS.absolutePath)
            add("-L${libGccDir.absolutePath}")
            listOf("lib64", "lib", "usr/lib64", "usr/lib").forEach { rel ->
                val d = File(sysRoot, rel)
                if (d.isDirectory) add("-L${d.absolutePath}")
            }
            objectFiles.forEach { add(it.absolutePath) }
            if (staticArchives.isNotEmpty()) {
                add("--whole-archive")
                staticArchives.forEach { add(it.absolutePath) }
                add("--no-whole-archive")
            }
            if (platformStaticArchives.isNotEmpty()) {
                add("--start-group")
                platformStaticArchives.forEach { add(it.absolutePath) }
                add("--end-group")
            }
            upstreamSos.forEach { add(it.absolutePath) }
            addAll(unwrappedOpts)
            add("-Bstatic")
            add("-lstdc++")
            add("-Bdynamic")
            add("-ldl")
            add("-lm")
            add("-lpthread")
            if (groupId == GenericUiNativeSharedLibraryCatalog.CORE_GROUP_ID) {
                add("--defsym")
                add("__cxa_demangle=Konan_cxa_demangle")
            }
            add("--as-needed")
            add("-lgcc_s")
            add("--no-as-needed")
            add("-lc")
            add("-lgcc")
            add("--as-needed")
            add("-lgcc_s")
            add("--no-as-needed")
            add(crtEndS.absolutePath)
            add(crtn.absolutePath)
        }
        if (outSo.exists()) {
            outSo.delete()
        }
        runProcess(cmd)
    }

    private fun linkWindowsSharedLibrary(
        llvmHomeDir: File,
        dependenciesDir: File,
        konanProps: Properties,
        konanTarget: String,
        objectFiles: List<File>,
        staticArchives: List<File>,
        platformStaticArchives: List<File>,
        upstreamLibraries: List<File>,
        extraLinkerOpts: List<String>,
        weakExportsDefFile: File?,
        outDll: File,
        outImplib: File,
        strip: Boolean
    ) {
        val clangPlusPlus = resolveLlvmTool(llvmHomeDir, "clang++")
        val ldLld = resolveLlvmTool(llvmHomeDir, "ld.lld")
        val sysRootRel = resolveTargetKonanProperty(konanProps, "targetSysRoot", host = null, target = konanTarget)
            ?: throw GradleException("targetSysRoot.$konanTarget missing in konan.properties")
        val sysRoot = File(dependenciesDir, sysRootRel)

        val cmd = buildList {
            add(clangPlusPlus.absolutePath)
            add("--sysroot")
            add(sysRoot.absolutePath)
            add("-target")
            add("x86_64-pc-windows-gnu")
            add("-shared")
            add("-fuse-ld=${ldLld.absolutePath}")
            add("-Wl,--export-all-symbols")
            add("-Wl,--allow-multiple-definition")
            add("-Wl,--out-implib,${outImplib.absolutePath}")
            if (strip) {
                add("-Wl,-S")
            }
            add("-o")
            add(outDll.absolutePath)
            objectFiles.forEach { add(it.absolutePath) }
            if (weakExportsDefFile != null && weakExportsDefFile.isFile) {
                add(weakExportsDefFile.absolutePath)
            }
            if (staticArchives.isNotEmpty()) {
                add("-Wl,--whole-archive")
                staticArchives.forEach { add(it.absolutePath) }
                add("-Wl,--no-whole-archive")
            }
            platformStaticArchives.forEach { add(it.absolutePath) }
            upstreamLibraries.forEach { add(it.absolutePath) }
            addAll(extraLinkerOpts)
            add("-static-libgcc")
            add("-static-libstdc++")
            add("-lbcrypt")
            add("-Wl,--dynamicbase")
            add("-Wl,--gc-sections")
            add("-Wl,-Bstatic,--whole-archive")
            add("-lwinpthread")
            add("-Wl,--no-whole-archive,-Bdynamic")
            WINDOWS_SYSTEM_LIBS.forEach { add("-l$it") }
        }
        if (outDll.exists()) {
            outDll.delete()
        }
        if (outImplib.exists()) {
            outImplib.delete()
        }
        runProcess(cmd)
    }

    private fun appendWindowsPlatformDeadIntrinsicStubs(
        llvmHomeDir: File,
        dependenciesDir: File,
        konanProps: Properties,
        konanTarget: String,
        objectFiles: List<File>,
        alreadyBuiltPlatformArchives: List<File>,
        upstreamLibs: List<File>,
        klibOpts: List<String>,
        targetArchive: File,
        workDir: File
    ) {
        val clangPlusPlus = resolveLlvmTool(llvmHomeDir, "clang++")
        val clangExe = resolveLlvmTool(llvmHomeDir, "clang")
        val ldLld = resolveLlvmTool(llvmHomeDir, "ld.lld")
        val llvmAr = resolveLlvmTool(llvmHomeDir, "llvm-ar")
        val sysRootRel = resolveTargetKonanProperty(konanProps, "targetSysRoot", host = null, target = konanTarget)
            ?: throw GradleException("targetSysRoot.$konanTarget missing in konan.properties")
        val sysRoot = File(dependenciesDir, sysRootRel)
        val probeDll = File(workDir, "probe_platform.dll")

        val probeCmd = buildList {
            add(clangPlusPlus.absolutePath)
            add("--sysroot")
            add(sysRoot.absolutePath)
            add("-target")
            add("x86_64-pc-windows-gnu")
            add("-shared")
            add("-fuse-ld=${ldLld.absolutePath}")
            add("-Wl,--allow-multiple-definition")
            add("-Wl,--error-limit=0")
            add("-Wl,--gc-sections")
            add("-o")
            add(probeDll.absolutePath)
            objectFiles.forEach { add(it.absolutePath) }
            if (alreadyBuiltPlatformArchives.isNotEmpty()) {
                add("-Wl,--start-group")
                alreadyBuiltPlatformArchives.forEach { add(it.absolutePath) }
                add("-Wl,--end-group")
            }
            upstreamLibs.forEach { add(it.absolutePath) }
            addAll(klibOpts)
            add("-static-libgcc")
            add("-static-libstdc++")
            add("-lbcrypt")
            add("-Wl,-Bstatic,--whole-archive")
            add("-lwinpthread")
            add("-Wl,--no-whole-archive,-Bdynamic")
            WINDOWS_SYSTEM_LIBS.forEach { add("-l$it") }
        }

        val pb = ProcessBuilder(probeCmd)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        proc.waitFor()
        if (probeDll.exists()) {
            probeDll.delete()
        }

        val rawSyms = linkedSetOf<String>()
        val impSyms = linkedSetOf<String>()
        val marker = "undefined symbol: "
        val dllImportPrefix = "__declspec(dllimport) "
        for (line in output.lines()) {
            val idx = line.indexOf(marker)
            if (idx < 0) continue
            val rawName = line.substring(idx + marker.length).trim()
            if (rawName.isEmpty()) continue
            val unprefixed = if (rawName.startsWith(dllImportPrefix)) {
                rawName.substring(dllImportPrefix.length).trim()
            } else {
                rawName
            }
            if (unprefixed.isEmpty() ||
                unprefixed.startsWith("kfun:") ||
                unprefixed.startsWith("kclass:") ||
                unprefixed.startsWith("ktype:") ||
                unprefixed.startsWith("kintf:") ||
                unprefixed.startsWith("_Konan") ||
                unprefixed == "__genericui_win_dead_stub"
            ) {
                continue
            }
            if (rawName.startsWith(dllImportPrefix)) {
                impSyms.add(unprefixed)
            } else {
                rawSyms.add(unprefixed)
            }
        }

        if (rawSyms.isEmpty() && impSyms.isEmpty()) {
            return
        }

        fun escapeIrSym(sym: String): String = sym.replace("\\", "\\5C").replace("\"", "\\22")

        val llFile = File(workDir, "dead_platform_stubs.ll")
        llFile.writeText(
            buildString {
                appendLine("target triple = \"x86_64-pc-windows-gnu\"")
                appendLine("declare void @abort()")
                appendLine("define hidden void @__genericui_win_dead_stub() {")
                appendLine("  call void @abort()")
                appendLine("  unreachable")
                appendLine("}")
                for (sym in rawSyms.sorted()) {
                    val escaped = escapeIrSym(sym)
                    appendLine("define hidden void @\"$escaped\"() {")
                    appendLine("  call void @abort()")
                    appendLine("  unreachable")
                    appendLine("}")
                }
                for (sym in impSyms.sorted()) {
                    val escaped = escapeIrSym(sym)
                    appendLine("@\"__imp_$escaped\" = hidden global ptr @__genericui_win_dead_stub")
                }
            },
            Charsets.UTF_8
        )

        val stubObj = File(workDir, "dead_platform_stubs.o")
        runProcess(
            listOf(
                clangExe.absolutePath,
                "-cc1",
                "-triple",
                "x86_64-pc-windows-gnu",
                "-emit-obj",
                "-ffunction-sections",
                "-fdata-sections",
                "-x",
                "ir",
                llFile.absolutePath,
                "-o",
                stubObj.absolutePath
            )
        )
        runProcess(
            listOf(
                llvmAr.absolutePath,
                "rcs",
                targetArchive.absolutePath,
                stubObj.absolutePath
            )
        )
    }

    private fun extractBundledKlibNativeBinaries(
        klibFile: File,
        konanTarget: String,
        destDir: File
    ): List<File> {
        val allowedIncludedExts = setOf("a", "o", "lib")
        val allowedNativeExts = setOf("a", "lib")
        val includedPrefix = "default/targets/$konanTarget/included/"
        val nativePrefix = "default/targets/$konanTarget/native/"
        val extracted = mutableListOf<File>()

        if (klibFile.isDirectory) {
            listOf(
                File(klibFile, includedPrefix) to allowedIncludedExts,
                File(klibFile, nativePrefix) to allowedNativeExts
            ).forEach { (dir, exts) ->
                if (!dir.isDirectory) return@forEach
                dir.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in exts && it.length() > 8 }
                    .sortedBy { it.absolutePath }
                    .forEach { src ->
                        destDir.mkdirs()
                        val dst = File(destDir, src.relativeTo(klibFile).path.replace('/', '_'))
                        src.copyTo(dst, overwrite = true)
                        extracted.add(dst)
                    }
            }
        } else if (klibFile.isFile && klibFile.extension.equals("klib", ignoreCase = true)) {
            ZipFile(klibFile).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.size > 8 }
                    .filter { entry ->
                        val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        (entry.name.startsWith(includedPrefix) && ext in allowedIncludedExts) ||
                            (entry.name.startsWith(nativePrefix) && ext in allowedNativeExts)
                    }
                    .sortedBy { it.name }
                    .toList()
                for (entry in entries) {
                    destDir.mkdirs()
                    val dst = File(destDir, entry.name.replace('/', '_'))
                    zip.getInputStream(entry).use { input ->
                        dst.outputStream().use { output -> input.copyTo(output) }
                    }
                    extracted.add(dst)
                }
            }
        }
        return extracted
    }

    /**
     * Parses COFF `.o` symbol tables to find weak external/COMDAT `Kotlin_*` runtime symbols
     * that `lld` does not automatically export under `--export-all-symbols`, emitting a `.def`
     * with `DATA` for non-code globals.
     */
    private fun writeWindowsWeakExportsDef(objectFiles: List<File>, defFile: File): Int {
        val weakExports = linkedMapOf<String, Boolean>() // symbolName -> isData
        for (obj in objectFiles) {
            for (sym in readCoffWeakKotlinSymbols(obj)) {
                val prev = weakExports[sym.name]
                if (prev == null) {
                    weakExports[sym.name] = sym.isData
                } else {
                    weakExports[sym.name] = prev && sym.isData
                }
            }
        }
        if (weakExports.isEmpty()) {
            return 0
        }
        defFile.parentFile?.mkdirs()
        defFile.writeText(
            buildString {
                appendLine("EXPORTS")
                for ((name, isData) in weakExports.entries.sortedBy { it.key }) {
                    if (isData) {
                        appendLine("  $name DATA")
                    } else {
                        appendLine("  $name")
                    }
                }
            },
            Charsets.UTF_8
        )
        return weakExports.size
    }

    private data class CoffWeakSymbol(val name: String, val isData: Boolean)

    private fun readCoffWeakKotlinSymbols(objFile: File): List<CoffWeakSymbol> {
        val bytes = objFile.readBytes()
        if (bytes.size < 20) return emptyList()
        val machine = readU16LE(bytes, 0)
        val sig2 = readU16LE(bytes, 2)
        val isBigObj = machine == 0 && sig2 == 0xFFFF
        val numSections: Int
        val ptrSymTable: Long
        val numSymbols: Int
        val sectionTableOffset: Int
        val symEntrySize: Int

        if (isBigObj) {
            if (bytes.size < 56) return emptyList()
            numSections = readU32LE(bytes, 44)
            ptrSymTable = readU32LE(bytes, 48).toLong() and 0xFFFFFFFFL
            numSymbols = readU32LE(bytes, 52)
            sectionTableOffset = 56
            symEntrySize = 20
        } else {
            numSections = sig2
            ptrSymTable = readU32LE(bytes, 8).toLong() and 0xFFFFFFFFL
            numSymbols = readU32LE(bytes, 12)
            val optHdrSize = readU16LE(bytes, 16)
            sectionTableOffset = 20 + optHdrSize
            symEntrySize = 18
        }
        if (ptrSymTable <= 0L || numSymbols <= 0) return emptyList()

        val sectionCharacteristics = IntArray(numSections) { idx ->
            val off = sectionTableOffset + idx * 40
            if (off + 40 <= bytes.size) readU32LE(bytes, off + 36) else 0
        }
        val stringTableOffset = ptrSymTable.toInt() + numSymbols * symEntrySize

        data class RawSym(
            val name: String,
            val secNum: Int,
            val type: Int,
            val storageClass: Int,
            val auxCount: Int,
            val symOffset: Int
        )

        val symByIndex = arrayOfNulls<RawSym>(numSymbols)
        var idx = 0
        while (idx < numSymbols) {
            val off = ptrSymTable.toInt() + idx * symEntrySize
            if (off + symEntrySize > bytes.size) break
            val nameZero = readU32LE(bytes, off)
            val symName = if (nameZero != 0) {
                readFixedCString(bytes, off, 8)
            } else {
                val strOff = stringTableOffset + readU32LE(bytes, off + 4)
                readNullTerminatedString(bytes, strOff)
            }
            val secNum: Int
            val type: Int
            val storageClass: Int
            val auxCount: Int
            if (isBigObj) {
                secNum = readU32LE(bytes, off + 12)
                type = readU16LE(bytes, off + 16)
                storageClass = bytes[off + 18].toInt() and 0xFF
                auxCount = bytes[off + 19].toInt() and 0xFF
            } else {
                secNum = readI16LE(bytes, off + 12)
                type = readU16LE(bytes, off + 14)
                storageClass = bytes[off + 16].toInt() and 0xFF
                auxCount = bytes[off + 17].toInt() and 0xFF
            }
            symByIndex[idx] = RawSym(symName, secNum, type, storageClass, auxCount, off)
            idx += 1 + auxCount
        }

        val result = mutableListOf<CoffWeakSymbol>()
        for (sym in symByIndex) {
            if (sym == null) continue
            if (!sym.name.startsWith("Kotlin_")) continue

            val isWeakExternal = sym.storageClass == 105 // IMAGE_SYM_CLASS_WEAK_EXTERNAL
            val isComdatExternal = sym.storageClass == 2 && sym.secNum in 1..numSections &&
                (sectionCharacteristics[sym.secNum - 1] and 0x00001000) != 0
            if (!isWeakExternal && !isComdatExternal) continue

            val isData = if (isWeakExternal && sym.auxCount > 0) {
                val targetIdx = readU32LE(bytes, sym.symOffset + symEntrySize)
                val targetSym = if (targetIdx in symByIndex.indices) symByIndex[targetIdx] else null
                isCoffSymbolData(targetSym?.type ?: sym.type, targetSym?.secNum ?: 0, sectionCharacteristics)
            } else {
                isCoffSymbolData(sym.type, sym.secNum, sectionCharacteristics)
            }
            result.add(CoffWeakSymbol(sym.name, isData))
        }
        return result
    }

    private fun isCoffSymbolData(type: Int, secNum: Int, sectionCharacteristics: IntArray): Boolean {
        if ((type and 0xF0) == 0x20) return false // IMAGE_SYM_DTYPE_FUNCTION
        if (secNum in 1..sectionCharacteristics.size) {
            val ch = sectionCharacteristics[secNum - 1]
            if ((ch and 0x00000020) != 0) return false // IMAGE_SCN_CNT_CODE
        }
        return true
    }

    private fun readElfDefinedSymbolNames(
        elfFile: File,
        globalFunctionsOnly: Boolean = false
    ): List<String> {
        val bytes = elfFile.readBytes()
        if (bytes.size < 64) return emptyList()
        if (bytes[0] != 0x7f.toByte() || bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) {
            return emptyList()
        }
        if ((bytes[4].toInt() and 0xFF) != 2 || (bytes[5].toInt() and 0xFF) != 1) {
            return emptyList() // ELF64 little-endian
        }

        val shOff = readU64LE(bytes, 40).toInt()
        val shEntSize = readU16LE(bytes, 58)
        val shNum = readU16LE(bytes, 60)
        if (shOff <= 0 || shEntSize <= 0 || shNum <= 0) return emptyList()

        val result = mutableListOf<String>()
        for (s in 0 until shNum) {
            val secPos = shOff + s * shEntSize
            if (secPos + shEntSize > bytes.size) break
            val shType = readU32LE(bytes, secPos + 4)
            if (shType != 2 && shType != 11) continue // SHT_SYMTAB / SHT_DYNSYM

            val symOffset = readU64LE(bytes, secPos + 24).toInt()
            val symSize = readU64LE(bytes, secPos + 32).toInt()
            val strSecIdx = readU32LE(bytes, secPos + 40)
            val entSize = readU64LE(bytes, secPos + 56).toInt().coerceAtLeast(24)
            if (strSecIdx !in 0 until shNum) continue

            val strSecPos = shOff + strSecIdx * shEntSize
            val strOffset = readU64LE(bytes, strSecPos + 24).toInt()
            val strSize = readU64LE(bytes, strSecPos + 32).toInt()
            val strEnd = (strOffset + strSize).coerceAtMost(bytes.size)

            var pos = symOffset
            val symEnd = (symOffset + symSize).coerceAtMost(bytes.size)
            while (pos + entSize <= symEnd) {
                val stName = readU32LE(bytes, pos)
                val stInfo = bytes[pos + 4].toInt() and 0xFF
                val stShndx = readU16LE(bytes, pos + 6)
                val passesFilter = if (globalFunctionsOnly) {
                    val stBind = stInfo ushr 4
                    val stType = stInfo and 0x0F
                    stBind != 0 && (stType == 2 || stType == 0)
                } else {
                    true
                }
                if (passesFilter && stShndx != 0 && stName > 0 && strOffset + stName < strEnd) {
                    val name = readNullTerminatedString(bytes, strOffset + stName)
                    if (name.isNotEmpty()) {
                        result.add(name)
                    }
                }
                pos += entSize
            }
        }
        return result
    }

    private fun normalizeDirectLdOptions(opts: List<String>, isMac: Boolean): List<String> {
        val out = mutableListOf<String>()
        for (opt in opts) {
            if (opt.startsWith("-Wl,")) {
                val parts = opt.removePrefix("-Wl,").split(',').filter { it.isNotEmpty() }
                out.addAll(parts)
            } else if (!isMac && (opt == "-static-libgcc" || opt == "-static-libstdc++")) {
                continue
            } else if (!isMac && opt == "-pthread") {
                out.add("-lpthread")
            } else {
                out.add(opt)
            }
        }
        // Filter duplicate --defsym __cxa_demangle if carried in from elsewhere
        val filtered = mutableListOf<String>()
        var skipNext = false
        for (i in out.indices) {
            if (skipNext) {
                skipNext = false
                continue
            }
            val token = out[i]
            if (!isMac && token == "--defsym" && i + 1 < out.size && out[i + 1].contains("__cxa_demangle")) {
                skipNext = true
                continue
            }
            if (!isMac && token.startsWith("--defsym=") && token.contains("__cxa_demangle")) {
                continue
            }
            filtered.add(token)
        }
        return filtered
    }

    private fun resolveLlvmHomeDir(
        konanProps: Properties,
        dependenciesDir: File,
        hostKonan: String
    ): File {
        val rel = resolveKonanProperty(konanProps, "llvmHome.$hostKonan")
            ?: throw GradleException("llvmHome.$hostKonan missing in konan.properties")
        val dir = File(dependenciesDir, rel)
        if (!dir.isDirectory) {
            throw GradleException("Konan LLVM directory not found at ${dir.absolutePath}")
        }
        return dir
    }

    private fun resolveLlvmTool(llvmHomeDir: File, baseName: String): File {
        val isHostWindows = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("win")
        val candidate = File(llvmHomeDir, if (isHostWindows) "bin/$baseName.exe" else "bin/$baseName")
        if (!candidate.isFile) {
            throw GradleException("Required LLVM tool $baseName not found at ${candidate.absolutePath}")
        }
        return candidate
    }

    private fun loadKonanProperties(konanHomeDir: File): Properties {
        val propsFile = File(konanHomeDir, "konan/konan.properties")
        if (!propsFile.isFile) {
            throw GradleException("konan.properties not found at ${propsFile.absolutePath}")
        }
        return Properties().apply {
            propsFile.bufferedReader(Charsets.UTF_8).use { load(it) }
        }
    }

    private fun resolveTargetKonanProperty(
        props: Properties,
        prefix: String,
        host: String?,
        target: String
    ): String? {
        if (host != null) {
            resolveKonanProperty(props, "$prefix.$host-$target")?.let { return it }
        }
        return resolveKonanProperty(props, "$prefix.$target")
    }

    private fun resolveKonanProperty(props: Properties, key: String, depth: Int = 0): String? {
        if (depth > 16) return null
        val raw = props.getProperty(key)?.trim() ?: return null
        val regex = Regex("\\$([A-Za-z0-9_.-]+)")
        return regex.replace(raw) { match ->
            val refKey = match.groupValues[1]
            resolveKonanProperty(props, refKey, depth + 1) ?: match.value
        }
    }

    private fun computeKlibFingerprint(klibFile: File, konanTarget: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(konanTarget.toByteArray(Charsets.UTF_8))
        if (klibFile.isDirectory) {
            klibFile.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.relativeTo(klibFile).path }
                .forEach { f ->
                    val rel = f.relativeTo(klibFile).path.replace('\\', '/')
                    if (!rel.startsWith("default/targets/") || rel.startsWith("default/targets/$konanTarget/")) {
                        md.update(rel.toByteArray(Charsets.UTF_8))
                        md.update(0)
                        md.update(f.readBytes())
                    }
                }
        } else if (klibFile.isFile) {
            md.update(klibFile.name.toByteArray(Charsets.UTF_8))
            md.update(klibFile.readBytes())
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun sanitizeFacadeDirName(uniqueName: String): String =
        uniqueName.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun linkOrCopyArtifact(targetFile: File, linkFile: File) {
        linkFile.parentFile?.mkdirs()
        if (linkFile.exists() || Files.isSymbolicLink(linkFile.toPath())) {
            linkFile.delete()
        }
        val created = runCatching {
            val relTarget = linkFile.parentFile.toPath().relativize(targetFile.toPath())
            Files.createSymbolicLink(linkFile.toPath(), relTarget)
            true
        }.getOrElse { false }
        if (!created) {
            targetFile.copyTo(linkFile, overwrite = true)
            linkFile.setExecutable(targetFile.canExecute(), false)
        }
    }

    private fun cleanDirectory(dir: File) {
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        dir.mkdirs()
    }

    private fun firstExistingFile(vararg candidates: File): File {
        return candidates.firstOrNull { it.isFile }
            ?: throw GradleException(
                "None of expected files exist: ${candidates.joinToString { it.absolutePath }}"
            )
    }

    private fun runProcess(command: List<String>) {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val exit = proc.waitFor()
        if (exit != 0) {
            throw GradleException(
                buildString {
                    appendLine("Command failed with exit code $exit:")
                    appendLine("  ${command.joinToString(" ")}")
                    if (out.isNotBlank()) {
                        appendLine("Output:")
                        appendLine(out.trimEnd())
                    }
                }
            )
        }
    }

    private fun runProcessOutput(command: List<String>): String {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val exit = proc.waitFor()
        if (exit != 0) {
            throw GradleException(
                buildString {
                    appendLine("Command failed with exit code $exit:")
                    appendLine("  ${command.joinToString(" ")}")
                    if (out.isNotBlank()) {
                        appendLine("Output:")
                        appendLine(out.trimEnd())
                    }
                }
            )
        }
        return out
    }

    private fun readU16LE(bytes: ByteArray, off: Int): Int =
        (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)

    private fun readI16LE(bytes: ByteArray, off: Int): Int {
        val v = readU16LE(bytes, off)
        return if ((v and 0x8000) != 0) v or -0x10000 else v
    }

    private fun readU32LE(bytes: ByteArray, off: Int): Int =
        (bytes[off].toInt() and 0xFF) or
            ((bytes[off + 1].toInt() and 0xFF) shl 8) or
            ((bytes[off + 2].toInt() and 0xFF) shl 16) or
            ((bytes[off + 3].toInt() and 0xFF) shl 24)

    private fun readU64LE(bytes: ByteArray, off: Int): Long =
        (readU32LE(bytes, off).toLong() and 0xFFFFFFFFL) or
            ((readU32LE(bytes, off + 4).toLong() and 0xFFFFFFFFL) shl 32)

    private fun readFixedCString(bytes: ByteArray, off: Int, maxLen: Int): String {
        var len = 0
        while (len < maxLen && off + len < bytes.size && bytes[off + len] != 0.toByte()) {
            len++
        }
        return String(bytes, off, len, Charsets.UTF_8)
    }

    private fun readNullTerminatedString(bytes: ByteArray, off: Int): String {
        if (off < 0 || off >= bytes.size) return ""
        var end = off
        while (end < bytes.size && bytes[end] != 0.toByte()) {
            end++
        }
        return String(bytes, off, end - off, Charsets.UTF_8)
    }

    companion object {
        private const val CACHE_SCHEMA_VERSION = "v3"
        private const val EXTRA_LINKING_REGISTERED = "genericUiNativeSharedLinkingRegistered"
        private const val EXTRA_TASK_CONFIGURED = "genericUiNativeSharedTaskConfigured"
        private val MANAGED_SHARED_LIB_REGEX = Regex(
            "^(lib)?(genericui|njs)(-[^.]+)?\\.(dylib|so|dll)$",
            RegexOption.IGNORE_CASE
        )

        private val WINDOWS_SYSTEM_LIBS = listOf(
            "gdi32",
            "user32",
            "comdlg32",
            "comctl32",
            "shell32",
            "ole32",
            "oleaut32",
            "uuid",
            "dwmapi",
            "uxtheme",
            "shlwapi",
            "imm32",
            "winmm",
            "ws2_32",
            "advapi32",
            "crypt32",
            "version",
            "ntdll",
            "rpcrt4",
            "winspool",
            "msimg32",
            "opengl32",
            "secur32",
            "usp10",
            "wininet"
        )

        internal fun isStaticPlatformCacheKlib(uniqueName: String, konanTarget: String): Boolean =
            GenericUiNativeSharedLibraryCatalog.isPlatformKlibUniqueName(uniqueName) &&
                (GenericUiNativeSharedLibraryCatalog.isLinuxKonanTarget(konanTarget) ||
                    GenericUiNativeSharedLibraryCatalog.isWindowsKonanTarget(konanTarget))

        internal fun buildKonanOverrideArg(
            hostKonan: String,
            konanTarget: String,
            includeLinuxLinkerOverride: Boolean
        ): String {
            val base = buildString {
                append("cacheableTargets.").append(hostKonan).append("=macos_arm64 macos_x64 linux_x64 linux_arm64 mingw_x64")
                if (konanTarget == "linux_arm64") {
                    append(";clangFlags.linux_arm64=-cc1 -emit-obj -disable-llvm-optzns -x ir -ffunction-sections -fdata-sections")
                }
            }
            if (!includeLinuxLinkerOverride) return base
            return when (konanTarget) {
                "linux_arm64" -> "$base;linkerKonanFlags.linux_arm64=-Bstatic -lstdc++ -Bdynamic -ldl -lm -lpthread --gc-sections"
                "linux_x64" -> "$base;linkerKonanFlags.linux_x64=-Bstatic -lstdc++ -Bdynamic -ldl -lm -lpthread --gc-sections"
                else -> base
            }
        }

        internal fun isManagedSharedLibraryFileName(fileName: String): Boolean =
            MANAGED_SHARED_LIB_REGEX.matches(fileName)

        @OptIn(KotlinNativeCacheApi::class)
        internal fun configureProject(project: Project, extension: GenericUiExtension) {
            val projectExtras = project.extensions.extraProperties
            if (projectExtras.has(EXTRA_LINKING_REGISTERED)) return
            projectExtras.set(EXTRA_LINKING_REGISTERED, true)

            val kmp = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
            val gradleUserHomePathValue = project.gradle.gradleUserHomeDir.absolutePath
            val buildDirProp = project.layout.buildDirectory

            kmp.targets.withType(KotlinNativeTarget::class.java).configureEach {
                val nativeTarget = this
                val targetName = nativeTarget.konanTarget.name
                if (!GenericUiNativeSharedLibraryCatalog.isNativeDesktopKonanTarget(targetName)) {
                    return@configureEach
                }

                nativeTarget.binaries.configureEach {
                    val binary = this
                    val kindName = binary.outputKind.name
                    if (kindName != "EXECUTABLE" && kindName != "TEST") {
                        return@configureEach
                    }

                    binary.disableNativeCache(
                        DisableCacheInKotlinVersion.`2_4_20`,
                        "GenericUI links core, backend, njs, extensions, and test modules as native shared libraries",
                        null
                    )

                    val linkTaskName = binary.linkTaskName
                    val linkTaskProvider = binary.linkTaskProvider
                    val variantProvider = project.provider {
                        resolveBackendVariant(linkTaskName, binary.name, kindName, targetName, extension)
                    }
                    val konanHomeDefault = org.jetbrains.kotlin.gradle.utils.NativeCompilerDownloader(project)
                        .compilerDirectory
                        .absolutePath
                    val konanDataDirDefault = System.getenv("KONAN_DATA_DIR")
                        ?.takeIf { it.isNotBlank() }
                        ?: File(System.getProperty("user.home"), ".konan").absolutePath
                    val resolvedKonanHomeProvider = project.providers.gradleProperty("kotlin.native.home")
                        .orElse(konanHomeDefault)
                    val resolvedKonanDataDirProvider = project.providers.gradleProperty("konan.data.dir")
                        .orElse(konanDataDirDefault)

                    val sharedTaskName = "buildGenericUiSharedLibsFor${linkTaskName.replaceFirstChar { it.uppercaseChar() }}"
                    val sharedLibsTask = if (project.tasks.findByName(sharedTaskName) != null) {
                        project.tasks.named(sharedTaskName, GenericUiBuildNativeSharedLibsTask::class.java)
                    } else {
                        project.tasks.register(sharedTaskName, GenericUiBuildNativeSharedLibsTask::class.java) {
                            group = GenericUiGradlePlugin.TASK_GROUP
                            description = "Builds dynamic shared libraries (libgenericui*, libgenericui-ext-*) for $linkTaskName."
                            libraries.from(binary.compilation.compileDependencyFiles)
                            konanTargetName.set(targetName)
                            backendVariant.set(variantProvider)
                            optimized.set(project.provider { binary.optimized })
                            debuggable.set(project.provider { binary.debuggable })
                            konanHomePath.set(resolvedKonanHomeProvider)
                            konanDataDirPath.set(resolvedKonanDataDirProvider)
                            gradleUserHomePath.set(gradleUserHomePathValue)
                            sharedLibsOutputDir.set(buildDirProp.dir("generated/genericui/nativeSharedLibs/$linkTaskName/libs"))
                            facadeCacheRootDir.set(buildDirProp.dir("generated/genericui/nativeSharedLibs/$linkTaskName/facades"))
                            cachedLibrariesManifestFile.set(buildDirProp.file("generated/genericui/nativeSharedLibs/$linkTaskName/cached-libraries.txt"))
                        }
                    }

                    val sharedLibsDirProvider = sharedLibsTask.flatMap { it.sharedLibsOutputDir }
                    val manifestFileProvider = sharedLibsTask.flatMap { it.cachedLibrariesManifestFile }

                    linkTaskProvider.configure {
                        val linkTask = this
                        val taskExtras = linkTask.extensions.extraProperties
                        if (taskExtras.has(EXTRA_TASK_CONFIGURED)) {
                            return@configure
                        }
                        taskExtras.set(EXTRA_TASK_CONFIGURED, true)

                        val konanHomeProvider = readKonanHomeProvider(linkTask)
                        val konanDataDirProvider = readKonanDataDirProvider(linkTask)

                        linkTask.dependsOn(sharedLibsTask)
                        linkTask.inputs.files(sharedLibsDirProvider)
                        linkTask.inputs.file(manifestFileProvider)

                        val isOptimized = linkTask.optimized
                        val outFileProvider = linkTask.outputFile
                        val destDirProvider = linkTask.destinationDirectory

                        linkTask.setActions(emptyList())
                        linkTask.doLast(Action<Task> {
                            val resolvedTask = this as KotlinNativeLink
                            executeDynamicSharedLink(
                                linkTask = resolvedTask,
                                targetName = targetName,
                                isOptimized = isOptimized,
                                konanHomeDir = File(konanHomeProvider.get()),
                                konanDataDir = konanDataDirProvider.orNull?.takeIf { it.isNotBlank() }?.let { File(it) },
                                gradleUserHomePathValue = gradleUserHomePathValue,
                                sharedLibsDir = sharedLibsDirProvider.get().asFile,
                                manifestFile = manifestFileProvider.get().asFile,
                                outFile = outFileProvider.get(),
                                destDir = destDirProvider.get().asFile
                            )
                        })
                    }
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun readKonanHomeProvider(linkTask: KotlinNativeLink): Provider<String> {
            return linkTask.javaClass.getMethod("getKonanHome").invoke(linkTask) as Provider<String>
        }

        private fun readKonanDataDirProvider(linkTask: KotlinNativeLink): Provider<String> {
            val raw = linkTask.javaClass.getMethod("getKonanDataDir").invoke(linkTask) as Provider<*>
            return raw.map { it.toString() }.orElse("")
        }

        private fun resolveBackendVariant(
            linkTaskName: String,
            binaryName: String,
            kindName: String,
            targetName: String,
            extension: GenericUiExtension
        ): String {
            return when {
                GenericUiNativeSharedLibraryCatalog.isMacOsKonanTarget(targetName) -> "macos"
                GenericUiNativeSharedLibraryCatalog.isWindowsKonanTarget(targetName) -> "windows"
                linkTaskName.contains("Qt") || binaryName.startsWith("qt", ignoreCase = true) -> "qt"
                linkTaskName.contains("Gtk") || binaryName.startsWith("gtk", ignoreCase = true) -> "gtk"
                kindName == "TEST" && extension.backends.gtkEnabled && extension.backends.qtEnabled -> "linux"
                !extension.backends.gtkEnabled && extension.backends.qtEnabled -> "qt"
                else -> "gtk"
            }
        }

        private fun executeDynamicSharedLink(
            linkTask: KotlinNativeLink,
            targetName: String,
            isOptimized: Boolean,
            konanHomeDir: File,
            konanDataDir: File?,
            gradleUserHomePathValue: String,
            sharedLibsDir: File,
            manifestFile: File,
            outFile: File,
            destDir: File
        ) {
            val baseArgs = buildBaseCompilerArgs(linkTask)
            val externalArgs = readExternalDependencyBuildArgs(linkTask)

            val filteredArgs = mutableListOf<String>()
            var skipNext = false
            for (arg in baseArgs + externalArgs) {
                if (skipNext) {
                    skipNext = false
                    continue
                }
                when {
                    arg == "-Xcached-library" ||
                        arg == "-Xcache-directory" ||
                        arg == "-Xauto-cache-from" ||
                        arg == "-Xoverride-konan-properties" -> {
                        skipNext = true
                    }
                    arg.startsWith("-Xcached-library=") ||
                        arg.startsWith("-Xcache-directory=") ||
                        arg.startsWith("-Xauto-cache-from=") ||
                        arg.startsWith("-Xoverride-konan-properties=") ||
                        arg == "-Xmake-per-file-cache" -> {
                        // Managed explicitly below.
                    }
                    else -> filteredArgs.add(arg)
                }
            }

            if (manifestFile.isFile) {
                manifestFile.readLines(Charsets.UTF_8)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { line ->
                        val parts = line.split('\t', limit = 2)
                        if (parts.size == 2) {
                            filteredArgs.add("-Xcached-library=${parts[0]},${parts[1]}")
                        }
                    }
            }

            val hostKonan = GenericUiKonanCompilerShim.hostKonanTargetName()
            filteredArgs.add(
                "-Xoverride-konan-properties=${buildKonanOverrideArg(hostKonan, targetName, includeLinuxLinkerOverride = true)}"
            )
            if (GenericUiNativeSharedLibraryCatalog.isWindowsKonanTarget(targetName)) {
                filteredArgs.add("-linker-option")
                filteredArgs.add("-Wl,--allow-multiple-definition")
                filteredArgs.add("-linker-option")
                filteredArgs.add("-Wl,--gc-sections")
                filteredArgs.add("-linker-option")
                filteredArgs.add("-lbcrypt")
                for (winLib in WINDOWS_SYSTEM_LIBS) {
                    filteredArgs.add("-linker-option")
                    filteredArgs.add("-l$winLib")
                }
            }
            if (isOptimized) {
                filteredArgs.add("-Xbinary=enableReleaseBinaryCache=true")
            }

            outFile.parentFile?.mkdirs()
            destDir.mkdirs()
            if (outFile.exists()) {
                outFile.delete()
            }

            val shimDir = File(gradleUserHomePathValue, "caches/genericui-native-shared/shims/${konanHomeDir.name}")
            val shimJar = GenericUiKonanCompilerShim.ensureShimJar(konanHomeDir, shimDir)
            GenericUiKonanCompilerShim.runKonanc(
                konanHomeDir = konanHomeDir,
                shimJar = shimJar,
                konanDataDir = konanDataDir,
                args = filteredArgs
            )

            // Sync built shared libraries into destDir (alongside executable / test binary).
            val activeNames = sharedLibsDir.listFiles()
                ?.filter {
                    val p = it.toPath()
                    (Files.isSymbolicLink(p) || Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) &&
                        isManagedSharedLibraryFileName(it.name)
                }
                ?.map { it.name }
                ?.toSet()
                .orEmpty()

            destDir.listFiles()?.forEach { existing ->
                val ep = existing.toPath()
                if ((Files.isSymbolicLink(ep) || Files.isRegularFile(ep, LinkOption.NOFOLLOW_LINKS)) &&
                    (existing.name.lowercase(Locale.ROOT).endsWith(".dll.a") ||
                        (isManagedSharedLibraryFileName(existing.name) && existing.name !in activeNames))
                ) {
                    GenericUiSymlinkPreservingCopy.deletePathNoFollow(ep)
                }
            }

            val isMac = GenericUiNativeSharedLibraryCatalog.isMacOsKonanTarget(targetName)
            val isWin = GenericUiNativeSharedLibraryCatalog.isWindowsKonanTarget(targetName)
            sharedLibsDir.listFiles()
                ?.filter {
                    val p = it.toPath()
                    (Files.isSymbolicLink(p) || Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) &&
                        isManagedSharedLibraryFileName(it.name)
                }
                ?.sortedWith(
                    compareBy<File> { if (Files.isSymbolicLink(it.toPath())) 1 else 0 }
                        .thenBy { it.name }
                )
                ?.forEach { src ->
                    val dst = File(destDir, src.name)
                    GenericUiSymlinkPreservingCopy.copyEntryPreservingSymlinks(
                        src = src,
                        dst = dst,
                        sourceRoot = sharedLibsDir,
                        setExecutableOnBinaries = !isWin
                    )
                    if (isMac && !Files.isSymbolicLink(dst.toPath())) {
                        signMacBinary(dst)
                    }
                }

            if (isMac && outFile.isFile) {
                signMacBinary(outFile)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun buildBaseCompilerArgs(linkTask: KotlinNativeLink): List<String> {
            val ctxClass = Class.forName(
                "org.jetbrains.kotlin.gradle.plugin.KotlinCompilerArgumentsProducer\$CreateCompilerArgumentsContext"
            )
            val companionObj = ctxClass.getField("Companion").get(null)
            val defaultCtx = companionObj.javaClass.getMethod("getDefault").invoke(companionObj)
            val argsObj = linkTask.javaClass
                .getMethod("createCompilerArguments", ctxClass)
                .invoke(linkTask, defaultCtx)
            val commonToolArgsClass = Class.forName("org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments")
            val argUtilsClass = Class.forName("org.jetbrains.kotlin.compilerRunner.ArgumentUtils")
            return argUtilsClass
                .getMethod("convertArgumentsToStringList", commonToolArgsClass)
                .invoke(null, argsObj) as List<String>
        }

        private fun readExternalDependencyBuildArgs(linkTask: KotlinNativeLink): List<String> {
            val getter = linkTask.javaClass.methods.firstOrNull {
                it.name.startsWith("getExternalDependenciesBuildCompilerArgs") && it.parameterCount == 0
            } ?: return emptyList()
            val prop = runCatching { getter.invoke(linkTask) }.getOrNull() ?: return emptyList()
            val orNullMethod = prop.javaClass.methods.firstOrNull { it.name == "getOrNull" && it.parameterCount == 0 }
                ?: return emptyList()
            val raw = runCatching { orNullMethod.invoke(prop) as? List<*> }.getOrNull() ?: return emptyList()
            return raw.mapNotNull { it?.toString() }
        }

        internal fun signMacBinary(file: File) {
            if (Files.isSymbolicLink(file.toPath())) return
            val pb = ProcessBuilder("codesign", "--force", "--sign", "-", file.absolutePath)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val exit = proc.waitFor()
            if (exit != 0) {
                throw GradleException(
                    "codesign failed (${exit}) for ${file.absolutePath}: ${out.trimEnd()}"
                )
            }
        }
    }
}
