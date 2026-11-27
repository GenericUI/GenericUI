@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple.style

import com.nexustools.genericui.apple.AppleReentrantLock
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssFilterColorMatrix
import com.nexustools.genericui.style.css.FastBackdropBlurKernel
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CValue
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Accelerate.kvImageEdgeExtend
import platform.Accelerate.kvImageGetTempBufferSize
import platform.Accelerate.kvImageNoFlags
import platform.Accelerate.vImageBoxConvolve_ARGB8888
import platform.Accelerate.vImagePremultipliedConstAlphaBlend_Planar8
import platform.Accelerate.vImage_Buffer
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFPropertyListRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.kCFNumberSInt32Type
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCopyPropertyList
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceRef
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.CoreGraphics.kCGColorSpaceSRGB
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.CIVector
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.CoreImage.kCIFormatBGRA8
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSNumber
import platform.Foundation.numberWithDouble
import platform.Foundation.setValue
import platform.IOSurface.IOSurfaceCreate
import platform.IOSurface.IOSurfaceRef
import platform.IOSurface.IOSurfaceSetValue
import platform.IOSurface.kIOSurfaceAllocSize
import platform.IOSurface.kIOSurfaceBytesPerElement
import platform.IOSurface.kIOSurfaceBytesPerRow
import platform.IOSurface.kIOSurfaceColorSpace
import platform.IOSurface.kIOSurfaceHeight
import platform.IOSurface.kIOSurfacePixelFormat
import platform.IOSurface.kIOSurfaceWidth
import platform.Metal.MTLCreateSystemDefaultDevice

/**
 * Shared CoreImage (`CIFilter` / `CIContext`) and premultiplied `BGRA_8888` CPU backdrop-filter
 * pipeline for macOS (`AppKit`) and iOS/iPadOS (`UIKit`).
 *
 * Covers all 10 W3C CSS filter functions (`blur`, `brightness`, `contrast`, `grayscale`,
 * `hue-rotate`, `invert`, `opacity`, `saturate`, `sepia`, and `drop-shadow`) using:
 * - `CIAffineClamp` + `CIGaussianBlur` for boundary-clean blur,
 * - a single fused `CIColorMatrix` computed from [CssFilterColorMatrix.buildMatrix4x5],
 * - CoreImage alpha-mask tint + Gaussian blur + offset composition for `drop-shadow`, and
 * - [FastBackdropBlurKernel.applyBackdropToPremulBgraRegion] for premultiplied `BGRA_8888` buffers.
 */
object AppleBackdropFilterController {

    private const val IOSURFACE_PIXEL_FORMAT_BGRA: Int = 0x42475241 // 'BGRA'

    private val contextLock = AppleReentrantLock()
    private var cachedContext: CIContext? = null

    private val sharedSrgbColorSpace: CGColorSpaceRef? by lazy {
        CGColorSpaceCreateWithName(kCGColorSpaceSRGB) ?: CGColorSpaceCreateDeviceRGB()
    }

    private val sharedSrgbColorSpacePropertyList: CFPropertyListRef? by lazy {
        val cs = CGColorSpaceCreateWithName(kCGColorSpaceSRGB) ?: return@lazy null
        try {
            CGColorSpaceCopyPropertyList(cs)
        } finally {
            CGColorSpaceRelease(cs)
        }
    }

    /**
     * Returns the lazily cached Metal-backed [CIContext] (falling back to default CoreImage options
     * only if no system Metal device is available).
     */
    fun sharedContext(): CIContext {
        cachedContext?.let { return it }
        return contextLock.withLock {
            cachedContext?.let { return it }
            val created = MTLCreateSystemDefaultDevice()?.let { device ->
                val metalDevice = interpretObjCPointer<objcnames.protocols.MTLDeviceProtocol>(device.objcPtr())
                CIContext.contextWithMTLDevice(metalDevice)
            } ?: CIContext.contextWithOptions(null)
            cachedContext = created
            created
        }
    }

    /**
     * Computes the halo padding (in points) required around a backdrop-filtered view so Gaussian
     * blur and `drop-shadow` sample real surrounding backdrop pixels without edge darkening.
     */
    fun computeBackdropPaddingPt(filter: CssFilter): Double {
        if (filter.isNone || !filter.hasActiveEffects) return 0.0
        val blurPx = (filter.blur?.resolvePx() ?: 0f).coerceAtLeast(0f).toDouble()
        val shadow = filter.dropShadow
        val shadowPad = if (shadow != null && shadow.color.a > 0) {
            val sBlur = (shadow.blurRadius.resolvePx() ?: 0f).coerceAtLeast(0f).toDouble()
            val sOx = abs((shadow.offsetX.resolvePx() ?: 0f).toDouble())
            val sOy = abs((shadow.offsetY.resolvePx() ?: 0f).toDouble())
            sBlur * 1.5 + max(sOx, sOy)
        } else {
            0.0
        }
        return ceil(max(blurPx * 1.5, shadowPad)).coerceIn(0.0, 128.0)
    }

