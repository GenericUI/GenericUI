package com.nexustools.genericui.plugin.nativebuild

import java.io.Serializable

/**
 * Declarative configuration for an external CMake native build (`genericui { jni { cmake("name") { ... } } }`).
 */
open class GenericUiJniCmakeSpec(val name: String) : Serializable {

    var listsFile: Any? = null
    val arguments: MutableList<String> = mutableListOf()
    val targets: MutableList<String> = mutableListOf()
    val abis: MutableList<String> = mutableListOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    var minApiLevel: Int = 24
    var pageSize16Kb: Boolean = true
    var buildType: String = "Release"

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

    /** Sets the `CMakeLists.txt` file path or directory containing `CMakeLists.txt`. */
    fun listsFile(path: Any) {
        listsFile = path
    }

    /** Appends `-D...` or CMake configure arguments. */
    fun arguments(vararg args: String) {
        args.map { it.trim() }.filter { it.isNotEmpty() }.forEach { arguments.add(it) }
    }

    /** Specifies specific CMake targets to build (builds default all target when empty). */
    fun targets(vararg targetNames: String) {
        targetNames.map { it.trim() }.filter { it.isNotEmpty() }.forEach { targets.add(it) }
    }

    /** Overrides the target Android ABIs for this CMake build. */
    fun abis(vararg targetAbis: String) {
        val normalized = targetAbis.map { it.trim() }.filter { it.isNotEmpty() }
        if (normalized.isNotEmpty()) {
            abis.clear()
            abis.addAll(normalized.distinct())
            abisExplicitlySet = true
        }
    }

    /** Creates a serializable snapshot with normalized string paths and scope-aware build defaults. */
    fun toSnapshot(
        pathNormalizer: (Any) -> String,
        defaultBuildAndroid: Boolean = buildAndroidLibraries,
        defaultBuildHostTests: Boolean = buildHostLibraryForTests,
        defaultBundleSwing: Boolean = bundleInSwingJar
    ): GenericUiJniCmakeSpec {
        val snap = GenericUiJniCmakeSpec(name)
        snap.listsFile = listsFile?.let(pathNormalizer)?.takeIf { it.isNotBlank() }
        snap.arguments.addAll(arguments)
        snap.targets.addAll(targets)
        snap.abis.clear()
        snap.abis.addAll(abis)
        snap.abisExplicitlySet = abisExplicitlySet
        snap.minApiLevel = minApiLevel
        snap.pageSize16Kb = pageSize16Kb
        snap.buildType = buildType
        snap.buildAndroidLibraries = if (buildAndroidLibrariesExplicitlySet) buildAndroidLibraries else defaultBuildAndroid
        snap.buildHostLibraryForTests = if (buildHostLibraryForTestsExplicitlySet) buildHostLibraryForTests else defaultBuildHostTests
        snap.bundleInSwingJar = if (bundleInSwingJarExplicitlySet) bundleInSwingJar else defaultBundleSwing
        return snap
    }
}
