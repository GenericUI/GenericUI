package com.nexustools.genericui.plugin

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Configures Kotlin Multiplatform targets, the GenericUI hierarchical sourceSet tree, native/web
 * executable binaries, and platform Development/Production build target tasks (`windowsDevelopment`,
 * `windowsProduction`, `macDevelopment`, `macProduction`, `linuxDevelopmentGtk`, `linuxProductionGtk`,
 * `linuxDevelopmentQt`, `linuxProductionQt`, `swingDevelopment`, `swingProduction`,
 * `androidDevelopment`, `androidProduction`, `iosDevelopment`, `iosProduction`,
 * `webDevelopment`, `webProduction`) so projects using GenericUI can rely entirely on `genericui { ... }`.
 *
 * Uses Gradle's standard API interfaces (`NamedDomainObjectContainer`, `SourceDirectorySet`, `Action`)
 * and reflective invocation on the `"kotlin"` extension so it operates seamlessly across composite builds
 * (`includeBuild("lib/GenericUI")`) without classloader conflicts.
 */
object GenericUiMultiplatformConfigurator {

    const val DEFAULT_COROUTINES_VERSION: String = "1.10.1"
    const val EXTRA_DEPENDENCY_BUILD_TASKS: String = "com.nexustools.genericui.dependencyBuildTasks"
    const val EXTRA_GENERATED_KOTLIN_DIRS: String = "com.nexustools.genericui.generatedKotlinDirs"
    const val EXTRA_EXTERNAL_DEPENDENCIES: String = "com.nexustools.genericui.externalDependencies"

    /**
     * Ensures `org.jetbrains.kotlin.multiplatform` is applied when `configureMultiplatformTargets`
     * is enabled (unless the project explicitly applied the single-target `java` or `org.jetbrains.kotlin.jvm` plugin).
     */
    fun ensureKotlinMultiplatformApplied(project: Project, extension: GenericUiExtension) {
        if (!extension.configureMultiplatformTargets) return
        if (project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) return
        if (project.plugins.hasPlugin("org.jetbrains.kotlin.jvm") || project.plugins.hasPlugin("java")) return
        runCatching {
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        }
    }

