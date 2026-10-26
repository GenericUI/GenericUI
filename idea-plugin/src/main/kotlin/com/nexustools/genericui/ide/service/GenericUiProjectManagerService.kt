package com.nexustools.genericui.ide.service

import com.nexustools.genericui.ide.generator.GenericUiBuildFileInspector
import com.nexustools.genericui.ide.generator.GenericUiGradleScriptGenerator
import com.nexustools.genericui.ide.generator.GenericUiStarterSourceGenerator
import com.nexustools.genericui.ide.model.GenericUiExtensionModule
import com.nexustools.genericui.ide.model.GenericUiProjectConfig
import com.nexustools.genericui.ide.model.GenericUiTargetBackend
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

/**
 * High-level IntelliJ IDEA project service that scaffolds new `GenericUI` projects
 * and updates existing projects' `build.gradle.kts` target backends and extensions.
 */
class GenericUiProjectManagerService(
    private val scriptGenerator: GenericUiGradleScriptGenerator = GenericUiGradleScriptGenerator(),
    private val sourceGenerator: GenericUiStarterSourceGenerator = GenericUiStarterSourceGenerator(),
    private val buildInspector: GenericUiBuildFileInspector = GenericUiBuildFileInspector(scriptGenerator),
    private val createSymlinkOnDisk: Boolean = false
) {

    /**
     * Scaffolds a complete `GenericUI` multiplatform project at [GenericUiProjectConfig.projectRootPath].
     *
     * Creates:
     * - `settings.gradle.kts`
     * - `build.gradle.kts`
     * - `gradle.properties`
     * - `lib/` directory
     * - `src/commonMain/kotlin/<packagePath>/Main.kt`
     * - `src/commonMain/kotlin/<packagePath>/MainFragment.kt`
     * - `src/commonMain/resources/styles/main.scss`
     */
    fun createProject(config: GenericUiProjectConfig): Path {
        val rootDir = Paths.get(config.projectRootPath).toAbsolutePath().normalize()
        Files.createDirectories(rootDir)

        val libDir = rootDir.resolve("lib")
        Files.createDirectories(libDir)
        if (createSymlinkOnDisk && config.genericUiLibPath.isNotBlank()) {
            tryCreateGenericUiLink(libDir.resolve("GenericUI"), Paths.get(config.genericUiLibPath))
        }

        Files.writeString(
            rootDir.resolve("settings.gradle.kts"),
            scriptGenerator.generateSettingsGradleKts(config),
            StandardCharsets.UTF_8
        )
        Files.writeString(
            rootDir.resolve("build.gradle.kts"),
            scriptGenerator.generateBuildGradleKts(config),
            StandardCharsets.UTF_8
        )
        Files.writeString(
            rootDir.resolve("gradle.properties"),
            scriptGenerator.generateGradleProperties(config),
            StandardCharsets.UTF_8
        )

        val packageRelPath = config.packageName.replace('.', '/')
        val kotlinDir = rootDir.resolve("src/commonMain/kotlin").resolve(packageRelPath)
        Files.createDirectories(kotlinDir)

        Files.writeString(
            kotlinDir.resolve("MainFragment.kt"),
            sourceGenerator.generateMainFragmentKt(config),
            StandardCharsets.UTF_8
        )

        val stylesDir = rootDir.resolve("src/commonMain/resources/styles")
        Files.createDirectories(stylesDir)
        Files.writeString(
            stylesDir.resolve("main.scss"),
            sourceGenerator.generateStyleScss(config),
            StandardCharsets.UTF_8
        )

        return rootDir
    }

    /**
     * Loads the current [GenericUiProjectConfig] from an existing project directory
     * by inspecting its `build.gradle.kts` and `settings.gradle.kts`.
     */
    fun loadProjectConfig(projectDir: Path): GenericUiProjectConfig {
        val normalizedDir = projectDir.toAbsolutePath().normalize()
        val buildFile = normalizedDir.resolve("build.gradle.kts")
        val settingsFile = normalizedDir.resolve("settings.gradle.kts")

        val projectName = if (Files.isRegularFile(settingsFile)) {
            val settingsContent = Files.readString(settingsFile, StandardCharsets.UTF_8)
            Regex("""rootProject\.name\s*=\s*"([^"]+)"""").find(settingsContent)?.groupValues?.get(1)
                ?: normalizedDir.fileName?.toString()
                ?: "GenericUiApp"
        } else {
            normalizedDir.fileName?.toString() ?: "GenericUiApp"
        }

        val buildContent = if (Files.isRegularFile(buildFile)) {
            Files.readString(buildFile, StandardCharsets.UTF_8)
        } else {
            ""
        }

        return buildInspector.inspectBuildGradleKts(
            buildGradleKtsContent = buildContent,
            projectName = projectName,
            projectRootPath = normalizedDir.toString()
        )
    }

    /**
     * Updates an existing project's `build.gradle.kts` with the newly checked
     * [updatedBackends] and [updatedExtensions] from the Manage GenericUI Targets dialog.
     */
    fun updateProjectTargetsAndExtensions(
        projectDir: Path,
        updatedBackends: Set<GenericUiTargetBackend>,
        updatedExtensions: Set<GenericUiExtensionModule>,
        swingUseLwjglGL: Boolean = true,
        swingUseVulkan: Boolean = true,
        compileSass: Boolean = true
    ): Path {
        val currentConfig = loadProjectConfig(projectDir)
        val newConfig = currentConfig.copy(
            selectedBackends = updatedBackends,
            selectedExtensions = GenericUiExtensionModule.resolveTransitive(updatedExtensions),
            swingUseLwjglGL = swingUseLwjglGL,
            swingUseVulkan = swingUseVulkan,
            compileSass = compileSass
        )

        val buildFile = projectDir.toAbsolutePath().normalize().resolve("build.gradle.kts")
        val updatedContent = if (Files.isRegularFile(buildFile)) {
            val existing = Files.readString(buildFile, StandardCharsets.UTF_8)
            buildInspector.updateBuildGradleKts(existing, newConfig)
        } else {
            scriptGenerator.generateBuildGradleKts(newConfig)
        }

        Files.writeString(buildFile, updatedContent, StandardCharsets.UTF_8)
        return buildFile
    }

    private fun tryCreateGenericUiLink(linkPath: Path, targetPath: Path) {
        if (Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS) || !Files.exists(targetPath)) return
        try {
            Files.createSymbolicLink(linkPath, targetPath.toAbsolutePath().normalize())
        } catch (_: Exception) {
            // Symbolic link creation may require elevated privileges on some systems;
            // ignore gracefully so project scaffolding always succeeds.
        }
    }
}
