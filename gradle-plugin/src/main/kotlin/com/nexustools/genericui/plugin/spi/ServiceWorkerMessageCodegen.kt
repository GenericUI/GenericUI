package com.nexustools.genericui.plugin.spi

import java.io.File

/**
 * Compile-time parser and reflection-free `PreferenceElement` codec generator for
 * `@ServiceWorkerMessage` data classes.
 *
 * Strictly emits 1 public top-level `object` per generated `.kt` file:
 * - `<Msg>ServiceWorkerCodec.kt` implementing `ServiceWorkerMessageCodec<<Msg>>`
 * - `GeneratedServiceWorkerCodecRegistrar.kt` registering every generated codec into `ServiceWorkerCodecRegistry`
 */
internal object ServiceWorkerMessageCodegen {

    private data class ParsedParam(
        val name: String,
        val rawType: String,
        val hasDefaultValue: Boolean
    )

    private data class ParsedEnum(
        val packageName: String,
        val simpleName: String,
        val qualifiedName: String,
        val entries: List<String>
    )

    private data class ParsedMessageClass(
        val packageName: String,
        val simpleName: String,
        val qualifiedName: String,
        val params: List<ParsedParam>
    )

    private data class ParsedSourceFile(
        val packageName: String,
        val explicitImports: Map<String, String>,
        val wildcardImports: List<String>,
        val enums: List<ParsedEnum>,
        val messages: List<ParsedMessageClass>
    )

