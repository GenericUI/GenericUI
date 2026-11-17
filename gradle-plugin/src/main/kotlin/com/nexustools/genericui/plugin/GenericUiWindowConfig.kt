package com.nexustools.genericui.plugin

/**
 * DSL configuration block for initial window geometry, title, resizability, fullscreen, always-on-top,
 * client-viewport aspect ratio, and icon across `genericui { window { ... } }` and per-backend/common variant blocks.
 */
open class GenericUiWindowConfig {
    var title: String? = null
    var width: Int? = null
    var height: Int? = null
    var minWidth: Int? = null
    var minHeight: Int? = null
    var resizable: Boolean? = null
    var fullscreen: Boolean? = null
    var alwaysOnTop: Boolean? = null
    var aspectRatio: Double? = null
    var icon: String? = null

    /** Sets both [width] and [height] in pixels. */
    fun size(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /** Sets both [minWidth] and [minHeight] in pixels. */
    fun minSize(width: Int, height: Int) {
        this.minWidth = width
        this.minHeight = height
    }

    /**
     * Sets a positive client-viewport aspect ratio constraint (`width / height`, e.g. `aspectRatio(16, 9)`).
     */
    fun aspectRatio(width: Int, height: Int) {
        require(width > 0 && height > 0) {
            "Window aspectRatio requires positive dimensions, got width=$width, height=$height."
        }
        this.aspectRatio = width.toDouble() / height.toDouble()
    }
}
