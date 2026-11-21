package com.nexustools.genericui.plugin.nativebuild

import org.gradle.api.Action
import java.io.Serializable

/**
 * DSL container (`cinterops { create(name) { ... } }`) wired across matching Kotlin/Native targets
 * and compilations by `GenericUiMultiplatformConfigurator`.
 */
open class GenericUiCInteropConfig : Serializable {

    val items: MutableMap<String, GenericUiCInteropSpec> = linkedMapOf()

    /** Creates or updates a [GenericUiCInteropSpec] by [name]. */
    fun create(name: String, action: Action<in GenericUiCInteropSpec>): GenericUiCInteropSpec {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "CInterop name must not be blank." }
        val spec = items.getOrPut(trimmed) { GenericUiCInteropSpec(trimmed) }
        action.execute(spec)
        return spec
    }

    /** Creates or retrieves a [GenericUiCInteropSpec] by [name]. */
    fun create(name: String): GenericUiCInteropSpec {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "CInterop name must not be blank." }
        return items.getOrPut(trimmed) { GenericUiCInteropSpec(trimmed) }
    }

    /** Alias for [create]. */
    fun maybeCreate(
        name: String,
        action: Action<in GenericUiCInteropSpec>? = null
    ): GenericUiCInteropSpec {
        val spec = create(name)
        action?.execute(spec)
        return spec
    }

    /** Returns all registered [GenericUiCInteropSpec] entries in insertion order. */
    fun all(): List<GenericUiCInteropSpec> = items.values.toList()
}
