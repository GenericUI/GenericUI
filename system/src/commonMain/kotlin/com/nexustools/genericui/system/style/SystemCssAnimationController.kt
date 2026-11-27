package com.nexustools.genericui.system.style

import com.nexustools.genericui.animation.AnimationHandle
import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.css.CssUnit
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssAnimationSpec
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.CssTransform
import com.nexustools.genericui.style.css.StyleSheetRegistry
import com.nexustools.genericui.style.theme.ThemeStyleSheetRegistry
import com.nexustools.genericui.style.transition.CssTimingFunction
import com.nexustools.genericui.style.transition.CssTransitionSpec
import com.nexustools.genericui.threading.PlatformReentrantLock
import com.nexustools.genericui.threading.withLock
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Runtime CSS `@keyframes` animation engine for `:system` backends, driven strictly by
 * [Animator.animateFloat] (finite animations) and [Animator.watchVBlank] (infinite animations)
 * with zero background sleep threads or ad-hoc timers.
 */
object SystemCssAnimationController {

    private data class ParsedStop(
        val offset: Float,
        val declarations: Map<String, String>
    )

    private data class ComponentAnimationTrack(
        val component: AbstractComponent,
        var spec: CssAnimationSpec,
        val signature: String,
        val stops: List<ParsedStop>,
        var baseStyleSnapshot: ComputedStyle,
        var elapsedActiveMs: Float = 0f,
        var delayRemainingMs: Float = 0f,
        var handle: AnimationHandle? = null,
        var vblankWatcherId: Int? = null,
        var finished: Boolean = false
    )

    private val trackLock = PlatformReentrantLock()
    private val activeTracksByComponent = linkedMapOf<AbstractComponent, MutableList<ComponentAnimationTrack>>()
    private var hooksInstalled: Boolean = false

    private val LAYOUT_TRANSITION_PROPERTIES = listOf(
        "width",
        "max-width",
        "min-width",
        "height",
        "max-height",
        "min-height",
        "margin-left",
        "margin-right",
        "margin-top",
        "margin-bottom",
        "left",
        "top",
        "right",
        "bottom"
    )

    init {
        ensureInstalled()
    }

    fun ensureInstalled() {
        if (hooksInstalled && AbstractComponent.systemCssAnimationHook != null) return
        hooksInstalled = true
        AbstractComponent.systemCssAnimationHook = { component, previous, current, sheet ->
            onComputedStyleApplied(component, previous, current, sheet)
        }
        AbstractComponent.systemCssAnimationDisposeHook = { component ->
            cancelForComponent(component)
        }
    }

    fun clear() {
        val entries = trackLock.withLock {
            val flattened = activeTracksByComponent.values.flatten()
            activeTracksByComponent.clear()
            flattened
        }
        for (track in entries) {
            stopTrackHandles(track)
        }
    }

    fun activeTrackCount(): Int = trackLock.withLock {
        activeTracksByComponent.values.sumOf { tracks ->
            tracks.count { !it.finished }
        }
    }

    fun cancelForComponent(component: AbstractComponent) {
        val removed = trackLock.withLock {
            activeTracksByComponent.remove(component)?.toList()
        } ?: return
        for (track in removed) {
            stopTrackHandles(track)
        }
    }

