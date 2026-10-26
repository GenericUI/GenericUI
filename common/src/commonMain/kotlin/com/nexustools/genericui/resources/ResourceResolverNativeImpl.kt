package com.nexustools.genericui.resources

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Platform-native resource resolver contract implemented by GenericUI backends to resolve
 * compiled [ResourceID] handles into native URLs, byte arrays, strings, colors, and dimensions.
 */
interface ResourceResolverNativeImpl : Disposable {
    fun resolveUrl(resId: ResourceID): String
    fun resolveBytes(resId: ResourceID): ByteArray?
    fun resolveString(resId: ResourceID): String?
    fun resolveColor(resId: ResourceID): RgbaColor?
    fun resolveDimenPx(resId: ResourceID): Float?
}
