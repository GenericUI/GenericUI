package com.nexustools.genericui.component.form

/**
 * Search query input field (`<input type="search">`).
 */
class SearchField(
    query: String = "",
    placeholder: String = "Search..."
) : TextField(value = query, placeholder = placeholder)
