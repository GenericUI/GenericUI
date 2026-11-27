package com.nexustools.genericui.style.css

/**
 * Parsed W3C CSS `@media` query supporting comma-separated query lists, `not`/`and` combinators,
 * media types (`all`, `screen`, `print`), width/height bounds (`min-width`, `max-width`,
 * `min-height`, `max-height`), `orientation` (`portrait`/`landscape`), and `prefers-color-scheme`.
 */
data class CssMediaQuery(
    val rawHeader: String,
    val clauses: List<MediaClause> = emptyList()
) {
    data class MediaFeature(
        val name: String,
        val rawValue: String?
    )

    data class MediaClause(
        val negated: Boolean = false,
        val mediaType: String? = null,
        val features: List<MediaFeature> = emptyList()
    )

    fun minWidthBoundsPx(): Int? {
        var minW: Int? = null
        for (c in clauses) {
            if (c.negated) continue
            for (f in c.features) {
                if (f.name == "min-width" && f.rawValue != null) {
                    val px = CssLength.parse(f.rawValue)?.resolvePx()?.toInt()
                    if (px != null) {
                        minW = if (minW == null) px else maxOf(minW, px)
                    }
                }
            }
        }
        return minW
    }

    fun maxWidthBoundsPx(): Int? {
        var maxW: Int? = null
        for (c in clauses) {
            if (c.negated) continue
            for (f in c.features) {
                if (f.name == "max-width" && f.rawValue != null) {
                    val px = CssLength.parse(f.rawValue)?.resolvePx()?.toInt()
                    if (px != null) {
                        maxW = if (maxW == null) px else minOf(maxW, px)
                    }
                }
            }
        }
        return maxW
    }

    fun matches(
        viewportWidthPx: Int,
        viewportHeightPx: Int = 800,
        isDarkTheme: Boolean = false
    ): Boolean {
        if (clauses.isEmpty()) return true
        for (clause in clauses) {
            val baseMatch = matchesClause(clause, viewportWidthPx, viewportHeightPx, isDarkTheme)
            val result = if (clause.negated) !baseMatch else baseMatch
            if (result) return true
        }
        return false
    }

    private fun matchesClause(
        clause: MediaClause,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        isDarkTheme: Boolean
    ): Boolean {
        val type = clause.mediaType?.lowercase()
        if (type != null && type != "all" && type != "screen") {
            return false
        }
        for (feature in clause.features) {
            if (!matchesFeature(feature, viewportWidthPx, viewportHeightPx, isDarkTheme)) {
                return false
            }
        }
        return true
    }

    private fun matchesFeature(
        feature: MediaFeature,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        isDarkTheme: Boolean
    ): Boolean {
        val v = feature.rawValue?.trim()?.lowercase()
        return when (feature.name) {
            "min-width" -> {
                val px = v?.let { CssLength.parse(it)?.resolvePx(viewportWidthPx = viewportWidthPx.toFloat()) } ?: return false
                viewportWidthPx >= px
            }
            "max-width" -> {
                val px = v?.let { CssLength.parse(it)?.resolvePx(viewportWidthPx = viewportWidthPx.toFloat()) } ?: return false
                viewportWidthPx <= px
            }
            "width" -> {
                val px = v?.let { CssLength.parse(it)?.resolvePx(viewportWidthPx = viewportWidthPx.toFloat()) } ?: return false
                viewportWidthPx == px.toInt()
            }
            "min-height" -> {
                val px = v?.let { CssLength.parse(it)?.resolvePx(viewportHeightPx = viewportHeightPx.toFloat()) } ?: return false
                viewportHeightPx >= px
            }
            "max-height" -> {
                val px = v?.let { CssLength.parse(it)?.resolvePx(viewportHeightPx = viewportHeightPx.toFloat()) } ?: return false
                viewportHeightPx <= px
            }
            "height" -> {
                val px = v?.let { CssLength.parse(it)?.resolvePx(viewportHeightPx = viewportHeightPx.toFloat()) } ?: return false
                viewportHeightPx == px.toInt()
            }
            "orientation" -> when (v) {
                "landscape" -> viewportWidthPx > viewportHeightPx
                "portrait" -> viewportHeightPx >= viewportWidthPx
                else -> false
            }
            "prefers-color-scheme" -> when (v) {
                "dark" -> isDarkTheme
                "light" -> !isDarkTheme
                else -> false
            }
            "color", "hover" -> true
            else -> {
                // Support range syntax inside feature e.g. width >= 600px, width <= 900px
                val match = RANGE_SYNTAX_REGEX.matchEntire(feature.name + (v?.let { ":$it" } ?: ""))
                if (match != null) {
                    val prop = match.groupValues[1].lowercase()
                    val op = match.groupValues[2]
                    val lenPx = CssLength.parse(match.groupValues[3])?.resolvePx() ?: return true
                    val actual = if (prop == "width") viewportWidthPx.toFloat() else viewportHeightPx.toFloat()
                    when (op) {
                        ">=" -> actual >= lenPx
                        "<=" -> actual <= lenPx
                        ">" -> actual > lenPx
                        "<" -> actual < lenPx
                        "=" -> actual == lenPx
                        else -> true
                    }
                } else {
                    true
                }
            }
        }
    }

    companion object {
        private val RANGE_SYNTAX_REGEX = Regex("^(width|height)\\s*(>=|<=|>|<|=)\\s*(.+)$", RegexOption.IGNORE_CASE)
        private val MEDIA_PREFIX_REGEX = Regex("^@media\\b", RegexOption.IGNORE_CASE)

        fun parse(rawHeader: String): CssMediaQuery {
            val cleaned = rawHeader
                .replace(MEDIA_PREFIX_REGEX, "")
                .trim()
            if (cleaned.isEmpty()) {
                return CssMediaQuery(rawHeader = rawHeader, clauses = emptyList())
            }
            val listParts = splitTopLevelCommas(cleaned)
            val clauses = ArrayList<MediaClause>(listParts.size)
            for (part in listParts) {
                parseSingleClause(part)?.let { clauses.add(it) }
            }
            return CssMediaQuery(rawHeader = rawHeader, clauses = clauses)
        }

        private fun parseSingleClause(rawClause: String): MediaClause? {
            var work = rawClause.trim()
            if (work.isEmpty()) return null
            var negated = false
            if (work.startsWith("not ", ignoreCase = true)) {
                negated = true
                work = work.substring(4).trim()
            } else if (work.startsWith("only ", ignoreCase = true)) {
                work = work.substring(5).trim()
            }

            val features = mutableListOf<MediaFeature>()
            var mediaType: String? = null
            var i = 0
            while (i < work.length) {
                while (i < work.length && work[i].isWhitespace()) i++
                if (i >= work.length) break
                if (work[i] == '(') {
                    var depth = 1
                    val start = i + 1
                    i++
                    while (i < work.length && depth > 0) {
                        if (work[i] == '(') depth++
                        else if (work[i] == ')') depth--
                        i++
                    }
                    val expr = work.substring(start, (i - 1).coerceAtLeast(start)).trim()
                    if (expr.isNotEmpty()) {
                        val colonIdx = expr.indexOf(':')
                        if (colonIdx > 0) {
                            features.add(
                                MediaFeature(
                                    name = expr.substring(0, colonIdx).trim().lowercase(),
                                    rawValue = expr.substring(colonIdx + 1).trim()
                                )
                            )
                        } else {
                            features.add(MediaFeature(name = expr.lowercase(), rawValue = null))
                        }
                    }
                } else {
                    val wordStart = i
                    while (i < work.length && !work[i].isWhitespace() && work[i] != '(') i++
                    val word = work.substring(wordStart, i).trim().lowercase()
                    if (word != "and" && word != "or" && mediaType == null && word.isNotEmpty()) {
                        mediaType = word
                    }
                }
            }
            return MediaClause(negated = negated, mediaType = mediaType, features = features)
        }

        private fun splitTopLevelCommas(input: String): List<String> {
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var depth = 0
            for (ch in input) {
                when {
                    ch == '(' -> {
                        depth++
                        cur.append(ch)
                    }
                    ch == ')' -> {
                        if (depth > 0) depth--
                        cur.append(ch)
                    }
                    ch == ',' && depth == 0 -> {
                        val s = cur.toString().trim()
                        if (s.isNotEmpty()) out.add(s)
                        cur.clear()
                    }
                    else -> cur.append(ch)
                }
            }
            val tail = cur.toString().trim()
            if (tail.isNotEmpty()) out.add(tail)
            return out
        }
    }
}
