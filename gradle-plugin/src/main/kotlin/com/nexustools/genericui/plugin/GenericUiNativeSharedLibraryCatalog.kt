package com.nexustools.genericui.plugin

import java.io.File
import java.io.StringReader
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipFile

/**
 * Maps resolved Kotlin/Native klibs on native desktop targets (`macos_arm64`, `linux_x64`,
 * `linux_arm64`, `mingw_x64`) into shared library groups (`genericui`, `genericui-<backend>`,
 * `njs`, `genericui-ext-<ext>`, `genericui-ext-<ext>-dsl`, `genericui-ext-dsl`, `genericui-tests`)
 * while leaving consumer application klibs uncached so they compile directly into the final binary.
 */
object GenericUiNativeSharedLibraryCatalog {

    const val CORE_GROUP_ID: String = "genericui"
    const val NJS_GROUP_ID: String = "njs"
    const val TESTS_GROUP_ID: String = "genericui-tests"

    private val SUPPORTED_NATIVE_DESKTOP_TARGETS: Set<String> = setOf(
        "macos_arm64",
        "macos_x64",
        "linux_x64",
        "linux_arm64",
        "mingw_x64"
    )

    private val GENERICUI_CORE_UNIQUE_NAMES: Set<String> = setOf(
        "stdlib",
        "com.nexustools.genericui:common",
        "com.nexustools.genericui:system"
    )

    data class KlibManifestInfo(
        val file: File,
        val uniqueName: String,
        val depends: List<String>,
        val linkerOpts: List<String>,
        val nativeTargets: List<String>,
        val isInterop: Boolean
    )

    data class SharedLibraryGroupPlan(
        val groupId: String,
        val sharedLibraryFileName: String,
        val klibs: List<KlibManifestInfo>,
        val upstreamGroupIds: List<String>
    )

    data class SharedLibraryPlan(
        val konanTargetName: String,
        val backendVariant: String,
        val allResolvedKlibs: List<KlibManifestInfo>,
        val cachedKlibsOrdered: List<KlibManifestInfo>,
        val uncachedKlibs: List<KlibManifestInfo>,
        val groupsOrdered: List<SharedLibraryGroupPlan>,
        val klibToGroupId: Map<String, String>
    )

    fun isNativeDesktopKonanTarget(konanTargetName: String): Boolean {
        return konanTargetName in SUPPORTED_NATIVE_DESKTOP_TARGETS
    }

    fun isWindowsKonanTarget(konanTargetName: String): Boolean {
        return konanTargetName.startsWith("mingw")
    }

    fun isMacOsKonanTarget(konanTargetName: String): Boolean {
        return konanTargetName.startsWith("macos")
    }

    fun isLinuxKonanTarget(konanTargetName: String): Boolean {
        return konanTargetName.startsWith("linux")
    }

    fun isPlatformKlibUniqueName(uniqueName: String): Boolean {
        return uniqueName.startsWith("org.jetbrains.kotlin.native.platform.") ||
            uniqueName.startsWith("org.jetbrains.kotlin.native.llvm.")
    }

    fun backendGroupId(konanTargetName: String, backendVariant: String): String {
        return when {
            konanTargetName.startsWith("macos") -> "genericui-macos"
            konanTargetName.startsWith("mingw") -> "genericui-windows"
            konanTargetName.startsWith("linux") -> {
                when {
                    backendVariant.equals("kde", ignoreCase = true) -> "genericui-kde"
                    backendVariant.equals("linux", ignoreCase = true) -> "genericui-linux"
                    else -> "genericui-gtk"
                }
            }
            else -> throw IllegalArgumentException("Unsupported native desktop target '$konanTargetName'")
        }
    }

    /**
     * Returns the emitted shared library file name for [groupId] on [konanTargetName].
     * Windows DLLs intentionally omit the `lib` prefix (`genericui.dll`, `genericui-windows.dll`, etc.).
     */
    fun sharedLibraryFileName(groupId: String, konanTargetName: String): String {
        return when {
            konanTargetName.startsWith("macos") -> "lib$groupId.dylib"
            konanTargetName.startsWith("linux") -> "lib$groupId.so"
            konanTargetName.startsWith("mingw") -> "$groupId.dll"
            else -> throw IllegalArgumentException("Unsupported native desktop target '$konanTargetName'")
        }
    }

    /**
     * Replaces colon (`:`) characters in a klib `uniqueName` with underscore (`_`) so file/directory
     * names used for Kotlin/Native compiler caches remain valid on Windows NTFS.
     */
    fun cacheStem(uniqueName: String): String = uniqueName.replace(':', '_')

