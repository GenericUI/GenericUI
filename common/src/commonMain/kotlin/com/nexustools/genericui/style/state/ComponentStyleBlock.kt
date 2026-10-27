package com.nexustools.genericui.style.state

import com.nexustools.genericui.style.css.ComponentStyle

/**
 * Deprecated alias for [ComponentStyle]. Inline styles now use [ComponentStyle] (`component.getStyle()`).
 */
@Deprecated(
    message = "Use ComponentStyle (com.nexustools.genericui.style.css.ComponentStyle) instead.",
    replaceWith = ReplaceWith("ComponentStyle", "com.nexustools.genericui.style.css.ComponentStyle")
)
typealias ComponentStyleBlock = ComponentStyle
