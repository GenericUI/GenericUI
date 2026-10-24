package com.nexustools.genericui.component.navigation

import com.nexustools.genericui.core.AbstractComponent

/**
 * Individual tab descriptor inside a [Tabs] container.
 */
data class TabItem(
    val id: String,
    var title: String,
    val content: AbstractComponent,
    val iconSvgOrPath: String? = null
)
