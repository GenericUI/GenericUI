package com.nexustools.genericui.component.dock

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.dock.ToolPaletteNativeImpl
import com.nexustools.genericui.style.RgbaColor

/**
 * Multi-column studio tool icon grid for selecting interactive viewport tools
 * (such as Move, Brush, Bezier Pen, Shape, Bucket Fill, Eyedropper, and Zoom).
 */
class ToolPalette(
    columns: Int = 2
) : AbstractContainer() {
    init {
        htmlTag = "genericui:toolpalette"
    }

    var columns: Int = columns
        set(value) {
            field = value.coerceAtLeast(1)
            typedNative<ToolPaletteNativeImpl<*>>()?.setColumns(field)
        }

    var foregroundColor: RgbaColor = RgbaColor.BLACK
        set(value) {
            field = value
            typedNative<ToolPaletteNativeImpl<*>>()?.setForegroundColor(value)
        }

    var backgroundColor: RgbaColor = RgbaColor.WHITE
        set(value) {
            field = value
            typedNative<ToolPaletteNativeImpl<*>>()?.setBackgroundColor(value)
        }

    private val _tools = mutableListOf<ToolPaletteItem>()
    val tools: List<ToolPaletteItem> get() = _tools

    private var isDispatchingToolChange: Boolean = false

    var activeToolId: String? = null
        set(value) {
            if (field == value) return
            field = value
            if (!isDispatchingToolChange) {
                isDispatchingToolChange = true
                try {
                    typedNative<ToolPaletteNativeImpl<*>>()?.setActiveToolId(value)
                    if (value != null) {
                        onToolSelected?.invoke(value)
                    }
                } finally {
                    isDispatchingToolChange = false
                }
            }
        }

    var onToolSelected: ((String) -> Unit)? = null
    var onColorsSwapped: ((RgbaColor, RgbaColor) -> Unit)? = null

    fun addTool(tool: ToolPaletteItem): ToolPalette = apply {
        _tools.add(tool)
        if (activeToolId == null) activeToolId = tool.id
        syncToolsToNative()
    }

    fun setTools(items: List<ToolPaletteItem>): ToolPalette = apply {
        _tools.clear()
        _tools.addAll(items)
        if (activeToolId == null) activeToolId = _tools.firstOrNull()?.id
        syncToolsToNative()
    }

    private fun syncToolsToNative() {
        val impl = typedNative<ToolPaletteNativeImpl<*>>() ?: return
        impl.setTools(_tools, activeToolId)
    }

    override fun createNativeImpl(factory: NativeFactory): ToolPaletteNativeImpl<*> =
        factory.createToolPalette()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<ToolPaletteNativeImpl<*>>() ?: return
        impl.setColumns(columns)
        syncToolsToNative()
        impl.setForegroundColor(foregroundColor)
        impl.setBackgroundColor(backgroundColor)
        impl.setOnToolSelected { id -> activeToolId = id }
        impl.setOnColorsSwapped { fg, bg ->
            foregroundColor = fg
            backgroundColor = bg
            onColorsSwapped?.invoke(fg, bg)
        }
    }
}