    /**
     * Matches Kotlin/Native `CachedLibrariesKt.getArtifactName` (`Family.dynamicPrefix + cacheName + "." + dynamicSuffix`)
     * with `:` replaced by `_` for Windows filesystem compatibility.
     */
    fun cacheArtifactFileName(uniqueName: String, konanTargetName: String): String {
        val cacheName = "${cacheStem(uniqueName)}-cache"
        return when {
            konanTargetName.startsWith("macos") -> "lib$cacheName.dylib"
            konanTargetName.startsWith("linux") -> "lib$cacheName.so"
            konanTargetName.startsWith("mingw") -> "$cacheName.dll"
            else -> throw IllegalArgumentException("Unsupported native desktop target '$konanTargetName'")
        }
    }

    /**
     * Classifies a single non-platform klib into its logical shared library group ID, or `null`
     * if it belongs to consumer/third-party code that stays compiled directly into the consumer binary.
     */
    fun classifyKlibGroup(
        uniqueName: String,
        konanTargetName: String,
        backendVariant: String
    ): String? {
        if (!isNativeDesktopKonanTarget(konanTargetName)) return null
        if (isPlatformKlibUniqueName(uniqueName)) return null

        if (uniqueName in GENERICUI_CORE_UNIQUE_NAMES) {
            return CORE_GROUP_ID
        }
        if (uniqueName.startsWith("org.jetbrains.kotlinx:atomicfu") ||
            uniqueName.startsWith("org.jetbrains.kotlinx:kotlinx-coroutines-core")
        ) {
            return CORE_GROUP_ID
        }
        if (uniqueName == "com.nexustools.genericui:apple") {
            return if (konanTargetName.startsWith("macos")) CORE_GROUP_ID else null
        }
        if (uniqueName == "com.nexustools.genericui:desktop") {
            return backendGroupId(konanTargetName, backendVariant)
        }
        if (uniqueName.startsWith("com.nexustools.njs:")) {
            return NJS_GROUP_ID
        }
        if (uniqueName == "com.nexustools.genericui:tests" ||
            uniqueName == "kotlin-test" ||
            uniqueName.startsWith("org.jetbrains.kotlin:kotlin-test")
        ) {
            return TESTS_GROUP_ID
        }
        if (uniqueName == "com.nexustools.genericui.extensions.ui:dsl") {
            return "genericui-ext-dsl"
        }
        if (uniqueName.startsWith("com.nexustools.genericui.extensions.")) {
            val colonIdx = uniqueName.indexOf(':')
            if (colonIdx > 0 && colonIdx < uniqueName.length - 1) {
                val groupPart = uniqueName.substring(0, colonIdx)
                val artifactPart = uniqueName.substring(colonIdx + 1)
                val sub = groupPart.removePrefix("com.nexustools.genericui.extensions.")
                return if (artifactPart == "dsl" && sub.contains('.')) {
                    val extLeaf = sub.substringAfterLast('.').lowercase(Locale.ROOT)
                    "genericui-ext-$extLeaf-dsl"
                } else {
                    "genericui-ext-${artifactPart.lowercase(Locale.ROOT)}"
                }
            }
        }
        return null
    }

    /**
     * Reads `default/manifest` from either an unpacked klib directory or a `.klib` archive.
     */
    fun readManifest(klibFile: File, konanTargetName: String): KlibManifestInfo? {
        if (!klibFile.exists()) return null
        val manifestText = if (klibFile.isDirectory) {
            val manifestFile = File(klibFile, "default/manifest")
            if (!manifestFile.isFile) return null
            manifestFile.readText(Charsets.UTF_8)
        } else if (klibFile.isFile && klibFile.extension.equals("klib", ignoreCase = true)) {
            runCatching {
                ZipFile(klibFile).use { zip ->
                    val entry = zip.getEntry("default/manifest") ?: return@use null
                    zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                }
            }.getOrNull() ?: return null
        } else {
            return null
        }

        val props = Properties()
        props.load(StringReader(manifestText))
        val uniqueName = props.getProperty("unique_name")?.trim().orEmpty()
        if (uniqueName.isEmpty()) return null

        val depends = splitWhitespaceTokens(props.getProperty("depends"))
        val linkerOpts = buildList {
            addAll(splitShellTokens(props.getProperty("linkerOpts")))
            addAll(splitShellTokens(props.getProperty("linkerOpts.$konanTargetName")))
        }
        val nativeTargets = splitWhitespaceTokens(props.getProperty("native_targets"))
        val isInterop = props.getProperty("interop")?.trim()?.equals("true", ignoreCase = true) == true
        return KlibManifestInfo(
            file = klibFile,
            uniqueName = uniqueName,
            depends = depends,
            linkerOpts = linkerOpts,
            nativeTargets = nativeTargets,
            isInterop = isInterop
        )
    }

