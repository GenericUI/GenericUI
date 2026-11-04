@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.util

@JsFun("(target) => typeof WeakRef !== 'undefined' ? new WeakRef(target) : ({ deref: () => target })")
private external fun wasmCreateWeakRef(target: JsAny): JsAny

@JsFun("(weakRef) => weakRef ? (weakRef.deref() || null) : null")
private external fun wasmDerefWeakRef(weakRef: JsAny?): JsAny?

actual class WeakReference<T : Any> actual constructor(referent: T) {
    private var jsWeakRef: JsAny? = wasmCreateWeakRef(referent.toJsReference())

    @Suppress("UNCHECKED_CAST")
    actual fun get(): T? {
        val ref = wasmDerefWeakRef(jsWeakRef) ?: return null
        return (ref.unsafeCast<JsReference<T>>()).get()
    }

    actual fun clear() {
        jsWeakRef = null
    }
}
