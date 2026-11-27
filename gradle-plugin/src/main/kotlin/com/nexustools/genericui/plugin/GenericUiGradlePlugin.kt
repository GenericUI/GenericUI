package com.nexustools.genericui.plugin

import com.nexustools.genericui.plugin.nativebuild.GenericUiCompileJniTask
import com.nexustools.genericui.plugin.nativebuild.GenericUiFetchNativeSourceArchiveTask
import com.nexustools.genericui.plugin.nativebuild.GenericUiNativeBuildResolver
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
            GenericUiMultiplatformConfigurator.registerBuildTargetTasks(project, extension)
        }
        project.tasks.withType(org.gradle.api.tasks.testing.AbstractTestTask::class.java).configureEach {
            val secs = extension.testing.testTimeoutSeconds.coerceAtLeast(10L)
            timeout.set(java.time.Duration.ofSeconds(secs))
            if (this is org.gradle.api.tasks.testing.Test && maxHeapSize.isNullOrBlank()) {
                maxHeapSize = "2560m"
            }
        }

        val angleTask = project.tasks.register("bundleGenericUiAngle", AngleNativeBundleTask::class.java) {
            group = TASK_GROUP
            description = "Stages Google ANGLE (libEGL, libGLESv2, d3dcompiler_47) and optional SwiftShader runtime metadata for OpenGL ES targets."
            bundleAngleOnWindows.set(project.provider { extension.graphics.bundleAngleOnWindows })
            bundleAngleOnApple.set(project.provider { extension.graphics.bundleAngleOnApple })
            bundleSoftwareFallback.set(project.provider { extension.graphics.bundleSoftwareFallback })
            compileShaders.set(project.provider { extension.graphics.compileShaders })
            angleBackendRenderer.set(project.provider { extension.graphics.angleBackendRenderer })
            preferOpenGLES31.set(project.provider { extension.graphics.preferOpenGLES31 })
            targetClassifiers.set(project.provider {
                extension.backends.swingConfig.targetClassifiers.map { it.lwjglClassifier }
            })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/angle"))
        }

        val vulkanTask = project.tasks.register("bundleGenericUiVulkan", VulkanNativeBundleTask::class.java) {
            group = TASK_GROUP
            description = "Stages Vulkan loader, MoltenVK ICD, and Shaderc configuration metadata for Canvas3D targets."
            bundleMoltenVkOnApple.set(project.provider { extension.graphics.bundleMoltenVkOnApple })
            bundleShaderc.set(project.provider { extension.graphics.bundleShaderc })
            enableValidationLayersInDebug.set(project.provider { extension.graphics.enableVulkanValidationLayersInDebug })
            lwjglVersion.set(project.provider { extension.backends.swingConfig.lwjglVersion })
            targetClassifiers.set(project.provider {
                extension.backends.swingConfig.targetClassifiers.map { it.lwjglClassifier }
            })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/vulkan"))
        }

        val vlcTask = if (project.tasks.findByName("bundleGenericUiVlc") != null) {
            project.tasks.named("bundleGenericUiVlc", VlcNativeBundleTask::class.java)
        } else {
            project.tasks.register("bundleGenericUiVlc", VlcNativeBundleTask::class.java) {
                group = TASK_GROUP
                description = "Stages LibVLC native runtime libraries (libvlc, libvlccore, plugins/) for desktop media targets."
                bundleVlcOnDesktop.set(project.provider { GenericUiMultiplatformConfigurator.isMediaExtensionEnabled(extension) })
                outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/nativeBundle/vlc"))
            }
        }

        val fetchNativeSourcesTask = project.tasks.register(
            "fetchGenericUiNativeSources",
            GenericUiFetchNativeSourceArchiveTask::class.java
        ) {
            group = TASK_GROUP
            description = "Downloads, SHA-256 verifies, and caches remote C/C++ native source archives."
            libraries.set(project.provider { GenericUiNativeBuildResolver.resolveJniLibraries(project, extension) })
            gradleUserHomePath.set(project.gradle.gradleUserHomeDir.absolutePath)
            offlineMode.set(project.gradle.startParameter.isOffline)
            sourcesManifestFile.set(project.layout.buildDirectory.file("intermediates/genericui_jni/sources-manifest.txt"))
        }

        val compileJniTask = project.tasks.register(
            "compileGenericUiJniLibraries",
            GenericUiCompileJniTask::class.java
        ) {
            group = TASK_GROUP
            description = "Compiles consumer C/C++ JNI and CMake libraries for Android ABIs and host JVM/Swing targets."
            dependsOn(fetchNativeSourcesTask)
            projectRootPath.set(project.projectDir.absolutePath)
            gradleUserHomePath.set(project.gradle.gradleUserHomeDir.absolutePath)
            konanHomePath.set(project.providers.gradleProperty("kotlin.native.home").orElse(""))
            konanDataDirPath.set(
                project.providers.gradleProperty("konan.data.dir")
                    .orElse(System.getenv("KONAN_DATA_DIR").orEmpty())
            )
            androidEnabled.set(project.provider { extension.backends.androidEnabled })
            swingEnabled.set(project.provider { extension.backends.swingConfig.enabled })
            offlineMode.set(project.gradle.startParameter.isOffline)
            libraries.set(project.provider { GenericUiNativeBuildResolver.resolveJniLibraries(project, extension) })
            cmakes.set(project.provider { GenericUiNativeBuildResolver.resolveJniCmakes(project, extension) })
            sourcesManifestFile.set(fetchNativeSourcesTask.flatMap { it.sourcesManifestFile })
            inputSourceFiles.from(project.provider { GenericUiNativeBuildResolver.collectLocalInputFiles(project, extension) })
            androidOutputDirectory.set(project.layout.buildDirectory.dir("intermediates/genericui_jni/android"))
            hostOutputDirectory.set(project.layout.buildDirectory.dir("intermediates/genericui_jni/host"))
            hostBundleManifestFile.set(project.layout.buildDirectory.file("intermediates/genericui_jni/host-bundle-manifest.txt"))
        }

        val sassTask = project.tasks.register("compileGenericUiSass", GenericUiSassCompileTask::class.java) {
            group = TASK_GROUP
            description = "Compiles GenericUI .scss and .sass stylesheets into normalized .css bundles for the Web target and precompiled .kt PrecompiledStyleSheet objects."
            compileSass.set(project.provider { extension.styles.compileSass })
            generateWebCss.set(project.provider { extension.styles.generateWebCss })
            webCssBundleFileName.set(project.provider { extension.styles.webCssBundleFileName })
            precompileToKotlin.set(project.provider { extension.styles.precompileToKotlin })
            kotlinPackageName.set(project.provider {
                resolveStylesKotlinPackageName(project, extension)
            })
            minify.set(project.provider { extension.styles.minify })
            includeBootstrapVariables.set(project.provider { extension.styles.includeBootstrapVariables })
            consumerApplicationMode.set(project.provider { !isGenericUiLibraryModule(project) })
            themeColor.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.COMMON).themeColor
            })
            themeMode.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.COMMON).themeMode
            })
            lightThemeColors.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.COMMON).lightThemeColors
            })
            darkThemeColors.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.COMMON).darkThemeColors
            })
            upstreamResourceManifestPaths.set(project.provider {
                resolveUpstreamThemeAndManifestPaths(project, extension)
            })
            upstreamResourceClasspath.from(project.provider {
                resolveUpstreamResourceClasspathFiles(project)
            })
            spiThemeScssChunks.set(project.provider {
                collectSpiThemeScssChunks(project, extension)
            })
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
            upstreamResourceManifestPaths.set(project.provider {
                resolveUpstreamResourceManifestPaths(project)
            })
            upstreamResourceClasspath.from(project.provider {
                resolveUpstreamResourceClasspathFiles(project)
            })
            translateForAllBackends.set(project.provider { extension.resources.translateForAllBackends })
            themeColor.set(project.provider {
                resolveAndroidVariant(project, extension).themeColor
            })
            themeMode.set(project.provider {
                resolveAndroidVariant(project, extension).themeMode.trim().ifEmpty { "auto" }
            })
            lightThemeColors.set(project.provider {
                resolveAndroidVariant(project, extension).lightThemeColors
            })
            darkThemeColors.set(project.provider {
                resolveAndroidVariant(project, extension).darkThemeColors
            })
            appName.set(project.provider {
                extension.application.desktop.appName.trim().ifEmpty {
                    extension.application.baseName.trim().ifEmpty { project.name }
                }
            })
            configuredIconPath.set(project.provider {
                extension.application.desktop.icon.trim().ifEmpty {
                    extension.resources.icon.trim().ifEmpty {
                        extension.windowConfig.icon?.trim().orEmpty().ifEmpty {
                            GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.COMMON).iconPath.orEmpty()
                        }
                    }
                }
            })
            appleIconPath.set(project.provider {
                extension.application.desktop.appleIcon.trim().ifEmpty {
                    extension.resources.appleIcon.trim()
                }
            })
            trayIconPath.set(project.provider {
                extension.extensions.serviceWorkerConfig.trayIcon?.trim().orEmpty()
            })
            macosTrayIconPath.set(project.provider {
                extension.extensions.serviceWorkerConfig.macosTrayIcon?.trim()?.takeIf { it.isNotEmpty() }
                    ?: extension.application.desktop.macosTrayIcon.trim()
            })
            macIconPath.set(project.provider {
                val resolved = GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.MACOS)
                if (resolved.appleIconExplicitlyProvided) resolved.iconPath.orEmpty() else ""
            })
            iosIconPath.set(project.provider {
                val resolved = GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.IOS)
                if (resolved.appleIconExplicitlyProvided) resolved.iconPath.orEmpty() else ""
            })
            ipadosIconPath.set(project.provider {
                val resolved = GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.IPADOS)
                if (resolved.appleIconExplicitlyProvided) resolved.iconPath.orEmpty() else ""
            })
            macIconExplicitlyProvided.set(project.provider {
                GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.MACOS).appleIconExplicitlyProvided
            })
            iosIconExplicitlyProvided.set(project.provider {
                GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.IOS).appleIconExplicitlyProvided
            })
            ipadosIconExplicitlyProvided.set(project.provider {
                GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.IPADOS).appleIconExplicitlyProvided
            })
            androidLauncherIconConfig.set(project.provider {
                extension.android.launcherIconConfig.toSerializedString()
            })
            androidSvgConverterVersion.set(project.provider {
                extension.android.svgConverterVersion
            })
            failOnSvgConversionWarnings.set(project.provider {
                extension.android.failOnSvgConversionWarnings
            })
            kotlinOutputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/resources/kotlin/commonMain"))
            backendOutputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/resources/backends"))
        }

        val compileWindowsResourcesTask = project.tasks.register(
            "compileGenericUiWindowsResources",
            GenericUiCompileWindowsResourcesTask::class.java
        ) {
            group = TASK_GROUP
            description = "Compiles generated Win32 genericui_resources.rc into genericui_resources.res and genericui_resources.o."
            dependsOn(resourcesTask)
            windowsResourceDirectory.set(project.layout.buildDirectory.dir("generated/genericui/resources/backends/windows"))
            rcScriptFile.set(project.layout.buildDirectory.file("generated/genericui/resources/backends/windows/genericui_resources.rc"))
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/resources/backends/windows/compiled"))
            outputResFile.set(project.layout.buildDirectory.file("generated/genericui/resources/backends/windows/compiled/genericui_resources.res"))
            outputObjectFile.set(project.layout.buildDirectory.file("generated/genericui/resources/backends/windows/compiled/genericui_resources.o"))
        }

        val validateAppTask = project.tasks.register(
            "genericuiValidateApp",
            GenericUiValidateAppTask::class.java
        ) {
            group = TASK_GROUP
            description = "Validates @MainAppFragment, @AppFragment, and @AppApplication declarations for all enabled GenericUI backend targets."
            projectRootPath.set(project.projectDir.absolutePath)
            extraSourceDirectories.set(project.provider {
                extension.sourceSets.allSpecs().flatMap { spec ->
                    spec.kotlinSrcDirs.mapNotNull { dir -> (dir as? File)?.absolutePath ?: (dir as? String) }
                }
            })
            enabledBackendIds.set(project.provider {
                GenericUiGenerateLaunchersTask.enabledAllBackendScopes(extension.backends).map { it.id }
            })
            generatedLauncherPackage.set(project.provider {
                val discovered = GenericUiFragmentScanner.scanProject(project.projectDir)
                GenericUiGenerateLaunchersTask.resolveGeneratedLauncherPackage(
                    extension = extension,
                    projectGroup = project.group.toString(),
                    discoveredFragments = discovered
                )
            })
            requireMainAppFragment.set(project.provider { extension.application.createExecutableBinaries })
            enabledExtensionModulePaths.set(project.provider {
                val all = linkedSetOf<String>()
                all.addAll(extension.extensions.enabledExtensionModulePaths())
                for (scope in GenericUiGenerateLaunchersTask.enabledAllBackendScopes(extension.backends)) {
                    all.addAll(extension.extensions.enabledExtensionModulePathsForTarget(scope))
                }
                all.toList()
            })
            extensionOptions.set(project.provider { buildPluginExtensionOptions(extension) })
            validationReportFile.set(project.layout.buildDirectory.file("generated/genericui/validation/app-validation.txt"))
        }

        val generateLaunchersTask = project.tasks.register(
            "generateGenericUiLaunchers",
            GenericUiGenerateLaunchersTask::class.java
        ) {
            group = TASK_GROUP
            description = "Generates per-backend GenericUiAppDescriptor and launcher main() entrypoints for @MainAppFragment."
            projectRootPath.set(project.projectDir.absolutePath)
            extraSourceDirectories.set(project.provider {
                extension.sourceSets.allSpecs().flatMap { spec ->
                    spec.kotlinSrcDirs.mapNotNull { dir -> (dir as? File)?.absolutePath ?: (dir as? String) }
                }
            })
            enabledBackendIds.set(project.provider {
                GenericUiGenerateLaunchersTask.enabledAllBackendScopes(extension.backends).map { it.id }
            })
            generatedPackageName.set(project.provider {
                val discovered = GenericUiFragmentScanner.scanProject(project.projectDir)
                GenericUiGenerateLaunchersTask.resolveGeneratedLauncherPackage(
                    extension = extension,
                    projectGroup = project.group.toString(),
                    discoveredFragments = discovered
                )
            })
            resourcesEnabled.set(project.provider { extension.resources.enabled })
            resourcePackageName.set(project.provider {
                val configured = extension.resources.packageName.trim()
                if (configured.isNotEmpty()) {
                    configured
                } else {
                    val grp = project.group.toString().trim()
                    if (grp.isNotEmpty() && grp != "unspecified") "$grp.res" else "com.nexustools.genericui.generated.res"
                }
            })
            backendAppIds.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.appId }
            })
            backendAppNames.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.appName }
            })
            backendAppVersions.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.appVersion }
            })
            backendWindowTitles.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.windowTitle }
            })
            backendWindowWidths.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.windowWidth }
            })
            backendWindowHeights.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.windowHeight }
            })
            backendMinWindowWidths.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.minWindowWidth }
            })
            backendMinWindowHeights.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.minWindowHeight }
            })
            backendWindowResizables.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.windowResizable }
            })
            backendFullscreens.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.fullscreen }
            })
            backendWindowAlwaysOnTops.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.windowAlwaysOnTop }
            })
            backendWindowAspectRatios.set(project.provider {
                val aspectRatios = linkedMapOf<String, Double>()
                for (backendScope in GenericUiGenerateLaunchersTask.enabledLauncherBackendScopes(extension.backends)) {
                    val ratio = resolveVariantWithProjectFallbacks(project, extension, backendScope).windowAspectRatio
                    if (ratio != null && ratio.isFinite() && ratio > 0.0) {
                        aspectRatios[backendScope.id] = ratio
                    }
                }
                aspectRatios
            })
            backendAppleIconExplicitlyProvided.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.appleIconExplicitlyProvided }
            })
            backendWindowSizeExplicitlySet.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.windowSizeExplicitlySet }
            })
            backendFullscreenExplicitlySet.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.fullscreenExplicitlySet }
            })
            backendThemeModes.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.themeMode }
            })
            backendThemeColors.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.themeColor }
            })
            backendLightThemeColors.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.lightThemeColors }
            })
            backendDarkThemeColors.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.darkThemeColors }
            })
            backendHasCustomThemeOverrides.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.hasCustomThemeOverrides }
            })
            backendIconPaths.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.iconPath ?: "" }
            })
            backendGlobalStyleSheets.set(project.provider {
                resolveBackendVariantMap(project, extension) { it.globalStyleSheet ?: "" }
            })
            backendProviderInstallerCalls.set(project.provider {
                val schemaPkg = resolveSchemaRegistrarPackage(project, extension)
                val resolvedBaseName = extension.application.baseName.trim().ifEmpty { project.name }
                resolveBackendVariantMap(project, extension) { resolved ->
                    extension.extensions.providerInstallerCallsForBackend(
                        backendScope = resolved.targetScope,
                        extensionOverrides = resolved.extensionOverrides,
                        generatedPackageName = schemaPkg,
                        baseName = resolvedBaseName
                    )
                }
            })
            precompiledStyleSheetObjectFqns.set(project.provider {
                if (!extension.styles.compileSass || !extension.styles.precompileToKotlin) {
                    emptyList()
                } else {
                    val stylesDir = resolveStylesSourceDir(project, extension.styles.sourceDir)
                    val pkg = resolveStylesKotlinPackageName(project, extension)
                    val fqns = LinkedHashSet<String>()
                    if (stylesDir != null && stylesDir.isDirectory) {
                        stylesDir.walkTopDown()
                            .filter {
                                it.isFile &&
                                    !it.name.startsWith("_") &&
                                    !it.nameWithoutExtension.equals("theme", ignoreCase = true) &&
                                    it.extension.lowercase() in setOf("scss", "sass", "css")
                            }
                            .sortedBy { it.relativeTo(stylesDir).invariantSeparatorsPath }
                            .forEach { file ->
                                val objName = GenericUiSassCompileTask.toPascalIdentifier(file.nameWithoutExtension) +
                                    "PrecompiledStyleSheet"
                                fqns.add(if (pkg.isNotEmpty()) "$pkg.$objName" else objName)
                            }
                    }
                    fqns.toList()
                }
            })
            themeStyleSheetObjectFqn.set(project.provider {
                resolveGeneratedThemeStyleSheetObjectFqn(project, extension)
            })
            generateVisualWindowsTestHelper.set(project.provider { extension.testing.visualOnWindows })
            testingEnabled.set(project.provider { extension.testing.enabled })
            baseName.set(project.provider { extension.application.baseName.trim().ifEmpty { project.name } })
            enabledExtensionModulePaths.set(project.provider { extension.extensions.enabledExtensionModulePaths() })
            backendEnabledExtensionModulePaths.set(project.provider {
                val map = linkedMapOf<String, List<String>>()
                for (backendScope in GenericUiGenerateLaunchersTask.enabledLauncherBackendScopes(extension.backends)) {
                    map[backendScope.id] = extension.extensions.enabledExtensionModulePathsForTarget(backendScope)
                }
                map
            })
            binaryCodecsEnabled.set(project.provider { extension.includeCommon })
            extensionOptions.set(project.provider { buildPluginExtensionOptionsStatic(extension) })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/launchers"))
        }

        val generateBinaryCodecsTask = project.tasks.register(
            "generateGenericUiBinaryCodecs",
            GenericUiGenerateBinaryCodecsTask::class.java
        ) {
            group = TASK_GROUP
            description = "Scans @BinarySerializable data classes and generates reflection-free DataClassCodec implementations and GeneratedBinaryCodecRegistrar."
            binaryCodecsEnabled.set(project.provider { extension.includeCommon })
            projectRootPath.set(project.projectDir.absolutePath)
            sourceDirectories.set(project.provider {
                collectBinaryCodecSourceDirectories(project, extension).map { it.absolutePath }
            })
            sourceFiles.from(project.provider {
                collectBinaryCodecSourceDirectories(project, extension)
                    .filter { it.exists() && it.isDirectory }
                    .map { dir -> project.fileTree(dir).matching { include("**/*.kt") } }
            })
            generatedPackageName.set(project.provider {
                resolveGeneratedPackageForProject(project, extension)
            })
            enabledExtensionModulePaths.set(project.provider { extension.extensions.enabledExtensionModulePaths() })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/binaryCodecs/commonMain/kotlin"))
        }

        val generateSchemaSourcesTask = project.tasks.register(
            "generateGenericUiSchemas",
            GenericUiGenerateSchemaSourcesTask::class.java
        ) {
            group = TASK_GROUP
            description = "Scans @SchemaData data classes and generates KMP SchemaDescriptor implementations and GeneratedSchemaRegistrar."
            schemaEnabled.set(project.provider {
                extension.extensions.isAnySchemaEnabled(extension.backends)
            })
            sourceDirectories.set(project.provider {
                collectSchemaSourceDirectories(project, extension).map { it.absolutePath }
            })
            sourceFiles.from(project.provider {
                collectSchemaSourceDirectories(project, extension)
                    .filter { it.exists() && it.isDirectory }
                    .map { dir -> project.fileTree(dir).matching { include("**/*.kt") } }
            })
            basePackageName.set(project.provider {
                resolveGeneratedPackageForProject(project, extension)
            })
            configuredRegistrarPackage.set(project.provider {
                extension.extensions.extensionOptions[
                    com.nexustools.genericui.plugin.spi.SchemaExtensionContributor.OPTION_REGISTRAR_PACKAGE
                ]?.trim().orEmpty()
            })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/schema/commonMain/kotlin"))
        }

        val generateServiceWorkerCodecsTask = project.tasks.register(
            "generateGenericUiServiceWorkerCodecs",
            GenericUiGenerateServiceWorkerCodecsTask::class.java
        ) {
            group = TASK_GROUP
            description = "Validates @MainServiceWorker declarations and generates reflection-free @ServiceWorkerMessage codecs and registrar."
            serviceWorkerEnabled.set(project.provider { extension.extensions.serviceWorkerEnabled })
            projectRootPath.set(project.projectDir.absolutePath)
            projectNameValue.set(project.name)
            sourceDirectories.set(project.provider {
                collectSchemaSourceDirectories(project, extension).map { it.absolutePath }
            })
            sourceFiles.from(project.provider {
                collectSchemaSourceDirectories(project, extension)
                    .filter { it.exists() && it.isDirectory }
                    .map { dir -> project.fileTree(dir).matching { include("**/*.kt") } }
            })
            basePackageName.set(project.provider {
                resolveGeneratedPackageForProject(project, extension)
            })
            configuredRegistrarPackage.set(project.provider {
                extension.extensions.extensionOptions[
                    com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_REGISTRAR_PACKAGE
                ]?.trim().orEmpty()
            })
            appId.set(project.provider { resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.COMMON).appId })
            appName.set(project.provider { resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.COMMON).appName })
            appVersion.set(project.provider { resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.COMMON).appVersion })
            baseName.set(project.provider { extension.application.baseName.trim().ifEmpty { project.name } })
            enabledExtensionModulePaths.set(project.provider { extension.extensions.enabledExtensionModulePaths() })
            extensionOptions.set(project.provider { buildPluginExtensionOptionsStatic(extension) })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/serviceworker/commonMain/kotlin"))
        }

        val generateAndroidActivitiesTask = project.tasks.register(
            "generateGenericUiAndroidActivities",
            GenericUiGenerateAndroidActivitiesTask::class.java
        ) {
            group = TASK_GROUP
            description = "Generates one concrete AppCompatActivity per Android @MainAppFragment/@AppFragment and GeneratedAndroidApplication."
            androidEnabled.set(project.provider { extension.backends.androidEnabled })
            projectRootPath.set(project.projectDir.absolutePath)
            sourceDirectories.set(project.provider {
                extension.sourceSets.allSpecs().flatMap { spec ->
                    spec.kotlinSrcDirs.mapNotNull { dir -> (dir as? File)?.absolutePath ?: (dir as? String) }
                }
            })
            sourceFiles.from(project.provider {
                val extraDirs = collectConfiguredExtraKotlinDirs(project, extension)
                GenericUiFragmentScanner.collectCandidateSourceDirectories(project.projectDir, extraDirs)
                    .map { dir -> project.fileTree(dir).matching { include("**/*.kt") } }
            })
            generatedRegistrarPackageName.set(project.provider {
                resolveGeneratedPackageForProject(project, extension)
            })
            appId.set(project.provider { resolveAndroidVariant(project, extension).appId })
            appName.set(project.provider { resolveAndroidVariant(project, extension).appName })
            appVersion.set(project.provider { resolveAndroidVariant(project, extension).appVersion })
            packageName.set(project.provider { resolveAndroidVariant(project, extension).packageName })
            windowTitle.set(project.provider { resolveAndroidVariant(project, extension).windowTitle })
            windowWidth.set(project.provider { resolveAndroidVariant(project, extension).windowWidth })
            windowHeight.set(project.provider { resolveAndroidVariant(project, extension).windowHeight })
            minWindowWidth.set(project.provider { resolveAndroidVariant(project, extension).minWindowWidth })
            minWindowHeight.set(project.provider { resolveAndroidVariant(project, extension).minWindowHeight })
            windowResizable.set(project.provider { resolveAndroidVariant(project, extension).windowResizable })
            fullscreen.set(project.provider { resolveAndroidVariant(project, extension).fullscreen })
            windowAlwaysOnTop.set(project.provider { resolveAndroidVariant(project, extension).windowAlwaysOnTop })
            windowAspectRatio.set(project.provider { resolveAndroidVariant(project, extension).windowAspectRatio })
            appleIconExplicitlyProvided.set(project.provider { resolveAndroidVariant(project, extension).appleIconExplicitlyProvided })
            themeMode.set(project.provider { resolveAndroidVariant(project, extension).themeMode })
            themeColor.set(project.provider { resolveAndroidVariant(project, extension).themeColor })
            lightThemeColors.set(project.provider { resolveAndroidVariant(project, extension).lightThemeColors })
            darkThemeColors.set(project.provider { resolveAndroidVariant(project, extension).darkThemeColors })
            hasCustomThemeOverrides.set(project.provider { resolveAndroidVariant(project, extension).hasCustomThemeOverrides })
            iconPath.set(project.provider { resolveAndroidVariant(project, extension).iconPath ?: "" })
            globalStyleSheet.set(project.provider { resolveAndroidVariant(project, extension).globalStyleSheet ?: "" })
            precompiledStyleSheetObjectFqn.set(project.provider {
                val styleSheet = resolveAndroidVariant(project, extension).globalStyleSheet?.trim().orEmpty()
                if (styleSheet.isEmpty() || !extension.styles.precompileToKotlin) {
                    ""
                } else {
                    val fileName = styleSheet.substringAfterLast('/').substringAfterLast('\\')
                    val stem = fileName.substringBeforeLast('.')
                    val objName = if (stem.equals("theme", ignoreCase = true)) {
                        GenericUiSassCompileTask.GENERATED_THEME_STYLESHEET_OBJECT_NAME
                    } else {
                        GenericUiSassCompileTask.toPascalIdentifier(stem) + "PrecompiledStyleSheet"
                    }
                    val pkg = resolveStylesKotlinPackageName(project, extension)
                    if (pkg.isNotEmpty()) "$pkg.$objName" else objName
                }
            })
            themeStyleSheetObjectFqn.set(project.provider {
                resolveGeneratedThemeStyleSheetObjectFqn(project, extension)
            })
            providerInstallers.set(project.provider {
                val resolved = resolveAndroidVariant(project, extension)
                val schemaPkg = resolveSchemaRegistrarPackage(project, extension)
                val resolvedBaseName = extension.application.baseName.trim().ifEmpty { project.name }
                extension.extensions.providerInstallerCallsForBackend(
                    backendScope = GenericUiVariantScope.ANDROID,
                    extensionOverrides = resolved.extensionOverrides,
                    generatedPackageName = schemaPkg,
                    baseName = resolvedBaseName
                )
            })
            resourceRegistryInstallerExpression.set(project.provider {
                if (!extension.resources.enabled) {
                    ""
                } else {
                    val configured = extension.resources.packageName.trim()
                    val resPkg = if (configured.isNotEmpty()) {
                        configured
                    } else {
                        val grp = project.group.toString().trim()
                        if (grp.isNotEmpty() && grp != "unspecified") "$grp.res" else "com.nexustools.genericui.generated.res"
                    }
                    "$resPkg.GeneratedResourceRegistry.ensureInitialized()"
                }
            })
            enabledExtensionModulePaths.set(project.provider {
                extension.extensions.enabledExtensionModulePathsForTarget(GenericUiVariantScope.ANDROID)
            })
            binaryCodecsEnabled.set(project.provider { extension.includeCommon })
            extensionOptions.set(project.provider { buildPluginExtensionOptionsStatic(extension) })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/android/kotlin"))
        }

        val generateAndroidManifestTask = project.tasks.register(
            "generateGenericUiAndroidManifest",
            GenericUiGenerateAndroidManifestTask::class.java
        ) {
            group = TASK_GROUP
            description = "Generates AndroidManifest.xml with activities for all discovered Android @MainAppFragment/@AppFragment classes and services."
            androidEnabled.set(project.provider { extension.backends.androidEnabled })
            projectRootPath.set(project.projectDir.absolutePath)
            sourceDirectories.set(project.provider {
                extension.sourceSets.allSpecs().flatMap { spec ->
                    spec.kotlinSrcDirs.mapNotNull { dir -> (dir as? File)?.absolutePath ?: (dir as? String) }
                }
            })
            sourceFiles.from(project.provider {
                val extraDirs = collectConfiguredExtraKotlinDirs(project, extension)
                GenericUiFragmentScanner.collectCandidateSourceDirectories(project.projectDir, extraDirs)
                    .map { dir -> project.fileTree(dir).matching { include("**/*.kt") } }
            })
            appId.set(project.provider { resolveAndroidVariant(project, extension).appId })
            appName.set(project.provider { resolveAndroidVariant(project, extension).appName })
            appVersion.set(project.provider { resolveAndroidVariant(project, extension).appVersion })
            packageName.set(project.provider { resolveAndroidVariant(project, extension).packageName })
            manifestModelJson.set(project.provider {
                GenericUiAndroidManifestModelSerializer.toJson(
                    manifestConfig = extension.android.manifestConfig,
                    services = extension.android.servicesConfig.services,
                    variantOverrides = extension.android.variants,
                    inheritedWebShortcuts = extension.application.desktop.webManifest.shortcuts
                )
            })
            manifestMergerVersion.set(project.provider { extension.android.manifestMergerVersion })
            enabledExtensionModulePaths.set(project.provider {
                extension.extensions.enabledExtensionModulePathsForTarget(GenericUiVariantScope.ANDROID)
            })
            extensionOptions.set(project.provider { buildPluginExtensionOptionsStatic(extension) })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/android-manifest"))
            outputManifestFile.set(project.layout.buildDirectory.file("generated/genericui/android-manifest/AndroidManifest.xml"))
            outputResDirectory.set(project.layout.buildDirectory.dir("generated/genericui/android-manifest/res"))
        }

        val generateIosAppTask = project.tasks.register(
            "generateGenericUiIosApp",
            GenericUiGenerateIosAppTask::class.java
        ) {
            group = TASK_GROUP
            description = "Generates the minimal Swift/Xcode iOS host application project (build/genericui/iosApp/)."
            dependsOn(resourcesTask)
            iosEnabled.set(project.provider { extension.backends.iosEnabled })
            projectRootPath.set(project.projectDir.absolutePath)
            projectBuildPath.set(project.layout.buildDirectory.asFile.map { it.absolutePath })
            baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            appName.set(project.provider { resolveIosVariant(project, extension).appName })
            appId.set(project.provider { resolveIosVariant(project, extension).appId })
            appVersion.set(project.provider { resolveIosVariant(project, extension).appVersion })
            frameworkName.set(project.provider {
                GenericUiGenerateIosAppTask.sanitizeFrameworkName(
                    extension.application.baseName.trim().ifEmpty { project.name }
                )
            })
            deviceFamily.set(GenericUiGenerateIosAppTask.DEVICE_FAMILY_IPHONE)
            frameworkSearchSubdirDebug.set("debugFramework")
            frameworkSearchSubdirRelease.set("releaseFramework")
            iconPath.set(project.provider { resolveIosVariant(project, extension).iconPath ?: "" })
            fallbackIconPath.set(project.provider {
                extension.application.desktop.icon.trim().ifEmpty { extension.resources.icon.trim() }
            })
            appleIconExplicitlyProvided.set(project.provider {
                resolveIosVariant(project, extension).appleIconExplicitlyProvided
            })
            enabledExtensionModulePaths.set(project.provider {
                extension.extensions.enabledExtensionModulePathsForTarget(GenericUiVariantScope.IOS)
            })
            extensionOptions.set(project.provider { buildPluginExtensionOptionsStatic(extension) })
            outputDirectory.set(project.layout.buildDirectory.dir("genericui/iosApp"))
        }

        val generateIpadosAppTask = project.tasks.register(
            "generateGenericUiIpadosApp",
            GenericUiGenerateIosAppTask::class.java
        ) {
            group = TASK_GROUP
            description = "Generates the minimal Swift/Xcode iPadOS host application project (build/genericui/ipadosApp/)."
            dependsOn(resourcesTask)
            iosEnabled.set(project.provider { extension.backends.ipadosEnabled })
            projectRootPath.set(project.projectDir.absolutePath)
            projectBuildPath.set(project.layout.buildDirectory.asFile.map { it.absolutePath })
            baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            appName.set(project.provider { resolveIpadosVariant(project, extension).appName })
            appId.set(project.provider { resolveIpadosVariant(project, extension).appId })
            appVersion.set(project.provider { resolveIpadosVariant(project, extension).appVersion })
            frameworkName.set(project.provider {
                GenericUiGenerateIosAppTask.sanitizeFrameworkName(
                    extension.application.baseName.trim().ifEmpty { project.name }
                )
            })
            deviceFamily.set(GenericUiGenerateIosAppTask.DEVICE_FAMILY_IPAD)
            frameworkSearchSubdirDebug.set(project.provider {
                if (extension.backends.iosEnabled) "ipadosDebugFramework" else "debugFramework"
            })
            frameworkSearchSubdirRelease.set(project.provider {
                if (extension.backends.iosEnabled) "ipadosReleaseFramework" else "releaseFramework"
            })
            iconPath.set(project.provider { resolveIpadosVariant(project, extension).iconPath ?: "" })
            fallbackIconPath.set(project.provider {
                extension.application.desktop.icon.trim().ifEmpty { extension.resources.icon.trim() }
            })
            appleIconExplicitlyProvided.set(project.provider {
                resolveIpadosVariant(project, extension).appleIconExplicitlyProvided
            })
            enabledExtensionModulePaths.set(project.provider {
                extension.extensions.enabledExtensionModulePathsForTarget(GenericUiVariantScope.IPADOS)
            })
            extensionOptions.set(project.provider { buildPluginExtensionOptionsStatic(extension) })
            outputDirectory.set(project.layout.buildDirectory.dir("genericui/ipadosApp"))
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
            windowsIconPath.set(project.provider {
                extension.application.desktop.windowsIcon.trim().ifEmpty {
                    GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.WINDOWS).iconPath.orEmpty()
                }
            })
            appleIconPath.set(project.provider {
                extension.application.desktop.appleIcon.trim().ifEmpty {
                    extension.resources.appleIcon.trim()
                }
            })
            macIconPath.set(project.provider {
                val resolvedMac = GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.MACOS)
                if (resolvedMac.appleIconExplicitlyProvided) resolvedMac.iconPath.orEmpty() else ""
            })
            macAppleIconExplicitlyProvided.set(project.provider {
                GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.MACOS).appleIconExplicitlyProvided
            })
            linuxIconPath.set(project.provider {
                extension.application.desktop.linuxIcon.trim().ifEmpty {
                    val linuxScope = if (!extension.backends.gtkEnabled && extension.backends.kdeEnabled) {
                        GenericUiVariantScope.KDE
                    } else {
                        GenericUiVariantScope.GTK
                    }
                    GenericUiVariantResolver.resolve(extension, linuxScope).iconPath.orEmpty()
                }
            })
            fallbackIconPath.set(project.provider {
                extension.application.desktop.icon.trim().ifEmpty {
                    extension.resources.icon.trim().ifEmpty {
                        extension.windowConfig.icon?.trim().orEmpty().ifEmpty {
                            GenericUiVariantResolver.resolve(extension, GenericUiVariantScope.COMMON).iconPath.orEmpty()
                        }
                    }
                }
            })
            embedWindowsResources.set(project.provider { extension.application.desktop.embedWindowsResources })
            macMinimumSystemVersion.set(project.provider { extension.application.desktop.macMinimumSystemVersion })
            macCategory.set(project.provider { extension.application.desktop.macCategory })
            macHighResolutionCapable.set(project.provider { extension.application.desktop.macHighResolutionCapable })
            macMultipleInstancesProhibited.set(project.provider { extension.application.desktop.macMultipleInstancesProhibited })
            macLocalNetworkUsageDescription.set(project.provider {
                extension.extensions.serviceWorkerConfig.macLocalNetworkUsageDescription?.trim()?.takeIf { it.isNotEmpty() }
                    ?: extension.application.desktop.macLocalNetworkUsageDescription.trim()
            })
            macBonjourServices.set(project.provider {
                (extension.extensions.serviceWorkerConfig.macBonjourServices +
                    extension.application.desktop.macBonjourServices)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
            })
            macosTrayIconPath.set(project.provider {
                extension.extensions.serviceWorkerConfig.macosTrayIcon?.trim()?.takeIf { it.isNotEmpty() }
                    ?: extension.application.desktop.macosTrayIcon.trim()
            })
            linuxCategories.set(project.provider { extension.application.desktop.linuxCategories })
            linuxStartupWmClass.set(project.provider { extension.application.desktop.linuxStartupWmClass })
            linuxTerminal.set(project.provider { extension.application.desktop.linuxTerminal })
            customResourceEntries.set(project.provider { extension.application.desktop.customResourceEntries })
            bundledResourcePaths.set(project.provider { extension.application.desktop.bundledResourcePaths })
            enabledExtensionModulePaths.set(project.provider {
                extension.extensions.enabledExtensionModulePathsForTarget(GenericUiVariantScope.DESKTOP_COMMON)
            })
            extensionOptions.set(project.provider { buildPluginExtensionOptionsStatic(extension) })
            outputDirectory.set(project.layout.buildDirectory.dir("generated/genericui/desktop-resources"))
        }

        fun configureWebDistTask(
            task: GenericUiWebDistributionTask,
            variant: String,
            outputRelPath: String
        ) {
            configureWebDistributionTaskInstance(project, extension, task, variant, outputRelPath)
        }

        val webDistTask = project.tasks.register(
            "packageGenericUiWebDistribution",
            GenericUiWebDistributionTask::class.java
        ) {
            configureWebDistTask(this, variant = "development", outputRelPath = "dist/web/development")
        }

        val webDevDistTask = project.tasks.register(
            "packageGenericUiWebDevelopmentDistribution",
            GenericUiWebDistributionTask::class.java
        ) {
            configureWebDistTask(this, variant = "development", outputRelPath = "dist/web/development")
        }

        val webProdDistTask = project.tasks.register(
            "packageGenericUiWebProductionDistribution",
            GenericUiWebDistributionTask::class.java
        ) {
            configureWebDistTask(this, variant = "production", outputRelPath = "dist/web/production")
        }

        fun configureAndroidApkTask(
            task: GenericUiPackageAndroidApkTask,
            variant: String,
            outputRelPath: String,
            secondaryOutputRelPath: String? = null,
            abi: String = ""
        ) {
            task.group = TASK_GROUP
            val abiLabel = if (abi.isBlank()) "DEX universal" else abi
            task.description = "Packages and signs the GenericUI Android ($abiLabel $variant) APK using aapt2, d8, zipalign, and apksigner into build/$outputRelPath."
            task.androidEnabled.set(project.provider { extension.backends.androidEnabled })
            task.projectRootPath.set(project.projectDir.absolutePath)
            task.baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            task.appId.set(project.provider {
                val baseId = resolveAndroidVariant(project, extension).appId
                val suffix = extension.android.variants[variant]?.applicationIdSuffix.orEmpty()
                baseId + suffix
            })
            task.appVersion.set(project.provider {
                val variantCfg = extension.android.variants[variant]
                val explicitName = variantCfg?.manifestConfig?.versionName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: extension.android.manifestConfig.versionName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: resolveAndroidVariant(project, extension).appVersion
                val suffix = variantCfg?.versionNameSuffix.orEmpty()
                explicitName + suffix
            })
            task.versionCode.set(project.provider {
                val variantCfg = extension.android.variants[variant]
                val resolvedVerName = variantCfg?.manifestConfig?.versionName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: extension.android.manifestConfig.versionName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: resolveAndroidVariant(project, extension).appVersion
                variantCfg?.manifestConfig?.versionCode
                    ?: extension.android.manifestConfig.versionCode
                    ?: GenericUiAndroidManifestXmlGenerator.deriveVersionCode(resolvedVerName)
            })
            task.minSdk.set(project.provider {
                val variantMin = extension.android.variants[variant]?.manifestConfig?.takeIf {
                    "minSdk" in it.explicitFields
                }?.minSdk
                variantMin
                    ?: extension.android.manifestConfig.takeIf { "minSdk" in it.explicitFields && it.minSdk > 0 }?.minSdk
                    ?: extension.application.androidMinSdk
            })
            task.compileSdk.set(project.provider {
                val variantCompile = extension.android.variants[variant]?.manifestConfig?.takeIf {
                    "compileSdk" in it.explicitFields
                }?.compileSdk
                variantCompile
                    ?: extension.android.manifestConfig.takeIf { "compileSdk" in it.explicitFields && it.compileSdk > 0 }?.compileSdk
                    ?: extension.application.androidCompileSdk
            })
            task.targetSdk.set(project.provider {
                val variantCfg = extension.android.variants[variant]?.manifestConfig
                val variantTarget = variantCfg?.takeIf { "targetSdk" in it.explicitFields }?.targetSdk
                variantTarget
                    ?: extension.android.manifestConfig.takeIf { "targetSdk" in it.explicitFields && it.targetSdk > 0 }?.targetSdk
                    ?: extension.android.manifestConfig.takeIf { "compileSdk" in it.explicitFields && it.compileSdk > 0 }?.compileSdk
                    ?: extension.application.androidCompileSdk
            })
            task.buildVariant.set(variant)
            task.targetAbi.set(abi)
            task.enabledGenericUiModulePaths.set(project.provider {
                resolveEnabledAndroidGenericUiModulePaths(project, extension)
            })
            task.includedBuildRootPaths.set(project.provider {
                val roots = LinkedHashSet<String>()
                runCatching {
                    project.gradle.includedBuilds.forEach { ib ->
                        roots.add(ib.projectDir.absolutePath)
                    }
                }
                listOfNotNull(
                    File(project.rootProject.projectDir, "lib/GenericUI"),
                    File(project.projectDir, "lib/GenericUI"),
                    project.rootProject.projectDir.parentFile?.let { File(it, "GenericUI") }
                ).forEach { candidate ->
                    if (candidate.isDirectory) {
                        roots.add(candidate.absolutePath)
                    }
                }
                if (project.rootProject.name.equals("GenericUI", ignoreCase = true) ||
                    File(project.rootProject.projectDir, "mobile/build.gradle.kts").isFile
                ) {
                    roots.add(project.rootProject.projectDir.absolutePath)
                }
                roots.toList()
            })
            task.manifestFile.set(
                project.layout.file(
                    generateAndroidManifestTask.map { genTask ->
                        val variantFile = genTask.outputDirectory.file("variants/$variant/AndroidManifest.xml").get().asFile
                        if (variantFile.isFile) variantFile else genTask.outputManifestFile.get().asFile
                    }
                )
            )
            task.generatedManifestResDir.set(
                project.layout.dir(
                    generateAndroidManifestTask.map { genTask ->
                        val variantRes = genTask.outputDirectory.dir("variants/$variant/res").get().asFile
                        if (variantRes.isDirectory) variantRes else genTask.outputResDirectory.get().asFile
                    }
                )
            )
            task.generatedAndroidResDir.set(project.layout.buildDirectory.dir("generated/genericui/resources/backends/android/res"))
            task.jniLibsDirectories.set(project.provider { GenericUiNativeBuildResolver.resolveJniLibsDirPaths(project, extension) })
            task.pageSize16Kb.set(project.provider {
                val libs = GenericUiNativeBuildResolver.resolveJniLibraries(project, extension)
                val cmakes = GenericUiNativeBuildResolver.resolveJniCmakes(project, extension)
                (libs.isEmpty() && cmakes.isEmpty()) ||
                    (libs.all { it.pageSize16Kb } && cmakes.all { it.pageSize16Kb })
            })
            task.outputDirectory.set(project.layout.buildDirectory.dir(outputRelPath))
            if (secondaryOutputRelPath != null) {
                task.secondaryOutputDirectory.set(project.layout.buildDirectory.dir(secondaryOutputRelPath))
            }
            task.outputs.upToDateWhen { false }
        }

        val androidDevApkTask = project.tasks.register(
            "packageGenericUiAndroidDevelopmentApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(
                task = this,
                variant = "development",
                outputRelPath = "dist/android/dex/development",
                secondaryOutputRelPath = "dist/android/development"
            )
        }

        val androidProdApkTask = project.tasks.register(
            "packageGenericUiAndroidProductionApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(
                task = this,
                variant = "production",
                outputRelPath = "dist/android/dex/production",
                secondaryOutputRelPath = "dist/android/production"
            )
        }

        val androidX32DevApkTask = project.tasks.register(
            "packageGenericUiAndroidX32DevelopmentApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "development", outputRelPath = "dist/android/x32/development", abi = "x86")
        }

        val androidX32ProdApkTask = project.tasks.register(
            "packageGenericUiAndroidX32ProductionApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "production", outputRelPath = "dist/android/x32/production", abi = "x86")
        }

        val androidX64DevApkTask = project.tasks.register(
            "packageGenericUiAndroidX64DevelopmentApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "development", outputRelPath = "dist/android/x64/development", abi = "x86_64")
        }

        val androidX64ProdApkTask = project.tasks.register(
            "packageGenericUiAndroidX64ProductionApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "production", outputRelPath = "dist/android/x64/production", abi = "x86_64")
        }

        val androidArm32DevApkTask = project.tasks.register(
            "packageGenericUiAndroidArm32DevelopmentApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "development", outputRelPath = "dist/android/arm32/development", abi = "armeabi-v7a")
        }

        val androidArm32ProdApkTask = project.tasks.register(
            "packageGenericUiAndroidArm32ProductionApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "production", outputRelPath = "dist/android/arm32/production", abi = "armeabi-v7a")
        }

        val androidArm64DevApkTask = project.tasks.register(
            "packageGenericUiAndroidArm64DevelopmentApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "development", outputRelPath = "dist/android/arm64/development", abi = "arm64-v8a")
        }

        val androidArm64ProdApkTask = project.tasks.register(
            "packageGenericUiAndroidArm64ProductionApk",
            GenericUiPackageAndroidApkTask::class.java
        ) {
            configureAndroidApkTask(this, variant = "production", outputRelPath = "dist/android/arm64/production", abi = "arm64-v8a")
        }

        val androidApkTaskProviders = listOf(
            androidDevApkTask,
            androidProdApkTask,
            androidX32DevApkTask,
            androidX32ProdApkTask,
            androidX64DevApkTask,
            androidX64ProdApkTask,
            androidArm32DevApkTask,
            androidArm32ProdApkTask,
            androidArm64DevApkTask,
            androidArm64ProdApkTask
        )

        fun configureSwingJarTask(
            task: GenericUiPackageSwingJarTask,
            variant: String,
            outputRelPath: String
        ) {
            task.group = TASK_GROUP
            task.description = "Packages all classes, resources, GenericUI modules, and JVM runtime libraries into a single executable Swing ($variant) fat JAR in build/$outputRelPath."
            task.swingEnabled.set(project.provider { extension.backends.swingConfig.enabled })
            task.projectRootPath.set(project.projectDir.absolutePath)
            task.baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            task.appName.set(project.provider {
                resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.SWING).appName
            })
            task.appVersion.set(project.provider {
                resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.SWING).appVersion
            })
            task.mainClass.set(project.provider {
                extension.application.mainClass.trim()
            })
            task.generatedLauncherPackage.set(project.provider {
                val discovered = GenericUiFragmentScanner.scanProject(project.projectDir)
                GenericUiGenerateLaunchersTask.resolveGeneratedLauncherPackage(
                    extension = extension,
                    projectGroup = project.group.toString(),
                    discoveredFragments = discovered
                )
            })
            task.buildVariant.set(variant)
            task.enabledGenericUiModulePaths.set(project.provider {
                resolveEnabledSwingGenericUiModulePaths(project, extension)
            })
            task.requireNjsClasses.set(project.provider {
                ":extensions:ui:webview" in resolveEnabledSwingGenericUiModulePaths(project, extension)
            })
            task.includedBuildRootPaths.set(project.provider {
                val roots = LinkedHashSet<String>()
                runCatching {
                    project.gradle.includedBuilds.forEach { ib ->
                        roots.add(ib.projectDir.absolutePath)
                    }
                }
                listOfNotNull(
                    File(project.rootProject.projectDir, "lib/GenericUI"),
                    File(project.projectDir, "lib/GenericUI"),
                    project.rootProject.projectDir.parentFile?.let { File(it, "GenericUI") },
                    project.projectDir.parentFile?.parentFile?.parentFile?.takeIf { File(it, "desktop/build.gradle.kts").isFile }
                ).forEach { candidate ->
                    if (candidate.isDirectory) {
                        roots.add(candidate.absolutePath)
                    }
                }
                if (project.rootProject.name.equals("GenericUI", ignoreCase = true) ||
                    File(project.rootProject.projectDir, "desktop/build.gradle.kts").isFile
                ) {
                    roots.add(project.rootProject.projectDir.absolutePath)
                }
                roots.toList()
            })
            task.jniLibsDirectories.set(project.provider { GenericUiNativeBuildResolver.resolveJniLibsDirPaths(project, extension) })
            task.outputDirectory.set(project.layout.buildDirectory.dir(outputRelPath))
            task.outputs.upToDateWhen { false }
        }

        val swingDevJarTask = project.tasks.register(
            "packageGenericUiSwingDevelopmentJar",
            GenericUiPackageSwingJarTask::class.java
        ) {
            configureSwingJarTask(this, variant = "development", outputRelPath = "dist/swing/development")
        }

        val swingProdJarTask = project.tasks.register(
            "packageGenericUiSwingProductionJar",
            GenericUiPackageSwingJarTask::class.java
        ) {
            configureSwingJarTask(this, variant = "production", outputRelPath = "dist/swing/production")
        }

        val swingJarTaskProviders = listOf(
            swingDevJarTask,
            swingProdJarTask
        )

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
                println("Icons:      win=${extension.application.desktop.windowsIcon}, apple=${extension.application.desktop.appleIcon}, mac=${extension.application.desktop.macIcon}, ios=${extension.application.desktop.iosIcon}, ipados=${extension.application.desktop.ipadosIcon}, linux=${extension.application.desktop.linuxIcon}, web=${extension.application.desktop.webIcon}")
            }
        }

        project.afterEvaluate {
            GenericUiMultiplatformConfigurator.configureMultiplatform(project, extension)
            GenericUiMultiplatformConfigurator.registerBuildTargetTasks(project, extension)
            GenericUiMultiplatformConfigurator.applySourceSetDependencies(project, extension)
            GenericUiNativeBuildResolver.configureKotlinNativeInteropsAndLinks(project, extension)
            GenericUiNativeBuildResolver.configureJniTaskWiring(
                project = project,
                extension = extension,
                fetchTaskName = fetchNativeSourcesTask.name,
                compileTaskName = compileJniTask.name,
                androidApkTaskNames = androidApkTaskProviders.map { it.name },
                swingJarTaskNames = swingJarTaskProviders.map { it.name }
            )
            if (GenericUiNativeBuildResolver.hasAnyCompilableJniConfiguration(extension)) {
                for (jarTaskProvider in swingJarTaskProviders) {
                    jarTaskProvider.configure {
                        bundledNativeLibrariesManifest.set(compileJniTask.flatMap { it.hostBundleManifestFile })
                    }
                }
            }
            sassTask.configure {
                val resolvedAfterEval = resolveStylesSourceDir(project, extension.styles.sourceDir)
                if (resolvedAfterEval != null) {
                    sourceDirectory.set(resolvedAfterEval)
                }
                for (otherProj in collectTransitiveProjectDependencies(project)) {
                    dependsOn(otherProj.tasks.matching {
                        it.name == "generateModuleResources" || it.name == "compileGenericUiResources"
                    })
                }
            }
            wireExtensionAndBackendModules(project, extension)
            wireTestingModules(project, extension)
            wireOptionalSwingGraphicsDependencies(project, extension)
            wireGeneratedResourceKotlinSources(project, extension, resourcesTask.name)
            wireGeneratedSchemaKotlinSources(project, extension, generateSchemaSourcesTask.name)
            wireGeneratedServiceWorkerCodecSources(project, extension, generateServiceWorkerCodecsTask.name)
            wireGeneratedBinaryCodecSources(project, extension, generateBinaryCodecsTask.name)
            resourcesTask.configure {
                for (otherProj in collectTransitiveProjectDependencies(project)) {
                    dependsOn(otherProj.tasks.matching {
                        it.name == "generateModuleResources" || it.name == "compileGenericUiResources"
                    })
                }
            }
            wireGeneratedLauncherAndAndroidSources(
                project = project,
                extension = extension,
                validateAppTaskName = validateAppTask.name,
                generateLaunchersTaskName = generateLaunchersTask.name,
                generateAndroidActivitiesTaskName = generateAndroidActivitiesTask.name,
                generateAndroidManifestTaskName = generateAndroidManifestTask.name,
                generateIosAppTaskName = generateIosAppTask.name,
                generateIpadosAppTaskName = generateIpadosAppTask.name
            )
            wirePrecompiledSassKotlinSources(project, extension, sassTask.name)
            wireGeneratedWebCssResources(project, extension, sassTask.name)
            wireDesktopResourceTasks(project, desktopResourcesTask.name)
            wireCompanionDependencyBuildTasks(project, extension, desktopResourcesTask.name)
            wireBuildLifecycleTasks(project, angleTask.name, vulkanTask.name, sassTask.name, resourcesTask.name, desktopResourcesTask.name)
            val webDistTaskNames = setOf(
                "packageGenericUiWebDistribution",
                "packageGenericUiWebDevelopmentDistribution",
                "packageGenericUiWebProductionDistribution",
                "packageGenericUiWebTestDistribution"
            )
            project.tasks.withType(GenericUiWebDistributionTask::class.java).configureEach {
                mustRunAfter(
                    project.tasks.matching { t ->
                        t.name == "compileGenericUiSass" ||
                            t.name.endsWith("ProcessResources") ||
                            (t.name.startsWith("compile") && (t.name.contains("KotlinWasmJs") || t.name.contains("KotlinJs"))) ||
                            t.name.endsWith("CompileSync") ||
                            t.name.endsWith("Webpack") ||
                            (t.name.endsWith("Distribution") && t.name !in webDistTaskNames)
                    }
                )
            }
            for (apkTaskProvider in androidApkTaskProviders) {
                apkTaskProvider.configure {
                    if (extension.backends.androidEnabled) {
                        dependsOn(
                            sassTask,
                            resourcesTask,
                            generateAndroidManifestTask,
                            generateAndroidActivitiesTask
                        )
                        listOf(
                            "compileAndroidMain",
                            "bundleAndroidMainClassesToRuntimeJar",
                            "bundleAndroidMainClassesToCompileJar",
                            "compileDebugKotlinAndroid",
                            "compileReleaseKotlinAndroid"
                        ).forEach { taskName ->
                            project.tasks.findByName(taskName)?.let { dependsOn(it) }
                        }
                        val artifactTypeAttr = org.gradle.api.attributes.Attribute.of("artifactType", String::class.java)
                        listOf(
                            "androidRuntimeClasspath",
                            "androidCompileClasspath",
                            "debugRuntimeClasspath",
                            "releaseRuntimeClasspath"
                        ).forEach { cfgName ->
                            val cfg = project.configurations.findByName(cfgName)
                            if (cfg != null && cfg.isCanBeResolved) {
                                for (artType in listOf("android-classes-jar", "jar")) {
                                    runCatching {
                                        dependsOn(
                                            cfg.incoming.artifactView {
                                                lenient(true)
                                                attributes {
                                                    attribute(artifactTypeAttr, artType)
                                                }
                                            }.files
                                        )
                                    }
                                }
                            }
                        }
                        val androidModules = resolveEnabledAndroidGenericUiModulePaths(project, extension)
                        for (modPath in androidModules) {
                            project.rootProject.findProject(modPath)?.let { subProj ->
                                subProj.tasks.findByName("bundleAndroidMainClassesToRuntimeJar")?.let { dependsOn(it) }
                                subProj.tasks.findByName("bundleAndroidMainClassesToCompileJar")?.let { dependsOn(it) }
                                subProj.tasks.findByName("compileAndroidMain")?.let { dependsOn(it) }
                            }
                            runCatching {
                                project.gradle.includedBuilds.forEach { ib ->
                                    val relModDir = File(ib.projectDir, modPath.removePrefix(":").replace(':', '/'))
                                    if (relModDir.isDirectory && File(relModDir, "build.gradle.kts").isFile) {
                                        dependsOn(ib.task("$modPath:bundleAndroidMainClassesToRuntimeJar"))
                                        dependsOn(ib.task("$modPath:bundleAndroidMainClassesToCompileJar"))
                                    }
                                }
                            }
                        }
                    }
                    mustRunAfter(
                        project.tasks.matching { t ->
                            t.name == "compileGenericUiSass" ||
                                t.name == "compileGenericUiResources" ||
                                t.name == "generateGenericUiAndroidManifest" ||
                                t.name == "generateGenericUiAndroidActivities" ||
                                t.name == "compileAndroidMain" ||
                                t.name == "bundleAndroidMainClassesToRuntimeJar" ||
                                t.name == "bundleAndroidMainClassesToCompileJar" ||
                                t.name == "compileDebugKotlinAndroid" ||
                                t.name == "compileReleaseKotlinAndroid" ||
                                (t.name.startsWith("compile") && t.name.contains("Android")) ||
                                (t.name.startsWith("link") && t.name.contains("AndroidNative"))
                        }
                    )
                }
            }
            for (swingJarTaskProvider in swingJarTaskProviders) {
                swingJarTaskProvider.configure {
                    if (extension.backends.swingConfig.enabled) {
                        dependsOn(
                            sassTask,
                            resourcesTask,
                            desktopResourcesTask,
                            validateAppTask,
                            generateLaunchersTask
                        )
                        listOf(
                            "compileKotlinJvm",
                            "jvmProcessResources",
                            "jvmMainClasses",
                            "jvmJar",
                            "compileKotlin",
                            "processResources",
                            "classes",
                            "jar"
                        ).forEach { taskName ->
                            project.tasks.findByName(taskName)?.let { dependsOn(it) }
                        }
                        val artifactTypeAttr = org.gradle.api.attributes.Attribute.of("artifactType", String::class.java)
                        listOf(
                            "jvmRuntimeClasspath",
                            "jvmCompileClasspath",
                            "runtimeClasspath",
                            "compileClasspath"
                        ).forEach { cfgName ->
                            val cfg = project.configurations.findByName(cfgName)
                            if (cfg != null && cfg.isCanBeResolved) {
                                runCatching {
                                    dependsOn(
                                        cfg.incoming.artifactView {
                                            lenient(true)
                                            attributes {
                                                attribute(artifactTypeAttr, "jar")
                                            }
                                        }.files
                                    )
                                }
                            }
                        }
                        val swingModules = resolveEnabledSwingGenericUiModulePaths(project, extension)
                        for (modPath in swingModules) {
                            project.rootProject.findProject(modPath)?.let { subProj ->
                                subProj.tasks.findByName("jvmJar")?.let { dependsOn(it) }
                                subProj.tasks.findByName("jar")?.let { dependsOn(it) }
                                subProj.tasks.findByName("compileKotlinJvm")?.let { dependsOn(it) }
                                subProj.tasks.findByName("compileKotlin")?.let { dependsOn(it) }
                                subProj.tasks.findByName("jvmProcessResources")?.let { dependsOn(it) }
                            }
                            runCatching {
                                project.gradle.includedBuilds.forEach { ib ->
                                    val relModDir = File(ib.projectDir, modPath.removePrefix(":").replace(':', '/'))
                                    val buildScript = File(relModDir, "build.gradle.kts")
                                    if (relModDir.isDirectory && buildScript.isFile) {
                                        val scriptText = buildScript.readText()
                                        if (scriptText.contains("kotlinJvm") || scriptText.contains("kotlin(\"jvm\")")) {
                                            dependsOn(ib.task("$modPath:jar"))
                                        } else {
                                            dependsOn(ib.task("$modPath:jvmJar"))
                                        }
                                    }
                                }
                            }
                        }
                        if (":extensions:ui:webview" in swingModules) {
                            project.rootProject.findProject(":NJS:common")?.tasks?.findByName("jvmJar")?.let { dependsOn(it) }
                            project.rootProject.findProject(":NJS:jvm")?.tasks?.findByName("jvmJar")?.let { dependsOn(it) }
                            runCatching {
                                project.gradle.includedBuilds.forEach { ib ->
                                    val isNjsBuild = ib.name.equals("NJS", ignoreCase = true) ||
                                        (File(ib.projectDir, "common/build.gradle.kts").isFile &&
                                            File(ib.projectDir, "jvm/build.gradle.kts").isFile)
                                    if (isNjsBuild) {
                                        dependsOn(ib.task(":common:jvmJar"))
                                        dependsOn(ib.task(":jvm:jvmJar"))
                                    }
                                }
                            }
                        }
                    }
                    mustRunAfter(
                        project.tasks.matching { t ->
                            t.name == "compileGenericUiSass" ||
                                t.name == "compileGenericUiResources" ||
                                t.name == "prepareGenericUiDesktopResources" ||
                                t.name == "generateGenericUiLaunchers" ||
                                t.name == "genericuiValidateApp" ||
                                t.name == "compileKotlinJvm" ||
                                t.name == "jvmProcessResources" ||
                                t.name == "jvmMainClasses" ||
                                t.name == "jvmJar" ||
                                t.name == "compileKotlin" ||
                                t.name == "classes" ||
                                t.name == "jar"
                        }
                    )
                }
            }
        }
    }

    private fun resolveEnabledSwingGenericUiModulePaths(
        project: Project,
        extension: GenericUiExtension
    ): List<String> {
        val paths = LinkedHashSet<String>()
        val backends = extension.backends
        if (extension.includeCommon) {
            paths.add(":common")
        }
        if (backends.requiresSystemModule) {
            paths.add(":system")
        }
        if (backends.requiresJvmModule) {
            paths.add(":jvm")
        }
        if (backends.requiresDesktopModule) {
            paths.add(":desktop")
        }
        for (extPath in extension.extensions.enabledExtensionModulePathsForTarget(GenericUiVariantScope.SWING)) {
            paths.add(extPath)
        }
        return paths.toList()
    }

    private fun resolveEnabledAndroidGenericUiModulePaths(
        project: Project,
        extension: GenericUiExtension
    ): List<String> {
        val paths = LinkedHashSet<String>()
        val backends = extension.backends
        if (extension.includeCommon) {
            paths.add(":common")
        }
        if (backends.requiresSystemModule) {
            paths.add(":system")
        }
        if (backends.requiresJvmModule) {
            paths.add(":jvm")
        }
        if (backends.requiresMobileModule) {
            paths.add(":mobile")
        }
        for (extPath in extension.extensions.enabledExtensionModulePathsForTarget(GenericUiVariantScope.ANDROID)) {
            paths.add(extPath)
        }
        return paths.toList()
    }

    private fun resolveStylesKotlinPackageName(
        project: Project,
        extension: GenericUiExtension
    ): String {
        val configured = extension.styles.kotlinPackageName.trim()
        if (configured.isNotEmpty() && configured != "com.nexustools.genericui.generated.styles") {
            return configured
        }
        val grp = project.group.toString().trim()
        if (grp.isNotEmpty() && grp != "unspecified") {
            return "$grp.styles"
        }
        val appPkg = extension.app.packageName.trim().ifEmpty { extension.app.id.trim() }
        if (appPkg.isNotEmpty() && appPkg != "com.nexustools.genericui.app") {
            return "$appPkg.styles"
        }
        return "com.nexustools.genericui.generated.styles"
    }

    private fun wireGeneratedLauncherAndAndroidSources(
        project: Project,
        extension: GenericUiExtension,
        validateAppTaskName: String,
        generateLaunchersTaskName: String,
        generateAndroidActivitiesTaskName: String,
        generateAndroidManifestTaskName: String,
        generateIosAppTaskName: String,
        generateIpadosAppTaskName: String
    ) {
        project.tasks.matching {
            it.name.startsWith("compile") &&
                !it.name.startsWith("compileGenericUi") &&
                (it.name.contains("Kotlin") || it.name.contains("Android"))
        }.configureEach {
            dependsOn(validateAppTaskName, generateLaunchersTaskName)
            if (extension.backends.androidEnabled &&
                (name.contains("Android", ignoreCase = true) || name == "compileKotlin")
            ) {
                dependsOn(generateAndroidActivitiesTaskName, generateAndroidManifestTaskName)
            }
            if (extension.backends.iosEnabled && name.contains("Ios", ignoreCase = true)) {
                dependsOn(generateIosAppTaskName)
            }
            if (extension.backends.ipadosEnabled && (name.contains("Ios", ignoreCase = true) || name.contains("Ipados", ignoreCase = true))) {
                dependsOn(generateIpadosAppTaskName)
            }
        }
    }

    private fun <T> resolveBackendVariantMap(
        project: Project,
        extension: GenericUiExtension,
        selector: (GenericUiResolvedVariantConfig) -> T
    ): Map<String, T> {
        val result = linkedMapOf<String, T>()
        for (backendScope in GenericUiGenerateLaunchersTask.enabledLauncherBackendScopes(extension.backends)) {
            val resolved = resolveVariantWithProjectFallbacks(project, extension, backendScope)
            result[backendScope.id] = selector(resolved)
        }
        return result
    }

    private fun resolveAndroidVariant(
        project: Project,
        extension: GenericUiExtension
    ): GenericUiResolvedVariantConfig =
        resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.ANDROID)

    private fun resolveIosVariant(
        project: Project,
        extension: GenericUiExtension
    ): GenericUiResolvedVariantConfig =
        resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.IOS)

    private fun resolveIpadosVariant(
        project: Project,
        extension: GenericUiExtension
    ): GenericUiResolvedVariantConfig =
        resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.IPADOS)

    private fun resolveWebVariant(
        project: Project,
        extension: GenericUiExtension
    ): GenericUiResolvedVariantConfig =
        resolveVariantWithProjectFallbacks(project, extension, GenericUiVariantScope.WEB)

    private fun resolveVariantWithProjectFallbacks(
        project: Project,
        extension: GenericUiExtension,
        backend: GenericUiVariantScope
    ): GenericUiResolvedVariantConfig {
        val base = GenericUiVariantResolver.resolve(extension, backend)
        val projectGroup = project.group.toString().trim().takeIf { it.isNotEmpty() && it != "unspecified" }
        val projectVersion = project.version.toString().trim().takeIf { it.isNotEmpty() && it != "unspecified" }
        val fallbackAppId = if (base.appId == "com.nexustools.genericui.app" && projectGroup != null) {
            projectGroup
        } else {
            base.appId
        }
        val fallbackAppName = if (base.appName == "GenericUI" && project.name.isNotBlank()) {
            project.name
        } else {
            base.appName
        }
        val fallbackVersion = if (base.appVersion == "1.0.0" && projectVersion != null) {
            projectVersion
        } else {
            base.appVersion
        }
        return base.copy(
            appId = fallbackAppId,
            appName = fallbackAppName,
            appVersion = fallbackVersion,
            packageName = base.packageName.ifBlank { fallbackAppId }
        )
    }

    private fun resolveUpstreamResourceManifestPaths(project: Project): List<String> {
        val paths = linkedSetOf<String>()
        for (otherProj in collectTransitiveProjectDependencies(project)) {
            val buildDir = otherProj.layout.buildDirectory.asFile.get()
            paths.add(
                File(buildDir, "generated/genericui/module-resources/resources/META-INF/genericui/module-resources")
                    .absolutePath
            )
            paths.add(
                File(buildDir, "generated/genericui/resources/backends/manifest/genericui-module-resources.json")
                    .absolutePath
            )
        }
        val includeDir = findGenericUiIncludeDir(project)
        if (includeDir != null) {
            val commonModRes = File(
                includeDir,
                "common/build/generated/genericui/module-resources/resources/META-INF/genericui/module-resources"
            )
            if (commonModRes.exists()) paths.add(commonModRes.absolutePath)
            val extRoot = File(includeDir, "extensions")
            if (extRoot.isDirectory) {
                extRoot.listFiles()?.filter { it.isDirectory }?.forEach { catDir ->
                    catDir.listFiles()?.filter { it.isDirectory }?.forEach { extDir ->
                        val extModRes = File(
                            extDir,
                            "build/generated/genericui/module-resources/resources/META-INF/genericui/module-resources"
                        )
                        if (extModRes.exists()) paths.add(extModRes.absolutePath)
                    }
                }
            }
        }
        return paths.toList()
    }

    private fun isGenericUiLibraryModule(project: Project): Boolean =
        project.path in KNOWN_GENERICUI_MODULES

    private fun resolveGeneratedThemeStyleSheetObjectFqn(
        project: Project,
        extension: GenericUiExtension
    ): String {
        if (isGenericUiLibraryModule(project) || !extension.styles.compileSass || !extension.styles.precompileToKotlin) {
            return ""
        }
        val stylesDir = resolveStylesSourceDir(project, extension.styles.sourceDir)
        val hasConsumerTheme = stylesDir != null && stylesDir.isDirectory && stylesDir.walkTopDown().any {
            it.isFile &&
                !it.name.startsWith("_") &&
                it.nameWithoutExtension.equals("theme", ignoreCase = true) &&
                it.extension.lowercase() in setOf("scss", "sass", "css")
        }
        val hasUpstreamDepThemeSource = collectTransitiveProjectDependencies(project).any { depProj ->
            val depRes = depProj.file("src/commonMain/resources")
            depRes.isDirectory && depRes.walkTopDown().any {
                it.isFile &&
                    !it.name.startsWith("_") &&
                    it.nameWithoutExtension.equals("theme", ignoreCase = true) &&
                    it.extension.lowercase() in setOf("scss", "sass", "css")
            }
        }
        val hasSpiTheme = collectSpiThemeScssChunks(project, extension).isNotEmpty()
        val hasManifestOrArchiveTheme = if (hasConsumerTheme || hasUpstreamDepThemeSource || hasSpiTheme) {
            true
        } else {
            val sources = resolveUpstreamThemeAndManifestPaths(project, extension).map(::File) +
                resolveUpstreamResourceClasspathFiles(project)
            GenericUiModuleResourceManifestParser.readThemeTemplatesFromSources(sources).isNotEmpty()
        }
        if (!hasConsumerTheme && !hasUpstreamDepThemeSource && !hasSpiTheme && !hasManifestOrArchiveTheme) {
            return ""
        }
        val pkg = resolveStylesKotlinPackageName(project, extension)
        val objName = GenericUiSassCompileTask.GENERATED_THEME_STYLESHEET_OBJECT_NAME
        return if (pkg.isNotEmpty()) "$pkg.$objName" else objName
    }

    private fun wireGeneratedSchemaKotlinSources(
        project: Project,
        extension: GenericUiExtension,
        schemaTaskName: String
    ) {
        val commonSchema = extension.extensions.isCommonSchemaEnabled()
        val desktopSchema = extension.extensions.isDesktopSchemaEnabled(extension.backends)
        if (!commonSchema && !desktopSchema) return
        if (commonSchema) {
            wireGeneratedCommonKotlinSources(
                project = project,
                enabled = true,
                generatedDirRelativePath = "generated/genericui/schema/commonMain/kotlin",
                generatorTaskName = schemaTaskName
            )
            return
        }
        wireGeneratedKotlinSourcesToSourceSet(
            project = project,
            generatedDirRelativePath = "generated/genericui/schema/commonMain/kotlin",
            generatorTaskName = schemaTaskName
        ) { sourceSetsContainer ->
            sourceSetsContainer.findByName("desktopCommon")
                ?: sourceSetsContainer.findByName("desktopMain")
                ?: if (sourceSetsContainer.findByName("commonMain") == null) {
                    sourceSetsContainer.findByName("main")
                } else {
                    null
                }
        }
    }

    private fun wireGeneratedServiceWorkerCodecSources(
        project: Project,
        extension: GenericUiExtension,
        serviceWorkerCodecTaskName: String
    ) {
        wireGeneratedCommonKotlinSources(
            project = project,
            enabled = extension.extensions.serviceWorkerEnabled,
            generatedDirRelativePath = "generated/genericui/serviceworker/commonMain/kotlin",
            generatorTaskName = serviceWorkerCodecTaskName
        )
    }

    private fun wireGeneratedBinaryCodecSources(
        project: Project,
        extension: GenericUiExtension,
        binaryCodecsTaskName: String
    ) {
        wireGeneratedCommonKotlinSources(
            project = project,
            enabled = extension.includeCommon,
            generatedDirRelativePath = "generated/genericui/binaryCodecs/commonMain/kotlin",
            generatorTaskName = binaryCodecsTaskName
        )
    }

    private fun wireGeneratedCommonKotlinSources(
        project: Project,
        enabled: Boolean,
        generatedDirRelativePath: String,
        generatorTaskName: String
    ) {
        if (!enabled) return
        wireGeneratedKotlinSourcesToSourceSet(
            project = project,
            generatedDirRelativePath = generatedDirRelativePath,
            generatorTaskName = generatorTaskName
        ) { sourceSetsContainer ->
            sourceSetsContainer.findByName("commonMain")
                ?: sourceSetsContainer.findByName("common")
                ?: sourceSetsContainer.findByName("main")
        }
    }

    private fun wireGeneratedKotlinSourcesToSourceSet(
        project: Project,
        generatedDirRelativePath: String,
        generatorTaskName: String,
        selectTargetSourceSet: (org.gradle.api.NamedDomainObjectContainer<*>) -> Any?
    ) {
        val ktGeneratedDir = project.layout.buildDirectory.dir(generatedDirRelativePath)
        val kotlinExt = project.extensions.findByName("kotlin")
        if (kotlinExt != null) {
            runCatching {
                val getSourceSets = kotlinExt.javaClass.methods.firstOrNull {
                    it.name == "getSourceSets" && it.parameterCount == 0
                }
                val sourceSetsContainer = getSourceSets?.invoke(kotlinExt) as? org.gradle.api.NamedDomainObjectContainer<*>
                val targetSourceSet = sourceSetsContainer?.let(selectTargetSourceSet)
                if (targetSourceSet != null) {
                    val getKotlin = targetSourceSet.javaClass.methods.firstOrNull {
                        it.name == "getKotlin" && it.parameterCount == 0
                    }
                    val kotlinSourceDirSet = getKotlin?.invoke(targetSourceSet) as? org.gradle.api.file.SourceDirectorySet
                    kotlinSourceDirSet?.srcDir(ktGeneratedDir)
                }
            }
        }
        project.tasks.matching {
            it.name.startsWith("compile") &&
                !it.name.startsWith("compileGenericUi") &&
                (it.name.contains("Kotlin") || it.name.contains("Android"))
        }.configureEach {
            dependsOn(generatorTaskName)
        }
    }

    private fun buildPluginExtensionOptions(extension: GenericUiExtension): Map<String, String> =
        buildPluginExtensionOptionsStatic(extension)

    private fun collectConfiguredExtraKotlinDirs(
        project: Project,
        extension: GenericUiExtension
    ): List<File> {
        val dirs = linkedSetOf<File>()
        for (spec in extension.sourceSets.allSpecs()) {
            for (rawDir in spec.kotlinSrcDirs) {
                val f = (rawDir as? File) ?: (rawDir as? String)?.let { File(it) } ?: continue
                dirs.add(if (f.isAbsolute) f else File(project.projectDir, f.path))
            }
        }
        return dirs.toList()
    }

    private fun collectSchemaSourceDirectories(
        project: Project,
        extension: GenericUiExtension
    ): List<File> {
        val dirs = linkedSetOf<File>()
        dirs.add(File(project.projectDir, "src/commonMain/kotlin"))
        dirs.add(File(project.projectDir, "src/common/kotlin"))
        dirs.addAll(collectConfiguredExtraKotlinDirs(project, extension))
        return dirs.toList()
    }

    private fun collectBinaryCodecSourceDirectories(
        project: Project,
        extension: GenericUiExtension
    ): List<File> {
        val dirs = linkedSetOf<File>()
        dirs.addAll(collectSchemaSourceDirectories(project, extension))
        val isMultiplatform = project.configurations.findByName("commonMainImplementation") != null
        if (!isMultiplatform) {
            dirs.add(File(project.projectDir, "src/main/kotlin"))
        }
        return dirs.toList()
    }

    private fun resolveGeneratedPackageForProject(
        project: Project,
        extension: GenericUiExtension
    ): String {
        val extraDirs = collectSchemaSourceDirectories(project, extension)
        val discovered = (
            GenericUiFragmentScanner.scanProject(project.projectDir) +
                GenericUiFragmentScanner.scanSourceDirectories(extraDirs)
            ).distinctBy { "${it.qualifiedName}:${it.isMainFragment}:${it.isApplicationClass}" }
        return GenericUiGenerateLaunchersTask.resolveGeneratedLauncherPackage(
            extension = extension,
            projectGroup = project.group.toString(),
            discoveredFragments = discovered
        )
    }

    private fun resolveSchemaRegistrarPackage(
        project: Project,
        extension: GenericUiExtension
    ): String {
        val explicit = extension.extensions.extensionOptions[
            com.nexustools.genericui.plugin.spi.SchemaExtensionContributor.OPTION_REGISTRAR_PACKAGE
        ]?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) return explicit
        return resolveGeneratedPackageForProject(project, extension)
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
            it.name.startsWith("compile") &&
                !it.name.startsWith("compileGenericUi") &&
                (it.name.contains("Kotlin") || it.name.contains("Android"))
        }.configureEach {
            dependsOn(resourcesTaskName)
        }
        if (project.plugins.hasPlugin("com.android.kotlin.multiplatform.library") &&
            extension.backends.androidEnabled
        ) {
            val hostTestRJarTaskName = "packageGenericUiAndroidHostTestRJar"
            val hostTestRJarTask = if (hostTestRJarTaskName in project.tasks.names) {
                project.tasks.named(hostTestRJarTaskName, org.gradle.api.tasks.bundling.Jar::class.java)
            } else {
                project.tasks.register(
                    hostTestRJarTaskName,
                    org.gradle.api.tasks.bundling.Jar::class.java
                ) {
                    group = TASK_GROUP
                    description = "Packages compiled GenericUI R*.class files stripped by AGP library bundling for Android host tests."
                    dependsOn(resourcesTaskName)
                    dependsOn(project.tasks.matching { it.name == "compileAndroidMain" })
                    archiveBaseName.set("${project.name}-genericui-host-test-r")
                    destinationDirectory.set(project.layout.buildDirectory.dir("intermediates/genericui_host_test_r"))
                    from(project.layout.buildDirectory.dir("classes/kotlin/android/main")) {
                        include("**/R.class", "**/R\$*.class")
                    }
                    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
                }
            }
            for (configName in listOf("androidHostTestImplementation", "androidUnitTestImplementation")) {
                if (project.configurations.findByName(configName) != null) {
                    project.dependencies.add(
                        configName,
                        project.files(hostTestRJarTask.flatMap { it.archiveFile }) {
                            builtBy(hostTestRJarTask)
                        }
                    )
                }
            }
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
            (it.name.startsWith("compile") &&
                !it.name.startsWith("compileGenericUi") &&
                (it.name.contains("Kotlin") || it.name.contains("Android"))) ||
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
            "src/commonMain/resources/style",
            "src/commonMain/resources/css",
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

        val commonPaths = extension.extensions.enabledExtensionModulePathsForTarget(GenericUiVariantScope.COMMON)
        val commonPathSet = commonPaths.toSet()
        for (extPath in commonPaths) {
            resolveModuleDependency(project, extPath, version)?.let { dep ->
                project.dependencies.add(commonConfigName, dep)
                addDepToEnabledBackendConfigs(project, backends, dep, commonConfigName)
            }
        }

        val isMultiplatform = project.configurations.findByName("commonMainImplementation") != null
        val hasNativeDesktop = backends.windowsEnabled || backends.macosEnabled || backends.gtkEnabled || backends.kdeEnabled
        val hasDesktop = hasNativeDesktop || backends.swingConfig.enabled

        if (hasDesktop) {
            val desktopExtras = extension.extensions
                .enabledExtensionModulePathsForTarget(GenericUiVariantScope.DESKTOP_COMMON)
                .filterNot { it in commonPathSet }
            if (desktopExtras.isNotEmpty()) {
                val desktopConfigs = resolveDesktopOnlyConfigurations(project, backends, commonConfigName, isMultiplatform)
                for (extPath in desktopExtras) {
                    val dep = resolveModuleDependency(project, extPath, version) ?: continue
                    for (cfg in desktopConfigs) {
                        project.dependencies.add(cfg, dep)
                    }
                }
            }
        }

        if (backends.androidEnabled) {
            val androidExtras = extension.extensions
                .enabledExtensionModulePathsForTarget(GenericUiVariantScope.ANDROID)
                .filterNot { it in commonPathSet }
            if (androidExtras.isNotEmpty()) {
                val androidConfigs = resolveAndroidOnlyConfigurations(project, backends, commonConfigName, isMultiplatform)
                for (extPath in androidExtras) {
                    val dep = resolveModuleDependency(project, extPath, version) ?: continue
                    for (cfg in androidConfigs) {
                        project.dependencies.add(cfg, dep)
                    }
                }
            }
        }

        for (backendPath in backends.enabledBackendModulePaths()) {
            addBackendModuleDependency(project, backendPath, backendPath, version, commonConfigName)
        }
    }

    private fun resolveDesktopOnlyConfigurations(
        project: Project,
        backends: GenericUiBackendsConfig,
        fallbackSingleTargetConfig: String,
        isMultiplatform: Boolean
    ): List<String> {
        val hasNativeDesktop = backends.windowsEnabled || backends.macosEnabled || backends.gtkEnabled || backends.kdeEnabled
        val hasDesktop = hasNativeDesktop || backends.swingConfig.enabled
        if (!hasDesktop) return emptyList()
        if (!isMultiplatform) {
            return if (!backends.androidEnabled &&
                !backends.iosEnabled &&
                !backends.ipadosEnabled &&
                !backends.webEnabled &&
                project.configurations.findByName(fallbackSingleTargetConfig) != null
            ) {
                listOf(fallbackSingleTargetConfig)
            } else {
                emptyList()
            }
        }
        val candidates = linkedSetOf<String>()
        candidates.add("desktopCommonImplementation")
        candidates.add("desktopMainImplementation")
        if (hasNativeDesktop) {
            candidates.addAll(
                listOf(
                    "nativeDesktopCommonImplementation",
                    "nativeDesktopMainImplementation",
                    "desktopNativeCommonImplementation",
                    "desktopNativeMainImplementation",
                    "linuxCommonImplementation",
                    "linuxMainImplementation",
                    "windowsMainImplementation",
                    "mingwMainImplementation",
                    "mingwX64MainImplementation",
                    "macMainImplementation",
                    "macosMainImplementation",
                    "macosArm64MainImplementation",
                    "gtkImplementation",
                    "gtkMainImplementation",
                    "kdeImplementation",
                    "kdeMainImplementation",
                    "linuxX64MainImplementation",
                    "linuxArm64MainImplementation",
                    "linuxArm32HfpMainImplementation",
                    "linuxX64GtkImplementation",
                    "linuxArm64GtkImplementation",
                    "linuxX64KdeImplementation",
                    "linuxArm64KdeImplementation"
                )
            )
        }
        if (backends.swingConfig.enabled) {
            candidates.addAll(
                listOf(
                    "swingImplementation",
                    "swingMainImplementation",
                    "jvmMainImplementation"
                )
            )
        }
        return candidates.filter { project.configurations.findByName(it) != null }
    }

    private fun resolveAndroidOnlyConfigurations(
        project: Project,
        backends: GenericUiBackendsConfig,
        fallbackSingleTargetConfig: String,
        isMultiplatform: Boolean
    ): List<String> {
        if (!backends.androidEnabled) return emptyList()
        val hasDesktop = backends.windowsEnabled ||
            backends.macosEnabled ||
            backends.gtkEnabled ||
            backends.kdeEnabled ||
            backends.swingConfig.enabled
        if (!isMultiplatform) {
            return if (!hasDesktop &&
                !backends.iosEnabled &&
                !backends.ipadosEnabled &&
                !backends.webEnabled &&
                project.configurations.findByName(fallbackSingleTargetConfig) != null
            ) {
                listOf(fallbackSingleTargetConfig)
            } else {
                emptyList()
            }
        }
        return listOf("androidImplementation", "androidMainImplementation")
            .filter { project.configurations.findByName(it) != null }
    }

    private fun wireTestingModules(project: Project, extension: GenericUiExtension) {
        if (!extension.testing.enabled) return
        val version = extension.version
        val backends = extension.backends
        val commonTestConfig = listOf("commonTestImplementation", "testImplementation").firstOrNull {
            project.configurations.findByName(it) != null
        } ?: return

        resolveModuleDependency(project, ":tests", version)?.let { dep ->
            project.dependencies.add(commonTestConfig, dep)
        }

        fun addTestsToConfigs(vararg configNames: String) {
            val dep = resolveModuleDependency(project, ":tests", version) ?: return
            for (cfg in configNames) {
                if (project.configurations.findByName(cfg) != null) {
                    project.dependencies.add(cfg, dep)
                }
            }
        }

        if (backends.requiresSystemModule) {
            addTestsToConfigs("systemTestImplementation")
        }
        if (backends.requiresAppleModule) {
            addTestsToConfigs("appleTestImplementation")
        }
        if (backends.requiresJvmModule) {
            addTestsToConfigs("jvmCommonTestImplementation")
        }
        if (backends.requiresNativeDesktopCommonModule) {
            addTestsToConfigs(
                "nativeDesktopTestImplementation",
                "nativeDesktopCommonTestImplementation",
                "desktopNativeTestImplementation",
                "desktopNativeCommonTestImplementation",
                "mingwX64TestImplementation",
                "linuxX64TestImplementation",
                "linuxArm64TestImplementation",
                "macosArm64TestImplementation"
            )
        }
        if (backends.windowsEnabled) {
            addTestsToConfigs("windowsTestImplementation", "mingwX64TestImplementation")
        }
        if (backends.macosEnabled) {
            addTestsToConfigs("macTestImplementation", "macosTestImplementation", "macosArm64TestImplementation")
        }
        if (backends.gtkEnabled || backends.kdeEnabled) {
            addTestsToConfigs(
                "linuxTestImplementation",
                "gtkTestImplementation",
                "kdeTestImplementation",
                "linuxX64TestImplementation",
                "linuxArm64TestImplementation"
            )
        }
        if (backends.swingConfig.enabled) {
            addTestsToConfigs("desktopTestImplementation", "swingTestImplementation", "jvmTestImplementation", "testImplementation")
        }
        if (backends.requiresMobileModule) {
            addTestsToConfigs(
                "mobileCommonTestImplementation",
                "mobileTestImplementation",
                "iosCommonTestImplementation",
                "androidTestImplementation",
                "androidUnitTestImplementation",
                "androidHostTestImplementation",
                "iosArm64TestImplementation",
                "iosSimulatorArm64TestImplementation"
            )
        }
        if (backends.androidEnabled) {
            addTestsToConfigs(
                "androidTestImplementation",
                "androidUnitTestImplementation",
                "androidHostTestImplementation",
                "androidInstrumentedTestImplementation"
            )
        }
        if (backends.iosEnabled) {
            addTestsToConfigs("iosTestImplementation", "iosArm64TestImplementation", "iosSimulatorArm64TestImplementation")
        }
        if (backends.ipadosEnabled) {
            addTestsToConfigs("ipadosTestImplementation", "iosArm64TestImplementation", "iosSimulatorArm64TestImplementation")
        }
        if (backends.webEnabled) {
            addTestsToConfigs("webTestImplementation", "jsTestImplementation", "wasmJsTestImplementation")
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
        if (modulePath != ":extensions:ui:dsl" && modulePath.endsWith(":dsl")) {
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

        // Core LWJGL + JAWT bridge are shared by both GLContext and VKContext on Swing/AWT
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
                val targetSourceSet = sourceSetsContainer?.findByName("systemMain")
                    ?: sourceSetsContainer?.findByName("systemCommon")
                    ?: sourceSetsContainer?.findByName("systemCommonMain")
                    ?: sourceSetsContainer?.findByName("commonMain")
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
            it.name.startsWith("compile") &&
                !it.name.startsWith("compileGenericUi") &&
                (it.name.contains("Kotlin") || it.name.contains("Android"))
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
        project.tasks.matching {
            it.name.startsWith("link") && it.name.contains("Executable")
        }.configureEach {
            dependsOn(desktopResourcesTaskName)
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
            ":system", ":systemCommon", ":system:shared" ->
                allExisting(
                    "systemCommonImplementation",
                    "systemMainImplementation",
                    "appleCommonImplementation",
                    "appleMainImplementation",
                    "jvmCommonImplementation",
                    "desktopCommonImplementation",
                    "desktopMainImplementation",
                    "nativeDesktopCommonImplementation",
                    "nativeDesktopMainImplementation",
                    "desktopNativeCommonImplementation",
                    "desktopNativeMainImplementation",
                    "linuxCommonImplementation",
                    "linuxMainImplementation",
                    "mobileCommonImplementation",
                    "mobileMainImplementation",
                    "iosCommonImplementation",
                    "swingImplementation",
                    "swingMainImplementation",
                    "jvmMainImplementation",
                    "androidImplementation",
                    "androidMainImplementation",
                    "windowsMainImplementation",
                    "mingwX64MainImplementation",
                    "macMainImplementation",
                    "macosMainImplementation",
                    "macosArm64MainImplementation",
                    "gtkMainImplementation",
                    "kdeMainImplementation",
                    "linuxX64MainImplementation",
                    "linuxArm64MainImplementation",
                    "linuxArm32HfpMainImplementation",
                    "iosImplementation",
                    "iosMainImplementation",
                    "ipadosMainImplementation",
                    "iosArm64MainImplementation",
                    "iosSimulatorArm64MainImplementation"
                )
            ":jvm" ->
                allExisting(
                    "jvmCommonImplementation",
                    "jvmMainImplementation",
                    "swingImplementation",
                    "swingMainImplementation",
                    "androidImplementation",
                    "androidMainImplementation"
                )
            ":apple" ->
                allExisting(
                    "appleCommonImplementation",
                    "appleMainImplementation",
                    "macMainImplementation",
                    "macosMainImplementation",
                    "macosArm64MainImplementation",
                    "iosCommonImplementation",
                    "iosImplementation",
                    "iosMainImplementation",
                    "ipadosMainImplementation",
                    "iosArm64MainImplementation",
                    "iosSimulatorArm64MainImplementation"
                )
            ":desktop", ":desktop:common", ":desktop:nativeCommon", ":desktop:shared" ->
                allExisting(
                    "desktopCommonImplementation",
                    "desktopMainImplementation",
                    "nativeDesktopCommonImplementation",
                    "nativeDesktopMainImplementation",
                    "desktopNativeCommonImplementation",
                    "desktopNativeMainImplementation",
                    "linuxCommonImplementation",
                    "linuxMainImplementation",
                    "windowsMainImplementation",
                    "mingwX64MainImplementation",
                    "macMainImplementation",
                    "macosMainImplementation",
                    "macosArm64MainImplementation",
                    "gtkImplementation",
                    "gtkMainImplementation",
                    "kdeImplementation",
                    "kdeMainImplementation",
                    "linuxX64MainImplementation",
                    "linuxArm64MainImplementation",
                    "linuxArm32HfpMainImplementation",
                    "linuxX64GtkImplementation",
                    "linuxArm64GtkImplementation",
                    "linuxX64KdeImplementation",
                    "linuxArm64KdeImplementation",
                    "swingImplementation",
                    "swingMainImplementation",
                    "jvmMainImplementation"
                )
            ":mobile", ":mobile:common", ":mobile:shared" ->
                allExisting(
                    "mobileCommonImplementation",
                    "mobileMainImplementation",
                    "androidImplementation",
                    "androidMainImplementation",
                    "iosCommonImplementation",
                    "iosImplementation",
                    "iosMainImplementation",
                    "ipadosMainImplementation",
                    "iosArm64MainImplementation",
                    "iosSimulatorArm64MainImplementation"
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

        internal fun buildPluginExtensionOptionsStatic(extension: GenericUiExtension): Map<String, String> {
            extension.extensions.syncServiceWorkerExtensionOptions()
            extension.extensions.syncWebViewExtensionOptions()
            val merged = LinkedHashMap<String, String>(extension.extensions.extensionOptions)
            val enabledBackends = GenericUiGenerateLaunchersTask.enabledAllBackendScopes(extension.backends)
                .joinToString(",") { it.id }
            merged[com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_ENABLED_BACKENDS] = enabledBackends
            merged[com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_RESOURCES_ENABLED] =
                extension.resources.enabled.toString()
            val resPkg = extension.resources.packageName.trim()
            if (resPkg.isNotEmpty()) {
                merged[com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_RESOURCE_PACKAGE] = resPkg
            }
            val schemaEnabled = extension.extensions.isAnySchemaEnabled(extension.backends)
            merged[com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_SCHEMA_ENABLED] =
                schemaEnabled.toString()
            merged[com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_BINARY_CODECS_ENABLED] =
                extension.includeCommon.toString()
            if (com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_MACOS_TRAY_ICON !in merged &&
                extension.application.desktop.macosTrayIcon.isNotBlank()
            ) {
                merged[com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_MACOS_TRAY_ICON] =
                    extension.application.desktop.macosTrayIcon.trim()
            }
            if (com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_MAC_LOCAL_NETWORK_DESC !in merged &&
                extension.application.desktop.macLocalNetworkUsageDescription.isNotBlank()
            ) {
                merged[com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_MAC_LOCAL_NETWORK_DESC] =
                    extension.application.desktop.macLocalNetworkUsageDescription.trim()
            }
            if (com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_MAC_BONJOUR_SERVICES !in merged &&
                extension.application.desktop.macBonjourServices.isNotEmpty()
            ) {
                val services = extension.application.desktop.macBonjourServices
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                if (services.isNotEmpty()) {
                    merged[com.nexustools.genericui.plugin.spi.ServiceWorkerExtensionContributor.OPTION_MAC_BONJOUR_SERVICES] =
                        services.joinToString(",")
                }
            }
            return merged
        }

        internal fun findGenericUiIncludeDir(project: Project): File? {
            runCatching {
                for (ib in project.gradle.includedBuilds) {
                    val candidate = ib.projectDir
                    if (File(candidate, "common/src/commonMain/kotlin").isDirectory) {
                        return candidate
                    }
                }
            }
            val candidates = listOfNotNull(
                File(project.rootProject.projectDir, "lib/GenericUI"),
                File(project.projectDir, "lib/GenericUI"),
                project.rootProject.projectDir.parentFile?.let { File(it, "GenericUI") }
            )
            return candidates.firstOrNull {
                it.isDirectory && File(it, "common/src/commonMain/kotlin").isDirectory
            }
        }

        internal fun collectTransitiveProjectDependencies(project: Project): List<Project> {
            val visited = linkedSetOf<Project>()
            val queue = ArrayDeque<Project>()
            fun enqueueFrom(src: Project) {
                for (cfg in src.configurations.toList()) {
                    for (dep in cfg.dependencies.withType(org.gradle.api.artifacts.ProjectDependency::class.java)) {
                        val other = project.rootProject.findProject(dep.path)
                        if (other != null && other != project && visited.add(other)) {
                            queue.add(other)
                        }
                    }
                }
            }
            enqueueFrom(project)
            while (queue.isNotEmpty()) {
                enqueueFrom(queue.removeFirst())
            }
            return visited.toList()
        }

        internal fun resolveUpstreamResourceClasspathFiles(project: Project): List<File> {
            val files = linkedSetOf<File>()
            val ownBuildDir = runCatching { project.layout.buildDirectory.asFile.get().canonicalPath }.getOrNull()
            val candidateConfigNames = setOf(
                "commonMainCompileKlibClasspath",
                "commonMainCompileDependenciesMetadata",
                "metadataCommonMainCompileClasspath",
                "jvmCompileClasspath",
                "jvmRuntimeClasspath",
                "compileClasspath",
                "runtimeClasspath",
                "androidCompileClasspath",
                "jsCompileClasspath",
                "wasmJsCompileClasspath"
            )
            for (cfg in project.configurations.toList()) {
                if (!cfg.isCanBeResolved) continue
                val matches = cfg.name in candidateConfigNames ||
                    cfg.name.endsWith("CompileKlib") ||
                    cfg.name.endsWith("CompileKlibClasspath") ||
                    cfg.name.endsWith("CompileClasspath") ||
                    cfg.name.endsWith("DependenciesMetadata")
                if (!matches) continue
                runCatching {
                    cfg.incoming.artifactView { lenient(true) }.files.forEach { file ->
                        if (file.exists()) {
                            val canon = runCatching { file.canonicalPath }.getOrNull()
                            if (ownBuildDir == null || canon == null || !canon.startsWith(ownBuildDir)) {
                                files.add(file)
                            }
                        }
                    }
                }
            }
            return files.toList()
        }

        internal fun collectSpiThemeScssChunks(project: Project, extension: GenericUiExtension): List<String> {
            val enabledSet = extension.extensions.enabledExtensionModulePaths().toSet() +
                extension.extensions.customModulePaths.toSet()
            if (enabledSet.isEmpty()) return emptyList()
            val commonVariant = resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.COMMON)
            val genPkg = GenericUiGenerateLaunchersTask.resolveGeneratedLauncherPackage(
                extension = extension,
                projectGroup = project.group.toString(),
                discoveredFragments = emptyList()
            )
            val ctx = com.nexustools.genericui.plugin.spi.ExtensionBuildContext(
                projectRootDir = project.projectDir,
                projectName = project.name,
                appId = commonVariant.appId,
                appName = commonVariant.appName,
                appVersion = commonVariant.appVersion,
                baseName = extension.application.baseName.trim().ifEmpty { project.name },
                generatedPackageName = genPkg,
                enabledExtensionModulePaths = extension.extensions.enabledExtensionModulePaths().toSet(),
                customExtensionModulePaths = extension.extensions.customModulePaths.toSet(),
                extensionOptions = buildPluginExtensionOptionsStatic(extension)
            )
            return com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
                .collectThemeScssContributions(ctx)
                .map { (modPath, raw) -> "/* SPI Theme Template: $modPath */\n$raw" }
        }

        internal fun resolveUpstreamThemeAndManifestPaths(project: Project, extension: GenericUiExtension): List<String> {
            val paths = linkedSetOf<String>()
            for (otherProj in collectTransitiveProjectDependencies(project)) {
                val buildDir = otherProj.layout.buildDirectory.asFile.get()
                paths.add(
                    File(buildDir, "generated/genericui/module-resources/resources/META-INF/genericui/module-resources")
                        .absolutePath
                )
                paths.add(
                    File(buildDir, "generated/genericui/module-resources/resources/META-INF/genericui/themes")
                        .absolutePath
                )
                paths.add(
                    File(buildDir, "generated/genericui/resources/backends/manifest/genericui-module-resources.json")
                        .absolutePath
                )
            }
            val includeDir = findGenericUiIncludeDir(project)
            if (includeDir != null) {
                listOf(
                    File(includeDir, "common/build/generated/genericui/module-resources/resources/META-INF/genericui/module-resources"),
                    File(includeDir, "common/build/generated/genericui/module-resources/resources/META-INF/genericui/themes")
                ).filter { it.exists() }.forEach { paths.add(it.absolutePath) }

                for (modPath in extension.extensions.enabledExtensionModulePaths()) {
                    val segs = modPath.trimStart(':').split(':').filter { it.isNotEmpty() }
                    if (segs.size >= 3 && segs[0] == "extensions") {
                        val modRel = "${segs[0]}/${segs[1]}/${segs[2]}"
                        listOf(
                            File(includeDir, "$modRel/build/generated/genericui/module-resources/resources/META-INF/genericui/module-resources"),
                            File(includeDir, "$modRel/build/generated/genericui/module-resources/resources/META-INF/genericui/themes")
                        ).filter { it.exists() }.forEach { paths.add(it.absolutePath) }
                    }
                }
            }
            return paths.toList()
        }

        internal fun configureWebDistributionTaskInstance(
            project: Project,
            extension: GenericUiExtension,
            task: GenericUiWebDistributionTask,
            variant: String,
            outputRelPath: String
        ) {
            task.group = TASK_GROUP
            task.description = "Packages the unified GenericUI Web ($variant) distribution with Wasm primary + JS IR fallback, splash screen icon, favicon, optional PWA manifest.webmanifest & installable WebApp tags, loader.js, and SHA-1 cache-busting hashes into build/$outputRelPath."
            task.projectRootPath.set(project.projectDir.absolutePath)
            task.projectBuildPath.set(project.layout.buildDirectory.asFile.map { it.absolutePath })
            task.baseName.set(project.provider {
                extension.application.baseName.trim().ifEmpty { project.name }
            })
            task.appName.set(project.provider {
                extension.application.desktop.appName.trim().ifEmpty {
                    extension.application.baseName.trim().ifEmpty { project.name }
                }
            })
            task.appDescription.set(project.provider { extension.application.desktop.description })
            task.themeColor.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB).themeColor
            })
            task.themeMode.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB).themeMode
            })
            task.lightThemeColors.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB).lightThemeColors
            })
            task.darkThemeColors.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB).darkThemeColors
            })
            task.hasCustomThemeOverrides.set(project.provider {
                resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB).hasCustomThemeOverrides
            })
            task.webIconPath.set(project.provider { extension.application.desktop.webIcon })
            task.fallbackIconPath.set(project.provider { extension.application.desktop.icon })
            task.linuxIconPath.set(project.provider { extension.application.desktop.linuxIcon })
            task.macIconPath.set(project.provider { extension.application.desktop.macIcon })
            task.windowsIconPath.set(project.provider { extension.application.desktop.windowsIcon })
            task.webCssBundleFileName.set(project.provider { extension.styles.webCssBundleFileName })
            task.globalStyleSheet.set(project.provider {
                extension.styles.globalStyleSheet?.trim()?.takeIf { it.isNotEmpty() }
                    ?: resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB).globalStyleSheet
                    ?: ""
            })
            task.manifestEnabled.set(project.provider { extension.application.desktop.webManifest.enabled })
            task.allowInstallAsWebApp.set(project.provider { extension.application.desktop.webManifest.allowInstallAsWebApp })
            task.manifestFileName.set(project.provider { extension.application.desktop.webManifest.fileName })
            task.manifestId.set(project.provider { extension.application.desktop.webManifest.id })
            task.manifestName.set(project.provider { extension.application.desktop.webManifest.name })
            task.manifestShortName.set(project.provider { extension.application.desktop.webManifest.shortName })
            task.manifestDescription.set(project.provider { extension.application.desktop.webManifest.description })
            task.manifestStartUrl.set(project.provider { extension.application.desktop.webManifest.startUrl })
            task.manifestScope.set(project.provider { extension.application.desktop.webManifest.scope })
            task.manifestDisplay.set(project.provider { extension.application.desktop.webManifest.display })
            task.manifestDisplayOverride.set(project.provider { extension.application.desktop.webManifest.displayOverride.toList() })
            task.manifestOrientation.set(project.provider { extension.application.desktop.webManifest.orientation })
            task.manifestThemeColor.set(project.provider { extension.application.desktop.webManifest.themeColor })
            task.manifestBackgroundColor.set(project.provider { extension.application.desktop.webManifest.backgroundColor })
            task.manifestLang.set(project.provider { extension.application.desktop.webManifest.lang })
            task.manifestDir.set(project.provider { extension.application.desktop.webManifest.dir })
            task.manifestCategories.set(project.provider { extension.application.desktop.webManifest.categories.toList() })
            task.manifestPreferRelatedApplications.set(project.provider { extension.application.desktop.webManifest.preferRelatedApplications })
            task.manifestAppleStatusBarStyle.set(project.provider { extension.application.desktop.webManifest.appleStatusBarStyle })
            task.manifestIcons.set(project.provider { extension.application.desktop.webManifest.icons.toList() })
            task.manifestShortcuts.set(project.provider { extension.application.desktop.webManifest.shortcuts.toList() })
            val swContributorsProvider = project.provider {
                extension.extensions.syncServiceWorkerExtensionOptions()
                val enabledExtSet = extension.extensions.enabledExtensionModulePaths().toSet()
                val discoveredFrags = GenericUiFragmentScanner.scanProject(project.projectDir)
                val scanSpecs = com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
                    .allAnnotationScanSpecs(enabledExtSet, project.projectDir)
                val discoveredSyms = GenericUiFragmentScanner.scanProjectSymbols(project.projectDir, scanSpecs)
                val genPkg = GenericUiGenerateLaunchersTask.resolveGeneratedLauncherPackage(
                    extension = extension,
                    projectGroup = project.group.toString(),
                    discoveredFragments = discoveredFrags
                )
                val webVariant = resolveVariantWithProjectFallbacksStatic(project, extension, GenericUiVariantScope.WEB)
                val baseCtx = com.nexustools.genericui.plugin.spi.ExtensionBuildContext(
                    projectRootDir = project.projectDir,
                    projectName = project.name,
                    appId = webVariant.appId,
                    appName = webVariant.appName,
                    appVersion = webVariant.appVersion,
                    baseName = extension.application.baseName.trim().ifEmpty { project.name },
                    generatedPackageName = genPkg,
                    enabledExtensionModulePaths = enabledExtSet,
                    customExtensionModulePaths = extension.extensions.customModulePaths.toSet(),
                    discoveredFragments = discoveredFrags,
                    discoveredSymbols = discoveredSyms,
                    extensionOptions = buildPluginExtensionOptionsStatic(extension)
                )
                com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
                    .collectWebServiceWorkerContributions(baseCtx)
            }
            task.serviceWorkerEnabled.set(swContributorsProvider.map { list -> list.any { it.enableServiceWorker } })
            task.serviceWorkerPluginPaths.set(swContributorsProvider.map { list ->
                list.flatMap { c ->
                    c.pluginScripts.filter { it.scriptContent == null }.map { s -> s.sourceFile?.absolutePath ?: s.relativeUrlPath }
                }.distinct()
            })
            task.serviceWorkerInlinePlugins.set(swContributorsProvider.map { list ->
                val map = linkedMapOf<String, String>()
                list.flatMap { it.pluginScripts }.forEach { script ->
                    val content = script.scriptContent
                    if (content != null && script.relativeUrlPath.isNotBlank()) {
                        map[script.relativeUrlPath] = content
                    }
                }
                map
            })
            task.serviceWorkerBinaryBaseNames.set(swContributorsProvider.map { list ->
                list.flatMap { c ->
                    c.workerBinaries.filter { it.serviceWorker }.map { it.binaryBaseName }
                }.distinct()
            })
            task.serviceWorkerOfflineUrls.set(swContributorsProvider.map { list ->
                list.flatMap { c -> c.offlineCacheUrls }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
            })
            task.upstreamResourceManifestPaths.set(project.provider {
                resolveUpstreamThemeAndManifestPaths(project, extension)
            })
            task.upstreamResourceClasspath.from(project.provider {
                resolveUpstreamResourceClasspathFiles(project)
            })
            task.spiThemeScssChunks.set(project.provider {
                collectSpiThemeScssChunks(project, extension)
            })
            task.buildVariant.set(variant)
            task.outputDirectory.set(project.layout.buildDirectory.dir(outputRelPath))
            task.outputs.upToDateWhen { false }
        }

        private fun resolveVariantWithProjectFallbacksStatic(
            project: Project,
            extension: GenericUiExtension,
            backend: GenericUiVariantScope
        ): GenericUiResolvedVariantConfig {
            val base = GenericUiVariantResolver.resolve(extension, backend)
            val projectGroup = project.group.toString().trim().takeIf { it.isNotEmpty() && it != "unspecified" }
            val projectVersion = project.version.toString().trim().takeIf { it.isNotEmpty() && it != "unspecified" }
            val fallbackAppId = if (base.appId == "com.nexustools.genericui.app" && projectGroup != null) {
                projectGroup
            } else {
                base.appId
            }
            val fallbackAppName = if (base.appName == "GenericUI" && project.name.isNotBlank()) {
                project.name
            } else {
                base.appName
            }
            val fallbackVersion = if (base.appVersion == "1.0.0" && projectVersion != null) {
                projectVersion
            } else {
                base.appVersion
            }
            return base.copy(
                appId = fallbackAppId,
                appName = fallbackAppName,
                appVersion = fallbackVersion,
                packageName = base.packageName.ifBlank { fallbackAppId }
            )
        }

        /**
         * Converts a GenericUI Gradle module path (e.g. `":common"`, `":desktop"`,
         * `":extensions:graphics:canvas2d"`, `":extensions:game:engine2d:dsl"`) into its exact Maven /
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
         * Historically listed extensions structured as a Project-of-Projects.
         * All modules in the consolidated 45-project architecture are single multiplatform Gradle projects.
         */
        val PROJECT_OF_PROJECTS_EXTENSIONS: Set<String> = emptySet()

        /**
         * Canonical set of GenericUI library/test module paths present in `GenericUI/settings.gradle.kts`.
         */
        val KNOWN_GENERICUI_MODULES: Set<String> = setOf(
            ":common",
            ":system",
            ":jvm",
            ":apple",
            ":desktop",
            ":mobile",
            ":web",
            ":extensions:graphics:gpu",
            ":extensions:graphics:imageprocessing",
            ":extensions:graphics:svg",
            ":extensions:graphics:svg:dsl",
            ":extensions:graphics:svg:alien",
            ":extensions:graphics:canvas2d",
            ":extensions:graphics:canvas2d:dsl",
            ":extensions:graphics:canvas2d:alien",
            ":extensions:graphics:canvas3d",
            ":extensions:graphics:canvas3d:dsl",
            ":extensions:graphics:canvas3d:alien",
            ":extensions:graphics:threejs",
            ":extensions:graphics:threejs:dsl",
            ":extensions:physics:box2d",
            ":extensions:physics:box2d:dsl",
            ":extensions:physics:box3d",
            ":extensions:physics:box3d:dsl",
            ":extensions:game:gamepad",
            ":extensions:game:engine",
            ":extensions:game:engine:dsl",
            ":extensions:game:engine2d",
            ":extensions:game:engine2d:dsl",
            ":extensions:game:engine3d",
            ":extensions:game:engine3d:dsl",
            ":extensions:media:media",
            ":extensions:media:media:dsl",
            ":extensions:media:media:alien",
            ":extensions:media:mediacontrols",
            ":extensions:data:schema",
            ":extensions:data:schema:dsl",
            ":extensions:system:ipc",
            ":extensions:system:serviceworker",
            ":extensions:ui:dsl",
            ":extensions:ui:overlay",
            ":extensions:ui:overlay:dsl",
            ":extensions:ui:alien",
            ":extensions:ui:alien:dsl",
            ":extensions:ui:webview",
            ":extensions:ui:webview:dsl",
            ":extensions:ui:codeeditor",
            ":extensions:ui:codeeditor:dsl",
            ":extensions:ui:aichat",
            ":extensions:ui:aichat:dsl",
            ":extensions:ui:pip",
            ":tests"
        )
    }
}
