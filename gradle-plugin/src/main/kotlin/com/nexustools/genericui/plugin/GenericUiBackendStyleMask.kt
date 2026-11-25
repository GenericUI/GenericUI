package com.nexustools.genericui.plugin

import java.util.Locale

/**
 * Build-time mirror of `:common`'s `BackendStyleMask` bitmask and `@<backend>` directive parser
 * used by `compileGenericUiSass` and `GenericUiWebDistributionTask`.
 */
internal object GenericUiBackendStyleMask {
    const val NONE: Int = 0
    const val WINDOWS: Int = 1 shl 0
    const val MACOS: Int = 1 shl 1
    const val GTK: Int = 1 shl 2
    const val KDE: Int = 1 shl 3
    const val SWING: Int = 1 shl 4
    const val ANDROID: Int = 1 shl 5
    const val IOS: Int = 1 shl 6
    const val IPADOS: Int = 1 shl 7
    const val WEB: Int = 1 shl 8
    const val ALL: Int = 0x1FF

    const val SYSTEM: Int = ALL and WEB.inv()
    const val DESKTOP: Int = WINDOWS or MACOS or GTK or KDE or SWING
    const val NATIVE_DESKTOP: Int = WINDOWS or MACOS or GTK or KDE
    const val LINUX: Int = GTK or KDE
    const val MOBILE: Int = ANDROID or IOS or IPADOS
    const val APPLE: Int = MACOS or IOS or IPADOS
    const val IOS_FAMILY: Int = IOS or IPADOS
    const val UNIX: Int = MACOS or GTK or KDE or ANDROID or IOS or IPADOS
    const val POSIX: Int = WINDOWS or MACOS or GTK or KDE or IOS or IPADOS
    const val NATIVE: Int = POSIX
    const val JVM: Int = SWING or ANDROID
    const val TOUCH_PRIMARY: Int = MOBILE
    const val BROWSER: Int = WEB

    private val CONCRETE_BITS_IN_ORDER: List<Pair<Int, String>> = listOf(
        WINDOWS to "windows",
        MACOS to "macos",
        GTK to "gtk",
        KDE to "kde",
        SWING to "swing",
        ANDROID to "android",
        IOS to "ios",
        IPADOS to "ipados",
        WEB to "web"
    )

    private val STANDARD_CSS_AT_KEYWORDS: Set<String> = setOf(
        "media",
        "keyframes",
        "supports",
        "font-face",
        "layer",
        "container",
        "property",
        "import",
        "use",
        "forward",
        "mixin",
        "include",
        "extend",
        "at-root",
        "charset",
        "namespace",
        "page",
        "scope",
        "starting-style",
        "counter-style",
        "font-palette-values",
        "font-feature-values",
        "color-profile"
    )

    fun parseToken(rawToken: String): Int? = when (rawToken.trim().lowercase(Locale.US)) {
        "all", "*" -> ALL
        "none" -> NONE
        "windows", "win", "win32" -> WINDOWS
        "macos", "mac", "mac_os", "mac-os", "osx", "darwin" -> MACOS
        "gtk" -> GTK
        "kde", "qt" -> KDE
        "swing", "desktop-jvm", "desktop_jvm" -> SWING
        "android" -> ANDROID
        "ios" -> IOS
        "ipados" -> IPADOS
        "web", "wasm", "js" -> WEB
        "browser" -> BROWSER
        "system" -> SYSTEM
        "desktop" -> DESKTOP
        "native-desktop", "native_desktop", "nativedesktop" -> NATIVE_DESKTOP
        "linux" -> LINUX
        "mobile" -> MOBILE
        "apple" -> APPLE
        "ios-family", "ios_family", "iosfamily" -> IOS_FAMILY
        "unix" -> UNIX
        "posix" -> POSIX
        "native" -> NATIVE
        "jvm" -> JVM
        "touch-primary", "touch_primary", "touchprimary" -> TOUCH_PRIMARY
        else -> null
    }

    private sealed interface ExprToken {
        data object LParen : ExprToken
        data object RParen : ExprToken
        data object Not : ExprToken
        data object And : ExprToken
        data object Or : ExprToken
        data class Mask(val bits: Int) : ExprToken
    }

    private class ExprParser(private val tokens: List<ExprToken>) {
        private var pos: Int = 0

        fun parse(): Int? {
            val result = parseOr() ?: return null
            return if (pos == tokens.size) result else null
        }

