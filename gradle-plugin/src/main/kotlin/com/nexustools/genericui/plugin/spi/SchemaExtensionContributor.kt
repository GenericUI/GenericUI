package com.nexustools.genericui.plugin.spi

import com.nexustools.genericui.plugin.GenericUiDiscoveredFragment
import com.nexustools.genericui.plugin.GenericUiVariantScope
import java.io.File

/**
 * Built-in SPI contributor for `:extensions:data:schema` handling `@SchemaData` annotation
 * scanning, compile-time `<ClassName>SchemaDescriptor.kt` + `GeneratedSchemaRegistrar.kt`
 * code generation, and runtime registrar invocation across all GenericUI backends.
 */
object SchemaExtensionContributor : GenericUiExtensionContributor {

    const val SCHEMA_MODULE_PATH: String = ":extensions:data:schema"
    const val SCHEMA_DSL_MODULE_PATH: String = ":extensions:data:schema:dsl"
    const val OPTION_REGISTRAR_PACKAGE: String = "schemaRegistrarPackage"
    const val DEFAULT_REGISTRAR_PACKAGE: String = "com.nexustools.genericui.generated"

    override val extensionModulePath: String = SCHEMA_MODULE_PATH

    override val dslModulePath: String = SCHEMA_DSL_MODULE_PATH

    override val annotationScanSpecs: List<ExtensionAnnotationScanSpec> = listOf(
        ExtensionAnnotationScanSpec(
            extensionModulePath = SCHEMA_MODULE_PATH,
            simpleAnnotationNames = setOf("SchemaData"),
            qualifiedAnnotationNames = setOf("com.nexustools.genericui.ext.schema.SchemaData"),
            targetKinds = setOf(ExtensionAnnotationScanSpec.SymbolTargetKind.CLASS)
        )
    )

    fun resolveRegistrarPackage(context: ExtensionBuildContext): String {
        val explicit = context.extensionOptions[OPTION_REGISTRAR_PACKAGE]?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) return explicit

        val genPkg = context.generatedPackageName.trim()
        if (genPkg.isNotEmpty()) {
            return if (genPkg.endsWith(".generated")) genPkg else "$genPkg.generated"
        }

        val mainFragmentPkg = context.discoveredFragments.firstOrNull {
            it.isMainFragment && !it.isApplicationClass && it.packageName.isNotBlank()
        }?.packageName?.trim()
        if (!mainFragmentPkg.isNullOrEmpty()) {
            return if (mainFragmentPkg.endsWith(".generated")) mainFragmentPkg else "$mainFragmentPkg.generated"
        }

        val symbolPkg = context.discoveredSymbols.firstOrNull {
            it.packageName.isNotBlank()
        }?.packageName?.trim()
        if (!symbolPkg.isNullOrEmpty()) {
            return if (symbolPkg.endsWith(".generated")) symbolPkg else "$symbolPkg.generated"
        }

        val appIdPkg = context.appId.trim().takeIf { it.isNotEmpty() }
        if (appIdPkg != null) {
            return if (appIdPkg.endsWith(".generated")) appIdPkg else "$appIdPkg.generated"
        }

