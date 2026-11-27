package com.nexustools.genericui.component.system

import com.nexustools.genericui.threading.PlatformReentrantLock
import com.nexustools.genericui.threading.withLock
import kotlin.reflect.KClass

/**
 * Thread-safe multiplatform registry mapping `@BinarySerializable` (and manually registered)
 * Kotlin classes to their reflection-free [DataClassCodec] implementations.
 */
object DataClassCodecRegistry {

    private val lock = PlatformReentrantLock()
    private val codecs = mutableMapOf<KClass<*>, DataClassCodec<*>>()

    fun <T : Any> register(type: KClass<T>, codec: DataClassCodec<T>) {
        lock.withLock {
            codecs[type] = codec
        }
    }

    inline fun <reified T : Any> register(codec: DataClassCodec<T>) {
        register(T::class, codec)
    }

    fun unregister(type: KClass<*>) {
        lock.withLock {
            codecs.remove(type)
        }
    }

    inline fun <reified T : Any> unregister() {
        unregister(T::class)
    }

    fun hasCodec(type: KClass<*>): Boolean =
        lock.withLock { codecs.containsKey(type) }

    inline fun <reified T : Any> hasCodec(): Boolean =
        hasCodec(T::class)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> codecFor(type: KClass<T>): DataClassCodec<T>? =
        lock.withLock { codecs[type] as? DataClassCodec<T> }

    @Suppress("UNCHECKED_CAST")
    fun encode(value: Any): PreferenceElement? {
        val codec = lock.withLock { codecs[value::class] as? DataClassCodec<Any> } ?: return null
        return codec.encode(value)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> encode(value: T, type: KClass<T>): PreferenceElement? {
        val codec = lock.withLock {
            (codecs[type] ?: codecs[value::class]) as? DataClassCodec<T>
        } ?: return null
        return codec.encode(value)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> decode(type: KClass<T>, element: PreferenceElement): T? {
        if (element.isNull) return null
        val codec = lock.withLock { codecs[type] as? DataClassCodec<T> } ?: return null
        return codec.decode(element)
    }
}
