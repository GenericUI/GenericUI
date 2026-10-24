package com.nexustools.genericui.nativeimpl.dock

import com.nexustools.genericui.component.dock.ToolPaletteItem
import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.style.RgbaColor

interface ToolPaletteNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setColumns(columns: Int)
    fun setTools(tools: List<ToolPaletteItem>, activeToolId: String?)
    fun setActiveToolId(toolId: String?)
    fun setForegroundColor(color: RgbaColor)
    fun setBackgroundColor(color: RgbaColor)
    fun setOnToolSelected(listener: ((String) -> Unit)?)
    fun setOnColorsSwapped(listener: ((RgbaColor, RgbaColor) -> Unit)?)
}
