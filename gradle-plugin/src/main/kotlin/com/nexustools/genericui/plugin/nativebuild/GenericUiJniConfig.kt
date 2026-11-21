package com.nexustools.genericui.plugin.nativebuild

import org.gradle.api.Action
import java.io.Serializable

/**
 * DSL container (`jni { ... }`) for consumer prebuilt `jniLibsDirs`, C/C++ JNI [libraries], and
 * external CMake [cmakes] builds.
 */
open class GenericUiJniConfig : Serializable {

    val jniLibsDirs: MutableList<Any> = mutableListOf()
    val libraries: MutableMap<String, GenericUiJniLibrarySpec> = linkedMapOf()
    val cmakes: MutableList<GenericUiJniCmakeSpec> = mutableListOf()

    /** Registers one or more prebuilt `<dir>/<abi>/` shared library directories to stage into APKs. */
    fun jniLibsDirs(vararg paths: Any) {
        jniLibsDirs.addAll(paths)
    }

    /** Registers a single prebuilt `<dir>/<abi>/` shared library directory to stage into APKs. */
    fun jniLibsDir(path: Any) {
        jniLibsDirs.add(path)
    }

    /** Registers or retrieves a JNI library build specification by [name]. */
    fun library(name: String): GenericUiJniLibrarySpec {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "JNI library name must not be blank." }
        return libraries.getOrPut(trimmed) { GenericUiJniLibrarySpec(trimmed) }
    }

    /** Registers or updates a JNI library build specification by [name]. */
    fun library(name: String, action: Action<in GenericUiJniLibrarySpec>): GenericUiJniLibrarySpec {
        val spec = library(name)
        action.execute(spec)
        return spec
    }

    /** Registers or retrieves an external CMake native build specification by [name]. */
    fun cmake(name: String): GenericUiJniCmakeSpec {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "CMake target name must not be blank." }
        return cmakes.firstOrNull { it.name == trimmed }
            ?: GenericUiJniCmakeSpec(trimmed).also { cmakes.add(it) }
    }

    /** Registers or updates an external CMake native build specification by [name]. */
    fun cmake(name: String, action: Action<in GenericUiJniCmakeSpec>): GenericUiJniCmakeSpec {
        val spec = cmake(name)
        action.execute(spec)
        return spec
    }

    /** Returns `true` when at least one `jniLibsDir`, `library`, or `cmake` entry is configured. */
    fun hasAnyConfiguration(): Boolean =
        jniLibsDirs.isNotEmpty() || libraries.isNotEmpty() || cmakes.isNotEmpty()
}
