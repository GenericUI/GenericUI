package com.nexustools.genericui.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

/**
 * Gradle plugin (`id("com.nexustools.genericui")`) that configures GenericUI multiplatform targets,
 * optional LWJGL 3 OpenGL ES & Vulkan native dependencies for `:swing`, Google ANGLE (`libEGL.dll`,
 * `libGLESv2.dll`, `d3dcompiler_47.dll`) on Windows, `MoltenVK` on macOS/iOS, `shaderc` SPIR-V
 * compilation, SASS/CSS compilation, and first-party extension modules (`:extensions:aichat`,
 * `:extensions:threejs`, `:extensions:box2d`, `:extensions:box3d`).
 */
class GenericUiGradlePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("genericui", GenericUiExtension::class.java)
        extension.onConfiguredCallback = {
            GenericUiMultiplatformConfigurator.configureMultiplatform(project, extension)
        }
        GenericUiMultiplatformConfigurator.registerBuildTargetTasks(project, extension)

        val angleTask = project.tasks.register("bundleGenericUiAngle", AngleNativeBundleTask::class.java) {
            group = TASK_GROUP
            description = "Stages Google ANGLE (libEGL.dll, libGLESv2.dll, d3dcompiler_47.dll) runtime metadata for Windows OpenGL ES targets."
            bundleAngleOnWindows.set(project.provider { extension.graphics.bundleAngleOnWindows })
            angleBackendRenderer.set(project.provider { extension.graphics.angleBackendRenderer })
            preferOpenGLES31.set(project.provider { extension.graphics.preferOpenGLES31 })
            targetClassifiers.set(project.provider {
                extension.backends.swingConfig.targetClassifiers.map { it.lwjglClassifier }
            })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/angle"))
        }

        val vulkanTask = project.tasks.register("bundleGenericUiVulkan", VulkanNativeBundleTask::class.java) {
            group = TASK_GROUP
            description = "Stages Vulkan loader, MoltenVK ICD, and Shaderc configuration metadata for VKCanvas targets."
            bundleMoltenVkOnApple.set(project.provider { extension.graphics.bundleMoltenVkOnApple })
            bundleShaderc.set(project.provider { extension.graphics.bundleShaderc })
            enableValidationLayersInDebug.set(project.provider { extension.graphics.enableVulkanValidationLayersInDebug })
            lwjglVersion.set(project.provider { extension.backends.swingConfig.lwjglVersion })
            targetClassifiers.set(project.provider {
                extension.backends.swingConfig.targetClassifiers.map { it.lwjglClassifier }
            })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/vulkan"))
        }

        val sassTask = project.tasks.register("compileGenericUiSass", GenericUiSassCompileTask::class.java) {
            group = TASK_GROUP
            description = "Compiles GenericUI .scss and .sass stylesheets into normalized .css bundles for the Web target and precompiled .kt PrecompiledStyleSheet objects."
            compileSass.set(project.provider { extension.styles.compileSass })
            generateWebCss.set(project.provider { extension.styles.generateWebCss })
            webCssBundleFileName.set(project.provider { extension.styles.webCssBundleFileName })
            precompileToKotlin.set(project.provider { extension.styles.precompileToKotlin })
            kotlinPackageName.set(project.provider {
                val configured = extension.styles.kotlinPackageName.trim()
                if (configured.isNotEmpty() && configured != "com.nexustools.genericui.generated.styles") {
                    configured
                } else {
                    val grp = project.group.toString().trim()
                    if (grp.isNotEmpty()) "$grp.styles" else "com.nexustools.genericui.generated.styles"
                }
            })
            minify.set(project.provider { extension.styles.minify })
            includeBootstrapVariables.set(project.provider { extension.styles.includeBootstrapVariables })
            val resolvedSrcDir = resolveStylesSourceDir(project, extension.styles.sourceDir)
            if (resolvedSrcDir != null) {
                sourceDirectory.set(resolvedSrcDir)
            }
            outputDirectory.set(project.layout.buildDirectory.dir(project.provider { extension.styles.outputDir }))
            webCssOutputDirectory.set(project.layout.buildDirectory.dir(project.provider { extension.styles.webCssOutputDir }))
            kotlinOutputDirectory.set(project.layout.buildDirectory.dir(project.provider { extension.styles.kotlinOutputDir }))
        }

        val resourcesTask = project.tasks.register(
            "compileGenericUiResources",
            GenericUiResourceCompilerTask::class.java
        ) {
            group = TASK_GROUP
            description = "Compiles Android-like resources (drawable/, values/, raw/, font/) into R.kt / RC.kt and translates native backend bundles."
            resourcesEnabled.set(project.provider { extension.resources.enabled })
            projectRootPath.set(project.projectDir.absolutePath)
            resourcePackageName.set(project.provider {
                val configured = extension.resources.packageName.trim()
                if (configured.isNotEmpty()) {
                    configured
                } else {
                    val grp = project.group.toString().trim()
                    if (grp.isNotEmpty() && grp != "unspecified") "$grp.res" else "com.nexustools.genericui.generated.res"
                }
            })
            candidateSourceDirs.set(project.provider { extension.resources.sourceDirs.toList() })
            translateForAllBackends.set(project.provider { extension.resources.translateForAllBackends })
            themeColor.set(project.provider { extension.application.desktop.themeColor })
            appName.set(project.provider {
                extension.application.desktop.appName.trim().ifEmpty {
                    extension.application.baseName.trim().ifEmpty { project.name }
                }
            })
            kotlinOutputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/resources/kotlin/commonMain"))
            backendOutputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/resources/backends"))
        }

        val desktopResourcesTask = project.tasks.register(
            "prepareGenericUiDesktopResources",
            GenericUiDesktopResourceTask::class.java
        ) {
            group = TASK_GROUP
            description = "Generates and compiles Windows (.rc/.res), macOS (Info.plist), and Linux (.desktop) application icons & metadata."
            projectRootPath.set(project.projectDir.absolutePath)
            baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            appName.set(project.provider {
                extension.application.desktop.appName.trim().ifEmpty {
                    extension.application.baseName.trim().ifEmpty { project.name }
                }
            })
            appId.set(project.provider {
                extension.application.desktop.appId.trim().ifEmpty {
                    val grp = project.group.toString().trim()
                    if (grp.isNotEmpty()) grp else "com.nexustools.${project.name.lowercase()}"
                }
            })
            appVersion.set(project.provider {
                extension.application.desktop.appVersion.trim().ifEmpty {
                    val v = project.version.toString().trim()
                    if (v.isNotEmpty() && v != "unspecified") v else "1.0.0"
                }
            })
            vendor.set(project.provider { extension.application.desktop.vendor })
            appDescription.set(project.provider { extension.application.desktop.description })
            copyright.set(project.provider { extension.application.desktop.copyright })
            license.set(project.provider { extension.application.desktop.license })
            homepageUrl.set(project.provider { extension.application.desktop.homepageUrl })
            issuesUrl.set(project.provider { extension.application.desktop.issuesUrl })
            themeColor.set(project.provider { extension.application.desktop.themeColor })
            windowsIconPath.set(project.provider { extension.application.desktop.windowsIcon })
            macIconPath.set(project.provider { extension.application.desktop.macIcon })
            linuxIconPath.set(project.provider { extension.application.desktop.linuxIcon })
            fallbackIconPath.set(project.provider { extension.application.desktop.icon })
            embedWindowsResources.set(project.provider { extension.application.desktop.embedWindowsResources })
            macMinimumSystemVersion.set(project.provider { extension.application.desktop.macMinimumSystemVersion })
            macCategory.set(project.provider { extension.application.desktop.macCategory })
            macHighResolutionCapable.set(project.provider { extension.application.desktop.macHighResolutionCapable })
            linuxCategories.set(project.provider { extension.application.desktop.linuxCategories })
            linuxStartupWmClass.set(project.provider { extension.application.desktop.linuxStartupWmClass })
            linuxTerminal.set(project.provider { extension.application.desktop.linuxTerminal })
            customResourceEntries.set(project.provider { extension.application.desktop.customResourceEntries })
            bundledResourcePaths.set(project.provider { extension.application.desktop.bundledResourcePaths })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/desktop-resources"))
        }

        val webDistTask = project.tasks.register(
            "packageGenericUiWebDistribution",
            GenericUiWebDistributionTask::class.java
        ) {
            group = TASK_GROUP
            description = "Packages the unified GenericUI Web distribution with Wasm primary + JS IR fallback, splash screen icon, favicon, optional PWA manifest.webmanifest & installable WebApp tags, genericui-loader.js, and SHA-1 cache-busting hashes."
            projectRootPath.set(project.projectDir.absolutePath)
            projectBuildPath.set(project.layout.buildDirectory.asFile.map { it.absolutePath })
            baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            appName.set(project.provider {
                extension.application.desktop.appName.trim().ifEmpty {
                    extension.application.baseName.trim().ifEmpty { project.name }
                }
            })
            appDescription.set(project.provider { extension.application.desktop.description })
            themeColor.set(project.provider { extension.application.desktop.themeColor })
            webIconPath.set(project.provider { extension.application.desktop.webIcon })
            fallbackIconPath.set(project.provider { extension.application.desktop.icon })
            linuxIconPath.set(project.provider { extension.application.desktop.linuxIcon })
            macIconPath.set(project.provider { extension.application.desktop.macIcon })
            windowsIconPath.set(project.provider { extension.application.desktop.windowsIcon })
            webCssBundleFileName.set(project.provider { extension.styles.webCssBundleFileName })
            manifestEnabled.set(project.provider { extension.application.desktop.webManifest.enabled })
            allowInstallAsWebApp.set(project.provider { extension.application.desktop.webManifest.allowInstallAsWebApp })
            manifestFileName.set(project.provider { extension.application.desktop.webManifest.fileName })
            manifestId.set(project.provider { extension.application.desktop.webManifest.id })
            manifestName.set(project.provider { extension.application.desktop.webManifest.name })
            manifestShortName.set(project.provider { extension.application.desktop.webManifest.shortName })
            manifestDescription.set(project.provider { extension.application.desktop.webManifest.description })
            manifestStartUrl.set(project.provider { extension.application.desktop.webManifest.startUrl })
            manifestScope.set(project.provider { extension.application.desktop.webManifest.scope })
            manifestDisplay.set(project.provider { extension.application.desktop.webManifest.display })
            manifestDisplayOverride.set(project.provider { extension.application.desktop.webManifest.displayOverride.toList() })
            manifestOrientation.set(project.provider { extension.application.desktop.webManifest.orientation })
            manifestThemeColor.set(project.provider { extension.application.desktop.webManifest.themeColor })
            manifestBackgroundColor.set(project.provider { extension.application.desktop.webManifest.backgroundColor })
            manifestLang.set(project.provider { extension.application.desktop.webManifest.lang })
            manifestDir.set(project.provider { extension.application.desktop.webManifest.dir })
            manifestCategories.set(project.provider { extension.application.desktop.webManifest.categories.toList() })
            manifestPreferRelatedApplications.set(project.provider { extension.application.desktop.webManifest.preferRelatedApplications })
            manifestAppleStatusBarStyle.set(project.provider { extension.application.desktop.webManifest.appleStatusBarStyle })
            manifestIcons.set(project.provider { extension.application.desktop.webManifest.icons.toList() })
            manifestShortcuts.set(project.provider { extension.application.desktop.webManifest.shortcuts.toList() })
            outputDirectory.set(project.layout.buildDirectory.dir("dist/web"))
        }

        val projectPath = project.path
        project.tasks.register("windowsVisualTest", GenericUiVisualTestTask::class.java) {
            group = "verification"
            description = "Runs GenericUI JestElectricRunner tests with a live visual Win32 window on Windows."
            visualOnWindows.set(project.provider { extension.testing.visualOnWindows })
            visualStepDelayMs.set(project.provider { extension.testing.visualStepDelayMs })
        }

        project.tasks.register("runJestElectricWindowsVisualTest", GenericUiVisualTestTask::class.java) {
            group = TASK_GROUP
            description = "Alias for windowsVisualTest: runs JestElectricRunner tests in a live Win32 window."
            visualOnWindows.set(project.provider { extension.testing.visualOnWindows })
            visualStepDelayMs.set(project.provider { extension.testing.visualStepDelayMs })
        }

        project.tasks.register("printGenericUiConfig") {
            group = TASK_GROUP
            description = "Prints the resolved GenericUI target backends, extensions, and module coordinates."
            notCompatibleWithConfigurationCache("Prints resolved GenericUI configuration")
            doLast {
                println("=== GenericUI Configuration ($projectPath) ===")
                println("Backends:   ${extension.backends.enabledBackendModulePaths()}")
                println("Extensions: ${extension.extensions.enabledExtensionModulePaths()}")
                println("Sass Dir:   ${extension.styles.sourceDir} (compile=${extension.styles.compileSass}, webCss=${extension.styles.generateWebCss})")
                println("Icons:      win=${extension.application.desktop.windowsIcon}, mac=${extension.application.desktop.macIcon}, linux=${extension.application.desktop.linuxIcon}, web=${extension.application.desktop.webIcon}")
            }
        }

        project.afterEvaluate {
            GenericUiMultiplatformConfigurator.configureMultiplatform(project, extension)
            GenericUiMultiplatformConfigurator.applySourceSetDependencies(project, extension)
            sassTask.configure {
                val resolvedAfterEval = resolveStylesSourceDir(project, extension.styles.sourceDir)
                if (resolvedAfterEval != null) {
                    sourceDirectory.set(resolvedAfterEval)
                }
            }
            wireExtensionAndBackendModules(project, extension)
            wireTestingModules(project, extension)
            wireOptionalSwingGraphicsDependencies(project, extension)
            wireGeneratedResourceKotlinSources(project, extension, resourcesTask.name)
            wirePrecompiledSassKotlinSources(project, extension, sassTask.name)
            wireGeneratedWebCssResources(project, extension, sassTask.name)
            wireDesktopResourceTasks(project, desktopResourcesTask.name)
            wireCompanionDependencyBuildTasks(project, extension, desktopResourcesTask.name)
            wireBuildLifecycleTasks(project, angleTask.name, vulkanTask.name, sassTask.name, resourcesTask.name, desktopResourcesTask.name)
            webDistTask.configure {
                mustRunAfter(
                    project.tasks.matching { t ->
                        t.name == "compileGenericUiSass" ||
                            t.name.endsWith("ProcessResources") ||
                            (t.name.startsWith("compile") && (t.name.contains("KotlinWasmJs") || t.name.contains("KotlinJs"))) ||
                            t.name.endsWith("CompileSync") ||
                            (t.name.endsWith("Distribution") && t.name != "packageGenericUiWebDistribution")
                    }
                )
            }
        }
    }

    private fun wireGeneratedResourceKotlinSources(
        project: Project,
        extension: GenericUiExtension,
        resourcesTaskName: String
    ) {
        if (!extension.resources.enabled) return
        val ktResDir = project.layout.buildDirectory.dir("generated/genericui/resources/kotlin/commonMain")
        val kotlinExt = project.extensions.findByName("kotlin")
        if (kotlinExt != null) {
            runCatching {
                val getSourceSets = kotlinExt.javaClass.methods.firstOrNull {
                    it.name == "getSourceSets" && it.parameterCount == 0
                }
                val sourceSetsContainer = getSourceSets?.invoke(kotlinExt) as? org.gradle.api.NamedDomainObjectContainer<*>
                val targetSourceSet = sourceSetsContainer?.findByName("commonMain")
                    ?: sourceSetsContainer?.findByName("common")
                    ?: sourceSetsContainer?.findByName("main")
                if (targetSourceSet != null) {
                    val getKotlin = targetSourceSet.javaClass.methods.firstOrNull {
                        it.name == "getKotlin" && it.parameterCount == 0
                    }
                    val kotlinSourceDirSet = getKotlin?.invoke(targetSourceSet) as? org.gradle.api.file.SourceDirectorySet
                    kotlinSourceDirSet?.srcDir(ktResDir)
                }
            }
        }
        project.tasks.matching {
            it.name.startsWith("compile") && it.name.contains("Kotlin")
        }.configureEach {
            dependsOn(resourcesTaskName)
        }
    }

    private fun wireCompanionDependencyBuildTasks(
        project: Project,
        extension: GenericUiExtension,
        desktopResourcesTaskName: String
    ) {
        val depTasks = GenericUiMultiplatformConfigurator.resolveCompanionDependencyBuildTasks(project, extension)
        if (depTasks.isEmpty()) return

        project.tasks.matching {
            (it.name.startsWith("compile") && it.name.contains("Kotlin")) ||
                it.name == desktopResourcesTaskName ||
                it.name.endsWith("ProcessResources", ignoreCase = true) ||
                it.name == "processResources"
        }.configureEach {
            for (depTask in depTasks) {
                if (this != depTask && this.name != depTask.toString()) {
                    dependsOn(depTask)
                }
            }
        }
    }

    private fun resolveStylesSourceDir(project: Project, configuredPath: String): File? {
        val primary = File(project.projectDir, configuredPath)
        if (primary.exists() && primary.isDirectory) {
            return primary
        }
        val fallbackDirs = listOf(
            "src/commonMain/resources/styles",
            "src/commonMain/resources",
            "src/webMain/resources/styles",
            "src/webMain/resources",
            "src/jsMain/resources/styles",
            "src/jsMain/resources",
            "src/wasmJsMain/resources/styles",
            "src/wasmJsMain/resources",
            "src/main/resources/styles",
            "src/main/resources"
        )
        return fallbackDirs
            .map { File(project.projectDir, it) }
            .firstOrNull { dir ->
                dir.exists() && dir.isDirectory && dir.walkTopDown().any { f ->
                    f.isFile && f.extension.lowercase() in setOf("scss", "sass", "css")
                }
            }
    }

    private fun wireExtensionAndBackendModules(project: Project, extension: GenericUiExtension) {
        val commonConfigName = resolvePrimaryImplementationConfiguration(project) ?: return
        val backends = extension.backends
        val version = extension.version

        if (extension.includeCommon) {
            resolveModuleDependency(project, ":common", version)?.let { dep ->
                project.dependencies.add(commonConfigName, dep)
                addDepToEnabledBackendConfigs(project, backends, dep, commonConfigName)
            }
        }

        for (extPath in extension.extensions.enabledExtensionModulePaths()) {
            val isProjectOfProjects = extPath in PROJECT_OF_PROJECTS_EXTENSIONS ||
                project.rootProject.findProject("$extPath:common") != null
            if (isProjectOfProjects) {
                resolveModuleDependency(project, "$extPath:common", version)?.let { dep ->
                    project.dependencies.add(commonConfigName, dep)
                    addDepToEnabledBackendConfigs(project, backends, dep, commonConfigName)
                }
                if (backends.requiresSharedSystemModule) {
                    addBackendModuleDependency(project, ":system:shared", "$extPath:sharedSystem", version, commonConfigName)
                }
                if (backends.requiresSharedDesktopModule) {
                    addBackendModuleDependency(project, ":desktop:shared", "$extPath:desktop:shared", version, commonConfigName)
                }
                val desktopPlatformMap = listOf(
                    Triple(":desktop:windows", backends.windowsEnabled, "$extPath:desktop:windows"),
                    Triple(":desktop:macos", backends.macosEnabled, "$extPath:desktop:macos"),
                    Triple(":desktop:gtk", backends.gtkEnabled, "$extPath:desktop:gtk"),
                    Triple(":desktop:qt", backends.qtEnabled, "$extPath:desktop:qt")
                )
                for ((backendKey, isEnabled, subPath) in desktopPlatformMap) {
                    if (isEnabled) {
                        addBackendModuleDependency(project, backendKey, subPath, version, commonConfigName)
                    }
                }
                if (backends.requiresSharedMobileModule) {
                    addBackendModuleDependency(project, ":mobile:shared", "$extPath:mobile:shared", version, commonConfigName)
                }
                if (backends.androidEnabled) {
                    addBackendModuleDependency(project, ":mobile:android", "$extPath:mobile:android", version, commonConfigName)
                }
                if (backends.iosEnabled) {
                    addBackendModuleDependency(project, ":mobile:ios", "$extPath:mobile:ios", version, commonConfigName)
                }
                if (backends.swingConfig.enabled) {
                    addBackendModuleDependency(project, ":desktop:swing", "$extPath:desktop:swing", version, commonConfigName)
                }
                if (backends.webEnabled) {
                    addBackendModuleDependency(project, ":web", "$extPath:web", version, commonConfigName)
                }
            } else {
                // Single Multiplatform Project extension (:extensions:<name>)
                resolveModuleDependency(project, extPath, version)?.let { dep ->
                    project.dependencies.add(commonConfigName, dep)
                    addDepToEnabledBackendConfigs(project, backends, dep, commonConfigName)
                }
            }
        }

        for (backendPath in backends.enabledBackendModulePaths()) {
            addBackendModuleDependency(project, backendPath, backendPath, version, commonConfigName)
        }
    }

    private fun wireTestingModules(project: Project, extension: GenericUiExtension) {
        if (!extension.testing.enabled) return
        val version = extension.version
        val backends = extension.backends
        val commonTestConfig = listOf("commonTestImplementation", "testImplementation").firstOrNull {
            project.configurations.findByName(it) != null
        } ?: return

        resolveModuleDependency(project, ":tests:common", version)?.let { dep ->
            project.dependencies.add(commonTestConfig, dep)
        }

        fun addTestDepIfConfigExists(modulePath: String, vararg configNames: String) {
            val dep = resolveModuleDependency(project, modulePath, version) ?: return
            for (cfg in configNames) {
                if (project.configurations.findByName(cfg) != null) {
                    project.dependencies.add(cfg, dep)
                }
            }
        }

        if (backends.requiresSharedDesktopModule) {
            addTestDepIfConfigExists(
                ":tests:desktop:shared",
                "desktopNativeTestImplementation",
                "desktopNativeCommonTestImplementation",
                "nativeDesktopTestImplementation",
                "nativeDesktopCommonTestImplementation",
                "mingwX64TestImplementation",
                "linuxX64TestImplementation",
                "linuxArm64TestImplementation",
                "macosX64TestImplementation",
                "macosArm64TestImplementation"
            )
        }
        if (backends.windowsEnabled) {
            addTestDepIfConfigExists(":tests:desktop:windows", "windowsTestImplementation", "mingwX64TestImplementation")
        }
        if (backends.macosEnabled) {
            addTestDepIfConfigExists(":tests:desktop:macos", "macTestImplementation", "macosX64TestImplementation", "macosArm64TestImplementation")
        }
        if (backends.gtkEnabled) {
            addTestDepIfConfigExists(":tests:desktop:gtk", "gtkTestImplementation", "linuxX64TestImplementation", "linuxArm64TestImplementation")
        }
        if (backends.qtEnabled) {
            addTestDepIfConfigExists(":tests:desktop:qt", "qtTestImplementation", "linuxX64TestImplementation", "linuxArm64TestImplementation")
        }
        if (backends.swingConfig.enabled) {
            addTestDepIfConfigExists(":tests:desktop:swing", "swingTestImplementation", "jvmTestImplementation", "testImplementation")
        }
        if (backends.requiresSharedMobileModule) {
            addTestDepIfConfigExists(
                ":tests:mobile:shared",
                "mobileCommonTestImplementation",
                "androidUnitTestImplementation",
                "iosArm64TestImplementation",
                "iosSimulatorArm64TestImplementation"
            )
        }
        if (backends.androidEnabled) {
            addTestDepIfConfigExists(":tests:mobile:android", "androidUnitTestImplementation", "androidInstrumentedTestImplementation")
        }
        if (backends.iosEnabled) {
            addTestDepIfConfigExists(":tests:mobile:ios", "iosTestImplementation", "iosArm64TestImplementation", "iosSimulatorArm64TestImplementation")
        }
        if (backends.webEnabled) {
            addTestDepIfConfigExists(":tests:web", "webTestImplementation", "jsTestImplementation", "wasmJsTestImplementation")
        }
    }

    private fun addDepToEnabledBackendConfigs(
        project: Project,
        backends: GenericUiBackendsConfig,
        dep: Any,
        fallbackSingleTargetConfig: String
    ) {
        val addedConfigs = mutableSetOf<String>()
        for (backendKey in backends.enabledBackendModulePaths()) {
            val targetConfigs = resolveBackendConfigurations(project, backendKey, fallbackSingleTargetConfig)
            for (cfg in targetConfigs) {
                if (cfg != fallbackSingleTargetConfig && addedConfigs.add(cfg)) {
                    project.dependencies.add(cfg, dep)
                }
            }
        }
    }

    private fun addBackendModuleDependency(
        project: Project,
        backendKey: String,
        modulePath: String,
        version: String,
        fallbackSingleTargetConfig: String
    ) {
        val dep = resolveModuleDependency(project, modulePath, version) ?: return
        val targetConfigs = resolveBackendConfigurations(project, backendKey, fallbackSingleTargetConfig)
        for (cfg in targetConfigs) {
            project.dependencies.add(cfg, dep)
        }
    }

    /**
     * Resolves a GenericUI module either as a local subproject (`project(":...")` when building inside
     * GenericUI) or as its exact composite-build / Maven coordinate (`com.nexustools.genericui...:0.1.0-SNAPSHOT`)
     * when `GenericUI` is linked via `includeBuild("lib/GenericUI")`.
     */
    internal fun resolveModuleDependency(
        project: Project,
        modulePath: String,
        version: String = DEFAULT_VERSION
    ): Any? {
        val localProject = project.rootProject.findProject(modulePath)
        if (localProject != null) {
            return if (localProject == project) null else localProject
        }
        if (modulePath !in KNOWN_GENERICUI_MODULES) {
            return null
        }
        if (modulePath != ":extensions:dsl" && modulePath.endsWith(":dsl")) {
            val relPath = modulePath.trimStart(':').replace(':', '/')
            val compositeCandidate = File(project.rootProject.projectDir, "lib/GenericUI/$relPath")
            val localCandidate = File(project.rootProject.projectDir, relPath)
            if (!compositeCandidate.isDirectory && !localCandidate.isDirectory) {
                return null
            }
        }
        return toMavenCoordinate(modulePath, version)
    }

    private fun wireOptionalSwingGraphicsDependencies(project: Project, extension: GenericUiExtension) {
        val swing = extension.backends.swingConfig
        if (!swing.useLwjglGL && !swing.useVulkan) {
            return
        }

        val jvmImplConfig = resolveJvmImplementationConfiguration(project) ?: return
        val jvmRuntimeConfig = resolveJvmRuntimeConfiguration(project) ?: jvmImplConfig
        val version = swing.lwjglVersion
        val classifiers = swing.targetClassifiers.ifEmpty { setOf(OsArchClassifier.detectHost()) }

        // Core LWJGL + JAWT bridge are shared by both GLCanvas and VKCanvas on Swing/AWT
        project.dependencies.add(jvmImplConfig, "org.lwjgl:lwjgl:$version")
        project.dependencies.add(jvmImplConfig, "org.lwjgl:lwjgl-jawt:$version")
        for (target in classifiers) {
            project.dependencies.add(jvmRuntimeConfig, "org.lwjgl:lwjgl:$version:${target.lwjglClassifier}")
        }

        if (swing.useLwjglGL) {
            val glModules = listOf(
                "org.lwjgl:lwjgl-egl",
                "org.lwjgl:lwjgl-opengles",
                "org.lwjgl:lwjgl-opengl"
            )
            for (module in glModules) {
                project.dependencies.add(jvmImplConfig, "$module:$version")
                for (target in classifiers) {
                    project.dependencies.add(jvmRuntimeConfig, "$module:$version:${target.lwjglClassifier}")
                }
            }
        }

        if (swing.useVulkan) {
            project.dependencies.add(jvmImplConfig, "org.lwjgl:lwjgl-vulkan:$version")
            for (target in classifiers) {
                if (target.isMacOs && extension.graphics.bundleMoltenVkOnApple) {
                    project.dependencies.add(
                        jvmRuntimeConfig,
                        "org.lwjgl:lwjgl-vulkan:$version:${target.lwjglClassifier}"
                    )
                }
            }

            if (extension.graphics.bundleShaderc) {
                project.dependencies.add(jvmImplConfig, "org.lwjgl:lwjgl-shaderc:$version")
                for (target in classifiers) {
                    project.dependencies.add(
                        jvmRuntimeConfig,
                        "org.lwjgl:lwjgl-shaderc:$version:${target.lwjglClassifier}"
                    )
                }
            }

            project.dependencies.add(jvmImplConfig, "org.lwjgl:lwjgl-vma:$version")
            for (target in classifiers) {
                project.dependencies.add(
                    jvmRuntimeConfig,
                    "org.lwjgl:lwjgl-vma:$version:${target.lwjglClassifier}"
                )
            }
        }
    }

    private fun wirePrecompiledSassKotlinSources(
        project: Project,
        extension: GenericUiExtension,
        sassTaskName: String
    ) {
        if (!extension.styles.compileSass || !extension.styles.precompileToKotlin) {
            return
        }
        val ktOutDir = project.layout.buildDirectory.dir(extension.styles.kotlinOutputDir)
        val kotlinExt = project.extensions.findByName("kotlin")
        if (kotlinExt != null) {
            runCatching {
                val getSourceSets = kotlinExt.javaClass.methods.firstOrNull {
                    it.name == "getSourceSets" && it.parameterCount == 0
                }
                val sourceSetsContainer = getSourceSets?.invoke(kotlinExt) as? org.gradle.api.NamedDomainObjectContainer<*>
                val targetSourceSet = sourceSetsContainer?.findByName("commonMain")
                    ?: sourceSetsContainer?.findByName("main")
                if (targetSourceSet != null) {
                    val getKotlin = targetSourceSet.javaClass.methods.firstOrNull {
                        it.name == "getKotlin" && it.parameterCount == 0
                    }
                    val kotlinSourceDirSet = getKotlin?.invoke(targetSourceSet) as? org.gradle.api.file.SourceDirectorySet
                    kotlinSourceDirSet?.srcDir(ktOutDir)
                }
            }
        }
        project.tasks.matching {
            it.name.startsWith("compile") && it.name.contains("Kotlin")
        }.configureEach {
            dependsOn(sassTaskName)
        }
    }

    /**
     * Wires the compiled `.css` files generated by `compileGenericUiSass` (`webCssOutputDirectory`)
     * into the Web target (`jsMain`, `wasmJsMain`, `webMain`) resource source sets, `jsProcessResources` /
     * `wasmJsProcessResources` copy tasks, and Web distribution / webpack tasks so the Web target
     * always has compiled `.css` files generated from `.scss` / `.sass`.
     */
    private fun wireGeneratedWebCssResources(
        project: Project,
        extension: GenericUiExtension,
        sassTaskName: String
    ) {
        if (!extension.styles.compileSass || !extension.styles.generateWebCss) {
            return
        }
        val webCssOutDir = project.layout.buildDirectory.dir(extension.styles.webCssOutputDir)
        val kotlinExt = project.extensions.findByName("kotlin")
        if (kotlinExt != null) {
            runCatching {
                val getSourceSets = kotlinExt.javaClass.methods.firstOrNull {
                    it.name == "getSourceSets" && it.parameterCount == 0
                }
                val sourceSetsContainer = getSourceSets?.invoke(kotlinExt) as? org.gradle.api.NamedDomainObjectContainer<*>
                if (sourceSetsContainer != null) {
                    val webSourceSetNames = listOf("jsMain", "wasmJsMain", "webMain")
                    val matchedWebSourceSets = webSourceSetNames.mapNotNull { sourceSetsContainer.findByName(it) }
                    val targetSourceSets = matchedWebSourceSets.ifEmpty {
                        listOfNotNull(
                            sourceSetsContainer.findByName("commonMain")
                                ?: sourceSetsContainer.findByName("main")
                        )
                    }
                    for (ss in targetSourceSets) {
                        val getResources = ss.javaClass.methods.firstOrNull {
                            it.name == "getResources" && it.parameterCount == 0
                        }
                        val resourcesDirSet = getResources?.invoke(ss) as? org.gradle.api.file.SourceDirectorySet
                        resourcesDirSet?.srcDir(webCssOutDir)
                    }
                }
            }
        }

        val webResourceTaskNames = setOf(
            "jsProcessResources",
            "wasmJsProcessResources",
            "webProcessResources"
        )
        project.tasks.matching {
            it.name in webResourceTaskNames ||
                (extension.backends.webEnabled && it.name == "processResources")
        }.configureEach {
            dependsOn(sassTaskName)
            if (this is org.gradle.api.tasks.Copy) {
                from(webCssOutDir)
                duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.INCLUDE
            }
        }

        val webLifecycleTaskPrefixes = listOf(
            "jsBrowser",
            "wasmJsBrowser",
            "jsDevelopmentExecutableCompileSync",
            "jsProductionExecutableCompileSync",
            "wasmJsDevelopmentExecutableCompileSync",
            "wasmJsProductionExecutableCompileSync"
        )
        project.tasks.matching { task ->
            webLifecycleTaskPrefixes.any { prefix -> task.name.startsWith(prefix) }
        }.configureEach {
            dependsOn(sassTaskName)
        }
    }

    private fun wireDesktopResourceTasks(
        project: Project,
        desktopResourcesTaskName: String
    ) {
        val runtimeAssetsDirProvider = project.layout.buildDirectory.dir("generated/genericui/desktop-resources/runtime-assets")
        project.tasks.matching {
            it.name.startsWith("link") && it.name.contains("Executable")
        }.configureEach {
            dependsOn(desktopResourcesTaskName)
            doLast {
                val assetsDir = runtimeAssetsDirProvider.get().asFile
                if (!assetsDir.exists()) return@doLast
                val getDestDir = this.javaClass.methods.firstOrNull {
                    (it.name == "getDestinationDirectory" || it.name == "getOutputDirectory") && it.parameterCount == 0
                }
                val destObj = getDestDir?.invoke(this)
                val destFile: File? = when (destObj) {
                    is File -> destObj
                    is org.gradle.api.file.DirectoryProperty -> destObj.orNull?.asFile
                    else -> {
                        val getOutputFile = this.javaClass.methods.firstOrNull {
                            it.name == "getOutputFile" && it.parameterCount == 0
                        }
                        (getOutputFile?.invoke(this) as? org.gradle.api.provider.Provider<*>)?.orNull?.let { it as? File }?.parentFile
                    }
                }
                if (destFile != null && destFile.exists()) {
                    assetsDir.listFiles()?.forEach { asset ->
                        val target = File(destFile, asset.name)
                        if (asset.isDirectory) {
                            asset.copyRecursively(target, overwrite = true)
                        } else {
                            asset.copyTo(target, overwrite = true)
                        }
                    }
                }
            }
        }
    }

    private fun wireBuildLifecycleTasks(
        project: Project,
        angleTaskName: String,
        vulkanTaskName: String,
        sassTaskName: String,
        resourcesTaskName: String,
        desktopResourcesTaskName: String
    ) {
        project.tasks.matching {
            it.name.endsWith("ProcessResources", ignoreCase = true) || it.name == "processResources"
        }.configureEach {
            dependsOn(angleTaskName, vulkanTaskName, sassTaskName, resourcesTaskName, desktopResourcesTaskName)
        }
        project.tasks.matching {
            it.name == desktopResourcesTaskName
        }.configureEach {
            dependsOn(resourcesTaskName)
        }
    }

    private fun resolvePrimaryImplementationConfiguration(project: Project): String? =
        listOf("commonMainImplementation", "implementation").firstOrNull {
            project.configurations.findByName(it) != null
        }

    private fun resolveBackendConfigurations(
        project: Project,
        backendPath: String,
        fallbackSingleTargetConfig: String
    ): List<String> {
        val isMultiplatform = project.configurations.findByName("commonMainImplementation") != null
        if (!isMultiplatform) {
            return if (project.configurations.findByName(fallbackSingleTargetConfig) != null) {
                listOf(fallbackSingleTargetConfig)
            } else {
                emptyList()
            }
        }

        fun firstExisting(vararg names: String): List<String> {
            val match = names.firstOrNull { project.configurations.findByName(it) != null }
            return if (match != null) listOf(match) else emptyList()
        }

        fun allExisting(vararg names: String): List<String> =
            names.filter { project.configurations.findByName(it) != null }

        return when (backendPath) {
            ":desktop:windows" ->
                allExisting(
                    "windowsMainImplementation",
                    "mingwMainImplementation",
                    "mingwX64MainImplementation"
                )
            ":desktop:macos" ->
                allExisting(
                    "macMainImplementation",
                    "macosMainImplementation",
                    "macosX64MainImplementation",
                    "macosArm64MainImplementation"
                )
            ":desktop:gtk" ->
                allExisting(
                    "gtkImplementation",
                    "gtkMainImplementation",
                    "linuxCommonImplementation",
                    "linuxMainImplementation",
                    "desktopNativeCommonImplementation",
                    "desktopNativeMainImplementation",
                    "nativeDesktopCommonImplementation",
                    "linuxX64MainImplementation",
                    "linuxArm64MainImplementation",
                    "linuxX64GtkImplementation",
                    "linuxArm64GtkImplementation"
                )
            ":desktop:qt" ->
                allExisting(
                    "qtImplementation",
                    "qtMainImplementation",
                    "linuxCommonImplementation",
                    "linuxMainImplementation",
                    "desktopNativeCommonImplementation",
                    "desktopNativeMainImplementation",
                    "nativeDesktopCommonImplementation",
                    "linuxX64MainImplementation",
                    "linuxArm64MainImplementation",
                    "linuxX64QtImplementation",
                    "linuxArm64QtImplementation"
                )
            ":system:shared" ->
                allExisting(
                    "systemCommonImplementation",
                    "systemMainImplementation",
                    "desktopCommonImplementation",
                    "desktopMainImplementation",
                    "desktopNativeCommonImplementation",
                    "desktopNativeMainImplementation",
                    "nativeDesktopCommonImplementation",
                    "nativeDesktopMainImplementation",
                    "mobileCommonImplementation",
                    "mobileMainImplementation",
                    "swingImplementation",
                    "swingMainImplementation",
                    "jvmMainImplementation",
                    "androidImplementation",
                    "androidMainImplementation",
                    "mingwX64MainImplementation",
                    "linuxX64MainImplementation",
                    "linuxArm64MainImplementation",
                    "macosX64MainImplementation",
                    "macosArm64MainImplementation",
                    "iosMainImplementation",
                    "iosArm64MainImplementation",
                    "iosSimulatorArm64MainImplementation",
                    "iosX64MainImplementation"
                )
            ":desktop:shared" ->
                allExisting(
                    "desktopNativeCommonImplementation",
                    "desktopNativeMainImplementation",
                    "nativeDesktopCommonImplementation",
                    "nativeDesktopMainImplementation",
                    "mingwX64MainImplementation",
                    "linuxX64MainImplementation",
                    "linuxArm64MainImplementation",
                    "macosX64MainImplementation",
                    "macosArm64MainImplementation"
                )
            ":desktop:swing" ->
                allExisting("swingImplementation", "swingMainImplementation", "jvmMainImplementation")
            ":mobile:android" ->
                allExisting("androidImplementation", "androidMainImplementation")
            ":mobile:ios" ->
                allExisting(
                    "iosImplementation",
                    "iosMainImplementation",
                    "iosArm64MainImplementation",
                    "iosSimulatorArm64MainImplementation",
                    "iosX64MainImplementation"
                )
            ":mobile:shared" ->
                allExisting(
                    "mobileCommonImplementation",
                    "mobileMainImplementation",
                    "androidMainImplementation",
                    "iosMainImplementation",
                    "iosArm64MainImplementation",
                    "iosSimulatorArm64MainImplementation",
                    "iosX64MainImplementation"
                )
            ":web" ->
                allExisting("webImplementation", "webMainImplementation", "jsMainImplementation", "wasmJsMainImplementation")
            else -> emptyList()
        }
    }

    private fun resolveJvmImplementationConfiguration(project: Project): String? =
        listOf("jvmMainImplementation", "implementation", "commonMainImplementation").firstOrNull {
            project.configurations.findByName(it) != null
        }

    private fun resolveJvmRuntimeConfiguration(project: Project): String? =
        listOf("jvmMainRuntimeOnly", "runtimeOnly", "jvmMainImplementation", "implementation").firstOrNull {
            project.configurations.findByName(it) != null
        }

    companion object {
        const val TASK_GROUP: String = "genericui"
        const val BASE_GROUP: String = "com.nexustools.genericui"
        const val DEFAULT_VERSION: String = "0.1.0-SNAPSHOT"

        /**
         * Converts a GenericUI Gradle module path (e.g. `":common"`, `":desktop:windows"`,
         * `":extensions:canvas2d:common"`, `":extensions:threejs"`) into its exact Maven /
         * composite-build coordinate matching GenericUI's `group` and artifact hierarchy.
         */
        fun toMavenCoordinate(modulePath: String, version: String = DEFAULT_VERSION): String {
            val segments = modulePath.trimStart(':').split(':').filter { it.isNotEmpty() }
            require(segments.isNotEmpty()) { "Invalid GenericUI module path: $modulePath" }
            return if (segments.size == 1) {
                "$BASE_GROUP:${segments.first()}:$version"
            } else {
                val groupSuffix = segments.dropLast(1).joinToString(".")
                val artifact = segments.last()
                "$BASE_GROUP.$groupSuffix:$artifact:$version"
            }
        }

        /**
         * Extensions structured as a Project-of-Projects (`:extensions:<name>:common`,
         * `:desktop`, `:mobile`, `:swing`, `:web`, plus umbrella `:extensions:<name>`).
         */
        val PROJECT_OF_PROJECTS_EXTENSIONS: Set<String> = setOf(
            ":extensions:gpu",
            ":extensions:imageprocessing",
            ":extensions:canvas2d",
            ":extensions:canvas3d",
            ":extensions:media",
            ":extensions:webview",
            ":extensions:alien",
            ":tests"
        )

        /**
         * Canonical set of GenericUI module paths present in `GenericUI/settings.gradle.kts`.
         */
        val KNOWN_GENERICUI_MODULES: Set<String> = setOf(
            ":common",
            ":desktop:shared",
            ":desktop:qt",
            ":desktop:gtk",
            ":desktop:windows",
            ":desktop:macos",
            ":desktop:swing",
            ":mobile:shared",
            ":mobile:android",
            ":mobile:ios",
            ":web",
            ":extensions:gpu",
            ":extensions:gpu:common",
            ":extensions:gpu:sharedSystem",
            ":extensions:gpu:desktop:shared",
            ":extensions:gpu:desktop:swing",
            ":extensions:gpu:mobile:shared",
            ":extensions:gpu:mobile:android",
            ":extensions:gpu:mobile:ios",
            ":extensions:gpu:web",
            ":extensions:imageprocessing",
            ":extensions:imageprocessing:common",
            ":extensions:imageprocessing:sharedSystem",
            ":extensions:imageprocessing:web",
            ":extensions:canvas2d",
            ":extensions:canvas2d:common",
            ":extensions:canvas2d:sharedSystem",
            ":extensions:canvas2d:desktop:shared",
            ":extensions:canvas2d:desktop:windows",
            ":extensions:canvas2d:desktop:macos",
            ":extensions:canvas2d:desktop:gtk",
            ":extensions:canvas2d:desktop:qt",
            ":extensions:canvas2d:desktop:swing",
            ":extensions:canvas2d:mobile:shared",
            ":extensions:canvas2d:mobile:android",
            ":extensions:canvas2d:mobile:ios",
            ":extensions:canvas2d:web",
            ":extensions:canvas3d",
            ":extensions:canvas3d:common",
            ":extensions:canvas3d:sharedSystem",
            ":extensions:canvas3d:desktop:shared",
            ":extensions:canvas3d:desktop:windows",
            ":extensions:canvas3d:desktop:macos",
            ":extensions:canvas3d:desktop:gtk",
            ":extensions:canvas3d:desktop:qt",
            ":extensions:canvas3d:desktop:swing",
            ":extensions:canvas3d:mobile:shared",
            ":extensions:canvas3d:mobile:android",
            ":extensions:canvas3d:mobile:ios",
            ":extensions:canvas3d:web",
            ":extensions:media",
            ":extensions:media:common",
            ":extensions:media:sharedSystem",
            ":extensions:media:desktop:shared",
            ":extensions:media:desktop:windows",
            ":extensions:media:desktop:macos",
            ":extensions:media:desktop:gtk",
            ":extensions:media:desktop:qt",
            ":extensions:media:desktop:swing",
            ":extensions:media:mobile:shared",
            ":extensions:media:mobile:android",
            ":extensions:media:mobile:ios",
            ":extensions:media:web",
            ":extensions:webview",
            ":extensions:webview:common",
            ":extensions:webview:sharedSystem",
            ":extensions:webview:desktop:shared",
            ":extensions:webview:desktop:windows",
            ":extensions:webview:desktop:macos",
            ":extensions:webview:desktop:gtk",
            ":extensions:webview:desktop:qt",
            ":extensions:webview:desktop:swing",
            ":extensions:webview:mobile:shared",
            ":extensions:webview:mobile:android",
            ":extensions:webview:mobile:ios",
            ":extensions:webview:web",
            ":extensions:alien",
            ":extensions:alien:common",
            ":extensions:alien:sharedSystem",
            ":extensions:alien:web",
            ":extensions:alien:dsl",
            ":extensions:aichat",
            ":extensions:aichat:dsl",
            ":extensions:codeeditor",
            ":extensions:codeeditor:dsl",
            ":extensions:threejs",
            ":extensions:threejs:dsl",
            ":extensions:box2d",
            ":extensions:box2d:dsl",
            ":extensions:box3d",
            ":extensions:box3d:dsl",
            ":extensions:canvas2d:dsl",
            ":extensions:canvas3d:dsl",
            ":extensions:media:dsl",
            ":extensions:webview:dsl",
            ":extensions:dsl",
            ":tests",
            ":tests:common",
            ":tests:desktop:shared",
            ":tests:desktop:windows",
            ":tests:desktop:macos",
            ":tests:desktop:gtk",
            ":tests:desktop:qt",
            ":tests:desktop:swing",
            ":tests:mobile:shared",
            ":tests:mobile:android",
            ":tests:mobile:ios",
            ":tests:web"
        )
    }
}
