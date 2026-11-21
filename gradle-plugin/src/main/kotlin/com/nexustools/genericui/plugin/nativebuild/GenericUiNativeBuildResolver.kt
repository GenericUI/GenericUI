package com.nexustools.genericui.plugin.nativebuild

import com.nexustools.genericui.plugin.GenericUiExtension
import com.nexustools.genericui.plugin.GenericUiVariantScope
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.nio.file.Path

/**
 * Resolves merged consumer `jni`, `cinterops`, and `nativeLibraries` configurations across
 * `GenericUiExtension` and `GenericUiVariantConfig` scopes, and wires them onto Kotlin/Native
 * targets, Android/Swing packaging tasks, and JVM / Robolectric `Test` tasks.
 */
object GenericUiNativeBuildResolver {

    private const val EXTRA_WIRING_DONE = "com.nexustools.genericui.nativebuild.appliedKeys"

    /** Converts a Gradle path/file/provider/glob object into a normalized string path. */
    fun normalizePathValue(project: Project, raw: Any): String {
        val unwrapped = if (raw is Provider<*>) raw.orNull ?: return "" else raw
        return when (unwrapped) {
            is File -> unwrapped.absolutePath
            is Path -> unwrapped.toAbsolutePath().toString()
            is FileSystemLocation -> unwrapped.asFile.absolutePath
            else -> unwrapped.toString().trim()
        }
    }

    /** Returns `true` if any `jni` library, CMake spec, or `jniLibsDirs` entry is configured. */
    fun hasAnyJniConfiguration(extension: GenericUiExtension): Boolean {
        if (extension.jni.hasAnyConfiguration()) return true
        return GenericUiVariantScope.entries.any { scope ->
            extension.variantConfigFor(scope).jni.hasAnyConfiguration()
        }
    }

    /** Returns `true` if any `jni.library` or `jni.cmake` requires compilation. */
    fun hasAnyCompilableJniConfiguration(extension: GenericUiExtension): Boolean {
        if (extension.jni.libraries.isNotEmpty() || extension.jni.cmakes.isNotEmpty()) return true
        return GenericUiVariantScope.entries.any { scope ->
            val cfg = extension.variantConfigFor(scope).jni
            cfg.libraries.isNotEmpty() || cfg.cmakes.isNotEmpty()
        }
    }

    /** Returns `true` when at least one configured library/CMake produces host test or Swing libraries. */
    fun hasHostJniLibraries(project: Project, extension: GenericUiExtension): Boolean {
        val libs = resolveJniLibraries(project, extension)
        val cmakes = resolveJniCmakes(project, extension)
        return libs.any { it.buildHostLibraryForTests || (extension.backends.swingConfig.enabled && it.bundleInSwingJar) } ||
            cmakes.any { it.buildHostLibraryForTests || (extension.backends.swingConfig.enabled && it.bundleInSwingJar) }
    }

    /** Resolves merged [GenericUiJniLibrarySpec] snapshots ordered from general to specific scopes. */
    fun resolveJniLibraries(project: Project, extension: GenericUiExtension): List<GenericUiJniLibrarySpec> {
        val merged = linkedMapOf<String, GenericUiJniLibrarySpec>()
        val normalizer: (Any) -> String = { normalizePathValue(project, it) }

        fun applyConfig(
            cfg: GenericUiJniConfig,
            defaultAndroid: Boolean,
            defaultHostTests: Boolean,
            defaultSwing: Boolean
        ) {
            for (spec in cfg.libraries.values) {
                val existing = merged[spec.name]
                if (existing == null) {
                    merged[spec.name] = spec.toSnapshot(
                        pathNormalizer = normalizer,
                        defaultBuildAndroid = defaultAndroid,
                        defaultBuildHostTests = defaultHostTests,
                        defaultBundleSwing = defaultSwing
                    )
                } else {
                    existing.mergeFrom(
                        spec.toSnapshot(
                            pathNormalizer = normalizer,
                            defaultBuildAndroid = existing.buildAndroidLibraries,
                            defaultBuildHostTests = existing.buildHostLibraryForTests,
                            defaultBundleSwing = existing.bundleInSwingJar
                        )
                    )
                }
            }
        }

        applyConfig(extension.jni, defaultAndroid = true, defaultHostTests = true, defaultSwing = true)
        for (scope in listOf(
            GenericUiVariantScope.COMMON,
            GenericUiVariantScope.SYSTEM_COMMON,
            GenericUiVariantScope.JVM_COMMON,
            GenericUiVariantScope.MOBILE_COMMON,
            GenericUiVariantScope.DESKTOP_COMMON
        )) {
            applyConfig(
                extension.variantConfigFor(scope).jni,
                defaultAndroid = true,
                defaultHostTests = true,
                defaultSwing = true
            )
        }
        applyConfig(
            extension.android.jni,
            defaultAndroid = true,
            defaultHostTests = true,
            defaultSwing = false
        )
        applyConfig(
            extension.swing.jni,
            defaultAndroid = false,
            defaultHostTests = true,
            defaultSwing = true
        )

        return merged.values.toList()
    }