    /**
     * Configures KMP targets, GenericUI sourceSets (`common`, `commonMain`, `desktopCommon`,
     * `nativeDesktopCommon`, `windowsMain`, `macMain`, `macosMain`, `linuxCommon`, `gtkMain`,
     * `qtMain`, `swingMain`, `mobileCommon`, `androidMain`, `iosMain`, `webMain`), executable
     * binaries, and Development/Production lifecycle build tasks.
     */
    fun configureMultiplatform(project: Project, extension: GenericUiExtension) {
        if (!extension.configureMultiplatformTargets) return
        ensureKotlinMultiplatformApplied(project, extension)

        val kmp = project.extensions.findByName("kotlin") ?: return
        val targets = invokeNoArg(kmp, "getTargets") as? NamedDomainObjectCollection<*> ?: return
        val ss = invokeNoArg(kmp, "getSourceSets") as? NamedDomainObjectContainer<*> ?: return

        val backends = extension.backends
        val app = extension.application

        // 1. Register enabled KMP targets
        val mingwTarget: Any? = if (backends.windowsEnabled) {
            targets.findByName("mingwX64") ?: invokeNoArg(kmp, "mingwX64")
        } else {
            targets.findByName("mingwX64")
        }

        val macArm64Target: Any? = if (backends.macosEnabled) {
            targets.findByName("macosArm64") ?: invokeNoArg(kmp, "macosArm64")
        } else {
            targets.findByName("macosArm64")
        }

        val macX64Target: Any? = if (backends.macosEnabled) {
            targets.findByName("macosX64") ?: invokeNoArg(kmp, "macosX64")
        } else {
            targets.findByName("macosX64")
        }

        val linuxX64Target: Any? = if (backends.gtkEnabled || backends.qtEnabled) {
            targets.findByName("linuxX64") ?: invokeNoArg(kmp, "linuxX64")
        } else {
            targets.findByName("linuxX64")
        }

        val linuxArm64Target: Any? = if (backends.gtkEnabled || backends.qtEnabled) {
            targets.findByName("linuxArm64") ?: invokeNoArg(kmp, "linuxArm64")
        } else {
            targets.findByName("linuxArm64")
        }

        if (backends.swingConfig.enabled) {
            if (targets.findByName("jvm") == null) {
                invokeNoArg(kmp, "jvm")
            }
        }

        if (backends.iosEnabled) {
            if (targets.findByName("iosArm64") == null) {
                invokeNoArg(kmp, "iosArm64")
            }
            if (targets.findByName("iosSimulatorArm64") == null) {
                invokeNoArg(kmp, "iosSimulatorArm64")
            }
        }

        if (backends.webEnabled) {
            val existingJs = targets.findByName("js")
            if (existingJs == null) {
                val jsTarget = invokeNoArg(kmp, "js")
                if (jsTarget != null) {
                    configureJsTarget(jsTarget, app)
                }
            }
        }

        if (backends.androidEnabled) {
            configureAndroidTargetIfPresent(project, kmp, targets, app)
        }

        // 2. Create and connect GenericUI SourceSets hierarchy
        val commonMain = ss.maybeCreate("commonMain").also {
            addKotlinSrcDir(it, "src/common/kotlin")
            addResourcesSrcDir(it, "src/common/resources")
        }
        val commonTest = ss.maybeCreate("commonTest").also {
            addKotlinSrcDir(it, "src/commonTest/kotlin")
        }
        val common = ss.maybeCreate("common").also {
            dependsOnIfNotConnected(it, commonMain)
            addKotlinSrcDir(it, "src/common/kotlin")
            addResourcesSrcDir(it, "src/common/resources")
        }

        val anyDesktop = backends.windowsEnabled ||
            backends.macosEnabled ||
            backends.gtkEnabled ||
            backends.qtEnabled ||
            backends.swingConfig.enabled

        val anyNativeDesktop = backends.windowsEnabled ||
            backends.macosEnabled ||
            backends.gtkEnabled ||
            backends.qtEnabled

        var desktopCommon: Any? = null
        var desktopMain: Any? = null
        var desktopTest: Any? = null
        if (anyDesktop) {
            desktopCommon = ss.maybeCreate("desktopCommon").also {
                dependsOnIfNotConnected(it, common)
                addKotlinSrcDir(it, "src/desktopCommon/kotlin")
                addResourcesSrcDir(it, "src/desktopCommon/resources")
            }
            desktopMain = ss.maybeCreate("desktopMain").also {
                dependsOnIfNotConnected(it, desktopCommon)
                addKotlinSrcDir(it, "src/desktopMain/kotlin")
                addResourcesSrcDir(it, "src/desktopMain/resources")
            }
            desktopTest = ss.maybeCreate("desktopTest").also {
                dependsOnIfNotConnected(it, commonTest)
                addKotlinSrcDir(it, "src/desktopTest/kotlin")
            }
        }

        if (backends.swingConfig.enabled && desktopMain != null && desktopTest != null) {
            val swingMain = ss.maybeCreate("swingMain").also {
                dependsOnIfNotConnected(it, desktopMain)
                addKotlinSrcDir(it, "src/swingMain/kotlin")
                addResourcesSrcDir(it, "src/swingMain/resources")
            }
            val swingTest = ss.maybeCreate("swingTest").also {
                dependsOnIfNotConnected(it, desktopTest)
                addKotlinSrcDir(it, "src/swingTest/kotlin")
            }
            ss.findByName("jvmMain")?.let { dependsOnIfNotConnected(it, swingMain) }
            ss.findByName("jvmTest")?.let { dependsOnIfNotConnected(it, swingTest) }
        }

        var nativeDesktopCommon: Any? = null
        var nativeDesktopMain: Any? = null
        var nativeDesktopTest: Any? = null
        if (anyNativeDesktop && desktopMain != null && desktopTest != null) {
            nativeDesktopCommon = ss.maybeCreate("nativeDesktopCommon").also {
                dependsOnIfNotConnected(it, desktopMain)
                addKotlinSrcDir(it, "src/nativeDesktopCommon/kotlin")
                addResourcesSrcDir(it, "src/nativeDesktopCommon/resources")
            }
            nativeDesktopMain = ss.maybeCreate("nativeDesktopMain").also {
                dependsOnIfNotConnected(it, nativeDesktopCommon)
                addKotlinSrcDir(it, "src/nativeDesktopMain/kotlin")
                addResourcesSrcDir(it, "src/nativeDesktopMain/resources")
            }
            nativeDesktopTest = ss.maybeCreate("nativeDesktopTest").also {
                dependsOnIfNotConnected(it, desktopTest)
                addKotlinSrcDir(it, "src/nativeDesktopTest/kotlin")
            }
        }

        if (backends.windowsEnabled && nativeDesktopMain != null && nativeDesktopTest != null) {
            val windowsMain = ss.maybeCreate("windowsMain").also {
                dependsOnIfNotConnected(it, nativeDesktopMain)
                addKotlinSrcDir(it, "src/windowsMain/kotlin")
                addResourcesSrcDir(it, "src/windowsMain/resources")
            }
            val windowsTest = ss.maybeCreate("windowsTest").also {
                dependsOnIfNotConnected(it, nativeDesktopTest)
                addKotlinSrcDir(it, "src/windowsTest/kotlin")
            }
            ss.findByName("mingwMain")?.let { dependsOnIfNotConnected(it, windowsMain) }
            ss.findByName("mingwX64Main")?.let { dependsOnIfNotConnected(it, windowsMain) }
            ss.findByName("mingwX64Test")?.let { dependsOnIfNotConnected(it, windowsTest) }
        }

        if (backends.macosEnabled && nativeDesktopMain != null && nativeDesktopTest != null) {
            val macMain = ss.maybeCreate("macMain").also {
                dependsOnIfNotConnected(it, nativeDesktopMain)
                addKotlinSrcDir(it, "src/macMain/kotlin")
                addResourcesSrcDir(it, "src/macMain/resources")
            }
            val macosMain = ss.maybeCreate("macosMain").also {
                dependsOnIfNotConnected(it, macMain)
                addKotlinSrcDir(it, "src/macosMain/kotlin")
                addResourcesSrcDir(it, "src/macosMain/resources")
            }
            val macTest = ss.maybeCreate("macTest").also {
                dependsOnIfNotConnected(it, nativeDesktopTest)
                addKotlinSrcDir(it, "src/macTest/kotlin")
            }
            val macosTest = ss.maybeCreate("macosTest").also {
                dependsOnIfNotConnected(it, macTest)
                addKotlinSrcDir(it, "src/macosTest/kotlin")
            }
            ss.findByName("macosArm64Main")?.let { dependsOnIfNotConnected(it, macosMain) }
            ss.findByName("macosX64Main")?.let { dependsOnIfNotConnected(it, macosMain) }
            ss.findByName("macosArm64Test")?.let { dependsOnIfNotConnected(it, macosTest) }
            ss.findByName("macosX64Test")?.let { dependsOnIfNotConnected(it, macosTest) }
        }

        if ((backends.gtkEnabled || backends.qtEnabled) && nativeDesktopMain != null && nativeDesktopTest != null) {
            val linuxCommon = ss.maybeCreate("linuxCommon").also {
                dependsOnIfNotConnected(it, nativeDesktopMain)
                addKotlinSrcDir(it, "src/linuxCommon/kotlin")
                addResourcesSrcDir(it, "src/linuxCommon/resources")
            }
            val linuxMain = ss.maybeCreate("linuxMain").also {
                dependsOnIfNotConnected(it, linuxCommon)
                addKotlinSrcDir(it, "src/linuxMain/kotlin")
                addResourcesSrcDir(it, "src/linuxMain/resources")
            }
            val linuxTest = ss.maybeCreate("linuxTest").also {
                dependsOnIfNotConnected(it, nativeDesktopTest)
                addKotlinSrcDir(it, "src/linuxTest/kotlin")
            }
            ss.findByName("linuxX64Main")?.let { dependsOnIfNotConnected(it, linuxMain) }
            ss.findByName("linuxArm64Main")?.let { dependsOnIfNotConnected(it, linuxMain) }
            ss.findByName("linuxX64Test")?.let { dependsOnIfNotConnected(it, linuxTest) }
            ss.findByName("linuxArm64Test")?.let { dependsOnIfNotConnected(it, linuxTest) }

            val linuxTargets = listOfNotNull(linuxX64Target, linuxArm64Target)
            val bothGtkAndQtDirsExist = File(project.projectDir, "src/gtkMain").exists() &&
                File(project.projectDir, "src/qtMain").exists()

            if (backends.gtkEnabled) {
                val gtkMain = ss.maybeCreate("gtkMain").also {
                    dependsOnIfNotConnected(it, linuxMain)
                    addKotlinSrcDir(it, "src/gtkMain/kotlin")
                    addResourcesSrcDir(it, "src/gtkMain/resources")
                }
                val gtkTest = ss.maybeCreate("gtkTest").also {
                    dependsOnIfNotConnected(it, linuxTest)
                    addKotlinSrcDir(it, "src/gtkTest/kotlin")
                }
                for (lt in linuxTargets) {
                    val comps = invokeNoArg(lt, "getCompilations") as? NamedDomainObjectContainer<*>
                    val gtkComp = comps?.maybeCreate("gtk")
                    val defaultSs = invokeNoArg(gtkComp, "getDefaultSourceSet")
                    if (defaultSs != null) {
                        dependsOnIfNotConnected(defaultSs, gtkMain)
                    }
                }
                if (!bothGtkAndQtDirsExist) {
                    ss.findByName("linuxX64Main")?.let { dependsOnIfNotConnected(it, gtkMain) }
                    ss.findByName("linuxArm64Main")?.let { dependsOnIfNotConnected(it, gtkMain) }
                }
                ss.findByName("linuxX64Test")?.let { dependsOnIfNotConnected(it, gtkTest) }
                ss.findByName("linuxArm64Test")?.let { dependsOnIfNotConnected(it, gtkTest) }
            }

            if (backends.qtEnabled) {
                val qtMain = ss.maybeCreate("qtMain").also {
                    dependsOnIfNotConnected(it, linuxMain)
                    addKotlinSrcDir(it, "src/qtMain/kotlin")
                    addResourcesSrcDir(it, "src/qtMain/resources")
                }
                val qtTest = ss.maybeCreate("qtTest").also {
                    dependsOnIfNotConnected(it, linuxTest)
                    addKotlinSrcDir(it, "src/qtTest/kotlin")
                }
                for (lt in linuxTargets) {
                    val comps = invokeNoArg(lt, "getCompilations") as? NamedDomainObjectContainer<*>
                    val qtComp = comps?.maybeCreate("qt")
                    val defaultSs = invokeNoArg(qtComp, "getDefaultSourceSet")
                    if (defaultSs != null) {
                        dependsOnIfNotConnected(defaultSs, qtMain)
                    }
                }
                if (!bothGtkAndQtDirsExist) {
                    ss.findByName("linuxX64Main")?.let { dependsOnIfNotConnected(it, qtMain) }
                    ss.findByName("linuxArm64Main")?.let { dependsOnIfNotConnected(it, qtMain) }
                }
                ss.findByName("linuxX64Test")?.let { dependsOnIfNotConnected(it, qtTest) }
                ss.findByName("linuxArm64Test")?.let { dependsOnIfNotConnected(it, qtTest) }
            }
        }

        val hasAndroidTarget = ss.findByName("androidMain") != null ||
            targets.names.any { it.contains("android", ignoreCase = true) }
        val anyMobileActive = (backends.androidEnabled && hasAndroidTarget) || backends.iosEnabled

        if (anyMobileActive) {
            val mobileCommon = ss.maybeCreate("mobileCommon").also {
                dependsOnIfNotConnected(it, common)
                addKotlinSrcDir(it, "src/mobileCommon/kotlin")
                addResourcesSrcDir(it, "src/mobileCommon/resources")
            }
            val mobileMain = ss.maybeCreate("mobileMain").also {
                dependsOnIfNotConnected(it, mobileCommon)
                addKotlinSrcDir(it, "src/mobileMain/kotlin")
                addResourcesSrcDir(it, "src/mobileMain/resources")
            }
            val mobileTest = ss.maybeCreate("mobileTest").also {
                dependsOnIfNotConnected(it, commonTest)
                addKotlinSrcDir(it, "src/mobileTest/kotlin")
            }

            if (backends.androidEnabled && hasAndroidTarget) {
                ss.findByName("androidMain")?.also {
                    dependsOnIfNotConnected(it, mobileMain)
                    addKotlinSrcDir(it, "src/androidMain/kotlin")
                    addResourcesSrcDir(it, "src/androidMain/resources")
                }
            }

            if (backends.iosEnabled) {
                val iosMain = ss.maybeCreate("iosMain").also {
                    dependsOnIfNotConnected(it, mobileMain)
                    addKotlinSrcDir(it, "src/iosMain/kotlin")
                    addResourcesSrcDir(it, "src/iosMain/resources")
                }
                val iosTest = ss.maybeCreate("iosTest").also {
                    dependsOnIfNotConnected(it, mobileTest)
                    addKotlinSrcDir(it, "src/iosTest/kotlin")
                }
                ss.findByName("iosArm64Main")?.let { dependsOnIfNotConnected(it, iosMain) }
                ss.findByName("iosSimulatorArm64Main")?.let { dependsOnIfNotConnected(it, iosMain) }
                ss.findByName("iosArm64Test")?.let { dependsOnIfNotConnected(it, iosTest) }
                ss.findByName("iosSimulatorArm64Test")?.let { dependsOnIfNotConnected(it, iosTest) }
            }
        }

        if (backends.webEnabled) {
            val webMain = ss.maybeCreate("webMain").also {
                dependsOnIfNotConnected(it, common)
                addKotlinSrcDir(it, "src/webMain/kotlin")
                addResourcesSrcDir(it, "src/webMain/resources")
            }
            val webTest = ss.maybeCreate("webTest").also {
                dependsOnIfNotConnected(it, commonTest)
                addKotlinSrcDir(it, "src/webTest/kotlin")
            }
            ss.findByName("jsMain")?.let { dependsOnIfNotConnected(it, webMain) }
            ss.findByName("jsTest")?.let { dependsOnIfNotConnected(it, webTest) }
            ss.findByName("wasmJsMain")?.let { dependsOnIfNotConnected(it, webMain) }
            ss.findByName("wasmJsTest")?.let { dependsOnIfNotConnected(it, webTest) }
        }

        // 3. Configure native desktop executable binaries (mingwX64, macosArm64, macosX64, linuxX64, linuxArm64)
        configureNativeDesktopBinaries(
            project = project,
            extension = extension,
            mingwTarget = mingwTarget,
            macTargets = listOfNotNull(macArm64Target, macX64Target),
            linuxTargets = listOfNotNull(linuxX64Target, linuxArm64Target)
        )

        // 4. Register Development/Production build target tasks
        registerBuildTargetTasks(project, extension)

        for (cb in extension.externalTargetsConfiguredCallbacks.toList()) {
            runCatching { cb.run() }
        }
    }

