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
            configureAndroidTargetIfPresent(project, kmp, targets, app)
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
                }
                ss.findByName("androidUnitTest")?.let { dependsOnIfNotConnected(it, androidTest) }
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

    private fun configureNativeDesktopBinaries(
        project: Project,
        extension: GenericUiExtension,
        mingwTarget: Any?,
        macTargets: List<Any>,
        linuxTargets: List<Any>,
        defaultEntryPoint: String = ""
    ) {
        val app = extension.application
        val entryPointStr = defaultEntryPoint.trim()
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
    fun registerBuildTargetTasks(project: Project, extension: GenericUiExtension) {
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
                    val baseTasks = (
                        listOf(
                            "genericuiValidateApp",
                            "generateGenericUiLaunchers",
                            "compileGenericUiResources"
                        ) + candidateTaskNames
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
                                    child.copyRecursively(target, overwrite = true)
                                } else if (child.canonicalFile != target.canonicalFile) {
                                    target.parentFile?.mkdirs()
                                    child.copyTo(target, overwrite = true)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. Windows (X32, X64, Arm64 + Host Aggregate)
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

        // 2. macOS (Apple Silicon Arm64 Only — macosX64 Removed)
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

        // 3. Linux GTK (X32, X64, Arm32, Arm64 + Aggregate)
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

        // 4. Linux Qt (X32, X64, Arm32, Arm64 + Aggregate)
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

        // 5. Android (Pure Java/DEX Universal APK + CPU-Specific Kotlin/Native Targets X32, X64, Arm32, Arm64)
        registerTargetTask(
            name = "developmentAndroid",
            desc = "Builds the Android (Pure Java/Kotlin DEX universal APK) Development target into build/dist/android/development.",
            distRelativePath = "dist/android/development",
            candidateTaskNames = listOf(
                "generateGenericUiAndroidManifest",
                "generateGenericUiAndroidActivities",
                "compileAndroidMain",
                "assembleDebug",
                "compileDebugKotlinAndroid"
            ),
            candidateSourceDirs = listOf(
                "outputs/apk/debug"
            )
        )
        registerTargetTask(
            name = "productionAndroid",
            desc = "Builds the Android (Pure Java/Kotlin DEX universal APK) Production target into build/dist/android/production.",
            distRelativePath = "dist/android/production",
            candidateTaskNames = listOf(
                "generateGenericUiAndroidManifest",
                "generateGenericUiAndroidActivities",
                "compileAndroidMain",
                "assembleRelease",
                "compileReleaseKotlinAndroid"
            ),
            candidateSourceDirs = listOf(
                "outputs/apk/release"
            )
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
                "assembleDebug"
            ),
            candidateSourceDirs = listOf(
                "bin/androidNativeX86/debugShared",
                "bin/androidNativeX86/debugExecutable",
                "outputs/apk/debug"
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
                "assembleRelease"
            ),
            candidateSourceDirs = listOf(
                "bin/androidNativeX86/releaseShared",
                "bin/androidNativeX86/releaseExecutable",
                "outputs/apk/release"
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
                "assembleDebug"
            ),
            candidateSourceDirs = listOf(
                "bin/androidNativeX64/debugShared",
                "bin/androidNativeX64/debugExecutable",
                "outputs/apk/debug"
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
                "assembleRelease"
            ),
            candidateSourceDirs = listOf(
                "bin/androidNativeX64/releaseShared",
                "bin/androidNativeX64/releaseExecutable",
                "outputs/apk/release"
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
                "assembleDebug"
            ),
            candidateSourceDirs = listOf(
                "bin/androidNativeArm32/debugShared",
                "bin/androidNativeArm32/debugExecutable",
                "outputs/apk/debug"
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
                "assembleRelease"
            ),
            candidateSourceDirs = listOf(
                "bin/androidNativeArm32/releaseShared",
                "bin/androidNativeArm32/releaseExecutable",
                "outputs/apk/release"
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
                "assembleDebug"
            ),
            candidateSourceDirs = listOf(
                "bin/androidNativeArm64/debugShared",
                "bin/androidNativeArm64/debugExecutable",
                "outputs/apk/debug"
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
                "assembleRelease"
            ),
            candidateSourceDirs = listOf(
                "bin/androidNativeArm64/releaseShared",
                "bin/androidNativeArm64/releaseExecutable",
                "outputs/apk/release"
            )
        )

        // 6. iOS (Arm64 Device & SimulatorArm64 Apple Silicon Simulator + Aggregate — iosX64 Removed)
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

        // 7. Swing & Web (Unified, No Sub-Targets)
        registerTargetTask(
            name = "developmentSwing",
            desc = "Builds the Java Swing (JVM / AWT / LWJGL) Development target into build/dist/swing/development.",
            distRelativePath = "dist/swing/development",
            candidateTaskNames = listOf("compileKotlinJvm", "jvmProcessResources"),
            candidateSourceDirs = listOf(
                "classes/kotlin/jvm/main",
                "processedResources/jvm/main",
                "generated/genericui/desktop-resources/runtime-assets"
            )
        )
        registerTargetTask(
            name = "productionSwing",
            desc = "Builds the Java Swing (JVM / AWT / LWJGL) Production JAR target into build/dist/swing/production.",
            distRelativePath = "dist/swing/production",
            candidateTaskNames = listOf("jvmJar", "compileKotlinJvm"),
            candidateSourceDirs = listOf(
                "libs",
                "generated/genericui/desktop-resources/runtime-assets"
            )
        )
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
