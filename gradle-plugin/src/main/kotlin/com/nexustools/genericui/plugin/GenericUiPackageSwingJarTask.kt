package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * Gradle task (`packageGenericUiSwingDevelopmentJar` and `packageGenericUiSwingProductionJar`)
 * that packages the application's compiled JVM classes, processed resources, desktop icons,
 * all GenericUI Swing/Desktop/System/Common module classes, and all JVM runtime dependency
 * libraries (`kotlin-stdlib`, `kotlinx-coroutines-core-jvm`, `kotlinx-coroutines-swing`, LWJGL, etc.)
 * into a single self-contained, executable fat `.jar` in `build/dist/swing/<variant>/`.
 */
@DisableCachingByDefault(because = "Packages a self-contained executable Swing fat JAR from runtime configurations and compiled classes")
abstract class GenericUiPackageSwingJarTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val swingEnabled: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val projectRootPath: Property<String>

    @get:Input
    @get:Optional
    abstract val baseName: Property<String>

    @get:Input
    @get:Optional
    abstract val appName: Property<String>

    @get:Input
    @get:Optional
    abstract val appVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val mainClass: Property<String>

    @get:Input
    @get:Optional
    abstract val generatedLauncherPackage: Property<String>

    @get:Input
    @get:Optional
    abstract val buildVariant: Property<String>

    @get:Input
    @get:Optional
    abstract val enabledGenericUiModulePaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val includedBuildRootPaths: ListProperty<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundledNativeLibrariesManifest: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val jniLibsDirectories: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val requireNjsClasses: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = GenericUiGradlePlugin.TASK_GROUP
        description = "Packages all classes, resources, GenericUI modules, and JVM runtime libraries into a single executable Swing fat JAR."
        notCompatibleWithConfigurationCache("Packages Swing fat JAR from runtime configurations and compiled classes")
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun packageSwingJar() {
        val outDir = outputDirectory.get().asFile
        outDir.mkdirs()

        if (swingEnabled.orNull == false) {
            return
        }

        val taskStartMs = System.currentTimeMillis()
        val buildDir = project.layout.buildDirectory.asFile.get()
        val rootDir = projectRootPath.orNull?.takeIf { it.isNotBlank() }?.let { File(it) } ?: project.projectDir
        val variant = buildVariant.orNull?.trim()?.ifEmpty { "development" } ?: "development"
        val resolvedBaseName = baseName.orNull?.trim()?.ifEmpty { project.name } ?: project.name
        val resolvedAppName = appName.orNull?.trim()?.ifEmpty { resolvedBaseName } ?: resolvedBaseName
        val resolvedVersion = appVersion.orNull?.trim()?.ifEmpty { "1.0.0" } ?: "1.0.0"

        val workDir = File(buildDir, "intermediates/genericui_swing_jar/$variant")
        if (workDir.exists()) {
            workDir.deleteRecursively()
        }
        workDir.mkdirs()

        // 1. Collect the project's own compiled JVM classes & resources
        val appClassesJar = collectAppJvmClassesAndResourcesJar(buildDir, rootDir, workDir)

        // 2. Resolve Main-Class
        val resolvedMainClass = resolveEffectiveMainClass(buildDir, appClassesJar)

        // 3. Resolve all runtime JARs (composite GenericUI modules, local subprojects, and Maven libraries)
        val runtimeJars = resolveJvmRuntimeJars(workDir, rootDir)

        // 4. Clean output directory so only the single executable fat JAR remains
        outDir.listFiles()?.forEach { child ->
            runCatching {
                if (child.isDirectory) {
                    child.deleteRecursively()
                } else {
                    child.delete()
                }
            }
        }

        val jarFileName = "$resolvedBaseName-$variant.jar"
        val finalJar = File(outDir, jarFileName)

        // 5. Merge app classes/resources + runtime JARs + bundled JNI libraries into a single executable fat JAR
        val allInputJars = buildList {
            add(appClassesJar)
            addAll(runtimeJars)
        }
        val nativeJarEntries = collectBundledNativeEntries(buildDir, rootDir)
        buildFatSwingJar(
            inputJars = allInputJars,
            mainClass = resolvedMainClass,
            appTitle = resolvedAppName,
            appVersion = resolvedVersion,
            outputJarFile = finalJar,
            additionalEntries = nativeJarEntries
        )

        val finishedMs = System.currentTimeMillis().coerceAtLeast(taskStartMs)
        finalJar.setLastModified(finishedMs)

        // 6. Verify that the fat JAR contains Main-Class, GenericUI Swing runtime classes, NJS (when WebView/NJS active), and Kotlin stdlib
        val hasWebView = ":extensions:ui:webview" in enabledGenericUiModulePaths.orNull.orEmpty()
        val requireNjs = (requireNjsClasses.orNull == true) || hasWebView
        verifySwingFatJar(
            jarFile = finalJar,
            expectedMainClass = resolvedMainClass,
            requireGenericUiRuntimeClasses = true,
            requireNjsRuntimeClasses = requireNjs,
            requireWebViewWorkerClasses = hasWebView
        )
    }

    private fun collectAppJvmClassesAndResourcesJar(
        buildDir: File,
        rootDir: File,
        workDir: File
    ): File {
        val appJar = File(workDir, "app-jvm-classes-and-resources.jar")
        val seenEntries = HashSet<String>()
        var classCount = 0

        val classDirs = listOf(
            File(buildDir, "classes/kotlin/jvm/main"),
            File(buildDir, "classes/java/jvmMain"),
            File(buildDir, "classes/kotlin/main"),
            File(buildDir, "classes/java/main")
        ).filter { it.isDirectory }

        val hierarchicalResDir = File(buildDir, "generated/genericui/resources/backends/web")
        val resourceDirs = listOf(
            hierarchicalResDir,
            File(buildDir, "generated/genericui/desktop-resources/runtime-assets"),
            File(buildDir, "processedResources/jvm/main"),
            File(buildDir, "resources/main")
        ).filter { it.isDirectory }

        JarOutputStream(BufferedOutputStream(FileOutputStream(appJar))).use { jos ->
            for (dir in classDirs) {
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relPath = file.relativeTo(dir).invariantSeparatorsPath.trimStart('/')
                    if (relPath.isNotEmpty() &&
                        !relPath.startsWith("META-INF/versions/") &&
                        !relPath.endsWith("module-info.class") &&
                        seenEntries.add(relPath)
                    ) {
                        jos.putNextEntry(JarEntry(relPath))
                        file.inputStream().buffered().use { it.copyTo(jos) }
                        jos.closeEntry()
                        if (relPath.endsWith(".class")) {
                            classCount++
                        }
                    }
                }
            }

            if (classCount == 0) {
                // Fallback to build/libs/<project>-jvm*.jar if classes dir was not populated directly
                val libsDir = File(buildDir, "libs")
                val candidateLibJars = libsDir.listFiles()
                    ?.filter { f ->
                        f.isFile &&
                            f.extension.equals("jar", ignoreCase = true) &&
                            !f.name.endsWith("-sources.jar", ignoreCase = true) &&
                            !f.name.endsWith("-javadoc.jar", ignoreCase = true) &&
                            !f.name.endsWith("-metadata.jar", ignoreCase = true)
                    }
                    ?.sortedByDescending { it.lastModified() }
                    .orEmpty()
                for (jarFile in candidateLibJars) {
                    runCatching {
                        JarFile(jarFile).use { jf ->
                            val entries = jf.entries()
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                if (entry.isDirectory) continue
                                val name = entry.name.trimStart('/')
                                if (name.isEmpty() ||
                                    name.equals("META-INF/MANIFEST.MF", ignoreCase = true) ||
                                    name.startsWith("META-INF/versions/") ||
                                    name.endsWith("module-info.class") ||
                                    !seenEntries.add(name)
                                ) {
                                    continue
                                }
                                jos.putNextEntry(JarEntry(name))
                                jf.getInputStream(entry).use { it.copyTo(jos) }
                                jos.closeEntry()
                                if (name.endsWith(".class")) {
                                    classCount++
                                }
                            }
                        }
                    }
                }
            }

            for (resDir in resourceDirs) {
                val isHierarchicalStagingDir = resDir == hierarchicalResDir
                resDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relPath = file.relativeTo(resDir).invariantSeparatorsPath.trimStart('/')
                    if (isHierarchicalStagingDir && (
                            relPath.startsWith("assets/") ||
                                relPath in setOf("genericui-resources.json", "genericui-resource-vars.css")
                            )
                    ) {
                        return@forEach
                    }
                    if (relPath.isNotEmpty() &&
                        !relPath.equals("META-INF/MANIFEST.MF", ignoreCase = true) &&
                        seenEntries.add(relPath)
                    ) {
                        jos.putNextEntry(JarEntry(relPath))
                        file.inputStream().buffered().use { it.copyTo(jos) }
                        jos.closeEntry()
                    }
                }
            }
        }

        if (classCount == 0) {
            throw GradleException(
                "No compiled JVM .class files found for project '${project.path}' in " +
                    "${File(buildDir, "classes/kotlin/jvm/main").absolutePath}. " +
                    "Ensure 'compileKotlinJvm' succeeded."
            )
        }
        return appJar
    }

    internal fun resolveEffectiveMainClass(buildDir: File, appClassesJar: File): String {
        val presentClassEntries = HashSet<String>()
        if (appClassesJar.isFile && appClassesJar.length() > 0L) {
            runCatching {
                JarFile(appClassesJar).use { jf ->
                    val entries = jf.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory && entry.name.endsWith(".class")) {
                            presentClassEntries.add(entry.name.trimStart('/'))
                        }
                    }
                }
            }
        }

        val candidates = LinkedHashSet<String>()
        generatedLauncherPackage.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { pkg ->
            candidates.add("$pkg.GeneratedSwingMain")
        }
        mainClass.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { configured ->
            candidates.add(configured)
        }

        // First pick any candidate that exists in presentClassEntries
        for (candidate in candidates) {
            if ("${candidate.replace('.', '/')}.class" in presentClassEntries) {
                return candidate
            }
        }

        // Next scan presentClassEntries for **/GeneratedSwingMain.class
        val discoveredSwingMain = presentClassEntries
            .firstOrNull { it.endsWith("/GeneratedSwingMain.class") || it == "GeneratedSwingMain.class" }
            ?.removeSuffix(".class")
            ?.replace('/', '.')
        if (!discoveredSwingMain.isNullOrEmpty()) {
            return discoveredSwingMain
        }

        // Next scan buildDir/classes/kotlin/jvm/main for any GeneratedSwingMain.class
        val jvmClassesDir = File(buildDir, "classes/kotlin/jvm/main")
        if (jvmClassesDir.isDirectory) {
            val found = jvmClassesDir.walkTopDown()
                .firstOrNull { it.isFile && it.name == "GeneratedSwingMain.class" }
            if (found != null) {
                return found.relativeTo(jvmClassesDir)
                    .invariantSeparatorsPath
                    .trimStart('/')
                    .removeSuffix(".class")
                    .replace('/', '.')
            }
        }

        return candidates.firstOrNull() ?: "com.nexustools.genericui.generated.GeneratedSwingMain"
    }

    private fun resolveJvmRuntimeJars(workDir: File, rootDir: File): List<File> {
        val candidateConfigNames = listOf(
            "jvmRuntimeClasspath",
            "jvmCompileClasspath",
            "runtimeClasspath",
            "compileClasspath"
        )
        val resolvableConfigs = candidateConfigNames
            .mapNotNull { project.configurations.findByName(it) }
            .filter { it.isCanBeResolved }

        val rawFiles = LinkedHashSet<File>()
        val moduleCoords = LinkedHashSet<String>()
        val projectModulePaths = LinkedHashSet<String>()
        val njsModuleNames = LinkedHashSet<String>()
        projectModulePaths.addAll(enabledGenericUiModulePaths.orNull.orEmpty())

        val artifactTypeAttr = Attribute.of("artifactType", String::class.java)
        for (cfg in resolvableConfigs) {
            runCatching {
                cfg.incoming.artifactView {
                    lenient(true)
                }.files.forEach { file ->
                    if (file.isFile && file.extension.equals("jar", ignoreCase = true)) {
                        rawFiles.add(file)
                    }
                }
            }
            runCatching {
                cfg.incoming.artifactView {
                    lenient(true)
                    attributes {
                        attribute(artifactTypeAttr, "jar")
                    }
                }.files.forEach { file ->
                    if (file.isFile && file.extension.equals("jar", ignoreCase = true)) {
                        rawFiles.add(file)
                    }
                }
            }
            runCatching {
                cfg.incoming.resolutionResult.allComponents.forEach { comp ->
                    when (val id = comp.id) {
                        is ModuleComponentIdentifier -> {
                            if (id.group.isNotBlank() && id.module.isNotBlank() && id.version.isNotBlank()) {
                                when {
                                    id.group == "com.nexustools.njs" -> {
                                        njsModuleNames.add(id.module.trim())
                                    }
                                    id.group.startsWith("com.nexustools.genericui") -> {
                                        mapGenericUiMavenToModulePath(id.group, id.module)?.let { projectModulePaths.add(it) }
                                    }
                                    else -> {
                                        moduleCoords.add("${id.group}:${id.module}:${id.version}")
                                    }
                                }
                            }
                        }
                        is ProjectComponentIdentifier -> {
                            val projPath = id.projectPath.trim()
                            val buildPath = runCatching { id.build.buildPath.trim() }.getOrDefault("")
                            when {
                                buildPath == ":NJS" || buildPath.endsWith(":NJS") -> {
                                    val modName = projPath.trim(':')
                                    if (modName.isNotEmpty()) njsModuleNames.add(modName)
                                }
                                projPath.startsWith(":NJS:") -> {
                                    val modName = projPath.removePrefix(":NJS:").trim(':')
                                    if (modName.isNotEmpty()) njsModuleNames.add(modName)
                                }
                                projPath.isNotEmpty() -> {
                                    projectModulePaths.add(projPath)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Resolve composite-build (includeBuild("lib/GenericUI") / includeBuild("../../..")) and local subproject JVM classes/JARs
        val moduleJarsDir = File(workDir, "module-jars").apply { mkdirs() }
        val compositeModuleJars = resolveModuleJvmClassesJars(
            modulePaths = projectModulePaths,
            rootDir = rootDir,
            moduleJarsDir = moduleJarsDir
        )
        rawFiles.addAll(compositeModuleJars)

        val hasWebView = ":extensions:ui:webview" in projectModulePaths
        if (requireNjsClasses.orNull == true || hasWebView) {
            njsModuleNames.add("common")
            njsModuleNames.add("jvm")
        }
        if (njsModuleNames.isNotEmpty()) {
            rawFiles.addAll(
                resolveNjsJvmClassesJars(
                    njsModuleNames = njsModuleNames,
                    rootDir = rootDir,
                    moduleJarsDir = moduleJarsDir
                )
            )
        }

        if (moduleCoords.isNotEmpty()) {
            runCatching {
                val deps = moduleCoords.map { coord -> project.dependencies.create(coord) }.toTypedArray()
                val detached = project.configurations.detachedConfiguration(*deps)
                detached.isTransitive = false
                detached.incoming.artifactView {
                    lenient(true)
                }.files.forEach { file ->
                    if (file.isFile && file.extension.equals("jar", ignoreCase = true)) {
                        rawFiles.add(file)
                    }
                }
            }
        }

        val jarFiles = mutableListOf<File>()
        val seenCanonical = HashSet<String>()

        for (file in rawFiles) {
            if (!file.isFile || file.length() == 0L) continue
            if (!file.extension.equals("jar", ignoreCase = true)) continue
            if (file.name.endsWith("-sources.jar", ignoreCase = true) ||
                file.name.endsWith("-javadoc.jar", ignoreCase = true) ||
                file.name.endsWith("-metadata.jar", ignoreCase = true)
            ) {
                continue
            }
            val canonical = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            if (seenCanonical.add(canonical)) {
                jarFiles.add(file)
            }
        }

        return jarFiles
    }

    private fun mapGenericUiMavenToModulePath(group: String, module: String): String? {
        return when {
            group == "com.nexustools.genericui" -> ":$module"
            group.startsWith("com.nexustools.genericui.") -> {
                val subGroup = group.removePrefix("com.nexustools.genericui.").replace('.', ':')
                ":$subGroup:$module"
            }
            else -> null
        }
    }

    internal fun resolveModuleJvmClassesJars(
        modulePaths: Collection<String>,
        rootDir: File,
        moduleJarsDir: File
    ): List<File> {
        val candidateGenericUiRoots = LinkedHashSet<File>()
        includedBuildRootPaths.orNull.orEmpty().forEach { path ->
            val f = File(path)
            if (f.isDirectory) candidateGenericUiRoots.add(f)
        }
        runCatching {
            project.gradle.includedBuilds.forEach { ib ->
                if (ib.projectDir.isDirectory) {
                    candidateGenericUiRoots.add(ib.projectDir)
                }
            }
        }
        listOfNotNull(
            File(project.rootProject.projectDir, "lib/GenericUI"),
            File(rootDir, "lib/GenericUI"),
            rootDir.parentFile?.let { File(it, "lib/GenericUI") },
            project.rootProject.projectDir.parentFile?.let { File(it, "GenericUI") },
            rootDir.parentFile?.parentFile?.parentFile?.takeIf { File(it, "desktop/build.gradle.kts").isFile }
        ).forEach { dir ->
            if (dir.isDirectory) candidateGenericUiRoots.add(dir)
        }
        if (project.rootProject.name.equals("GenericUI", ignoreCase = true) ||
            File(project.rootProject.projectDir, "desktop/build.gradle.kts").isFile
        ) {
            candidateGenericUiRoots.add(project.rootProject.projectDir)
        }

        val resolvedModuleDirs = LinkedHashMap<String, Pair<String, File>>()
        val currentProjectCanonical = runCatching { project.projectDir.canonicalPath }.getOrElse { project.projectDir.absolutePath }

        for (rawPath in modulePaths) {
            val trimmed = rawPath.trim()
            if (trimmed.isEmpty() || trimmed == ":") continue
            val normalizedPath = when {
                trimmed.startsWith(":GenericUI:") -> ":" + trimmed.removePrefix(":GenericUI:")
                trimmed.startsWith("GenericUI:") -> ":" + trimmed.removePrefix("GenericUI:")
                trimmed.startsWith(":") -> trimmed
                else -> ":$trimmed"
            }

            val candidateDirs = mutableListOf<File>()
            runCatching {
                project.rootProject.findProject(trimmed)?.projectDir?.let { candidateDirs.add(it) }
                if (normalizedPath != trimmed) {
                    project.rootProject.findProject(normalizedPath)?.projectDir?.let { candidateDirs.add(it) }
                }
            }
            val relSubPath = normalizedPath.removePrefix(":").replace(':', '/')
            if (relSubPath.isNotEmpty()) {
                for (genericUiRoot in candidateGenericUiRoots) {
                    candidateDirs.add(File(genericUiRoot, relSubPath))
                }
                candidateDirs.add(File(project.rootProject.projectDir, relSubPath))
            }

            for (dir in candidateDirs) {
                if (!dir.isDirectory) continue
                val canonical = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
                if (canonical == currentProjectCanonical) continue
                resolvedModuleDirs.putIfAbsent(canonical, normalizedPath to dir)
            }
        }

        val collectedJars = mutableListOf<File>()
        for ((_, pathAndDir) in resolvedModuleDirs) {
            val (modPath, modDir) = pathAndDir
            val jars = collectModuleJvmClassesJarsFromDir(modDir, modPath, moduleJarsDir)
            collectedJars.addAll(jars)
        }
        return collectedJars
    }

    internal fun resolveNjsJvmClassesJars(
        njsModuleNames: Collection<String>,
        rootDir: File,
        moduleJarsDir: File
    ): List<File> {
        val candidateNjsRoots = LinkedHashSet<File>()
        fun addIfNjsRoot(dir: File?) {
            if (dir != null &&
                dir.isDirectory &&
                File(dir, "common/build.gradle.kts").isFile &&
                File(dir, "jvm/build.gradle.kts").isFile
            ) {
                candidateNjsRoots.add(dir)
            }
        }
        includedBuildRootPaths.orNull.orEmpty().forEach { path ->
            val f = File(path)
            addIfNjsRoot(f)
            addIfNjsRoot(File(f, "lib/NJS"))
        }
        runCatching {
            project.gradle.includedBuilds.forEach { ib ->
                addIfNjsRoot(ib.projectDir)
                addIfNjsRoot(File(ib.projectDir, "lib/NJS"))
            }
        }
        listOfNotNull(
            File(project.rootProject.projectDir, "lib/NJS"),
            File(rootDir, "lib/NJS"),
            File(project.rootProject.projectDir, "lib/GenericUI/lib/NJS"),
            File(rootDir, "lib/GenericUI/lib/NJS"),
            rootDir.parentFile?.let { File(it, "lib/NJS") },
            project.rootProject.projectDir.parentFile?.let { File(it, "GenericUI/lib/NJS") }
        ).forEach { addIfNjsRoot(it) }

        val resolvedModuleDirs = LinkedHashMap<String, Pair<String, File>>()
        val currentProjectCanonical = runCatching { project.projectDir.canonicalPath }.getOrElse { project.projectDir.absolutePath }
        for (rawMod in njsModuleNames) {
            val cleanMod = rawMod.trim().trim(':').ifEmpty { continue }
            val candidateDirs = mutableListOf<File>()
            runCatching {
                project.rootProject.findProject(":NJS:$cleanMod")?.projectDir?.let { candidateDirs.add(it) }
            }
            val relSub = cleanMod.replace(':', '/')
            for (njsRoot in candidateNjsRoots) {
                candidateDirs.add(File(njsRoot, relSub))
            }
            for (dir in candidateDirs) {
                if (!dir.isDirectory) continue
                val canonical = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
                if (canonical == currentProjectCanonical) continue
                resolvedModuleDirs.putIfAbsent(canonical, "njs-$cleanMod" to dir)
            }
        }

        val collectedJars = mutableListOf<File>()
        for ((_, pathAndDir) in resolvedModuleDirs) {
            val (modLabel, modDir) = pathAndDir
            collectedJars.addAll(collectModuleJvmClassesJarsFromDir(modDir, modLabel, moduleJarsDir))
        }
        return collectedJars
    }

    private fun collectModuleJvmClassesJarsFromDir(
        moduleDir: File,
        modulePath: String,
        moduleJarsDir: File
    ): List<File> {
        val modBuildDir = File(moduleDir, "build")
        if (!modBuildDir.isDirectory) return emptyList()

        val foundJars = mutableListOf<File>()

        val classDirs = listOf(
            File(modBuildDir, "classes/kotlin/jvm/main"),
            File(modBuildDir, "classes/java/jvmMain"),
            File(modBuildDir, "classes/kotlin/main"),
            File(modBuildDir, "classes/java/main")
        ).filter { it.isDirectory }

        val resDirs = listOf(
            File(modBuildDir, "processedResources/jvm/main"),
            File(modBuildDir, "resources/main"),
            File(moduleDir, "src/jvmMain/resources"),
            File(moduleDir, "src/swingMain/resources"),
            File(moduleDir, "src/commonMain/resources")
        ).filter { it.isDirectory }

        if (classDirs.isNotEmpty()) {
            val safeName = modulePath.trim(':').replace(':', '-').ifEmpty { moduleDir.name }
            val packagedJar = File(moduleJarsDir, "$safeName-jvm-classes.jar")
            val seenEntries = HashSet<String>()
            var count = 0
            JarOutputStream(BufferedOutputStream(FileOutputStream(packagedJar))).use { jos ->
                for (dir in classDirs) {
                    dir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relPath = file.relativeTo(dir).invariantSeparatorsPath.trimStart('/')
                        if (relPath.isNotEmpty() &&
                            !relPath.startsWith("META-INF/versions/") &&
                            !relPath.endsWith("module-info.class") &&
                            seenEntries.add(relPath)
                        ) {
                            jos.putNextEntry(JarEntry(relPath))
                            file.inputStream().buffered().use { it.copyTo(jos) }
                            jos.closeEntry()
                            if (relPath.endsWith(".class")) count++
                        }
                    }
                }
                for (rDir in resDirs) {
                    rDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relPath = file.relativeTo(rDir).invariantSeparatorsPath.trimStart('/')
                        if (relPath.isNotEmpty() &&
                            !relPath.equals("META-INF/MANIFEST.MF", ignoreCase = true) &&
                            seenEntries.add(relPath)
                        ) {
                            jos.putNextEntry(JarEntry(relPath))
                            file.inputStream().buffered().use { it.copyTo(jos) }
                            jos.closeEntry()
                        }
                    }
                }
            }
            if (count > 0 && packagedJar.isFile && packagedJar.length() > 0L) {
                foundJars.add(packagedJar)
            }
        }

        val libsDir = File(modBuildDir, "libs")
        if (libsDir.isDirectory) {
            libsDir.listFiles()
                ?.filter { f ->
                    f.isFile &&
                        f.extension.equals("jar", ignoreCase = true) &&
                        f.length() > 0L &&
                        !f.name.endsWith("-sources.jar", ignoreCase = true) &&
                        !f.name.endsWith("-javadoc.jar", ignoreCase = true) &&
                        !f.name.endsWith("-metadata.jar", ignoreCase = true)
                }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { foundJars.add(it) }
        }

        return foundJars
    }

    internal fun collectBundledNativeEntries(buildDir: File, rootDir: File): Map<String, File> {
        val entries = LinkedHashMap<String, File>()
        val manifest = bundledNativeLibrariesManifest.orNull?.asFile
            ?: File(buildDir, "intermediates/genericui_jni/host-bundle-manifest.txt")
        if (manifest.isFile) {
            for (line in manifest.readLines(StandardCharsets.UTF_8)) {
                val parts = line.split('\t')
                if (parts.size < 3) continue
                val osArch = parts[0].trim()
                val file = File(parts[1].trim())
                val bundle = parts[2].trim().toBooleanStrictOrNull() ?: true
                if (bundle && osArch.isNotEmpty() && file.isFile) {
                    entries.putIfAbsent("META-INF/native/$osArch/${file.name}", file)
                }
            }
        }
        val nativeDirPattern = Regex("^(windows|macos|linux)-(x86_64|aarch64|x86|arm)$")
        for (rawDir in jniLibsDirectories.orNull.orEmpty()) {
            val baseDir = File(rawDir).let { if (it.isAbsolute) it else File(rootDir, it.path) }
            if (!baseDir.isDirectory) continue
            baseDir.listFiles()?.filter { it.isDirectory && nativeDirPattern.matches(it.name) }?.forEach { osArchDir ->
                osArchDir.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase() in setOf("dll", "dylib", "so") }
                    ?.forEach { libFile ->
                        entries.putIfAbsent("META-INF/native/${osArchDir.name}/${libFile.name}", libFile)
                    }
            }
        }
        return entries
    }

    internal fun buildFatSwingJar(
        inputJars: List<File>,
        mainClass: String,
        appTitle: String,
        appVersion: String,
        outputJarFile: File,
        additionalEntries: Map<String, File> = emptyMap()
    ) {
        outputJarFile.parentFile?.mkdirs()

        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name.MAIN_CLASS] = mainClass
            mainAttributes[Attributes.Name.IMPLEMENTATION_TITLE] = appTitle
            mainAttributes[Attributes.Name.IMPLEMENTATION_VERSION] = appVersion
        }

        val seenEntries = HashSet<String>()
        seenEntries.add("META-INF/MANIFEST.MF")
        val serviceFiles = LinkedHashMap<String, LinkedHashSet<String>>()

        JarOutputStream(BufferedOutputStream(FileOutputStream(outputJarFile)), manifest).use { jos ->
            for (jarFile in inputJars) {
                if (!jarFile.isFile || jarFile.length() == 0L) continue
                runCatching {
                    JarFile(jarFile).use { jf ->
                        val entries = jf.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.isDirectory) continue
                            val name = entry.name.trimStart('/')
                            if (name.isEmpty()) continue

                            val upper = name.uppercase()
                            if (upper == "META-INF/MANIFEST.MF") continue
                            if (upper.startsWith("META-INF/") && (
                                    upper.endsWith(".SF") ||
                                        upper.endsWith(".DSA") ||
                                        upper.endsWith(".RSA") ||
                                        upper.substringAfter("META-INF/").startsWith("SIG-")
                                )
                            ) {
                                continue
                            }
                            if (name == "module-info.class" || name.endsWith("/module-info.class")) continue
                            if (name.startsWith("META-INF/versions/")) continue

                            if (name.startsWith("META-INF/services/")) {
                                val serviceName = name.removePrefix("META-INF/services/").trim()
                                if (serviceName.isNotEmpty() && !serviceName.contains('/')) {
                                    val lines = jf.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).readLines()
                                    val set = serviceFiles.getOrPut(name) { LinkedHashSet() }
                                    for (line in lines) {
                                        val trimmed = line.trim()
                                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                            set.add(trimmed)
                                        }
                                    }
                                }
                                continue
                            }

                            if (!seenEntries.add(name)) continue

                            jos.putNextEntry(JarEntry(name))
                            jf.getInputStream(entry).use { it.copyTo(jos) }
                            jos.closeEntry()
                        }
                    }
                }
            }

            for ((servicePath, providers) in serviceFiles) {
                if (providers.isEmpty() || !seenEntries.add(servicePath)) continue
                val content = (providers.joinToString("\n") + "\n").toByteArray(StandardCharsets.UTF_8)
                jos.putNextEntry(JarEntry(servicePath))
                jos.write(content)
                jos.closeEntry()
            }

            for ((entryPath, srcFile) in additionalEntries) {
                val normalized = entryPath.trimStart('/')
                if (normalized.isEmpty() || !srcFile.isFile || !seenEntries.add(normalized)) continue
                jos.putNextEntry(JarEntry(normalized))
                srcFile.inputStream().buffered().use { it.copyTo(jos) }
                jos.closeEntry()
            }
        }
    }

    internal fun verifySwingFatJar(
        jarFile: File,
        expectedMainClass: String,
        requireGenericUiRuntimeClasses: Boolean = true,
        requireNjsRuntimeClasses: Boolean = false,
        requireWebViewWorkerClasses: Boolean = false
    ) {
        if (!jarFile.isFile || jarFile.length() == 0L) {
            throw GradleException(
                "Swing fat JAR packaging failed: output JAR does not exist or is empty at ${jarFile.absolutePath}"
            )
        }

        val presentEntries = HashSet<String>()
        var manifestMainClass: String? = null

        JarFile(jarFile).use { jf ->
            manifestMainClass = jf.manifest?.mainAttributes?.getValue(Attributes.Name.MAIN_CLASS)?.trim()
            val entries = jf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.size != 0L) {
                    presentEntries.add(entry.name.trimStart('/'))
                }
            }
        }

        if (manifestMainClass.isNullOrEmpty() || manifestMainClass != expectedMainClass) {
            throw GradleException(
                "Swing fat JAR packaging failed: META-INF/MANIFEST.MF Main-Class is '$manifestMainClass', expected '$expectedMainClass' in ${jarFile.absolutePath}"
            )
        }

        val requiredClasses = LinkedHashSet<String>()
        requiredClasses.add(expectedMainClass)
        if (requireGenericUiRuntimeClasses) {
            requiredClasses.add("com.nexustools.genericui.swing.SwingLauncher")
            requiredClasses.add("com.nexustools.genericui.swing.SwingNativeFactory")
            requiredClasses.add("com.nexustools.genericui.swing.SwingApplicationImpl")
            requiredClasses.add("com.nexustools.genericui.swing.SwingWindowImpl")
            requiredClasses.add("com.nexustools.genericui.core.GenericUI")
            requiredClasses.add("com.nexustools.genericui.component.fragment.Fragment")
            requiredClasses.add("kotlin.Unit")
        }
        if (requireNjsRuntimeClasses) {
            requiredClasses.add("com.nexustools.njs.runtime.Global")
            requiredClasses.add("com.nexustools.njs.compiler.JavaTranspiler")
        }
        if (requireWebViewWorkerClasses) {
            requiredClasses.add("com.nexustools.genericui.ext.webview.desktop.process.WebViewWorkerMain")
        }

        val missingClasses = requiredClasses.filter { fqcn ->
            "${fqcn.replace('.', '/')}.class" !in presentEntries
        }
        if (missingClasses.isNotEmpty()) {
            throw GradleException(
                "Swing fat JAR packaging failed: required classes are missing from ${jarFile.absolutePath}: " +
                    missingClasses.joinToString(", ") + ". " +
                    "Ensure all GenericUI Swing modules (:common, :system, :jvm, :desktop), Kotlin stdlib, and app classes are compiled."
            )
        }
    }
}
