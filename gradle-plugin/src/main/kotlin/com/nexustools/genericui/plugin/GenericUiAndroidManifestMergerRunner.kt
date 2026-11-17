package com.nexustools.genericui.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.net.URLClassLoader

/**
 * Resolves `com.android.tools.build:manifest-merger` (and its transitive runtime JARs) via a
 * detached Gradle configuration or local `~/.gradle/caches/modules-2/files-2.1` lookup and runs
 * Google's `ManifestMerger2` inside an isolated `URLClassLoader`.
 */
object GenericUiAndroidManifestMergerRunner {

    const val DEFAULT_MERGER_VERSION: String = "32.1.1"

    /**
     * Merges [baseManifestFile] with higher-priority [overlayFiles] via `ManifestMerger2` using
     * `MergeType.APPLICATION`, substitutes [placeholders], logs warnings, and returns the final XML.
     */
    fun merge(
        baseManifestFile: File,
        overlayFiles: List<File>,
        placeholders: Map<String, String> = emptyMap(),
        mergerVersion: String = DEFAULT_MERGER_VERSION,
        minSdk: Int? = null,
        targetSdk: Int? = null,
        project: Project? = null,
        logger: Logger? = null
    ): String {
        val existingOverlays = overlayFiles.filter { it.isFile }
        if (existingOverlays.isEmpty()) {
            return GenericUiAndroidManifestXmlGenerator.expandPlaceholdersInXml(
                baseManifestFile.readText(),
                placeholders
            )
        }

        val jarFiles = resolveMergerJars(project, mergerVersion.trim().ifEmpty { DEFAULT_MERGER_VERSION })
        val urls = jarFiles.map { it.toURI().toURL() }.toTypedArray()

        val mergedXml = URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { cl ->
            runMergerInClassLoader(
                cl = cl,
                baseManifestFile = baseManifestFile,
                overlayFiles = existingOverlays,
                placeholders = placeholders,
                minSdk = minSdk,
                targetSdk = targetSdk,
                logger = logger
            )
        }

        return GenericUiAndroidManifestXmlGenerator.expandPlaceholdersInXml(mergedXml, placeholders)
    }

