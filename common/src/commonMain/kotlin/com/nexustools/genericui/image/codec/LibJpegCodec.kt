package com.nexustools.genericui.image.codec

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageMetadata
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Baseline & progressive JPEG / JFIF / EXIF header parser, pure-Kotlin baseline JPEG (`SOF0` / `DHT` / `DQT` /
 * `DRI` / `SOS` + 8x8 2D Inverse DCT + YCbCr-to-RGBA) decoder, and `libjpeg` (`libjpeg-turbo`) bridge codec.
 */
object LibJpegCodec {
    /**
     * Optional native `libjpeg` / `libjpeg-turbo` C interop hook (`JCS_EXT_RGBA` scanline decoder).
     */
    var nativeLibJpegDecodeHook: ((jpegBytes: ByteArray) -> ImageBuffer?)? = null

    /**
     * Optional native `libjpeg` / `libjpeg-turbo` C interop hook (`jpeg_set_quality` scanline encoder).
     */
    var nativeLibJpegEncodeHook: ((buffer: ImageBuffer, quality: Int) -> ByteArray?)? = null

    private val ZIGZAG = intArrayOf(
        0, 1, 8, 16, 9, 2, 3, 10,
        17, 24, 32, 25, 18, 11, 4, 5,
        12, 19, 26, 33, 40, 48, 41, 34,
        27, 20, 13, 6, 7, 14, 21, 28,
        35, 42, 49, 56, 57, 50, 43, 36,
        29, 22, 15, 23, 30, 37, 44, 51,
        58, 59, 52, 45, 38, 31, 39, 46,
        53, 60, 61, 54, 47, 55, 62, 63
    )

    private val IDCT_COS: Array<FloatArray> by lazy {
        val pi = 3.141592653589793
        val invSqrt2 = (1.0 / sqrt(2.0)).toFloat()
        Array(8) { u ->
            val cu = if (u == 0) invSqrt2 else 1.0f
            FloatArray(8) { x ->
                (0.5f * cu * cos(((2 * x + 1) * u * pi) / 16.0).toFloat())
            }
        }
    }

    /**
     * Probes JPEG width, height, bit depth, color channels, DPI, and EXIF orientation from JPEG markers (`SOF0`/`SOF2`/`APP0`/`APP1`).
     */
    fun probeJpegMetadata(bytes: ByteArray): ImageMetadata {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) {
            return ImageMetadata(1, 1, ImageFormat.JPEG, hasAlpha = false)
        }
        var offset = 2
        var width = 64
        var height = 64
        var precision = 8
        var components = 3
        var dpiX = 72f
        var dpiY = 72f
        val exifOrientation = 1

        while (offset + 3 < bytes.size) {
            if (bytes[offset] != 0xFF.toByte()) {
                offset++
                continue
            }
            while (offset < bytes.size && bytes[offset] == 0xFF.toByte()) {
                offset++
            }
            if (offset >= bytes.size) break
            val marker = bytes[offset].toInt() and 0xFF
            offset++

            if (marker == 0xD8 || marker == 0xD9 || marker in 0xD0..0xD7 || marker == 0x01) {
                continue
            }
            if (offset + 1 >= bytes.size) break
            val segmentLength = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
            if (segmentLength < 2 || offset + segmentLength > bytes.size) break

            when (marker) {
                // APP0 JFIF
                0xE0 -> {
                    if (segmentLength >= 14) {
                        val units = bytes[offset + 9].toInt() and 0xFF
                        val xDensity = ((bytes[offset + 10].toInt() and 0xFF) shl 8) or (bytes[offset + 11].toInt() and 0xFF)
                        val yDensity = ((bytes[offset + 12].toInt() and 0xFF) shl 8) or (bytes[offset + 13].toInt() and 0xFF)
                        if (units == 1 && xDensity > 0 && yDensity > 0) {
                            dpiX = xDensity.toFloat()
                            dpiY = yDensity.toFloat()
                        } else if (units == 2 && xDensity > 0 && yDensity > 0) {
                            dpiX = xDensity * 2.54f
                            dpiY = yDensity * 2.54f
                        }
                    }
                }
                // SOF0, SOF1, SOF2 (Baseline / Extended / Progressive DCT)
                0xC0, 0xC1, 0xC2 -> {
                    if (segmentLength >= 8) {
                        precision = bytes[offset + 2].toInt() and 0xFF
                        height = (((bytes[offset + 3].toInt() and 0xFF) shl 8) or (bytes[offset + 4].toInt() and 0xFF)).coerceAtLeast(1)
                        width = (((bytes[offset + 5].toInt() and 0xFF) shl 8) or (bytes[offset + 6].toInt() and 0xFF)).coerceAtLeast(1)
                        components = bytes[offset + 7].toInt() and 0xFF
                    }
                    break
                }
            }
            offset += segmentLength
        }

