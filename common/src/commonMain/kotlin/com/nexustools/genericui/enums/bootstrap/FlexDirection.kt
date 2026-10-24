package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 flexbox direction utilities (`flex-row`, `flex-row-reverse`, `flex-column`, `flex-column-reverse`).
 */
enum class FlexDirection(val cssClass: String) {
    ROW("flex-row"),
    ROW_REVERSE("flex-row-reverse"),
    COLUMN("flex-column"),
    COLUMN_REVERSE("flex-column-reverse");

    companion object {
        fun fromCssClass(cssClass: String): FlexDirection? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