    /**
     * Allocates a BGRA8888 sRGB [IOSurfaceRef] sized [widthPx] x [heightPx] suitable for
     * zero-copy Metal [CIContext] rendering and `CALayer.contents` presentation.
     * Caller owns releasing the returned surface via [releaseIoSurface].
     */
    fun createBgraIoSurface(widthPx: Int, heightPx: Int): IOSurfaceRef? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val alignedRowBytes = ((widthPx * 4 + 15) / 16) * 16
        val allocSize = (alignedRowBytes.toLong() * heightPx.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val props: CFDictionaryRef = memScoped {
            val keys = allocArray<COpaquePointerVar>(6)
            val vals = allocArray<COpaquePointerVar>(6)
            val vWidth = alloc<IntVar>().apply { value = widthPx }
            val vHeight = alloc<IntVar>().apply { value = heightPx }
            val vBpe = alloc<IntVar>().apply { value = 4 }
            val vBpr = alloc<IntVar>().apply { value = alignedRowBytes }
            val vSize = alloc<IntVar>().apply { value = allocSize }
            val vFmt = alloc<IntVar>().apply { value = IOSURFACE_PIXEL_FORMAT_BGRA }

            val nWidth = CFNumberCreate(null, kCFNumberSInt32Type, vWidth.ptr)
            val nHeight = CFNumberCreate(null, kCFNumberSInt32Type, vHeight.ptr)
            val nBpe = CFNumberCreate(null, kCFNumberSInt32Type, vBpe.ptr)
            val nBpr = CFNumberCreate(null, kCFNumberSInt32Type, vBpr.ptr)
            val nSize = CFNumberCreate(null, kCFNumberSInt32Type, vSize.ptr)
            val nFmt = CFNumberCreate(null, kCFNumberSInt32Type, vFmt.ptr)
            try {
                if (nWidth == null || nHeight == null || nBpe == null || nBpr == null || nSize == null || nFmt == null) {
                    return@memScoped null
                }
                keys[0] = kIOSurfaceWidth
                vals[0] = nWidth
                keys[1] = kIOSurfaceHeight
                vals[1] = nHeight
                keys[2] = kIOSurfaceBytesPerElement
                vals[2] = nBpe
                keys[3] = kIOSurfaceBytesPerRow
                vals[3] = nBpr
                keys[4] = kIOSurfaceAllocSize
                vals[4] = nSize
                keys[5] = kIOSurfacePixelFormat
                vals[5] = nFmt
                CFDictionaryCreate(
                    null,
                    keys,
                    vals,
                    6L,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr
                )
            } finally {
                nWidth?.let { CFRelease(it) }
                nHeight?.let { CFRelease(it) }
                nBpe?.let { CFRelease(it) }
                nBpr?.let { CFRelease(it) }
                nSize?.let { CFRelease(it) }
                nFmt?.let { CFRelease(it) }
            }
        } ?: return null

        return try {
            val created = IOSurfaceCreate(props) ?: return null
            val csKey = kIOSurfaceColorSpace
            val csPlist = sharedSrgbColorSpacePropertyList
            if (csKey != null && csPlist != null) {
                IOSurfaceSetValue(created, csKey, csPlist)
            }
            created
        } finally {
            CFRelease(props)
        }
    }

    /**
     * Releases an [IOSurfaceRef] previously returned by [createBgraIoSurface].
     */
    fun releaseIoSurface(surface: IOSurfaceRef?) {
        if (surface != null) {
            CFRelease(surface)
        }
    }

    /**
     * Bridges a retained [IOSurfaceRef] for assignment to `CALayer.contents` without transferring
     * ownership away from the caller's pool slot.
     */
    fun ioSurfaceToLayerContents(surface: IOSurfaceRef): Any? =
        CFBridgingRelease(CFRetain(surface))

    /**
     * Reusable 2-slot ping-pong BGRA [IOSurfaceRef] pool so CoreAnimation's WindowServer can read
     * the previous frame while Metal [CIContext] renders the next frame without stalls or tearing.
     */
    class IoSurfacePingPongPool {
        private val surfaces = arrayOfNulls<IOSurfaceRef?>(2)
        private var widthPx: Int = 0
        private var heightPx: Int = 0
        private var cursor: Int = 0

        fun acquire(reqWidthPx: Int, reqHeightPx: Int): IOSurfaceRef? {
            if (reqWidthPx <= 0 || reqHeightPx <= 0) return null
            if (widthPx != reqWidthPx || heightPx != reqHeightPx) {
                releaseAll()
                widthPx = reqWidthPx
                heightPx = reqHeightPx
            }
            val nextIdx = (cursor + 1) % surfaces.size
            val existing = surfaces[nextIdx]
            if (existing != null) {
                cursor = nextIdx
                return existing
            }
            val created = createBgraIoSurface(reqWidthPx, reqHeightPx) ?: return null
            surfaces[nextIdx] = created
            cursor = nextIdx
            return created
        }

