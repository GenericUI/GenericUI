package com.nexustools.genericui.window

/**
 * Picture-in-Picture (PiP) configuration passed to [com.nexustools.genericui.component.shell.Window.enterPictureInPicture].
 */
data class PipConfig(
    val aspectRatioNumerator: Int = 16,
    val aspectRatioDenominator: Int = 9,
    val autoEnterOnBackground: Boolean = true,
    val actions: List<PipAction> = emptyList()
)
