package com.nexustools.genericui.component.content

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.image.AnimatedImage
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.image.MonocolorImageRecolorProcessor
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolver
import com.nexustools.genericui.style.color.ImageTintMode
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssFilter

/**
 * Cross-platform image display component integrated with the base GenericUI [ImageLoader] API.
 *
 * Accepts a URL/file path, an [ImageSource], an in-memory [ImageBuffer] (including GPU-processed
 * buffers produced by `:extensions:imageprocessing`), or an [AnimatedImage].
 */
open class ImageView(
    sourceUrl: String? = null,
    objectFit: ObjectFit = ObjectFit.CONTAIN,
    placeholderUrl: String? = ImageLoader.DEFAULT_PLACEHOLDER_URI
) : AbstractComponent(htmlTag = "img") {

    private var rawImageBuffer: ImageBuffer? = null
    private var isAsyncLoadingState: Boolean = false
    private var suppressNativeSourceUrlSync: Boolean = false
    private var lastAppliedFilter: CssFilter? = null

    var resourceId: ResourceID? = null
        protected set

    var placeholderResourceId: ResourceID? = com.nexustools.genericui.res.Resources.LOADING_IMAGE
        protected set

    constructor(
        resId: ResourceID,
        alt: String = resId.name
    ) : this(
        sourceUrl = ResourceResolver.getUrl(resId),
        objectFit = ObjectFit.CONTAIN
    ) {
        this.resourceId = resId
        this.fallbackText = alt
    }

    open fun setSource(resId: ResourceID) {
        this.resourceId = resId
        this.sourceUrl = ResourceResolver.getUrl(resId)
    }

    var sourceUrl: String? = sourceUrl
        set(value) {
            field = value
            if (!suppressNativeSourceUrlSync) {
                if (value == null || !value.startsWith("memory://", ignoreCase = true)) {
                    rawImageBuffer = null
                    imageBuffer = null
                } else {
                    val resolved = ImageLoader.resolveMemoryBuffer(value)
                    if (resolved != null) {
                        rawImageBuffer = resolved
                        imageBuffer = resolved
                        typedNative<ImageNativeImpl<*>>()?.setImageBuffer(resolved)
                        return
                    }
                }
                typedNative<ImageNativeImpl<*>>()?.setSourceUrl(value)
            }
        }

    /**
     * Optional placeholder image URL (or `res://` / `data:` URI) displayed immediately while [sourceUrl]
     * is loading asynchronously across all platforms.
     */
    var placeholderUrl: String? = placeholderUrl
        set(value) {
            field = value
            typedNative<ImageNativeImpl<*>>()?.setPlaceholderUrl(value)
        }

    /**
     * Optional in-memory placeholder [ImageBuffer] displayed while an asynchronous image load is in flight.
     */
    var placeholderBuffer: ImageBuffer? = null

    /**
     * Configures a compiled [ResourceID] as the placeholder loading image for this component.
     */
    open fun setPlaceholder(resId: ResourceID) {
        this.placeholderResourceId = resId
        this.placeholderUrl = ResourceResolver.getUrl(resId)
    }

    /**
     * Configures a URL or data URI as the placeholder loading image for this component.
     */
    open fun setPlaceholder(url: String?) {
        this.placeholderResourceId = null
        this.placeholderUrl = url
    }

    /**
     * Configures an in-memory [ImageBuffer] as the placeholder loading image for this component.
     */
    open fun setPlaceholder(buffer: ImageBuffer?) {
        this.placeholderBuffer = buffer
    }

    /**
     * Enables smooth antialiased image resampling (area-averaging + bilinear/bicubic interpolation)
     * when the image is resized. Enabled (`true`) by default across all platforms.
     */
    var antialiasing: Boolean = true
        set(value) {
            field = value
            typedNative<ImageNativeImpl<*>>()?.setAntialiasing(value)
        }

    /**
     * `true` while the target image is actively loading asynchronously.
     */
    val isLoading: Boolean
        get() = typedNative<ImageNativeImpl<*>>()?.isLoading() ?: isAsyncLoadingState

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

    var tintColor: RgbaColor? = null
        set(value) {
            field = value
            typedNative<ImageNativeImpl<*>>()?.setTintColor(value)
            refreshProcessedImageBuffer()
        }

    var tintMode: ImageTintMode = ImageTintMode.NONE
        set(value) {
            field = value
            typedNative<ImageNativeImpl<*>>()?.setTintMode(value)
            refreshProcessedImageBuffer()
        }

    /**
     * Enables or disables Android-style automatic monocolor icon recoloring for the active theme
     * (converting black icons to white in Dark Mode and white icons to black in Light Mode).
     */
    fun setMonocolorThemeRecolor(enabled: Boolean, customTintColor: RgbaColor? = null) {
        this.tintColor = customTintColor
        this.tintMode = if (enabled) ImageTintMode.MONOCOLOR_AUTO_THEME else ImageTintMode.NONE
    }

    var cssFilter: CssFilter?
        get() = getStyle().getFilter() ?: computedStyle?.filter
        set(value) {
            getStyle().setFilter(value)
            typedNative<ImageNativeImpl<*>>()?.setCssFilter(value)
            refreshProcessedImageBuffer()
        }

    var onLoad: (() -> Unit)? = null
        set(value) {
            field = value
            typedNative<ImageNativeImpl<*>>()?.setOnLoad(value)
        }

    var onError: (() -> Unit)? = null
        set(value) {
            field = value
            typedNative<ImageNativeImpl<*>>()?.setOnError(value)
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
        this.rawImageBuffer = buffer
        this.animatedImage = null
        this.widthPx = widthPx ?: buffer.width
        this.heightPx = heightPx ?: buffer.height
        refreshProcessedImageBuffer()
    }

    internal fun onThemeChanged() {
        typedNative<ImageNativeImpl<*>>()?.setTintMode(tintMode)
        refreshProcessedImageBuffer()
    }

    private fun refreshProcessedImageBuffer() {
        val base = rawImageBuffer ?: return
        val isDark = Application.current?.getResolvedTheme() == ThemeMode.DARK
        var processed = if (tintMode != ImageTintMode.NONE) {
            MonocolorImageRecolorProcessor.processImageBuffer(base, tintMode, tintColor, isDark)
        } else {
            base
        }
        val activeFilter = cssFilter
        if (activeFilter != null && !activeFilter.isNone) {
            processed = activeFilter.applyToImageBuffer(processed)
        }
        this.imageBuffer = processed
        val memoryUri = "memory://imagebuffer/${processed.width}x${processed.height}@${processed.hashCode()}"
        ImageLoader.registerMemoryBuffer(memoryUri, processed)
        suppressNativeSourceUrlSync = true
        try {
            this.sourceUrl = memoryUri
        } finally {
            suppressNativeSourceUrlSync = false
        }
        typedNative<ImageNativeImpl<*>>()?.setImageBuffer(processed)
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        super.applyComputedStyle(style)
        val effectiveFilter = getStyle().getFilter() ?: style.filter
        if (effectiveFilter != lastAppliedFilter) {
            lastAppliedFilter = effectiveFilter
            typedNative<ImageNativeImpl<*>>()?.setCssFilter(effectiveFilter)
            refreshProcessedImageBuffer()
        }
    }

    /**
     * Loads and displays an [ImageSource] using [ImageLoader].
     */
    fun loadImage(
        source: ImageSource,
        options: ImageDecodeOptions = ImageDecodeOptions(antialiasing = antialiasing)
    ) {
        isAsyncLoadingState = true
        placeholderBuffer?.let { placeholder ->
            if (rawImageBuffer == null) {
                setImageBuffer(placeholder)
            }
        }
        if (source.uriOrPath != null && source.encodedBytes == null && source.memoryBuffer == null) {
            this.sourceUrl = source.uriOrPath
        }
        ImageLoader.loadAsync(
            source = source,
            options = options,
            onLoaded = { decoded ->
                isAsyncLoadingState = false
                setImageBuffer(decoded)
                onLoad?.invoke()
            },
            onError = {
                isAsyncLoadingState = false
                onError?.invoke()
            }
        )
    }

    /**
     * Displays a multi-frame [AnimatedImage].
     */
    fun setAnimatedImage(animated: AnimatedImage) {
        this.animatedImage = animated
        animated.frames.firstOrNull()?.buffer?.let { setImageBuffer(it) }
    }

    override fun setAttribute(name: String, value: String) {
        super.setAttribute(name, value)
        if (name == "src") {
            sourceUrl = value
        } else if (name == "placeholder") {
            placeholderUrl = value
        }
    }

    protected override fun createNativeImpl(factory: NativeFactory): ImageNativeImpl<*> =
        factory.createImage()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<ImageNativeImpl<*>>() ?: return
        impl.setOnLoad(onLoad)
        impl.setOnError(onError)
        impl.setAntialiasing(antialiasing)
        impl.setPlaceholderUrl(placeholderUrl)
        val currentBuffer = imageBuffer
        if (currentBuffer != null) {
            impl.setImageBuffer(currentBuffer)
        } else {
            impl.setSourceUrl(sourceUrl)
        }
        impl.setFallbackText(fallbackText)
        impl.setObjectFit(objectFit)
        impl.setTintColor(tintColor)
        impl.setTintMode(tintMode)
        impl.setCssFilter(cssFilter)
    }

    override fun dispose() {
        rawImageBuffer = null
        imageBuffer = null
        placeholderBuffer = null
        animatedImage = null
        super.dispose()
    }
}