        fun releaseAll() {
            for (i in surfaces.indices) {
                releaseIoSurface(surfaces[i])
                surfaces[i] = null
            }
            widthPx = 0
            heightPx = 0
            cursor = 0
        }
    }

    /**
     * Returns `true` when [filter] applies any non-blur effect (color matrix pixel ops or
     * `drop-shadow`) that requires CoreImage or [FastBackdropBlurKernel] rather than a pure blur layer.
     */
    fun requiresFullFilterPipeline(filter: CssFilter): Boolean =
        filter.hasPixelOps || filter.hasDropShadow

    /**
     * Builds the ordered CoreImage [CIFilter] chain (`CIAffineClamp`, `CIGaussianBlur`, fused
     * `CIColorMatrix`, and optional `drop-shadow` tint/blur/composite filters) for [filter].
     */
    fun buildCoreImageFilters(filter: CssFilter): List<CIFilter> {
        if (filter.isNone || !filter.hasActiveEffects) return emptyList()
        val filters = mutableListOf<CIFilter>()

        val blurPx = filter.blur?.resolvePx() ?: 0f
        if (blurPx > 0f) {
            CIFilter.filterWithName("CIAffineClamp")?.let { clamp ->
                clamp.setDefaults()
                filters.add(clamp)
            }
            CIFilter.filterWithName("CIGaussianBlur")?.let { blur ->
                blur.setDefaults()
                blur.setValue(NSNumber.numberWithDouble(blurPx.toDouble()), forKey = "inputRadius")
                filters.add(blur)
            }
        }

        if (filter.hasPixelOps) {
            createColorMatrixFilter(filter)?.let { filters.add(it) }
        }

        val shadow = filter.dropShadow
        if (shadow != null) {
            createShadowTintFilter(shadow)?.let { filters.add(it) }
            val shadowBlur = shadow.blurRadius.resolvePx() ?: 0f
            if (shadowBlur > 0f) {
                CIFilter.filterWithName("CIGaussianBlur")?.let { shadowBlurFilter ->
                    shadowBlurFilter.setDefaults()
                    shadowBlurFilter.setValue(NSNumber.numberWithDouble(shadowBlur.toDouble()), forKey = "inputRadius")
                    filters.add(shadowBlurFilter)
                }
            }
            CIFilter.filterWithName("CISourceOverCompositing")?.let { composite ->
                composite.setDefaults()
                filters.add(composite)
            }
        }

        return filters
    }

    /**
     * Applies all active operations in [filter] to [source] and returns the filtered [CIImage].
     *
     * @param yPointsUp CoreImage's native coordinate orientation (`true` for `CGImage`/`IOSurface`-backed
     * inputs where `+y` points upward; pass `false` if [source] is pre-flipped top-down).
     * @param scaleFactor Pixel-to-point backing scale factor applied to CSS `blur()` and `drop-shadow()` lengths.
     */
    fun applyToCiImage(
        source: CIImage,
        filter: CssFilter,
        yPointsUp: Boolean = true,
        scaleFactor: Double = 1.0
    ): CIImage {
        if (filter.isNone || !filter.hasActiveEffects) return source
        val scale = scaleFactor.coerceAtLeast(1.0)
        val extent = source.extent
        var current = source

        val blurPx = (filter.blur?.resolvePx() ?: 0f).coerceAtLeast(0f).toDouble() * scale
        if (blurPx > 0.0) {
            val clamped = current.imageByClampingToExtent()
            CIFilter.filterWithName("CIGaussianBlur")?.let { blurFilter ->
                blurFilter.setDefaults()
                blurFilter.setValue(clamped, forKey = "inputImage")
                blurFilter.setValue(NSNumber.numberWithDouble(blurPx), forKey = "inputRadius")
                blurFilter.outputImage?.let { blurred ->
                    current = blurred.imageByCroppingToRect(extent)
                }
            }
        }

        if (filter.hasPixelOps) {
            createColorMatrixFilter(filter)?.let { matrixFilter ->
                val unpremul = current.imageByUnpremultiplyingAlpha()
                matrixFilter.setValue(unpremul, forKey = "inputImage")
                matrixFilter.outputImage?.let { colorAdjusted ->
                    current = colorAdjusted.imageByPremultiplyingAlpha().imageByCroppingToRect(extent)
                }
            }
        }

        val shadow = filter.dropShadow
        if (shadow != null) {
            current = applyDropShadow(current, shadow, extent, yPointsUp, scale)
        }

        return current
    }

