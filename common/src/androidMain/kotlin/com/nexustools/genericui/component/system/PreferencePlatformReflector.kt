package com.nexustools.genericui.component.system

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass

actual object PreferencePlatformReflector {

    actual fun extractObjectFields(instance: Any): Map<String, Any?>? {
        val cls = instance.javaClass
        if (cls.isPrimitive || cls == String::class.java || Number::class.java.isAssignableFrom(cls) ||
            cls == java.lang.Boolean::class.java || cls.isEnum || cls.isArray ||
            Collection::class.java.isAssignableFrom(cls) || Map::class.java.isAssignableFrom(cls)
        ) {
            return null
        }
        val fields = collectSerializableFields(cls)
        val result = LinkedHashMap<String, Any?>(fields.size)
        for (field in fields) {
            runCatching {
                field.isAccessible = true
                result[field.name] = field.get(instance)
            }
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    actual fun <T : Any> instantiateObject(
        targetClass: KClass<T>,
        fields: Map<String, PreferenceElement>,
        defaultValue: T?
    ): T? {
        val jClass = targetClass.java
        if (jClass.isInterface || Modifier.isAbstract(jClass.modifiers)) {
            return defaultValue
        }

        val declaredFields = collectSerializableFields(jClass)

        val noArgInstance: T? = runCatching {
            val ctor = jClass.getDeclaredConstructor()
            ctor.isAccessible = true
            ctor.newInstance()
        }.getOrNull()

        if (noArgInstance != null) {
            populateFields(noArgInstance, declaredFields, fields, defaultValue)
            return noArgInstance
        }

        val constructors = jClass.declaredConstructors.sortedByDescending { it.parameterCount }
        for (ctor in constructors) {
            val paramTypes = ctor.parameterTypes
            if (paramTypes.any { it.name == "kotlin.jvm.internal.DefaultConstructorMarker" }) {
                continue
            }
            if (paramTypes.size == declaredFields.size) {
                val genericTypes = ctor.genericParameterTypes
                val args = arrayOfNulls<Any?>(paramTypes.size)
                var compatible = true
                for (i in paramTypes.indices) {
                    val field = declaredFields[i]
                    val elem = fields[field.name]
                    val fallbackVal = if (defaultValue != null) {
                        runCatching {
                            field.isAccessible = true
                            field.get(defaultValue)
                        }.getOrNull()
                    } else {
                        null
                    }
                    val converted = if (elem != null && !elem.isNull) {
                        convertElementToJavaType(elem, paramTypes[i], genericTypes.getOrNull(i) ?: field.genericType, fallbackVal)
                    } else if (elem != null && elem.isNull) {
                        if (paramTypes[i].isPrimitive) fallbackVal ?: defaultForJavaType(paramTypes[i]) else null
                    } else {
                        fallbackVal ?: defaultForJavaType(paramTypes[i])
                    }
                    if (converted == null && paramTypes[i].isPrimitive) {
                        compatible = false
                        break
                    }
                    args[i] = converted
                }
                if (compatible) {
                    val created = runCatching {
                        ctor.isAccessible = true
                        ctor.newInstance(*args) as T
                    }.getOrNull()
                    if (created != null) {
                        populateFields(created, declaredFields, fields, defaultValue)
                        return created
                    }
                }
            }
        }

        val unsafeInstance = allocateWithUnsafe(jClass)
        if (unsafeInstance != null) {
            populateFields(unsafeInstance, declaredFields, fields, defaultValue)
            return unsafeInstance
        }

        return defaultValue
    }

    @Suppress("UNCHECKED_CAST")
    actual fun <T : Any> resolveEnumConstant(
        targetClass: KClass<T>,
        name: String,
        defaultValue: T?
    ): T? {
        val jClass = targetClass.java
        val constants = jClass.enumConstants ?: return defaultValue
        for (c in constants) {
            val enumVal = c as? Enum<*> ?: continue
            if (enumVal.name == name) return c
        }
        for (c in constants) {
            val enumVal = c as? Enum<*> ?: continue
            if (enumVal.name.equals(name, ignoreCase = true)) return c
        }
        return defaultValue
    }

    private fun collectSerializableFields(cls: Class<*>): List<Field> {
        val hierarchy = mutableListOf<Class<*>>()
        var curr: Class<*>? = cls
        while (curr != null && curr != Any::class.java && curr != Object::class.java) {
            hierarchy.add(0, curr)
            curr = curr.superclass
        }
        val out = mutableListOf<Field>()
        for (c in hierarchy) {
            for (f in c.declaredFields) {
                val mods = f.modifiers
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || f.isSynthetic) continue
                if (f.name == "Companion" || f.name.startsWith("\$")) continue
                out.add(f)
            }
        }
        return out
    }

    private fun <T : Any> populateFields(
        instance: T,
        declaredFields: List<Field>,
        fields: Map<String, PreferenceElement>,
        defaultValue: T?
    ) {
        for (field in declaredFields) {
            val elem = fields[field.name]
            val fallbackVal = if (defaultValue != null) {
                runCatching {
                    field.isAccessible = true
                    field.get(defaultValue)
                }.getOrNull()
            } else {
                null
            }
            if (elem != null) {
                val value = convertElementToJavaType(elem, field.type, field.genericType, fallbackVal)
                runCatching {
                    field.isAccessible = true
                    if (value != null || !field.type.isPrimitive) {
                        field.set(instance, value)
                    }
                }
            } else if (fallbackVal != null) {
                runCatching {
                    field.isAccessible = true
                    field.set(instance, fallbackVal)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertElementToJavaType(
        element: PreferenceElement,
        rawType: Class<*>,
        genericType: Type?,
        fallbackValue: Any?
    ): Any? {
        if (element.isNull) {
            return if (rawType.isPrimitive) fallbackValue ?: defaultForJavaType(rawType) else null
        }
        return when {
            rawType == PreferenceElement::class.java -> element
            rawType == String::class.java || rawType == CharSequence::class.java -> element.asString("")
            rawType == Boolean::class.javaPrimitiveType || rawType == java.lang.Boolean::class.java -> element.asBoolean()
            rawType == Int::class.javaPrimitiveType || rawType == java.lang.Integer::class.java -> element.asInt()
            rawType == Long::class.javaPrimitiveType || rawType == java.lang.Long::class.java -> element.asLong()
            rawType == Float::class.javaPrimitiveType || rawType == java.lang.Float::class.java -> element.asFloat()
            rawType == Double::class.javaPrimitiveType || rawType == java.lang.Double::class.java -> element.asDouble()
            rawType == Byte::class.javaPrimitiveType || rawType == java.lang.Byte::class.java -> element.asByte()
            rawType == Short::class.javaPrimitiveType || rawType == java.lang.Short::class.java -> element.asShort()
            rawType == Char::class.javaPrimitiveType || rawType == java.lang.Character::class.java ->
                element.asString()?.firstOrNull() ?: '\u0000'
            rawType.isEnum -> {
                val name = element.asString() ?: return fallbackValue
                resolveEnumConstant((rawType as Class<Any>).kotlin, name, fallbackValue)
            }
            Set::class.java.isAssignableFrom(rawType) -> {
                val itemClass = resolveGenericTypeArgument(genericType, 0)
                val set = LinkedHashSet<Any?>()
                for (item in element.arrayElements) {
                    set.add(
                        if (itemClass != null && itemClass != Any::class.java) {
                            convertElementToJavaType(item, itemClass, itemClass, null)
                        } else {
                            item.toPlainValue()
                        }
                    )
                }
                set
            }
            List::class.java.isAssignableFrom(rawType) || Collection::class.java.isAssignableFrom(rawType) -> {
                val itemClass = resolveGenericTypeArgument(genericType, 0)
                val list = ArrayList<Any?>(element.arrayElements.size)
                for (item in element.arrayElements) {
                    list.add(
                        if (itemClass != null && itemClass != Any::class.java) {
                            convertElementToJavaType(item, itemClass, itemClass, null)
                        } else {
                            item.toPlainValue()
                        }
                    )
                }
                list
            }
            Map::class.java.isAssignableFrom(rawType) -> {
                val valueClass = resolveGenericTypeArgument(genericType, 1)
                val map = LinkedHashMap<String, Any?>(element.mapEntries.size)
                for ((k, v) in element.mapEntries) {
                    map[k] = if (valueClass != null && valueClass != Any::class.java) {
                        convertElementToJavaType(v, valueClass, valueClass, null)
                    } else {
                        v.toPlainValue()
                    }
                }
                map
            }
            rawType == IntArray::class.java -> IntArray(element.arrayElements.size) { element.arrayElements[it].asInt() }
            rawType == LongArray::class.java -> LongArray(element.arrayElements.size) { element.arrayElements[it].asLong() }
            rawType == FloatArray::class.java -> FloatArray(element.arrayElements.size) { element.arrayElements[it].asFloat() }
            rawType == DoubleArray::class.java -> DoubleArray(element.arrayElements.size) { element.arrayElements[it].asDouble() }
            rawType == BooleanArray::class.java -> BooleanArray(element.arrayElements.size) { element.arrayElements[it].asBoolean() }
            rawType == ByteArray::class.java -> ByteArray(element.arrayElements.size) { element.arrayElements[it].asByte() }
            rawType.isArray -> {
                val compType = rawType.componentType ?: Any::class.java
                val arr = java.lang.reflect.Array.newInstance(compType, element.arrayElements.size)
                for (i in element.arrayElements.indices) {
                    val itemVal = convertElementToJavaType(element.arrayElements[i], compType, compType, null)
                    java.lang.reflect.Array.set(arr, i, itemVal)
                }
                arr
            }
            rawType == Any::class.java -> element.toPlainValue()
            else -> PreferenceObjectCodec.fromElement(element, (rawType as Class<Any>).kotlin, fallbackValue)
        }
    }

    private fun resolveGenericTypeArgument(genericType: Type?, index: Int): Class<*>? {
        if (genericType !is ParameterizedType) return null
        val args = genericType.actualTypeArguments
        val arg = args.getOrNull(index) ?: return null
        return when (arg) {
            is Class<*> -> arg
            is ParameterizedType -> arg.rawType as? Class<*>
            else -> null
        }
    }

    private fun defaultForJavaType(type: Class<*>): Any? = when {
        type == Boolean::class.javaPrimitiveType || type == java.lang.Boolean::class.java -> false
        type == Int::class.javaPrimitiveType || type == java.lang.Integer::class.java -> 0
        type == Long::class.javaPrimitiveType || type == java.lang.Long::class.java -> 0L
        type == Float::class.javaPrimitiveType || type == java.lang.Float::class.java -> 0f
        type == Double::class.javaPrimitiveType || type == java.lang.Double::class.java -> 0.0
        type == Byte::class.javaPrimitiveType || type == java.lang.Byte::class.java -> 0.toByte()
        type == Short::class.javaPrimitiveType || type == java.lang.Short::class.java -> 0.toShort()
        type == Char::class.javaPrimitiveType || type == java.lang.Character::class.java -> '\u0000'
        type == String::class.java -> ""
        Set::class.java.isAssignableFrom(type) -> emptySet<Any?>()
        List::class.java.isAssignableFrom(type) || Collection::class.java.isAssignableFrom(type) -> emptyList<Any?>()
        Map::class.java.isAssignableFrom(type) -> emptyMap<String, Any?>()
        type.isEnum -> type.enumConstants?.firstOrNull()
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> allocateWithUnsafe(jClass: Class<T>): T? = runCatching {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        allocateInstance.invoke(unsafe, jClass) as T
    }.getOrNull()
}
