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
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.tools.ToolProvider

/**
 * Gradle task (`packageGenericUiAndroidDevelopmentApk`, `packageGenericUiAndroidProductionApk`,
 * and architecture-specific variants) that packages, aligns, and signs an installable Android `.apk`
 * directly from `com.android.kotlin.multiplatform.library` compiled classes, generated GenericUI
 * `AndroidManifest.xml` & resources, and `androidRuntimeClasspath` `.jar`/`.aar` dependencies.
 */
@DisableCachingByDefault(because = "Packages and signs an installable Android APK")
abstract class GenericUiPackageAndroidApkTask : DefaultTask() {

    private data class SdkTools(
        val sdkDir: File,
        val buildToolsDir: File,
        val aapt2: File,
        val zipalign: File,
        val d8Jar: File,
        val apksignerJar: File,
        val androidJar: File
    )

    private data class ExtractedAar(
        val name: String,
        val packageName: String?,
        val classesJars: List<File>,
        val resDir: File?,
        val assetsDir: File?,
        val jniDir: File?
    )

    @get:Input
    @get:Optional
    abstract val androidEnabled: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val projectRootPath: Property<String>

    @get:Input
    @get:Optional
    abstract val baseName: Property<String>

    @get:Input
    @get:Optional
    abstract val appId: Property<String>

    @get:Input
    @get:Optional
    abstract val appVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val minSdk: Property<Int>

    @get:Input
    @get:Optional
    abstract val compileSdk: Property<Int>

    @get:Input
    @get:Optional
    abstract val targetSdk: Property<Int>

    @get:Input
    @get:Optional
    abstract val buildVariant: Property<String>

    @get:Input
    @get:Optional
    abstract val targetAbi: Property<String>

    @get:Input
    @get:Optional
    abstract val enabledGenericUiModulePaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val includedBuildRootPaths: ListProperty<String>

    @get:Internal
    abstract val manifestFile: RegularFileProperty

    @get:Internal
    abstract val generatedAndroidResDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    abstract val secondaryOutputDirectory: DirectoryProperty

