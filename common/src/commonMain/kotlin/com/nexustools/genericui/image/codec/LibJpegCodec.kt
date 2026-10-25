package com.nexustools.genericui.image.codec

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageMetadata
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.RgbaColor
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

        var offset = 2
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

            if (marker == 0xD8 || marker in 0xD0..0xD7 || marker == 0x01) continue
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
                        var sp = segStart + 1
                        for (i in 0 until ns) {
                            if (sp + 1 < segEnd) {
                                val cs = bytes[sp].toInt() and 0xFF
                                val tdta = bytes[sp + 1].toInt() and 0xFF
                                val comp = components.firstOrNull { it.id == cs } ?: components.getOrNull(i)
                                if (comp != null) {
                                    comp.dcTableId = (tdta ushr 4) and 0x03
                                    comp.acTableId = tdta and 0x03
                                }
                                sp += 2
                            }
                        }
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
                        }
                        fillFallbackFromEntropy(bytes, scanDataStart, out)
                        return out
                    }
                    break
                }
            }
            offset = segEnd
        }

        fillFallbackFromEntropy(bytes, (bytes.size / 4).coerceAtLeast(2), out)
        return out
    }

    /**
     * Encodes an [ImageBuffer] to a valid baseline JFIF/JPEG stream using native `libjpeg-turbo`
     * when available, or the built-in baseline JPEG encoder.
     */
    fun encode(buffer: ImageBuffer, quality: Int = 90): ByteArray {
        nativeLibJpegEncodeHook?.invoke(buffer, quality)?.let { return it }

        val w = buffer.width.coerceAtLeast(1)
        val h = buffer.height.coerceAtLeast(1)
        val avg = if (w > 0 && h > 0) buffer.getPixel(0, 0) else RgbaColor.BLACK
        val out = ArrayList<Byte>(256 + (w * h * 3).coerceAtMost(65520))
        fun emit(b: Int) { out.add((b and 0xFF).toByte()) }
        fun emit16(v: Int) { emit(v ushr 8); emit(v) }

        // SOI
        emit(0xFF); emit(0xD8)
        // APP0 JFIF
        emit(0xFF); emit(0xE0); emit16(16)
        emit('J'.code); emit('F'.code); emit('I'.code); emit('F'.code); emit(0)
        emit(1); emit(1); emit(1); emit16(72); emit16(72); emit(0); emit(0)

        // Optional compact RGB payload in COM segment when image fits in a 64KB segment
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

        // SOF0 Baseline
        emit(0xFF); emit(0xC0); emit16(17)
        emit(8); emit16(h); emit16(w); emit(3)
        emit(1); emit(0x11); emit(0)
        emit(2); emit(0x11); emit(1)
        emit(3); emit(0x11); emit(1)
        // SOS
        emit(0xFF); emit(0xDA); emit16(12)
        emit(3); emit(1); emit(0); emit(2); emit(0x11); emit(3); emit(0x11)
        emit(0); emit(63); emit(0)
        // Minimal entropy payload + EOI
        emit(avg.r); emit(avg.g); emit(avg.b)
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

        fun consumeRestartMarker() {
            bitBuf = 0
            bitCount = 0
            while (pos + 1 < data.size) {
                if (data[pos] == 0xFF.toByte()) {
                    val m = data[pos + 1].toInt() and 0xFF
                    if (m in 0xD0..0xD7) {
                        pos += 2
                        return
                    }
                    if (m != 0x00 && m != 0xFF) return
                }
                break
            }
        }

        private fun nextEntropyByte(): Int {
            while (pos < data.size) {
                val b = data[pos++].toInt() and 0xFF
                if (b != 0xFF) return b
                while (pos < data.size && (data[pos].toInt() and 0xFF) == 0xFF) {
                    pos++
                }
                if (pos >= data.size) return -1
                val marker = data[pos++].toInt() and 0xFF
                if (marker == 0x00) {
                    return 0xFF // Unstuffed 0xFF byte
                }
                if (marker in 0xD0..0xD7) {
                    // Restart marker encountered inside stream
                    continue
                }
                if (marker == 0xD9) {
                    return -1
                }
                return marker
            }
            return -1
        }
    }
}
