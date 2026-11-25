package com.nexustools.genericui.style.css

import com.nexustools.genericui.style.transition.CssTimingFunction
import com.nexustools.genericui.style.transition.CssTransitionSpec

/**
 * Parsed CSS `@keyframes` animation specification resolved from `animation` shorthand and/or
 * longhand `animation-*` properties.
 */
data class CssAnimationSpec(
    val name: String,
    val durationMs: Float = 0f,
    val timingFunction: CssTimingFunction = CssTimingFunction.EASE,
    val delayMs: Float = 0f,
    val iterationCount: Float = 1f,
    val direction: String = DIRECTION_NORMAL,
    val fillMode: String = FILL_NONE,
    val playState: String = PLAY_RUNNING
) {
    val isInfinite: Boolean
        get() = iterationCount.isInfinite() || iterationCount < 0f

    val isPaused: Boolean
        get() = playState.equals(PLAY_PAUSED, ignoreCase = true)

    fun toCssString(): String {
        val iterStr = if (isInfinite) "infinite" else formatNumber(iterationCount)
        return "$name ${formatNumber(durationMs)}ms ${timingFunction.toCssString()} ${formatNumber(delayMs)}ms $iterStr $direction $fillMode $playState"
    }

    private fun formatNumber(v: Float): String {
        val asInt = v.toInt()
        return if (v == asInt.toFloat()) asInt.toString() else v.toString()
    }

    companion object {
        const val DIRECTION_NORMAL = "normal"
        const val DIRECTION_REVERSE = "reverse"
        const val DIRECTION_ALTERNATE = "alternate"
        const val DIRECTION_ALTERNATE_REVERSE = "alternate-reverse"

        const val FILL_NONE = "none"
        const val FILL_FORWARDS = "forwards"
        const val FILL_BACKWARDS = "backwards"
        const val FILL_BOTH = "both"

        const val PLAY_RUNNING = "running"
        const val PLAY_PAUSED = "paused"

        private val DIRECTION_KEYWORDS = setOf(
            DIRECTION_NORMAL,
            DIRECTION_REVERSE,
            DIRECTION_ALTERNATE,
            DIRECTION_ALTERNATE_REVERSE
        )

        private val FILL_MODE_KEYWORDS = setOf(
            FILL_NONE,
            FILL_FORWARDS,
            FILL_BACKWARDS,
            FILL_BOTH
        )

        private val PLAY_STATE_KEYWORDS = setOf(
            PLAY_RUNNING,
            PLAY_PAUSED
        )

        fun parseList(animationCss: String?): List<CssAnimationSpec> {
            if (animationCss.isNullOrBlank()) return emptyList()
            val cleaned = stripImportant(animationCss)
            if (cleaned.isEmpty() || cleaned.equals("none", ignoreCase = true)) return emptyList()

            val segments = CssTransitionSpec.splitTopLevelCommas(cleaned)
            val result = ArrayList<CssAnimationSpec>(segments.size)
            for (seg in segments) {
                parseSingle(seg)?.let { result.add(it) }
            }
            return result
        }

        fun resolveEffectiveSpecs(
            animationShorthand: String? = null,
            animationName: String? = null,
            animationDuration: String? = null,
            animationTimingFunction: String? = null,
            animationDelay: String? = null,
            animationIterationCount: String? = null,
            animationDirection: String? = null,
            animationFillMode: String? = null,
            animationPlayState: String? = null
        ): List<CssAnimationSpec> {
            val baseList = if (animationName != null) {
                val names = splitCommaTokens(animationName)
                    .filter { it.isNotEmpty() && !it.equals("none", ignoreCase = true) }
                names.map { CssAnimationSpec(name = it) }
            } else {
                parseList(animationShorthand)
            }
            if (baseList.isEmpty()) return emptyList()

            val durations = splitCommaTokens(animationDuration)
            val timings = splitCommaTokens(animationTimingFunction)
            val delays = splitCommaTokens(animationDelay)
            val counts = splitCommaTokens(animationIterationCount)
            val directions = splitCommaTokens(animationDirection)
            val fills = splitCommaTokens(animationFillMode)
            val playStates = splitCommaTokens(animationPlayState)

            return baseList.mapIndexed { idx, base ->
                val dur = durations.atCycled(idx)?.let { CssTransitionSpec.parseTimeMs(it) } ?: base.durationMs
                val timing = timings.atCycled(idx)?.let { CssTimingFunction.parse(it) } ?: base.timingFunction
                val delay = delays.atCycled(idx)?.let { CssTransitionSpec.parseTimeMs(it) } ?: base.delayMs
                val count = counts.atCycled(idx)?.let(::parseIterationCount) ?: base.iterationCount
                val dir = directions.atCycled(idx)?.lowercase()?.takeIf { it in DIRECTION_KEYWORDS } ?: base.direction
                val fill = fills.atCycled(idx)?.lowercase()?.takeIf { it in FILL_MODE_KEYWORDS } ?: base.fillMode
                val state = playStates.atCycled(idx)?.lowercase()?.takeIf { it in PLAY_STATE_KEYWORDS } ?: base.playState
                CssAnimationSpec(
                    name = base.name,
                    durationMs = dur.coerceAtLeast(0f),
                    timingFunction = timing,
                    delayMs = delay,
                    iterationCount = count,
                    direction = dir,
                    fillMode = fill,
                    playState = state
                )
            }
        }

        private fun isAsciiFloatToken(s: String): Boolean {
            val len = s.length
            if (len == 0) return false
            var i = 0
            if (s[0] == '+' || s[0] == '-') {
                if (len == 1) return false
                i = 1
            }
            var digits = false
            var dot = false
            var exp = false
            while (i < len) {
                val c = s[i]
                when {
                    c in '0'..'9' -> digits = true
                    c == '.' && !dot && !exp -> dot = true
                    (c == 'e' || c == 'E') && digits && !exp -> {
                        exp = true
                        digits = false
                        if (i + 1 < len && (s[i + 1] == '+' || s[i + 1] == '-')) i++
                    }
                    else -> return false
                }
                i++
            }
            return digits
        }

        internal fun parseIterationCount(raw: String): Float? {
            val lower = stripImportant(raw).lowercase()
            if (lower == "infinite") return Float.POSITIVE_INFINITY
            return if (isAsciiFloatToken(lower)) lower.toFloatOrNull()?.coerceAtLeast(0f) else null
        }

        private fun parseSingle(segment: String): CssAnimationSpec? {
            val tokens = CssTransitionSpec.tokenizeTransitionSegment(segment.trim())
            if (tokens.isEmpty()) return null

            var name: String? = null
            var durationMs: Float? = null
            var delayMs = 0f
            var timing = CssTimingFunction.EASE
            var timingMatched = false
            var count = 1f
            var countMatched = false
            var direction = DIRECTION_NORMAL
            var directionMatched = false
            var fillMode = FILL_NONE
            var fillMatched = false
            var playState = PLAY_RUNNING
            var playStateMatched = false

            for (tok in tokens) {
                val lower = tok.lowercase()
                val timeVal = CssTransitionSpec.parseTimeMs(lower)
                when {
                    timeVal != null -> {
                        if (durationMs == null) {
                            durationMs = timeVal
                        } else {
                            delayMs = timeVal
                        }
                    }
                    !timingMatched && isTimingFunctionToken(lower) -> {
                        timing = CssTimingFunction.parse(lower)
                        timingMatched = true
                    }
                    !countMatched && (lower == "infinite" || isAsciiFloatToken(lower)) -> {
                        count = if (lower == "infinite") Float.POSITIVE_INFINITY else lower.toFloat().coerceAtLeast(0f)
                        countMatched = true
                    }
                    !directionMatched && lower in DIRECTION_KEYWORDS -> {
                        direction = lower
                        directionMatched = true
                    }
                    !fillMatched && lower in FILL_MODE_KEYWORDS -> {
                        fillMode = lower
                        fillMatched = true
                    }
                    !playStateMatched && lower in PLAY_STATE_KEYWORDS -> {
                        playState = lower
                        playStateMatched = true
                    }
                    lower == "none" -> {
                        if (!fillMatched) {
                            fillMode = FILL_NONE
                            fillMatched = true
                        } else if (name == null) {
                            return null
                        }
                    }
                    name == null -> name = tok
                }
            }

            val resolvedName = name?.takeIf { it.isNotEmpty() && !it.equals("none", ignoreCase = true) } ?: return null
            return CssAnimationSpec(
                name = resolvedName,
                durationMs = (durationMs ?: 0f).coerceAtLeast(0f),
                timingFunction = timing,
                delayMs = delayMs,
                iterationCount = count,
                direction = direction,
                fillMode = fillMode,
                playState = playState
            )
        }

        private fun isTimingFunctionToken(lower: String): Boolean =
            lower == "linear" ||
                lower == "ease" ||
                lower == "ease-in" ||
                lower == "ease-out" ||
                lower == "ease-in-out" ||
                lower.startsWith("cubic-bezier(")

        private fun splitCommaTokens(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            val cleaned = stripImportant(raw)
            if (cleaned.isEmpty()) return emptyList()
            return CssTransitionSpec.splitTopLevelCommas(cleaned)
        }

        private fun <T> List<T>.atCycled(index: Int): T? =
            if (isEmpty()) null else this[index % size]

        private fun stripImportant(raw: String): String =
            raw.replace("!important", "", ignoreCase = true).trim()
    }
}