    private fun runMergerInClassLoader(
        cl: ClassLoader,
        baseManifestFile: File,
        overlayFiles: List<File>,
        placeholders: Map<String, String>,
        minSdk: Int?,
        targetSdk: Int?,
        logger: Logger?
    ): String {
        val mergerClass = Class.forName("com.android.manifmerger.ManifestMerger2", true, cl)
        val loggerIface = Class.forName("com.android.utils.ILogger", true, cl)
        val mergeTypeClass = Class.forName("com.android.manifmerger.ManifestMerger2\$MergeType", true, cl)
        val featureClass = Class.forName("com.android.manifmerger.ManifestMerger2\$Invoker\$Feature", true, cl)
        val mergedKindClass = Class.forName("com.android.manifmerger.MergingReport\$MergedManifestKind", true, cl)

        val recordedErrors = mutableListOf<String>()
        val loggerProxy = Proxy.newProxyInstance(cl, arrayOf(loggerIface)) { _, method, args ->
            val name = method.name
            val rawMsg = formatLoggerArgs(args, startIndex = if (name == "error") 1 else 0)
            when (name) {
                "error" -> {
                    val throwable = args?.getOrNull(0) as? Throwable
                    val fullMsg = if (throwable != null && throwable.message != null) {
                        "$rawMsg (${throwable.message})"
                    } else {
                        rawMsg
                    }
                    if (fullMsg.isNotBlank()) recordedErrors.add(fullMsg)
                    logger?.error("[ManifestMerger2] {}", fullMsg)
                }
                "warning" -> logger?.warn("[ManifestMerger2] {}", rawMsg)
                "info", "lifecycle", "quiet" -> logger?.info("[ManifestMerger2] {}", rawMsg)
                "verbose" -> logger?.debug("[ManifestMerger2] {}", rawMsg)
            }
            null
        }

        try {
            @Suppress("UNCHECKED_CAST")
            val appMergeType = java.lang.Enum.valueOf(mergeTypeClass as Class<out Enum<*>>, "APPLICATION")
            val newMergerMethod = mergerClass.getMethod(
                "newMerger",
                File::class.java,
                loggerIface,
                mergeTypeClass
            )
            val invoker = newMergerMethod.invoke(null, baseManifestFile, loggerProxy, appMergeType)

            invoker.javaClass
                .getMethod("addFlavorAndBuildTypeManifests", Array<File>::class.java)
                .invoke(invoker, overlayFiles.toTypedArray())

            if (placeholders.isNotEmpty()) {
                val phMap = LinkedHashMap<String, Any>()
                placeholders.forEach { (k, v) -> phMap[k] = v }
                invoker.javaClass
                    .getMethod("setPlaceHolderValues", Map::class.java)
                    .invoke(invoker, phMap)
            }

            if (minSdk != null || targetSdk != null) {
                runCatching {
                    val sysPropClass = Class.forName("com.android.manifmerger.ManifestSystemProperty", true, cl)
                    @Suppress("UNCHECKED_CAST")
                    val sysPropEnumClass = sysPropClass as Class<out Enum<*>>
                    val setOverrideMethod = invoker.javaClass.getMethod("setOverride", sysPropClass, String::class.java)
                    minSdk?.let {
                        val minProp = enumValueOrNull(sysPropEnumClass, "MIN_SDK_VERSION")
                        if (minProp != null) setOverrideMethod.invoke(invoker, minProp, it.toString())
                    }
                    targetSdk?.let {
                        val targetProp = enumValueOrNull(sysPropEnumClass, "TARGET_SDK_VERSION")
                        if (targetProp != null) setOverrideMethod.invoke(invoker, targetProp, it.toString())
                    }
                }
            }

            @Suppress("UNCHECKED_CAST")
            val featureEnumClass = featureClass as Class<out Enum<*>>
            val enabledFeatures = listOfNotNull(
                enumValueOrNull(featureEnumClass, "REMOVE_TOOLS_DECLARATIONS"),
                enumValueOrNull(featureEnumClass, "SUPPRESS_MANIFEST_PACKAGE_WARNING")
            )
            if (enabledFeatures.isNotEmpty()) {
                val typedArray = java.lang.reflect.Array.newInstance(featureClass, enabledFeatures.size)
                enabledFeatures.forEachIndexed { idx, enumVal ->
                    java.lang.reflect.Array.set(typedArray, idx, enumVal)
                }
                invoker.javaClass
                    .getMethod("withFeatures", typedArray.javaClass)
                    .invoke(invoker, typedArray)
            }

            val report = invoker.javaClass.getMethod("merge").invoke(invoker)
            report.javaClass.getMethod("log", loggerIface).invoke(report, loggerProxy)

            val result = report.javaClass.getMethod("getResult").invoke(report)
            val isError = result.javaClass.getMethod("isError").invoke(result) as? Boolean ?: false
            if (isError) {
                val reportStr = runCatching {
                    report.javaClass.getMethod("getReportString").invoke(report)?.toString()
                }.getOrNull().orEmpty()
                val recordMsgs = extractErrorRecordMessages(report)
                val details = buildList {
                    if (reportStr.isNotBlank()) add(reportStr)
                    addAll(recordMsgs)
                    addAll(recordedErrors)
                }.distinct().joinToString("\n")
                throw GradleException("Android ManifestMerger2 failed:\n$details")
            }

            @Suppress("UNCHECKED_CAST")
            val mergedKind = java.lang.Enum.valueOf(mergedKindClass as Class<out Enum<*>>, "MERGED")
            val doc = report.javaClass.getMethod("getMergedDocument", mergedKindClass).invoke(report, mergedKind)
            val xml = extractXmlText(doc)
                ?: run {
                    val xmlDoc = runCatching {
                        report.javaClass.getMethod("getMergedXmlDocument", mergedKindClass).invoke(report, mergedKind)
                    }.getOrNull()
                    extractXmlText(xmlDoc)
                }

            if (xml.isNullOrBlank()) {
                throw GradleException("Android ManifestMerger2 returned an empty merged manifest document.")
            }
            return xml
        } catch (ex: InvocationTargetException) {
            val target = ex.targetException ?: ex
            if (target is GradleException) throw target
            throw GradleException("Android ManifestMerger2 execution failed: ${target.message}", target)
        }
    }

    private fun extractXmlText(doc: Any?): String? {
        if (doc == null) return null
        if (doc is String) return doc
        val pretty = runCatching {
            doc.javaClass.getMethod("prettyPrint").invoke(doc) as? String
        }.getOrNull()
        return pretty ?: doc.toString()
    }

    private fun extractErrorRecordMessages(report: Any): List<String> {
        val records = runCatching {
            report.javaClass.getMethod("getLoggingRecords").invoke(report) as? Iterable<*>
        }.getOrNull() ?: return emptyList()
        val msgs = mutableListOf<String>()
        for (rec in records) {
            if (rec == null) continue
            val sev = runCatching { rec.javaClass.getMethod("getSeverity").invoke(rec)?.toString() }.getOrNull()
            if (sev == "ERROR") {
                val msg = runCatching { rec.javaClass.getMethod("getMessage").invoke(rec)?.toString() }.getOrNull()
                if (!msg.isNullOrBlank()) {
                    msgs.add(rec.toString())
                }
            }
        }
        return msgs
    }

