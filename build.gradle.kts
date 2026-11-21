import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

allprojects {
    val parentSuffix = parent?.path?.trimStart(':')?.replace(':', '.') ?: ""
    group = if (parentSuffix.isEmpty()) {
        "com.nexustools.genericui"
    } else {
        "com.nexustools.genericui.$parentSuffix"
    }
    version = "0.1.0-SNAPSHOT"

    tasks.withType(org.gradle.api.tasks.testing.AbstractTestTask::class.java).configureEach {
        timeout.set(java.time.Duration.ofSeconds(120))
    }
}

fun addFriendCompilation(
    consumer: KotlinCompilation<*>,
    friendProj: Project,
    friendComp: KotlinCompilation<*>
) {
    val friendOutputs = friendComp.output.classesDirs
    val friendTargetName = friendComp.target.name
    val isJvmOrAndroid = friendTargetName == "jvm" ||
        friendTargetName == "android" ||
        friendTargetName.isEmpty()
    val jarTaskNames = if (isJvmOrAndroid) {
        setOf(
            "jvmJar",
            "jar",
            "bundleAndroidMainClassesToCompileJar",
            "bundleAndroidMainClassesToRuntimeJar"
        )
    } else {
        emptySet()
    }
    val friendJarOutputs = friendProj.tasks.matching { it.name in jarTaskNames }
    consumer.compileTaskProvider.configure {
        when (this) {
            is BaseKotlinCompile -> {
                friendPaths.from(friendOutputs)
                if (isJvmOrAndroid) {
                    friendPaths.from(friendJarOutputs)
                }
            }
            is KotlinNativeCompile -> {
                val friendModuleCol = javaClass.methods
                    .firstOrNull { it.name.startsWith("getFriendModule") && it.parameterCount == 0 }
                    ?.invoke(this) as? org.gradle.api.file.ConfigurableFileCollection
                friendModuleCol?.from(friendOutputs)
            }
        }
    }
}

fun collectDirectProjectDependencies(project: Project, commonOnly: Boolean): Set<Project> {
    val result = linkedSetOf<Project>()
    for (config in project.configurations.toList()) {
        if (commonOnly) {
            val name = config.name
            val isCommonConfig = name.startsWith("commonMain") ||
                name == "api" ||
                name == "implementation" ||
                name == "compileOnly"
            if (!isCommonConfig) continue
        }
        for (dep in config.dependencies.withType(ProjectDependency::class.java)) {
            val targetProject = project.rootProject.findProject(dep.path)
            if (targetProject != null && targetProject != project) {
                result.add(targetProject)
            }
        }
    }
    return result
}

fun collectTransitiveFriendProjects(start: Project, commonOnly: Boolean): Set<Project> {
    val visited = linkedSetOf<Project>()
    val queue = ArrayDeque<Project>()
    for (direct in collectDirectProjectDependencies(start, commonOnly)) {
        if (visited.add(direct)) {
            queue.add(direct)
        }
    }
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (next in collectDirectProjectDependencies(current, commonOnly)) {
            if (next != start && visited.add(next)) {
                queue.add(next)
            }
        }
    }
    return visited
}

object ModuleResGenerator {
    data class ModuleResEntry(
        val category: String,
        val typeEnum: String,
        val typeCode: Int,
        val name: String,
        val numericId: Int,
        val moduleLocalRelPath: String,
        val canonicalRelPath: String,
        val format: String,
        val value: String? = null,
        val base64Data: String? = null,
        val svgSource: String? = null,
        val svgViewBoxWidth: Float = 24f,
        val svgViewBoxHeight: Float = 24f
    )

    private data class RawClassified(
        val category: String,
        val typeEnum: String,
        val typeCode: Int,
        val baseIdent: String,
        val ext: String,
        val relPath: String,
        val file: File
    )

    fun computeExtensionModuleByte(category: String, extension: String): Int {
        val normalizedKey = "${category.trim().lowercase()}:${extension.trim().lowercase()}"
        var hash = 0x811C9DC5.toInt()
        for (ch in normalizedKey) {
            hash = hash xor ch.code
            hash *= 0x01000193
        }
        val span = (0x7D - 0x60) + 1
        val offset = (hash and 0x7FFF_FFFF) % span
        return 0x60 + offset
    }

