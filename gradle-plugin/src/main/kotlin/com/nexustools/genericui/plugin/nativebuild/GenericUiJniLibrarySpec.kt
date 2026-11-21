package com.nexustools.genericui.plugin.nativebuild

import java.io.Serializable

/**
 * Declarative configuration for a consumer C/C++ JNI shared library compiled for Android ABIs
 * (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) and/or the host JVM (`buildHostLibraryForTests` /
 * `bundleInSwingJar`).
 */
open class GenericUiJniLibrarySpec(val name: String) : Serializable {

    var sourceArchiveUrl: String? = null
    var sourceArchiveSha256: String? = null
    var sourceArchiveStripComponents: Int = 0

    val sources: MutableList<Any> = mutableListOf()
    val archiveSources: MutableList<String> = mutableListOf()
    val excludes: MutableList<String> = mutableListOf()
    val includeDirs: MutableList<Any> = mutableListOf()
    val archiveIncludeDirs: MutableList<String> = mutableListOf()
    val defines: MutableMap<String, String?> = linkedMapOf()
    val cFlags: MutableList<String> = mutableListOf()
    val cppFlags: MutableList<String> = mutableListOf()
    val linkerFlags: MutableList<String> = mutableListOf()
    val androidLinkLibraries: MutableList<String> = mutableListOf()
    val hostLinkLibraries: MutableList<String> = mutableListOf()
    val abis: MutableList<String> = mutableListOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    val jniHeaderSources: MutableList<Any> = mutableListOf()

    var minApiLevel: Int = 24
    var pageSize16Kb: Boolean = true
    var generateJniHeaders: Boolean = true
    var cStandard: String = "c11"
    var cppStandard: String = "c++17"

    internal var buildHostLibraryForTestsExplicitlySet: Boolean = false
    var buildHostLibraryForTests: Boolean = true
        set(value) {
            field = value
            buildHostLibraryForTestsExplicitlySet = true
        }

    internal var bundleInSwingJarExplicitlySet: Boolean = false
    var bundleInSwingJar: Boolean = true
        set(value) {
            field = value
            bundleInSwingJarExplicitlySet = true
        }

    internal var buildAndroidLibrariesExplicitlySet: Boolean = false
    var buildAndroidLibraries: Boolean = true
        set(value) {
            field = value
            buildAndroidLibrariesExplicitlySet = true
        }

    internal var abisExplicitlySet: Boolean = false

