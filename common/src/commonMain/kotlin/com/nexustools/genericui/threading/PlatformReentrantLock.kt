package com.nexustools.genericui.threading

/**
 * Lightweight cross-platform reentrant mutual exclusion lock used internally to synchronize
 * lazy native widget realization and container child reconciliation across UI and logic threads.
 */
internal expect class PlatformReentrantLock() {
    fun lock()
    fun unlock()
}

internal inline fun <T> PlatformReentrantLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
