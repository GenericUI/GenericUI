package com.nexustools.genericui.image.codec

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageMetadata
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.abs

/**
 * Pure-Kotlin Multiplatform WebP (`RIFF`/`WEBP`, `VP8L` lossless, `VP8X`, `ALPH`, `ANIM`/`ANMF`)
 * header parser and lossless decoder, with optional native `libwebp` C interop hooks.
 *
 * Pure-Kotlin decoding supports all WebP Lossless (`VP8L`) features (canonical Huffman prefix trees,
 * simple 1/2-symbol trees, meta-Huffman groups, color cache, LZ77 backward references with 2D distance
 * mapping, and Predictor / Cross-Color / Subtract-Green / Color-Indexing transforms) as well as `ALPH`
 * alpha plane decoding. Lossy `VP8 ` streams require an active platform native codec hook and fail fast
 * with [UnsupportedOperationException] when no native hook is registered.
 */
object LibWebpCodec {
    var nativeLibWebpDecodeHook: ((webpBytes: ByteArray) -> ImageBuffer?)? = null
    var nativeLibWebpEncodeHook: ((buffer: ImageBuffer, quality: Int) -> ByteArray?)? = null

    private const val COLOR_CACHE_HASH_MULT = 0x1e35a7bd
    private const val NUM_LITERAL_CODES = 256
    private const val NUM_LENGTH_CODES = 24
    private const val NUM_DISTANCE_CODES = 40
    private const val MAX_ALLOWED_CODE_LENGTH = 15

    private val CODE_LENGTH_CODE_ORDER = intArrayOf(
        17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    )

    private val DISTANCE_MAP = intArrayOf(
        0x18, 0x07, 0x17, 0x19, 0x28, 0x06, 0x27, 0x29, 0x16, 0x1a,
        0x26, 0x2a, 0x38, 0x05, 0x37, 0x39, 0x15, 0x1b, 0x36, 0x3a,
        0x25, 0x2b, 0x48, 0x04, 0x47, 0x49, 0x14, 0x1c, 0x35, 0x3b,
        0x46, 0x4a, 0x24, 0x2c, 0x58, 0x45, 0x4b, 0x34, 0x3c, 0x03,
        0x57, 0x59, 0x13, 0x1d, 0x56, 0x5a, 0x23, 0x2d, 0x44, 0x4c,
        0x55, 0x5b, 0x33, 0x3d, 0x68, 0x02, 0x67, 0x69, 0x12, 0x1e,
        0x66, 0x6a, 0x22, 0x2e, 0x54, 0x5c, 0x43, 0x4d, 0x65, 0x6b,
        0x32, 0x3e, 0x78, 0x01, 0x77, 0x79, 0x53, 0x5d, 0x11, 0x1f,
        0x64, 0x6c, 0x42, 0x4e, 0x76, 0x7a, 0x21, 0x2f, 0x75, 0x7b,
        0x31, 0x3f, 0x63, 0x6d, 0x52, 0x5e, 0x00, 0x74, 0x7c, 0x41,
        0x4f, 0x10, 0x20, 0x62, 0x6e, 0x30, 0x73, 0x7d, 0x51, 0x5f,
        0x40, 0x72, 0x7e, 0x61, 0x6f, 0x50, 0x71, 0x7f, 0x60, 0x70
    )

    fun probeWebpMetadata(bytes: ByteArray): ImageMetadata {
        if (!isValidRiffWebpHeader(bytes)) {
            return ImageMetadata(1, 1, ImageFormat.WEBP)
        }

        var width = 1
        var height = 1
        var hasDimensions = false
        var hasAlpha = false
        var isAnimated = false
        var anmfCount = 0

        val riffEnd = resolveRiffEnd(bytes)
        var offset = 12
        while (offset + 8 <= riffEnd) {
            val chunkSize = readUInt32LE(bytes, offset + 4)
            if (chunkSize < 0) break
            val dataStart = offset + 8
            val dataEnd = minOf(riffEnd.toLong(), dataStart.toLong() + chunkSize).toInt()
            val available = dataEnd - dataStart

            when {
                matchesFourCc(bytes, offset, 'V', 'P', '8', 'X') && available >= 10 -> {
                    val flags = bytes[dataStart].toInt() and 0xFF
                    isAnimated = (flags and 0x02) != 0
                    hasAlpha = (flags and 0x10) != 0
                    width = 1 + readUInt24LE(bytes, dataStart + 4)
                    height = 1 + readUInt24LE(bytes, dataStart + 7)
                    hasDimensions = true
                }
                matchesFourCc(bytes, offset, 'A', 'L', 'P', 'H') -> {
                    hasAlpha = true
                }
                matchesFourCc(bytes, offset, 'A', 'N', 'I', 'M') -> {
                    isAnimated = true
                }
                matchesFourCc(bytes, offset, 'A', 'N', 'M', 'F') -> {
                    isAnimated = true
                    anmfCount++
                }
                matchesFourCc(bytes, offset, 'V', 'P', '8', ' ') && available >= 10 -> {
                    if (!hasDimensions) {
                        val isKeyframe = (bytes[dataStart].toInt() and 0x01) == 0
                        if (isKeyframe &&
                            (bytes[dataStart + 3].toInt() and 0xFF) == 0x9D &&
                            (bytes[dataStart + 4].toInt() and 0xFF) == 0x01 &&
                            (bytes[dataStart + 5].toInt() and 0xFF) == 0x2A
                        ) {
                            width = (readUInt16LE(bytes, dataStart + 6) and 0x3FFF).coerceAtLeast(1)
                            height = (readUInt16LE(bytes, dataStart + 8) and 0x3FFF).coerceAtLeast(1)
                            hasDimensions = true
                        }
                    }
                }
                matchesFourCc(bytes, offset, 'V', 'P', '8', 'L') && available >= 5 -> {
                    if ((bytes[dataStart].toInt() and 0xFF) == 0x2F) {
                        val packed = readInt32LE(bytes, dataStart + 1)
                        if (!hasDimensions) {
                            width = 1 + (packed and 0x3FFF)
                            height = 1 + ((packed ushr 14) and 0x3FFF)
                            hasDimensions = true
                        }
                        if (((packed ushr 28) and 0x01) != 0) {
                            hasAlpha = true
                        }
                    }
                }
            }

            val paddedSize = chunkSize + (chunkSize and 1L)
            val nextOffset = dataStart.toLong() + paddedSize
            if (nextOffset <= offset || nextOffset > Int.MAX_VALUE) break
            offset = nextOffset.toInt()
        }

        return ImageMetadata(
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            format = ImageFormat.WEBP,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = ImageColorSpace.SRGB,
            hasAlpha = hasAlpha,
            isAnimated = isAnimated,
            frameCount = anmfCount.coerceAtLeast(1)
        )
    }