    /**
     * Applies user-configured `genericui { sourceSets { ... } }` dependencies and custom source dirs,
     * companion plugin extraProperties (`com.nexustools.genericui.*`), and default `kotlinx-coroutines-core`
     * and `kotlin-test` dependencies when enabled.
     */
    @Suppress("UNCHECKED_CAST")
    fun applySourceSetDependencies(project: Project, extension: GenericUiExtension) {
        val kmp = project.extensions.findByName("kotlin")
        val ssContainer = invokeNoArg(kmp, "getSourceSets") as? NamedDomainObjectContainer<*>

        val extraProps = project.extensions.extraProperties
        if (extraProps.has(EXTRA_GENERATED_KOTLIN_DIRS)) {
            val extraDirs = extraProps.get(EXTRA_GENERATED_KOTLIN_DIRS) as? Collection<*>
            extraDirs?.forEach { entry ->
                if (entry is Pair<*, *>) {
                    val ssName = entry.first?.toString() ?: "commonMain"
                    val dirObj = entry.second
                    if (dirObj != null) {
                        extension.registerGeneratedKotlinSrcDir(ssName, dirObj)
                    }
                }
            }
        }
        if (extraProps.has(EXTRA_EXTERNAL_DEPENDENCIES)) {
            val extraDeps = extraProps.get(EXTRA_EXTERNAL_DEPENDENCIES) as? Collection<*>
            extraDeps?.forEach { entry ->
                if (entry is Pair<*, *>) {
                    val ssName = entry.first?.toString() ?: "commonMain"
                    val depObj = entry.second
                    if (depObj != null) {
                        extension.registerExternalDependency(ssName, depObj)
                    }
                }
            }
        }
        if (extraProps.has(EXTRA_DEPENDENCY_BUILD_TASKS)) {
            val extraTasks = extraProps.get(EXTRA_DEPENDENCY_BUILD_TASKS) as? Collection<*>
            extraTasks?.forEach { taskObj ->
                if (taskObj != null) {
                    extension.registerDependencyBuildTask(taskObj)
                }
            }
        }

        if (extension.includeCoroutines) {
            val commonImpl = if (project.configurations.findByName("commonMainImplementation") != null) {
                "commonMainImplementation"
            } else {
                "implementation"
            }
            if (project.configurations.findByName(commonImpl) != null) {
                project.dependencies.add(
                    commonImpl,
                    "org.jetbrains.kotlinx:kotlinx-coroutines-core:${extension.coroutinesVersion}"
                )
            }
            if (project.configurations.findByName("commonTestImplementation") != null) {
                project.dependencies.add(
                    "commonTestImplementation",
                    "org.jetbrains.kotlin:kotlin-test"
                )
            }
        }

        for (spec in extension.sourceSets.allSpecs()) {
            val targetSs = ssContainer?.findByName(spec.name)
                ?: when (spec.name) {
                    "common" -> ssContainer?.findByName("commonMain")
                    "macMain" -> ssContainer?.findByName("macosMain")
                    "nativeDesktopCommon" -> ssContainer?.findByName("nativeDesktopMain") ?: ssContainer?.findByName("mingwX64Main")
                    "desktopCommon" -> ssContainer?.findByName("desktopMain") ?: ssContainer?.findByName("commonMain")
                    else -> null
                }

            if (targetSs != null) {
                for (srcPath in spec.kotlinSrcDirs) {
                    addKotlinSrcDir(targetSs, srcPath)
                }
                for (resPath in spec.resourcesSrcDirs) {
                    addResourcesSrcDir(targetSs, resPath)
                }
            }

            val configPrefix = if (targetSs != null) {
                spec.name
            } else {
                "commonMain"
            }
            addSpecDepsToConfig(project, "${configPrefix}Implementation", spec.implementationDependencies)
            addSpecDepsToConfig(project, "${configPrefix}Api", spec.apiDependencies)
            addSpecDepsToConfig(project, "${configPrefix}CompileOnly", spec.compileOnlyDependencies)
            addSpecDepsToConfig(project, "${configPrefix}RuntimeOnly", spec.runtimeOnlyDependencies)
        }
    }

