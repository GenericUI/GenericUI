package com.nexustools.genericui.component.system

/**
 * Custom type adapter for converting instances of [T] to and from a [PreferenceElement].
 */
interface PreferenceTypeAdapter<T : Any> {
    fun toElement(value: T): PreferenceElement
    fun fromElement(element: PreferenceElement): T
}
