package com.nexustools.genericui.core

import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.theme.ThemeConfig
import com.nexustools.genericui.threading.GenericUiThreading

/**
 * Global entry point for registering and retrieving the active platform [NativeFactory].
 */
object GenericUI {
    var activeFactory: NativeFactory? = null
    var applicationName: String = "GenericUI"
    var defaultThemeMode: ThemeMode = ThemeMode.AUTO
    var defaultThemeConfig: ThemeConfig = ThemeConfig.DEFAULT

    val GENERICUI: ResourceID
        get() = Resources.GENERICUI
    val BOOTSTRAP: ResourceID
        get() = Resources.BOOTSTRAP
    val BOOTSTRAP_JS: ResourceID
        get() = Resources.BOOTSTRAP_JS
    val BOOTSTRAP_ICONS: ResourceID
        get() = Resources.BOOTSTRAP_ICONS
    val GENERICUI_NAMESPACE: ResourceID
        get() = Resources.GENERICUI_NAMESPACE
    val LOADING_IMAGE: ResourceID
        get() = Resources.LOADING_IMAGE

    fun initialize(factory: NativeFactory, appName: String? = null) {
        activeFactory = factory
        GenericUiThreading.onFactoryInitialized(factory)
        if (!appName.isNullOrBlank()) {
            applicationName = appName.trim()
        }
    }

    fun requireFactory(): NativeFactory =
        activeFactory ?: error("GenericUI.initialize(factory) has not been called with a platform NativeFactory.")
}