    fun onComputedStyleApplied(
        component: AbstractComponent,
        previous: ComputedStyle?,
        current: ComputedStyle,
        sheet: CssStyleSheet?
    ): ComputedStyle? {
        if (component.isDisposed) {
            cancelForComponent(component)
            return null
        }

        val resolvedSpecs = current.resolveEffectiveAnimations()
        if (resolvedSpecs.isEmpty()) {
            val (previousWasAnimating, sameTransitionBase) = trackLock.withLock {
                val existingList = activeTracksByComponent[component]
                val wasAnimating = existingList?.any { !it.finished } == true
                val transitionBase = existingList
                    ?.takeIf { list -> list.isNotEmpty() && list.all { it.spec.name.startsWith("__transition_") } }
                    ?.firstOrNull()
                    ?.baseStyleSnapshot
                wasAnimating to (transitionBase === current)
            }
            if (sameTransitionBase) {
                return null
            }
            cancelForComponent(component)
            if (previous != null) {
                val transSpecs = current.transitions
                    ?: current.transition?.let { CssTransitionSpec.parseList(it) }
                    ?: emptyList()
                if (transSpecs.isNotEmpty()) {
                    return startTransitionTracks(
                        component = component,
                        previous = previous,
                        current = current,
                        transSpecs = transSpecs,
                        includeOpacityWithLayout = previousWasAnimating
                    )
                }
            }
            return null
        }

        var matchedSnapshot: ComputedStyle? = null
        var matched = false
        val toPause = mutableListOf<ComponentAnimationTrack>()
        val toStart = mutableListOf<ComponentAnimationTrack>()

        trackLock.withLock {
            val existing = activeTracksByComponent[component]
            if (existing != null &&
                existing.size == resolvedSpecs.size &&
                existing.indices.all { idx -> specsMatchIgnoringPlayState(existing[idx].spec, resolvedSpecs[idx]) }
            ) {
                matched = true
                for (idx in resolvedSpecs.indices) {
                    val track = existing[idx]
                    val nextSpec = resolvedSpecs[idx]
                    track.baseStyleSnapshot = current
                    if (track.spec.playState != nextSpec.playState) {
                        track.spec = nextSpec
                        if (nextSpec.isPaused) {
                            toPause.add(track)
                        } else if (!track.finished) {
                            toStart.add(track)
                        }
                    }
                }
                matchedSnapshot = buildTracksFrameLocked(existing, current)
            }
        }

        if (matched) {
            for (track in toPause) {
                stopTrackHandles(track)
            }
            for (track in toStart) {
                startTrackDriver(track)
            }
            return matchedSnapshot
        }

        cancelForComponent(component)
        val newTracks = mutableListOf<ComponentAnimationTrack>()
        for (spec in resolvedSpecs) {
            val rawFrames = findKeyframesFor(spec.name, sheet) ?: continue
            val stops = parseKeyframeStops(rawFrames)
            if (stops.isEmpty()) continue
            val initialDelay = if (spec.delayMs < 0f) 0f else spec.delayMs
            val initialElapsed = if (spec.delayMs < 0f) -spec.delayMs else 0f
            val finishedImmediately = spec.durationMs <= 0f || spec.iterationCount == 0f
            val track = ComponentAnimationTrack(
                component = component,
                spec = spec,
                signature = "${spec.name}:${spec.durationMs}:${spec.iterationCount}:${spec.direction}:${spec.fillMode}:${spec.delayMs}",
                stops = stops,
                baseStyleSnapshot = current,
                elapsedActiveMs = initialElapsed,
                delayRemainingMs = initialDelay,
                finished = finishedImmediately
            )
            newTracks.add(track)
        }
        if (newTracks.isEmpty()) return null

        val initialSnapshot = trackLock.withLock {
            activeTracksByComponent[component] = newTracks
            buildTracksFrameLocked(newTracks, current)
        }
        for (track in newTracks) {
            if (!track.spec.isPaused && !track.finished) {
                startTrackDriver(track)
            }
        }
        return initialSnapshot
    }

    private fun specsMatchIgnoringPlayState(a: CssAnimationSpec, b: CssAnimationSpec): Boolean {
        return a.name == b.name &&
            a.durationMs == b.durationMs &&
            a.delayMs == b.delayMs &&
            a.iterationCount == b.iterationCount &&
            a.direction == b.direction &&
            a.fillMode == b.fillMode &&
            a.timingFunction == b.timingFunction
    }

