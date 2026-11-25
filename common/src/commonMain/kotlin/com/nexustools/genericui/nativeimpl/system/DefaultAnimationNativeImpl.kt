package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.animation.AnimationHandle
import com.nexustools.genericui.animation.AnimationSpec
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.threading.PlatformReentrantLock
import com.nexustools.genericui.threading.withLock
import kotlin.math.min

/**
 * Pure Kotlin Multiplatform implementation of [AnimationNativeImpl] providing:
 * - Deterministic virtual-clock stepping ([stepFrame], [advanceTimeBy], [flushAll]) for unit tests.
 * - Shared VBlank frame dispatching ([onNativeVBlankTick]) and optional native timed-animation hook
 *   ([startNativeTimedAnimation]) for platform subclasses (`NSAnimation` / `NSWindow.displayLink`,
 *   `UIViewPropertyAnimator` / `CADisplayLink`, `ValueAnimator` / `Choreographer`, Web Animations API /
 *   `requestAnimationFrame`, `DwmFlush`, `GdkFrameClock`, `QPauseAnimation` / `QWindow.requestUpdate`,
 *   and Swing monitor-synced VBlank).
 */
open class DefaultAnimationNativeImpl : AnimationNativeImpl {

    private class TimedEntry(
        val id: Int,
        val spec: AnimationSpec,
        val onUpdate: (Float) -> Unit,
        val onComplete: ((Boolean) -> Unit)?,
        var nativeDriven: Boolean = false,
        var nativeCancel: (() -> Unit)? = null,
        var elapsedMs: Double = 0.0,
        var settled: Boolean = false
    ) {
        val totalDurationMs: Double
            get() = (spec.delayMs.coerceAtLeast(0) + spec.durationMs.coerceAtLeast(0)).toDouble()

        fun rawProgress(): Float {
            val active = elapsedMs - spec.delayMs.coerceAtLeast(0).toDouble()
            if (active <= 0.0) return 0f
            val dur = spec.durationMs.coerceAtLeast(0).toDouble()
            if (dur <= 0.0) return 1f
            return (active / dur).toFloat().coerceIn(0f, 1f)
        }

        fun easedProgress(): Float = spec.timingFunction.evaluate(rawProgress())

        fun isFinished(): Boolean = elapsedMs >= totalDurationMs
    }

    private val lock = PlatformReentrantLock()
    private var nextId: Int = 1
    private val timedEntries = linkedMapOf<Int, TimedEntry>()
    private val rafCallbacks = linkedMapOf<Int, (Double) -> Unit>()
    private val vblankWatchers = linkedMapOf<Int, (Double, Double, Long) -> Unit>()

    private var currentTimeMs: Double = 0.0
    private var lastNativeTimestampMs: Double? = null
    private var frameCounter: Long = 0L
    private var lastNotifiedVBlankActive: Boolean = false

    override var isDisposed: Boolean = false
        protected set

    val activeAnimationCount: Int
        get() = lock.withLock { timedEntries.size }

    val activeVBlankConsumerCount: Int
        get() = lock.withLock {
            rafCallbacks.size + vblankWatchers.size + timedEntries.values.count { !it.nativeDriven && !it.settled }
        }

    /**
     * Subclasses override to start a platform-native timed animation (e.g. AppKit `NSAnimation`,
     * Android `ValueAnimator`, GTK `AdwTimedAnimation`, Web `Element.animate`, Windows `UIAnimation`).
     *
     * Return a non-null cancellation lambda when the platform object owns its frame loop; return `null`
     * to have [DefaultAnimationNativeImpl] advance this animation cleanly on every [onNativeVBlankTick].
     */
    protected open fun startNativeTimedAnimation(
        id: Int,
        spec: AnimationSpec,
        target: NativeImpl<*>?,
        onProgress: (easedProgress: Float) -> Unit,
        onFinished: (cancelled: Boolean) -> Unit
    ): (() -> Unit)? = null

    /**
     * Called when the number of active VBlank-driven animations, `requestAnimationFrame` callbacks, or
     * `watchVBlank` listeners transitions between `0` and `> 0`. Platform subclasses use this to start/resume
     * their OS display link when `true` and pause/park at 0% CPU when `false`.
     */
    protected open fun onActiveVBlankWorkChanged(hasActiveWork: Boolean) {}

