package com.nexustools.genericui.image.cache

import com.nexustools.genericui.image.ImageFormat

/**
 * Metadata record describing an image cached by [ImageCacheManager] inside the user's OS
 * [com.nexustools.genericui.util.CacheDirectory].
 */
data class ImageCacheEntry(
    val url: String,
    val cacheFileName: String,
    val cachedFilePath: String,
    val byteSize: Int,
    val width: Int,
    val height: Int,
    val format: ImageFormat
)
