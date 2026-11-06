package com.nexustools.genericui.system.style

import com.nexustools.genericui.style.css.ComputedStyle

/**
 * Blink-style `MatchedPropertiesCache` caching resolved [ComputedStyle] instances across
 * identical component selector signatures (`tagName`, `id`, `classes`, `pseudoStateMask`,
 * `themeOrdinal`, `viewportWidthBucket`, and `epoch`).
 */
object BlinkMatchedPropertiesCache {
    private const val MAX_ENTRIES = 2048
    private val cache = linkedMapOf<String, ComputedStyle>()

    var epoch: Long = 1L
        private set

    var hitCount: Long = 0L
        private set

    var missCount: Long = 0L
        private set

    val size: Int
        get() = cache.size

    fun buildKey(
        sheetId: Int,
        tagName: String,
        id: String?,
        classes: Set<String>,
        pseudoStateMask: Int,
        themeOrdinal: Int,
        viewportWidthBucket: Int,
        ancestorSignature: String = ""
    ): String = buildString(96) {
        append(epoch).append('|')
        append(sheetId).append('|')
        append(tagName).append('|')
        append(id ?: "").append('|')
        append(pseudoStateMask).append('|')
        append(themeOrdinal).append('|')
        append(viewportWidthBucket).append('|')
        for (cls in classes) {
            append(cls).append(',')
        }
        if (ancestorSignature.isNotEmpty()) {
            append('|').append(ancestorSignature)
        }
    }

    fun get(key: String): ComputedStyle? {
        val found = cache[key]
        if (found != null) {
            hitCount++
        } else {
            missCount++
        }
        return found
    }

    fun put(key: String, style: ComputedStyle) {
        if (cache.size >= MAX_ENTRIES) {
            val firstKey = cache.keys.firstOrNull()
            if (firstKey != null) {
                cache.remove(firstKey)
            }
        }
        cache[key] = style
    }

    fun invalidateAll() {
        epoch++
        hitCount = 0L
        missCount = 0L
        cache.clear()
    }
}
