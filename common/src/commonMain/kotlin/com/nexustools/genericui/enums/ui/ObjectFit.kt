package com.nexustools.genericui.enums.ui

/**
 * Image scaling / object-fit modes (`CONTAIN`, `COVER`, `FILL`, `SCALE_DOWN`, `NONE`).
 */
enum class ObjectFit(val cssClass: String) {
    CONTAIN("object-fit-contain"),
    COVER("object-fit-cover"),
    FILL("object-fit-fill"),
    SCALE_DOWN("object-fit-scale"),
    NONE("object-fit-none")
}