    private fun startTransitionTracks(
        component: AbstractComponent,
        previous: ComputedStyle,
        current: ComputedStyle,
        transSpecs: List<CssTransitionSpec>,
        includeOpacityWithLayout: Boolean
    ): ComputedStyle? {
        val grouped = linkedMapOf<Triple<Float, Float, CssTimingFunction>, Pair<MutableMap<String, String>, MutableMap<String, String>>>()
        var hasLayoutTransition = false
        for (prop in LAYOUT_TRANSITION_PROPERTIES) {
            val spec = CssTransitionSpec.findMatching(transSpecs, prop) ?: continue
            if (spec.durationMs <= 0f) continue
            val fromStr = resolveBasePropertyValueString(prop, previous) ?: continue
            val toStr = resolveBasePropertyValueString(prop, current) ?: continue
            if (fromStr == toStr) continue
            hasLayoutTransition = true
            val key = Triple(spec.durationMs, spec.delayMs, spec.timingFunction)
            val pair = grouped.getOrPut(key) { linkedMapOf<String, String>() to linkedMapOf() }
            pair.first[prop] = fromStr
            pair.second[prop] = toStr
        }
        if (hasLayoutTransition || includeOpacityWithLayout || !component.transitionController.hasActiveSoftwareTransition("opacity")) {
            CssTransitionSpec.findMatching(transSpecs, "opacity")?.takeIf { it.durationMs > 0f }?.let { spec ->
                if (hasLayoutTransition || spec.property == "opacity") {
                    val fromOp = resolveBasePropertyValueString("opacity", previous)
                    val toOp = resolveBasePropertyValueString("opacity", current)
                    if (fromOp != null && toOp != null && fromOp != toOp) {
                        val key = Triple(spec.durationMs, spec.delayMs, spec.timingFunction)
                        val pair = grouped.getOrPut(key) { linkedMapOf<String, String>() to linkedMapOf() }
                        pair.first["opacity"] = fromOp
                        pair.second["opacity"] = toOp
                    }
                }
            }
        }
        if (grouped.isEmpty()) return null

        val newTracks = ArrayList<ComponentAnimationTrack>(grouped.size)
        var idx = 0
        for ((key, decls) in grouped) {
            val (startDecls, endDecls) = decls
            newTracks.add(
                ComponentAnimationTrack(
                    component = component,
                    spec = CssAnimationSpec(
                        name = "__transition_$idx",
                        durationMs = key.first,
                        timingFunction = key.third,
                        delayMs = key.second,
                        iterationCount = 1f,
                        direction = CssAnimationSpec.DIRECTION_NORMAL,
                        fillMode = CssAnimationSpec.FILL_NONE
                    ),
                    signature = "__transition:$idx:${key.first}:${key.second}",
                    stops = listOf(
                        ParsedStop(0f, startDecls),
                        ParsedStop(1f, endDecls)
                    ),
                    baseStyleSnapshot = current,
                    elapsedActiveMs = 0f,
                    delayRemainingMs = key.second.coerceAtLeast(0f)
                )
            )
            idx++
        }
        val initialSnapshot = trackLock.withLock {
            activeTracksByComponent[component] = newTracks
            buildTracksFrameLocked(newTracks, current)
        }
        for (track in newTracks) {
            if (!track.finished) {
                startTrackDriver(track)
            }
        }
        return initialSnapshot
    }

    private fun stopTrackHandles(track: ComponentAnimationTrack) {
        val (handle, watcherId) = trackLock.withLock {
            val h = track.handle
            val w = track.vblankWatcherId
            track.handle = null
            track.vblankWatcherId = null
            h to w
        }
        handle?.cancel()
        watcherId?.let { Animator.unwatchVBlank(it) }
    }

