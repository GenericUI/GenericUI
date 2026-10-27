package com.nexustools.genericui.style.css

/**
 * Common contract for all strongly-typed CSS value representations in GenericUI.
 */
interface CssValue {
    /**
     * Serializes this typed value to its canonical W3C CSS declaration string.
     */
    fun toCssString(): String
}