    private fun addSpecDepsToConfig(project: Project, configName: String, deps: List<Any>) {
        if (deps.isEmpty()) return
        val cfg = project.configurations.findByName(configName)
            ?: when {
                configName.startsWith("nativeDesktop") ->
                    project.configurations.findByName("nativeDesktopMainImplementation")
                        ?: project.configurations.findByName("mingwX64MainImplementation")
                        ?: project.configurations.findByName("commonMainImplementation")
                configName.startsWith("desktop") ->
                    project.configurations.findByName("desktopMainImplementation")
                        ?: project.configurations.findByName("commonMainImplementation")
                else ->
                    project.configurations.findByName("commonMainImplementation")
                        ?: project.configurations.findByName("implementation")
            }
            ?: return
        for (dep in deps) {
            project.dependencies.add(cfg.name, dep)
        }
    }

    private fun configureJsTarget(jsTarget: Any, app: GenericUiApplicationConfig) {
        runCatching {
            invokeNoArg(jsTarget, "browser")
            invokeNoArg(jsTarget, "nodejs")
            if (app.createExecutableBinaries) {
                val binaries = invokeNoArg(jsTarget, "getBinaries")
                invokeNoArg(binaries, "executable")
            }
        }
    }

    private fun configureNativeDesktopBinaries(
        project: Project,
        extension: GenericUiExtension,
        mingwTarget: Any?,
        macTargets: List<Any>,
        linuxTargets: List<Any>
    ) {
        val app = extension.application
        val entryPointStr = app.entryPoint.trim()
        if (!app.createExecutableBinaries || entryPointStr.isEmpty()) {
            return
        }
        val resolvedBaseName = app.baseName.trim().ifEmpty { project.name }

        if (mingwTarget != null) {
            val winLinkerOpts = mutableListOf(
                "-lgdi32",
                "-luser32",
                "-lcomdlg32",
                "-lshell32",
                "-lole32",
                "-ldwmapi",
                "-luxtheme"
            )
            if (!app.consoleApplication) {
                winLinkerOpts.add("-Wl,--subsystem,windows")
            }
            if (app.desktop.embedWindowsResources && GenericUiDesktopResourceTask.findWindresExecutable() != null) {
                val resFile = File(
                    project.layout.buildDirectory.asFile.get(),
                    "generated/genericui/desktop-resources/windows/app_resources.res"
                )
                winLinkerOpts.add(resFile.absolutePath)
            }
            winLinkerOpts.addAll(app.extraWindowsLinkerOpts)
            configureTargetExecutable(
                nativeTarget = mingwTarget,
                namePrefix = null,
                compilationObj = null,
                entryPointStr = entryPointStr,
                baseNameStr = resolvedBaseName,
                linkerOptsList = winLinkerOpts
            )
        }

        for (macTarget in macTargets) {
            configureTargetExecutable(
                nativeTarget = macTarget,
                namePrefix = null,
                compilationObj = null,
                entryPointStr = entryPointStr,
                baseNameStr = resolvedBaseName,
                linkerOptsList = app.extraPosixLinkerOpts
            )
        }

        for (linuxTarget in linuxTargets) {
            configureTargetExecutable(
                nativeTarget = linuxTarget,
                namePrefix = null,
                compilationObj = null,
                entryPointStr = entryPointStr,
                baseNameStr = resolvedBaseName,
                linkerOptsList = app.extraPosixLinkerOpts
            )
            val comps = invokeNoArg(linuxTarget, "getCompilations") as? NamedDomainObjectContainer<*>
            if (extension.backends.gtkEnabled) {
                val gtkComp = comps?.findByName("gtk")
                if (gtkComp != null) {
                    configureTargetExecutable(
                        nativeTarget = linuxTarget,
                        namePrefix = "gtk",
                        compilationObj = gtkComp,
                        entryPointStr = entryPointStr,
                        baseNameStr = "$resolvedBaseName-gtk",
                        linkerOptsList = app.extraPosixLinkerOpts
                    )
                }
            }
            if (extension.backends.qtEnabled) {
                val qtComp = comps?.findByName("qt")
                if (qtComp != null) {
                    configureTargetExecutable(
                        nativeTarget = linuxTarget,
                        namePrefix = "qt",
                        compilationObj = qtComp,
                        entryPointStr = entryPointStr,
                        baseNameStr = "$resolvedBaseName-qt",
                        linkerOptsList = app.extraPosixLinkerOpts
                    )
                }
            }
        }
    }

