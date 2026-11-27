package com.nexustools.genericui.component.system

/**
 * Reflection-free multiplatform codec for encoding and decoding instances of [T]
 * to and from [PreferenceElement] AST trees.
 */
interface DataClassCodec<T : Any> {
    fun encode(value: T): PreferenceElement
    fun decode(element: PreferenceElement): T
}
