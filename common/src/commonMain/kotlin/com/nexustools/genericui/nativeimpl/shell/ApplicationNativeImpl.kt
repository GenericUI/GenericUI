package com.nexustools.genericui.nativeimpl.shell

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.ui.ThemeMode

interface ApplicationNativeImpl<out W> : Disposable {
    val nativeApplication: W
    fun setThemeMode(mode: ThemeMode)
    fun getThemeMode(): ThemeMode
    fun setThemeColor(colorHex: String?) {}
    fun getThemeColor(): String? = null
    fun runEventLoop(onStarted: () -> Unit)
    fun exitApplication(exitCode: Int = 0)
}