    private fun configureTargetExecutable(
        nativeTarget: Any,
        namePrefix: String?,
        compilationObj: Any?,
        entryPointStr: String,
        baseNameStr: String,
        linkerOptsList: List<String>
    ) {
        val binaries = invokeNoArg(nativeTarget, "getBinaries") ?: return
        val configureAction = Action<Any> {
            val exe = this
            if (compilationObj != null) {
                exe.javaClass.methods.firstOrNull {
                    it.name == "setCompilation" && it.parameterCount == 1
                }?.invoke(exe, compilationObj)
            }
            exe.javaClass.methods.firstOrNull {
                it.name == "setEntryPoint" && it.parameterCount == 1
            }?.invoke(exe, entryPointStr)
            exe.javaClass.methods.firstOrNull {
                it.name == "setBaseName" && it.parameterCount == 1
            }?.invoke(exe, baseNameStr)
            if (linkerOptsList.isNotEmpty()) {
                val existingOpts = invokeNoArg(exe, "getLinkerOpts") as? MutableCollection<*>
                val missingOpts = if (existingOpts != null) {
                    linkerOptsList.filter { it !in existingOpts }
                } else {
                    linkerOptsList
                }
                if (missingOpts.isNotEmpty()) {
                    exe.javaClass.methods.firstOrNull {
                        it.name == "linkerOpts" && it.parameterCount == 1 &&
                            Iterable::class.java.isAssignableFrom(it.parameterTypes[0])
                    }?.invoke(exe, missingOpts)
                }
            }
        }

        runCatching {
            if (namePrefix == null) {
                val existingDebug = binaries.javaClass.methods.firstOrNull {
                    it.name == "findExecutable" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
                }?.invoke(binaries, "DEBUG")
                if (existingDebug == null) {
                    binaries.javaClass.methods.firstOrNull {
                        it.name == "executable" && it.parameterCount == 1 &&
                            Action::class.java.isAssignableFrom(it.parameterTypes[0])
                    }?.invoke(binaries, configureAction)
                } else {
                    configureAction.execute(existingDebug)
                }
            } else {
                val existingNamed = binaries.javaClass.methods.firstOrNull {
                    it.name == "findExecutable" && it.parameterCount == 2 &&
                        it.parameterTypes[0] == String::class.java &&
                        it.parameterTypes[1] == String::class.java
                }?.invoke(binaries, namePrefix, "DEBUG")
                if (existingNamed == null) {
                    binaries.javaClass.methods.firstOrNull {
                        it.name == "executable" && it.parameterCount == 2 &&
                            it.parameterTypes[0] == String::class.java &&
                            Action::class.java.isAssignableFrom(it.parameterTypes[1])
                    }?.invoke(binaries, namePrefix, configureAction)
                } else {
                    configureAction.execute(existingNamed)
                }
            }
        }
    }