    private fun startTrackDriver(track: ComponentAnimationTrack) {
        val spec = trackLock.withLock {
            if (track.component.isDisposed || track.finished || track.spec.isPaused) return
            if (track.spec.durationMs <= 0f || track.spec.iterationCount == 0f) {
                track.finished = true
                return
            }
            track.spec
        }

        if (spec.isInfinite) {
            val watcherId = Animator.watchVBlank { deltaMs, _, _ ->
                var shouldStop = false
                val frameToApply = trackLock.withLock {
                    val curSpec = track.spec
                    val activeList = activeTracksByComponent[track.component]
                    if (track.component.isDisposed || track.finished || curSpec.isPaused || activeList == null || track !in activeList) {
                        shouldStop = true
                        return@withLock null
                    }
                    var dt = deltaMs.toFloat().coerceAtLeast(0f)
                    if (track.delayRemainingMs > 0f) {
                        val consumed = minOf(dt, track.delayRemainingMs)
                        track.delayRemainingMs -= consumed
                        dt -= consumed
                    }
                    if (dt > 0f || track.delayRemainingMs <= 0f) {
                        track.elapsedActiveMs += dt
                        val cyclePos = track.elapsedActiveMs / curSpec.durationMs
                        val iterIndex = floor(cyclePos.toDouble()).toInt().coerceAtLeast(0)
                        val iterFraction = (cyclePos - iterIndex).coerceIn(0f, 1f)
                        val directed = applyDirection(iterFraction, iterIndex, curSpec.direction)
                        val eased = curSpec.timingFunction.evaluate(directed)
                        buildTracksFrameLocked(activeList, track.baseStyleSnapshot, track, eased)
                    } else {
                        null
                    }
                }
                if (shouldStop) {
                    stopTrackHandles(track)
                    return@watchVBlank
                }
                if (frameToApply != null) {
                    track.component.applyCssAnimationFrameInternal(frameToApply)
                }
            }
            val cancelImmediately = trackLock.withLock {
                val activeList = activeTracksByComponent[track.component]
                val stillActive = !track.finished && !track.component.isDisposed && !track.spec.isPaused &&
                    activeList != null && track in activeList
                if (stillActive) {
                    track.vblankWatcherId = watcherId
                    false
                } else {
                    true
                }
            }
            if (cancelImmediately) {
                Animator.unwatchVBlank(watcherId)
            }
        } else {
            val (totalMs, startElapsed, delayMsInt, animSpanMs) = trackLock.withLock {
                val curSpec = track.spec
                val total = (curSpec.durationMs * curSpec.iterationCount).coerceAtLeast(1f)
                val remaining = (total - track.elapsedActiveMs).coerceAtLeast(1f)
                val delayInt = track.delayRemainingMs.roundToInt().coerceAtLeast(0)
                val spanInt = remaining.roundToInt().coerceAtLeast(1)
                TrackTimingArgs(total, track.elapsedActiveMs, delayInt, spanInt)
            }

            val handle = Animator.animateFloat(
                from = 0f,
                to = 1f,
                durationMs = animSpanMs,
                delayMs = delayMsInt,
                timingFunction = CssTimingFunction.LINEAR,
                target = track.component.nativeImpl,
                onUpdate = { linearProgress ->
                    val frameToApply = trackLock.withLock {
                        val curSpec = track.spec
                        val activeList = activeTracksByComponent[track.component]
                        if (track.component.isDisposed || track.finished || curSpec.isPaused || activeList == null || track !in activeList) {
                            return@withLock null
                        }
                        track.delayRemainingMs = 0f
                        val curTotalElapsed = startElapsed + (totalMs - startElapsed) * linearProgress.coerceIn(0f, 1f)
                        track.elapsedActiveMs = curTotalElapsed
                        val eased = evaluateActiveProgress(curTotalElapsed, curSpec)
                        buildTracksFrameLocked(activeList, track.baseStyleSnapshot, track, eased)
                    }
                    if (frameToApply != null) {
                        track.component.applyCssAnimationFrameInternal(frameToApply)
                    }
                },
                onComplete = { cancelled ->
                    val frameToApply = trackLock.withLock {
                        if (track.finished) return@withLock null
                        track.finished = true
                        track.handle = null
                        if (cancelled || track.component.isDisposed) return@withLock null
                        val activeList = activeTracksByComponent[track.component]
                        if (activeList == null || track !in activeList) return@withLock null
                        val curSpec = track.spec
                        val keepForwards = curSpec.fillMode == CssAnimationSpec.FILL_FORWARDS ||
                            curSpec.fillMode == CssAnimationSpec.FILL_BOTH
                        if (keepForwards) {
                            val eased = evaluateFinalProgress(curSpec)
                            buildTracksFrameLocked(activeList, track.baseStyleSnapshot, track, eased)
                        } else {
                            val base = track.baseStyleSnapshot
                            activeList.remove(track)
                            if (activeList.isEmpty()) {
                                activeTracksByComponent.remove(track.component)
                                base
                            } else {
                                buildTracksFrameLocked(activeList, base) ?: base
                            }
                        }
                    }
                    if (frameToApply != null) {
                        track.component.applyCssAnimationFrameInternal(frameToApply)
                    }
                }
            )
            val cancelImmediately = trackLock.withLock {
                val activeList = activeTracksByComponent[track.component]
                val stillActive = !track.finished && !track.component.isDisposed && !track.spec.isPaused &&
                    activeList != null && track in activeList
                if (stillActive) {
                    track.handle = handle
                    false
                } else {
                    true
                }
            }
            if (cancelImmediately) {
                handle.cancel()
            }
        }
    }

