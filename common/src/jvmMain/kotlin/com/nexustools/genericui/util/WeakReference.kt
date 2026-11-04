package com.nexustools.genericui.util

actual class WeakReference<T : Any> actual constructor(referent: T) {
    private val delegate = java.lang.ref.WeakReference(referent)

    actual fun get(): T? = delegate.get()

    actual fun clear() {
        delegate.clear()
    }
}