    /**
     * Registers high-level GenericUI Development and Production build target tasks:
     * - `windowsDevelopment`, `windowsProduction`
     * - `macDevelopment`, `macProduction` (plus aliases `macosDevelopment`, `macosProduction`)
     * - `linuxDevelopmentGtk`, `linuxProductionGtk`
     * - `linuxDevelopmentQt`, `linuxProductionQt`
     * - `swingDevelopment`, `swingProduction`
     * - `androidDevelopment`, `androidProduction`
     * - `iosDevelopment`, `iosProduction`
     * - `webDevelopment`, `webProduction`
     */
    fun registerBuildTargetTasks(project: Project, extension: GenericUiExtension) {
        fun registerTargetTask(
            name: String,
            desc: String,
            candidateTaskNames: List<String>
        ): TaskProvider<*> {
            val existing = project.tasks.findByName(name)
            if (existing != null) {
                return project.tasks.named(name)
            }
            return project.tasks.register(name) {
                group = GenericUiGradlePlugin.TASK_GROUP
                description = desc
                dependsOn(project.provider {
                    val baseTasks = (candidateTaskNames + "compileGenericUiResources").distinct()
                        .mapNotNull { taskName -> project.tasks.findByName(taskName) }
                    val depBuildTasks = resolveCompanionDependencyBuildTasks(project, extension)
                    baseTasks + depBuildTasks
                })
            }
        }

        registerTargetTask(
            name = "windowsDevelopment",
            desc = "Builds the Windows (Win32 / Direct2D / ANGLE / Vulkan) Development target.",
            candidateTaskNames = listOf(
                "prepareGenericUiDesktopResources",
                "linkDebugExecutableMingwX64",
                "compileKotlinMingwX64",
                "bundleGenericUiAngle",
                "bundleGenericUiVulkan"
            )
        )
        registerTargetTask(
            name = "windowsProduction",
            desc = "Builds the Windows (Win32 / Direct2D / ANGLE / Vulkan) Production target.",
            candidateTaskNames = listOf(
                "prepareGenericUiDesktopResources",
                "linkReleaseExecutableMingwX64",
                "compileKotlinMingwX64",
                "bundleGenericUiAngle",
                "bundleGenericUiVulkan"
            )
        )

        val macDevCandidates = listOf(
            "prepareGenericUiDesktopResources",
            "linkDebugExecutableMacosArm64",
            "linkDebugExecutableMacosX64",
            "compileKotlinMacosArm64",
            "compileKotlinMacosX64",
            "bundleGenericUiVulkan"
        )
        val macProdCandidates = listOf(
            "prepareGenericUiDesktopResources",
            "linkReleaseExecutableMacosArm64",
            "linkReleaseExecutableMacosX64",
            "compileKotlinMacosArm64",
            "compileKotlinMacosX64",
            "bundleGenericUiVulkan"
        )
        registerTargetTask("macDevelopment", "Builds the macOS (AppKit / MoltenVK) Development target.", macDevCandidates)
        registerTargetTask("macosDevelopment", "Builds the macOS (AppKit / MoltenVK) Development target.", macDevCandidates)
        registerTargetTask("macProduction", "Builds the macOS (AppKit / MoltenVK) Production target.", macProdCandidates)
        registerTargetTask("macosProduction", "Builds the macOS (AppKit / MoltenVK) Production target.", macProdCandidates)

        registerTargetTask(
            name = "linuxDevelopmentGtk",
            desc = "Builds the Linux GTK (GTK4 / Cairo / EGL / Vulkan) Development target.",
            candidateTaskNames = listOf(
                "linkGtkDebugExecutableLinuxX64",
                "linkGtkDebugExecutableLinuxArm64",
                "compileGtkKotlinLinuxX64",
                "compileGtkKotlinLinuxArm64",
                "linkDebugExecutableLinuxX64",
                "compileKotlinLinuxX64"
            )
        )
        registerTargetTask(
            name = "linuxProductionGtk",
            desc = "Builds the Linux GTK (GTK4 / Cairo / EGL / Vulkan) Production target.",
            candidateTaskNames = listOf(
                "linkGtkReleaseExecutableLinuxX64",
                "linkGtkReleaseExecutableLinuxArm64",
                "compileGtkKotlinLinuxX64",
                "compileGtkKotlinLinuxArm64",
                "linkReleaseExecutableLinuxX64",
                "compileKotlinLinuxX64"
            )
        )

        registerTargetTask(
            name = "linuxDevelopmentQt",
            desc = "Builds the Linux Qt (Qt6 / QPainter / QVulkanWindow) Development target.",
            candidateTaskNames = listOf(
                "linkQtDebugExecutableLinuxX64",
                "linkQtDebugExecutableLinuxArm64",
                "compileQtKotlinLinuxX64",
                "compileQtKotlinLinuxArm64",
                "linkDebugExecutableLinuxX64",
                "compileKotlinLinuxX64"
            )
        )
        registerTargetTask(
            name = "linuxProductionQt",
            desc = "Builds the Linux Qt (Qt6 / QPainter / QVulkanWindow) Production target.",
            candidateTaskNames = listOf(
                "linkQtReleaseExecutableLinuxX64",
                "linkQtReleaseExecutableLinuxArm64",
                "compileQtKotlinLinuxX64",
                "compileQtKotlinLinuxArm64",
                "linkReleaseExecutableLinuxX64",
                "compileKotlinLinuxX64"
            )
        )

        registerTargetTask(
            name = "swingDevelopment",
            desc = "Builds the Java Swing (JVM / AWT / LWJGL) Development target.",
            candidateTaskNames = listOf("compileKotlinJvm", "jvmProcessResources")
        )
        registerTargetTask(
            name = "swingProduction",
            desc = "Builds the Java Swing (JVM / AWT / LWJGL) Production JAR target.",
            candidateTaskNames = listOf("jvmJar", "compileKotlinJvm")
        )

        registerTargetTask(
            name = "androidDevelopment",
            desc = "Builds the Android Development target.",
            candidateTaskNames = listOf("compileAndroidMain", "assembleDebug", "compileDebugKotlinAndroid")
        )
        registerTargetTask(
            name = "androidProduction",
            desc = "Builds the Android Production target.",
            candidateTaskNames = listOf("compileAndroidMain", "assembleRelease", "compileReleaseKotlinAndroid")
        )

        registerTargetTask(
            name = "iosDevelopment",
            desc = "Builds the iOS (UIKit / MoltenVK) Development target.",
            candidateTaskNames = listOf("compileKotlinIosSimulatorArm64", "compileKotlinIosArm64")
        )
        registerTargetTask(
            name = "iosProduction",
            desc = "Builds the iOS (UIKit / MoltenVK) Production target.",
            candidateTaskNames = listOf("compileKotlinIosArm64")
        )

        registerTargetTask(
            name = "webDevelopment",
            desc = "Builds the Web (HTML5 DOM / Canvas2D / WebGL2 / WebGPU + compiled SASS CSS) Development target.",
            candidateTaskNames = listOf(
                "compileGenericUiSass",
                "jsProcessResources",
                "compileDevelopmentExecutableKotlinJs",
                "jsDevelopmentExecutableCompileSync",
                "compileKotlinJs"
            )
        )
        registerTargetTask(
            name = "webProduction",
            desc = "Builds the Web (HTML5 DOM / Canvas2D / WebGL2 / WebGPU + compiled SASS CSS) Production distribution target.",
            candidateTaskNames = listOf(
                "compileGenericUiSass",
                "jsProcessResources",
                "compileProductionExecutableKotlinJs",
                "jsProductionExecutableCompileSync",
                "jsBrowserDistribution",
                "compileKotlinJs"
            )
        )
    }