    init {
        group = GenericUiGradlePlugin.TASK_GROUP
        description = "Packages, aligns, and signs the GenericUI Android APK."
        notCompatibleWithConfigurationCache("Packages Android APK from runtime configurations and compiled classes")
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun packageApk() {
        val outDir = outputDirectory.get().asFile
        outDir.mkdirs()

        if (androidEnabled.orNull == false) {
            return
        }

        val taskStartMs = System.currentTimeMillis()
        val buildDir = project.layout.buildDirectory.asFile.get()
        val rootDir = projectRootPath.orNull?.takeIf { it.isNotBlank() }?.let { File(it) } ?: project.projectDir

        // Clean stale pre-GenericUI build/outputs/apk when AGP application tasks are not present
        if (project.tasks.findByName("assembleDebug") == null && project.tasks.findByName("assembleRelease") == null) {
            val legacyOutputsApk = File(buildDir, "outputs/apk")
            if (legacyOutputsApk.exists()) {
                runCatching { legacyOutputsApk.deleteRecursively() }
            }
        }

        val variant = buildVariant.orNull?.trim()?.ifEmpty { "development" } ?: "development"
        val abiFilter = targetAbi.orNull?.trim().orEmpty()
        val resolvedBaseName = baseName.orNull?.trim()?.ifEmpty { project.name } ?: project.name
        val resolvedAppId = appId.orNull?.trim()?.ifEmpty { "com.nexustools.genericui.app" } ?: "com.nexustools.genericui.app"
        val resolvedVersion = appVersion.orNull?.trim()?.ifEmpty { "1.0.0" } ?: "1.0.0"
        val resolvedMinSdk = (minSdk.orNull ?: 24).coerceAtLeast(21)
        val resolvedCompileSdk = (compileSdk.orNull ?: 35).coerceAtLeast(resolvedMinSdk)
        val resolvedTargetSdk = (targetSdk.orNull ?: resolvedCompileSdk).coerceAtLeast(resolvedMinSdk)

        val workFolderName = if (abiFilter.isEmpty()) variant else "$variant-$abiFilter"
        val workDir = File(buildDir, "intermediates/genericui_apk/$workFolderName")
        if (workDir.exists()) {
            workDir.deleteRecursively()
        }
        workDir.mkdirs()

        // 1. Resolve Android SDK & build-tools
        val sdkDir = resolveAndroidSdkDir(rootDir)
        val sdkTools = resolveBuildTools(sdkDir, resolvedCompileSdk)

        // 2. Verify generated AndroidManifest.xml exists
        val manifest = manifestFile.orNull?.asFile
            ?: File(buildDir, "generated/genericui/android-manifest/AndroidManifest.xml")
        if (!manifest.isFile) {
            throw GradleException(
                "GenericUI AndroidManifest.xml not found at ${manifest.absolutePath}. " +
                    "Ensure 'generateGenericUiAndroidManifest' ran before '$name'."
            )
        }

        // 3. Collect the project's own compiled Android classes
        val appClassesJar = collectAppClassesJar(buildDir, workDir)

        // 4. Resolve runtime JARs and AARs from androidRuntimeClasspath / androidCompileClasspath / composite GenericUI modules
        val (runtimeJars, aarFiles) = resolveRuntimeJarsAndAars(workDir, rootDir)
        val aarsExtractRoot = File(workDir, "aars").apply { mkdirs() }
        val extractedAars = extractAars(aarFiles, aarsExtractRoot)

        // 5. Stage application resources (filtering out .svg, .scss, .sass, .css, etc.)
        val stagedAppResDir = File(workDir, "staged-app-res").apply { mkdirs() }
        val genResDir = generatedAndroidResDir.orNull?.asFile
            ?: File(buildDir, "generated/genericui/resources/backends/android/res")
        stageAppResources(
            rootDir = rootDir,
            genResDir = genResDir,
            stagedAppResDir = stagedAppResDir
        )

        // 6. Compile AAR resources and app resources with aapt2 compile
        val compiledResDir = File(workDir, "compiled-res").apply { mkdirs() }
        val (aarFlataFiles, appFlataFile) = compileResourcesWithAapt2(
            aapt2 = sdkTools.aapt2,
            extractedAars = extractedAars,
            stagedAppResDir = stagedAppResDir,
            compiledResDir = compiledResDir
        )

        // 7. Link resources and AndroidManifest.xml with aapt2 link -> linked.ap_ + R.java
        val rJavaDir = File(workDir, "r-java").apply { mkdirs() }
        val linkedApFile = File(workDir, "linked-resources.ap_")
        linkResourcesWithAapt2(
            aapt2 = sdkTools.aapt2,
            androidJar = sdkTools.androidJar,
            manifestFile = manifest,
            appId = resolvedAppId,
            appVersion = resolvedVersion,
            minSdk = resolvedMinSdk,
            targetSdk = resolvedTargetSdk,
            extractedAars = extractedAars,
            aarFlataFiles = aarFlataFiles,
            appFlataFile = appFlataFile,
            rJavaDir = rJavaDir,
            linkedApFile = linkedApFile,
            workDir = workDir
        )

        // 8. Compile generated R.java files into r-classes.jar
        val rClassesDir = File(workDir, "r-classes").apply { mkdirs() }
        val rJarFile = File(workDir, "r-classes.jar")
        compileRJavaToJar(rJavaDir, rClassesDir, rJarFile, workDir)

        // 9. Merge all program .class files (deduplicated, with app classes & R classes taking priority)
        val mergedProgramJar = File(workDir, "merged-program-classes.jar")
        val allInputJars = buildList {
            add(appClassesJar)
            if (rJarFile.isFile && rJarFile.length() > 0L) {
                add(rJarFile)
            }
            addAll(runtimeJars)
            for (aar in extractedAars) {
                addAll(aar.classesJars)
            }
        }
        buildMergedProgramJar(allInputJars, mergedProgramJar)

        // 10. Run D8 to compile merged program classes into classes*.dex
        val dexOutDir = File(workDir, "dex").apply { mkdirs() }
        runD8(
            d8Jar = sdkTools.d8Jar,
            androidJar = sdkTools.androidJar,
            minSdk = resolvedMinSdk,
            isRelease = variant.equals("production", ignoreCase = true) || variant.equals("release", ignoreCase = true),
            programJar = mergedProgramJar,
            dexOutDir = dexOutDir,
            workDir = workDir
        )

        // 10b. Verify that all manifest-declared classes (application, activities, services, etc.) and
        // GenericUI runtime base classes exist in both merged-program-classes.jar and classes*.dex
        verifyRequiredClassesInMergedJarAndDex(
            manifestFile = manifest,
            fallbackAppId = resolvedAppId,
            mergedProgramJar = mergedProgramJar,
            dexOutDir = dexOutDir,
            requireGenericUiRuntimeClasses = true
        )

        // 11. Assemble unsigned APK (linked.ap_ + classes*.dex + assets + META-INF/services + JNI .so)
        val unsignedApk = File(workDir, "unsigned.apk")
        assembleUnsignedApk(
            linkedApFile = linkedApFile,
            dexOutDir = dexOutDir,
            rootDir = rootDir,
            buildDir = buildDir,
            extractedAars = extractedAars,
            allInputJars = allInputJars,
            abiFilter = abiFilter,
            unsignedApk = unsignedApk
        )

        // 12. Zipalign (-f -p 4)
        val alignedApk = File(workDir, "aligned.apk")
        runCommand(
            listOf(
                sdkTools.zipalign.absolutePath,
                "-f",
                "-p",
                "4",
                unsignedApk.absolutePath,
                alignedApk.absolutePath
            ),
            "zipalign"
        )
        if (!alignedApk.isFile || alignedApk.length() == 0L) {
            throw GradleException("zipalign failed to produce aligned APK at ${alignedApk.absolutePath}")
        }

        // 13. Sign with apksigner using debug.keystore
        val keystoreFile = ensureDebugKeystore(workDir)
        outDir.listFiles()?.forEach { f ->
            if (f.isFile && (f.name.endsWith(".apk", ignoreCase = true) || f.name.endsWith(".idsig", ignoreCase = true))) {
                runCatching { f.delete() }
            }
        }
        val apkFileName = "$resolvedBaseName-$variant.apk"
        val finalApk = File(outDir, apkFileName)
        runCommand(
            listOf(
                resolveJavaExecutable().absolutePath,
                "-jar",
                sdkTools.apksignerJar.absolutePath,
                "sign",
                "--ks",
                keystoreFile.absolutePath,
                "--ks-pass",
                "pass:android",
                "--ks-key-alias",
                "androiddebugkey",
                "--key-pass",
                "pass:android",
                "--out",
                finalApk.absolutePath,
                alignedApk.absolutePath
            ),
            "apksigner"
        )

        val finishedMs = System.currentTimeMillis().coerceAtLeast(taskStartMs)
        finalApk.setLastModified(finishedMs)

        if (!finalApk.isFile || finalApk.length() == 0L || finalApk.lastModified() < taskStartMs) {
            throw GradleException(
                "Failed to produce fresh signed Android APK at ${finalApk.absolutePath} " +
                    "(exists=${finalApk.exists()}, size=${finalApk.length()})."
            )
        }

        val secondaryDir = secondaryOutputDirectory.orNull?.asFile
        if (secondaryDir != null && secondaryDir.canonicalPath != outDir.canonicalPath) {
            secondaryDir.mkdirs()
            secondaryDir.listFiles()?.forEach { f ->
                if (f.isFile && (f.name.endsWith(".apk", ignoreCase = true) || f.name.endsWith(".idsig", ignoreCase = true))) {
                    runCatching { f.delete() }
                }
            }
            val secondaryApk = File(secondaryDir, apkFileName)
            finalApk.copyTo(secondaryApk, overwrite = true)
            secondaryApk.setLastModified(finishedMs)
        }
    }

    private fun resolveAndroidSdkDir(rootDir: File): File {
        val envCandidates = listOfNotNull(
            System.getenv("ANDROID_HOME")?.trim()?.takeIf { it.isNotEmpty() },
            System.getenv("ANDROID_SDK_ROOT")?.trim()?.takeIf { it.isNotEmpty() }
        ).map { File(it) }
        envCandidates.firstOrNull { it.isDirectory }?.let { return it }

        val localPropsCandidates = listOfNotNull(
            File(project.projectDir, "local.properties"),
            File(project.rootProject.projectDir, "local.properties"),
            File(rootDir, "local.properties"),
            rootDir.parentFile?.let { File(it, "local.properties") },
            rootDir.parentFile?.parentFile?.let { File(it, "local.properties") }
        ).distinctBy { it.absolutePath }

        for (propsFile in localPropsCandidates) {
            if (propsFile.isFile) {
                val props = Properties()
                runCatching {
                    FileInputStream(propsFile).use { props.load(it) }
                }
                val sdkDirStr = props.getProperty("sdk.dir")?.trim().orEmpty()
                if (sdkDirStr.isNotEmpty()) {
                    val sdkDir = File(sdkDirStr)
                    if (sdkDir.isDirectory) return sdkDir
                }
            }
        }

        val userHome = System.getProperty("user.home").orEmpty()
        val localAppData = System.getenv("LOCALAPPDATA").orEmpty()
        val defaultOsPaths = listOfNotNull(
            if (localAppData.isNotEmpty()) File(localAppData, "Android/Sdk") else null,
            if (userHome.isNotEmpty()) File(userHome, "AppData/Local/Android/Sdk") else null,
            if (userHome.isNotEmpty()) File(userHome, "Library/Android/sdk") else null,
            if (userHome.isNotEmpty()) File(userHome, "Android/Sdk") else null,
            File("/opt/android-sdk"),
            File("/usr/lib/android-sdk")
        )
        defaultOsPaths.firstOrNull { it.isDirectory }?.let { return it }

        throw GradleException(
            "Android SDK not found. Set ANDROID_HOME / ANDROID_SDK_ROOT or configure sdk.dir in local.properties."
        )
    }

    private fun resolveBuildTools(sdkDir: File, requestedCompileSdk: Int): SdkTools {
        val isWin = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        val aapt2Name = if (isWin) "aapt2.exe" else "aapt2"
        val zipalignName = if (isWin) "zipalign.exe" else "zipalign"

        val buildToolsParent = File(sdkDir, "build-tools")
        val buildToolsDirs = buildToolsParent.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedWith { a, b -> compareVersionStrings(b.name, a.name) }
            .orEmpty()

        val selectedBuildTools = buildToolsDirs.firstOrNull { dir ->
            File(dir, aapt2Name).isFile &&
                File(dir, zipalignName).isFile &&
                File(dir, "lib/d8.jar").isFile &&
                File(dir, "lib/apksigner.jar").isFile
        } ?: throw GradleException(
            "No complete Android SDK build-tools installation (requiring $aapt2Name, $zipalignName, lib/d8.jar, lib/apksigner.jar) " +
                "found under ${buildToolsParent.absolutePath}."
        )

        val platformsParent = File(sdkDir, "platforms")
        val platformDirs = platformsParent.listFiles()
            ?.filter { it.isDirectory && File(it, "android.jar").isFile }
            ?.sortedWith { a, b -> compareVersionStrings(b.name.removePrefix("android-"), a.name.removePrefix("android-")) }
            .orEmpty()

        val exactPlatform = File(platformsParent, "android-$requestedCompileSdk/android.jar").takeIf { it.isFile }
            ?: platformDirs.firstOrNull { dir ->
                val verStr = dir.name.removePrefix("android-")
                verStr == "$requestedCompileSdk" || verStr.startsWith("$requestedCompileSdk.")
            }?.let { File(it, "android.jar") }
            ?: platformDirs.firstOrNull()?.let { File(it, "android.jar") }
            ?: throw GradleException(
                "No Android platform android.jar found under ${platformsParent.absolutePath} (requested compileSdk=$requestedCompileSdk)."
            )

        return SdkTools(
            sdkDir = sdkDir,
            buildToolsDir = selectedBuildTools,
            aapt2 = File(selectedBuildTools, aapt2Name),
            zipalign = File(selectedBuildTools, zipalignName),
            d8Jar = File(selectedBuildTools, "lib/d8.jar"),
            apksignerJar = File(selectedBuildTools, "lib/apksigner.jar"),
            androidJar = exactPlatform
        )
    }

    private fun collectAppClassesJar(buildDir: File, workDir: File): File {
        val candidateClassDirs = listOf(
            File(buildDir, "classes/kotlin/android/main"),
            File(buildDir, "classes/java/androidMain"),
            File(buildDir, "intermediates/javac/androidMain/classes")
        ).filter { it.isDirectory }

        val candidateJars = listOf(
            File(buildDir, "intermediates/runtime_library_classes_jar/androidMain/bundleAndroidMainClassesToRuntimeJar/classes.jar"),
            File(buildDir, "intermediates/compile_library_classes_jar/androidMain/bundleAndroidMainClassesToCompileJar/classes.jar"),
            File(buildDir, "intermediates/full_jar/androidMain/createFullJarAndroidMain/full.jar")
        ).filter { it.isFile && it.length() > 0L }

        val appClassesJar = File(workDir, "app-classes.jar")
        val seenEntries = HashSet<String>()
        var classCount = 0

        JarOutputStream(BufferedOutputStream(FileOutputStream(appClassesJar))).use { jos ->
            for (dir in candidateClassDirs) {
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relPath = file.relativeTo(dir).invariantSeparatorsPath.trimStart('/')
                    if (relPath.isNotEmpty() && !shouldSkipPackagingEntry(relPath) && seenEntries.add(relPath)) {
                        val entry = JarEntry(relPath)
                        jos.putNextEntry(entry)
                        file.inputStream().buffered().use { it.copyTo(jos) }
                        jos.closeEntry()
                        if (relPath.endsWith(".class")) {
                            classCount++
                        }
                    }
                }
            }

            for (jarFile in candidateJars) {
                runCatching {
                    JarFile(jarFile).use { jf ->
                        val entries = jf.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.isDirectory) continue
                            val name = entry.name.trimStart('/')
                            if (name.isEmpty() || shouldSkipPackagingEntry(name) || !seenEntries.add(name)) continue
                            val newEntry = JarEntry(name)
                            jos.putNextEntry(newEntry)
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

        if (classCount == 0) {
            throw GradleException(
                "No compiled Android .class files found for project '${project.path}' in " +
                    "${File(buildDir, "classes/kotlin/android/main").absolutePath} or " +
                    "${File(buildDir, "intermediates/runtime_library_classes_jar/androidMain").absolutePath}. " +
                    "Ensure 'compileAndroidMain' succeeded."
            )
        }
        return appClassesJar
    }

    private fun resolveRuntimeJarsAndAars(workDir: File, rootDir: File): Pair<List<File>, List<File>> {
        val candidateConfigNames = listOf(
            "androidRuntimeClasspath",
            "androidCompileClasspath",
            "debugRuntimeClasspath",
            "releaseRuntimeClasspath",
            "debugCompileClasspath",
            "releaseCompileClasspath",
            "runtimeClasspath"
        )
        val resolvableConfigs = candidateConfigNames
            .mapNotNull { project.configurations.findByName(it) }
            .filter { it.isCanBeResolved }

        val rawFiles = LinkedHashSet<File>()
        val moduleCoords = LinkedHashSet<String>()
        val projectModulePaths = LinkedHashSet<String>()
        projectModulePaths.addAll(enabledGenericUiModulePaths.orNull.orEmpty())

        val artifactTypeAttr = Attribute.of("artifactType", String::class.java)
        for (cfg in resolvableConfigs) {
            runCatching {
                cfg.incoming.artifactView {
                    lenient(true)
                }.files.forEach { file ->
                    resolveExistingOrSiblingArtifact(file)?.let { rawFiles.add(it) }
                }
            }
            for (artType in listOf("android-classes-jar", "classes-jar", "jar", "aar")) {
                runCatching {
                    cfg.incoming.artifactView {
                        lenient(true)
                        attributes {
                            attribute(artifactTypeAttr, artType)
                        }
                    }.files.forEach { file ->
                        resolveExistingOrSiblingArtifact(file)?.let { rawFiles.add(it) }
                    }
                }
            }
            runCatching {
                cfg.incoming.resolutionResult.allComponents.forEach { comp ->
                    when (val id = comp.id) {
                        is ModuleComponentIdentifier -> {
                            if (id.group.isNotBlank() && id.module.isNotBlank() && id.version.isNotBlank()) {
                                moduleCoords.add("${id.group}:${id.module}:${id.version}")
                            }
                        }
                        is ProjectComponentIdentifier -> {
                            val projPath = id.projectPath.trim()
                            if (projPath.isNotEmpty()) {
                                projectModulePaths.add(projPath)
                            }
                        }
                    }
                }
            }
        }

        // Resolve composite-build (includeBuild("lib/GenericUI")) and local subproject Android classes
        val moduleJarsDir = File(workDir, "module-jars").apply { mkdirs() }
        val compositeModuleJars = resolveModuleAndroidClassesJars(
            modulePaths = projectModulePaths,
            rootDir = rootDir,
            moduleJarsDir = moduleJarsDir
        )
        rawFiles.addAll(compositeModuleJars)

        // Also resolve raw Maven artifacts (.aar and .jar) via a detached configuration for every
        // ModuleComponentIdentifier in the resolved graph so external Android libraries (AppCompat, Material,
        // AndroidX Core/Fragment/Activity/Lifecycle/RecyclerView) provide their full .aar (res/ + AndroidManifest.xml + classes.jar).
        if (moduleCoords.isNotEmpty()) {
            runCatching {
                val deps = moduleCoords.map { coord -> project.dependencies.create(coord) }.toTypedArray()
                val detached = project.configurations.detachedConfiguration(*deps)
                detached.isTransitive = false
                detached.incoming.artifactView {
                    lenient(true)
                }.files.forEach { file ->
                    if (file.isFile) {
                        rawFiles.add(file)
                    }
                }
            }
        }

        val jarFiles = mutableListOf<File>()
        val aarFiles = mutableListOf<File>()
        val seenCanonical = HashSet<String>()

        for (file in rawFiles) {
            if (!file.isFile || file.length() == 0L) continue
            val canonical = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            if (!seenCanonical.add(canonical)) continue
            when (file.extension.lowercase()) {
                "aar" -> aarFiles.add(file)
                "jar" -> jarFiles.add(file)
            }
        }

        return jarFiles to aarFiles
    }

    internal fun resolveModuleAndroidClassesJars(
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
            project.rootProject.projectDir.parentFile?.let { File(it, "GenericUI") }
        ).forEach { dir ->
            if (dir.isDirectory) candidateGenericUiRoots.add(dir)
        }
        if (project.rootProject.name.equals("GenericUI", ignoreCase = true) ||
            File(project.rootProject.projectDir, "mobile/android/build.gradle.kts").isFile
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
            val jars = collectModuleAndroidClassesJarsFromDir(modDir, modPath, moduleJarsDir)
            collectedJars.addAll(jars)
        }
        return collectedJars
    }

    private fun collectModuleAndroidClassesJarsFromDir(
        moduleDir: File,
        modulePath: String,
        moduleJarsDir: File
    ): List<File> {
        val modBuildDir = File(moduleDir, "build")
        if (!modBuildDir.isDirectory) return emptyList()

        val foundJars = mutableListOf<File>()
        val directCandidates = listOf(
            File(modBuildDir, "intermediates/runtime_library_classes_jar/androidMain/bundleAndroidMainClassesToRuntimeJar/classes.jar"),
            File(modBuildDir, "intermediates/compile_library_classes_jar/androidMain/bundleAndroidMainClassesToCompileJar/classes.jar"),
            File(modBuildDir, "intermediates/runtime_library_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar"),
            File(modBuildDir, "intermediates/compile_library_classes_jar/debug/bundleDebugClassesToCompileJar/classes.jar"),
            File(modBuildDir, "intermediates/runtime_library_classes_jar/release/bundleReleaseClassesToRuntimeJar/classes.jar"),
            File(modBuildDir, "intermediates/compile_library_classes_jar/release/bundleReleaseClassesToCompileJar/classes.jar")
        ).filter { it.isFile && it.length() > 0L }
            .sortedByDescending { it.lastModified() }

        foundJars.addAll(directCandidates)

        if (foundJars.isEmpty()) {
            listOf(
                File(modBuildDir, "intermediates/runtime_library_classes_jar"),
                File(modBuildDir, "intermediates/compile_library_classes_jar")
            ).filter { it.isDirectory }.forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.name == "classes.jar" && it.length() > 0L }
                    .sortedByDescending { it.lastModified() }
                    .forEach { foundJars.add(it) }
            }
        }

        val classDirs = listOf(
            File(modBuildDir, "classes/kotlin/android/main"),
            File(modBuildDir, "classes/kotlin/androidMain/main"),
            File(modBuildDir, "classes/java/androidMain"),
            File(modBuildDir, "tmp/kotlin-classes/androidMain"),
            File(modBuildDir, "tmp/kotlin-classes/debug"),
            File(modBuildDir, "tmp/kotlin-classes/release")
        ).filter { it.isDirectory }

        if (classDirs.isNotEmpty() && foundJars.isEmpty()) {
            val safeName = modulePath.trim(':').replace(':', '-').ifEmpty { moduleDir.name }
            val packagedJar = File(moduleJarsDir, "$safeName-android-classes.jar")
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
            }
            if (count > 0 && packagedJar.isFile && packagedJar.length() > 0L) {
                foundJars.add(packagedJar)
            }
        }