    fun discoverExtensionPackagePrefix(projectDir: File, extCategory: String, extName: String): String {
        if (extCategory.equals("media", ignoreCase = true) && extName.equals("media", ignoreCase = true)) {
            return "com.nexustools.genericui.component.media"
        }
        val ktDir = File(projectDir, "src/commonMain/kotlin")
        if (ktDir.isDirectory) {
            val extFile = ktDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith("Extension.kt") }
                .sortedBy { it.invariantSeparatorsPath }
                .firstOrNull()
            if (extFile != null) {
                val pkgLine = extFile.useLines { lines ->
                    lines.map { it.trim() }.firstOrNull { it.startsWith("package ") }
                }
                if (pkgLine != null) {
                    return pkgLine.removePrefix("package ").trim()
                }
            }
            val anyPkg = ktDir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .mapNotNull { file ->
                    file.useLines { lines ->
                        lines.map { it.trim() }.firstOrNull { it.startsWith("package ") }
                            ?.removePrefix("package ")?.trim()
                    }
                }
                .sortedBy { it.length }
                .firstOrNull()
            if (!anyPkg.isNullOrBlank()) {
                return anyPkg
            }
        }
        return "com.nexustools.genericui.ext.${extName.lowercase()}"
    }

    private fun sanitizeModuleResIdent(raw: String): String {
        val cleaned = raw.trim()
            .replace('-', '_')
            .replace('.', '_')
            .replace(' ', '_')
            .replace(Regex("[^A-Za-z0-9_]"), "")
            .lowercase()
        if (cleaned.isEmpty()) return ""
        return if (cleaned[0].isDigit()) "_$cleaned" else cleaned
    }

    private fun sanitizeNativeModuleKey(raw: String): String {
        val trimmed = raw.trim().replace('\\', '/').trim('/')
        if (trimmed.isEmpty()) return "res_unnamed"
        val sb = StringBuilder(trimmed.length + 4)
        for (ch in trimmed) {
            val lower = ch.lowercaseChar()
            if (lower in 'a'..'z' || lower in '0'..'9' || lower == '_') {
                sb.append(lower)
            } else {
                sb.append('_')
            }
        }
        var out = sb.toString().trim('_')
        if (out.isEmpty()) return "res_unnamed"
        if (out[0] !in 'a'..'z') {
            out = "r_$out"
        }
        return out
    }

    private fun escapeModuleKotlinStr(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '$' -> append("\\$")
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun escapeModuleJsonStr(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    fun copyKlibMetadata(out: File, metaRoot: File) {
        val srcMeta = File(metaRoot, "META-INF/genericui/module-resources")
        if (out.isDirectory && srcMeta.isDirectory) {
            val destMeta = File(out, "default/resources/META-INF/genericui/module-resources")
            destMeta.mkdirs()
            srcMeta.listFiles()?.filter { it.isFile }?.forEach { file ->
                file.copyTo(File(destMeta, file.name), overwrite = true)
            }
        }
    }

    fun generate(
        resSourceDir: File,
        ktRoot: File,
        metaRoot: File,
        moduleByte: Int,
        scopeId: String,
        moduleGroupId: String,
        scopePrefix: String,
        packageName: String
    ) {
        ktRoot.deleteRecursively()
        metaRoot.deleteRecursively()
        ktRoot.mkdirs()
        metaRoot.mkdirs()

        val files = resSourceDir.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(resSourceDir).invariantSeparatorsPath }
            .toList()

        val drawableExts = setOf("svg", "png", "ico", "webp", "jpg", "jpeg", "gif", "bmp")
        val fontExts = setOf("ttf", "otf", "woff", "woff2")
        val styleExts = setOf("scss", "sass", "css")

        val classified = mutableListOf<RawClassified>()
        for (file in files) {
            val relPath = file.relativeTo(resSourceDir).invariantSeparatorsPath
            val parentLower = file.parentFile?.name?.lowercase() ?: ""
            val ext = file.extension.lowercase()
            val baseIdent = sanitizeModuleResIdent(file.nameWithoutExtension)
            if (baseIdent.isEmpty()) continue
            when {
                parentLower.startsWith("drawable") || parentLower.startsWith("mipmap") || parentLower == "icons" || parentLower == "images" -> {
                    if (ext in drawableExts) {
                        classified.add(RawClassified("drawable", "DRAWABLE", 0x01, baseIdent, ext, relPath, file))
                    }
                }
                parentLower in setOf("styles", "style", "css", "scss", "sass") && ext in styleExts && !file.name.startsWith("_") -> {
                    classified.add(RawClassified("style", "STYLE", 0x0C, baseIdent, ext, relPath, file))
                }
                parentLower == "raw" || parentLower == "scripts" -> {
                    classified.add(RawClassified("raw", "RAW", 0x07, baseIdent, ext, relPath, file))
                }
                parentLower == "font" || parentLower == "fonts" -> {
                    if (ext in fontExts) {
                        classified.add(RawClassified("font", "FONT", 0x08, baseIdent, ext, relPath, file))
                    }
                }
                parentLower.startsWith("xml") && ext == "xml" -> {
                    classified.add(RawClassified("xml", "XML", 0x09, baseIdent, ext, relPath, file))
                }
            }
        }

        val stemCounts = classified.groupingBy { it.category to it.baseIdent }.eachCount()
        val counters = mutableMapOf<String, Int>()
        val usedNames = mutableMapOf<String, MutableSet<String>>()
        val entries = mutableListOf<ModuleResEntry>()

        for (item in classified) {
            var ident = if ((stemCounts[item.category to item.baseIdent] ?: 0) > 1 && item.ext.isNotEmpty()) {
                "${item.baseIdent}_${sanitizeModuleResIdent(item.ext)}"
            } else {
                item.baseIdent
            }
            val catSet = usedNames.getOrPut(item.category) { mutableSetOf() }
            if (!catSet.add(ident)) {
                var suffix = 2
                while (!catSet.add("${ident}_$suffix")) suffix++
                ident = "${ident}_$suffix"
            }
            val nextIdx = (counters[item.category] ?: 0) + 1
            counters[item.category] = nextIdx
            val numId = ((moduleByte and 0xFF) shl 24) or ((item.typeCode and 0xFF) shl 16) or (nextIdx and 0xFFFF)
            val canonicalRel = "$scopePrefix/${item.relPath}"
            val bytes = item.file.readBytes()
            val b64 = java.util.Base64.getEncoder().encodeToString(bytes)
            var svgSrc: String? = null
            var vbW = 24f
            var vbH = 24f
            if (item.category == "drawable" && item.ext == "svg") {
                svgSrc = bytes.toString(Charsets.UTF_8)
                val vbMatch = Regex("viewBox\\s*=\\s*[\"']\\s*[-0-9.]+\\s+[-0-9.]+\\s+([0-9.]+)\\s+([0-9.]+)\\s*[\"']").find(svgSrc)
                if (vbMatch != null) {
                    vbW = vbMatch.groupValues[1].toFloatOrNull() ?: 24f
                    vbH = vbMatch.groupValues[2].toFloatOrNull() ?: 24f
                }
            }
            entries.add(
                ModuleResEntry(
                    category = item.category,
                    typeEnum = item.typeEnum,
                    typeCode = item.typeCode,
                    name = ident,
                    numericId = numId,
                    moduleLocalRelPath = item.relPath,
                    canonicalRelPath = canonicalRel,
                    format = item.ext,
                    base64Data = b64,
                    svgSource = svgSrc,
                    svgViewBoxWidth = vbW,
                    svgViewBoxHeight = vbH
                )
            )
        }

        val pkgDir = File(ktRoot, packageName.replace('.', '/')).apply { mkdirs() }
        val byCategory = entries.groupBy { it.category }
        val allCategories = listOf("drawable", "string", "color", "dimen", "integer", "bool", "raw", "font", "xml", "style")

        fun appendResObjects(sb: StringBuilder, withHelpers: Boolean) {
            for (cat in allCategories) {
                val list = byCategory[cat].orEmpty()
                val categoryType = cat.replaceFirstChar { ch -> ch.uppercaseChar() } + "Entries"
                sb.appendLine("    object $categoryType {")
                for (e in list) {
                    sb.appendLine(
                        "        val ${e.name}: ResourceID = ResourceID(0x${e.numericId.toString(16)}, ResourceType.${e.typeEnum}, ${escapeModuleKotlinStr(e.name)}, ${escapeModuleKotlinStr(e.canonicalRelPath)}, ${escapeModuleKotlinStr(moduleGroupId)})"
                    )
                }
                sb.appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
                sb.appendLine("    }")
                sb.appendLine()
                sb.appendLine("    val $cat: $categoryType")
                sb.appendLine("        get() = $categoryType")
                sb.appendLine()
            }
            if (withHelpers) {
                sb.appendLine("    fun getString(id: ResourceID, vararg formatArgs: Any): String =")
                sb.appendLine("        com.nexustools.genericui.resources.ResourceResolver.getString(id, *formatArgs)")
                sb.appendLine()
                sb.appendLine("    fun getColor(id: ResourceID): com.nexustools.genericui.style.color.RgbaColor =")
                sb.appendLine("        com.nexustools.genericui.resources.ResourceResolver.getColor(id)")
                sb.appendLine()
                sb.appendLine("    fun getDimenPx(id: ResourceID): Float =")
                sb.appendLine("        com.nexustools.genericui.resources.ResourceResolver.getDimenPx(id)")
                sb.appendLine()
                sb.appendLine("    fun getInteger(id: ResourceID): Int =")
                sb.appendLine("        com.nexustools.genericui.resources.ResourceResolver.getInteger(id)")
                sb.appendLine()
                sb.appendLine("    fun getBool(id: ResourceID): Boolean =")
                sb.appendLine("        com.nexustools.genericui.resources.ResourceResolver.getBool(id)")
                sb.appendLine()
                sb.appendLine("    fun getUrl(id: ResourceID): String =")
                sb.appendLine("        com.nexustools.genericui.resources.ResourceResolver.getUrl(id)")
                sb.appendLine()
                sb.appendLine("    fun getBytes(id: ResourceID): ByteArray? =")
                sb.appendLine("        com.nexustools.genericui.resources.ResourceResolver.getBytes(id)")
                sb.appendLine()
                sb.appendLine("    suspend fun openReadable(id: ResourceID): com.nexustools.genericui.io.Readable? =")
                sb.appendLine("        com.nexustools.genericui.resources.ResourceResolver.openReadable(id)")
                sb.appendLine()
                sb.appendLine("    suspend fun openStream(id: ResourceID): com.nexustools.genericui.io.Readable? =")
                sb.appendLine("        com.nexustools.genericui.resources.ResourceResolver.openReadable(id)")
                sb.appendLine()
                sb.appendLine("    fun getImageBuffer(id: ResourceID): com.nexustools.genericui.image.ImageBuffer? =")
                sb.appendLine("        com.nexustools.genericui.resources.ResourceResolver.getImageBuffer(id)")
            }
        }

        File(pkgDir, "R.kt").writeText(buildString {
            appendLine("package $packageName")
            appendLine()
            // Top-level property R compiles into RKt.class (never stripped by Android Gradle Plugin's
            // R.class filter) while supporting `R.drawable.*`, `R.style.*`, etc. across all targets.
            appendLine("val R: Resource")
            appendLine("    get() = Resource")
        }, Charsets.UTF_8)

        File(pkgDir, "RC.kt").writeText(buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import com.nexustools.genericui.res.DrawableResource")
            appendLine("import com.nexustools.genericui.res.Resources")
            appendLine("import com.nexustools.genericui.resources.ResourceID")
            appendLine("import com.nexustools.genericui.style.color.RgbaColor")
            appendLine()
            appendLine("object RC {")
            for (cat in allCategories) {
                val categoryType = cat.replaceFirstChar { ch -> ch.uppercaseChar() } + "Entries"
                appendLine("    val $cat: Resource.$categoryType get() = Resource.$cat")
            }
            appendLine("    fun getString(resId: ResourceID): String = Resources.getString(resId)")
            appendLine("    fun getString(resId: ResourceID, vararg args: Any): String = Resources.getString(resId, *args)")
            appendLine("    fun getColor(resId: ResourceID): RgbaColor = Resources.getColor(resId)")
            appendLine("    fun getDimen(resId: ResourceID): Float = Resources.getDimen(resId)")
            appendLine("    fun getDrawable(resId: ResourceID): DrawableResource? = Resources.getDrawable(resId)")
            appendLine("    fun icon(resId: ResourceID): String = resId.toResourceUri()")
            appendLine("    init { GeneratedResourceRegistry.ensureInitialized() }")
            appendLine("}")
        }, Charsets.UTF_8)

        File(pkgDir, "Resource.kt").writeText(buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import com.nexustools.genericui.resources.ResourceID")
            appendLine("import com.nexustools.genericui.resources.ResourceType")
            appendLine()
            appendLine("object Resource {")
            appendResObjects(this, withHelpers = true)
            appendLine("    init { GeneratedResourceRegistry.ensureInitialized() }")
            appendLine("}")
        }, Charsets.UTF_8)

        File(pkgDir, "GeneratedResourceRegistry.kt").writeText(buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import com.nexustools.genericui.res.DrawableResource")
            appendLine("import com.nexustools.genericui.res.Resources")
            appendLine("import com.nexustools.genericui.resources.ResourceID")
            appendLine("import com.nexustools.genericui.resources.ResourceResolver")
            appendLine("import com.nexustools.genericui.resources.ResourceTable")
            appendLine("import com.nexustools.genericui.resources.ResourceType")
            appendLine()
            appendLine("object GeneratedResourceRegistry {")
            appendLine("    private var initialized: Boolean = false")
            appendLine()
            appendLine("    init {")
            appendLine("        ResourceResolver.registerModuleInitializer { ensureInitialized() }")
            appendLine("    }")
            appendLine()
            appendLine("    fun ensureInitialized(table: ResourceTable = Resources.activeTable) {")
            appendLine("        if (initialized) return")
            appendLine("        initialized = true")
            for (e in entries) {
                val idExpr = "ResourceID(0x${e.numericId.toString(16)}, ResourceType.${e.typeEnum}, ${escapeModuleKotlinStr(e.name)}, ${escapeModuleKotlinStr(e.canonicalRelPath)}, ${escapeModuleKotlinStr(moduleGroupId)})"
                when (e.category) {
                    "drawable" -> {
                        val svgSrc = e.svgSource
                        if (svgSrc != null && svgSrc.length <= 32768) {
                            appendLine("        table.registerDrawable(DrawableResource(resId = $idExpr, format = ${escapeModuleKotlinStr(e.format)}, svgSource = ${escapeModuleKotlinStr(svgSrc)}, viewBoxWidth = ${e.svgViewBoxWidth}f, viewBoxHeight = ${e.svgViewBoxHeight}f))")
                            appendLine("        table.registerRawText($idExpr, ${escapeModuleKotlinStr(svgSrc)})")
                        } else {
                            appendLine("        table.registerResourceId($idExpr)")
                        }
                    }
                    "font" -> {
                        appendLine("        table.registerFont($idExpr, ${escapeModuleKotlinStr(e.canonicalRelPath)})")
                    }
                    "raw", "xml" -> {
                        val b64 = e.base64Data
                        if (e.format in setOf("txt", "json", "xml", "svg", "css", "scss", "js") && b64 != null) {
                            val rawText = java.util.Base64.getDecoder().decode(b64).toString(Charsets.UTF_8)
                            if (rawText.length <= 32768) {
                                appendLine("        table.registerRawText($idExpr, ${escapeModuleKotlinStr(rawText)})")
                            } else {
                                appendLine("        table.registerResourceId($idExpr)")
                            }
                        } else {
                            appendLine("        table.registerResourceId($idExpr)")
                        }
                    }
                    else -> {
                        appendLine("        table.registerResourceId($idExpr)")
                    }
                }
            }
            appendLine("    }")
            appendLine("}")
        }, Charsets.UTF_8)

        val manifestFile = File(metaRoot, "META-INF/genericui/module-resources/$scopeId.json")
        manifestFile.parentFile.mkdirs()
        val entryLines = entries.map { e ->
            val nativeKey = sanitizeNativeModuleKey(e.moduleLocalRelPath)
            buildString {
                append("{")
                append("\"type\":${escapeModuleJsonStr(e.typeEnum)},")
                append("\"name\":${escapeModuleJsonStr(e.name)},")
                append("\"id\":${e.numericId},")
                append("\"relativePath\":${escapeModuleJsonStr(e.moduleLocalRelPath)},")
                append("\"canonicalRelativePath\":${escapeModuleJsonStr(e.canonicalRelPath)},")
                append("\"nativeResourceKey\":${escapeModuleJsonStr(nativeKey)},")
                append("\"nativeLibraryGroupId\":${escapeModuleJsonStr(moduleGroupId)},")
                append("\"format\":${escapeModuleJsonStr(e.format)},")
                if (e.category == "drawable") {
                    val svgB64 = e.svgSource?.let {
                        java.util.Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
                    } ?: ""
                    append("\"viewBoxWidth\":${e.svgViewBoxWidth},")
                    append("\"viewBoxHeight\":${e.svgViewBoxHeight},")
                    append("\"svgBase64\":${escapeModuleJsonStr(svgB64)},")
                }
                if (e.format in setOf("txt", "json", "xml", "js", "ts", "css", "scss", "sass", "svg")) {
                    val txtB64 = e.base64Data ?: ""
                    append("\"textBase64\":${escapeModuleJsonStr(txtB64)},")
                }
                append("\"base64Data\":${escapeModuleJsonStr(e.base64Data ?: "")}")
                append("}")
            }
        }
        manifestFile.writeText(buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": 2,")
            appendLine("  \"scopeId\": ${escapeModuleJsonStr(scopeId)},")
            appendLine("  \"moduleByte\": $moduleByte,")
            appendLine("  \"moduleGroupId\": ${escapeModuleJsonStr(moduleGroupId)},")
            appendLine("  \"scopePrefix\": ${escapeModuleJsonStr(scopePrefix)},")
            appendLine("  \"package\": ${escapeModuleJsonStr(packageName)},")
            appendLine("  \"entries\": [")
            entryLines.forEachIndexed { idx, line ->
                val comma = if (idx < entryLines.lastIndex) "," else ""
                appendLine("    $line$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }, Charsets.UTF_8)
    }
}

