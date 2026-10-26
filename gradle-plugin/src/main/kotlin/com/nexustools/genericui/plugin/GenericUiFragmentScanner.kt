package com.nexustools.genericui.plugin

import java.io.File

/**
 * Scans project Kotlin source files (`.kt`) for classes annotated with `@MainAppFragment`,
 * `@AppFragment`, or `@AppApplication`, extracting their target [GenericUiVariantScope]s,
 * Android manifest attributes (`label`, `route`, `exported`, `screenOrientation`), and
 * constructor signatures (`hasNoArgConstructor`).
 */
object GenericUiFragmentScanner {

    private val DEFAULT_SOURCE_ROOTS = listOf(
        "src/commonMain/kotlin",
        "src/common/kotlin",
        "src/systemCommon/kotlin",
        "src/systemMain/kotlin",
        "src/desktopCommon/kotlin",
        "src/desktopMain/kotlin",
        "src/nativeDesktopCommon/kotlin",
        "src/nativeDesktopMain/kotlin",
        "src/desktopNativeCommon/kotlin",
        "src/desktopNativeMain/kotlin",
        "src/linuxCommon/kotlin",
        "src/linuxMain/kotlin",
        "src/mobileCommon/kotlin",
        "src/mobileMain/kotlin",
        "src/windowsMain/kotlin",
        "src/windows/kotlin",
        "src/macMain/kotlin",
        "src/macosMain/kotlin",
        "src/mac/kotlin",
        "src/gtkMain/kotlin",
        "src/gtk/kotlin",
        "src/qtMain/kotlin",
        "src/qt/kotlin",
        "src/swingMain/kotlin",
        "src/swing/kotlin",
        "src/androidMain/kotlin",
        "src/android/kotlin",
        "src/iosMain/kotlin",
        "src/ios/kotlin",
        "src/webMain/kotlin",
        "src/web/kotlin",
        "src/jsMain/kotlin",
        "src/wasmJsMain/kotlin",
        "src/main/kotlin"
    )

    private val PACKAGE_REGEX = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
    private val ANNOTATION_START_REGEX = Regex(
        """@(?:com\.nexustools\.genericui\.component\.fragment\.)?(MainAppFragment|AppFragment|AppApplication)\b"""
    )
    private val CLASS_DECL_REGEX = Regex("""\bclass\s+(`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)""")

    /**
     * Scans all standard GenericUI Kotlin source directories under [projectDir].
     */
    fun scanProject(projectDir: File): List<GenericUiDiscoveredFragment> {
        val candidateDirs = DEFAULT_SOURCE_ROOTS
            .map { File(projectDir, it) }
            .filter { it.exists() && it.isDirectory }
        return scanSourceDirectories(candidateDirs)
    }

