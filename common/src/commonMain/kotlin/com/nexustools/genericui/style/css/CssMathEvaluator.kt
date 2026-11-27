package com.nexustools.genericui.style.css

import kotlin.math.max
import kotlin.math.min

/**
 * Recursive W3C CSS math expression evaluator supporting `calc(...)`, `min(...)`, `max(...)`,
 * and `clamp(min, val, max)` with `+`, `-`, `*`, `/`, parenthesized sub-expressions, and mixed
 * `%`, `px`, `dp`, `pt`, `em`, `rem`, `vw`, `vh`, `vmin`, `vmax`, and bare numeric terms.
 */
object CssMathEvaluator {

    private val EM_UNIT_REGEX = Regex("""[0-9.]em\b""")

    fun isMathFunction(raw: String): Boolean {
        val lower = raw.trim().lowercase()
        return lower.startsWith("calc(") ||
            lower.startsWith("min(") ||
            lower.startsWith("max(") ||
            lower.startsWith("clamp(")
    }

    fun isValidLengthExpression(raw: String): Boolean {
        val trimmed = raw.trim()
        if (!isMathFunction(trimmed)) return false
        return evaluateLengthPx(
            expression = trimmed,
            referencePx = 100f,
            rootFontSizePx = 16f,
            parentFontSizePx = 16f,
            viewportWidthPx = 1280f,
            viewportHeightPx = 800f
        ) != null
    }

    /**
     * Checks whether a length expression depends ONLY on fixed physical units (`px`, `dp`, `pt`, `0`)
     * so it can safely fold into a static [CssLength] pixel constant at parse time.
     */
    fun isFixedPixelOnlyExpression(raw: String): Boolean {
        val lower = raw.lowercase()
        return '%' !in lower &&
            "vw" !in lower &&
            "vh" !in lower &&
            "vmin" !in lower &&
            "vmax" !in lower &&
            "rem" !in lower &&
            !EM_UNIT_REGEX.containsMatchIn(lower)
    }

    fun evaluateLengthPx(
        expression: String,
        referencePx: Float = 0f,
        rootFontSizePx: Float = 16f,
        parentFontSizePx: Float = 16f,
        viewportWidthPx: Float = 1280f,
        viewportHeightPx: Float = 800f
    ): Float? {
        val cleaned = expression.trim()
        if (cleaned.isEmpty()) return null
        val parser = Parser(
            input = cleaned,
            referencePx = referencePx,
            rootFontSizePx = rootFontSizePx,
            parentFontSizePx = parentFontSizePx,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            bareNumberIsPx = true
        )
        val result = parser.parseExpression() ?: return null
        parser.skipWhitespace()
        if (!parser.isAtEnd()) return null
        return if (result.value.isFinite()) result.value else null
    }

    fun evaluateScalar(expression: String): Float? {
        val cleaned = expression.trim()
        if (cleaned.isEmpty()) return null
        val parser = Parser(
            input = cleaned,
            referencePx = 0f,
            rootFontSizePx = 16f,
            parentFontSizePx = 16f,
            viewportWidthPx = 1280f,
            viewportHeightPx = 800f,
            bareNumberIsPx = false
        )
        val result = parser.parseExpression() ?: return null
        parser.skipWhitespace()
        if (!parser.isAtEnd()) return null
        return if (result.value.isFinite()) result.value else null
    }

    private data class EvalValue(
        val value: Float,
        val isDimension: Boolean
    )

    private class Parser(
        private val input: String,
        private val referencePx: Float,
        private val rootFontSizePx: Float,
        private val parentFontSizePx: Float,
        private val viewportWidthPx: Float,
        private val viewportHeightPx: Float,
        private val bareNumberIsPx: Boolean
    ) {
        private var pos: Int = 0

        fun isAtEnd(): Boolean = pos >= input.length

        fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }

        fun parseExpression(): EvalValue? {
            var left = parseTerm() ?: return null
            while (true) {
                skipWhitespace()
                if (pos >= input.length) break
                val op = input[pos]
                if (op != '+' && op != '-') break
                pos++
                val right = parseTerm() ?: return null
                val combined = if (op == '+') left.value + right.value else left.value - right.value
                left = EvalValue(combined, left.isDimension || right.isDimension)
            }
            return left
        }

        private fun parseTerm(): EvalValue? {
            var left = parseUnary() ?: return null
            while (true) {
                skipWhitespace()
                if (pos >= input.length) break
                val op = input[pos]
                if (op != '*' && op != '/') break
                pos++
                val right = parseUnary() ?: return null
                val combined = if (op == '*') {
                    left.value * right.value
                } else {
                    if (right.value == 0f) return null
                    left.value / right.value
                }
                left = EvalValue(combined, left.isDimension || right.isDimension)
            }
            return left
        }

