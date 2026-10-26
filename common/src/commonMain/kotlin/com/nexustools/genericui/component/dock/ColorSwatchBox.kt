package com.nexustools.genericui.component.dock

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.style.RgbaColor

/**
 * Dual foreground/background color swatch widget with swap (`X`) and reset (`D`) controls
 * and palette history swatches for studio tool docks.
 */
class ColorSwatchBox(
    foreground: RgbaColor = RgbaColor.BLACK,
    background: RgbaColor = RgbaColor.WHITE
) : AbstractContainer() {
    init {
        htmlTag = "genericui:colorswatchbox"
    }

    var foregroundColor: RgbaColor = foreground
        set(value) {
            field = value
            onForegroundChanged?.invoke(value)
        }

    var backgroundColor: RgbaColor = background
        set(value) {
            field = value
            onBackgroundChanged?.invoke(value)
        }

    private val _paletteSwatches = mutableListOf<RgbaColor>()
    val paletteSwatches: List<RgbaColor> get() = _paletteSwatches

    var onForegroundChanged: ((RgbaColor) -> Unit)? = null
    var onBackgroundChanged: ((RgbaColor) -> Unit)? = null

    /**
     * Swaps foreground and background colors (`X` shortcut in studio editors).
     */
    fun swapColors() {
        val prevFg = foregroundColor
        foregroundColor = backgroundColor
        backgroundColor = prevFg
    }

    /**
     * Resets foreground to black and background to white (`D` shortcut in studio editors).
     */
    fun resetToDefaultColors() {
        foregroundColor = RgbaColor.BLACK
        backgroundColor = RgbaColor.WHITE
    }

    fun setPaletteSwatches(colors: List<RgbaColor>): ColorSwatchBox = apply {
        _paletteSwatches.clear()
        _paletteSwatches.addAll(colors)
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("color-swatch-box", htmlTag)
}
