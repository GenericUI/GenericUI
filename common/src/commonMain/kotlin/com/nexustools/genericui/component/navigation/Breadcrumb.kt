package com.nexustools.genericui.component.navigation

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Hierarchical path navigation trail (`<ol class="breadcrumb">`).
 */
class Breadcrumb : AbstractContainer() {
    init {
        htmlTag = "genericui:breadcrumb"
    }

    private val _items = mutableListOf<BreadcrumbItem>()
    val items: List<BreadcrumbItem> get() = _items

    fun setItems(entries: List<BreadcrumbItem>): Breadcrumb = apply {
        _items.clear()
        _items.addAll(entries)
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("breadcrumb", htmlTag)
}
