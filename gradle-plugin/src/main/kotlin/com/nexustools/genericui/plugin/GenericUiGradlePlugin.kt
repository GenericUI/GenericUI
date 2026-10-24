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
            description = "Compiles GenericUI .scss and .sass stylesheets into normalized .css bundles and precompiled .kt PrecompiledStyleSheet objects."
            compileSass.set(project.provider { extension.styles.compileSass })
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
            val srcDir = File(project.projectDir, extension.styles.sourceDir)
            if (srcDir.exists()) {
                sourceDirectory.set(srcDir)
            }
            outputDirectory.set(project.layout.buildDirectory.dir(extension.styles.outputDir))
            kotlinOutputDirectory.set(project.layout.buildDirectory.dir(extension.styles.kotlinOutputDir))
        }

        val projectPath = project.path
        project.tasks.register("printGenericUiConfig") {
            group = TASK_GROUP
            description = "Prints the resolved GenericUI target backends, extensions, and module coordinates."
            notCompatibleWithConfigurationCache("Prints resolved GenericUI configuration")
            doLast {
                println("=== GenericUI Configuration ($projectPath) ===")
                println("Backends:   ${extension.backends.enabledBackendModulePaths()}")
                println("Extensions: ${extension.extensions.enabledExtensionModulePaths()}")
                println("Sass Dir:   ${extension.styles.sourceDir} (compile=${extension.styles.compileSass})")
            }
        }

        project.afterEvaluate {
            wireExtensionAndBackendModules(project, extension)
            wireOptionalSwingGraphicsDependencies(project, extension)
            wirePrecompiledSassKotlinSources(project, extension, sassTask.name)
            wireBuildLifecycleTasks(project, angleTask.name, vulkanTask.name, sassTask.name)
        }
    }

    private fun wireExtensionAndBackendModules(project: Project, extension: GenericUiExtension) {
        val commonConfigName = resolvePrimaryImplementationConfiguration(project) ?: return
        val backends = extension.backends
        val version = extension.version

        if (extension.includeCommon) {
            resolveModuleDependency(project, ":common", version)?.let { dep ->
                project.dependencies.add(commonConfigName, dep)
            }
        }

        for (extPath in extension.extensions.enabledExtensionModulePaths()) {
            val isProjectOfProjects = extPath in PROJECT_OF_PROJECTS_EXTENSIONS ||
                project.rootProject.findProject("$extPath:common") != null
            if (isProjectOfProjects) {
                resolveModuleDependency(project, "$extPath:common", version)?.let { dep ->
                    project.dependencies.add(commonConfigName, dep)
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
                }
            }
        }

        for (backendPath in backends.enabledBackendModulePaths()) {
            addBackendModuleDependency(project, backendPath, backendPath, version, commonConfigName)
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

    private fun wireBuildLifecycleTasks(
        project: Project,
        angleTaskName: String,
        vulkanTaskName: String,
        sassTaskName: String
    ) {
        project.tasks.matching { it.name == "processResources" || it.name == "jvmProcessResources" }.configureEach {
            dependsOn(angleTaskName, vulkanTaskName, sassTaskName)
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
                firstExisting("windowsMainImplementation", "mingwMainImplementation").ifEmpty {
                    allExisting("mingwX64MainImplementation")
                }
            ":desktop:macos" ->
                firstExisting("macosMainImplementation").ifEmpty {
                    allExisting("macosX64MainImplementation", "macosArm64MainImplementation")
                }
            ":desktop:gtk" ->
                firstExisting("gtkMainImplementation", "linuxMainImplementation").ifEmpty {
                    allExisting("linuxX64MainImplementation", "linuxArm64MainImplementation")
                }
            ":desktop:qt" ->
                firstExisting("qtMainImplementation", "linuxMainImplementation").ifEmpty {
                    allExisting("linuxX64MainImplementation", "linuxArm64MainImplementation")
                }
            ":desktop:shared" ->
                firstExisting("desktopMainImplementation").ifEmpty {
                    allExisting(
                        "mingwX64MainImplementation",
                        "linuxX64MainImplementation",
                        "linuxArm64MainImplementation",
                        "macosX64MainImplementation",
                        "macosArm64MainImplementation"
                    )
                }
            ":desktop:swing" ->
                firstExisting("swingMainImplementation", "jvmMainImplementation")
            ":mobile:android" ->
                firstExisting("androidMainImplementation")
            ":mobile:ios" ->
                firstExisting("iosMainImplementation").ifEmpty {
                    allExisting(
                        "iosArm64MainImplementation",
                        "iosSimulatorArm64MainImplementation",
                        "iosX64MainImplementation"
                    )
                }
            ":mobile:shared" ->
                firstExisting("mobileMainImplementation").ifEmpty {
                    val mobileTargets = mutableListOf<String>()
                    mobileTargets.addAll(firstExisting("androidMainImplementation"))
                    mobileTargets.addAll(
                        firstExisting("iosMainImplementation").ifEmpty {
                            allExisting(
                                "iosArm64MainImplementation",
                                "iosSimulatorArm64MainImplementation",
                                "iosX64MainImplementation"
                            )
                        }
                    )
                    mobileTargets
                }
            ":web" ->
                firstExisting("webMainImplementation").ifEmpty {
                    allExisting("jsMainImplementation", "wasmJsMainImplementation")
                }
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
            ":extensions:canvas2d",
            ":extensions:canvas3d",
            ":extensions:media",
            ":extensions:webview"
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
            ":extensions:gpu:desktop:shared",
            ":extensions:gpu:desktop:swing",
            ":extensions:gpu:mobile:shared",
            ":extensions:gpu:mobile:android",
            ":extensions:gpu:mobile:ios",
            ":extensions:gpu:web",
            ":extensions:canvas2d",
            ":extensions:canvas2d:common",
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
            ":extensions:imageprocessing",
            ":extensions:aichat",
            ":extensions:codeeditor",
            ":extensions:threejs",
            ":extensions:box2d",
            ":extensions:box3d",
            ":extensions:dsl"
        )
    }
}
