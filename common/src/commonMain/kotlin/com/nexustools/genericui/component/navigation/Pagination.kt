package com.nexustools.genericui.component.navigation

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Page number selector bar (`<ul class="pagination">`).
 */
class Pagination(
    var currentPage: Int = 1,
    var totalPages: Int = 1
) : AbstractContainer() {
    init {
        htmlTag = "genericui:pagination"
    }

    var onPageChanged: ((Int) -> Unit)? = null

    fun selectPage(page: Int) {
        currentPage = page.coerceIn(1, totalPages.coerceAtLeast(1))
        onPageChanged?.invoke(currentPage)
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("pagination", htmlTag)
}
