package com.nexustools.genericui.plugin.spi

import java.io.File

/**
 * Compile-time parser and reflection-free [com.nexustools.genericui.component.system.DataClassCodec]
 * generator for `@BinarySerializable` `data class` declarations.
 *
 * Strictly emits 1 public top-level `object` per generated `.kt` file:
 * - `<Class>BinaryCodec.kt` implementing `DataClassCodec<<Class>>`
 * - `GeneratedBinaryCodecRegistrar.kt` registering every generated codec into `DataClassCodecRegistry`
 */
internal object BinaryCodecCodegen {

    private data class ParsedParam(
        val name: String,
        val rawType: String,
        val hasDefaultValue: Boolean,
        val defaultValueExpr: String?
    )

    private data class ParsedEnum(
        val packageName: String,
        val simpleName: String,
        val qualifiedName: String,
        val entries: List<String>
    )

    private data class ParsedDataClass(
        val packageName: String,
        val simpleName: String,
        val qualifiedName: String,
        val params: List<ParsedParam>
    )

    private data class ParsedSourceFile(
        val packageName: String,
        val imports: List<String>,
        val explicitImports: Map<String, String>,
        val wildcardImports: List<String>,
        val enums: List<ParsedEnum>,
        val dataClasses: List<ParsedDataClass>
    )

    private val DEFAULT_SIMPLE_ANNOTATIONS = setOf("BinarySerializable")
    private val DEFAULT_QUALIFIED_ANNOTATIONS = setOf(
        "com.nexustools.genericui.component.system.BinarySerializable"
    )

    fun generateCodecSources(
        sourceDirectories: List<File>,
        outputDirectory: File,
        registrarPackageName: String,
        extraSimpleAnnotationNames: Set<String> = emptySet(),
        extraQualifiedAnnotationNames: Set<String> = emptySet()
    ): List<File> {
        val simpleAnnNames = (DEFAULT_SIMPLE_ANNOTATIONS + extraSimpleAnnotationNames)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val qualifiedAnnNames = (DEFAULT_QUALIFIED_ANNOTATIONS + extraQualifiedAnnotationNames)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

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
            parseKotlinFile(
                source = file.readText(Charsets.UTF_8),
                simpleAnnNames = simpleAnnNames,
                qualifiedAnnNames = qualifiedAnnNames
            )
        }

        val enumRegistry = LinkedHashMap<String, ParsedEnum>()
        val classRegistry = LinkedHashMap<String, ParsedDataClass>()
        val classFileByQName = LinkedHashMap<String, ParsedSourceFile>()

        for (pf in parsedFiles) {
            for (en in pf.enums) {
                enumRegistry.putIfAbsent(en.qualifiedName, en)
            }
            for (dc in pf.dataClasses) {
                classRegistry.putIfAbsent(dc.qualifiedName, dc)
                classFileByQName.putIfAbsent(dc.qualifiedName, pf)
            }
        }

        val desiredFiles = LinkedHashMap<String, String>()
        val registrations = mutableListOf<Pair<String, String>>()

        for (dc in classRegistry.values) {
            val sourceFile = classFileByQName.getValue(dc.qualifiedName)
            val codecSimpleName = "${dc.simpleName}BinaryCodec"
            val pkgPath = if (dc.packageName.isEmpty()) "" else dc.packageName.replace('.', '/') + "/"
            val relPath = "$pkgPath$codecSimpleName.kt"
            val codecFqn = if (dc.packageName.isEmpty()) codecSimpleName else "${dc.packageName}.$codecSimpleName"
            desiredFiles[relPath] = generateCodecFile(
                dc = dc,
                parsedFile = sourceFile,
                enums = enumRegistry,
                dataClasses = classRegistry
            )
            registrations.add(dc.qualifiedName to codecFqn)
        }