    /**
     * Filters [source] with all 10 W3C operations in [filter] scaled by [scaleFactor], crops to
     * [cropRectPx], and translates the origin to `(0, 0)` for direct GPU rendering via
     * [renderCiImageToIoSurface] or [renderFilteredCiImageToCgImage].
     */
    fun renderFilteredCiImage(
        source: CIImage,
        filter: CssFilter,
        cropRectPx: CValue<CGRect>,
        scaleFactor: Double = 1.0,
        yPointsUp: Boolean = true
    ): CIImage {
        val filtered = applyToCiImage(source, filter, yPointsUp = yPointsUp, scaleFactor = scaleFactor)
        val cropped = filtered.imageByCroppingToRect(cropRectPx)
        val (ox, oy) = cropRectPx.useContents { origin.x to origin.y }
        return if (ox != 0.0 || oy != 0.0) {
            cropped.imageByApplyingTransform(CGAffineTransformMakeTranslation(-ox, -oy))
        } else {
            cropped
        }
    }

    /**
     * Renders [ciImage] cropped to [cropRectPx] into [surface] on the Metal-backed [sharedContext].
     * Translates [cropRectPx] origin to `(0, 0)` so `(0, 0)` aligns to the surface's origin.
     */
    fun renderCiImageToIoSurface(
        ciImage: CIImage,
        cropRectPx: CValue<CGRect>,
        surface: IOSurfaceRef
    ): Boolean {
        val (ox, oy, w, h) = cropRectPx.useContents {
            doubleArrayOf(origin.x, origin.y, size.width, size.height)
        }
        if (w <= 0.0 || h <= 0.0) return false
        val cs = sharedSrgbColorSpace ?: return false
        val aligned = if (ox != 0.0 || oy != 0.0) {
            ciImage.imageByCroppingToRect(cropRectPx)
                .imageByApplyingTransform(CGAffineTransformMakeTranslation(-ox, -oy))
        } else {
            ciImage
        }
        sharedContext().render(
            aligned,
            toIOSurface = surface,
            bounds = CGRectMake(0.0, 0.0, w, h),
            colorSpace = cs
        )
        return true
    }

    /**
     * Renders [ciImage] inside [cropRectPx] to a `+1` retained [CGImageRef] via [sharedContext].
     * Caller owns releasing the returned [CGImageRef] with `CGImageRelease`.
     */
    fun renderFilteredCiImageToCgImage(
        ciImage: CIImage,
        cropRectPx: CValue<CGRect>
    ): CGImageRef? {
        val (w, h) = cropRectPx.useContents { size.width to size.height }
        if (w <= 0.0 || h <= 0.0) return null
        return sharedContext().createCGImage(ciImage, fromRect = cropRectPx)
    }

    /**
     * Filters [source] via CoreImage on a `CIContext` and returns a `+1` retained [CGImageRef].
     * Caller owns releasing the returned [CGImageRef] with `CGImageRelease`.
     */
    fun renderFilteredCgImage(
        source: CGImageRef,
        filter: CssFilter,
        width: Int,
        height: Int,
        scaleFactor: Double = 1.0
    ): CGImageRef? {
        if (width <= 0 || height <= 0 || filter.isNone || !filter.hasActiveEffects) return null
        val ciInput = CIImage.imageWithCGImage(source)
        val filtered = applyToCiImage(ciInput, filter, yPointsUp = true, scaleFactor = scaleFactor)
        val outRect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
        return sharedContext().createCGImage(filtered, fromRect = outRect)
    }

    private class AccelerateScratch {
        var bufA: IntArray = IntArray(0)
        var bufB: IntArray = IntArray(0)
        var tempBuf: ByteArray = ByteArray(0)
        var alphaMaskRow: ByteArray = ByteArray(0)
        var savedCorners: IntArray = IntArray(0)
        var lastTempCapW: Int = -1
        var lastTempCapH: Int = -1
        var lastTempStride: Int = -1
        var lastTempKernel: UInt = 0u
        var cachedReqTempBytes: Int = 0

        fun ensureCapacity(pixelCount: Int, tempBytes: Int, maskRowBytes: Int) {
            if (bufA.size < pixelCount) bufA = IntArray(pixelCount)
            if (bufB.size < pixelCount) bufB = IntArray(pixelCount)
            if (tempBuf.size < tempBytes) tempBuf = ByteArray(tempBytes)
            if (alphaMaskRow.size < maskRowBytes) {
                alphaMaskRow = ByteArray(maskRowBytes) { 0xFF.toByte() }
            }
        }

        fun ensureSavedCornerCapacity(needed: Int) {
            if (savedCorners.size < needed) {
                savedCorners = IntArray(needed)
            }
        }
    }

    private val scratchLock = AppleReentrantLock()
    private val scratchPool = ArrayList<AccelerateScratch>(4)

    private fun acquireScratch(): AccelerateScratch = scratchLock.withLock {
        if (scratchPool.isNotEmpty()) scratchPool.removeAt(scratchPool.lastIndex) else AccelerateScratch()
    }

    private fun releaseScratch(scratch: AccelerateScratch) = scratchLock.withLock {
        if (scratchPool.size < 4) {
            scratchPool.add(scratch)
        }
    }

