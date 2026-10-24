package com.nexustools.genericui.window

import com.nexustools.genericui.enums.bootstrap.ResponsiveBreakpoint
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.WindowState

/**
 * Snapshot of window/screen configuration passed to [com.nexustools.genericui.component.fragment.Fragment.onConfigurationChanged].
 * Mirrors Android's `Configuration` (orientation, `smallestScreenWidthDp`, viewport dimensions, theme mode) across all 8 backends.
 */
data class WindowConfiguration(
    val widthPx: Int,
    val heightPx: Int,
    val smallestScreenWidthDp: Int = minOf(widthPx, heightPx),
    val densityScale: Float = 1.0f,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val windowState: WindowState = WindowState.NORMAL,
    val isFullscreen: Boolean = windowState == WindowState.FULLSCREEN,
    val isInPictureInPicture: Boolean = windowState == WindowState.PICTURE_IN_PICTURE
) {
    val isLandscape: Boolean
        get() = widthPx > heightPx

    val isPortrait: Boolean
        get() = !isLandscape

    val isTabletOrDesktop: Boolean
        get() = smallestScreenWidthDp >= 600 || widthPx >= 768

    val breakpoint: ResponsiveBreakpoint
        get() = ResponsiveBreakpoint.forWidthPx(widthPx)
}
