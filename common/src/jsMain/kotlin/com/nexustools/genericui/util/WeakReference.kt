package com.nexustools.genericui.util

actual class WeakReference<T : Any> actual constructor(referent: T) {
    private var jsWeakRef: dynamic = createJsWeakRef(referent)

    @Suppress("UNCHECKED_CAST")
    actual fun get(): T? {
        val ref = jsWeakRef ?: return null
        return ref.deref() as? T
    }

    actual fun clear() {
        jsWeakRef = null
    }

    companion object {
        private fun createJsWeakRef(target: Any): dynamic =
            js("typeof WeakRef !== 'undefined' ? new WeakRef(target) : { deref: function() { return target; } }")
    }
}