        private fun parseUnary(): EvalValue? {
            skipWhitespace()
            if (pos >= input.length) return null
            val ch = input[pos]
            if (ch == '+') {
                pos++
                return parseUnary()
            }
            if (ch == '-') {
                pos++
                val v = parseUnary() ?: return null
                return EvalValue(-v.value, v.isDimension)
            }
            return parsePrimary()
        }

        private fun parsePrimary(): EvalValue? {
            skipWhitespace()
            if (pos >= input.length) return null
            val ch = input[pos]
            if (ch == '(') {
                pos++
                val inner = parseExpression() ?: return null
                skipWhitespace()
                if (pos >= input.length || input[pos] != ')') return null
                pos++
                return inner
            }
            if (ch.isLetter()) {
                val fnStart = pos
                while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '-' || input[pos] == '_')) {
                    pos++
                }
                val ident = input.substring(fnStart, pos).lowercase()
                skipWhitespace()
                if (pos >= input.length || input[pos] != '(') {
                    return when (ident) {
                        "pi" -> EvalValue(3.1415927f, false)
                        "e" -> EvalValue(2.7182817f, false)
                        else -> null
                    }
                }
                pos++ // consume '('
                val args = mutableListOf<EvalValue>()
                while (true) {
                    val arg = parseExpression() ?: return null
                    args.add(arg)
                    skipWhitespace()
                    if (pos >= input.length) return null
                    val sep = input[pos]
                    if (sep == ',') {
                        pos++
                        continue
                    }
                    if (sep == ')') {
                        pos++
                        break
                    }
                    return null
                }
                return evaluateFunction(ident, args)
            }
            return parseNumberWithOptionalUnit()
        }

        private fun evaluateFunction(name: String, args: List<EvalValue>): EvalValue? {
            if (args.isEmpty()) return null
            return when (name) {
                "calc" -> if (args.size == 1) args[0] else null
                "min" -> {
                    var best = args[0].value
                    var dim = args[0].isDimension
                    for (i in 1 until args.size) {
                        best = min(best, args[i].value)
                        dim = dim || args[i].isDimension
                    }
                    EvalValue(best, dim)
                }
                "max" -> {
                    var best = args[0].value
                    var dim = args[0].isDimension
                    for (i in 1 until args.size) {
                        best = max(best, args[i].value)
                        dim = dim || args[i].isDimension
                    }
                    EvalValue(best, dim)
                }
                "clamp" -> {
                    if (args.size != 3) return null
                    val minV = args[0].value
                    val prefV = args[1].value
                    val maxV = args[2].value
                    val clamped = max(minV, min(prefV, maxV))
                    EvalValue(clamped, args.any { it.isDimension })
                }
                else -> null
            }
        }

        private fun parseNumberWithOptionalUnit(): EvalValue? {
            val start = pos
            var sawDigit = false
            var sawDot = false
            while (pos < input.length) {
                val c = input[pos]
                if (c.isDigit()) {
                    sawDigit = true
                    pos++
                } else if (c == '.' && !sawDot) {
                    sawDot = true
                    pos++
                } else {
                    break
                }
            }
            if (!sawDigit) return null
            val num = input.substring(start, pos).toFloatOrNull() ?: return null
            val unitStart = pos
            if (pos < input.length && input[pos] == '%') {
                pos++
                return EvalValue((referencePx * (num / 100f)), true)
            }
            while (pos < input.length && input[pos].isLetter()) {
                pos++
            }
            val unit = input.substring(unitStart, pos).lowercase()
            if (unit.isEmpty()) {
                return EvalValue(num, bareNumberIsPx && num == 0f)
            }
            val resolved = when (unit) {
                "px", "dp" -> num
                "rem" -> num * rootFontSizePx
                "em" -> num * parentFontSizePx
                "vw" -> (num / 100f) * viewportWidthPx
                "vh" -> (num / 100f) * viewportHeightPx
                "vmin" -> (num / 100f) * min(viewportWidthPx, viewportHeightPx)
                "vmax" -> (num / 100f) * max(viewportWidthPx, viewportHeightPx)
                "pt" -> num * (96f / 72f)
                "deg" -> num
                "rad" -> num * 57.29578f
                "turn" -> num * 360f
                else -> return null
            }
            return EvalValue(resolved, true)
        }
    }
}
