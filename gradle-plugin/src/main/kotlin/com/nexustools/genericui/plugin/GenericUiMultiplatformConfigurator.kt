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
 * executable binaries, and platform Development/Production build target tasks (`developmentWindows`,
 * `productionWindows`, `developmentMac`, `productionMac`, `developmentLinuxGtk`, `productionLinuxGtk`,
 * `developmentLinuxQt`, `productionLinuxQt`, `developmentSwing`, `productionSwing`,
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

        val iosArm64Target: Any? = if (backends.iosEnabled) {
            targets.findByName("iosArm64") ?: invokeNoArg(kmp, "iosArm64")
        } else {
            targets.findByName("iosArm64")
        }
        val iosSimArm64Target: Any? = if (backends.iosEnabled) {
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
        //         - qt, gtk, windows, mac
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
            backends.qtEnabled ||
            backends.swingConfig.enabled

        val anyNativeDesktop = backends.windowsEnabled ||
            backends.macosEnabled ||
            backends.gtkEnabled ||
            backends.qtEnabled

        val anyMobile = backends.androidEnabled || backends.iosEnabled
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

        if ((backends.gtkEnabled || backends.qtEnabled) && nativeDesktopMain != null && nativeDesktopTest != null) {
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
            val bothGtkAndQtDirsExist = File(project.projectDir, "src/gtkMain").exists() &&
                File(project.projectDir, "src/qtMain").exists()

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
                if (!bothGtkAndQtDirsExist) {
                    ss.findByName("linuxX64Main")?.let { dependsOnIfNotConnected(it, gtkMain) }
                    ss.findByName("linuxArm64Main")?.let { dependsOnIfNotConnected(it, gtkMain) }
                }
                ss.findByName("linuxX64Test")?.let { dependsOnIfNotConnected(it, gtkTest) }
                ss.findByName("linuxArm64Test")?.let { dependsOnIfNotConnected(it, gtkTest) }
            }

            if (backends.qtEnabled) {
                val qt = ss.maybeCreate("qt").also {
                    dependsOnIfNotConnected(it, linuxMain)
                    addKotlinSrcDir(it, "src/qt/kotlin")
                    addResourcesSrcDir(it, "src/qt/resources")
                }
                val qtMain = ss.maybeCreate("qtMain").also {
                    dependsOnIfNotConnected(it, qt)
                    addKotlinSrcDir(it, "src/qtMain/kotlin")
                    addKotlinSrcDir(it, "src/qt/kotlin")
                    addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/qt/kotlin"))
                    addResourcesSrcDir(it, "src/qtMain/resources")
                    addResourcesSrcDir(it, "src/qt/resources")
                }
                val qtTest = ss.maybeCreate("qtTest").also {
                    dependsOnIfNotConnected(it, linuxTest)
                    addKotlinSrcDir(it, "src/qtTest/kotlin")
                    addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/qtTest/kotlin"))
                }
                for (lt in linuxTargets) {
                    val comps = invokeNoArg(lt, "getCompilations") as? NamedDomainObjectContainer<*>
                    val qtComp = comps?.maybeCreate("qt")
                    val defaultSs = invokeNoArg(qtComp, "getDefaultSourceSet")
                    if (defaultSs != null) {
                        dependsOnIfNotConnected(defaultSs, qtMain)
                    }
                }
                if (!bothGtkAndQtDirsExist && !(hasMainAppFragment && backends.gtkEnabled)) {
                    ss.findByName("linuxX64Main")?.let { dependsOnIfNotConnected(it, qtMain) }
                    ss.findByName("linuxArm64Main")?.let { dependsOnIfNotConnected(it, qtMain) }
                }
                ss.findByName("linuxX64Test")?.let { dependsOnIfNotConnected(it, qtTest) }
                ss.findByName("linuxArm64Test")?.let { dependsOnIfNotConnected(it, qtTest) }
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
                    addKotlinSrcDir(it, "src/androidTest/kotlin")
                    addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/androidTest/kotlin"))
                }
                ss.findByName("androidUnitTest")?.let { dependsOnIfNotConnected(it, androidTest) }
                ss.findByName("androidHostTest")?.let { dependsOnIfNotConnected(it, androidTest) }
            }

            if (backends.iosEnabled) {
                val ios = ss.maybeCreate("ios").also {
                    dependsOnIfNotConnected(it, mobileMain)
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
                    dependsOnIfNotConnected(it, mobileTest)
                    addKotlinSrcDir(it, "src/iosTest/kotlin")
                    addKotlinSrcDir(it, project.layout.buildDirectory.dir("generated/genericui/launchers/iosTest/kotlin"))
                }
                ss.findByName("iosArm64Main")?.let { dependsOnIfNotConnected(it, iosMain) }
                ss.findByName("iosSimulatorArm64Main")?.let { dependsOnIfNotConnected(it, iosMain) }
                ss.findByName("iosArm64Test")?.let { dependsOnIfNotConnected(it, iosTest) }
                ss.findByName("iosSimulatorArm64Test")?.let { dependsOnIfNotConnected(it, iosTest) }
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

        if (backends.iosEnabled) {
            configureIosFrameworks(
                project = project,
                extension = extension,
                iosTargets = listOfNotNull(iosArm64Target, iosSimArm64Target)
            )
        }

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
            "-static-libgcc",
            "-static-libstdc++",
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

        val app = extension.application
        val entryPointStr = defaultEntryPoint.trim()
        if (!app.createExecutableBinaries || entryPointStr.isEmpty()) {
            return
        }
        val resolvedBaseName = app.baseName.trim().ifEmpty { project.name }

        if (mingwTarget != null) {
            val winLinkerOpts = baseWinLibs.toMutableList()
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

        val macLinkerOpts = (baseMacLinkerOpts + app.extraPosixLinkerOpts).distinct()
        for (macTarget in macTargets) {
            configureTargetExecutable(
                nativeTarget = macTarget,
                namePrefix = null,
                compilationObj = null,
                entryPointStr = entryPointStr,
                baseNameStr = resolvedBaseName,
                linkerOptsList = macLinkerOpts
            )
        }

        val linuxLinkerOpts = (baseLinuxLinkerOpts + app.extraPosixLinkerOpts).distinct()
        for (linuxTarget in linuxTargets) {
            configureTargetExecutable(
                nativeTarget = linuxTarget,
                namePrefix = null,
                compilationObj = null,
                entryPointStr = entryPointStr,
                baseNameStr = resolvedBaseName,
                linkerOptsList = linuxLinkerOpts
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
                        linkerOptsList = linuxLinkerOpts
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
                        linkerOptsList = linuxLinkerOpts
                    )
                }
            }
        }
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
     * - Linux Qt (`X32`, `X64`, `Arm32`, `Arm64` + Aggregate): `developmentLinuxQt`, `productionLinuxQt`,
     *   `developmentLinuxQtX32`, `productionLinuxQtX32`, `developmentLinuxQtX64`, `productionLinuxQtX64`,
     *   `developmentLinuxQtArm32`, `productionLinuxQtArm32`, `developmentLinuxQtArm64`, `productionLinuxQtArm64`
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
            ":extensions:media" in extension.extensions.enabledExtensionModulePaths()

    internal fun isWebViewExtensionEnabled(extension: GenericUiExtension): Boolean =
        extension.extensions.webViewEnabled ||
            ":extensions:webview" in extension.extensions.enabledExtensionModulePaths()

    internal fun isGpuExtensionEnabled(extension: GenericUiExtension): Boolean =
        extension.extensions.gpuEnabled ||
            ":extensions:gpu" in extension.extensions.enabledExtensionModulePaths()

    private fun ensureExtensionNativeBundleTasksRegistered(project: Project, extension: GenericUiExtension) {
        if (project.tasks.findByName("bundleGenericUiVlc") == null) {
            project.tasks.register("bundleGenericUiVlc", VlcNativeBundleTask::class.java) {
                group = GenericUiGradlePlugin.TASK_GROUP
                description = "Stages LibVLC native runtime libraries (libvlc, libvlccore, plugins/) for desktop media targets."
                bundleVlcOnDesktop.set(project.provider { isMediaExtensionEnabled(extension) })
                outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/nativeBundle/vlc"))
            }
        }
        if (project.tasks.findByName("bundleGenericUiWebViewProcess") == null) {
            project.tasks.register("bundleGenericUiWebViewProcess", WebViewNativeProcessBundleTask::class.java) {
                group = GenericUiGradlePlugin.TASK_GROUP
                description = "Stages the desktop WebView out-of-process helper executable (genericui-webview-process)."
                webViewEnabled.set(project.provider { isWebViewExtensionEnabled(extension) })
                genericUiRootPath.set(project.projectDir.absolutePath)
                outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/nativeBundle/webview"))
            }
        }
    }

    private fun copyFilteredDirectoryContents(sourceDir: File, targetDir: File, setExecutableOnBinaries: Boolean = false) {
        if (!sourceDir.isDirectory) return
        targetDir.mkdirs()
        sourceDir.walkTopDown().forEach { src ->
            if (src == sourceDir) return@forEach
            if (isExcludedStaticCoreLibrary(src)) return@forEach
            val rel = src.relativeTo(sourceDir).Path
            val dst = File(targetDir, rel)
            if (src.isDirectory) {
                dst.mkdirs()
            } else {
                dst.parentFile?.mkdirs()
                runCatching {
                    if (!dst.exists() || src.canonicalFile != dst.canonicalFile) {
                        src.copyTo(dst, overwrite = true)
                    }
                    if (setExecutableOnBinaries && (dst.extension.isEmpty() || dst.extension == "so" || dst.extension == "dylib" || dst.name.contains(".so."))) {
                        dst.setExecutable(true, false)
                    }
                }
            }
        }
    }

    private val File.Path: String
        get() = path

    private fun purgeExcludedStaticCoreLibraries(rootDir: File) {
        if (!rootDir.exists()) return
        rootDir.walkBottomUp().forEach { f ->
            if (f.isFile && isExcludedStaticCoreLibrary(f)) {
                runCatching { f.delete() }
            }
        }
    }

    private fun stageWindowsNativeExtensionBundles(
        buildDir: File,
        distDir: File,
        candidateBinDirs: List<File>,
        mode: String,
        mediaEnabled: Boolean,
        webViewEnabled: Boolean,
        gpuEnabled: Boolean
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

        if (webViewEnabled) {
            val webViewWinDir = File(buildDir, "generated/genericui/nativeBundle/webview/windows/$mode")
            if (webViewWinDir.isDirectory) {
                for (dest in destinations) {
                    copyFilteredDirectoryContents(webViewWinDir, dest)
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
        webViewEnabled: Boolean,
        gpuEnabled: Boolean
    ) {
        val appName = extension.application.desktop.appName.trim().ifEmpty {
            extension.application.baseName.trim().ifEmpty { project.name }
        }
        val baseName = extension.application.baseName.trim().ifEmpty { project.name }
        val appId = extension.application.desktop.appId.trim().ifEmpty {
            val grp = project.group.toString().trim()
            if (grp.isNotEmpty() && grp != "unspecified") grp else "com.nexustools.${baseName.lowercase()}"
        }
        val appVersion = extension.application.desktop.appVersion.trim().ifEmpty {
            val v = project.version.toString().trim()
            if (v.isNotEmpty() && v != "unspecified") v else "1.0.0"
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
        val exeCandidates = buildList {
            for (binDir in candidateBinDirs) {
                if (binDir.isDirectory) {
                    binDir.listFiles()?.filter {
                        it.isFile && (it.extension == "kexe" || it.name == baseName || it.canExecute()) &&
                            !it.name.endsWith(".dylib") && !it.name.endsWith(".plist")
                    }?.forEach { add(it) }
                }
            }
            distDir.listFiles()?.filter {
                it.isFile && (it.extension == "kexe" || it.name == baseName) &&
                    !it.name.endsWith(".dylib") && !it.name.endsWith(".plist")
            }?.forEach { add(it) }
        }
        val primaryExe = exeCandidates.firstOrNull()
        val bundleExecutableName = baseName
        val targetExeFile = File(macOsDir, bundleExecutableName)
        if (primaryExe != null && primaryExe.isFile) {
            primaryExe.copyTo(targetExeFile, overwrite = true)
            targetExeFile.setExecutable(true, false)
            if (primaryExe.name != bundleExecutableName) {
                val kexeCopy = File(macOsDir, primaryExe.name)
                primaryExe.copyTo(kexeCopy, overwrite = true)
                kexeCopy.setExecutable(true, false)
            }
        } else {
            // Ensure Contents/MacOS/<baseName> placeholder exists when cross-building on non-macOS host
            if (!targetExeFile.exists()) {
                WebViewNativeProcessBundleTask.writeMinimalMachOArm64Executable(targetExeFile)
            }
            targetExeFile.setExecutable(true, false)
        }

        // 3. Info.plist
        val desktopInfoPlist = File(buildDir, "generated/genericui/desktop-resources/macos/Info.plist")
        val resourceCompilerInfoPlist = File(buildDir, "generated/genericui/resources/backends/macos/Info.plist")
        val targetInfoPlist = File(contentsDir, "Info.plist")
        when {
            desktopInfoPlist.isFile -> desktopInfoPlist.copyTo(targetInfoPlist, overwrite = true)
            resourceCompilerInfoPlist.isFile -> resourceCompilerInfoPlist.copyTo(targetInfoPlist, overwrite = true)
            else -> {
                targetInfoPlist.writeText(
                    buildString {
                        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                        appendLine("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">")
                        appendLine("<plist version=\"1.0\">")
                        appendLine("<dict>")
                        appendLine("    <key>CFBundleName</key>")
                        appendLine("    <string>$appName</string>")
                        appendLine("    <key>CFBundleDisplayName</key>")
                        appendLine("    <string>$appName</string>")
                        appendLine("    <key>CFBundleIdentifier</key>")
                        appendLine("    <string>$appId</string>")
                        appendLine("    <key>CFBundleVersion</key>")
                        appendLine("    <string>$appVersion</string>")
                        appendLine("    <key>CFBundleShortVersionString</key>")
                        appendLine("    <string>$appVersion</string>")
                        appendLine("    <key>CFBundleExecutable</key>")
                        appendLine("    <string>$bundleExecutableName</string>")
                        appendLine("    <key>CFBundleIconFile</key>")
                        appendLine("    <string>icon.icns</string>")
                        appendLine("    <key>CFBundlePackageType</key>")
                        appendLine("    <string>APPL</string>")
                        appendLine("    <key>NSHighResolutionCapable</key>")
                        appendLine("    <true/>")
                        appendLine("</dict>")
                        appendLine("</plist>")
                    },
                    Charsets.UTF_8
                )
            }
        }

        // 4. WebView helper executable in Contents/MacOS/genericui-webview-process
        if (webViewEnabled) {
            val webViewMacFile = File(buildDir, "generated/genericui/nativeBundle/webview/macos/$mode/genericui-webview-process")
            if (webViewMacFile.isFile) {
                val dstHelper = File(macOsDir, "genericui-webview-process")
                webViewMacFile.copyTo(dstHelper, overwrite = true)
                dstHelper.setExecutable(true, false)
            }
        }

        // 5. Extension dynamic libraries & plugins in Contents/Frameworks/
        if (mediaEnabled) {
            val vlcMacDir = File(buildDir, "generated/genericui/nativeBundle/vlc/macos")
            if (vlcMacDir.isDirectory) {
                copyFilteredDirectoryContents(vlcMacDir, frameworksDir, setExecutableOnBinaries = true)
            }
        }
        if (gpuEnabled) {
            val gpuMacDir = File(buildDir, "generated/genericui/nativeBundle/gpu/macos")
            if (gpuMacDir.isDirectory) {
                gpuMacDir.listFiles()?.forEach { f ->
                    if (f.name == "MoltenVK_icd.json" || isExcludedStaticCoreLibrary(f)) return@forEach
                    val dst = File(frameworksDir, f.name)
                    f.copyTo(dst, overwrite = true)
                    if (dst.extension == "dylib") {
                        dst.setExecutable(true, false)
                    }
                }
            }
        }

        // 6. Resources (icon.icns + MoltenVK_icd.json + translated backend resources)
        val desktopIcns = File(buildDir, "generated/genericui/desktop-resources/macos/icon.icns")
        val resourceCompilerMacDir = File(buildDir, "generated/genericui/resources/backends/macos")
        if (desktopIcns.isFile) {
            desktopIcns.copyTo(File(resourcesDir, "icon.icns"), overwrite = true)
        }
        if (resourceCompilerMacDir.isDirectory) {
            resourceCompilerMacDir.walkTopDown().forEach { src ->
                if (src == resourceCompilerMacDir || src.name.equals("Info.plist", ignoreCase = true)) return@forEach
                if (isExcludedStaticCoreLibrary(src)) return@forEach
                val rel = src.relativeTo(resourceCompilerMacDir).path
                val dst = File(resourcesDir, rel)
                if (src.isDirectory) {
                    dst.mkdirs()
                } else {
                    dst.parentFile?.mkdirs()
                    src.copyTo(dst, overwrite = true)
                }
            }
        }
        if (gpuEnabled) {
            val moltenVkIcd = File(buildDir, "generated/genericui/nativeBundle/gpu/macos/MoltenVK_icd.json")
            if (moltenVkIcd.isFile) {
                val icdTarget = File(resourcesDir, "vulkan/icd.d/MoltenVK_icd.json")
                icdTarget.parentFile?.mkdirs()
                moltenVkIcd.copyTo(icdTarget, overwrite = true)
            }
        }

        purgeExcludedStaticCoreLibraries(appBundleDir)
        purgeExcludedStaticCoreLibraries(distDir)
    }

    private fun stageLinuxNativeExtensionBundles(
        buildDir: File,
        distDir: File,
        candidateBinDirs: List<File>,
        mode: String,
        mediaEnabled: Boolean,
        webViewEnabled: Boolean,
        gpuEnabled: Boolean
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

        if (webViewEnabled) {
            val webViewLinuxDir = File(buildDir, "generated/genericui/nativeBundle/webview/linux/$mode")
            if (webViewLinuxDir.isDirectory) {
                for (dest in destinations) {
                    copyFilteredDirectoryContents(webViewLinuxDir, dest, setExecutableOnBinaries = true)
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
            if (f.isFile && (f.extension == "kexe" || f.extension.isEmpty() || f.name.contains(".so"))) {
                runCatching { f.setExecutable(true, false) }
            }
        }

        for (dest in destinations) {
            purgeExcludedStaticCoreLibraries(dest)
        }
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
                    val dynamicExtensionTasks = buildList {
                        if (isDesktopDist) {
                            add("prepareGenericUiDesktopResources")
                            if (isMediaExtensionEnabled(extension)) {
                                add("bundleGenericUiVlc")
                            }
                            if (isWebViewExtensionEnabled(extension)) {
                                add("bundleGenericUiWebViewProcess")
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
                        ) + dynamicExtensionTasks + candidateTaskNames
                    ).distinct().mapNotNull { taskName -> project.tasks.findByName(taskName) }
                    val depBuildTasks = resolveCompanionDependencyBuildTasks(project, extension)
                    baseTasks + depBuildTasks
                })
                doLast {
                    val buildDir = buildDirProvider.get()
                    val distDir = File(buildDir, distRelativePath)
                    distDir.mkdirs()
                    val excludedExtensions = setOf("svg", "png", "ico", "icns", "rc", "res", "desktop", "plist", "cur")
                    val excludedNames = setOf("androidmanifest.xml", "info.plist")
                    fun isExcludedNonWebDistFile(file: File): Boolean {
                        val lowerName = file.name.lowercase()
                        if (lowerName.contains(".exe.old") || lowerName in excludedNames) return true
                        if (isExcludedStaticCoreLibrary(file)) return true
                        if (file.isFile && file.extension.lowercase() in excludedExtensions) return true
                        return false
                    }
                    distDir.listFiles()?.forEach { existing ->
                        if (isExcludedNonWebDistFile(existing)) {
                            runCatching { existing.delete() }
                        }
                    }
                    for (relSrc in candidateSourceDirs) {
                        val srcDir = File(buildDir, relSrc)
                        if (!srcDir.exists() || !srcDir.isDirectory) continue
                        srcDir.listFiles()?.forEach { child ->
                            if (isExcludedNonWebDistFile(child)) return@forEach
                            val target = File(distDir, child.name)
                            runCatching {
                                if (child.isDirectory) {
                                    copyFilteredDirectoryContents(child, target)
                                } else if (child.canonicalFile != target.canonicalFile) {
                                    target.parentFile?.mkdirs()
                                    child.copyTo(target, overwrite = true)
                                }
                            }
                        }
                    }

                    val mode = if (distRelativePath.endsWith("/production")) "production" else "development"
                    val candidateBinDirs = candidateSourceDirs.map { File(buildDir, it) }
                    val mediaEnabled = isMediaExtensionEnabled(extension)
                    val webViewEnabled = isWebViewExtensionEnabled(extension)
                    val gpuEnabled = isGpuExtensionEnabled(extension)

                    when {
                        distRelativePath.startsWith("dist/windows/") -> {
                            stageWindowsNativeExtensionBundles(
                                buildDir = buildDir,
                                distDir = distDir,
                                candidateBinDirs = candidateBinDirs,
                                mode = mode,
                                mediaEnabled = mediaEnabled,
                                webViewEnabled = webViewEnabled,
                                gpuEnabled = gpuEnabled
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
                                webViewEnabled = webViewEnabled,
                                gpuEnabled = gpuEnabled
                            )
                        }
                        distRelativePath.startsWith("dist/linux-gtk/") || distRelativePath.startsWith("dist/linux-qt/") -> {
                            stageLinuxNativeExtensionBundles(
                                buildDir = buildDir,
                                distDir = distDir,
                                candidateBinDirs = candidateBinDirs,
                                mode = mode,
                                mediaEnabled = mediaEnabled,
                                webViewEnabled = webViewEnabled,
                                gpuEnabled = gpuEnabled
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
                it.name in setOf(
                    "linkDebugExecutableMingwX64",
                    "linkReleaseExecutableMingwX64",
                    "linkDebugTestMingwX64",
                    "mingwX64Test",
                    "windowsVisualTest"
                )
            }.configureEach {
                dependsOn(project.provider {
                    buildList {
                        if (isMediaExtensionEnabled(extension)) add("bundleGenericUiVlc")
                        if (isWebViewExtensionEnabled(extension)) add("bundleGenericUiWebViewProcess")
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
                            webViewEnabled = isWebViewExtensionEnabled(extension),
                            gpuEnabled = isGpuExtensionEnabled(extension)
                        )
                    }
                }
                doLast {
                    if (name.startsWith("link")) {
                        val bDir = buildDirProvider.get()
                        val isRelease = name.contains("Release")
                        val modeStr = if (isRelease) "production" else "development"
                        val targetBinDir = when {
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
                            webViewEnabled = isWebViewExtensionEnabled(extension),
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

        // 4. Linux Qt (X32, X64, Arm32, Arm64 + Aggregate)
        if (backends.qtEnabled) {
            registerTargetTask(
                name = "developmentLinuxQt",
                desc = "Builds the Linux Qt (Qt6 / QPainter / QVulkanWindow) Development target into build/dist/linux-qt/x64/development.",
                distRelativePath = "dist/linux-qt/x64/development",
                candidateTaskNames = listOf(
                    "linkQtDebugExecutableLinuxX64",
                    "linkQtDebugExecutableLinuxArm64",
                    "compileQtKotlinLinuxX64",
                    "compileQtKotlinLinuxArm64",
                    "linkDebugExecutableLinuxX64",
                    "compileKotlinLinuxX64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX64/qtDebugExecutable",
                    "bin/linuxX64/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxQt",
                desc = "Builds the Linux Qt (Qt6 / QPainter / QVulkanWindow) Production target into build/dist/linux-qt/x64/production.",
                distRelativePath = "dist/linux-qt/x64/production",
                candidateTaskNames = listOf(
                    "linkQtReleaseExecutableLinuxX64",
                    "linkQtReleaseExecutableLinuxArm64",
                    "compileQtKotlinLinuxX64",
                    "compileQtKotlinLinuxArm64",
                    "linkReleaseExecutableLinuxX64",
                    "compileKotlinLinuxX64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX64/qtReleaseExecutable",
                    "bin/linuxX64/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentLinuxQtX32",
                desc = "Builds the Linux Qt x32 (Qt6 / QPainter / QVulkanWindow) Development target into build/dist/linux-qt/x32/development.",
                distRelativePath = "dist/linux-qt/x32/development",
                candidateTaskNames = listOf(
                    "linkQtDebugExecutableLinuxX32",
                    "compileQtKotlinLinuxX32",
                    "linkDebugExecutableLinuxX32",
                    "compileKotlinLinuxX32"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX32/qtDebugExecutable",
                    "bin/linuxX32/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxQtX32",
                desc = "Builds the Linux Qt x32 (Qt6 / QPainter / QVulkanWindow) Production target into build/dist/linux-qt/x32/production.",
                distRelativePath = "dist/linux-qt/x32/production",
                candidateTaskNames = listOf(
                    "linkQtReleaseExecutableLinuxX32",
                    "compileQtKotlinLinuxX32",
                    "linkReleaseExecutableLinuxX32",
                    "compileKotlinLinuxX32"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX32/qtReleaseExecutable",
                    "bin/linuxX32/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentLinuxQtX64",
                desc = "Builds the Linux Qt x64 (Qt6 / QPainter / QVulkanWindow) Development target into build/dist/linux-qt/x64/development.",
                distRelativePath = "dist/linux-qt/x64/development",
                candidateTaskNames = listOf(
                    "linkQtDebugExecutableLinuxX64",
                    "compileQtKotlinLinuxX64",
                    "linkDebugExecutableLinuxX64",
                    "compileKotlinLinuxX64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX64/qtDebugExecutable",
                    "bin/linuxX64/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxQtX64",
                desc = "Builds the Linux Qt x64 (Qt6 / QPainter / QVulkanWindow) Production target into build/dist/linux-qt/x64/production.",
                distRelativePath = "dist/linux-qt/x64/production",
                candidateTaskNames = listOf(
                    "linkQtReleaseExecutableLinuxX64",
                    "compileQtKotlinLinuxX64",
                    "linkReleaseExecutableLinuxX64",
                    "compileKotlinLinuxX64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxX64/qtReleaseExecutable",
                    "bin/linuxX64/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentLinuxQtArm32",
                desc = "Builds the Linux Qt Arm32 (Qt6 / QPainter / QVulkanWindow) Development target into build/dist/linux-qt/arm32/development.",
                distRelativePath = "dist/linux-qt/arm32/development",
                candidateTaskNames = listOf(
                    "linkQtDebugExecutableLinuxArm32Hfp",
                    "compileQtKotlinLinuxArm32Hfp",
                    "linkDebugExecutableLinuxArm32Hfp",
                    "compileKotlinLinuxArm32Hfp"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxArm32Hfp/qtDebugExecutable",
                    "bin/linuxArm32Hfp/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxQtArm32",
                desc = "Builds the Linux Qt Arm32 (Qt6 / QPainter / QVulkanWindow) Production target into build/dist/linux-qt/arm32/production.",
                distRelativePath = "dist/linux-qt/arm32/production",
                candidateTaskNames = listOf(
                    "linkQtReleaseExecutableLinuxArm32Hfp",
                    "compileQtKotlinLinuxArm32Hfp",
                    "linkReleaseExecutableLinuxArm32Hfp",
                    "compileKotlinLinuxArm32Hfp"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxArm32Hfp/qtReleaseExecutable",
                    "bin/linuxArm32Hfp/releaseExecutable"
                )
            )
            registerTargetTask(
                name = "developmentLinuxQtArm64",
                desc = "Builds the Linux Qt Arm64 (Qt6 / QPainter / QVulkanWindow) Development target into build/dist/linux-qt/arm64/development.",
                distRelativePath = "dist/linux-qt/arm64/development",
                candidateTaskNames = listOf(
                    "linkQtDebugExecutableLinuxArm64",
                    "compileQtKotlinLinuxArm64",
                    "linkDebugExecutableLinuxArm64",
                    "compileKotlinLinuxArm64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxArm64/qtDebugExecutable",
                    "bin/linuxArm64/debugExecutable"
                )
            )
            registerTargetTask(
                name = "productionLinuxQtArm64",
                desc = "Builds the Linux Qt Arm64 (Qt6 / QPainter / QVulkanWindow) Production target into build/dist/linux-qt/arm64/production.",
                distRelativePath = "dist/linux-qt/arm64/production",
                candidateTaskNames = listOf(
                    "linkQtReleaseExecutableLinuxArm64",
                    "compileQtKotlinLinuxArm64",
                    "linkReleaseExecutableLinuxArm64",
                    "compileKotlinLinuxArm64"
                ),
                candidateSourceDirs = listOf(
                    "bin/linuxArm64/qtReleaseExecutable",
                    "bin/linuxArm64/releaseExecutable"
                )
            )

            val isLinuxHost = System.getProperty("os.name").orEmpty().lowercase().contains("linux")
            registerTestTargetTask(
                name = "testLinuxQtX64",
                desc = "Runs Linux Qt x64 tests (linuxX64Test on Linux hosts; compiles/links test binary otherwise).",
                candidateTaskNames = buildList {
                    add("compileTestKotlinLinuxX64")
                    add("linkDebugTestLinuxX64")
                    if (isLinuxHost) add("linuxX64Test")
                }
            )
            registerTestTargetTask(
                name = "testLinuxQtArm64",
                desc = "Runs Linux Qt Arm64 tests.",
                candidateTaskNames = listOf(
                    "compileTestKotlinLinuxArm64",
                    "linkDebugTestLinuxArm64",
                    "linuxArm64Test"
                )
            )
            registerTestTargetTask(
                name = "testLinuxQtX32",
                desc = "Verifies Linux Qt x32 test compilation and readiness.",
                candidateTaskNames = listOf(
                    "compileTestKotlinLinuxX32",
                    "compileTestKotlinLinuxX64",
                    "developmentLinuxQtX32"
                )
            )
            registerTestTargetTask(
                name = "testLinuxQtArm32",
                desc = "Verifies Linux Qt Arm32 test compilation and readiness.",
                candidateTaskNames = listOf(
                    "compileTestKotlinLinuxArm32Hfp",
                    "compileTestKotlinLinuxArm64",
                    "developmentLinuxQtArm32"
                )
            )
            registerTestTargetTask(
                name = "testLinuxQt",
                desc = "Runs the Linux Qt test suite (delegates to testLinuxQtX64).",
                candidateTaskNames = listOf("testLinuxQtX64")
            )
            registerTestTargetTask(
                name = "testQt",
                desc = "Runs the Linux Qt test suite (delegates to testLinuxQt).",
                candidateTaskNames = listOf("testLinuxQt")
            )
        }

        if (backends.gtkEnabled || backends.qtEnabled) {
            registerTestTargetTask(
                name = "testLinux",
                desc = "Runs all enabled Linux backend test suites (GTK and/or Qt).",
                candidateTaskNames = buildList {
                    if (backends.gtkEnabled) add("testLinuxGtk")
                    if (backends.qtEnabled) add("testLinuxQt")
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

            val compileSdk = extension.android.manifestConfig.compileSdk.takeIf { it > 0 }
                ?: extension.application.androidCompileSdk
            androidExt.javaClass.methods.firstOrNull {
                it.name == "setCompileSdk" && it.parameterCount == 1 && it.parameterTypes[0] == Int::class.javaObjectType
            }?.invoke(androidExt, compileSdk)

            val minSdk = extension.android.manifestConfig.minSdk.takeIf { it > 0 }
                ?: extension.application.androidMinSdk
            androidExt.javaClass.methods.firstOrNull {
                it.name == "setMinSdk" && it.parameterCount == 1 && it.parameterTypes[0] == Int::class.javaObjectType
            }?.invoke(androidExt, minSdk)

            invokeNoArg(androidExt, "withHostTest")
        }
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