    fun decode(bytes: ByteArray): ImageBuffer {
        nativeLibWebpDecodeHook?.invoke(bytes)?.let { return it }

        if (!isValidRiffWebpHeader(bytes)) {
            throw IllegalArgumentException("Invalid WebP stream: missing RIFF/WEBP container header")
        }

        val riffEnd = resolveRiffEnd(bytes)
        var hasVp8X = false
        var canvasWidth = 0
        var canvasHeight = 0
        var firstAnmfStart = -1
        var firstAnmfEnd = -1

        var offset = 12
        while (offset + 8 <= riffEnd) {
            val chunkSize = readUInt32LE(bytes, offset + 4)
            if (chunkSize < 0) {
                throw IllegalArgumentException("Invalid WebP RIFF chunk size at offset $offset")
            }
            val dataStart = offset + 8
            val dataEndLong = dataStart.toLong() + chunkSize
            if (dataEndLong > riffEnd) {
                throw IllegalArgumentException("Truncated WebP RIFF chunk at offset $offset")
            }
            val dataEnd = dataEndLong.toInt()

            when {
                matchesFourCc(bytes, offset, 'V', 'P', '8', 'X') -> {
                    if (dataEnd - dataStart < 10) {
                        throw IllegalArgumentException("Truncated VP8X chunk")
                    }
                    hasVp8X = true
                    canvasWidth = 1 + readUInt24LE(bytes, dataStart + 4)
                    canvasHeight = 1 + readUInt24LE(bytes, dataStart + 7)
                }
                matchesFourCc(bytes, offset, 'V', 'P', '8', 'L') -> {
                    return decodeVp8lChunk(bytes, dataStart, dataEnd)
                }
                matchesFourCc(bytes, offset, 'V', 'P', '8', ' ') -> {
                    throw UnsupportedOperationException(
                        "WebP VP8 lossy decoding requires an active native ImageCodecNativeImpl on this platform"
                    )
                }
                matchesFourCc(bytes, offset, 'A', 'N', 'M', 'F') -> {
                    if (firstAnmfStart < 0) {
                        firstAnmfStart = dataStart
                        firstAnmfEnd = dataEnd
                    }
                }
            }

            val nextOffset = dataStart.toLong() + chunkSize + (chunkSize and 1L)
            if (nextOffset <= offset || nextOffset > Int.MAX_VALUE) break
            offset = nextOffset.toInt()
        }

        if (firstAnmfStart >= 0) {
            return decodeFirstAnmfFrame(bytes, firstAnmfStart, firstAnmfEnd, hasVp8X, canvasWidth, canvasHeight)
        }

        throw IllegalArgumentException("WebP stream does not contain a VP8L or VP8 image chunk")
    }

    /**
     * Decodes a WebP `ALPH` chunk payload into an unpacked row-major alpha plane (`0..255` per pixel).
     * Supports raw uncompressed alpha (`method == 0`), VP8L-compressed alpha (`method == 1`), and all
     * four WebP alpha spatial filters (`0 = None`, `1 = Horizontal`, `2 = Vertical`, `3 = Gradient`).
     */
    internal fun decodeAlphaChunk(alphBytes: ByteArray, width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0) { "Alpha dimensions must be positive: ${width}x$height" }
        require(alphBytes.isNotEmpty()) { "Empty WebP ALPH chunk" }

        val flags = alphBytes[0].toInt() and 0xFF
        val compressionMethod = flags and 0x03
        val filteringMethod = (flags ushr 2) and 0x03
        val totalPixels = width * height

        val alpha = IntArray(totalPixels)
        when (compressionMethod) {
            0 -> {
                if (alphBytes.size < 1 + totalPixels) {
                    throw IllegalArgumentException("Truncated uncompressed WebP ALPH chunk")
                }
                for (i in 0 until totalPixels) {
                    alpha[i] = alphBytes[1 + i].toInt() and 0xFF
                }
            }
            1 -> {
                val reader = BitReader(alphBytes, 1, alphBytes.size)
                val pixels = decodeVp8lPayload(reader, width, height)
                for (i in 0 until totalPixels) {
                    alpha[i] = (pixels[i] ushr 8) and 0xFF
                }
            }
            else -> throw IllegalArgumentException("Unsupported WebP ALPH compression method: $compressionMethod")
        }

