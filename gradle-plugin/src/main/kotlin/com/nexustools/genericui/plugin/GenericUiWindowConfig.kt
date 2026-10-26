package com.nexustools.genericui.plugin

/**
 * DSL configuration block (`window { ... }`) for configuring window title, dimensions,
 * resizability, fullscreen state, and icon at the root `genericui` level or inside any
 * variant scope (`common`, `desktopCommon`, `windows`, `macos`, `gtk`, `qt`, `swing`, `android`, `ios`, `web`).
 */
open class GenericUiWindowConfig {
    var title: String? = null
    var width: Int? = null
    var height: Int? = null
    var minWidth: Int? = null
    var minHeight: Int? = null
    var resizable: Boolean? = null
    var fullscreen: Boolean? = null
    var icon: String? = null

    /** Sets both [width] and [height] in pixels. */
    fun size(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /** Sets both [minWidth] and [minHeight] in pixels. */
    fun minSize(minWidth: Int, minHeight: Int) {
        this.minWidth = minWidth
        this.minHeight = minHeight
    }
}
