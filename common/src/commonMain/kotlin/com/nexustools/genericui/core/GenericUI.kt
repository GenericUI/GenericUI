package com.nexustools.genericui.core

/**
 * Global entry point for registering and retrieving the active platform [NativeFactory].
 */
object GenericUI {
    var activeFactory: NativeFactory? = null

    fun initialize(factory: NativeFactory) {
        activeFactory = factory
    }

    fun requireFactory(): NativeFactory =
        activeFactory ?: error("GenericUI.initialize(factory) has not been called with a platform NativeFactory.")
}
