package com.nexustools.genericui.jvm

import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared coroutine I/O dispatcher helper for JVM and Android (`minSdk >= 24`) backends.
 *
 * Switches blocking I/O onto [Dispatchers.IO] whenever the caller executes inside a
 * [ContinuationInterceptor] or on a toolkit UI main thread, while running inline when
 * invoked directly inside synchronous headless unit-test coroutines.
 */
object JvmIoExecutor {

    /**
     * Executes blocking [block] on [Dispatchers.IO] when running under a dispatcher or on the toolkit main thread.
     */
    suspend inline fun <T> runOnIo(
        noinline isOnMainThread: () -> Boolean = { false },
        crossinline block: () -> T
    ): T {
        val hasInterceptor = coroutineContext[ContinuationInterceptor] != null
        return if (hasInterceptor || isOnMainThread()) {
            withContext(Dispatchers.IO) { block() }
        } else {
            block()
        }
    }

    /**
     * Executes suspending [block] on [Dispatchers.IO] when running under a dispatcher or on the toolkit main thread.
     */
    suspend fun <T> runOnIoSuspend(
        isOnMainThread: () -> Boolean = { false },
        block: suspend () -> T
    ): T {
        val hasInterceptor = coroutineContext[ContinuationInterceptor] != null
        return if (hasInterceptor || isOnMainThread()) {
            withContext(Dispatchers.IO) { block() }
        } else {
            block()
        }
    }
}