    /** Resolves merged [GenericUiJniCmakeSpec] snapshots across scopes. */
    fun resolveJniCmakes(project: Project, extension: GenericUiExtension): List<GenericUiJniCmakeSpec> {
        val merged = linkedMapOf<String, GenericUiJniCmakeSpec>()
        val normalizer: (Any) -> String = { normalizePathValue(project, it) }

        fun applyConfig(
            cfg: GenericUiJniConfig,
            defaultAndroid: Boolean,
            defaultHostTests: Boolean,
            defaultSwing: Boolean
        ) {
            for (spec in cfg.cmakes) {
                merged[spec.name] = spec.toSnapshot(
                    pathNormalizer = normalizer,
                    defaultBuildAndroid = defaultAndroid,
                    defaultBuildHostTests = defaultHostTests,
                    defaultBundleSwing = defaultSwing
                )
            }
        }

        applyConfig(extension.jni, defaultAndroid = true, defaultHostTests = true, defaultSwing = true)
        for (scope in listOf(
            GenericUiVariantScope.COMMON,
            GenericUiVariantScope.SYSTEM_COMMON,
            GenericUiVariantScope.JVM_COMMON,
            GenericUiVariantScope.MOBILE_COMMON,
            GenericUiVariantScope.DESKTOP_COMMON
        )) {
            applyConfig(
                extension.variantConfigFor(scope).jni,
                defaultAndroid = true,
                defaultHostTests = true,
                defaultSwing = true
            )
        }
        applyConfig(extension.android.jni, defaultAndroid = true, defaultHostTests = true, defaultSwing = false)
        applyConfig(extension.swing.jni, defaultAndroid = false, defaultHostTests = true, defaultSwing = true)
        return merged.values.toList()
    }

    /** Resolves all configured `jniLibsDirs` across top-level and variant scopes to absolute paths. */
    fun resolveJniLibsDirPaths(project: Project, extension: GenericUiExtension): List<String> {
        val paths = LinkedHashSet<String>()
        fun collect(cfg: GenericUiJniConfig) {
            for (raw in cfg.jniLibsDirs) {
                val str = normalizePathValue(project, raw)
                if (str.isEmpty()) continue
                val file = File(str).let { if (it.isAbsolute) it else File(project.projectDir, it.path) }
                paths.add(file.absolutePath)
            }
        }
        collect(extension.jni)
        for (scope in GenericUiVariantScope.entries) {
            collect(extension.variantConfigFor(scope).jni)
        }
        return paths.toList()
    }

    /** Collects concrete local C/C++/header/CMake files so Gradle UP-TO-DATE checks detect native edits. */
    fun collectLocalInputFiles(project: Project, extension: GenericUiExtension): List<File> {
        val files = LinkedHashSet<File>()
        val libs = resolveJniLibraries(project, extension)
        val cmakes = resolveJniCmakes(project, extension)

        fun addPathOrDir(raw: Any) {
            val str = normalizePathValue(project, raw)
            if (str.isEmpty()) return
            val hasGlob = str.contains('*') || str.contains('?') || str.contains('[')
            if (hasGlob) {
                val baseDir = File(str.substringBefore('*').substringBefore('?').substringBefore('['))
                    .let { if (it.isAbsolute) it else File(project.projectDir, it.path) }
                    .let { if (it.isDirectory) it else it.parentFile ?: project.projectDir }
                if (baseDir.isDirectory) {
                    baseDir.walkTopDown().filter { it.isFile }.forEach { files.add(it) }
                }
                return
            }
            val candidate = File(str).let { if (it.isAbsolute) it else File(project.projectDir, it.path) }
            if (candidate.isFile) {
                files.add(candidate)
            } else if (candidate.isDirectory) {
                candidate.walkTopDown().filter { it.isFile }.forEach { files.add(it) }
            }
        }

        for (lib in libs) {
            lib.sources.forEach(::addPathOrDir)
            lib.includeDirs.forEach(::addPathOrDir)
            lib.jniHeaderSources.forEach(::addPathOrDir)
        }
        for (cmake in cmakes) {
            cmake.listsFile?.let(::addPathOrDir)
        }
        return files.toList()
    }

