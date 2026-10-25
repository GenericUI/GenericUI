package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Base64

/**
 * Gradle task (`compileGenericUiResources`) that implements an Android-like `R` / `RC` resource
 * compiler and multi-backend resource translator for GenericUI projects.
 *
 * Generates:
 * 1. `R.kt`, `RC.kt`, and `GeneratedResourceRegistry.kt` (`1 Class Per File`) in `kotlinOutputDirectory`.
 * 2. Translated native backend resource bundles in `backendOutputDirectory`:
 *    - Windows Win32 `.rc` script & `.h` header (`windows/genericui_resources.rc`, `windows/genericui_resource_ids.h`)
 *    - Android `res/values/<name>.xml` & `res/drawable/<name>.xml` (`VectorDrawable` translated from `.svg`)
 *    - macOS / iOS `Localizable.strings` & `Assets.xcassets`
 *    - Linux GTK4 `genericui.gresource.xml` & Qt6 `genericui_resources.qrc`
 *    - Web / Swing `genericui-resources.json` & `genericui-resource-vars.css`
 */
abstract class GenericUiResourceCompilerTask : DefaultTask() {

    @get:Input
    abstract val resourcesEnabled: Property<Boolean>

    @get:Input
    abstract val projectRootPath: Property<String>

    @get:Input
    abstract val resourcePackageName: Property<String>

    @get:Input
    abstract val candidateSourceDirs: ListProperty<String>

    @get:Input
    abstract val translateForAllBackends: Property<Boolean>

    @get:OutputDirectory
    abstract val kotlinOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val backendOutputDirectory: DirectoryProperty

    private data class DrawableEntry(
        val name: String,
        val numericId: Int,
        val relativePath: String,
        val format: String,
        val svgSource: String?,
        val svgPaths: List<String>,
        val viewBoxWidth: Float,
        val viewBoxHeight: Float,
        val base64Data: String?
    )

    private data class ValueEntry(
        val name: String,
        val numericId: Int,
        val rawValue: String
    )

    private data class RawEntry(
        val name: String,
        val numericId: Int,
        val relativePath: String,
        val textContent: String?,
        val base64Content: String?
    )