subprojects {
    val subproject = this
    val pathSegments = subproject.path.trimStart(':').split(':').filter { it.isNotEmpty() }
    val isCommonModule = subproject.path == ":common"
    val isExtensionModule = pathSegments.size == 3 && pathSegments[0] == "extensions"
    if (!isCommonModule && !isExtensionModule) return@subprojects

    val resSourceDir = subproject.file("src/commonMain/resources")
    val hasResourceFiles = resSourceDir.isDirectory && resSourceDir.walkTopDown().any { it.isFile }
    if (!hasResourceFiles) return@subprojects

    val moduleByte: Int
    val scopeId: String
    val moduleGroupId: String
    val scopePrefix: String
    val packageName: String
    if (isCommonModule) {
        moduleByte = 0x7E
        scopeId = "genericui-common"
        moduleGroupId = "genericui"
        scopePrefix = "gui"
        packageName = "com.nexustools.genericui.res"
    } else {
        val extCat = pathSegments[1].lowercase()
        val extName = pathSegments[2].lowercase()
        moduleByte = ModuleResGenerator.computeExtensionModuleByte(extCat, extName)
        scopeId = "genericui-ext-$extCat-$extName"
        moduleGroupId = "genericui-ext-$extName"
        scopePrefix = "gui/ext/$extCat/$extName"
        val rootPkg = ModuleResGenerator.discoverExtensionPackagePrefix(subproject.projectDir, extCat, extName)
        packageName = "$rootPkg.res"
    }

    val ktGenDir = subproject.layout.buildDirectory.dir("generated/genericui/module-resources/kotlin/commonMain")
    val metaGenDir = subproject.layout.buildDirectory.dir("generated/genericui/module-resources/resources")

    val generateModuleResources = subproject.tasks.register("generateModuleResources") {
        group = "genericui"
        description = "Generates scoped R/RC/Resource/GeneratedResourceRegistry Kotlin files and META-INF module manifest for ${subproject.path}."
        inputs.dir(resSourceDir)
        inputs.property("moduleByte", moduleByte)
        inputs.property("scopeId", scopeId)
        inputs.property("moduleGroupId", moduleGroupId)
        inputs.property("scopePrefix", scopePrefix)
        inputs.property("packageName", packageName)
        inputs.property("generatorVersion", "v2-property-R")
        outputs.dir(ktGenDir)
        outputs.dir(metaGenDir)

        val srcDirPath = resSourceDir.absolutePath
        val ktProvider = ktGenDir
        val metaProvider = metaGenDir
        doLast {
            ModuleResGenerator.generate(
                resSourceDir = File(srcDirPath),
                ktRoot = ktProvider.get().asFile,
                metaRoot = metaProvider.get().asFile,
                moduleByte = moduleByte,
                scopeId = scopeId,
                moduleGroupId = moduleGroupId,
                scopePrefix = scopePrefix,
                packageName = packageName
            )
        }
    }

    subproject.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val kmp = subproject.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return@withId
        kmp.sourceSets.named("commonMain") {
            kotlin.srcDir(ktGenDir)
            resources.srcDir(metaGenDir)
        }
        subproject.tasks.configureEach {
            if (this is BaseKotlinCompile ||
                this is org.gradle.language.jvm.tasks.ProcessResources ||
                this is org.gradle.api.tasks.bundling.AbstractArchiveTask ||
                name.startsWith("compile") ||
                name.contains("ProcessResources") ||
                name.contains("SourcesJar")
            ) {
                if (this.name != "generateModuleResources") {
                    dependsOn(generateModuleResources)
                }
            }
            if (this is KotlinNativeCompile) {
                val outProvider = outputFile
                val metaProvider = metaGenDir
                doLast {
                    ModuleResGenerator.copyKlibMetadata(
                        out = outProvider.get(),
                        metaRoot = metaProvider.get().asFile
                    )
                }
            }
        }
    }
}