        return foundJars
    }

    private fun resolveExistingOrSiblingArtifact(file: File): File? {
        val normalized = file.invariantSeparatorsPath
        val candidates = mutableListOf<File>()
        if (file.isFile && file.length() > 0L) {
            candidates.add(file)
        }
        if (normalized.contains("/runtime_library_classes_jar/androidMain/")) {
            val compileSibling = File(
                normalized.replace(
                    "/runtime_library_classes_jar/androidMain/bundleAndroidMainClassesToRuntimeJar/",
                    "/compile_library_classes_jar/androidMain/bundleAndroidMainClassesToCompileJar/"
                )
            )
            if (compileSibling.isFile && compileSibling.length() > 0L) {
                candidates.add(compileSibling)
            }
        } else if (normalized.contains("/compile_library_classes_jar/androidMain/")) {
            val runtimeSibling = File(
                normalized.replace(
                    "/compile_library_classes_jar/androidMain/bundleAndroidMainClassesToCompileJar/",
                    "/runtime_library_classes_jar/androidMain/bundleAndroidMainClassesToRuntimeJar/"
                )
            )
            if (runtimeSibling.isFile && runtimeSibling.length() > 0L) {
                candidates.add(runtimeSibling)
            }
        }
        return candidates.maxByOrNull { it.lastModified() }
    }

    internal fun extractManifestRequiredClasses(manifestXml: String, fallbackPackage: String): List<String> {
        val packageAttrRegex = Regex("""\bpackage\s*=\s*"([^"]+)"""")
        val manifestPkg = packageAttrRegex.find(manifestXml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallbackPackage.trim()

        val tagRegex = Regex(
            """<\s*(application|activity|service|receiver|provider)\b([^>]*)>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val androidNameRegex = Regex("""\bandroid:name\s*=\s*"([^"]+)"""")
        val result = LinkedHashSet<String>()

        for (match in tagRegex.findAll(manifestXml)) {
            val attrs = match.groupValues[2]
            val rawName = androidNameRegex.find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
            if (rawName.isEmpty()) continue
            val fqcn = when {
                rawName.startsWith(".") -> "$manifestPkg$rawName"
                !rawName.contains(".") -> "$manifestPkg.$rawName"
                else -> rawName
            }
            result.add(fqcn)
        }
        return result.toList()
    }

    internal fun verifyRequiredClassesInMergedJarAndDex(
        manifestFile: File,
        fallbackAppId: String,
        mergedProgramJar: File,
        dexOutDir: File,
        requireGenericUiRuntimeClasses: Boolean = true
    ) {
        val manifestXml = if (manifestFile.isFile) manifestFile.readText() else ""
        val requiredClasses = LinkedHashSet<String>()
        requiredClasses.addAll(extractManifestRequiredClasses(manifestXml, fallbackAppId))

        if (requireGenericUiRuntimeClasses) {
            requiredClasses.add("com.nexustools.genericui.android.GenericUiAndroidApplication")
            requiredClasses.add("com.nexustools.genericui.android.GenericUiFragmentActivity")
            requiredClasses.add("com.nexustools.genericui.android.AndroidNativeFactory")
            requiredClasses.add("com.nexustools.genericui.core.GenericUI")
            requiredClasses.add("com.nexustools.genericui.component.fragment.Fragment")
        }

        if (requiredClasses.isEmpty()) return

        if (!mergedProgramJar.isFile || mergedProgramJar.length() == 0L) {
            throw GradleException(
                "Android APK packaging failed: merged program JAR does not exist or is empty at ${mergedProgramJar.absolutePath}"
            )
        }

        val presentJarEntries = HashSet<String>()
        JarFile(mergedProgramJar).use { jf ->
            val entries = jf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.size != 0L) {
                    presentJarEntries.add(entry.name.trimStart('/'))
                }
            }
        }

        val missingFromJar = requiredClasses.filter { fqcn ->
            "${fqcn.replace('.', '/')}.class" !in presentJarEntries
        }
        if (missingFromJar.isNotEmpty()) {
            throw GradleException(
                "Android APK packaging failed: required manifest/GenericUI classes are missing from merged program classes (${mergedProgramJar.absolutePath}): " +
                    missingFromJar.joinToString(", ") + ". " +
                    "Ensure all GenericUI modules (:common, :systemCommon, :mobile:common, :mobile:android) and app classes are compiled."
            )
        }

        val dexFiles = dexOutDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("classes") && it.name.endsWith(".dex") && it.length() > 0L }
            .orEmpty()
        if (dexFiles.isEmpty()) {
            throw GradleException(
                "Android APK packaging failed: no classes*.dex files found in ${dexOutDir.absolutePath}"
            )
        }

        val dexContents = dexFiles.map { String(it.readBytes(), StandardCharsets.ISO_8859_1) }
        val missingFromDex = requiredClasses.filter { fqcn ->
            val descriptor = "L${fqcn.replace('.', '/')};"
            dexContents.none { it.contains(descriptor) }
        }
        if (missingFromDex.isNotEmpty()) {
            throw GradleException(
                "Android APK packaging failed: required manifest/GenericUI classes are missing from D8 DEX output (${dexOutDir.absolutePath}): " +
                    missingFromDex.joinToString(", ")
            )
        }
    }

    private fun extractAars(aarFiles: List<File>, aarsExtractRoot: File): List<ExtractedAar> {
        val result = mutableListOf<ExtractedAar>()
        val packageAttrRegex = Regex("""\bpackage\s*=\s*"([^"]+)"""")

        aarFiles.forEachIndexed { index, aarFile ->
            val safeName = aarFile.nameWithoutExtension.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val extractDir = File(aarsExtractRoot, "${index.toString().padStart(3, '0')}-$safeName")
            extractDir.mkdirs()

            runCatching {
                ZipFile(aarFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val target = File(extractDir, entry.name)
                        if (!target.canonicalPath.startsWith(extractDir.canonicalPath)) continue
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(target).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
            }

            val aarManifest = File(extractDir, "AndroidManifest.xml")
            val pkgName = if (aarManifest.isFile) {
                packageAttrRegex.find(aarManifest.readText())?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }

            val classesJars = mutableListOf<File>()
            val mainClassesJar = File(extractDir, "classes.jar")
            if (mainClassesJar.isFile && mainClassesJar.length() > 0L) {
                classesJars.add(mainClassesJar)
            }
            val libsDir = File(extractDir, "libs")
            if (libsDir.isDirectory) {
                libsDir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("jar", ignoreCase = true) && it.length() > 0L }
                    ?.sortedBy { it.name }
                    ?.let { classesJars.addAll(it) }
            }

            val resDir = File(extractDir, "res").takeIf { dir ->
                dir.isDirectory && dir.walkTopDown().any { it.isFile }
            }
            val assetsDir = File(extractDir, "assets").takeIf { dir ->
                dir.isDirectory && dir.walkTopDown().any { it.isFile }
            }
            val jniDir = File(extractDir, "jni").takeIf { dir ->
                dir.isDirectory && dir.walkTopDown().any { it.isFile && it.extension.equals("so", ignoreCase = true) }
            }

            result.add(
                ExtractedAar(
                    name = safeName,
                    packageName = pkgName,
                    classesJars = classesJars,
                    resDir = resDir,
                    assetsDir = assetsDir,
                    jniDir = jniDir
                )
            )
        }

        return result
    }

    private fun stageAppResources(
        rootDir: File,
        genResDir: File?,
        stagedAppResDir: File
    ) {
        val validResPrefixes = setOf(
            "drawable",
            "mipmap",
            "values",
            "layout",
            "xml",
            "color",
            "anim",
            "animator",
            "interpolator",
            "menu",
            "raw",
            "font",
            "navigation",
            "transition"
        )
        val excludedExtensions = setOf(
            "svg",
            "scss",
            "sass",
            "css",
            "md",
            "cur",
            "icns",
            "ico",
            "desktop",
            "plist",
            "rc",
            "res",
            "ds_store",
            "db",
            "kt",
            "java",
            "js",
            "json"
        )
        val excludedNames = setOf(".ds_store", "thumbs.db", "desktop.ini")

        val candidateResRoots = buildList {
            add(File(rootDir, "src/commonMain/resources"))
            add(File(rootDir, "src/common/resources"))
            add(File(rootDir, "src/systemCommon/resources"))
            add(File(rootDir, "src/mobileCommon/resources"))
            add(File(rootDir, "src/mobileMain/resources"))
            add(File(rootDir, "src/android/resources"))
            add(File(rootDir, "src/androidMain/resources"))
            add(File(rootDir, "src/androidMain/res"))
            if (genResDir != null) {
                add(genResDir)
            }
        }.filter { it.isDirectory }

        for (resRoot in candidateResRoots) {
            val subDirs = resRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
            for (subDir in subDirs) {
                val dirType = subDir.name.substringBefore('-').lowercase()
                if (dirType !in validResPrefixes) continue

                val targetSubDir = File(stagedAppResDir, subDir.name.lowercase())
                val files = subDir.listFiles()?.filter { it.isFile }
                    ?.sortedWith(compareBy<File> { it.extension.lowercase() != "xml" }.thenBy { it.name })
                    .orEmpty()

                for (file in files) {
                    val lowerName = file.name.lowercase()
                    val ext = file.extension.lowercase()
                    if (lowerName in excludedNames || lowerName.startsWith(".") || ext in excludedExtensions) {
                        continue
                    }
                    if (dirType == "values" && ext != "xml") {
                        continue
                    }

                    targetSubDir.mkdirs()
                    val sanitizedStem = sanitizeAndroidResFileStem(file.nameWithoutExtension)
                    val targetFileName = if (ext.isNotEmpty()) "$sanitizedStem.$ext" else sanitizedStem

                    if (dirType != "values") {
                        // Ensure no duplicate resource with the same stem and a different extension exists in this folder
                        targetSubDir.listFiles()?.forEach { existing ->
                            if (existing.isFile &&
                                existing.nameWithoutExtension.equals(sanitizedStem, ignoreCase = true) &&
                                !existing.name.equals(targetFileName, ignoreCase = true)
                            ) {
                                runCatching { existing.delete() }
                            }
                        }
                    }

                    val targetFile = File(targetSubDir, targetFileName)
                    if (dirType == "values" && ext == "xml") {
                        val content = file.readText()
                        val normalizedXml = if (content.trimStart().startsWith("<?xml")) {
                            content
                        } else {
                            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n$content"
                        }
                        targetFile.writeText(normalizedXml)
                    } else {
                        file.copyTo(targetFile, overwrite = true)
                    }
                }
            }
        }

        // Ensure @drawable/icon exists in stagedAppResDir
        val hasIcon = stagedAppResDir.listFiles()
            ?.filter { it.isDirectory && (it.name.startsWith("drawable") || it.name.startsWith("mipmap")) }
            ?.any { dir ->
                dir.listFiles()?.any { f -> f.isFile && f.nameWithoutExtension == "icon" } == true
            } == true

        if (!hasIcon) {
            val drawableDir = File(stagedAppResDir, "drawable").apply { mkdirs() }
            File(drawableDir, "icon.xml").writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <path
                        android:fillColor="#6650A4"
                        android:pathData="M0,0h108v108h-108z"/>
                    <path
                        android:fillColor="#FFFFFF"
                        android:pathData="M30,34h48v8h-48zM30,50h48v8h-48zM30,66h32v8h-32z"/>
                </vector>
                """.trimIndent() + "\n"
            )
        }
    }

    private fun sanitizeAndroidResFileStem(stem: String): String {
        val cleaned = stem.trim().lowercase().replace(Regex("[^a-z0-9_.]"), "_")
        if (cleaned.isEmpty()) return "res_item"
        return if (cleaned.first().isDigit()) "_$cleaned" else cleaned
    }

    private fun compileResourcesWithAapt2(
        aapt2: File,
        extractedAars: List<ExtractedAar>,
        stagedAppResDir: File,
        compiledResDir: File
    ): Pair<List<File>, File?> {
        val aarFlataFiles = mutableListOf<File>()
        extractedAars.forEachIndexed { index, aar ->
            val resDir = aar.resDir ?: return@forEachIndexed
            val outFlata = File(compiledResDir, "aar-${index.toString().padStart(3, '0')}-${aar.name}.flata")
            compileDirWithAapt2(aapt2, resDir, outFlata)
            if (outFlata.isFile && outFlata.length() > 0L) {
                aarFlataFiles.add(outFlata)
            }
        }

        val hasAppRes = stagedAppResDir.walkTopDown().any { it.isFile }
        val appFlata = if (hasAppRes) {
            val outFlata = File(compiledResDir, "app-res.flata")
            compileDirWithAapt2(aapt2, stagedAppResDir, outFlata)
            outFlata.takeIf { it.isFile && it.length() > 0L }
        } else {
            null
        }

        return aarFlataFiles to appFlata
    }

    private fun compileDirWithAapt2(aapt2: File, resDir: File, outFlata: File) {
        val primaryCmd = listOf(
            aapt2.absolutePath,
            "compile",
            "--dir",
            resDir.absolutePath,
            "-o",
            outFlata.absolutePath
        )
        val primaryResult = executeProcess(primaryCmd)
        if (primaryResult.first == 0 && outFlata.isFile) {
            return
        }
        val legacyCmd = listOf(
            aapt2.absolutePath,
            "compile",
            "--legacy",
            "--dir",
            resDir.absolutePath,
            "-o",
            outFlata.absolutePath
        )
        val legacyResult = executeProcess(legacyCmd)
        if (legacyResult.first != 0 || !outFlata.isFile) {
            throw GradleException(
                "aapt2 compile failed for ${resDir.absolutePath} (exit ${legacyResult.first}):\n${legacyResult.second}"
            )
        }
    }

    private fun linkResourcesWithAapt2(
        aapt2: File,
        androidJar: File,
        manifestFile: File,
        appId: String,
        appVersion: String,
        minSdk: Int,
        targetSdk: Int,
        extractedAars: List<ExtractedAar>,
        aarFlataFiles: List<File>,
        appFlataFile: File?,
        rJavaDir: File,
        linkedApFile: File,
        workDir: File
    ) {
        val packageAttrRegex = Regex("""\bpackage\s*=\s*"([^"]+)"""")
        val manifestText = manifestFile.readText()
        val manifestPkg = packageAttrRegex.find(manifestText)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: appId

        val validPkgRegex = Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$")
        val extraPackages = LinkedHashSet<String>()
        if (appId != manifestPkg && validPkgRegex.matches(appId)) {
            extraPackages.add(appId)
        }
        extraPackages.add("com.nexustools.genericui.android")
        for (aar in extractedAars) {
            val pkg = aar.packageName?.trim().orEmpty()
            if (pkg.isNotEmpty() && pkg != manifestPkg && validPkgRegex.matches(pkg)) {
                extraPackages.add(pkg)
            }
        }
        extraPackages.remove(manifestPkg)

        // Write -R arguments to an @args file if there are many archives
        val rArgsFile = File(workDir, "aapt2-r-args.txt")
        val allFlata = buildList {
            addAll(aarFlataFiles)
            if (appFlataFile != null) {
                add(appFlataFile)
            }
        }
        rArgsFile.writeText(
            allFlata.joinToString(" ") { "-R ${it.absolutePath}" },
            StandardCharsets.UTF_8
        )

        val cmd = mutableListOf(
            aapt2.absolutePath,
            "link",
            "-I",
            androidJar.absolutePath,
            "--manifest",
            manifestFile.absolutePath,
            "--min-sdk-version",
            minSdk.toString(),
            "--target-sdk-version",
            targetSdk.toString(),
            "--version-code",
            "1",
            "--version-name",
            appVersion,
            "--auto-add-overlay",
            "--java",
            rJavaDir.absolutePath,
            "-o",
            linkedApFile.absolutePath
        )
        if (extraPackages.isNotEmpty()) {
            cmd.add("--extra-packages")
            cmd.add(extraPackages.joinToString(":"))
        }
        for (flata in allFlata) {
            cmd.add("-R")
            cmd.add(flata.absolutePath)
        }

        runCommand(cmd, "aapt2 link")
        if (!linkedApFile.isFile || linkedApFile.length() == 0L) {
            throw GradleException("aapt2 link failed to produce ${linkedApFile.absolutePath}")
        }
    }

    private fun compileRJavaToJar(
        rJavaDir: File,
        rClassesDir: File,
        rJarFile: File,
        workDir: File
    ) {
        val javaFiles = rJavaDir.walkTopDown().filter { it.isFile && it.extension.equals("java", ignoreCase = true) }.toList()
        if (javaFiles.isEmpty()) {
            return
        }

        val sourcesListFile = File(workDir, "r-java-sources.txt")
        sourcesListFile.writeText(
            javaFiles.joinToString("\n") { "\"${it.absolutePath.replace("\\", "\\\\")}\"" },
            StandardCharsets.UTF_8
        )

        val javacArgs = arrayOf(
            "-source",
            "11",
            "-target",
            "11",
            "-proc:none",
            "-g:none",
            "-nowarn",
            "-d",
            rClassesDir.absolutePath,
            "@${sourcesListFile.absolutePath}"
        )

        val compiler = ToolProvider.getSystemJavaCompiler()
        if (compiler != null) {
            val outStream = ByteArrayOutputStream()
            val errStream = ByteArrayOutputStream()
            val code = compiler.run(null, outStream, errStream, *javacArgs)
            if (code != 0) {
                throw GradleException(
                    "Failed to compile generated R.java files (exit $code):\n" +
                        errStream.toString(StandardCharsets.UTF_8) + "\n" +
                        outStream.toString(StandardCharsets.UTF_8)
                )
            }
        } else {
            val javacBin = resolveJavacExecutable()
            runCommand(listOf(javacBin.absolutePath) + javacArgs.toList(), "javac R.java")
        }

        JarOutputStream(BufferedOutputStream(FileOutputStream(rJarFile))).use { jos ->
            rClassesDir.walkTopDown().filter { it.isFile && it.extension.equals("class", ignoreCase = true) }.forEach { clsFile ->
                val relPath = clsFile.relativeTo(rClassesDir).invariantSeparatorsPath.trimStart('/')
                val entry = JarEntry(relPath)
                jos.putNextEntry(entry)
                clsFile.inputStream().buffered().use { it.copyTo(jos) }
                jos.closeEntry()
            }
        }
    }

    private fun buildMergedProgramJar(inputJars: List<File>, mergedJarFile: File) {
        val seenClassEntries = HashSet<String>()
        JarOutputStream(BufferedOutputStream(FileOutputStream(mergedJarFile))).use { jos ->
            for (jarFile in inputJars) {
                if (!jarFile.isFile || jarFile.length() == 0L) continue
                runCatching {
                    JarFile(jarFile).use { jf ->
                        val entries = jf.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.isDirectory) continue
                            val name = entry.name.trimStart('/')
                            if (!name.endsWith(".class")) continue
                            if (name == "module-info.class" || name.endsWith("/module-info.class")) continue
                            if (name.startsWith("META-INF/versions/")) continue
                            if (!seenClassEntries.add(name)) continue

                            val outEntry = JarEntry(name)
                            jos.putNextEntry(outEntry)
                            jf.getInputStream(entry).use { it.copyTo(jos) }
                            jos.closeEntry()
                        }
                    }
                }
            }
        }
    }

    private fun runD8(
        d8Jar: File,
        androidJar: File,
        minSdk: Int,
        isRelease: Boolean,
        programJar: File,
        dexOutDir: File,
        workDir: File
    ) {
        val d8ArgsFile = File(workDir, "d8-args.txt")
        val argsLines = listOf(
            if (isRelease) "--release" else "--debug",
            "--min-api",
            minSdk.toString(),
            "--lib",
            androidJar.absolutePath,
            "--output",
            dexOutDir.absolutePath,
            programJar.absolutePath
        )
        d8ArgsFile.writeText(argsLines.joinToString("\n"), StandardCharsets.UTF_8)

        runCommand(
            listOf(
                resolveJavaExecutable().absolutePath,
                "-Xmx2048m",
                "-cp",
                d8Jar.absolutePath,
                "com.android.tools.r8.D8",
                "@${d8ArgsFile.absolutePath}"
            ),
            "d8"
        )

        val primaryDex = File(dexOutDir, "classes.dex")
        if (!primaryDex.isFile || primaryDex.length() == 0L) {
            throw GradleException("D8 did not produce classes.dex in ${dexOutDir.absolutePath}")
        }
    }

    private fun assembleUnsignedApk(
        linkedApFile: File,
        dexOutDir: File,
        rootDir: File,
        buildDir: File,
        extractedAars: List<ExtractedAar>,
        allInputJars: List<File>,
        abiFilter: String,
        unsignedApk: File
    ) {
        val addedEntries = HashSet<String>()
        val serviceFiles = LinkedHashMap<String, LinkedHashSet<String>>()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(unsignedApk))).use { zos ->
            // 1. Copy linked resources and binary AndroidManifest.xml from linked.ap_, preserving STORED entries (especially resources.arsc)
            ZipFile(linkedApFile).use { apZip ->
                val entries = apZip.entries()
                while (entries.hasMoreElements()) {
                    val srcEntry = entries.nextElement()
                    if (srcEntry.isDirectory) continue
                    val entryName = srcEntry.name.trimStart('/')
                    if (entryName.isEmpty() || !addedEntries.add(entryName)) continue

                    val bytes = apZip.getInputStream(srcEntry).use { it.readBytes() }
                    if (srcEntry.method == ZipEntry.STORED || entryName == "resources.arsc") {
                        writeStoredEntry(zos, entryName, bytes)
                    } else {
                        val dstEntry = ZipEntry(entryName)
                        dstEntry.method = ZipEntry.DEFLATED
                        zos.putNextEntry(dstEntry)
                        zos.write(bytes)
                        zos.closeEntry()
                    }
                }
            }

            // 2. Add classes*.dex files from dexOutDir
            dexOutDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("dex", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?.forEach { dexFile ->
                    if (addedEntries.add(dexFile.name)) {
                        val dstEntry = ZipEntry(dexFile.name)
                        dstEntry.method = ZipEntry.DEFLATED
                        zos.putNextEntry(dstEntry)
                        dexFile.inputStream().buffered().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }

            // 3. Add assets/** from project and AARs
            val assetDirs = buildList {
                add(File(rootDir, "src/androidMain/assets"))
                add(File(rootDir, "src/commonMain/assets"))
                for (aar in extractedAars) {
                    aar.assetsDir?.let { add(it) }
                }
            }.filter { it.isDirectory }

            for (assetRoot in assetDirs) {
                assetRoot.walkTopDown().filter { it.isFile }.forEach { assetFile ->
                    val relPath = "assets/" + assetFile.relativeTo(assetRoot).invariantSeparatorsPath.trimStart('/')
                    if (addedEntries.add(relPath)) {
                        val dstEntry = ZipEntry(relPath)
                        dstEntry.method = ZipEntry.DEFLATED
                        zos.putNextEntry(dstEntry)
                        assetFile.inputStream().buffered().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            // 4. Add Kotlin .kotlin_builtins and collect META-INF/services/** from runtime/AAR jars
            for (jarFile in allInputJars) {
                if (!jarFile.isFile || jarFile.length() == 0L) continue
                runCatching {
                    JarFile(jarFile).use { jf ->
                        val entries = jf.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.isDirectory) continue
                            val name = entry.name.trimStart('/')
                            if (name.isEmpty() || name.endsWith(".class")) continue
                            if (name.startsWith("META-INF/services/") && name.length > "META-INF/services/".length) {
                                val lines = jf.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).readLines()
                                val set = serviceFiles.getOrPut(name) { LinkedHashSet() }
                                lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { set.add(it) }
                                continue
                            }
                            if (shouldSkipPackagingEntry(name)) continue
                            if (name.endsWith(".kotlin_builtins") && addedEntries.add(name)) {
                                val dstEntry = ZipEntry(name)
                                dstEntry.method = ZipEntry.DEFLATED
                                zos.putNextEntry(dstEntry)
                                jf.getInputStream(entry).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }

            for ((servicePath, providers) in serviceFiles) {
                if (providers.isNotEmpty() && addedEntries.add(servicePath)) {
                    val dstEntry = ZipEntry(servicePath)
                    dstEntry.method = ZipEntry.DEFLATED
                    zos.putNextEntry(dstEntry)
                    zos.write((providers.joinToString("\n") + "\n").toByteArray(StandardCharsets.UTF_8))
                    zos.closeEntry()
                }
            }

            // 5. Add JNI native libraries (lib/<abi>/*.so) from AARs and Kotlin/Native outputs
            val allowedAbis = if (abiFilter.isNotEmpty()) {
                setOf(abiFilter)
            } else {
                setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }

            for (aar in extractedAars) {
                val jniDir = aar.jniDir ?: continue
                jniDir.listFiles()?.filter { it.isDirectory && it.name in allowedAbis }?.forEach { abiDir ->
                    abiDir.walkTopDown().filter { it.isFile && it.extension.equals("so", ignoreCase = true) }.forEach { soFile ->
                        val entryPath = "lib/${abiDir.name}/${soFile.name}"
                        if (addedEntries.add(entryPath)) {
                            writeStoredEntry(zos, entryPath, soFile.readBytes())
                        }
                    }
                }
            }

            val nativeTargetDirMap = mapOf(
                "x86" to "androidNativeX86",
                "x86_64" to "androidNativeX64",
                "armeabi-v7a" to "androidNativeArm32",
                "arm64-v8a" to "androidNativeArm64"
            )
            for ((abi, kmpTargetName) in nativeTargetDirMap) {
                if (abi !in allowedAbis) continue
                val binTargetDir = File(buildDir, "bin/$kmpTargetName")
                if (!binTargetDir.isDirectory) continue
                binTargetDir.walkTopDown().filter { it.isFile && it.extension.equals("so", ignoreCase = true) }.forEach { soFile ->
                    val entryPath = "lib/$abi/${soFile.name}"
                    if (addedEntries.add(entryPath)) {
                        writeStoredEntry(zos, entryPath, soFile.readBytes())
                    }
                }
            }
        }
    }

    private fun writeStoredEntry(zos: ZipOutputStream, entryName: String, bytes: ByteArray) {
        val crc = CRC32()
        crc.update(bytes)
        val entry = ZipEntry(entryName)
        entry.method = ZipEntry.STORED
        entry.size = bytes.size.toLong()
        entry.compressedSize = bytes.size.toLong()
        entry.crc = crc.value
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun shouldSkipPackagingEntry(name: String): Boolean {
        val upper = name.uppercase()
        if (upper == "ANDROIDMANIFEST.XML" || upper == "RESOURCES.ARSC") return true
        if (upper.endsWith("/MODULE-INFO.CLASS") || upper == "MODULE-INFO.CLASS") return true
        if (upper.startsWith("META-INF/")) {
            if (upper.endsWith(".SF") ||
                upper.endsWith(".DSA") ||
                upper.endsWith(".RSA") ||
                upper.endsWith(".EC") ||
                upper.endsWith("MANIFEST.MF") ||
                upper.endsWith(".VERSION") ||
                upper.startsWith("META-INF/MAVEN/") ||
                upper.startsWith("META-INF/PROGUARD/") ||
                upper.startsWith("META-INF/COM.ANDROID.TOOLS/") ||
                upper.startsWith("META-INF/AL2.0") ||
                upper.startsWith("META-INF/LGPL2.1") ||
                upper.startsWith("META-INF/LICENSE") ||
                upper.startsWith("META-INF/NOTICE")
            ) {
                return true
            }
        }
        return false
    }

    private fun ensureDebugKeystore(workDir: File): File {
        val userHome = System.getProperty("user.home").orEmpty()
        val defaultKeystore = if (userHome.isNotEmpty()) {
            File(userHome, ".android/debug.keystore")
        } else {
            File(workDir, "debug.keystore")
        }
        if (defaultKeystore.isFile && defaultKeystore.length() > 0L) {
            return defaultKeystore
        }

        val targetKeystore = runCatching {
            defaultKeystore.parentFile?.mkdirs()
            defaultKeystore
        }.getOrElse { File(workDir, "debug.keystore") }

        if (!targetKeystore.isFile || targetKeystore.length() == 0L) {
            val keytool = resolveKeytoolExecutable()
            runCommand(
                listOf(
                    keytool.absolutePath,
                    "-genkeypair",
                    "-v",
                    "-keystore",
                    targetKeystore.absolutePath,
                    "-storepass",
                    "android",
                    "-alias",
                    "androiddebugkey",
                    "-keypass",
                    "android",
                    "-keyalg",
                    "RSA",
                    "-keysize",
                    "2048",
                    "-validity",
                    "10000",
                    "-dname",
                    "CN=Android Debug,O=Android,C=US"
                ),
                "keytool -genkeypair"
            )
        }
        return targetKeystore
    }

    private fun resolveJavaExecutable(): File {
        val isWin = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        val exeName = if (isWin) "java.exe" else "java"
        val javaHome = System.getProperty("java.home").orEmpty()
        if (javaHome.isNotEmpty()) {
            val candidate = File(javaHome, "bin/$exeName")
            if (candidate.isFile) return candidate
        }
        return File(exeName)
    }

    private fun resolveJavacExecutable(): File {
        val isWin = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        val exeName = if (isWin) "javac.exe" else "javac"
        val javaHome = System.getProperty("java.home").orEmpty()
        if (javaHome.isNotEmpty()) {
            val candidate = File(javaHome, "bin/$exeName")
            if (candidate.isFile) return candidate
        }
        return File(exeName)
    }

    private fun resolveKeytoolExecutable(): File {
        val isWin = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        val exeName = if (isWin) "keytool.exe" else "keytool"
        val javaHome = System.getProperty("java.home").orEmpty()
        if (javaHome.isNotEmpty()) {
            val candidate = File(javaHome, "bin/$exeName")
            if (candidate.isFile) return candidate
        }
        return File(exeName)
    }

    private fun runCommand(command: List<String>, stepName: String) {
        val (exitCode, output) = executeProcess(command)
        if (exitCode != 0) {
            throw GradleException("Android APK packaging step '$stepName' failed (exit $exitCode):\n$output")
        }
    }

    private fun executeProcess(command: List<String>): Pair<Int, String> {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        val process = pb.start()
        val output = BufferedInputStream(process.inputStream).bufferedReader(StandardCharsets.UTF_8).readText()
        val exitCode = process.waitFor()
        return exitCode to output
    }

    private fun compareVersionStrings(a: String, b: String): Int {
        val aParts = a.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val bParts = b.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val av = aParts.getOrElse(i) { 0 }
            val bv = bParts.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return a.compareTo(b)
    }
}
