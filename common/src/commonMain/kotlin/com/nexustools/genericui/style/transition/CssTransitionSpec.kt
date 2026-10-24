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

        private fun splitTopLevelCommas(input: String): List<String> {
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

        private fun tokenizeTransitionSegment(segment: String): List<String> {
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
