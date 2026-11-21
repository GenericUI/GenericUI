package com.nexustools.genericui.plugin

import java.io.File
import java.util.Base64
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Parses GenericUI module resource manifests (`META-INF/genericui/module-resources/<scope>.json`
 * and legacy `genericui-module-resources.json`) from files, directories, `.jar` archives,
 * and `.klib` archives without mixing upstream entries into consumer `R` classes.
 */
object GenericUiModuleResourceManifestParser {

    data class ManifestEntry(
        val type: String,
        val name: String,
        val id: Int?,
        val moduleLocalRelativePath: String,
        val canonicalRelativePath: String,
        val nativeResourceKey: String,
        val nativeLibraryGroupId: String,
        val scopePrefix: String,
        val format: String,
        val rawValue: String?,
        val textContent: String?,
        val base64Data: String?,
        val viewBoxWidth: Float,
        val viewBoxHeight: Float
    ) {
        val canonicalStem: String
            get() {
                val path = canonicalRelativePath.trim().replace('\\', '/').trimStart('/')
                val lastSlash = path.lastIndexOf('/')
                val lastDot = path.lastIndexOf('.')
                return if (lastDot > lastSlash) path.substring(0, lastDot) else path
            }

        val flatName: String
            get() = sanitizeFlatIdentifier(canonicalStem)

        fun decodePayloadBytes(): ByteArray? {
            if (!base64Data.isNullOrEmpty()) {
                return runCatching { Base64.getDecoder().decode(base64Data) }.getOrNull()
            }
            if (textContent != null) {
                return textContent.toByteArray(Charsets.UTF_8)
            }
            if (rawValue != null && type in setOf("STRING", "COLOR", "DIMEN", "INTEGER", "BOOL")) {
                return rawValue.toByteArray(Charsets.UTF_8)
            }
            return null
        }
    }

    data class ModuleManifest(
        val schemaVersion: Int,
        val scopeId: String,
        val moduleByte: Int,
        val moduleGroupId: String,
        val scopePrefix: String,
        val packageName: String,
        val entries: List<ManifestEntry>
    )

    fun readManifestsFromSources(sources: Collection<File>): List<ModuleManifest> {
        val result = mutableListOf<ModuleManifest>()
        val seenKeys = linkedSetOf<String>()
        for (source in sources) {
            if (!source.exists()) continue
            when {
                source.isFile && source.extension.equals("json", ignoreCase = true) -> {
                    parseJsonManifest(source.readText(Charsets.UTF_8))?.let { m ->
                        if (seenKeys.add("${m.scopeId}|${m.packageName}|${m.entries.size}")) {
                            result.add(m)
                        }
                    }
                }
                source.isFile && (source.extension.equals("jar", ignoreCase = true) || source.extension.equals("klib", ignoreCase = true) || source.extension.equals("zip", ignoreCase = true)) -> {
                    readFromZipArchive(source).forEach { m ->
                        if (seenKeys.add("${m.scopeId}|${m.packageName}|${m.entries.size}")) {
                            result.add(m)
                        }
                    }
                }
                source.isDirectory -> {
                    readFromDirectory(source).forEach { m ->
                        if (seenKeys.add("${m.scopeId}|${m.packageName}|${m.entries.size}")) {
                            result.add(m)
                        }
                    }
                }
            }
        }
        return result
    }

