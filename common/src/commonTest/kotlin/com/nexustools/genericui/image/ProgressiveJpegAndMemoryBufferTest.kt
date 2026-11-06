package com.nexustools.genericui.image

import com.nexustools.genericui.component.content.ImageView
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.image.cache.ImageCacheManager
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProgressiveJpegAndMemoryBufferTest {

    private class BitPacker {
        private val bytes = ArrayList<Byte>()
        private var bitBuf = 0
        private var bitCount = 0

        fun writeBits(value: Int, numBits: Int) {
            for (i in (numBits - 1) downTo 0) {
                val bit = (value ushr i) and 1
                bitBuf = (bitBuf shl 1) or bit
                bitCount++
                if (bitCount == 8) {
                    emitByteWithStuffing(bitBuf)
                    bitBuf = 0
                    bitCount = 0
                }
            }
        }

        fun writeSignedValue(v: Int, s: Int) {
            if (s <= 0) return
            val bits = if (v > 0) v else (v + (1 shl s) - 1)
            writeBits(bits, s)
        }

        fun flushToByteArray(): ByteArray {
            if (bitCount > 0) {
                val pad = 8 - bitCount
                val padded = (bitBuf shl pad) or ((1 shl pad) - 1)
                emitByteWithStuffing(padded)
                bitBuf = 0
                bitCount = 0
            }
            return bytes.toByteArray()
        }

        private fun emitByteWithStuffing(b: Int) {
            val clean = b and 0xFF
            bytes.add(clean.toByte())
            if (clean == 0xFF) {
                bytes.add(0x00)
            }
        }
    }

    private fun buildTestJpeg(progressive: Boolean): ByteArray {
        val out = ArrayList<Byte>()
        fun emit(b: Int) {
            out.add((b and 0xFF).toByte())
        }
        fun emit16(v: Int) {
            emit(v ushr 8)
            emit(v)
        }
        fun emitBytes(arr: ByteArray) {
            for (b in arr) out.add(b)
        }

        // SOI
        emit(0xFF); emit(0xD8)

        // DQT (Table 0: all 64 quant entries = 4)
        emit(0xFF); emit(0xDB); emit16(2 + 1 + 64)
        emit(0x00) // pq=0 (8-bit), tq=0
        for (i in 0 until 64) emit(4)

        // SOF0 (0xC0) or SOF2 (0xC2): 16x8, 3 components (Y=1, Cb=2, Cr=3), 1x1 sampling
        emit(0xFF); emit(if (progressive) 0xC2 else 0xC0); emit16(17)
        emit(8) // precision
        emit16(8) // height = 8
        emit16(16) // width = 16
        emit(3) // 3 components
        emit(1); emit(0x11); emit(0)
        emit(2); emit(0x11); emit(0)
        emit(3); emit(0x11); emit(0)

        // DHT:
        // DC table 0 (tc=0, th=0): 6 symbols (0..5) at length 3 -> codes 000..101
        emit(0xFF); emit(0xC4); emit16(2 + 1 + 16 + 6)
        emit(0x00)
        for (len in 1..16) emit(if (len == 3) 6 else 0)
        for (sym in 0..5) emit(sym)

        // AC table 0 (tc=1, th=0): 8 symbols at length 4 -> codes 0000..0111
        val acSyms = intArrayOf(0x00, 0x01, 0x02, 0x03, 0x10, 0x11, 0x12, 0x13)
        emit(0xFF); emit(0xC4); emit16(2 + 1 + 16 + acSyms.size)
        emit(0x10)
        for (len in 1..16) emit(if (len == 4) acSyms.size else 0)
        for (sym in acSyms) emit(sym)

        fun dcCode(cat: Int): Int = cat // 3-bit code 0..5
        fun acCode(rs: Int): Int = acSyms.indexOf(rs) // 4-bit code 0..7

        if (!progressive) {
            // Single interleaved Baseline scan (Ns=3, Ss=0, Se=63, Ah=0, Al=0)
            emit(0xFF); emit(0xDA); emit16(12)
            emit(3)
            emit(1); emit(0x00)
            emit(2); emit(0x00)
            emit(3); emit(0x00)
            emit(0); emit(63); emit(0)

            val bits = BitPacker()
            // MCU 0:
            // Y0: DC = +13 (s=4), AC[1] = +7 (0x03), AC[2] = +1 (0x01), EOB0 (0x00)
            bits.writeBits(dcCode(4), 3); bits.writeSignedValue(13, 4)
            bits.writeBits(acCode(0x03), 4); bits.writeSignedValue(7, 3)
            bits.writeBits(acCode(0x01), 4); bits.writeSignedValue(1, 1)
            bits.writeBits(acCode(0x00), 4)
            // Cb0: DC = -4 (s=3), EOB0
            bits.writeBits(dcCode(3), 3); bits.writeSignedValue(-4, 3)
            bits.writeBits(acCode(0x00), 4)
            // Cr0: DC = +10 (s=4), EOB0
            bits.writeBits(dcCode(4), 3); bits.writeSignedValue(10, 4)
            bits.writeBits(acCode(0x00), 4)

            // MCU 1:
            // Y1: DC = +19 (diff = +6, s=3), AC[2] = -5 (r=1, s=3 -> 0x13), EOB0
            bits.writeBits(dcCode(3), 3); bits.writeSignedValue(6, 3)
            bits.writeBits(acCode(0x13), 4); bits.writeSignedValue(-5, 3)
            bits.writeBits(acCode(0x00), 4)
            // Cb1: DC = -4 (diff = 0, s=0), EOB0
            bits.writeBits(dcCode(0), 3)
            bits.writeBits(acCode(0x00), 4)
            // Cr1: DC = +10 (diff = 0, s=0), EOB0
            bits.writeBits(dcCode(0), 3)
            bits.writeBits(acCode(0x00), 4)

            emitBytes(bits.flushToByteArray())
        } else {
            // Scan 1: Interleaved DC First (Ns=3, Ss=0, Se=0, Ah=0, Al=1)
            emit(0xFF); emit(0xDA); emit16(12)
            emit(3)
            emit(1); emit(0x00)
            emit(2); emit(0x00)
            emit(3); emit(0x00)
            emit(0); emit(0); emit(0x01)
            val dcFirst = BitPacker()
            // MCU 0: Y0=6 (s=3), Cb0=-2 (s=2), Cr0=5 (s=3)
            dcFirst.writeBits(dcCode(3), 3); dcFirst.writeSignedValue(6, 3)
            dcFirst.writeBits(dcCode(2), 3); dcFirst.writeSignedValue(-2, 2)
            dcFirst.writeBits(dcCode(3), 3); dcFirst.writeSignedValue(5, 3)
            // MCU 1: Y1=9 (diff=+3, s=2), Cb1=-2 (diff=0), Cr1=5 (diff=0)
            dcFirst.writeBits(dcCode(2), 3); dcFirst.writeSignedValue(3, 2)
            dcFirst.writeBits(dcCode(0), 3)
            dcFirst.writeBits(dcCode(0), 3)
            emitBytes(dcFirst.flushToByteArray())

            // Scan 2: Interleaved DC Refine (Ns=3, Ss=0, Se=0, Ah=1, Al=0)
            emit(0xFF); emit(0xDA); emit16(12)
            emit(3)
            emit(1); emit(0x00)
            emit(2); emit(0x00)
            emit(3); emit(0x00)
            emit(0); emit(0); emit(0x10)
            val dcRefine = BitPacker()
            // MCU 0: Y0 bit=1 (12->13), Cb0 bit=0, Cr0 bit=0
            dcRefine.writeBits(1, 1)
            dcRefine.writeBits(0, 1)
            dcRefine.writeBits(0, 1)
            // MCU 1: Y1 bit=1 (18->19), Cb1 bit=0, Cr1 bit=0
            dcRefine.writeBits(1, 1)
            dcRefine.writeBits(0, 1)
            dcRefine.writeBits(0, 1)
            emitBytes(dcRefine.flushToByteArray())

            // Scan 3: Non-interleaved Y AC First (Ns=1, comp=1, Ss=1, Se=63, Ah=0, Al=1)
            emit(0xFF); emit(0xDA); emit16(8)
            emit(1); emit(1); emit(0x00)
            emit(1); emit(63); emit(0x01)
            val yAcFirst = BitPacker()
            // Block 0: AC[1] >> 1 = +3 (r=0, s=2 -> 0x02), EOB0
            yAcFirst.writeBits(acCode(0x02), 4); yAcFirst.writeSignedValue(3, 2)
            yAcFirst.writeBits(acCode(0x00), 4)
            // Block 1: AC[2] >> 1 = -2 (r=1, s=2 -> 0x12), EOB0
            yAcFirst.writeBits(acCode(0x12), 4); yAcFirst.writeSignedValue(-2, 2)
            yAcFirst.writeBits(acCode(0x00), 4)
            emitBytes(yAcFirst.flushToByteArray())

            // Scan 4: Non-interleaved Y AC Refine (Ns=1, comp=1, Ss=1, Se=63, Ah=1, Al=0)
            emit(0xFF); emit(0xDA); emit16(8)
            emit(1); emit(1); emit(0x00)
            emit(1); emit(63); emit(0x10)
            val yAcRefine = BitPacker()
            // Block 0: k=1 is non-zero (6 -> 7, corrBit=1), k=2 is newly +1 (r=0, s=1, sign=1), then EOB0
            yAcRefine.writeBits(acCode(0x01), 4)
            yAcRefine.writeBits(1, 1) // sign bit for newCoeff at k=2 (+1)
            yAcRefine.writeBits(1, 1) // correction bit for existing non-zero at k=1 (6 -> 7)
            yAcRefine.writeBits(acCode(0x00), 4) // EOB0 for rest of Block 0
            // Block 1: EOB0, plus correction bit 1 for existing non-zero at k=2 (-4 -> -5)
            yAcRefine.writeBits(acCode(0x00), 4)
            yAcRefine.writeBits(1, 1) // correction bit for k=2 (-4 -> -5)
            emitBytes(yAcRefine.flushToByteArray())

            // Scan 5: Non-interleaved Cb AC First (Ns=1, comp=2, Ss=1, Se=63, Ah=0, Al=0)
            emit(0xFF); emit(0xDA); emit16(8)
            emit(1); emit(2); emit(0x00)
            emit(1); emit(63); emit(0x00)
            val cbAcFirst = BitPacker()
            // EOB1 (0x10) + extra bit 0 -> run of 2 blocks
            cbAcFirst.writeBits(acCode(0x10), 4); cbAcFirst.writeBits(0, 1)
            emitBytes(cbAcFirst.flushToByteArray())

            // Scan 6: Non-interleaved Cr AC First (Ns=1, comp=3, Ss=1, Se=63, Ah=0, Al=0)
            emit(0xFF); emit(0xDA); emit16(8)
            emit(1); emit(3); emit(0x00)
            emit(1); emit(63); emit(0x00)
            val crAcFirst = BitPacker()
            crAcFirst.writeBits(acCode(0x10), 4); crAcFirst.writeBits(0, 1)
            emitBytes(crAcFirst.flushToByteArray())
        }

        // EOI
        emit(0xFF); emit(0xD9)
        return out.toByteArray()
    }

    @Test
    fun `LibJpegCodec decodes multi-scan Progressive JPEG SOF2 matching Baseline JPEG SOF0`() {
        val baselineBytes = buildTestJpeg(progressive = false)
        val progressiveBytes = buildTestJpeg(progressive = true)

        val metaProg = LibJpegCodec.probeJpegMetadata(progressiveBytes)
        assertEquals(16, metaProg.width)
        assertEquals(8, metaProg.height)
        assertEquals(ImageFormat.JPEG, metaProg.format)

        val baselineDecoded = LibJpegCodec.decodePureKotlin(baselineBytes)
        val progressiveDecoded = LibJpegCodec.decodePureKotlin(progressiveBytes)

        assertEquals(16, progressiveDecoded.width)
        assertEquals(8, progressiveDecoded.height)

        // Verify progressive JPEG pixels match baseline JPEG pixels across both 8x8 MCUs
        for (y in 0 until 8) {
            for (x in 0 until 16) {
                val expected = baselineDecoded.getPixel(x, y)
                val actual = progressiveDecoded.getPixel(x, y)
                assertEquals(expected, actual, "Mismatch at ($x, $y)")
            }
        }

        // Verify spatial variation across AC coefficients and between MCU 0 and MCU 1
        assertNotEquals(progressiveDecoded.getPixel(0, 0), progressiveDecoded.getPixel(7, 0))
        assertNotEquals(progressiveDecoded.getPixel(0, 0), progressiveDecoded.getPixel(8, 0))
    }

    @Test
    fun `ImageLoader resolves registered memory buffers immediately without network fetches`() {
        val buf = ImageBuffer(6, 4, PixelFormat.RGBA_8888)
        buf.fill(RgbaColor(12, 180, 240, 1f))
        buf.setPixel(3, 2, RgbaColor(250, 40, 90, 1f))

        val uri = "memory://imagebuffer/${buf.width}x${buf.height}@${buf.hashCode()}"
        ImageLoader.registerMemoryBuffer(uri, buf)

        var networkCalled = false
        val prevFetcher = ImageCacheManager.urlFetcherHook
        try {
            ImageCacheManager.urlFetcherHook = {
                networkCalled = true
                null
            }

            val resolved = ImageLoader.resolveMemoryBuffer(uri)
            assertNotNull(resolved)
            assertSame(buf, resolved)

            val loaded = ImageLoader.load(ImageSource.fromUrl(uri))
            assertEquals(6, loaded.width)
            assertEquals(4, loaded.height)
            assertEquals(RgbaColor(250, 40, 90, 1f), loaded.getPixel(3, 2))
            assertTrue(!networkCalled, "Memory buffer load must not trigger network fetch")
        } finally {
            ImageCacheManager.urlFetcherHook = prevFetcher
        }
    }

    @Test
    fun `ImageView setImageBuffer registers memory URI and resolves via ImageLoader`() {
        val buf = ImageBuffer(10, 8, PixelFormat.RGBA_8888).fill(RgbaColor(64, 128, 255, 1f))

        val imageView = ImageView()
        imageView.setImageBuffer(buf)

        assertSame(buf, imageView.imageBuffer)
        assertNotNull(imageView.sourceUrl)
        assertTrue(imageView.sourceUrl!!.startsWith("memory://imagebuffer/10x8@"))
        assertSame(buf, ImageLoader.resolveMemoryBuffer(imageView.sourceUrl))
        val loadedFromMemoryUri = ImageLoader.load(ImageSource.fromUrl(imageView.sourceUrl!!))
        assertEquals(10, loadedFromMemoryUri.width)
        assertEquals(8, loadedFromMemoryUri.height)
        assertEquals(RgbaColor(64, 128, 255, 1f), loadedFromMemoryUri.getPixel(5, 4))
    }
}