    /** Configures a remote `.tar.gz`/`.tgz`/`.zip` source archive with optional SHA-256 verification. */
    fun sourceArchive(url: String, sha256: String? = null) {
        sourceArchiveUrl = url.trim().takeIf { it.isNotEmpty() }
        sourceArchiveSha256 = sha256?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Configures a remote source archive with optional SHA-256 verification and stripped leading path components. */
    fun sourceArchive(url: String, sha256: String?, stripComponents: Int) {
        sourceArchive(url, sha256)
        sourceArchiveStripComponents = stripComponents.coerceAtLeast(0)
    }

    /** Adds local C/C++ source files, directories, or glob patterns. */
    fun sources(vararg paths: Any) {
        sources.addAll(paths)
    }

    /** Adds source file or glob patterns relative to the extracted [sourceArchiveUrl] root. */
    fun archiveSources(vararg patterns: String) {
        patterns.map { it.trim() }.filter { it.isNotEmpty() }.forEach { archiveSources.add(it) }
    }

    /** Adds file or glob patterns to exclude from compilation. */
    fun exclude(vararg patterns: String) {
        patterns.map { it.trim() }.filter { it.isNotEmpty() }.forEach { excludes.add(it) }
    }

    /** Adds local C/C++ header include directories. */
    fun includeDirs(vararg dirs: Any) {
        includeDirs.addAll(dirs)
    }

    /** Adds include directories relative to the extracted [sourceArchiveUrl] root. */
    fun archiveIncludeDirs(vararg dirs: String) {
        dirs.map { it.trim() }.filter { it.isNotEmpty() }.forEach { archiveIncludeDirs.add(it) }
    }

    /** Adds preprocessor macro definitions (`"KEY=VALUE"` or `"FLAG"`). */
    fun defines(vararg entries: String) {
        for (raw in entries) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                defines[trimmed.substring(0, eqIdx).trim()] = trimmed.substring(eqIdx + 1)
            } else {
                defines[trimmed] = null
            }
        }
    }

    /** Adds preprocessor macro definitions as `(key, value)` pairs. */
    fun defines(vararg pairs: Pair<String, String?>) {
        for ((k, v) in pairs) {
            val key = k.trim()
            if (key.isNotEmpty()) {
                defines[key] = v
            }
        }
    }

    /** Adds or updates a single preprocessor macro definition. */
    fun define(key: String, value: String? = null) {
        val trimmed = key.trim()
        if (trimmed.isNotEmpty()) {
            defines[trimmed] = value
        }
    }

    /** Adds compiler flags used when compiling C translation units. */
    fun cFlags(vararg flags: String) {
        flags.map { it.trim() }.filter { it.isNotEmpty() }.forEach { cFlags.add(it) }
    }

    /** Adds compiler flags used when compiling C++ translation units. */
    fun cppFlags(vararg flags: String) {
        flags.map { it.trim() }.filter { it.isNotEmpty() }.forEach { cppFlags.add(it) }
    }

    /** Adds linker flags forwarded when linking Android `.so` and host shared libraries. */
    fun linkerFlags(vararg flags: String) {
        flags.map { it.trim() }.filter { it.isNotEmpty() }.forEach { linkerFlags.add(it) }
    }

    /** Adds Android system libraries to link (e.g. `"log"`, `"android"`, `"OpenSLES"`). */
    fun androidLinkLibraries(vararg libs: String) {
        libs.map { it.trim().removePrefix("-l") }.filter { it.isNotEmpty() }.forEach {
            androidLinkLibraries.add(it)
        }
    }

    /** Adds host system libraries to link when building host test/Swing shared libraries. */
    fun hostLinkLibraries(vararg libs: String) {
        libs.map { it.trim().removePrefix("-l") }.filter { it.isNotEmpty() }.forEach {
            hostLinkLibraries.add(it)
        }
    }

    /** Overrides the target Android ABIs to compile for this library. */
    fun abis(vararg targetAbis: String) {
        val normalized = targetAbis.map { it.trim() }.filter { it.isNotEmpty() }
        if (normalized.isNotEmpty()) {
            abis.clear()
            abis.addAll(normalized.distinct())
            abisExplicitlySet = true
        }
    }

    /** Adds `.java` source files/directories for optional `javac -h` JNI header generation. */
    fun jniHeaderSources(vararg paths: Any) {
        jniHeaderSources.addAll(paths)
    }

    /** Creates a serializable snapshot with normalized string paths and scope-aware build defaults. */
    fun toSnapshot(
        pathNormalizer: (Any) -> String,
        defaultBuildAndroid: Boolean = buildAndroidLibraries,
        defaultBuildHostTests: Boolean = buildHostLibraryForTests,
        defaultBundleSwing: Boolean = bundleInSwingJar
    ): GenericUiJniLibrarySpec {
        val snap = GenericUiJniLibrarySpec(name)
        snap.sourceArchiveUrl = sourceArchiveUrl
        snap.sourceArchiveSha256 = sourceArchiveSha256
        snap.sourceArchiveStripComponents = sourceArchiveStripComponents
        snap.sources.addAll(sources.map(pathNormalizer).filter { it.isNotBlank() })
        snap.archiveSources.addAll(archiveSources)
        snap.excludes.addAll(excludes)
        snap.includeDirs.addAll(includeDirs.map(pathNormalizer).filter { it.isNotBlank() })
        snap.archiveIncludeDirs.addAll(archiveIncludeDirs)
        snap.defines.putAll(defines)
        snap.cFlags.addAll(cFlags)
        snap.cppFlags.addAll(cppFlags)
        snap.linkerFlags.addAll(linkerFlags)
        snap.androidLinkLibraries.addAll(androidLinkLibraries)
        snap.hostLinkLibraries.addAll(hostLinkLibraries)
        snap.abis.clear()
        snap.abis.addAll(abis)
        snap.abisExplicitlySet = abisExplicitlySet
        snap.jniHeaderSources.addAll(jniHeaderSources.map(pathNormalizer).filter { it.isNotBlank() })
        snap.minApiLevel = minApiLevel
        snap.pageSize16Kb = pageSize16Kb
        snap.generateJniHeaders = generateJniHeaders
        snap.cStandard = cStandard
        snap.cppStandard = cppStandard
        snap.buildAndroidLibraries = if (buildAndroidLibrariesExplicitlySet) buildAndroidLibraries else defaultBuildAndroid
        snap.buildHostLibraryForTests = if (buildHostLibraryForTestsExplicitlySet) buildHostLibraryForTests else defaultBuildHostTests
        snap.bundleInSwingJar = if (bundleInSwingJarExplicitlySet) bundleInSwingJar else defaultBundleSwing
        return snap
    }

    /** Merges explicit settings from [overrideSpec] into this spec. */
    fun mergeFrom(overrideSpec: GenericUiJniLibrarySpec) {
        overrideSpec.sourceArchiveUrl?.let { sourceArchiveUrl = it }
        overrideSpec.sourceArchiveSha256?.let { sourceArchiveSha256 = it }
        if (overrideSpec.sourceArchiveStripComponents > 0) {
            sourceArchiveStripComponents = overrideSpec.sourceArchiveStripComponents
        }
        sources.addAll(overrideSpec.sources)
        archiveSources.addAll(overrideSpec.archiveSources)
        excludes.addAll(overrideSpec.excludes)
        includeDirs.addAll(overrideSpec.includeDirs)
        archiveIncludeDirs.addAll(overrideSpec.archiveIncludeDirs)
        defines.putAll(overrideSpec.defines)
        cFlags.addAll(overrideSpec.cFlags)
        cppFlags.addAll(overrideSpec.cppFlags)
        linkerFlags.addAll(overrideSpec.linkerFlags)
        androidLinkLibraries.addAll(overrideSpec.androidLinkLibraries)
        hostLinkLibraries.addAll(overrideSpec.hostLinkLibraries)
        if (overrideSpec.abisExplicitlySet) {
            abis.clear()
            abis.addAll(overrideSpec.abis)
            abisExplicitlySet = true
        }
        jniHeaderSources.addAll(overrideSpec.jniHeaderSources)
        minApiLevel = overrideSpec.minApiLevel
        pageSize16Kb = overrideSpec.pageSize16Kb
        generateJniHeaders = overrideSpec.generateJniHeaders
        cStandard = overrideSpec.cStandard
        cppStandard = overrideSpec.cppStandard
        if (overrideSpec.buildAndroidLibrariesExplicitlySet) {
            buildAndroidLibraries = overrideSpec.buildAndroidLibraries
        }
        if (overrideSpec.buildHostLibraryForTestsExplicitlySet) {
            buildHostLibraryForTests = overrideSpec.buildHostLibraryForTests
        }
        if (overrideSpec.bundleInSwingJarExplicitlySet) {
            bundleInSwingJar = overrideSpec.bundleInSwingJar
        }
    }
}
