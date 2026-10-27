package com.nexustools.genericui.style.theme

import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.enums.ui.ThemeMode

/**
 * Cross-platform system theme detector (`ThemeMode.AUTO` -> `ThemeMode.DARK` or `ThemeMode.LIGHT`)
 * and lifecycle hook dispatcher for backend theme/stylesheet synchronization.
 *
 * Active platform backends install [backendDetector], [onThemeAppliedHook],
 * and [onStyleSheetChangedHook] during initialization.
 */
object SystemThemeDetector {
    var overrideDetectedTheme: ThemeMode? = null
    var backendDetector: (() -> ThemeMode)? = null
    var onThemeAppliedHook: ((configuredTheme: ThemeMode, resolvedTheme: ThemeMode) -> Unit)? = null
    var onStyleSheetChangedHook: ((IApplication) -> Unit)? = null
    var fallbackSystemTheme: ThemeMode = ThemeMode.DARK
    private val systemThemeListeners = mutableListOf<(ThemeMode) -> Unit>()

    fun addSystemThemeListener(listener: (ThemeMode) -> Unit) {
        if (listener !in systemThemeListeners) {
            systemThemeListeners.add(listener)
        }
    }

    fun removeSystemThemeListener(listener: (ThemeMode) -> Unit) {
        systemThemeListeners.remove(listener)
    }

    fun notifySystemThemeChanged(detected: ThemeMode = detectSystemTheme()) {
        for (listener in systemThemeListeners.toList()) {
            listener(detected)
        }
    }

    /**
     * Queries the active platform's dark/light theme preference, returning either [ThemeMode.DARK]
     * or [ThemeMode.LIGHT].
     */
    fun detectSystemTheme(): ThemeMode {
        overrideDetectedTheme?.let {
            if (!it.isAuto) return it
        }
        val detected = try {
            backendDetector?.invoke()
        } catch (_: Throwable) {
            null
        }
        return when (detected) {
            ThemeMode.LIGHT -> ThemeMode.LIGHT
            ThemeMode.DARK -> ThemeMode.DARK
            else -> if (fallbackSystemTheme == ThemeMode.LIGHT) ThemeMode.LIGHT else ThemeMode.DARK
        }
    }

    /**
     * Resolves [mode] so that [ThemeMode.AUTO] maps to [detectSystemTheme] while [ThemeMode.DARK]
     * and [ThemeMode.LIGHT] return as-is.
     */
    fun resolveTheme(mode: ThemeMode): ThemeMode = when (mode) {
        ThemeMode.AUTO -> detectSystemTheme()
        ThemeMode.DARK -> ThemeMode.DARK
        ThemeMode.LIGHT -> ThemeMode.LIGHT
    }
}
