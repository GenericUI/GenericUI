package com.nexustools.genericui.component.system

/**
 * Structured intermediate AST element representing any primitive, collection, map, or serialized object
 * stored in a [PreferenceStore] or encoded via `BinaryJson`, `BinaryPlist`, or JSON.
 */
class PreferenceElement(
    val kind: PreferenceElementKind,
    val booleanValue: Boolean? = null,
    val numberValue: Number? = null,
    val numberTypeTag: Byte = NUMBER_TAG_INT,
    val stringValue: String? = null,
    val arrayElements: List<PreferenceElement> = emptyList(),
    val mapEntries: Map<String, PreferenceElement> = emptyMap()
) {
    val isNull: Boolean
        get() = kind == PreferenceElementKind.NULL

    val isBoolean: Boolean
        get() = kind == PreferenceElementKind.BOOLEAN

    val isNumber: Boolean
        get() = kind == PreferenceElementKind.NUMBER

    val isString: Boolean
        get() = kind == PreferenceElementKind.STRING

    val isArray: Boolean
        get() = kind == PreferenceElementKind.ARRAY

    val isMap: Boolean
        get() = kind == PreferenceElementKind.MAP

    val isObject: Boolean
        get() = kind == PreferenceElementKind.OBJECT

    fun asBoolean(defaultValue: Boolean = false): Boolean = when (kind) {
        PreferenceElementKind.BOOLEAN -> booleanValue ?: defaultValue
        PreferenceElementKind.NUMBER -> (numberValue?.toInt() ?: 0) != 0
        PreferenceElementKind.STRING -> stringValue?.trim()?.lowercase()?.let {
            when (it) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> defaultValue
            }
        } ?: defaultValue
        else -> defaultValue
    }

    fun asByte(defaultValue: Byte = 0): Byte = when (kind) {
        PreferenceElementKind.NUMBER -> numberValue?.toByte() ?: defaultValue
        PreferenceElementKind.STRING -> stringValue?.trim()?.toByteOrNull() ?: defaultValue
        PreferenceElementKind.BOOLEAN -> if (booleanValue == true) 1 else 0
        else -> defaultValue
    }

    fun asShort(defaultValue: Short = 0): Short = when (kind) {
        PreferenceElementKind.NUMBER -> numberValue?.toShort() ?: defaultValue
        PreferenceElementKind.STRING -> stringValue?.trim()?.toShortOrNull() ?: defaultValue
        PreferenceElementKind.BOOLEAN -> if (booleanValue == true) 1 else 0
        else -> defaultValue
    }

    fun asInt(defaultValue: Int = 0): Int = when (kind) {
        PreferenceElementKind.NUMBER -> numberValue?.toInt() ?: defaultValue
        PreferenceElementKind.STRING -> stringValue?.trim()?.toIntOrNull()
            ?: stringValue?.trim()?.toDoubleOrNull()?.toInt()
            ?: defaultValue
        PreferenceElementKind.BOOLEAN -> if (booleanValue == true) 1 else 0
        else -> defaultValue
    }

    fun asLong(defaultValue: Long = 0L): Long = when (kind) {
        PreferenceElementKind.NUMBER -> numberValue?.toLong() ?: defaultValue
        PreferenceElementKind.STRING -> stringValue?.trim()?.toLongOrNull()
            ?: stringValue?.trim()?.toDoubleOrNull()?.toLong()
            ?: defaultValue
        PreferenceElementKind.BOOLEAN -> if (booleanValue == true) 1L else 0L
        else -> defaultValue
    }

    fun asFloat(defaultValue: Float = 0f): Float = when (kind) {
        PreferenceElementKind.NUMBER -> numberValue?.toFloat() ?: defaultValue
        PreferenceElementKind.STRING -> stringValue?.trim()?.toFloatOrNull() ?: defaultValue
        PreferenceElementKind.BOOLEAN -> if (booleanValue == true) 1f else 0f
        else -> defaultValue
    }

    fun asDouble(defaultValue: Double = 0.0): Double = when (kind) {
        PreferenceElementKind.NUMBER -> numberValue?.toDouble() ?: defaultValue
        PreferenceElementKind.STRING -> stringValue?.trim()?.toDoubleOrNull() ?: defaultValue
        PreferenceElementKind.BOOLEAN -> if (booleanValue == true) 1.0 else 0.0
        else -> defaultValue
    }

    fun asString(defaultValue: String? = null): String? = when (kind) {
        PreferenceElementKind.NULL -> defaultValue
        PreferenceElementKind.STRING -> stringValue ?: defaultValue
        PreferenceElementKind.BOOLEAN -> booleanValue?.toString() ?: defaultValue
        PreferenceElementKind.NUMBER -> numberValue?.toString() ?: defaultValue
        else -> defaultValue
    }

    operator fun get(key: String): PreferenceElement? =
        if (kind == PreferenceElementKind.MAP || kind == PreferenceElementKind.OBJECT) {
            mapEntries[key]
        } else {
            null
        }

    operator fun get(index: Int): PreferenceElement? =
        if (kind == PreferenceElementKind.ARRAY && index in arrayElements.indices) {
            arrayElements[index]
        } else {
            null
        }

    /**
     * Converts this [PreferenceElement] tree into standard Kotlin primitives, [List]s, and [LinkedHashMap]s.
     */
    fun toPlainValue(): Any? = when (kind) {
        PreferenceElementKind.NULL -> null
        PreferenceElementKind.BOOLEAN -> booleanValue ?: false
        PreferenceElementKind.NUMBER -> when (numberTypeTag) {
            NUMBER_TAG_BYTE -> numberValue?.toByte() ?: 0.toByte()
            NUMBER_TAG_SHORT -> numberValue?.toShort() ?: 0.toShort()
            NUMBER_TAG_INT -> numberValue?.toInt() ?: 0
            NUMBER_TAG_LONG -> numberValue?.toLong() ?: 0L
            NUMBER_TAG_FLOAT -> numberValue?.toFloat() ?: 0f
            NUMBER_TAG_DOUBLE -> numberValue?.toDouble() ?: 0.0
            else -> numberValue ?: 0
        }
        PreferenceElementKind.STRING -> stringValue ?: ""
        PreferenceElementKind.ARRAY -> arrayElements.map { it.toPlainValue() }
        PreferenceElementKind.MAP, PreferenceElementKind.OBJECT -> {
            val map = LinkedHashMap<String, Any?>(mapEntries.size)
            for ((k, v) in mapEntries) {
                map[k] = v.toPlainValue()
            }
            map
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreferenceElement) return false
        if (kind != other.kind) return false
        return when (kind) {
            PreferenceElementKind.NULL -> true
            PreferenceElementKind.BOOLEAN -> booleanValue == other.booleanValue
            PreferenceElementKind.NUMBER -> numberValue?.toDouble() == other.numberValue?.toDouble() &&
                numberValue?.toLong() == other.numberValue?.toLong()
            PreferenceElementKind.STRING -> stringValue == other.stringValue
            PreferenceElementKind.ARRAY -> arrayElements == other.arrayElements
            PreferenceElementKind.MAP, PreferenceElementKind.OBJECT -> mapEntries == other.mapEntries
        }
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + (booleanValue?.hashCode() ?: 0)
        result = 31 * result + (numberValue?.toDouble()?.hashCode() ?: 0)
        result = 31 * result + (stringValue?.hashCode() ?: 0)
        result = 31 * result + arrayElements.hashCode()
        result = 31 * result + mapEntries.hashCode()
        return result
    }

    override fun toString(): String = PreferenceObjectCodec.elementToJson(this)

    companion object {
        const val NUMBER_TAG_BYTE: Byte = 0x02
        const val NUMBER_TAG_SHORT: Byte = 0x03
        const val NUMBER_TAG_INT: Byte = 0x04
        const val NUMBER_TAG_LONG: Byte = 0x05
        const val NUMBER_TAG_FLOAT: Byte = 0x06
        const val NUMBER_TAG_DOUBLE: Byte = 0x07

        val Null: PreferenceElement = PreferenceElement(PreferenceElementKind.NULL)

        fun ofBoolean(value: Boolean): PreferenceElement =
            PreferenceElement(PreferenceElementKind.BOOLEAN, booleanValue = value)

        fun ofByte(value: Byte): PreferenceElement =
            PreferenceElement(PreferenceElementKind.NUMBER, numberValue = value, numberTypeTag = NUMBER_TAG_BYTE)

        fun ofShort(value: Short): PreferenceElement =
            PreferenceElement(PreferenceElementKind.NUMBER, numberValue = value, numberTypeTag = NUMBER_TAG_SHORT)

        fun ofInt(value: Int): PreferenceElement =
            PreferenceElement(PreferenceElementKind.NUMBER, numberValue = value, numberTypeTag = NUMBER_TAG_INT)

        fun ofLong(value: Long): PreferenceElement =
            PreferenceElement(PreferenceElementKind.NUMBER, numberValue = value, numberTypeTag = NUMBER_TAG_LONG)

        fun ofFloat(value: Float): PreferenceElement =
            PreferenceElement(PreferenceElementKind.NUMBER, numberValue = value, numberTypeTag = NUMBER_TAG_FLOAT)

        fun ofDouble(value: Double): PreferenceElement =
            PreferenceElement(PreferenceElementKind.NUMBER, numberValue = value, numberTypeTag = NUMBER_TAG_DOUBLE)

        fun ofNumber(value: Number): PreferenceElement = when (value) {
            is Byte -> ofByte(value)
            is Short -> ofShort(value)
            is Int -> ofInt(value)
            is Long -> ofLong(value)
            is Float -> ofFloat(value)
            is Double -> ofDouble(value)
            else -> ofDouble(value.toDouble())
        }

        fun ofString(value: String): PreferenceElement =
            PreferenceElement(PreferenceElementKind.STRING, stringValue = value)

        fun ofArray(elements: List<PreferenceElement>): PreferenceElement =
            PreferenceElement(PreferenceElementKind.ARRAY, arrayElements = elements.toList())

        fun ofMap(entries: Map<String, PreferenceElement>): PreferenceElement =
            PreferenceElement(PreferenceElementKind.MAP, mapEntries = LinkedHashMap(entries))

        fun ofObject(fields: Map<String, PreferenceElement>): PreferenceElement =
            PreferenceElement(PreferenceElementKind.OBJECT, mapEntries = LinkedHashMap(fields))
    }
}
