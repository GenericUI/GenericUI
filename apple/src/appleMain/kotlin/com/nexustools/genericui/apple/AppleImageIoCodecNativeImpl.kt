@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.image.AnimatedImage
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageFrame
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageMetadata
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.image.ImageCodecNativeImpl
import com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl
import com.nexustools.genericui.res.Resources
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import platform.CoreFoundation.CFBooleanGetTypeID
import platform.CoreFoundation.CFBooleanGetValue
import platform.CoreFoundation.CFBooleanRef
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFGetTypeID
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFNumberGetTypeID
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFNumberRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringGetTypeID
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFNumberDoubleType
import platform.CoreFoundation.kCFNumberSInt32Type
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceRef
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextRotateCTM
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreate
import platform.CoreGraphics.CGImageGetAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.CoreGraphics.kCGColorSpaceDisplayP3
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.CoreGraphics.kCGInterpolationHigh
import platform.CoreGraphics.kCGInterpolationNone
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.CGImageSourceCopyProperties
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.CGImageSourceGetCount
import platform.ImageIO.CGImageSourceGetType
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImageDestinationLossyCompressionQuality
import platform.ImageIO.kCGImagePropertyAPNGDelayTime
import platform.ImageIO.kCGImagePropertyAPNGLoopCount
import platform.ImageIO.kCGImagePropertyAPNGUnclampedDelayTime
import platform.ImageIO.kCGImagePropertyColorModel
import platform.ImageIO.kCGImagePropertyColorModelCMYK
import platform.ImageIO.kCGImagePropertyColorModelGray
import platform.ImageIO.kCGImagePropertyDPIHeight
import platform.ImageIO.kCGImagePropertyDPIWidth
import platform.ImageIO.kCGImagePropertyDepth
import platform.ImageIO.kCGImagePropertyGIFDelayTime
import platform.ImageIO.kCGImagePropertyGIFDictionary
import platform.ImageIO.kCGImagePropertyGIFLoopCount
import platform.ImageIO.kCGImagePropertyGIFUnclampedDelayTime
import platform.ImageIO.kCGImagePropertyHasAlpha
import platform.ImageIO.kCGImagePropertyOrientation
import platform.ImageIO.kCGImagePropertyPNGDictionary
import platform.ImageIO.kCGImagePropertyPixelHeight
import platform.ImageIO.kCGImagePropertyPixelWidth
import platform.ImageIO.kCGImagePropertyProfileName
import platform.ImageIO.kCGImagePropertyWebPDelayTime
import platform.ImageIO.kCGImagePropertyWebPDictionary
import platform.ImageIO.kCGImagePropertyWebPLoopCount
import platform.ImageIO.kCGImagePropertyWebPUnclampedDelayTime
import platform.posix.memcpy
import kotlin.math.PI
import kotlin.math.roundToInt

