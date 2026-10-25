package com.nexustools.genericui.util

import com.nexustools.genericui.component.system.PreferenceStore
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.image.cache.ImageCacheManager
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.image.codec.LibPngCodec
import com.nexustools.genericui.style.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDirectoryAndImageCacheTest {

    @Test
    fun `ConfigDirectory and CacheDirectory hierarchy manages per-key and cached files`() {
        GenericUI.applicationName = "DirectoryTestApp"
        val configDir = ConfigDirectory(appName = "DirectoryTestApp", subdirectory = "settings")
        val cacheDir = CacheDirectory(appName = "DirectoryTestApp", subdirectory = "images")

        assertTrue(cacheDir is ConfigDirectory)
        assertTrue(configDir is UserDirectory)
        assertTrue(configDir.ensureExists())
        assertTrue(cacheDir.ensureExists())

        assertTrue(configDir.writeKeyBytes("window_state", ".bson", byteArrayOf(1, 2, 3, 4)))
        assertTrue(configDir.containsKey("window_state", ".bson"))
        assertEquals(setOf("window_state"), configDir.keysForExtension(".bson"))
        val readKey = configDir.readKeyBytes("window_state", ".bson")
        assertNotNull(readKey)
        assertEquals(4, readKey.size)

        val url = "https://cdn.example.com/sprites/hero.png?v=2"
        val savedPath = cacheDir.putCachedBytes(url, byteArrayOf(10, 20, 30))
        assertTrue(savedPath.isNotEmpty())
        assertTrue(cacheDir.hasCachedEntry(url))
        val cachedBytes = cacheDir.getCachedBytes(url)
        assertNotNull(cachedBytes)
        assertEquals(3, cachedBytes.size)
        assertTrue(cacheDir.totalByteSize() >= 3L)

        assertTrue(cacheDir.evictCachedEntry(url))
        assertFalse(cacheDir.hasCachedEntry(url))

        val store = PreferenceStore.open("DirectoryTestApp")
        assertEquals("DirectoryTestApp", store.configDirectory.appName)

        configDir.clear()
        cacheDir.clearCache()
        configDir.dispose()
        cacheDir.dispose()
        store.dispose()
    }

    @Test
    fun `LibPngCodec and LibJpegCodec encode and decode RGBA pixels losslessly`() {
        val src = ImageBuffer(4, 3, PixelFormat.RGBA_8888)
        for (y in 0 until 3) {
            for (x in 0 until 4) {
                src.setPixel(x, y, RgbaColor(x * 60, y * 90, (x + y) * 40, 1f))
            }
        }
        src.setPixel(2, 1, RgbaColor(240, 128, 32, 0.5f))

        val pngBytes = LibPngCodec.encode(src)
        assertEquals(ImageFormat.PNG, ImageFormat.detectFromHeader(pngBytes))
        val pngMeta = LibPngCodec.probePngMetadata(pngBytes)
        assertEquals(4, pngMeta.width)
        assertEquals(3, pngMeta.height)
        assertTrue(pngMeta.hasAlpha)

        val decodedPng = LibPngCodec.decode(pngBytes)
        assertEquals(4, decodedPng.width)
        assertEquals(3, decodedPng.height)
        for (y in 0 until 3) {
            for (x in 0 until 4) {
                val expected = src.getPixel(x, y)
                val actual = decodedPng.getPixel(x, y)
                assertEquals(expected.r, actual.r)
                assertEquals(expected.g, actual.g)
                assertEquals(expected.b, actual.b)
                assertEquals(expected.a, actual.a)
            }
        }

        val jpegBytes = LibJpegCodec.encode(src, quality = 95)
        assertEquals(ImageFormat.JPEG, ImageFormat.detectFromHeader(jpegBytes))
        val decodedJpeg = LibJpegCodec.decode(jpegBytes)
        assertEquals(4, decodedJpeg.width)
        assertEquals(3, decodedJpeg.height)
        assertEquals(src.getPixel(2, 1).r, decodedJpeg.getPixel(2, 1).r)
        assertEquals(src.getPixel(2, 1).g, decodedJpeg.getPixel(2, 1).g)
        assertEquals(src.getPixel(2, 1).b, decodedJpeg.getPixel(2, 1).b)
    }

    @Test
    fun `ImageLoader and ImageCacheManager cache remote URLs in OS CacheDirectory and clearCache removes all entries`() {
        GenericUI.applicationName = "ImageCacheTestApp"
        ImageLoader.clearCache()

        val sampleImg = ImageBuffer(8, 6, PixelFormat.RGBA_8888).fill(RgbaColor(42, 160, 220, 1f))
        val samplePngBytes = LibPngCodec.encode(sampleImg)

        var networkFetchCount = 0
        val prevHook = ImageCacheManager.urlFetcherHook
        try {
            ImageCacheManager.urlFetcherHook = { requestedUrl ->
                if (requestedUrl == "https://assets.example.org/banner.png?rev=4") {
                    networkFetchCount++
                    samplePngBytes
                } else {
                    null
                }
            }

            val remoteUrl = "https://assets.example.org/banner.png?rev=4"
            assertFalse(ImageLoader.isCached(remoteUrl))

            // First load triggers urlFetcherHook and stores encoded bytes in CacheDirectory
            val loadedFirst = ImageLoader.loadFromUrl(remoteUrl)
            assertEquals(8, loadedFirst.width)
            assertEquals(6, loadedFirst.height)
            assertEquals(42, loadedFirst.getPixel(3, 2).r)
            assertEquals(160, loadedFirst.getPixel(3, 2).g)
            assertEquals(220, loadedFirst.getPixel(3, 2).b)
            assertEquals(1, networkFetchCount)
            assertTrue(ImageLoader.isCached(remoteUrl))
            assertTrue(ImageLoader.cachedDiskByteSize > 0L)

            // Clear memory cache only: second load reads from OS CacheDirectory without calling urlFetcherHook!
            ImageCacheManager.clearMemoryCache()
            val loadedFromDiskCache = ImageLoader.loadFromUrl(remoteUrl)
            assertEquals(8, loadedFromDiskCache.width)
            assertEquals(6, loadedFromDiskCache.height)
            assertEquals(42, loadedFromDiskCache.getPixel(0, 0).r)
            assertEquals(1, networkFetchCount)

            // Clear entire cache ("Clear Cache" user action)
            val deletedCount = ImageLoader.clearCache()
            assertTrue(deletedCount >= 1)
            assertFalse(ImageLoader.isCached(remoteUrl))
            assertEquals(0L, ImageLoader.cachedDiskByteSize)
            assertNull(ImageCacheManager.getCacheEntry(remoteUrl))
        } finally {
            ImageCacheManager.urlFetcherHook = prevHook
            ImageLoader.clearCache()
        }
    }
}
