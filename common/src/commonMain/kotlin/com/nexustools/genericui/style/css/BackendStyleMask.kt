package com.nexustools.genericui.style.css

import com.nexustools.genericui.enums.system.BackendTarget

/**
 * 9-bit bitmask and boolean expression parser for GenericUI backend-targeted SCSS/CSS `@` directives
 * (e.g. `@macos`, `@desktop`, `@system`, `@web`, `@backend(desktop and not macos)`).
 */
object BackendStyleMask {
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

    private val COMPOUND_DIRECTIVE_NAMES = setOf("backend", "platform", "target")

    val ALL_DIRECTIVE_DESCRIPTIONS: List<Pair<String, String>> = listOf(
        "@windows" to "Windows (Win32) backend only",
        "@macos" to "macOS (AppKit) backend only",
        "@gtk" to "Linux GTK4 / Libadwaita backend only",
        "@kde" to "Linux KDE Plasma backend only",
        "@swing" to "JVM Swing backend only",
        "@android" to "Android backend only",
        "@ios" to "iOS (UIKit) backend only",
        "@ipados" to "iPadOS (UIKit) backend only",
        "@web" to "Web (HTML5 / JS / Wasm) backend only",
        "@system" to "All OS/system backends (excludes Web)",
        "@desktop" to "All desktop backends (Windows, macOS, GTK, KDE, Swing)",
        "@native-desktop" to "Native desktop backends (Windows, macOS, GTK, KDE)",
        "@linux" to "Linux desktop backends (GTK and KDE)",
        "@mobile" to "Mobile backends (Android, iOS, iPadOS)",
        "@touch" to "Touch-primary backends (Android, iOS, iPadOS)",
        "@touch-primary" to "Touch-primary backends (Android, iOS, iPadOS)",
        "@apple" to "Apple backends (macOS, iOS, iPadOS)",
        "@ios-family" to "UIKit iOS-family backends (iOS, iPadOS)",
        "@uikit" to "UIKit iOS-family backends (iOS, iPadOS)",
        "@unix" to "Unix-like OS backends (macOS, GTK, KDE, Android, iOS, iPadOS)",
        "@posix" to "POSIX / Kotlin-Native backends (Windows, macOS, GTK, KDE, iOS, iPadOS)",
        "@native" to "Ahead-of-time native backends (Windows, macOS, GTK, KDE, iOS, iPadOS)",
        "@jvm" to "JVM / ART bytecode backends (Swing, Android)",
        "@browser" to "Browser environment (Web)",
        "@all" to "All GenericUI backends",
        "@backend(...)" to "Boolean backend expression using and, or, ',', not, and parentheses"
    )

    fun bitFor(target: BackendTarget): Int = when (target) {
        BackendTarget.Windows -> WINDOWS
        BackendTarget.MacOS -> MACOS
        BackendTarget.Gtk -> GTK
        BackendTarget.Kde -> KDE
        BackendTarget.Swing -> SWING
        BackendTarget.Android -> ANDROID
        BackendTarget.IOS -> IOS
        BackendTarget.IPadOS -> IPADOS
        BackendTarget.Web -> WEB
    }

    fun matches(mask: Int, target: BackendTarget?): Boolean =
        mask != 0 && (target == null || (mask and bitFor(target)) != 0)

    fun resolveRuntimeBackendTarget(
        applicationTarget: BackendTarget? = null,
        factoryTarget: BackendTarget? = null
    ): BackendTarget = applicationTarget ?: factoryTarget ?: resolveHostDefaultBackendTarget()

    fun parseToken(raw: String): Int? = when (raw.trim().lowercase()) {
        "windows" -> WINDOWS
        "macos" -> MACOS
        "gtk" -> GTK
        "kde" -> KDE
        "swing" -> SWING
        "android" -> ANDROID
        "ios" -> IOS
        "ipados" -> IPADOS
        "web" -> WEB
        "system" -> SYSTEM
        "desktop" -> DESKTOP
        "native-desktop", "nativedesktop" -> NATIVE_DESKTOP
        "linux" -> LINUX
        "mobile" -> MOBILE
        "touch", "touch-primary" -> TOUCH_PRIMARY
        "apple" -> APPLE
        "ios-family", "uikit" -> IOS_FAMILY
        "unix" -> UNIX
        "posix" -> POSIX
        "native" -> NATIVE
        "jvm" -> JVM
        "browser" -> BROWSER
        "all" -> ALL
        else -> null
    }

    private enum class ExprTokenKind {
        IDENT,
        AND,
        OR,
        NOT,
        LPAREN,
        RPAREN,
        COMMA
    }

    private data class ExprToken(
        val kind: ExprTokenKind,
        val text: String
    )

