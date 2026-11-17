package com.nexustools.genericui.plugin

/**
 * Per-fragment Android `<activity>` configuration override registered via
 * `genericui { android { manifest { activity("MyFragment") { ... } } } }`.
 */
open class GenericUiAndroidActivityOverrideSpec(
    val fragmentName: String
) : GenericUiAndroidActivityDefaultsSpec() {

    /** Returns an independent deep copy of this activity override spec. */
    fun copyOverride(): GenericUiAndroidActivityOverrideSpec =
        GenericUiAndroidActivityOverrideSpec(fragmentName).also { copyInto(it) }
}
