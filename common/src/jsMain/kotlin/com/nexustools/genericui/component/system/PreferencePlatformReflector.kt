package com.nexustools.genericui.component.system

import kotlin.reflect.KClass

actual object PreferencePlatformReflector {

    actual fun extractObjectFields(instance: Any): Map<String, Any?>? {
        if (instance is PreferenceSerializable) {
            return instance.toPreferenceMap()
        }
        val dyn: dynamic = instance
        val keys: Array<String>? = runCatching {
            js("Object.keys")(dyn) as? Array<String>
        }.getOrNull()
        if (keys == null || keys.isEmpty()) return null
        val map = LinkedHashMap<String, Any?>(keys.size)
        for (rawKey in keys) {
            if (rawKey.startsWith("$")) continue
            val cleanKey = rawKey.substringBefore("_")
            map[cleanKey] = dyn[rawKey]
        }
        return map
    }

    @Suppress("UNCHECKED_CAST")
    actual fun <T : Any> instantiateObject(
        targetClass: KClass<T>,
        fields: Map<String, PreferenceElement>,
        defaultValue: T?
    ): T? {
        if (defaultValue != null) {
            val proto: dynamic = js("Object.getPrototypeOf")(defaultValue)
            val clone: dynamic = js("Object.create")(proto)
            val defaultDyn: dynamic = defaultValue
            val keys: Array<String> = js("Object.keys")(defaultDyn) as Array<String>
            for (rawKey in keys) {
                val cleanKey = rawKey.substringBefore("_")
                val elem = fields[cleanKey] ?: fields[rawKey]
                clone[rawKey] = if (elem != null) elem.toPlainValue() else defaultDyn[rawKey]
            }
            return clone as T
        }
        val jsCtor: dynamic = targetClass.js
        return runCatching {
            val obj: dynamic = js("Object.create")(jsCtor.prototype)
            for ((k, v) in fields) {
                obj[k] = v.toPlainValue()
            }
            obj as T
        }.getOrNull()
    }

    actual fun <T : Any> resolveEnumConstant(
        targetClass: KClass<T>,
        name: String,
        defaultValue: T?
    ): T? = defaultValue
}