        private fun parseOr(): Int? {
            var left = parseAnd() ?: return null
            while (pos < tokens.size && tokens[pos] === ExprToken.Or) {
                pos++
                val right = parseAnd() ?: return null
                left = (left or right) and ALL
            }
            return left
        }

        private fun parseAnd(): Int? {
            var left = parseNot() ?: return null
            while (pos < tokens.size && tokens[pos] === ExprToken.And) {
                pos++
                val right = parseNot() ?: return null
                left = (left and right) and ALL
            }
            return left
        }

        private fun parseNot(): Int? {
            if (pos < tokens.size && tokens[pos] === ExprToken.Not) {
                pos++
                val operand = parseNot() ?: return null
                return ALL and operand.inv()
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Int? {
            if (pos >= tokens.size) return null
            return when (val tok = tokens[pos]) {
                ExprToken.LParen -> {
                    pos++
                    val inner = parseOr() ?: return null
                    if (pos >= tokens.size || tokens[pos] !== ExprToken.RParen) return null
                    pos++
                    inner
                }
                is ExprToken.Mask -> {
                    pos++
                    tok.bits and ALL
                }
                else -> null
            }
        }
    }

    fun parseExpression(rawExpr: String): Int? {
        val tokens = tokenizeExpression(rawExpr) ?: return null
        if (tokens.isEmpty()) return null
        return ExprParser(tokens).parse()
    }

    private fun tokenizeExpression(rawExpr: String): List<ExprToken>? {
        val out = mutableListOf<ExprToken>()
        var i = 0
        val n = rawExpr.length
        while (i < n) {
            val ch = rawExpr[i]
            when {
                ch.isWhitespace() -> i++
                ch == '(' -> {
                    out.add(ExprToken.LParen)
                    i++
                }
                ch == ')' -> {
                    out.add(ExprToken.RParen)
                    i++
                }
                ch == ',' -> {
                    out.add(ExprToken.Or)
                    i++
                }
                ch == '!' -> {
                    out.add(ExprToken.Not)
                    i++
                }
                ch == '&' -> {
                    if (i + 1 < n && rawExpr[i + 1] == '&') {
                        out.add(ExprToken.And)
                        i += 2
                    } else {
                        return null
                    }
                }
                ch == '|' -> {
                    if (i + 1 < n && rawExpr[i + 1] == '|') {
                        out.add(ExprToken.Or)
                        i += 2
                    } else {
                        return null
                    }
                }
                ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '*' -> {
                    val start = i
                    while (i < n && (rawExpr[i].isLetterOrDigit() || rawExpr[i] == '_' || rawExpr[i] == '-' || rawExpr[i] == '*')) {
                        i++
                    }
                    val word = rawExpr.substring(start, i)
                    when (word.lowercase(Locale.US)) {
                        "not" -> out.add(ExprToken.Not)
                        "and" -> out.add(ExprToken.And)
                        "or" -> out.add(ExprToken.Or)
                        else -> {
                            val bits = parseToken(word) ?: return null
                            out.add(ExprToken.Mask(bits))
                        }
                    }
                }
                else -> return null
            }
        }
        return out
    }

    fun parseDirectiveHeader(rawHeader: String): Int? {
        val trimmed = rawHeader.trim()
        if (!trimmed.startsWith("@") || trimmed.length < 2) return null

        val afterAt = trimmed.substring(1).trimStart()
        var kwEnd = 0
        while (kwEnd < afterAt.length && (afterAt[kwEnd].isLetterOrDigit() || afterAt[kwEnd] == '-' || afterAt[kwEnd] == '_')) {
            kwEnd++
        }
        if (kwEnd == 0) return null
        val keyword = afterAt.substring(0, kwEnd).lowercase(Locale.US)
        if (keyword.startsWith("-") || keyword in STANDARD_CSS_AT_KEYWORDS) {
            return null
        }

        val rest = afterAt.substring(kwEnd).trim()
        if (keyword == "backend" || keyword == "platform" || keyword == "target") {
            if (!rest.startsWith("(") || !rest.endsWith(")")) return null
            val inner = rest.substring(1, rest.length - 1)
            return parseExpression(inner)
        }

        if (rest.isNotEmpty()) return null
        return parseToken(keyword)
    }

    fun formatCanonicalDirective(mask: Int): String = when (val m = mask and ALL) {
        ALL -> "@all"
        NONE -> "@none"
        WINDOWS -> "@windows"
        MACOS -> "@macos"
        GTK -> "@gtk"
        KDE -> "@kde"
        SWING -> "@swing"
        ANDROID -> "@android"
        IOS -> "@ios"
        IPADOS -> "@ipados"
        WEB -> "@web"
        SYSTEM -> "@system"
        DESKTOP -> "@desktop"
        NATIVE_DESKTOP -> "@native-desktop"
        LINUX -> "@linux"
        MOBILE -> "@mobile"
        APPLE -> "@apple"
        IOS_FAMILY -> "@ios-family"
        UNIX -> "@unix"
        POSIX -> "@posix"
        JVM -> "@jvm"
        else -> {
            val parts = CONCRETE_BITS_IN_ORDER.mapNotNull { (bit, name) ->
                if ((m and bit) != 0) name else null
            }
            if (parts.isEmpty()) "@none" else "@backend(${parts.joinToString(", ")})"
        }
    }

    fun formatKotlinExpr(mask: Int): String = when (val m = mask and ALL) {
        ALL -> "BackendStyleMask.ALL"
        NONE -> "BackendStyleMask.NONE"
        WINDOWS -> "BackendStyleMask.WINDOWS"
        MACOS -> "BackendStyleMask.MACOS"
        GTK -> "BackendStyleMask.GTK"
        KDE -> "BackendStyleMask.KDE"
        SWING -> "BackendStyleMask.SWING"
        ANDROID -> "BackendStyleMask.ANDROID"
        IOS -> "BackendStyleMask.IOS"
        IPADOS -> "BackendStyleMask.IPADOS"
        WEB -> "BackendStyleMask.WEB"
        SYSTEM -> "BackendStyleMask.SYSTEM"
        DESKTOP -> "BackendStyleMask.DESKTOP"
        NATIVE_DESKTOP -> "BackendStyleMask.NATIVE_DESKTOP"
        LINUX -> "BackendStyleMask.LINUX"
        MOBILE -> "BackendStyleMask.MOBILE"
        APPLE -> "BackendStyleMask.APPLE"
        IOS_FAMILY -> "BackendStyleMask.IOS_FAMILY"
        UNIX -> "BackendStyleMask.UNIX"
        POSIX -> "BackendStyleMask.POSIX"
        JVM -> "BackendStyleMask.JVM"
        else -> "0x" + m.toString(16).uppercase(Locale.US).padStart(3, '0')
    }

    fun containsGenericUiBackendDirective(source: String): Boolean {
        var i = 0
        val n = source.length
        var inSingle = false
        var inDouble = false
        while (i < n) {
            val ch = source[i]
            if (inSingle) {
                if (ch == '\\' && i + 1 < n) { i += 2; continue }
                if (ch == '\'') inSingle = false
                i++
                continue
            }
            if (inDouble) {
                if (ch == '\\' && i + 1 < n) { i += 2; continue }
                if (ch == '"') inDouble = false
                i++
                continue
            }
            if (ch == '\'') {
                inSingle = true
                i++
                continue
            }
            if (ch == '"') {
                inDouble = true
                i++
                continue
            }
            if (ch == '/' && i + 1 < n && source[i + 1] == '*') {
                i += 2
                while (i + 1 < n && !(source[i] == '*' && source[i + 1] == '/')) i++
                i += 2
                continue
            }
            if (ch == '/' && i + 1 < n && source[i + 1] == '/') {
                i += 2
                while (i < n && source[i] != '\n') i++
                continue
            }
            if (ch == '@') {
                var j = i + 1
                var parenDepth = 0
                var hSingle = false
                var hDouble = false
                while (j < n) {
                    val hch = source[j]
                    if (hSingle) {
                        if (hch == '\\' && j + 1 < n) { j += 2; continue }
                        if (hch == '\'') hSingle = false
                    } else if (hDouble) {
                        if (hch == '\\' && j + 1 < n) { j += 2; continue }
                        if (hch == '"') hDouble = false
                    } else {
                        when (hch) {
                            '\'' -> hSingle = true
                            '"' -> hDouble = true
                            '(' -> parenDepth++
                            ')' -> parenDepth = (parenDepth - 1).coerceAtLeast(0)
                            '{', ';', '}' -> if (parenDepth == 0) break
                        }
                    }
                    j++
                }
                if (j < n && source[j] == '{') {
                    val header = source.substring(i, j).trim()
                    if (parseDirectiveHeader(header) != null) {
                        return true
                    }
                }
            }
            i++
        }
        return false
    }
}