    /**
     * Wires `cinterops` and `nativeLibraries` from `GenericUiExtension` and matching
     * `GenericUiVariantConfig` scopes onto every registered Kotlin/Native target.
     */
    fun configureKotlinNativeInteropsAndLinks(project: Project, extension: GenericUiExtension) {
        val kmp = project.extensions.findByName("kotlin") ?: return
        val targets = invokeNoArg(kmp, "getTargets") as? NamedDomainObjectContainer<*> ?: return

        @Suppress("UNCHECKED_CAST")
        val appliedKeys = run {
            val extra = project.extensions.extraProperties
            if (extra.has(EXTRA_WIRING_DONE)) {
                extra.get(EXTRA_WIRING_DONE) as MutableSet<String>
            } else {
                LinkedHashSet<String>().also { extra.set(EXTRA_WIRING_DONE, it) }
            }
        }

        for (target in targets) {
            if (target == null) continue
            val konanTargetObj = invokeNoArg(target, "getKonanTarget") ?: continue
            val targetName = (invokeNoArg(target, "getName") as? String).orEmpty()
            val primaryScope = resolveVariantScopeForTarget(targetName) ?: continue

            // 1. Apply nativeLibraries linkerOpts onto binaries of this native target
            val resolvedLinkOpts = resolveNativeLinkerOpts(project.projectDir, extension, primaryScope)
            if (resolvedLinkOpts.isNotEmpty()) {
                applyLinkerOptsToTargetBinaries(target, resolvedLinkOpts)
            }

            // 2. Apply cinterops onto matching compilations of this native target
            val compilations = invokeNoArg(target, "getCompilations") as? NamedDomainObjectContainer<*> ?: continue
            for (comp in compilations) {
                if (comp == null) continue
                val compName = (invokeNoArg(comp, "getName") as? String).orEmpty()
                if (compName.endsWith("Test", ignoreCase = true) || compName == "test") continue
                val compScope = resolveVariantScopeForCompilation(targetName, compName, extension) ?: primaryScope
                val cinteropSpecs = resolveCInteropsForScope(extension, compScope)
                    .filter { it.compilationName == compName }
                if (cinteropSpecs.isEmpty()) continue

                val cinteropsContainer = invokeNoArg(comp, "getCinterops") as? NamedDomainObjectContainer<*> ?: continue
                for (spec in cinteropSpecs) {
                    val key = "$targetName:$compName:${spec.name}:${konanTargetObj.hashCode()}"
                    if (!appliedKeys.add(key)) continue

                    val settings = cinteropsContainer.findByName(spec.name) ?: cinteropsContainer.create(spec.name) ?: continue
                    configureCInteropSettings(project, settings, spec)
                }
            }
        }
    }

    /**
     * Wires `compileGenericUiJniLibraries` task dependencies and `-Djava.library.path` / `PATH`
     * environment injection onto host JVM and Android unit-test tasks.
     */
    fun configureJniTaskWiring(
        project: Project,
        extension: GenericUiExtension,
        fetchTaskName: String,
        compileTaskName: String,
        androidApkTaskNames: List<String>,
        swingJarTaskNames: List<String>
    ) {
        val hasCompilable = hasAnyCompilableJniConfiguration(extension)
        val hasHostLibs = hasCompilable && hasHostJniLibraries(project, extension)
        val hasPrebuiltJniDirs = resolveJniLibsDirPaths(project, extension).isNotEmpty()
        val hostOsArchTag = GenericUiJniToolchainResolver.hostOsArchTag()
        val hostJniDir = project.layout.buildDirectory.dir("intermediates/genericui_jni/host/$hostOsArchTag").get().asFile

        if (hasCompilable) {
            project.tasks.matching {
                it.name == "compileAndroidMain" ||
                    it.name == "compileDebugKotlinAndroid" ||
                    it.name == "compileReleaseKotlinAndroid"
            }.configureEach {
                dependsOn(compileTaskName)
            }

            for (apkTaskName in androidApkTaskNames) {
                project.tasks.findByName(apkTaskName)?.dependsOn(compileTaskName)
            }
            for (jarTaskName in swingJarTaskNames) {
                project.tasks.findByName(jarTaskName)?.dependsOn(compileTaskName)
            }
        }

        if (hasHostLibs || hasPrebuiltJniDirs) {
            val hostJniDirs = buildList {
                if (hasHostLibs) add(hostJniDir)
                for (dirPath in resolveJniLibsDirPaths(project, extension)) {
                    val dir = File(dirPath)
                    val hostSub = File(dir, hostOsArchTag)
                    if (hostSub.isDirectory) add(hostSub)
                }
            }

            project.tasks.withType(Test::class.java).configureEach {
                if (name in setOf(
                        "testAndroidHostTest",
                        "testDebugUnitTest",
                        "testReleaseUnitTest",
                        "jvmTest",
                        "test"
                    ) || name.endsWith("HostTest") || name.endsWith("UnitTest")
                ) {
                    if (hasHostLibs) {
                        dependsOn(compileTaskName)
                    }
                    doFirst {
                        injectHostLibraryPaths(
                            dirs = hostJniDirs,
                            getSysProp = { systemProperties["java.library.path"]?.toString() },
                            setSysProp = { systemProperty("java.library.path", it) },
                            getEnv = { environment[it]?.toString() },
                            setEnv = { k, v -> environment(k, v) }
                        )
                    }
                }
            }

            project.tasks.withType(JavaExec::class.java).configureEach {
                if (name == "jvmRun" || name == "run") {
                    if (hasHostLibs) {
                        dependsOn(compileTaskName)
                    }
                    doFirst {
                        injectHostLibraryPaths(
                            dirs = hostJniDirs,
                            getSysProp = { systemProperties["java.library.path"]?.toString() },
                            setSysProp = { systemProperty("java.library.path", it) },
                            getEnv = { environment[it]?.toString() },
                            setEnv = { k, v -> environment(k, v) }
                        )
                    }
                }
            }
        }

        // Ensure fetch task runs before compile task
        project.tasks.findByName(compileTaskName)?.dependsOn(fetchTaskName)
    }

