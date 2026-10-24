package com.nexustools.genericui.style.transition

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import kotlin.math.roundToInt

/**
 * Coordinates CSS property transitions (`opacity`, `transform`, `left`, `top`, `width`, `height`,
 * `background-color`) for an [AbstractComponent] or [AbstractContainer].
 *
 * - **Native Transition Processing First**: When attached to a backend whose `NativeImpl`
 *   reports `supportsNativeTransitions() == true` (`:web`, `:macos`, `:ios`, `:android`, `:gtk`, `:qt`),
 *   delegates transition execution to the native compositor (`runNativeTransition`) and enables
 *   native hardware layer caching (`enableHardwareLayerCache(true)`).
 * - **Window-Manager-Style Pre-Rendered Container Buffer Caching**: For [AbstractContainer] instances,
 *   pre-renders the container's child tree into [ContainerRenderCache] once at transition start and
 *   animates the cached [com.nexustools.genericui.image.ImageBuffer] across frames (`step(deltaMs)`),
 *   re-rendering interior contents during animation **only** if a child is marked dirty (`isDirty == true`).
 */
class CssTransitionController(
    private val owner: AbstractComponent
) {
    private class ActivePropertyTransition(
        val property: String,
        val spec: CssTransitionSpec,
        val fromFloat: Float = 0f,
        val toFloat: Float = 0f,
        val fromTransform: CssTransform2D = CssTransform2D.IDENTITY,
        val toTransform: CssTransform2D = CssTransform2D.IDENTITY,
        val fromColor: RgbaColor? = null,
        val toColor: RgbaColor? = null,
        var elapsedMs: Float = 0f,
        var nativeDispatched: Boolean = false
    ) {
        fun rawProgress(): Float {
            val activeTime = elapsedMs - spec.delayMs
            if (activeTime <= 0f) return 0f
            if (spec.durationMs <= 0f) return 1f
            return (activeTime / spec.durationMs).coerceIn(0f, 1f)
        }

        fun easedProgress(): Float = spec.timingFunction.evaluate(rawProgress())

        fun isComplete(): Boolean = elapsedMs >= (spec.delayMs + spec.durationMs)
    }

    var specs: List<CssTransitionSpec> = emptyList()
        private set

    private val activeTransitions = linkedMapOf<String, ActivePropertyTransition>()

    var currentFrameState: ContainerAnimationFrameState = ContainerAnimationFrameState()
        private set

    val isTransitioning: Boolean
        get() = activeTransitions.isNotEmpty()

    val activeTransitionProperties: Set<String>
        get() = activeTransitions.keys.toSet()

    var usingNativeTransitionBackend: Boolean = false
        private set

    var onTransitionFrame: ((ContainerAnimationFrameState) -> Unit)? = null
    var onTransitionFinished: (() -> Unit)? = null

    fun updateTransitionCss(transitionCss: String?) {
        specs = CssTransitionSpec.parseList(transitionCss)
        owner.nativeImpl?.let { impl ->
            if (impl.supportsNativeTransitions()) {
                impl.configureNativeTransitions(specs)
            }
        }
    }

    /**
     * Compares [previous] and [next] styles and begins CSS transitions for any changed properties
     * covered by [specs].
     */
    fun onStyleChanged(previous: ComputedStyle?, next: ComputedStyle) {
        if (next.transition != null && next.transition != previous?.transition) {
            updateTransitionCss(next.transition)
        }
        if (specs.isEmpty() || previous == null) {
            currentFrameState = buildSteadyState(next)
            return
        }

        // Check opacity
        val prevOpacity = previous.opacity ?: 1f
        val nextOpacity = next.opacity ?: 1f
        if (prevOpacity != nextOpacity) {
            CssTransitionSpec.findMatching(specs, "opacity")?.let { spec ->
                val startVal = if (activeTransitions.containsKey("opacity")) currentFrameState.opacity else prevOpacity
                startPropertyTransition(
                    ActivePropertyTransition(
                        property = "opacity",
                        spec = spec,
                        fromFloat = startVal,
                        toFloat = nextOpacity
                    ),
                    fromRaw = startVal.toString(),
                    toRaw = nextOpacity.toString()
                )
            }
        }

        // Check transform
        val prevTransformStr = previous.transform
        val nextTransformStr = next.transform
        if (prevTransformStr != nextTransformStr && (prevTransformStr != null || nextTransformStr != null)) {
            CssTransitionSpec.findMatching(specs, "transform")?.let { spec ->
                val startTf = if (activeTransitions.containsKey("transform")) {
                    currentFrameState.transform
                } else {
                    CssTransform2D.parse(prevTransformStr)
                }
                val endTf = CssTransform2D.parse(nextTransformStr)
                if (startTf != endTf) {
                    startPropertyTransition(
                        ActivePropertyTransition(
                            property = "transform",
                            spec = spec,
                            fromTransform = startTf,
                            toTransform = endTf
                        ),
                        fromRaw = prevTransformStr ?: "none",
                        toRaw = nextTransformStr ?: "none"
                    )
                }
            }
        }

        // Check left, top, width, height
        checkNumericDimensionTransition("left", previous.left, next.left, currentFrameState.leftPx)
        checkNumericDimensionTransition("top", previous.top, next.top, currentFrameState.topPx)
        checkNumericDimensionTransition("width", previous.width?.toString(), next.width?.toString(), currentFrameState.widthPx)
        checkNumericDimensionTransition("height", previous.height?.toString(), next.height?.toString(), currentFrameState.heightPx)

        // Check background-color
        if (previous.backgroundColor != next.backgroundColor && previous.backgroundColor != null && next.backgroundColor != null) {
            CssTransitionSpec.findMatching(specs, "background-color")?.let { spec ->
                startPropertyTransition(
                    ActivePropertyTransition(
                        property = "background-color",
                        spec = spec,
                        fromColor = currentFrameState.backgroundColor ?: previous.backgroundColor,
                        toColor = next.backgroundColor
                    ),
                    fromRaw = previous.backgroundColor.toCssRgba(),
                    toRaw = next.backgroundColor.toCssRgba()
                )
            }
        }
    }

    private fun checkNumericDimensionTransition(
        property: String,
        prevRaw: String?,
        nextRaw: String?,
        currentAnimatedPx: Float?
    ) {
        if (prevRaw == nextRaw || (prevRaw == null && nextRaw == null)) return
        val spec = CssTransitionSpec.findMatching(specs, property) ?: return
        val prevNum = currentAnimatedPx ?: parsePxNumber(prevRaw) ?: return
        val nextNum = parsePxNumber(nextRaw) ?: return
        if (prevNum == nextNum) return

        startPropertyTransition(
            ActivePropertyTransition(
                property = property,
                spec = spec,
                fromFloat = prevNum,
                toFloat = nextNum
            ),
            fromRaw = "${prevNum}px",
            toRaw = "${nextNum}px"
        )
    }

    private fun startPropertyTransition(
        transition: ActivePropertyTransition,
        fromRaw: String?,
        toRaw: String
    ) {
        val wasEmpty = activeTransitions.isEmpty()
        val impl = owner.nativeImpl
        val nativeSupported = impl?.supportsNativeTransitions() == true
        usingNativeTransitionBackend = nativeSupported

        if (wasEmpty && owner is AbstractContainer) {
            (impl as? AbstractContainerNativeImpl<*>)?.enableHardwareLayerCache(true)
            // Pre-render the container into its cached buffer once when the animation begins!
            owner.renderCache.ensurePrerenderedBuffer(owner)
        }

        if (nativeSupported && impl != null) {
            transition.nativeDispatched = impl.runNativeTransition(
                property = transition.property,
                fromValue = fromRaw,
                toValue = toRaw,
                spec = transition.spec,
                onComplete = {
                    activeTransitions.remove(transition.property)
                    if (activeTransitions.isEmpty()) {
                        completeAllTransitions()
                    }
                }
            )
        }

        activeTransitions[transition.property] = transition
        recomputeCurrentFrameState(isInitialStart = true)
    }

    /**
     * Advances all active CSS transitions by [deltaMs] milliseconds, updating [currentFrameState]
     * and compositing the pre-rendered container buffer (re-rendering children ONLY if
     * `container.renderCache.isDirty` was marked `true` during the animation).
     */
    fun step(deltaMs: Float) {
        if (activeTransitions.isEmpty()) return
        val safeDelta = deltaMs.coerceAtLeast(0f)

        val completedKeys = mutableListOf<String>()
        for ((key, tr) in activeTransitions) {
            tr.elapsedMs += safeDelta
            if (tr.isComplete()) {
                completedKeys.add(key)
            }
        }

        recomputeCurrentFrameState(isInitialStart = false)

        if (owner is AbstractContainer) {
            val composited = owner.renderCache.compositeAnimatedFrame(owner, currentFrameState)
            (owner.nativeImpl as? AbstractContainerNativeImpl<*>)?.presentAnimatedBuffer(composited, currentFrameState)
        }

        onTransitionFrame?.invoke(currentFrameState)

        for (key in completedKeys) {
            activeTransitions.remove(key)
        }

        if (activeTransitions.isEmpty()) {
            completeAllTransitions()
        }
    }

    /**
     * Immediately completes all active transitions and snaps [currentFrameState] to target values.
     */
    fun finishAllTransitions() {
        if (activeTransitions.isEmpty()) return
        for (tr in activeTransitions.values) {
            tr.elapsedMs = tr.spec.delayMs + tr.spec.durationMs
        }
        recomputeCurrentFrameState(isInitialStart = false)
        activeTransitions.clear()
        completeAllTransitions()
    }

    private fun completeAllTransitions() {
        currentFrameState = currentFrameState.copy(progress = 1.0f, isAnimating = false)
        if (owner is AbstractContainer) {
            val containerImpl = owner.nativeImpl as? AbstractContainerNativeImpl<*>
            containerImpl?.enableHardwareLayerCache(false)
            containerImpl?.releaseAnimatedBuffer()
        }
        onTransitionFinished?.invoke()
    }

    private fun recomputeCurrentFrameState(isInitialStart: Boolean) {
        val style = owner.computedStyle
        var opacity = style?.opacity ?: owner.opacity
        var transform = CssTransform2D.parse(style?.transform)
        var leftPx = parsePxNumber(style?.left)
        var topPx = parsePxNumber(style?.top)
        var widthPx = parsePxNumber(style?.width) ?: owner.widthPx?.toFloat()
        var heightPx = parsePxNumber(style?.height) ?: owner.heightPx?.toFloat()
        var bgColor = style?.backgroundColor
        var maxProgress = if (isInitialStart) 0f else 1f

        for ((prop, tr) in activeTransitions) {
            val p = tr.easedProgress()
            maxProgress = p
            when (prop) {
                "opacity" -> opacity = lerp(tr.fromFloat, tr.toFloat, p)
                "transform" -> transform = tr.fromTransform.interpolateTo(tr.toTransform, p)
                "left" -> leftPx = lerp(tr.fromFloat, tr.toFloat, p)
                "top" -> topPx = lerp(tr.fromFloat, tr.toFloat, p)
                "width" -> widthPx = lerp(tr.fromFloat, tr.toFloat, p)
                "height" -> heightPx = lerp(tr.fromFloat, tr.toFloat, p)
                "background-color" -> {
                    val fc = tr.fromColor
                    val tc = tr.toColor
                    if (fc != null && tc != null) {
                        bgColor = RgbaColor(
                            r = lerp(fc.r.toFloat(), tc.r.toFloat(), p).roundToInt().coerceIn(0, 255),
                            g = lerp(fc.g.toFloat(), tc.g.toFloat(), p).roundToInt().coerceIn(0, 255),
                            b = lerp(fc.b.toFloat(), tc.b.toFloat(), p).roundToInt().coerceIn(0, 255),
                            a = lerp(fc.a.toFloat(), tc.a.toFloat(), p).roundToInt().coerceIn(0, 255)
                        )
                    }
                }
            }
        }

        currentFrameState = ContainerAnimationFrameState(
            opacity = opacity,
            transform = transform,
            leftPx = leftPx,
            topPx = topPx,
            widthPx = widthPx,
            heightPx = heightPx,
            backgroundColor = bgColor,
            progress = maxProgress,
            isAnimating = activeTransitions.isNotEmpty(),
            usingNativeTransition = usingNativeTransitionBackend
        )
    }

    private fun buildSteadyState(style: ComputedStyle): ContainerAnimationFrameState =
        ContainerAnimationFrameState(
            opacity = style.opacity ?: owner.opacity,
            transform = CssTransform2D.parse(style.transform),
            leftPx = parsePxNumber(style.left),
            topPx = parsePxNumber(style.top),
            widthPx = parsePxNumber(style.width) ?: owner.widthPx?.toFloat(),
            heightPx = parsePxNumber(style.height) ?: owner.heightPx?.toFloat(),
            backgroundColor = style.backgroundColor,
            progress = 1.0f,
            isAnimating = false,
            usingNativeTransition = usingNativeTransitionBackend
        )

    private fun parsePxNumber(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace("!important", "", ignoreCase = true)
            .trim()
            .lowercase()
            .removeSuffix("px")
            .removeSuffix("%")
            .trim()
        return cleaned.toFloatOrNull()
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
}
