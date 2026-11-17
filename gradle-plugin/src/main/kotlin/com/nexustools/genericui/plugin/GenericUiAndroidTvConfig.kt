package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Android TV / Leanback manifest configuration (`genericui { android { manifest { androidTv { ... } } } }`).
 */
open class GenericUiAndroidTvConfig : Serializable {
    internal val explicitFields: MutableSet<String> = linkedSetOf()

    var enabled: Boolean = false
        set(value) {
            field = value
            explicitFields.add("enabled")
        }

    var required: Boolean = false
        set(value) {
            field = value
            explicitFields.add("required")
        }

    var banner: String? = null
        set(value) {
            field = value
            explicitFields.add("banner")
        }

    /** Merges explicit fields from [overlay] onto a copy of this configuration. */
    fun overlayWith(overlay: GenericUiAndroidTvConfig): GenericUiAndroidTvConfig =
        copySpec().apply {
            if ("enabled" in overlay.explicitFields) enabled = overlay.enabled
            if ("required" in overlay.explicitFields) required = overlay.required
            if ("banner" in overlay.explicitFields || overlay.banner != null) banner = overlay.banner
        }

    /** Returns an independent deep copy of this Android TV configuration. */
    fun copySpec(): GenericUiAndroidTvConfig = GenericUiAndroidTvConfig().also { copy ->
        copy.enabled = enabled
        copy.required = required
        copy.banner = banner
        copy.explicitFields.clear()
        copy.explicitFields.addAll(explicitFields)
    }
}
