package com.nexustools.genericui.util

/**
 * Cross-platform weak reference wrapper used by [com.nexustools.genericui.image.ImageLoader] and
 * [com.nexustools.genericui.image.cache.ImageCacheManager] so decoded in-memory image buffers are
 * automatically reclaimed by the garbage collector when no longer referenced by any active component.
 */
expect class WeakReference<T : Any>(referent: T) {
    /**
     * Returns the weakly referenced object, or `null` if it has been cleared or garbage-collected.
     */
    fun get(): T?

    /**
     * Explicitly clears this weak reference.
     */
    fun clear()
}