    /**
     * Scans all `.kt` files recursively inside [directories].
     */
    fun scanSourceDirectories(directories: Iterable<File>): List<GenericUiDiscoveredFragment> {
        val ktFiles = linkedSetOf<File>()
        for (dir in directories) {
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("kt", ignoreCase = true) }
                    .forEach { ktFiles.add(it.canonicalFile) }
            }
        }
        return scanSourceFiles(ktFiles)
    }

    /**
     * Scans the provided Kotlin [files] and returns all discovered `@MainAppFragment`, `@AppFragment`,
     * and `@AppApplication` descriptors (deduplicated by `qualifiedName + isMainFragment + isApplicationClass`).
     */
    fun scanSourceFiles(files: Iterable<File>): List<GenericUiDiscoveredFragment> {
        val results = LinkedHashMap<String, GenericUiDiscoveredFragment>()
        for (file in files) {
            if (!file.exists() || !file.isFile) continue
            val parsed = parseKotlinSource(file.readText(), file.invariantSeparatorsPath)
            for (item in parsed) {
                val key = "${item.qualifiedName}:${item.isMainFragment}:${item.isApplicationClass}"
                results.putIfAbsent(key, item)
            }
        }
        return results.values.toList()
    }

    /**
     * Parses a single Kotlin source string and extracts all classes annotated with
     * `@MainAppFragment`, `@AppFragment`, or `@AppApplication`.
     */
    fun parseKotlinSource(
        sourceText: String,
        sourceFilePath: String = ""
    ): List<GenericUiDiscoveredFragment> {
        val stripped = stripCommentsPreservingLength(sourceText)
        val packageName = PACKAGE_REGEX.find(stripped)?.groupValues?.get(1)?.trim().orEmpty()
        val discovered = mutableListOf<GenericUiDiscoveredFragment>()

        var searchIndex = 0
        while (searchIndex < stripped.length) {
            val match = ANNOTATION_START_REGEX.find(stripped, searchIndex) ?: break
            val annotationName = match.groupValues[1]
            var afterAnnotation = match.range.last + 1

            while (afterAnnotation < stripped.length && stripped[afterAnnotation].isWhitespace()) {
                afterAnnotation++
            }

            var argsString = ""
            if (afterAnnotation < stripped.length && stripped[afterAnnotation] == '(') {
                val closeParen = findMatchingDelimiter(stripped, afterAnnotation, '(', ')')
                if (closeParen > afterAnnotation) {
                    argsString = stripped.substring(afterAnnotation + 1, closeParen)
                    afterAnnotation = closeParen + 1
                }
            }

            val classMatch = CLASS_DECL_REGEX.find(stripped, afterAnnotation)
            if (classMatch == null) {
                searchIndex = afterAnnotation
                continue
            }

            // Ensure no other class/object/interface body opened between the annotation and `class <Name>`
            val between = stripped.substring(afterAnnotation, classMatch.range.first)
            if (between.contains('{') || between.contains(';')) {
                searchIndex = afterAnnotation
                continue
            }

            val rawClassName = classMatch.groupValues[1].removeSurrounding("`")
            val qualifiedName = if (packageName.isEmpty()) rawClassName else "$packageName.$rawClassName"
            val afterClassNameIdx = classMatch.range.last + 1

            val hasNoArg = checkClassHasNoArgConstructor(stripped, afterClassNameIdx)
            val isMain = annotationName == "MainAppFragment"
            val isAppClass = annotationName == "AppApplication"

            val parsedTargets = parseTargetsAttribute(argsString)
            val label = parseStringAttribute(argsString, "label") ?: ""
            val route = parseStringAttribute(argsString, "route") ?: ""
            val exported = parseBooleanAttribute(argsString, "exported") ?: isMain
            val screenOrientation = parseStringAttribute(argsString, "screenOrientation") ?: ""

            discovered.add(
                GenericUiDiscoveredFragment(
                    packageName = packageName,
                    simpleName = rawClassName,
                    qualifiedName = qualifiedName,
                    isMainFragment = isMain,
                    targets = parsedTargets,
                    label = label,
                    route = route,
                    exported = exported,
                    screenOrientation = screenOrientation,
                    hasNoArgConstructor = hasNoArg,
                    sourceFilePath = sourceFilePath,
                    isApplicationClass = isAppClass
                )
            )

            searchIndex = afterClassNameIdx
        }

        return discovered
    }

    private fun parseTargetsAttribute(argsString: String): List<GenericUiVariantScope> {
        if (argsString.isBlank()) return listOf(GenericUiVariantScope.COMMON)
        val topLevelArgs = splitTopLevelCommas(argsString)
        var rawTargetsExpr: String? = null
        for ((index, arg) in topLevelArgs.withIndex()) {
            val eqIndex = findTopLevelEquals(arg)
            if (eqIndex >= 0) {
                val name = arg.substring(0, eqIndex).trim()
                if (name == "targets") {
                    rawTargetsExpr = arg.substring(eqIndex + 1).trim()
                    break
                }
            } else if (index == 0) {
                val trimmed = arg.trim()
                if (trimmed.startsWith("[") ||
                    trimmed.startsWith("arrayOf") ||
                    trimmed.contains("AppTarget") ||
                    GenericUiVariantScope.fromNameOrNull(trimmed) != null
                ) {
                    rawTargetsExpr = trimmed
                }
            }
        }

        if (rawTargetsExpr.isNullOrBlank()) {
            return listOf(GenericUiVariantScope.COMMON)
        }

        val unwrapped = rawTargetsExpr.trim()
            .let { expr ->
                when {
                    expr.startsWith("[") && expr.endsWith("]") ->
                        expr.substring(1, expr.length - 1)
                    expr.startsWith("arrayOf(") && expr.endsWith(")") ->
                        expr.substring("arrayOf(".length, expr.length - 1)
                    else -> expr
                }
            }

        val resolved = splitTopLevelCommas(unwrapped)
            .mapNotNull { token -> GenericUiVariantScope.fromNameOrNull(token) }
            .distinct()

        return resolved.ifEmpty { listOf(GenericUiVariantScope.COMMON) }
    }

    private fun parseStringAttribute(argsString: String, attributeName: String): String? {
        if (argsString.isBlank()) return null
        for (arg in splitTopLevelCommas(argsString)) {
            val eqIndex = findTopLevelEquals(arg)
            if (eqIndex >= 0) {
                val key = arg.substring(0, eqIndex).trim()
                if (key == attributeName) {
                    val valueExpr = arg.substring(eqIndex + 1).trim()
                    if (valueExpr.startsWith("\"") && valueExpr.endsWith("\"") && valueExpr.length >= 2) {
                        return valueExpr.substring(1, valueExpr.length - 1)
                    }
                }
            }
        }
        return null
    }

    private fun parseBooleanAttribute(argsString: String, attributeName: String): Boolean? {
        if (argsString.isBlank()) return null
        for (arg in splitTopLevelCommas(argsString)) {
            val eqIndex = findTopLevelEquals(arg)
            if (eqIndex >= 0) {
                val key = arg.substring(0, eqIndex).trim()
                if (key == attributeName) {
                    val valueExpr = arg.substring(eqIndex + 1).trim()
                    if (valueExpr == "true") return true
                    if (valueExpr == "false") return false
                }
            }
        }
        return null
    }

    private fun checkClassHasNoArgConstructor(strippedSource: String, afterClassNameIdx: Int): Boolean {
        var idx = afterClassNameIdx
        val len = strippedSource.length

        while (idx < len && strippedSource[idx].isWhitespace()) idx++

        // Skip optional type parameters `<T, U>`
        if (idx < len && strippedSource[idx] == '<') {
            val closeAngle = findMatchingDelimiter(strippedSource, idx, '<', '>')
            if (closeAngle > idx) {
                idx = closeAngle + 1
            }
        }

        while (idx < len && strippedSource[idx].isWhitespace()) idx++

        // Skip optional `public` / `internal` / `private` / `protected` / `@Inject` / `constructor` keyword
        val constructorPrefix = Regex("""^(?:(?:public|internal|private|protected|@[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?)\s+)*constructor\b""")
            .find(strippedSource.substring(idx))
        if (constructorPrefix != null) {
            idx += constructorPrefix.value.length
            while (idx < len && strippedSource[idx].isWhitespace()) idx++
        }

        // Check if primary constructor parameter list `(...)` is present
        if (idx < len && strippedSource[idx] == '(') {
            val closeParen = findMatchingDelimiter(strippedSource, idx, '(', ')')
            if (closeParen > idx) {
                val paramsText = strippedSource.substring(idx + 1, closeParen).trim()
                if (paramsText.isEmpty()) return true
                val params = splitTopLevelCommas(paramsText).map { it.trim() }.filter { it.isNotEmpty() }
                val allHaveDefaultOrVararg = params.all { param ->
                    findTopLevelEquals(param) >= 0 || Regex("""\bvararg\b""").containsMatchIn(param)
                }
                if (allHaveDefaultOrVararg) return true

                // Primary constructor requires arguments; check if class body defines a 0-arg secondary `constructor()`
                val bodyStart = findClassBodyStart(strippedSource, closeParen + 1)
                if (bodyStart >= 0) {
                    val bodyEnd = findMatchingDelimiter(strippedSource, bodyStart, '{', '}')
                    if (bodyEnd > bodyStart) {
                        val bodyContent = strippedSource.substring(bodyStart + 1, bodyEnd)
                        if ( Regex("""\bconstructor\s*\(\s*\)""").containsMatchIn(bodyContent)) {
                            return true
                        }
                    }
                }
                return false
            }
        }

        return true
    }

    private fun findClassBodyStart(source: String, startIndex: Int): Int {
        var i = startIndex
        var parenDepth = 0
        var angleDepth = 0
        while (i < source.length) {
            when (source[i]) {
                '(' -> parenDepth++
                ')' -> if (parenDepth > 0) parenDepth--
                '<' -> angleDepth++
                '>' -> if (angleDepth > 0) angleDepth--
                '{' -> if (parenDepth == 0 && angleDepth == 0) return i
                '\n' -> {
                    // continue scanning header across lines
                }
            }
            i++
        }
        return -1
    }

    private fun findMatchingDelimiter(source: String, openIndex: Int, openChar: Char, closeChar: Char): Int {
        var depth = 0
        var i = openIndex
        var inString = false
        while (i < source.length) {
            val c = source[i]
            if (c == '"' && (i == 0 || source[i - 1] != '\\')) {
                inString = !inString
            } else if (!inString) {
                if (c == openChar) {
                    depth++
                } else if (c == closeChar) {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun splitTopLevelCommas(input: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var angleDepth = 0
        var inString = false

        for (i in input.indices) {
            val c = input[i]
            if (c == '"' && (i == 0 || input[i - 1] != '\\')) {
                inString = !inString
                current.append(c)
            } else if (!inString) {
                when (c) {
                    '(' -> {
                        parenDepth++
                        current.append(c)
                    }
                    ')' -> {
                        if (parenDepth > 0) parenDepth--
                        current.append(c)
                    }
                    '[' -> {
                        bracketDepth++
                        current.append(c)
                    }
                    ']' -> {
                        if (bracketDepth > 0) bracketDepth--
                        current.append(c)
                    }
                    '{' -> {
                        braceDepth++
                        current.append(c)
                    }
                    '}' -> {
                        if (braceDepth > 0) braceDepth--
                        current.append(c)
                    }
                    '<' -> {
                        angleDepth++
                        current.append(c)
                    }
                    '>' -> {
                        if ((i == 0 || input[i - 1] != '-') && angleDepth > 0) angleDepth--
                        current.append(c)
                    }
                    ',' -> {
                        if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && angleDepth == 0) {
                            parts.add(current.toString())
                            current.setLength(0)
                        } else {
                            current.append(c)
                        }
                    }
                    else -> current.append(c)
                }
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) {
            parts.add(current.toString())
        }
        return parts
    }

    private fun findTopLevelEquals(input: String): Int {
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var angleDepth = 0
        var inString = false
        for (i in input.indices) {
            val c = input[i]
            if (c == '"' && (i == 0 || input[i - 1] != '\\')) {
                inString = !inString
            } else if (!inString) {
                when (c) {
                    '(' -> parenDepth++
                    ')' -> if (parenDepth > 0) parenDepth--
                    '[' -> bracketDepth++
                    ']' -> if (bracketDepth > 0) bracketDepth--
                    '{' -> braceDepth++
                    '}' -> if (braceDepth > 0) braceDepth--
                    '<' -> angleDepth++
                    '>' -> if ((i == 0 || input[i - 1] != '-') && angleDepth > 0) angleDepth--
                    '=' -> {
                        val prev = if (i > 0) input[i - 1] else ' '
                        val next = if (i + 1 < input.length) input[i + 1] else ' '
                        if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && angleDepth == 0 &&
                            prev != '=' && prev != '!' && prev != '<' && prev != '>' && next != '='
                        ) {
                            return i
                        }
                    }
                }
            }
        }
        return -1
    }

    private fun stripCommentsPreservingLength(source: String): String {
        val sb = StringBuilder(source.length)
        var i = 0
        val len = source.length
        var inString = false
        var inTripleQuote = false

        while (i < len) {
            if (!inString && i + 2 < len && source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"') {
                inTripleQuote = !inTripleQuote
                sb.append("\"\"\"")
                i += 3
                continue
            }
            if (!inTripleQuote && source[i] == '"' && (i == 0 || source[i - 1] != '\\')) {
                inString = !inString
                sb.append(source[i])
                i++
                continue
            }
            if (!inString && !inTripleQuote && i + 1 < len && source[i] == '/' && source[i + 1] == '/') {
                while (i < len && source[i] != '\n') {
                    sb.append(' ')
                    i++
                }
                continue
            }
            if (!inString && !inTripleQuote && i + 1 < len && source[i] == '/' && source[i + 1] == '*') {
                sb.append("  ")
                i += 2
                var depth = 1
                while (i < len && depth > 0) {
                    if (i + 1 < len && source[i] == '/' && source[i + 1] == '*') {
                        depth++
                        sb.append("  ")
                        i += 2
                    } else if (i + 1 < len && source[i] == '*' && source[i + 1] == '/') {
                        depth--
                        sb.append("  ")
                        i += 2
                    } else {
                        sb.append(if (source[i] == '\n') '\n' else ' ')
                        i++
                    }
                }
                continue
            }
            sb.append(source[i])
            i++
        }
        return sb.toString()
    }
}
