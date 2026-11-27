package com.nexustools.genericui.plugin

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Configures Kotlin Multiplatform targets, the GenericUI hierarchical sourceSet tree, native/web
 * executable binaries, and platform Development/Production build target tasks (`developmentWindows`,
 * `productionWindows`, `developmentMac`, `productionMac`, `developmentLinuxGtk`, `productionLinuxGtk`,
 * `developmentLinuxKde`, `productionLinuxKde`, `developmentSwing`, `productionSwing`,
 * `developmentAndroid`, `productionAndroid`, `developmentIos`, `productionIos`,
 * `developmentWeb`, `productionWeb`, and their CPU architecture-specific variants) so projects
 * using GenericUI can rely entirely on `genericui { ... }`.
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

    val EXCLUDED_STATIC_CORE_LIBRARY_PREFIXES: List<String> = listOf(
        "libpng",
        "libjpeg",
        "libturbojpeg",
        "zlib",
        "libz.",
        "libz-",
        "libwebp",
        "libgcc_s",
        "libstdc++",
        "libwinpthread"
    )

    fun isExcludedStaticCoreLibrary(file: File): Boolean {
        val lowerName = file.name.lowercase()
        return EXCLUDED_STATIC_CORE_LIBRARY_PREFIXES.any { prefix -> lowerName.startsWith(prefix) }
    }

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
     * `kdeMain`, `swingMain`, `mobileCommon`, `androidMain`, `iosMain`, `webMain`), executable
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

        val discoveredFragments = GenericUiFragmentScanner.scanProject(project.projectDir)
        val hasMainAppFragment = discoveredFragments.any { it.isMainFragment }
        val generatedLauncherPackage = if (hasMainAppFragment) {
            GenericUiGenerateLaunchersTask.resolveGeneratedLauncherPackage(
                extension = extension,
                projectGroup = project.group.toString(),
                discoveredFragments = discoveredFragments
            )
        } else {
            ""
        }
        if (hasMainAppFragment && app.mainClass.isBlank()) {
            app.mainClass = "$generatedLauncherPackage.GeneratedSwingMain"
        }

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

        val linuxX64Target: Any? = if (backends.gtkEnabled || backends.kdeEnabled) {
            targets.findByName("linuxX64") ?: invokeNoArg(kmp, "linuxX64")
        } else {
            targets.findByName("linuxX64")
        }

        val linuxArm64Target: Any? = if (backends.gtkEnabled || backends.kdeEnabled) {
            targets.findByName("linuxArm64") ?: invokeNoArg(kmp, "linuxArm64")
        } else {
            targets.findByName("linuxArm64")
        }

        if (backends.swingConfig.enabled) {
            if (targets.findByName("jvm") == null) {
                invokeNoArg(kmp, "jvm")
            }
        }

        val anyAppleMobile = backends.iosEnabled || backends.ipadosEnabled
        val iosArm64Target: Any? = if (anyAppleMobile) {
            targets.findByName("iosArm64") ?: invokeNoArg(kmp, "iosArm64")
        } else {
            targets.findByName("iosArm64")
        }
        val iosSimArm64Target: Any? = if (anyAppleMobile) {
            targets.findByName("iosSimulatorArm64") ?: invokeNoArg(kmp, "iosSimulatorArm64")
        } else {
            targets.findByName("iosSimulatorArm64")
        }

        if (backends.webEnabled) {
            val existingWasmJs = targets.findByName("wasmJs")
            val wasmJsTarget = existingWasmJs ?: invokeNoArg(kmp, "wasmJs")
            if (wasmJsTarget != null) {
                configureJsTarget(wasmJsTarget, app)
            }

            val existingJs = targets.findByName("js")
            val jsTarget = existingJs ?: invokeNoArg(kmp, "js")
            if (jsTarget != null) {
                configureJsTarget(jsTarget, app)
            }
        }

        if (backends.androidEnabled) {
            configureAndroidTargetIfPresent(project, kmp, targets, extension)
        }

        // 2. Create and connect GenericUI SourceSets hierarchy:
        // - common (commonMain / common / commonTest)
        //   - web (web / webMain / webTest)
        //   - systemCommon (systemCommon / systemMain / systemTest)
        //     - desktopCommon (desktopCommon / desktopMain / desktopTest)
        //       - swing (swing / swingMain / swingTest)
        //       - desktopNativeCommon (desktopNativeCommon / desktopNativeMain / desktopNativeTest)
        //         - kde, gtk, windows, mac
        //     - mobileCommon (mobileCommon / mobileMain / mobileTest)
        //       - android, ios
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

        if (backends.webEnabled) {
            val web = ss.maybeCreate("web").also {
                dependsOnIfNotConnected(it, common)
                addKotlinSrcDir(it, "src/web/kotlin")
                addResourcesSrcDir(it, "src/web/resources")
            }
            val webMain = ss.maybeCreate("webMain").also {
                dependsOnIfNotConnected(it, web)
                addKotlinSrcDir(it, "src/webMain/kotlin")
                addKotlinSrcDir(it, "src/web/kotlin")
                addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/web/kotlin"))
                addResourcesSrcDir(it, "src/webMain/resources")
                addResourcesSrcDir(it, "src/web/resources")
            }
            val webTest = ss.maybeCreate("webTest").also {
                dependsOnIfNotConnected(it, commonTest)
                addKotlinSrcDir(it, "src/webTest/kotlin")
                addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/webTest/kotlin"))
            }
            ss.findByName("jsMain")?.let { dependsOnIfNotConnected(it, webMain) }
            ss.findByName("jsTest")?.let { dependsOnIfNotConnected(it, webTest) }
            ss.findByName("wasmJsMain")?.let { dependsOnIfNotConnected(it, webMain) }
            ss.findByName("wasmJsTest")?.let { dependsOnIfNotConnected(it, webTest) }
        }

        val anyDesktop = backends.windowsEnabled ||
            backends.macosEnabled ||
            backends.gtkEnabled ||
            backends.kdeEnabled ||
            backends.swingConfig.enabled

        val anyNativeDesktop = backends.windowsEnabled ||
            backends.macosEnabled ||
            backends.gtkEnabled ||
            backends.kdeEnabled

        val anyMobile = backends.androidEnabled || backends.iosEnabled || backends.ipadosEnabled
        val anyApple = backends.macosEnabled || backends.iosEnabled || backends.ipadosEnabled
        val anyJvm = backends.swingConfig.enabled || backends.androidEnabled
        val anySystem = anyDesktop || anyMobile

        var systemCommon: Any? = null
        var systemMain: Any? = null
        var systemTest: Any? = null
        if (anySystem) {
            systemCommon = ss.maybeCreate("systemCommon").also {
                dependsOnIfNotConnected(it, common)
                addKotlinSrcDir(it, "src/systemCommon/kotlin")
                addResourcesSrcDir(it, "src/systemCommon/resources")
            }
            systemMain = ss.maybeCreate("systemMain").also {
                dependsOnIfNotConnected(it, systemCommon)
                addKotlinSrcDir(it, "src/systemMain/kotlin")
                addKotlinSrcDir(it, "src/systemCommon/kotlin")
                addResourcesSrcDir(it, "src/systemMain/resources")
                addResourcesSrcDir(it, "src/systemCommon/resources")
            }
            systemTest = ss.maybeCreate("systemTest").also {
                dependsOnIfNotConnected(it, commonTest)
                addKotlinSrcDir(it, "src/systemTest/kotlin")
                addKotlinSrcDir(it, "src/systemCommonTest/kotlin")
            }
        }

        var appleCommon: Any? = null
        var appleMain: Any? = null
        var appleTest: Any? = null
        if (anyApple && systemMain != null && systemTest != null) {
            appleCommon = ss.maybeCreate("appleCommon").also {
                dependsOnIfNotConnected(it, systemMain)
                addKotlinSrcDir(it, "src/appleCommon/kotlin")
                addResourcesSrcDir(it, "src/appleCommon/resources")
            }
            appleMain = ss.maybeCreate("appleMain").also {
                dependsOnIfNotConnected(it, appleCommon)
                addKotlinSrcDir(it, "src/appleMain/kotlin")
                addKotlinSrcDir(it, "src/appleCommon/kotlin")
                addResourcesSrcDir(it, "src/appleMain/resources")
                addResourcesSrcDir(it, "src/appleCommon/resources")
            }
            appleTest = ss.maybeCreate("appleTest").also {
                dependsOnIfNotConnected(it, systemTest)
                addKotlinSrcDir(it, "src/appleTest/kotlin")
                addKotlinSrcDir(it, "src/appleCommonTest/kotlin")
            }
        }

        var jvmCommon: Any? = null
        var jvmCommonTest: Any? = null
        if (anyJvm && systemMain != null && systemTest != null) {
            jvmCommon = ss.maybeCreate("jvmCommon").also {
                dependsOnIfNotConnected(it, systemMain)
                addKotlinSrcDir(it, "src/jvmCommon/kotlin")
                addResourcesSrcDir(it, "src/jvmCommon/resources")
            }
            jvmCommonTest = ss.maybeCreate("jvmCommonTest").also {
                dependsOnIfNotConnected(it, systemTest)
                addKotlinSrcDir(it, "src/jvmCommonTest/kotlin")
            }
        }

        var desktopCommon: Any? = null
        var desktopMain: Any? = null
        var desktopTest: Any? = null
        if (anyDesktop && systemMain != null && systemTest != null) {
            desktopCommon = ss.maybeCreate("desktopCommon").also {
                dependsOnIfNotConnected(it, systemMain)
                addKotlinSrcDir(it, "src/desktopCommon/kotlin")
                addResourcesSrcDir(it, "src/desktopCommon/resources")
            }
            desktopMain = ss.maybeCreate("desktopMain").also {
                dependsOnIfNotConnected(it, desktopCommon)
                addKotlinSrcDir(it, "src/desktopMain/kotlin")
                addKotlinSrcDir(it, "src/desktopCommon/kotlin")
                addResourcesSrcDir(it, "src/desktopMain/resources")
                addResourcesSrcDir(it, "src/desktopCommon/resources")
            }
            desktopTest = ss.maybeCreate("desktopTest").also {
                dependsOnIfNotConnected(it, systemTest)
                addKotlinSrcDir(it, "src/desktopTest/kotlin")
            }
        }

        if (backends.swingConfig.enabled && desktopMain != null && desktopTest != null) {
            val swing = ss.maybeCreate("swing").also {
                dependsOnIfNotConnected(it, desktopMain)
                if (jvmCommon != null) dependsOnIfNotConnected(it, jvmCommon)
                addKotlinSrcDir(it, "src/swing/kotlin")
                addResourcesSrcDir(it, "src/swing/resources")
            }
            val swingMain = ss.maybeCreate("swingMain").also {
                dependsOnIfNotConnected(it, swing)
                addKotlinSrcDir(it, "src/swingMain/kotlin")
                addKotlinSrcDir(it, "src/swing/kotlin")
                addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/swing/kotlin"))
                addResourcesSrcDir(it, "src/swingMain/resources")
                addResourcesSrcDir(it, "src/swing/resources")
            }
            val swingTest = ss.maybeCreate("swingTest").also {
                dependsOnIfNotConnected(it, desktopTest)
                if (jvmCommonTest != null) dependsOnIfNotConnected(it, jvmCommonTest)
                addKotlinSrcDir(it, "src/swingTest/kotlin")
                addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/swingTest/kotlin"))
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
                addKotlinSrcDir(it, "src/desktopNativeCommon/kotlin")
                addResourcesSrcDir(it, "src/desktopNativeCommon/resources")
            }
            nativeDesktopMain = ss.maybeCreate("nativeDesktopMain").also {
                dependsOnIfNotConnected(it, nativeDesktopCommon)
                addKotlinSrcDir(it, "src/nativeDesktopMain/kotlin")
                addKotlinSrcDir(it, "src/nativeDesktopCommon/kotlin")
                addKotlinSrcDir(it, "src/desktopNativeMain/kotlin")
                addKotlinSrcDir(it, "src/desktopNativeCommon/kotlin")
                addResourcesSrcDir(it, "src/nativeDesktopMain/resources")
                addResourcesSrcDir(it, "src/nativeDesktopCommon/resources")
                addResourcesSrcDir(it, "src/desktopNativeMain/resources")
                addResourcesSrcDir(it, "src/desktopNativeCommon/resources")
            }
            nativeDesktopTest = ss.maybeCreate("nativeDesktopTest").also {
                dependsOnIfNotConnected(it, desktopTest)
                addKotlinSrcDir(it, "src/nativeDesktopTest/kotlin")
                addKotlinSrcDir(it, "src/desktopNativeTest/kotlin")
            }
            ss.maybeCreate("desktopNativeCommon").also {
                dependsOnIfNotConnected(it, nativeDesktopCommon)
            }
            ss.maybeCreate("desktopNativeMain").also {
                dependsOnIfNotConnected(it, nativeDesktopMain)
            }
            ss.maybeCreate("desktopNativeTest").also {
                dependsOnIfNotConnected(it, nativeDesktopTest)
            }
        }

        if (backends.windowsEnabled && nativeDesktopMain != null && nativeDesktopTest != null) {
            val windows = ss.maybeCreate("windows").also {
                dependsOnIfNotConnected(it, nativeDesktopMain)
                addKotlinSrcDir(it, "src/windows/kotlin")
                addResourcesSrcDir(it, "src/windows/resources")
            }
            val windowsMain = ss.maybeCreate("windowsMain").also {
                dependsOnIfNotConnected(it, windows)
                addKotlinSrcDir(it, "src/windowsMain/kotlin")
                addKotlinSrcDir(it, "src/windows/kotlin")
                addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/windows/kotlin"))
                addResourcesSrcDir(it, "src/windowsMain/resources")
                addResourcesSrcDir(it, "src/windows/resources")
            }
            val windowsTest = ss.maybeCreate("windowsTest").also {
                dependsOnIfNotConnected(it, nativeDesktopTest)
                addKotlinSrcDir(it, "src/windowsTest/kotlin")
                addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/windowsTest/kotlin"))
            }
            ss.findByName("mingwMain")?.let { dependsOnIfNotConnected(it, windowsMain) }
            ss.findByName("mingwX64Main")?.let { dependsOnIfNotConnected(it, windowsMain) }
            ss.findByName("mingwX64Test")?.let { dependsOnIfNotConnected(it, windowsTest) }
        }

        if (backends.macosEnabled && nativeDesktopMain != null && nativeDesktopTest != null) {
            val mac = ss.maybeCreate("mac").also {
                dependsOnIfNotConnected(it, nativeDesktopMain)
                if (appleMain != null) dependsOnIfNotConnected(it, appleMain)
                addKotlinSrcDir(it, "src/mac/kotlin")
                addResourcesSrcDir(it, "src/mac/resources")
            }
            val macMain = ss.maybeCreate("macMain").also {
                dependsOnIfNotConnected(it, mac)
                addKotlinSrcDir(it, "src/macMain/kotlin")
                addKotlinSrcDir(it, "src/mac/kotlin")
                addResourcesSrcDir(it, "src/macMain/resources")
                addResourcesSrcDir(it, "src/mac/resources")
            }
            val macosMain = ss.maybeCreate("macosMain").also {
                dependsOnIfNotConnected(it, macMain)
                addKotlinSrcDir(it, "src/macosMain/kotlin")
                addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/macos/kotlin"))
                addResourcesSrcDir(it, "src/macosMain/resources")
            }
            val macTest = ss.maybeCreate("macTest").also {
                dependsOnIfNotConnected(it, nativeDesktopTest)
                if (appleTest != null) dependsOnIfNotConnected(it, appleTest)
                addKotlinSrcDir(it, "src/macTest/kotlin")
            }
            val macosTest = ss.maybeCreate("macosTest").also {
                dependsOnIfNotConnected(it, macTest)
                addKotlinSrcDir(it, "src/macosTest/kotlin")
                addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/macosTest/kotlin"))
            }
            ss.findByName("macosArm64Main")?.let { dependsOnIfNotConnected(it, macosMain) }
            ss.findByName("macosArm64Test")?.let { dependsOnIfNotConnected(it, macosTest) }
        }

        if ((backends.gtkEnabled || backends.kdeEnabled) && nativeDesktopMain != null && nativeDesktopTest != null) {
            val linuxCommon = ss.maybeCreate("linuxCommon").also {
                dependsOnIfNotConnected(it, nativeDesktopMain)
                addKotlinSrcDir(it, "src/linuxCommon/kotlin")
                addResourcesSrcDir(it, "src/linuxCommon/resources")
            }
            val linuxMain = ss.maybeCreate("linuxMain").also {
                dependsOnIfNotConnected(it, linuxCommon)
                addKotlinSrcDir(it, "src/linuxMain/kotlin")
                addKotlinSrcDir(it, "src/linuxCommon/kotlin")
                addResourcesSrcDir(it, "src/linuxMain/resources")
                addResourcesSrcDir(it, "src/linuxCommon/resources")
            }
            val linuxTest = ss.maybeCreate("linuxTest").also {
                dependsOnIfNotConnected(it, nativeDesktopTest)
                addKotlinSrcDir(it, "src/linuxTest/kotlin")
                addKotlinSrcDir(it, "src/linuxCommonTest/kotlin")
            }
            ss.findByName("linuxX64Main")?.let { dependsOnIfNotConnected(it, linuxMain) }
            ss.findByName("linuxArm64Main")?.let { dependsOnIfNotConnected(it, linuxMain) }
            ss.findByName("linuxX64Test")?.let { dependsOnIfNotConnected(it, linuxTest) }
            ss.findByName("linuxArm64Test")?.let { dependsOnIfNotConnected(it, linuxTest) }

            val linuxTargets = listOfNotNull(linuxX64Target, linuxArm64Target)
            val bothGtkAndKdeDirsExist = File(project.projectDir, "src/gtkMain").exists() &&
                File(project.projectDir, "src/kdeMain").exists()

            if (backends.gtkEnabled) {
                val gtk = ss.maybeCreate("gtk").also {
                    dependsOnIfNotConnected(it, linuxMain)
                    addKotlinSrcDir(it, "src/gtk/kotlin")
                    addResourcesSrcDir(it, "src/gtk/resources")
                }
                val gtkMain = ss.maybeCreate("gtkMain").also {
                    dependsOnIfNotConnected(it, gtk)
                    addKotlinSrcDir(it, "src/gtkMain/kotlin")
                    addKotlinSrcDir(it, "src/gtk/kotlin")
                    addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/gtk/kotlin"))
                    addResourcesSrcDir(it, "src/gtkMain/resources")
                    addResourcesSrcDir(it, "src/gtk/resources")
                }
                val gtkTest = ss.maybeCreate("gtkTest").also {
                    dependsOnIfNotConnected(it, linuxTest)
                    addKotlinSrcDir(it, "src/gtkTest/kotlin")
                    addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/gtkTest/kotlin"))
                }
                for (lt in linuxTargets) {
                    val comps = invokeNoArg(lt, "getCompilations") as? NamedDomainObjectContainer<*>
                    val gtkComp = comps?.maybeCreate("gtk")
                    val defaultSs = invokeNoArg(gtkComp, "getDefaultSourceSet")
                    if (defaultSs != null) {
                        dependsOnIfNotConnected(defaultSs, gtkMain)
                    }
                }
                if (!bothGtkAndKdeDirsExist) {
                    ss.findByName("linuxX64Main")?.let { dependsOnIfNotConnected(it, gtkMain) }
                    ss.findByName("linuxArm64Main")?.let { dependsOnIfNotConnected(it, gtkMain) }
                }
                ss.findByName("linuxX64Test")?.let { dependsOnIfNotConnected(it, gtkTest) }
                ss.findByName("linuxArm64Test")?.let { dependsOnIfNotConnected(it, gtkTest) }
            }

            if (backends.kdeEnabled) {
                val kde = ss.maybeCreate("kde").also {
                    dependsOnIfNotConnected(it, linuxMain)
                    addKotlinSrcDir(it, "src/kde/kotlin")
                    addResourcesSrcDir(it, "src/kde/resources")
                }
                val kdeMain = ss.maybeCreate("kdeMain").also {
                    dependsOnIfNotConnected(it, kde)
                    addKotlinSrcDir(it, "src/kdeMain/kotlin")
                    addKotlinSrcDir(it, "src/kde/kotlin")
                    addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/kde/kotlin"))
                    addResourcesSrcDir(it, "src/kdeMain/resources")
                    addResourcesSrcDir(it, "src/kde/resources")
                }
                val kdeTest = ss.maybeCreate("kdeTest").also {
                    dependsOnIfNotConnected(it, linuxTest)
                    addKotlinSrcDir(it, "src/kdeTest/kotlin")
                    addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/kdeTest/kotlin"))
                }
                for (lt in linuxTargets) {
                    val comps = invokeNoArg(lt, "getCompilations") as? NamedDomainObjectContainer<*>
                    val kdeComp = comps?.maybeCreate("kde")
                    val defaultSs = invokeNoArg(kdeComp, "getDefaultSourceSet")
                    if (defaultSs != null) {
                        dependsOnIfNotConnected(defaultSs, kdeMain)
                    }
                }
                if (!bothGtkAndKdeDirsExist && !(hasMainAppFragment && backends.gtkEnabled)) {
                    ss.findByName("linuxX64Main")?.let { dependsOnIfNotConnected(it, kdeMain) }
                    ss.findByName("linuxArm64Main")?.let { dependsOnIfNotConnected(it, kdeMain) }
                }
                ss.findByName("linuxX64Test")?.let { dependsOnIfNotConnected(it, kdeTest) }
                ss.findByName("linuxArm64Test")?.let { dependsOnIfNotConnected(it, kdeTest) }
            }
        }

        if (anyMobile && systemMain != null && systemTest != null) {
            val mobileCommon = ss.maybeCreate("mobileCommon").also {
                dependsOnIfNotConnected(it, systemMain)
                addKotlinSrcDir(it, "src/mobileCommon/kotlin")
                addResourcesSrcDir(it, "src/mobileCommon/resources")
            }
            val mobileMain = ss.maybeCreate("mobileMain").also {
                dependsOnIfNotConnected(it, mobileCommon)
                addKotlinSrcDir(it, "src/mobileMain/kotlin")
                addKotlinSrcDir(it, "src/mobileCommon/kotlin")
                addResourcesSrcDir(it, "src/mobileMain/resources")
                addResourcesSrcDir(it, "src/mobileCommon/resources")
            }
            val mobileTest = ss.maybeCreate("mobileTest").also {
                dependsOnIfNotConnected(it, systemTest)
                addKotlinSrcDir(it, "src/mobileTest/kotlin")
            }

            if (backends.androidEnabled) {
                val android = ss.maybeCreate("android").also {
                    dependsOnIfNotConnected(it, mobileMain)
                    if (jvmCommon != null) dependsOnIfNotConnected(it, jvmCommon)
                    addKotlinSrcDir(it, "src/android/kotlin")
                    addResourcesSrcDir(it, "src/android/resources")
                }
                ss.maybeCreate("androidMain").also {
                    dependsOnIfNotConnected(it, android)
                    addKotlinSrcDir(it, "src/androidMain/kotlin")
                    addKotlinSrcDir(it, "src/android/kotlin")
                    addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/android/kotlin"))
                    addResourcesSrcDir(it, "src/androidMain/resources")
                    addResourcesSrcDir(it, "src/android/resources")
                }
                val androidTest = ss.maybeCreate("androidTest").also {
                    dependsOnIfNotConnected(it, mobileTest)
                    if (jvmCommonTest != null) dependsOnIfNotConnected(it, jvmCommonTest)
                    addKotlinSrcDir(it, "src/androidTest/kotlin")
                    addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/androidTest/kotlin"))
                }
                ss.findByName("androidUnitTest")?.let { dependsOnIfNotConnected(it, androidTest) }
                ss.findByName("androidHostTest")?.let { dependsOnIfNotConnected(it, androidTest) }
            }

            if (backends.iosEnabled || backends.ipadosEnabled) {
                val iosCommon = ss.maybeCreate("iosCommon").also {
                    dependsOnIfNotConnected(it, mobileMain)
                    if (appleMain != null) dependsOnIfNotConnected(it, appleMain)
                    addKotlinSrcDir(it, "src/iosCommonMain/kotlin")
                    addKotlinSrcDir(it, "src/iosCommon/kotlin")
                    addResourcesSrcDir(it, "src/iosCommonMain/resources")
                    addResourcesSrcDir(it, "src/iosCommon/resources")
                }
                val iosCommonTest = ss.maybeCreate("iosCommonTest").also {
                    dependsOnIfNotConnected(it, mobileTest)
                    if (appleTest != null) dependsOnIfNotConnected(it, appleTest)
                    addKotlinSrcDir(it, "src/iosCommonTest/kotlin")
                }
                val iosTargets = listOfNotNull(iosArm64Target, iosSimArm64Target)

                if (backends.iosEnabled) {
                    val ios = ss.maybeCreate("ios").also {
                        dependsOnIfNotConnected(it, iosCommon)
                        addKotlinSrcDir(it, "src/ios/kotlin")
                        addResourcesSrcDir(it, "src/ios/resources")
                    }
                    val iosMain = ss.maybeCreate("iosMain").also {
                        dependsOnIfNotConnected(it, ios)
                        addKotlinSrcDir(it, "src/iosMain/kotlin")
                        addKotlinSrcDir(it, "src/ios/kotlin")
                        addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/ios/kotlin"))
                        addResourcesSrcDir(it, "src/iosMain/resources")
                        addResourcesSrcDir(it, "src/ios/resources")
                    }
                    val iosTest = ss.maybeCreate("iosTest").also {
                        dependsOnIfNotConnected(it, iosCommonTest)
                        addKotlinSrcDir(it, "src/iosTest/kotlin")
                        addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/iosTest/kotlin"))
                    }
                    ss.findByName("iosArm64Main")?.let { dependsOnIfNotConnected(it, iosMain) }
                    ss.findByName("iosSimulatorArm64Main")?.let { dependsOnIfNotConnected(it, iosMain) }
                    ss.findByName("iosArm64Test")?.let { dependsOnIfNotConnected(it, iosTest) }
                    ss.findByName("iosSimulatorArm64Test")?.let { dependsOnIfNotConnected(it, iosTest) }
                }

                if (backends.ipadosEnabled) {
                    val ipados = ss.maybeCreate("ipados").also {
                        dependsOnIfNotConnected(it, iosCommon)
                        addKotlinSrcDir(it, "src/ipados/kotlin")
                        addKotlinSrcDir(it, "src/ipad/kotlin")
                        addResourcesSrcDir(it, "src/ipados/resources")
                        addResourcesSrcDir(it, "src/ipad/resources")
                    }
                    val ipadosMain = ss.maybeCreate("ipadosMain").also {
                        dependsOnIfNotConnected(it, ipados)
                        addKotlinSrcDir(it, "src/ipadosMain/kotlin")
                        addKotlinSrcDir(it, "src/ipadMain/kotlin")
                        addKotlinSrcDir(it, "src/ipados/kotlin")
                        addKotlinSrcDir(it, "src/ipad/kotlin")
                        addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/ipados/kotlin"))
                        addResourcesSrcDir(it, "src/ipadosMain/resources")
                        addResourcesSrcDir(it, "src/ipadMain/resources")
                    }
                    val ipadosTest = ss.maybeCreate("ipadosTest").also {
                        dependsOnIfNotConnected(it, iosCommonTest)
                        addKotlinSrcDir(it, "src/ipadosTest/kotlin")
                        addKotlinSrcDir(it, "src/ipadTest/kotlin")
                        addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/ipadosTest/kotlin"))
                    }
                    for (itgt in iosTargets) {
                        val comps = invokeNoArg(itgt, "getCompilations") as? NamedDomainObjectContainer<*>
                        val ipadosComp = comps?.maybeCreate("ipados")
                        val defaultSs = invokeNoArg(ipadosComp, "getDefaultSourceSet")
                        if (defaultSs != null) {
                            dependsOnIfNotConnected(defaultSs, ipadosMain)
                        }
                    }
                    if (!backends.iosEnabled) {
                        ss.findByName("iosArm64Main")?.let { dependsOnIfNotConnected(it, ipadosMain) }
                        ss.findByName("iosSimulatorArm64Main")?.let { dependsOnIfNotConnected(it, ipadosMain) }
                    }
                    ss.findByName("iosArm64Test")?.let { dependsOnIfNotConnected(it, ipadosTest) }
                    ss.findByName("iosSimulatorArm64Test")?.let { dependsOnIfNotConnected(it, ipadosTest) }
                }
            }
        }

        // 3. Configure native desktop executable binaries (mingwX64, macosArm64, linuxX64, linuxArm64)
        configureNativeDesktopBinaries(
            project = project,
            extension = extension,
            mingwTarget = mingwTarget,
            macTargets = listOfNotNull(macArm64Target),
            linuxTargets = listOfNotNull(linuxX64Target, linuxArm64Target),
            defaultEntryPoint = if (hasMainAppFragment) "$generatedLauncherPackage.main" else ""
        )

        if (backends.iosEnabled || backends.ipadosEnabled) {
            configureIosFrameworks(
                project = project,
                extension = extension,
                iosTargets = listOfNotNull(iosArm64Target, iosSimArm64Target)
            )
        }

        // 3b. Wire consumer Kotlin/Native cinterops and nativeLibraries linker flags
        com.nexustools.genericui.plugin.nativebuild.GenericUiNativeBuildResolver
            .configureKotlinNativeInteropsAndLinks(project, extension)

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
                    "systemCommon" -> ssContainer?.findByName("systemMain") ?: ssContainer?.findByName("commonMain")
                    "appleCommon" -> ssContainer?.findByName("appleMain") ?: ssContainer?.findByName("systemMain")
                    "jvmCommon" -> ssContainer?.findByName("jvmCommon") ?: ssContainer?.findByName("systemMain")
                    "iosCommon" -> ssContainer?.findByName("iosCommon") ?: ssContainer?.findByName("mobileMain")
                    "macMain" -> ssContainer?.findByName("macosMain")
                    "linuxCommon" -> ssContainer?.findByName("linuxMain") ?: ssContainer?.findByName("linuxX64Main")
                    "nativeDesktopCommon", "desktopNativeCommon" ->
                        ssContainer?.findByName("nativeDesktopMain")
                            ?: ssContainer?.findByName("desktopNativeMain")
                            ?: ssContainer?.findByName("mingwX64Main")
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
                configName.startsWith("linux") ->
                    project.configurations.findByName("linuxMainImplementation")
                        ?: project.configurations.findByName("linuxCommonImplementation")
                        ?: project.configurations.findByName("linuxX64MainImplementation")
                        ?: project.configurations.findByName("desktopNativeMainImplementation")
                        ?: project.configurations.findByName("nativeDesktopMainImplementation")
                        ?: project.configurations.findByName("commonMainImplementation")
                configName.startsWith("nativeDesktop") || configName.startsWith("desktopNative") ->
                    project.configurations.findByName("nativeDesktopMainImplementation")
                        ?: project.configurations.findByName("desktopNativeMainImplementation")
                        ?: project.configurations.findByName("mingwX64MainImplementation")
                        ?: project.configurations.findByName("commonMainImplementation")
                configName.startsWith("desktop") ->
                    project.configurations.findByName("desktopMainImplementation")
                        ?: project.configurations.findByName("systemMainImplementation")
                        ?: project.configurations.findByName("commonMainImplementation")
                configName.startsWith("system") ->
                    project.configurations.findByName("systemMainImplementation")
                        ?: project.configurations.findByName("commonMainImplementation")
                configName.startsWith("mobile") ->
                    project.configurations.findByName("mobileMainImplementation")
                        ?: project.configurations.findByName("systemMainImplementation")
                        ?: project.configurations.findByName("commonMainImplementation")
                configName.startsWith("apple") ->
                    project.configurations.findByName("appleMainImplementation")
                        ?: project.configurations.findByName("systemMainImplementation")
                        ?: project.configurations.findByName("commonMainImplementation")
                configName.startsWith("iosCommon") ->
                    project.configurations.findByName("iosCommonImplementation")
                        ?: project.configurations.findByName("mobileMainImplementation")
                        ?: project.configurations.findByName("commonMainImplementation")
                configName.startsWith("ipados") ->
                    project.configurations.findByName("ipadosMainImplementation")
                        ?: project.configurations.findByName("iosCommonImplementation")
                        ?: project.configurations.findByName("mobileMainImplementation")
                        ?: project.configurations.findByName("commonMainImplementation")
                configName.startsWith("jvmCommon") ->
                    project.configurations.findByName("jvmCommonImplementation")
                        ?: project.configurations.findByName("jvmMainImplementation")
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

    private fun applyLinkerOptsToAllBinaries(nativeTarget: Any, opts: List<String>) {
        if (opts.isEmpty()) return
        runCatching {
            val binaries = invokeNoArg(nativeTarget, "getBinaries") as? org.gradle.api.DomainObjectCollection<*>
            binaries?.all(Action<Any> {
                val bin = this
                val existingOpts = invokeNoArg(bin, "getLinkerOpts") as? MutableCollection<*>
                val missingOpts = if (existingOpts != null) {
                    opts.filter { it !in existingOpts }
                } else {
                    opts
                }
                if (missingOpts.isNotEmpty()) {
                    bin.javaClass.methods.firstOrNull {
                        it.name == "linkerOpts" && it.parameterCount == 1 &&
                            Iterable::class.java.isAssignableFrom(it.parameterTypes[0])
                    }?.invoke(bin, missingOpts)
                }
            })
        }
    }

    private fun configureNativeDesktopBinaries(
        project: Project,
        extension: GenericUiExtension,
        mingwTarget: Any?,
        macTargets: List<Any>,
        linuxTargets: List<Any>,
        defaultEntryPoint: String = ""
    ) {
        val baseWinLibs = listOf(
            "-Wl,--allow-multiple-definition",
            "-lgdi32",
            "-luser32",
            "-lcomdlg32",
            "-lshell32",
            "-lole32",
            "-ldwmapi",
            "-luxtheme",
            "-static-libgcc",
            "-static-libstdc++",
            "-Wl,-Bstatic",
            "-lpthread",
            "-Wl,-Bdynamic"
        )
        val baseMacLinkerOpts = listOf(
            "-Wl,-rpath,@executable_path/../Frameworks",
            "-Wl,-rpath,@executable_path"
        )
        val baseLinuxLinkerOpts = listOf(
            "-Wl,-rpath,\$ORIGIN",
            "-Wl,-rpath,\$ORIGIN/lib"
        )

        if (mingwTarget != null) {
            applyLinkerOptsToAllBinaries(mingwTarget, baseWinLibs)
        }
        for (macTarget in macTargets) {
            applyLinkerOptsToAllBinaries(macTarget, baseMacLinkerOpts)
        }
        for (linuxTarget in linuxTargets) {
            applyLinkerOptsToAllBinaries(linuxTarget, baseLinuxLinkerOpts)
        }

        GenericUiBuildNativeSharedLibsTask.configureProject(project, extension)

        val app = extension.application
        val entryPointStr = defaultEntryPoint.trim()
        if (!app.createExecutableBinaries || entryPointStr.isEmpty()) {
            return
        }
        val resolvedBaseName = app.baseName.trim().ifEmpty { project.name }
        val buildDirFile = project.layout.buildDirectory.asFile.get()

        if (mingwTarget != null) {
            val winLinkerOpts = baseWinLibs.toMutableList()
            val winLinkDeps = mutableListOf<String>()
            if (!app.consoleApplication) {
                winLinkerOpts.add("-Wl,--subsystem,windows")
            }
            if (GenericUiDesktopResourceTask.findWindresExecutable() != null) {
                if (app.desktop.embedWindowsResources) {
                    val resFile = File(
                        buildDirFile,
                        "generated/genericui/desktop-resources/windows/app_resources.res"
                    )
                    winLinkerOpts.add(resFile.absolutePath)
                    winLinkDeps.add("prepareGenericUiDesktopResources")
                }
                val appResourcesObj = File(
                    buildDirFile,
                    "generated/genericui/resources/backends/windows/compiled/genericui_resources.o"
                )
                winLinkerOpts.add(appResourcesObj.absolutePath)
                winLinkDeps.add("compileGenericUiWindowsResources")
            }
            winLinkerOpts.addAll(app.extraWindowsLinkerOpts)
            configureTargetExecutable(
                nativeTarget = mingwTarget,
                namePrefix = null,
                compilationObj = null,
                entryPointStr = entryPointStr,
                baseNameStr = resolvedBaseName,
                linkerOptsList = winLinkerOpts,
                linkDependsOnTasks = winLinkDeps
            )
        }

        val (desktopCtx, secondaryDesktopExes) = collectDesktopContextAndSecondaryExecutables(project, extension)

        if (mingwTarget != null && secondaryDesktopExes.isNotEmpty()) {
            for (sec in secondaryDesktopExes) {
                val secWinOpts = baseWinLibs.toMutableList()
                val secWinDeps = mutableListOf<String>()
                if (!sec.windowsSubsystem.equals("console", ignoreCase = true)) {
                    secWinOpts.add("-Wl,--subsystem,${sec.windowsSubsystem}")
                }
                if (GenericUiDesktopResourceTask.findWindresExecutable() != null) {
                    if (app.desktop.embedWindowsResources) {
                        val resFile = File(
                            buildDirFile,
                            "generated/genericui/desktop-resources/windows/app_resources.res"
                        )
                        secWinOpts.add(resFile.absolutePath)
                        secWinDeps.add("prepareGenericUiDesktopResources")
                    }
                    val appResourcesObj = File(
                        buildDirFile,
                        "generated/genericui/resources/backends/windows/compiled/genericui_resources.o"
                    )
                    secWinOpts.add(appResourcesObj.absolutePath)
                    secWinDeps.add("compileGenericUiWindowsResources")
                }
                secWinOpts.addAll(sec.windowsExtraLibs)
                val safePrefix = sanitizeBinaryNamePrefix(sec.binaryBaseName)
                configureTargetExecutable(
                    nativeTarget = mingwTarget,
                    namePrefix = safePrefix,
                    compilationObj = null,
                    entryPointStr = sec.entryPointFqn,
                    baseNameStr = sec.binaryBaseName,
                    linkerOptsList = secWinOpts,
                    linkDependsOnTasks = secWinDeps
                )
            }
        }

        val macLinkerOpts = (baseMacLinkerOpts + listOf("-Wl,-export_dynamic") + app.extraPosixLinkerOpts).distinct()
        for (macTarget in macTargets) {
            val konanName = resolveKonanTargetName(macTarget)
            val (appResObj, compileTaskName) = ensurePosixAppResourcesObjectTask(
                project = project,
                platformDirName = "macos",
                konanTargetName = konanName
            )
            configureTargetExecutable(
                nativeTarget = macTarget,
                namePrefix = null,
                compilationObj = null,
                entryPointStr = entryPointStr,
                baseNameStr = resolvedBaseName,
                linkerOptsList = macLinkerOpts + appResObj.absolutePath,
                linkDependsOnTasks = listOf(compileTaskName)
            )
            for (sec in secondaryDesktopExes) {
                val safePrefix = sanitizeBinaryNamePrefix(sec.binaryBaseName)
                configureTargetExecutable(
                    nativeTarget = macTarget,
                    namePrefix = safePrefix,
                    compilationObj = null,
                    entryPointStr = sec.entryPointFqn,
                    baseNameStr = sec.binaryBaseName,
                    linkerOptsList = macLinkerOpts + sec.macosExtraLinkerOpts + appResObj.absolutePath,
                    linkDependsOnTasks = listOf(compileTaskName)
                )
            }
        }

        val linuxLinkerOpts = (
            baseLinuxLinkerOpts +
                listOf("-Wl,--export-dynamic-symbol=genericui_res_lookup_app") +
                app.extraPosixLinkerOpts
            ).distinct()
        val bothGtkAndKdeDirsExist = File(project.projectDir, "src/gtkMain").exists() &&
            File(project.projectDir, "src/kdeMain").exists()
        for (linuxTarget in linuxTargets) {
            val konanName = resolveKonanTargetName(linuxTarget)
            val (appResObj, compileTaskName) = ensurePosixAppResourcesObjectTask(
                project = project,
                platformDirName = "linux",
                konanTargetName = konanName
            )
            val targetLinuxLinkerOpts = linuxLinkerOpts + appResObj.absolutePath
            val targetLinuxDeps = listOf(compileTaskName)
            configureTargetExecutable(
                nativeTarget = linuxTarget,
                namePrefix = null,
                compilationObj = null,
                entryPointStr = entryPointStr,
                baseNameStr = resolvedBaseName,
                linkerOptsList = targetLinuxLinkerOpts,
                linkDependsOnTasks = targetLinuxDeps
            )
            if (!bothGtkAndKdeDirsExist) {
                for (sec in secondaryDesktopExes) {
                    val safePrefix = sanitizeBinaryNamePrefix(sec.binaryBaseName)
                    configureTargetExecutable(
                        nativeTarget = linuxTarget,
                        namePrefix = safePrefix,
                        compilationObj = null,
                        entryPointStr = sec.entryPointFqn,
                        baseNameStr = sec.binaryBaseName,
                        linkerOptsList = targetLinuxLinkerOpts + sec.linuxExtraLinkerOpts,
                        linkDependsOnTasks = targetLinuxDeps
                    )
                }
            }
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
                        linkerOptsList = targetLinuxLinkerOpts,
                        linkDependsOnTasks = targetLinuxDeps
                    )
                    for (sec in secondaryDesktopExes) {
                        val safeCap = sanitizeBinaryNamePrefix(sec.binaryBaseName)
                            .replaceFirstChar { it.uppercaseChar() }
                        configureTargetExecutable(
                            nativeTarget = linuxTarget,
                            namePrefix = "gtk$safeCap",
                            compilationObj = gtkComp,
                            entryPointStr = sec.entryPointFqn,
                            baseNameStr = sec.binaryBaseName,
                            linkerOptsList = targetLinuxLinkerOpts + sec.linuxExtraLinkerOpts,
                            linkDependsOnTasks = targetLinuxDeps
                        )
                    }
                }
            }
            if (extension.backends.kdeEnabled) {
                val kdeComp = comps?.findByName("kde")
                if (kdeComp != null) {
                    configureTargetExecutable(
                        nativeTarget = linuxTarget,
                        namePrefix = "kde",
                        compilationObj = kdeComp,
                        entryPointStr = entryPointStr,
                        baseNameStr = "$resolvedBaseName-kde",
                        linkerOptsList = targetLinuxLinkerOpts,
                        linkDependsOnTasks = targetLinuxDeps
                    )
                    for (sec in secondaryDesktopExes) {
                        val safeCap = sanitizeBinaryNamePrefix(sec.binaryBaseName)
                            .replaceFirstChar { it.uppercaseChar() }
                        configureTargetExecutable(
                            nativeTarget = linuxTarget,
                            namePrefix = "kde$safeCap",
                            compilationObj = kdeComp,
                            entryPointStr = sec.entryPointFqn,
                            baseNameStr = sec.binaryBaseName,
                            linkerOptsList = targetLinuxLinkerOpts + sec.linuxExtraLinkerOpts,
                            linkDependsOnTasks = targetLinuxDeps
                        )
                    }
                }
            }
        }
    }

    private fun sanitizeBinaryNamePrefix(binaryBaseName: String): String =
        binaryBaseName.replace(Regex("[^A-Za-z0-9]"), "")
            .ifEmpty { "secondary" }
            .replaceFirstChar { it.lowercaseChar() }

    private fun collectDesktopContextAndSecondaryExecutables(
        project: Project,
        extension: GenericUiExtension
    ): Pair<
        com.nexustools.genericui.plugin.spi.ExtensionBuildContext,
        List<com.nexustools.genericui.plugin.spi.SecondaryDesktopExecutableContribution>
    > {
        val app = extension.application
        val resolvedBaseName = app.baseName.trim().ifEmpty { project.name }
        val enabledExtPathsSet = extension.extensions
            .enabledExtensionModulePathsForTarget(GenericUiVariantScope.DESKTOP_COMMON)
            .toSet()
        val extraSourceDirs = extension.sourceSets.allSpecs().flatMap { spec ->
            spec.kotlinSrcDirs.mapNotNull { dir ->
                when (dir) {
                    is File -> dir
                    is String -> File(dir).let { if (it.isAbsolute) it else File(project.projectDir, it.path) }
                    else -> null
                }
            }
        }
        val discoveredFrags = if (extraSourceDirs.isNotEmpty()) {
            (GenericUiFragmentScanner.scanProject(project.projectDir) +
                GenericUiFragmentScanner.scanSourceDirectories(extraSourceDirs))
                .distinctBy { "${it.qualifiedName}:${it.isMainFragment}:${it.isApplicationClass}" }
        } else {
            GenericUiFragmentScanner.scanProject(project.projectDir)
        }
        val scanSpecs = com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
            .allAnnotationScanSpecs(enabledExtPathsSet, project.projectDir)
        val discoveredSyms = GenericUiFragmentScanner.scanProjectSymbols(
            projectDir = project.projectDir,
            scanSpecs = scanSpecs,
            extraDirectories = extraSourceDirs
        )
        val resolvedLauncherPkg = GenericUiGenerateLaunchersTask.resolveGeneratedLauncherPackage(
            extension = extension,
            projectGroup = project.group.toString(),
            discoveredFragments = discoveredFrags
        )
        val desktopOptions = LinkedHashMap<String, String>(extension.extensions.extensionOptions).apply {
            put(
                com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_ENABLED_BACKENDS,
                GenericUiGenerateLaunchersTask.enabledAllBackendScopes(extension.backends).joinToString(",") { it.id }
            )
            put(
                com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_RESOURCES_ENABLED,
                extension.resources.enabled.toString()
            )
            val resPkg = extension.resources.packageName.trim()
            if (resPkg.isNotEmpty()) {
                put(
                    com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_RESOURCE_PACKAGE,
                    resPkg
                )
            }
            val schemaEnabled =
                com.nexustools.genericui.plugin.spi.SchemaExtensionContributor.SCHEMA_MODULE_PATH in enabledExtPathsSet
            put(
                com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_SCHEMA_ENABLED,
                schemaEnabled.toString()
            )
            put(
                com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_BINARY_CODECS_ENABLED,
                extension.includeCommon.toString()
            )
        }
        val desktopCtx = com.nexustools.genericui.plugin.spi.ExtensionBuildContext(
            projectRootDir = project.projectDir,
            projectName = project.name,
            appId = app.desktop.appId.trim().ifEmpty { resolvedBaseName },
            appName = app.desktop.appName.trim().ifEmpty { resolvedBaseName },
            appVersion = GenericUiGradlePlugin.resolveVariantWithProjectFallbacksStatic(
                project,
                extension,
                GenericUiVariantScope.COMMON
            ).appVersion,
            baseName = resolvedBaseName,
            generatedPackageName = resolvedLauncherPkg,
            enabledExtensionModulePaths = enabledExtPathsSet,
            customExtensionModulePaths = extension.extensions.customModulePaths.toSet(),
            discoveredFragments = discoveredFrags,
            discoveredSymbols = discoveredSyms,
            extensionOptions = desktopOptions
        )
        val secondaryDesktopExes = com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
            .collectDesktopContributions(desktopCtx)
            .flatMap { it.secondaryExecutables }
            .filter { it.binaryBaseName.isNotBlank() && it.entryPointFqn.isNotBlank() }
        return desktopCtx to secondaryDesktopExes
    }

    private fun resolveKonanTargetName(nativeTarget: Any): String {
        val gradleTargetName = (invokeNoArg(nativeTarget, "getName") as? String).orEmpty()
        return when (gradleTargetName) {
            "macosArm64" -> "macos_arm64"
            "macosX64" -> "macos_x64"
            "linuxX64" -> "linux_x64"
            "linuxArm64" -> "linux_arm64"
            "mingwX64" -> "mingw_x64"
            "iosArm64" -> "ios_arm64"
            "iosSimulatorArm64" -> "ios_simulator_arm64"
            else -> gradleTargetName.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
        }
    }

    private fun ensurePosixAppResourcesObjectTask(
        project: Project,
        platformDirName: String,
        konanTargetName: String
    ): Pair<File, String> {
        val buildDir = project.layout.buildDirectory.asFile.get()
        val objOut = File(
            buildDir,
            "generated/genericui/resources/backends/$platformDirName/compiled/genericui_app_resources_$konanTargetName.o"
        )
        val suffix = konanTargetName.split('_', '-')
            .filter { it.isNotEmpty() }
            .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
        val taskName = "compileGenericUiAppResources$suffix"
        if (project.tasks.findByName(taskName) == null) {
            val cSourceFile = File(
                buildDir,
                "generated/genericui/resources/backends/$platformDirName/genericui_app_resources.c"
            )
            project.tasks.register(taskName) {
                group = GenericUiGradlePlugin.TASK_GROUP
                description = "Compiles embedded $platformDirName application resources C table for $konanTargetName."
                project.tasks.findByName("compileGenericUiResources")?.let { dependsOn(it) }
                doLast {
                    val effectiveC = if (cSourceFile.isFile) {
                        cSourceFile
                    } else {
                        cSourceFile.parentFile?.mkdirs()
                        cSourceFile.writeText(
                            GenericUiNativeResourceEmbedder.generatePosixResourceTableCSource(
                                lookupSymbol = "genericui_res_lookup_app",
                                resources = emptyMap(),
                                includeMachOConstSection = platformDirName == "macos"
                            ),
                            Charsets.UTF_8
                        )
                        cSourceFile
                    }
                    GenericUiNativeResourceEmbedder.compileCSourceToObject(
                        cFile = effectiveC,
                        objOut = objOut,
                        konanTargetName = konanTargetName
                    )
                }
            }
        }
        return objOut to taskName
    }

    private fun configureIosFrameworks(
        project: Project,
        extension: GenericUiExtension,
        iosTargets: List<Any>
    ) {
        val app = extension.application
        if (!app.createExecutableBinaries) return
        val frameworkBaseName = GenericUiGenerateIosAppTask.sanitizeFrameworkName(
            app.baseName.trim().ifEmpty { project.name }
        )
        val backends = extension.backends
        for (iosTarget in iosTargets) {
            runCatching {
                val binaries = invokeNoArg(iosTarget, "getBinaries") ?: return@runCatching
                val configureAction = Action<Any> {
                    val fw = this
                    fw.javaClass.methods.firstOrNull {
                        it.name == "setBaseName" && it.parameterCount == 1
                    }?.invoke(fw, frameworkBaseName)
                }
                val findFw = binaries.javaClass.methods.firstOrNull {
                    it.name == "findFramework" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
                }
                if (backends.iosEnabled || !backends.ipadosEnabled) {
                    val existingDebug = findFw?.invoke(binaries, "DEBUG")
                    val existingRelease = findFw?.invoke(binaries, "RELEASE")
                    if (existingDebug == null && existingRelease == null) {
                        binaries.javaClass.methods.firstOrNull {
                            it.name == "framework" && it.parameterCount == 1 &&
                                Action::class.java.isAssignableFrom(it.parameterTypes[0])
                        }?.invoke(binaries, configureAction)
                    } else {
                        existingDebug?.let { configureAction.execute(it) }
                        existingRelease?.let { configureAction.execute(it) }
                    }
                }
                if (backends.ipadosEnabled) {
                    if (!backends.iosEnabled) {
                        val existingDebug = findFw?.invoke(binaries, "DEBUG")
                        val existingRelease = findFw?.invoke(binaries, "RELEASE")
                        if (existingDebug == null && existingRelease == null) {
                            binaries.javaClass.methods.firstOrNull {
                                it.name == "framework" && it.parameterCount == 1 &&
                                    Action::class.java.isAssignableFrom(it.parameterTypes[0])
                            }?.invoke(binaries, configureAction)
                        } else {
                            existingDebug?.let { configureAction.execute(it) }
                            existingRelease?.let { configureAction.execute(it) }
                        }
                    } else {
                        val comps = invokeNoArg(iosTarget, "getCompilations") as? NamedDomainObjectContainer<*>
                        val ipadosComp = comps?.findByName("ipados")
                        if (ipadosComp != null) {
                            val ipadosFwAction = Action<Any> {
                                val fw = this
                                fw.javaClass.methods.firstOrNull {
                                    it.name == "setCompilation" && it.parameterCount == 1
                                }?.invoke(fw, ipadosComp)
                                fw.javaClass.methods.firstOrNull {
                                    it.name == "setBaseName" && it.parameterCount == 1
                                }?.invoke(fw, frameworkBaseName)
                            }
                            val existingNamed = (binaries as? Iterable<*>)?.filterNotNull()?.filter { bin ->
                                val binName = runCatching { bin.javaClass.getMethod("getName").invoke(bin) as? String }.getOrNull().orEmpty()
                                binName.startsWith("ipados", ignoreCase = true) && binName.endsWith("Framework", ignoreCase = true)
                            }.orEmpty()
                            if (existingNamed.isEmpty()) {
                                binaries.javaClass.methods.firstOrNull {
                                    it.name == "framework" && it.parameterCount == 2 &&
                                        it.parameterTypes[0] == String::class.java &&
                                        Action::class.java.isAssignableFrom(it.parameterTypes[1])
                                }?.invoke(binaries, "ipados", ipadosFwAction)
                            } else {
                                existingNamed.forEach { ipadosFwAction.execute(it) }
                            }
                        }
                    }
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
        linkerOptsList: List<String>,
        linkDependsOnTasks: List<String> = emptyList()
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
            if (linkDependsOnTasks.isNotEmpty()) {
                val linkTaskProvider = invokeNoArg(exe, "getLinkTaskProvider") as? TaskProvider<*>
                linkTaskProvider?.configure {
                    for (depName in linkDependsOnTasks) {
                        project.tasks.findByName(depName)?.let { dependsOn(it) } ?: dependsOn(depName)
                    }
                }
            }
        }

        runCatching {
            if (namePrefix == null) {
                val findExec = binaries.javaClass.methods.firstOrNull {
                    it.name == "findExecutable" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
                }
                val existingDebug = findExec?.invoke(binaries, "DEBUG")
                val existingRelease = findExec?.invoke(binaries, "RELEASE")
                if (existingDebug == null && existingRelease == null) {
                    binaries.javaClass.methods.firstOrNull {
                        it.name == "executable" && it.parameterCount == 1 &&
                            Action::class.java.isAssignableFrom(it.parameterTypes[0])
                    }?.invoke(binaries, configureAction)
                } else {
                    existingDebug?.let { configureAction.execute(it) }
                    existingRelease?.let { configureAction.execute(it) }
                }
            } else {
                val findNamedExec = binaries.javaClass.methods.firstOrNull {
                    it.name == "findExecutable" && it.parameterCount == 2 &&
                        it.parameterTypes[0] == String::class.java &&
                        it.parameterTypes[1] == String::class.java
                }
                val existingNamedDebug = findNamedExec?.invoke(binaries, namePrefix, "DEBUG")
                val existingNamedRelease = findNamedExec?.invoke(binaries, namePrefix, "RELEASE")
                if (existingNamedDebug == null && existingNamedRelease == null) {
                    binaries.javaClass.methods.firstOrNull {
                        it.name == "executable" && it.parameterCount == 2 &&
                            it.parameterTypes[0] == String::class.java &&
                            Action::class.java.isAssignableFrom(it.parameterTypes[1])
                    }?.invoke(binaries, namePrefix, configureAction)
                } else {
                    existingNamedDebug?.let { configureAction.execute(it) }
                    existingNamedRelease?.let { configureAction.execute(it) }
                }
            }
        }
    }

    /**
     * Registers canonical GenericUI `development<Target>` and `production<Target>` build target tasks:
     * - Windows (`X32`, `X64`, `Arm64` + Host Aggregate): `developmentWindows`, `productionWindows`,
     *   `developmentWindowsX64`, `productionWindowsX64`, `developmentWindowsX32`, `productionWindowsX32`,
     *   `developmentWindowsArm64`, `productionWindowsArm64`
     * - macOS (Apple Silicon `Arm64` Only): `developmentMac`, `productionMac`, `developmentMacArm64`, `productionMacArm64`
     * - Linux GTK (`X32`, `X64`, `Arm32`, `Arm64` + Aggregate): `developmentLinuxGtk`, `productionLinuxGtk`,
     *   `developmentLinuxGtkX32`, `productionLinuxGtkX32`, `developmentLinuxGtkX64`, `productionLinuxGtkX64`,
     *   `developmentLinuxGtkArm32`, `productionLinuxGtkArm32`, `developmentLinuxGtkArm64`, `productionLinuxGtkArm64`
     * - Linux KDE (`X32`, `X64`, `Arm32`, `Arm64` + Aggregate): `developmentLinuxKde`, `productionLinuxKde`,
     *   `developmentLinuxKdeX32`, `productionLinuxKdeX32`, `developmentLinuxKdeX64`, `productionLinuxKdeX64`,
     *   `developmentLinuxKdeArm32`, `productionLinuxKdeArm32`, `developmentLinuxKdeArm64`, `productionLinuxKdeArm64`
     * - Android (Pure Java/DEX Universal APK + CPU-Specific Kotlin/Native Targets `X32`, `X64`, `Arm32`, `Arm64`):
     *   `developmentAndroid`, `productionAndroid`, `developmentAndroidX32`, `productionAndroidX32`,
     *   `developmentAndroidX64`, `productionAndroidX64`, `developmentAndroidArm32`, `productionAndroidArm32`,
     *   `developmentAndroidArm64`, `productionAndroidArm64`
     * - iOS (`Arm64` Device & `SimulatorArm64` Apple Silicon Simulator + Aggregate): `developmentIos`, `productionIos`,
     *   `developmentIosArm64`, `productionIosArm64`, `developmentIosSimulatorArm64`, `productionIosSimulatorArm64`
     * - Swing & Web (Unified, No Sub-Targets): `developmentSwing`, `productionSwing`, `developmentWeb`, `productionWeb`
     */
    internal fun isMediaExtensionEnabled(extension: GenericUiExtension): Boolean =
        extension.extensions.mediaEnabled ||
            ":extensions:media:media" in extension.extensions.enabledExtensionModulePathsForTarget(
                GenericUiVariantScope.DESKTOP_COMMON
            )

    internal fun isGpuExtensionEnabled(extension: GenericUiExtension): Boolean =
        extension.extensions.gpuEnabled ||
            ":extensions:graphics:gpu" in extension.extensions.enabledExtensionModulePathsForTarget(
                GenericUiVariantScope.DESKTOP_COMMON
            )

    private fun ensureExtensionNativeBundleTasksRegistered(project: Project, extension: GenericUiExtension) {
        if (project.tasks.findByName("bundleGenericUiVlc") == null) {
            project.tasks.register("bundleGenericUiVlc", VlcNativeBundleTask::class.java) {
                group = GenericUiGradlePlugin.TASK_GROUP
                description = "Stages LibVLC native runtime libraries (libvlc, libvlccore, plugins/) for desktop media targets."
                bundleVlcOnDesktop.set(project.provider { isMediaExtensionEnabled(extension) })
                outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/nativeBundle/vlc"))
            }
        }
    }

    internal fun copyFilteredDirectoryContents(sourceDir: File, targetDir: File, setExecutableOnBinaries: Boolean = false) {
        GenericUiSymlinkPreservingCopy.copyDirectoryContentsPreservingSymlinks(
            sourceDir = sourceDir,
            targetDir = targetDir,
            sourceRoot = sourceDir,
            setExecutableOnBinaries = setExecutableOnBinaries,
            entryFilter = { !isExcludedStaticCoreLibrary(it) }
        )
    }

    private fun purgeExcludedStaticCoreLibraries(rootDir: File) {
        GenericUiSymlinkPreservingCopy.walkPathsNoFollow(rootDir, bottomUp = true) { p ->
            val f = p.toFile()
            if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) && isExcludedStaticCoreLibrary(f)) {
                runCatching { Files.deleteIfExists(p) }
            }
        }
    }

    private fun stageWindowsNativeExtensionBundles(
        buildDir: File,
        distDir: File,
        candidateBinDirs: List<File>,
        mode: String,
        mediaEnabled: Boolean,
        gpuEnabled: Boolean,
        legacySecondaryBinaryNames: Set<String> = emptySet()
    ) {
        val destinations = buildList {
            add(distDir)
            candidateBinDirs.filter { it.exists() || it.name.contains("Executable") || it.name.contains("Test") }.forEach { add(it) }
        }.distinct()

        if (mediaEnabled) {
            val vlcWinDir = File(buildDir, "generated/genericui/nativeBundle/vlc/windows")
            if (vlcWinDir.isDirectory) {
                for (dest in destinations) {
                    copyFilteredDirectoryContents(vlcWinDir, dest)
                }
            }
        }

        if (gpuEnabled) {
            val gpuWinDir = File(buildDir, "generated/genericui/nativeBundle/gpu/windows")
            if (gpuWinDir.isDirectory) {
                for (dest in destinations) {
                    copyFilteredDirectoryContents(gpuWinDir, dest)
                }
            }
        }

        for (dest in destinations) {
            purgeExcludedStaticCoreLibraries(dest)
            listOf("gui", "app", "res").forEach { staleDir ->
                File(dest, staleDir).takeIf { it.exists() }?.deleteRecursively()
            }
            for (legacyName in legacySecondaryBinaryNames) {
                File(dest, "$legacyName.exe").takeIf { it.exists() }?.delete()
                File(dest, legacyName).takeIf { it.exists() }?.delete()
            }
            dest.listFiles()?.forEach { f ->
                if (f.isFile && f.name.lowercase().endsWith(".dll.a")) {
                    runCatching { f.delete() }
                }
            }
        }
    }

    private fun assembleMacosAppBundle(
        project: Project,
        extension: GenericUiExtension,
        buildDir: File,
        distDir: File,
        candidateBinDirs: List<File>,
        mode: String,
        mediaEnabled: Boolean,
        gpuEnabled: Boolean,
        secondaryExes: List<com.nexustools.genericui.plugin.spi.SecondaryDesktopExecutableContribution> = emptyList()
    ) {
        val appName = extension.application.desktop.appName.trim().ifEmpty {
            extension.app.name.trim().ifEmpty {
                extension.application.baseName.trim().ifEmpty { project.name }
            }
        }
        val baseName = extension.application.baseName.trim().ifEmpty { project.name }
        val appId = extension.application.desktop.appId.trim().ifEmpty {
            extension.app.id.trim().ifEmpty {
                val grp = project.group.toString().trim()
                if (grp.isNotEmpty() && grp != "unspecified") grp else "com.nexustools.${baseName.lowercase()}"
            }
        }
        val resolvedMac = GenericUiGradlePlugin.resolveVariantWithProjectFallbacksStatic(
            project,
            extension,
            GenericUiVariantScope.MACOS
        )
        val appVersion = resolvedMac.appVersion
        val gitHash = resolvedMac.gitHash
        val macosBundleVersion = GenericUiGitVersionResolver.resolveMacosBundleVersion(
            version = appVersion,
            buildVersion = resolvedMac.buildVersion,
            gitHash = gitHash
        )

        val appVendor = extension.app.vendor.trim().ifEmpty {
            extension.application.desktop.vendor.trim().ifEmpty { "NexusTools" }
        }
        val resolvedPackageName = extension.app.packageName.trim().ifEmpty {
            extension.application.androidNamespace.trim().ifEmpty { appId }
        }
        val appCopyright = extension.app.copyright.trim().ifEmpty {
            extension.application.desktop.copyright.trim().ifEmpty {
                GenericUiVariantResolver.deriveDefaultCopyright(
                    packageName = resolvedPackageName,
                    appId = appId,
                    fallbackVendor = appVendor
                )
            }
        }
        val appBundleDir = File(distDir, "$appName.app")
        val contentsDir = File(appBundleDir, "Contents")
        val macOsDir = File(contentsDir, "MacOS")
        val frameworksDir = File(contentsDir, "Frameworks")
        val resourcesDir = File(contentsDir, "Resources")
        macOsDir.mkdirs()
        frameworksDir.mkdirs()
        resourcesDir.mkdirs()

        // 1.PkgInfo
        File(contentsDir, "PkgInfo").writeText("APPL????", Charsets.US_ASCII)

        // 2. Main executable in Contents/MacOS/<executable>
        macOsDir.listFiles()?.forEach { existing ->
            if (existing.isFile && existing.name.endsWith(".dylib", ignoreCase = true)) {
                runCatching { existing.delete() }
            }
        }
        val exeCandidates = buildList {
            for (binDir in candidateBinDirs) {
                if (binDir.isDirectory) {
                    binDir.listFiles()?.filter {
                        it.isFile && (it.extension == "kexe" || it.name == baseName || it.canExecute()) &&
                            !it.name.endsWith(".dylib") && !it.name.endsWith(".plist")
                    }?.forEach { add(it) }
                }
            }
        }
        fun candidateBinaryName(file: File): String =
            if (file.extension.equals("kexe", ignoreCase = true)) {
                file.nameWithoutExtension
            } else {
                file.name
            }
        val secondaryByBaseName = secondaryExes.associateBy { it.binaryBaseName }
        val protectedPrimaryNames = setOf(baseName, "$baseName-gtk", "$baseName-kde")
        val legacySecondaryNames = secondaryExes
            .flatMap { it.legacyBinaryBaseNames }
            .filter { it.isNotBlank() && it !in secondaryByBaseName && it !in protectedPrimaryNames }
            .toSet()
        val excludedPrimaryNames = secondaryByBaseName.keys + legacySecondaryNames
        val primaryExe = exeCandidates.firstOrNull { candidate ->
            candidateBinaryName(candidate) !in excludedPrimaryNames
        }
        val bundleExecutableName = baseName
        val targetExeFile = File(macOsDir, bundleExecutableName)
        for (staleName in legacySecondaryNames) {
            File(macOsDir, staleName).takeIf { it.exists() }?.delete()
            File(macOsDir, "$staleName.kexe").takeIf { it.exists() }?.delete()
        }
        for (sec in secondaryExes) {
            if (!sec.macosHelperBundleSuffix.isNullOrBlank()) {
                val helperExeName = if (sec.binaryBaseName.endsWith(".exe", ignoreCase = true)) {
                    sec.binaryBaseName.dropLast(4)
                } else {
                    sec.binaryBaseName
                }
                if (helperExeName != bundleExecutableName) {
                    File(macOsDir, helperExeName).takeIf { it.exists() }?.delete()
                    File(macOsDir, "$helperExeName.kexe").takeIf { it.exists() }?.delete()
                }
            }
        }

        val swConfig = extension.extensions.serviceWorkerConfig
        val localNetDesc = swConfig.macLocalNetworkUsageDescription?.trim()?.takeIf { it.isNotEmpty() }
            ?: extension.application.desktop.macLocalNetworkUsageDescription.trim().takeIf { it.isNotEmpty() }
        val bonjourServices = (swConfig.macBonjourServices + extension.application.desktop.macBonjourServices)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val macMinSystemVersion = extension.application.desktop.macMinimumSystemVersion.trim().ifEmpty { "12.0" }
        val macCategory = extension.application.desktop.macCategory.trim().ifEmpty { "public.app-category.utilities" }
        val multipleInstancesProhibited = extension.application.desktop.macMultipleInstancesProhibited
        val helpersDir = File(contentsDir, "Helpers")
        data class StagedHelperBundle(
            val bundleDir: File,
            val executableFile: File,
            val bundleIdentifier: String
        )
        val stagedHelperBundles = mutableListOf<StagedHelperBundle>()

        if (primaryExe != null && primaryExe.isFile) {
            if (targetExeFile.exists()) {
                targetExeFile.delete()
            }
            primaryExe.copyTo(targetExeFile, overwrite = true)
            targetExeFile.setExecutable(true, false)
            macOsDir.listFiles()?.forEach { existing ->
                if (existing.isFile && existing.name.endsWith(".kexe", ignoreCase = true) && existing != targetExeFile) {
                    runCatching { existing.delete() }
                }
            }
        } else {
            // Ensure Contents/MacOS/<baseName> placeholder exists when cross-building on non-macOS host
            if (!targetExeFile.exists()) {
                writeMinimalMachOArm64Executable(targetExeFile)
            }
            targetExeFile.setExecutable(true, false)
        }

        for (secondaryExe in exeCandidates.filter { it != primaryExe }) {
            if (!secondaryExe.isFile) continue
            val secName = candidateBinaryName(secondaryExe)
            if (secName.isBlank() || secName == bundleExecutableName || secName in legacySecondaryNames) continue
            val spec = secondaryByBaseName[secName]
            val helperSuffix = spec?.macosHelperBundleSuffix?.trim()?.takeIf { it.isNotEmpty() }
            if (spec != null && helperSuffix != null) {
                helpersDir.mkdirs()
                val helperDisplayName = "$appName $helperSuffix"
                val helperBundleDir = File(helpersDir, "$helperDisplayName.app")
                val helperContentsDir = File(helperBundleDir, "Contents")
                val helperMacOsDir = File(helperContentsDir, "MacOS")
                val helperResourcesDir = File(helperContentsDir, "Resources")
                helperMacOsDir.mkdirs()
                helperResourcesDir.mkdirs()
                File(helperContentsDir, "PkgInfo").writeText("APPL????", Charsets.US_ASCII)

                val helperExeName = if (spec.binaryBaseName.endsWith(".exe", ignoreCase = true)) {
                    spec.binaryBaseName.dropLast(4)
                } else {
                    spec.binaryBaseName
                }
                for (legacyName in spec.legacyBinaryBaseNames) {
                    if (legacyName.isNotBlank() && legacyName != helperExeName) {
                        File(helperMacOsDir, legacyName).takeIf { it.exists() }?.delete()
                        File(helperMacOsDir, "$legacyName.kexe").takeIf { it.exists() }?.delete()
                    }
                }
                helperMacOsDir.listFiles()?.forEach { existing ->
                    if (existing.isFile && existing.extension.equals("kexe", ignoreCase = true)) {
                        runCatching { existing.delete() }
                    }
                }
                val targetHelperExe = File(helperMacOsDir, helperExeName)
                if (targetHelperExe.exists()) {
                    targetHelperExe.delete()
                }
                secondaryExe.copyTo(targetHelperExe, overwrite = true)
                targetHelperExe.setExecutable(true, false)

                val helperBundleIdSuffix = spec.macosHelperBundleIdSuffix?.trim()?.takeIf { it.isNotEmpty() } ?: helperExeName
                val helperBundleId = "$appId.$helperBundleIdSuffix"
                stagedHelperBundles.add(StagedHelperBundle(helperBundleDir, targetHelperExe, helperBundleId))

                val helperLocalNetDesc = localNetDesc
                    ?: "$appName uses local network access to discover and communicate with devices on your local network."
                File(helperContentsDir, "Info.plist").writeText(
                    buildString {
                        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                        appendLine("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">")
                        appendLine("<plist version=\"1.0\">")
                        appendLine("<dict>")
                        appendLine("    <key>CFBundleName</key>")
                        appendLine("    <string>${escapePlistXml(helperDisplayName)}</string>")
                        appendLine("    <key>CFBundleDisplayName</key>")
                        appendLine("    <string>${escapePlistXml(helperDisplayName)}</string>")
                        appendLine("    <key>CFBundleIdentifier</key>")
                        appendLine("    <string>${escapePlistXml(helperBundleId)}</string>")
                        appendLine("    <key>CFBundleVersion</key>")
                        appendLine("    <string>${escapePlistXml(macosBundleVersion)}</string>")
                        appendLine("    <key>CFBundleShortVersionString</key>")
                        appendLine("    <string>${escapePlistXml(appVersion)}</string>")
                        if (gitHash.isNotEmpty()) {
                            appendLine("    <key>GenericUIGitCommit</key>")
                            appendLine("    <string>${escapePlistXml(gitHash)}</string>")
                        }
                        appendLine("    <key>CFBundleExecutable</key>")
                        appendLine("    <string>${escapePlistXml(helperExeName)}</string>")
                        appendLine("    <key>CFBundleIconFile</key>")
                        appendLine("    <string>icon.icns</string>")
                        appendLine("    <key>CFBundlePackageType</key>")
                        appendLine("    <string>APPL</string>")
                        appendLine("    <key>LSMinimumSystemVersion</key>")
                        appendLine("    <string>${escapePlistXml(macMinSystemVersion)}</string>")
                        appendLine("    <key>LSUIElement</key>")
                        appendLine("    <${if (spec.macosHelperLsUiElement) "true" else "false"}/>")
                        appendLine("    <key>LSMultipleInstancesProhibited</key>")
                        appendLine("    <${if (multipleInstancesProhibited) "true" else "false"}/>")
                        appendLine("    <key>NSHighResolutionCapable</key>")
                        appendLine("    <true/>")
                        appendLine("    <key>NSHumanReadableCopyright</key>")
                        appendLine("    <string>${escapePlistXml(appCopyright)}</string>")
                        appendLine("    <key>NSLocalNetworkUsageDescription</key>")
                        appendLine("    <string>${escapePlistXml(helperLocalNetDesc)}</string>")
                        if (bonjourServices.isNotEmpty()) {
                            appendLine("    <key>NSBonjourServices</key>")
                            appendLine("    <array>")
                            for (svc in bonjourServices) {
                                appendLine("        <string>${escapePlistXml(svc)}</string>")
                            }
                            appendLine("    </array>")
                        }
                        appendLine("</dict>")
                        appendLine("</plist>")
                    },
                    Charsets.UTF_8
                )
            } else {
                val targetSecondary = File(macOsDir, secName)
                secondaryExe.copyTo(targetSecondary, overwrite = true)
                targetSecondary.setExecutable(true, false)
            }
        }

        // 3. Info.plist
        val desktopInfoPlist = File(buildDir, "generated/genericui/desktop-resources/macos/Info.plist")
        val resourceCompilerInfoPlist = File(buildDir, "generated/genericui/resources/backends/macos/Info.plist")
        val targetInfoPlist = File(contentsDir, "Info.plist")
        when {
            desktopInfoPlist.isFile -> desktopInfoPlist.copyTo(targetInfoPlist, overwrite = true)
            resourceCompilerInfoPlist.isFile -> resourceCompilerInfoPlist.copyTo(targetInfoPlist, overwrite = true)
            else -> {
                val mainLocalNetDesc = localNetDesc
                    ?: if (bonjourServices.isNotEmpty()) "$appName accesses devices on your local network." else null
                targetInfoPlist.writeText(
                    buildString {
                        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                        appendLine("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">")
                        appendLine("<plist version=\"1.0\">")
                        appendLine("<dict>")
                        appendLine("    <key>CFBundleName</key>")
                        appendLine("    <string>${escapePlistXml(appName)}</string>")
                        appendLine("    <key>CFBundleDisplayName</key>")
                        appendLine("    <string>${escapePlistXml(appName)}</string>")
                        appendLine("    <key>CFBundleIdentifier</key>")
                        appendLine("    <string>${escapePlistXml(appId)}</string>")
                        appendLine("    <key>CFBundleVersion</key>")
                        appendLine("    <string>${escapePlistXml(macosBundleVersion)}</string>")
                        appendLine("    <key>CFBundleShortVersionString</key>")
                        appendLine("    <string>${escapePlistXml(appVersion)}</string>")
                        if (gitHash.isNotEmpty()) {
                            appendLine("    <key>GenericUIGitCommit</key>")
                            appendLine("    <string>${escapePlistXml(gitHash)}</string>")
                        }
                        appendLine("    <key>CFBundleExecutable</key>")
                        appendLine("    <string>${escapePlistXml(bundleExecutableName)}</string>")
                        appendLine("    <key>CFBundleIconFile</key>")
                        appendLine("    <string>icon.icns</string>")
                        appendLine("    <key>CFBundlePackageType</key>")
                        appendLine("    <string>APPL</string>")
                        appendLine("    <key>LSMinimumSystemVersion</key>")
                        appendLine("    <string>${escapePlistXml(macMinSystemVersion)}</string>")
                        appendLine("    <key>LSApplicationCategoryType</key>")
                        appendLine("    <string>${escapePlistXml(macCategory)}</string>")
                        appendLine("    <key>LSMultipleInstancesProhibited</key>")
                        appendLine("    <${if (multipleInstancesProhibited) "true" else "false"}/>")
                        appendLine("    <key>NSHighResolutionCapable</key>")
                        appendLine("    <true/>")
                        appendLine("    <key>NSHumanReadableCopyright</key>")
                        appendLine("    <string>${escapePlistXml(appCopyright)}</string>")
                        if (mainLocalNetDesc != null) {
                            appendLine("    <key>NSLocalNetworkUsageDescription</key>")
                            appendLine("    <string>${escapePlistXml(mainLocalNetDesc)}</string>")
                        }
                        if (bonjourServices.isNotEmpty()) {
                            appendLine("    <key>NSBonjourServices</key>")
                            appendLine("    <array>")
                            for (svc in bonjourServices) {
                                appendLine("        <string>${escapePlistXml(svc)}</string>")
                            }
                            appendLine("    </array>")
                        }
                        appendLine("</dict>")
                        appendLine("</plist>")
                    },
                    Charsets.UTF_8
                )
            }
        }

        // 4. GenericUI & extension dynamic libraries + plugins in Contents/Frameworks/
        val activeFrameworkDylibNames = buildSet {
            for (binDir in candidateBinDirs) {
                if (!binDir.isDirectory) continue
                binDir.listFiles()?.forEach { f ->
                    val fPath = f.toPath()
                    if ((Files.isSymbolicLink(fPath) || Files.isRegularFile(fPath, LinkOption.NOFOLLOW_LINKS)) &&
                        f.extension.equals("dylib", ignoreCase = true) &&
                        !isExcludedStaticCoreLibrary(f)
                    ) {
                        add(f.name)
                    }
                }
            }
        }
        frameworksDir.listFiles()?.forEach { existing ->
            val existingPath = existing.toPath()
            if ((Files.isSymbolicLink(existingPath) || Files.isRegularFile(existingPath, LinkOption.NOFOLLOW_LINKS)) &&
                GenericUiBuildNativeSharedLibsTask.isManagedSharedLibraryFileName(existing.name) &&
                existing.name !in activeFrameworkDylibNames
            ) {
                runCatching { Files.deleteIfExists(existingPath) }
            }
        }
        for (binDir in candidateBinDirs) {
            if (!binDir.isDirectory) continue
            val sortedEntries = binDir.listFiles()?.sortedWith(
                compareBy<File> { if (Files.isSymbolicLink(it.toPath())) 1 else 0 }
                    .thenBy { it.name }
            ).orEmpty()
            for (f in sortedEntries) {
                if ((!f.extension.equals("dylib", ignoreCase = true) &&
                        !f.name.endsWith(".framework", ignoreCase = true)) ||
                    isExcludedStaticCoreLibrary(f)
                ) {
                    continue
                }
                GenericUiSymlinkPreservingCopy.copyEntryPreservingSymlinks(
                    src = f,
                    dst = File(frameworksDir, f.name),
                    sourceRoot = binDir,
                    setExecutableOnBinaries = true,
                    entryFilter = { !isExcludedStaticCoreLibrary(it) }
                )
            }
        }

        if (mediaEnabled) {
            val vlcMacDir = File(buildDir, "generated/genericui/nativeBundle/vlc/macos")
            if (vlcMacDir.isDirectory) {
                copyFilteredDirectoryContents(vlcMacDir, frameworksDir, setExecutableOnBinaries = true)
                val opusFixSrc = File(vlcMacDir, "opus_fix_plugins")
                if (opusFixSrc.isDirectory) {
                    for (binDir in candidateBinDirs) {
                        if (binDir.exists() || binDir.name.contains("Executable") || binDir.name.contains("Test")) {
                            copyFilteredDirectoryContents(
                                opusFixSrc,
                                File(binDir, "opus_fix_plugins"),
                                setExecutableOnBinaries = true
                            )
                        }
                    }
                }
            }
        }
        if (gpuEnabled) {
            val gpuMacDir = File(buildDir, "generated/genericui/nativeBundle/gpu/macos")
            if (gpuMacDir.isDirectory) {
                GenericUiSymlinkPreservingCopy.copyDirectoryContentsPreservingSymlinks(
                    sourceDir = gpuMacDir,
                    targetDir = frameworksDir,
                    sourceRoot = gpuMacDir,
                    setExecutableOnBinaries = true,
                    entryFilter = { it.name != "MoltenVK_icd.json" && !isExcludedStaticCoreLibrary(it) }
                )
            }
        }

        // 6. Resources (icon.icns + optional vulkan/icd.d/MoltenVK_icd.json ONLY; runtime resources live in Mach-O sections)
        val desktopIcns = File(buildDir, "generated/genericui/desktop-resources/macos/icon.icns")
        if (desktopIcns.isFile) {
            desktopIcns.copyTo(File(resourcesDir, "icon.icns"), overwrite = true)
            for (helperBundle in stagedHelperBundles) {
                if (helperBundle.bundleDir.isDirectory) {
                    val helperResDir = File(helperBundle.bundleDir, "Contents/Resources").apply { mkdirs() }
                    desktopIcns.copyTo(File(helperResDir, "icon.icns"), overwrite = true)
                }
            }
        }
        listOf("gui", "app", "res", "Assets.xcassets", "genericui_app_resources.c", "genericui_app_resources.o").forEach { staleName ->
            File(resourcesDir, staleName).takeIf { it.exists() }?.deleteRecursively()
        }
        if (gpuEnabled) {
            val moltenVkIcd = File(buildDir, "generated/genericui/nativeBundle/gpu/macos/MoltenVK_icd.json")
            if (moltenVkIcd.isFile) {
                val icdTarget = File(resourcesDir, "vulkan/icd.d/MoltenVK_icd.json")
                icdTarget.parentFile?.mkdirs()
                moltenVkIcd.copyTo(icdTarget, overwrite = true)
                for (helperBundle in stagedHelperBundles) {
                    if (helperBundle.bundleDir.isDirectory) {
                        val helperIcdTarget = File(helperBundle.bundleDir, "Contents/Resources/vulkan/icd.d/MoltenVK_icd.json")
                        helperIcdTarget.parentFile?.mkdirs()
                        moltenVkIcd.copyTo(helperIcdTarget, overwrite = true)
                    }
                }
            }
        }

        purgeExcludedStaticCoreLibraries(appBundleDir)
        purgeExcludedStaticCoreLibraries(distDir)
        distDir.listFiles()?.forEach { loose ->
            if (loose.isFile && (loose.extension.equals("kexe", ignoreCase = true) || loose.extension.equals("dylib", ignoreCase = true))) {
                runCatching { loose.delete() }
            }
        }
        val nowMs = System.currentTimeMillis()
        runCatching { appBundleDir.setLastModified(nowMs) }
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        if (osName.contains("mac") || osName.contains("darwin")) {
            if ((primaryExe != null && primaryExe.isFile) || stagedHelperBundles.isNotEmpty()) {
                val (regularBinaries, frameworkBundles) = GenericUiSymlinkPreservingCopy.collectMacCodeSignTargets(frameworksDir)
                for (dylib in regularBinaries) {
                    GenericUiBuildNativeSharedLibsTask.signMacBinary(dylib)
                }
                for (frameworkBundle in frameworkBundles) {
                    GenericUiBuildNativeSharedLibsTask.signMacBinary(frameworkBundle)
                }
                for (helperBundle in stagedHelperBundles) {
                    val stagedHelperExe = helperBundle.executableFile
                    if (stagedHelperExe.isFile) {
                        if (isMachOBinary(stagedHelperExe)) {
                            ensureMacBinaryHasRpath(stagedHelperExe, "@executable_path/../../../../Frameworks")
                        }
                        GenericUiBuildNativeSharedLibsTask.signMacBinary(
                            stagedHelperExe,
                            identifier = helperBundle.bundleIdentifier
                        )
                        if (helperBundle.bundleDir.isDirectory) {
                            GenericUiBuildNativeSharedLibsTask.signMacBinary(
                                helperBundle.bundleDir,
                                identifier = helperBundle.bundleIdentifier
                            )
                        }
                    }
                }
                if (primaryExe != null && primaryExe.isFile) {
                    macOsDir.listFiles()?.sortedBy { it.name }?.forEach { macExe ->
                        if (macExe.isFile && !Files.isSymbolicLink(macExe.toPath())) {
                            GenericUiBuildNativeSharedLibsTask.signMacBinary(macExe)
                        }
                    }
                    GenericUiBuildNativeSharedLibsTask.signMacBinary(appBundleDir)
                }
            }
            val lsregister = File("/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister")
            if (lsregister.isFile && lsregister.canExecute()) {
                runCatching {
                    val proc = ProcessBuilder(lsregister.absolutePath, "-f", appBundleDir.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
        }
    }

    private fun isMachOBinary(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        val magic = ByteArray(4)
        file.inputStream().use { input ->
            if (input.read(magic) != 4) return false
        }
        val b0 = magic[0].toInt() and 0xFF
        val b1 = magic[1].toInt() and 0xFF
        val b2 = magic[2].toInt() and 0xFF
        val b3 = magic[3].toInt() and 0xFF
        val be = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        val le = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
        return be == 0xFEEDFACE.toInt() ||
            be == 0xFEEDFACF.toInt() ||
            be == 0xCAFEBABE.toInt() ||
            le == 0xFEEDFACE.toInt() ||
            le == 0xFEEDFACF.toInt() ||
            le == 0xCAFEBABE.toInt()
    }

    private fun ensureMacBinaryHasRpath(binaryFile: File, rpath: String) {
        val existingRpaths = runCatching {
            val otoolProc = ProcessBuilder("otool", "-l", binaryFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val otoolOut = otoolProc.inputStream.bufferedReader(Charsets.UTF_8).readText()
            otoolProc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            otoolOut
        }.getOrDefault("")
        if (existingRpaths.contains(rpath)) {
            return
        }
        runCatching {
            val proc = ProcessBuilder("install_name_tool", "-add_rpath", rpath, binaryFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
            proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    private fun escapePlistXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun writeMinimalMachOArm64Executable(target: File) {
        target.parentFile?.mkdirs()
        val buf = java.nio.ByteBuffer.allocate(4096).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0, 0xFEEDFACF.toInt())
        buf.putInt(4, 0x0100000C)
        buf.putInt(8, 0)
        buf.putInt(12, 2)
        buf.putInt(16, 1)
        buf.putInt(20, 72)
        buf.putInt(24, 0x00200085)
        buf.putInt(28, 0)
        buf.putInt(32, 0x19)
        buf.putInt(36, 72)
        "__TEXT".toByteArray(Charsets.US_ASCII).forEachIndexed { idx, b -> buf.put(40 + idx, b) }
        buf.putLong(56, 0x100000000L)
        buf.putLong(64, 4096L)
        buf.putLong(72, 0L)
        buf.putLong(80, 4096L)
        buf.putInt(88, 5)
        buf.putInt(92, 5)
        buf.putInt(96, 0)
        buf.putInt(100, 0)
        buf.putInt(256, 0x52800000)
        buf.putInt(260, 0xD65F03C0.toInt())
        target.writeBytes(buf.array())
        target.setExecutable(true, false)
    }

    private fun stageLinuxNativeExtensionBundles(
        buildDir: File,
        distDir: File,
        candidateBinDirs: List<File>,
        mode: String,
        mediaEnabled: Boolean,
        gpuEnabled: Boolean,
        legacySecondaryBinaryNames: Set<String> = emptySet()
    ) {
        val destinations = buildList {
            add(distDir)
            candidateBinDirs.filter { it.exists() }.forEach { add(it) }
        }.distinct()

        if (mediaEnabled) {
            val vlcLinuxDir = File(buildDir, "generated/genericui/nativeBundle/vlc/linux")
            if (vlcLinuxDir.isDirectory) {
                for (dest in destinations) {
                    copyFilteredDirectoryContents(vlcLinuxDir, dest, setExecutableOnBinaries = true)
                }
            }
        }

        if (gpuEnabled) {
            val gpuLinuxDir = File(buildDir, "generated/genericui/nativeBundle/gpu/linux")
            if (gpuLinuxDir.isDirectory) {
                for (dest in destinations) {
                    copyFilteredDirectoryContents(gpuLinuxDir, dest, setExecutableOnBinaries = true)
                }
            }
        }

        distDir.listFiles()?.forEach { f ->
            val p = f.toPath()
            if (!Files.isSymbolicLink(p) &&
                Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) &&
                (f.extension == "kexe" || f.extension.isEmpty() || f.name.contains(".so"))
            ) {
                runCatching { f.setExecutable(true, false) }
            }
        }

        for (dest in destinations) {
            purgeExcludedStaticCoreLibraries(dest)
            listOf("gui", "app", "res").forEach { staleDir ->
                File(dest, staleDir).takeIf { it.exists() }?.deleteRecursively()
            }
            for (legacyName in legacySecondaryBinaryNames) {
                File(dest, "$legacyName.kexe").takeIf { it.exists() }?.delete()
                File(dest, legacyName).takeIf { it.exists() }?.delete()
            }
            dest.listFiles()?.forEach { f ->
                if (f.isFile && f.name.lowercase().endsWith(".dll.a")) {
                    runCatching { f.delete() }
                }
            }
        }
    }

    private fun expandDesktopCandidateTasks(
        project: Project,
        candidateTaskNames: List<String>,
        secondaryExecutables: List<com.nexustools.genericui.plugin.spi.SecondaryDesktopExecutableContribution>
    ): List<String> {
        if (secondaryExecutables.isEmpty()) return candidateTaskNames
        val linkPattern = Regex("^link(Gtk|Kde)?(Debug|Release)Executable(.+)$")
        val hasToolkitSpecificPrimary = candidateTaskNames.any {
            it.startsWith("linkGtk") || it.startsWith("linkKde")
        }
        val expanded = mutableListOf<String>()
        for (taskName in candidateTaskNames) {
            expanded.add(taskName)
            val match = linkPattern.matchEntire(taskName) ?: continue
            val toolkitPrefix = match.groupValues[1]
            val mode = match.groupValues[2]
            val targetSuffix = match.groupValues[3]
            for (sec in secondaryExecutables) {
                val safeCap = sanitizeBinaryNamePrefix(sec.binaryBaseName)
                    .replaceFirstChar { it.uppercaseChar() }
                if (toolkitPrefix.isNotEmpty()) {
                    val toolkitTask = "link$toolkitPrefix$safeCap${mode}Executable$targetSuffix"
                    val fallbackTask = "link$safeCap${mode}Executable$targetSuffix"
                    if (project.tasks.findByName(toolkitTask) != null) {
                        expanded.add(toolkitTask)
                    } else if (project.tasks.findByName(fallbackTask) != null) {
                        expanded.add(fallbackTask)
                    }
                } else {
                    val fallbackTask = "link$safeCap${mode}Executable$targetSuffix"
                    val gtkTask = "linkGtk$safeCap${mode}Executable$targetSuffix"
                    val kdeTask = "linkKde$safeCap${mode}Executable$targetSuffix"
                    if (!hasToolkitSpecificPrimary ||
                        (project.tasks.findByName(gtkTask) == null && project.tasks.findByName(kdeTask) == null)
                    ) {
                        expanded.add(fallbackTask)
                    }
                }
            }
        }
        return expanded.distinct()
    }

    private fun expandDesktopCandidateSourceDirs(
        candidateSourceDirs: List<String>,
        secondaryExecutables: List<com.nexustools.genericui.plugin.spi.SecondaryDesktopExecutableContribution>
    ): List<String> {
        if (secondaryExecutables.isEmpty()) return candidateSourceDirs
        val dirPattern = Regex("^bin/([^/]+)/(gtk|kde)?(Debug|Release|debug|release)Executable$")
        val extraDirs = mutableListOf<String>()
        for (relDir in candidateSourceDirs) {
            val match = dirPattern.matchEntire(relDir) ?: continue
            val targetName = match.groupValues[1]
            val toolkit = match.groupValues[2]
            val modeCap = match.groupValues[3].replaceFirstChar { it.uppercaseChar() }
            for (sec in secondaryExecutables) {
                val safePrefix = sanitizeBinaryNamePrefix(sec.binaryBaseName)
                val secDir = if (toolkit.isEmpty()) {
                    "bin/$targetName/$safePrefix${modeCap}Executable"
                } else {
                    val safeCap = safePrefix.replaceFirstChar { it.uppercaseChar() }
                    "bin/$targetName/$toolkit$safeCap${modeCap}Executable"
                }
                extraDirs.add(secDir)
            }
        }
        return (candidateSourceDirs + extraDirs).distinct()
    }

    fun registerBuildTargetTasks(project: Project, extension: GenericUiExtension) {
        ensureExtensionNativeBundleTasksRegistered(project, extension)

        fun registerTargetTask(
            name: String,
            desc: String,
            distRelativePath: String,
            candidateTaskNames: List<String>,
            candidateSourceDirs: List<String> = emptyList()
        ): TaskProvider<*> {
            val existing = project.tasks.findByName(name)
            if (existing != null) {
                return project.tasks.named(name)
            }
            val buildDirProvider = project.layout.buildDirectory.asFile
            return project.tasks.register(name) {
                group = GenericUiGradlePlugin.TASK_GROUP
                description = desc
                extensions.extraProperties.set("genericUiDistRelativePath", distRelativePath)
                dependsOn(project.provider {
                    val isDesktopDist = distRelativePath.startsWith("dist/windows/") ||
                        distRelativePath.startsWith("dist/mac/") ||
                        distRelativePath.startsWith("dist/linux-")
                    val secondaryExes = if (isDesktopDist && extension.application.createExecutableBinaries) {
                        collectDesktopContextAndSecondaryExecutables(project, extension).second
                    } else {
                        emptyList()
                    }
                    val effectiveCandidateTasks = if (isDesktopDist) {
                        expandDesktopCandidateTasks(project, candidateTaskNames, secondaryExes)
                    } else {
                        candidateTaskNames
                    }
                    val dynamicExtensionTasks = buildList {
                        if (isDesktopDist) {
                            add("prepareGenericUiDesktopResources")
                            if (isMediaExtensionEnabled(extension)) {
                                add("bundleGenericUiVlc")
                            }
                            if (isGpuExtensionEnabled(extension)) {
                                add("bundleGenericUiAngle")
                                add("bundleGenericUiVulkan")
                            }
                        }
                    }
                    val baseTasks = (
                        listOf(
                            "genericuiValidateApp",
                            "generateGenericUiLaunchers",
                            "compileGenericUiResources"
                        ) + dynamicExtensionTasks + effectiveCandidateTasks
                    ).distinct().mapNotNull { taskName -> project.tasks.findByName(taskName) }
                    val depBuildTasks = resolveCompanionDependencyBuildTasks(project, extension)
                    baseTasks + depBuildTasks
                })
                doLast {
                    val buildDir = buildDirProvider.get()
                    val distDir = File(buildDir, distRelativePath)
                    distDir.mkdirs()
                    val isDesktopDist = distRelativePath.startsWith("dist/windows/") ||
                        distRelativePath.startsWith("dist/mac/") ||
                        distRelativePath.startsWith("dist/linux-")
                    val secondaryExes = if (isDesktopDist && extension.application.createExecutableBinaries) {
                        collectDesktopContextAndSecondaryExecutables(project, extension).second
                    } else {
                        emptyList()
                    }
                    val resolvedMainBaseName = extension.application.baseName.trim().ifEmpty { project.name }
                    val protectedPrimaryNames = setOf(
                        resolvedMainBaseName,
                        "$resolvedMainBaseName-gtk",
                        "$resolvedMainBaseName-kde"
                    )
                    val secondaryBaseNames = secondaryExes.map { it.binaryBaseName }.toSet()
                    val legacySecondaryBinaryNames = secondaryExes
                        .flatMap { it.legacyBinaryBaseNames }
                        .filter { it.isNotBlank() && it !in secondaryBaseNames && it !in protectedPrimaryNames }
                        .toSet()
                    val expandedSourceDirs = if (isDesktopDist) {
                        expandDesktopCandidateSourceDirs(candidateSourceDirs, secondaryExes)
                    } else {
                        candidateSourceDirs
                    }
                    val isMacDist = distRelativePath.startsWith("dist/mac/")
                    val isLinuxGtkDist = distRelativePath.startsWith("dist/linux-gtk/")
                    val isLinuxKdeDist = distRelativePath.startsWith("dist/linux-kde/")
                    val excludedExtensions = setOf("svg", "png", "ico", "icns", "rc", "res", "desktop", "plist", "cur")
                    val excludedNames = setOf("androidmanifest.xml", "info.plist", "cached-libraries.txt")
                    fun isExcludedNonWebDistFile(file: File): Boolean {
                        val lowerName = file.name.lowercase()
                        if (lowerName.contains(".exe.old") || lowerName.endsWith(".dll.a") || lowerName in excludedNames) {
                            return true
                        }
                        if (isExcludedStaticCoreLibrary(file)) return true
                        val baseOrFull = when {
                            lowerName.endsWith(".exe") -> lowerName.dropLast(4)
                            lowerName.endsWith(".kexe") -> lowerName.dropLast(5)
                            else -> lowerName
                        }
                        if (legacySecondaryBinaryNames.any { it.equals(baseOrFull, ignoreCase = true) }) {
                            return true
                        }
                        if (isLinuxKdeDist && (lowerName == "libgenericui-gtk.so" || lowerName.endsWith("-gtk.kexe"))) {
                            return true
                        }
                        if (isLinuxGtkDist && (lowerName == "libgenericui-kde.so" || lowerName.endsWith("-kde.kexe"))) {
                            return true
                        }
                        if (file.isFile && file.extension.lowercase() in excludedExtensions) return true
                        return false
                    }
                    distDir.listFiles()?.forEach { existing ->
                        if (isExcludedNonWebDistFile(existing) ||
                            (isMacDist && (existing.extension.equals("kexe", ignoreCase = true) ||
                                existing.extension.equals("dylib", ignoreCase = true) ||
                                existing.name.endsWith(".dSYM", ignoreCase = true)))
                        ) {
                            runCatching { GenericUiSymlinkPreservingCopy.deletePathNoFollow(existing.toPath()) }
                        }
                    }
                    val resolvedSourceDirs = when {
                        isLinuxKdeDist -> {
                            val kdeOnly = expandedSourceDirs.filter { it.contains("/kde") }
                            if (kdeOnly.any { File(buildDir, it).let { d -> d.isDirectory && !d.list().isNullOrEmpty() } }) {
                                kdeOnly
                            } else {
                                expandedSourceDirs
                            }
                        }
                        isLinuxGtkDist -> {
                            val gtkOnly = expandedSourceDirs.filter { it.contains("/gtk") }
                            if (gtkOnly.any { File(buildDir, it).let { d -> d.isDirectory && !d.list().isNullOrEmpty() } }) {
                                gtkOnly
                            } else {
                                expandedSourceDirs
                            }
                        }
                        else -> expandedSourceDirs
                    }
                    if (!isMacDist) {
                        val activeManagedNames = buildSet {
                            for (relSrc in resolvedSourceDirs) {
                                val srcDir = File(buildDir, relSrc)
                                if (!srcDir.exists() || !srcDir.isDirectory) continue
                                srcDir.listFiles()?.forEach { f ->
                                    val fp = f.toPath()
                                    if ((Files.isSymbolicLink(fp) || Files.isRegularFile(fp, LinkOption.NOFOLLOW_LINKS)) &&
                                        GenericUiBuildNativeSharedLibsTask.isManagedSharedLibraryFileName(f.name)
                                    ) {
                                        add(f.name)
                                    }
                                }
                            }
                        }
                        distDir.listFiles()?.forEach { existing ->
                            val ep = existing.toPath()
                            if ((Files.isSymbolicLink(ep) || Files.isRegularFile(ep, LinkOption.NOFOLLOW_LINKS)) &&
                                GenericUiBuildNativeSharedLibsTask.isManagedSharedLibraryFileName(existing.name) &&
                                existing.name !in activeManagedNames
                            ) {
                                runCatching { GenericUiSymlinkPreservingCopy.deletePathNoFollow(ep) }
                            }
                        }
                        for (relSrc in resolvedSourceDirs) {
                            val srcDir = File(buildDir, relSrc)
                            if (!srcDir.exists() || !srcDir.isDirectory) continue
                            val sortedChildren = srcDir.listFiles()?.sortedWith(
                                compareBy<File> { if (Files.isSymbolicLink(it.toPath())) 1 else 0 }
                                    .thenBy { it.name }
                            ).orEmpty()
                            for (child in sortedChildren) {
                                if (isExcludedNonWebDistFile(child)) continue
                                val target = File(distDir, child.name)
                                runCatching {
                                    GenericUiSymlinkPreservingCopy.copyEntryPreservingSymlinks(
                                        src = child,
                                        dst = target,
                                        sourceRoot = srcDir,
                                        setExecutableOnBinaries = isLinuxGtkDist || isLinuxKdeDist,
                                        entryFilter = { !isExcludedNonWebDistFile(it) }
                                    )
                                }
                            }
                        }
                        if (isLinuxGtkDist || isLinuxKdeDist) {
                            for (sec in secondaryExes) {
                                val kexeFile = File(distDir, "${sec.binaryBaseName}.kexe")
                                val aliasFile = File(distDir, sec.binaryBaseName)
                                if (kexeFile.isFile) {
                                    kexeFile.copyTo(aliasFile, overwrite = true)
                                    aliasFile.setExecutable(true, false)
                                }
                            }
                        }
                    }

                    val mode = if (distRelativePath.endsWith("/production")) "production" else "development"
                    val candidateBinDirs = resolvedSourceDirs.map { File(buildDir, it) }
                    val mediaEnabled = isMediaExtensionEnabled(extension)
                    val gpuEnabled = isGpuExtensionEnabled(extension)

                    when {
                        distRelativePath.startsWith("dist/windows/") -> {
                            stageWindowsNativeExtensionBundles(
                                buildDir = buildDir,
                                distDir = distDir,
                                candidateBinDirs = candidateBinDirs,
                                mode = mode,
                                mediaEnabled = mediaEnabled,
                                gpuEnabled = gpuEnabled,
                                legacySecondaryBinaryNames = legacySecondaryBinaryNames
                            )
                        }
                        distRelativePath.startsWith("dist/mac/") -> {
                            assembleMacosAppBundle(
                                project = project,
                                extension = extension,
                                buildDir = buildDir,
                                distDir = distDir,
                                candidateBinDirs = candidateBinDirs,
                                mode = mode,
                                mediaEnabled = mediaEnabled,
                                gpuEnabled = gpuEnabled,
                                secondaryExes = secondaryExes
                            )
                        }
                        distRelativePath.startsWith("dist/linux-gtk/") || distRelativePath.startsWith("dist/linux-kde/") -> {
                            stageLinuxNativeExtensionBundles(
                                buildDir = buildDir,
                                distDir = distDir,
                                candidateBinDirs = candidateBinDirs,
                                mode = mode,
                                mediaEnabled = mediaEnabled,
                                gpuEnabled = gpuEnabled,
                                legacySecondaryBinaryNames = legacySecondaryBinaryNames
                            )
                        }
                        else -> {
                            purgeExcludedStaticCoreLibraries(distDir)
                        }
                    }
                }
            }
        }

        fun registerTestTargetTask(
            name: String,
            desc: String,
            candidateTaskNames: List<String>
        ): TaskProvider<*> {
            val existing = project.tasks.findByName(name)
            if (existing != null) {
                return project.tasks.named(name)
            }
            return project.tasks.register(name) {
                group = "genericui test"
                description = desc
                dependsOn(project.provider {
                    val baseTasks = (
                        listOf(
                            "generateGenericUiLaunchers",
                            "compileGenericUiResources"
                        ) + candidateTaskNames
                    ).distinct().mapNotNull { taskName -> project.tasks.findByName(taskName) }
                    val depBuildTasks = resolveCompanionDependencyBuildTasks(project, extension)
                    baseTasks + depBuildTasks
                })
            }
        }

        val backends = extension.backends

        // 1. Windows (X32, X64, Arm64 + Host Aggregate)
        if (backends.windowsEnabled) {
            val winX64DevCandidates = listOf(
                "prepareGenericUiDesktopResources",
                "linkDebugExecutableMingwX64",
                "compileKotlinMingwX64",
                "bundleGenericUiAngle",
                "bundleGenericUiVulkan"
            )
            val winX64ProdCandidates = listOf(
                "prepareGenericUiDesktopResources",
                "linkReleaseExecutableMingwX64",
                "compileKotlinMingwX64",
                "bundleGenericUiAngle",
                "bundleGenericUiVulkan"
            )
            val winX64DevSources = listOf(
                "bin/mingwX64/debugExecutable"
            )
            val winX64ProdSources = listOf(
                "bin/mingwX64/releaseExecutable"
            )
            registerTargetTask(
                name = "developmentWindows",
                desc = "Builds the Windows (Win32 / Direct2D / ANGLE / Vulkan) Development target into build/dist/windows/x64/development.",
                distRelativePath = "dist/windows/x64/development",
                candidateTaskNames = winX64DevCandidates,
                candidateSourceDirs = winX64DevSources
            )
            registerTargetTask(
                name = "productionWindows",
                desc = "Builds the Windows (Win32 / Direct2D / ANGLE / Vulkan) Production target into build/dist/windows/x64/production.",
                distRelativePath = "dist/windows/x64/production",
                candidateTaskNames = winX64ProdCandidates,
                candidateSourceDirs = winX64ProdSources
            )
            registerTargetTask(
                name = "developmentWindowsX64",
                desc = "Builds the Windows x64 (Win32 / Direct2D / ANGLE / Vulkan) Development target into build/dist/windows/x64/development.",
                distRelativePath = "dist/windows/x64/development",
                candidateTaskNames = winX64DevCandidates,
                candidateSourceDirs = winX64DevSources
            )
            registerTargetTask(
                name = "productionWindowsX64",
                desc = "Builds the Windows x64 (Win32 / Direct2D / ANGLE / Vulkan) Production target into build/dist/windows/x64/production.",
                distRelativePath = "dist/windows/x64/production",
                candidateTaskNames = winX64ProdCandidates,
                candidateSourceDirs = winX64ProdSources
            )
            registerTargetTask(
                name = "developmentWindowsX32",
                desc = "Builds the Windows x32 (Win32 / Direct2D / ANGLE / Vulkan) Development target into build/dist/windows/x32/development.",
                distRelativePath = "dist/windows/x32/development",
                candidateTaskNames = listOf(
                    "prepareGenericUiDesktopResources",
                    "linkDebugExecutableWindowsX32",
                    "linkDebugExecutableMingwX86",
                    "compileKotlinWindowsX32",
                    "compileKotlinMingwX86",
                    "bundleGenericUiAngle",
                    "bundleGenericUiVulkan"
                ),
                candidateSourceDirs = listOf(
                    "bin/windowsX32/debugExecutable",
                    "bin/mingwX86/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionWindowsX32",
                desc = "Builds the Windows x32 (Win32 / Direct2D / ANGLE / Vulkan) Production target into build/dist/windows/x32/production.",
                distRelativePath = "dist/windows/x32/production",
                candidateTaskNames = listOf(
                    "prepareGenericUiDesktopResources",
                    "linkReleaseExecutableWindowsX32",
                    "linkReleaseExecutableMingwX86",
                    "compileKotlinWindowsX32",
                    "compileKotlinMingwX86",
                    "bundleGenericUiAngle",
                    "bundleGenericUiVulkan"
                ),
                candidateSourceDirs = listOf(
                    "bin/windowsX32/releaseExecutable",
                    "bin/mingwX86/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentWindowsArm64",
                desc = "Builds the Windows Arm64 (Win32 / Direct2D / ANGLE / Vulkan) Development target into build/dist/windows/arm64/development.",
                distRelativePath = "dist/windows/arm64/development",
                candidateTaskNames = listOf(
                    "prepareGenericUiDesktopResources",
                    "linkDebugExecutableWindowsArm64",
                    "linkDebugExecutableMingwArm64",
                    "compileKotlinWindowsArm64",
                    "compileKotlinMingwArm64",
                    "bundleGenericUiAngle",
                    "bundleGenericUiVulkan"
                ),
                candidateSourceDirs = listOf(
                    "bin/windowsArm64/debugExecutable",
                    "bin/mingwArm64/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionWindowsArm64",
                desc = "Builds the Windows Arm64 (Win32 / Direct2D / ANGLE / Vulkan) Production target into build/dist/windows/arm64/production.",
                distRelativePath = "dist/windows/arm64/production",
                candidateTaskNames = listOf(
                    "prepareGenericUiDesktopResources",
                    "linkReleaseExecutableWindowsArm64",
                    "linkReleaseExecutableMingwArm64",
                    "compileKotlinWindowsArm64",
                    "compileKotlinMingwArm64",
                    "bundleGenericUiAngle",
                    "bundleGenericUiVulkan"
                ),
                candidateSourceDirs = listOf(
                    "bin/windowsArm64/releaseExecutable",
                    "bin/mingwArm64/releaseExecutable"
                )
            )

            val isWindowsHost = System.getProperty("os.name").orEmpty().lowercase().contains("win")
            val buildDirProvider = project.layout.buildDirectory.asFile
            project.tasks.matching {
                (it.name.startsWith("link") && it.name.endsWith("ExecutableMingwX64")) ||
                    it.name in setOf(
                        "linkDebugTestMingwX64",
                        "mingwX64Test",
                        "windowsVisualTest"
                    )
            }.configureEach {
                dependsOn(project.provider {
                    buildList {
                        add("prepareGenericUiDesktopResources")
                        if (isMediaExtensionEnabled(extension)) add("bundleGenericUiVlc")
                        if (isGpuExtensionEnabled(extension)) {
                            add("bundleGenericUiAngle")
                            add("bundleGenericUiVulkan")
                        }
                    }.mapNotNull { taskName -> project.tasks.findByName(taskName) }
                })
                doFirst {
                    if (name == "mingwX64Test" || name == "windowsVisualTest") {
                        val bDir = buildDirProvider.get()
                        val debugExeDir = File(bDir, "bin/mingwX64/debugExecutable")
                        val debugTestDir = File(bDir, "bin/mingwX64/debugTest")
                        stageWindowsNativeExtensionBundles(
                            buildDir = bDir,
                            distDir = debugTestDir,
                            candidateBinDirs = listOf(debugExeDir, debugTestDir),
                            mode = "development",
                            mediaEnabled = isMediaExtensionEnabled(extension),
                            gpuEnabled = isGpuExtensionEnabled(extension)
                        )
                    }
                }
                doLast {
                    if (name.startsWith("link")) {
                        val bDir = buildDirProvider.get()
                        val isRelease = name.contains("Release")
                        val modeStr = if (isRelease) "production" else "development"
                        val destProp = invokeNoArg(this, "getDestinationDirectory") as? org.gradle.api.file.DirectoryProperty
                        val targetBinDir = destProp?.orNull?.asFile ?: when {
                            name.contains("Test") -> File(bDir, "bin/mingwX64/debugTest")
                            isRelease -> File(bDir, "bin/mingwX64/releaseExecutable")
                            else -> File(bDir, "bin/mingwX64/debugExecutable")
                        }
                        stageWindowsNativeExtensionBundles(
                            buildDir = bDir,
                            distDir = targetBinDir,
                            candidateBinDirs = listOf(targetBinDir),
                            mode = modeStr,
                            mediaEnabled = isMediaExtensionEnabled(extension),
                            gpuEnabled = isGpuExtensionEnabled(extension)
                        )
                    }
                }
            }
            val winX64TestCandidates = buildList {
                add("compileTestKotlinMingwX64")
                add("linkDebugTestMingwX64")
                if (isWindowsHost) {
                    add("mingwX64Test")
                    if (extension.testing.visualOnWindows) {
                        add("windowsVisualTest")
                    }
                }
            }
            registerTestTargetTask(
                name = "testWindowsX64",
                desc = "Runs Windows x64 tests (mingwX64Test / windowsVisualTest).",
                candidateTaskNames = winX64TestCandidates
            )
            registerTestTargetTask(
                name = "testWindowsX32",
                desc = "Verifies Windows x32 test compilation and readiness.",
                candidateTaskNames = listOf(
                    "mingwX86Test",
                    "windowsX32Test",
                    "compileTestKotlinMingwX86",
                    "compileTestKotlinWindowsX32",
                    "compileTestKotlinMingwX64",
                    "developmentWindowsX32"
                )
            )
            registerTestTargetTask(
                name = "testWindowsArm64",
                desc = "Verifies Windows Arm64 test compilation and readiness.",
                candidateTaskNames = listOf(
                    "mingwArm64Test",
                    "windowsArm64Test",
                    "compileTestKotlinMingwArm64",
                    "compileTestKotlinWindowsArm64",
                    "compileTestKotlinMingwX64",
                    "developmentWindowsArm64"
                )
            )
            registerTestTargetTask(
                name = "testWindows",
                desc = "Runs the Windows test suite (delegates to testWindowsX64).",
                candidateTaskNames = listOf("testWindowsX64")
            )
        }

        // 2. macOS (Apple Silicon Arm64 Only — macosX64 Removed)
        if (backends.macosEnabled) {
            val macArm64DevCandidates = listOf(
                "prepareGenericUiDesktopResources",
                "linkDebugExecutableMacosArm64",
                "compileKotlinMacosArm64",
                "bundleGenericUiVulkan"
            )
            val macArm64ProdCandidates = listOf(
                "prepareGenericUiDesktopResources",
                "linkReleaseExecutableMacosArm64",
                "compileKotlinMacosArm64",
                "bundleGenericUiVulkan"
            )
            val macDevSources = listOf(
                "bin/macosArm64/debugExecutable"
            )
            val macProdSources = listOf(
                "bin/macosArm64/releaseExecutable"
            )
            registerTargetTask(
                name = "developmentMac",
                desc = "Builds the macOS (AppKit / MoltenVK) Development target into build/dist/mac/development.",
                distRelativePath = "dist/mac/development",
                candidateTaskNames = macArm64DevCandidates,
                candidateSourceDirs = macDevSources
            )
            registerTargetTask(
                name = "productionMac",
                desc = "Builds the macOS (AppKit / MoltenVK) Production target into build/dist/mac/production.",
                distRelativePath = "dist/mac/production",
                candidateTaskNames = macArm64ProdCandidates,
                candidateSourceDirs = macProdSources
            )
            registerTargetTask(
                name = "developmentMacArm64",
                desc = "Builds the macOS Arm64 (AppKit / MoltenVK) Development target into build/dist/mac/development.",
                distRelativePath = "dist/mac/development",
                candidateTaskNames = macArm64DevCandidates,
                candidateSourceDirs = macDevSources
            )
            registerTargetTask(
                name = "productionMacArm64",
                desc = "Builds the macOS Arm64 (AppKit / MoltenVK) Production target into build/dist/mac/production.",
                distRelativePath = "dist/mac/production",
                candidateTaskNames = macArm64ProdCandidates,
                candidateSourceDirs = macProdSources
            )

            val isMacHost = System.getProperty("os.name").orEmpty().lowercase().let {
                it.contains("mac") || it.contains("darwin")
            }
            val macBuildDirProvider = project.layout.buildDirectory.asFile
            project.tasks.matching {
                (it.name.startsWith("link") && it.name.endsWith("ExecutableMacosArm64")) ||
                    it.name in setOf(
                        "linkDebugTestMacosArm64",
                        "macosArm64Test"
                    )
            }.configureEach {
                dependsOn(project.provider {
                    buildList {
                        add("prepareGenericUiDesktopResources")
                        if (isMediaExtensionEnabled(extension)) add("bundleGenericUiVlc")
                    }.mapNotNull { taskName -> project.tasks.findByName(taskName) }
                })
                doFirst {
                    if (name == "macosArm64Test" && isMediaExtensionEnabled(extension)) {
                        val bDir = macBuildDirProvider.get()
                        val opusFixSrc = File(bDir, "generated/genericui/nativeBundle/vlc/macos/opus_fix_plugins")
                        val debugTestDir = File(bDir, "bin/macosArm64/debugTest")
                        if (opusFixSrc.isDirectory && debugTestDir.isDirectory) {
                            copyFilteredDirectoryContents(
                                opusFixSrc,
                                File(debugTestDir, "opus_fix_plugins"),
                                setExecutableOnBinaries = true
                            )
                        }
                    }
                }
                doLast {
                    if (name.startsWith("link") && isMediaExtensionEnabled(extension)) {
                        val bDir = macBuildDirProvider.get()
                        val opusFixSrc = File(bDir, "generated/genericui/nativeBundle/vlc/macos/opus_fix_plugins")
                        if (opusFixSrc.isDirectory) {
                            val targetBinDir = when {
                                name.contains("Test") -> File(bDir, "bin/macosArm64/debugTest")
                                name.contains("Release") -> File(bDir, "bin/macosArm64/releaseExecutable")
                                else -> File(bDir, "bin/macosArm64/debugExecutable")
                            }
                            if (targetBinDir.isDirectory) {
                                copyFilteredDirectoryContents(
                                    opusFixSrc,
                                    File(targetBinDir, "opus_fix_plugins"),
                                    setExecutableOnBinaries = true
                                )
                            }
                        }
                    }
                }
            }
            registerTestTargetTask(
                name = "testMacArm64",
                desc = "Runs macOS Arm64 tests (macosArm64Test on macOS hosts; compiles/links test binary otherwise).",
                candidateTaskNames = buildList {
                    add("compileTestKotlinMacosArm64")
                    add("linkDebugTestMacosArm64")
                    if (isMacHost) add("macosArm64Test")
                }
            )
            registerTestTargetTask(
                name = "testMacosArm64",
                desc = "Runs macOS Arm64 tests (alias for testMacArm64).",
                candidateTaskNames = listOf("testMacArm64")
            )
            registerTestTargetTask(
                name = "testMac",
                desc = "Runs the macOS test suite (delegates to testMacArm64).",
                candidateTaskNames = listOf("testMacArm64")
            )
            registerTestTargetTask(
                name = "testMacos",
                desc = "Runs the macOS test suite (delegates to testMac).",
                candidateTaskNames = listOf("testMac")
            )
        }

        // 3. Linux GTK (X32, X64, Arm32, Arm64 + Aggregate)
        if (backends.gtkEnabled) {
            registerTargetTask(
                name = "developmentLinuxGtk",
                desc = "Builds the Linux GTK (GTK4 / Cairo / EGL / Vulkan) Development target into build/dist/linux-gtk/x64/development.",
                distRelativePath = "dist/linux-gtk/x64/development",
                candidateTaskNames = listOf(
                    "linkGtkDebugExecutableLinuxX64",
                    "linkGtkDebugExecutableLinuxArm64",
                    "compileGtkKotlinLinuxX64",
                    "compileGtkKotlinLinuxArm64",
                    "linkDebugExecutableLinuxX64",
                    "compileKotlinLinuxX64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX64/gtkDebugExecutable",
                    "bin/linuxX64/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxGtk",
                desc = "Builds the Linux GTK (GTK4 / Cairo / EGL / Vulkan) Production target into build/dist/linux-gtk/x64/production.",
                distRelativePath = "dist/linux-gtk/x64/production",
                candidateTaskNames = listOf(
                    "linkGtkReleaseExecutableLinuxX64",
                    "linkGtkReleaseExecutableLinuxArm64",
                    "compileGtkKotlinLinuxX64",
                    "compileGtkKotlinLinuxArm64",
                    "linkReleaseExecutableLinuxX64",
                    "compileKotlinLinuxX64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX64/gtkReleaseExecutable",
                    "bin/linuxX64/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentLinuxGtkX32",
                desc = "Builds the Linux GTK x32 (GTK4 / Cairo / EGL / Vulkan) Development target into build/dist/linux-gtk/x32/development.",
                distRelativePath = "dist/linux-gtk/x32/development",
                candidateTaskNames = listOf(
                    "linkGtkDebugExecutableLinuxX32",
                    "compileGtkKotlinLinuxX32",
                    "linkDebugExecutableLinuxX32",
                    "compileKotlinLinuxX32"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX32/gtkDebugExecutable",
                    "bin/linuxX32/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxGtkX32",
                desc = "Builds the Linux GTK x32 (GTK4 / Cairo / EGL / Vulkan) Production target into build/dist/linux-gtk/x32/production.",
                distRelativePath = "dist/linux-gtk/x32/production",
                candidateTaskNames = listOf(
                    "linkGtkReleaseExecutableLinuxX32",
                    "compileGtkKotlinLinuxX32",
                    "linkReleaseExecutableLinuxX32",
                    "compileKotlinLinuxX32"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX32/gtkReleaseExecutable",
                    "bin/linuxX32/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentLinuxGtkX64",
                desc = "Builds the Linux GTK x64 (GTK4 / Cairo / EGL / Vulkan) Development target into build/dist/linux-gtk/x64/development.",
                distRelativePath = "dist/linux-gtk/x64/development",
                candidateTaskNames = listOf(
                    "linkGtkDebugExecutableLinuxX64",
                    "compileGtkKotlinLinuxX64",
                    "linkDebugExecutableLinuxX64",
                    "compileKotlinLinuxX64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX64/gtkDebugExecutable",
                    "bin/linuxX64/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxGtkX64",
                desc = "Builds the Linux GTK x64 (GTK4 / Cairo / EGL / Vulkan) Production target into build/dist/linux-gtk/x64/production.",
                distRelativePath = "dist/linux-gtk/x64/production",
                candidateTaskNames = listOf(
                    "linkGtkReleaseExecutableLinuxX64",
                    "compileGtkKotlinLinuxX64",
                    "linkReleaseExecutableLinuxX64",
                    "compileKotlinLinuxX64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX64/gtkReleaseExecutable",
                    "bin/linuxX64/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentLinuxGtkArm32",
                desc = "Builds the Linux GTK Arm32 (GTK4 / Cairo / EGL / Vulkan) Development target into build/dist/linux-gtk/arm32/development.",
                distRelativePath = "dist/linux-gtk/arm32/development",
                candidateTaskNames = listOf(
                    "linkGtkDebugExecutableLinuxArm32Hfp",
                    "compileGtkKotlinLinuxArm32Hfp",
                    "linkDebugExecutableLinuxArm32Hfp",
                    "compileKotlinLinuxArm32Hfp"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxArm32Hfp/gtkDebugExecutable",
                    "bin/linuxArm32Hfp/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxGtkArm32",
                desc = "Builds the Linux GTK Arm32 (GTK4 / Cairo / EGL / Vulkan) Production target into build/dist/linux-gtk/arm32/production.",
                distRelativePath = "dist/linux-gtk/arm32/production",
                candidateTaskNames = listOf(
                    "linkGtkReleaseExecutableLinuxArm32Hfp",
                    "compileGtkKotlinLinuxArm32Hfp",
                    "linkReleaseExecutableLinuxArm32Hfp",
                    "compileKotlinLinuxArm32Hfp"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxArm32Hfp/gtkReleaseExecutable",
                    "bin/linuxArm32Hfp/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentLinuxGtkArm64",
                desc = "Builds the Linux GTK Arm64 (GTK4 / Cairo / EGL / Vulkan) Development target into build/dist/linux-gtk/arm64/development.",
                distRelativePath = "dist/linux-gtk/arm64/development",
                candidateTaskNames = listOf(
                    "linkGtkDebugExecutableLinuxArm64",
                    "compileGtkKotlinLinuxArm64",
                    "linkDebugExecutableLinuxArm64",
                    "compileKotlinLinuxArm64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxArm64/gtkDebugExecutable",
                    "bin/linuxArm64/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxGtkArm64",
                desc = "Builds the Linux GTK Arm64 (GTK4 / Cairo / EGL / Vulkan) Production target into build/dist/linux-gtk/arm64/production.",
                distRelativePath = "dist/linux-gtk/arm64/production",
                candidateTaskNames = listOf(
                    "linkGtkReleaseExecutableLinuxArm64",
                    "compileGtkKotlinLinuxArm64",
                    "linkReleaseExecutableLinuxArm64",
                    "compileKotlinLinuxArm64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxArm64/gtkReleaseExecutable",
                    "bin/linuxArm64/releaseExecutable"
                )
            )

            val isLinuxHost = System.getProperty("os.name").orEmpty().lowercase().contains("linux")
            registerTestTargetTask(
                name = "testLinuxGtkX64",
                desc = "Runs Linux GTK x64 tests (linuxX64Test on Linux hosts; compiles/links test binary otherwise).",
                candidateTaskNames = buildList {
                    add("compileTestKotlinLinuxX64")
                    add("linkDebugTestLinuxX64")
                    if (isLinuxHost) add("linuxX64Test")
                }
            )
            registerTestTargetTask(
                name = "testLinuxGtkArm64",
                desc = "Runs Linux GTK Arm64 tests.",
                candidateTaskNames = listOf(
                    "compileTestKotlinLinuxArm64",
                    "linkDebugTestLinuxArm64",
                    "linuxArm64Test"
                )
            )
            registerTestTargetTask(
                name = "testLinuxGtkX32",
                desc = "Verifies Linux GTK x32 test compilation and readiness.",
                candidateTaskNames = listOf(
                    "compileTestKotlinLinuxX32",
                    "compileTestKotlinLinuxX64",
                    "developmentLinuxGtkX32"
                )
            )
            registerTestTargetTask(
                name = "testLinuxGtkArm32",
                desc = "Verifies Linux GTK Arm32 test compilation and readiness.",
                candidateTaskNames = listOf(
                    "compileTestKotlinLinuxArm32Hfp",
                    "compileTestKotlinLinuxArm64",
                    "developmentLinuxGtkArm32"
                )
            )
            registerTestTargetTask(
                name = "testLinuxGtk",
                desc = "Runs the Linux GTK test suite (delegates to testLinuxGtkX64).",
                candidateTaskNames = listOf("testLinuxGtkX64")
            )
            registerTestTargetTask(
                name = "testGtk",
                desc = "Runs the Linux GTK test suite (delegates to testLinuxGtk).",
                candidateTaskNames = listOf("testLinuxGtk")
            )
        }

        // 4. Linux KDE (X32, X64, Arm32, Arm64 + Aggregate)
        if (backends.kdeEnabled) {
            registerTargetTask(
                name = "developmentLinuxKde",
                desc = "Builds the Linux KDE (KF6 / Kirigami / QPainter / QVulkanWindow) Development target into build/dist/linux-kde/x64/development.",
                distRelativePath = "dist/linux-kde/x64/development",
                candidateTaskNames = listOf(
                    "linkKdeDebugExecutableLinuxX64",
                    "linkKdeDebugExecutableLinuxArm64",
                    "compileKdeKotlinLinuxX64",
                    "compileKdeKotlinLinuxArm64",
                    "linkDebugExecutableLinuxX64",
                    "compileKotlinLinuxX64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX64/kdeDebugExecutable",
                    "bin/linuxX64/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxKde",
                desc = "Builds the Linux KDE (KF6 / Kirigami / QPainter / QVulkanWindow) Production target into build/dist/linux-kde/x64/production.",
                distRelativePath = "dist/linux-kde/x64/production",
                candidateTaskNames = listOf(
                    "linkKdeReleaseExecutableLinuxX64",
                    "linkKdeReleaseExecutableLinuxArm64",
                    "compileKdeKotlinLinuxX64",
                    "compileKdeKotlinLinuxArm64",
                    "linkReleaseExecutableLinuxX64",
                    "compileKotlinLinuxX64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX64/kdeReleaseExecutable",
                    "bin/linuxX64/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentLinuxKdeX32",
                desc = "Builds the Linux KDE x32 (KF6 / Kirigami / QPainter / QVulkanWindow) Development target into build/dist/linux-kde/x32/development.",
                distRelativePath = "dist/linux-kde/x32/development",
                candidateTaskNames = listOf(
                    "linkKdeDebugExecutableLinuxX32",
                    "compileKdeKotlinLinuxX32",
                    "linkDebugExecutableLinuxX32",
                    "compileKotlinLinuxX32"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX32/kdeDebugExecutable",
                    "bin/linuxX32/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxKdeX32",
                desc = "Builds the Linux KDE x32 (KF6 / Kirigami / QPainter / QVulkanWindow) Production target into build/dist/linux-kde/x32/production.",
                distRelativePath = "dist/linux-kde/x32/production",
                candidateTaskNames = listOf(
                    "linkKdeReleaseExecutableLinuxX32",
                    "compileKdeKotlinLinuxX32",
                    "linkReleaseExecutableLinuxX32",
                    "compileKotlinLinuxX32"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX32/kdeReleaseExecutable",
                    "bin/linuxX32/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentLinuxKdeX64",
                desc = "Builds the Linux KDE x64 (KF6 / Kirigami / QPainter / QVulkanWindow) Development target into build/dist/linux-kde/x64/development.",
                distRelativePath = "dist/linux-kde/x64/development",
                candidateTaskNames = listOf(
                    "linkKdeDebugExecutableLinuxX64",
                    "compileKdeKotlinLinuxX64",
                    "linkDebugExecutableLinuxX64",
                    "compileKotlinLinuxX64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX64/kdeDebugExecutable",
                    "bin/linuxX64/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxKdeX64",
                desc = "Builds the Linux KDE x64 (KF6 / Kirigami / QPainter / QVulkanWindow) Production target into build/dist/linux-kde/x64/production.",
                distRelativePath = "dist/linux-kde/x64/production",
                candidateTaskNames = listOf(
                    "linkKdeReleaseExecutableLinuxX64",
                    "compileKdeKotlinLinuxX64",
                    "linkReleaseExecutableLinuxX64",
                    "compileKotlinLinuxX64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX64/kdeReleaseExecutable",
                    "bin/linuxX64/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentLinuxKdeArm32",
                desc = "Builds the Linux KDE Arm32 (KF6 / Kirigami / QPainter / QVulkanWindow) Development target into build/dist/linux-kde/arm32/development.",
                distRelativePath = "dist/linux-kde/arm32/development",
                candidateTaskNames = listOf(
                    "linkKdeDebugExecutableLinuxArm32Hfp",
                    "compileKdeKotlinLinuxArm32Hfp",
                    "linkDebugExecutableLinuxArm32Hfp",
                    "compileKotlinLinuxArm32Hfp"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxArm32Hfp/kdeDebugExecutable",
                    "bin/linuxArm32Hfp/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxKdeArm32",
                desc = "Builds the Linux KDE Arm32 (KF6 / Kirigami / QPainter / QVulkanWindow) Production target into build/dist/linux-kde/arm32/production.",
                distRelativePath = "dist/linux-kde/arm32/production",
                candidateTaskNames = listOf(
                    "linkKdeReleaseExecutableLinuxArm32Hfp",
                    "compileKdeKotlinLinuxArm32Hfp",
                    "linkReleaseExecutableLinuxArm32Hfp",
                    "compileKotlinLinuxArm32Hfp"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxArm32Hfp/kdeReleaseExecutable",
                    "bin/linuxArm32Hfp/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentLinuxKdeArm64",
                desc = "Builds the Linux KDE Arm64 (KF6 / Kirigami / QPainter / QVulkanWindow) Development target into build/dist/linux-kde/arm64/development.",
                distRelativePath = "dist/linux-kde/arm64/development",
                candidateTaskNames = listOf(
                    "linkKdeDebugExecutableLinuxArm64",
                    "compileKdeKotlinLinuxArm64",
                    "linkDebugExecutableLinuxArm64",
                    "compileKotlinLinuxArm64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxArm64/kdeDebugExecutable",
                    "bin/linuxArm64/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxKdeArm64",
                desc = "Builds the Linux KDE Arm64 (KF6 / Kirigami / QPainter / QVulkanWindow) Production target into build/dist/linux-kde/arm64/production.",
                distRelativePath = "dist/linux-kde/arm64/production",
                candidateTaskNames = listOf(
                    "linkKdeReleaseExecutableLinuxArm64",
                    "compileKdeKotlinLinuxArm64",
                    "linkReleaseExecutableLinuxArm64",
                    "compileKotlinLinuxArm64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxArm64/kdeReleaseExecutable",
                    "bin/linuxArm64/releaseExecutable"
                )
            )

            val isLinuxHost = System.getProperty("os.name").orEmpty().lowercase().contains("linux")
            registerTestTargetTask(
                name = "testLinuxKdeX64",
                desc = "Runs Linux KDE x64 tests (linuxX64Test on Linux hosts; compiles/links test binary otherwise).",
                candidateTaskNames = buildList {
                    add("compileTestKotlinLinuxX64")
                    add("linkDebugTestLinuxX64")
                    if (isLinuxHost) add("linuxX64Test")
                }
            )
            registerTestTargetTask(
                name = "testLinuxKdeArm64",
                desc = "Runs Linux KDE Arm64 tests.",
                candidateTaskNames = listOf(
                    "compileTestKotlinLinuxArm64",
                    "linkDebugTestLinuxArm64",
                    "linuxArm64Test"
                )
            )
            registerTestTargetTask(
                name = "testLinuxKdeX32",
                desc = "Verifies Linux KDE x32 test compilation and readiness.",
                candidateTaskNames = listOf(
                    "compileTestKotlinLinuxX32",
                    "compileTestKotlinLinuxX64",
                    "developmentLinuxKdeX32"
                )
            )
            registerTestTargetTask(
                name = "testLinuxKdeArm32",
                desc = "Verifies Linux KDE Arm32 test compilation and readiness.",
                candidateTaskNames = listOf(
                    "compileTestKotlinLinuxArm32Hfp",
                    "compileTestKotlinLinuxArm64",
                    "developmentLinuxKdeArm32"
                )
            )
            registerTestTargetTask(
                name = "testLinuxKde",
                desc = "Runs the Linux KDE test suite (delegates to testLinuxKdeX64).",
                candidateTaskNames = listOf("testLinuxKdeX64")
            )
            registerTestTargetTask(
                name = "testKde",
                desc = "Runs the Linux KDE test suite (delegates to testLinuxKde).",
                candidateTaskNames = listOf("testLinuxKde")
            )
        }

        if (backends.gtkEnabled || backends.kdeEnabled) {
            registerTestTargetTask(
                name = "testLinux",
                desc = "Runs all enabled Linux backend test suites (GTK and/or KDE).",
                candidateTaskNames = buildList {
                    if (backends.gtkEnabled) add("testLinuxGtk")
                    if (backends.kdeEnabled) add("testLinuxKde")
                }
            )
        }

        // 5. Android (Pure Java/DEX Universal APK + CPU-Specific Kotlin/Native Targets X32, X64, Arm32, Arm64)
        if (backends.androidEnabled) {
            registerTargetTask(
                name = "developmentAndroid",
                desc = "Builds the Android (Pure Java/Kotlin DEX universal APK) Development target into build/dist/android/dex/development.",
                distRelativePath = "dist/android/dex/development",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "compileAndroidMain",
                    "bundleAndroidMainClassesToRuntimeJar",
                    "assembleDebug",
                    "compileDebugKotlinAndroid",
                    "packageGenericUiAndroidDevelopmentApk"
                ),
                candidateSourceDirs = emptyList()
            )
            registerTargetTask(
                name = "productionAndroid",
                desc = "Builds the Android (Pure Java/Kotlin DEX universal APK) Production target into build/dist/android/dex/production.",
                distRelativePath = "dist/android/dex/production",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "compileAndroidMain",
                    "bundleAndroidMainClassesToRuntimeJar",
                    "assembleRelease",
                    "compileReleaseKotlinAndroid",
                    "packageGenericUiAndroidProductionApk"
                ),
                candidateSourceDirs = emptyList()
            )
            registerTargetTask(
                name = "developmentAndroidDex",
                desc = "Builds the Android DEX (Pure Java/Kotlin universal APK) Development target into build/dist/android/dex/development.",
                distRelativePath = "dist/android/dex/development",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "compileAndroidMain",
                    "bundleAndroidMainClassesToRuntimeJar",
                    "assembleDebug",
                    "compileDebugKotlinAndroid",
                    "packageGenericUiAndroidDevelopmentApk"
                ),
                candidateSourceDirs = emptyList()
            )
            registerTargetTask(
                name = "productionAndroidDex",
                desc = "Builds the Android DEX (Pure Java/Kotlin universal APK) Production target into build/dist/android/dex/production.",
                distRelativePath = "dist/android/dex/production",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "compileAndroidMain",
                    "bundleAndroidMainClassesToRuntimeJar",
                    "assembleRelease",
                    "compileReleaseKotlinAndroid",
                    "packageGenericUiAndroidProductionApk"
                ),
                candidateSourceDirs = emptyList()
            )
            registerTargetTask(
                name = "developmentAndroidX32",
                desc = "Builds the Android x32 (Kotlin/Native AndroidNativeX86 + APK) Development target into build/dist/android/x32/development.",
                distRelativePath = "dist/android/x32/development",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "linkDebugSharedAndroidNativeX86",
                    "linkDebugExecutableAndroidNativeX86",
                    "compileKotlinAndroidNativeX86",
                    "compileAndroidMain",
                    "bundleAndroidMainClassesToRuntimeJar",
                    "assembleDebug",
                    "packageGenericUiAndroidX32DevelopmentApk"
                ),
                candidateSourceDirs = listOf(
                    "bin/androidNativeX86/debugShared",
                    "bin/androidNativeX86/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionAndroidX32",
                desc = "Builds the Android x32 (Kotlin/Native AndroidNativeX86 + APK) Production target into build/dist/android/x32/production.",
                distRelativePath = "dist/android/x32/production",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "linkReleaseSharedAndroidNativeX86",
                    "linkReleaseExecutableAndroidNativeX86",
                    "compileKotlinAndroidNativeX86",
                    "compileAndroidMain",
                    "bundleAndroidMainClassesToRuntimeJar",
                    "assembleRelease",
                    "packageGenericUiAndroidX32ProductionApk"
                ),
                candidateSourceDirs = listOf(
                    "bin/androidNativeX86/releaseShared",
                    "bin/androidNativeX86/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentAndroidX64",
                desc = "Builds the Android x64 (Kotlin/Native AndroidNativeX64 + APK) Development target into build/dist/android/x64/development.",
                distRelativePath = "dist/android/x64/development",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "linkDebugSharedAndroidNativeX64",
                    "linkDebugExecutableAndroidNativeX64",
                    "compileKotlinAndroidNativeX64",
                    "compileAndroidMain",
                    "bundleAndroidMainClassesToRuntimeJar",
                    "assembleDebug",
                    "packageGenericUiAndroidX64DevelopmentApk"
                ),
                candidateSourceDirs = listOf(
                    "bin/androidNativeX64/debugShared",
                    "bin/androidNativeX64/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionAndroidX64",
                desc = "Builds the Android x64 (Kotlin/Native AndroidNativeX64 + APK) Production target into build/dist/android/x64/production.",
                distRelativePath = "dist/android/x64/production",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "linkReleaseSharedAndroidNativeX64",
                    "linkReleaseExecutableAndroidNativeX64",
                    "compileKotlinAndroidNativeX64",
                    "compileAndroidMain",
                    "bundleAndroidMainClassesToRuntimeJar",
                    "assembleRelease",
                    "packageGenericUiAndroidX64ProductionApk"
                ),
                candidateSourceDirs = listOf(
                    "bin/androidNativeX64/releaseShared",
                    "bin/androidNativeX64/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentAndroidArm32",
                desc = "Builds the Android Arm32 (Kotlin/Native AndroidNativeArm32 + APK) Development target into build/dist/android/arm32/development.",
                distRelativePath = "dist/android/arm32/development",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "linkDebugSharedAndroidNativeArm32",
                    "linkDebugExecutableAndroidNativeArm32",
                    "compileKotlinAndroidNativeArm32",
                    "compileAndroidMain",
                    "bundleAndroidMainClassesToRuntimeJar",
                    "assembleDebug",
                    "packageGenericUiAndroidArm32DevelopmentApk"
                ),
                candidateSourceDirs = listOf(
                    "bin/androidNativeArm32/debugShared",
                    "bin/androidNativeArm32/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionAndroidArm32",
                desc = "Builds the Android Arm32 (Kotlin/Native AndroidNativeArm32 + APK) Production target into build/dist/android/arm32/production.",
                distRelativePath = "dist/android/arm32/production",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "linkReleaseSharedAndroidNativeArm32",
                    "linkReleaseExecutableAndroidNativeArm32",
                    "compileKotlinAndroidNativeArm32",
                    "compileAndroidMain",
                    "bundleAndroidMainClassesToRuntimeJar",
                    "assembleRelease",
                    "packageGenericUiAndroidArm32ProductionApk"
                ),
                candidateSourceDirs = listOf(
                    "bin/androidNativeArm32/releaseShared",
                    "bin/androidNativeArm32/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentAndroidArm64",
                desc = "Builds the Android Arm64 (Kotlin/Native AndroidNativeArm64 + APK) Development target into build/dist/android/arm64/development.",
                distRelativePath = "dist/android/arm64/development",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "linkDebugSharedAndroidNativeArm64",
                    "linkDebugExecutableAndroidNativeArm64",
                    "compileKotlinAndroidNativeArm64",
                    "compileAndroidMain",
                    "bundleAndroidMainClassesToRuntimeJar",
                    "assembleDebug",
                    "packageGenericUiAndroidArm64DevelopmentApk"
                ),
                candidateSourceDirs = listOf(
                    "bin/androidNativeArm64/debugShared",
                    "bin/androidNativeArm64/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionAndroidArm64",
                desc = "Builds the Android Arm64 (Kotlin/Native AndroidNativeArm64 + APK) Production target into build/dist/android/arm64/production.",
                distRelativePath = "dist/android/arm64/production",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "linkReleaseSharedAndroidNativeArm64",
                    "linkReleaseExecutableAndroidNativeArm64",
                    "compileKotlinAndroidNativeArm64",
                    "compileAndroidMain",
                    "bundleAndroidMainClassesToRuntimeJar",
                    "assembleRelease",
                    "packageGenericUiAndroidArm64ProductionApk"
                ),
                candidateSourceDirs = listOf(
                    "bin/androidNativeArm64/releaseShared",
                    "bin/androidNativeArm64/releaseExecutable"
                )
            )

            registerTestTargetTask(
                name = "testAndroidDex",
                desc = "Runs Android DEX / Robolectric JVM unit tests.",
                candidateTaskNames = listOf(
                    "generateGenericUiAndroidManifest",
                    "generateGenericUiAndroidActivities",
                    "testDebugUnitTest",
                    "testAndroidHostTest",
                    "compileDebugUnitTestKotlinAndroid",
                    "compileAndroidMain"
                )
            )
            registerTestTargetTask(
                name = "testAndroidX32",
                desc = "Runs Android x32 tests.",
                candidateTaskNames = listOf(
                    "androidNativeX86Test",
                    "compileTestKotlinAndroidNativeX86",
                    "testAndroidDex"
                )
            )
            registerTestTargetTask(
                name = "testAndroidX64",
                desc = "Runs Android x64 tests.",
                candidateTaskNames = listOf(
                    "androidNativeX64Test",
                    "compileTestKotlinAndroidNativeX64",
                    "testAndroidDex"
                )
            )
            registerTestTargetTask(
                name = "testAndroidArm32",
                desc = "Runs Android Arm32 tests.",
                candidateTaskNames = listOf(
                    "androidNativeArm32Test",
                    "compileTestKotlinAndroidNativeArm32",
                    "testAndroidDex"
                )
            )
            registerTestTargetTask(
                name = "testAndroidArm64",
                desc = "Runs Android Arm64 tests.",
                candidateTaskNames = listOf(
                    "androidNativeArm64Test",
                    "compileTestKotlinAndroidNativeArm64",
                    "testAndroidDex"
                )
            )
            if (project.plugins.hasPlugin("com.android.kotlin.multiplatform.library") && "testAndroid" !in project.tasks.names) {
                project.tasks.matching { it.name == "testAndroid" }.configureEach {
                    group = "genericui test"
                    description = "Runs the Android test suite (delegates to testAndroidDex)."
                    dependsOn("testAndroidDex")
                }
            } else {
                registerTestTargetTask(
                    name = "testAndroid",
                    desc = "Runs the Android test suite (delegates to testAndroidDex).",
                    candidateTaskNames = listOf("testAndroidDex")
                )
            }
        }

        // 6. iOS (Arm64 Device & SimulatorArm64 Apple Silicon Simulator + Aggregate — iosX64 Removed)
        if (backends.iosEnabled) {
            registerTargetTask(
                name = "developmentIos",
                desc = "Builds the iOS (UIKit / MoltenVK) Development target into build/dist/ios/arm64/development.",
                distRelativePath = "dist/ios/arm64/development",
                candidateTaskNames = listOf(
                    "generateGenericUiIosApp",
                    "linkDebugFrameworkIosSimulatorArm64",
                    "linkDebugFrameworkIosArm64",
                    "compileKotlinIosSimulatorArm64",
                    "compileKotlinIosArm64"
                ),
                candidateSourceDirs = listOf(
                    "bin/iosArm64/debugFramework",
                    "genericui/iosApp"
                )
            )
            registerTargetTask(
                name = "productionIos",
                desc = "Builds the iOS (UIKit / MoltenVK) Production target into build/dist/ios/arm64/production.",
                distRelativePath = "dist/ios/arm64/production",
                candidateTaskNames = listOf(
                    "generateGenericUiIosApp",
                    "linkReleaseFrameworkIosArm64",
                    "compileKotlinIosArm64"
                ),
                candidateSourceDirs = listOf(
                    "bin/iosArm64/releaseFramework",
                    "genericui/iosApp"
                )
            )
            registerTargetTask(
                name = "developmentIosArm64",
                desc = "Builds the iOS Arm64 Device (UIKit / MoltenVK) Development target into build/dist/ios/arm64/development.",
                distRelativePath = "dist/ios/arm64/development",
                candidateTaskNames = listOf(
                    "generateGenericUiIosApp",
                    "linkDebugFrameworkIosArm64",
                    "compileKotlinIosArm64"
                ),
                candidateSourceDirs = listOf(
                    "bin/iosArm64/debugFramework",
                    "genericui/iosApp"
                )
            )
            registerTargetTask(
                name = "productionIosArm64",
                desc = "Builds the iOS Arm64 Device (UIKit / MoltenVK) Production target into build/dist/ios/arm64/production.",
                distRelativePath = "dist/ios/arm64/production",
                candidateTaskNames = listOf(
                    "generateGenericUiIosApp",
                    "linkReleaseFrameworkIosArm64",
                    "compileKotlinIosArm64"
                ),
                candidateSourceDirs = listOf(
                    "bin/iosArm64/releaseFramework",
                    "genericui/iosApp"
                )
            )
            registerTargetTask(
                name = "developmentIosSimulatorArm64",
                desc = "Builds the iOS Simulator Arm64 (UIKit / MoltenVK) Development target into build/dist/ios/simulatorArm64/development.",
                distRelativePath = "dist/ios/simulatorArm64/development",
                candidateTaskNames = listOf(
                    "generateGenericUiIosApp",
                    "linkDebugFrameworkIosSimulatorArm64",
                    "compileKotlinIosSimulatorArm64"
                ),
                candidateSourceDirs = listOf(
                    "bin/iosSimulatorArm64/debugFramework",
                    "genericui/iosApp"
                )
            )
            registerTargetTask(
                name = "productionIosSimulatorArm64",
                desc = "Builds the iOS Simulator Arm64 (UIKit / MoltenVK) Production target into build/dist/ios/simulatorArm64/production.",
                distRelativePath = "dist/ios/simulatorArm64/production",
                candidateTaskNames = listOf(
                    "generateGenericUiIosApp",
                    "linkReleaseFrameworkIosSimulatorArm64",
                    "compileKotlinIosSimulatorArm64"
                ),
                candidateSourceDirs = listOf(
                    "bin/iosSimulatorArm64/releaseFramework",
                    "genericui/iosApp"
                )
            )

            val isMacHost = System.getProperty("os.name").orEmpty().lowercase().let {
                it.contains("mac") || it.contains("darwin")
            }
            registerTestTargetTask(
                name = "testIosSimulatorArm64",
                desc = "Runs iOS Simulator Arm64 tests (iosSimulatorArm64Test on macOS hosts; compiles/links test binary otherwise).",
                candidateTaskNames = buildList {
                    add("compileTestKotlinIosSimulatorArm64")
                    add("linkDebugTestIosSimulatorArm64")
                    if (isMacHost) add("iosSimulatorArm64Test")
                }
            )
            registerTestTargetTask(
                name = "testIosArm64",
                desc = "Compiles and links iOS Arm64 device test binaries.",
                candidateTaskNames = listOf(
                    "compileTestKotlinIosArm64",
                    "linkDebugTestIosArm64"
                )
            )
            registerTestTargetTask(
                name = "testIos",
                desc = "Runs the iOS test suite (delegates to testIosSimulatorArm64).",
                candidateTaskNames = listOf("testIosSimulatorArm64")
            )
        }

        // 6b. iPadOS (Arm64 Device & SimulatorArm64 Apple Silicon Simulator + Aggregate)
        if (backends.ipadosEnabled) {
            val usePrefixedIpadosFramework = backends.iosEnabled
            val debugFwSubdir = if (usePrefixedIpadosFramework) "ipadosDebugFramework" else "debugFramework"
            val releaseFwSubdir = if (usePrefixedIpadosFramework) "ipadosReleaseFramework" else "releaseFramework"
            val linkDebugArm64Task = if (usePrefixedIpadosFramework) "linkIpadosDebugFrameworkIosArm64" else "linkDebugFrameworkIosArm64"
            val linkReleaseArm64Task = if (usePrefixedIpadosFramework) "linkIpadosReleaseFrameworkIosArm64" else "linkReleaseFrameworkIosArm64"
            val linkDebugSimTask = if (usePrefixedIpadosFramework) "linkIpadosDebugFrameworkIosSimulatorArm64" else "linkDebugFrameworkIosSimulatorArm64"
            val linkReleaseSimTask = if (usePrefixedIpadosFramework) "linkIpadosReleaseFrameworkIosSimulatorArm64" else "linkReleaseFrameworkIosSimulatorArm64"
            val compileArm64Task = if (usePrefixedIpadosFramework) "compileIpadosKotlinIosArm64" else "compileKotlinIosArm64"
            val compileSimTask = if (usePrefixedIpadosFramework) "compileIpadosKotlinIosSimulatorArm64" else "compileKotlinIosSimulatorArm64"

            registerTargetTask(
                name = "developmentIpados",
                desc = "Builds the iPadOS (UIKit / MoltenVK) Development target into build/dist/ipados/arm64/development.",
                distRelativePath = "dist/ipados/arm64/development",
                candidateTaskNames = listOf(
                    "generateGenericUiIpadosApp",
                    linkDebugSimTask,
                    linkDebugArm64Task,
                    compileSimTask,
                    compileArm64Task
                ),
                candidateSourceDirs = listOf(
                    "bin/iosArm64/$debugFwSubdir",
                    "genericui/ipadosApp"
                )
            )
            registerTargetTask(
                name = "productionIpados",
                desc = "Builds the iPadOS (UIKit / MoltenVK) Production target into build/dist/ipados/arm64/production.",
                distRelativePath = "dist/ipados/arm64/production",
                candidateTaskNames = listOf(
                    "generateGenericUiIpadosApp",
                    linkReleaseArm64Task,
                    compileArm64Task
                ),
                candidateSourceDirs = listOf(
                    "bin/iosArm64/$releaseFwSubdir",
                    "genericui/ipadosApp"
                )
            )
            registerTargetTask(
                name = "developmentIpadosArm64",
                desc = "Builds the iPadOS Arm64 Device (UIKit / MoltenVK) Development target into build/dist/ipados/arm64/development.",
                distRelativePath = "dist/ipados/arm64/development",
                candidateTaskNames = listOf(
                    "generateGenericUiIpadosApp",
                    linkDebugArm64Task,
                    compileArm64Task
                ),
                candidateSourceDirs = listOf(
                    "bin/iosArm64/$debugFwSubdir",
                    "genericui/ipadosApp"
                )
            )
            registerTargetTask(
                name = "productionIpadosArm64",
                desc = "Builds the iPadOS Arm64 Device (UIKit / MoltenVK) Production target into build/dist/ipados/arm64/production.",
                distRelativePath = "dist/ipados/arm64/production",
                candidateTaskNames = listOf(
                    "generateGenericUiIpadosApp",
                    linkReleaseArm64Task,
                    compileArm64Task
                ),
                candidateSourceDirs = listOf(
                    "bin/iosArm64/$releaseFwSubdir",
                    "genericui/ipadosApp"
                )
            )
            registerTargetTask(
                name = "developmentIpadosSimulatorArm64",
                desc = "Builds the iPadOS Simulator Arm64 (UIKit / MoltenVK) Development target into build/dist/ipados/simulatorArm64/development.",
                distRelativePath = "dist/ipados/simulatorArm64/development",
                candidateTaskNames = listOf(
                    "generateGenericUiIpadosApp",
                    linkDebugSimTask,
                    compileSimTask
                ),
                candidateSourceDirs = listOf(
                    "bin/iosSimulatorArm64/$debugFwSubdir",
                    "genericui/ipadosApp"
                )
            )
            registerTargetTask(
                name = "productionIpadosSimulatorArm64",
                desc = "Builds the iPadOS Simulator Arm64 (UIKit / MoltenVK) Production target into build/dist/ipados/simulatorArm64/production.",
                distRelativePath = "dist/ipados/simulatorArm64/production",
                candidateTaskNames = listOf(
                    "generateGenericUiIpadosApp",
                    linkReleaseSimTask,
                    compileSimTask
                ),
                candidateSourceDirs = listOf(
                    "bin/iosSimulatorArm64/$releaseFwSubdir",
                    "genericui/ipadosApp"
                )
            )

            val isMacHost = System.getProperty("os.name").orEmpty().lowercase().let {
                it.contains("mac") || it.contains("darwin")
            }
            registerTestTargetTask(
                name = "testIpadosSimulatorArm64",
                desc = "Runs iPadOS Simulator Arm64 tests (iosSimulatorArm64Test on macOS hosts; compiles/links test binary otherwise).",
                candidateTaskNames = buildList {
                    add("compileTestKotlinIosSimulatorArm64")
                    add("linkDebugTestIosSimulatorArm64")
                    if (isMacHost) add("iosSimulatorArm64Test")
                }
            )
            registerTestTargetTask(
                name = "testIpadosArm64",
                desc = "Compiles and links iPadOS Arm64 device test binaries.",
                candidateTaskNames = listOf(
                    "compileTestKotlinIosArm64",
                    "linkDebugTestIosArm64"
                )
            )
            registerTestTargetTask(
                name = "testIpados",
                desc = "Runs the iPadOS test suite (delegates to testIpadosSimulatorArm64).",
                candidateTaskNames = listOf("testIpadosSimulatorArm64")
            )
        }

        // 7. Swing & Web (Unified, No Sub-Targets)
        if (backends.swingConfig.enabled) {
            registerTargetTask(
                name = "developmentSwing",
                desc = "Builds the Java Swing (JVM / AWT / LWJGL) Development fat JAR target into build/dist/swing/development.",
                distRelativePath = "dist/swing/development",
                candidateTaskNames = listOf(
                    "prepareGenericUiDesktopResources",
                    "compileKotlinJvm",
                    "jvmProcessResources",
                    "packageGenericUiSwingDevelopmentJar"
                ),
                candidateSourceDirs = emptyList()
            )
            registerTargetTask(
                name = "productionSwing",
                desc = "Builds the Java Swing (JVM / AWT / LWJGL) Production fat JAR target into build/dist/swing/production.",
                distRelativePath = "dist/swing/production",
                candidateTaskNames = listOf(
                    "prepareGenericUiDesktopResources",
                    "compileKotlinJvm",
                    "jvmProcessResources",
                    "jvmJar",
                    "packageGenericUiSwingProductionJar"
                ),
                candidateSourceDirs = emptyList()
            )
            registerTestTargetTask(
                name = "testSwing",
                desc = "Runs the Java Swing (JVM) test suite (jvmTest).",
                candidateTaskNames = listOf(
                    "jvmTest",
                    "compileTestKotlinJvm",
                    "test"
                )
            )
        }

        if (backends.webEnabled) {
            registerTargetTask(
                name = "developmentWeb",
                desc = "Builds the Web (Wasm default + JS IR fallback, HTML5 DOM / Canvas2D / WebGL2 / WebGPU + compiled SASS CSS) Development target into build/dist/web/development.",
                distRelativePath = "dist/web/development",
                candidateTaskNames = listOf(
                    "compileGenericUiSass",
                    "wasmJsProcessResources",
                    "jsProcessResources",
                    "compileDevelopmentExecutableKotlinWasmJs",
                    "wasmJsDevelopmentExecutableCompileSync",
                    "wasmJsBrowserDevelopmentWebpack",
                    "wasmJsBrowserDevelopmentExecutableDistribution",
                    "compileKotlinWasmJs",
                    "compileDevelopmentExecutableKotlinJs",
                    "jsDevelopmentExecutableCompileSync",
                    "jsBrowserDevelopmentWebpack",
                    "jsBrowserDevelopmentExecutableDistribution",
                    "compileKotlinJs",
                    "packageGenericUiWebDevelopmentDistribution"
                )
            )
            registerTargetTask(
                name = "productionWeb",
                desc = "Builds the Web (Wasm default + JS IR fallback, HTML5 DOM / Canvas2D / WebGL2 / WebGPU + compiled SASS CSS) Production distribution target into build/dist/web/production.",
                distRelativePath = "dist/web/production",
                candidateTaskNames = listOf(
                    "compileGenericUiSass",
                    "wasmJsProcessResources",
                    "jsProcessResources",
                    "compileProductionExecutableKotlinWasmJs",
                    "wasmJsProductionExecutableCompileSync",
                    "wasmJsBrowserProductionWebpack",
                    "wasmJsBrowserDistribution",
                    "compileKotlinWasmJs",
                    "compileProductionExecutableKotlinJs",
                    "jsProductionExecutableCompileSync",
                    "jsBrowserProductionWebpack",
                    "jsBrowserDistribution",
                    "compileKotlinJs",
                    "packageGenericUiWebProductionDistribution"
                )
            )

            val webTestDistTask = if (project.tasks.findByName("packageGenericUiWebTestDistribution") != null) {
                project.tasks.named("packageGenericUiWebTestDistribution", GenericUiWebDistributionTask::class.java)
            } else {
                project.tasks.register(
                    "packageGenericUiWebTestDistribution",
                    GenericUiWebDistributionTask::class.java
                ) {
                    GenericUiGradlePlugin.configureWebDistributionTaskInstance(
                        project = project,
                        extension = extension,
                        task = this,
                        variant = "development",
                        outputRelPath = "distributions/web/test"
                    )
                    includeTestScripts.set(true)
                    dependsOn(project.provider {
                        listOf(
                            "generateGenericUiLaunchers",
                            "compileGenericUiResources",
                            "compileGenericUiSass",
                            "wasmJsProcessResources",
                            "jsProcessResources",
                            "compileDevelopmentExecutableKotlinWasmJs",
                            "wasmJsDevelopmentExecutableCompileSync",
                            "compileKotlinWasmJs",
                            "compileDevelopmentExecutableKotlinJs",
                            "jsDevelopmentExecutableCompileSync",
                            "compileKotlinJs",
                            "compileTestDevelopmentExecutableKotlinJs",
                            "jsTestDevelopmentExecutableCompileSync",
                            "compileTestKotlinJs"
                        ).mapNotNull { taskName -> project.tasks.findByName(taskName) } +
                            resolveCompanionDependencyBuildTasks(project, extension)
                    })
                }
            }

            if (project.tasks.findByName("testWeb") == null) {
                project.tasks.register("testWeb", GenericUiWebTestTask::class.java) {
                    group = "genericui test"
                    description = "Packages the Web test distribution and executes Web tests in the GenericUI native WebView test runner."
                    webTestDistributionDir.set(webTestDistTask.flatMap { it.outputDirectory })
                    reportDir.set(project.layout.buildDirectory.dir("reports/tests/testWeb"))
                    genericUiRootDir.set(project.provider {
                        listOfNotNull(
                            File(project.rootProject.projectDir, "lib/GenericUI").takeIf { it.isDirectory },
                            File(project.projectDir, "lib/GenericUI").takeIf { it.isDirectory },
                            project.rootProject.projectDir.takeIf { File(it, "tests/web-test-runner").isDirectory },
                            File("D:/IdeaProjects/GenericUI").takeIf { it.isDirectory }
                        ).firstOrNull()?.absolutePath ?: project.rootProject.projectDir.absolutePath
                    })
                    timeoutSeconds.set(project.provider { extension.testing.testTimeoutSeconds })
                    dependsOn(webTestDistTask)
                }
            }
        }
    }

    private fun configureAndroidTargetIfPresent(
        project: Project,
        kmp: Any,
        targets: NamedDomainObjectCollection<*>,
        extension: GenericUiExtension
    ) {
        val hasKmpAndroidLibraryPlugin = project.plugins.hasPlugin("com.android.kotlin.multiplatform.library")
        if (!hasKmpAndroidLibraryPlugin) return

        runCatching {
            val androidExt = (kmp as? ExtensionAware)?.extensions?.findByName("android")
                ?: targets.findByName("android")
                ?: return
            val resolvedAndroidVariant = GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.ANDROID)
            val ns = resolvedAndroidVariant.packageName.trim()
                .ifEmpty { extension.application.androidNamespace.trim() }
                .ifEmpty { resolvedAndroidVariant.appId.trim() }
                .ifEmpty {
                    val grp = project.group.toString().trim()
                    if (grp.isNotEmpty() && grp != "unspecified") grp else "com.nexustools.genericui.app"
                }
            androidExt.javaClass.methods.firstOrNull {
                it.name == "setNamespace" && it.parameterCount == 1
            }?.invoke(androidExt, ns)

            val compileSdk = extension.android.manifestConfig.takeIf {
                "compileSdk" in it.explicitFields && it.compileSdk > 0
            }?.compileSdk
                ?: extension.application.androidCompileSdk
            androidExt.javaClass.methods.firstOrNull {
                it.name == "setCompileSdk" && it.parameterCount == 1 && it.parameterTypes[0] == Int::class.javaObjectType
            }?.invoke(androidExt, compileSdk)

            val minSdk = extension.android.manifestConfig.takeIf {
                "minSdk" in it.explicitFields && it.minSdk > 0
            }?.minSdk
                ?: extension.application.androidMinSdk
            androidExt.javaClass.methods.firstOrNull {
                it.name == "setMinSdk" && it.parameterCount == 1 && it.parameterTypes[0] == Int::class.javaObjectType
            }?.invoke(androidExt, minSdk)

            configureHostTest(androidExt)
        }
    }

    private fun applyHostTestOptions(builder: Any?) {
        if (builder == null) return
        val cls = builder::class.java
        cls.methods.firstOrNull {
            it.name == "setIncludeAndroidResources" && it.parameterCount == 1
        }?.invoke(builder, true)
        cls.methods.firstOrNull {
            it.name == "setReturnDefaultValues" && it.parameterCount == 1
        }?.invoke(builder, true)
    }

    private fun configureHostTest(androidExt: Any) {
        val actionMethod = androidExt::class.java.methods.firstOrNull {
            it.name == "withHostTest" &&
                it.parameterCount == 1 &&
                org.gradle.api.Action::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        if (actionMethod != null) {
            runCatching {
                actionMethod.invoke(androidExt, object : org.gradle.api.Action<Any> {
                    override fun execute(builder: Any) {
                        applyHostTestOptions(builder)
                    }
                })
                return
            }
        }
        val fn1Method = androidExt::class.java.methods.firstOrNull {
            it.name == "withHostTest" &&
                it.parameterCount == 1 &&
                Function1::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        if (fn1Method != null) {
            runCatching {
                val cfg: (Any?) -> Unit = { builder -> applyHostTestOptions(builder) }
                fn1Method.invoke(androidExt, cfg)
                return
            }
        }
        invokeNoArg(androidExt, "withHostTest")
    }

    private fun invokeNoArg(target: Any?, methodName: String): Any? {
        if (target == null) return null
        return runCatching {
            val noArgMethod = target.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 0
            }
            if (noArgMethod != null) {
                return@runCatching noArgMethod.invoke(target)
            }
            val actionMethod = target.javaClass.methods.firstOrNull {
                it.name == methodName &&
                    it.parameterCount == 1 &&
                    org.gradle.api.Action::class.java.isAssignableFrom(it.parameterTypes[0])
            }
            if (actionMethod != null) {
                val noOpAction = org.gradle.api.Action<Any> {}
                return@runCatching actionMethod.invoke(target, noOpAction)
            }
            val fn1Method = target.javaClass.methods.firstOrNull {
                it.name == methodName &&
                    it.parameterCount == 1 &&
                    Function1::class.java.isAssignableFrom(it.parameterTypes[0])
            }
            if (fn1Method != null) {
                val noOpFn: (Any?) -> Unit = {}
                return@runCatching fn1Method.invoke(target, noOpFn)
            }
            null
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
