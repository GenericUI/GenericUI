package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 flexbox wrap utilities (`flex-nowrap`, `flex-wrap`, `flex-wrap-reverse`).
 */
enum class FlexWrap(val cssClass: String) {
    NOWRAP("flex-nowrap"),
    WRAP("flex-wrap"),
    WRAP_REVERSE("flex-wrap-reverse");

    companion object {
        fun fromCssClass(cssClass: String): FlexWrap? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
