package com.nexustools.genericui.component.system

import kotlin.reflect.KClass

/**
 * Platform-specific object introspection and instantiation helper used by [PreferenceObjectCodec].
 *
 * - On JVM (`:desktop:swing`) and Android (`:mobile:android`), uses Java reflection (`declaredFields`,
 *   primary/no-arg constructors, and `ParameterizedType` inspection) to automatically serialize and
 *   deserialize arbitrary Kotlin `data class`es, POJOs, and enums without requiring boilerplate.
 * - On JavaScript (`:web` JS) and WebAssembly (`:web` Wasm), inspects JS object keys and reconstructs
 *   typed instances via registered adapters, [PreferenceSerializable], or `defaultValue` templates.
 * - On Kotlin/Native (`:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`, `:mobile:ios`),
 *   uses [PreferenceSerializable], registered adapters/factories, and `defaultValue` templates.
 */
expect object PreferencePlatformReflector {
    fun extractObjectFields(instance: Any): Map<String, Any?>?

    fun <T : Any> instantiateObject(
        targetClass: KClass<T>,
        fields: Map<String, PreferenceElement>,
        defaultValue: T?
    ): T?

    fun <T : Any> resolveEnumConstant(
        targetClass: KClass<T>,
        name: String,
        defaultValue: T?
    ): T?
}
