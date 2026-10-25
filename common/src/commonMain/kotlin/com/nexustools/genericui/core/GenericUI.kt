package com.nexustools.genericui.core

/**
 * Global entry point for registering and retrieving the active platform [NativeFactory].
 */
object GenericUI {
    var activeFactory: NativeFactory? = null
    var applicationName: String = "GenericUI"

    fun initialize(factory: NativeFactory, appName: String? = null) {
        activeFactory = factory
        if (!appName.isNullOrBlank()) {
            applicationName = appName.trim()
        }
    }

    fun requireFactory(): NativeFactory =
        activeFactory ?: error("GenericUI.initialize(factory) has not been called with a platform NativeFactory.")
}