    private fun enumValueOrNull(enumClass: Class<out Enum<*>>, constantName: String): Any? =
        runCatching { java.lang.Enum.valueOf(enumClass, constantName) }.getOrNull()

    private fun formatLoggerArgs(args: Array<out Any?>?, startIndex: Int): String {
        if (args == null || args.size <= startIndex) return ""
        val formatStr = args[startIndex]?.toString().orEmpty()
        val varArgs = args.getOrNull(startIndex + 1) as? Array<*>
        if (varArgs == null || varArgs.isEmpty()) return formatStr
        return try {
            String.format(formatStr, *varArgs)
        } catch (_: Throwable) {
            "$formatStr ${varArgs.joinToString(", ")}"
        }
    }

    /**
     * Resolves `com.android.tools.build:manifest-merger` and its transitive runtime JARs using a
     * detached Gradle configuration when repositories are available, falling back to local Gradle
     * module caches when running without repositories (such as `ProjectBuilder` unit tests).
     */
    internal fun resolveMergerJars(project: Project?, mergerVersion: String): List<File> {
        if (project != null && project.repositories.isNotEmpty()) {
            val fromGradle = runCatching {
                val dep = project.dependencies.create("com.android.tools.build:manifest-merger:$mergerVersion")
                val cfg = project.configurations.detachedConfiguration(dep).apply {
                    isTransitive = true
                    isCanBeConsumed = false
                    isCanBeResolved = true
                }
                cfg.resolve().filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
            }.getOrNull().orEmpty()
            if (fromGradle.any { it.name.startsWith("manifest-merger") }) {
                return fromGradle
            }
        }

        return resolveFromGradleCaches(project, mergerVersion)
    }

    private fun resolveFromGradleCaches(project: Project?, requestedVersion: String): List<File> {
        val candidateHomes = listOfNotNull(
            project?.gradle?.gradleUserHomeDir,
            System.getenv("GRADLE_USER_HOME")?.takeIf { it.isNotBlank() }?.let(::File),
            File(System.getProperty("user.home"), ".gradle")
        ).distinctBy { it.absolutePath }

        val moduleDirs = candidateHomes
            .map { File(it, "caches/modules-2/files-2.1") }
            .filter { it.isDirectory }

        var chosenFilesRoot: File? = null
        var mergerJar: File? = null
        var resolvedToolsVersion = requestedVersion

        for (root in moduleDirs) {
            val candidate = findArtifactJar(
                filesRoot = root,
                group = "com.android.tools.build",
                artifact = "manifest-merger",
                preferredVersion = requestedVersion
            )
            if (candidate != null) {
                chosenFilesRoot = root
                mergerJar = candidate.first
                resolvedToolsVersion = candidate.second
                break
            }
        }

        if (chosenFilesRoot == null || mergerJar == null) {
            throw GradleException(
                "Unable to resolve 'com.android.tools.build:manifest-merger:$requestedVersion'. " +
                    "Configure repositories (google(), mavenCentral()) or ensure Android Gradle Plugin " +
                    "artifacts are cached under ${moduleDirs.joinToString(", ") { it.absolutePath }}."
            )
        }

        val collected = linkedMapOf<String, File>()
        collected["com.android.tools.build:manifest-merger"] = mergerJar

        val androidToolsArtifacts = listOf(
            "com.android.tools" to "common",
            "com.android.tools" to "sdk-common",
            "com.android.tools" to "sdklib",
            "com.android.tools" to "annotations",
            "com.android.tools" to "repository",
            "com.android.tools" to "dvlib",
            "com.android.tools.layoutlib" to "layoutlib-api"
        )
        for ((grp, art) in androidToolsArtifacts) {
            findArtifactJarAcrossRoots(moduleDirs, grp, art, resolvedToolsVersion)?.let { (jar, _) ->
                collected["$grp:$art"] = jar
            }
        }

        val otherRuntimeDeps = listOf(
            "com.google.guava" to "guava",
            "com.google.guava" to "failureaccess",
            "com.google.guava" to "listenablefuture",
            "com.google.code.gson" to "gson",
            "net.sf.kxml" to "kxml2",
            "org.jetbrains.kotlin" to "kotlin-stdlib",
            "org.jetbrains.kotlin" to "kotlin-stdlib-jdk8",
            "org.jetbrains.kotlin" to "kotlin-stdlib-jdk7",
            "org.jetbrains" to "annotations",
            "com.google.errorprone" to "error_prone_annotations",
            "org.checkerframework" to "checker-qual",
            "com.google.j2objc" to "j2objc-annotations",
            "com.google.protobuf" to "protobuf-java",
            "jakarta.xml.bind" to "jakarta.xml.bind-api",
            "org.glassfish.jaxb" to "jaxb-runtime",
            "org.glassfish.jaxb" to "txw2",
            "com.sun.xml.fastinfoset" to "FastInfoset",
            "org.apache.commons" to "commons-compress",
            "org.apache.httpcomponents" to "httpclient",
            "org.apache.httpcomponents" to "httpcore",
            "commons-io" to "commons-io",
            "net.java.dev.jna" to "jna"
        )

        for ((grp, art) in otherRuntimeDeps) {
            val found = if (grp == "com.google.guava" && art == "guava") {
                findArtifactJarAcrossRoots(moduleDirs, grp, art, preferredVersion = null, preferSuffix = "-jre.jar", avoidSuffix = "-android.jar")
            } else {
                findArtifactJarAcrossRoots(moduleDirs, grp, art, preferredVersion = null, avoidSuffix = "-all.jar")
            }
            if (found != null) {
                collected["$grp:$art"] = found.first
            }
        }

        return collected.values.toList()
    }

