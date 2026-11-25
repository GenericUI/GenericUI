package com.nexustools.genericui.animation

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.nativeimpl.system.DefaultAnimationNativeImpl
import com.nexustools.genericui.style.transition.CssTimingFunction
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimatorAndTransitionTest {

    private class RecordingAnimationScheduler : DefaultAnimationNativeImpl() {
        val vblankActiveTransitions = mutableListOf<Boolean>()
        var nativeFrameRequests: Int = 0
            private set

        override fun onActiveVBlankWorkChanged(hasActiveWork: Boolean) {
            vblankActiveTransitions.add(hasActiveWork)
        }

        override fun onRequestNativeFrame() {
            nativeFrameRequests++
        }
    }

    private lateinit var scheduler: RecordingAnimationScheduler

    @BeforeTest
    fun setUp() {
        scheduler = RecordingAnimationScheduler()
        Animator.customScheduler = scheduler
    }

    @AfterTest
    fun tearDown() {
        Animator.cancelAllAnimations()
        Animator.customScheduler = null
        scheduler.dispose()
    }

    @Test
    fun testAnimateAndAnimateFloatProgressWithCompletionAndIdleVBlankToggle() {
        val values = mutableListOf<Float>()
        var completed = false
        var wasCancelled: Boolean? = null

        val handle = Animator.animateFloat(
            from = 10f,
            to = 110f,
            durationMs = 200,
            timingFunction = CssTimingFunction.LINEAR,
            onUpdate = { v -> values.add(v) },
            onComplete = { cancelled ->
                completed = true
                wasCancelled = cancelled
            }
        )

        assertTrue(handle.isRunning)
        assertTrue(scheduler.vblankActiveTransitions.contains(true))

        Animator.advanceTimeBy(100.0)
        assertTrue(values.isNotEmpty())
        assertTrue(abs(values.last() - 60f) < 0.01f, "Expected ~60f at midpoint, got ${values.last()}")
        assertFalse(completed)

        Animator.advanceTimeBy(100.0)
        assertEquals(110f, values.last())
        assertTrue(completed)
        assertEquals(false, wasCancelled)
        assertFalse(handle.isRunning)
        assertEquals(false, scheduler.vblankActiveTransitions.last())
    }

    @Test
    fun testCancelAnimationStopsWithoutJumpingToFinalValue() {
        val values = mutableListOf<Float>()
        var wasCancelled: Boolean? = null

        val handle = Animator.animateFloat(
            from = 0f,
            to = 1f,
            durationMs = 200,
            timingFunction = CssTimingFunction.LINEAR,
            onUpdate = { v -> values.add(v) },
            onComplete = { cancelled -> wasCancelled = cancelled }
        )

        Animator.advanceTimeBy(50.0)
        val atQuarter = values.last()
        assertTrue(abs(atQuarter - 0.25f) < 0.01f, "Expected ~0.25f, got $atQuarter")

        handle.cancel()
        assertFalse(handle.isRunning)
        assertEquals(true, wasCancelled)
        assertEquals(atQuarter, values.last())
        assertEquals(false, scheduler.vblankActiveTransitions.last())

        val countAfterCancel = values.size
        Animator.advanceTimeBy(200.0)
        assertEquals(countAfterCancel, values.size)
    }

    @Test
    fun testRequestAnimationFrameCancelAndWatchVBlank() {
        val rafTimestamps = mutableListOf<Double>()
        val raf1 = Animator.requestAnimationFrame { ts -> rafTimestamps.add(ts) }
        val raf2 = Animator.requestAnimationFrame { ts -> rafTimestamps.add(ts + 1000.0) }
        assertTrue(raf1 > 0 && raf2 > 0)
        Animator.cancelAnimationFrame(raf2)

        val watcherDeltas = mutableListOf<Double>()
        val watcherFrames = mutableListOf<Long>()
        val watcherId = Animator.watchVBlank { deltaMs, _, frameNumber ->
            watcherDeltas.add(deltaMs)
            watcherFrames.add(frameNumber)
        }

        Animator.stepFrame(16.0)
        assertEquals(listOf(16.0), rafTimestamps)
        assertEquals(listOf(16.0), watcherDeltas)
        assertEquals(listOf(1L), watcherFrames)

        Animator.stepFrame(8.0)
        assertEquals(1, rafTimestamps.size, "One-shot requestAnimationFrame must fire only once")
        assertEquals(listOf(16.0, 8.0), watcherDeltas)
        assertEquals(listOf(1L, 2L), watcherFrames)

        Animator.unwatchVBlank(watcherId)
        Animator.stepFrame(16.0)
        assertEquals(2, watcherDeltas.size)
        assertEquals(false, scheduler.vblankActiveTransitions.last())
    }

    @Test
    fun testAbstractComponentAnimateAutoCancelsOnDispose() {
        val comp = Container()
        val progressUpdates = mutableListOf<Float>()
        var cancelledFlag: Boolean? = null

        val handle = comp.animate(
            durationMs = 200,
            timingFunction = CssTimingFunction.LINEAR,
            onUpdate = { p -> progressUpdates.add(p) },
            onComplete = { cancelled -> cancelledFlag = cancelled }
        )
        assertTrue(handle.isRunning)

        Animator.advanceTimeBy(40.0)
        assertTrue(progressUpdates.isNotEmpty())
        assertTrue(abs(progressUpdates.last() - 0.2f) < 0.01f)

        comp.dispose()
        assertFalse(handle.isRunning)
        assertEquals(true, cancelledFlag)

        val updatesAtDispose = progressUpdates.size
        Animator.advanceTimeBy(200.0)
        assertEquals(updatesAtDispose, progressUpdates.size)
    }

    @Test
    fun testCssTransitionControllerAutoVBlankSteppingForOpacity() {
        val comp = Container()
        comp.transitionController.updateTransitionCss("opacity 200ms linear")
        comp.opacity = 1f
        comp.opacity = 0f

        assertTrue(
            comp.transitionController.hasActiveSoftwareTransition("opacity"),
            "Setting opacity with an active transition spec should start software VBlank transition"
        )
        assertEquals(true, scheduler.vblankActiveTransitions.last())

        Animator.advanceTimeBy(100.0)
        val midOpacity = comp.transitionController.currentFrameState.opacity
        assertTrue(midOpacity in 0.45f..0.55f, "Expected ~0.5f mid-transition opacity, got $midOpacity")

        Animator.flushAll()
        assertEquals(0f, comp.transitionController.currentFrameState.opacity)
        assertFalse(comp.transitionController.hasActiveSoftwareTransition("opacity"))
        assertEquals(false, scheduler.vblankActiveTransitions.last())
    }

    @Test
    fun testTabContainerFadeTransitionUsesAnimator() {
        val tabs = TabContainer(fadeTransition = true)
        val content0 = Container()
        val content1 = Container()
        tabs.addTab(title = "Tab 0", content = content0)
        tabs.addTab(title = "Tab 1", content = content1)

        tabs.selectedIndex = 1
        assertEquals(0f, content1.opacity)

        Animator.advanceTimeBy(90.0)
        assertTrue(
            content1.opacity > 0.05f && content1.opacity < 0.99f,
            "Expected TabContainer fade-in to be partially progressed at 90ms, got ${content1.opacity}"
        )

        Animator.flushAll()
        assertEquals(1f, content1.opacity)
    }
}
