package com.nexustools.genericui.resources

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.ReadableByteBuffer
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Default cross-platform [ResourceResolverNativeImpl] resolving values, binary payloads,
 * and asynchronous [Readable] streams from [Resources.activeTable].
 */
open class DefaultResourceResolverNativeImpl : ResourceResolverNativeImpl {

    override var isDisposed: Boolean = false
        protected set

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

    override suspend fun openResourceStream(resId: ResourceID): Readable? {
        val rawBytes = resolveBytes(resId)
        if (rawBytes != null) {
            return ReadableByteBuffer(ByteBuffer.wrap(rawBytes))
        }
        val rawText = Resources.activeTable.getRawText(resId)
            ?: Resources.activeTable.getStringOrNull(resId)
            ?: Resources.activeTable.getDrawable(resId)?.svgSource
        if (rawText != null) {
            return ReadableByteBuffer(ByteBuffer.fromString(rawText, "utf8"))
        }
        return null
    }

    override fun dispose() {
        isDisposed = true
    }
}