    private fun findArtifactJarAcrossRoots(
        roots: List<File>,
        group: String,
        artifact: String,
        preferredVersion: String? = null,
        preferSuffix: String? = null,
        avoidSuffix: String? = null
    ): Pair<File, String>? {
        for (root in roots) {
            val found = findArtifactJar(root, group, artifact, preferredVersion, preferSuffix, avoidSuffix)
            if (found != null) return found
        }
        return null
    }

    private fun findArtifactJar(
        filesRoot: File,
        group: String,
        artifact: String,
        preferredVersion: String? = null,
        preferSuffix: String? = null,
        avoidSuffix: String? = null
    ): Pair<File, String>? {
        val artifactDir = File(filesRoot, "$group/$artifact")
        if (!artifactDir.isDirectory) return null

        val versionDirs = artifactDir.listFiles()?.filter { it.isDirectory }.orEmpty()
        if (versionDirs.isEmpty()) return null

        val sortedVersions = versionDirs.sortedWith(
            Comparator<File> { a, b -> compareVersionsDesc(a.name, b.name) }
        )

        val orderedCandidates = if (!preferredVersion.isNullOrBlank()) {
            val exact = sortedVersions.firstOrNull { it.name == preferredVersion }
            if (exact != null) listOf(exact) + sortedVersions.filter { it != exact } else sortedVersions
        } else {
            sortedVersions
        }

        for (vDir in orderedCandidates) {
            val jars = vDir.walkTopDown()
                .filter { f ->
                    f.isFile &&
                        f.extension.equals("jar", ignoreCase = true) &&
                        !f.name.endsWith("-sources.jar", ignoreCase = true) &&
                        !f.name.endsWith("-javadoc.jar", ignoreCase = true) &&
                        !f.name.endsWith("-test.jar", ignoreCase = true)
                }
                .toList()
            if (jars.isEmpty()) continue
            val preferred = jars.firstOrNull { j ->
                (preferSuffix == null || j.name.endsWith(preferSuffix, ignoreCase = true)) &&
                    (avoidSuffix == null || !j.name.endsWith(avoidSuffix, ignoreCase = true))
            }
            if (preferred != null) {
                return preferred to vDir.name
            }
        }
        for (vDir in orderedCandidates) {
            val jars = vDir.walkTopDown()
                .filter { f ->
                    f.isFile &&
                        f.extension.equals("jar", ignoreCase = true) &&
                        !f.name.endsWith("-sources.jar", ignoreCase = true) &&
                        !f.name.endsWith("-javadoc.jar", ignoreCase = true) &&
                        !f.name.endsWith("-test.jar", ignoreCase = true)
                }
                .toList()
            if (jars.isEmpty()) continue
            val acceptable = jars.firstOrNull { j ->
                avoidSuffix == null || !j.name.endsWith(avoidSuffix, ignoreCase = true)
            }
            if (acceptable != null) {
                return acceptable to vDir.name
            }
        }
        return null
    }

    private fun compareVersionsDesc(a: String, b: String): Int {
        val partsA = a.split('.', '-')
        val partsB = b.split('.', '-')
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val segA = partsA.getOrNull(i).orEmpty()
            val segB = partsB.getOrNull(i).orEmpty()
            val numA = segA.toLongOrNull()
            val numB = segB.toLongOrNull()
            val cmp = if (numA != null && numB != null) {
                numB.compareTo(numA)
            } else {
                segB.compareTo(segA)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }
}
