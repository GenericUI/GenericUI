package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * DSL container for declaring Android `<service>` components inside `genericui { android { services { ... } } }`.
 */
open class GenericUiAndroidServicesConfig {
    val services: MutableList<GenericUiAndroidServiceSpec> = mutableListOf()

    /** Registers or updates an Android service by [className] and configures it with [action]. */
    fun service(
        className: String,
        action: Action<in GenericUiAndroidServiceSpec>? = null
    ): GenericUiAndroidServiceSpec {
        val trimmed = className.trim()
        require(trimmed.isNotEmpty()) { "Android service className must not be blank." }
        val existing = services.firstOrNull { it.className == trimmed }
        val spec = existing ?: GenericUiAndroidServiceSpec(trimmed).also { services.add(it) }
        action?.execute(spec)
        return spec
    }

    /** Registers an Android service by [className] with default settings. */
    fun service(className: String): GenericUiAndroidServiceSpec =
        service(className, null)
}
