package com.nexustools.genericui.plugin

import org.gradle.api.Action
import java.io.Serializable

/**
 * DSL configuration for Android 11+ `<queries>` package visibility declarations inside
 * `genericui { android { manifest { queries { ... } } } }`.
 */
open class GenericUiAndroidQueriesConfig : Serializable {
    val packageNames: MutableList<String> = mutableListOf()
    val intents: MutableList<GenericUiAndroidIntentFilterSpec> = mutableListOf()
    val providerAuthorities: MutableList<String> = mutableListOf()

    /** Adds a `<package android:name="..."/>` visibility entry. */
    fun packageName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && trimmed !in packageNames) {
            packageNames.add(trimmed)
        }
    }

    /** Adds an `<intent>` query matching a single `<action android:name="..."/>`. */
    fun intentAction(action: String) {
        val trimmed = action.trim()
        if (trimmed.isNotEmpty()) {
            intents.add(GenericUiAndroidIntentFilterSpec().apply { action(trimmed) })
        }
    }

    /** Configures and appends a full `<intent>` query specification. */
    fun intent(action: Action<in GenericUiAndroidIntentFilterSpec>) {
        val spec = GenericUiAndroidIntentFilterSpec()
        action.execute(spec)
        intents.add(spec)
    }

    /** Adds a `<provider android:authorities="..."/>` visibility entry. */
    fun providerAuthority(authority: String) {
        val trimmed = authority.trim()
        if (trimmed.isNotEmpty() && trimmed !in providerAuthorities) {
            providerAuthorities.add(trimmed)
        }
    }

    /** Returns `true` if any `<queries>` entries are configured. */
    fun isNotEmpty(): Boolean =
        packageNames.isNotEmpty() || intents.isNotEmpty() || providerAuthorities.isNotEmpty()

    /** Merges [overlay] queries onto a copy of this configuration. */
    fun overlayWith(overlay: GenericUiAndroidQueriesConfig): GenericUiAndroidQueriesConfig =
        copySpec().apply {
            overlay.packageNames.forEach { packageName(it) }
            overlay.intents.mapTo(intents) { it.copySpec() }
            overlay.providerAuthorities.forEach { providerAuthority(it) }
        }

    /** Returns an independent deep copy of this queries configuration. */
    fun copySpec(): GenericUiAndroidQueriesConfig = GenericUiAndroidQueriesConfig().also { copy ->
        copy.packageNames.addAll(packageNames)
        intents.mapTo(copy.intents) { it.copySpec() }
        copy.providerAuthorities.addAll(providerAuthorities)
    }
}