    /**
     * Called by platform subclasses whenever `requestAnimationFrame(id, callback)` can optionally
     * register a platform handle, or they may rely purely on [onActiveVBlankWorkChanged] + [onNativeVBlankTick].
     */
    protected open fun onRequestNativeFrame() {}

    protected open fun onCancelNativeFrame(handle: Int) {}

    private fun computeActiveVBlankWorkLocked(): Boolean =
        !isDisposed && (
            rafCallbacks.isNotEmpty() ||
                vblankWatchers.isNotEmpty() ||
                timedEntries.values.any { !it.nativeDriven && !it.settled }
            )

    private fun checkAndNotifyVBlankState() {
        val notifyTarget: Boolean? = lock.withLock {
            val active = computeActiveVBlankWorkLocked()
            if (active != lastNotifiedVBlankActive) {
                lastNotifiedVBlankActive = active
                active
            } else {
                null
            }
        }
        if (notifyTarget != null) {
            onActiveVBlankWorkChanged(notifyTarget)
        }
    }

    private fun settleTimedEntry(id: Int, cancelled: Boolean, emitFinalValue: Boolean): TimedEntry? {
        val settledEntry = lock.withLock {
            val entry = timedEntries[id] ?: return@withLock null
            if (entry.settled) return@withLock null
            entry.settled = true
            timedEntries.remove(id)
            entry
        } ?: return null

        val nativeCancel = settledEntry.nativeCancel
        settledEntry.nativeCancel = null
        if (cancelled) {
            nativeCancel?.invoke()
        } else if (emitFinalValue) {
            runCatching { settledEntry.onUpdate(1f) }
        }
        runCatching { settledEntry.onComplete?.invoke(cancelled) }
        checkAndNotifyVBlankState()
        return settledEntry
    }

    override fun startAnimation(
        spec: AnimationSpec,
        target: NativeImpl<*>?,
        onUpdate: (progress: Float) -> Unit,
        onComplete: ((cancelled: Boolean) -> Unit)?
    ): AnimationHandle {
        val normalizedSpec = spec.copy(
            durationMs = spec.durationMs.coerceAtLeast(0),
            delayMs = spec.delayMs.coerceAtLeast(0)
        )
        if (isDisposed) {
            onComplete?.invoke(true)
            return FinishedAnimationHandle(-1)
        }
        if (normalizedSpec.durationMs == 0 && normalizedSpec.delayMs == 0) {
            onUpdate(1f)
            onComplete?.invoke(false)
            return FinishedAnimationHandle(lock.withLock { nextId++ })
        }

        val id = lock.withLock {
            val assigned = nextId++
            timedEntries[assigned] = TimedEntry(
                id = assigned,
                spec = normalizedSpec,
                onUpdate = onUpdate,
                onComplete = onComplete
            )
            assigned
        }

        val nativeCancel = startNativeTimedAnimation(
            id = id,
            spec = normalizedSpec,
            target = target,
            onProgress = { eased ->
                val activeEntry = lock.withLock {
                    timedEntries[id]?.takeIf { !it.settled }
                }
                if (activeEntry != null) {
                    activeEntry.onUpdate(eased.coerceIn(0f, 1f))
                }
            },
            onFinished = { cancelled ->
                settleTimedEntry(id = id, cancelled = cancelled, emitFinalValue = !cancelled)
            }
        )

        var alreadySettled = false
        lock.withLock {
            val entry = timedEntries[id]
            if (entry == null || entry.settled) {
                alreadySettled = true
            } else if (nativeCancel != null) {
                entry.nativeDriven = true
                entry.nativeCancel = nativeCancel
            }
        }
        if (alreadySettled) {
            nativeCancel?.invoke()
        } else {
            checkAndNotifyVBlankState()
        }

        return object : AnimationHandle {
            override val id: Int = id
            override val isRunning: Boolean
                get() = lock.withLock { timedEntries[id]?.let { !it.settled } == true }

            override fun cancel() {
                settleTimedEntry(id = id, cancelled = true, emitFinalValue = false)
            }
        }
    }

