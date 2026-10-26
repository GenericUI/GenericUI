package com.nexustools.genericui.component.layout

import com.nexustools.genericui.window.IWindow
import kotlin.jvm.JvmName
import kotlin.math.min

/**
 * Optional, opt-in controller ([HybridFullscreenPolicy.DISABLED] by default) implementing dynamic
 * phone vs. tablet vs. desktop/web fullscreen and screen orientation rules for [HybridColumnLayout]
 * and [com.nexustools.genericui.component.fragment.helper.HybridColumnFragment].
 *
 * - **Disabled by default ([HybridFullscreenPolicy.DISABLED])**: Never forces fullscreen or orientation changes unless explicitly enabled.
 * - **Phone (`smallestScreenWidthDp < 600`)**: When [HybridFullscreenPolicy.AUTO_FULLSCREEN_ON_PHONE_LANDSCAPE] or
 *   [HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED] is active, rotating to landscape automatically enters
 *   fullscreen (`requestedScreenOrientation = "USER"`), and rotating back to portrait automatically exits fullscreen.
 * - **Tablet (`smallestScreenWidthDp >= 600`)**: Rotating between landscape and portrait does **not** force fullscreen
 *   (allowing [HybridColumnLayout] to switch between [HybridLayoutMode.COLUMNS] and [HybridLayoutMode.SINGLE]).
 *   When fullscreen is explicitly entered under [HybridFullscreenPolicy.TABLET_COLUMNS_AND_LOCKED_FULLSCREEN] or
 *   [HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED], `requestedScreenOrientation` locks to `"LANDSCAPE"`
 *   until fullscreen is exited (`"UNSPECIFIED"`).
 */
class DynamicFullscreenController(
    var policy: HybridFullscreenPolicy = HybridFullscreenPolicy.DISABLED,
    var breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    var targetWindow: IWindow? = null,
    var onFullscreenChanged: ((Boolean) -> Unit)? = null,
    var onPictureInPictureChanged: ((Boolean) -> Unit)? = null,
    var onRequestedOrientationChanged: ((String) -> Unit)? = null
) {

    @set:JvmName("setFullscreenInternal")
    var isFullscreen: Boolean = false
        private set

    @set:JvmName("setPictureInPictureInternal")
    var isInPictureInPictureMode: Boolean = false
        private set

    var isTablet: Boolean = true
        private set

    var isLandscape: Boolean = true
        private set

    var requestedScreenOrientation: String = "UNSPECIFIED"
        private set

    val isEnabled: Boolean
        get() = policy != HybridFullscreenPolicy.DISABLED

    /**
     * Enters or exits fullscreen mode when [policy] is not [HybridFullscreenPolicy.DISABLED].
     */
    fun setFullscreen(enabled: Boolean, userInitiated: Boolean = true) {
        if (policy == HybridFullscreenPolicy.DISABLED) return

        val previousFullscreen = isFullscreen
        val previousOrientation = requestedScreenOrientation
        isFullscreen = enabled

        val includesTabletLocked =
            policy == HybridFullscreenPolicy.TABLET_COLUMNS_AND_LOCKED_FULLSCREEN ||
                policy == HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED
        val includesPhoneAuto =
            policy == HybridFullscreenPolicy.AUTO_FULLSCREEN_ON_PHONE_LANDSCAPE ||
                policy == HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED

        requestedScreenOrientation = when {
            isTablet && includesTabletLocked -> if (enabled) "LANDSCAPE" else "UNSPECIFIED"
            !isTablet && includesPhoneAuto -> "USER"
            else -> "UNSPECIFIED"
        }

        targetWindow?.setFullscreen(enabled)
        if (requestedScreenOrientation != previousOrientation || userInitiated) {
            onRequestedOrientationChanged?.invoke(requestedScreenOrientation)
        }
        if (isFullscreen != previousFullscreen || userInitiated) {
            onFullscreenChanged?.invoke(isFullscreen)
        }
    }

    /**
     * Toggles fullscreen state when [isEnabled] is `true`.
     */
    fun toggleFullscreen(): Boolean {
        setFullscreen(!isFullscreen, userInitiated = true)
        return isFullscreen
    }

    /**
     * Updates Picture-in-Picture state and notifies [onPictureInPictureChanged].
     */
    fun setPictureInPictureMode(inPip: Boolean) {
        if (policy == HybridFullscreenPolicy.DISABLED && !inPip && !isInPictureInPictureMode) return
        val changed = isInPictureInPictureMode != inPip
        isInPictureInPictureMode = inPip
        if (changed) {
            onPictureInPictureChanged?.invoke(inPip)
        }
    }

    /**
     * Evaluates viewport dimensions and device class (`smallestScreenWidthDp`) on resize or orientation change.
     */
    fun onViewportOrOrientationChanged(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp),
        isMobileTarget: Boolean = true
    ) {
        isTablet = breakpointSpec.isTablet(smallestScreenWidthDp)
        isLandscape = viewportWidthDp > viewportHeightDp

        if (policy == HybridFullscreenPolicy.DISABLED || policy == HybridFullscreenPolicy.MANUAL_ONLY) {
            return
        }

        val autoPhoneLandscape =
            policy == HybridFullscreenPolicy.AUTO_FULLSCREEN_ON_PHONE_LANDSCAPE ||
                policy == HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED

        if (isMobileTarget && !isTablet && autoPhoneLandscape) {
            if (isLandscape && !isFullscreen) {
                setFullscreen(true, userInitiated = false)
            } else if (!isLandscape && isFullscreen) {
                setFullscreen(false, userInitiated = false)
            }
        }
    }

    /**
     * Consumes the back button press if currently in fullscreen mode, exiting fullscreen and returning `true`.
     */
    fun onBackPressed(): Boolean {
        if (isEnabled && isFullscreen) {
            setFullscreen(false, userInitiated = true)
            return true
        }
        return false
    }
}
