package com.nexustools.genericui.nativeimpl.system

/**
 * Pure Kotlin Multiplatform implementation of [UiTimerNativeImpl] providing deterministic virtual-clock
 * timer tracking ([advanceTimeBy], [flushAll]) for headless tests and native targets.
 */
open class DefaultUiTimerNativeImpl : UiTimerNativeImpl {

    private class ScheduledTimerEntry(
        val id: Int,
        var dueAtMs: Long,
        val intervalMs: Int?,
        val callback: () -> Unit
    )

    private var nextTimerId: Int = 1
    private val entries = linkedMapOf<Int, ScheduledTimerEntry>()

    var currentTimeMs: Long = 0L
        private set

    override var isDisposed: Boolean = false
        protected set

    val activeTimerCount: Int
        get() = entries.size

    fun isTimerActive(timerId: Int): Boolean = entries.containsKey(timerId)

    override fun setTimeout(delayMs: Int, callback: () -> Unit): Int {
        if (isDisposed) return -1
        val id = nextTimerId++
        val normalizedDelay = delayMs.coerceAtLeast(0).toLong()
        entries[id] = ScheduledTimerEntry(
            id = id,
            dueAtMs = currentTimeMs + normalizedDelay,
            intervalMs = null,
            callback = callback
        )
        return id
    }

    override fun setInterval(intervalMs: Int, callback: () -> Unit): Int {
        if (isDisposed) return -1
        val id = nextTimerId++
        val normalizedInterval = intervalMs.coerceAtLeast(1)
        entries[id] = ScheduledTimerEntry(
            id = id,
            dueAtMs = currentTimeMs + normalizedInterval.toLong(),
            intervalMs = normalizedInterval,
            callback = callback
        )
        return id
    }

    override fun clearTimer(timerId: Int) {
        entries.remove(timerId)
    }

    override fun clearAllTimers() {
        entries.clear()
    }

    override fun advanceTimeBy(deltaMs: Int): Int {
        if (isDisposed) return 0
        val targetTimeMs = currentTimeMs + deltaMs.coerceAtLeast(0).toLong()
        var firedCount = 0
        var safetyCounter = 0
        while (safetyCounter < MAX_STEP_FIRINGS) {
            val nextEntry = entries.values
                .filter { it.dueAtMs <= targetTimeMs }
                .minWithOrNull(compareBy<ScheduledTimerEntry> { it.dueAtMs }.thenBy { it.id })
                ?: break
            safetyCounter++
            currentTimeMs = nextEntry.dueAtMs
            val interval = nextEntry.intervalMs
            if (interval != null) {
                nextEntry.dueAtMs = currentTimeMs + interval.toLong()
            } else {
                entries.remove(nextEntry.id)
            }
            nextEntry.callback.invoke()
            firedCount++
        }
        currentTimeMs = targetTimeMs
        return firedCount
    }

    override fun flushAll(): Int {
        if (isDisposed || entries.isEmpty()) return 0
        var totalFired = 0
        var pass = 0
        while (entries.isNotEmpty() && pass < MAX_FLUSH_PASSES) {
            pass++
            val snapshotIds = entries.keys.toList()
            val maxDue = entries.values.maxOfOrNull { it.dueAtMs } ?: currentTimeMs
            val delta = (maxDue - currentTimeMs).coerceAtLeast(0L).toInt()
            val firedInPass = advanceTimeBy(delta)
            totalFired += firedInPass
            val hasRemainingOneShot = entries.values.any { it.intervalMs == null }
            if (!hasRemainingOneShot || firedInPass == 0 || entries.keys.toList() == snapshotIds) {
                break
            }
        }
        return totalFired
    }

    override fun dispose() {
        isDisposed = true
        clearAllTimers()
    }

    companion object {
        private const val MAX_STEP_FIRINGS: Int = 10_000
        private const val MAX_FLUSH_PASSES: Int = 64
    }
}