    /**
     * Applies [filter] onto a premultiplied little-endian `BGRA_8888` (`0xAARRGGBB`) [IntArray]
     * region using Apple `Accelerate` (`vImageBoxConvolve_ARGB8888` + `vImagePremultipliedConstAlphaBlend_Planar8`)
     * for pure blur layers and [FastBackdropBlurKernel.applyBackdropToPremulBgraRegion] when pixel
     * ops or drop-shadow are active.
     */
    fun applyBackdropToPremulBgraRegion(
        filter: CssFilter,
        pixels: IntArray,
        strideWords: Int,
        bufferWidth: Int,
        bufferHeight: Int,
        regionX: Int,
        regionY: Int,
        regionWidth: Int,
        regionHeight: Int,
        cornerRadiusPx: Float = 0f,
        componentOpacity: Float = 1f
    ): Boolean {
        if (!filter.isNone && filter.hasActiveEffects && !filter.hasPixelOps && !filter.hasDropShadow) {
            val blurPx = (filter.blur?.resolvePx() ?: 0f).coerceAtLeast(0f)
            if (blurPx > 0f) {
                return applyAccelerateBlurOnlyPremulBgra(
                    blurPx = blurPx,
                    filterOpacity = filter.opacity ?: 1f,
                    pixels = pixels,
                    strideWords = strideWords,
                    bufferWidth = bufferWidth,
                    bufferHeight = bufferHeight,
                    regionX = regionX,
                    regionY = regionY,
                    regionWidth = regionWidth,
                    regionHeight = regionHeight,
                    cornerRadiusPx = cornerRadiusPx,
                    componentOpacity = componentOpacity
                )
            }
        }
        return FastBackdropBlurKernel.applyBackdropToPremulBgraRegion(
            filter = filter,
            pixels = pixels,
            strideWords = strideWords,
            bufferWidth = bufferWidth,
            bufferHeight = bufferHeight,
            regionX = regionX,
            regionY = regionY,
            regionWidth = regionWidth,
            regionHeight = regionHeight,
            cornerRadiusPx = cornerRadiusPx,
            componentOpacity = componentOpacity
        )
    }