    fun generateCodecSources(
        sourceDirectories: List<File>,
        outputDirectory: File,
        registrarPackageName: String
    ): List<File> {
        val ktFiles = sourceDirectories
            .filter { it.exists() && it.isDirectory }
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("kt", ignoreCase = true) }
                    .toList()
            }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sortedBy { it.invariantSeparatorsPath }

        val parsedFiles = ktFiles.map { file ->
            parseKotlinFile(file.readText(Charsets.UTF_8))
        }

        val enumRegistry = LinkedHashMap<String, ParsedEnum>()
        val messageRegistry = LinkedHashMap<String, ParsedMessageClass>()
        val messageFileByQName = LinkedHashMap<String, ParsedSourceFile>()

        for (pf in parsedFiles) {
            for (en in pf.enums) {
                enumRegistry.putIfAbsent(en.qualifiedName, en)
            }
            for (msg in pf.messages) {
                messageRegistry.putIfAbsent(msg.qualifiedName, msg)
                messageFileByQName.putIfAbsent(msg.qualifiedName, pf)
            }
        }

        val desiredFiles = LinkedHashMap<String, String>()
        val codecEntries = mutableListOf<String>()

        for (msg in messageRegistry.values) {
            val sourceFile = messageFileByQName.getValue(msg.qualifiedName)
            val codecSimpleName = "${msg.simpleName}ServiceWorkerCodec"
            val pkgPath = if (msg.packageName.isEmpty()) "" else msg.packageName.replace('.', '/') + "/"
            val relPath = "$pkgPath$codecSimpleName.kt"
            val codecFqn = if (msg.packageName.isEmpty()) codecSimpleName else "${msg.packageName}.$codecSimpleName"
            desiredFiles[relPath] = generateCodecFile(
                msg = msg,
                parsedFile = sourceFile,
                enums = enumRegistry,
                messages = messageRegistry
            )
            codecEntries.add(codecFqn)
        }

        val cleanRegistrarPkg = registrarPackageName.trim().ifEmpty {
            ServiceWorkerExtensionContributor.DEFAULT_REGISTRAR_PACKAGE
        }
        val registrarRelPath = cleanRegistrarPkg.replace('.', '/') + "/GeneratedServiceWorkerCodecRegistrar.kt"
        desiredFiles[registrarRelPath] = generateRegistrarFile(cleanRegistrarPkg, codecEntries.sorted())

        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        } else {
            val existingKtFiles = outputDirectory.walkTopDown()
                .filter { it.isFile && it.extension.equals("kt", ignoreCase = true) }
                .toList()
            for (existing in existingKtFiles) {
                val rel = existing.relativeTo(outputDirectory).invariantSeparatorsPath
                if (rel !in desiredFiles) {
                    existing.delete()
                }
            }
        }

        val written = mutableListOf<File>()
        for ((relPath, content) in desiredFiles) {
            val outFile = File(outputDirectory, relPath)
            outFile.parentFile?.mkdirs()
            val normalizedNew = content.replace("\r\n", "\n")
            val existing = if (outFile.isFile) outFile.readText(Charsets.UTF_8).replace("\r\n", "\n") else null
            if (existing != normalizedNew) {
                outFile.writeText(normalizedNew, Charsets.UTF_8)
            }
            written.add(outFile)
        }
        return written
    }

    private fun generateRegistrarFile(
        registrarPackage: String,
        codecFqns: List<String>
    ): String = buildString {
        appendLine("package $registrarPackage")
        appendLine()
        appendLine("import com.nexustools.genericui.ext.serviceworker.ServiceWorkerCodecRegistry")
        appendLine()
        appendLine("object GeneratedServiceWorkerCodecRegistrar {")
        appendLine("    fun registerAll() {")
        for (fqn in codecFqns) {
            appendLine("        ServiceWorkerCodecRegistry.register($fqn)")
        }
        appendLine("    }")
        appendLine("}")
    }

    private fun generateCodecFile(
        msg: ParsedMessageClass,
        parsedFile: ParsedSourceFile,
        enums: Map<String, ParsedEnum>,
        messages: Map<String, ParsedMessageClass>
    ): String = buildString {
        val codecSimpleName = "${msg.simpleName}ServiceWorkerCodec"
        val allHaveDefaults = msg.params.isNotEmpty() && msg.params.all { it.hasDefaultValue }

        if (msg.packageName.isNotEmpty()) {
            appendLine("package ${msg.packageName}")
            appendLine()
        }
        appendLine("import com.nexustools.genericui.component.system.PreferenceElement")
        appendLine("import com.nexustools.genericui.ext.serviceworker.ServiceWorkerMessageCodec")
        appendLine("import kotlin.reflect.KClass")
        appendLine()
        appendLine("object $codecSimpleName : ServiceWorkerMessageCodec<${msg.simpleName}> {")
        appendLine("    override val typeId: String = ${quoteString(msg.qualifiedName)}")
        appendLine("    override val messageClass: KClass<${msg.simpleName}> = ${msg.simpleName}::class")
        appendLine()
        appendLine("    override fun encode(value: ${msg.simpleName}): PreferenceElement {")
        appendLine("        val fields = LinkedHashMap<String, PreferenceElement>(${msg.params.size})")
        for (param in msg.params) {
            val encodeExpr = buildEncodeExpr(
                rawType = param.rawType,
                valueExpr = "value.${param.name}",
                parsedFile = parsedFile,
                enums = enums,
                messages = messages,
                depth = 0
            )
            appendLine("        fields[${quoteString(param.name)}] = $encodeExpr")
        }
        appendLine("        return PreferenceElement.ofObject(fields)")
        appendLine("    }")
        appendLine()
        appendLine("    override fun decode(element: PreferenceElement): ${msg.simpleName} {")
        if (allHaveDefaults) {
            appendLine("        val defaults = ${msg.simpleName}()")
        }
        appendLine("        val fields = element.mapEntries")
        if (msg.params.isEmpty()) {
            appendLine("        return ${msg.simpleName}()")
        } else {
            appendLine("        return ${msg.simpleName}(")
            msg.params.forEachIndexed { idx, param ->
                val comma = if (idx < msg.params.lastIndex) "," else ""
                val keyLit = quoteString(param.name)
                val decodeExpr = buildDecodeExpr(
                    rawType = param.rawType,
                    elemExpr = "fields[$keyLit]",
                    defaultFallbackExpr = if (allHaveDefaults) "defaults.${param.name}" else null,
                    parsedFile = parsedFile,
                    enums = enums,
                    messages = messages,
                    depth = 0
                )
                appendLine("            ${param.name} = $decodeExpr$comma")
            }
            appendLine("        )")
        }
        appendLine("    }")
        appendLine("}")
    }

    private fun buildEncodeExpr(
        rawType: String,
        valueExpr: String,
        parsedFile: ParsedSourceFile,
        enums: Map<String, ParsedEnum>,
        messages: Map<String, ParsedMessageClass>,
        depth: Int
    ): String {
        val trimmed = rawType.trim()
        val isNullable = trimmed.endsWith("?")
        val base = trimmed.removeSuffix("?").trim()
        val varName = "v$depth"
        val nonNull = buildNonNullEncodeExpr(base, if (isNullable) varName else valueExpr, parsedFile, enums, messages, depth)
        return if (isNullable) {
            "($valueExpr)?.let { $varName -> $nonNull } ?: PreferenceElement.Null"
        } else {
            nonNull
        }
    }

    private fun buildNonNullEncodeExpr(
        baseType: String,
        valueExpr: String,
        parsedFile: ParsedSourceFile,
        enums: Map<String, ParsedEnum>,
        messages: Map<String, ParsedMessageClass>,
        depth: Int
    ): String {
        return when (baseType) {
            "String", "kotlin.String" -> "PreferenceElement.ofString($valueExpr)"
            "Int", "kotlin.Int" -> "PreferenceElement.ofInt($valueExpr)"
            "Long", "kotlin.Long" -> "PreferenceElement.ofLong($valueExpr)"
            "Float", "kotlin.Float" -> "PreferenceElement.ofFloat($valueExpr)"
            "Double", "kotlin.Double" -> "PreferenceElement.ofDouble($valueExpr)"
            "Boolean", "kotlin.Boolean" -> "PreferenceElement.ofBoolean($valueExpr)"
            "PreferenceElement", "com.nexustools.genericui.component.system.PreferenceElement" -> valueExpr
            else -> {
                val typeHead = baseType.substringBefore('<').trim()
                val genericArgs = extractGenericTypeArguments(baseType)
                if (typeHead in setOf("List", "MutableList", "kotlin.collections.List", "kotlin.collections.MutableList", "Set", "MutableSet", "kotlin.collections.Set", "kotlin.collections.MutableSet") &&
                    genericArgs.size == 1
                ) {
                    val itemVar = "item$depth"
                    val itemEncode = buildEncodeExpr(genericArgs[0], itemVar, parsedFile, enums, messages, depth + 1)
                    "PreferenceElement.ofArray($valueExpr.map { $itemVar -> $itemEncode })"
                } else if (typeHead in setOf("Map", "MutableMap", "LinkedHashMap", "kotlin.collections.Map", "kotlin.collections.MutableMap") &&
                    genericArgs.size == 2
                ) {
                    val kVar = "k$depth"
                    val vVar = "mv$depth"
                    val valEncode = buildEncodeExpr(genericArgs[1], vVar, parsedFile, enums, messages, depth + 1)
                    "PreferenceElement.ofMap($valueExpr.entries.associate { ($kVar, $vVar) -> $kVar to $valEncode })"
                } else {
                    val nestedMsg = resolveMessageClass(baseType, parsedFile, messages)
                    if (nestedMsg != null) {
                        val codecFqn = if (nestedMsg.packageName.isEmpty()) {
                            "${nestedMsg.simpleName}ServiceWorkerCodec"
                        } else {
                            "${nestedMsg.packageName}.${nestedMsg.simpleName}ServiceWorkerCodec"
                        }
                        "$codecFqn.encode($valueExpr)"
                    } else {
                        val enumDecl = resolveEnumDecl(baseType, parsedFile, enums)
                        if (enumDecl != null) {
                            "PreferenceElement.ofString($valueExpr.name)"
                        } else {
                            throw IllegalArgumentException(
                                "Unsupported property type '$baseType' in @ServiceWorkerMessage data class. " +
                                    "Supported types: String, Int, Long, Float, Double, Boolean, enums, List<...>, Map<String, ...>, nested @ServiceWorkerMessage classes, and nullable variants."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun buildDecodeExpr(
        rawType: String,
        elemExpr: String,
        defaultFallbackExpr: String?,
        parsedFile: ParsedSourceFile,
        enums: Map<String, ParsedEnum>,
        messages: Map<String, ParsedMessageClass>,
        depth: Int
    ): String {
        val trimmed = rawType.trim()
        val isNullable = trimmed.endsWith("?")
        val base = trimmed.removeSuffix("?").trim()
        if (isNullable) {
            val elVar = "el$depth"
            val nonNullDecode = buildNonNullDecodeExpr(base, elVar, null, parsedFile, enums, messages, depth)
            return if (defaultFallbackExpr != null) {
                "$elemExpr.let { raw$depth -> when { raw$depth == null -> $defaultFallbackExpr; raw$depth.isNull -> null; else -> raw$depth.let { $elVar -> $nonNullDecode } } }"
            } else {
                "($elemExpr)?.takeIf { !it.isNull }?.let { $elVar -> $nonNullDecode }"
            }
        }
        return if (defaultFallbackExpr != null) {
            val rawVar = "raw$depth"
            val nonNullDecode = buildNonNullDecodeExpr(base, rawVar, defaultFallbackExpr, parsedFile, enums, messages, depth)
            "($elemExpr)?.takeIf { !it.isNull }?.let { $rawVar -> $nonNullDecode } ?: $defaultFallbackExpr"
        } else {
            buildNonNullDecodeExpr(base, elemExpr, null, parsedFile, enums, messages, depth)
        }
    }

    private fun buildNonNullDecodeExpr(
        baseType: String,
        elemExpr: String,
        defaultFallbackExpr: String?,
        parsedFile: ParsedSourceFile,
        enums: Map<String, ParsedEnum>,
        messages: Map<String, ParsedMessageClass>,
        depth: Int
    ): String {
        return when (baseType) {
            "String", "kotlin.String" -> "($elemExpr)?.asString(${defaultFallbackExpr ?: "\"\""}) ?: ${defaultFallbackExpr ?: "\"\""}"
            "Int", "kotlin.Int" -> "($elemExpr)?.asInt(${defaultFallbackExpr ?: "0"}) ?: ${defaultFallbackExpr ?: "0"}"
            "Long", "kotlin.Long" -> "($elemExpr)?.asLong(${defaultFallbackExpr ?: "0L"}) ?: ${defaultFallbackExpr ?: "0L"}"
            "Float", "kotlin.Float" -> "($elemExpr)?.asFloat(${defaultFallbackExpr ?: "0f"}) ?: ${defaultFallbackExpr ?: "0f"}"
            "Double", "kotlin.Double" -> "($elemExpr)?.asDouble(${defaultFallbackExpr ?: "0.0"}) ?: ${defaultFallbackExpr ?: "0.0"}"
            "Boolean", "kotlin.Boolean" -> "($elemExpr)?.asBoolean(${defaultFallbackExpr ?: "false"}) ?: ${defaultFallbackExpr ?: "false"}"
            "PreferenceElement", "com.nexustools.genericui.component.system.PreferenceElement" -> "($elemExpr) ?: PreferenceElement.Null"
            else -> {
                val typeHead = baseType.substringBefore('<').trim()
                val genericArgs = extractGenericTypeArguments(baseType)
                if (typeHead in setOf("List", "MutableList", "kotlin.collections.List", "kotlin.collections.MutableList", "Set", "MutableSet", "kotlin.collections.Set", "kotlin.collections.MutableSet") &&
                    genericArgs.size == 1
                ) {
                    val itemVar = "it$depth"
                    val itemDecode = buildDecodeExpr(genericArgs[0], itemVar, null, parsedFile, enums, messages, depth + 1)
                    val mapCall = if (typeHead.endsWith("MutableList")) ".toMutableList()" else if (typeHead.contains("Set")) ".toSet()" else ""
                    "($elemExpr)?.takeIf { it.isArray }?.arrayElements?.map { $itemVar -> $itemDecode }$mapCall ?: ${defaultFallbackExpr ?: if (typeHead.endsWith("MutableList")) "mutableListOf()" else if (typeHead.contains("Set")) "emptySet()" else "emptyList()"}"
                } else if (typeHead in setOf("Map", "MutableMap", "LinkedHashMap", "kotlin.collections.Map", "kotlin.collections.MutableMap") &&
                    genericArgs.size == 2
                ) {
                    val kVar = "mk$depth"
                    val vVar = "mv$depth"
                    val valDecode = buildDecodeExpr(genericArgs[1], vVar, null, parsedFile, enums, messages, depth + 1)
                    val suffix = if (typeHead.contains("MutableMap") || typeHead == "LinkedHashMap") ".toMutableMap()" else ""
                    "($elemExpr)?.takeIf { it.isMap || it.isObject }?.mapEntries?.entries?.associate { ($kVar, $vVar) -> $kVar to $valDecode }$suffix ?: ${defaultFallbackExpr ?: if (suffix.isNotEmpty()) "mutableMapOf()" else "emptyMap()"}"
                } else {
                    val nestedMsg = resolveMessageClass(baseType, parsedFile, messages)
                    if (nestedMsg != null) {
                        val codecFqn = if (nestedMsg.packageName.isEmpty()) {
                            "${nestedMsg.simpleName}ServiceWorkerCodec"
                        } else {
                            "${nestedMsg.packageName}.${nestedMsg.simpleName}ServiceWorkerCodec"
                        }
                        "$codecFqn.decode(($elemExpr) ?: PreferenceElement.Null)"
                    } else {
                        val enumDecl = resolveEnumDecl(baseType, parsedFile, enums)
                        if (enumDecl != null) {
                            val fallback = defaultFallbackExpr ?: "${enumDecl.qualifiedName}.entries.first()"
                            "($elemExpr)?.asString()?.let { s$depth -> ${enumDecl.qualifiedName}.entries.firstOrNull { it.name == s$depth } } ?: $fallback"
                        } else {
                            throw IllegalArgumentException(
                                "Unsupported property type '$baseType' in @ServiceWorkerMessage data class."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun resolveMessageClass(
        rawType: String,
        parsedFile: ParsedSourceFile,
        messages: Map<String, ParsedMessageClass>
    ): ParsedMessageClass? {
        messages[rawType]?.let { return it }
        parsedFile.explicitImports[rawType]?.let { q -> messages[q]?.let { return it } }
        if (parsedFile.packageName.isNotEmpty()) {
            messages["${parsedFile.packageName}.$rawType"]?.let { return it }
        }
        for (wc in parsedFile.wildcardImports) {
            messages["$wc.$rawType"]?.let { return it }
        }
        return messages.values.firstOrNull { it.simpleName == rawType }
    }

    private fun resolveEnumDecl(
        rawType: String,
        parsedFile: ParsedSourceFile,
        enums: Map<String, ParsedEnum>
    ): ParsedEnum? {
        enums[rawType]?.let { return it }
        parsedFile.explicitImports[rawType]?.let { q -> enums[q]?.let { return it } }
        if (parsedFile.packageName.isNotEmpty()) {
            enums["${parsedFile.packageName}.$rawType"]?.let { return it }
        }
        for (wc in parsedFile.wildcardImports) {
            enums["$wc.$rawType"]?.let { return it }
        }
        return enums.values.firstOrNull { it.simpleName == rawType }
    }

    private fun extractGenericTypeArguments(baseType: String): List<String> {
        val open = baseType.indexOf('<')
        val close = baseType.lastIndexOf('>')
        if (open < 0 || close <= open) return emptyList()
        return splitTopLevelByChar(baseType.substring(open + 1, close), ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseKotlinFile(source: String): ParsedSourceFile {
        val code = stripCommentsPreservingStrings(source)
        val pkgName = Regex("""(?m)^\s*package\s+([A-Za-z0-9_.]+)""").find(code)?.groupValues?.get(1)?.trim().orEmpty()
        val explicitImports = LinkedHashMap<String, String>()
        val wildcardImports = mutableListOf<String>()
        for (m in Regex("""(?m)^\s*import\s+([A-Za-z0-9_.*]+)(?:\s+as\s+[A-Za-z_][A-Za-z0-9_]*)?""").findAll(code)) {
            val imp = m.groupValues[1].trim()
            if (imp.endsWith(".*")) {
                wildcardImports.add(imp.removeSuffix(".*"))
            } else if ('.' in imp) {
                explicitImports[imp.substringAfterLast('.')] = imp
            }
        }
        return ParsedSourceFile(
            packageName = pkgName,
            explicitImports = explicitImports,
            wildcardImports = wildcardImports,
            enums = parseEnums(code, pkgName),
            messages = parseMessageClasses(code, pkgName)
        )
    }

    private fun parseEnums(code: String, pkgName: String): List<ParsedEnum> {
        val out = mutableListOf<ParsedEnum>()
        val regex = Regex("""\benum\s+class\s+([A-Za-z_][A-Za-z0-9_]*)\b""")
        for (m in regex.findAll(code)) {
            val simple = m.groupValues[1]
            val braceIdx = findNextCharOutsideParens(code, m.range.last + 1, '{') ?: continue
            val closeIdx = findMatchingDelimiter(code, braceIdx, '{', '}') ?: continue
            val body = code.substring(braceIdx + 1, closeIdx)
            val headerPart = splitTopLevelByChar(body, ';').firstOrNull().orEmpty()
            val entries = splitTopLevelByChar(headerPart, ',').mapNotNull { entry ->
                val stripped = entry.trim()
                if (stripped.isEmpty()) return@mapNotNull null
                Regex("""(?:@[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?\s*)*([A-Za-z_][A-Za-z0-9_]*)""")
                    .find(stripped)?.groupValues?.get(1)
            }
            val qName = if (pkgName.isEmpty()) simple else "$pkgName.$simple"
            out.add(ParsedEnum(pkgName, simple, qName, entries))
        }
        return out
    }

    private fun parseMessageClasses(code: String, pkgName: String): List<ParsedMessageClass> {
        val out = mutableListOf<ParsedMessageClass>()
        var idx = 0
        val n = code.length
        while (idx < n) {
            if (isStringLiteralStart(code, idx)) {
                idx = skipStringLiteral(code, idx)
                continue
            }
            if (code[idx] == '@') {
                val anns = mutableListOf<String>()
                var cursor = idx
                while (cursor < n && code[cursor] == '@') {
                    val parsed = readAnnotationName(code, cursor) ?: break
                    anns.add(parsed.first)
                    cursor = skipWhitespace(code, parsed.second)
                }
                val isSwMessage = anns.any {
                    it == "ServiceWorkerMessage" ||
                        it == "com.nexustools.genericui.ext.serviceworker.ServiceWorkerMessage"
                }
                if (isSwMessage) {
                    val classDecl = parseClassHeaderAt(code, cursor)
                    if (classDecl != null) {
                        val (simpleName, rawParams, endIdx) = classDecl
                        val params = parseConstructorParameters(rawParams)
                        val qName = if (pkgName.isEmpty()) simpleName else "$pkgName.$simpleName"
                        out.add(ParsedMessageClass(pkgName, simpleName, qName, params))
                        idx = endIdx
                        continue
                    }
                }
            }
            idx++
        }
        return out
    }

    private fun readAnnotationName(code: String, atIndex: Int): Pair<String, Int>? {
        var cursor = atIndex + 1
        val rest = code.substring(cursor)
        val useSite = Regex("""^(?:field|property|get|set|receiver|param|setparam|delegate)\s*:""").find(rest)
        if (useSite != null) {
            cursor += useSite.value.length
        }
        cursor = skipWhitespace(code, cursor)
        val match = Regex("""^[A-Za-z_][A-Za-z0-9_.]*""").find(code.substring(cursor)) ?: return null
        val name = match.value
        cursor += name.length
        cursor = skipWhitespace(code, cursor)
        if (cursor < code.length && code[cursor] == '(') {
            val close = findMatchingDelimiter(code, cursor, '(', ')') ?: return null
            cursor = close + 1
        }
        return name to cursor
    }

    private fun parseClassHeaderAt(code: String, start: Int): Triple<String, String, Int>? {
        val slice = code.substring(start)
        val classRegex = Regex("""^(?:(?:public|internal|private|data|open|final|abstract|sealed|value)\s+)*class\s+([A-Za-z_][A-Za-z0-9_]*)\b""")
        val match = classRegex.find(slice) ?: return null
        val simpleName = match.groupValues[1]
        var cursor = skipWhitespace(code, start + match.value.length)
        if (cursor < code.length && code[cursor] == '<') {
            val closeAngle = findMatchingDelimiter(code, cursor, '<', '>') ?: return null
            cursor = skipWhitespace(code, closeAngle + 1)
        }
        val ctorKw = Regex("""^(?:(?:public|internal|private)\s+)?constructor\b""").find(code.substring(cursor))
        if (ctorKw != null) {
            cursor = skipWhitespace(code, cursor + ctorKw.value.length)
        }
        if (cursor >= code.length || code[cursor] != '(') {
            return Triple(simpleName, "", cursor)
        }
        val closeParen = findMatchingDelimiter(code, cursor, '(', ')') ?: return null
        return Triple(simpleName, code.substring(cursor + 1, closeParen), closeParen + 1)
    }

    private fun parseConstructorParameters(rawParams: String): List<ParsedParam> {
        if (rawParams.isBlank()) return emptyList()
        val result = mutableListOf<ParsedParam>()
        for (item in splitTopLevelByChar(rawParams, ',')) {
            val trimmed = item.trim()
            if (trimmed.isEmpty()) continue
            var cursor = 0
            while (cursor < trimmed.length && trimmed[cursor] == '@') {
                val parsed = readAnnotationName(trimmed, cursor) ?: break
                cursor = skipWhitespace(trimmed, parsed.second)
            }
            var decl = trimmed.substring(cursor).trim()
            while (true) {
                val next = decl.replace(Regex("""^(?:override|private|protected|internal|public|open|final|vararg)\s+"""), "")
                if (next == decl) break
                decl = next.trim()
            }
            if (!decl.startsWith("val ") && !decl.startsWith("var ")) continue
            decl = decl.removePrefix("val ").removePrefix("var ").trim()
            val colonIdx = findTopLevelChar(decl, ':') ?: continue
            val propName = decl.substring(0, colonIdx).trim()
            val afterColon = decl.substring(colonIdx + 1).trim()
            val eqIdx = findTopLevelChar(afterColon, '=')
            val typeStr = if (eqIdx != null) afterColon.substring(0, eqIdx).trim() else afterColon.trim()
            result.add(ParsedParam(propName, typeStr, eqIdx != null))
        }
        return result
    }

    private fun quoteString(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '$' -> sb.append("\\$")
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun stripCommentsPreservingStrings(source: String): String {
        val sb = StringBuilder(source.length)
        var i = 0
        val n = source.length
        while (i < n) {
            when {
                isRawStringStart(source, i) -> {
                    val end = skipRawString(source, i)
                    sb.append(source, i, end)
                    i = end
                }
                source[i] == '"' -> {
                    val end = skipQuotedString(source, i)
                    sb.append(source, i, end)
                    i = end
                }
                source[i] == '\'' -> {
                    val end = skipCharLiteral(source, i)
                    sb.append(source, i, end)
                    i = end
                }
                i + 1 < n && source[i] == '/' && source[i + 1] == '/' -> {
                    i += 2
                    while (i < n && source[i] != '\n') i++
                }
                i + 1 < n && source[i] == '/' && source[i + 1] == '*' -> {
                    i += 2
                    var depth = 1
                    while (i < n && depth > 0) {
                        if (i + 1 < n && source[i] == '/' && source[i + 1] == '*') {
                            depth++
                            i += 2
                        } else if (i + 1 < n && source[i] == '*' && source[i + 1] == '/') {
                            depth--
                            i += 2
                        } else {
                            if (source[i] == '\n') sb.append('\n')
                            i++
                        }
                    }
                }
                else -> {
                    sb.append(source[i])
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun splitTopLevelByChar(text: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var paren = 0
        var bracket = 0
        var brace = 0
        var angle = 0
        var i = 0
        val n = text.length
        while (i < n) {
            if (isStringLiteralStart(text, i)) {
                val end = skipStringLiteral(text, i)
                current.append(text, i, end)
                i = end
                continue
            }
            if (text[i] == '\'') {
                val end = skipCharLiteral(text, i)
                current.append(text, i, end)
                i = end
                continue
            }
            val ch = text[i]
            when (ch) {
                '(' -> paren++
                ')' -> if (paren > 0) paren--
                '[' -> bracket++
                ']' -> if (bracket > 0) bracket--
                '{' -> brace++
                '}' -> if (brace > 0) brace--
                '<' -> if (paren == 0 && brace == 0) angle++
                '>' -> if (paren == 0 && brace == 0 && angle > 0) angle--
            }
            if (ch == delimiter && paren == 0 && bracket == 0 && brace == 0 && angle == 0) {
                parts.add(current.toString())
                current.setLength(0)
            } else {
                current.append(ch)
            }
            i++
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        return parts
    }

    private fun findTopLevelChar(text: String, target: Char): Int? {
        var paren = 0
        var bracket = 0
        var brace = 0
        var angle = 0
        var i = 0
        val n = text.length
        while (i < n) {
            if (isStringLiteralStart(text, i)) {
                i = skipStringLiteral(text, i)
                continue
            }
            if (text[i] == '\'') {
                i = skipCharLiteral(text, i)
                continue
            }
            val ch = text[i]
            if (ch == target && paren == 0 && bracket == 0 && brace == 0 && angle == 0) {
                return i
            }
            when (ch) {
                '(' -> paren++
                ')' -> if (paren > 0) paren--
                '[' -> bracket++
                ']' -> if (bracket > 0) bracket--
                '{' -> brace++
                '}' -> if (brace > 0) brace--
                '<' -> angle++
                '>' -> if (angle > 0) angle--
            }
            i++
        }
        return null
    }

    private fun findNextCharOutsideParens(text: String, start: Int, target: Char): Int? {
        var i = start
        var paren = 0
        val n = text.length
        while (i < n) {
            if (isStringLiteralStart(text, i)) {
                i = skipStringLiteral(text, i)
                continue
            }
            val ch = text[i]
            if (ch == '(') paren++
            else if (ch == ')' && paren > 0) paren--
            else if (ch == target && paren == 0) return i
            i++
        }
        return null
    }

    private fun findMatchingDelimiter(text: String, openIdx: Int, open: Char, close: Char): Int? {
        var depth = 0
        var i = openIdx
        val n = text.length
        while (i < n) {
            if (isStringLiteralStart(text, i)) {
                i = skipStringLiteral(text, i)
                continue
            }
            if (text[i] == '\'') {
                i = skipCharLiteral(text, i)
                continue
            }
            when (text[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    private fun skipWhitespace(text: String, start: Int): Int {
        var i = start
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }

    private fun isStringLiteralStart(text: String, idx: Int): Boolean =
        idx < text.length && text[idx] == '"'

    private fun skipStringLiteral(text: String, idx: Int): Int =
        if (isRawStringStart(text, idx)) skipRawString(text, idx) else skipQuotedString(text, idx)

    private fun isRawStringStart(text: String, idx: Int): Boolean =
        idx + 2 < text.length && text[idx] == '"' && text[idx + 1] == '"' && text[idx + 2] == '"'

    private fun skipRawString(text: String, idx: Int): Int {
        var i = idx + 3
        val n = text.length
        while (i + 2 < n) {
            if (text[i] == '"' && text[i + 1] == '"' && text[i + 2] == '"') return i + 3
            i++
        }
        return n
    }

    private fun skipQuotedString(text: String, idx: Int): Int {
        var i = idx + 1
        val n = text.length
        while (i < n) {
            if (text[i] == '\\' && i + 1 < n) {
                i += 2
                continue
            }
            if (text[i] == '"') return i + 1
            i++
        }
        return n
    }

    private fun skipCharLiteral(text: String, idx: Int): Int {
        var i = idx + 1
        val n = text.length
        while (i < n) {
            if (text[i] == '\\' && i + 1 < n) {
                i += 2
                continue
            }
            if (text[i] == '\'') return i + 1
            i++
        }
        return n
    }
}
