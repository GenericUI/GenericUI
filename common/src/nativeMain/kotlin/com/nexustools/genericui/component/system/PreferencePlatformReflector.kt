package com.nexustools.genericui.component.system

import kotlin.reflect.KClass

actual object PreferencePlatformReflector {

    actual fun extractObjectFields(instance: Any): Map<String, Any?>? {
        if (instance is PreferenceSerializable) {
            return instance.toPreferenceMap()
        }
        return null
    }

    actual fun <T : Any> instantiateObject(
        targetClass: KClass<T>,
        fields: Map<String, PreferenceElement>,
        defaultValue: T?
    ): T? = defaultValue

    actual fun <T : Any> resolveEnumConstant(
        targetClass: KClass<T>,
        name: String,
        defaultValue: T?
    ): T? = defaultValue
}