    @TaskAction
    fun compileResources() {
        val ktOutRoot = kotlinOutputDirectory.get().asFile
        val backendOutRoot = backendOutputDirectory.get().asFile
        ktOutRoot.deleteRecursively()
        backendOutRoot.deleteRecursively()
        ktOutRoot.mkdirs()
        backendOutRoot.mkdirs()

        if (!resourcesEnabled.getOrElse(true)) {
            return
        }

        val projDir = File(projectRootPath.get())
        val pkgName = resourcePackageName.get().trim().ifEmpty { "com.nexustools.genericui.generated.res" }
        val existingDirs = candidateSourceDirs.getOrElse(emptyList())
            .map { File(projDir, it) }
            .filter { it.exists() && it.isDirectory }

        val drawables = linkedMapOf<String, DrawableEntry>()
        val strings = linkedMapOf<String, ValueEntry>()
        val colors = linkedMapOf<String, ValueEntry>()
        val dimens = linkedMapOf<String, ValueEntry>()
        val integers = linkedMapOf<String, ValueEntry>()
        val bools = linkedMapOf<String, ValueEntry>()
        val raws = linkedMapOf<String, RawEntry>()
        val fonts = linkedMapOf<String, ValueEntry>()

        var nextDrawableIdx = 1
        var nextStringIdx = 1
        var nextColorIdx = 1
        var nextDimenIdx = 1
        var nextIntIdx = 1
        var nextBoolIdx = 1
        var nextRawIdx = 1
        var nextFontIdx = 1

        for (root in existingDirs) {
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val relPath = file.relativeTo(root).invariantSeparatorsPath
                val parentDir = file.parentFile?.name?.lowercase() ?: ""
                val ext = file.extension.lowercase()
                val ident = sanitizeIdentifier(file.nameWithoutExtension)

                when {
                    parentDir.startsWith("drawable") || parentDir == "icons" || parentDir == "images" ||
                        (parentDir == root.name.lowercase() && ext in setOf("svg", "png", "ico", "webp", "jpg")) -> {
                        if (ident.isNotEmpty() && ident !in drawables && ext in setOf("svg", "png", "ico", "webp", "jpg", "jpeg")) {
                            val numId = (0x7f01 shl 16) or (nextDrawableIdx++ and 0xFFFF)
                            if (ext == "svg") {
                                val svgText = file.readText()
                                val (vbW, vbH) = extractSvgViewBox(svgText)
                                val pathList = extractSvgPaths(svgText)
                                drawables[ident] = DrawableEntry(
                                    name = ident,
                                    numericId = numId,
                                    relativePath = relPath,
                                    format = "svg",
                                    svgSource = svgText,
                                    svgPaths = pathList,
                                    viewBoxWidth = vbW,
                                    viewBoxHeight = vbH,
                                    base64Data = null
                                )
                            } else {
                                val b64 = Base64.getEncoder().encodeToString(file.readBytes())
                                drawables[ident] = DrawableEntry(
                                    name = ident,
                                    numericId = numId,
                                    relativePath = relPath,
                                    format = ext,
                                    svgSource = null,
                                    svgPaths = emptyList(),
                                    viewBoxWidth = 24f,
                                    viewBoxHeight = 24f,
                                    base64Data = b64
                                )
                            }
                        }
                    }
                    parentDir.startsWith("values") && ext == "xml" -> {
                        parseAndroidValuesXml(
                            xmlText = file.readText(),
                            onString = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in strings) {
                                    strings[id] = ValueEntry(id, (0x7f02 shl 16) or (nextStringIdx++ and 0xFFFF), v)
                                }
                            },
                            onColor = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in colors) {
                                    colors[id] = ValueEntry(id, (0x7f03 shl 16) or (nextColorIdx++ and 0xFFFF), v)
                                }
                            },
                            onDimen = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in dimens) {
                                    dimens[id] = ValueEntry(id, (0x7f04 shl 16) or (nextDimenIdx++ and 0xFFFF), v)
                                }
                            },
                            onInteger = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in integers) {
                                    integers[id] = ValueEntry(id, (0x7f05 shl 16) or (nextIntIdx++ and 0xFFFF), v)
                                }
                            },
                            onBool = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in bools) {
                                    bools[id] = ValueEntry(id, (0x7f06 shl 16) or (nextBoolIdx++ and 0xFFFF), v)
                                }
                            }
                        )
                    }
                    parentDir == "raw" || parentDir == "scripts" -> {
                        if (ident.isNotEmpty() && ident !in raws) {
                            val numId = (0x7f07 shl 16) or (nextRawIdx++ and 0xFFFF)
                            val isText = ext in setOf("txt", "json", "js", "ts", "glsl", "wgsl", "xml", "csv", "md")
                            raws[ident] = RawEntry(
                                name = ident,
                                numericId = numId,
                                relativePath = relPath,
                                textContent = if (isText) file.readText() else null,
                                base64Content = if (!isText) Base64.getEncoder().encodeToString(file.readBytes()) else null
                            )
                        }
                    }
                    parentDir == "font" || parentDir == "fonts" -> {
                        if (ident.isNotEmpty() && ident !in fonts && ext in setOf("ttf", "otf", "woff", "woff2")) {
                            val numId = (0x7f08 shl 16) or (nextFontIdx++ and 0xFFFF)
                            fonts[ident] = ValueEntry(ident, numId, relPath)
                        }
                    }
                }
            }
        }

        val pkgDir = File(ktOutRoot, pkgName.replace('.', '/'))
        pkgDir.mkdirs()

        // 1. Emit GeneratedResourceRegistry.kt (1 Class Per File)
        File(pkgDir, "GeneratedResourceRegistry.kt").writeText(
            buildGeneratedResourceRegistryKotlin(
                pkgName = pkgName,
                drawables = drawables.values.toList(),
                strings = strings.values.toList(),
                colors = colors.values.toList(),
                dimens = dimens.values.toList(),
                integers = integers.values.toList(),
                bools = bools.values.toList(),
                raws = raws.values.toList(),
                fonts = fonts.values.toList()
            )
        )

        // 2. Emit R.kt (1 Class Per File)
        File(pkgDir, "R.kt").writeText(
            buildRClassKotlin(
                pkgName = pkgName,
                drawables = drawables.values.toList(),
                strings = strings.values.toList(),
                colors = colors.values.toList(),
                dimens = dimens.values.toList(),
                integers = integers.values.toList(),
                bools = bools.values.toList(),
                raws = raws.values.toList(),
                fonts = fonts.values.toList()
            )
        )

        // 3. Emit RC.kt (1 Class Per File)
        File(pkgDir, "RC.kt").writeText(buildRcAliasKotlin(pkgName))

        // 4. Emit translated native backend resource files when enabled
        if (translateForAllBackends.getOrElse(true)) {
            emitWindowsRcTranslations(
                outDir = File(backendOutRoot, "windows"),
                drawables = drawables.values.toList(),
                strings = strings.values.toList()
            )
            emitAndroidResourceTranslations(
                outDir = File(backendOutRoot, "android/res"),
                drawables = drawables.values.toList(),
                strings = strings.values.toList(),
                colors = colors.values.toList(),
                dimens = dimens.values.toList()
            )
            emitAppleResourceTranslations(
                outDir = File(backendOutRoot, "apple"),
                drawables = drawables.values.toList(),
                strings = strings.values.toList(),
                colors = colors.values.toList()
            )
            emitLinuxGtkAndQtTranslations(
                outDir = File(backendOutRoot, "linux"),
                pkgName = pkgName,
                drawables = drawables.values.toList(),
                raws = raws.values.toList()
            )
            emitWebAndSwingResourceTranslations(
                outDir = File(backendOutRoot, "web"),
                drawables = drawables.values.toList(),
                strings = strings.values.toList(),
                colors = colors.values.toList(),
                dimens = dimens.values.toList()
            )
        }
    }

    private fun buildGeneratedResourceRegistryKotlin(
        pkgName: String,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        dimens: List<ValueEntry>,
        integers: List<ValueEntry>,
        bools: List<ValueEntry>,
        raws: List<RawEntry>,
        fonts: List<ValueEntry>
    ): String = buildString {
        appendLine("package $pkgName")
        appendLine()
        appendLine("import com.nexustools.genericui.res.DrawableResource")
        appendLine("import com.nexustools.genericui.res.ResourceId")
        appendLine("import com.nexustools.genericui.res.ResourceType")
        appendLine("import com.nexustools.genericui.res.Resources")
        appendLine()
        appendLine("/**")
        appendLine(" * Auto-generated by GenericUI `compileGenericUiResources`.")
        appendLine(" * Populates [Resources.activeTable] across all target backends.")
        appendLine(" */")
        appendLine("object GeneratedResourceRegistry {")
        appendLine("    private var initialized: Boolean = false")
        appendLine()
        appendLine("    fun ensureInitialized() {")
        appendLine("        if (initialized) return")
        appendLine("        initialized = true")
        appendLine("        val table = Resources.activeTable")
        for (d in drawables) {
            val pathsLiteral = if (d.svgPaths.isEmpty()) {
                "emptyList()"
            } else {
                "listOf(" + d.svgPaths.joinToString(", ") { escapeKotlinString(it) } + ")"
            }
            val svgSrcLiteral = d.svgSource?.let { escapeKotlinString(it) } ?: "null"
            val b64Literal = d.base64Data?.let { escapeKotlinString(it) } ?: "null"
            appendLine(
                "        table.registerDrawable(DrawableResource(" +
                    "resId = ResourceId(0x${d.numericId.toString(16)}, ResourceType.DRAWABLE, ${escapeKotlinString(d.name)}, ${escapeKotlinString(d.relativePath)}), " +
                    "format = ${escapeKotlinString(d.format)}, " +
                    "svgSource = $svgSrcLiteral, " +
                    "svgPathDataList = $pathsLiteral, " +
                    "viewBoxWidth = ${d.viewBoxWidth}f, " +
                    "viewBoxHeight = ${d.viewBoxHeight}f, " +
                    "base64Data = $b64Literal))"
            )
        }
        for (s in strings) {
            appendLine(
                "        table.registerString(ResourceId(0x${s.numericId.toString(16)}, ResourceType.STRING, ${escapeKotlinString(s.name)}), ${escapeKotlinString(s.rawValue)})"
            )
        }
        for (c in colors) {
            appendLine(
                "        table.registerColor(ResourceId(0x${c.numericId.toString(16)}, ResourceType.COLOR, ${escapeKotlinString(c.name)}), ${escapeKotlinString(c.rawValue)})"
            )
        }
        for (dm in dimens) {
            val numericVal = dm.rawValue.replace(Regex("[^0-9.-]"), "").toFloatOrNull() ?: 0f
            appendLine(
                "        table.registerDimen(ResourceId(0x${dm.numericId.toString(16)}, ResourceType.DIMEN, ${escapeKotlinString(dm.name)}), ${numericVal}f)"
            )
        }
        for (iv in integers) {
            val intVal = iv.rawValue.trim().toIntOrNull() ?: 0
            appendLine(
                "        table.registerInteger(ResourceId(0x${iv.numericId.toString(16)}, ResourceType.INTEGER, ${escapeKotlinString(iv.name)}), $intVal)"
            )
        }
        for (bv in bools) {
            val boolVal = bv.rawValue.trim().equals("true", ignoreCase = true)
            appendLine(
                "        table.registerBool(ResourceId(0x${bv.numericId.toString(16)}, ResourceType.BOOL, ${escapeKotlinString(bv.name)}), $boolVal)"
            )
        }
        for (rw in raws) {
            if (rw.textContent != null) {
                appendLine(
                    "        table.registerRawText(ResourceId(0x${rw.numericId.toString(16)}, ResourceType.RAW, ${escapeKotlinString(rw.name)}, ${escapeKotlinString(rw.relativePath)}), ${escapeKotlinString(rw.textContent)})"
                )
            } else if (rw.base64Content != null) {
                appendLine(
                    "        table.registerRawBase64(ResourceId(0x${rw.numericId.toString(16)}, ResourceType.RAW, ${escapeKotlinString(rw.name)}, ${escapeKotlinString(rw.relativePath)}), ${escapeKotlinString(rw.base64Content)})"
                )
            }
        }
        for (fn in fonts) {
            appendLine(
                "        table.registerFont(ResourceId(0x${fn.numericId.toString(16)}, ResourceType.FONT, ${escapeKotlinString(fn.name)}, ${escapeKotlinString(fn.rawValue)}), ${escapeKotlinString(fn.rawValue)})"
            )
        }
        appendLine("    }")
        appendLine("}")
    }

    private fun buildRClassKotlin(
        pkgName: String,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        dimens: List<ValueEntry>,
        integers: List<ValueEntry>,
        bools: List<ValueEntry>,
        raws: List<RawEntry>,
        fonts: List<ValueEntry>
    ): String = buildString {
        appendLine("package $pkgName")
        appendLine()
        appendLine("import com.nexustools.genericui.res.ResourceId")
        appendLine("import com.nexustools.genericui.res.ResourceType")
        appendLine()
        appendLine("/**")
        appendLine(" * Android-like `R` resource class generated by the GenericUI Gradle plugin.")
        appendLine(" */")
        appendLine("object R {")
        appendLine("    init {")
        appendLine("        GeneratedResourceRegistry.ensureInitialized()")
        appendLine("    }")
        appendLine()
        appendLine("    object drawable {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (d in drawables) {
            appendLine(
                "        val ${d.name}: ResourceId = ResourceId(0x${d.numericId.toString(16)}, ResourceType.DRAWABLE, ${escapeKotlinString(d.name)}, ${escapeKotlinString(d.relativePath)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object string {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (s in strings) {
            appendLine(
                "        val ${s.name}: ResourceId = ResourceId(0x${s.numericId.toString(16)}, ResourceType.STRING, ${escapeKotlinString(s.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object color {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (c in colors) {
            appendLine(
                "        val ${c.name}: ResourceId = ResourceId(0x${c.numericId.toString(16)}, ResourceType.COLOR, ${escapeKotlinString(c.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object dimen {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (dm in dimens) {
            appendLine(
                "        val ${dm.name}: ResourceId = ResourceId(0x${dm.numericId.toString(16)}, ResourceType.DIMEN, ${escapeKotlinString(dm.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object integer {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (iv in integers) {
            appendLine(
                "        val ${iv.name}: ResourceId = ResourceId(0x${iv.numericId.toString(16)}, ResourceType.INTEGER, ${escapeKotlinString(iv.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object bool {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (bv in bools) {
            appendLine(
                "        val ${bv.name}: ResourceId = ResourceId(0x${bv.numericId.toString(16)}, ResourceType.BOOL, ${escapeKotlinString(bv.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object raw {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (rw in raws) {
            appendLine(
                "        val ${rw.name}: ResourceId = ResourceId(0x${rw.numericId.toString(16)}, ResourceType.RAW, ${escapeKotlinString(rw.name)}, ${escapeKotlinString(rw.relativePath)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object font {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (fn in fonts) {
            appendLine(
                "        val ${fn.name}: ResourceId = ResourceId(0x${fn.numericId.toString(16)}, ResourceType.FONT, ${escapeKotlinString(fn.name)}, ${escapeKotlinString(fn.rawValue)})"
            )
        }
        appendLine("    }")
        appendLine("}")
    }

    private fun buildRcAliasKotlin(pkgName: String): String = """
package $pkgName

import com.nexustools.genericui.res.DrawableResource
import com.nexustools.genericui.res.ResourceId
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.RgbaColor

/**
 * Android/Win32-style `RC` resource accessor generated by the GenericUI Gradle plugin.
 * Provides direct access to [R.drawable], [R.string], [R.color], [R.dimen], [R.raw], and [R.font]
 * as well as convenience lookup helpers across all target backends.
 */
object RC {
    init {
        GeneratedResourceRegistry.ensureInitialized()
    }

    val drawable: R.drawable get() = R.drawable
    val string: R.string get() = R.string
    val color: R.color get() = R.color
    val dimen: R.dimen get() = R.dimen
    val integer: R.integer get() = R.integer
    val bool: R.bool get() = R.bool
    val raw: R.raw get() = R.raw
    val font: R.font get() = R.font

    fun getString(resId: ResourceId): String = Resources.getString(resId)
    fun getString(resId: ResourceId, vararg args: Any): String = Resources.getString(resId, *args)
    fun getColor(resId: ResourceId): RgbaColor = Resources.getColor(resId)
    fun getDimen(resId: ResourceId): Float = Resources.getDimen(resId)
    fun getDrawable(resId: ResourceId): DrawableResource? = Resources.getDrawable(resId)
    fun icon(resId: ResourceId): String = resId.toResourceUri()
}
""".trimIndent() + "\n"

    private fun emitWindowsRcTranslations(
        outDir: File,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>
    ) {
        outDir.mkdirs()
        val headerSb = StringBuilder()
        headerSb.appendLine("// Auto-generated Win32 Resource Header by GenericUI")
        headerSb.appendLine("#pragma once")
        for ((idx, d) in drawables.withIndex()) {
            headerSb.appendLine("#define IDR_DRAWABLE_${d.name.uppercase()} ${1100 + idx}")
        }
        for ((idx, s) in strings.withIndex()) {
            headerSb.appendLine("#define IDS_STRING_${s.name.uppercase()} ${2100 + idx}")
        }
        File(outDir, "genericui_resource_ids.h").writeText(headerSb.toString())

        val rcSb = StringBuilder()
        rcSb.appendLine("// Auto-generated Win32 Resource Script (.rc) by GenericUI")
        rcSb.appendLine("#include \"genericui_resource_ids.h\"")
        rcSb.appendLine()
        if (strings.isNotEmpty()) {
            rcSb.appendLine("STRINGTABLE")
            rcSb.appendLine("BEGIN")
            for (s in strings) {
                val escaped = s.rawValue.replace("\"", "\"\"").replace("\n", "\\n")
                rcSb.appendLine("    IDS_STRING_${s.name.uppercase()} \"$escaped\"")
            }
            rcSb.appendLine("END")
        }
        File(outDir, "genericui_resources.rc").writeText(rcSb.toString())
    }

    private fun emitAndroidResourceTranslations(
        outDir: File,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        dimens: List<ValueEntry>
    ) {
        val valuesDir = File(outDir, "values").apply { mkdirs() }
        val drawableDir = File(outDir, "drawable").apply { mkdirs() }

        if (strings.isNotEmpty()) {
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            for (s in strings) {
                sb.appendLine("    <string name=\"${s.name}\">${escapeXml(s.rawValue)}</string>")
            }
            sb.appendLine("</resources>")
            File(valuesDir, "strings.xml").writeText(sb.toString())
        }

        if (colors.isNotEmpty()) {
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            for (c in colors) {
                sb.appendLine("    <color name=\"${c.name}\">${escapeXml(c.rawValue)}</color>")
            }
            sb.appendLine("</resources>")
            File(valuesDir, "colors.xml").writeText(sb.toString())
        }

        if (dimens.isNotEmpty()) {
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            for (d in dimens) {
                sb.appendLine("    <dimen name=\"${d.name}\">${escapeXml(d.rawValue)}</dimen>")
            }
            sb.appendLine("</resources>")
            File(valuesDir, "dimens.xml").writeText(sb.toString())
        }

        for (d in drawables) {
            if (d.format == "svg" && d.svgPaths.isNotEmpty()) {
                val vd = StringBuilder()
                vd.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                vd.appendLine("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"")
                vd.appendLine("    android:width=\"${d.viewBoxWidth.toInt()}dp\"")
                vd.appendLine("    android:height=\"${d.viewBoxHeight.toInt()}dp\"")
                vd.appendLine("    android:viewportWidth=\"${d.viewBoxWidth}\"")
                vd.appendLine("    android:viewportHeight=\"${d.viewBoxHeight}\">")
                for (pathData in d.svgPaths) {
                    vd.appendLine("    <path")
                    vd.appendLine("        android:fillColor=\"#FF000000\"")
                    vd.appendLine("        android:strokeColor=\"#FF000000\"")
                    vd.appendLine("        android:strokeWidth=\"1.8\"")
                    vd.appendLine("        android:pathData=\"${escapeXml(pathData)}\" />")
                }
                vd.appendLine("</vector>")
                File(drawableDir, "${d.name}.xml").writeText(vd.toString())
            }
        }
    }

    private fun emitAppleResourceTranslations(
        outDir: File,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>
    ) {
        val lproj = File(outDir, "en.lproj").apply { mkdirs() }
        val xcassets = File(outDir, "Assets.xcassets").apply { mkdirs() }
        val ls = StringBuilder("// Auto-generated Apple Localizable.strings by GenericUI\n")
        for (s in strings) {
            val esc = s.rawValue.replace("\"", "\\\"").replace("\n", "\\n")
            ls.appendLine("\"${s.name}\" = \"$esc\";")
        }
        File(lproj, "Localizable.strings").writeText(ls.toString())

        for (d in drawables) {
            val imgSet = File(xcassets, "${d.name}.imageset").apply { mkdirs() }
            if (d.svgSource != null) {
                File(imgSet, "${d.name}.svg").writeText(d.svgSource)
            }
            File(imgSet, "Contents.json").writeText(
                """
                {
                  "images": [{ "idiom": "universal", "filename": "${d.name}.${d.format}" }],
                  "info": { "version": 1, "author": "genericui" }
                }
                """.trimIndent()
            )
        }
        for (c in colors) {
            val colSet = File(xcassets, "${c.name}.colorset").apply { mkdirs() }
            File(colSet, "Contents.json").writeText(
                """
                {
                  "colors": [{ "idiom": "universal", "color": { "color-space": "srgb", "hex": "${c.rawValue}" } }],
                  "info": { "version": 1, "author": "genericui" }
                }
                """.trimIndent()
            )
        }
    }

    private fun emitLinuxGtkAndQtTranslations(
        outDir: File,
        pkgName: String,
        drawables: List<DrawableEntry>,
        raws: List<RawEntry>
    ) {
        outDir.mkdirs()
        val prefixPath = "/" + pkgName.replace('.', '/')
        val gres = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gresources>\n  <gresource prefix=\"$prefixPath\">\n")
        val qrc = StringBuilder("<!DOCTYPE RCC>\n<RCC version=\"1.0\">\n  <qresource prefix=\"/\">\n")
        for (d in drawables) {
            gres.appendLine("    <file>${d.relativePath}</file>")
            qrc.appendLine("    <file alias=\"drawable/${d.name}.${d.format}\">${d.relativePath}</file>")
        }
        for (r in raws) {
            gres.appendLine("    <file>${r.relativePath}</file>")
            qrc.appendLine("    <file alias=\"raw/${r.name}\">${r.relativePath}</file>")
        }
        gres.appendLine("  </gresource>\n</gresources>")
        qrc.appendLine("  </qresource>\n</RCC>")
        File(outDir, "genericui.gresource.xml").writeText(gres.toString())
        File(outDir, "genericui_resources.qrc").writeText(qrc.toString())
    }

    private fun emitWebAndSwingResourceTranslations(
        outDir: File,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        dimens: List<ValueEntry>
    ) {
        outDir.mkdirs()
        val css = StringBuilder(":root {\n")
        for (c in colors) {
            css.appendLine("  --rc-color-${c.name.replace('_', '-')}: ${c.rawValue};")
        }
        for (d in dimens) {
            css.appendLine("  --rc-dimen-${d.name.replace('_', '-')}: ${d.rawValue};")
        }
        css.appendLine("}")
        File(outDir, "genericui-resource-vars.css").writeText(css.toString())

        val json = StringBuilder("{\n  \"drawables\": {\n")
        drawables.forEachIndexed { idx, d ->
            val comma = if (idx < drawables.lastIndex) "," else ""
            json.appendLine("    \"${d.name}\": { \"id\": ${d.numericId}, \"format\": \"${d.format}\", \"path\": \"${d.relativePath}\" }$comma")
        }
        json.appendLine("  },\n  \"strings\": {")
        strings.forEachIndexed { idx, s ->
            val comma = if (idx < strings.lastIndex) "," else ""
            val esc = s.rawValue.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            json.appendLine("    \"${s.name}\": \"$esc\"$comma")
        }
        json.appendLine("  }\n}")
        File(outDir, "genericui-resources.json").writeText(json.toString())
    }

    private fun parseAndroidValuesXml(
        xmlText: String,
        onString: (String, String) -> Unit,
        onColor: (String, String) -> Unit,
        onDimen: (String, String) -> Unit,
        onInteger: (String, String) -> Unit,
        onBool: (String, String) -> Unit
    ) {
        val tagRegex = Regex("<(string|color|dimen|integer|bool)\\s+name\\s*=\\s*\"([^\"]+)\"[^>]*>([\\s\\S]*?)</\\1>")
        for (match in tagRegex.findAll(xmlText)) {
            val tag = match.groupValues[1]
            val name = match.groupValues[2].trim()
            val value = unescapeXml(match.groupValues[3].trim())
            when (tag) {
                "string" -> onString(name, value)
                "color" -> onColor(name, value)
                "dimen" -> onDimen(name, value)
                "integer" -> onInteger(name, value)
                "bool" -> onBool(name, value)
            }
        }
    }

    private fun extractSvgViewBox(svg: String): Pair<Float, Float> {
        val vbMatch = Regex("viewBox\\s*=\\s*\"([^\"]+)\"").find(svg)
        if (vbMatch != null) {
            val nums = vbMatch.groupValues[1].trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
            if (nums.size >= 4) {
                return nums[2].coerceAtLeast(1f) to nums[3].coerceAtLeast(1f)
            }
        }
        return 24f to 24f
    }

    private fun extractSvgPaths(svg: String): List<String> {
        val result = mutableListOf<String>()
        for (m in Regex("<path[^>]*\\bd\\s*=\\s*\"([^\"]+)\"[^>]*>").findAll(svg)) {
            val d = m.groupValues[1].trim()
            if (d.isNotEmpty()) result.add(d)
        }
        for (m in Regex("<rect[^>]*>").findAll(svg)) {
            val tag = m.value
            val x = attrFloat(tag, "x", 3f)
            val y = attrFloat(tag, "y", 3f)
            val w = attrFloat(tag, "width", 18f)
            val h = attrFloat(tag, "height", 18f)
            result.add("M $x $y L ${x + w} $y L ${x + w} ${y + h} L $x ${y + h} Z")
        }
        for (m in Regex("<line[^>]*>").findAll(svg)) {
            val tag = m.value
            val x1 = attrFloat(tag, "x1", 0f)
            val y1 = attrFloat(tag, "y1", 0f)
            val x2 = attrFloat(tag, "x2", 24f)
            val y2 = attrFloat(tag, "y2", 24f)
            result.add("M $x1 $y1 L $x2 $y2")
        }
        for (m in Regex("<polyline[^>]*\\bpoints\\s*=\\s*\"([^\"]+)\"[^>]*>").findAll(svg)) {
            val pts = m.groupValues[1].trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
            if (pts.size >= 4) {
                val sb = StringBuilder("M ${pts[0]} ${pts[1]}")
                var i = 2
                while (i + 1 < pts.size) {
                    sb.append(" L ${pts[i]} ${pts[i + 1]}")
                    i += 2
                }
                result.add(sb.toString())
            }
        }
        for (m in Regex("<polygon[^>]*\\bpoints\\s*=\\s*\"([^\"]+)\"[^>]*>").findAll(svg)) {
            val pts = m.groupValues[1].trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
            if (pts.size >= 4) {
                val sb = StringBuilder("M ${pts[0]} ${pts[1]}")
                var i = 2
                while (i + 1 < pts.size) {
                    sb.append(" L ${pts[i]} ${pts[i + 1]}")
                    i += 2
                }
                sb.append(" Z")
                result.add(sb.toString())
            }
        }
        for (m in Regex("<circle[^>]*>").findAll(svg)) {
            val tag = m.value
            val cx = attrFloat(tag, "cx", 12f)
            val cy = attrFloat(tag, "cy", 12f)
            val r = attrFloat(tag, "r", 6f)
            result.add("M ${cx - r} $cy Q ${cx - r} ${cy - r} $cx ${cy - r} Q ${cx + r} ${cy - r} ${cx + r} $cy Q ${cx + r} ${cy + r} $cx ${cy + r} Q ${cx - r} ${cy + r} ${cx - r} $cy Z")
        }
        for (m in Regex("<ellipse[^>]*>").findAll(svg)) {
            val tag = m.value
            val cx = attrFloat(tag, "cx", 12f)
            val cy = attrFloat(tag, "cy", 12f)
            val rx = attrFloat(tag, "rx", 8f)
            val ry = attrFloat(tag, "ry", 6f)
            result.add("M ${cx - rx} $cy Q ${cx - rx} ${cy - ry} $cx ${cy - ry} Q ${cx + rx} ${cy - ry} ${cx + rx} $cy Q ${cx + rx} ${cy + ry} $cx ${cy + ry} Q ${cx - rx} ${cy + ry} ${cx - rx} $cy Z")
        }
        return result
    }

    private fun attrFloat(tag: String, attrName: String, defaultVal: Float): Float {
        val m = Regex("\\b$attrName\\s*=\\s*\"([^\"]+)\"").find(tag) ?: return defaultVal
        return m.groupValues[1].replace("px", "").trim().toFloatOrNull() ?: defaultVal
    }

    private fun sanitizeIdentifier(raw: String): String {
        val cleaned = raw.trim()
            .replace('-', '_')
            .replace('.', '_')
            .replace(' ', '_')
            .replace(Regex("[^A-Za-z0-9_]"), "")
            .lowercase()
        if (cleaned.isEmpty()) return ""
        return if (cleaned[0].isDigit()) "_$cleaned" else cleaned
    }

    private fun escapeKotlinString(value: String): String = buildString {
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

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun unescapeXml(value: String): String =
        value.replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
}