    private data class TrackTimingArgs(
        val totalMs: Float,
        val startElapsed: Float,
        val delayMsInt: Int,
        val animSpanMs: Int
    )

    private fun evaluateActiveProgress(elapsedMs: Float, spec: CssAnimationSpec): Float {
        if (spec.durationMs <= 0f) return spec.timingFunction.evaluate(applyDirection(1f, 0, spec.direction))
        val cyclePos = if (spec.isInfinite) {
            elapsedMs / spec.durationMs
        } else {
            (elapsedMs / spec.durationMs).coerceIn(0f, spec.iterationCount)
        }
        val rawIndex = floor(cyclePos.toDouble()).toInt().coerceAtLeast(0)
        val atExactEnd = !spec.isInfinite && kotlin.math.abs(cyclePos - spec.iterationCount) < 1e-4f
        val iterIndex = if (atExactEnd && cyclePos > 0f && cyclePos == floor(cyclePos.toDouble()).toFloat()) {
            (rawIndex - 1).coerceAtLeast(0)
        } else {
            rawIndex
        }
        val iterFraction = if (atExactEnd && cyclePos == floor(cyclePos.toDouble()).toFloat() && cyclePos > 0f) {
            1f
        } else {
            (cyclePos - iterIndex).coerceIn(0f, 1f)
        }
        val directed = applyDirection(iterFraction, iterIndex, spec.direction)
        return spec.timingFunction.evaluate(directed)
    }

    private fun evaluateFinalProgress(spec: CssAnimationSpec): Float {
        val finalIterIndex = if (spec.iterationCount > 0f && !spec.isInfinite) {
            (kotlin.math.ceil(spec.iterationCount.toDouble()).toInt() - 1).coerceAtLeast(0)
        } else {
            0
        }
        val frac = spec.iterationCount - floor(spec.iterationCount.toDouble()).toFloat()
        val endIterT = if (spec.durationMs <= 0f || spec.iterationCount == 0f || frac <= 1e-4f) 1f else frac
        val directed = applyDirection(endIterT, finalIterIndex, spec.direction)
        return spec.timingFunction.evaluate(directed)
    }

    private fun evaluateTrackEasedT(track: ComponentAnimationTrack): Float? {
        val spec = track.spec
        val hasBackwards = spec.fillMode == CssAnimationSpec.FILL_BACKWARDS || spec.fillMode == CssAnimationSpec.FILL_BOTH
        val hasForwards = spec.fillMode == CssAnimationSpec.FILL_FORWARDS || spec.fillMode == CssAnimationSpec.FILL_BOTH
        if (track.delayRemainingMs > 0f && track.elapsedActiveMs <= 0f && !hasBackwards) {
            return null
        }
        if (spec.durationMs <= 0f || spec.iterationCount == 0f || (track.finished && !spec.isInfinite)) {
            return if (hasForwards) evaluateFinalProgress(spec) else null
        }
        return evaluateActiveProgress(track.elapsedActiveMs, spec)
    }

