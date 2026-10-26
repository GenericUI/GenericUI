package com.nexustools.genericui.component.dock

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Searchable, categorized tree view for browsing grouped presets,
 * layer hierarchies, and component libraries.
 */
class CategorizedTreeView : AbstractContainer() {
    init {
        htmlTag = "genericui:categorizedtreeview"
    }

    var searchQuery: String = ""

    private val _categories = linkedMapOf<String, MutableList<Pair<String, String>>>()
    val categories: Map<String, List<Pair<String, String>>> get() = _categories

    var selectedItemId: String? = null
        private set

    var onItemSelected: ((String) -> Unit)? = null

    fun addCategoryItem(category: String, itemId: String, label: String): CategorizedTreeView = apply {
        _categories.getOrPut(category) { mutableListOf() }.add(itemId to label)
    }

    fun selectItem(itemId: String) {
        selectedItemId = itemId
        onItemSelected?.invoke(itemId)
    }

    fun clearCategories(): CategorizedTreeView = apply {
        _categories.clear()
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("categorized-tree-view", htmlTag)
}
