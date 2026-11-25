@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.threading

import kotlin.concurrent.atomics.AtomicInt
import kotlin.native.concurrent.ThreadLocal

private val nativeThreadTokenAllocLock = AtomicInt(0)
private var nextNativeThreadToken: Int = 1

@ThreadLocal
private var currentNativeThreadToken: Int = 0

private fun currentThreadToken(): Int {
    val existing = currentNativeThreadToken
    if (existing != 0) return existing
    while (!nativeThreadTokenAllocLock.compareAndSet(0, 1)) {
        // Short spin while allocating a unique token for this OS thread
    }
    val allocated = try {
        val tok = nextNativeThreadToken
        nextNativeThreadToken = if (tok == Int.MAX_VALUE) 1 else tok + 1
        tok
    } finally {
        nativeThreadTokenAllocLock.store(0)
    }
    currentNativeThreadToken = allocated
    return allocated
}

internal actual class PlatformReentrantLock actual constructor() {
    private val ownerToken = AtomicInt(0)
    private var recursionDepth: Int = 0

    actual fun lock() {
        val me = currentThreadToken()
        if (ownerToken.load() == me) {
            recursionDepth++
            return
        }
        while (!ownerToken.compareAndSet(0, me)) {
            // Short spin while peer thread completes widget realization/sync
        }
        recursionDepth = 1
    }

    actual fun unlock() {
        val me = currentThreadToken()
        if (ownerToken.load() != me) return
        recursionDepth--
        if (recursionDepth <= 0) {
            recursionDepth = 0
            ownerToken.store(0)
        }
    }
}