    override fun requestAnimationFrame(callback: (timestampMs: Double) -> Unit): Int {
        if (isDisposed) return -1
        val id = lock.withLock {
            val assigned = nextId++
            rafCallbacks[assigned] = callback
            assigned
        }
        checkAndNotifyVBlankState()
        onRequestNativeFrame()
        return id
    }

    override fun cancelAnimationFrame(handle: Int) {
        if (handle <= 0) return
        val removed = lock.withLock { rafCallbacks.remove(handle) != null }
        if (removed) {
            onCancelNativeFrame(handle)
            checkAndNotifyVBlankState()
        }
    }

    override fun watchVBlank(listener: (deltaMs: Double, timestampMs: Double, frameNumber: Long) -> Unit): Int {
        if (isDisposed) return -1
        val id = lock.withLock {
            val assigned = nextId++
            vblankWatchers[assigned] = listener
            assigned
        }
        checkAndNotifyVBlankState()
        return id
    }

    override fun unwatchVBlank(watcherId: Int) {
        if (watcherId <= 0) return
        val removed = lock.withLock { vblankWatchers.remove(watcherId) != null }
        if (removed) {
            checkAndNotifyVBlankState()
        }
    }

    private data class CancelSnapshot(
        val entriesToCancel: List<TimedEntry>,
        val rafIds: List<Int>
    )

    private data class FrameStepSnapshot(
        val timestampMs: Double,
        val deltaMs: Double,
        val currentFrameNumber: Long,
        val pendingRafs: List<(Double) -> Unit>,
        val activeTimersSnapshot: List<TimedEntry>,
        val watchersSnapshot: List<(Double, Double, Long) -> Unit>
    )

    override fun cancelAllAnimations() {
        val snapshot = lock.withLock {
            val entries = timedEntries.values.filter { !it.settled }
            for (entry in entries) {
                entry.settled = true
            }
            timedEntries.clear()
            val rafs = rafCallbacks.keys.toList()
            rafCallbacks.clear()
            vblankWatchers.clear()
            lastNativeTimestampMs = null
            CancelSnapshot(entriesToCancel = entries, rafIds = rafs)
        }
        for (rafId in snapshot.rafIds) {
            onCancelNativeFrame(rafId)
        }
        for (entry in snapshot.entriesToCancel) {
            entry.nativeCancel?.invoke()
            entry.nativeCancel = null
            runCatching { entry.onComplete?.invoke(true) }
        }
        checkAndNotifyVBlankState()
    }

    /**
     * Entry point called by platform VBlank drivers (CADisplayLink, Choreographer, requestAnimationFrame,
     * DwmFlush, GdkFrameClock, QUnifiedTimer, Swing nano pacer).
     */
    protected fun onNativeVBlankTick(timestampMs: Double): Int =
        stepInternal(explicitTimestampMs = timestampMs, explicitDeltaMs = null, stepNativeDriven = false)

    override fun stepFrame(deltaMs: Double): Int =
        stepInternal(explicitTimestampMs = null, explicitDeltaMs = deltaMs.coerceAtLeast(0.0), stepNativeDriven = true)

    override fun advanceTimeBy(deltaMs: Double): Int {
        if (isDisposed) return 0
        var remaining = deltaMs.coerceAtLeast(0.0)
        if (remaining == 0.0) {
            return stepFrame(0.0)
        }
        var totalDispatched = 0
        var safety = 0
        while (remaining > 0.0 && safety < MAX_TEST_STEP_SLICES) {
            val hasWork = lock.withLock {
                rafCallbacks.isNotEmpty() || vblankWatchers.isNotEmpty() || timedEntries.isNotEmpty()
            }
            if (!hasWork) {
                lock.withLock { currentTimeMs += remaining }
                break
            }
            val slice = min(16.0, remaining)
            totalDispatched += stepFrame(slice)
            remaining -= slice
            safety++
        }
        return totalDispatched
    }

