package com.nexustools.genericui.style.transition

/**
 * Parsed CSS transition specification for a single property (e.g., `transform .5s ease`,
 * `opacity .5s ease-in`, `left 300ms cubic-bezier(0.4, 0, 0.2, 1) 50ms`).
 */
data class CssTransitionSpec(
    val property: String,
    val durationMs: Float,
    val timingFunction: CssTimingFunction = CssTimingFunction.EASE,
    val delayMs: Float = 0f
) {
    fun matches(propertyName: String): Boolean {
        val norm = propertyName.trim().lowercase()
        return property == "all" || property == norm
    }

    fun toCssString(): String {
        val durStr = "${durationMs.toInt()}ms"
        val timingStr = timingFunction.toCssString()
        return if (delayMs > 0f) {
            "$property $durStr $timingStr ${delayMs.toInt()}ms"
        } else {
            "$property $durStr $timingStr"
        }
    }

    companion object {
        fun parseList(transitionCss: String?): List<CssTransitionSpec> {
            if (transitionCss.isNullOrBlank()) return emptyList()
            val cleaned = transitionCss.replace("!important", "", ignoreCase = true).trim()
            if (cleaned.isEmpty() || cleaned.equals("none", ignoreCase = true)) return emptyList()

            val segments = splitTopLevelCommas(cleaned)
            val result = mutableListOf<CssTransitionSpec>()
            for (seg in segments) {
                parseSingle(seg)?.let { result.add(it) }
            }
            return result
        }

        fun resolveEffectiveSpecs(
            transitionShorthand: String? = null,
            transitionProperty: String? = null,
            transitionDuration: String? = null,
            transitionTimingFunction: String? = null,
            transitionDelay: String? = null
        ): List<CssTransitionSpec> {
            if (transitionProperty == null &&
                transitionDuration == null &&
                transitionTimingFunction == null &&
                transitionDelay == null
            ) {
                return parseList(transitionShorthand)
            }

            val base = parseList(transitionShorthand)
            val props = transitionProperty
                ?.let { splitTopLevelCommas(it.replace("!important", "", ignoreCase = true)) }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?: if (base.isNotEmpty()) base.map { it.property } else listOf("all")
            if (props.size == 1 && props[0].equals("none", ignoreCase = true)) return emptyList()

            val durations = transitionDuration
                ?.let { splitTopLevelCommas(it.replace("!important", "", ignoreCase = true)) }
                ?.map { parseTimeMs(it) ?: 0f }
                ?.takeIf { it.isNotEmpty() }
                ?: base.map { it.durationMs }.takeIf { it.isNotEmpty() }
                ?: emptyList()

            val timings = transitionTimingFunction
                ?.let { splitTopLevelCommas(it.replace("!important", "", ignoreCase = true)) }
                ?.map { CssTimingFunction.parse(it) }
                ?.takeIf { it.isNotEmpty() }
                ?: base.map { it.timingFunction }.takeIf { it.isNotEmpty() }
                ?: listOf(CssTimingFunction.EASE)

            val delays = transitionDelay
                ?.let { splitTopLevelCommas(it.replace("!important", "", ignoreCase = true)) }
                ?.map { parseTimeMs(it) ?: 0f }
                ?.takeIf { it.isNotEmpty() }
                ?: base.map { it.delayMs }.takeIf { it.isNotEmpty() }
                ?: listOf(0f)

            val out = ArrayList<CssTransitionSpec>(props.size)
            for (idx in props.indices) {
                val propName = props[idx].lowercase()
                if (propName == "none") continue
                val dur = if (durations.isNotEmpty()) durations[idx % durations.size] else 0f
                if (dur <= 0f) continue
                val tf = timings[idx % timings.size]
                val del = delays[idx % delays.size]
                out.add(CssTransitionSpec(property = propName, durationMs = dur, timingFunction = tf, delayMs = del))
            }
            return out
        }

        fun findMatching(specs: List<CssTransitionSpec>, propertyName: String): CssTransitionSpec? {
            val norm = propertyName.trim().lowercase()
            return specs.lastOrNull { it.property == norm } ?: specs.lastOrNull { it.property == "all" }
        }

        private fun parseSingle(segment: String): CssTransitionSpec? {
            val tokens = tokenizeTransitionSegment(segment.trim())
            if (tokens.isEmpty()) return null

            var property = "all"
            var durationMs: Float? = null
            var delayMs = 0f
            var timing = CssTimingFunction.EASE

            for (tok in tokens) {
                val lower = tok.lowercase()
                val timeVal = parseTimeMs(lower)
                if (timeVal != null) {
                    if (durationMs == null) {
                        durationMs = timeVal
                    } else {
                        delayMs = timeVal
                    }
                } else if (
                    lower == "linear" ||
                    lower == "ease" ||
                    lower == "ease-in" ||
                    lower == "ease-out" ||
                    lower == "ease-in-out" ||
                    lower.startsWith("cubic-bezier(")
                ) {
                    timing = CssTimingFunction.parse(lower)
                } else {
                    property = lower
                }
            }

            val finalDuration = durationMs ?: return null
            if (finalDuration <= 0f) return null
            return CssTransitionSpec(
                property = property,
                durationMs = finalDuration,
                timingFunction = timing,
                delayMs = delayMs
            )
        }

        internal fun parseTimeMs(token: String): Float? {
            val t = token.trim().lowercase()
            if (t.endsWith("ms")) {
                return t.removeSuffix("ms").trim().toFloatOrNull()
            }
            if (t.endsWith("s")) {
                val sec = t.removeSuffix("s").trim().toFloatOrNull() ?: return null
                return sec * 1000f
            }
            return null
        }

        internal fun splitTopLevelCommas(input: String): List<String> {
            val result = mutableListOf<String>()
            var depth = 0
            var start = 0
            for (i in input.indices) {
                when (input[i]) {
                    '(' -> depth++
                    ')' -> if (depth > 0) depth--
                    ',' -> if (depth == 0) {
                        val sub = input.substring(start, i).trim()
                        if (sub.isNotEmpty()) result.add(sub)
                        start = i + 1
                    }
                }
            }
            val tail = input.substring(start).trim()
            if (tail.isNotEmpty()) result.add(tail)
            return result
        }

        internal fun tokenizeTransitionSegment(segment: String): List<String> {
            val tokens = mutableListOf<String>()
            var depth = 0
            val current = StringBuilder()
            for (ch in segment) {
                when {
                    ch == '(' -> {
                        depth++
                        current.append(ch)
                    }
                    ch == ')' -> {
                        if (depth > 0) depth--
                        current.append(ch)
                    }
                    ch.isWhitespace() && depth == 0 -> {
                        if (current.isNotEmpty()) {
                            tokens.add(current.toString())
                            current.clear()
                        }
                    }
                    else -> current.append(ch)
                }
            }
            if (current.isNotEmpty()) {
                tokens.add(current.toString())
            }
            return tokens
        }
    }
}
