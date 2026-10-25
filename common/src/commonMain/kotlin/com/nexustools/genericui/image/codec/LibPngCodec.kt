package com.nexustools.genericui.image.codec

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageMetadata
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.RgbaColor

/**
 * Pure-Kotlin Multiplatform PNG (`IHDR`, `PLTE`, `tRNS`, `pHYs`, `acTL`, `IDAT`, `IEND`) decoder and encoder
 * with built-in RFC 1950 `zlib` / RFC 1951 `DEFLATE` decompression, PNG scanline unfiltering (`None`, `Sub`,
 * `Up`, `Average`, `Paeth`), Adam7 de-interlacing, and optional native `libpng` C interop hooks.
 */
object LibPngCodec {
    var nativeLibPngDecodeHook: ((pngBytes: ByteArray) -> ImageBuffer?)? = null
    var nativeLibPngEncodeHook: ((buffer: ImageBuffer, compressionLevel: Int) -> ByteArray?)? = null

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
    )

    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
    )

    private val DIST_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577
    )

    private val DIST_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
    )

    private val CL_ORDER = intArrayOf(
        16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15
    )

    private val ADAM7_X_ORIGIN = intArrayOf(0, 4, 0, 2, 0, 1, 0)
    private val ADAM7_Y_ORIGIN = intArrayOf(0, 0, 4, 0, 2, 0, 1)
    private val ADAM7_X_STEP = intArrayOf(8, 8, 4, 4, 2, 2, 1)
    private val ADAM7_Y_STEP = intArrayOf(8, 8, 8, 4, 4, 2, 2)

    private val CRC_TABLE = IntArray(256).apply {
        for (n in 0 until 256) {
            var c = n
            for (k in 0 until 8) {
                c = if ((c and 1) != 0) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1)
            }
            this[n] = c
        }
    }

    fun probePngMetadata(bytes: ByteArray): ImageMetadata {
        if (bytes.size < 24) return ImageMetadata(1, 1, ImageFormat.PNG)
        val width = readInt32BE(bytes, 16).coerceAtLeast(1)
        val height = readInt32BE(bytes, 20).coerceAtLeast(1)
        val bitDepth = if (bytes.size > 24) bytes[24].toInt() and 0xFF else 8
        val colorType = if (bytes.size > 25) bytes[25].toInt() and 0xFF else 6
        var hasAlpha = (colorType == 4 || colorType == 6)
        var dpiX = 72f
        var dpiY = 72f
        var isAnimated = false
        var frameCount = 1

        var offset = 8
        while (offset + 8 <= bytes.size) {
            val length = readInt32BE(bytes, offset)
            if (length < 0 || offset + 12 + length > bytes.size) break
            val type0 = bytes[offset + 4].toInt().toChar()
            val type1 = bytes[offset + 5].toInt().toChar()
            val type2 = bytes[offset + 6].toInt().toChar()
            val type3 = bytes[offset + 7].toInt().toChar()
            val dataOffset = offset + 8
            if (type0 == 't' && type1 == 'R' && type2 == 'N' && type3 == 'S') {
                hasAlpha = true
            } else if (type0 == 'p' && type1 == 'H' && type2 == 'Y' && type3 == 's' && length >= 9) {
                val ppuX = readInt32BE(bytes, dataOffset)
                val ppuY = readInt32BE(bytes, dataOffset + 4)
                val unit = bytes[dataOffset + 8].toInt() and 0xFF
                if (unit == 1 && ppuX > 0 && ppuY > 0) {
                    dpiX = ppuX * 0.0254f
                    dpiY = ppuY * 0.0254f
                }
            } else if (type0 == 'a' && type1 == 'c' && type2 == 'T' && type3 == 'L' && length >= 8) {
                val frames = readInt32BE(bytes, dataOffset)
                if (frames > 1) {
                    isAnimated = true
                    frameCount = frames
                }
            } else if (type0 == 'I' && type1 == 'E' && type2 == 'N' && type3 == 'D') {
                break
            }
            offset += 12 + length
        }

        return ImageMetadata(
            width = width,
            height = height,
            format = ImageFormat.PNG,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = ImageColorSpace.SRGB,
            bitDepthPerChannel = bitDepth,
            hasAlpha = hasAlpha,
            isAnimated = isAnimated,
            frameCount = frameCount,
            dpiX = dpiX,
            dpiY = dpiY
        )
    }

    fun decode(bytes: ByteArray): ImageBuffer {
        nativeLibPngDecodeHook?.invoke(bytes)?.let { return it }
        val meta = probePngMetadata(bytes)
        val out = ImageBuffer(meta.width, meta.height, PixelFormat.RGBA_8888, ImageColorSpace.SRGB)
        if (bytes.size < 33) return out

        for (i in 0 until 8) {
            if (bytes[i] != PNG_SIGNATURE[i]) return out
        }

        var width = meta.width
        var height = meta.height
        var bitDepth = 8
        var colorType = 6
        var interlaceMethod = 0
        var palette = ByteArray(0)
        var trns = ByteArray(0)
        val idatStream = ByteOutBuffer(bytes.size)

        var offset = 8
        while (offset + 8 <= bytes.size) {
            val length = readInt32BE(bytes, offset)
            if (length < 0 || offset + 12 + length > bytes.size) break
            val t0 = bytes[offset + 4].toInt().toChar()
            val t1 = bytes[offset + 5].toInt().toChar()
            val t2 = bytes[offset + 6].toInt().toChar()
            val t3 = bytes[offset + 7].toInt().toChar()
            val dataOffset = offset + 8

            if (t0 == 'I' && t1 == 'H' && t2 == 'D' && t3 == 'R' && length >= 13) {
                width = readInt32BE(bytes, dataOffset).coerceAtLeast(1)
                height = readInt32BE(bytes, dataOffset + 4).coerceAtLeast(1)
                bitDepth = bytes[dataOffset + 8].toInt() and 0xFF
                colorType = bytes[dataOffset + 9].toInt() and 0xFF
                interlaceMethod = bytes[dataOffset + 12].toInt() and 0xFF
            } else if (t0 == 'P' && t1 == 'L' && t2 == 'T' && t3 == 'E' && length > 0) {
                palette = bytes.copyOfRange(dataOffset, dataOffset + length)
            } else if (t0 == 't' && t1 == 'R' && t2 == 'N' && t3 == 'S' && length > 0) {
                trns = bytes.copyOfRange(dataOffset, dataOffset + length)
            } else if (t0 == 'I' && t1 == 'D' && t2 == 'A' && t3 == 'T' && length > 0) {
                idatStream.writeBytes(bytes, dataOffset, length)
            } else if (t0 == 'I' && t1 == 'E' && t2 == 'N' && t3 == 'D') {
                break
            }
            offset += 12 + length
        }

        val compressedIdat = idatStream.toByteArray()
        if (compressedIdat.size <= 2) return out

        val decompressed = runCatching { inflateZlib(compressedIdat, width * height * 4 + height * 2) }
            .getOrNull() ?: return out
        if (decompressed.isEmpty()) return out

        val targetBuffer = if (width != out.width || height != out.height) {
            ImageBuffer(width, height, PixelFormat.RGBA_8888, ImageColorSpace.SRGB)
        } else {
            out
        }

        val channels = when (colorType) {
            0 -> 1
            2 -> 3
            3 -> 1
            4 -> 2
            6 -> 4
            else -> 4
        }
        val bitsPerPixel = channels * bitDepth
        val filterBpp = ((bitsPerPixel + 7) / 8).coerceAtLeast(1)

        if (interlaceMethod == 0) {
            decodeSubImage(
                raw = decompressed,
                rawOffset = 0,
                subWidth = width,
                subHeight = height,
                bitsPerPixel = bitsPerPixel,
                filterBpp = filterBpp,
                bitDepth = bitDepth,
                colorType = colorType,
                palette = palette,
                trns = trns,
                dst = targetBuffer,
                xOrigin = 0,
                yOrigin = 0,
                xStep = 1,
                yStep = 1
            )
        } else if (interlaceMethod == 1) {
            var cursor = 0
            for (pass in 0 until 7) {
                val xOrig = ADAM7_X_ORIGIN[pass]
                val yOrig = ADAM7_Y_ORIGIN[pass]
                val xStep = ADAM7_X_STEP[pass]
                val yStep = ADAM7_Y_STEP[pass]
                if (xOrig >= width || yOrig >= height) continue
                val subW = (width - xOrig + xStep - 1) / xStep
                val subH = (height - yOrig + yStep - 1) / yStep
                if (subW <= 0 || subH <= 0) continue
                cursor = decodeSubImage(
                    raw = decompressed,
                    rawOffset = cursor,
                    subWidth = subW,
                    subHeight = subH,
                    bitsPerPixel = bitsPerPixel,
                    filterBpp = filterBpp,
                    bitDepth = bitDepth,
                    colorType = colorType,
                    palette = palette,
                    trns = trns,
                    dst = targetBuffer,
                    xOrigin = xOrig,
                    yOrigin = yOrig,
                    xStep = xStep,
                    yStep = yStep
                )
            }
        }

        return targetBuffer
    }

    fun encode(buffer: ImageBuffer, compressionLevel: Int = 6): ByteArray {
        nativeLibPngEncodeHook?.invoke(buffer, compressionLevel)?.let { return it }

        val w = buffer.width.coerceAtLeast(1)
        val h = buffer.height.coerceAtLeast(1)
        val rgba = buffer.asRgba8888()

        val rowRawBytes = 1 + w * 4
        val rawScanlines = ByteArray(h * rowRawBytes)
        var rawPos = 0
        for (y in 0 until h) {
            rawScanlines[rawPos++] = 0 // Filter type 0 (None)
            for (x in 0 until w) {
                val p = rgba.getPixel(x, y)
                rawScanlines[rawPos++] = p.r.toByte()
                rawScanlines[rawPos++] = p.g.toByte()
                rawScanlines[rawPos++] = p.b.toByte()
                rawScanlines[rawPos++] = p.a.coerceIn(0, 255).toByte()
            }
        }

        val zlibPayload = deflateUncompressedZlib(rawScanlines)
        val out = ByteOutBuffer(64 + zlibPayload.size)

        // 8-byte PNG signature
        out.writeBytes(PNG_SIGNATURE, 0, PNG_SIGNATURE.size)

        // IHDR chunk (13 bytes)
        val ihdr = ByteArray(13)
        writeInt32BE(ihdr, 0, w)
        writeInt32BE(ihdr, 4, h)
        ihdr[8] = 8  // bit depth 8
        ihdr[9] = 6  // color type 6 (RGBA)
        ihdr[10] = 0 // compression method 0
        ihdr[11] = 0 // filter method 0
        ihdr[12] = 0 // interlace method 0
        writeChunk(out, "IHDR", ihdr)

        // IDAT chunk
        writeChunk(out, "IDAT", zlibPayload)

        // IEND chunk
        writeChunk(out, "IEND", ByteArray(0))

        return out.toByteArray()
    }

    private fun decodeSubImage(
        raw: ByteArray,
        rawOffset: Int,
        subWidth: Int,
        subHeight: Int,
        bitsPerPixel: Int,
        filterBpp: Int,
        bitDepth: Int,
        colorType: Int,
        palette: ByteArray,
        trns: ByteArray,
        dst: ImageBuffer,
        xOrigin: Int,
        yOrigin: Int,
        xStep: Int,
        yStep: Int
    ): Int {
        val rowByteCount = (subWidth * bitsPerPixel + 7) / 8
        var prevRow = ByteArray(rowByteCount)
        var currRow = ByteArray(rowByteCount)
        var pos = rawOffset

        for (ry in 0 until subHeight) {
            if (pos >= raw.size) break
            val filterType = raw[pos++].toInt() and 0xFF
            for (i in 0 until rowByteCount) {
                val rawByte = if (pos < raw.size) (raw[pos++].toInt() and 0xFF) else 0
                val left = if (i >= filterBpp) (currRow[i - filterBpp].toInt() and 0xFF) else 0
                val up = prevRow[i].toInt() and 0xFF
                val upLeft = if (i >= filterBpp) (prevRow[i - filterBpp].toInt() and 0xFF) else 0
                val recon = when (filterType) {
                    0 -> rawByte
                    1 -> (rawByte + left) and 0xFF
                    2 -> (rawByte + up) and 0xFF
                    3 -> (rawByte + ((left + up) ushr 1)) and 0xFF
                    4 -> (rawByte + paethPredictor(left, up, upLeft)) and 0xFF
                    else -> rawByte
                }
                currRow[i] = recon.toByte()
            }

            val dstY = yOrigin + ry * yStep
            if (dstY < dst.height) {
                unpackScanlineToBuffer(
                    scanline = currRow,
                    subWidth = subWidth,
                    bitDepth = bitDepth,
                    colorType = colorType,
                    palette = palette,
                    trns = trns,
                    dst = dst,
                    dstY = dstY,
                    xOrigin = xOrigin,
                    xStep = xStep
                )
            }

            val swap = prevRow
            prevRow = currRow
            currRow = swap
        }
        return pos
    }

    private fun unpackScanlineToBuffer(
        scanline: ByteArray,
        subWidth: Int,
        bitDepth: Int,
        colorType: Int,
        palette: ByteArray,
        trns: ByteArray,
        dst: ImageBuffer,
        dstY: Int,
        xOrigin: Int,
        xStep: Int
    ) {
        val dstBytes = dst.bytes
        val dstWidth = dst.width
        for (rx in 0 until subWidth) {
            val dstX = xOrigin + rx * xStep
            if (dstX >= dstWidth) break
            var r = 0
            var g = 0
            var b = 0
            var a = 255

            when (colorType) {
                6 -> { // RGBA
                    if (bitDepth == 16) {
                        val base = rx * 8
                        if (base + 6 < scanline.size) {
                            r = scanline[base].toInt() and 0xFF
                            g = scanline[base + 2].toInt() and 0xFF
                            b = scanline[base + 4].toInt() and 0xFF
                            a = scanline[base + 6].toInt() and 0xFF
                        }
                    } else {
                        val base = rx * 4
                        if (base + 3 < scanline.size) {
                            r = scanline[base].toInt() and 0xFF
                            g = scanline[base + 1].toInt() and 0xFF
                            b = scanline[base + 2].toInt() and 0xFF
                            a = scanline[base + 3].toInt() and 0xFF
                        }
                    }
                }
                2 -> { // RGB
                    if (bitDepth == 16) {
                        val base = rx * 6
                        if (base + 4 < scanline.size) {
                            r = scanline[base].toInt() and 0xFF
                            g = scanline[base + 2].toInt() and 0xFF
                            b = scanline[base + 4].toInt() and 0xFF
                        }
                    } else {
                        val base = rx * 3
                        if (base + 2 < scanline.size) {
                            r = scanline[base].toInt() and 0xFF
                            g = scanline[base + 1].toInt() and 0xFF
                            b = scanline[base + 2].toInt() and 0xFF
                            if (trns.size >= 6) {
                                val tr = trns[1].toInt() and 0xFF
                                val tg = trns[3].toInt() and 0xFF
                                val tb = trns[5].toInt() and 0xFF
                                if (r == tr && g == tg && b == tb) a = 0
                            }
                        }
                    }
                }
                3 -> { // Indexed Palette
                    val index = readSubByteSample(scanline, rx, bitDepth)
                    val palOffset = index * 3
                    if (palOffset + 2 < palette.size) {
                        r = palette[palOffset].toInt() and 0xFF
                        g = palette[palOffset + 1].toInt() and 0xFF
                        b = palette[palOffset + 2].toInt() and 0xFF
                    }
                    if (index < trns.size) {
                        a = trns[index].toInt() and 0xFF
                    }
                }
                0 -> { // Grayscale
                    val sample = if (bitDepth == 16) {
                        val base = rx * 2
                        if (base < scanline.size) (scanline[base].toInt() and 0xFF) else 0
                    } else {
                        val rawSample = readSubByteSample(scanline, rx, bitDepth)
                        scaleBitSampleTo255(rawSample, bitDepth)
                    }
                    r = sample
                    g = sample
                    b = sample
                    if (trns.size >= 2) {
                        val key = scaleBitSampleTo255(trns[1].toInt() and 0xFF, bitDepth)
                        if (sample == key) a = 0
                    }
                }
                4 -> { // Grayscale + Alpha
                    if (bitDepth == 16) {
                        val base = rx * 4
                        if (base + 2 < scanline.size) {
                            val lum = scanline[base].toInt() and 0xFF
                            r = lum; g = lum; b = lum
                            a = scanline[base + 2].toInt() and 0xFF
                        }
                    } else {
                        val base = rx * 2
                        if (base + 1 < scanline.size) {
                            val lum = scanline[base].toInt() and 0xFF
                            r = lum; g = lum; b = lum
                            a = scanline[base + 1].toInt() and 0xFF
                        }
                    }
                }
            }

            if (dst.pixelFormat == PixelFormat.RGBA_8888) {
                val pIdx = (dstY * dstWidth + dstX) * 4
                if (pIdx + 3 < dstBytes.size) {
                    dstBytes[pIdx] = r.toByte()
                    dstBytes[pIdx + 1] = g.toByte()
                    dstBytes[pIdx + 2] = b.toByte()
                    dstBytes[pIdx + 3] = a.toByte()
                }
            } else {
                dst.setPixel(dstX, dstY, RgbaColor(r, g, b, a / 255f))
            }
        }
    }

    private fun readSubByteSample(scanline: ByteArray, sampleIndex: Int, bitDepth: Int): Int {
        return when (bitDepth) {
            8 -> if (sampleIndex < scanline.size) (scanline[sampleIndex].toInt() and 0xFF) else 0
            4 -> {
                val byteIdx = sampleIndex ushr 1
                if (byteIdx >= scanline.size) 0
                else {
                    val b = scanline[byteIdx].toInt() and 0xFF
                    if ((sampleIndex and 1) == 0) (b ushr 4) and 0x0F else b and 0x0F
                }
            }
            2 -> {
                val byteIdx = sampleIndex ushr 2
                if (byteIdx >= scanline.size) 0
                else {
                    val shift = (3 - (sampleIndex and 3)) * 2
                    ((scanline[byteIdx].toInt() and 0xFF) ushr shift) and 0x03
                }
            }
            1 -> {
                val byteIdx = sampleIndex ushr 3
                if (byteIdx >= scanline.size) 0
                else {
                    val shift = 7 - (sampleIndex and 7)
                    ((scanline[byteIdx].toInt() and 0xFF) ushr shift) and 0x01
                }
            }
            else -> 0
        }
    }

    private fun scaleBitSampleTo255(value: Int, bitDepth: Int): Int = when (bitDepth) {
        1 -> if (value != 0) 255 else 0
        2 -> (value and 0x03) * 85
        4 -> (value and 0x0F) * 17
        else -> value and 0xFF
    }

    private fun paethPredictor(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }

    private fun inflateZlib(zlibBytes: ByteArray, initialCapacity: Int): ByteArray {
        if (zlibBytes.size < 2) return ByteArray(0)
        val flg = zlibBytes[1].toInt() and 0xFF
        val fdict = (flg and 0x20) != 0
        val startByte = if (fdict) 6 else 2
        if (startByte >= zlibBytes.size) return ByteArray(0)

        val reader = BitReader(zlibBytes, startByte)
        val out = ByteOutBuffer(initialCapacity.coerceAtLeast(256))

        var bfinal = 0
        while (bfinal == 0 && !reader.isExhausted()) {
            bfinal = reader.readBits(1)
            when (val btype = reader.readBits(2)) {
                0 -> { // Uncompressed block
                    reader.alignToByte()
                    val len = reader.readBits(16)
                    reader.readBits(16) // nlen
                    for (i in 0 until len) {
                        if (reader.isExhausted()) break
                        out.writeByte(reader.readAlignedByte())
                    }
                }
                1, 2 -> { // Fixed or Dynamic Huffman block
                    val litLenTree: HuffmanTree
                    val distTree: HuffmanTree
                    if (btype == 1) {
                        litLenTree = FIXED_LIT_LEN_TREE
                        distTree = FIXED_DIST_TREE
                    } else {
                        val hlit = reader.readBits(5) + 257
                        val hdist = reader.readBits(5) + 1
                        val hclen = reader.readBits(4) + 4
                        val clLengths = IntArray(19)
                        for (i in 0 until hclen) {
                            clLengths[CL_ORDER[i]] = reader.readBits(3)
                        }
                        val clTree = HuffmanTree.build(clLengths)
                        val totalCodes = hlit + hdist
                        val codeLengths = IntArray(totalCodes)
                        var idx = 0
                        while (idx < totalCodes && !reader.isExhausted()) {
                            val sym = clTree.decodeSymbol(reader)
                            if (sym < 0) break
                            when {
                                sym < 16 -> {
                                    codeLengths[idx++] = sym
                                }
                                sym == 16 -> {
                                    val repeat = reader.readBits(2) + 3
                                    val prev = if (idx > 0) codeLengths[idx - 1] else 0
                                    for (r in 0 until repeat) {
                                        if (idx < totalCodes) codeLengths[idx++] = prev
                                    }
                                }
                                sym == 17 -> {
                                    val repeat = reader.readBits(3) + 3
                                    for (r in 0 until repeat) {
                                        if (idx < totalCodes) codeLengths[idx++] = 0
                                    }
                                }
                                sym == 18 -> {
                                    val repeat = reader.readBits(7) + 11
                                    for (r in 0 until repeat) {
                                        if (idx < totalCodes) codeLengths[idx++] = 0
                                    }
                                }
                            }
                        }
                        litLenTree = HuffmanTree.build(codeLengths.copyOfRange(0, hlit))
                        distTree = HuffmanTree.build(codeLengths.copyOfRange(hlit, totalCodes))
                    }

                    while (!reader.isExhausted()) {
                        val sym = litLenTree.decodeSymbol(reader)
                        if (sym < 0 || sym == 256) break
                        if (sym < 256) {
                            out.writeByte(sym)
                        } else {
                            val lenIdx = sym - 257
                            if (lenIdx !in LENGTH_BASE.indices) break
                            val length = LENGTH_BASE[lenIdx] + reader.readBits(LENGTH_EXTRA[lenIdx])
                            val distSym = distTree.decodeSymbol(reader)
                            if (distSym !in DIST_BASE.indices) break
                            val distance = DIST_BASE[distSym] + reader.readBits(DIST_EXTRA[distSym])
                            out.copyFromHistory(distance, length)
                        }
                    }
                }
                else -> break
            }
        }
        return out.toByteArray()
    }

    private fun deflateUncompressedZlib(raw: ByteArray): ByteArray {
        val maxBlock = 65535
        val numBlocks = ((raw.size + maxBlock - 1) / maxBlock).coerceAtLeast(1)
        val out = ByteOutBuffer(2 + numBlocks * 5 + raw.size + 4)

        // Zlib header (CMF = 0x78, FLG = 0x01)
        out.writeByte(0x78)
        out.writeByte(0x01)

        var offset = 0
        for (blockIdx in 0 until numBlocks) {
            val isLast = blockIdx == numBlocks - 1
            val chunkLen = (raw.size - offset).coerceIn(0, maxBlock)
            val nlen = chunkLen.inv() and 0xFFFF
            out.writeByte(if (isLast) 0x01 else 0x00) // BFINAL + BTYPE=00
            out.writeByte(chunkLen and 0xFF)
            out.writeByte((chunkLen ushr 8) and 0xFF)
            out.writeByte(nlen and 0xFF)
            out.writeByte((nlen ushr 8) and 0xFF)
            if (chunkLen > 0) {
                out.writeBytes(raw, offset, chunkLen)
                offset += chunkLen
            }
        }

        // Adler-32 checksum (big-endian)
        var s1 = 1
        var s2 = 0
        for (b in raw) {
            s1 = (s1 + (b.toInt() and 0xFF)) % 65521
            s2 = (s2 + s1) % 65521
        }
        val adler = (s2 shl 16) or s1
        out.writeInt32BE(adler)
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteOutBuffer, type: String, data: ByteArray) {
        out.writeInt32BE(data.size)
        val typeBytes = type.encodeToByteArray()
        out.writeBytes(typeBytes, 0, 4)
        if (data.isNotEmpty()) {
            out.writeBytes(data, 0, data.size)
        }
        var crc = -1
        for (i in 0 until 4) {
            val idx = (crc xor (typeBytes[i].toInt() and 0xFF)) and 0xFF
            crc = (crc ushr 8) xor CRC_TABLE[idx]
        }
        for (i in data.indices) {
            val idx = (crc xor (data[i].toInt() and 0xFF)) and 0xFF
            crc = (crc ushr 8) xor CRC_TABLE[idx]
        }
        out.writeInt32BE(crc.inv())
    }

    private val FIXED_LIT_LEN_TREE: HuffmanTree by lazy {
        val lengths = IntArray(288)
        for (i in 0..143) lengths[i] = 8
        for (i in 144..255) lengths[i] = 9
        for (i in 256..279) lengths[i] = 7
        for (i in 280..287) lengths[i] = 8
        HuffmanTree.build(lengths)
    }

    private val FIXED_DIST_TREE: HuffmanTree by lazy {
        val lengths = IntArray(32) { 5 }
        HuffmanTree.build(lengths)
    }

    private class BitReader(private val data: ByteArray, startOffset: Int) {
        private var bytePos: Int = startOffset
        private var bitBuf: Int = 0
        private var bitCount: Int = 0

        fun isExhausted(): Boolean = bytePos >= data.size && bitCount == 0

        fun readBits(count: Int): Int {
            if (count == 0) return 0
            while (bitCount < count) {
                val nextByte = if (bytePos < data.size) (data[bytePos++].toInt() and 0xFF) else 0
                bitBuf = bitBuf or (nextByte shl bitCount)
                bitCount += 8
            }
            val mask = (1 shl count) - 1
            val value = bitBuf and mask
            bitBuf = bitBuf ushr count
            bitCount -= count
            return value
        }

        fun alignToByte() {
            bitBuf = 0
            bitCount = 0
        }

        fun readAlignedByte(): Int {
            return if (bytePos < data.size) (data[bytePos++].toInt() and 0xFF) else 0
        }
    }

    private class HuffmanTree(
        private val left: IntArray,
        private val right: IntArray,
        private val symbol: IntArray
    ) {
        fun decodeSymbol(reader: BitReader): Int {
            var node = 0
            while (true) {
                val sym = symbol[node]
                if (sym >= 0) return sym
                val bit = reader.readBits(1)
                node = if (bit == 0) left[node] else right[node]
                if (node <= 0) return -1
            }
        }

        companion object {
            fun build(codeLengths: IntArray): HuffmanTree {
                var maxBits = 0
                for (len in codeLengths) {
                    if (len > maxBits) maxBits = len
                }
                if (maxBits == 0) {
                    return HuffmanTree(IntArray(1), IntArray(1), intArrayOf(0))
                }
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

                val maxNodes = (codeLengths.size * maxBits + 2).coerceAtLeast(4)
                val left = IntArray(maxNodes) { -1 }
                val right = IntArray(maxNodes) { -1 }
                val symbol = IntArray(maxNodes) { -1 }
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
                                next = nodeCount++
                                if (bit == 0) left[cur] = next else right[cur] = next
                            }
                            cur = next
                        }
                        symbol[cur] = sym
                    }
                }
                return HuffmanTree(left, right, symbol)
            }
        }
    }

    private class ByteOutBuffer(initialCapacity: Int) {
        private var buf = ByteArray(initialCapacity.coerceAtLeast(64))
        var size: Int = 0
            private set

        private fun ensureCapacity(extra: Int) {
            val required = size + extra
            if (required > buf.size) {
                var newCap = buf.size * 2
                if (newCap < required) newCap = required
                buf = buf.copyOf(newCap)
            }
        }

        fun writeByte(b: Int) {
            ensureCapacity(1)
            buf[size++] = (b and 0xFF).toByte()
        }

        fun writeBytes(src: ByteArray, offset: Int, length: Int) {
            if (length <= 0) return
            ensureCapacity(length)
            src.copyInto(buf, size, offset, offset + length)
            size += length
        }

        fun writeInt32BE(v: Int) {
            ensureCapacity(4)
            buf[size++] = ((v ushr 24) and 0xFF).toByte()
            buf[size++] = ((v ushr 16) and 0xFF).toByte()
            buf[size++] = ((v ushr 8) and 0xFF).toByte()
            buf[size++] = (v and 0xFF).toByte()
        }

        fun copyFromHistory(distance: Int, length: Int) {
            if (distance <= 0 || distance > size || length <= 0) {
                for (i in 0 until length) writeByte(0)
                return
            }
            ensureCapacity(length)
            var srcIdx = size - distance
            for (i in 0 until length) {
                buf[size++] = buf[srcIdx++]
            }
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }

    private fun readInt32BE(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)

    private fun writeInt32BE(b: ByteArray, offset: Int, value: Int) {
        b[offset] = ((value ushr 24) and 0xFF).toByte()
        b[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        b[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        b[offset + 3] = (value and 0xFF).toByte()
    }
}