    private fun tokenizeExpression(expr: String): List<ExprToken>? {
        val tokens = ArrayList<ExprToken>(8)
        var i = 0
        val n = expr.length
        while (i < n) {
            val ch = expr[i]
            when {
                ch.isWhitespace() -> i++
                ch == '(' -> {
                    tokens.add(ExprToken(ExprTokenKind.LPAREN, "("))
                    i++
                }
                ch == ')' -> {
                    tokens.add(ExprToken(ExprTokenKind.RPAREN, ")"))
                    i++
                }
                ch == ',' -> {
                    tokens.add(ExprToken(ExprTokenKind.COMMA, ","))
                    i++
                }
                isIdentChar(ch) -> {
                    val start = i
                    while (i < n && isIdentChar(expr[i])) i++
                    val raw = expr.substring(start, i)
                    val kind = when (raw.lowercase()) {
                        "and" -> ExprTokenKind.AND
                        "or" -> ExprTokenKind.OR
                        "not" -> ExprTokenKind.NOT
                        else -> ExprTokenKind.IDENT
                    }
                    tokens.add(ExprToken(kind, raw))
                }
                else -> return null
            }
        }
        return tokens
    }

    fun parseExpression(expr: String): Int? {
        val tokens = tokenizeExpression(expr) ?: return null
        if (tokens.isEmpty()) return null
        return ExprParser(tokens).parse()
    }

    private class ExprParser(private val tokens: List<ExprToken>) {
        private var pos: Int = 0

        fun parse(): Int? {
            val value = parseOr() ?: return null
            return if (pos == tokens.size) value else null
        }

        private fun peek(): ExprToken? = tokens.getOrNull(pos)

        private fun parseOr(): Int? {
            var left = parseAnd() ?: return null
            while (true) {
                val tok = peek() ?: break
                if (tok.kind == ExprTokenKind.OR || tok.kind == ExprTokenKind.COMMA) {
                    pos++
                    val right = parseAnd() ?: return null
                    left = left or right
                } else {
                    break
                }
            }
            return left
        }

        private fun parseAnd(): Int? {
            var left = parseUnary() ?: return null
            while (true) {
                val tok = peek() ?: break
                if (tok.kind == ExprTokenKind.AND) {
                    pos++
                    val right = parseUnary() ?: return null
                    left = left and right
                } else {
                    break
                }
            }
            return left
        }

        private fun parseUnary(): Int? {
            val tok = peek() ?: return null
            if (tok.kind == ExprTokenKind.NOT) {
                pos++
                val inner = parseUnary() ?: return null
                return ALL and inner.inv()
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Int? {
            val tok = peek() ?: return null
            return when (tok.kind) {
                ExprTokenKind.LPAREN -> {
                    pos++
                    val inner = parseOr() ?: return null
                    val close = peek() ?: return null
                    if (close.kind != ExprTokenKind.RPAREN) return null
                    pos++
                    inner
                }
                ExprTokenKind.IDENT -> {
                    pos++
                    parseToken(tok.text)
                }
                else -> null
            }
        }
    }

    fun parseDirectiveHeader(header: String): Int? {
        val trimmed = header.trim()
        if (!trimmed.startsWith("@")) return null
        val nameEnd = scanIdentEnd(trimmed, 1)
        if (nameEnd <= 1) return null
        val name = trimmed.substring(1, nameEnd)
        val lower = name.lowercase()
        if (lower in COMPOUND_DIRECTIVE_NAMES) {
            val inner = extractBalancedParensContent(trimmed, nameEnd) ?: return null
            return parseExpression(inner)
        }
        if (nameEnd < trimmed.length) return null
        return parseToken(lower)
    }

    fun isBackendDirectivePrefix(header: String): Boolean {
        val trimmed = header.trim()
        if (!trimmed.startsWith("@")) return false
        val nameEnd = scanIdentEnd(trimmed, 1)
        if (nameEnd <= 1) return false
        val lower = trimmed.substring(1, nameEnd).lowercase()
        return lower in COMPOUND_DIRECTIVE_NAMES || parseToken(lower) != null
    }

    fun validateDirectiveHeader(header: String): String? {
        val trimmed = header.trim()
        if (!trimmed.startsWith("@")) return null
        val nameEnd = scanIdentEnd(trimmed, 1)
        if (nameEnd <= 1) return null
        val rawName = trimmed.substring(1, nameEnd)
        val lower = rawName.lowercase()
        if (lower in COMPOUND_DIRECTIVE_NAMES) {
            var cursor = nameEnd
            while (cursor < trimmed.length && trimmed[cursor].isWhitespace()) cursor++
            if (cursor >= trimmed.length || trimmed[cursor] != '(') {
                return "Expected '(<expression>)' after '@$lower', e.g. '@$lower(macos, windows)'"
            }
            var depth = 0
            var i = cursor
            var closeIdx = -1
            while (i < trimmed.length) {
                when (trimmed[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            closeIdx = i
                            break
                        }
                    }
                }
                i++
            }
            if (closeIdx < 0) {
                return "Unbalanced parentheses in '@$lower(...)' directive"
            }
            if (trimmed.substring(closeIdx + 1).isNotBlank()) {
                return "Unexpected trailing tokens after '@$lower(...)' directive"
            }
            val inner = trimmed.substring(cursor + 1, closeIdx)
            if (inner.isBlank()) {
                return "Empty backend expression in '@$lower()'"
            }
            if (parseExpression(inner) == null) {
                return "Invalid backend expression '$inner' in '@$lower(...)'"
            }
            return null
        }
        if (parseToken(lower) != null && nameEnd < trimmed.length) {
            return "Unexpected arguments after '@$lower' directive; use '@backend(...)' for compound expressions"
        }
        return null
    }

