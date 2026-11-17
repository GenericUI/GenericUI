package com.nexustools.genericui.plugin

import org.gradle.api.Action
import java.io.Serializable

/**
 * Helper DSL receiver for `networkSecurity { debugOverrides { ... } }`, supporting both
 * `debugOverrides { trustAnchors { user() } }` and shorthand `debugOverrides { user() }`.
 */
open class GenericUiAndroidDebugOverridesSpec(
    var trustAnchors: GenericUiAndroidTrustAnchorsSpec? = null
) : Serializable {

    /** Configures `<trust-anchors>` inside `<debug-overrides>`. */
    fun trustAnchors(action: Action<in GenericUiAndroidTrustAnchorsSpec>) {
        val spec = trustAnchors ?: GenericUiAndroidTrustAnchorsSpec().also { trustAnchors = it }
        action.execute(spec)
    }

    /** Shorthand to enable or disable user-installed CAs in `<debug-overrides><trust-anchors>`. */
    @JvmOverloads
    fun user(enabled: Boolean = true) {
        val spec = trustAnchors ?: GenericUiAndroidTrustAnchorsSpec().also { trustAnchors = it }
        spec.user(enabled)
    }

    /** Shorthand to enable or disable system CAs in `<debug-overrides><trust-anchors>`. */
    @JvmOverloads
    fun system(enabled: Boolean = true) {
        val spec = trustAnchors ?: GenericUiAndroidTrustAnchorsSpec().also { trustAnchors = it }
        spec.system(enabled)
    }

    /** Shorthand to add a certificate resource reference in `<debug-overrides><trust-anchors>`. */
    fun certificates(resRef: String) {
        val spec = trustAnchors ?: GenericUiAndroidTrustAnchorsSpec().also { trustAnchors = it }
        spec.certificates(resRef)
    }
}