        val cleanRegistrarPkg = registrarPackageName.trim().ifEmpty {
            "com.nexustools.genericui.generated"
        }
        val registrarRelPath = cleanRegistrarPkg.replace('.', '/') + "/GeneratedBinaryCodecRegistrar.kt"
        desiredFiles[registrarRelPath] = generateRegistrarFile(
            registrarPackage = cleanRegistrarPkg,
            registrations = registrations.sortedBy { it.first }
        )

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
        registrations: List<Pair<String, String>>
    ): String = buildString {
        appendLine("package $registrarPackage")
        appendLine()
        appendLine("import com.nexustools.genericui.component.system.DataClassCodecRegistry")
        appendLine()
        appendLine("object GeneratedBinaryCodecRegistrar {")
        appendLine("    fun registerAll() {")
        for ((modelFqn, codecFqn) in registrations) {
            appendLine("        DataClassCodecRegistry.register($modelFqn::class, $codecFqn)")
        }
        appendLine("    }")
        appendLine("}")
    }

    private fun generateCodecFile(
        dc: ParsedDataClass,
        parsedFile: ParsedSourceFile,
        enums: Map<String, ParsedEnum>,
        dataClasses: Map<String, ParsedDataClass>
    ): String = buildString {
        val codecSimpleName = "${dc.simpleName}BinaryCodec"
        val allHaveDefaults = dc.params.isNotEmpty() && dc.params.all { it.hasDefaultValue }
        val reservedSimpleNames = setOf("DataClassCodec", "PreferenceElement")
        val filteredImports = parsedFile.imports.filter { imp ->
            val clean = imp.trim()
            val lastSeg = clean.substringAfterLast('.').substringBefore(" as ").trim()
            lastSeg !in reservedSimpleNames &&
                clean != "com.nexustools.genericui.component.system.DataClassCodec" &&
                clean != "com.nexustools.genericui.component.system.PreferenceElement" &&
                clean != "com.nexustools.genericui.component.system.BinarySerializable"
        }.distinct()

        if (dc.packageName.isNotEmpty()) {
            appendLine("package ${dc.packageName}")
            appendLine()
        }
        appendLine("import com.nexustools.genericui.component.system.DataClassCodec")
        appendLine("import com.nexustools.genericui.component.system.PreferenceElement")
        for (imp in filteredImports) {
            appendLine("import $imp")
        }
        appendLine()
        appendLine("object $codecSimpleName : DataClassCodec<${dc.simpleName}> {")
        appendLine("    override fun encode(value: ${dc.simpleName}): PreferenceElement {")
        appendLine("        val fields = LinkedHashMap<String, PreferenceElement>(${dc.params.size})")
        for (param in dc.params) {
            val encodeExpr = buildEncodeExpr(
                rawType = param.rawType,
                valueExpr = "value.${param.name}",
                parsedFile = parsedFile,
                enums = enums,
                dataClasses = dataClasses,
                depth = 0
            )
            appendLine("        fields[${quoteString(param.name)}] = $encodeExpr")
        }
        appendLine("        return PreferenceElement.ofObject(fields)")
        appendLine("    }")
        appendLine()
        appendLine("    override fun decode(element: PreferenceElement): ${dc.simpleName} {")
        if (allHaveDefaults) {
            appendLine("        val defaults = ${dc.simpleName}()")
        }
        appendLine("        val fields = element.mapEntries")
        if (dc.params.isEmpty()) {
            appendLine("        return ${dc.simpleName}()")
        } else {
            appendLine("        return ${dc.simpleName}(")
            dc.params.forEachIndexed { idx, param ->
                val comma = if (idx < dc.params.lastIndex) "," else ""
                val keyLit = quoteString(param.name)
                val fallbackExpr = when {
                    !param.hasDefaultValue -> null
                    allHaveDefaults -> "defaults.${param.name}"
                    !param.defaultValueExpr.isNullOrBlank() -> "(${param.defaultValueExpr})"
                    else -> null
                }
                val decodeExpr = buildDecodeExpr(
                    rawType = param.rawType,
                    elemExpr = "fields[$keyLit]",
                    defaultFallbackExpr = fallbackExpr,
                    parsedFile = parsedFile,
                    enums = enums,
                    dataClasses = dataClasses,
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
        dataClasses: Map<String, ParsedDataClass>,
        depth: Int
    ): String {
        val trimmed = rawType.trim()
        val isNullable = trimmed.endsWith("?")
        val base = trimmed.removeSuffix("?").trim()
        val varName = "v$depth"
        val nonNull = buildNonNullEncodeExpr(base, if (isNullable) varName else valueExpr, parsedFile, enums, dataClasses, depth)
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
        dataClasses: Map<String, ParsedDataClass>,
        depth: Int
    ): String {
        return when (baseType) {
            "Boolean", "kotlin.Boolean" -> "PreferenceElement.ofBoolean($valueExpr)"
            "Byte", "kotlin.Byte" -> "PreferenceElement.ofByte($valueExpr)"
            "Short", "kotlin.Short" -> "PreferenceElement.ofShort($valueExpr)"
            "Int", "kotlin.Int" -> "PreferenceElement.ofInt($valueExpr)"
            "Long", "kotlin.Long" -> "PreferenceElement.ofLong($valueExpr)"
            "Float", "kotlin.Float" -> "PreferenceElement.ofFloat($valueExpr)"
            "Double", "kotlin.Double" -> "PreferenceElement.ofDouble($valueExpr)"
            "String", "kotlin.String" -> "PreferenceElement.ofString($valueExpr)"
            "ByteArray", "kotlin.ByteArray" -> "PreferenceElement.ofBytes($valueExpr)"
            "PreferenceElement", "com.nexustools.genericui.component.system.PreferenceElement" -> valueExpr
            else -> {
                val typeHead = baseType.substringBefore('<').trim()
                val genericArgs = extractGenericTypeArguments(baseType)
                if (typeHead in setOf(
                        "List", "MutableList", "kotlin.collections.List", "kotlin.collections.MutableList",
                        "Set", "MutableSet", "kotlin.collections.Set", "kotlin.collections.MutableSet"
                    ) && genericArgs.size == 1
                ) {
                    val itemVar = "item$depth"
                    val itemEncode = buildEncodeExpr(genericArgs[0], itemVar, parsedFile, enums, dataClasses, depth + 1)
                    "PreferenceElement.ofArray($valueExpr.map { $itemVar -> $itemEncode })"
                } else if (typeHead in setOf(
                        "Map", "MutableMap", "LinkedHashMap",
                        "kotlin.collections.Map", "kotlin.collections.MutableMap", "kotlin.collections.LinkedHashMap"
                    ) && genericArgs.size == 2
                ) {
                    val kVar = "k$depth"
                    val vVar = "mv$depth"
                    val keyEncode = buildMapKeyEncodeExpr(genericArgs[0], kVar, parsedFile, enums)
                    val valEncode = buildEncodeExpr(genericArgs[1], vVar, parsedFile, enums, dataClasses, depth + 1)
                    "PreferenceElement.ofMap($valueExpr.entries.associate { ($kVar, $vVar) -> $keyEncode to $valEncode })"
                } else {
                    val nestedClass = resolveDataClass(baseType, parsedFile, dataClasses)
                    if (nestedClass != null) {
                        val codecFqn = if (nestedClass.packageName.isEmpty()) {
                            "${nestedClass.simpleName}BinaryCodec"
                        } else {
                            "${nestedClass.packageName}.${nestedClass.simpleName}BinaryCodec"
                        }
                        "$codecFqn.encode($valueExpr)"
                    } else {
                        val enumDecl = resolveEnumDecl(baseType, parsedFile, enums)
                        if (enumDecl != null) {
                            "PreferenceElement.ofString($valueExpr.name)"
                        } else {
                            throw IllegalArgumentException(
                                "Unsupported property type '$baseType' in @BinarySerializable data class. " +
                                    "Supported types: Boolean, Byte, Short, Int, Long, Float, Double, String, ByteArray, " +
                                    "enums, List/Set<...>, Map<..., ...>, nested @BinarySerializable data classes, and nullable variants."
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
        dataClasses: Map<String, ParsedDataClass>,
        depth: Int
    ): String {
        val trimmed = rawType.trim()
        val isNullable = trimmed.endsWith("?")
        val base = trimmed.removeSuffix("?").trim()
        if (isNullable) {
            val elVar = "el$depth"
            val nonNullDecode = buildNonNullDecodeExpr(base, elVar, null, parsedFile, enums, dataClasses, depth)
            return if (defaultFallbackExpr != null) {
                "$elemExpr.let { raw$depth -> when { raw$depth == null -> $defaultFallbackExpr; raw$depth.isNull -> null; else -> raw$depth.let { $elVar -> $nonNullDecode } } }"
            } else {
                "($elemExpr)?.takeIf { !it.isNull }?.let { $elVar -> $nonNullDecode }"
            }
        }
        return if (defaultFallbackExpr != null) {
            val rawVar = "raw$depth"
            val nonNullDecode = buildNonNullDecodeExpr(base, rawVar, defaultFallbackExpr, parsedFile, enums, dataClasses, depth)
            "($elemExpr)?.takeIf { !it.isNull }?.let { $rawVar -> $nonNullDecode } ?: $defaultFallbackExpr"
        } else {
            buildNonNullDecodeExpr(base, elemExpr, null, parsedFile, enums, dataClasses, depth)
        }
    }

    private fun buildNonNullDecodeExpr(
        baseType: String,
        elemExpr: String,
        defaultFallbackExpr: String?,
        parsedFile: ParsedSourceFile,
        enums: Map<String, ParsedEnum>,
        dataClasses: Map<String, ParsedDataClass>,
        depth: Int
    ): String {
        return when (baseType) {
            "Boolean", "kotlin.Boolean" -> "($elemExpr)?.asBoolean(${defaultFallbackExpr ?: "false"}) ?: ${defaultFallbackExpr ?: "false"}"
            "Byte", "kotlin.Byte" -> "($elemExpr)?.asByte(${defaultFallbackExpr ?: "0.toByte()"}) ?: ${defaultFallbackExpr ?: "0.toByte()"}"
            "Short", "kotlin.Short" -> "($elemExpr)?.asShort(${defaultFallbackExpr ?: "0.toShort()"}) ?: ${defaultFallbackExpr ?: "0.toShort()"}"
            "Int", "kotlin.Int" -> "($elemExpr)?.asInt(${defaultFallbackExpr ?: "0"}) ?: ${defaultFallbackExpr ?: "0"}"
            "Long", "kotlin.Long" -> "($elemExpr)?.asLong(${defaultFallbackExpr ?: "0L"}) ?: ${defaultFallbackExpr ?: "0L"}"
            "Float", "kotlin.Float" -> "($elemExpr)?.asFloat(${defaultFallbackExpr ?: "0f"}) ?: ${defaultFallbackExpr ?: "0f"}"
            "Double", "kotlin.Double" -> "($elemExpr)?.asDouble(${defaultFallbackExpr ?: "0.0"}) ?: ${defaultFallbackExpr ?: "0.0"}"
            "String", "kotlin.String" -> "($elemExpr)?.asString(${defaultFallbackExpr ?: "\"\""}) ?: ${defaultFallbackExpr ?: "\"\""}"
            "ByteArray", "kotlin.ByteArray" -> {
                val fallback = defaultFallbackExpr ?: "ByteArray(0)"
                "($elemExpr)?.let { b$depth -> b$depth.bytesOrNull() ?: b$depth.asBytes($fallback) } ?: $fallback"
            }
            "PreferenceElement", "com.nexustools.genericui.component.system.PreferenceElement" -> "($elemExpr) ?: ${defaultFallbackExpr ?: "PreferenceElement.Null"}"
            else -> {
                val typeHead = baseType.substringBefore('<').trim()
                val genericArgs = extractGenericTypeArguments(baseType)
                if (typeHead in setOf(
                        "List", "MutableList", "kotlin.collections.List", "kotlin.collections.MutableList",
                        "Set", "MutableSet", "kotlin.collections.Set", "kotlin.collections.MutableSet"
                    ) && genericArgs.size == 1
                ) {
                    val itemVar = "it$depth"
                    val itemDecode = buildDecodeExpr(genericArgs[0], itemVar, null, parsedFile, enums, dataClasses, depth + 1)
                    val mapCall = if (typeHead.endsWith("MutableList")) ".toMutableList()" else if (typeHead.endsWith("MutableSet")) ".toMutableSet()" else if (typeHead.contains("Set")) ".toSet()" else ""
                    val defaultColl = defaultFallbackExpr ?: when {
                        typeHead.endsWith("MutableList") -> "mutableListOf()"
                        typeHead.endsWith("MutableSet") -> "mutableSetOf()"
                        typeHead.contains("Set") -> "emptySet()"
                        else -> "emptyList()"
                    }
                    "($elemExpr)?.takeIf { it.isArray }?.arrayElements?.map { $itemVar -> $itemDecode }$mapCall ?: $defaultColl"
                } else if (typeHead in setOf(
                        "Map", "MutableMap", "LinkedHashMap",
                        "kotlin.collections.Map", "kotlin.collections.MutableMap", "kotlin.collections.LinkedHashMap"
                    ) && genericArgs.size == 2
                ) {
                    val kVar = "mk$depth"
                    val vVar = "mv$depth"
                    val keyDecode = buildMapKeyDecodeExpr(genericArgs[0], kVar, parsedFile, enums)
                    val valDecode = buildDecodeExpr(genericArgs[1], vVar, null, parsedFile, enums, dataClasses, depth + 1)
                    val suffix = if (typeHead.contains("MutableMap") || typeHead.endsWith("LinkedHashMap")) ".toMutableMap()" else ""
                    "($elemExpr)?.takeIf { it.isMap || it.isObject }?.mapEntries?.entries?.associate { ($kVar, $vVar) -> $keyDecode to $valDecode }$suffix ?: ${defaultFallbackExpr ?: if (suffix.isNotEmpty()) "mutableMapOf()" else "emptyMap()"}"
                } else {
                    val nestedClass = resolveDataClass(baseType, parsedFile, dataClasses)
                    if (nestedClass != null) {
                        val codecFqn = if (nestedClass.packageName.isEmpty()) {
                            "${nestedClass.simpleName}BinaryCodec"
                        } else {
                            "${nestedClass.packageName}.${nestedClass.simpleName}BinaryCodec"
                        }
                        if (defaultFallbackExpr != null) {
                            "($elemExpr)?.takeIf { !it.isNull }?.let { nc$depth -> $codecFqn.decode(nc$depth) } ?: $defaultFallbackExpr"
                        } else {
                            "$codecFqn.decode(($elemExpr) ?: PreferenceElement.Null)"
                        }
                    } else {
                        val enumDecl = resolveEnumDecl(baseType, parsedFile, enums)
                        if (enumDecl != null) {
                            val fallback = defaultFallbackExpr ?: "${enumDecl.qualifiedName}.entries.first()"
                            "($elemExpr)?.asString()?.let { s$depth -> ${enumDecl.qualifiedName}.entries.firstOrNull { it.name == s$depth } } ?: $fallback"
                        } else {
                            throw IllegalArgumentException(
                                "Unsupported property type '$baseType' in @BinarySerializable data class."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun buildMapKeyEncodeExpr(
        rawKeyType: String,
        keyVar: String,
        parsedFile: ParsedSourceFile,
        enums: Map<String, ParsedEnum>
    ): String {
        val clean = rawKeyType.trim().removeSuffix("?").trim()
        return when (clean) {
            "String", "kotlin.String" -> keyVar
            "Boolean", "kotlin.Boolean",
            "Byte", "kotlin.Byte",
            "Short", "kotlin.Short",
            "Int", "kotlin.Int",
            "Long", "kotlin.Long",
            "Float", "kotlin.Float",
            "Double", "kotlin.Double" -> "$keyVar.toString()"
            else -> {
                val enumDecl = resolveEnumDecl(clean, parsedFile, enums)
                if (enumDecl != null) {
                    "$keyVar.name"
                } else {
                    throw IllegalArgumentException(
                        "Unsupported Map key type '$rawKeyType' in @BinarySerializable data class. " +
                            "Supported key types: String, primitive scalars, and enum classes."
                    )
                }
            }
        }
    }

    private fun buildMapKeyDecodeExpr(
        rawKeyType: String,
        keyVar: String,
        parsedFile: ParsedSourceFile,
        enums: Map<String, ParsedEnum>
    ): String {
        val clean = rawKeyType.trim().removeSuffix("?").trim()
        return when (clean) {
            "String", "kotlin.String" -> keyVar
            "Boolean", "kotlin.Boolean" -> "$keyVar.toBooleanStrict()"
            "Byte", "kotlin.Byte" -> "$keyVar.toByte()"
            "Short", "kotlin.Short" -> "$keyVar.toShort()"
            "Int", "kotlin.Int" -> "$keyVar.toInt()"
            "Long", "kotlin.Long" -> "$keyVar.toLong()"
            "Float", "kotlin.Float" -> "$keyVar.toFloat()"
            "Double", "kotlin.Double" -> "$keyVar.toDouble()"
            else -> {
                val enumDecl = resolveEnumDecl(clean, parsedFile, enums)
                if (enumDecl != null) {
                    "${enumDecl.qualifiedName}.valueOf($keyVar)"
                } else {
                    throw IllegalArgumentException(
                        "Unsupported Map key type '$rawKeyType' in @BinarySerializable data class."
                    )
                }
            }
        }
    }

    private fun resolveDataClass(
        rawType: String,
        parsedFile: ParsedSourceFile,
        dataClasses: Map<String, ParsedDataClass>
    ): ParsedDataClass? {
        dataClasses[rawType]?.let { return it }
        parsedFile.explicitImports[rawType]?.let { q -> dataClasses[q]?.let { return it } }
        if (parsedFile.packageName.isNotEmpty()) {
            dataClasses["${parsedFile.packageName}.$rawType"]?.let { return it }
        }
        for (wc in parsedFile.wildcardImports) {
            dataClasses["$wc.$rawType"]?.let { return it }
        }
        return dataClasses.values.firstOrNull { it.simpleName == rawType }
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

    private fun parseKotlinFile(
        source: String,
        simpleAnnNames: Set<String>,
        qualifiedAnnNames: Set<String>
    ): ParsedSourceFile {
        val code = stripCommentsPreservingStrings(source)
        val pkgName = Regex("""(?m)^\s*package\s+([A-Za-z0-9_.]+)""").find(code)?.groupValues?.get(1)?.trim().orEmpty()
        val imports = mutableListOf<String>()
        val explicitImports = LinkedHashMap<String, String>()
        val wildcardImports = mutableListOf<String>()
        for (m in Regex("""(?m)^\s*import\s+([A-Za-z0-9_.*]+(?:\s+as\s+[A-Za-z_][A-Za-z0-9_]*)?)""").findAll(code)) {
            val fullImp = m.groupValues[1].trim()
            imports.add(fullImp)
            val impPath = fullImp.substringBefore(" as ").trim()
            val alias = if (" as " in fullImp) fullImp.substringAfter(" as ").trim() else null
            if (impPath.endsWith(".*")) {
                wildcardImports.add(impPath.removeSuffix(".*"))
            } else if ('.' in impPath) {
                explicitImports[alias ?: impPath.substringAfterLast('.')] = impPath
            }
        }
        return ParsedSourceFile(
            packageName = pkgName,
            imports = imports,
            explicitImports = explicitImports,
            wildcardImports = wildcardImports,
            enums = parseEnums(code, pkgName),
            dataClasses = parseDataClasses(code, pkgName, simpleAnnNames, qualifiedAnnNames)
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

    private fun parseDataClasses(
        code: String,
        pkgName: String,
        simpleAnnNames: Set<String>,
        qualifiedAnnNames: Set<String>
    ): List<ParsedDataClass> {
        val out = mutableListOf<ParsedDataClass>()
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
                val isBinarySerializable = anns.any {
                    it in simpleAnnNames || it in qualifiedAnnNames
                }
                if (isBinarySerializable) {
                    val classDecl = parseClassHeaderAt(code, cursor)
                    if (classDecl != null) {
                        val (simpleName, rawParams, endIdx) = classDecl
                        val params = parseConstructorParameters(rawParams)
                        val qName = if (pkgName.isEmpty()) simpleName else "$pkgName.$simpleName"
                        out.add(ParsedDataClass(pkgName, simpleName, qName, params))
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
        if (!Regex("""\bdata\b""").containsMatchIn(match.value.substringBefore("class"))) {
            return null
        }
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
            val defaultExpr = if (eqIdx != null) afterColon.substring(eqIdx + 1).trim() else null
            result.add(ParsedParam(propName, typeStr, eqIdx != null, defaultExpr))
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
                '<' -> if (paren == 0 && brace == 0) angle++
                '>' -> if (paren == 0 && brace == 0 && angle > 0) angle--
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