    fun readFromZipArchive(archiveFile: File): List<ModuleManifest> {
        if (!archiveFile.isFile) return emptyList()
        val manifests = mutableListOf<ModuleManifest>()
        runCatching {
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val normName = entry.name.replace('\\', '/').trimStart('/')
                    val isModuleResJson = (
                        normName.startsWith("META-INF/genericui/module-resources/") ||
                            normName.startsWith("default/resources/META-INF/genericui/module-resources/") ||
                            normName == "META-INF/genericui-module-resources.json"
                        ) && normName.endsWith(".json", ignoreCase = true)
                    if (isModuleResJson) {
                        val jsonText = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                        parseJsonManifest(jsonText)?.let { manifests.add(it) }
                    }
                }
            }
        }
        return manifests
    }

    fun readFromDirectory(dir: File): List<ModuleManifest> {
        if (!dir.isDirectory) return emptyList()
        val manifests = mutableListOf<ModuleManifest>()
        val directCandidates = mutableListOf<File>()
        listOf(
            File(dir, "META-INF/genericui/module-resources"),
            File(dir, "default/resources/META-INF/genericui/module-resources"),
            dir
        ).filter { it.isDirectory }.forEach { sub ->
            sub.listFiles()?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?.forEach { directCandidates.add(it) }
        }
        listOf(
            File(dir, "manifest/genericui-module-resources.json"),
            File(dir, "genericui-module-resources.json")
        ).filter { it.isFile }.forEach { directCandidates.add(it) }

        val seenPaths = linkedSetOf<String>()
        for (file in directCandidates) {
            if (!seenPaths.add(file.canonicalPath)) continue
            parseJsonManifest(file.readText(Charsets.UTF_8))?.let { manifests.add(it) }
        }
        return manifests
    }

    fun parseJsonManifest(jsonText: String): ModuleManifest? {
        val headerStrings = linkedMapOf<String, String>()
        val headerScalars = linkedMapOf<String, String>()
        var foundEntriesArray = false
        var entryObjects: List<String> = emptyList()

        val n = jsonText.length
        var i = 0
        while (i < n && jsonText[i].isWhitespace()) i++
        if (i >= n || jsonText[i] != '{') return null
        i++
        val posOut = IntArray(1)
        while (i < n) {
            while (i < n && (jsonText[i].isWhitespace() || jsonText[i] == ',')) i++
            if (i >= n || jsonText[i] == '}') break
            if (jsonText[i] != '"') {
                i++
                continue
            }
            val key = decodeJsonStringAt(jsonText, i, posOut)
            i = posOut[0]
            while (i < n && jsonText[i].isWhitespace()) i++
            if (i >= n || jsonText[i] != ':') continue
            i++
            while (i < n && jsonText[i].isWhitespace()) i++
            if (i >= n) break
            when (jsonText[i]) {
                '"' -> {
                    headerStrings[key] = decodeJsonStringAt(jsonText, i, posOut)
                    i = posOut[0]
                }
                '[' -> {
                    if (key == "entries") {
                        foundEntriesArray = true
                        entryObjects = splitJsonObjectsInArray(jsonText, i)
                    }
                    i = skipJsonComposite(jsonText, i)
                }
                '{' -> {
                    i = skipJsonComposite(jsonText, i)
                }
                else -> {
                    val tokStart = i
                    while (i < n && jsonText[i] != ',' && jsonText[i] != '}' && !jsonText[i].isWhitespace()) i++
                    val tok = jsonText.substring(tokStart, i)
                    if (tok.isNotEmpty() && tok != "null") {
                        headerScalars[key] = tok
                    }
                }
            }
        }
        if (!foundEntriesArray) return null

        val pkgName = headerStrings["package"].orEmpty()
        val schemaVersion = headerScalars["schemaVersion"]?.toIntOrNull() ?: 1
        val moduleByte = headerScalars["moduleByte"]?.toIntOrNull() ?: 0x7E
        val moduleGroupId = headerStrings["moduleGroupId"]?.takeIf { it.isNotEmpty() }
            ?: inferModuleGroupId(pkgName, moduleByte)
        val scopePrefix = headerStrings["scopePrefix"]?.takeIf { it.isNotEmpty() }
            ?: inferScopePrefix(pkgName, moduleByte)
        val scopeId = headerStrings["scopeId"]?.takeIf { it.isNotEmpty() }
            ?: moduleGroupId.ifEmpty { pkgName.ifEmpty { "genericui-upstream" } }

        val parsedEntries = ArrayList<ManifestEntry>(entryObjects.size)

        for (obj in entryObjects) {
            val objFields = parseJsonObject(obj)
            val type = objFields.strings["type"] ?: continue
            val name = objFields.strings["name"] ?: continue
            val id = objFields.scalars["id"]?.toIntOrNull()
            val relFromJson = objFields.strings["relativePath"]
            val canonicalFromJson = objFields.strings["canonicalRelativePath"]
            val format = objFields.strings["format"]
                ?: relFromJson?.substringAfterLast('.', "")
                ?: if (type == "DRAWABLE") "svg" else ""
            val moduleLocalRel = when {
                !relFromJson.isNullOrBlank() -> stripScopePrefix(relFromJson)
                !canonicalFromJson.isNullOrBlank() -> stripScopePrefix(canonicalFromJson)
                else -> "${directoryNameForType(type)}/$name" + if (format.isNotEmpty()) ".$format" else ""
            }
            val canonicalRel = canonicalFromJson?.takeIf { it.isNotBlank() }
                ?: if (relFromJson != null && (relFromJson.startsWith("gui/") || relFromJson.startsWith("app/"))) {
                    relFromJson
                } else {
                    "$scopePrefix/${moduleLocalRel.trimStart('/')}"
                }
            val nativeKey = objFields.strings["nativeResourceKey"]?.takeIf { it.isNotBlank() }
                ?: sanitizeFlatIdentifier(moduleLocalRel)
            val libGroup = objFields.strings["nativeLibraryGroupId"] ?: moduleGroupId
            val svgB64 = objFields.strings["svgBase64"].orEmpty()
            val txtB64 = objFields.strings["textBase64"].orEmpty()
            val dataB64 = objFields.strings["base64Data"]?.takeIf { it.isNotEmpty() }
            val textContent = when {
                svgB64.isNotEmpty() -> runCatching {
                    String(Base64.getDecoder().decode(svgB64), Charsets.UTF_8)
                }.getOrNull()
                txtB64.isNotEmpty() -> runCatching {
                    String(Base64.getDecoder().decode(txtB64), Charsets.UTF_8)
                }.getOrNull()
                else -> null
            }
            val effectiveB64 = dataB64
                ?: svgB64.takeIf { it.isNotEmpty() }
                ?: txtB64.takeIf { it.isNotEmpty() }
            val rawValue = objFields.strings["value"]
            val vbW = objFields.scalars["viewBoxWidth"]?.toFloatOrNull() ?: 24f
            val vbH = objFields.scalars["viewBoxHeight"]?.toFloatOrNull() ?: 24f

            parsedEntries.add(
                ManifestEntry(
                    type = type,
                    name = name,
                    id = id,
                    moduleLocalRelativePath = moduleLocalRel,
                    canonicalRelativePath = canonicalRel,
                    nativeResourceKey = nativeKey,
                    nativeLibraryGroupId = libGroup,
                    scopePrefix = scopePrefix,
                    format = format,
                    rawValue = rawValue,
                    textContent = textContent,
                    base64Data = effectiveB64,
                    viewBoxWidth = vbW,
                    viewBoxHeight = vbH
                )
            )
        }

        return ModuleManifest(
            schemaVersion = schemaVersion,
            scopeId = scopeId,
            moduleByte = moduleByte,
            moduleGroupId = moduleGroupId,
            scopePrefix = scopePrefix,
            packageName = pkgName,
            entries = parsedEntries
        )
    }

    fun stripScopePrefix(path: String): String {
        val clean = path.trim().replace('\\', '/').trimStart('/')
        if (clean.startsWith("gui/ext/")) {
            val rest = clean.removePrefix("gui/ext/")
            val firstSlash = rest.indexOf('/')
            if (firstSlash >= 0) {
                val secondSlash = rest.indexOf('/', firstSlash + 1)
                if (secondSlash >= 0 && secondSlash + 1 < rest.length) {
                    return rest.substring(secondSlash + 1)
                }
                if (firstSlash + 1 < rest.length) {
                    return rest.substring(firstSlash + 1)
                }
            }
            return rest
        }
        if (clean.startsWith("gui/")) return clean.removePrefix("gui/")
        if (clean.startsWith("app/")) return clean.removePrefix("app/")
        return clean
    }

    fun sanitizeFlatIdentifier(raw: String): String {
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

    private fun inferModuleGroupId(pkgName: String, moduleByte: Int): String {
        if (moduleByte == 0x7E || pkgName == "com.nexustools.genericui.res") {
            return "genericui"
        }
        if (pkgName.startsWith("com.nexustools.genericui.ext.")) {
            val extSegment = pkgName.removePrefix("com.nexustools.genericui.ext.")
                .substringBefore('.')
                .lowercase(Locale.US)
            if (extSegment.isNotEmpty()) {
                return "genericui-ext-$extSegment"
            }
        }
        return ""
    }

    private fun inferScopePrefix(pkgName: String, moduleByte: Int): String {
        if (moduleByte == 0x7E || pkgName == "com.nexustools.genericui.res") {
            return "gui"
        }
        if (pkgName.startsWith("com.nexustools.genericui.ext.")) {
            val extSegment = pkgName.removePrefix("com.nexustools.genericui.ext.")
                .substringBefore('.')
                .lowercase(Locale.US)
            if (extSegment.isNotEmpty()) {
                return "gui/ext/ui/$extSegment"
            }
        }
        return if (moduleByte in 0x60..0x7D) "gui/ext" else "gui"
    }

    private fun directoryNameForType(type: String): String = when (type.uppercase(Locale.US)) {
        "DRAWABLE" -> "drawable"
        "STRING" -> "string"
        "COLOR" -> "color"
        "DIMEN" -> "dimen"
        "INTEGER" -> "integer"
        "BOOL" -> "bool"
        "RAW" -> "raw"
        "FONT" -> "font"
        "XML" -> "xml"
        "MENU" -> "menu"
        "LAYOUT" -> "layout"
        "STYLE" -> "style"
        else -> "raw"
    }

    private fun splitJsonObjectsInArray(json: String, arrayStartIdx: Int): List<String> {
        val objects = mutableListOf<String>()
        var inString = false
        var escaped = false
        var depth = 0
        var objStart = -1
        for (i in (arrayStartIdx + 1) until json.length) {
            val ch = json[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objStart >= 0) {
                        objects.add(json.substring(objStart, i + 1))
                        objStart = -1
                    }
                }
                ']' -> {
                    if (depth <= 0) break
                }
            }
        }
        return objects
    }

    internal class ParsedJsonObject(
        val strings: Map<String, String>,
        val scalars: Map<String, String>,
        val stringArrays: Map<String, List<String>>
    )

    internal fun parseJsonObject(json: String, startIndex: Int = 0): ParsedJsonObject {
        val strings = linkedMapOf<String, String>()
        val scalars = linkedMapOf<String, String>()
        val stringArrays = linkedMapOf<String, List<String>>()
        val n = json.length
        var i = startIndex
        while (i < n && json[i].isWhitespace()) i++
        if (i < n && json[i] == '{') i++
        val posOut = IntArray(1)
        while (i < n) {
            while (i < n && (json[i].isWhitespace() || json[i] == ',')) i++
            if (i >= n || json[i] == '}') break
            if (json[i] != '"') {
                i++
                continue
            }
            val key = decodeJsonStringAt(json, i, posOut)
            i = posOut[0]
            while (i < n && json[i].isWhitespace()) i++
            if (i >= n || json[i] != ':') continue
            i++
            while (i < n && json[i].isWhitespace()) i++
            if (i >= n) break
            when (json[i]) {
                '"' -> {
                    strings[key] = decodeJsonStringAt(json, i, posOut)
                    i = posOut[0]
                }
                '[' -> {
                    stringArrays[key] = parseJsonStringArrayAt(json, i, posOut)
                    i = posOut[0]
                }
                '{' -> {
                    i = skipJsonComposite(json, i)
                }
                else -> {
                    val tokStart = i
                    while (i < n && json[i] != ',' && json[i] != '}' && !json[i].isWhitespace()) i++
                    val tok = json.substring(tokStart, i)
                    if (tok.isNotEmpty() && tok != "null") {
                        scalars[key] = tok
                    }
                }
            }
        }
        return ParsedJsonObject(strings = strings, scalars = scalars, stringArrays = stringArrays)
    }

    private fun decodeJsonStringAt(json: String, quoteIndex: Int, nextPosOut: IntArray): String {
        var i = quoteIndex + 1
        val n = json.length
        var sb: StringBuilder? = null
        var chunkStart = i
        while (i < n) {
            val ch = json[i]
            if (ch == '"') {
                val result = if (sb == null) {
                    json.substring(chunkStart, i)
                } else {
                    sb.append(json, chunkStart, i)
                    sb.toString()
                }
                nextPosOut[0] = i + 1
                return result
            }
            if (ch == '\\') {
                val builder = sb ?: StringBuilder().also { sb = it }
                if (i > chunkStart) {
                    builder.append(json, chunkStart, i)
                }
                i++
                if (i >= n) break
                when (val esc = json[i]) {
                    '"' -> builder.append('"')
                    '\\' -> builder.append('\\')
                    '/' -> builder.append('/')
                    'b' -> builder.append('\b')
                    'f' -> builder.append('\u000C')
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    't' -> builder.append('\t')
                    'u' -> {
                        if (i + 4 < n) {
                            val code = json.substring(i + 1, i + 5).toIntOrNull(16)
                            if (code != null) {
                                builder.append(code.toChar())
                                i += 4
                            } else {
                                builder.append("\\u")
                            }
                        } else {
                            builder.append("\\u")
                        }
                    }
                    else -> builder.append(esc)
                }
                i++
                chunkStart = i
                continue
            }
            i++
        }
        nextPosOut[0] = n
        return if (sb == null) {
            json.substring(chunkStart.coerceAtMost(n), n)
        } else {
            sb!!.append(json, chunkStart.coerceAtMost(n), n).toString()
        }
    }

    private fun parseJsonStringArrayAt(json: String, arrayStartIndex: Int, nextPosOut: IntArray): List<String> {
        val items = mutableListOf<String>()
        var i = arrayStartIndex + 1
        val n = json.length
        while (i < n) {
            while (i < n && (json[i].isWhitespace() || json[i] == ',')) i++
            if (i >= n) break
            when (json[i]) {
                ']' -> {
                    nextPosOut[0] = i + 1
                    return items
                }
                '"' -> {
                    val item = decodeJsonStringAt(json, i, nextPosOut)
                    if (item.isNotEmpty()) items.add(item)
                    i = nextPosOut[0]
                }
                '{', '[' -> {
                    i = skipJsonComposite(json, i)
                }
                else -> {
                    while (i < n && json[i] != ',' && json[i] != ']' && !json[i].isWhitespace()) i++
                }
            }
        }
        nextPosOut[0] = n
        return items
    }

    private fun skipJsonComposite(json: String, startIndex: Int): Int {
        var depth = 0
        var i = startIndex
        val n = json.length
        var inString = false
        var escaped = false
        while (i < n) {
            val ch = json[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                i++
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth <= 0) return i + 1
                }
            }
            i++
        }
        return n
    }

    fun collectResourcePayloadsForGroup(
        sources: Collection<File>,
        targetGroupId: String
    ): Map<String, ByteArray> {
        val normalizedTarget = targetGroupId.trim()
        val manifests = readManifestsFromSources(sources)
        val payloads = linkedMapOf<String, ByteArray>()
        if (manifests.isEmpty()) return payloads

        for (manifest in manifests) {
            for (entry in manifest.entries) {
                val entryGroup = entry.nativeLibraryGroupId.trim().ifEmpty { manifest.moduleGroupId.trim() }
                if (entryGroup != normalizedTarget) continue
                val key = entry.nativeResourceKey.trim()
                if (key.isEmpty()) continue
                val decoded = entry.decodePayloadBytes()
                    ?: findPayloadBytesInSources(sources, entry.moduleLocalRelativePath, entry.canonicalRelativePath)
                if (decoded != null) {
                    payloads.putIfAbsent(key, decoded)
                }
            }
        }
        return payloads
    }

    private fun findPayloadBytesInSources(
        sources: Collection<File>,
        moduleLocalRelativePath: String,
        canonicalRelativePath: String
    ): ByteArray? {
        val relCandidates = linkedSetOf(
            moduleLocalRelativePath.trim().replace('\\', '/').trimStart('/'),
            "default/resources/${moduleLocalRelativePath.trim().replace('\\', '/').trimStart('/')}",
            canonicalRelativePath.trim().replace('\\', '/').trimStart('/'),
            "default/resources/${canonicalRelativePath.trim().replace('\\', '/').trimStart('/')}"
        ).filter { it.isNotEmpty() }

        for (source in sources) {
            if (!source.exists()) continue
            if (source.isDirectory) {
                for (rel in relCandidates) {
                    val f = File(source, rel)
                    if (f.isFile) return f.readBytes()
                }
            } else if (source.isFile && (source.extension.equals("klib", ignoreCase = true) ||
                    source.extension.equals("jar", ignoreCase = true) ||
                    source.extension.equals("zip", ignoreCase = true))
            ) {
                val found = runCatching {
                    ZipFile(source).use { zip ->
                        for (rel in relCandidates) {
                            val entry = zip.getEntry(rel)
                            if (entry != null && !entry.isDirectory) {
                                return@use zip.getInputStream(entry).readBytes()
                            }
                        }
                        null
                    }
                }.getOrNull()
                if (found != null) return found
            }
        }
        return null
    }

}
