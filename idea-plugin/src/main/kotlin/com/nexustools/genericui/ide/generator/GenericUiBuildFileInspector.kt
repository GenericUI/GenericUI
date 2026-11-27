package com.nexustools.genericui.ide.generator

import com.nexustools.genericui.ide.model.GenericUiExtensionModule
import com.nexustools.genericui.ide.model.GenericUiProjectConfig
import com.nexustools.genericui.ide.model.GenericUiTargetBackend

/**
 * Parses an existing `build.gradle.kts` file to inspect which [GenericUiTargetBackend]s
 * and [GenericUiExtensionModule]s are currently enabled, and updates `build.gradle.kts`
 * in-place when the user changes target or extension checkboxes in IntelliJ IDEA.
 */
class GenericUiBuildFileInspector(
    private val scriptGenerator: GenericUiGradleScriptGenerator = GenericUiGradleScriptGenerator()
) {

    /**
     * Parses the `backends { ... }` section inside `genericui { ... }` of [buildGradleKtsContent]
     * and returns the set of enabled [GenericUiTargetBackend]s.
     */
    fun parseSelectedBackends(buildGradleKtsContent: String): Set<GenericUiTargetBackend> {
        val genericUiBody = extractGenericUiBody(buildGradleKtsContent)
        val backendsBlock = extractDirectChildBlockContent(genericUiBody, "backends") ?: return emptySet()
        val result = linkedSetOf<GenericUiTargetBackend>()

        if (Regex("""\ballExceptSwing\s*\(""").containsMatchIn(backendsBlock)) {
            result.addAll(GenericUiTargetBackend.allExceptSwingSet())
        }
        if (
            Regex("""\ballDesktopExceptSwing\s*\(""").containsMatchIn(backendsBlock) ||
            Regex("""\ballNativeDesktop\s*\(""").containsMatchIn(backendsBlock)
        ) {
            result.addAll(GenericUiTargetBackend.desktopExceptSwingSet())
        }
        if (Regex("""\ballDesktop\s*\(""").containsMatchIn(backendsBlock)) {
            result.addAll(GenericUiTargetBackend.desktopAllSet())
        }
        if (Regex("""\ballMobile\s*\(""").containsMatchIn(backendsBlock)) {
            result.addAll(GenericUiTargetBackend.mobileSet())
        }
        if (Regex("""\ball\s*\(\s*\)""").containsMatchIn(backendsBlock)) {
            result.addAll(GenericUiTargetBackend.allSet())
        }

        for (backend in GenericUiTargetBackend.entries) {
            val callPattern = Regex("""\b${backend.dslMethodName}\s*(\(\s*(true|false)?\s*\)|\{)""")
            for (match in callPattern.findAll(backendsBlock)) {
                val arg = match.groupValues.getOrNull(2)?.trim()
                if (arg == "false") {
                    result.remove(backend)
                } else {
                    result.add(backend)
                }
            }
        }

        val macAliasPattern = Regex("""\bmac\s*(\(\s*(true|false)?\s*\)|\{)""")
        for (match in macAliasPattern.findAll(backendsBlock)) {
            val arg = match.groupValues.getOrNull(2)?.trim()
            if (arg == "false") {
                result.remove(GenericUiTargetBackend.MACOS)
            } else {
                result.add(GenericUiTargetBackend.MACOS)
            }
        }

        val ipadAliasPattern = Regex("""\bipad\s*(\(\s*(true|false)?\s*\)|\{)""")
        for (match in ipadAliasPattern.findAll(backendsBlock)) {
            val arg = match.groupValues.getOrNull(2)?.trim()
            if (arg == "false") {
                result.remove(GenericUiTargetBackend.IPADOS)
            } else {
                result.add(GenericUiTargetBackend.IPADOS)
            }
        }

        return GenericUiTargetBackend.entries.filter { it in result }.toCollection(LinkedHashSet())
    }

    /**
     * Parses the `extensions { ... }` section inside `genericui { ... }` of [buildGradleKtsContent]
     * and returns the transitively resolved set of enabled [GenericUiExtensionModule]s.
     *
     * Accurately handles `ipc()`, `svg()`, `pip()`, `mediaControls()`, `gamepad()`, `serviceWorker { ... }`,
     * `webView { ... }` configuration blocks, boolean flags (`ext(true)` / `ext(false)` / `ext(enabled = false)`),
     * comments, and backend constraints (e.g. [GenericUiExtensionModule.IPC] requires Desktop or Android when backends are declared).
     */
    fun parseSelectedExtensions(buildGradleKtsContent: String): Set<GenericUiExtensionModule> {
        val backends = parseSelectedBackends(buildGradleKtsContent)
        val genericUiBody = extractGenericUiBody(buildGradleKtsContent)
        val extensionBlocks = extractAllDirectChildBlockContents(genericUiBody, "extensions")
        if (extensionBlocks.isEmpty()) return emptySet()

        val direct = linkedSetOf<GenericUiExtensionModule>()
        var enableAllRequested = false

        for (extensionsBlock in extensionBlocks) {
            var i = 0
            val n = extensionsBlock.length
            var inLineComment = false
            var inBlockComment = false
            var inString = false

            while (i < n) {
                val c = extensionsBlock[i]
                val next = if (i + 1 < n) extensionsBlock[i + 1] else '\u0000'
                when {
                    inLineComment -> {
                        if (c == '\n') inLineComment = false
                        i++
                    }
                    inBlockComment -> {
                        if (c == '*' && next == '/') {
                            inBlockComment = false
                            i += 2
                        } else {
                            i++
                        }
                    }
                    inString -> {
                        if (c == '\\') {
                            i += 2
                        } else {
                            if (c == '"') inString = false
                            i++
                        }
                    }
                    c == '/' && next == '/' -> {
                        inLineComment = true
                        i += 2
                    }
                    c == '/' && next == '*' -> {
                        inBlockComment = true
                        i += 2
                    }
                    c == '"' -> {
                        inString = true
                        i++
                    }
                    c.isLetter() || c == '_' -> {
                        val prevChar = if (i > 0) extensionsBlock[i - 1] else ' '
                        if (prevChar.isLetterOrDigit() || prevChar == '_') {
                            while (i < n && (extensionsBlock[i].isLetterOrDigit() || extensionsBlock[i] == '_')) i++
                            continue
                        }
                        val identStart = i
                        while (i < n && (extensionsBlock[i].isLetterOrDigit() || extensionsBlock[i] == '_')) i++
                        val ident = extensionsBlock.substring(identStart, i)

                        val afterIdent = skipWhitespaceAndComments(extensionsBlock, i)
                        if (afterIdent >= n) {
                            i = afterIdent
                            continue
                        }

                        when (extensionsBlock[afterIdent]) {
                            '(' -> {
                                val closeParen = findMatchingCloseDelimiter(extensionsBlock, afterIdent, '(', ')')
                                if (closeParen == null) {
                                    i = afterIdent + 1
                                    continue
                                }
                                val args = extensionsBlock.substring(afterIdent + 1, closeParen)
                                val afterParen = skipWhitespaceAndComments(extensionsBlock, closeParen + 1)
                                var blockDisabled = false
                                val nextIndex = if (afterParen < n && extensionsBlock[afterParen] == '{') {
                                    val closeBrace = findMatchingCloseBrace(extensionsBlock, afterParen)
                                    if (closeBrace != null) {
                                        val body = extensionsBlock.substring(afterParen + 1, closeBrace)
                                        blockDisabled = hasTopLevelEnabledFalse(body)
                                        closeBrace + 1
                                    } else {
                                        closeParen + 1
                                    }
                                } else {
                                    closeParen + 1
                                }

                                if (ident.equals("enableAll", ignoreCase = true)) {
                                    if (!isFalseCallArg(args) && !blockDisabled) {
                                        enableAllRequested = true
                                        direct.addAll(GenericUiExtensionModule.entries)
                                    }
                                } else if (ident.equals("custom", ignoreCase = true)) {
                                    handleCustomExtensionCall(args, direct)
                                } else {
                                    findExtensionByCallName(ident)?.let { ext ->
                                        if (isFalseCallArg(args) || blockDisabled) {
                                            direct.remove(ext)
                                        } else {
                                            direct.add(ext)
                                        }
                                    }
                                }
                                i = nextIndex
                            }
                            '{' -> {
                                val closeBrace = findMatchingCloseBrace(extensionsBlock, afterIdent)
                                if (closeBrace == null) {
                                    i = afterIdent + 1
                                    continue
                                }
                                val body = extensionsBlock.substring(afterIdent + 1, closeBrace)
                                findExtensionByCallName(ident)?.let { ext ->
                                    if (hasTopLevelEnabledFalse(body)) {
                                        direct.remove(ext)
                                    } else {
                                        direct.add(ext)
                                    }
                                }
                                i = closeBrace + 1
                            }
                            '=' -> {
                                val (rhs, endIndex) = readAssignmentValue(extensionsBlock, afterIdent + 1)
                                findExtensionByPropertyName(ident)?.let { ext ->
                                    if (rhs.equals("false", ignoreCase = true)) {
                                        direct.remove(ext)
                                    } else if (rhs.equals("true", ignoreCase = true)) {
                                        direct.add(ext)
                                    }
                                }
                                i = endIndex
                            }
                            else -> {
                                i = afterIdent
                            }
                        }
                    }
                    c == '{' -> {
                        val closeBrace = findMatchingCloseBrace(extensionsBlock, i)
                        i = (closeBrace ?: i) + 1
                    }
                    c == '(' -> {
                        val closeParen = findMatchingCloseDelimiter(extensionsBlock, i, '(', ')')
                        i = (closeParen ?: i) + 1
                    }
                    else -> i++
                }
            }
        }

        if (enableAllRequested && backends.isEmpty()) {
            return GenericUiExtensionModule.entries.toCollection(LinkedHashSet())
        }
        return GenericUiExtensionModule.resolveTransitive(direct, backends)
    }

    /**
     * Inspects a complete `build.gradle.kts` file (and optional `projectName`/`projectRootPath`)
     * and reconstructs a [GenericUiProjectConfig].
     */
    fun inspectBuildGradleKts(
        buildGradleKtsContent: String,
        projectName: String = "GenericUiApp",
        projectRootPath: String = "."
    ): GenericUiProjectConfig {
        val genericUiBody = extractGenericUiBody(buildGradleKtsContent)
        val appMetadataBlock = extractDirectChildBlockContent(genericUiBody, "app")
        val appId = appMetadataBlock?.let {
            Regex("""\bid\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1)
        }
        val appName = appMetadataBlock?.let {
            Regex("""\bname\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1)
        }

        val groupMatch = Regex("""group\s*=\s*"([^"]+)"""").find(buildGradleKtsContent)
        val packageName = appId ?: groupMatch?.groupValues?.get(1) ?: "com.example.app"

        val appBlock = extractDirectChildBlockContent(genericUiBody, "application")
        val resolvedProjectName = appName ?: appBlock?.let {
            Regex("""baseName\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1)
        } ?: projectName

        val backends = parseSelectedBackends(buildGradleKtsContent)
        val extensions = parseSelectedExtensions(buildGradleKtsContent)

        val backendsBlock = extractDirectChildBlockContent(genericUiBody, "backends")
        val swingBlock = backendsBlock?.let { extractDirectChildBlockContent(it, "swing") }
        val swingUseLwjglGL = swingBlock?.let {
            !Regex("""useLwjglGL\s*=\s*false""").containsMatchIn(it)
        } ?: true
        val swingUseVulkan = swingBlock?.let {
            !Regex("""useVulkan\s*=\s*false""").containsMatchIn(it)
        } ?: true

        val themeBlock = extractDirectChildBlockContent(genericUiBody, "theme")
        val themeMode = themeBlock?.let {
            Regex("""\bmode\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1)
        } ?: "auto"
        val themeStyleSheet = themeBlock?.let {
            Regex("""\bstyleSheet\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1)
        }

        val stylesBlock = extractDirectChildBlockContent(genericUiBody, "styles")
        val compileSass = stylesBlock?.let {
            !Regex("""compileSass\s*=\s*false""").containsMatchIn(it)
        } ?: true
        val stylesGlobalStyleSheet = stylesBlock?.let {
            Regex("""\bglobalStyleSheet\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1)
        }
        val globalStyleSheet = stylesGlobalStyleSheet ?: themeStyleSheet ?: "styles/main.scss"

        val testingBlock = extractDirectChildBlockContent(genericUiBody, "testing")
        val enableTesting = testingBlock?.let {
            !Regex("""\benabled\s*=\s*false""").containsMatchIn(it)
        } ?: true

        val windowBlock = extractDirectChildBlockContent(genericUiBody, "window")
        val windowAlwaysOnTop = parseWindowAlwaysOnTop(windowBlock)
        val (aspectWidth, aspectHeight) = parseWindowAspectRatio(windowBlock)

        val desktopBlock = extractDirectChildBlockContent(genericUiBody, "desktop")
        val appleIcon = findStringProperty(appBlock, "appleIcon")
            ?: findStringProperty(desktopBlock, "appleIcon")
            ?: findStringProperty(genericUiBody, "appleIcon")
            ?: ""

        val macosBlock = extractDirectChildBlockContent(genericUiBody, "macos")
            ?: extractDirectChildBlockContent(genericUiBody, "mac")
        val macIcon = findStringProperty(macosBlock, "icon")
            ?: findStringProperty(appBlock, "macIcon")
            ?: findStringProperty(appBlock, "macosIcon")
            ?: findStringProperty(desktopBlock, "macIcon")
            ?: findStringProperty(desktopBlock, "macosIcon")
            ?: ""

        val iosBlock = extractDirectChildBlockContent(genericUiBody, "ios")
        val iosIcon = findStringProperty(iosBlock, "icon")
            ?: findStringProperty(appBlock, "iosIcon")
            ?: findStringProperty(desktopBlock, "iosIcon")
            ?: ""

        val ipadosBlock = extractDirectChildBlockContent(genericUiBody, "ipados")
            ?: extractDirectChildBlockContent(genericUiBody, "ipad")
        val ipadosIcon = findStringProperty(ipadosBlock, "icon")
            ?: findStringProperty(appBlock, "ipadosIcon")
            ?: findStringProperty(appBlock, "ipadIcon")
            ?: findStringProperty(desktopBlock, "ipadosIcon")
            ?: findStringProperty(desktopBlock, "ipadIcon")
            ?: ""

        return GenericUiProjectConfig(
            projectName = resolvedProjectName,
            packageName = packageName,
            projectRootPath = projectRootPath,
            selectedBackends = backends,
            selectedExtensions = extensions,
            compileSass = compileSass,
            swingUseLwjglGL = swingUseLwjglGL,
            swingUseVulkan = swingUseVulkan,
            themeMode = themeMode,
            globalStyleSheet = globalStyleSheet,
            enableTesting = enableTesting,
            windowAlwaysOnTop = windowAlwaysOnTop,
            windowAspectRatioWidth = aspectWidth,
            windowAspectRatioHeight = aspectHeight,
            appleIcon = appleIcon,
            macIcon = macIcon,
            iosIcon = iosIcon,
            ipadosIcon = ipadosIcon
        )
    }

    /**
     * Updates an existing `build.gradle.kts` content in-place by replacing the `genericui { ... }`
     * DSL block to match [updatedConfig].
     */
    fun updateBuildGradleKts(
        existingBuildGradleKts: String,
        updatedConfig: GenericUiProjectConfig
    ): String {
        val newGenericUiBlock = scriptGenerator.generateGenericUiDslBlock(updatedConfig)
        return replaceTopLevelBlock(existingBuildGradleKts, "genericui", newGenericUiBlock)
    }

    private fun parseWindowAlwaysOnTop(windowBlock: String?): Boolean {
        if (windowBlock == null) return false
        val assignMatch = Regex("""\balwaysOnTop\s*=\s*(true|false)""").find(windowBlock)
        if (assignMatch != null) {
            return assignMatch.groupValues[1] == "true"
        }
        val callMatch = Regex("""\balwaysOnTop\s*\(\s*(true|false)?\s*\)""").find(windowBlock)
        if (callMatch != null) {
            return callMatch.groupValues[1].trim() != "false"
        }
        return Regex("""(?m)^\s*alwaysOnTop\s*(?:;|$)""").containsMatchIn(windowBlock)
    }

    private fun parseWindowAspectRatio(windowBlock: String?): Pair<Int?, Int?> {
        if (windowBlock == null) return null to null
        val callMatch = Regex("""\baspectRatio\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)""").find(windowBlock)
        if (callMatch != null) {
            return callMatch.groupValues[1].toIntOrNull() to callMatch.groupValues[2].toIntOrNull()
        }
        val ratioExprMatch = Regex("""\baspectRatio\s*=\s*(\d+)(?:\.0+)?\s*/\s*(\d+)(?:\.0+)?""").find(windowBlock)
        if (ratioExprMatch != null) {
            return ratioExprMatch.groupValues[1].toIntOrNull() to ratioExprMatch.groupValues[2].toIntOrNull()
        }
        return null to null
    }

    private fun findStringProperty(block: String?, propertyName: String): String? {
        if (block == null) return null
        val assignMatch = Regex("""\b$propertyName\s*=\s*"([^"]*)"""").find(block)
        if (assignMatch != null) return assignMatch.groupValues[1]
        val callMatch = Regex("""\b$propertyName\s*\(\s*"([^"]*)"\s*\)""").find(block)
        return callMatch?.groupValues?.get(1)
    }

    private fun extractGenericUiBody(source: String): String =
        extractDirectChildBlockContent(source, "genericui") ?: source

    private fun extractDirectChildBlockContent(source: String, blockName: String): String? =
        extractAllDirectChildBlockContents(source, blockName).firstOrNull()

    private fun extractAllDirectChildBlockContents(source: String, blockName: String): List<String> {
        val results = mutableListOf<String>()
        var i = 0
        var depth = 0
        var inString = false
        var inLineComment = false
        var inBlockComment = false
        val n = source.length

        while (i < n) {
            val c = source[i]
            val next = if (i + 1 < n) source[i + 1] else '\u0000'

            when {
                inLineComment -> {
                    if (c == '\n') inLineComment = false
                }
                inBlockComment -> {
                    if (c == '*' && next == '/') {
                        inBlockComment = false
                        i++
                    }
                }
                inString -> {
                    if (c == '\\') {
                        i++
                    } else if (c == '"') {
                        inString = false
                    }
                }
                c == '/' && next == '/' -> {
                    inLineComment = true
                    i++
                }
                c == '/' && next == '*' -> {
                    inBlockComment = true
                    i++
                }
                c == '"' -> {
                    inString = true
                }
                c == '{' -> {
                    depth++
                }
                c == '}' -> {
                    if (depth > 0) depth--
                }
                depth == 0 && source.startsWith(blockName, i) -> {
                    val prevChar = if (i > 0) source[i - 1] else ' '
                    if (!prevChar.isLetterOrDigit() && prevChar != '_') {
                        var j = i + blockName.length
                        while (j < n && source[j].isWhitespace()) j++
                        if (j < n && source[j] == '{') {
                            val closeIndex = findMatchingCloseBrace(source, j) ?: return results
                            results.add(source.substring(j + 1, closeIndex))
                            i = closeIndex
                        }
                    }
                }
            }
            i++
        }
        return results
    }

    private fun findMatchingCloseBrace(source: String, openBraceIndex: Int): Int? =
        findMatchingCloseDelimiter(source, openBraceIndex, '{', '}')

    private fun findMatchingCloseDelimiter(
        source: String,
        openIndex: Int,
        openChar: Char,
        closeChar: Char
    ): Int? {
        var depth = 0
        var inString = false
        var inLineComment = false
        var inBlockComment = false
        var i = openIndex
        val n = source.length
        while (i < n) {
            val c = source[i]
            val next = if (i + 1 < n) source[i + 1] else '\u0000'
            when {
                inLineComment -> if (c == '\n') inLineComment = false
                inBlockComment -> if (c == '*' && next == '/') {
                    inBlockComment = false
                    i++
                }
                inString -> if (c == '\\') {
                    i++
                } else if (c == '"') {
                    inString = false
                }
                c == '/' && next == '/' -> {
                    inLineComment = true
                    i++
                }
                c == '/' && next == '*' -> {
                    inBlockComment = true
                    i++
                }
                c == '"' -> inString = true
                c == openChar -> depth++
                c == closeChar -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    private fun skipWhitespaceAndComments(source: String, startIndex: Int): Int {
        var i = startIndex
        val n = source.length
        while (i < n) {
            val c = source[i]
            val next = if (i + 1 < n) source[i + 1] else '\u0000'
            when {
                c.isWhitespace() -> i++
                c == '/' && next == '/' -> {
                    i += 2
                    while (i < n && source[i] != '\n') i++
                }
                c == '/' && next == '*' -> {
                    i += 2
                    while (i + 1 < n && !(source[i] == '*' && source[i + 1] == '/')) i++
                    i = if (i + 1 < n) i + 2 else n
                }
                else -> return i
            }
        }
        return i
    }

    private fun stripCommentsAndStrings(source: String, stripNestedBraces: Boolean): String = buildString {
        var i = 0
        val n = source.length
        var depth = 0
        var inString = false
        var inLineComment = false
        var inBlockComment = false
        while (i < n) {
            val c = source[i]
            val next = if (i + 1 < n) source[i + 1] else '\u0000'
            when {
                inLineComment -> {
                    if (c == '\n') {
                        inLineComment = false
                        append('\n')
                    }
                    i++
                }
                inBlockComment -> {
                    if (c == '*' && next == '/') {
                        inBlockComment = false
                        i += 2
                    } else {
                        i++
                    }
                }
                inString -> {
                    if (c == '\\') {
                        i += 2
                    } else {
                        if (c == '"') inString = false
                        i++
                    }
                }
                c == '/' && next == '/' -> {
                    inLineComment = true
                    i += 2
                }
                c == '/' && next == '*' -> {
                    inBlockComment = true
                    i += 2
                }
                c == '"' -> {
                    inString = true
                    i++
                }
                stripNestedBraces && c == '{' -> {
                    depth++
                    append(' ')
                    i++
                }
                stripNestedBraces && c == '}' -> {
                    if (depth > 0) depth--
                    append(' ')
                    i++
                }
                !stripNestedBraces || depth == 0 -> {
                    append(c)
                    i++
                }
                else -> i++
            }
        }
    }

    private fun isFalseCallArg(args: String): Boolean {
        val cleaned = stripCommentsAndStrings(args, stripNestedBraces = false).trim()
        return cleaned.equals("false", ignoreCase = true) ||
            Regex("""^enabled\s*=\s*false$""", RegexOption.IGNORE_CASE).matches(cleaned)
    }

    private fun hasTopLevelEnabledFalse(blockBody: String): Boolean {
        val topLevel = stripCommentsAndStrings(blockBody, stripNestedBraces = true)
        return Regex("""\benabled\s*=\s*false\b""", RegexOption.IGNORE_CASE).containsMatchIn(topLevel)
    }

    private fun readAssignmentValue(source: String, startIndex: Int): Pair<String, Int> {
        var i = skipWhitespaceAndComments(source, startIndex)
        val start = i
        val n = source.length
        while (i < n && (source[i].isLetterOrDigit() || source[i] == '_')) i++
        return source.substring(start, i) to i
    }

    private fun findExtensionByCallName(name: String): GenericUiExtensionModule? =
        GenericUiExtensionModule.entries.firstOrNull {
            it.dslMethodName.equals(name, ignoreCase = true) ||
                it.id.equals(name, ignoreCase = true)
        }

    private fun findExtensionByPropertyName(name: String): GenericUiExtensionModule? {
        if (name.equals("useCodeEditor", ignoreCase = true)) {
            return GenericUiExtensionModule.CODE_EDITOR
        }
        if (name.endsWith("Enabled")) {
            return findExtensionByCallName(name.removeSuffix("Enabled"))
        }
        return null
    }

    private fun handleCustomExtensionCall(args: String, target: MutableSet<GenericUiExtensionModule>) {
        val path = Regex(""""([^"]+)"""").find(args)?.groupValues?.get(1)?.trim() ?: return
        val matched = GenericUiExtensionModule.fromDslMethod(path) ?: return
        target.add(matched)
        val afterPath = stripCommentsAndStrings(args, stripNestedBraces = false)
        if (Regex("""\btrue\b""", RegexOption.IGNORE_CASE).containsMatchIn(afterPath)) {
            target.add(GenericUiExtensionModule.DSL)
        }
    }

    private fun replaceTopLevelBlock(source: String, blockName: String, replacementBlock: String): String {
        val headerRegex = Regex("""(?m)^[ \t]*$blockName\s*\{""")
        val match = headerRegex.find(source) ?: return "$source\n\n$replacementBlock\n"
        val blockStart = match.range.first
        val openBraceIndex = source.indexOf('{', blockStart)
        if (openBraceIndex < 0) return source
        val closeIndex = findMatchingCloseBrace(source, openBraceIndex) ?: return source
        return source.substring(0, blockStart) +
            replacementBlock +
            source.substring(closeIndex + 1)
    }
}
