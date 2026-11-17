@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.nativeimpl.system.UiTimerNativeImpl
import platform.Foundation.NSDate
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runMode
import platform.darwin.DISPATCH_SOURCE_TYPE_TIMER
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_t
import platform.darwin.dispatch_resume
import platform.darwin.dispatch_source_cancel
import platform.darwin.dispatch_source_create
import platform.darwin.dispatch_source_set_event_handler
import platform.darwin.dispatch_source_set_timer
import platform.darwin.dispatch_source_t
import platform.darwin.dispatch_time
import platform.posix.usleep
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class AppleUiTimerNativeImpl(
    private val targetQueue: dispatch_queue_t = dispatch_get_main_queue()
) : UiTimerNativeImpl {
    private val lock = AppleReentrantLock()
    private val entries = LinkedHashMap<Int, TimerEntry>()
    private var nextTimerId = 1
    private var totalFirings = 0
    private var disposedState = false

    override val isDisposed: Boolean
        get() = lock.withLock { disposedState }

    val activeTimerCount: Int
        get() = lock.withLock { entries.size }

    override fun setTimeout(delayMs: Int, callback: () -> Unit): Int {
        return scheduleTimer(delayMs.coerceAtLeast(0), isInterval = false, callback = callback)
    }

    override fun setInterval(intervalMs: Int, callback: () -> Unit): Int {
        return scheduleTimer(intervalMs.coerceAtLeast(1), isInterval = true, callback = callback)
    }

    override fun clearTimer(timerId: Int) {
        val entry = lock.withLock {
            entries.remove(timerId)?.also { it.cancelled = true }
        }
        entry?.source?.let { dispatch_source_cancel(it) }
    }

    override fun clearAllTimers() {
        val snapshot = lock.withLock {
            val list = entries.values.toList()
            entries.clear()
            list.forEach { it.cancelled = true }
            list
        }
        for (entry in snapshot) {
            entry.source?.let { dispatch_source_cancel(it) }
        }
    }

    override fun advanceTimeBy(deltaMs: Int): Int {
        if (deltaMs <= 0 || isDisposed) return 0
        val beforeCount = lock.withLock { totalFirings }
        val deadline = TimeSource.Monotonic.markNow() + deltaMs.milliseconds
        val isMain = NSThread.isMainThread
        while (TimeSource.Monotonic.markNow() < deadline && !isDisposed) {
            val remainingMs = (deadline - TimeSource.Monotonic.markNow()).inWholeMilliseconds.coerceAtLeast(1L)
            val sliceSec = minOf(remainingMs, 10L).toDouble() / 1000.0
            if (isMain) {
                NSRunLoop.currentRunLoop.runMode(
                    NSDefaultRunLoopMode,
                    beforeDate = NSDate.dateWithTimeIntervalSinceNow(sliceSec)
                )
            } else {
                usleep((minOf(remainingMs, 10L) * 1000L).toUInt())
            }
        }
        if (isMain) {
            NSRunLoop.currentRunLoop.runMode(
                NSDefaultRunLoopMode,
                beforeDate = NSDate.dateWithTimeIntervalSinceNow(0.0)
            )
        }
        return lock.withLock { (totalFirings - beforeCount).coerceAtLeast(0) }
    }

    override fun flushAll(): Int {
        if (isDisposed) return 0
        val beforeCount = lock.withLock { totalFirings }
        val oneShots = lock.withLock {
            val list = entries.values.filter { !it.isInterval }
            for (e in list) {
                e.cancelled = true
                entries.remove(e.id)
            }
            list
        }
        for (entry in oneShots) {
            entry.source?.let { dispatch_source_cancel(it) }
            entry.callback.invoke()
            lock.withLock { totalFirings++ }
        }
        return lock.withLock { (totalFirings - beforeCount).coerceAtLeast(0) }
    }

    override fun dispose() {
        val alreadyDisposed = lock.withLock {
            if (disposedState) true else {
                disposedState = true
                false
            }
        }
        if (!alreadyDisposed) {
            clearAllTimers()
        }
    }

    private fun scheduleTimer(delayMs: Int, isInterval: Boolean, callback: () -> Unit): Int {
        if (isDisposed) return -1

        val timerId = lock.withLock { nextTimerId++ }
        val source = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0uL, 0uL, targetQueue)
            ?: return -1

        val entry = TimerEntry(
            id = timerId,
            isInterval = isInterval,
            callback = callback,
            source = source
        )
        lock.withLock {
            if (disposedState) {
                entry.cancelled = true
            } else {
                entries[timerId] = entry
            }
        }
        if (entry.cancelled) {
            dispatch_source_set_event_handler(source) {}
            dispatch_resume(source)
            dispatch_source_cancel(source)
            return -1
        }

        val delayNs = delayMs.toLong() * NSEC_PER_MSEC.toLong()
        val intervalNs = if (isInterval) delayMs.toULong() * NSEC_PER_MSEC else ULong.MAX_VALUE
        val leewayNs = minOf((delayNs.coerceAtLeast(1L) / 10L).toULong(), 5uL * NSEC_PER_MSEC)

        dispatch_source_set_timer(
            source = source,
            start = dispatch_time(DISPATCH_TIME_NOW, delayNs),
            interval = intervalNs,
            leeway = leewayNs
        )
        dispatch_source_set_event_handler(source) {
            val shouldRun = lock.withLock {
                if (entry.cancelled || disposedState) {
                    false
                } else {
                    if (!entry.isInterval) {
                        entry.cancelled = true
                        entries.remove(entry.id)
                    }
                    totalFirings++
                    true
                }
            }
            if (shouldRun) {
                if (!entry.isInterval) {
                    dispatch_source_cancel(source)
                }
                entry.callback.invoke()
            }
        }
        dispatch_resume(source)
        return timerId
    }

    private class TimerEntry(
        val id: Int,
        val isInterval: Boolean,
        val callback: () -> Unit,
        val source: dispatch_source_t
    ) {
        var cancelled: Boolean = false
    }
}
