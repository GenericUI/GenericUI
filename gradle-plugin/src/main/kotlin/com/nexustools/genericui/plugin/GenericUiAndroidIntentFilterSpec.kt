package com.nexustools.genericui.plugin

import org.gradle.api.Action
import java.io.Serializable

/**
 * Specification for an Android `<intent-filter>` (or `<queries><intent>`) block.
 */
open class GenericUiAndroidIntentFilterSpec : Serializable {
    val actions: MutableList<String> = mutableListOf()
    val categories: MutableList<String> = mutableListOf()
    val dataSpecs: MutableList<GenericUiAndroidIntentDataSpec> = mutableListOf()
    var autoVerify: Boolean = false
    var priority: Int? = null
    var label: String? = null

    /** Adds an `<action android:name="..."/>` entry to this filter. */
    fun action(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && trimmed !in actions) {
            actions.add(trimmed)
        }
    }

    /** Adds a `<category android:name="..."/>` entry to this filter. */
    fun category(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && trimmed !in categories) {
            categories.add(trimmed)
        }
    }

    /** Configures and appends a `<data .../>` entry to this filter. */
    fun data(action: Action<in GenericUiAndroidIntentDataSpec>) {
        val spec = GenericUiAndroidIntentDataSpec()
        action.execute(spec)
        dataSpecs.add(spec)
    }

    /** Returns an independent deep copy of this intent filter specification. */
    fun copySpec(): GenericUiAndroidIntentFilterSpec = GenericUiAndroidIntentFilterSpec().also { copy ->
        copy.actions.addAll(actions)
        copy.categories.addAll(categories)
        dataSpecs.mapTo(copy.dataSpecs) { it.copySpec() }
        copy.autoVerify = autoVerify
        copy.priority = priority
        copy.label = label
    }
}