gradle.projectsEvaluated {
    for (subproject in subprojects) {
        if (
            subproject.path == ":common" ||
            subproject.path == ":gradle-plugin" ||
            subproject.path == ":idea-plugin" ||
            subproject.path.startsWith(":examples")
        ) {
            continue
        }

        val kmpExt = subproject.extensions.findByType(KotlinMultiplatformExtension::class.java)
        if (kmpExt != null) {
            val allFriends = collectTransitiveFriendProjects(subproject, commonOnly = false)
            val commonFriends = collectTransitiveFriendProjects(subproject, commonOnly = true)

            for (target in kmpExt.targets) {
                for (compilation in target.compilations) {
                    if (target.name == "metadata") {
                        for (friendProj in commonFriends) {
                            val friendKmp = friendProj.extensions.findByType(KotlinMultiplatformExtension::class.java)
                                ?: continue
                            if (friendKmp.targets.count { it.name != "metadata" } <= 1) continue
                            val friendComp = friendKmp.targets.findByName("metadata")
                                ?.compilations
                                ?.findByName("commonMain")
                            if (friendComp != null) {
                                addFriendCompilation(compilation, friendProj, friendComp)
                            }
                        }
                    } else {
                        for (friendProj in allFriends) {
                            val friendKmp = friendProj.extensions.findByType(KotlinMultiplatformExtension::class.java)
                            if (friendKmp != null) {
                                val friendTarget = friendKmp.targets.findByName(target.name)
                                val friendComp = friendTarget?.compilations?.findByName("main")
                                    ?: friendTarget?.compilations?.findByName("${target.name}Main")
                                if (friendComp != null) {
                                    addFriendCompilation(compilation, friendProj, friendComp)
                                }
                            } else if (target.name == "jvm") {
                                val friendJvm = friendProj.extensions.findByType(KotlinJvmProjectExtension::class.java)
                                val friendComp = friendJvm?.target?.compilations?.findByName("main")
                                if (friendComp != null) {
                                    addFriendCompilation(compilation, friendProj, friendComp)
                                }
                            }
                        }
                    }
                }
            }
        }

        val jvmExt = subproject.extensions.findByType(KotlinJvmProjectExtension::class.java)
        if (jvmExt != null) {
            val allFriends = collectTransitiveFriendProjects(subproject, commonOnly = false)
            for (compilation in jvmExt.target.compilations) {
                for (friendProj in allFriends) {
                    val friendKmp = friendProj.extensions.findByType(KotlinMultiplatformExtension::class.java)
                    if (friendKmp != null) {
                        val friendComp = friendKmp.targets.findByName("jvm")
                            ?.compilations
                            ?.findByName("main")
                        if (friendComp != null) {
                            addFriendCompilation(compilation, friendProj, friendComp)
                        }
                    } else {
                        val friendJvm = friendProj.extensions.findByType(KotlinJvmProjectExtension::class.java)
                        val friendComp = friendJvm?.target?.compilations?.findByName("main")
                        if (friendComp != null) {
                            addFriendCompilation(compilation, friendProj, friendComp)
                        }
                    }
                }
            }
        }
    }
}