    fun formatCanonicalDirective(mask: Int): String {
        val masked = mask and ALL
        return when (masked) {
            ALL -> "@all"
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
                val ids = BackendTarget.entries
                    .filter { (masked and bitFor(it)) != 0 }
                    .joinToString(", ") { it.id }
                "@backend($ids)"
            }
        }
    }

    fun formatKotlinExpr(mask: Int): String {
        val masked = mask and ALL
        return when (masked) {
            NONE -> "BackendStyleMask.NONE"
            ALL -> "BackendStyleMask.ALL"
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
            else -> "0x${masked.toString(16)}"
        }
    }

    fun containsGenericUiBackendDirective(cssOrScss: String): Boolean {
        if (!cssOrScss.contains('@')) return false
        val n = cssOrScss.length
        var i = 0
        var inQuote: Char? = null
        while (i < n) {
            val ch = cssOrScss[i]
            if (inQuote != null) {
                if (ch == '\\' && i + 1 < n) {
                    i += 2
                    continue
                }
                if (ch == inQuote) {
                    inQuote = null
                }
                i++
                continue
            }
            if (ch == '\'' || ch == '"') {
                inQuote = ch
                i++
                continue
            }
            if (ch == '/' && i + 1 < n && cssOrScss[i + 1] == '*') {
                val close = cssOrScss.indexOf("*/", i + 2)
                i = if (close >= 0) close + 2 else n
                continue
            }
            if (ch == '/' && i + 1 < n && cssOrScss[i + 1] == '/') {
                val prev = cssOrScss.substring(0, i)
                val lastNewline = prev.lastIndexOf('\n')
                val linePrefix = if (lastNewline >= 0) prev.substring(lastNewline + 1) else prev
                if (!linePrefix.contains("http:", ignoreCase = true) &&
                    !linePrefix.contains("https:", ignoreCase = true) &&
                    !linePrefix.contains("data:", ignoreCase = true)
                ) {
                    val nl = cssOrScss.indexOf('\n', i + 2)
                    i = if (nl >= 0) nl + 1 else n
                    continue
                }
            }
            if (ch == '@') {
                val prevValid = i == 0 || !isIdentChar(cssOrScss[i - 1])
                if (prevValid) {
                    val nameEnd = scanIdentEnd(cssOrScss, i + 1)
                    if (nameEnd > i + 1) {
                        val lower = cssOrScss.substring(i + 1, nameEnd).lowercase()
                        var look = nameEnd
                        while (look < n && cssOrScss[look].isWhitespace()) look++
                        val nextChar = if (look < n) cssOrScss[look] else '\u0000'
                        if (lower in COMPOUND_DIRECTIVE_NAMES) {
                            if (nextChar == '(' || nextChar == '{') return true
                        } else if (parseToken(lower) != null && nextChar == '{') {
                            return true
                        }
                    }
                }
            }
            i++
        }
        return false
    }

    private fun isIdentChar(ch: Char): Boolean =
        ch.isLetterOrDigit() || ch == '-' || ch == '_'

    private fun scanIdentEnd(source: String, start: Int): Int {
        var i = start
        val n = source.length
        while (i < n && isIdentChar(source[i])) i++
        return i
    }

    private fun extractBalancedParensContent(source: String, startAfterName: Int): String? {
        var cursor = startAfterName
        val n = source.length
        while (cursor < n && source[cursor].isWhitespace()) cursor++
        if (cursor >= n || source[cursor] != '(') return null
        var depth = 0
        var i = cursor
        var closeIdx = -1
        while (i < n) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        closeIdx = i
                        break
                    }
                }
            }
            i++
        }
        if (closeIdx < 0) return null
        if (source.substring(closeIdx + 1).isNotBlank()) return null
        return source.substring(cursor + 1, closeIdx)
    }
}