    /**
     * Computes a topologically ordered plan of shared library groups and their constituent klibs.
     * Any klib that depends directly or transitively on an uncached klib is automatically excluded
     * from caching to preserve Kotlin/Native's closed-world cache invariant.
     *
     * When [platformKlibsRootDir] is provided, transitively referenced Kotlin/Native platform klibs
     * (`org.jetbrains.kotlin.native.platform.*`) are discovered under [platformKlibsRootDir] and
     * assigned to the dominating cached shared library group (`consumer`, backend, or `genericui`).
     */
    fun planSharedLibraries(
        libraryFiles: Collection<File>,
        konanTargetName: String,
        backendVariant: String,
        platformKlibsRootDir: File? = null
    ): SharedLibraryPlan {
        val byUniqueName = linkedMapOf<String, KlibManifestInfo>()
        for (file in libraryFiles) {
            val info = readManifest(file, konanTargetName) ?: continue
            if (isPlatformKlibUniqueName(info.uniqueName)) continue
            if (!byUniqueName.containsKey(info.uniqueName)) {
                byUniqueName[info.uniqueName] = info.copy(file = info.file.absoluteFile.normalize())
            }
        }

        val groupByUniqueName = mutableMapOf<String, String?>()
        for ((uniqueName, _) in byUniqueName) {
            groupByUniqueName[uniqueName] = classifyKlibGroup(
                uniqueName = uniqueName,
                konanTargetName = konanTargetName,
                backendVariant = backendVariant
            )
        }

        // Enforce topological closedness: no cached klib may depend on an uncached non-platform klib.
        var changed = true
        while (changed) {
            changed = false
            for ((uniqueName, info) in byUniqueName) {
                if (groupByUniqueName[uniqueName] == null) continue
                val hasUncachedDep = info.depends.any { dep ->
                    !isPlatformKlibUniqueName(dep) &&
                        byUniqueName.containsKey(dep) &&
                        groupByUniqueName[dep] == null
                }
                if (hasUncachedDep) {
                    groupByUniqueName[uniqueName] = null
                    changed = true
                }
            }
        }

        // Discover transitively reachable platform klibs from cached non-platform klibs and assign
        // each to its dominating group without introducing cross-group cycles.
        if (platformKlibsRootDir != null && platformKlibsRootDir.isDirectory) {
            val cachedNonPlatformGroups = groupByUniqueName.values.filterNotNull().toSet()
            val nonPlatDirectUpstream = mutableMapOf<String, MutableSet<String>>()
            for (gid in cachedNonPlatformGroups) {
                nonPlatDirectUpstream[gid] = linkedSetOf()
            }
            for ((uniqueName, info) in byUniqueName) {
                val gid = groupByUniqueName[uniqueName] ?: continue
                for (dep in info.depends) {
                    val depGid = groupByUniqueName[dep]
                    if (depGid != null && depGid != gid) {
                        nonPlatDirectUpstream.getValue(gid).add(depGid)
                    }
                }
            }
            val nonPlatformUpstreamByGroup = mutableMapOf<String, Set<String>>()
            for (gid in cachedNonPlatformGroups) {
                val reachableUp = linkedSetOf<String>()
                val queue = ArrayDeque<String>()
                for (direct in nonPlatDirectUpstream[gid].orEmpty()) {
                    if (reachableUp.add(direct)) queue.addLast(direct)
                }
                while (queue.isNotEmpty()) {
                    val cur = queue.removeFirst()
                    for (up in nonPlatDirectUpstream[cur].orEmpty()) {
                        if (reachableUp.add(up)) queue.addLast(up)
                    }
                }
                nonPlatformUpstreamByGroup[gid] = reachableUp
            }

            val platformConsumers = linkedMapOf<String, MutableSet<String>>()
            val cachedNonPlatformEntries = byUniqueName.entries
                .filter { groupByUniqueName[it.key] != null }
                .toList()
            for ((uniqueName, info) in cachedNonPlatformEntries) {
                val gid = groupByUniqueName.getValue(uniqueName)!!
                val visitedPlat = hashSetOf<String>()
                val queue = ArrayDeque<String>()
                for (dep in info.depends) {
                    if (isPlatformKlibUniqueName(dep) && visitedPlat.add(dep)) {
                        queue.addLast(dep)
                    }
                }
                while (queue.isNotEmpty()) {
                    val platName = queue.removeFirst()
                    val platInfo = byUniqueName[platName] ?: run {
                        val platDir = File(platformKlibsRootDir, platName)
                        readManifest(platDir, konanTargetName)?.let { loaded ->
                            loaded.copy(file = loaded.file.absoluteFile.normalize()).also {
                                byUniqueName[platName] = it
                            }
                        }
                    } ?: continue
                    platformConsumers.getOrPut(platName) { linkedSetOf() }.add(gid)
                    for (subDep in platInfo.depends) {
                        if (isPlatformKlibUniqueName(subDep) && visitedPlat.add(subDep)) {
                            queue.addLast(subDep)
                        }
                    }
                }
            }

            val backendGrp = if (isNativeDesktopKonanTarget(konanTargetName)) {
                backendGroupId(konanTargetName, backendVariant)
            } else {
                ""
            }
            for ((platName, consumers) in platformConsumers) {
                groupByUniqueName[platName] = when {
                    consumers.size == 1 -> consumers.first()
                    backendGrp.isNotEmpty() &&
                        backendGrp in cachedNonPlatformGroups &&
                        consumers.all { it == backendGrp || backendGrp in nonPlatformUpstreamByGroup[it].orEmpty() } ->
                        backendGrp
                    else -> CORE_GROUP_ID
                }
            }
        }

        // Kahn's topological sort on cached klibs with deterministic tie-breaking.
        val cachedMap = byUniqueName.filterKeys { groupByUniqueName[it] != null }
        val inDegree = mutableMapOf<String, Int>()
        val dependents = mutableMapOf<String, MutableList<String>>()
        for (name in cachedMap.keys) {
            inDegree[name] = 0
            dependents[name] = mutableListOf()
        }
        for ((name, info) in cachedMap) {
            for (dep in info.depends) {
                if (dep != name && cachedMap.containsKey(dep)) {
                    dependents[dep]!!.add(name)
                    inDegree[name] = (inDegree[name] ?: 0) + 1
                }
            }
        }

        val ready = cachedMap.keys
            .filter { (inDegree[it] ?: 0) == 0 }
            .sortedWith(klibOrderComparator(groupByUniqueName))
            .toMutableList()

        val cachedOrdered = mutableListOf<KlibManifestInfo>()
        while (ready.isNotEmpty()) {
            val next = ready.removeAt(0)
            cachedOrdered.add(cachedMap.getValue(next))
            for (child in dependents[next].orEmpty()) {
                val updated = (inDegree[child] ?: 1) - 1
                inDegree[child] = updated
                if (updated == 0) {
                    ready.add(child)
                    ready.sortWith(klibOrderComparator(groupByUniqueName))
                }
            }
        }
        check(cachedOrdered.size == cachedMap.size) {
            "Cyclic klib dependency detected among cached GenericUI libraries: ${cachedMap.keys - cachedOrdered.map { it.uniqueName }.toSet()}"
        }

        val uncached = byUniqueName.values.filter { groupByUniqueName[it.uniqueName] == null }

        // Group topological ordering based on klib edges across groups.
        val groupKlibs = linkedMapOf<String, MutableList<KlibManifestInfo>>()
        for (info in cachedOrdered) {
            val gid = groupByUniqueName.getValue(info.uniqueName)!!
            groupKlibs.getOrPut(gid) { mutableListOf() }.add(info)
        }

        val groupDirectUpstream = linkedMapOf<String, MutableSet<String>>()
        val groupDependents = linkedMapOf<String, MutableSet<String>>()
        val groupInDegree = linkedMapOf<String, Int>()
        for (gid in groupKlibs.keys) {
            groupDirectUpstream[gid] = linkedSetOf()
            groupDependents[gid] = linkedSetOf()
            groupInDegree[gid] = 0
        }
        for ((gid, members) in groupKlibs) {
            for (member in members) {
                for (dep in member.depends) {
                    val depGroup = groupByUniqueName[dep]
                    if (depGroup != null && depGroup != gid && groupKlibs.containsKey(depGroup)) {
                        if (groupDirectUpstream[gid]!!.add(depGroup)) {
                            groupDependents[depGroup]!!.add(gid)
                            groupInDegree[gid] = (groupInDegree[gid] ?: 0) + 1
                        }
                    }
                }
            }
        }

        val readyGroups = groupKlibs.keys
            .filter { (groupInDegree[it] ?: 0) == 0 }
            .sortedWith(groupIdComparator())
            .toMutableList()

        val groupIdsOrdered = mutableListOf<String>()
        while (readyGroups.isNotEmpty()) {
            val g = readyGroups.removeAt(0)
            groupIdsOrdered.add(g)
            for (child in groupDependents[g].orEmpty()) {
                val updated = (groupInDegree[child] ?: 1) - 1
                groupInDegree[child] = updated
                if (updated == 0) {
                    readyGroups.add(child)
                    readyGroups.sortWith(groupIdComparator())
                }
            }
        }
        check(groupIdsOrdered.size == groupKlibs.size) {
            "Cyclic dependency detected among shared library groups: ${groupKlibs.keys - groupIdsOrdered.toSet()}"
        }

        val groupTransitiveUpstream = mutableMapOf<String, Set<String>>()
        for (gid in groupIdsOrdered) {
            val transitive = linkedSetOf<String>()
            for (direct in groupDirectUpstream[gid].orEmpty()) {
                transitive.addAll(groupTransitiveUpstream[direct].orEmpty())
                transitive.add(direct)
            }
            groupTransitiveUpstream[gid] = transitive
        }

        val groupIndex = groupIdsOrdered.withIndex().associate { it.value to it.index }
        val groupsOrdered = groupIdsOrdered.map { gid ->
            val orderedUpstream = groupTransitiveUpstream[gid].orEmpty()
                .sortedBy { groupIndex[it] ?: Int.MAX_VALUE }
            SharedLibraryGroupPlan(
                groupId = gid,
                sharedLibraryFileName = sharedLibraryFileName(gid, konanTargetName),
                klibs = groupKlibs.getValue(gid),
                upstreamGroupIds = orderedUpstream
            )
        }

        val resolvedGroupMap = buildMap {
            for ((k, v) in groupByUniqueName) {
                if (v != null) put(k, v)
            }
        }
        return SharedLibraryPlan(
            konanTargetName = konanTargetName,
            backendVariant = backendVariant,
            allResolvedKlibs = byUniqueName.values.toList(),
            cachedKlibsOrdered = cachedOrdered,
            uncachedKlibs = uncached,
            groupsOrdered = groupsOrdered,
            klibToGroupId = resolvedGroupMap
        )
    }

