package com.nexustools.genericui.component.form

/**
 * Search query input field (`<input type="search">`).
 */
class SearchField(
    query: String = "",
    placeholder: String = "Search...",
    label: String? = null
) : TextField(value = query, placeholder = placeholder, label = label) {
    init {
        setAttribute("data-control-variant", "search")
    }

    override fun syncStateToNative() {
        setAttribute("data-control-variant", "search")
        super.syncStateToNative()
    }
}
