package com.nexustools.genericui.plugin

import java.io.File

/**
 * Target-specific icon configuration block used for `apple { icon = ... }`, `mac { icon = ... }`,
 * `macos { icon = ... }`, `ios { icon = ... }`, `ipados { icon = ... }`, and `ipad { icon = ... }`.
 */
open class GenericUiAppleIconConfig(
    private val getter: () -> String,
    private val setter: (String) -> Unit
) {
    /** Icon path (`String` or `File`) assigned to this target block. */
    var icon: Any?
        get() = getter().ifBlank { null }
        set(value) {
            setter(normalizeIconValue(value))
        }

    /** Sets the icon path (`String`, `File`, etc.) for this target block. */
    fun icon(path: Any?) {
        setter(normalizeIconValue(path))
    }

    companion object {
        /** Normalizes an icon DSL value (`String`, `File`, `CharSequence`, or `null`) into a trimmed path. */
        fun normalizeIconValue(value: Any?): String = when (value) {
            null -> ""
            is File -> value.path.trim()
            is CharSequence -> value.toString().trim()
            else -> value.toString().trim()
        }
    }
}
