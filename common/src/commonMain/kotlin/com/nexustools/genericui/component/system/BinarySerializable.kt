package com.nexustools.genericui.component.system

/**
 * Marks a Kotlin `data class` for compile-time multiplatform [DataClassCodec] generation
 * and automatic registration with [DataClassCodecRegistry] so it can be serialized and
 * deserialized via [PreferenceObjectCodec], `BinaryJson`, `BinaryPlist`, and [PreferenceStore]
 * across all Kotlin Multiplatform targets without reflection.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class BinarySerializable