        unfilterAlphaPlane(alpha, width, height, filteringMethod)
        return ByteArray(totalPixels) { alpha[it].toByte() }
    }

    private fun decodeFirstAnmfFrame(
        bytes: ByteArray,
        anmfStart: Int,
        anmfEnd: Int,
        hasVp8X: Boolean,
        canvasWidth: Int,
        canvasHeight: Int
    ): ImageBuffer {
        if (anmfEnd - anmfStart < 16) {
            throw IllegalArgumentException("Truncated WebP ANMF chunk")
        }
        val frameX = readUInt24LE(bytes, anmfStart) * 2
        val frameY = readUInt24LE(bytes, anmfStart + 3) * 2

        var subOffset = anmfStart + 16
        while (subOffset + 8 <= anmfEnd) {
            val subSize = readUInt32LE(bytes, subOffset + 4)
            if (subSize < 0) {
                throw IllegalArgumentException("Invalid ANMF sub-chunk size at offset $subOffset")
            }
            val subStart = subOffset + 8
            val subEndLong = subStart.toLong() + subSize
            if (subEndLong > anmfEnd) {
                throw IllegalArgumentException("Truncated ANMF sub-chunk at offset $subOffset")
            }
            val subEnd = subEndLong.toInt()

            when {
                matchesFourCc(bytes, subOffset, 'V', 'P', '8', 'L') -> {
                    val frameBuffer = decodeVp8lChunk(bytes, subStart, subEnd)
                    if (!hasVp8X || (frameX == 0 && frameY == 0 &&
                            frameBuffer.width == canvasWidth && frameBuffer.height == canvasHeight)
                    ) {
                        return frameBuffer
                    }
                    val canvas = ImageBuffer(
                        canvasWidth.coerceAtLeast(1),
                        canvasHeight.coerceAtLeast(1),
                        PixelFormat.RGBA_8888,
                        ImageColorSpace.SRGB
                    )
                    blitRgbaFrame(frameBuffer, canvas, frameX, frameY)
                    return canvas
                }
                matchesFourCc(bytes, subOffset, 'V', 'P', '8', ' ') -> {
                    throw UnsupportedOperationException(
                        "WebP VP8 lossy decoding requires an active native ImageCodecNativeImpl on this platform"
                    )
                }
            }

            val nextSub = subStart.toLong() + subSize + (subSize and 1L)
            if (nextSub <= subOffset || nextSub > Int.MAX_VALUE) break
            subOffset = nextSub.toInt()
        }

        throw IllegalArgumentException("WebP ANMF chunk does not contain a VP8L or VP8 frame")
    }

    private fun blitRgbaFrame(src: ImageBuffer, dst: ImageBuffer, dstX: Int, dstY: Int) {
        val srcBytes = src.bytes
        val dstBytes = dst.bytes
        for (y in 0 until src.height) {
            val ty = dstY + y
            if (ty !in 0 until dst.height) continue
            for (x in 0 until src.width) {
                val tx = dstX + x
                if (tx !in 0 until dst.width) continue
                val sIdx = (y * src.width + x) * 4
                val dIdx = (ty * dst.width + tx) * 4
                dstBytes[dIdx] = srcBytes[sIdx]
                dstBytes[dIdx + 1] = srcBytes[sIdx + 1]
                dstBytes[dIdx + 2] = srcBytes[sIdx + 2]
                dstBytes[dIdx + 3] = srcBytes[sIdx + 3]
            }
        }
    }

    private fun decodeVp8lChunk(bytes: ByteArray, start: Int, end: Int): ImageBuffer {
        if (end - start < 5) {
            throw IllegalArgumentException("Truncated VP8L header")
        }
        val signature = bytes[start].toInt() and 0xFF
        if (signature != 0x2F) {
            throw IllegalArgumentException("Invalid VP8L signature byte: 0x${signature.toString(16)}")
        }
        val header = readInt32LE(bytes, start + 1)
        val width = 1 + (header and 0x3FFF)
        val height = 1 + ((header ushr 14) and 0x3FFF)
        val version = (header ushr 29) and 0x07
        if (version != 0) {
            throw UnsupportedOperationException("Unsupported VP8L bitstream version: $version")
        }

        val reader = BitReader(bytes, start + 5, end)
        val argbPixels = decodeVp8lPayload(reader, width, height)

        val out = ImageBuffer(width, height, PixelFormat.RGBA_8888, ImageColorSpace.SRGB)
        val dst = out.bytes
        var dstPos = 0
        for (i in argbPixels.indices) {
            val argb = argbPixels[i]
            dst[dstPos++] = ((argb ushr 16) and 0xFF).toByte()
            dst[dstPos++] = ((argb ushr 8) and 0xFF).toByte()
            dst[dstPos++] = (argb and 0xFF).toByte()
            dst[dstPos++] = ((argb ushr 24) and 0xFF).toByte()
        }
        return out
    }

    private fun decodeVp8lPayload(reader: BitReader, width: Int, height: Int): IntArray {
        val transforms = ArrayList<Vp8lTransform>(4)
        var seenMask = 0
        var currentWidth = width

        while (reader.readBits(1) == 1) {
            val transformType = reader.readBits(2)
            val typeBit = 1 shl transformType
            if ((seenMask and typeBit) != 0) {
                throw IllegalArgumentException("Duplicate VP8L transform type: $transformType")
            }
            seenMask = seenMask or typeBit

            when (transformType) {
                0 -> { // Predictor transform
                    val sizeBits = reader.readBits(3) + 2
                    val subW = divCeil(currentWidth, 1 shl sizeBits)
                    val subH = divCeil(height, 1 shl sizeBits)
                    val predData = decodeImageStream(reader, subW, subH, isTopLevel = false)
                    transforms.add(Vp8lTransform.Predictor(currentWidth, sizeBits, subW, predData))
                }
                1 -> { // Cross-color transform
                    val sizeBits = reader.readBits(3) + 2
                    val subW = divCeil(currentWidth, 1 shl sizeBits)
                    val subH = divCeil(height, 1 shl sizeBits)
                    val colorData = decodeImageStream(reader, subW, subH, isTopLevel = false)
                    transforms.add(Vp8lTransform.CrossColor(currentWidth, sizeBits, subW, colorData))
                }
                2 -> { // Subtract green transform
                    transforms.add(Vp8lTransform.SubtractGreen)
                }
                3 -> { // Color indexing (palette) transform
                    val colorTableSize = reader.readBits(8) + 1
                    val palette = decodeImageStream(reader, colorTableSize, 1, isTopLevel = false)
                    for (i in 1 until colorTableSize) {
                        palette[i] = addArgbMod256(palette[i], palette[i - 1])
                    }
                    val widthBits = when {
                        colorTableSize <= 2 -> 3
                        colorTableSize <= 4 -> 2
                        colorTableSize <= 16 -> 1
                        else -> 0
                    }
                    transforms.add(Vp8lTransform.ColorIndexing(currentWidth, widthBits, palette))
                    currentWidth = divCeil(currentWidth, 1 shl widthBits)
                }
            }
        }

        var pixels = decodeImageStream(reader, currentWidth, height, isTopLevel = true)

        for (i in transforms.lastIndex downTo 0) {
            pixels = when (val tr = transforms[i]) {
                is Vp8lTransform.SubtractGreen -> applyInverseSubtractGreen(pixels)
                is Vp8lTransform.CrossColor -> applyInverseCrossColor(pixels, tr.width, height, tr.sizeBits, tr.subWidth, tr.colorData)
                is Vp8lTransform.Predictor -> applyInversePredictor(pixels, tr.width, height, tr.sizeBits, tr.subWidth, tr.predData)
                is Vp8lTransform.ColorIndexing -> applyInverseColorIndexing(pixels, tr.originalWidth, height, tr.widthBits, tr.palette)
            }
        }

        return pixels
    }

    private fun decodeImageStream(
        reader: BitReader,
        width: Int,
        height: Int,
        isTopLevel: Boolean
    ): IntArray {
        val useColorCache = reader.readBits(1) == 1
        var colorCacheBits = 0
        var colorCache: IntArray? = null
        if (useColorCache) {
            colorCacheBits = reader.readBits(4)
            if (colorCacheBits !in 1..11) {
                throw IllegalArgumentException("Invalid VP8L color cache bits: $colorCacheBits")
            }
            colorCache = IntArray(1 shl colorCacheBits)
        }

        var huffmanBits = 0
        var huffmanWidth = 0
        var entropyImage: IntArray? = null
        var numGroups = 1

        if (isTopLevel && reader.readBits(1) == 1) {
            huffmanBits = reader.readBits(3) + 2
            huffmanWidth = divCeil(width, 1 shl huffmanBits)
            val huffmanHeight = divCeil(height, 1 shl huffmanBits)
            val metaPixels = decodeImageStream(reader, huffmanWidth, huffmanHeight, isTopLevel = false)
            var maxGroupIdx = 0
            for (px in metaPixels) {
                val idx = (px ushr 8) and 0xFFFF
                if (idx > maxGroupIdx) maxGroupIdx = idx
            }
            entropyImage = metaPixels
            numGroups = maxGroupIdx + 1
        }

        val cacheSize = colorCache?.size ?: 0
        val greenAlphabetSize = NUM_LITERAL_CODES + NUM_LENGTH_CODES + cacheSize
        val groups = Array(numGroups) {
            HuffmanGroup(
                green = readHuffmanCode(reader, greenAlphabetSize),
                red = readHuffmanCode(reader, NUM_LITERAL_CODES),
                blue = readHuffmanCode(reader, NUM_LITERAL_CODES),
                alpha = readHuffmanCode(reader, NUM_LITERAL_CODES),
                distance = readHuffmanCode(reader, NUM_DISTANCE_CODES)
            )
        }

        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        val cacheShift = 32 - colorCacheBits
        var pos = 0

        while (pos < totalPixels) {
            val group = if (entropyImage != null) {
                val x = pos % width
                val y = pos / width
                val tileIdx = (y ushr huffmanBits) * huffmanWidth + (x ushr huffmanBits)
                val groupIdx = (entropyImage[tileIdx] ushr 8) and 0xFFFF
                groups[groupIdx]
            } else {
                groups[0]
            }

            val code = group.green.decodeSymbol(reader)
            when {
                code < NUM_LITERAL_CODES -> {
                    val green = code
                    val red = group.red.decodeSymbol(reader)
                    val blue = group.blue.decodeSymbol(reader)
                    val alpha = group.alpha.decodeSymbol(reader)
                    val argb = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                    pixels[pos++] = argb
                    if (colorCache != null) {
                        val key = (argb * COLOR_CACHE_HASH_MULT) ushr cacheShift
                        colorCache[key] = argb
                    }
                }
                code < NUM_LITERAL_CODES + NUM_LENGTH_CODES -> {
                    val length = readCopyLength(code - NUM_LITERAL_CODES, reader)
                    val distPrefixCode = group.distance.decodeSymbol(reader)
                    val distSym = readCopyLength(distPrefixCode, reader)
                    val distance = if (distSym > 120) {
                        distSym - 120
                    } else {
                        val packed = DISTANCE_MAP[distSym - 1]
                        val dy = packed ushr 4
                        val dx = 8 - (packed and 0x0F)
                        (dy * width + dx).coerceAtLeast(1)
                    }
                    if (distance <= 0 || distance > pos || pos + length > totalPixels) {
                        throw IllegalArgumentException(
                            "Invalid VP8L LZ77 reference at pixel $pos (distance=$distance, length=$length)"
                        )
                    }
                    for (k in 0 until length) {
                        val px = pixels[pos - distance]
                        pixels[pos++] = px
                        if (colorCache != null) {
                            val key = (px * COLOR_CACHE_HASH_MULT) ushr cacheShift
                            colorCache[key] = px
                        }
                    }
                }
                else -> {
                    val cacheIdx = code - (NUM_LITERAL_CODES + NUM_LENGTH_CODES)
                    if (colorCache == null || cacheIdx !in colorCache.indices) {
                        throw IllegalArgumentException("Invalid VP8L color cache index: $cacheIdx")
                    }
                    pixels[pos++] = colorCache[cacheIdx]
                }
            }
        }

        return pixels
    }

    private fun readHuffmanCode(reader: BitReader, alphabetSize: Int): HuffmanTree {
        val simple = reader.readBits(1)
        if (simple == 1) {
            val numSymbols = reader.readBits(1) + 1
            val isFirst8Bits = reader.readBits(1)
            val sym0 = reader.readBits(1 + 7 * isFirst8Bits)
            if (sym0 >= alphabetSize) {
                throw IllegalArgumentException("VP8L simple Huffman symbol $sym0 >= alphabetSize $alphabetSize")
            }
            if (numSymbols == 1) {
                return HuffmanTree.singleSymbol(sym0)
            }
            val sym1 = reader.readBits(8)
            if (sym1 >= alphabetSize) {
                throw IllegalArgumentException("VP8L simple Huffman symbol $sym1 >= alphabetSize $alphabetSize")
            }
            if (sym0 == sym1) {
                return HuffmanTree.singleSymbol(sym0)
            }
            return HuffmanTree.twoSymbols(minOf(sym0, sym1), maxOf(sym0, sym1))
        }

        val numCodeLengths = reader.readBits(4) + 4
        val clLengths = IntArray(19)
        for (i in 0 until numCodeLengths) {
            clLengths[CODE_LENGTH_CODE_ORDER[i]] = reader.readBits(3)
        }
        val clTree = HuffmanTree.build(clLengths)

        val useMaxSymbol = reader.readBits(1)
        val maxSymbolLimit = if (useMaxSymbol == 0) {
            alphabetSize
        } else {
            val lengthBits = 2 + 2 * reader.readBits(3)
            val limit = 2 + reader.readBits(lengthBits)
            if (limit > alphabetSize) {
                throw IllegalArgumentException("VP8L max_symbol $limit exceeds alphabetSize $alphabetSize")
            }
            limit
        }

        val codeLengths = IntArray(alphabetSize)
        var prevCodeLen = 8
        var remainingSymbols = maxSymbolLimit
        var sym = 0

        while (sym < alphabetSize) {
            if (remainingSymbols-- == 0) break
            val cl = clTree.decodeSymbol(reader)
            when {
                cl < 16 -> {
                    codeLengths[sym++] = cl
                    if (cl != 0) prevCodeLen = cl
                }
                cl == 16 -> {
                    val repeat = reader.readBits(2) + 3
                    if (sym + repeat > alphabetSize) {
                        throw IllegalArgumentException("VP8L code length repeat-16 overflow")
                    }
                    for (r in 0 until repeat) {
                        codeLengths[sym++] = prevCodeLen
                    }
                }
                cl == 17 -> {
                    val repeat = reader.readBits(3) + 3
                    if (sym + repeat > alphabetSize) {
                        throw IllegalArgumentException("VP8L code length repeat-17 overflow")
                    }
                    sym += repeat
                }
                else -> { // 18
                    val repeat = reader.readBits(7) + 11
                    if (sym + repeat > alphabetSize) {
                        throw IllegalArgumentException("VP8L code length repeat-18 overflow")
                    }
                    sym += repeat
                }
            }
        }

        return HuffmanTree.build(codeLengths)
    }

    private fun readCopyLength(prefixCode: Int, reader: BitReader): Int {
        if (prefixCode < 4) return prefixCode + 1
        val extraBits = (prefixCode - 2) ushr 1
        val offset = (2 + (prefixCode and 1)) shl extraBits
        return offset + reader.readBits(extraBits) + 1
    }

    private fun applyInverseSubtractGreen(pixels: IntArray): IntArray {
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            val outR = (r + g) and 0xFF
            val outB = (b + g) and 0xFF
            pixels[i] = (a shl 24) or (outR shl 16) or (g shl 8) or outB
        }
        return pixels
    }

    private fun applyInverseCrossColor(
        pixels: IntArray,
        width: Int,
        height: Int,
        sizeBits: Int,
        subWidth: Int,
        colorData: IntArray
    ): IntArray {
        for (y in 0 until height) {
            val tileRow = (y ushr sizeBits) * subWidth
            val rowOff = y * width
            for (x in 0 until width) {
                val ctm = colorData[tileRow + (x ushr sizeBits)]
                val greenToRed = (ctm and 0xFF).toByte().toInt()
                val greenToBlue = ((ctm ushr 8) and 0xFF).toByte().toInt()
                val redToBlue = ((ctm ushr 16) and 0xFF).toByte().toInt()

                val p = pixels[rowOff + x]
                val a = (p ushr 24) and 0xFF
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF

                val gSigned = g.toByte().toInt()
                val outR = (r + ((greenToRed * gSigned) shr 5)) and 0xFF
                val rNewSigned = outR.toByte().toInt()
                val outB = (b + ((greenToBlue * gSigned) shr 5) + ((redToBlue * rNewSigned) shr 5)) and 0xFF
                pixels[rowOff + x] = (a shl 24) or (outR shl 16) or (g shl 8) or outB
            }
        }
        return pixels
    }

    private fun applyInversePredictor(
        pixels: IntArray,
        width: Int,
        height: Int,
        sizeBits: Int,
        subWidth: Int,
        predData: IntArray
    ): IntArray {
        val opaqueBlack = 0xFF000000.toInt()
        pixels[0] = addArgbMod256(pixels[0], opaqueBlack)
        for (x in 1 until width) {
            pixels[x] = addArgbMod256(pixels[x], pixels[x - 1])
        }
        for (y in 1 until height) {
            val rowOff = y * width
            pixels[rowOff] = addArgbMod256(pixels[rowOff], pixels[rowOff - width])
            val tileRow = (y ushr sizeBits) * subWidth
            for (x in 1 until width) {
                val idx = rowOff + x
                val mode = (predData[tileRow + (x ushr sizeBits)] ushr 8) and 0xFF
                val l = pixels[idx - 1]
                val t = pixels[idx - width]
                val tl = pixels[idx - width - 1]
                val tr = pixels[idx - width + 1]
                val pred = when (mode) {
                    0 -> opaqueBlack
                    1 -> l
                    2 -> t
                    3 -> tr
                    4 -> tl
                    5 -> average2(average2(l, tr), t)
                    6 -> average2(l, tl)
                    7 -> average2(l, t)
                    8 -> average2(tl, t)
                    9 -> average2(t, tr)
                    10 -> average2(average2(l, tl), average2(t, tr))
                    11 -> selectPredictor(l, t, tl)
                    12 -> clampAddSubtractFull(l, t, tl)
                    13 -> clampAddSubtractHalf(average2(l, t), tl)
                    else -> throw IllegalArgumentException("Invalid VP8L predictor mode: $mode")
                }
                pixels[idx] = addArgbMod256(pixels[idx], pred)
            }
        }
        return pixels
    }

    private fun applyInverseColorIndexing(
        pixels: IntArray,
        originalWidth: Int,
        height: Int,
        widthBits: Int,
        palette: IntArray
    ): IntArray {
        val paletteSize = palette.size
        val packedWidth = divCeil(originalWidth, 1 shl widthBits)
        val out = IntArray(originalWidth * height)
        if (widthBits == 0) {
            for (y in 0 until height) {
                val srcRow = y * packedWidth
                val dstRow = y * originalWidth
                for (x in 0 until originalWidth) {
                    val idx = (pixels[srcRow + x] ushr 8) and 0xFF
                    out[dstRow + x] = if (idx < paletteSize) palette[idx] else 0
                }
            }
        } else {
            val bitsPerPixel = 8 ushr widthBits
            val mask = (1 shl bitsPerPixel) - 1
            val subMask = (1 shl widthBits) - 1
            for (y in 0 until height) {
                val srcRow = y * packedWidth
                val dstRow = y * originalWidth
                for (x in 0 until originalWidth) {
                    val packedGreen = (pixels[srcRow + (x ushr widthBits)] ushr 8) and 0xFF
                    val subIdx = x and subMask
                    val paletteIdx = (packedGreen ushr (subIdx * bitsPerPixel)) and mask
                    out[dstRow + x] = if (paletteIdx < paletteSize) palette[paletteIdx] else 0
                }
            }
        }
        return out
    }

    private fun unfilterAlphaPlane(alpha: IntArray, width: Int, height: Int, method: Int) {
        if (method == 0) return
        for (y in 0 until height) {
            val rowOff = y * width
            for (x in 0 until width) {
                val idx = rowOff + x
                val left = if (x > 0) alpha[idx - 1] else 0
                val top = if (y > 0) alpha[idx - width] else 0
                val topLeft = if (x > 0 && y > 0) alpha[idx - width - 1] else 0
                val pred = when (method) {
                    1 -> when {
                        x > 0 -> left
                        y > 0 -> top
                        else -> 0
                    }
                    2 -> when {
                        y > 0 -> top
                        x > 0 -> left
                        else -> 0
                    }
                    3 -> when {
                        x == 0 && y == 0 -> 0
                        y == 0 -> left
                        x == 0 -> top
                        else -> clampByte(left + top - topLeft)
                    }
                    else -> throw IllegalArgumentException("Invalid WebP ALPH filter method: $method")
                }
                alpha[idx] = (alpha[idx] + pred) and 0xFF
            }
        }
    }

    private fun addArgbMod256(a: Int, b: Int): Int {
        val aa = (((a ushr 24) and 0xFF) + ((b ushr 24) and 0xFF)) and 0xFF
        val ar = (((a ushr 16) and 0xFF) + ((b ushr 16) and 0xFF)) and 0xFF
        val ag = (((a ushr 8) and 0xFF) + ((b ushr 8) and 0xFF)) and 0xFF
        val ab = ((a and 0xFF) + (b and 0xFF)) and 0xFF
        return (aa shl 24) or (ar shl 16) or (ag shl 8) or ab
    }

    private fun average2(a: Int, b: Int): Int {
        val aa = (((a ushr 24) and 0xFF) + ((b ushr 24) and 0xFF)) ushr 1
        val ar = (((a ushr 16) and 0xFF) + ((b ushr 16) and 0xFF)) ushr 1
        val ag = (((a ushr 8) and 0xFF) + ((b ushr 8) and 0xFF)) ushr 1
        val ab = ((a and 0xFF) + (b and 0xFF)) ushr 1
        return (aa shl 24) or (ar shl 16) or (ag shl 8) or ab
    }

    private fun selectPredictor(l: Int, t: Int, tl: Int): Int {
        val pa = abs(((t ushr 24) and 0xFF) - ((tl ushr 24) and 0xFF)) +
            abs(((t ushr 16) and 0xFF) - ((tl ushr 16) and 0xFF)) +
            abs(((t ushr 8) and 0xFF) - ((tl ushr 8) and 0xFF)) +
            abs((t and 0xFF) - (tl and 0xFF))
        val pb = abs(((l ushr 24) and 0xFF) - ((tl ushr 24) and 0xFF)) +
            abs(((l ushr 16) and 0xFF) - ((tl ushr 16) and 0xFF)) +
            abs(((l ushr 8) and 0xFF) - ((tl ushr 8) and 0xFF)) +
            abs((l and 0xFF) - (tl and 0xFF))
        return if (pa < pb) l else t
    }

    private fun clampAddSubtractFull(l: Int, t: Int, tl: Int): Int {
        val aa = clampByte(((l ushr 24) and 0xFF) + ((t ushr 24) and 0xFF) - ((tl ushr 24) and 0xFF))
        val ar = clampByte(((l ushr 16) and 0xFF) + ((t ushr 16) and 0xFF) - ((tl ushr 16) and 0xFF))
        val ag = clampByte(((l ushr 8) and 0xFF) + ((t ushr 8) and 0xFF) - ((tl ushr 8) and 0xFF))
        val ab = clampByte((l and 0xFF) + (t and 0xFF) - (tl and 0xFF))
        return (aa shl 24) or (ar shl 16) or (ag shl 8) or ab
    }

    private fun clampAddSubtractHalf(ave: Int, tl: Int): Int {
        val va = (ave ushr 24) and 0xFF
        val vr = (ave ushr 16) and 0xFF
        val vg = (ave ushr 8) and 0xFF
        val vb = ave and 0xFF
        val aa = clampByte(va + (va - ((tl ushr 24) and 0xFF)) / 2)
        val ar = clampByte(vr + (vr - ((tl ushr 16) and 0xFF)) / 2)
        val ag = clampByte(vg + (vg - ((tl ushr 8) and 0xFF)) / 2)
        val ab = clampByte(vb + (vb - (tl and 0xFF)) / 2)
        return (aa shl 24) or (ar shl 16) or (ag shl 8) or ab
    }

    private fun clampByte(v: Int): Int = if (v < 0) 0 else if (v > 255) 255 else v

    private fun divCeil(a: Int, b: Int): Int = (a + b - 1) / b

    private fun isValidRiffWebpHeader(bytes: ByteArray): Boolean =
        bytes.size >= 12 &&
            matchesFourCc(bytes, 0, 'R', 'I', 'F', 'F') &&
            matchesFourCc(bytes, 8, 'W', 'E', 'B', 'P')

    private fun resolveRiffEnd(bytes: ByteArray): Int {
        val riffPayloadSize = readUInt32LE(bytes, 4)
        val declaredEnd = if (riffPayloadSize >= 4) 8L + riffPayloadSize else bytes.size.toLong()
        return minOf(bytes.size.toLong(), declaredEnd).toInt()
    }

    private fun matchesFourCc(bytes: ByteArray, offset: Int, c0: Char, c1: Char, c2: Char, c3: Char): Boolean =
        offset + 4 <= bytes.size &&
            bytes[offset] == c0.code.toByte() &&
            bytes[offset + 1] == c1.code.toByte() &&
            bytes[offset + 2] == c2.code.toByte() &&
            bytes[offset + 3] == c3.code.toByte()

    private fun readUInt16LE(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8)

    private fun readUInt24LE(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16)

    private fun readInt32LE(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)

    private fun readUInt32LE(b: ByteArray, offset: Int): Long =
        readInt32LE(b, offset).toLong() and 0xFFFFFFFFL

    private sealed interface Vp8lTransform {
        data class Predictor(
            val width: Int,
            val sizeBits: Int,
            val subWidth: Int,
            val predData: IntArray
        ) : Vp8lTransform

        data class CrossColor(
            val width: Int,
            val sizeBits: Int,
            val subWidth: Int,
            val colorData: IntArray
        ) : Vp8lTransform

        data object SubtractGreen : Vp8lTransform

        data class ColorIndexing(
            val originalWidth: Int,
            val widthBits: Int,
            val palette: IntArray
        ) : Vp8lTransform
    }

    private class HuffmanGroup(
        val green: HuffmanTree,
        val red: HuffmanTree,
        val blue: HuffmanTree,
        val alpha: HuffmanTree,
        val distance: HuffmanTree
    )

    private class BitReader(
        private val data: ByteArray,
        startOffset: Int,
        private val endOffset: Int
    ) {
        private var bytePos: Int = startOffset
        private var bitBuf: Int = 0
        private var bitCount: Int = 0

        fun readBits(count: Int): Int {
            if (count == 0) return 0
            while (bitCount < count) {
                if (bytePos >= endOffset) {
                    throw IllegalArgumentException("Unexpected end of VP8L bitstream")
                }
                bitBuf = bitBuf or ((data[bytePos++].toInt() and 0xFF) shl bitCount)
                bitCount += 8
            }
            val value = bitBuf and ((1 shl count) - 1)
            bitBuf = bitBuf ushr count
            bitCount -= count
            return value
        }
    }

    private class HuffmanTree private constructor(
        private val left: IntArray,
        private val right: IntArray,
        private val symbol: IntArray,
        private val singleSymbol: Int
    ) {
        fun decodeSymbol(reader: BitReader): Int {
            if (singleSymbol == -2) {
                throw IllegalArgumentException("Cannot decode symbol from empty VP8L Huffman tree")
            }
            if (singleSymbol >= 0) {
                return singleSymbol
            }
            var node = 0
            while (true) {
                val sym = symbol[node]
                if (sym >= 0) return sym
                val bit = reader.readBits(1)
                node = if (bit == 0) left[node] else right[node]
                if (node <= 0) {
                    throw IllegalArgumentException("Invalid VP8L Huffman prefix code")
                }
            }
        }

        companion object {
            private val EMPTY = HuffmanTree(IntArray(0), IntArray(0), IntArray(0), singleSymbol = -2)

            fun singleSymbol(sym: Int): HuffmanTree =
                HuffmanTree(IntArray(0), IntArray(0), IntArray(0), singleSymbol = sym)

            fun twoSymbols(sym0: Int, sym1: Int): HuffmanTree =
                HuffmanTree(
                    left = intArrayOf(1, -1, -1),
                    right = intArrayOf(2, -1, -1),
                    symbol = intArrayOf(-1, sym0, sym1),
                    singleSymbol = -1
                )

            fun build(codeLengths: IntArray): HuffmanTree {
                var maxBits = 0
                var nonZeroCount = 0
                var lastNonZeroSym = -1

                for (sym in codeLengths.indices) {
                    val len = codeLengths[sym]
                    if (len > 0) {
                        if (len > MAX_ALLOWED_CODE_LENGTH) {
                            throw IllegalArgumentException("VP8L Huffman code length $len exceeds $MAX_ALLOWED_CODE_LENGTH")
                        }
                        if (len > maxBits) maxBits = len
                        nonZeroCount++
                        lastNonZeroSym = sym
                    }
                }

                if (nonZeroCount == 0) return EMPTY
                if (nonZeroCount == 1) return singleSymbol(lastNonZeroSym)

                val blCount = IntArray(maxBits + 1)
                for (len in codeLengths) {
                    if (len > 0) blCount[len]++
                }
                val nextCode = IntArray(maxBits + 1)
                var code = 0
                for (bits in 1..maxBits) {
                    code = (code + blCount[bits - 1]) shl 1
                    nextCode[bits] = code
                }

                var capacity = (nonZeroCount * 4 + 4).coerceAtLeast(16)
                var left = IntArray(capacity) { -1 }
                var right = IntArray(capacity) { -1 }
                var symbol = IntArray(capacity) { -1 }
                var nodeCount = 1

                for (sym in codeLengths.indices) {
                    val len = codeLengths[sym]
                    if (len > 0) {
                        val c = nextCode[len]++
                        var cur = 0
                        for (bitIdx in len - 1 downTo 0) {
                            val bit = (c ushr bitIdx) and 1
                            var next = if (bit == 0) left[cur] else right[cur]
                            if (next < 0) {
                                if (nodeCount >= capacity) {
                                    val newCap = capacity * 2
                                    left = left.copyOf(newCap).also { it.fill(-1, capacity, newCap) }
                                    right = right.copyOf(newCap).also { it.fill(-1, capacity, newCap) }
                                    symbol = symbol.copyOf(newCap).also { it.fill(-1, capacity, newCap) }
                                    capacity = newCap
                                }
                                next = nodeCount++
                                if (bit == 0) left[cur] = next else right[cur] = next
                            }
                            cur = next
                        }
                        symbol[cur] = sym
                    }
                }

                return HuffmanTree(left, right, symbol, singleSymbol = -1)
            }
        }
    }
}