    private fun buildTracksFrameLocked(
        tracks: List<ComponentAnimationTrack>,
        base: ComputedStyle,
        overrideTrack: ComponentAnimationTrack? = null,
        overrideEasedT: Float? = null
    ): ComputedStyle? {
        var acc: ComputedStyle? = null
        for (track in tracks) {
            val easedT = if (track === overrideTrack && overrideEasedT != null) {
                overrideEasedT
            } else {
                evaluateTrackEasedT(track)
            } ?: continue
            acc = evaluateStopsAt(track.stops, easedT.coerceIn(0f, 1f), acc ?: base)
        }
        return acc
    }

    private fun applyDirection(iterFraction: Float, iterationIndex: Int, direction: String): Float {
        val f = iterFraction.coerceIn(0f, 1f)
        return when (direction.lowercase()) {
            CssAnimationSpec.DIRECTION_REVERSE -> 1f - f
            CssAnimationSpec.DIRECTION_ALTERNATE -> if (iterationIndex % 2 == 1) 1f - f else f
            CssAnimationSpec.DIRECTION_ALTERNATE_REVERSE -> if (iterationIndex % 2 == 0) 1f - f else f
            else -> f
        }
    }

    private fun findKeyframesFor(name: String, primarySheet: CssStyleSheet?): Map<String, Map<String, String>>? {
        primarySheet?.keyframes?.get(name)?.let { return it }
        if (primarySheet != null) {
            for (child in primarySheet.composedSheets) {
                child.keyframes[name]?.let { return it }
            }
        }
        for (sheet in BlinkStyleEngine.getActiveStyleSheets()) {
            sheet.keyframes[name]?.let { return it }
            sheet.styleSheet.keyframes[name]?.let { return it }
        }
        for (themeSheet in ThemeStyleSheetRegistry.getCompiledThemeStyleSheets()) {
            themeSheet.keyframes[name]?.let { return it }
        }
        for (precompiled in StyleSheetRegistry.all()) {
            precompiled.keyframes[name]?.let { return it }
            precompiled.styleSheet.keyframes[name]?.let { return it }
        }
        return null
    }

    private fun parseKeyframeStops(rawFrames: Map<String, Map<String, String>>): List<ParsedStop> {
        val byOffset = linkedMapOf<Float, MutableMap<String, String>>()
        for ((selector, decls) in rawFrames) {
            for (part in selector.split(',')) {
                val offset = parseKeyframeOffset(part) ?: continue
                val targetMap = byOffset.getOrPut(offset) { linkedMapOf() }
                for ((k, v) in decls) {
                    targetMap[k.trim().lowercase()] = v.trim()
                }
            }
        }
        return byOffset.entries
            .sortedBy { it.key }
            .map { ParsedStop(offset = it.key, declarations = it.value) }
    }

    private fun parseKeyframeOffset(token: String): Float? {
        val lower = token.trim().lowercase()
        return when {
            lower == "from" -> 0f
            lower == "to" -> 1f
            lower.endsWith("%") -> {
                val num = lower.removeSuffix("%").trim().toFloatOrNull() ?: return null
                (num / 100f).coerceIn(0f, 1f)
            }
            else -> null
        }
    }

