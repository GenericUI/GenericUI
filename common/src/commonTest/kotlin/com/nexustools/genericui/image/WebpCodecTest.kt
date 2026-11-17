package com.nexustools.genericui.image

import com.nexustools.genericui.image.codec.LibWebpCodec
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WebpCodecTest {

    private class Vp8lBitWriter {
        private val bytes = ArrayList<Byte>()
        private var bitBuf = 0
        private var bitCount = 0

        fun writeBits(value: Int, numBits: Int) {
            for (i in 0 until numBits) {
                val bit = (value ushr i) and 1
                bitBuf = bitBuf or (bit shl bitCount)
                bitCount++
                if (bitCount == 8) {
                    bytes.add((bitBuf and 0xFF).toByte())
                    bitBuf = 0
                    bitCount = 0
                }
            }
        }

        fun writeMsbBits(value: Int, numBits: Int) {
            for (i in (numBits - 1) downTo 0) {
                writeBits((value ushr i) and 1, 1)
            }
        }

        fun finish(): ByteArray {
            if (bitCount > 0) {
                bytes.add((bitBuf and 0xFF).toByte())
                bitBuf = 0
                bitCount = 0
            }
            return bytes.toByteArray()
        }
    }

    private fun writeFixed8Tree(writer: Vp8lBitWriter, alphabetSize: Int) {
        writer.writeBits(0, 1) // normal code
        writer.writeBits(12 - 4, 4) // first 12 CL symbols in order -> covers symbol 8 at index 11
        for (i in 0 until 12) {
            writer.writeBits(if (i == 11) 1 else 0, 3)
        }
        if (alphabetSize == 256) {
            writer.writeBits(0, 1) // use_max_symbol = 0 -> 256 symbols using 0-bit CL code
        } else {
            writer.writeBits(1, 1) // use_max_symbol = 1
            writer.writeBits(4, 3) // lengthBits = 2 + 2*4 = 10
            writer.writeBits(256 - 2, 10) // max_symbol = 256
        }
    }

    private fun writeSimpleOneSymTree(writer: Vp8lBitWriter, sym: Int) {
        writer.writeBits(1, 1) // simple
        writer.writeBits(0, 1) // 1 symbol
        val is8 = if (sym > 1) 1 else 0
        writer.writeBits(is8, 1)
        writer.writeBits(sym, 1 + 7 * is8)
    }

    private fun writeSimpleTwoSymTree(writer: Vp8lBitWriter, sym0: Int, sym1: Int) {
        writer.writeBits(1, 1) // simple
        writer.writeBits(1, 1) // 2 symbols
        val is8 = if (sym0 > 1) 1 else 0
        writer.writeBits(is8, 1)
        writer.writeBits(sym0, 1 + 7 * is8)
        writer.writeBits(sym1, 8)
    }

    private fun writeSparsePowerOfTwoTree(
        writer: Vp8lBitWriter,
        alphabetSize: Int,
        symbols: IntArray
    ): Map<Int, Pair<Int, Int>> {
        val k = symbols.size
        var bitLen = 0
        var tmp = k
        while (tmp > 1) {
            tmp = tmp ushr 1
            bitLen++
        }
        val order = intArrayOf(17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
        val lengths = IntArray(alphabetSize)
        for (s in symbols) lengths[s] = bitLen

        writer.writeBits(0, 1) // normal
        writer.writeBits(19 - 4, 4) // all 19 CL entries
        for (clSym in order) {
            writer.writeBits(if (clSym == 0 || clSym == bitLen) 1 else 0, 3)
        }
        writer.writeBits(0, 1) // use_max_symbol = 0
        for (len in lengths) {
            writer.writeBits(if (len == 0) 0 else 1, 1)
        }
        val sorted = symbols.sorted()
        return sorted.mapIndexed { idx, sym -> sym to (idx to bitLen) }.toMap()
    }

    private fun writeFixed8ImageStream(writer: Vp8lBitWriter, pixels: IntArray, isTopLevel: Boolean) {
        writer.writeBits(0, 1) // no color cache
        if (isTopLevel) writer.writeBits(0, 1) // no meta
        writeFixed8Tree(writer, 256 + 24) // green
        writeFixed8Tree(writer, 256) // red
        writeFixed8Tree(writer, 256) // blue
        writeFixed8Tree(writer, 256) // alpha
        writeSimpleOneSymTree(writer, 0) // distance
        for (px in pixels) {
            writer.writeMsbBits((px ushr 8) and 0xFF, 8)
            writer.writeMsbBits((px ushr 16) and 0xFF, 8)
            writer.writeMsbBits(px and 0xFF, 8)
            writer.writeMsbBits((px ushr 24) and 0xFF, 8)
        }
    }

    private fun wrapRiffWebp(vararg chunks: Pair<String, ByteArray>): ByteArray {
        val body = ArrayList<Byte>()
        body.add('W'.code.toByte())
        body.add('E'.code.toByte())
        body.add('B'.code.toByte())
        body.add('P'.code.toByte())
        for ((fourCc, data) in chunks) {
            for (c in fourCc) body.add(c.code.toByte())
            appendInt32LE(body, data.size)
            for (b in data) body.add(b)
            if ((data.size and 1) != 0) body.add(0)
        }
        val out = ArrayList<Byte>(8 + body.size)
        out.add('R'.code.toByte())
        out.add('I'.code.toByte())
        out.add('F'.code.toByte())
        out.add('F'.code.toByte())
        appendInt32LE(out, body.size)
        out.addAll(body)
        return out.toByteArray()
    }

    private fun wrapVp8lChunk(width: Int, height: Int, hasAlpha: Boolean, bitstreamBody: ByteArray): ByteArray {
        val chunk = ByteArray(5 + bitstreamBody.size)
        chunk[0] = 0x2F.toByte()
        val header = ((width - 1) and 0x3FFF) or
            (((height - 1) and 0x3FFF) shl 14) or
            ((if (hasAlpha) 1 else 0) shl 28)
        chunk[1] = (header and 0xFF).toByte()
        chunk[2] = ((header ushr 8) and 0xFF).toByte()
        chunk[3] = ((header ushr 16) and 0xFF).toByte()
        chunk[4] = ((header ushr 24) and 0xFF).toByte()
        bitstreamBody.copyInto(chunk, 5)
        return chunk
    }

    private fun appendInt32LE(dst: MutableList<Byte>, v: Int) {
        dst.add((v and 0xFF).toByte())
        dst.add(((v ushr 8) and 0xFF).toByte())
        dst.add(((v ushr 16) and 0xFF).toByte())
        dst.add(((v ushr 24) and 0xFF).toByte())
    }

    private fun assertArgbBufferMatches(expectedArgb: IntArray, buffer: ImageBuffer, width: Int, height: Int) {
        assertEquals(width, buffer.width)
        assertEquals(height, buffer.height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val exp = expectedArgb[y * width + x]
                val expectedColor = RgbaColor(
                    r = (exp ushr 16) and 0xFF,
                    g = (exp ushr 8) and 0xFF,
                    b = exp and 0xFF,
                    a = (exp ushr 24) and 0xFF
                )
                assertEquals(expectedColor, buffer.getPixel(x, y), "Pixel mismatch at ($x, $y)")
            }
        }
    }

    private inline fun withPureKotlinWebpDecode(block: () -> Unit) {
        val prevWebpHook = LibWebpCodec.nativeLibWebpDecodeHook
        val prevNativeCodec = ImageLoader.activeNativeCodec
        val prevDecoderHook = ImageLoader.platformDecoderHook
        LibWebpCodec.nativeLibWebpDecodeHook = null
        ImageLoader.activeNativeCodec = null
        ImageLoader.platformDecoderHook = null
        try {
            block()
        } finally {
            LibWebpCodec.nativeLibWebpDecodeHook = prevWebpHook
            ImageLoader.activeNativeCodec = prevNativeCodec
            ImageLoader.platformDecoderHook = prevDecoderHook
        }
    }

    @Test
    fun `LibWebpCodec decodes real cwebp lossless VP8L fixtures with palettes gradients and alpha`() {
        withPureKotlinWebpDecode {
            // 1. 1x1 solid VP8L
            val solidMeta = LibWebpCodec.probeWebpMetadata(SOLID_1X1)
            assertEquals(1, solidMeta.width)
            assertEquals(1, solidMeta.height)
            assertTrue(solidMeta.hasAlpha)
            assertFalse(solidMeta.isAnimated)
            val solidBuf = LibWebpCodec.decode(SOLID_1X1)
            assertEquals(RgbaColor(12, 34, 56, 200), solidBuf.getPixel(0, 0))

            // 2. 17x13 16-color palette VP8L (4-bit color indexing transform)
            val pal16Expected = IntArray(17 * 13)
            var seed16 = 202
            for (idx in pal16Expected.indices) {
                seed16 = (seed16 * 1103515245 + 12345) and 0x7FFFFFFF
                val i = (seed16 ushr 16) and 15
                val r = (i * 43 + 10) and 0xFF
                val g = (i * 97 + 30) and 0xFF
                val b = (i * 151 + 60) and 0xFF
                val a = if ((i and 1) != 0) 255 else 180
                pal16Expected[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            val pal16Buf = LibWebpCodec.decode(PAL16_17X13)
            assertArgbBufferMatches(pal16Expected, pal16Buf, 17, 13)

            // 3. 19x17 32-color palette VP8L (8-bit color indexing transform)
            val pal32Expected = IntArray(19 * 17)
            var seed32 = 303
            for (idx in pal32Expected.indices) {
                seed32 = (seed32 * 1103515245 + 12345) and 0x7FFFFFFF
                val i = (seed32 ushr 16) and 31
                val r = (i * 37 + 15) and 0xFF
                val g = (i * 73 + 5) and 0xFF
                val b = (i * 19 + 80) and 0xFF
                pal32Expected[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            val pal32Buf = LibWebpCodec.decode(PAL32_19X17)
            assertArgbBufferMatches(pal32Expected, pal32Buf, 19, 17)

            // 4. 40x40 gradient with Subtract-Green + Predictor + Cross-Color + Alpha
            val gradExpected = IntArray(40 * 40)
            for (y in 0 until 40) {
                for (x in 0 until 40) {
                    val r = (x * 6 + y * 2) and 0xFF
                    val g = (y * 6 + x) and 0xFF
                    val b = ((r + g * 2) / 2) and 0xFF
                    val a = if ((x + y) % 4 != 0) 255 else 160
                    gradExpected[y * 40 + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            val gradBuf = LibWebpCodec.decode(GRAD_40X40)
            assertArgbBufferMatches(gradExpected, gradBuf, 40, 40)

            // Verify ImageLoader.load(fromBytes) decodes real VP8L pixels rather than blank pixels
            val loaded = ImageLoader.load(ImageSource.fromBytes(GRAD_40X40, ImageFormat.WEBP))
            assertArgbBufferMatches(gradExpected, loaded, 40, 40)
        }
    }

    @Test
    fun `LibWebpCodec decodes VP8L ColorIndexing 1-bit and 2-bit packed palettes`() {
        withPureKotlinWebpDecode {
            // Case A: 2-color palette (widthBits = 3 -> 8 pixels/byte), 11x3 -> packedWidth = 2
            val w1 = 11
            val h1 = 3
            val pal2 = intArrayOf(0xFF112233.toInt(), 0x80CCDDEE.toInt())
            val pal2Deltas = intArrayOf(
                pal2[0],
                subArgbMod256(pal2[1], pal2[0])
            )
            val expected1 = IntArray(w1 * h1) { i -> pal2[(i * 3 + 1) and 1] }
            val packed1 = IntArray(2 * h1)
            for (y in 0 until h1) {
                for (px in 0 until 2) {
                    var packedByte = 0
                    for (bit in 0 until 8) {
                        val x = px * 8 + bit
                        if (x < w1) {
                            val idx = ((y * w1 + x) * 3 + 1) and 1
                            packedByte = packedByte or (idx shl bit)
                        }
                    }
                    packed1[y * 2 + px] = (0xFF shl 24) or (packedByte shl 8)
                }
            }
            val bw1 = Vp8lBitWriter()
            bw1.writeBits(1, 1) // has transform
            bw1.writeBits(3, 2) // COLOR_INDEXING
            bw1.writeBits(2 - 1, 8) // palette size = 2
            writeFixed8ImageStream(bw1, pal2Deltas, isTopLevel = false)
            bw1.writeBits(0, 1) // end transforms
            writeFixed8ImageStream(bw1, packed1, isTopLevel = true)
            val webp1 = wrapRiffWebp("VP8L" to wrapVp8lChunk(w1, h1, true, bw1.finish()))
            assertArgbBufferMatches(expected1, LibWebpCodec.decode(webp1), w1, h1)

            // Case B: 4-color palette (widthBits = 2 -> 4 pixels/byte), 7x2 -> packedWidth = 2
            val w2 = 7
            val h2 = 2
            val pal4 = intArrayOf(
                0xFFFF0000.toInt(),
                0xFF00FF00.toInt(),
                0xFF0000FF.toInt(),
                0x80FFFFFF.toInt()
            )
            val pal4Deltas = IntArray(4) { i ->
                if (i == 0) pal4[0] else subArgbMod256(pal4[i], pal4[i - 1])
            }
            val expected2 = IntArray(w2 * h2) { i -> pal4[(i * 5 + 2) and 3] }
            val packed2 = IntArray(2 * h2)
            for (y in 0 until h2) {
                for (px in 0 until 2) {
                    var packedByte = 0
                    for (sub in 0 until 4) {
                        val x = px * 4 + sub
                        if (x < w2) {
                            val idx = ((y * w2 + x) * 5 + 2) and 3
                            packedByte = packedByte or (idx shl (sub * 2))
                        }
                    }
                    packed2[y * 2 + px] = (0xFF shl 24) or (packedByte shl 8)
                }
            }
            val bw2 = Vp8lBitWriter()
            bw2.writeBits(1, 1)
            bw2.writeBits(3, 2)
            bw2.writeBits(4 - 1, 8)
            writeFixed8ImageStream(bw2, pal4Deltas, isTopLevel = false)
            bw2.writeBits(0, 1)
            writeFixed8ImageStream(bw2, packed2, isTopLevel = true)
            val webp2 = wrapRiffWebp("VP8L" to wrapVp8lChunk(w2, h2, true, bw2.finish()))
            assertArgbBufferMatches(expected2, LibWebpCodec.decode(webp2), w2, h2)
        }
    }

    @Test
    fun `LibWebpCodec decodes VP8L all 14 Predictor modes CrossColor and SubtractGreen`() {
        withPureKotlinWebpDecode {
            val w = 56 // 14 blocks of width 4 (sizeBits = 2) -> exercises modes 0..13
            val h = 4
            val orig = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val r = (x * 13 + y * 37) and 0xFF
                    val g = (x * 29 + y * 11) and 0xFF
                    val b = (x * 7 + y * 53) and 0xFF
                    val a = if ((x + y) % 3 == 0) 200 else 255
                    orig[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            // Force a Select tie at block 11 (x = 45, y = 2): pa == pb so Select must choose T over L
            val tieX = 45
            val tieY = 2
            orig[(tieY - 1) * w + (tieX - 1)] = 0xFF504030.toInt() // TL
            orig[(tieY - 1) * w + tieX] = 0xFF554535.toInt()       // T: |T - TL| = 5+5+5 = 15
            orig[tieY * w + (tieX - 1)] = 0xFF4B3B2B.toInt()       // L: |L - TL| = 5+5+5 = 15

            // 1. Forward Cross-Color Transform (sizeBits = 2 -> 14x1 tiles; keep block 11 identity to preserve exact Select tie)
            val ctmTiles = IntArray(14) { bx ->
                if (bx == 11) {
                    0xFF000000.toInt()
                } else {
                    val g2r = ((bx * 9 - 40) and 0xFF)
                    val g2b = ((bx * 7 - 25) and 0xFF)
                    val r2b = ((bx * 5 - 15) and 0xFF)
                    (0xFF shl 24) or (r2b shl 16) or (g2b shl 8) or g2r
                }
            }
            val afterCrossColor = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val ctm = ctmTiles[x ushr 2]
                    val g2r = (ctm and 0xFF).toByte().toInt()
                    val g2b = ((ctm ushr 8) and 0xFF).toByte().toInt()
                    val r2b = ((ctm ushr 16) and 0xFF).toByte().toInt()
                    val p = orig[y * w + x]
                    val a = (p ushr 24) and 0xFF
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    val gSigned = g.toByte().toInt()
                    val rSigned = r.toByte().toInt()
                    val outR = (r - ((g2r * gSigned) shr 5)) and 0xFF
                    val outB = (b - ((g2b * gSigned) shr 5) - ((r2b * rSigned) shr 5)) and 0xFF
                    afterCrossColor[y * w + x] = (a shl 24) or (outR shl 16) or (g shl 8) or outB
                }
            }

            // 2. Forward Subtract-Green Transform
            val afterSubGreen = IntArray(w * h) { i ->
                val p = afterCrossColor[i]
                val a = (p ushr 24) and 0xFF
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                (a shl 24) or (((r - g) and 0xFF) shl 16) or (g shl 8) or ((b - g) and 0xFF)
            }

            // 3. Forward Predictor Transform (sizeBits = 2, mode = x / 4 in 0..13)
            val predModes = IntArray(14) { bx -> (0xFF shl 24) or (bx shl 8) }
            val residuals = IntArray(w * h)
            val opaqueBlack = 0xFF000000.toInt()
            residuals[0] = subArgbMod256(afterSubGreen[0], opaqueBlack)
            for (x in 1 until w) {
                residuals[x] = subArgbMod256(afterSubGreen[x], afterSubGreen[x - 1])
            }
            for (y in 1 until h) {
                residuals[y * w] = subArgbMod256(afterSubGreen[y * w], afterSubGreen[(y - 1) * w])
                for (x in 1 until w) {
                    val idx = y * w + x
                    val l = afterSubGreen[idx - 1]
                    val t = afterSubGreen[idx - w]
                    val tl = afterSubGreen[idx - w - 1]
                    val tr = afterSubGreen[idx - w + 1]
                    val pred = predictForward(x ushr 2, l, t, tl, tr)
                    residuals[idx] = subArgbMod256(afterSubGreen[idx], pred)
                }
            }

            // Encode bitstream: Transform order [CrossColor(1), SubtractGreen(2), Predictor(0)]
            // (Decoder inverts in reverse: Predictor -> SubtractGreen -> CrossColor)
            val bw = Vp8lBitWriter()
            bw.writeBits(1, 1); bw.writeBits(1, 2); bw.writeBits(0, 3) // CrossColor sizeBits=2
            writeFixed8ImageStream(bw, ctmTiles, isTopLevel = false)
            bw.writeBits(1, 1); bw.writeBits(2, 2) // SubtractGreen
            bw.writeBits(1, 1); bw.writeBits(0, 2); bw.writeBits(0, 3) // Predictor sizeBits=2
            writeFixed8ImageStream(bw, predModes, isTopLevel = false)
            bw.writeBits(0, 1) // end transforms
            writeFixed8ImageStream(bw, residuals, isTopLevel = true)

            val webp = wrapRiffWebp("VP8L" to wrapVp8lChunk(w, h, true, bw.finish()))
            assertArgbBufferMatches(orig, LibWebpCodec.decode(webp), w, h)
        }
    }

    @Test
    fun `LibWebpCodec decodes VP8L Meta-Huffman ColorCache LZ77 2D distances and ANMF frames`() {
        withPureKotlinWebpDecode {
            val w = 8
            val h = 2
            val px0 = 0xFF140A1E.toInt()
            val px1 = 0xFF283200.toInt()
            val key0 = (px0 * 0x1e35a7bd) ushr (32 - 3) // 0
            val key1 = (px1 * 0x1e35a7bd) ushr (32 - 3) // 7
            val cacheBase = 256 + 24

            val bw = Vp8lBitWriter()
            bw.writeBits(0, 1) // no transforms
            bw.writeBits(1, 1); bw.writeBits(3, 4) // colorCacheBits = 3 (size = 8)
            bw.writeBits(1, 1); bw.writeBits(0, 3) // useMeta = 1, huffmanBits = 2 -> 2x1 meta tiles
            bw.writeBits(0, 1) // no cache in meta
            writeSimpleTwoSymTree(bw, 0, 1) // meta green: group 0 or 1
            repeat(4) { writeSimpleOneSymTree(bw, 0) }
            bw.writeBits(0, 1) // tile 0 -> group 0
            bw.writeBits(1, 1) // tile 1 -> group 1

            // Group 0
            val g0 = writeSparsePowerOfTwoTree(
                bw, cacheBase + 8,
                intArrayOf(10, 50, 256, 257, cacheBase + key0, cacheBase + key1, 0, 1)
            )
            writeSimpleTwoSymTree(bw, 20, 40)   // R0
            writeSimpleTwoSymTree(bw, 30, 0)    // B0 (tests sym0 > sym1 canonical sorting!)
            writeSimpleOneSymTree(bw, 255)      // A0
            writeSimpleTwoSymTree(bw, 0, 2)     // D0: 0->distSym=1 (width=8), 2->distSym=3 (width+1=9)

            // Group 1
            val g1 = writeSparsePowerOfTwoTree(
                bw, cacheBase + 8,
                intArrayOf(cacheBase + key0, cacheBase + key1, 0, 1)
            )
            repeat(4) { writeSimpleOneSymTree(bw, 0) }

            fun emitCode(map: Map<Int, Pair<Int, Int>>, sym: Int) {
                val (code, len) = map.getValue(sym)
                bw.writeMsbBits(code, len)
            }

            // Row 0: px0, px1, cache[key0], cache[key1], cache[key0], cache[key1], cache[key0], cache[key1]
            emitCode(g0, 10); bw.writeBits(0, 1); bw.writeBits(1, 1) // px0 (R=20->0, B=30->1)
            emitCode(g0, 50); bw.writeBits(1, 1); bw.writeBits(0, 1) // px1 (R=40->1, B=0->0)
            emitCode(g0, cacheBase + key0)
            emitCode(g0, cacheBase + key1)
            emitCode(g1, cacheBase + key0)
            emitCode(g1, cacheBase + key1)
            emitCode(g1, cacheBase + key0)
            emitCode(g1, cacheBase + key1)

            // Row 1: LZ77(len=1, dy=1,dx=0 -> px0), LZ77(len=2, dy=1,dx=1 -> px0,px1), cache[key1], ...
            emitCode(g0, 256); bw.writeBits(0, 1)
            emitCode(g0, 257); bw.writeBits(1, 1)
            emitCode(g0, cacheBase + key1)
            emitCode(g1, cacheBase + key1)
            emitCode(g1, cacheBase + key0)
            emitCode(g1, cacheBase + key1)
            emitCode(g1, cacheBase + key0)

            val expected = intArrayOf(
                px0, px1, px0, px1, px0, px1, px0, px1,
                px0, px0, px1, px1, px1, px0, px1, px0
            )
            val vp8lChunk = wrapVp8lChunk(w, h, false, bw.finish())
            val directWebp = wrapRiffWebp("VP8L" to vp8lChunk)
            assertArgbBufferMatches(expected, LibWebpCodec.decode(directWebp), w, h)

            // Also verify VP8X + ANIM + ANMF animated container containing the same VP8L frame at (2, 0) on a 12x4 canvas
            val vp8x = byteArrayOf(
                0x02, 0x00, 0x00, 0x00, // animation flag
                (12 - 1).toByte(), 0x00, 0x00,
                (4 - 1).toByte(), 0x00, 0x00
            )
            val anim = ByteArray(6)
            val anmfHeader = byteArrayOf(
                1, 0, 0, // frameX / 2 = 1 -> x = 2
                0, 0, 0, // frameY / 2 = 0 -> y = 0
                (w - 1).toByte(), 0, 0,
                (h - 1).toByte(), 0, 0,
                50, 0, 0, // duration
                0x00
            )
            val anmfPayload = ArrayList<Byte>()
            for (b in anmfHeader) anmfPayload.add(b)
            for (c in "VP8L") anmfPayload.add(c.code.toByte())
            appendInt32LE(anmfPayload, vp8lChunk.size)
            for (b in vp8lChunk) anmfPayload.add(b)
            if ((vp8lChunk.size and 1) != 0) anmfPayload.add(0)

            val animWebp = wrapRiffWebp(
                "VP8X" to vp8x,
                "ANIM" to anim,
                "ANMF" to anmfPayload.toByteArray()
            )
            val animMeta = LibWebpCodec.probeWebpMetadata(animWebp)
            assertEquals(12, animMeta.width)
            assertEquals(4, animMeta.height)
            assertTrue(animMeta.isAnimated)
            val animBuf = LibWebpCodec.decode(animWebp)
            assertEquals(12, animBuf.width)
            assertEquals(4, animBuf.height)
            assertEquals(RgbaColor(20, 10, 30, 255), animBuf.getPixel(2, 0))
            assertEquals(RgbaColor(40, 50, 0, 255), animBuf.getPixel(3, 0))
            assertEquals(RgbaColor(0, 0, 0, 0), animBuf.getPixel(0, 0))
        }
    }

    @Test
    fun `LibWebpCodec decodes ALPH planes and fails fast on lossy VP8 unless native hook is set`() {
        // Test uncompressed ALPH with Gradient filter (method=0, filter=3 -> flags=0x0C)
        val aw = 4
        val ah = 3
        val expectedAlpha = byteArrayOf(
            10, 25, 40, 55,
            20, 45, 70, 90,
            35, 80, 120, 200.toByte()
        )
        val filtered = ByteArray(1 + aw * ah)
        filtered[0] = 0x0C // compression=0, filtering=3 (Gradient)
        for (y in 0 until ah) {
            for (x in 0 until aw) {
                val idx = y * aw + x
                val left = if (x > 0) expectedAlpha[idx - 1].toInt() and 0xFF else 0
                val top = if (y > 0) expectedAlpha[idx - aw].toInt() and 0xFF else 0
                val topLeft = if (x > 0 && y > 0) expectedAlpha[idx - aw - 1].toInt() and 0xFF else 0
                val pred = when {
                    x == 0 && y == 0 -> 0
                    y == 0 -> left
                    x == 0 -> top
                    else -> (left + top - topLeft).coerceIn(0, 255)
                }
                filtered[1 + idx] = (((expectedAlpha[idx].toInt() and 0xFF) - pred) and 0xFF).toByte()
            }
        }
        val decodedUncompressedAlpha = LibWebpCodec.decodeAlphaChunk(filtered, aw, ah)
        assertTrue(expectedAlpha.contentEquals(decodedUncompressedAlpha))

        // Test VP8L-compressed ALPH with Horizontal filter (compression=1, filtering=1 -> flags=0x05)
        val horizFiltered = IntArray(aw * ah)
        for (y in 0 until ah) {
            for (x in 0 until aw) {
                val idx = y * aw + x
                val cur = expectedAlpha[idx].toInt() and 0xFF
                val pred = when {
                    x > 0 -> expectedAlpha[idx - 1].toInt() and 0xFF
                    y > 0 -> expectedAlpha[idx - aw].toInt() and 0xFF
                    else -> 0
                }
                val raw = (cur - pred) and 0xFF
                horizFiltered[idx] = (0xFF shl 24) or (raw shl 8)
            }
        }
        val alphBw = Vp8lBitWriter()
        alphBw.writeBits(0, 1) // no transforms
        writeFixed8ImageStream(alphBw, horizFiltered, isTopLevel = true)
        val compressedBody = alphBw.finish()
        val compressedAlph = ByteArray(1 + compressedBody.size)
        compressedAlph[0] = 0x05 // compression=1, filtering=1 (Horizontal)
        compressedBody.copyInto(compressedAlph, 1)
        val decodedCompressedAlpha = LibWebpCodec.decodeAlphaChunk(compressedAlph, aw, ah)
        assertTrue(expectedAlpha.contentEquals(decodedCompressedAlpha))

        // Verify lossy VP8 throws UnsupportedOperationException without native hook, and uses hook when present
        val prevHook = LibWebpCodec.nativeLibWebpDecodeHook
        try {
            LibWebpCodec.nativeLibWebpDecodeHook = null
            val lossyMeta = LibWebpCodec.probeWebpMetadata(LOSSY_8X8)
            assertEquals(8, lossyMeta.width)
            assertEquals(8, lossyMeta.height)
            assertTrue(lossyMeta.hasAlpha)

            val ex = assertFailsWith<UnsupportedOperationException> {
                LibWebpCodec.decode(LOSSY_8X8)
            }
            assertTrue(
                ex.message?.contains("WebP VP8 lossy decoding requires an active native ImageCodecNativeImpl") == true
            )

            val sentinel = ImageBuffer(8, 8, PixelFormat.RGBA_8888).fill(RgbaColor(1, 2, 3, 255))
            LibWebpCodec.nativeLibWebpDecodeHook = { sentinel }
            assertSame(sentinel, LibWebpCodec.decode(LOSSY_8X8))
        } finally {
            LibWebpCodec.nativeLibWebpDecodeHook = prevHook
        }
    }

    private fun subArgbMod256(a: Int, b: Int): Int {
        val aa = (((a ushr 24) and 0xFF) - ((b ushr 24) and 0xFF)) and 0xFF
        val ar = (((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)) and 0xFF
        val ag = (((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)) and 0xFF
        val ab = ((a and 0xFF) - (b and 0xFF)) and 0xFF
        return (aa shl 24) or (ar shl 16) or (ag shl 8) or ab
    }

    private fun avg2(a: Int, b: Int): Int {
        val aa = (((a ushr 24) and 0xFF) + ((b ushr 24) and 0xFF)) ushr 1
        val ar = (((a ushr 16) and 0xFF) + ((b ushr 16) and 0xFF)) ushr 1
        val ag = (((a ushr 8) and 0xFF) + ((b ushr 8) and 0xFF)) ushr 1
        val ab = ((a and 0xFF) + (b and 0xFF)) ushr 1
        return (aa shl 24) or (ar shl 16) or (ag shl 8) or ab
    }

    private fun predictForward(mode: Int, l: Int, t: Int, tl: Int, tr: Int): Int = when (mode) {
        0 -> 0xFF000000.toInt()
        1 -> l
        2 -> t
        3 -> tr
        4 -> tl
        5 -> avg2(avg2(l, tr), t)
        6 -> avg2(l, tl)
        7 -> avg2(l, t)
        8 -> avg2(tl, t)
        9 -> avg2(t, tr)
        10 -> avg2(avg2(l, tl), avg2(t, tr))
        11 -> {
            val pa = abs(((t ushr 24) and 0xFF) - ((tl ushr 24) and 0xFF)) +
                abs(((t ushr 16) and 0xFF) - ((tl ushr 16) and 0xFF)) +
                abs(((t ushr 8) and 0xFF) - ((tl ushr 8) and 0xFF)) +
                abs((t and 0xFF) - (tl and 0xFF))
            val pb = abs(((l ushr 24) and 0xFF) - ((tl ushr 24) and 0xFF)) +
                abs(((l ushr 16) and 0xFF) - ((tl ushr 16) and 0xFF)) +
                abs(((l ushr 8) and 0xFF) - ((tl ushr 8) and 0xFF)) +
                abs((l and 0xFF) - (tl and 0xFF))
            if (pa < pb) l else t
        }
        12 -> {
            val aa = (((l ushr 24) and 0xFF) + ((t ushr 24) and 0xFF) - ((tl ushr 24) and 0xFF)).coerceIn(0, 255)
            val ar = (((l ushr 16) and 0xFF) + ((t ushr 16) and 0xFF) - ((tl ushr 16) and 0xFF)).coerceIn(0, 255)
            val ag = (((l ushr 8) and 0xFF) + ((t ushr 8) and 0xFF) - ((tl ushr 8) and 0xFF)).coerceIn(0, 255)
            val ab = ((l and 0xFF) + (t and 0xFF) - (tl and 0xFF)).coerceIn(0, 255)
            (aa shl 24) or (ar shl 16) or (ag shl 8) or ab
        }
        13 -> {
            val ave = avg2(l, t)
            val aa = (((ave ushr 24) and 0xFF) + (((ave ushr 24) and 0xFF) - ((tl ushr 24) and 0xFF)) / 2).coerceIn(0, 255)
            val ar = (((ave ushr 16) and 0xFF) + (((ave ushr 16) and 0xFF) - ((tl ushr 16) and 0xFF)) / 2).coerceIn(0, 255)
            val ag = (((ave ushr 8) and 0xFF) + (((ave ushr 8) and 0xFF) - ((tl ushr 8) and 0xFF)) / 2).coerceIn(0, 255)
            val ab = ((ave and 0xFF) + ((ave and 0xFF) - (tl and 0xFF)) / 2).coerceIn(0, 255)
            (aa shl 24) or (ar shl 16) or (ag shl 8) or ab
        }
        else -> error("Unknown mode $mode")
    }

    companion object {
        private val SOLID_1X1 = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(), 0x18.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte(), 0x56.toByte(), 0x50.toByte(), 0x38.toByte(), 0x4C.toByte(),
            0x0C.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x2F.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x10.toByte(), 0xA8.toByte(), 0x48.toByte(), 0x19.toByte(), 0x8A.toByte(), 0x53.toByte(), 0xE4.toByte(), 0x00.toByte()
        )

        private val PAL16_17X13 = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(), 0xD6.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte(), 0x56.toByte(), 0x50.toByte(), 0x38.toByte(), 0x4C.toByte(),
            0xC9.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x2F.toByte(), 0x10.toByte(), 0x00.toByte(), 0x03.toByte(), 0x10.toByte(), 0x7F.toByte(), 0xA0.toByte(), 0x90.toByte(), 0x8D.toByte(), 0x24.toByte(), 0xE8.toByte(), 0x1E.toByte(),
            0xCA.toByte(), 0xF3.toByte(), 0x58.toByte(), 0xE5.toByte(), 0x9A.toByte(), 0x1C.toByte(), 0x84.toByte(), 0x99.toByte(), 0xB6.toByte(), 0x6D.toByte(), 0xF8.toByte(), 0xE3.toByte(), 0xEB.toByte(), 0x36.toByte(), 0xDF.toByte(), 0xE3.toByte(),
            0xD1.toByte(), 0x83.toByte(), 0x30.toByte(), 0xDB.toByte(), 0x08.toByte(), 0xE9.toByte(), 0xFC.toByte(), 0x69.toByte(), 0x4E.toByte(), 0x60.toByte(), 0xBF.toByte(), 0x0A.toByte(), 0x08.toByte(), 0x04.toByte(), 0x08.toByte(), 0x7F.toByte(),
            0x7A.toByte(), 0xF5.toByte(), 0xE9.toByte(), 0xC1.toByte(), 0xFE.toByte(), 0x46.toByte(), 0x30.toByte(), 0x8D.toByte(), 0xD7.toByte(), 0xE5.toByte(), 0x41.toByte(), 0x94.toByte(), 0x06.toByte(), 0x44.toByte(), 0x69.toByte(), 0x00.toByte(),
            0x8A.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(), 0x9C.toByte(), 0xB3.toByte(), 0x6D.toByte(), 0x9B.toByte(), 0xC9.toByte(), 0x46.toByte(), 0x9C.toByte(), 0x6D.toByte(), 0xDB.toByte(), 0xB6.toByte(), 0x6D.toByte(), 0x56.toByte(),
            0xDB.toByte(), 0x5B.toByte(), 0x5A.toByte(), 0x76.toByte(), 0x67.toByte(), 0xB2.toByte(), 0x92.toByte(), 0x6D.toByte(), 0xEB.toByte(), 0x21.toByte(), 0x11.toByte(), 0xFD.toByte(), 0x8F.toByte(), 0x31.toByte(), 0xC4.toByte(), 0x85.toByte(),
            0xCA.toByte(), 0xEF.toByte(), 0xAD.toByte(), 0x65.toByte(), 0x03.toByte(), 0x7F.toByte(), 0x7A.toByte(), 0x9F.toByte(), 0xBC.toByte(), 0x39.toByte(), 0x8F.toByte(), 0xB3.toByte(), 0x61.toByte(), 0xE1.toByte(), 0x97.toByte(), 0x4A.toByte(),
            0x2C.toByte(), 0x84.toByte(), 0x75.toByte(), 0x55.toByte(), 0xAF.toByte(), 0x07.toByte(), 0x0F.toByte(), 0xBB.toByte(), 0x87.toByte(), 0x51.toByte(), 0x91.toByte(), 0xDC.toByte(), 0xC9.toByte(), 0x1D.toByte(), 0xD0.toByte(), 0x2A.toByte(),
            0xE3.toByte(), 0xB8.toByte(), 0xD4.toByte(), 0x32.toByte(), 0xE7.toByte(), 0x25.toByte(), 0x80.toByte(), 0x38.toByte(), 0x75.toByte(), 0x7E.toByte(), 0xBD.toByte(), 0x70.toByte(), 0x5E.toByte(), 0x1C.toByte(), 0x2D.toByte(), 0xD0.toByte(),
            0x92.toByte(), 0x80.toByte(), 0xFB.toByte(), 0x1D.toByte(), 0x5C.toByte(), 0x41.toByte(), 0x1C.toByte(), 0x0C.toByte(), 0x81.toByte(), 0x39.toByte(), 0x7E.toByte(), 0x98.toByte(), 0x11.toByte(), 0x08.toByte(), 0xEB.toByte(), 0x56.toByte(),
            0x60.toByte(), 0x31.toByte(), 0xF0.toByte(), 0xD4.toByte(), 0x72.toByte(), 0xE4.toByte(), 0x89.toByte(), 0xD7.toByte(), 0x0A.toByte(), 0xB8.toByte(), 0xBA.toByte(), 0x3B.toByte(), 0xFB.toByte(), 0x3F.toByte(), 0x6E.toByte(), 0xF0.toByte(),
            0xF6.toByte(), 0x06.toByte(), 0xB4.toByte(), 0x35.toByte(), 0xDA.toByte(), 0x7F.toByte(), 0xB6.toByte(), 0x2B.toByte(), 0xF3.toByte(), 0x9D.toByte(), 0x85.toByte(), 0x47.toByte(), 0x7A.toByte(), 0x73.toByte(), 0x6D.toByte(), 0x21.toByte(),
            0xAE.toByte(), 0xCF.toByte(), 0x5D.toByte(), 0xB8.toByte(), 0xA2.toByte(), 0x7D.toByte(), 0x07.toByte(), 0x31.toByte(), 0xCB.toByte(), 0x29.toByte(), 0x30.toByte(), 0x07.toByte(), 0x06.toByte(), 0x00.toByte()
        )

        private val PAL32_19X17 = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(), 0x1E.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte(), 0x56.toByte(), 0x50.toByte(), 0x38.toByte(), 0x4C.toByte(),
            0x11.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x2F.toByte(), 0x12.toByte(), 0x00.toByte(), 0x04.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xA0.toByte(), 0xA0.toByte(), 0x6D.toByte(), 0x1B.toByte(), 0x46.toByte(), 0x0A.toByte(),
            0x7F.toByte(), 0x36.toByte(), 0x55.toByte(), 0x01.toByte(), 0xB6.toByte(), 0x3B.toByte(), 0x18.toByte(), 0x88.toByte(), 0xB4.toByte(), 0x6D.toByte(), 0x6C.toByte(), 0xCC.toByte(), 0xBF.toByte(), 0xB3.toByte(), 0xA3.toByte(), 0x7B.toByte(),
            0x28.toByte(), 0x22.toByte(), 0x93.toByte(), 0xB6.toByte(), 0xA1.toByte(), 0xFE.toByte(), 0x45.toByte(), 0xCE.toByte(), 0x45.toByte(), 0xF6.toByte(), 0xCE.toByte(), 0xBF.toByte(), 0xDA.toByte(), 0xB6.toByte(), 0x6D.toByte(), 0x18.toByte(),
            0xF7.toByte(), 0x6C.toByte(), 0xB9.toByte(), 0x83.toByte(), 0x16.toByte(), 0x78.toByte(), 0xB7.toByte(), 0x52.toByte(), 0x0F.toByte(), 0x78.toByte(), 0xAF.toByte(), 0xFC.toByte(), 0x72.toByte(), 0x13.toByte(), 0x80.toByte(), 0x81.toByte(),
            0x24.toByte(), 0x49.toByte(), 0x4D.toByte(), 0xF3.toByte(), 0x20.toByte(), 0x07.toByte(), 0xE8.toByte(), 0x49.toByte(), 0xCE.toByte(), 0x99.toByte(), 0x88.toByte(), 0xFE.toByte(), 0xC7.toByte(), 0x52.toByte(), 0x87.toByte(), 0xD8.toByte(),
            0x3E.toByte(), 0x87.toByte(), 0x08.toByte(), 0x5D.toByte(), 0xE3.toByte(), 0x0C.toByte(), 0xED.toByte(), 0x15.toByte(), 0x98.toByte(), 0x67.toByte(), 0xF9.toByte(), 0x21.toByte(), 0x09.toByte(), 0x94.toByte(), 0xEC.toByte(), 0x05.toByte(),
            0x52.toByte(), 0xFF.toByte(), 0x46.toByte(), 0xF8.toByte(), 0x82.toByte(), 0xBD.toByte(), 0x31.toByte(), 0x32.toByte(), 0xF7.toByte(), 0x07.toByte(), 0xF8.toByte(), 0xA6.toByte(), 0x33.toByte(), 0x7A.toByte(), 0xE3.toByte(), 0x52.toByte(),
            0xCD.toByte(), 0xAE.toByte(), 0x91.toByte(), 0xD3.toByte(), 0x17.toByte(), 0xED.toByte(), 0x15.toByte(), 0x72.toByte(), 0x75.toByte(), 0x23.toByte(), 0x67.toByte(), 0x63.toByte(), 0x01.toByte(), 0x88.toByte(), 0xAD.toByte(), 0xBA.toByte(),
            0xB1.toByte(), 0x40.toByte(), 0x79.toByte(), 0x3A.toByte(), 0x79.toByte(), 0x6A.toByte(), 0xBE.toByte(), 0x4E.toByte(), 0xDD.toByte(), 0xDE.toByte(), 0x70.toByte(), 0xD4.toByte(), 0x35.toByte(), 0x94.toByte(), 0x94.toByte(), 0x9B.toByte(),
            0x76.toByte(), 0x8D.toByte(), 0x6D.toByte(), 0x70.toByte(), 0x91.toByte(), 0xE6.toByte(), 0xB4.toByte(), 0xF0.toByte(), 0xE6.toByte(), 0xC4.toByte(), 0x57.toByte(), 0x31.toByte(), 0xD1.toByte(), 0x58.toByte(), 0xE6.toByte(), 0x6D.toByte(),
            0x06.toByte(), 0x99.toByte(), 0x9F.toByte(), 0xF5.toByte(), 0xC4.toByte(), 0x47.toByte(), 0x45.toByte(), 0x00.toByte(), 0x29.toByte(), 0x3F.toByte(), 0x83.toByte(), 0xCA.toByte(), 0x59.toByte(), 0xAF.toByte(), 0xD0.toByte(), 0x19.toByte(),
            0x3F.toByte(), 0x57.toByte(), 0x97.toByte(), 0x83.toByte(), 0xF1.toByte(), 0x52.toByte(), 0x68.toByte(), 0x33.toByte(), 0xD7.toByte(), 0xE0.toByte(), 0x4D.toByte(), 0x6C.toByte(), 0xBE.toByte(), 0x1D.toByte(), 0xC7.toByte(), 0xA3.toByte(),
            0xAE.toByte(), 0x31.toByte(), 0x44.toByte(), 0xE3.toByte(), 0x4A.toByte(), 0xDD.toByte(), 0x1E.toByte(), 0xB0.toByte(), 0x1E.toByte(), 0x9A.toByte(), 0x5F.toByte(), 0xCF.toByte(), 0x57.toByte(), 0x5B.toByte(), 0xD1.toByte(), 0x62.toByte(),
            0x07.toByte(), 0xC2.toByte(), 0xC2.toByte(), 0xCE.toByte(), 0x8A.toByte(), 0x70.toByte(), 0x3C.toByte(), 0x94.toByte(), 0x5E.toByte(), 0x4E.toByte(), 0x6F.toByte(), 0x73.toByte(), 0xE1.toByte(), 0xEA.toByte(), 0x4C.toByte(), 0x05.toByte(),
            0xB4.toByte(), 0x19.toByte(), 0xB4.toByte(), 0x6D.toByte(), 0x9B.toByte(), 0x79.toByte(), 0xCF.toByte(), 0xED.toByte(), 0xEB.toByte(), 0x23.toByte(), 0x03.toByte(), 0x2D.toByte(), 0xB7.toByte(), 0xCF.toByte(), 0x80.toByte(), 0x70.toByte(),
            0xBE.toByte(), 0xFA.toByte(), 0xA1.toByte(), 0xE5.toByte(), 0x26.toByte(), 0x7B.toByte(), 0x71.toByte(), 0x60.toByte(), 0x9E.toByte(), 0xB7.toByte(), 0x44.toByte(), 0x15.toByte(), 0x3A.toByte(), 0x5F.toByte(), 0xD0.toByte(), 0xE9.toByte(),
            0xB9.toByte(), 0xCD.toByte(), 0x52.toByte(), 0xB3.toByte(), 0xF9.toByte(), 0x17.toByte(), 0x11.toByte(), 0x9D.toByte(), 0x4D.toByte(), 0x69.toByte(), 0x9A.toByte(), 0x40.toByte(), 0x3D.toByte(), 0x73.toByte(), 0x0F.toByte(), 0x83.toByte(),
            0x92.toByte(), 0x6B.toByte(), 0x4D.toByte(), 0x97.toByte(), 0x39.toByte(), 0x00.toByte()
        )

        private val GRAD_40X40 = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte(), 0x56.toByte(), 0x50.toByte(), 0x38.toByte(), 0x4C.toByte(),
            0x97.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x2F.toByte(), 0x27.toByte(), 0xC0.toByte(), 0x09.toByte(), 0x10.toByte(), 0xCD.toByte(), 0x65.toByte(), 0x44.toByte(), 0xFF.toByte(), 0x63.toByte(), 0x57.toByte(), 0xF8.toByte(),
            0x88.toByte(), 0xFE.toByte(), 0x07.toByte(), 0x98.toByte(), 0xB6.toByte(), 0x91.toByte(), 0x24.toByte(), 0x69.toByte(), 0x4D.toByte(), 0x26.toByte(), 0xFF.toByte(), 0x94.toByte(), 0xEF.toByte(), 0xBF.toByte(), 0x2B.toByte(), 0x74.toByte(),
            0x10.toByte(), 0x6D.toByte(), 0xDB.toByte(), 0xB6.toByte(), 0x49.toByte(), 0xD2.toByte(), 0xF2.toByte(), 0xFF.toByte(), 0x87.toByte(), 0x35.toByte(), 0x28.toByte(), 0x68.toByte(), 0xDB.toByte(), 0x86.toByte(), 0xD9.toByte(), 0x31.toByte(),
            0xF4.toByte(), 0x00.toByte(), 0x17.toByte(), 0x08.toByte(), 0x24.toByte(), 0x99.toByte(), 0xCE.toByte(), 0xB6.toByte(), 0xA9.toByte(), 0xDA.toByte(), 0xB6.toByte(), 0x6D.toByte(), 0x98.toByte(), 0xC1.toByte(), 0x29.toByte(), 0x44.toByte(),
            0xF2.toByte(), 0xC8.toByte(), 0x9A.toByte(), 0x19.toByte(), 0x01.toByte(), 0xE0.toByte(), 0xC4.toByte(), 0x27.toByte(), 0x55.toByte(), 0xC1.toByte(), 0x13.toByte(), 0x55.toByte(), 0xD0.toByte(), 0x44.toByte(), 0x15.toByte(), 0x98.toByte(),
            0xFC.toByte(), 0xF7.toByte(), 0x98.toByte(), 0x64.toByte(), 0x4C.toByte(), 0x32.toByte(), 0xA6.toByte(), 0x27.toByte(), 0xAA.toByte(), 0x64.toByte(), 0x4C.toByte(), 0x41.toByte(), 0xC5.toByte(), 0x04.toByte(), 0x26.toByte(), 0x55.toByte(),
            0xC1.toByte(), 0x11.toByte(), 0x13.toByte(), 0x5C.toByte(), 0x31.toByte(), 0x8D.toByte(), 0x36.toByte(), 0xAE.toByte(), 0x12.toByte(), 0x31.toByte(), 0x05.toByte(), 0x15.toByte(), 0x13.toByte(), 0xD8.toByte(), 0x39.toByte(), 0x4A.toByte(),
            0xC4.toByte(), 0x04.toByte(), 0x1F.toByte(), 0xDC.toByte(), 0x5C.toByte(), 0x1E.toByte(), 0xDC.toByte(), 0xE4.toByte(), 0xE0.toByte(), 0xE4.toByte(), 0xBB.toByte(), 0x62.toByte(), 0x02.toByte(), 0x27.toByte(), 0x4E.toByte(), 0x12.toByte(),
            0x31.toByte(), 0xC1.toByte(), 0x15.toByte(), 0xD3.toByte(), 0xE8.toByte(), 0xCC.toByte(), 0x45.toByte(), 0x22.toByte(), 0xA6.toByte(), 0xA0.toByte(), 0x62.toByte(), 0x02.toByte(), 0x17.toByte(), 0x0E.toByte(), 0x12.toByte(), 0x31.toByte(),
            0xC1.toByte(), 0x15.toByte(), 0xD3.toByte(), 0xE8.toByte(), 0xCA.toByte(), 0xDF.toByte(), 0x73.toByte(), 0xE5.toByte(), 0xE8.toByte(), 0x1B.toByte(), 0x01.toByte(), 0x00.toByte()
        )

        private val LOSSY_8X8 = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(), 0x72.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte(), 0x56.toByte(), 0x50.toByte(), 0x38.toByte(), 0x58.toByte(),
            0x0A.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x10.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x07.toByte(), 0x00.toByte(), 0x00.toByte(), 0x07.toByte(), 0x00.toByte(), 0x00.toByte(), 0x41.toByte(), 0x4C.toByte(),
            0x50.toByte(), 0x48.toByte(), 0x0E.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x0F.toByte(), 0xF0.toByte(), 0xD0.toByte(), 0xFF.toByte(), 0x88.toByte(), 0x88.toByte(), 0xF0.toByte(), 0x08.toByte(), 0xC4.toByte(),
            0x88.toByte(), 0xFE.toByte(), 0x07.toByte(), 0x38.toByte(), 0x56.toByte(), 0x50.toByte(), 0x38.toByte(), 0x20.toByte(), 0x3E.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xD0.toByte(), 0x01.toByte(), 0x00.toByte(), 0x9D.toByte(),
            0x01.toByte(), 0x2A.toByte(), 0x08.toByte(), 0x00.toByte(), 0x08.toByte(), 0x00.toByte(), 0x02.toByte(), 0x00.toByte(), 0x34.toByte(), 0x25.toByte(), 0xB0.toByte(), 0x02.toByte(), 0x74.toByte(), 0x01.toByte(), 0x0E.toByte(), 0xBB.toByte(),
            0x05.toByte(), 0xEF.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFE.toByte(), 0xFC.toByte(), 0x03.toByte(), 0xFC.toByte(), 0x0A.toByte(), 0x7F.toByte(), 0xBA.toByte(), 0xDF.toByte(), 0x87.toByte(), 0x57.toByte(), 0x81.toByte(), 0xFC.toByte(),
            0xCC.toByte(), 0xB8.toByte(), 0x2C.toByte(), 0x98.toByte(), 0x22.toByte(), 0xDB.toByte(), 0x39.toByte(), 0x5A.toByte(), 0xFF.toByte(), 0xFC.toByte(), 0x54.toByte(), 0x4F.toByte(), 0x22.toByte(), 0xBE.toByte(), 0x7F.toByte(), 0xA6.toByte(),
            0xCF.toByte(), 0xE4.toByte(), 0x57.toByte(), 0xCF.toByte(), 0xE0.toByte(), 0x27.toByte(), 0x70.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )
    }
}