    private fun groupRank(groupId: String): Int {
        return when {
            groupId == CORE_GROUP_ID -> 0
            groupId.startsWith("genericui-macos") ||
                groupId.startsWith("genericui-windows") ||
                groupId.startsWith("genericui-gtk") ||
                groupId.startsWith("genericui-kde") ||
                groupId.startsWith("genericui-linux") -> 1
            groupId == NJS_GROUP_ID -> 2
            groupId == "genericui-ext-dsl" -> 3
            groupId.startsWith("genericui-ext-") && !groupId.endsWith("-dsl") -> 4
            groupId.startsWith("genericui-ext-") && groupId.endsWith("-dsl") -> 5
            groupId == TESTS_GROUP_ID -> 6
            else -> 7
        }
    }

    private fun groupIdComparator(): Comparator<String> = Comparator { a, b ->
        val ra = groupRank(a)
        val rb = groupRank(b)
        if (ra != rb) ra.compareTo(rb) else a.compareTo(b)
    }

    private fun klibRank(uniqueName: String, groupId: String?): Int {
        return when {
            uniqueName == "stdlib" -> 0
            isPlatformKlibUniqueName(uniqueName) -> 1
            uniqueName.startsWith("org.jetbrains.kotlinx:atomicfu-cinterop-") -> 2
            uniqueName.startsWith("org.jetbrains.kotlinx:atomicfu") -> 3
            uniqueName.startsWith("org.jetbrains.kotlinx:kotlinx-coroutines-core") -> 4
            uniqueName == "com.nexustools.genericui:common" -> 5
            uniqueName == "com.nexustools.genericui:system" -> 6
            uniqueName == "com.nexustools.genericui:apple" -> 7
            groupId != null -> 10 + groupRank(groupId)
            else -> 100
        }
    }

    private fun klibOrderComparator(groupByUniqueName: Map<String, String?>): Comparator<String> =
        Comparator { a, b ->
            val ra = klibRank(a, groupByUniqueName[a])
            val rb = klibRank(b, groupByUniqueName[b])
            if (ra != rb) ra.compareTo(rb) else a.compareTo(b)
        }

    internal fun splitWhitespaceTokens(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    internal fun splitShellTokens(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoteChar = '\u0000'
        var escaping = false
        for (ch in raw.trim()) {
            when {
                escaping -> {
                    current.append(ch)
                    escaping = false
                }
                ch == '\\' && quoteChar != '\'' -> {
                    escaping = true
                }
                quoteChar != '\u0000' -> {
                    if (ch == quoteChar) {
                        quoteChar = '\u0000'
                    } else {
                        current.append(ch)
                    }
                }
                ch == '"' || ch == '\'' -> {
                    quoteChar = ch
                }
                ch.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }
}
