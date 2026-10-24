package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 responsive grid breakpoints.
 */
enum class ResponsiveBreakpoint(val cssInfix: String, val minWidthPx: Int) {
    XS("", 0),
    SM("sm", 576),
    MD("md", 768),
    LG("lg", 992),
    XL("xl", 1200),
    XXL("xxl", 1400);

    companion object {
        fun forWidthPx(widthPx: Int): ResponsiveBreakpoint =
            entries.lastOrNull { widthPx >= it.minWidthPx } ?: XS
    }
}