    private fun injectHostLibraryPaths(
        dirs: List<File>,
        getSysProp: () -> String?,
        setSysProp: (String) -> Unit,
        getEnv: (String) -> String?,
        setEnv: (String, String) -> Unit
    ) {
        val validDirs = dirs.map { it.absolutePath }.distinct()
        if (validDirs.isEmpty()) return

        val existingLibPath = getSysProp()?.takeIf { it.isNotBlank() }
            ?: System.getProperty("java.library.path").orEmpty()
        setSysProp(prependPathEntries(validDirs, existingLibPath))

        val os = GenericUiJniToolchainResolver.normalizedHostOs()
        val envVarName = when (os) {
            "windows" -> "PATH"
            "macos" -> "DYLD_LIBRARY_PATH"
            else -> "LD_LIBRARY_PATH"
        }
        val existingEnv = getEnv(envVarName)?.takeIf { it.isNotBlank() }
            ?: System.getenv(envVarName).orEmpty()
        setEnv(envVarName, prependPathEntries(validDirs, existingEnv))
    }

    private fun prependPathEntries(prefixDirs: List<String>, existing: String): String {
        val parts = LinkedHashSet<String>()
        parts.addAll(prefixDirs)
        if (existing.isNotBlank()) {
            existing.split(File.pathSeparatorChar)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { parts.add(it) }
        }
        return parts.joinToString(File.pathSeparator)
    }

    private fun resolveVariantScopeForTarget(targetName: String): GenericUiVariantScope? = when {
        targetName.startsWith("mingw") || targetName.startsWith("windows") -> GenericUiVariantScope.WINDOWS
        targetName.startsWith("macos") -> GenericUiVariantScope.MACOS
        targetName.startsWith("linux") -> GenericUiVariantScope.LINUX_COMMON
        targetName.startsWith("ios") -> GenericUiVariantScope.IOS
        targetName.startsWith("androidNative") -> GenericUiVariantScope.ANDROID
        else -> null
    }

    private fun resolveVariantScopeForCompilation(
        targetName: String,
        compilationName: String,
        extension: GenericUiExtension
    ): GenericUiVariantScope? {
        if (targetName.startsWith("linux")) {
            return when {
                compilationName.equals("gtk", ignoreCase = true) -> GenericUiVariantScope.GTK
                compilationName.equals("qt", ignoreCase = true) -> GenericUiVariantScope.QT
                extension.backends.gtkEnabled && !extension.backends.qtEnabled -> GenericUiVariantScope.GTK
                extension.backends.qtEnabled && !extension.backends.gtkEnabled -> GenericUiVariantScope.QT
                else -> GenericUiVariantScope.LINUX_COMMON
            }
        }
        if (targetName.startsWith("ios")) {
            return if (compilationName.equals("ipados", ignoreCase = true) ||
                (!extension.backends.iosEnabled && extension.backends.ipadosEnabled)
            ) {
                GenericUiVariantScope.IPADOS
            } else {
                GenericUiVariantScope.IOS
            }
        }
        return resolveVariantScopeForTarget(targetName)
    }