    private fun evaluateStopsAt(
        stops: List<ParsedStop>,
        progress: Float,
        base: ComputedStyle
    ): ComputedStyle {
        if (stops.isEmpty()) return base
        val t = progress.coerceIn(0f, 1f)

        val animatedProps = linkedSetOf<String>()
        for (stop in stops) {
            animatedProps.addAll(stop.declarations.keys)
        }
        if (animatedProps.isEmpty()) return base

        var result = base
        for (prop in animatedProps) {
            var before: Pair<Float, String>? = null
            var after: Pair<Float, String>? = null
            for (stop in stops) {
                val rawVal = stop.declarations[prop] ?: continue
                if (stop.offset <= t) {
                    before = stop.offset to rawVal
                }
                if (stop.offset >= t && after == null) {
                    after = stop.offset to rawVal
                }
            }
            val baseValStr = resolveBasePropertyValueString(prop, base)
            val startPair = before ?: (0f to (baseValStr ?: after?.second ?: continue))
            val endPair = after ?: (1f to (baseValStr ?: startPair.second))
            val span = (endPair.first - startPair.first)
            val localT = if (span <= 1e-5f) {
                if (t >= endPair.first) 1f else 0f
            } else {
                ((t - startPair.first) / span).coerceIn(0f, 1f)
            }
            result = applyInterpolatedProperty(result, prop, startPair.second, endPair.second, localT)
        }
        return result
    }

    private fun resolveBasePropertyValueString(prop: String, base: ComputedStyle): String? = when (prop) {
        "opacity" -> base.opacity.toString()
        "transform" -> base.transform ?: "none"
        "width" -> base.width ?: "0px"
        "height" -> base.height ?: "0px"
        "min-width" -> base.minWidth ?: "0px"
        "min-height" -> base.minHeight ?: "0px"
        "max-width" -> base.maxWidth
        "max-height" -> base.maxHeight
        "left" -> base.left ?: "0px"
        "top" -> base.top ?: "0px"
        "right" -> base.right
        "bottom" -> base.bottom
        "margin-left" -> base.marginLeft?.toCssString() ?: "0px"
        "margin-right" -> base.marginRight?.toCssString() ?: "0px"
        "margin-top" -> base.marginTop?.toCssString() ?: "0px"
        "margin-bottom" -> base.marginBottom?.toCssString() ?: "0px"
        "background-color" -> base.backgroundColor?.toCssRgbaString() ?: RgbaColor.TRANSPARENT.toCssRgbaString()
        "color" -> base.textColor?.toCssRgbaString()
        "border-color" -> base.borderColor?.toCssRgbaString() ?: RgbaColor.TRANSPARENT.toCssRgbaString()
        else -> null
    }

