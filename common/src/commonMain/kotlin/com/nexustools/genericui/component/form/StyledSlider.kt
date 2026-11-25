package com.nexustools.genericui.component.form

import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.surface.StyledComponent
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerDragManager
import com.nexustools.genericui.event.PointerDragSession
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.style.css.CssLength
import kotlin.math.abs
import kotlin.math.round

/**
 * Reusable composite slider (`<genericui:styled-slider class="styled-slider">`) built strictly
 * out of GenericUI primitives ([StyledButton] + [StyledComponent]) inside a sealed [Composite]:
 * - [trackButton] (`.styled-slider-track`): interactive clickable/draggable rail
 * - [progressSegment] (`.styled-slider-progress`): visual fill bar (`pointer-events: none` via CSS)
 * - [knobButton] (`.styled-slider-knob`): interactive thumb handle
 *
 * All visual styling (colors, sizes, radii, shadows, `position`, `top`, `transform`, `z-index`,
 * `:hover`, `:disabled`) lives cleanly in `style.scss` / `theme.scss`; this class updates only the
 * runtime horizontal percentage geometry of [progressSegment] (`width: <pct>%`) and [knobButton]
 * (`left: <pct>%`).
 */
open class StyledSlider(
    min: Double = 0.0,
    max: Double = 100.0,
    value: Double = 0.0,
    step: Double = 0.0
) : Composite(classPrefix = DEFAULT_CLASS_PREFIX, tagName = DEFAULT_TAG_NAME) {

    protected val trackButton: StyledButton = StyledButton(
        text = "",
        icon = null,
        styledClassPrefix = TRACK_CLASS
    ).apply {
        addClass(TRACK_CLASS)
        setFocusable(false)
    }

    protected val progressSegment: StyledComponent = StyledComponent(
        classPrefix = PROGRESS_CLASS
    ).apply {
        addClass(PROGRESS_CLASS)
    }

    protected val knobButton: StyledButton = StyledButton(
        text = "",
        icon = null,
        styledClassPrefix = KNOB_CLASS
    ).apply {
        addClass(KNOB_CLASS)
    }

    override val focusDelegateChild: AbstractComponent
        get() = knobButton

    private var activeDragSession: PointerDragSession? = null
    private var dragStartedOnKnob: Boolean = false
    private var knobDragStartScreenX: Float = 0f
    private var knobDragStartFraction: Double = 0.0
    private var lastHandledKeyEvent: KeyEvent? = null
    private var lastSyncedPct: Double = -1.0

    private val valueChangedListeners = mutableListOf<(Double) -> Unit>()
    private val userScrubListeners = mutableListOf<(Double) -> Unit>()

    var onValueChanged: ((Double) -> Unit)? = null
    var onUserScrub: ((Double) -> Unit)? = null
    var onChange: ((Double) -> Unit)? = null
    var onScrubStateChanged: ((Boolean) -> Unit)? = null

    var min: Double = min
        set(newMin) {
            if (abs(field - newMin) < EPSILON) return
            field = newMin
            if (max < field) {
                max = field
            }
            val snapped = clampAndSnap(value)
            if (abs(snapped - value) >= EPSILON) {
                value = snapped
            } else {
                syncGeometryPercentages()
            }
        }

    var max: Double = max.coerceAtLeast(min)
        set(newMax) {
            val coerced = newMax.coerceAtLeast(min)
            if (abs(field - coerced) < EPSILON) return
            field = coerced
            val snapped = clampAndSnap(value)
            if (abs(snapped - value) >= EPSILON) {
                value = snapped
            } else {
                syncGeometryPercentages()
            }
        }

    var step: Double = step.coerceAtLeast(0.0)
        set(newStep) {
            val coerced = newStep.coerceAtLeast(0.0)
            if (abs(field - coerced) < EPSILON) return
            field = coerced
            val snapped = clampAndSnap(value)
            if (abs(snapped - value) >= EPSILON) {
                value = snapped
            }
        }

    var value: Double = 0.0
        set(newVal) {
            val snapped = clampAndSnap(newVal)
            val changed = abs(field - snapped) >= EPSILON
            if (!changed && lastSyncedPct >= 0.0) return
            field = snapped
            if (changed || lastSyncedPct < 0.0) {
                syncGeometryPercentages()
            }
            if (changed) {
                notifyValueChanged(snapped)
            }
        }

    var isScrubbing: Boolean = false
        private set

    val normalizedFraction: Double
        get() {
            val span = max - min
            if (span <= 0.0) return 0.0
            return ((value - min) / span).coerceIn(0.0, 1.0)
        }

    init {
        addClass(DEFAULT_CLASS_PREFIX)

        addCompositeChild(trackButton)
        addCompositeChild(progressSegment)
        addCompositeChild(knobButton)

        this.value = value
        syncGeometryPercentages()
        wireInteractionHandlers()
    }

    fun onValueChanged(listener: (Double) -> Unit) {
        if (listener !in valueChangedListeners) {
            valueChangedListeners.add(listener)
        }
    }

    fun onUserScrub(listener: (Double) -> Unit) {
        if (listener !in userScrubListeners) {
            userScrubListeners.add(listener)
        }
    }

    fun onChange(listener: (Double) -> Unit) {
        onValueChanged(listener)
    }

    fun removeOnValueChanged(listener: (Double) -> Unit): Boolean =
        valueChangedListeners.remove(listener)

    fun removeOnUserScrub(listener: (Double) -> Unit): Boolean =
        userScrubListeners.remove(listener)

    fun beginScrub() {
        if (!isInteractiveEnabled()) return
        if (!isScrubbing) {
            isScrubbing = true
            addClass(SCRUBBING_CLASS)
            onScrubStateChanged?.invoke(true)
        }
    }

    fun endScrub() {
        val wasScrubbing = isScrubbing
        activeDragSession = null
        dragStartedOnKnob = false
        if (wasScrubbing) {
            isScrubbing = false
            removeClass(SCRUBBING_CLASS)
            onScrubStateChanged?.invoke(false)
        }
    }

    fun beginKnobDrag() = beginScrub()

    fun endKnobDrag() = endScrub()

    internal fun internalTrackButton(): StyledButton = trackButton
    internal fun internalProgressSegment(): StyledComponent = progressSegment
    internal fun internalKnobButton(): StyledButton = knobButton

    private fun isInteractiveEnabled(): Boolean =
        isEnabled && !hasClass(DISABLED_CLASS)

    private fun isSliderFocused(): Boolean = isFocused

    private fun clampAndSnap(candidate: Double): Double {
        val span = max - min
        if (span <= 0.0) return min
        val clamped = candidate.coerceIn(min, max)
        if (step <= 0.0) return clamped
        val stepped = min + round((clamped - min) / step) * step
        return stepped.coerceIn(min, max)
    }

    private fun resolveTrackWidthPx(): Float {
        val trackW = trackButton.getBounds().width
        if (trackW > 0) return trackW.toFloat()
        val selfW = getBounds().width
        if (selfW > 0) return selfW.toFloat()
        val explicitW = trackButton.style.width?.resolvePx()
            ?: style.width?.resolvePx()
            ?: computedStyle?.width?.removeSuffix("px")?.trim()?.toFloatOrNull()
        if (explicitW != null && explicitW > 0f) return explicitW
        return DEFAULT_FALLBACK_WIDTH_PX
    }

    private fun resolveFractionFromTrackX(localX: Float): Double {
        val w = resolveTrackWidthPx()
        if (w <= 0f) return 0.0
        return (localX / w).toDouble().coerceIn(0.0, 1.0)
    }

    private fun scrubToFraction(fraction: Double) {
        if (!isInteractiveEnabled()) return
        val clamped = fraction.coerceIn(0.0, 1.0)
        val targetValue = clampAndSnap(min + clamped * (max - min))
        value = targetValue
        notifyUserScrub(value)
    }

    private fun resolvePointerScreenX(source: AbstractComponent, event: PointerEvent): Float =
        if (event.screenX != 0f || event.screenY != 0f) {
            event.screenX
        } else {
            source.getBounds().x.toFloat() + event.x
        }

    private fun wireInteractionHandlers() {
        val handleRailDown: (PointerEvent) -> Unit = { event ->
            if (isInteractiveEnabled()) {
                isFocused = true
                dragStartedOnKnob = false
                beginScrub()
                scrubToFraction(resolveFractionFromTrackX(event.x))
                activeDragSession = startPointerDrag(
                    initialEvent = event,
                    onDragMove = { moveEvent ->
                        if (isInteractiveEnabled() && isScrubbing) {
                            scrubToFraction(resolveFractionFromTrackX(moveEvent.x))
                        }
                    },
                    onDragEnd = { endEvent, cancelled ->
                        if (!cancelled &&
                            isInteractiveEnabled() &&
                            isScrubbing &&
                            (endEvent.screenX != 0f || endEvent.x != 0f)
                        ) {
                            scrubToFraction(resolveFractionFromTrackX(endEvent.x))
                        }
                        endScrub()
                    }
                )
            }
        }
        val handleRailMove: (PointerEvent) -> Unit = { event ->
            if (isInteractiveEnabled() && isScrubbing && !dragStartedOnKnob) {
                if (PointerDragManager.isDragging(this)) {
                    PointerDragManager.notifyGlobalPointerMove(
                        event,
                        coordinatesAreTargetLocal = (event.screenX == 0f && event.screenY == 0f)
                    )
                } else {
                    scrubToFraction(resolveFractionFromTrackX(event.x))
                }
            }
        }
        val handleRailUp: (PointerEvent) -> Unit = { event ->
            if (PointerDragManager.isDragging(this)) {
                PointerDragManager.notifyGlobalPointerUp(
                    event,
                    coordinatesAreTargetLocal = (event.screenX == 0f && event.screenY == 0f)
                )
            } else if (isScrubbing && !dragStartedOnKnob) {
                scrubToFraction(resolveFractionFromTrackX(event.x))
                endScrub()
            }
        }

        trackButton.onPointerDown = handleRailDown
        trackButton.onPointerMove = handleRailMove
        trackButton.onPointerUp = handleRailUp

        this.onPointerDown = handleRailDown
        this.onPointerMove = handleRailMove
        this.onPointerUp = handleRailUp

        knobButton.onPointerDown = { event ->
            if (isInteractiveEnabled()) {
                knobButton.isFocused = true
                dragStartedOnKnob = true
                knobDragStartScreenX = resolvePointerScreenX(knobButton, event)
                knobDragStartFraction = normalizedFraction
                beginScrub()
                activeDragSession = startPointerDrag(
                    initialEvent = event,
                    onDragMove = { moveEvent ->
                        if (isInteractiveEnabled() && isScrubbing) {
                            val w = resolveTrackWidthPx()
                            val curX = resolvePointerScreenX(this, moveEvent)
                            val deltaFraction = if (w > 0f) ((curX - knobDragStartScreenX) / w).toDouble() else 0.0
                            scrubToFraction(knobDragStartFraction + deltaFraction)
                        }
                    },
                    onDragEnd = { endEvent, cancelled ->
                        if (!cancelled &&
                            isInteractiveEnabled() &&
                            isScrubbing &&
                            (endEvent.screenX != 0f || endEvent.screenY != 0f || endEvent.x != 0f)
                        ) {
                            val w = resolveTrackWidthPx()
                            val curX = resolvePointerScreenX(this, endEvent)
                            val deltaFraction = if (w > 0f) ((curX - knobDragStartScreenX) / w).toDouble() else 0.0
                            scrubToFraction(knobDragStartFraction + deltaFraction)
                        }
                        endScrub()
                    }
                )
            }
        }
        knobButton.onPointerMove = { event ->
            if (isInteractiveEnabled() && isScrubbing && dragStartedOnKnob) {
                if (PointerDragManager.isDragging(this)) {
                    PointerDragManager.notifyGlobalPointerMove(event, coordinatesAreTargetLocal = false)
                } else {
                    val w = resolveTrackWidthPx()
                    val deltaFraction = if (w > 0f) ((resolvePointerScreenX(knobButton, event) - knobDragStartScreenX) / w).toDouble() else 0.0
                    scrubToFraction(knobDragStartFraction + deltaFraction)
                }
            }
        }
        knobButton.onPointerUp = { event ->
            if (PointerDragManager.isDragging(this)) {
                PointerDragManager.notifyGlobalPointerUp(event, coordinatesAreTargetLocal = false)
            } else if (isScrubbing && dragStartedOnKnob) {
                val w = resolveTrackWidthPx()
                val deltaFraction = if (w > 0f) ((resolvePointerScreenX(knobButton, event) - knobDragStartScreenX) / w).toDouble() else 0.0
                scrubToFraction(knobDragStartFraction + deltaFraction)
                endScrub()
            }
        }

        val handleKey: (KeyEvent) -> Unit = { ev ->
            handleSliderKeyDown(ev)
        }
        this.onKeyDown = handleKey
        knobButton.onKeyDown = handleKey
        trackButton.onKeyDown = handleKey
    }

    private fun handleSliderKeyDown(ev: KeyEvent) {
        if (!isInteractiveEnabled() || !isSliderFocused()) return
        if (lastHandledKeyEvent === ev) return
        lastHandledKeyEvent = ev
        val span = max - min
        if (span <= 0.0) return
        val fineStep = if (step > 0.0) step else span / 100.0
        val pageStep = if (step > 0.0) maxOf(step * 10.0, span / 10.0) else span / 10.0
        val nextValue = when (ev.key) {
            "ArrowRight", "ArrowUp" -> value + fineStep
            "ArrowLeft", "ArrowDown" -> value - fineStep
            "PageUp" -> value + pageStep
            "PageDown" -> value - pageStep
            "Home" -> min
            "End" -> max
            else -> return
        }
        val clampedNext = clampAndSnap(nextValue)
        if (abs(clampedNext - value) >= EPSILON) {
            value = clampedNext
            notifyUserScrub(value)
        }
    }

    protected fun syncGeometryPercentages() {
        val pct = (normalizedFraction * 100.0).coerceIn(0.0, 100.0)
        if (abs(pct - lastSyncedPct) < EPSILON) return
        lastSyncedPct = pct
        val pctFloat = pct.toFloat()
        progressSegment.style.setWidth(CssLength.percent(pctFloat))
        knobButton.style.setLeft(CssLength.percent(pctFloat))
    }

    private fun notifyValueChanged(newValue: Double) {
        onValueChanged?.invoke(newValue)
        onChange?.invoke(newValue)
        for (listener in valueChangedListeners.toList()) {
            listener(newValue)
        }
    }

    private fun notifyUserScrub(newValue: Double) {
        onUserScrub?.invoke(newValue)
        for (listener in userScrubListeners.toList()) {
            listener(newValue)
        }
    }

    override fun dispose() {
        activeDragSession?.cancel()
        activeDragSession = null
        valueChangedListeners.clear()
        userScrubListeners.clear()
        super.dispose()
    }

    companion object {
        const val DEFAULT_TAG_NAME: String = "genericui:styled-slider"
        const val DEFAULT_CLASS_PREFIX: String = "styled-slider"
        const val TRACK_CLASS: String = "styled-slider-track"
        const val PROGRESS_CLASS: String = "styled-slider-progress"
        const val KNOB_CLASS: String = "styled-slider-knob"
        const val SCRUBBING_CLASS: String = "scrubbing"
        const val DISABLED_CLASS: String = "disabled"

        private const val EPSILON: Double = 1e-6
        private const val DEFAULT_FALLBACK_WIDTH_PX: Float = 200f
    }
}
