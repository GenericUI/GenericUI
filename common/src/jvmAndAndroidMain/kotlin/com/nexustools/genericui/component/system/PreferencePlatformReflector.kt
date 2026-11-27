package com.nexustools.genericui.component.system

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass

actual object PreferencePlatformReflector {

    actual fun extractObjectFields(instance: Any): Map<String, Any?>? {
        val cls = instance.javaClass
        if (cls.isPrimitive || cls == String::class.java || Number::class.java.isAssignableFrom(cls) ||
            cls == java.lang.Boolean::class.java || cls == java.lang.Character::class.java ||
            cls.isEnum || cls.superclass?.isEnum == true || cls.isArray ||
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

        // Filter out synthetic Kotlin DefaultConstructorMarker constructors using parameterTypes (API 1)
        val nonDefaultConstructors = jClass.declaredConstructors
            .filter { ctor ->
                ctor.parameterTypes.none { it.name == "kotlin.jvm.internal.DefaultConstructorMarker" }
            }
            .sortedByDescending { it.parameterCount }

        // 1. Prefer primary Kotlin data-class constructors (matching field count or having a $default bridge)
        for (ctor in nonDefaultConstructors) {
            val paramTypes = ctor.parameterTypes
            if (paramTypes.isEmpty()) continue
            val isPrimaryCandidate = paramTypes.size == declaredFields.size ||
                findDefaultConstructor(jClass, paramTypes) != null
            if (!isPrimaryCandidate) continue
            val created = tryInstantiateWithConstructor(jClass, ctor, declaredFields, fields, defaultValue)
            if (created != null) {
                return created
            }
        }

        // 2. Try no-arg constructor for Java POJOs and Kotlin classes with 0-arg primary constructors
        val noArgInstance: T? = runCatching {
            val ctor = jClass.getDeclaredConstructor()
            ctor.isAccessible = true
            ctor.newInstance()
        }.getOrNull()

        if (noArgInstance != null) {
            populateFields(noArgInstance, declaredFields, fields, defaultValue)
            return noArgInstance
        }

        // 3. Try remaining parameterized constructors (e.g. primary constructor + extra body fields without defaults)
        for (ctor in nonDefaultConstructors) {
            val paramTypes = ctor.parameterTypes
            if (paramTypes.isEmpty() || paramTypes.size > declaredFields.size) continue
            val created = tryInstantiateWithConstructor(jClass, ctor, declaredFields, fields, defaultValue)
            if (created != null) {
                return created
            }
        }

        // 4. Fallback: allocate via sun.misc.Unsafe if available and populate fields directly
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

    private fun <T : Any> tryInstantiateWithConstructor(
        jClass: Class<T>,
        ctor: Constructor<*>,
        declaredFields: List<Field>,
        fields: Map<String, PreferenceElement>,
        defaultValue: T?
    ): T? {
        val paramTypes = ctor.parameterTypes
        if (paramTypes.size > declaredFields.size) return null
        for (i in paramTypes.indices) {
            if (!areTypesCompatibleForBinding(paramTypes[i], declaredFields[i].type)) {
                return null
            }
        }

        val genericTypes = ctor.genericParameterTypes
        val defaultCtor = findDefaultConstructor(jClass, paramTypes)
        val maskWords = IntArray(maxOf(1, (paramTypes.size + 31) / 32))
        var useDefaultCtor = false
        val args = arrayOfNulls<Any?>(paramTypes.size)

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

            if (elem != null && !elem.isNull) {
                val converted = convertElementToJavaType(
                    elem,
                    paramTypes[i],
                    genericTypes.getOrNull(i) ?: field.genericType,
                    fallbackVal
                )
                if (converted == null && paramTypes[i].isPrimitive) {
                    return null
                }
                args[i] = converted
            } else if (elem != null && elem.isNull) {
                if (paramTypes[i].isPrimitive) {
                    if (fallbackVal != null) {
                        args[i] = fallbackVal
                    } else if (defaultCtor != null) {
                        maskWords[i / 32] = maskWords[i / 32] or (1 shl (i % 32))
                        useDefaultCtor = true
                        args[i] = defaultForJavaType(paramTypes[i])
                    } else {
                        args[i] = defaultForJavaType(paramTypes[i])
                    }
                } else {
                    args[i] = null
                }
            } else {
                // Field omitted from decoded map
                if (defaultValue != null) {
                    if (fallbackVal == null && paramTypes[i].isPrimitive) {
                        return null
                    }
                    args[i] = fallbackVal
                } else if (defaultCtor != null) {
                    maskWords[i / 32] = maskWords[i / 32] or (1 shl (i % 32))
                    useDefaultCtor = true
                    args[i] = defaultForJavaType(paramTypes[i])
                } else {
                    val defVal = defaultForJavaType(paramTypes[i])
                    if (defVal == null && paramTypes[i].isPrimitive) {
                        return null
                    }
                    args[i] = defVal
                }
            }
        }

        val created: T? = if (useDefaultCtor && defaultCtor != null) {
            val totalArgs = paramTypes.size + maskWords.size + 1
            val fullArgs = arrayOfNulls<Any?>(totalArgs)
            args.copyInto(fullArgs, destinationOffset = 0)
            for (w in maskWords.indices) {
                fullArgs[paramTypes.size + w] = maskWords[w]
            }
            fullArgs[totalArgs - 1] = null // kotlin.jvm.internal.DefaultConstructorMarker
            runCatching {
                defaultCtor.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                defaultCtor.newInstance(*fullArgs) as T
            }.getOrNull()
        } else {
            runCatching {
                ctor.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                ctor.newInstance(*args) as T
            }.getOrNull()
        }

        if (created != null && paramTypes.size < declaredFields.size) {
            val remainingFields = declaredFields.subList(paramTypes.size, declaredFields.size)
            populateFields(created, remainingFields, fields, defaultValue)
        }
        return created
    }

    private fun findDefaultConstructor(
        jClass: Class<*>,
        primaryParamTypes: Array<Class<*>>
    ): Constructor<*>? {
        val paramCount = primaryParamTypes.size
        if (paramCount == 0) return null
        val maskWordCount = (paramCount + 31) / 32
        val expectedTotalParams = paramCount + maskWordCount + 1
        for (ctor in jClass.declaredConstructors) {
            val candidateTypes = ctor.parameterTypes
            if (candidateTypes.size != expectedTotalParams) continue
            var matchesPrefix = true
            for (i in 0 until paramCount) {
                if (candidateTypes[i] != primaryParamTypes[i]) {
                    matchesPrefix = false
                    break
                }
            }
            if (!matchesPrefix) continue
            var masksValid = true
            for (w in 0 until maskWordCount) {
                if (candidateTypes[paramCount + w] != Int::class.javaPrimitiveType) {
                    masksValid = false
                    break
                }
            }
            if (!masksValid) continue
            if (candidateTypes[expectedTotalParams - 1].name == "kotlin.jvm.internal.DefaultConstructorMarker") {
                return ctor
            }
        }
        return null
    }

    private fun areTypesCompatibleForBinding(paramType: Class<*>, fieldType: Class<*>): Boolean {
        if (paramType == fieldType) return true
        if (paramType.isAssignableFrom(fieldType) || fieldType.isAssignableFrom(paramType)) return true
        return boxedType(paramType) == boxedType(fieldType)
    }

    private fun boxedType(cls: Class<*>): Class<*> = when (cls) {
        Boolean::class.javaPrimitiveType -> java.lang.Boolean::class.java
        Byte::class.javaPrimitiveType -> java.lang.Byte::class.java
        Short::class.javaPrimitiveType -> java.lang.Short::class.java
        Char::class.javaPrimitiveType -> java.lang.Character::class.java
        Int::class.javaPrimitiveType -> java.lang.Integer::class.java
        Long::class.javaPrimitiveType -> java.lang.Long::class.java
        Float::class.javaPrimitiveType -> java.lang.Float::class.java
        Double::class.javaPrimitiveType -> java.lang.Double::class.java
        else -> cls
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
                if (element.isNumber) element.asInt().toChar() else element.asString()?.firstOrNull() ?: '\u0000'
            rawType.isEnum -> {
                val name = element.asString() ?: return fallbackValue
                resolveEnumConstant((rawType as Class<Any>).kotlin, name, fallbackValue)
            }
            Set::class.java.isAssignableFrom(rawType) -> {
                if (!element.isArray) return fallbackValue
                val itemType = resolveTypeArgument(genericType, 0)
                val itemClass = resolveRawClass(itemType)
                val set = LinkedHashSet<Any?>(element.arrayElements.size)
                for (item in element.arrayElements) {
                    set.add(
                        if (itemClass != null && itemClass != Any::class.java) {
                            convertElementToJavaType(item, itemClass, itemType, null)
                        } else {
                            item.toPlainValue()
                        }
                    )
                }
                set
            }
            List::class.java.isAssignableFrom(rawType) || Collection::class.java.isAssignableFrom(rawType) -> {
                if (!element.isArray) return fallbackValue
                val itemType = resolveTypeArgument(genericType, 0)
                val itemClass = resolveRawClass(itemType)
                val list = ArrayList<Any?>(element.arrayElements.size)
                for (item in element.arrayElements) {
                    list.add(
                        if (itemClass != null && itemClass != Any::class.java) {
                            convertElementToJavaType(item, itemClass, itemType, null)
                        } else {
                            item.toPlainValue()
                        }
                    )
                }
                list
            }
            Map::class.java.isAssignableFrom(rawType) -> {
                if (!element.isMap && !element.isObject) return fallbackValue
                val keyType = resolveTypeArgument(genericType, 0)
                val keyClass = resolveRawClass(keyType)
                val valueType = resolveTypeArgument(genericType, 1)
                val valueClass = resolveRawClass(valueType)
                val map = LinkedHashMap<Any?, Any?>(element.mapEntries.size)
                for ((k, v) in element.mapEntries) {
                    val convertedKey = convertMapKey(k, keyClass)
                    map[convertedKey] = if (valueClass != null && valueClass != Any::class.java) {
                        convertElementToJavaType(v, valueClass, valueType, null)
                    } else {
                        v.toPlainValue()
                    }
                }
                map
            }
            rawType == ByteArray::class.java -> when {
                element.isBytes -> element.bytesOrNull() ?: ByteArray(0)
                element.isArray -> ByteArray(element.arrayElements.size) { element.arrayElements[it].asByte() }
                else -> fallbackValue
            }
            rawType == ShortArray::class.java -> if (element.isArray) {
                ShortArray(element.arrayElements.size) { element.arrayElements[it].asShort() }
            } else {
                fallbackValue
            }
            rawType == IntArray::class.java -> if (element.isArray) {
                IntArray(element.arrayElements.size) { element.arrayElements[it].asInt() }
            } else {
                fallbackValue
            }
            rawType == LongArray::class.java -> if (element.isArray) {
                LongArray(element.arrayElements.size) { element.arrayElements[it].asLong() }
            } else {
                fallbackValue
            }
            rawType == FloatArray::class.java -> if (element.isArray) {
                FloatArray(element.arrayElements.size) { element.arrayElements[it].asFloat() }
            } else {
                fallbackValue
            }
            rawType == DoubleArray::class.java -> if (element.isArray) {
                DoubleArray(element.arrayElements.size) { element.arrayElements[it].asDouble() }
            } else {
                fallbackValue
            }
            rawType == BooleanArray::class.java -> if (element.isArray) {
                BooleanArray(element.arrayElements.size) { element.arrayElements[it].asBoolean() }
            } else {
                fallbackValue
            }
            rawType == CharArray::class.java -> when {
                element.isString -> (element.stringValue ?: "").toCharArray()
                element.isArray -> CharArray(element.arrayElements.size) { idx ->
                    val item = element.arrayElements[idx]
                    if (item.isNumber) item.asInt().toChar() else item.asString()?.firstOrNull() ?: '\u0000'
                }
                else -> fallbackValue
            }
            rawType.isArray -> {
                if (!element.isArray) return fallbackValue
                val unwrappedGeneric = unwrapType(genericType)
                val compGenericType = if (unwrappedGeneric is GenericArrayType) {
                    unwrapType(unwrappedGeneric.genericComponentType)
                } else {
                    rawType.componentType
                }
                val compType = rawType.componentType ?: resolveRawClass(compGenericType) ?: Any::class.java
                val arr = java.lang.reflect.Array.newInstance(compType, element.arrayElements.size)
                for (i in element.arrayElements.indices) {
                    val itemVal = convertElementToJavaType(element.arrayElements[i], compType, compGenericType, null)
                    java.lang.reflect.Array.set(arr, i, itemVal)
                }
                arr
            }
            rawType == Any::class.java -> element.toPlainValue()
            else -> PreferenceObjectCodec.fromElement(element, (rawType as Class<Any>).kotlin, fallbackValue)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertMapKey(key: String, keyClass: Class<*>?): Any = when {
        keyClass == null || keyClass == String::class.java || keyClass == CharSequence::class.java || keyClass == Any::class.java -> key
        keyClass.isEnum -> resolveEnumConstant((keyClass as Class<Any>).kotlin, key, key) ?: key
        keyClass == Int::class.javaObjectType || keyClass == Int::class.javaPrimitiveType -> key.toIntOrNull() ?: key
        keyClass == Long::class.javaObjectType || keyClass == Long::class.javaPrimitiveType -> key.toLongOrNull() ?: key
        keyClass == Short::class.javaObjectType || keyClass == Short::class.javaPrimitiveType -> key.toShortOrNull() ?: key
        keyClass == Byte::class.javaObjectType || keyClass == Byte::class.javaPrimitiveType -> key.toByteOrNull() ?: key
        keyClass == Boolean::class.javaObjectType || keyClass == Boolean::class.javaPrimitiveType -> key.toBooleanStrictOrNull() ?: key
        else -> key
    }

    private fun unwrapType(type: Type?): Type? = when (type) {
        is WildcardType -> {
            val upper = type.upperBounds.firstOrNull()
            if (upper != null && upper != Any::class.java && upper != Object::class.java) {
                unwrapType(upper)
            } else {
                type.lowerBounds.firstOrNull()?.let { unwrapType(it) } ?: upper
            }
        }
        is TypeVariable<*> -> type.bounds.firstOrNull()?.let { unwrapType(it) }
        else -> type
    }

    private fun resolveRawClass(type: Type?): Class<*>? = when (val unwrapped = unwrapType(type)) {
        is Class<*> -> unwrapped
        is ParameterizedType -> unwrapped.rawType as? Class<*>
        is GenericArrayType -> {
            val comp = resolveRawClass(unwrapped.genericComponentType)
            if (comp != null) java.lang.reflect.Array.newInstance(comp, 0).javaClass else null
        }
        else -> null
    }

    private fun resolveTypeArgument(type: Type?, index: Int): Type? {
        val unwrapped = unwrapType(type) as? ParameterizedType ?: return null
        val arg = unwrapped.actualTypeArguments.getOrNull(index) ?: return null
        return unwrapType(arg)
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
        type == ByteArray::class.java -> ByteArray(0)
        type == ShortArray::class.java -> ShortArray(0)
        type == IntArray::class.java -> IntArray(0)
        type == LongArray::class.java -> LongArray(0)
        type == FloatArray::class.java -> FloatArray(0)
        type == DoubleArray::class.java -> DoubleArray(0)
        type == BooleanArray::class.java -> BooleanArray(0)
        type == CharArray::class.java -> CharArray(0)
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