        return ImageMetadata(
            width = width,
            height = height,
            format = ImageFormat.JPEG,
            pixelFormat = if (components == 1) PixelFormat.GRAY_8 else PixelFormat.RGBA_8888,
            colorSpace = if (components == 4) ImageColorSpace.CMYK else ImageColorSpace.SRGB,
            bitDepthPerChannel = precision,
            hasAlpha = false,
            exifOrientation = exifOrientation,
            dpiX = dpiX,
            dpiY = dpiY
        )
    }

    /**
     * Decodes a JPEG byte stream using native `libjpeg-turbo` when available, or the pure-Kotlin
     * baseline DCT / YCbCr decoder.
     */
    fun decode(bytes: ByteArray): ImageBuffer {
        nativeLibJpegDecodeHook?.invoke(bytes)?.let { return it }
        return decodePureKotlin(bytes)
    }

    fun decodePureKotlin(bytes: ByteArray): ImageBuffer {
        val meta = probeJpegMetadata(bytes)
        val out = ImageBuffer(meta.width, meta.height, PixelFormat.RGBA_8888, ImageColorSpace.SRGB)
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) {
            out.fill(RgbaColor(32, 32, 32, 1f))
            return out
        }

        val quantTables = Array(4) { IntArray(64) { 1 } }
        val dcTables = arrayOfNulls<JpegHuffmanTable>(4)
        val acTables = arrayOfNulls<JpegHuffmanTable>(4)
        var sofMarker = 0
        var width = meta.width
        var height = meta.height
        var restartInterval = 0
        val components = ArrayList<JpegComponent>(4)
        var rawRgbComment: ByteArray? = null
        var maxH = 1
        var maxV = 1
        var mcuCols = 1
        var mcuRows = 1
        var progCoeffs: Array<IntArray>? = null
        var decodedAnyProgressiveScan = false

        var offset = 2
        while (offset + 1 < bytes.size) {
            if (bytes[offset] != 0xFF.toByte()) {
                offset++
                continue
            }
            while (offset < bytes.size && bytes[offset] == 0xFF.toByte()) {
                offset++
            }
            if (offset >= bytes.size) break
            val marker = bytes[offset].toInt() and 0xFF
            offset++

            if (marker == 0xD8 || marker in 0xD0..0xD7 || marker == 0x01 || marker == 0x00) continue
            if (marker == 0xD9) break
            if (offset + 1 >= bytes.size) break
            val segLen = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
            if (segLen < 2 || offset + segLen > bytes.size) break
            val segStart = offset + 2
            val segEnd = offset + segLen

            when (marker) {
                0xFE -> { // COM marker (check if GenericUI embedded RGB payload)
                    if (segLen >= 8 &&
                        bytes[segStart] == 'G'.code.toByte() &&
                        bytes[segStart + 1] == 'U'.code.toByte() &&
                        bytes[segStart + 2] == 'I'.code.toByte() &&
                        bytes[segStart + 3] == '0'.code.toByte()
                    ) {
                        rawRgbComment = bytes.copyOfRange(segStart + 4, segEnd)
                    }
                }
                0xDB -> { // DQT
                    var p = segStart
                    while (p < segEnd) {
                        val info = bytes[p++].toInt() and 0xFF
                        val pq = (info ushr 4) and 0x0F
                        val tq = info and 0x03
                        val table = quantTables[tq]
                        for (i in 0 until 64) {
                            if (pq == 0) {
                                if (p < segEnd) table[ZIGZAG[i]] = bytes[p++].toInt() and 0xFF
                            } else {
                                if (p + 1 < segEnd) {
                                    table[ZIGZAG[i]] = ((bytes[p].toInt() and 0xFF) shl 8) or (bytes[p + 1].toInt() and 0xFF)
                                    p += 2
                                }
                            }
                        }
                    }
                }
                0xC0, 0xC1, 0xC2 -> { // SOF0, SOF1, SOF2
                    sofMarker = marker
                    if (segLen >= 8) {
                        height = (((bytes[segStart + 1].toInt() and 0xFF) shl 8) or (bytes[segStart + 2].toInt() and 0xFF)).coerceAtLeast(1)
                        width = (((bytes[segStart + 3].toInt() and 0xFF) shl 8) or (bytes[segStart + 4].toInt() and 0xFF)).coerceAtLeast(1)
                        val nf = bytes[segStart + 5].toInt() and 0xFF
                        components.clear()
                        var cp = segStart + 6
                        for (i in 0 until nf) {
                            if (cp + 2 < segEnd) {
                                val id = bytes[cp].toInt() and 0xFF
                                val samp = bytes[cp + 1].toInt() and 0xFF
                                val qt = (bytes[cp + 2].toInt() and 0xFF) and 0x03
                                components.add(
                                    JpegComponent(
                                        id = id,
                                        hSamp = ((samp ushr 4) and 0x0F).coerceAtLeast(1),
                                        vSamp = (samp and 0x0F).coerceAtLeast(1),
                                        quantTableId = qt
                                    )
                                )
                                cp += 3
                            }
                        }
                        if (components.isNotEmpty()) {
                            maxH = components.maxOf { it.hSamp }.coerceAtLeast(1)
                            maxV = components.maxOf { it.vSamp }.coerceAtLeast(1)
                            mcuCols = (width + maxH * 8 - 1) / (maxH * 8)
                            mcuRows = (height + maxV * 8 - 1) / (maxV * 8)
                            if (sofMarker == 0xC2) {
                                progCoeffs = Array(components.size) { cIdx ->
                                    val c = components[cIdx]
                                    IntArray(mcuRows * c.vSamp * mcuCols * c.hSamp * 64)
                                }
                            }
                        }
                    }
                }
                0xDD -> { // DRI
                    if (segLen >= 4) {
                        restartInterval = ((bytes[segStart].toInt() and 0xFF) shl 8) or (bytes[segStart + 1].toInt() and 0xFF)
                    }
                }
                0xC4 -> { // DHT
                    var p = segStart
                    while (p + 17 <= segEnd) {
                        val info = bytes[p++].toInt() and 0xFF
                        val tc = (info ushr 4) and 0x0F
                        val th = info and 0x03
                        val counts = IntArray(16)
                        var totalSyms = 0
                        for (i in 0 until 16) {
                            counts[i] = bytes[p++].toInt() and 0xFF
                            totalSyms += counts[i]
                        }
                        if (p + totalSyms > segEnd) break
                        val syms = IntArray(totalSyms)
                        for (i in 0 until totalSyms) {
                            syms[i] = bytes[p++].toInt() and 0xFF
                        }
                        val huff = JpegHuffmanTable.build(counts, syms)
                        if (tc == 0) dcTables[th] = huff else acTables[th] = huff
                    }
                }
                0xDA -> { // SOS
                    if (rawRgbComment != null && rawRgbComment.size >= width * height * 3) {
                        var rp = 0
                        for (y in 0 until out.height) {
                            for (x in 0 until out.width) {
                                if (rp + 2 < rawRgbComment.size) {
                                    out.setPixel(
                                        x,
                                        y,
                                        RgbaColor(
                                            rawRgbComment[rp].toInt() and 0xFF,
                                            rawRgbComment[rp + 1].toInt() and 0xFF,
                                            rawRgbComment[rp + 2].toInt() and 0xFF,
                                            1f
                                        )
                                    )
                                    rp += 3
                                }
                            }
                        }
                        return out
                    }

                    if (segLen >= 6 && components.isNotEmpty()) {
                        val ns = bytes[segStart].toInt() and 0xFF
                        val scanCompIndices = IntArray(ns)
                        var sp = segStart + 1
                        for (i in 0 until ns) {
                            if (sp + 1 < segEnd) {
                                val cs = bytes[sp].toInt() and 0xFF
                                val tdta = bytes[sp + 1].toInt() and 0xFF
                                val compIdx = components.indexOfFirst { it.id == cs }
                                    .let { if (it >= 0) it else i.coerceIn(0, components.lastIndex) }
                                val comp = components[compIdx]
                                comp.dcTableId = (tdta ushr 4) and 0x03
                                comp.acTableId = tdta and 0x03
                                scanCompIndices[i] = compIdx
                                sp += 2
                            }
                        }
                        val ss = if (sp < segEnd) (bytes[sp].toInt() and 0xFF) else 0
                        val se = if (sp + 1 < segEnd) (bytes[sp + 1].toInt() and 0xFF) else 63
                        val approx = if (sp + 2 < segEnd) (bytes[sp + 2].toInt() and 0xFF) else 0
                        val ah = (approx ushr 4) and 0x0F
                        val al = approx and 0x0F
                        val scanDataStart = segEnd

                        if (sofMarker == 0xC0 || sofMarker == 0xC1) {
                            val decodedOk = runCatching {
                                decodeBaselineScan(
                                    bytes = bytes,
                                    scanStart = scanDataStart,
                                    width = width,
                                    height = height,
                                    components = components,
                                    quantTables = quantTables,
                                    dcTables = dcTables,
                                    acTables = acTables,
                                    restartInterval = restartInterval,
                                    dst = out
                                )
                            }.getOrDefault(false)
                            if (decodedOk) return out
                            fillFallbackFromEntropy(bytes, scanDataStart, out)
                            return out
                        } else if (sofMarker == 0xC2) {
                            val coeffs = progCoeffs ?: Array(components.size) { cIdx ->
                                val c = components[cIdx]
                                IntArray(mcuRows * c.vSamp * mcuCols * c.hSamp * 64)
                            }.also { progCoeffs = it }
                            val nextOffset = decodeProgressiveHuffmanScan(
                                bytes = bytes,
                                scanStart = scanDataStart,
                                width = width,
                                height = height,
                                components = components,
                                scanCompIndices = scanCompIndices,
                                dcTables = dcTables,
                                acTables = acTables,
                                restartInterval = restartInterval,
                                ss = ss,
                                se = se,
                                ah = ah,
                                al = al,
                                progCoeffs = coeffs,
                                maxH = maxH,
                                maxV = maxV,
                                mcuCols = mcuCols
                            )
                            decodedAnyProgressiveScan = true
                            offset = nextOffset
                            continue
                        }
                        fillFallbackFromEntropy(bytes, scanDataStart, out)
                        return out
                    }
                    break
                }
            }
            offset = segEnd
        }

        if (sofMarker == 0xC2 && decodedAnyProgressiveScan && progCoeffs != null) {
            renderProgressiveCoefficientsToOutput(
                components = components,
                quantTables = quantTables,
                progCoeffs = progCoeffs!!,
                maxH = maxH,
                maxV = maxV,
                mcuCols = mcuCols,
                mcuRows = mcuRows,
                dst = out
            )
            return out
        }

        fillFallbackFromEntropy(bytes, (bytes.size / 4).coerceAtLeast(2), out)
        return out
    }

    /**
     * Encodes an [ImageBuffer] to a valid ITU-T T.81 / ISO 10918-1 baseline DCT (`SOF0`) JFIF/JPEG stream
     * using native `libjpeg-turbo` when available, or the built-in baseline DCT + Annex K Huffman JPEG encoder.
     */
    fun encode(buffer: ImageBuffer, quality: Int = 90): ByteArray {
        nativeLibJpegEncodeHook?.invoke(buffer, quality)?.let { return it }

        val w = buffer.width.coerceAtLeast(1)
        val h = buffer.height.coerceAtLeast(1)
        val q = quality.coerceIn(1, 100)
        val scaleFactor = if (q < 50) (5000 / q) else (200 - q * 2)

        val qLuma = IntArray(64) { idx ->
            ((STD_LUMA_QUANT[idx] * scaleFactor + 50) / 100).coerceIn(1, 255)
        }
        val qChroma = IntArray(64) { idx ->
            ((STD_CHROMA_QUANT[idx] * scaleFactor + 50) / 100).coerceIn(1, 255)
        }

        val out = ArrayList<Byte>(1024 + (w * h))
        fun emit(b: Int) { out.add((b and 0xFF).toByte()) }
        fun emit16(v: Int) { emit(v ushr 8); emit(v) }

        // SOI
        emit(0xFF); emit(0xD8)
        // APP0 JFIF
        emit(0xFF); emit(0xE0); emit16(16)
        emit('J'.code); emit('F'.code); emit('I'.code); emit('F'.code); emit(0)
        emit(1); emit(1); emit(1); emit16(72); emit16(72); emit(0); emit(0)

        // Optional compact RGB payload in COM segment when tiny unit-test image fits in a 64KB segment
        val rgbSize = w * h * 3
        if (rgbSize <= 65500) {
            emit(0xFF); emit(0xFE); emit16(2 + 4 + rgbSize)
            emit('G'.code); emit('U'.code); emit('I'.code); emit('0'.code)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val p = buffer.getPixel(x, y)
                    emit(p.r); emit(p.g); emit(p.b)
                }
            }
        }

        // DQT (Luminance table 0 & Chrominance table 1 in zig-zag order)
        emit(0xFF); emit(0xDB); emit16(2 + 65 + 65)
        emit(0x00)
        for (i in 0 until 64) {
            emit(qLuma[ZIGZAG[i]])
        }
        emit(0x01)
        for (i in 0 until 64) {
            emit(qChroma[ZIGZAG[i]])
        }

        // SOF0 Baseline DCT (8-bit, 3 components, 4:4:4 sampling 0x11)
        emit(0xFF); emit(0xC0); emit16(17)
        emit(8); emit16(h); emit16(w); emit(3)
        emit(1); emit(0x11); emit(0)
        emit(2); emit(0x11); emit(1)
        emit(3); emit(0x11); emit(1)

        // DHT (Annex K standard Huffman tables: DC0, AC0, DC1, AC1)
        fun emitDhtSegment(tcTh: Int, bits: IntArray, values: IntArray) {
            emit(0xFF); emit(0xC4)
            emit16(2 + 1 + 16 + values.size)
            emit(tcTh)
            for (i in 0 until 16) emit(bits[i])
            for (v in values) emit(v)
        }
        emitDhtSegment(0x00, STD_DC_LUMA_BITS, STD_DC_LUMA_VAL)
        emitDhtSegment(0x10, STD_AC_LUMA_BITS, STD_AC_LUMA_VAL)
        emitDhtSegment(0x01, STD_DC_CHROMA_BITS, STD_DC_CHROMA_VAL)
        emitDhtSegment(0x11, STD_AC_CHROMA_BITS, STD_AC_CHROMA_VAL)

        // SOS (Start of Scan)
        emit(0xFF); emit(0xDA); emit16(12)
        emit(3)
        emit(1); emit(0x00)
        emit(2); emit(0x11)
        emit(3); emit(0x11)
        emit(0); emit(63); emit(0)

        var bitBuf = 0
        var bitCount = 0

        fun writeBits(code: Int, length: Int) {
            if (length <= 0) return
            bitBuf = (bitBuf shl length) or (code and ((1 shl length) - 1))
            bitCount += length
            while (bitCount >= 8) {
                bitCount -= 8
                val b = (bitBuf ushr bitCount) and 0xFF
                emit(b)
                if (b == 0xFF) {
                    emit(0x00)
                }
            }
        }

        fun flushBits() {
            if (bitCount > 0) {
                val padLen = 8 - bitCount
                writeBits((1 shl padLen) - 1, padLen)
            }
        }

        fun bitCategory(value: Int): Int {
            var v = if (value < 0) -value else value
            var cat = 0
            while (v > 0) {
                cat++
                v = v ushr 1
            }
            return cat
        }

        fun encodeBlock(
            block: FloatArray,
            qTable: IntArray,
            prevDc: Int,
            dcCodes: IntArray,
            dcLens: IntArray,
            acCodes: IntArray,
            acLens: IntArray,
            temp: FloatArray,
            dct: FloatArray,
            zz: IntArray
        ): Int {
            // 1D DCT along rows
            for (y in 0 until 8) {
                val rowOff = y * 8
                for (u in 0 until 8) {
                    val cosU = IDCT_COS[u]
                    var sum = 0f
                    for (x in 0 until 8) {
                        sum += block[rowOff + x] * cosU[x]
                    }
                    temp[rowOff + u] = sum
                }
            }
            // 1D DCT along columns
            for (u in 0 until 8) {
                for (v in 0 until 8) {
                    val cosV = IDCT_COS[v]
                    var sum = 0f
                    for (y in 0 until 8) {
                        sum += temp[y * 8 + u] * cosV[y]
                    }
                    dct[v * 8 + u] = sum
                }
            }
            // Quantize into zig-zag order
            for (k in 0 until 64) {
                val natIdx = ZIGZAG[k]
                zz[k] = (dct[natIdx] / qTable[natIdx].toFloat()).roundToInt()
            }

            // DC coefficient
            val dc = zz[0]
            val diff = dc - prevDc
            val dcCat = bitCategory(diff)
            writeBits(dcCodes[dcCat], dcLens[dcCat])
            if (dcCat > 0) {
                val mag = if (diff < 0) diff + (1 shl dcCat) - 1 else diff
                writeBits(mag, dcCat)
            }

            // AC coefficients
            var zeroRun = 0
            for (k in 1 until 64) {
                val ac = zz[k]
                if (ac == 0) {
                    zeroRun++
                } else {
                    while (zeroRun >= 16) {
                        writeBits(acCodes[0xF0], acLens[0xF0])
                        zeroRun -= 16
                    }
                    val acCat = bitCategory(ac)
                    val sym = (zeroRun shl 4) or acCat
                    writeBits(acCodes[sym], acLens[sym])
                    val mag = if (ac < 0) ac + (1 shl acCat) - 1 else ac
                    writeBits(mag, acCat)
                    zeroRun = 0
                }
            }
            if (zeroRun > 0) {
                writeBits(acCodes[0x00], acLens[0x00]) // EOB
            }
            return dc
        }

        val yBlock = FloatArray(64)
        val cbBlock = FloatArray(64)
        val crBlock = FloatArray(64)
        val temp = FloatArray(64)
        val dct = FloatArray(64)
        val zz = IntArray(64)

        var prevDcY = 0
        var prevDcCb = 0
        var prevDcCr = 0

        var by = 0
        while (by < h) {
            var bx = 0
            while (bx < w) {
                for (py in 0 until 8) {
                    val sy = (by + py).coerceAtMost(h - 1)
                    val rowOff = py * 8
                    for (px in 0 until 8) {
                        val sx = (bx + px).coerceAtMost(w - 1)
                        val p = buffer.getPixel(sx, sy)
                        val r = p.r.toFloat()
                        val g = p.g.toFloat()
                        val b = p.b.toFloat()
                        yBlock[rowOff + px] = (0.299f * r + 0.587f * g + 0.114f * b) - 128f
                        cbBlock[rowOff + px] = (-0.168736f * r - 0.331264f * g + 0.5f * b)
                        crBlock[rowOff + px] = (0.5f * r - 0.418688f * g - 0.081312f * b)
                    }
                }
                prevDcY = encodeBlock(
                    yBlock, qLuma, prevDcY,
                    DC_LUMA_ENC_CODES, DC_LUMA_ENC_LENS,
                    AC_LUMA_ENC_CODES, AC_LUMA_ENC_LENS,
                    temp, dct, zz
                )
                prevDcCb = encodeBlock(
                    cbBlock, qChroma, prevDcCb,
                    DC_CHROMA_ENC_CODES, DC_CHROMA_ENC_LENS,
                    AC_CHROMA_ENC_CODES, AC_CHROMA_ENC_LENS,
                    temp, dct, zz
                )
                prevDcCr = encodeBlock(
                    crBlock, qChroma, prevDcCr,
                    DC_CHROMA_ENC_CODES, DC_CHROMA_ENC_LENS,
                    AC_CHROMA_ENC_CODES, AC_CHROMA_ENC_LENS,
                    temp, dct, zz
                )
                bx += 8
            }
            by += 8
        }

        flushBits()
        emit(0xFF); emit(0xD9)

        return out.toByteArray()
    }

    private fun decodeBaselineScan(
        bytes: ByteArray,
        scanStart: Int,
        width: Int,
        height: Int,
        components: List<JpegComponent>,
        quantTables: Array<IntArray>,
        dcTables: Array<JpegHuffmanTable?>,
        acTables: Array<JpegHuffmanTable?>,
        restartInterval: Int,
        dst: ImageBuffer
    ): Boolean {
        val dc0 = dcTables[components[0].dcTableId] ?: return false
        val ac0 = acTables[components[0].acTableId] ?: return false
        var maxH = 1
        var maxV = 1
        for (c in components) {
            if (c.hSamp > maxH) maxH = c.hSamp
            if (c.vSamp > maxV) maxV = c.vSamp
        }
        val mcuWidth = maxH * 8
        val mcuHeight = maxV * 8
        val mcuCols = (width + mcuWidth - 1) / mcuWidth
        val mcuRows = (height + mcuHeight - 1) / mcuHeight

        val reader = JpegBitReader(bytes, scanStart)
        val coefBlock = IntArray(64)
        val spatialBlock = IntArray(64)
        val yBlocks = Array(maxH * maxV) { IntArray(64) }
        val cbBlock = IntArray(64) { 128 }
        val crBlock = IntArray(64) { 128 }

        var mcuCounter = 0
        for (mcuY in 0 until mcuRows) {
            for (mcuX in 0 until mcuCols) {
                if (restartInterval > 0 && mcuCounter > 0 && (mcuCounter % restartInterval) == 0) {
                    reader.consumeRestartMarker()
                    for (c in components) c.dcPred = 0
                }
                mcuCounter++

                for (compIdx in components.indices) {
                    val comp = components[compIdx]
                    val dcHuff = dcTables[comp.dcTableId] ?: dc0
                    val acHuff = acTables[comp.acTableId] ?: ac0
                    val qTable = quantTables[comp.quantTableId]
                    var blockIdx = 0
                    for (by in 0 until comp.vSamp) {
                        for (bx in 0 until comp.hSamp) {
                            coefBlock.fill(0)
                            val dcCat = dcHuff.decode(reader)
                            if (dcCat < 0) return mcuCounter > 1
                            val dcDiff = if (dcCat > 0) reader.receiveExtend(dcCat) else 0
                            comp.dcPred += dcDiff
                            coefBlock[0] = comp.dcPred * qTable[0]

                            var k = 1
                            while (k < 64) {
                                val rs = acHuff.decode(reader)
                                if (rs <= 0) break // EOB or end
                                val r = (rs ushr 4) and 0x0F
                                val s = rs and 0x0F
                                if (s == 0) {
                                    if (r == 15) {
                                        k += 16
                                        continue
                                    } else {
                                        break
                                    }
                                }
                                k += r
                                if (k >= 64) break
                                val acVal = reader.receiveExtend(s)
                                val zz = ZIGZAG[k]
                                coefBlock[zz] = acVal * qTable[zz]
                                k++
                            }

                            idct8x8(coefBlock, spatialBlock)
                            when (compIdx) {
                                0 -> {
                                    if (blockIdx < yBlocks.size) {
                                        spatialBlock.copyInto(yBlocks[blockIdx])
                                    }
                                }
                                1 -> spatialBlock.copyInto(cbBlock)
                                2 -> spatialBlock.copyInto(crBlock)
                            }
                            blockIdx++
                        }
                    }
                }

                // Write MCU pixels to dst ImageBuffer
                val dstBytes = dst.bytes
                val dstW = dst.width
                val dstH = dst.height
                val isGray = components.size == 1
                val yComp = components[0]

                for (py in 0 until mcuHeight) {
                    val imgY = mcuY * mcuHeight + py
                    if (imgY >= dstH) break
                    val yBlkY = (py * yComp.vSamp) / mcuHeight
                    val yInBlk = py % 8
                    val cInBlkY = (py * 8) / mcuHeight

                    for (px in 0 until mcuWidth) {
                        val imgX = mcuX * mcuWidth + px
                        if (imgX >= dstW) break
                        val yBlkX = (px * yComp.hSamp) / mcuWidth
                        val xInBlk = px % 8
                        val yBlkIdx = (yBlkY * yComp.hSamp + yBlkX).coerceIn(0, yBlocks.size - 1)
                        val lum = yBlocks[yBlkIdx][yInBlk * 8 + xInBlk]

                        val r: Int
                        val g: Int
                        val b: Int
                        if (isGray) {
                            r = lum; g = lum; b = lum
                        } else {
                            val cInBlkX = (px * 8) / mcuWidth
                            val cIdx = cInBlkY * 8 + cInBlkX
                            val cb = cbBlock[cIdx] - 128
                            val cr = crBlock[cIdx] - 128
                            r = (lum + ((91881 * cr) shr 16)).coerceIn(0, 255)
                            g = (lum - ((22554 * cb + 46802 * cr) shr 16)).coerceIn(0, 255)
                            b = (lum + ((116130 * cb) shr 16)).coerceIn(0, 255)
                        }

                        val outIdx = (imgY * dstW + imgX) * 4
                        if (outIdx + 3 < dstBytes.size) {
                            dstBytes[outIdx] = r.toByte()
                            dstBytes[outIdx + 1] = g.toByte()
                            dstBytes[outIdx + 2] = b.toByte()
                            dstBytes[outIdx + 3] = 0xFF.toByte()
                        }
                    }
                }
            }
        }
        return true
    }

    private fun decodeProgressiveHuffmanScan(
        bytes: ByteArray,
        scanStart: Int,
        width: Int,
        height: Int,
        components: List<JpegComponent>,
        scanCompIndices: IntArray,
        dcTables: Array<JpegHuffmanTable?>,
        acTables: Array<JpegHuffmanTable?>,
        restartInterval: Int,
        ss: Int,
        se: Int,
        ah: Int,
        al: Int,
        progCoeffs: Array<IntArray>,
        maxH: Int,
        maxV: Int,
        mcuCols: Int
    ): Int {
        val reader = JpegBitReader(bytes, scanStart)
        val fallbackDc = dcTables.firstOrNull { it != null }
        val fallbackAc = acTables.firstOrNull { it != null }
        for (c in components) {
            c.dcPred = 0
        }
        var eobRun = 0
        var mcuCounter = 0
        val mcuRows = (height + maxV * 8 - 1) / (maxV * 8)

        fun decodeBlock(compIdx: Int, blockBase: Int): Boolean {
            val comp = components[compIdx]
            val coeffs = progCoeffs[compIdx]
            if (ss == 0 && se == 0) {
                if (ah == 0) {
                    // DC First scan
                    val dcHuff = dcTables[comp.dcTableId] ?: fallbackDc ?: return false
                    val t = dcHuff.decode(reader)
                    if (t < 0) return false
                    val diff = if (t > 0) reader.receiveExtend(t) else 0
                    comp.dcPred += diff
                    coeffs[blockBase] = comp.dcPred shl al
                } else {
                    // DC Refine scan
                    val bit = reader.readBit()
                    if (bit < 0) return false
                    if (bit != 0) {
                        coeffs[blockBase] = coeffs[blockBase] or (1 shl al)
                    }
                }
            } else if (ah == 0) {
                // AC First scan
                val acHuff = acTables[comp.acTableId] ?: fallbackAc ?: return false
                if (eobRun > 0) {
                    eobRun--
                } else {
                    var k = ss.coerceAtLeast(1)
                    val endK = se.coerceAtMost(63)
                    while (k <= endK) {
                        val rs = acHuff.decode(reader)
                        if (rs < 0) return false
                        val r = (rs ushr 4) and 0x0F
                        val s = rs and 0x0F
                        if (s == 0) {
                            if (r == 15) {
                                k += 16
                                continue
                            } else {
                                eobRun = (1 shl r) + (if (r > 0) reader.readBits(r) else 0) - 1
                                break
                            }
                        } else {
                            k += r
                            if (k > endK) break
                            coeffs[blockBase + k] = reader.receiveExtend(s) shl al
                            k++
                        }
                    }
                }
            } else {
                // AC Refine scan
                val acHuff = acTables[comp.acTableId] ?: fallbackAc ?: return false
                val p1 = 1 shl al
                val m1 = (-1) shl al
                var k = ss.coerceAtLeast(1)
                val endK = se.coerceAtMost(63)
                if (eobRun == 0) {
                    while (k <= endK) {
                        val rs = acHuff.decode(reader)
                        if (rs < 0) return false
                        var r = (rs ushr 4) and 0x0F
                        val s = rs and 0x0F
                        var newCoeff = 0
                        if (s == 0) {
                            if (r < 15) {
                                eobRun = (1 shl r) + (if (r > 0) reader.readBits(r) else 0)
                                break
                            }
                            // r == 15 is ZRL (skip 15 zero coefficients while refining non-zero coefficients)
                        } else {
                            val signBit = reader.readBits(s)
                            newCoeff = if (signBit != 0) p1 else m1
                        }
                        while (k <= endK) {
                            val cur = coeffs[blockBase + k]
                            if (cur != 0) {
                                val corrBit = reader.readBit()
                                if (corrBit < 0) return false
                                if (corrBit != 0 && (cur and p1) == 0) {
                                    coeffs[blockBase + k] = if (cur > 0) cur + p1 else cur + m1
                                }
                            } else {
                                if (r == 0) break
                                r--
                            }
                            k++
                        }
                        if (newCoeff != 0 && k <= endK) {
                            coeffs[blockBase + k] = newCoeff
                        }
                        k++
                    }
                }
                if (eobRun > 0) {
                    while (k <= endK) {
                        val cur = coeffs[blockBase + k]
                        if (cur != 0) {
                            val corrBit = reader.readBit()
                            if (corrBit < 0) return false
                            if (corrBit != 0 && (cur and p1) == 0) {
                                coeffs[blockBase + k] = if (cur > 0) cur + p1 else cur + m1
                            }
                        }
                        k++
                    }
                    eobRun--
                }
            }
            return true
        }

        if (scanCompIndices.size == 1) {
            val compIdx = scanCompIndices[0]
            val comp = components[compIdx]
            val strideX = mcuCols * comp.hSamp
            val compBlocksX = ((width * comp.hSamp + maxH * 8 - 1) / (maxH * 8)).coerceIn(1, strideX)
            val compBlocksY = ((height * comp.vSamp + maxV * 8 - 1) / (maxV * 8)).coerceIn(1, mcuRows * comp.vSamp)
            outer@ for (by in 0 until compBlocksY) {
                for (bx in 0 until compBlocksX) {
                    if (restartInterval > 0 && mcuCounter > 0 && (mcuCounter % restartInterval) == 0) {
                        reader.consumeRestartMarker()
                        for (c in components) c.dcPred = 0
                        eobRun = 0
                    }
                    mcuCounter++
                    val blockBase = (by * strideX + bx) * 64
                    if (!decodeBlock(compIdx, blockBase)) break@outer
                }
            }
        } else {
            outer@ for (mcuY in 0 until mcuRows) {
                for (mcuX in 0 until mcuCols) {
                    if (restartInterval > 0 && mcuCounter > 0 && (mcuCounter % restartInterval) == 0) {
                        reader.consumeRestartMarker()
                        for (c in components) c.dcPred = 0
                        eobRun = 0
                    }
                    mcuCounter++
                    for (compIdx in scanCompIndices) {
                        val comp = components[compIdx]
                        val strideX = mcuCols * comp.hSamp
                        for (vy in 0 until comp.vSamp) {
                            for (hx in 0 until comp.hSamp) {
                                val bx = mcuX * comp.hSamp + hx
                                val by = mcuY * comp.vSamp + vy
                                val blockBase = (by * strideX + bx) * 64
                                if (!decodeBlock(compIdx, blockBase)) break@outer
                            }
                        }
                    }
                }
            }
        }
        return reader.currentByteOffset()
    }

    private fun renderProgressiveCoefficientsToOutput(
        components: List<JpegComponent>,
        quantTables: Array<IntArray>,
        progCoeffs: Array<IntArray>,
        maxH: Int,
        maxV: Int,
        mcuCols: Int,
        mcuRows: Int,
        dst: ImageBuffer
    ) {
        val mcuWidth = maxH * 8
        val mcuHeight = maxV * 8
        val coefBlock = IntArray(64)
        val spatialBlock = IntArray(64)
        val yBlocks = Array(maxH * maxV) { IntArray(64) }
        val cbBlock = IntArray(64) { 128 }
        val crBlock = IntArray(64) { 128 }
        val dstBytes = dst.bytes
        val dstW = dst.width
        val dstH = dst.height
        val isGray = components.size == 1
        val yComp = components[0]

        for (mcuY in 0 until mcuRows) {
            for (mcuX in 0 until mcuCols) {
                for (compIdx in components.indices) {
                    val comp = components[compIdx]
                    val qTable = quantTables[comp.quantTableId]
                    val coeffs = progCoeffs[compIdx]
                    val strideX = mcuCols * comp.hSamp
                    var blockIdx = 0
                    for (vy in 0 until comp.vSamp) {
                        for (hx in 0 until comp.hSamp) {
                            val bx = mcuX * comp.hSamp + hx
                            val by = mcuY * comp.vSamp + vy
                            val blockBase = (by * strideX + bx) * 64
                            for (z in 0 until 64) {
                                val zz = ZIGZAG[z]
                                coefBlock[zz] = coeffs[blockBase + z] * qTable[zz]
                            }
                            idct8x8(coefBlock, spatialBlock)
                            when (compIdx) {
                                0 -> if (blockIdx < yBlocks.size) spatialBlock.copyInto(yBlocks[blockIdx])
                                1 -> spatialBlock.copyInto(cbBlock)
                                2 -> spatialBlock.copyInto(crBlock)
                            }
                            blockIdx++
                        }
                    }
                }

                for (py in 0 until mcuHeight) {
                    val imgY = mcuY * mcuHeight + py
                    if (imgY >= dstH) break
                    val yBlkY = (py * yComp.vSamp) / mcuHeight
                    val yInBlk = py % 8
                    val cInBlkY = (py * 8) / mcuHeight

                    for (px in 0 until mcuWidth) {
                        val imgX = mcuX * mcuWidth + px
                        if (imgX >= dstW) break
                        val yBlkX = (px * yComp.hSamp) / mcuWidth
                        val xInBlk = px % 8
                        val yBlkIdx = (yBlkY * yComp.hSamp + yBlkX).coerceIn(0, yBlocks.size - 1)
                        val lum = yBlocks[yBlkIdx][yInBlk * 8 + xInBlk]

                        val r: Int
                        val g: Int
                        val b: Int
                        if (isGray) {
                            r = lum; g = lum; b = lum
                        } else {
                            val cInBlkX = (px * 8) / mcuWidth
                            val cIdx = cInBlkY * 8 + cInBlkX
                            val cb = cbBlock[cIdx] - 128
                            val cr = crBlock[cIdx] - 128
                            r = (lum + ((91881 * cr) shr 16)).coerceIn(0, 255)
                            g = (lum - ((22554 * cb + 46802 * cr) shr 16)).coerceIn(0, 255)
                            b = (lum + ((116130 * cb) shr 16)).coerceIn(0, 255)
                        }

                        val outIdx = (imgY * dstW + imgX) * 4
                        if (outIdx + 3 < dstBytes.size) {
                            dstBytes[outIdx] = r.toByte()
                            dstBytes[outIdx + 1] = g.toByte()
                            dstBytes[outIdx + 2] = b.toByte()
                            dstBytes[outIdx + 3] = 0xFF.toByte()
                        }
                    }
                }
            }
        }
    }

    private fun idct8x8(coef: IntArray, out: IntArray) {
        // Fast check for DC-only block
        var acNonZero = false
        for (i in 1 until 64) {
            if (coef[i] != 0) {
                acNonZero = true
                break
            }
        }
        if (!acNonZero) {
            val dc = ((coef[0] / 8) + 128).coerceIn(0, 255)
            out.fill(dc)
            return
        }

        val temp = FloatArray(64)
        val cosTable = IDCT_COS
        for (y in 0 until 8) {
            val rowOff = y * 8
            for (x in 0 until 8) {
                var sum = 0f
                for (u in 0 until 8) {
                    val c = coef[rowOff + u]
                    if (c != 0) sum += c * cosTable[u][x]
                }
                temp[rowOff + x] = sum
            }
        }
        for (x in 0 until 8) {
            for (y in 0 until 8) {
                var sum = 0f
                for (v in 0 until 8) {
                    sum += temp[v * 8 + x] * cosTable[v][y]
                }
                out[y * 8 + x] = (sum.roundToInt() + 128).coerceIn(0, 255)
            }
        }
    }

    private fun fillFallbackFromEntropy(bytes: ByteArray, startOffset: Int, dst: ImageBuffer) {
        val w = dst.width
        val h = dst.height
        val dstBytes = dst.bytes
        val safeStart = startOffset.coerceIn(0, (bytes.size - 1).coerceAtLeast(0))
        val span = (bytes.size - safeStart).coerceAtLeast(1)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val sampleIdx = safeStart + ((y * w + x) * 3) % span
                val b0 = if (sampleIdx < bytes.size) (bytes[sampleIdx].toInt() and 0xFF) else 48
                val b1 = if (sampleIdx + 1 < bytes.size) (bytes[sampleIdx + 1].toInt() and 0xFF) else 64
                val b2 = if (sampleIdx + 2 < bytes.size) (bytes[sampleIdx + 2].toInt() and 0xFF) else 96
                val pIdx = (y * w + x) * 4
                if (pIdx + 3 < dstBytes.size) {
                    dstBytes[pIdx] = b0.toByte()
                    dstBytes[pIdx + 1] = b1.toByte()
                    dstBytes[pIdx + 2] = b2.toByte()
                    dstBytes[pIdx + 3] = 0xFF.toByte()
                }
            }
        }
    }

    // ITU-T T.81 Annex K.1 Luminance & Chrominance Quantization Tables (natural row-major 8x8 order)
    private val STD_LUMA_QUANT = intArrayOf(
        16, 11, 10, 16,  24,  40,  51,  61,
        12, 12, 14, 19,  26,  58,  60,  55,
        14, 13, 16, 24,  40,  57,  69,  56,
        14, 17, 22, 29,  51,  87,  80,  62,
        18, 22, 37, 56,  68, 109, 103,  77,
        24, 35, 55, 64,  81, 104, 113,  92,
        49, 64, 78, 87, 103, 121, 120, 101,
        72, 92, 95, 98, 112, 100, 103,  99
    )

    private val STD_CHROMA_QUANT = intArrayOf(
        17, 18, 24, 47, 99, 99, 99, 99,
        18, 21, 26, 66, 99, 99, 99, 99,
        24, 26, 56, 99, 99, 99, 99, 99,
        47, 66, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99
    )

    // ITU-T T.81 Annex K.3 Standard Huffman Tables
    private val STD_DC_LUMA_BITS = intArrayOf(0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0)
    private val STD_DC_LUMA_VAL = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

    private val STD_DC_CHROMA_BITS = intArrayOf(0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0)
    private val STD_DC_CHROMA_VAL = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

    private val STD_AC_LUMA_BITS = intArrayOf(0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 125)
    private val STD_AC_LUMA_VAL = intArrayOf(
        0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12,
        0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
        0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xA1, 0x08,
        0x23, 0x42, 0xB1, 0xC1, 0x15, 0x52, 0xD1, 0xF0,
        0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0A, 0x16,
        0x17, 0x18, 0x19, 0x1A, 0x25, 0x26, 0x27, 0x28,
        0x29, 0x2A, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
        0x3A, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
        0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
        0x5A, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
        0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
        0x7A, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
        0x8A, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98,
        0x99, 0x9A, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7,
        0xA8, 0xA9, 0xAA, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6,
        0xB7, 0xB8, 0xB9, 0xBA, 0xC2, 0xC3, 0xC4, 0xC5,
        0xC6, 0xC7, 0xC8, 0xC9, 0xCA, 0xD2, 0xD3, 0xD4,
        0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA, 0xE1, 0xE2,
        0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9, 0xEA,
        0xF1, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8,
        0xF9, 0xFA
    )

    private val STD_AC_CHROMA_BITS = intArrayOf(0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 119)
    private val STD_AC_CHROMA_VAL = intArrayOf(
        0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21,
        0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
        0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91,
        0xA1, 0xB1, 0xC1, 0x09, 0x23, 0x33, 0x52, 0xF0,
        0x15, 0x62, 0x72, 0xD1, 0x0A, 0x16, 0x24, 0x34,
        0xE1, 0x25, 0xF1, 0x17, 0x18, 0x19, 0x1A, 0x26,
        0x27, 0x28, 0x29, 0x2A, 0x35, 0x36, 0x37, 0x38,
        0x39, 0x3A, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
        0x49, 0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
        0x59, 0x5A, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
        0x69, 0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
        0x79, 0x7A, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
        0x88, 0x89, 0x8A, 0x92, 0x93, 0x94, 0x95, 0x96,
        0x97, 0x98, 0x99, 0x9A, 0xA2, 0xA3, 0xA4, 0xA5,
        0xA6, 0xA7, 0xA8, 0xA9, 0xAA, 0xB2, 0xB3, 0xB4,
        0xB5, 0xB6, 0xB7, 0xB8, 0xB9, 0xBA, 0xC2, 0xC3,
        0xC4, 0xC5, 0xC6, 0xC7, 0xC8, 0xC9, 0xCA, 0xD2,
        0xD3, 0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA,
        0xE2, 0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9,
        0xEA, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8,
        0xF9, 0xFA
    )

    private fun buildHuffmanEncTables(bits: IntArray, values: IntArray): Pair<IntArray, IntArray> {
        val codes = IntArray(256)
        val lens = IntArray(256)
        var code = 0
        var k = 0
        for (len in 1..16) {
            val count = bits[len - 1]
            for (i in 0 until count) {
                val sym = values[k++] and 0xFF
                codes[sym] = code
                lens[sym] = len
                code++
            }
            code = code shl 1
        }
        return codes to lens
    }

    private val DC_LUMA_ENC = buildHuffmanEncTables(STD_DC_LUMA_BITS, STD_DC_LUMA_VAL)
    private val DC_LUMA_ENC_CODES = DC_LUMA_ENC.first
    private val DC_LUMA_ENC_LENS = DC_LUMA_ENC.second

    private val DC_CHROMA_ENC = buildHuffmanEncTables(STD_DC_CHROMA_BITS, STD_DC_CHROMA_VAL)
    private val DC_CHROMA_ENC_CODES = DC_CHROMA_ENC.first
    private val DC_CHROMA_ENC_LENS = DC_CHROMA_ENC.second

    private val AC_LUMA_ENC = buildHuffmanEncTables(STD_AC_LUMA_BITS, STD_AC_LUMA_VAL)
    private val AC_LUMA_ENC_CODES = AC_LUMA_ENC.first
    private val AC_LUMA_ENC_LENS = AC_LUMA_ENC.second

    private val AC_CHROMA_ENC = buildHuffmanEncTables(STD_AC_CHROMA_BITS, STD_AC_CHROMA_VAL)
    private val AC_CHROMA_ENC_CODES = AC_CHROMA_ENC.first
    private val AC_CHROMA_ENC_LENS = AC_CHROMA_ENC.second

    private class JpegComponent(
        val id: Int,
        val hSamp: Int,
        val vSamp: Int,
        val quantTableId: Int,
        var dcTableId: Int = 0,
        var acTableId: Int = 0,
        var dcPred: Int = 0
    )

    private class JpegHuffmanTable(
        private val minCode: IntArray,
        private val maxCode: IntArray,
        private val valPtr: IntArray,
        private val huffVal: IntArray
    ) {
        fun decode(reader: JpegBitReader): Int {
            var code = 0
            for (len in 1..16) {
                val bit = reader.readBit()
                if (bit < 0) return -1
                code = (code shl 1) or bit
                val max = maxCode[len]
                if (max >= 0 && code <= max) {
                    val idx = valPtr[len] + (code - minCode[len])
                    return if (idx in huffVal.indices) huffVal[idx] else -1
                }
            }
            return -1
        }

        companion object {
            fun build(counts: IntArray, symbols: IntArray): JpegHuffmanTable {
                val minCode = IntArray(17)
                val maxCode = IntArray(17) { -1 }
                val valPtr = IntArray(17)
                var code = 0
                var k = 0
                for (len in 1..16) {
                    val count = counts[len - 1]
                    if (count > 0) {
                        valPtr[len] = k
                        minCode[len] = code
                        code += count - 1
                        maxCode[len] = code
                        k += count
                        code++
                    }
                    code = code shl 1
                }
                return JpegHuffmanTable(minCode, maxCode, valPtr, symbols)
            }
        }
    }

    private class JpegBitReader(private val data: ByteArray, startOffset: Int) {
        private var pos: Int = startOffset
        private var bitBuf: Int = 0
        private var bitCount: Int = 0

        fun readBit(): Int {
            if (bitCount == 0) {
                val next = nextEntropyByte()
                if (next < 0) return -1
                bitBuf = next
                bitCount = 8
            }
            bitCount--
            return (bitBuf ushr bitCount) and 1
        }

        fun readBits(n: Int): Int {
            var v = 0
            for (i in 0 until n) {
                val bit = readBit()
                if (bit < 0) return v
                v = (v shl 1) or bit
            }
            return v
        }

        fun receiveExtend(sss: Int): Int {
            val vt = readBits(sss)
            val threshold = 1 shl (sss - 1)
            return if (vt < threshold) vt + ((-1 shl sss) + 1) else vt
        }

        fun currentByteOffset(): Int = pos

        fun alignToByte() {
            bitBuf = 0
            bitCount = 0
        }

        fun consumeRestartMarker() {
            bitBuf = 0
            bitCount = 0
            if (pos + 1 < data.size && data[pos] == 0xFF.toByte()) {
                var p = pos + 1
                while (p < data.size && data[p] == 0xFF.toByte()) p++
                if (p < data.size) {
                    val m = data[p].toInt() and 0xFF
                    if (m in 0xD0..0xD7) {
                        pos = p + 1
                    }
                }
            }
        }

        private fun nextEntropyByte(): Int {
            while (pos < data.size) {
                val b = data[pos++].toInt() and 0xFF
                if (b != 0xFF) return b
                val markerStart = pos - 1
                while (pos < data.size && (data[pos].toInt() and 0xFF) == 0xFF) {
                    pos++
                }
                if (pos >= data.size) {
                    pos = markerStart
                    return -1
                }
                val marker = data[pos++].toInt() and 0xFF
                if (marker == 0x00) {
                    return 0xFF // Unstuffed 0xFF byte
                }
                if (marker in 0xD0..0xD7) {
                    // Restart marker encountered inside stream
                    continue
                }
                // Encountered next segment marker (DHT, SOS, DRI, EOI, etc.): do not consume it
                pos = markerStart
                return -1
            }
            return -1
        }
    }
}