    private fun configureAndroidTargetIfPresent(
        project: Project,
        kmp: Any,
        targets: NamedDomainObjectCollection<*>,
        app: GenericUiApplicationConfig
    ) {
        val hasKmpAndroidLibraryPlugin = project.plugins.hasPlugin("com.android.kotlin.multiplatform.library")
        if (!hasKmpAndroidLibraryPlugin) return

        runCatching {
            val androidExt = (kmp as? ExtensionAware)?.extensions?.findByName("android")
                ?: targets.findByName("android")
                ?: return
            val ns = app.androidNamespace.trim().ifEmpty {
                val grp = project.group.toString().trim()
                if (grp.isNotEmpty()) grp else "com.nexustools.genericui.app"
            }
            androidExt.javaClass.methods.firstOrNull {
                it.name == "setNamespace" && it.parameterCount == 1
            }?.invoke(androidExt, ns)

            androidExt.javaClass.methods.firstOrNull {
                it.name == "setCompileSdk" && it.parameterCount == 1 && it.parameterTypes[0] == Int::class.javaObjectType
            }?.invoke(androidExt, app.androidCompileSdk)

            androidExt.javaClass.methods.firstOrNull {
                it.name == "setMinSdk" && it.parameterCount == 1 && it.parameterTypes[0] == Int::class.javaObjectType
            }?.invoke(androidExt, app.androidMinSdk)
        }
    }