    private fun applyInterpolatedProperty(
        style: ComputedStyle,
        prop: String,
        fromRaw: String,
        toRaw: String,
        localT: Float
    ): ComputedStyle = when (prop) {
        "opacity" -> {
            val fromVal = fromRaw.toFloatOrNull() ?: style.opacity
            val toVal = toRaw.toFloatOrNull() ?: fromVal
            val interp = (fromVal + (toVal - fromVal) * localT).coerceIn(0f, 1f)
            style.copy(opacity = interp)
        }
        "transform" -> {
            val fromTf = CssTransform.parse(fromRaw) ?: CssTransform.IDENTITY
            val toTf = CssTransform.parse(toRaw) ?: CssTransform.IDENTITY
            val interp = fromTf.interpolateTo(toTf, localT)
            style.copy(transform = interp.toCssString(), transform2D = interp.toTransform2D())
        }
        "width" -> style.copy(width = interpolateCssLengthString(fromRaw, toRaw, localT))
        "height" -> style.copy(height = interpolateCssLengthString(fromRaw, toRaw, localT))
        "min-width" -> style.copy(minWidth = interpolateCssLengthString(fromRaw, toRaw, localT))
        "min-height" -> style.copy(minHeight = interpolateCssLengthString(fromRaw, toRaw, localT))
        "max-width" -> style.copy(maxWidth = interpolateCssLengthString(fromRaw, toRaw, localT))
        "max-height" -> style.copy(maxHeight = interpolateCssLengthString(fromRaw, toRaw, localT))
        "left" -> style.copy(left = interpolateCssLengthString(fromRaw, toRaw, localT))
        "top" -> style.copy(top = interpolateCssLengthString(fromRaw, toRaw, localT))
        "right" -> style.copy(right = interpolateCssLengthString(fromRaw, toRaw, localT))
        "bottom" -> style.copy(bottom = interpolateCssLengthString(fromRaw, toRaw, localT))
        "margin-left" -> style.copy(marginLeft = CssLength.parse(interpolateCssLengthString(fromRaw, toRaw, localT)))
        "margin-right" -> style.copy(marginRight = CssLength.parse(interpolateCssLengthString(fromRaw, toRaw, localT)))
        "margin-top" -> style.copy(marginTop = CssLength.parse(interpolateCssLengthString(fromRaw, toRaw, localT)))
        "margin-bottom" -> style.copy(marginBottom = CssLength.parse(interpolateCssLengthString(fromRaw, toRaw, localT)))
        "background-color", "background" -> {
            val fromCol = CssStyleSheet.resolveCssColorValue(fromRaw, false) ?: RgbaColor.TRANSPARENT
            val toCol = CssStyleSheet.resolveCssColorValue(toRaw, false) ?: fromCol
            style.copy(backgroundColor = interpolateRgba(fromCol, toCol, localT))
        }
        "color" -> {
            val fromCol = CssStyleSheet.resolveCssColorValue(fromRaw, false)
            val toCol = CssStyleSheet.resolveCssColorValue(toRaw, false)
            if (fromCol != null && toCol != null) {
                style.copy(textColor = interpolateRgba(fromCol, toCol, localT))
            } else style
        }
        "border-color" -> {
            val fromCol = CssStyleSheet.resolveCssColorValue(fromRaw, false) ?: RgbaColor.TRANSPARENT
            val toCol = CssStyleSheet.resolveCssColorValue(toRaw, false) ?: fromCol
            style.copy(borderColor = interpolateRgba(fromCol, toCol, localT))
        }
        else -> style
    }

    private fun interpolateCssLengthString(fromRaw: String, toRaw: String, t: Float): String {
        val fromLen = CssLength.parse(fromRaw)
        val toLen = CssLength.parse(toRaw)
        if (fromLen == null || toLen == null) return if (t < 0.5f) fromRaw else toRaw
        if (fromLen.unit == toLen.unit || fromLen.value == 0f || toLen.value == 0f) {
            val targetUnit = if (toLen.value != 0f) toLen.unit else fromLen.unit
            val v = fromLen.value + (toLen.value - fromLen.value) * t
            return when (targetUnit) {
                CssUnit.PERCENT -> "${formatFloat(v)}%"
                CssUnit.VW -> "${formatFloat(v)}vw"
                CssUnit.VH -> "${formatFloat(v)}vh"
                CssUnit.REM -> "${formatFloat(v)}rem"
                CssUnit.EM -> "${formatFloat(v)}em"
                CssUnit.NUMBER -> formatFloat(v)
                else -> "${formatFloat(v)}px"
            }
        }
        val fromPx = fromLen.resolvePx() ?: return if (t < 0.5f) fromRaw else toRaw
        val toPx = toLen.resolvePx() ?: return if (t < 0.5f) fromRaw else toRaw
        val px = fromPx + (toPx - fromPx) * t
        return "${formatFloat(px)}px"
    }

    private fun interpolateRgba(from: RgbaColor, to: RgbaColor, t: Float): RgbaColor = RgbaColor(
        r = (from.r + (to.r - from.r) * t).roundToInt().coerceIn(0, 255),
        g = (from.g + (to.g - from.g) * t).roundToInt().coerceIn(0, 255),
        b = (from.b + (to.b - from.b) * t).roundToInt().coerceIn(0, 255),
        a = (from.a + (to.a - from.a) * t).roundToInt().coerceIn(0, 255)
    )

    private fun formatFloat(v: Float): String {
        val rounded = (v * 100f).roundToInt() / 100f
        val asInt = rounded.toInt()
        return if (rounded == asInt.toFloat()) asInt.toString() else rounded.toString()
    }
}
