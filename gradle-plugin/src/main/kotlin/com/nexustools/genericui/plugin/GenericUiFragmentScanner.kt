package com.nexustools.genericui.plugin

import com.nexustools.genericui.plugin.spi.ExtensionAnnotationScanSpec
import com.nexustools.genericui.plugin.spi.GenericUiDiscoveredSymbol
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
        "src/appleCommon/kotlin",
        "src/appleMain/kotlin",
        "src/jvmCommon/kotlin",
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
        "src/kdeMain/kotlin",
        "src/kde/kotlin",
        "src/swingMain/kotlin",
        "src/swing/kotlin",
        "src/androidMain/kotlin",
        "src/android/kotlin",
        "src/iosCommon/kotlin",
        "src/iosCommonMain/kotlin",
        "src/iosMain/kotlin",
        "src/ios/kotlin",
        "src/ipadosMain/kotlin",
        "src/ipados/kotlin",
        "src/ipadMain/kotlin",
        "src/ipad/kotlin",
        "src/webMain/kotlin",
        "src/web/kotlin",
        "src/jsMain/kotlin",
        "src/wasmJsMain/kotlin",
        "src/main/kotlin"
    )

    private data class ParsedAnnotation(
        val rawName: String,
        val simpleName: String,
        val argsString: String
    )

    private data class AnnotationParseResult(
        val annotation: ParsedAnnotation?,
        val nextIndex: Int
    )

    private data class ClassDeclarationHeader(
        val simpleName: String,
        val afterClassNameIdx: Int
    )

    private data class ParsedAnnotationArgs(
        val named: Map<String, String>,
        val positionalByIndex: Map<Int, String>
    )

    private val PACKAGE_REGEX = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")

    private val ANCHOR_SIMPLE_NAMES = setOf(
        "MainAppFragment",
        "AppFragment",
        "AppApplication"
    )

    private val MAIN_FRAGMENT_POSITIONAL_PARAMS = listOf(
        "targets",
        "label",
        "route",
        "exported",
        "screenOrientation",
        "multipleWindows"
    )

    private val APP_FRAGMENT_POSITIONAL_PARAMS = listOf(
        "targets",
        "label",
        "route",
        "exported",
        "screenOrientation",
        "multipleWindows",
        "dialog"
    )

    private val CLASS_MODIFIER_KEYWORDS = setOf(
        "public",
        "private",
        "protected",
        "internal",
        "open",
        "final",
        "abstract",
        "sealed",
        "data",
        "inner",
        "enum",
        "annotation",
        "value",
        "expect",
        "actual",
        "external",
        "inline",
        "tailrec",
        "operator",
        "infix",
        "suspend",
        "const",
        "lateinit",
        "override",
        "companion"
    )

    /**
     * Returns all existing candidate Kotlin source directories under [projectDir], merging the
     * standard GenericUI roots with any configured [extraDirectories].
     */
    fun collectCandidateSourceDirectories(
        projectDir: File,
        extraDirectories: Iterable<File> = emptyList()
    ): List<File> {
        val dirs = linkedMapOf<String, File>()
        for (relRoot in DEFAULT_SOURCE_ROOTS) {
            val candidate = File(projectDir, relRoot)
            if (candidate.exists() && candidate.isDirectory) {
                val canonical = runCatching { candidate.canonicalFile }.getOrDefault(candidate)
                dirs.putIfAbsent(canonical.path, canonical)
            }
        }
        for (extra in extraDirectories) {
            if (extra.exists() && extra.isDirectory) {
                val canonical = runCatching { extra.canonicalFile }.getOrDefault(extra)
                dirs.putIfAbsent(canonical.path, canonical)
            }
        }
        return dirs.values.toList()
    }

    /**
     * Scans all standard GenericUI Kotlin source directories under [projectDir].
     */
    fun scanProject(projectDir: File): List<GenericUiDiscoveredFragment> =
        scanSourceDirectories(collectCandidateSourceDirectories(projectDir))

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
     * Scans project Kotlin sources for extension-contributed annotations declared in [scanSpecs].
     */
    fun scanProjectSymbols(
        projectDir: File,
        scanSpecs: List<ExtensionAnnotationScanSpec>,
        extraDirectories: Iterable<File> = emptyList()
    ): List<GenericUiDiscoveredSymbol> {
        if (scanSpecs.isEmpty()) return emptyList()
        val candidateDirs = linkedSetOf<File>()
        DEFAULT_SOURCE_ROOTS.map { File(projectDir, it) }.filter { it.isDirectory }.forEach { candidateDirs.add(it) }
        extraDirectories.filter { it.isDirectory }.forEach { candidateDirs.add(it) }
        return scanSourceDirectoriesSymbols(candidateDirs, scanSpecs)
    }

    /**
     * Scans Kotlin files under [directories] for extension-contributed annotations in [scanSpecs].
     */
    fun scanSourceDirectoriesSymbols(
        directories: Iterable<File>,
        scanSpecs: List<ExtensionAnnotationScanSpec>
    ): List<GenericUiDiscoveredSymbol> {
        if (scanSpecs.isEmpty()) return emptyList()
        val ktFiles = linkedSetOf<File>()
        for (dir in directories) {
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("kt", ignoreCase = true) }
                    .forEach { ktFiles.add(it.canonicalFile) }
            }
        }
        val results = LinkedHashMap<String, GenericUiDiscoveredSymbol>()
        for (file in ktFiles) {
            for (sym in parseKotlinSymbols(file.readText(), file.invariantSeparatorsPath, scanSpecs)) {
                val key = "${sym.extensionModulePath}:${sym.qualifiedName}:${sym.annotationName}"
                results.putIfAbsent(key, sym)
            }
        }
        return results.values.toList()
    }

    /**
     * Parses a single Kotlin source string and extracts [GenericUiDiscoveredSymbol] entries matching [scanSpecs].
     */
    fun parseKotlinSymbols(
        sourceText: String,
        sourceFilePath: String = "",
        scanSpecs: List<ExtensionAnnotationScanSpec>
    ): List<GenericUiDiscoveredSymbol> {
        if (scanSpecs.isEmpty()) return emptyList()
        val stripped = stripCommentsPreservingLength(sourceText)
        val packageName = PACKAGE_REGEX.find(stripped)?.groupValues?.get(1)?.trim().orEmpty()
        val discovered = mutableListOf<GenericUiDiscoveredSymbol>()
        val pendingAnnotations = mutableListOf<ParsedAnnotation>()

        val len = stripped.length
        var i = 0
        while (i < len) {
            val c = stripped[i]
            if (c.isWhitespace()) {
                i++
                continue
            }
            if (c == '"' || c == '\'') {
                pendingAnnotations.clear()
                i = skipStringOrCharLiteral(stripped, i)
                continue
            }
            if (c == '@') {
                val prevIsIdent = i > 0 && isKotlinIdentifierPart(stripped[i - 1])
                if (!prevIsIdent) {
                    val parsed = tryParseAnnotation(stripped, i)
                    if (parsed != null) {
                        if (parsed.annotation != null) {
                            pendingAnnotations.add(parsed.annotation)
                        }
                        i = parsed.nextIndex
                        continue
                    }
                }
                pendingAnnotations.clear()
                i++
                continue
            }
            if (c == '{' || c == '(' || c == '[' || c == '}' || c == ')' || c == ']' || c == ';') {
                pendingAnnotations.clear()
                i++
                continue
            }
            if (isKotlinIdentifierStart(c)) {
                val start = i
                var end = i + 1
                while (end < len && isKotlinIdentifierPart(stripped[end])) {
                    end++
                }
                val ident = stripped.substring(start, end)
                val targetKind = when (ident) {
                    "class" -> ExtensionAnnotationScanSpec.SymbolTargetKind.CLASS
                    "object" -> ExtensionAnnotationScanSpec.SymbolTargetKind.OBJECT
                    "fun" -> ExtensionAnnotationScanSpec.SymbolTargetKind.FUNCTION
                    else -> null
                }
                if (targetKind != null && pendingAnnotations.isNotEmpty()) {
                    var lookBack = start - 1
                    while (lookBack >= 0 && stripped[lookBack].isWhitespace()) lookBack--
                    val precededByDoubleColon = lookBack >= 1 && stripped[lookBack] == ':' && stripped[lookBack - 1] == ':'
                    val decl = if (!precededByDoubleColon) tryParseClassDeclaration(stripped, end) else null
                    if (decl != null) {
                        for (spec in scanSpecs) {
                            if (targetKind !in spec.targetKinds) continue
                            val matchedAnn = pendingAnnotations.firstOrNull { ann ->
                                ann.simpleName in spec.simpleAnnotationNames ||
                                    ann.rawName in spec.qualifiedAnnotationNames
                            } ?: continue
                            val qName = if (packageName.isEmpty()) decl.simpleName else "$packageName.${decl.simpleName}"
                            val attrs = extractAllStringAndBoolAttributes(matchedAnn.argsString)
                            val targets = parseTargetsAttribute(matchedAnn.argsString)
                            val hasNoArg = if (targetKind == ExtensionAnnotationScanSpec.SymbolTargetKind.CLASS) {
                                checkClassHasNoArgConstructor(stripped, decl.afterClassNameIdx)
                            } else {
                                true
                            }
                            discovered.add(
                                GenericUiDiscoveredSymbol(
                                    extensionModulePath = spec.extensionModulePath,
                                    annotationName = matchedAnn.simpleName,
                                    qualifiedAnnotationName = matchedAnn.rawName,
                                    packageName = packageName,
                                    symbolName = decl.simpleName,
                                    qualifiedName = qName,
                                    sourceFilePath = sourceFilePath,
                                    hasNoArgConstructor = hasNoArg,
                                    attributes = attrs,
                                    targetScopes = targets
                                )
                            )
                        }
                        pendingAnnotations.clear()
                        i = decl.afterClassNameIdx
                        continue
                    }
                }
                if (ident !in CLASS_MODIFIER_KEYWORDS && ident != "interface") {
                    pendingAnnotations.clear()
                }
                i = end
                continue
            }
            pendingAnnotations.clear()
            i++
        }
        return discovered
    }

    private fun extractAllStringAndBoolAttributes(argsString: String): Map<String, String> {
        if (argsString.isBlank()) return emptyMap()
        val attrs = linkedMapOf<String, String>()
        for (arg in splitTopLevelCommas(argsString)) {
            val eqIdx = findTopLevelEquals(arg)
            if (eqIdx > 0) {
                val k = arg.substring(0, eqIdx).trim()
                val v = arg.substring(eqIdx + 1).trim()
                val unquoted = if (v.startsWith("\"") && v.endsWith("\"") && v.length >= 2) {
                    v.substring(1, v.length - 1)
                } else {
                    v
                }
                if (k.isNotEmpty()) {
                    attrs[k] = unquoted
                }
            }
        }
        return attrs
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
        val pendingAnnotations = mutableListOf<ParsedAnnotation>()

        val len = stripped.length
        var i = 0
        while (i < len) {
            val c = stripped[i]
            if (c.isWhitespace()) {
                i++
                continue
            }

            if (c == '"' || c == '\'') {
                pendingAnnotations.clear()
                i = skipStringOrCharLiteral(stripped, i)
                continue
            }

            if (c == '@') {
                val prevIsIdent = i > 0 && isKotlinIdentifierPart(stripped[i - 1])
                if (!prevIsIdent) {
                    val parsed = tryParseAnnotation(stripped, i)
                    if (parsed != null) {
                        if (parsed.annotation != null) {
                            pendingAnnotations.add(parsed.annotation)
                        }
                        i = parsed.nextIndex
                        continue
                    }
                }
                pendingAnnotations.clear()
                i++
                continue
            }

            if (c == '`') {
                pendingAnnotations.clear()
                val closeTick = stripped.indexOf('`', i + 1)
                i = if (closeTick >= 0) closeTick + 1 else len
                continue
            }

            if (isKotlinIdentifierStart(c)) {
                val start = i
                var end = i + 1
                while (end < len && isKotlinIdentifierPart(stripped[end])) {
                    end++
                }
                val ident = stripped.substring(start, end)
                if (ident == "class") {
                    var lookBack = start - 1
                    while (lookBack >= 0 && stripped[lookBack].isWhitespace()) {
                        lookBack--
                    }
                    val precededByDoubleColon = lookBack >= 1 &&
                        stripped[lookBack] == ':' &&
                        stripped[lookBack - 1] == ':'
                    val classDecl = if (!precededByDoubleColon) {
                        tryParseClassDeclaration(stripped, end)
                    } else {
                        null
                    }
                    if (classDecl != null) {
                        val anchor = resolveAnchorAnnotation(pendingAnnotations)
                        if (anchor != null) {
                            val rawClassName = classDecl.simpleName
                            val qualifiedName = if (packageName.isEmpty()) rawClassName else "$packageName.$rawClassName"
                            val hasNoArg = checkClassHasNoArgConstructor(stripped, classDecl.afterClassNameIdx)
                            val isMain = anchor.simpleName == "MainAppFragment"
                            val isAppClass = anchor.simpleName == "AppApplication"

                            val positionalOrder = when {
                                isMain -> MAIN_FRAGMENT_POSITIONAL_PARAMS
                                isAppClass -> listOf("targets")
                                else -> APP_FRAGMENT_POSITIONAL_PARAMS
                            }
                            val parsedArgs = parseAnnotationArgs(anchor.argsString, positionalOrder)
                            val parsedTargets = parseTargetsAttribute(anchor.argsString)
                            val label = parseStringAttribute(parsedArgs, "label", positionalOrder) ?: ""
                            val route = parseStringAttribute(parsedArgs, "route", positionalOrder) ?: ""
                            val exported = parseBooleanAttribute(parsedArgs, "exported", positionalOrder) ?: isMain
                            val screenOrientation = parseStringAttribute(parsedArgs, "screenOrientation", positionalOrder) ?: ""
                            val multipleWindows = if (isAppClass) {
                                false
                            } else {
                                parseBooleanAttribute(parsedArgs, "multipleWindows", positionalOrder) ?: false
                            }
                            val hasIllegalMainDialogFlag = isMain && (
                                "dialog" in parsedArgs.named ||
                                    parsedArgs.positionalByIndex.containsKey(6)
                                )
                            val dialog = if (isMain || isAppClass) {
                                false
                            } else {
                                parseBooleanAttribute(parsedArgs, "dialog", positionalOrder) ?: false
                            }

                            val allAnnotations = linkedSetOf<String>()
                            for (ann in pendingAnnotations) {
                                allAnnotations.add(ann.simpleName)
                                if ('.' in ann.rawName) {
                                    allAnnotations.add(ann.rawName)
                                }
                            }

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
                                    isApplicationClass = isAppClass,
                                    annotations = allAnnotations,
                                    multipleWindows = multipleWindows,
                                    dialog = dialog,
                                    hasIllegalMainDialogFlag = hasIllegalMainDialogFlag
                                )
                            )
                        }
                        pendingAnnotations.clear()
                        i = classDecl.afterClassNameIdx
                        continue
                    }
                }
                if (ident !in CLASS_MODIFIER_KEYWORDS) {
                    pendingAnnotations.clear()
                }
                i = end
                continue
            }

            pendingAnnotations.clear()
            i++
        }

        return discovered
    }

    private fun resolveAnchorAnnotation(annotations: List<ParsedAnnotation>): ParsedAnnotation? {
        val matching = annotations.filter { ann ->
            ann.simpleName in ANCHOR_SIMPLE_NAMES &&
                (ann.rawName == ann.simpleName ||
                    ann.rawName == "com.nexustools.genericui.component.fragment.${ann.simpleName}")
        }
        return matching.firstOrNull { it.simpleName == "MainAppFragment" }
            ?: matching.firstOrNull { it.simpleName == "AppFragment" }
            ?: matching.firstOrNull { it.simpleName == "AppApplication" }
    }

    private fun tryParseAnnotation(source: String, atIndex: Int): AnnotationParseResult? {
        val len = source.length
        var idx = atIndex + 1
        while (idx < len && source[idx].isWhitespace()) {
            idx++
        }
        if (idx >= len || !isKotlinIdentifierStart(source[idx])) {
            return null
        }

        val firstStart = idx
        idx++
        while (idx < len && isKotlinIdentifierPart(source[idx])) {
            idx++
        }
        val firstSegment = source.substring(firstStart, idx)

        var probe = idx
        while (probe < len && source[probe].isWhitespace()) {
            probe++
        }
        val isUseSiteTarget = probe < len &&
            source[probe] == ':' &&
            (probe + 1 >= len || source[probe + 1] != ':')

        val rawName: String
        if (isUseSiteTarget) {
            idx = probe + 1
            while (idx < len && source[idx].isWhitespace()) {
                idx++
            }
            if (idx < len && source[idx] == '[') {
                val closeBracket = findMatchingDelimiter(source, idx, '[', ']')
                val nextIdx = if (closeBracket > idx) closeBracket + 1 else idx + 1
                return AnnotationParseResult(annotation = null, nextIndex = nextIdx)
            }
            if (idx >= len || !isKotlinIdentifierStart(source[idx])) {
                return null
            }
            val segStart = idx
            idx++
            while (idx < len && isKotlinIdentifierPart(source[idx])) {
                idx++
            }
            val qualified = readQualifiedIdentifierSuffix(source, source.substring(segStart, idx), idx)
            rawName = qualified.first
            idx = qualified.second
        } else {
            val qualified = readQualifiedIdentifierSuffix(source, firstSegment, idx)
            rawName = qualified.first
            idx = qualified.second
        }

        var afterName = idx
        while (afterName < len && source[afterName].isWhitespace()) {
            afterName++
        }
        if (afterName < len && source[afterName] == '<') {
            val closeAngle = findMatchingDelimiter(source, afterName, '<', '>')
            if (closeAngle > afterName) {
                idx = closeAngle + 1
                afterName = idx
                while (afterName < len && source[afterName].isWhitespace()) {
                    afterName++
                }
            }
        }

        var argsString = ""
        if (afterName < len && source[afterName] == '(') {
            val closeParen = findMatchingDelimiter(source, afterName, '(', ')')
            if (closeParen > afterName) {
                argsString = source.substring(afterName + 1, closeParen)
                idx = closeParen + 1
            }
        }

        val annotation = if (isUseSiteTarget) {
            null
        } else {
            ParsedAnnotation(
                rawName = rawName,
                simpleName = rawName.substringAfterLast('.'),
                argsString = argsString
            )
        }
        return AnnotationParseResult(annotation = annotation, nextIndex = idx)
    }

    private fun readQualifiedIdentifierSuffix(
        source: String,
        firstSegment: String,
        startIdx: Int
    ): Pair<String, Int> {
        val sb = StringBuilder(firstSegment)
        var idx = startIdx
        val len = source.length
        while (true) {
            var probe = idx
            while (probe < len && source[probe].isWhitespace()) {
                probe++
            }
            if (probe >= len || source[probe] != '.') break
            var segStart = probe + 1
            while (segStart < len && source[segStart].isWhitespace()) {
                segStart++
            }
            if (segStart >= len || !isKotlinIdentifierStart(source[segStart])) break
            var segEnd = segStart + 1
            while (segEnd < len && isKotlinIdentifierPart(source[segEnd])) {
                segEnd++
            }
            sb.append('.').append(source, segStart, segEnd)
            idx = segEnd
        }
        return sb.toString() to idx
    }

    private fun tryParseClassDeclaration(source: String, afterClassKeyword: Int): ClassDeclarationHeader? {
        val len = source.length
        if (afterClassKeyword >= len || !source[afterClassKeyword].isWhitespace()) {
            return null
        }
        var idx = afterClassKeyword
        while (idx < len && source[idx].isWhitespace()) {
            idx++
        }
        if (idx >= len) return null
        return if (source[idx] == '`') {
            val closeTick = source.indexOf('`', idx + 1)
            if (closeTick <= idx + 1) return null
            val name = source.substring(idx + 1, closeTick)
            ClassDeclarationHeader(simpleName = name, afterClassNameIdx = closeTick + 1)
        } else if (isKotlinIdentifierStart(source[idx])) {
            val nameStart = idx
            idx++
            while (idx < len && isKotlinIdentifierPart(source[idx])) {
                idx++
            }
            val name = source.substring(nameStart, idx)
            ClassDeclarationHeader(simpleName = name, afterClassNameIdx = idx)
        } else {
            null
        }
    }

    private fun isKotlinIdentifierStart(ch: Char): Boolean = ch == '_' || ch.isLetter()

    private fun isKotlinIdentifierPart(ch: Char): Boolean = ch == '_' || ch.isLetterOrDigit()

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

    private fun parseAnnotationArgs(
        argsString: String,
        positionalOrder: List<String>
    ): ParsedAnnotationArgs {
        if (argsString.isBlank()) return ParsedAnnotationArgs(emptyMap(), emptyMap())
        val named = linkedMapOf<String, String>()
        val positionalByIndex = linkedMapOf<Int, String>()
        var inPositionalOrder = true
        var argIndex = 0
        for (rawArg in splitTopLevelCommas(argsString)) {
            val trimmed = rawArg.trim()
            if (trimmed.isEmpty()) continue
            val eqIndex = findTopLevelEquals(trimmed)
            if (eqIndex > 0) {
                val key = trimmed.substring(0, eqIndex).trim()
                val value = trimmed.substring(eqIndex + 1).trim()
                if (key.isNotEmpty()) {
                    named[key] = value
                }
                if (positionalOrder.getOrNull(argIndex) != key) {
                    inPositionalOrder = false
                }
            } else if (inPositionalOrder) {
                positionalByIndex[argIndex] = trimmed
            }
            argIndex++
        }
        return ParsedAnnotationArgs(named = named, positionalByIndex = positionalByIndex)
    }

    private fun resolveAttributeExpr(
        parsedArgs: ParsedAnnotationArgs,
        attributeName: String,
        positionalOrder: List<String>
    ): String? {
        parsedArgs.named[attributeName]?.let { return it }
        val idx = positionalOrder.indexOf(attributeName)
        if (idx >= 0) {
            return parsedArgs.positionalByIndex[idx]
        }
        return null
    }

    private fun parseStringAttribute(
        parsedArgs: ParsedAnnotationArgs,
        attributeName: String,
        positionalOrder: List<String>
    ): String? {
        val valueExpr = resolveAttributeExpr(parsedArgs, attributeName, positionalOrder) ?: return null
        if (valueExpr.startsWith("\"\"\"") && valueExpr.endsWith("\"\"\"") && valueExpr.length >= 6) {
            return valueExpr.substring(3, valueExpr.length - 3)
        }
        if (valueExpr.startsWith("\"") && valueExpr.endsWith("\"") && valueExpr.length >= 2) {
            return valueExpr.substring(1, valueExpr.length - 1)
        }
        return null
    }

    private fun parseBooleanAttribute(
        parsedArgs: ParsedAnnotationArgs,
        attributeName: String,
        positionalOrder: List<String>
    ): Boolean? {
        val valueExpr = resolveAttributeExpr(parsedArgs, attributeName, positionalOrder) ?: return null
        return when (valueExpr) {
            "true" -> true
            "false" -> false
            else -> null
        }
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
        while (i < source.length) {
            val c = source[i]
            if (c == '"' || c == '\'') {
                i = skipStringOrCharLiteral(source, i)
                continue
            }
            if (c == openChar) {
                depth++
            } else if (c == closeChar) {
                if (closeChar == '>' && i > 0 && source[i - 1] == '-') {
                    i++
                    continue
                }
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return -1
    }

    private fun skipStringOrCharLiteral(source: String, startIndex: Int): Int {
        val len = source.length
        if (startIndex >= len) return len
        if (source.startsWith("\"\"\"", startIndex)) {
            val closeIdx = source.indexOf("\"\"\"", startIndex + 3)
            return if (closeIdx >= 0) closeIdx + 3 else len
        }
        val quote = source[startIndex]
        var i = startIndex + 1
        while (i < len) {
            val c = source[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == quote) {
                return i + 1
            }
            if (quote == '\'' && c == '\n') {
                return i + 1
            }
            i++
        }
        return len
    }

    private fun splitTopLevelCommas(input: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var angleDepth = 0

        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '"' || c == '\'') {
                val endIdx = skipStringOrCharLiteral(input, i)
                current.append(input, i, endIdx)
                i = endIdx
                continue
            }
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
            i++
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
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '"' || c == '\'') {
                i = skipStringOrCharLiteral(input, i)
                continue
            }
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
            i++
        }
        return -1
    }

    private fun stripCommentsPreservingLength(source: String): String {
        val sb = StringBuilder(source.length)
        var i = 0
        val len = source.length
        var inString = false
        var inChar = false
        var inTripleQuote = false

        while (i < len) {
            if (!inString && !inChar && i + 2 < len && source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"') {
                inTripleQuote = !inTripleQuote
                sb.append("\"\"\"")
                i += 3
                continue
            }
            if ((inString || inChar) && source[i] == '\\' && i + 1 < len) {
                sb.append(source[i]).append(source[i + 1])
                i += 2
                continue
            }
            if (!inTripleQuote && !inChar && source[i] == '"') {
                inString = !inString
                sb.append(source[i])
                i++
                continue
            }
            if (!inTripleQuote && !inString && source[i] == '\'') {
                inChar = !inChar
                sb.append(source[i])
                i++
                continue
            }
            if (inChar && source[i] == '\n') {
                inChar = false
                sb.append('\n')
                i++
                continue
            }
            if (!inString && !inChar && !inTripleQuote && i + 1 < len && source[i] == '/' && source[i + 1] == '/') {
                while (i < len && source[i] != '\n') {
                    sb.append(' ')
                    i++
                }
                continue
            }
            if (!inString && !inChar && !inTripleQuote && i + 1 < len && source[i] == '/' && source[i + 1] == '*') {
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
