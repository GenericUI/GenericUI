package com.nexustools.genericui.component.content

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.image.AnimatedImage
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl

/**
 * Cross-platform image display component integrated with the base GenericUI [ImageLoader] API.
 *
 * Accepts a URL/file path, an [ImageSource], an in-memory [ImageBuffer] (including GPU-processed
 * buffers produced by `:extensions:imageprocessing`), or an [AnimatedImage].
 */
open class ImageView(
    sourceUrl: String? = null,
    objectFit: ObjectFit = ObjectFit.CONTAIN
) : AbstractComponent() {
    var sourceUrl: String? = sourceUrl
        set(value) {
            field = value
            typedNative<ImageNativeImpl<*>>()?.setSourceUrl(value)
        }

    var fallbackText: String? = null
        set(value) {
            field = value
            typedNative<ImageNativeImpl<*>>()?.setFallbackText(value)
        }

    var objectFit: ObjectFit = objectFit
        set(value) {
            field = value
            typedNative<ImageNativeImpl<*>>()?.setObjectFit(value)
        }

    var imageBuffer: ImageBuffer? = null
        private set

    var animatedImage: AnimatedImage? = null
        private set

    /**
     * Displays an in-memory [ImageBuffer] (e.g. decoded via [ImageLoader] or manipulated offscreen
     * via `:extensions:imageprocessing`).
     */
    fun setImageBuffer(buffer: ImageBuffer) {
        this.imageBuffer = buffer
        this.animatedImage = null
        this.widthPx = widthPx ?: buffer.width
        this.heightPx = heightPx ?: buffer.height
        this.sourceUrl = "memory://imagebuffer/${buffer.width}x${buffer.height}@${buffer.hashCode()}"
    }

    /**
     * Loads and displays an [ImageSource] using [ImageLoader].
     */
    fun loadImage(source: ImageSource, options: ImageDecodeOptions = ImageDecodeOptions()) {
        if (source.uriOrPath != null && source.encodedBytes == null && source.memoryBuffer == null) {
            this.sourceUrl = source.uriOrPath
        }
        ImageLoader.loadAsync(
            source = source,
            options = options,
            onLoaded = { decoded -> setImageBuffer(decoded) }
        )
    }

    /**
     * Displays a multi-frame [AnimatedImage].
     */
    fun setAnimatedImage(animated: AnimatedImage) {
        this.animatedImage = animated
        animated.frames.firstOrNull()?.buffer?.let { setImageBuffer(it) }
    }

    override fun createNativeImpl(factory: NativeFactory): ImageNativeImpl<*> =
        factory.createImage()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<ImageNativeImpl<*>>() ?: return
        impl.setSourceUrl(sourceUrl)
        impl.setFallbackText(fallbackText)
        impl.setObjectFit(objectFit)
    }
}