class AppleImageIoCodecNativeImpl(
    httpClient: HttpClientNativeImpl? = null
) : ImageCodecNativeImpl {
    private val lock = AppleReentrantLock()
    private val ownsHttpClient = httpClient == null
    private val effectiveHttpClient: HttpClientNativeImpl = httpClient ?: AppleHttpClientNativeImpl()
    private var disposedState = false

    override val codecBackendName: String = "AppleImageIO+CoreGraphics"
    override val usesBrowserManagedImageLoading: Boolean = false

    override val isDisposed: Boolean
        get() = lock.withLock { disposedState }

    override fun probeMetadata(source: ImageSource): ImageMetadata? {
        if (isDisposed) return null
        source.memoryBuffer?.let { mem ->
            return ImageMetadata(
                width = mem.width,
                height = mem.height,
                format = if (source.formatHint != ImageFormat.UNKNOWN) source.formatHint else ImageFormat.RAW_RGBA,
                pixelFormat = mem.pixelFormat,
                colorSpace = mem.colorSpace,
                bitDepthPerChannel = if (mem.pixelFormat.isFloatingPoint) 16 else 8,
                hasAlpha = mem.pixelFormat.hasAlpha,
                isAnimated = false,
                frameCount = 1
            )
        }

        val bytes = resolveSourceBytes(source) ?: return null
        if (bytes.isEmpty()) return null

        val svgDims = AppleSvgCoreGraphicsRasterizer.probeDimensions(bytes)
        if (svgDims != null || source.formatHint == ImageFormat.SVG) {
            val dims = svgDims ?: (48 to 48)
            return ImageMetadata(
                width = dims.first,
                height = dims.second,
                format = ImageFormat.SVG,
                pixelFormat = PixelFormat.RGBA_8888,
                colorSpace = ImageColorSpace.SRGB,
                bitDepthPerChannel = 8,
                hasAlpha = true,
                isAnimated = false,
                frameCount = 1,
                attributes = mapOf("backend" to "AppleCoreGraphicsSVG")
            )
        }

        return withImageSource(bytes, source.formatHint) { imgSrc ->
            val count = CGImageSourceGetCount(imgSrc).toInt()
            if (count <= 0) return@withImageSource null

            val uti = cfStringToKotlin(CGImageSourceGetType(imgSrc))
            val detectedFormat = AppleUniformTypeHelper.imageFormatForUti(uti)
                .takeIf { it != ImageFormat.UNKNOWN }
                ?: detectFormatFromBytes(bytes, source.formatHint)

            val props = CGImageSourceCopyPropertiesAtIndex(imgSrc, 0uL, null)
            try {
                val width = dictInt(props, kCGImagePropertyPixelWidth) ?: 0
                val height = dictInt(props, kCGImagePropertyPixelHeight) ?: 0
                val bitDepth = (dictInt(props, kCGImagePropertyDepth) ?: 8).coerceAtLeast(1)
                val hasAlpha = dictBoolean(props, kCGImagePropertyHasAlpha)
                    ?: (detectedFormat == ImageFormat.PNG || detectedFormat == ImageFormat.GIF || detectedFormat == ImageFormat.WEBP)
                val orientation = dictInt(props, kCGImagePropertyOrientation) ?: 1
                val dpiX = (dictDouble(props, kCGImagePropertyDPIWidth) ?: 72.0).toFloat()
                val dpiY = (dictDouble(props, kCGImagePropertyDPIHeight) ?: 72.0).toFloat()

                val colorModel = dictString(props, kCGImagePropertyColorModel)
                val profileName = dictString(props, kCGImagePropertyProfileName)
                val colorSpace = when {
                    profileName?.contains("Display P3", ignoreCase = true) == true -> ImageColorSpace.DISPLAY_P3
                    colorModel == cfStringToKotlin(kCGImagePropertyColorModelCMYK) -> ImageColorSpace.CMYK
                    else -> ImageColorSpace.SRGB
                }
                val pixelFormat = when {
                    colorModel == cfStringToKotlin(kCGImagePropertyColorModelGray) ->
                        if (hasAlpha) PixelFormat.GRAY_ALPHA_88 else PixelFormat.GRAY_8
                    else -> PixelFormat.RGBA_8888
                }

                val attrs = LinkedHashMap<String, String>()
                attrs["backend"] = codecBackendName
                if (uti != null) attrs["uti"] = uti
                if (profileName != null) attrs["profileName"] = profileName
                if (colorModel != null) attrs["colorModel"] = colorModel

                ImageMetadata(
                    width = width,
                    height = height,
                    format = detectedFormat,
                    pixelFormat = pixelFormat,
                    colorSpace = colorSpace,
                    bitDepthPerChannel = bitDepth,
                    hasAlpha = hasAlpha,
                    isAnimated = count > 1,
                    frameCount = count,
                    exifOrientation = orientation,
                    dpiX = dpiX,
                    dpiY = dpiY,
                    attributes = attrs
                )
            } finally {
                if (props != null) CFRelease(props)
            }
        }
    }

    override fun decode(source: ImageSource, options: ImageDecodeOptions): ImageBuffer? {
        if (isDisposed) return null

        source.memoryBuffer?.let { mem ->
            val (dstW, dstH) = ImageLoader.resolveTargetDimensions(mem.width, mem.height, options)
            if (dstW == mem.width && dstH == mem.height && options.preferredPixelFormat == mem.pixelFormat) {
                return mem
            }
            return resizeBufferViaCoreGraphics(mem, dstW, dstH, options)
        }

        val bytes = resolveSourceBytes(source) ?: return null
        if (bytes.isEmpty()) return null

        if (source.formatHint == ImageFormat.SVG || AppleSvgCoreGraphicsRasterizer.probeDimensions(bytes) != null) {
            return AppleSvgCoreGraphicsRasterizer.rasterize(bytes, options)
        }

        return withImageSource(bytes, source.formatHint) { imgSrc ->
            val count = CGImageSourceGetCount(imgSrc).toInt()
            if (count <= 0) return@withImageSource null
            decodeFrameAtIndex(imgSrc, 0uL, options)
        }
    }

    override fun decodeAnimated(source: ImageSource, options: ImageDecodeOptions): AnimatedImage? {
        if (isDisposed) return null

        val bytes = resolveSourceBytes(source) ?: return null
        if (bytes.isEmpty()) return null

        if (source.formatHint == ImageFormat.SVG || AppleSvgCoreGraphicsRasterizer.probeDimensions(bytes) != null) {
            val single = AppleSvgCoreGraphicsRasterizer.rasterize(bytes, options) ?: return null
            return AnimatedImage(single.width, single.height, listOf(ImageFrame(0, single, 100)))
        }

        return withImageSource(bytes, source.formatHint) { imgSrc ->
            val count = CGImageSourceGetCount(imgSrc).toInt()
            if (count <= 0) return@withImageSource null

            val containerProps = CGImageSourceCopyProperties(imgSrc, null)
            val loopCount = try {
                extractLoopCount(containerProps)
            } finally {
                if (containerProps != null) CFRelease(containerProps)
            }

            val frames = ArrayList<ImageFrame>(count)
            for (i in 0 until count) {
                val idx = i.toULong()
                val frameBuf = decodeFrameAtIndex(imgSrc, idx, options) ?: continue
                val frameProps = CGImageSourceCopyPropertiesAtIndex(imgSrc, idx, null)
                val durationMs = try {
                    extractFrameDelayMs(frameProps)
                } finally {
                    if (frameProps != null) CFRelease(frameProps)
                }
                frames.add(ImageFrame(frameIndex = i, buffer = frameBuf, durationMs = durationMs))
            }

            if (frames.isEmpty()) return@withImageSource null
            val first = frames.first().buffer
            AnimatedImage(
                width = first.width,
                height = first.height,
                frames = frames,
                loopCount = loopCount
            )
        }
    }

    override fun encode(buffer: ImageBuffer, options: ImageEncodeOptions): ByteArray? {
        if (isDisposed || buffer.width <= 0 || buffer.height <= 0) return null

        if (options.format == ImageFormat.SVG) return null

        val utiStr = AppleUniformTypeHelper.utiForImageFormat(options.format) ?: return null
        val premulRgba = AppleImagePixelUtils.toPremultipliedRgba8888(buffer)
        val cfData = AppleDataBridge.byteArrayToCFData(premulRgba) ?: return null
        val provider = CGDataProviderCreateWithCFData(cfData)
        CFRelease(cfData)
        if (provider == null) return null

        val colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB) ?: CGColorSpaceCreateDeviceRGB()
        if (colorSpace == null) {
            CGDataProviderRelease(provider)
            return null
        }

        val bitmapInfo = kCGBitmapByteOrder32Big or CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
        val cgImage = CGImageCreate(
            width = buffer.width.toULong(),
            height = buffer.height.toULong(),
            bitsPerComponent = 8uL,
            bitsPerPixel = 32uL,
            bytesPerRow = (buffer.width * 4).toULong(),
            space = colorSpace,
            bitmapInfo = bitmapInfo,
            provider = provider,
            decode = null,
            shouldInterpolate = true,
            intent = CGColorRenderingIntent.kCGRenderingIntentDefault
        )
        CGDataProviderRelease(provider)
        CGColorSpaceRelease(colorSpace)
        if (cgImage == null) return null

        val mutableData = CFDataCreateMutable(null, 0L)
        if (mutableData == null) {
            CGImageRelease(cgImage)
            return null
        }

        val utiCf = CFStringCreateWithCString(null, utiStr, kCFStringEncodingUTF8)
        if (utiCf == null) {
            CFRelease(mutableData)
            CGImageRelease(cgImage)
            return null
        }

        val dest = CGImageDestinationCreateWithData(mutableData, utiCf, 1uL, null)
        CFRelease(utiCf)
        if (dest == null) {
            CFRelease(mutableData)
            CGImageRelease(cgImage)
            return null
        }

        try {
            val frameProps = if (options.format == ImageFormat.JPEG || options.format == ImageFormat.WEBP) {
                memScoped {
                    val qualityVal = alloc<DoubleVar>()
                    qualityVal.value = (options.quality.coerceIn(1, 100) / 100.0)
                    val numRef = CFNumberCreate(null, kCFNumberDoubleType, qualityVal.ptr)
                    if (numRef != null) {
                        val keys = allocArray<COpaquePointerVar>(1)
                        val vals = allocArray<COpaquePointerVar>(1)
                        keys[0] = kCGImageDestinationLossyCompressionQuality
                        vals[0] = numRef
                        val dict = CFDictionaryCreate(
                            null,
                            keys,
                            vals,
                            1L,
                            kCFTypeDictionaryKeyCallBacks.ptr,
                            kCFTypeDictionaryValueCallBacks.ptr
                        )
                        CFRelease(numRef)
                        dict
                    } else {
                        null
                    }
                }
            } else {
                null
            }

            try {
                CGImageDestinationAddImage(dest, cgImage, frameProps)
            } finally {
                if (frameProps != null) CFRelease(frameProps)
            }

            if (!CGImageDestinationFinalize(dest)) return null

            val outLen = CFDataGetLength(mutableData).toInt()
            if (outLen <= 0) return ByteArray(0)
            val bytePtr = CFDataGetBytePtr(mutableData) ?: return null
            val outBytes = ByteArray(outLen)
            outBytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), bytePtr, outLen.toULong())
            }
            return outBytes
        } finally {
            CFRelease(dest)
            CFRelease(mutableData)
            CGImageRelease(cgImage)
        }
    }

    override fun dispose() {
        val shouldDisposeClient = lock.withLock {
            if (disposedState) false else {
                disposedState = true
                ownsHttpClient
            }
        }
        if (shouldDisposeClient) {
            effectiveHttpClient.dispose()
        }
    }

    private fun decodeFrameAtIndex(
        imgSrc: CGImageSourceRef,
        index: ULong,
        options: ImageDecodeOptions
    ): ImageBuffer? {
        val cgImage = CGImageSourceCreateImageAtIndex(imgSrc, index, null) ?: return null
        val frameProps = CGImageSourceCopyPropertiesAtIndex(imgSrc, index, null)
        try {
            val orientation = dictInt(frameProps, kCGImagePropertyOrientation) ?: 1
            val profileName = dictString(frameProps, kCGImagePropertyProfileName)
            val targetSpace = if (profileName?.contains("Display P3", ignoreCase = true) == true) {
                ImageColorSpace.DISPLAY_P3
            } else {
                ImageColorSpace.SRGB
            }
            return rasterizeCgImage(cgImage, options, orientation, targetSpace)
        } finally {
            if (frameProps != null) CFRelease(frameProps)
            CGImageRelease(cgImage)
        }
    }

    private fun rasterizeCgImage(
        cgImage: CGImageRef,
        options: ImageDecodeOptions,
        exifOrientation: Int,
        colorSpaceKind: ImageColorSpace
    ): ImageBuffer? {
        val rawW = CGImageGetWidth(cgImage).toInt()
        val rawH = CGImageGetHeight(cgImage).toInt()
        if (rawW <= 0 || rawH <= 0) return null

        val oriented = options.applyExifOrientation && exifOrientation in 5..8
        val srcW = if (oriented) rawH else rawW
        val srcH = if (oriented) rawW else rawH
        val (dstW, dstH) = ImageLoader.resolveTargetDimensions(srcW, srcH, options)
        if (dstW <= 0 || dstH <= 0) return null

        val cgColorSpace: CGColorSpaceRef = when (colorSpaceKind) {
            ImageColorSpace.DISPLAY_P3 -> CGColorSpaceCreateWithName(kCGColorSpaceDisplayP3)
                ?: CGColorSpaceCreateDeviceRGB()
            else -> CGColorSpaceCreateWithName(kCGColorSpaceSRGB)
                ?: CGColorSpaceCreateDeviceRGB()
        } ?: return null

        val outBytes = ByteArray(dstW * dstH * 4)
        val bitmapInfo = kCGBitmapByteOrder32Big or CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
        val ctx = outBytes.usePinned { pinned ->
            CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = dstW.toULong(),
                height = dstH.toULong(),
                bitsPerComponent = 8uL,
                bytesPerRow = (dstW * 4).toULong(),
                space = cgColorSpace,
                bitmapInfo = bitmapInfo
            )
        }
        if (ctx == null) {
            CGColorSpaceRelease(cgColorSpace)
            return null
        }

        try {
            CGContextSetInterpolationQuality(ctx, if (options.antialiasing) kCGInterpolationHigh else kCGInterpolationNone)
            CGContextSaveGState(ctx)

            val w = dstW.toDouble()
            val h = dstH.toDouble()
            val drawW: Double
            val drawH: Double
            if (options.applyExifOrientation && exifOrientation in 2..8) {
                when (exifOrientation) {
                    2 -> {
                        CGContextTranslateCTM(ctx, w, 0.0)
                        CGContextScaleCTM(ctx, -1.0, 1.0)
                        drawW = w; drawH = h
                    }
                    3 -> {
                        CGContextTranslateCTM(ctx, w, h)
                        CGContextRotateCTM(ctx, PI)
                        drawW = w; drawH = h
                    }
                    4 -> {
                        CGContextTranslateCTM(ctx, 0.0, h)
                        CGContextScaleCTM(ctx, 1.0, -1.0)
                        drawW = w; drawH = h
                    }
                    5 -> {
                        CGContextTranslateCTM(ctx, w, 0.0)
                        CGContextScaleCTM(ctx, -1.0, 1.0)
                        CGContextTranslateCTM(ctx, w, 0.0)
                        CGContextRotateCTM(ctx, PI / 2.0)
                        drawW = h; drawH = w
                    }
                    6 -> {
                        CGContextTranslateCTM(ctx, 0.0, h)
                        CGContextRotateCTM(ctx, -PI / 2.0)
                        drawW = h; drawH = w
                    }
                    7 -> {
                        CGContextTranslateCTM(ctx, 0.0, h)
                        CGContextScaleCTM(ctx, 1.0, -1.0)
                        CGContextTranslateCTM(ctx, 0.0, h)
                        CGContextRotateCTM(ctx, -PI / 2.0)
                        drawW = h; drawH = w
                    }
                    8 -> {
                        CGContextTranslateCTM(ctx, w, 0.0)
                        CGContextRotateCTM(ctx, PI / 2.0)
                        drawW = h; drawH = w
                    }
                    else -> {
                        drawW = w; drawH = h
                    }
                }
            } else {
                drawW = w
                drawH = h
            }

            CGContextDrawImage(ctx, CGRectMake(0.0, 0.0, drawW, drawH), cgImage)
            CGContextRestoreGState(ctx)
        } finally {
            CGContextRelease(ctx)
            CGColorSpaceRelease(cgColorSpace)
        }

        val alphaInfo = CGImageGetAlphaInfo(cgImage)
        val hasSourceAlpha = alphaInfo != CGImageAlphaInfo.kCGImageAlphaNone &&
            alphaInfo != CGImageAlphaInfo.kCGImageAlphaNoneSkipLast &&
            alphaInfo != CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst
        val finalRgba = if (options.premultiplyAlpha || !hasSourceAlpha) {
            outBytes
        } else {
            AppleImagePixelUtils.unpremultiplyRgba(outBytes)
        }
        val base = ImageBuffer(
            width = dstW,
            height = dstH,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = colorSpaceKind,
            bytes = finalRgba
        )
        return AppleImagePixelUtils.convertRgbaToTargetFormat(base, options.preferredPixelFormat)
    }

    private fun resizeBufferViaCoreGraphics(
        src: ImageBuffer,
        dstWidth: Int,
        dstHeight: Int,
        options: ImageDecodeOptions
    ): ImageBuffer? {
        val premul = AppleImagePixelUtils.toPremultipliedRgba8888(src)
        val cfData = AppleDataBridge.byteArrayToCFData(premul) ?: return null
        val provider = CGDataProviderCreateWithCFData(cfData)
        CFRelease(cfData)
        if (provider == null) return null
        val cs = CGColorSpaceCreateDeviceRGB() ?: run {
            CGDataProviderRelease(provider)
            return null
        }
        val bitmapInfo = kCGBitmapByteOrder32Big or CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
        val cgImage = CGImageCreate(
            width = src.width.toULong(),
            height = src.height.toULong(),
            bitsPerComponent = 8uL,
            bitsPerPixel = 32uL,
            bytesPerRow = (src.width * 4).toULong(),
            space = cs,
            bitmapInfo = bitmapInfo,
            provider = provider,
            decode = null,
            shouldInterpolate = true,
            intent = CGColorRenderingIntent.kCGRenderingIntentDefault
        )
        CGDataProviderRelease(provider)
        CGColorSpaceRelease(cs)
        if (cgImage == null) return null
        return try {
            val forcedOptions = options.copy(targetWidth = dstWidth, targetHeight = dstHeight, preserveAspectRatio = false)
            rasterizeCgImage(cgImage, forcedOptions, exifOrientation = 1, colorSpaceKind = src.colorSpace)
        } finally {
            CGImageRelease(cgImage)
        }
    }

    private fun extractLoopCount(containerProps: CFDictionaryRef?): Int {
        if (containerProps == null) return 0
        val gifDict = dictDictionary(containerProps, kCGImagePropertyGIFDictionary)
        dictInt(gifDict, kCGImagePropertyGIFLoopCount)?.let { return it }

        val pngDict = dictDictionary(containerProps, kCGImagePropertyPNGDictionary)
        dictInt(pngDict, kCGImagePropertyAPNGLoopCount)?.let { return it }

        val webpDict = dictDictionary(containerProps, kCGImagePropertyWebPDictionary)
        dictInt(webpDict, kCGImagePropertyWebPLoopCount)?.let { return it }

        return 0
    }

    private fun extractFrameDelayMs(frameProps: CFDictionaryRef?): Int {
        if (frameProps == null) return 100

        val delaySeconds: Double? = run {
            val gifDict = dictDictionary(frameProps, kCGImagePropertyGIFDictionary)
            val gifDelay = dictDouble(gifDict, kCGImagePropertyGIFUnclampedDelayTime)
                ?: dictDouble(gifDict, kCGImagePropertyGIFDelayTime)
            if (gifDelay != null && gifDelay > 0.0) return@run gifDelay

            val pngDict = dictDictionary(frameProps, kCGImagePropertyPNGDictionary)
            val apngDelay = dictDouble(pngDict, kCGImagePropertyAPNGUnclampedDelayTime)
                ?: dictDouble(pngDict, kCGImagePropertyAPNGDelayTime)
            if (apngDelay != null && apngDelay > 0.0) return@run apngDelay

            val webpDict = dictDictionary(frameProps, kCGImagePropertyWebPDictionary)
            val webpDelay = dictDouble(webpDict, kCGImagePropertyWebPUnclampedDelayTime)
                ?: dictDouble(webpDict, kCGImagePropertyWebPDelayTime)
            if (webpDelay != null && webpDelay > 0.0) return@run webpDelay

            gifDelay ?: apngDelay ?: webpDelay
        }

        if (delaySeconds == null || delaySeconds <= 0.0) return 100
        return (delaySeconds * 1000.0).roundToInt().coerceAtLeast(10)
    }

    private inline fun <T> withImageSource(
        bytes: ByteArray,
        formatHint: ImageFormat,
        block: (CGImageSourceRef) -> T?
    ): T? {
        val cfData = AppleDataBridge.byteArrayToCFData(bytes) ?: return null
        val hintUti = AppleUniformTypeHelper.utiForImageFormat(formatHint)
        val hintCf = hintUti?.let { CFStringCreateWithCString(null, it, kCFStringEncodingUTF8) }
        val optionsDict = if (hintCf != null) {
            memScoped {
                val keys = allocArray<COpaquePointerVar>(1)
                val vals = allocArray<COpaquePointerVar>(1)
                keys[0] = platform.ImageIO.kCGImageSourceTypeIdentifierHint
                vals[0] = hintCf
                CFDictionaryCreate(
                    null,
                    keys,
                    vals,
                    1L,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr
                )
            }
        } else {
            null
        }
        val imgSrc = CGImageSourceCreateWithData(cfData, optionsDict)
        if (optionsDict != null) CFRelease(optionsDict)
        if (hintCf != null) CFRelease(hintCf)
        CFRelease(cfData)
        if (imgSrc == null) return null

        return try {
            block(imgSrc)
        } finally {
            CFRelease(imgSrc)
        }
    }

    private fun resolveSourceBytes(source: ImageSource): ByteArray? {
        source.encodedBytes?.let { return it }

        source.resId?.let { resId ->
            Resources.getRawBytes(resId)?.let { return it }
            if (resId.relativePath.isNotBlank()) {
                readLocalFileBytes(resId.relativePath)?.let { return it }
            }
        }

        val uri = source.uriOrPath ?: return null
        if (uri.startsWith("data:", ignoreCase = true)) {
            return decodeDataUri(uri)
        }
        if (uri.startsWith("res://", ignoreCase = true)) {
            val resPath = uri.removePrefix("res://")
            val name = resPath.substringAfterLast('/').substringBeforeLast('.')
            val raw = Resources.activeTable.getDrawableByName(name)
            raw?.svgSource?.let { return it.encodeToByteArray() }
            raw?.toIconUri()?.takeIf { it.startsWith("data:", ignoreCase = true) }?.let { dataUri ->
                decodeDataUri(dataUri)?.let { bytes -> return bytes }
            }
        }
        if (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)) {
            return runBlocking {
                val resp = effectiveHttpClient.execute(AppleHttpRequestNativeImpl(uri))
                if (resp.isSuccessful && resp.bodyBytes.isNotEmpty()) resp.bodyBytes else null
            }
        }
        val filePath = if (uri.startsWith("file://", ignoreCase = true)) {
            NSURL.URLWithString(uri)?.path ?: uri.removePrefix("file://")
        } else {
            uri
        }
        return readLocalFileBytes(filePath)
    }

    private fun readLocalFileBytes(path: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return AppleDataBridge.nsDataToByteArray(data)
    }

    private fun decodeDataUri(uri: String): ByteArray? {
        val commaIdx = uri.indexOf(',')
        if (commaIdx < 0) return null
        val meta = uri.substring(5, commaIdx)
        val payload = uri.substring(commaIdx + 1)
        return if (meta.contains(";base64", ignoreCase = true)) {
            val cleaned = payload.replace("\n", "").replace("\r", "").replace(" ", "")
            val nsData = NSData.create(base64EncodedString = cleaned, options = 0uL) ?: return null
            AppleDataBridge.nsDataToByteArray(nsData)
        } else {
            percentDecodeUtf8(payload)
        }
    }

    private fun percentDecodeUtf8(encoded: String): ByteArray {
        val out = ArrayList<Byte>(encoded.length)
        var i = 0
        while (i < encoded.length) {
            val ch = encoded[i]
            if (ch == '%' && i + 2 < encoded.length) {
                val hex = encoded.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    out.add(hex.toByte())
                    i += 3
                    continue
                }
            }
            for (b in ch.toString().encodeToByteArray()) {
                out.add(b)
            }
            i++
        }
        return out.toByteArray()
    }

    private fun detectFormatFromBytes(bytes: ByteArray, hint: ImageFormat): ImageFormat {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
        ) return ImageFormat.PNG
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) return ImageFormat.JPEG
        if (bytes.size >= 6) {
            val head = bytes.copyOf(6).decodeToString()
            if (head == "GIF87a" || head == "GIF89a") return ImageFormat.GIF
        }
        if (bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            bytes.copyOfRange(8, 12).decodeToString() == "WEBP"
        ) return ImageFormat.WEBP
        if (bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) {
            return ImageFormat.BMP
        }
        return hint
    }

    private fun dictDictionary(dict: CFDictionaryRef?, key: CFStringRef?): CFDictionaryRef? {
        if (dict == null || key == null) return null
        val raw = CFDictionaryGetValue(dict, key) ?: return null
        return raw.reinterpret()
    }

    private fun dictInt(dict: CFDictionaryRef?, key: CFStringRef?): Int? {
        if (dict == null || key == null) return null
        val raw = CFDictionaryGetValue(dict, key) ?: return null
        if (CFGetTypeID(raw) != CFNumberGetTypeID()) return null
        val numRef: CFNumberRef = raw.reinterpret()
        return memScoped {
            val out = alloc<IntVar>()
            if (CFNumberGetValue(numRef, kCFNumberSInt32Type, out.ptr)) out.value else null
        }
    }

    private fun dictDouble(dict: CFDictionaryRef?, key: CFStringRef?): Double? {
        if (dict == null || key == null) return null
        val raw = CFDictionaryGetValue(dict, key) ?: return null
        if (CFGetTypeID(raw) != CFNumberGetTypeID()) return null
        val numRef: CFNumberRef = raw.reinterpret()
        return memScoped {
            val out = alloc<DoubleVar>()
            if (CFNumberGetValue(numRef, kCFNumberDoubleType, out.ptr)) out.value else null
        }
    }

    private fun dictBoolean(dict: CFDictionaryRef?, key: CFStringRef?): Boolean? {
        if (dict == null || key == null) return null
        val raw = CFDictionaryGetValue(dict, key) ?: return null
        val typeId = CFGetTypeID(raw)
        if (typeId == CFBooleanGetTypeID()) {
            val bRef: CFBooleanRef = raw.reinterpret()
            return CFBooleanGetValue(bRef)
        }
        if (typeId == CFNumberGetTypeID()) {
            val num = dictInt(dict, key) ?: return null
            return num != 0
        }
        return null
    }

    private fun dictString(dict: CFDictionaryRef?, key: CFStringRef?): String? {
        if (dict == null || key == null) return null
        val raw = CFDictionaryGetValue(dict, key) ?: return null
        if (CFGetTypeID(raw) != CFStringGetTypeID()) return null
        return cfStringToKotlin(raw.reinterpret())
    }

    private fun cfStringToKotlin(cfStr: CFStringRef?): String? {
        if (cfStr == null) return null
        CFRetain(cfStr)
        return CFBridgingRelease(cfStr) as? String
    }
}
