package com.nexustools.genericui.style.color

/**
 * Tint modes for [com.nexustools.genericui.component.media.ImageView] — modeled after
 * Android's `ImageView.setImageTintMode` / `PorterDuff.Mode` plus automatic monocolor
 * theme recoloring for Dark/Light themes.
 */
enum class ImageTintMode {
    /** No tinting applied; original image pixels are rendered unmodified. */
    NONE,

    /**
     * Replaces RGB channels of every pixel with the tint color while preserving
     * the original pixel alpha multiplied by the tint alpha (`PorterDuff.Mode.SRC_IN`).
     */
    SRC_IN,

    /**
     * Multiplies RGB channels of every pixel by the normalized tint color while
     * preserving original pixel alpha (`PorterDuff.Mode.MULTIPLY`).
     */
    MULTIPLY,

    /**
     * Automatically detects whether an icon/image is monocolor (e.g. an all-black or
     * all-white glyph with varying alpha) and recolors its RGB channels to the active
     * theme foreground color (`#DEE2E6` in Dark mode, `#212529` in Light mode) or an
     * explicit `tintColor` if set, while leaving multi-colored thumbnails/artwork untouched.
     */
    MONOCOLOR_AUTO_THEME
}
