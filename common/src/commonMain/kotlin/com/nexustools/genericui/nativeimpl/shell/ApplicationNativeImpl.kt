package com.nexustools.genericui.nativeimpl.shell

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.theme.SystemThemeDetector

interface ApplicationNativeImpl<out W> : Disposable {
    val nativeApplication: W
    fun setThemeMode(mode: ThemeMode)
    fun getThemeMode(): ThemeMode
    fun detectSystemThemeMode(): ThemeMode = SystemThemeDetector.detectSystemTheme()
    fun setThemeColor(colorHex: String?) {}
    fun getThemeColor(): String? = null
    fun addStyleSheet(styleSheet: PrecompiledStyleSheet) {}
    fun removeStyleSheet(styleSheet: PrecompiledStyleSheet) {}
    fun runEventLoop(onStarted: () -> Unit)
    fun exitApplication(exitCode: Int = 0)
}
