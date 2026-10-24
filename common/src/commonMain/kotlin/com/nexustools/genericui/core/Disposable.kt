package com.nexustools.genericui.core

/**
 * Resource cleanup contract for components and native handles.
 */
interface Disposable {
    val isDisposed: Boolean
        get() = false

    fun dispose()
}
