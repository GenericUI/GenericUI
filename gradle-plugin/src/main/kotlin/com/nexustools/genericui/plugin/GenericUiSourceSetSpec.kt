package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * DSL specification for a single GenericUI multiplatform sourceSet inside `genericui { sourceSets { ... } }`.
 *
 * Supports declaring `implementation(...)`, `api(...)`, `compileOnly(...)`, and `runtimeOnly(...)`
 * dependencies directly or inside a nested `dependencies { ... }` block, as well as adding extra
 * Kotlin source and resource directories.
 */
open class GenericUiSourceSetSpec(
    val name: String
) {
    val implementationDependencies: MutableList<Any> = mutableListOf()
    val apiDependencies: MutableList<Any> = mutableListOf()
    val compileOnlyDependencies: MutableList<Any> = mutableListOf()
    val runtimeOnlyDependencies: MutableList<Any> = mutableListOf()
    val kotlinSrcDirs: MutableList<Any> = mutableListOf()
    val resourcesSrcDirs: MutableList<Any> = mutableListOf()

    /** Adds an `implementation` dependency to this GenericUI source set. */
    fun implementation(dependencyNotation: Any) {
        implementationDependencies.add(dependencyNotation)
    }

    /** Adds an `api` dependency to this GenericUI source set. */
    fun api(dependencyNotation: Any) {
        apiDependencies.add(dependencyNotation)
    }

    /** Adds a `compileOnly` dependency to this GenericUI source set. */
    fun compileOnly(dependencyNotation: Any) {
        compileOnlyDependencies.add(dependencyNotation)
    }

    /** Adds a `runtimeOnly` dependency to this GenericUI source set. */
    fun runtimeOnly(dependencyNotation: Any) {
        runtimeOnlyDependencies.add(dependencyNotation)
    }

    /** Optional nested `dependencies { ... }` block for familiarity with Kotlin Multiplatform DSL. */
    fun dependencies(action: Action<in GenericUiSourceSetSpec>) {
        action.execute(this)
    }

    /**
     * Registers an additional Kotlin source directory (relative path `String`, `File`, `Provider`, or `TaskProvider`)
     * for this source set, allowing companion plugins (such as `com.nexustools.njs`) to wire generated sources.
     */
    fun kotlinSrcDir(srcDir: Any) {
        kotlinSrcDirs.add(srcDir)
    }

    /**
     * Registers an additional resources directory (relative path `String`, `File`, `Provider`, or `TaskProvider`)
     * for this source set.
     */
    fun resourcesSrcDir(resDir: Any) {
        resourcesSrcDirs.add(resDir)
    }
}
