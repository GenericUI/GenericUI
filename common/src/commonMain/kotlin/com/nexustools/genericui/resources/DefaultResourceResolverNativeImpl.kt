package com.nexustools.genericui.resources

import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Default cross-platform [ResourceResolverNativeImpl] resolving values and binary payloads
 * from [Resources.activeTable].
 */
open class DefaultResourceResolverNativeImpl : ResourceResolverNativeImpl {

    override fun resolveUrl(resId: ResourceID): String =
        Resources.activeTable.getUrl(resId)

    override fun resolveBytes(resId: ResourceID): ByteArray? =
        Resources.activeTable.getRawBytes(resId)

    override fun resolveString(resId: ResourceID): String? =
        Resources.activeTable.getStringOrNull(resId)

    override fun resolveColor(resId: ResourceID): RgbaColor? =
        Resources.activeTable.getColorOrNull(resId)

    override fun resolveDimenPx(resId: ResourceID): Float? =
        Resources.activeTable.getDimenOrNull(resId)

    override fun dispose() {}
}
