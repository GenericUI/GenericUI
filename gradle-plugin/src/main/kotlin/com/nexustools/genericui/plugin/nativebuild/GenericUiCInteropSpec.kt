package com.nexustools.genericui.plugin.nativebuild

import java.io.Serializable

/**
 * Declarative specification for a Kotlin/Native `cinterop` binding inside
 * `cinterops { create(name) { ... } }`.
 */
open class GenericUiCInteropSpec(val name: String) : Serializable {

    var defFile: Any? = null
    var packageName: String? = null
    val headers: MutableList<Any> = mutableListOf()
    val includeDirs: MutableList<Any> = mutableListOf()
    val compilerOpts: MutableList<String> = mutableListOf()
    val linkerOpts: MutableList<String> = mutableListOf()
    val extraOpts: MutableList<String> = mutableListOf()
    var compilationName: String = "main"

    /** Sets the `.def` definition file path. */
    fun defFile(file: Any) {
        defFile = file
    }

    /** Sets the generated Kotlin interop package name. */
    fun packageName(pkg: String) {
        packageName = pkg.trim().takeIf { it.isNotEmpty() }
    }

    /** Adds C/Objective-C header files to bind. */
    fun headers(vararg files: Any) {
        headers.addAll(files)
    }

    /** Adds header search directories passed to cinterop (`-I...`). */
    fun includeDirs(vararg dirs: Any) {
        includeDirs.addAll(dirs)
    }

    /** Adds compiler options forwarded to clang during cinterop indexing. */
    fun compilerOpts(vararg opts: String) {
        opts.map { it.trim() }.filter { it.isNotEmpty() }.forEach { compilerOpts.add(it) }
    }

    /** Adds linker options stored in the cinterop klib manifest. */
    fun linkerOpts(vararg opts: String) {
        opts.map { it.trim() }.filter { it.isNotEmpty() }.forEach { linkerOpts.add(it) }
    }

    /** Adds extra `cinterop` CLI options. */
    fun extraOpts(vararg opts: String) {
        opts.map { it.trim() }.filter { it.isNotEmpty() }.forEach { extraOpts.add(it) }
    }
}