    private fun invokeNoArg(target: Any?, methodName: String): Any? {
        if (target == null) return null
        return runCatching {
            target.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 0
            }?.invoke(target)
        }.getOrNull()
    }

    fun resolveCompanionDependencyBuildTasks(project: Project, extension: GenericUiExtension): List<Any> {
        val result = mutableListOf<Any>()
        for (item in extension.dependencyBuildTasks) {
            if (item is String) {
                project.tasks.findByName(item)?.let { result.add(it) }
            } else {
                result.add(item)
            }
        }
        val extraProps = project.extensions.extraProperties
        if (extraProps.has(EXTRA_DEPENDENCY_BUILD_TASKS)) {
            val extraTasks = extraProps.get(EXTRA_DEPENDENCY_BUILD_TASKS) as? Collection<*>
            extraTasks?.forEach { item ->
                if (item is String) {
                    project.tasks.findByName(item)?.let { result.add(it) }
                } else if (item != null) {
                    result.add(item)
                }
            }
        }
        return result
    }

    private fun addKotlinSrcDir(sourceSetObj: Any?, srcDir: Any) {
        val dirSet = invokeNoArg(sourceSetObj, "getKotlin") as? SourceDirectorySet ?: return
        dirSet.srcDir(srcDir)
    }

    private fun addResourcesSrcDir(sourceSetObj: Any?, resDir: Any) {
        val dirSet = invokeNoArg(sourceSetObj, "getResources") as? SourceDirectorySet ?: return
        dirSet.srcDir(resDir)
    }

    private fun dependsOnIfNotConnected(childSs: Any?, parentSs: Any?) {
        if (childSs == null || parentSs == null || childSs === parentSs) return
        runCatching {
            val existing = invokeNoArg(childSs, "getDependsOn") as? Set<*>
            if (existing != null && parentSs in existing) return
            childSs.javaClass.methods.firstOrNull {
                it.name == "dependsOn" && it.parameterCount == 1
            }?.invoke(childSs, parentSs)
        }
    }
}