    private fun applyAccelerateBlurOnlyPremulBgra(
        blurPx: Float,
        filterOpacity: Float,
        pixels: IntArray,
        strideWords: Int,
        bufferWidth: Int,
        bufferHeight: Int,
        regionX: Int,
        regionY: Int,
        regionWidth: Int,
        regionHeight: Int,
        cornerRadiusPx: Float,
        componentOpacity: Float
    ): Boolean {
        if (bufferWidth <= 0 || bufferHeight <= 0 || strideWords < bufferWidth) return false
        if (pixels.size < strideWords * (bufferHeight - 1) + bufferWidth) return false
        if (regionWidth <= 0 || regionHeight <= 0) return false

        val totalOpacity = (componentOpacity * filterOpacity.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val x0 = regionX.coerceIn(0, bufferWidth)
        val y0 = regionY.coerceIn(0, bufferHeight)
        val x1 = (regionX + regionWidth).coerceIn(0, bufferWidth)
        val y1 = (regionY + regionHeight).coerceIn(0, bufferHeight)
        if (x1 <= x0 || y1 <= y0 || totalOpacity <= 0.001f) {
            return true
        }

        val radii = CssFilter.boxBlurRadiiForGaussianSigma(blurPx)
        if (radii.none { it > 0 }) return true
        val maxRadius = radii.maxOrNull()?.coerceAtLeast(1) ?: 1
        val maxKernel = (maxRadius * 2 + 1).toUInt()
        val halo = radii.sum().coerceAtLeast(1)

        val capX0 = (x0 - halo).coerceIn(0, bufferWidth)
        val capY0 = (y0 - halo).coerceIn(0, bufferHeight)
        val capX1 = (x1 + halo).coerceIn(0, bufferWidth)
        val capY1 = (y1 + halo).coerceIn(0, bufferHeight)
        val capW = capX1 - capX0
        val capH = capY1 - capY0
        if (capW <= 0 || capH <= 0) return true

        val clippedW = x1 - x0
        val clippedH = y1 - y0
        val op255 = (totalOpacity * 255f).roundToInt().coerceIn(0, 255)
        val effectiveRadius = cornerRadiusPx.coerceIn(0f, min(regionWidth, regionHeight) * 0.5f)
        val checkRounded = effectiveRadius > 0.01f

        val scratch = acquireScratch()
        return try {
            if (scratch.lastTempCapW != capW ||
                scratch.lastTempCapH != capH ||
                scratch.lastTempStride != strideWords ||
                scratch.lastTempKernel != maxKernel
            ) {
                val reqTempBytes = memScoped {
                    val dSrc = alloc<vImage_Buffer>().apply {
                        data = null
                        width = capW.toULong()
                        height = capH.toULong()
                        rowBytes = (strideWords * 4).toULong()
                    }
                    val dDst = alloc<vImage_Buffer>().apply {
                        data = null
                        width = capW.toULong()
                        height = capH.toULong()
                        rowBytes = (capW * 4).toULong()
                    }
                    vImageBoxConvolve_ARGB8888(
                        src = dSrc.ptr,
                        dest = dDst.ptr,
                        tempBuffer = null,
                        srcOffsetToROI_X = 0uL,
                        srcOffsetToROI_Y = 0uL,
                        kernel_height = maxKernel,
                        kernel_width = maxKernel,
                        backgroundColor = null,
                        flags = kvImageEdgeExtend.toUInt() or kvImageGetTempBufferSize.toUInt()
                    )
                }
                scratch.cachedReqTempBytes = reqTempBytes.toInt().coerceAtLeast(1024)
                scratch.lastTempCapW = capW
                scratch.lastTempCapH = capH
                scratch.lastTempStride = strideWords
                scratch.lastTempKernel = maxKernel
            }
            val maskBytes = if (op255 < 255) clippedW * 4 else 0
            scratch.ensureCapacity(capW * capH, scratch.cachedReqTempBytes, maskBytes)

            var savedCount = 0
            if (checkRounded) {
                val rCeil = ceil(effectiveRadius).toInt()
                val topEnd = min(y1, regionY + rCeil)
                val botStart = maxOf(topEnd, regionY + regionHeight - rCeil).coerceIn(y0, y1)
                val rSq = effectiveRadius * effectiveRadius
                val rRegionH = regionHeight.toFloat()
                val regRight = regionX + regionWidth
                val maxCornerRows = ((topEnd - y0).coerceAtLeast(0) + (y1 - botStart).coerceAtLeast(0))
                val maxSavedPerRow = min(clippedW, rCeil * 2 + 4) + 3
                scratch.ensureSavedCornerCapacity((maxCornerRows * maxSavedPerRow).coerceAtLeast(16))
                val saved = scratch.savedCorners

                fun captureCornerRows(fromY: Int, untilY: Int) {
                    for (y in fromY until untilY) {
                        val py = (y - regionY).toFloat() + 0.5f
                        val dy = when {
                            py < effectiveRadius -> effectiveRadius - py
                            py > rRegionH - effectiveRadius -> py - (rRegionH - effectiveRadius)
                            else -> 0f
                        }
                        if (dy <= 0f) continue
                        val rem = rSq - dy * dy
                        val rowStart: Int
                        val rowEnd: Int
                        if (rem <= 0f) {
                            rowStart = clippedW
                            rowEnd = clippedW
                        } else {
                            val maxDx = sqrt(rem)
                            val inset = (effectiveRadius - maxDx - 0.5f)
                            val leftClipX = regionX + if (inset > 0f) ceil(inset).toInt() else 0
                            val rightClipX = regRight - if (inset > 0f) floor(inset).toInt() else 0
                            rowStart = if (leftClipX > x0) (leftClipX - x0).coerceAtMost(clippedW) else 0
                            rowEnd = if (rightClipX < x1) (rightClipX - x0).coerceAtLeast(rowStart) else clippedW
                        }
                        val rightLen = clippedW - rowEnd
                        if (rowStart <= 0 && rightLen <= 0) continue
                        val rowOff = y * strideWords + x0
                        saved[savedCount++] = rowOff
                        saved[savedCount++] = rowStart
                        saved[savedCount++] = rowEnd
                        for (lx in 0 until rowStart) {
                            saved[savedCount++] = pixels[rowOff + lx]
                        }
                        for (lx in rowEnd until clippedW) {
                            saved[savedCount++] = pixels[rowOff + lx]
                        }
                    }
                }
                if (y0 < topEnd) captureCornerRows(y0, topEnd)
                if (botStart < y1) captureCornerRows(botStart, y1)
            }

            pixels.usePinned { pinnedPix ->
                scratch.bufA.usePinned { pinnedA ->
                    scratch.bufB.usePinned { pinnedB ->
                        scratch.tempBuf.usePinned { pinnedTemp ->
                            memScoped {
                                val srcBuf = alloc<vImage_Buffer>().apply {
                                    data = pinnedPix.addressOf(capY0 * strideWords + capX0)
                                    width = capW.toULong()
                                    height = capH.toULong()
                                    rowBytes = (strideWords * 4).toULong()
                                }
                                val dstABuf = alloc<vImage_Buffer>().apply {
                                    data = pinnedA.addressOf(0)
                                    width = capW.toULong()
                                    height = capH.toULong()
                                    rowBytes = (capW * 4).toULong()
                                }
                                val dstBBuf = alloc<vImage_Buffer>().apply {
                                    data = pinnedB.addressOf(0)
                                    width = capW.toULong()
                                    height = capH.toULong()
                                    rowBytes = (capW * 4).toULong()
                                }
                                val tempPtr = pinnedTemp.addressOf(0)
                                val flags = kvImageEdgeExtend.toUInt()

                                var passIdx = 0
                                var resultInA = true
                                for (r in radii) {
                                    if (r <= 0) continue
                                    val k = (r * 2 + 1).toUInt()
                                    if (passIdx == 0) {
                                        vImageBoxConvolve_ARGB8888(srcBuf.ptr, dstABuf.ptr, tempPtr, 0uL, 0uL, k, k, null, flags)
                                        resultInA = true
                                    } else if (resultInA) {
                                        vImageBoxConvolve_ARGB8888(dstABuf.ptr, dstBBuf.ptr, tempPtr, 0uL, 0uL, k, k, null, flags)
                                        resultInA = false
                                    } else {
                                        vImageBoxConvolve_ARGB8888(dstBBuf.ptr, dstABuf.ptr, tempPtr, 0uL, 0uL, k, k, null, flags)
                                        resultInA = true
                                    }
                                    passIdx++
                                }

                                val subStart = (y0 - capY0) * capW + (x0 - capX0)
                                val srcArrPinned = if (resultInA) pinnedA else pinnedB
                                if (op255 >= 255) {
                                    val rowCopyBytes = (clippedW * 4).toULong()
                                    for (row in 0 until clippedH) {
                                        platform.posix.memcpy(
                                            pinnedPix.addressOf((y0 + row) * strideWords + x0),
                                            srcArrPinned.addressOf(subStart + row * capW),
                                            rowCopyBytes
                                        )
                                    }
                                } else {
                                    scratch.alphaMaskRow.usePinned { pinnedMask ->
                                        val topBuf = alloc<vImage_Buffer>().apply {
                                            data = srcArrPinned.addressOf(subStart)
                                            width = (clippedW * 4).toULong()
                                            height = clippedH.toULong()
                                            rowBytes = (capW * 4).toULong()
                                        }
                                        val maskBuf = alloc<vImage_Buffer>().apply {
                                            data = pinnedMask.addressOf(0)
                                            width = (clippedW * 4).toULong()
                                            height = clippedH.toULong()
                                            rowBytes = 0uL
                                        }
                                        val botBuf = alloc<vImage_Buffer>().apply {
                                            data = pinnedPix.addressOf(y0 * strideWords + x0)
                                            width = (clippedW * 4).toULong()
                                            height = clippedH.toULong()
                                            rowBytes = (strideWords * 4).toULong()
                                        }
                                        vImagePremultipliedConstAlphaBlend_Planar8(
                                            topBuf.ptr,
                                            op255.toUByte(),
                                            maskBuf.ptr,
                                            botBuf.ptr,
                                            botBuf.ptr,
                                            kvImageNoFlags.toUInt()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (savedCount > 0) {
                val saved = scratch.savedCorners
                var idx = 0
                while (idx < savedCount) {
                    val rowOff = saved[idx++]
                    val rowStart = saved[idx++]
                    val rowEnd = saved[idx++]
                    for (lx in 0 until rowStart) {
                        pixels[rowOff + lx] = saved[idx++]
                    }
                    for (lx in rowEnd until clippedW) {
                        pixels[rowOff + lx] = saved[idx++]
                    }
                }
            }
            true
        } finally {
            releaseScratch(scratch)
        }
    }

    /**
     * Renders a CoreGraphics offscreen [CGImageRef] through CoreImage [filter] into a newly
     * allocated premultiplied little-endian `BGRA_8888` (`0xAARRGGBB`) [IntArray] of size
     * `width * height`.
     */
    fun renderCgImageWithFilterToPremulBgra(
        source: CGImageRef,
        filter: CssFilter,
        width: Int,
        height: Int
    ): IntArray? {
        if (width <= 0 || height <= 0) return null
        val outPixels = IntArray(width * height)
        val ciInput = CIImage.imageWithCGImage(source)
        val output = if (!filter.isNone && filter.hasActiveEffects) {
            applyToCiImage(ciInput, filter, yPointsUp = true)
        } else {
            ciInput
        }
        val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return null
        return try {
            val rowBytes = width.toLong() * 4L
            val bounds = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
            outPixels.usePinned { pinned ->
                sharedContext().render(
                    output,
                    toBitmap = pinned.addressOf(0),
                    rowBytes = rowBytes,
                    bounds = bounds,
                    format = kCIFormatBGRA8,
                    colorSpace = colorSpace
                )
            }
            outPixels
        } finally {
            CGColorSpaceRelease(colorSpace)
        }
    }

    /**
     * Creates a little-endian premultiplied `BGRA_8888` (`0xAARRGGBB`) `CGBitmapContext` backed
     * directly by [pixels], returning the [platform.CoreGraphics.CGContextRef] (caller must
     * release with `CGContextRelease`).
     */
    fun renderPremulBgraToCgContext(
        pixels: IntArray,
        width: Int,
        height: Int,
        drawBlock: (platform.CoreGraphics.CGContextRef) -> Unit
    ): Boolean {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return false
        val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return false
        val bitmapInfo = kCGBitmapByteOrder32Little or CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value
        return try {
            pixels.usePinned { pinned ->
                val ctx = CGBitmapContextCreate(
                    data = pinned.addressOf(0),
                    width = width.toULong(),
                    height = height.toULong(),
                    bitsPerComponent = 8uL,
                    bytesPerRow = (width * 4).toULong(),
                    space = colorSpace,
                    bitmapInfo = bitmapInfo
                ) ?: return false
                try {
                    drawBlock(ctx)
                } finally {
                    CGContextRelease(ctx)
                }
            }
            true
        } finally {
            CGColorSpaceRelease(colorSpace)
        }
    }

    private fun createColorMatrixFilter(filter: CssFilter): CIFilter? {
        val m = CssFilterColorMatrix.buildMatrix4x5(filter)
        val matrixFilter = CIFilter.filterWithName("CIColorMatrix") ?: return null
        matrixFilter.setDefaults()
        matrixFilter.setValue(
            CIVector.vectorWithX(m[0].toDouble(), Y = m[1].toDouble(), Z = m[2].toDouble(), W = m[3].toDouble()),
            forKey = "inputRVector"
        )
        matrixFilter.setValue(
            CIVector.vectorWithX(m[5].toDouble(), Y = m[6].toDouble(), Z = m[7].toDouble(), W = m[8].toDouble()),
            forKey = "inputGVector"
        )
        matrixFilter.setValue(
            CIVector.vectorWithX(m[10].toDouble(), Y = m[11].toDouble(), Z = m[12].toDouble(), W = m[13].toDouble()),
            forKey = "inputBVector"
        )
        matrixFilter.setValue(
            CIVector.vectorWithX(m[15].toDouble(), Y = m[16].toDouble(), Z = m[17].toDouble(), W = m[18].toDouble()),
            forKey = "inputAVector"
        )
        matrixFilter.setValue(
            CIVector.vectorWithX(m[4].toDouble(), Y = m[9].toDouble(), Z = m[14].toDouble(), W = m[19].toDouble()),
            forKey = "inputBiasVector"
        )
        return matrixFilter
    }

    private fun createShadowTintFilter(shadow: com.nexustools.genericui.style.css.CssShadow): CIFilter? {
        val tintFilter = CIFilter.filterWithName("CIColorMatrix") ?: return null
        tintFilter.setDefaults()
        val cr = shadow.color.r / 255.0
        val cg = shadow.color.g / 255.0
        val cb = shadow.color.b / 255.0
        val ca = shadow.color.a / 255.0
        tintFilter.setValue(CIVector.vectorWithX(0.0, Y = 0.0, Z = 0.0, W = 0.0), forKey = "inputRVector")
        tintFilter.setValue(CIVector.vectorWithX(0.0, Y = 0.0, Z = 0.0, W = 0.0), forKey = "inputGVector")
        tintFilter.setValue(CIVector.vectorWithX(0.0, Y = 0.0, Z = 0.0, W = 0.0), forKey = "inputBVector")
        tintFilter.setValue(CIVector.vectorWithX(0.0, Y = 0.0, Z = 0.0, W = ca), forKey = "inputAVector")
        tintFilter.setValue(CIVector.vectorWithX(cr, Y = cg, Z = cb, W = 0.0), forKey = "inputBiasVector")
        return tintFilter
    }

    private fun applyDropShadow(
        source: CIImage,
        shadow: com.nexustools.genericui.style.css.CssShadow,
        extent: kotlinx.cinterop.CValue<platform.CoreGraphics.CGRect>,
        yPointsUp: Boolean,
        scaleFactor: Double = 1.0
    ): CIImage {
        val tintFilter = createShadowTintFilter(shadow) ?: return source
        tintFilter.setValue(source, forKey = "inputImage")
        var shadowImg = tintFilter.outputImage ?: return source

        val blurRadius = (shadow.blurRadius.resolvePx() ?: 0f).coerceAtLeast(0f).toDouble() * scaleFactor
        if (blurRadius > 0.0) {
            CIFilter.filterWithName("CIGaussianBlur")?.let { blur ->
                blur.setDefaults()
                blur.setValue(shadowImg, forKey = "inputImage")
                blur.setValue(NSNumber.numberWithDouble(blurRadius), forKey = "inputRadius")
                blur.outputImage?.let { blurred -> shadowImg = blurred }
            }
        }

        val dx = (shadow.offsetX.resolvePx() ?: 0f).toDouble() * scaleFactor
        val rawDy = (shadow.offsetY.resolvePx() ?: 0f).toDouble() * scaleFactor
        val dy = if (yPointsUp) -rawDy else rawDy
        val shifted = shadowImg.imageByApplyingTransform(CGAffineTransformMakeTranslation(dx, dy))
        return source.imageByCompositingOverImage(shifted).imageByCroppingToRect(extent)
    }
}
