package com.nexustools.genericui.resources

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.ReadableByteBuffer
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Platform-native resource resolver contract implemented by GenericUI backends to resolve
 * compiled [ResourceID] handles into native URLs, async readable streams, byte arrays, strings,
 * colors, and dimensions.
 */
interface ResourceResolverNativeImpl : Disposable {
    fun resolveUrl(resId: ResourceID): String
    fun resolveBytes(resId: ResourceID): ByteArray?
    fun resolveString(resId: ResourceID): String?
    fun resolveColor(resId: ResourceID): RgbaColor?
    fun resolveDimenPx(resId: ResourceID): Float?

    /**
     * Asynchronously opens a [Readable] stream for [resId] without blocking the UI thread.
     */
    suspend fun openResourceStream(resId: ResourceID): Readable? =
        resolveBytes(resId)?.let { ReadableByteBuffer(ByteBuffer.wrap(it)) }

    /**
     * Asynchronously opens a [Readable] stream for [resId] without blocking the UI thread.
     */
    suspend fun openReadable(resId: ResourceID): Readable? =
        openResourceStream(resId)
}