    override fun flushAll(): Int {
        if (isDisposed) return 0
        var totalDispatched = 0
        var pass = 0
        var vblankOnlyPasses = 0
        while (pass < MAX_FLUSH_SLICES) {
            val stepMs = lock.withLock {
                val anyRaf = rafCallbacks.isNotEmpty()
                val anyWatchers = vblankWatchers.isNotEmpty()
                val maxRem = timedEntries.values
                    .filter { !it.settled }
                    .maxOfOrNull { (it.totalDurationMs - it.elapsedMs).coerceAtLeast(0.0) }
                when {
                    maxRem != null -> min(16.0, maxRem.coerceAtLeast(1.0))
                    anyRaf -> 16.0
                    anyWatchers && vblankOnlyPasses < MAX_VBLANK_ONLY_FLUSH_SLICES -> {
                        vblankOnlyPasses++
                        16.0
                    }
                    else -> null
                }
            } ?: break
            totalDispatched += stepFrame(stepMs)
            pass++
        }
        return totalDispatched
    }

    private fun stepInternal(
        explicitTimestampMs: Double?,
        explicitDeltaMs: Double?,
        stepNativeDriven: Boolean
    ): Int {
        if (isDisposed) return 0

        val snapshot = lock.withLock {
            val timestampMs: Double
            val deltaMs: Double
            if (explicitTimestampMs != null) {
                timestampMs = explicitTimestampMs
                val fallbackStep = getRefreshRateHz().takeIf { it > 0.0 }?.let { 1000.0 / it } ?: 16.667
                deltaMs = lastNativeTimestampMs?.let { (timestampMs - it).coerceIn(0.0, MAX_DELTA_MS) }
                    ?: fallbackStep
                lastNativeTimestampMs = timestampMs
                currentTimeMs = timestampMs
            } else {
                deltaMs = explicitDeltaMs ?: 16.667
                currentTimeMs += deltaMs
                timestampMs = currentTimeMs
            }
            frameCounter += 1L
            val currentFrameNumber = frameCounter

            val pendingRafs = if (rafCallbacks.isEmpty()) emptyList() else rafCallbacks.values.toList()
            rafCallbacks.clear()

            val activeTimersSnapshot = timedEntries.values.filter { !it.settled && (stepNativeDriven || !it.nativeDriven) }
            val watchersSnapshot = if (vblankWatchers.isEmpty()) emptyList() else vblankWatchers.values.toList()
            FrameStepSnapshot(
                timestampMs = timestampMs,
                deltaMs = deltaMs,
                currentFrameNumber = currentFrameNumber,
                pendingRafs = pendingRafs,
                activeTimersSnapshot = activeTimersSnapshot,
                watchersSnapshot = watchersSnapshot
            )
        }

        var dispatched = 0

        for (cb in snapshot.pendingRafs) {
            dispatched++
            cb(snapshot.timestampMs)
        }

        val completedIds = ArrayList<Int>()
        for (entry in snapshot.activeTimersSnapshot) {
            if (entry.settled) continue
            entry.elapsedMs += snapshot.deltaMs
            if (entry.isFinished()) {
                completedIds.add(entry.id)
            } else {
                val activeTime = entry.elapsedMs - entry.spec.delayMs.coerceAtLeast(0).toDouble()
                if (activeTime >= 0.0) {
                    dispatched++
                    entry.onUpdate(entry.easedProgress())
                }
            }
        }
        for (doneId in completedIds) {
            if (settleTimedEntry(id = doneId, cancelled = false, emitFinalValue = true) != null) {
                dispatched++
            }
        }

        for (watcher in snapshot.watchersSnapshot) {
            dispatched++
            watcher(snapshot.deltaMs, snapshot.timestampMs, snapshot.currentFrameNumber)
        }

        checkAndNotifyVBlankState()
        return dispatched
    }

    override fun dispose() {
        isDisposed = true
        cancelAllAnimations()
    }

    private class FinishedAnimationHandle(override val id: Int) : AnimationHandle {
        override val isRunning: Boolean = false
        override fun cancel() {}
    }

    companion object {
        private const val MAX_DELTA_MS: Double = 250.0
        private const val MAX_TEST_STEP_SLICES: Int = 4_000
        private const val MAX_FLUSH_SLICES: Int = 4_000
        private const val MAX_VBLANK_ONLY_FLUSH_SLICES: Int = 128
    }
}