        return DEFAULT_REGISTRAR_PACKAGE
    }

    fun resolveRegistrarPackageFromSettings(
        explicitRegistrarPackage: String?,
        basePackageName: String?
    ): String {
        val explicit = explicitRegistrarPackage?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) return explicit

        val base = basePackageName?.trim()?.takeIf { it.isNotEmpty() } ?: return DEFAULT_REGISTRAR_PACKAGE
        return if (base.endsWith(".generated")) base else "$base.generated"
    }

    override fun providerInstallerCalls(
        backendScope: GenericUiVariantScope,
        context: ExtensionBuildContext
    ): List<String> {
        if (SCHEMA_MODULE_PATH !in context.enabledExtensionModulePaths) {
            return emptyList()
        }
        val registrarPkg = resolveRegistrarPackage(context)
        return listOf("$registrarPkg.GeneratedSchemaRegistrar.ensureRegistered()")
    }

    override fun generatedLauncherSources(
        backendScope: GenericUiVariantScope,
        context: ExtensionBuildContext
    ): List<ExtensionGeneratedSource> = emptyList()

    override fun contributeAndroid(
        context: ExtensionBuildContext,
        fragment: GenericUiDiscoveredFragment?
    ): AndroidExtensionContribution? {
        if (fragment != null) return null
        if (SCHEMA_MODULE_PATH !in context.enabledExtensionModulePaths) return null
        return AndroidExtensionContribution(
            providerInstallerCalls = providerInstallerCalls(GenericUiVariantScope.ANDROID, context)
        )
    }

    internal fun generateSchemaSources(
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
            .distinctBy { it.canonicalPath }
            .sortedBy { it.canonicalPath }

        val parsedFiles = ktFiles.mapNotNull { file ->
            runCatching { parseKotlinSourceFile(file, file.readText()) }.getOrNull()
        }

        val enumRegistry = LinkedHashMap<String, ParsedEnum>()
        val schemaClassRegistry = LinkedHashMap<String, ParsedSchemaClass>()
        for (parsed in parsedFiles) {
            for (en in parsed.enums) {
                enumRegistry[en.qualifiedName] = en
                enumRegistry.putIfAbsent(en.simpleName, en)
            }
            for (cls in parsed.schemaClasses) {
                schemaClassRegistry[cls.qualifiedName] = cls
                schemaClassRegistry.putIfAbsent(cls.simpleName, cls)
            }
        }

        val desiredFiles = LinkedHashMap<String, String>()
        val descriptorEntries = mutableListOf<Pair<String, String>>()

        for (parsed in parsedFiles) {
            for (schemaCls in parsed.schemaClasses) {
                val descriptorCode = generateDescriptorSource(
                    schemaClass = schemaCls,
                    parsedFile = parsed,
                    schemaClasses = schemaClassRegistry,
                    enums = enumRegistry
                )
                val pkgSubPath = if (schemaCls.packageName.isEmpty()) {
                    ""
                } else {
                    schemaCls.packageName.replace('.', '/') + "/"
                }
                val descriptorClassName = "${schemaCls.simpleName}SchemaDescriptor"
                val relPath = "$pkgSubPath$descriptorClassName.kt"
                desiredFiles[relPath] = descriptorCode
                val descriptorFqn = if (schemaCls.packageName.isEmpty()) {
                    descriptorClassName
                } else {
                    "${schemaCls.packageName}.$descriptorClassName"
                }
                val modelFqn = schemaCls.qualifiedName
                descriptorEntries.add(descriptorFqn to modelFqn)
            }
        }

        val registrarPkg = registrarPackageName.trim().ifEmpty { DEFAULT_REGISTRAR_PACKAGE }
        val registrarRelPath = registrarPkg.replace('.', '/') + "/GeneratedSchemaRegistrar.kt"
        desiredFiles[registrarRelPath] = generateRegistrarSource(registrarPkg, descriptorEntries)

        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        } else {
            // Remove stale generated files no longer produced by the current source set.
            outputDirectory.walkBottomUp()
                .filter { it.isFile && it.extension.equals("kt", ignoreCase = true) }
                .forEach { existing ->
                    val rel = existing.relativeTo(outputDirectory).invariantSeparatorsPath
                    if (rel !in desiredFiles) {
                        existing.delete()
                    }
                }
        }

        val writtenFiles = mutableListOf<File>()
        for ((relPath, content) in desiredFiles) {
            val outFile = File(outputDirectory, relPath)
            outFile.parentFile?.mkdirs()
            val normalizedNew = content.replace("\r\n", "\n")
            val existingContent = if (outFile.isFile) outFile.readText().replace("\r\n", "\n") else null
            if (existingContent != normalizedNew) {
                outFile.writeText(normalizedNew, Charsets.UTF_8)
            }
            writtenFiles.add(outFile)
        }
        return writtenFiles
    }

    private data class ParsedAnnotation(
        val name: String,
        val rawArgs: String?
    )

    private data class ParsedParam(
        val name: String,
        val rawType: String,
        val hasDefaultValue: Boolean,
        val annotations: List<ParsedAnnotation>
    )

    private data class ParsedEnum(
        val packageName: String,
        val simpleName: String,
        val qualifiedName: String,
        val entries: List<String>
    )

    private data class ParsedSchemaClass(
        val packageName: String,
        val simpleName: String,
        val qualifiedName: String,
        val schemaAnnotation: ParsedAnnotation,
        val params: List<ParsedParam>
    )

    private data class ParsedKotlinFile(
        val packageName: String,
        val explicitImports: Map<String, String>,
        val wildcardImports: List<String>,
        val enums: List<ParsedEnum>,
        val schemaClasses: List<ParsedSchemaClass>
    )

    private fun generateRegistrarSource(
        registrarPackage: String,
        descriptorEntries: List<Pair<String, String>>
    ): String = buildString {
        appendLine("package $registrarPackage")
        appendLine()
        appendLine("import com.nexustools.genericui.ext.schema.SchemaRegistry")
        appendLine()
        appendLine("object GeneratedSchemaRegistrar {")
        appendLine("    fun ensureRegistered() {")
        for ((descriptorFqn, modelFqn) in descriptorEntries.sortedBy { it.first }) {
            appendLine("        SchemaRegistry.register($descriptorFqn, $modelFqn::class)")
        }
        appendLine("    }")
        appendLine("}")
    }

    private fun generateDescriptorSource(
        schemaClass: ParsedSchemaClass,
        parsedFile: ParsedKotlinFile,
        schemaClasses: Map<String, ParsedSchemaClass>,
        enums: Map<String, ParsedEnum>
    ): String {
        val schemaArgs = parseNamedArgs(
            schemaClass.schemaAnnotation.rawArgs,
            listOf("id", "title", "description")
        )
        val schemaId = unquoteString(schemaArgs["id"])?.takeIf { it.isNotEmpty() } ?: schemaClass.simpleName
        val schemaTitle = unquoteString(schemaArgs["title"]) ?: ""
        val schemaDescription = unquoteString(schemaArgs["description"])?.takeIf { it.isNotEmpty() }

        val activeParams = schemaClass.params.filter { p ->
            p.annotations.none { ann -> isAnnotationNamed(ann, "SchemaIgnore", "com.nexustools.genericui.ext.schema.SchemaIgnore") }
        }
        val allParamsHaveDefaults = schemaClass.params.all { it.hasDefaultValue }

        val descriptorName = "${schemaClass.simpleName}SchemaDescriptor"
        val sectionsOrder = LinkedHashSet<String>()
        var usesRgbaColor = false

        data class PreparedProp(
            val param: ParsedParam,
            val key: String,
            val fieldSpecCode: String,
            val toMapExpr: String,
            val fromMapExpr: String
        )

        val preparedProps = mutableListOf<PreparedProp>()
        for (param in activeParams) {
            val fieldAnn = param.annotations.firstOrNull {
                isAnnotationNamed(it, "SchemaField", "com.nexustools.genericui.ext.schema.SchemaField")
            }
            val visibleAnn = param.annotations.firstOrNull {
                isAnnotationNamed(it, "SchemaVisibleWhen", "com.nexustools.genericui.ext.schema.SchemaVisibleWhen")
            }
            val enabledAnn = param.annotations.firstOrNull {
                isAnnotationNamed(it, "SchemaEnabledWhen", "com.nexustools.genericui.ext.schema.SchemaEnabledWhen")
            }

            val fieldArgs = parseNamedArgs(
                fieldAnn?.rawArgs,
                listOf(
                    "key", "label", "description", "placeholder", "group", "unit",
                    "min", "max", "step", "precision", "maxLength", "required",
                    "secret", "multiline", "fullWidth", "widgetHint", "legacyKeys"
                )
            )
            val key = unquoteString(fieldArgs["key"])?.takeIf { it.isNotEmpty() } ?: param.name
            val label = unquoteString(fieldArgs["label"]) ?: ""
            val description = unquoteString(fieldArgs["description"])?.takeIf { it.isNotEmpty() }
            val placeholder = unquoteString(fieldArgs["placeholder"])?.takeIf { it.isNotEmpty() }
            val group = unquoteString(fieldArgs["group"])?.takeIf { it.isNotEmpty() }
            if (group != null) sectionsOrder.add(group)
            val unit = unquoteString(fieldArgs["unit"])?.takeIf { it.isNotEmpty() }
            val minNum = parseDoubleLiteral(fieldArgs["min"])
            val maxNum = parseDoubleLiteral(fieldArgs["max"])
            val stepNum = parseDoubleLiteral(fieldArgs["step"])
            val precisionNum = parseIntLiteral(fieldArgs["precision"])?.takeIf { it >= 0 }
            val maxLengthNum = parseIntLiteral(fieldArgs["maxLength"])?.takeIf { it >= 0 }
            val required = parseBooleanLiteral(fieldArgs["required"]) ?: false
            val secret = parseBooleanLiteral(fieldArgs["secret"]) ?: false
            val multiline = parseBooleanLiteral(fieldArgs["multiline"]) ?: false
            val fullWidth = parseBooleanLiteral(fieldArgs["fullWidth"]) ?: false
            val widgetHint = parseWidgetHint(fieldArgs["widgetHint"])
            val legacyKeys = parseStringArrayLiteral(fieldArgs["legacyKeys"])

            val visibleWhenExpr = visibleAnn?.let { buildConditionExpr(it) }
            val enabledWhenExpr = enabledAnn?.let { buildConditionExpr(it) }

            val trimmedType = param.rawType.trim()
            val isNullable = trimmedType.endsWith("?")
            val baseType = trimmedType.removeSuffix("?").trim()
            val defaultRef = "defaults.${param.name}"

            val commonProps = buildString {
                append("key = ${quoteString(key)}")
                if (label.isNotEmpty()) append(", label = ${quoteString(label)}")
                if (description != null) append(", description = ${quoteString(description)}")
                if (placeholder != null) append(", placeholder = ${quoteString(placeholder)}")
                if (group != null) append(", sectionId = ${quoteString(group)}")
                if (fullWidth) append(", fullWidth = true")
                if (required) append(", required = true")
                if (legacyKeys.isNotEmpty()) {
                    append(", legacyKeys = listOf(${legacyKeys.joinToString(", ") { quoteString(it) }})")
                }
                if (visibleWhenExpr != null) append(", visibleWhen = $visibleWhenExpr")
                if (enabledWhenExpr != null) append(", enabledWhen = $enabledWhenExpr")
            }

            when {
                baseType == "Boolean" || baseType == "kotlin.Boolean" -> {
                    val hint = widgetHint ?: "SchemaWidgetHint.SWITCH"
                    val defExpr = if (isNullable) "($defaultRef ?: false)" else defaultRef
                    val spec = "BooleanFieldSpec($commonProps, defaultValue = $defExpr, widgetHint = $hint)"
                    val toExpr = if (isNullable) "(instance.${param.name} ?: false)" else "instance.${param.name}"
                    val fromExpr = buildFromBooleanExpr(key, isNullable, defaultRef)
                    preparedProps.add(PreparedProp(param, key, spec, toExpr, fromExpr))
                }
                baseType == "Int" || baseType == "kotlin.Int" -> {
                    val defExpr = if (isNullable) "($defaultRef ?: 0)" else defaultRef
                    val extra = buildString {
                        if (minNum != null) append(", min = ${minNum.toInt()}")
                        if (maxNum != null) append(", max = ${maxNum.toInt()}")
                        if (stepNum != null) append(", step = ${stepNum.toInt()}")
                        if (unit != null) append(", unit = ${quoteString(unit)}")
                        if (widgetHint != null) append(", widgetHint = $widgetHint")
                    }
                    val spec = "IntFieldSpec($commonProps, defaultValue = $defExpr$extra)"
                    val toExpr = "instance.${param.name}"
                    val fromExpr = buildFromIntExpr(key, isNullable, defaultRef, minNum?.toInt(), maxNum?.toInt())
                    preparedProps.add(PreparedProp(param, key, spec, toExpr, fromExpr))
                }
                baseType == "Long" || baseType == "kotlin.Long" -> {
                    val defExpr = if (isNullable) "($defaultRef ?: 0L)" else defaultRef
                    val extra = buildString {
                        if (minNum != null) append(", min = ${minNum.toInt()}")
                        if (maxNum != null) append(", max = ${maxNum.toInt()}")
                        if (stepNum != null) append(", step = ${stepNum.toInt()}")
                        if (unit != null) append(", unit = ${quoteString(unit)}")
                        append(", widgetHint = ${widgetHint ?: "SchemaWidgetHint.NUMBER_FIELD"}")
                    }
                    val spec = "IntFieldSpec($commonProps, defaultValue = $defExpr.toInt(), isLong = true, defaultLongValue = $defExpr$extra)"
                    val toExpr = "instance.${param.name}"
                    val fromExpr = buildFromLongExpr(key, isNullable, defaultRef)
                    preparedProps.add(PreparedProp(param, key, spec, toExpr, fromExpr))
                }
                baseType == "Double" || baseType == "kotlin.Double" -> {
                    val defExpr = if (isNullable) "($defaultRef ?: 0.0)" else defaultRef
                    val extra = buildString {
                        if (minNum != null) append(", min = $minNum")
                        if (maxNum != null) append(", max = $maxNum")
                        if (stepNum != null) append(", step = $stepNum")
                        if (precisionNum != null) append(", precision = $precisionNum")
                        if (unit != null) append(", unit = ${quoteString(unit)}")
                        if (widgetHint != null) append(", widgetHint = $widgetHint")
                    }
                    val spec = "DoubleFieldSpec($commonProps, defaultValue = $defExpr$extra)"
                    val toExpr = "instance.${param.name}"
                    val fromExpr = buildFromDoubleExpr(key, isNullable, defaultRef, minNum, maxNum)
                    preparedProps.add(PreparedProp(param, key, spec, toExpr, fromExpr))
                }
                baseType == "Float" || baseType == "kotlin.Float" -> {
                    val defExpr = if (isNullable) "($defaultRef ?: 0f).toDouble()" else "$defaultRef.toDouble()"
                    val extra = buildString {
                        if (minNum != null) append(", min = $minNum")
                        if (maxNum != null) append(", max = $maxNum")
                        if (stepNum != null) append(", step = $stepNum")
                        if (precisionNum != null) append(", precision = $precisionNum")
                        if (unit != null) append(", unit = ${quoteString(unit)}")
                        if (widgetHint != null) append(", widgetHint = $widgetHint")
                    }
                    val spec = "DoubleFieldSpec($commonProps, defaultValue = $defExpr, isFloat = true$extra)"
                    val toExpr = "instance.${param.name}"
                    val fromExpr = buildFromFloatExpr(key, isNullable, defaultRef, minNum, maxNum)
                    preparedProps.add(PreparedProp(param, key, spec, toExpr, fromExpr))
                }
                baseType == "String" || baseType == "kotlin.String" -> {
                    val defExpr = if (isNullable) "($defaultRef ?: \"\")" else defaultRef
                    val extra = buildString {
                        if (maxLengthNum != null) append(", maxLength = $maxLengthNum")
                        if (multiline) append(", multiline = true")
                        if (secret) append(", secret = true")
                        if (widgetHint != null) append(", widgetHint = $widgetHint")
                    }
                    val spec = "StringFieldSpec($commonProps, defaultValue = $defExpr$extra)"
                    val toExpr = "instance.${param.name}"
                    val fromExpr = if (isNullable) {
                        "SchemaValueCodec.resolveRawValue(values, fieldsByKey[${quoteString(key)}]!!).let { (f, v) -> if (f) v?.toString() else $defaultRef }"
                    } else {
                        "SchemaValueCodec.resolveRawValue(values, fieldsByKey[${quoteString(key)}]!!).let { (f, v) -> if (f) (v?.toString() ?: \"\") else $defaultRef }"
                    }
                    preparedProps.add(PreparedProp(param, key, spec, toExpr, fromExpr))
                }
                baseType == "RgbaColor" || baseType == "com.nexustools.genericui.style.color.RgbaColor" -> {
                    usesRgbaColor = true
                    val defHexExpr = if (isNullable) {
                        "($defaultRef?.toHex(includeAlpha = true) ?: \"#000000\")"
                    } else {
                        "$defaultRef.toHex(includeAlpha = true)"
                    }
                    val hint = widgetHint ?: "SchemaWidgetHint.COLOR"
                    val spec = "ColorFieldSpec($commonProps, defaultValue = $defHexExpr, returnRgbaColorValue = true, widgetHint = $hint)"
                    val toExpr = "instance.${param.name}"
                    val fromExpr = if (isNullable) {
                        "SchemaValueCodec.resolveRawValue(values, fieldsByKey[${quoteString(key)}]!!).let { (f, v) -> if (!f) $defaultRef else when (v) { null -> null; is RgbaColor -> v; else -> RgbaColor.fromCss(v.toString()) ?: $defaultRef } }"
                    } else {
                        "SchemaValueCodec.resolveRawValue(values, fieldsByKey[${quoteString(key)}]!!).let { (f, v) -> if (!f) $defaultRef else when (v) { is RgbaColor -> v; is String -> RgbaColor.fromCss(v.trim()) ?: $defaultRef; else -> $defaultRef } }"
                    }
                    preparedProps.add(PreparedProp(param, key, spec, toExpr, fromExpr))
                }
                isStringListOrSetType(baseType) -> {
                    val isSet = baseType.startsWith("Set<") || baseType.startsWith("MutableSet<") ||
                        baseType.startsWith("kotlin.collections.Set<") || baseType.startsWith("kotlin.collections.MutableSet<")
                    val defListExpr = when {
                        isNullable && isSet -> "($defaultRef?.toList() ?: emptyList())"
                        isNullable -> "($defaultRef ?: emptyList())"
                        isSet -> "$defaultRef.toList()"
                        else -> defaultRef
                    }
                    val extra = if (widgetHint != null) ", widgetHint = $widgetHint" else ""
                    val spec = "OrderedMultiSelectFieldSpec($commonProps, defaultValue = $defListExpr$extra)"
                    val toExpr = if (isSet) {
                        if (isNullable) "instance.${param.name}?.toList()" else "instance.${param.name}.toList()"
                    } else {
                        "instance.${param.name}"
                    }
                    val fromListExpr = "SchemaValueCodec.coerceOrderedMultiSelectValue(fieldsByKey[${quoteString(key)}] as OrderedMultiSelectFieldSpec, SchemaValueCodec.resolveRawValue(values, fieldsByKey[${quoteString(key)}]!!).let { (f, v) -> if (f) v else $defListExpr })"
                    val fromExpr = when {
                        isNullable && isSet -> "SchemaValueCodec.resolveRawValue(values, fieldsByKey[${quoteString(key)}]!!).let { (f, v) -> if (!f) $defaultRef else if (v == null) null else $fromListExpr.toSet() }"
                        isNullable -> "SchemaValueCodec.resolveRawValue(values, fieldsByKey[${quoteString(key)}]!!).let { (f, v) -> if (!f) $defaultRef else if (v == null) null else $fromListExpr }"
                        isSet -> "$fromListExpr.toSet()"
                        else -> fromListExpr
                    }
                    preparedProps.add(PreparedProp(param, key, spec, toExpr, fromExpr))
                }
                else -> {
                    val nestedSchema = resolveNestedSchemaClass(baseType, parsedFile, schemaClasses)
                    if (nestedSchema != null) {
                        val descFqn = if (nestedSchema.packageName.isEmpty()) {
                            "${nestedSchema.simpleName}SchemaDescriptor"
                        } else {
                            "${nestedSchema.packageName}.${nestedSchema.simpleName}SchemaDescriptor"
                        }
                        val spec = "SchemaGroupSpec(key = ${quoteString(key)}${
                            if (label.isNotEmpty()) ", label = ${quoteString(label)}" else ""
                        }${
                            if (description != null) ", description = ${quoteString(description)}" else ""
                        }, children = $descFqn.schema.nodes${
                            if (group != null) ", sectionId = ${quoteString(group)}" else ""
                        }${
                            if (visibleWhenExpr != null) ", visibleWhen = $visibleWhenExpr" else ""
                        }${
                            if (enabledWhenExpr != null) ", enabledWhen = $enabledWhenExpr" else ""
                        })"
                        val toExpr = if (isNullable) {
                            "instance.${param.name}?.let { $descFqn.toValueMap(it) }"
                        } else {
                            "$descFqn.toValueMap(instance.${param.name})"
                        }
                        val fromExpr = if (isNullable) {
                            "SchemaValueCodec.resolveRawValue(values, nodesByKey[${quoteString(key)}]!!).let { (f, v) -> if (!f) $defaultRef else if (v == null) null else $descFqn.fromValueMap(SchemaValueCodec.coerceToMap(v)) }"
                        } else {
                            "SchemaValueCodec.resolveRawValue(values, nodesByKey[${quoteString(key)}]!!).let { (f, v) -> if (!f) $defaultRef else $descFqn.fromValueMap(SchemaValueCodec.coerceToMap(v)) }"
                        }
                        preparedProps.add(PreparedProp(param, key, spec, toExpr, fromExpr))
                    } else {
                        val resolvedEnumFqn = resolveSymbolFqn(baseType, parsedFile, enums)
                        val defOptExpr = if (isNullable) "($defaultRef?.name)" else "$defaultRef.name"
                        val extra = if (widgetHint != null) ", widgetHint = $widgetHint" else ""
                        val spec = "SelectFieldSpec($commonProps, defaultValue = $defOptExpr, staticOptions = $resolvedEnumFqn.entries.map { SchemaOption(it.name, it.name.replace('_', ' ')) }$extra)"
                        val toExpr = if (isNullable) "instance.${param.name}?.name" else "instance.${param.name}.name"
                        val fromExpr = if (isNullable) {
                            "SchemaValueCodec.resolveRawValue(values, fieldsByKey[${quoteString(key)}]!!).let { (f, v) -> if (!f) $defaultRef else if (v == null) null else $resolvedEnumFqn.entries.firstOrNull { e -> e.name == v.toString() } ?: $defaultRef }"
                        } else {
                            "SchemaValueCodec.resolveRawValue(values, fieldsByKey[${quoteString(key)}]!!).let { (f, v) -> if (!f || v == null) $defaultRef else $resolvedEnumFqn.entries.firstOrNull { e -> e.name == v.toString() } ?: $defaultRef }"
                        }
                        preparedProps.add(PreparedProp(param, key, spec, toExpr, fromExpr))
                    }
                }
            }
        }

        return buildString {
            if (schemaClass.packageName.isNotEmpty()) {
                appendLine("package ${schemaClass.packageName}")
                appendLine()
            }
            appendLine("import com.nexustools.genericui.component.system.PreferenceElement")
            appendLine("import com.nexustools.genericui.component.system.PreferenceStore")
            appendLine("import com.nexustools.genericui.ext.schema.*")
            if (usesRgbaColor) {
                appendLine("import com.nexustools.genericui.style.color.RgbaColor")
            }
            for ((simple, fqn) in parsedFile.explicitImports) {
                if (!fqn.startsWith("com.nexustools.genericui.ext.schema.") && simple != descriptorName) {
                    appendLine("import $fqn")
                }
            }
            for (wild in parsedFile.wildcardImports) {
                if (wild != "com.nexustools.genericui.ext.schema") {
                    appendLine("import $wild.*")
                }
            }
            appendLine()
            appendLine("object $descriptorName : SchemaDescriptor<${schemaClass.simpleName}> {")
            if (allParamsHaveDefaults) {
                appendLine("    private val defaults: ${schemaClass.simpleName} = ${schemaClass.simpleName}()")
            } else {
                appendLine("    private val defaults: ${schemaClass.simpleName} = ${buildFallbackConstructorCall(schemaClass, schemaClasses, enums, parsedFile)}")
            }
            appendLine()
            appendLine("    private val nodesList: List<SchemaNode> = listOf(")
            preparedProps.forEachIndexed { idx, p ->
                val comma = if (idx < preparedProps.lastIndex) "," else ""
                appendLine("        ${p.fieldSpecCode}$comma")
            }
            appendLine("    )")
            appendLine()
            appendLine("    private val sectionsList: List<SchemaSectionSpec> = listOf(")
            val secList = sectionsOrder.toList()
            secList.forEachIndexed { idx, sec ->
                val comma = if (idx < secList.lastIndex) "," else ""
                val secTitle = sec.replace('_', ' ').replace('-', ' ')
                    .split(' ').filter { it.isNotEmpty() }
                    .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
                appendLine("        SchemaSectionSpec(id = ${quoteString(sec)}, title = ${quoteString(secTitle)})$comma")
            }
            appendLine("    )")
            appendLine()
            appendLine("    override val schema: ObjectSchema = ObjectSchema(")
            appendLine("        id = ${quoteString(schemaId)},")
            appendLine("        title = ${quoteString(schemaTitle)},")
            appendLine("        description = ${schemaDescription?.let { quoteString(it) } ?: "null"},")
            appendLine("        nodes = nodesList,")
            appendLine("        sections = sectionsList")
            appendLine("    )")
            appendLine()
            appendLine("    private val fieldsByKey: Map<String, SchemaFieldSpec> get() = schema.fieldsByKey")
            appendLine("    private val nodesByKey: Map<String, SchemaNode> get() = schema.nodesByKey")
            appendLine()
            appendLine("    override fun defaultInstance(): ${schemaClass.simpleName} = defaults")
            appendLine()
            appendLine("    override fun toValueMap(instance: ${schemaClass.simpleName}): Map<String, Any?> = linkedMapOf(")
            preparedProps.forEachIndexed { idx, p ->
                val comma = if (idx < preparedProps.lastIndex) "," else ""
                appendLine("        ${quoteString(p.key)} to ${p.toMapExpr}$comma")
            }
            appendLine("    )")
            appendLine()
            appendLine("    override fun fromValueMap(values: Map<String, Any?>): ${schemaClass.simpleName} = ${schemaClass.simpleName}(")
            preparedProps.forEachIndexed { idx, p ->
                val comma = if (idx < preparedProps.lastIndex) "," else ""
                appendLine("        ${p.param.name} = ${p.fromMapExpr}$comma")
            }
            appendLine("    )")
            appendLine()
            appendLine("    override fun toPreferenceElement(instance: ${schemaClass.simpleName}): PreferenceElement =")
            appendLine("        SchemaPreferenceCodec.toPreferenceElement(schema, toValueMap(instance))")
            appendLine()
            appendLine("    override fun fromPreferenceElement(element: PreferenceElement): ${schemaClass.simpleName} =")
            appendLine("        fromValueMap(SchemaPreferenceCodec.fromPreferenceElement(schema, element))")
            appendLine()
            appendLine("    override fun toJson(instance: ${schemaClass.simpleName}): String =")
            appendLine("        SchemaJsonCodec.encodeValues(schema, toValueMap(instance))")
            appendLine()
            appendLine("    override fun fromJson(json: String): ${schemaClass.simpleName} =")
            appendLine("        fromValueMap(SchemaJsonCodec.decodeValues(schema, json))")
            appendLine()
            appendLine("    override fun saveTo(store: PreferenceStore, keyOrPrefix: String, instance: ${schemaClass.simpleName}) {")
            appendLine("        SchemaPreferenceCodec.saveTo(store, keyOrPrefix, schema, toValueMap(instance))")
            appendLine("    }")
            appendLine()
            appendLine("    override fun loadFrom(store: PreferenceStore, keyOrPrefix: String): ${schemaClass.simpleName} =")
            appendLine("        fromValueMap(SchemaPreferenceCodec.loadFrom(store, keyOrPrefix, schema))")
            appendLine("}")
        }
    }

    private fun buildFallbackConstructorCall(
        cls: ParsedSchemaClass,
        schemaClasses: Map<String, ParsedSchemaClass>,
        enums: Map<String, ParsedEnum>,
        parsedFile: ParsedKotlinFile
    ): String {
        val args = cls.params.filter { !it.hasDefaultValue }.map { param ->
            val trimmedType = param.rawType.trim()
            val isNullable = trimmedType.endsWith("?")
            val baseType = trimmedType.removeSuffix("?").trim()
            val literal = if (isNullable) {
                "null"
            } else {
                when {
                    baseType == "Boolean" || baseType == "kotlin.Boolean" -> "false"
                    baseType == "Int" || baseType == "kotlin.Int" -> "0"
                    baseType == "Long" || baseType == "kotlin.Long" -> "0L"
                    baseType == "Double" || baseType == "kotlin.Double" -> "0.0"
                    baseType == "Float" || baseType == "kotlin.Float" -> "0f"
                    baseType == "String" || baseType == "kotlin.String" -> "\"\""
                    baseType == "RgbaColor" || baseType == "com.nexustools.genericui.style.color.RgbaColor" -> "RgbaColor.BLACK"
                    baseType.startsWith("Set<") || baseType.startsWith("MutableSet<") -> "emptySet()"
                    baseType.startsWith("List<") || baseType.startsWith("MutableList<") -> "emptyList()"
                    else -> {
                        val nested = resolveNestedSchemaClass(baseType, parsedFile, schemaClasses)
                        if (nested != null) {
                            val descFqn = if (nested.packageName.isEmpty()) {
                                "${nested.simpleName}SchemaDescriptor"
                            } else {
                                "${nested.packageName}.${nested.simpleName}SchemaDescriptor"
                            }
                            "$descFqn.defaultInstance()"
                        } else {
                            val enumFqn = resolveSymbolFqn(baseType, parsedFile, enums)
                            "$enumFqn.entries.first()"
                        }
                    }
                }
            }
            "${param.name} = $literal"
        }
        return "${cls.simpleName}(${args.joinToString(", ")})"
    }

    private fun buildFromBooleanExpr(key: String, isNullable: Boolean, defaultRef: String): String {
        val k = quoteString(key)
        return if (isNullable) {
            "SchemaValueCodec.resolveRawValue(values, fieldsByKey[$k]!!).let { (f, v) -> if (!f) $defaultRef else if (v == null) null else SchemaValueCodec.coerceBoolean(v, $defaultRef ?: false) }"
        } else {
            "SchemaValueCodec.resolveRawValue(values, fieldsByKey[$k]!!).let { (f, v) -> if (!f) $defaultRef else SchemaValueCodec.coerceBoolean(v, $defaultRef) }"
        }
    }

    private fun buildFromIntExpr(key: String, isNullable: Boolean, defaultRef: String, minVal: Int?, maxVal: Int?): String {
        val k = quoteString(key)
        val clampSuffix = when {
            minVal != null && maxVal != null -> ".coerceIn($minVal, $maxVal)"
            minVal != null -> ".coerceAtLeast($minVal)"
            maxVal != null -> ".coerceAtMost($maxVal)"
            else -> ""
        }
        return if (isNullable) {
            "SchemaValueCodec.resolveRawValue(values, fieldsByKey[$k]!!).let { (f, v) -> if (!f) $defaultRef else if (v == null) null else ((v as? Number)?.toInt() ?: v.toString().trim().toIntOrNull() ?: $defaultRef)$clampSuffix }"
        } else {
            "SchemaValueCodec.resolveRawValue(values, fieldsByKey[$k]!!).let { (f, v) -> if (!f) $defaultRef else ((v as? Number)?.toInt() ?: v?.toString()?.trim()?.toIntOrNull() ?: $defaultRef)$clampSuffix }"
        }
    }

    private fun buildFromLongExpr(key: String, isNullable: Boolean, defaultRef: String): String {
        val k = quoteString(key)
        return if (isNullable) {
            "SchemaValueCodec.resolveRawValue(values, fieldsByKey[$k]!!).let { (f, v) -> if (!f) $defaultRef else if (v == null) null else ((v as? Number)?.toLong() ?: v.toString().trim().toLongOrNull() ?: $defaultRef) }"
        } else {
            "SchemaValueCodec.resolveRawValue(values, fieldsByKey[$k]!!).let { (f, v) -> if (!f) $defaultRef else ((v as? Number)?.toLong() ?: v?.toString()?.trim()?.toLongOrNull() ?: $defaultRef) }"
        }
    }

    private fun buildFromDoubleExpr(key: String, isNullable: Boolean, defaultRef: String, minVal: Double?, maxVal: Double?): String {
        val k = quoteString(key)
        val clampSuffix = when {
            minVal != null && maxVal != null -> ".coerceIn($minVal, $maxVal)"
            minVal != null -> ".coerceAtLeast($minVal)"
            maxVal != null -> ".coerceAtMost($maxVal)"
            else -> ""
        }
        return if (isNullable) {
            "SchemaValueCodec.resolveRawValue(values, fieldsByKey[$k]!!).let { (f, v) -> if (!f) $defaultRef else if (v == null) null else ((v as? Number)?.toDouble() ?: v.toString().trim().toDoubleOrNull() ?: $defaultRef)$clampSuffix }"
        } else {
            "SchemaValueCodec.resolveRawValue(values, fieldsByKey[$k]!!).let { (f, v) -> if (!f) $defaultRef else ((v as? Number)?.toDouble() ?: v?.toString()?.trim()?.toDoubleOrNull() ?: $defaultRef)$clampSuffix }"
        }
    }

    private fun buildFromFloatExpr(key: String, isNullable: Boolean, defaultRef: String, minVal: Double?, maxVal: Double?): String {
        val k = quoteString(key)
        val clampSuffix = when {
            minVal != null && maxVal != null -> ".coerceIn(${minVal}f, ${maxVal}f)"
            minVal != null -> ".coerceAtLeast(${minVal}f)"
            maxVal != null -> ".coerceAtMost(${maxVal}f)"
            else -> ""
        }
        return if (isNullable) {
            "SchemaValueCodec.resolveRawValue(values, fieldsByKey[$k]!!).let { (f, v) -> if (!f) $defaultRef else if (v == null) null else ((v as? Number)?.toFloat() ?: v.toString().trim().toFloatOrNull() ?: $defaultRef)$clampSuffix }"
        } else {
            "SchemaValueCodec.resolveRawValue(values, fieldsByKey[$k]!!).let { (f, v) -> if (!f) $defaultRef else ((v as? Number)?.toFloat() ?: v?.toString()?.trim()?.toFloatOrNull() ?: $defaultRef)$clampSuffix }"
        }
    }

    private fun buildConditionExpr(ann: ParsedAnnotation): String? {
        val args = parseNamedArgs(ann.rawArgs, listOf("field", "equalsValue", "inValues", "negate"))
        val field = unquoteString(args["field"])?.takeIf { it.isNotEmpty() } ?: return null
        val equalsValue = unquoteString(args["equalsValue"])
        val inValues = parseStringArrayLiteral(args["inValues"])
        val negate = parseBooleanLiteral(args["negate"]) ?: false

        val base = when {
            !equalsValue.isNullOrEmpty() ->
                "SchemaCondition.Equals(${quoteString(field)}, ${quoteString(equalsValue)})"
            inValues.isNotEmpty() ->
                "SchemaCondition.In(${quoteString(field)}, listOf(${inValues.joinToString(", ") { quoteString(it) }}))"
            else ->
                "SchemaCondition.IsTrue(${quoteString(field)})"
        }
        return if (negate) "SchemaCondition.Not($base)" else base
    }

    private fun resolveNestedSchemaClass(
        typeName: String,
        file: ParsedKotlinFile,
        schemaClasses: Map<String, ParsedSchemaClass>
    ): ParsedSchemaClass? {
        schemaClasses[typeName]?.let { return it }
        file.explicitImports[typeName]?.let { fqn ->
            schemaClasses[fqn]?.let { return it }
        }
        if (file.packageName.isNotEmpty()) {
            schemaClasses["${file.packageName}.$typeName"]?.let { return it }
        }
        return null
    }

    private fun resolveSymbolFqn(
        typeName: String,
        file: ParsedKotlinFile,
        enums: Map<String, ParsedEnum>
    ): String {
        if ('.' in typeName) return typeName
        file.explicitImports[typeName]?.let { return it }
        if (file.packageName.isNotEmpty()) {
            val samePkg = "${file.packageName}.$typeName"
            if (samePkg in enums) return samePkg
        }
        enums[typeName]?.let { return it.qualifiedName }
        return typeName
    }

    private fun isStringListOrSetType(baseType: String): Boolean {
        val normalized = baseType.replace(" ", "")
        return normalized == "List<String>" ||
            normalized == "MutableList<String>" ||
            normalized == "kotlin.collections.List<String>" ||
            normalized == "kotlin.collections.MutableList<String>" ||
            normalized == "Set<String>" ||
            normalized == "MutableSet<String>" ||
            normalized == "kotlin.collections.Set<String>" ||
            normalized == "kotlin.collections.MutableSet<String>"
    }

    private fun parseKotlinSourceFile(file: File, source: String): ParsedKotlinFile {
        val code = stripCommentsPreservingStrings(source)
        val pkgMatch = Regex("""(?m)^\s*package\s+([A-Za-z0-9_.]+)""").find(code)
        val pkgName = pkgMatch?.groupValues?.get(1)?.trim().orEmpty()

        val explicitImports = LinkedHashMap<String, String>()
        val wildcardImports = mutableListOf<String>()
        Regex("""(?m)^\s*import\s+([A-Za-z0-9_.*]+)(?:\s+as\s+[A-Za-z0-9_]+)?""").findAll(code).forEach { m ->
            val imp = m.groupValues[1].trim()
            if (imp.endsWith(".*")) {
                wildcardImports.add(imp.removeSuffix(".*"))
            } else {
                val simple = imp.substringAfterLast('.')
                explicitImports[simple] = imp
            }
        }

        val discoveredEnums = parseEnums(code, pkgName)
        val discoveredSchemas = parseSchemaClasses(code, pkgName)
        return ParsedKotlinFile(
            packageName = pkgName,
            explicitImports = explicitImports,
            wildcardImports = wildcardImports,
            enums = discoveredEnums,
            schemaClasses = discoveredSchemas
        )
    }

    private fun parseEnums(code: String, pkgName: String): List<ParsedEnum> {
        val out = mutableListOf<ParsedEnum>()
        val regex = Regex("""\benum\s+class\s+([A-Za-z_][A-Za-z0-9_]*)\b""")
        for (match in regex.findAll(code)) {
            val name = match.groupValues[1]
            val braceIdx = findNextCharOutsideParens(code, match.range.last + 1, '{') ?: continue
            val closeBraceIdx = findMatchingDelimiter(code, braceIdx, '{', '}') ?: continue
            val body = code.substring(braceIdx + 1, closeBraceIdx)
            val headerPart = splitTopLevelByChar(body, ';').firstOrNull().orEmpty()
            val entries = splitTopLevelByChar(headerPart, ',')
                .mapNotNull { entry ->
                    val stripped = entry.trim()
                    val identMatch = Regex("""(?:@[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?\s*)*([A-Za-z_][A-Za-z0-9_]*)""").find(stripped)
                    identMatch?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
                }
            val qName = if (pkgName.isEmpty()) name else "$pkgName.$name"
            out.add(ParsedEnum(pkgName, name, qName, entries))
        }
        return out
    }

    private fun parseSchemaClasses(code: String, pkgName: String): List<ParsedSchemaClass> {
        val out = mutableListOf<ParsedSchemaClass>()
        var idx = 0
        val n = code.length
        while (idx < n) {
            if (code[idx] == '@') {
                val annStart = idx
                val anns = mutableListOf<ParsedAnnotation>()
                var cursor = idx
                while (cursor < n) {
                    cursor = skipWhitespace(code, cursor)
                    if (cursor < n && code[cursor] == '@') {
                        val parsedAnn = readAnnotation(code, cursor) ?: break
                        anns.add(parsedAnn.first)
                        cursor = parsedAnn.second
                    } else {
                        break
                    }
                }
                val schemaAnn = anns.firstOrNull {
                    isAnnotationNamed(it, "SchemaData", "com.nexustools.genericui.ext.schema.SchemaData")
                }
                if (schemaAnn != null) {
                    val headerStart = skipWhitespace(code, cursor)
                    val classDecl = parseClassHeaderAt(code, headerStart)
                    if (classDecl != null) {
                        val (simpleName, rawParamList, endCursor) = classDecl
                        val params = parseConstructorParameters(rawParamList)
                        val qName = if (pkgName.isEmpty()) simpleName else "$pkgName.$simpleName"
                        out.add(
                            ParsedSchemaClass(
                                packageName = pkgName,
                                simpleName = simpleName,
                                qualifiedName = qName,
                                schemaAnnotation = schemaAnn,
                                params = params
                            )
                        )
                        idx = endCursor
                        continue
                    }
                }
                idx = if (cursor > annStart) cursor else annStart + 1
            } else if (isStringLiteralStart(code, idx)) {
                idx = skipStringLiteral(code, idx)
            } else {
                idx++
            }
        }
        return out
    }

    private fun parseClassHeaderAt(code: String, start: Int): Triple<String, String, Int>? {
        val classRegex = Regex("""^(?:(?:public|internal|private|data|open|final|abstract|sealed|value)\s+)*class\s+([A-Za-z_][A-Za-z0-9_]*)\b""")
        val slice = code.substring(start)
        val match = classRegex.find(slice) ?: return null
        val simpleName = match.groupValues[1]
        var cursor = start + match.range.last + 1
        cursor = skipWhitespace(code, cursor)
        // Optional type parameters <...>
        if (cursor < code.length && code[cursor] == '<') {
            val closeAngle = findMatchingDelimiter(code, cursor, '<', '>') ?: return null
            cursor = skipWhitespace(code, closeAngle + 1)
        }
        // Optional primary constructor modifiers / @Inject constructor
        val ctorKeyword = Regex("""^(?:(?:public|internal|private)\s+)?constructor\b""").find(code.substring(cursor))
        if (ctorKeyword != null) {
            cursor = skipWhitespace(code, cursor + ctorKeyword.range.last + 1)
        }
        if (cursor >= code.length || code[cursor] != '(') {
            return Triple(simpleName, "", cursor)
        }
        val closeParen = findMatchingDelimiter(code, cursor, '(', ')') ?: return null
        val rawParams = code.substring(cursor + 1, closeParen)
        return Triple(simpleName, rawParams, closeParen + 1)
    }

    private fun parseConstructorParameters(rawParams: String): List<ParsedParam> {
        if (rawParams.isBlank()) return emptyList()
        val rawItems = splitTopLevelByChar(rawParams, ',')
        val result = mutableListOf<ParsedParam>()
        for (item in rawItems) {
            val trimmed = item.trim()
            if (trimmed.isEmpty()) continue
            val anns = mutableListOf<ParsedAnnotation>()
            var cursor = 0
            while (cursor < trimmed.length) {
                cursor = skipWhitespace(trimmed, cursor)
                if (cursor < trimmed.length && trimmed[cursor] == '@') {
                    val parsed = readAnnotation(trimmed, cursor) ?: break
                    anns.add(parsed.first)
                    cursor = parsed.second
                } else {
                    break
                }
            }
            var decl = trimmed.substring(cursor).trim()
            decl = decl.replace(Regex("""^(?:override|private|protected|internal|public|open|final|vararg)\s+"""), "")
            while (true) {
                val next = decl.replace(Regex("""^(?:override|private|protected|internal|public|open|final|vararg)\s+"""), "")
                if (next == decl) break
                decl = next
            }
            if (!decl.startsWith("val ") && !decl.startsWith("var ")) {
                continue
            }
            decl = decl.removePrefix("val ").removePrefix("var ").trim()
            val colonIdx = findTopLevelChar(decl, ':') ?: continue
            val propName = decl.substring(0, colonIdx).trim()
            val afterColon = decl.substring(colonIdx + 1).trim()
            val eqIdx = findTopLevelChar(afterColon, '=')
            val typeStr = if (eqIdx != null) afterColon.substring(0, eqIdx).trim() else afterColon.trim()
            if (propName.isNotEmpty() && typeStr.isNotEmpty()) {
                result.add(
                    ParsedParam(
                        name = propName,
                        rawType = typeStr,
                        hasDefaultValue = (eqIdx != null),
                        annotations = anns
                    )
                )
            }
        }
        return result
    }

    private fun readAnnotation(code: String, atIndex: Int): Pair<ParsedAnnotation, Int>? {
        if (atIndex >= code.length || code[atIndex] != '@') return null
        var cursor = atIndex + 1
        // Optional use-site target e.g. @param:SchemaField or @property:SchemaField
        val rest = code.substring(cursor)
        val useSiteMatch = Regex("""^(?:field|property|get|set|receiver|param|setparam|delegate)\s*:""").find(rest)
        if (useSiteMatch != null) {
            cursor += useSiteMatch.range.last + 1
        }
        cursor = skipWhitespace(code, cursor)
        val nameMatch = Regex("""^[A-Za-z_][A-Za-z0-9_.]*""").find(code.substring(cursor)) ?: return null
        val name = nameMatch.value
        cursor += nameMatch.value.length
        cursor = skipWhitespace(code, cursor)
        var rawArgs: String? = null
        if (cursor < code.length && code[cursor] == '(') {
            val closeIdx = findMatchingDelimiter(code, cursor, '(', ')') ?: return null
            rawArgs = code.substring(cursor + 1, closeIdx)
            cursor = closeIdx + 1
        }
        return ParsedAnnotation(name, rawArgs) to cursor
    }

    private fun isAnnotationNamed(ann: ParsedAnnotation, simple: String, qualified: String): Boolean =
        ann.name == simple || ann.name == qualified

    private fun parseNamedArgs(rawArgs: String?, positionalNames: List<String>): Map<String, String> {
        if (rawArgs.isNullOrBlank()) return emptyMap()
        val items = splitTopLevelByChar(rawArgs, ',')
        val result = LinkedHashMap<String, String>()
        var posIdx = 0
        for (item in items) {
            val trimmed = item.trim()
            if (trimmed.isEmpty()) continue
            val eqIdx = findTopLevelChar(trimmed, '=')
            if (eqIdx != null) {
                val key = trimmed.substring(0, eqIdx).trim()
                val valStr = trimmed.substring(eqIdx + 1).trim()
                result[key] = valStr
            } else {
                val targetName = positionalNames.getOrNull(posIdx)
                if (targetName != null) {
                    result[targetName] = trimmed
                }
                posIdx++
            }
        }
        return result
    }

    private fun unquoteString(raw: String?): String? {
        if (raw == null) return null
        val t = raw.trim()
        if (t.startsWith("\"\"\"") && t.endsWith("\"\"\"") && t.length >= 6) {
            return t.substring(3, t.length - 3)
        }
        if (t.startsWith("\"") && t.endsWith("\"") && t.length >= 2) {
            val inner = t.substring(1, t.length - 1)
            val sb = StringBuilder(inner.length)
            var i = 0
            while (i < inner.length) {
                val c = inner[i]
                if (c == '\\' && i + 1 < inner.length) {
                    val next = inner[i + 1]
                    when (next) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        '\\' -> sb.append('\\')
                        '"' -> sb.append('"')
                        '$' -> sb.append('$')
                        else -> sb.append(next)
                    }
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }
        return null
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

    private fun parseDoubleLiteral(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .replace("_", "")
            .removeSuffix("f").removeSuffix("F")
        if (cleaned == "Double.NaN" || cleaned == "kotlin.Double.NaN") return null
        val parsed = cleaned.toDoubleOrNull() ?: return null
        return if (parsed.isNaN()) null else parsed
    }

    private fun parseIntLiteral(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace("_", "")
        return cleaned.toIntOrNull()
    }

    private fun parseBooleanLiteral(raw: String?): Boolean? = when (raw?.trim()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private fun parseWidgetHint(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val entry = raw.trim().substringAfterLast('.')
        return "SchemaWidgetHint.$entry"
    }

    private fun parseStringArrayLiteral(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val trimmed = raw.trim()
        val inner = when {
            trimmed.startsWith("[") && trimmed.endsWith("]") -> trimmed.substring(1, trimmed.length - 1)
            trimmed.startsWith("arrayOf(") && trimmed.endsWith(")") -> trimmed.substring(8, trimmed.length - 1)
            else -> return emptyList()
        }
        return splitTopLevelByChar(inner, ',')
            .mapNotNull { unquoteString(it) }
    }

    private fun stripCommentsPreservingStrings(source: String): String {
        val sb = StringBuilder(source.length)
        var i = 0
        val n = source.length
        while (i < n) {
            if (isRawStringStart(source, i)) {
                val end = skipRawString(source, i)
                sb.append(source, i, end)
                i = end
            } else if (source[i] == '"') {
                val end = skipQuotedString(source, i)
                sb.append(source, i, end)
                i = end
            } else if (source[i] == '\'') {
                val end = skipCharLiteral(source, i)
                sb.append(source, i, end)
                i = end
            } else if (i + 1 < n && source[i] == '/' && source[i + 1] == '/') {
                i += 2
                while (i < n && source[i] != '\n') i++
            } else if (i + 1 < n && source[i] == '/' && source[i + 1] == '*') {
                var depth = 1
                i += 2
                while (i + 1 < n && depth > 0) {
                    if (source[i] == '/' && source[i + 1] == '*') {
                        depth++
                        i += 2
                    } else if (source[i] == '*' && source[i + 1] == '/') {
                        depth--
                        i += 2
                    } else {
                        if (source[i] == '\n') sb.append('\n')
                        i++
                    }
                }
            } else {
                sb.append(source[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun splitTopLevelByChar(text: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        var parens = 0
        var brackets = 0
        var braces = 0
        var angles = 0
        var start = 0
        var i = 0
        val n = text.length
        while (i < n) {
            if (isRawStringStart(text, i)) {
                i = skipRawString(text, i)
                continue
            }
            if (text[i] == '"') {
                i = skipQuotedString(text, i)
                continue
            }
            if (text[i] == '\'') {
                i = skipCharLiteral(text, i)
                continue
            }
            val ch = text[i]
            when (ch) {
                '(' -> parens++
                ')' -> if (parens > 0) parens--
                '[' -> brackets++
                ']' -> if (brackets > 0) brackets--
                '{' -> braces++
                '}' -> if (braces > 0) braces--
                '<' -> if (parens == 0 && brackets == 0 && braces == 0) angles++
                '>' -> if (angles > 0) angles--
                delimiter -> if (parens == 0 && brackets == 0 && braces == 0 && angles == 0) {
                    parts.add(text.substring(start, i))
                    start = i + 1
                }
            }
            i++
        }
        parts.add(text.substring(start))
        return parts
    }

    private fun findTopLevelChar(text: String, target: Char): Int? {
        var parens = 0
        var brackets = 0
        var braces = 0
        var angles = 0
        var i = 0
        val n = text.length
        while (i < n) {
            if (isRawStringStart(text, i)) {
                i = skipRawString(text, i)
                continue
            }
            if (text[i] == '"') {
                i = skipQuotedString(text, i)
                continue
            }
            if (text[i] == '\'') {
                i = skipCharLiteral(text, i)
                continue
            }
            val ch = text[i]
            if (ch == target && parens == 0 && brackets == 0 && braces == 0 && angles == 0) {
                return i
            }
            when (ch) {
                '(' -> parens++
                ')' -> if (parens > 0) parens--
                '[' -> brackets++
                ']' -> if (brackets > 0) brackets--
                '{' -> braces++
                '}' -> if (braces > 0) braces--
                '<' -> if (parens == 0 && brackets == 0 && braces == 0) angles++
                '>' -> if (angles > 0) angles--
            }
            i++
        }
        return null
    }

    private fun findNextCharOutsideParens(text: String, start: Int, target: Char): Int? {
        var parens = 0
        var i = start
        val n = text.length
        while (i < n) {
            if (isRawStringStart(text, i)) {
                i = skipRawString(text, i)
                continue
            }
            if (text[i] == '"') {
                i = skipQuotedString(text, i)
                continue
            }
            val ch = text[i]
            if (ch == target && parens == 0) return i
            when (ch) {
                '(' -> parens++
                ')' -> if (parens > 0) parens--
            }
            i++
        }
        return null
    }

    private fun findMatchingDelimiter(text: String, openIdx: Int, open: Char, close: Char): Int? {
        var depth = 0
        var i = openIdx
        val n = text.length
        while (i < n) {
            if (isRawStringStart(text, i)) {
                i = skipRawString(text, i)
                continue
            }
            if (text[i] == '"') {
                i = skipQuotedString(text, i)
                continue
            }
            if (text[i] == '\'') {
                i = skipCharLiteral(text, i)
                continue
            }
            val ch = text[i]
            if (ch == open) depth++
            else if (ch == close) {
                depth--
                if (depth == 0) return i
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
        text[idx] == '"' || text[idx] == '\''

    private fun skipStringLiteral(text: String, idx: Int): Int = when {
        isRawStringStart(text, idx) -> skipRawString(text, idx)
        text[idx] == '"' -> skipQuotedString(text, idx)
        text[idx] == '\'' -> skipCharLiteral(text, idx)
        else -> idx + 1
    }

    private fun isRawStringStart(text: String, idx: Int): Boolean =
        idx + 2 < text.length && text[idx] == '"' && text[idx + 1] == '"' && text[idx + 2] == '"'

    private fun skipRawString(text: String, idx: Int): Int {
        var i = idx + 3
        while (i + 2 < text.length) {
            if (text[i] == '"' && text[i + 1] == '"' && text[i + 2] == '"') {
                return i + 3
            }
            i++
        }
        return text.length
    }

    private fun skipQuotedString(text: String, idx: Int): Int {
        var i = idx + 1
        while (i < text.length) {
            if (text[i] == '\\') {
                i += 2
            } else if (text[i] == '"') {
                return i + 1
            } else {
                i++
            }
        }
        return text.length
    }

    private fun skipCharLiteral(text: String, idx: Int): Int {
        var i = idx + 1
        while (i < text.length) {
            if (text[i] == '\\') {
                i += 2
            } else if (text[i] == '\'') {
                return i + 1
            } else {
                i++
            }
        }
        return text.length
    }
}