    private fun resolveNativeLinkerOpts(
        projectDir: File,
        extension: GenericUiExtension,
        targetScope: GenericUiVariantScope
    ): List<String> {
        val opts = LinkedHashSet<String>()
        opts.addAll(extension.nativeLibraries.resolveLinkerOpts(projectDir))
        for (scope in GenericUiVariantScope.chainFromCommonToTarget(targetScope)) {
            opts.addAll(extension.variantConfigFor(scope).nativeLibraries.resolveLinkerOpts(projectDir))
        }
        return opts.toList()
    }

    private fun resolveCInteropsForScope(
        extension: GenericUiExtension,
        targetScope: GenericUiVariantScope
    ): List<GenericUiCInteropSpec> {
        val merged = linkedMapOf<String, GenericUiCInteropSpec>()
        for (spec in extension.cinterops.all()) {
            merged[spec.name] = spec
        }
        for (scope in GenericUiVariantScope.chainFromCommonToTarget(targetScope)) {
            for (spec in extension.variantConfigFor(scope).cinterops.all()) {
                merged[spec.name] = spec
            }
        }
        return merged.values.toList()
    }

    private fun applyLinkerOptsToTargetBinaries(nativeTarget: Any, opts: List<String>) {
        if (opts.isEmpty()) return
        runCatching {
            val binaries = invokeNoArg(nativeTarget, "getBinaries") as? org.gradle.api.DomainObjectCollection<*>
            binaries?.all(Action<Any> {
                val bin = this
                val existingOpts = invokeNoArg(bin, "getLinkerOpts") as? MutableCollection<*>
                val missing = if (existingOpts != null) opts.filter { it !in existingOpts } else opts
                if (missing.isNotEmpty()) {
                    bin.javaClass.methods.firstOrNull {
                        it.name == "linkerOpts" &&
                            it.parameterCount == 1 &&
                            Iterable::class.java.isAssignableFrom(it.parameterTypes[0])
                    }?.invoke(bin, missing)
                }
            })
        }
    }

    private fun configureCInteropSettings(
        project: Project,
        settings: Any,
        spec: GenericUiCInteropSpec
    ) {
        val cls = settings.javaClass
        spec.defFile?.let { rawDef ->
            val file = resolveProjectFile(project, rawDef)
            cls.methods.firstOrNull { it.name == "defFile" && it.parameterCount == 1 }
                ?.invoke(settings, file)
        }
        spec.packageName?.takeIf { it.isNotBlank() }?.let { pkg ->
            cls.methods.firstOrNull {
                it.name == "packageName" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
            }?.invoke(settings, pkg)
        }
        if (spec.headers.isNotEmpty()) {
            val headerFiles = spec.headers.map { resolveProjectFile(project, it) }.toTypedArray()
            cls.methods.firstOrNull {
                it.name == "headers" && it.parameterCount == 1 && it.parameterTypes[0].isArray
            }?.invoke(settings, headerFiles)
        }
        if (spec.includeDirs.isNotEmpty()) {
            val incFiles = spec.includeDirs.map { resolveProjectFile(project, it) }.toTypedArray()
            cls.methods.firstOrNull {
                it.name == "includeDirs" && it.parameterCount == 1 && it.parameterTypes[0].isArray
            }?.invoke(settings, incFiles)
        }
        if (spec.compilerOpts.isNotEmpty()) {
            cls.methods.firstOrNull {
                it.name == "compilerOpts" && it.parameterCount == 1 && List::class.java.isAssignableFrom(it.parameterTypes[0])
            }?.invoke(settings, spec.compilerOpts.toList())
        }
        if (spec.linkerOpts.isNotEmpty()) {
            cls.methods.firstOrNull {
                it.name == "linkerOpts" && it.parameterCount == 1 && List::class.java.isAssignableFrom(it.parameterTypes[0])
            }?.invoke(settings, spec.linkerOpts.toList())
        }
        if (spec.extraOpts.isNotEmpty()) {
            cls.methods.firstOrNull {
                it.name == "extraOpts" && it.parameterCount == 1 && List::class.java.isAssignableFrom(it.parameterTypes[0])
            }?.invoke(settings, spec.extraOpts.toList())
        }
    }

    private fun resolveProjectFile(project: Project, raw: Any): File {
        val str = normalizePathValue(project, raw)
        val f = File(str)
        return if (f.isAbsolute) f else project.file(str)
    }

    private fun invokeNoArg(target: Any?, methodName: String): Any? {
        if (target == null) return null
        return runCatching {
            target.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 0
            }?.invoke(target)
        }.getOrNull()
    }
}
