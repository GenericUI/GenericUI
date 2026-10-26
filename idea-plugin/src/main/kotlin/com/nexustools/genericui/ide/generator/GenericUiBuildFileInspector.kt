package com.nexustools.genericui.ide.generator

import com.nexustools.genericui.ide.model.GenericUiExtensionModule
import com.nexustools.genericui.ide.model.GenericUiProjectConfig
import com.nexustools.genericui.ide.model.GenericUiTargetBackend

/**
 * Parses an existing `build.gradle.kts` file to inspect which [GenericUiTargetBackend]s
 * and [GenericUiExtensionModule]s are currently enabled, and updates `build.gradle.kts`
 * in-place when the user changes target or extension checkboxes in IntelliJ IDEA.
 */
class GenericUiBuildFileInspector(
    private val scriptGenerator: GenericUiGradleScriptGenerator = GenericUiGradleScriptGenerator()
) {

    /**
     * Parses the `backends { ... }` section inside `genericui { ... }` of [buildGradleKtsContent]
     * and returns the set of enabled [GenericUiTargetBackend]s.
     */
    fun parseSelectedBackends(buildGradleKtsContent: String): Set<GenericUiTargetBackend> {
        val backendsBlock = extractBlockContent(buildGradleKtsContent, "backends") ?: return emptySet()
        val result = linkedSetOf<GenericUiTargetBackend>()

        if (Regex("""\ballExceptSwing\s*\(""").containsMatchIn(backendsBlock)) {
            result.addAll(GenericUiTargetBackend.allExceptSwingSet())
        }
        if (
            Regex("""\ballDesktopExceptSwing\s*\(""").containsMatchIn(backendsBlock) ||
            Regex("""\ballNativeDesktop\s*\(""").containsMatchIn(backendsBlock)
        ) {
            result.addAll(GenericUiTargetBackend.desktopExceptSwingSet())
        }
        if (Regex("""\ballDesktop\s*\(""").containsMatchIn(backendsBlock)) {
            result.addAll(GenericUiTargetBackend.desktopAllSet())
        }
        if (Regex("""\ballMobile\s*\(""").containsMatchIn(backendsBlock)) {
            result.addAll(GenericUiTargetBackend.mobileSet())
        }
        if (Regex("""\ball\s*\(\s*\)""").containsMatchIn(backendsBlock)) {
            result.addAll(GenericUiTargetBackend.allSet())
        }

        for (backend in GenericUiTargetBackend.entries) {
            val callPattern = Regex("""\b${backend.dslMethodName}\s*(\(\s*(true|false)?\s*\)|\{)""")
            for (match in callPattern.findAll(backendsBlock)) {
                val arg = match.groupValues.getOrNull(2)?.trim()
                if (arg == "false") {
                    result.remove(backend)
                } else {
                    result.add(backend)
                }
            }
        }

        return GenericUiTargetBackend.entries.filter { it in result }.toCollection(LinkedHashSet())
    }

    /**
     * Parses the `extensions { ... }` section inside `genericui { ... }` of [buildGradleKtsContent]
     * and returns the transitively resolved set of enabled [GenericUiExtensionModule]s.
     */
    fun parseSelectedExtensions(buildGradleKtsContent: String): Set<GenericUiExtensionModule> {
        val extensionsBlock = extractBlockContent(buildGradleKtsContent, "extensions") ?: return emptySet()
        val result = linkedSetOf<GenericUiExtensionModule>()

        if (Regex("""\benableAll\s*\(""").containsMatchIn(extensionsBlock)) {
            return GenericUiExtensionModule.entries.toCollection(LinkedHashSet())
        }

        for (ext in GenericUiExtensionModule.entries) {
            val callPattern = Regex("""\b${ext.dslMethodName}\s*\(\s*(true|false)?\s*\)""")
            for (match in callPattern.findAll(extensionsBlock)) {
                val arg = match.groupValues.getOrNull(1)?.trim()
                if (arg == "false") {
                    result.remove(ext)
                } else {
                    result.add(ext)
                }
            }
        }

        return GenericUiExtensionModule.resolveTransitive(result)
    }

    /**
     * Inspects a complete `build.gradle.kts` file (and optional `projectName`/`projectRootPath`)
     * and reconstructs a [GenericUiProjectConfig].
     */
    fun inspectBuildGradleKts(
        buildGradleKtsContent: String,
        projectName: String = "GenericUiApp",
        projectRootPath: String = "."
    ): GenericUiProjectConfig {
        val appMetadataBlock = extractBlockContent(buildGradleKtsContent, "app")
        val appId = appMetadataBlock?.let {
            Regex("""\bid\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1)
        }
        val appName = appMetadataBlock?.let {
            Regex("""\bname\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1)
        }

        val groupMatch = Regex("""group\s*=\s*"([^"]+)"""").find(buildGradleKtsContent)
        val packageName = appId ?: groupMatch?.groupValues?.get(1) ?: "com.example.app"

        val appBlock = extractBlockContent(buildGradleKtsContent, "application")
        val resolvedProjectName = appName ?: appBlock?.let {
            Regex("""baseName\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1)
        } ?: projectName

        val backends = parseSelectedBackends(buildGradleKtsContent)
        val extensions = parseSelectedExtensions(buildGradleKtsContent)

        val swingBlock = extractBlockContent(buildGradleKtsContent, "swing")
        val swingUseLwjglGL = swingBlock?.let {
            !Regex("""useLwjglGL\s*=\s*false""").containsMatchIn(it)
        } ?: true
        val swingUseVulkan = swingBlock?.let {
            !Regex("""useVulkan\s*=\s*false""").containsMatchIn(it)
        } ?: true

        val stylesBlock = extractBlockContent(buildGradleKtsContent, "styles")
        val compileSass = stylesBlock?.let {
            !Regex("""compileSass\s*=\s*false""").containsMatchIn(it)
        } ?: true

        return GenericUiProjectConfig(
            projectName = resolvedProjectName,
            packageName = packageName,
            projectRootPath = projectRootPath,
            selectedBackends = backends,
            selectedExtensions = extensions,
            compileSass = compileSass,
            swingUseLwjglGL = swingUseLwjglGL,
            swingUseVulkan = swingUseVulkan
        )
    }

    /**
     * Updates an existing `build.gradle.kts` content in-place by replacing the `genericui { ... }`
     * DSL block to match [updatedConfig].
     */
    fun updateBuildGradleKts(
        existingBuildGradleKts: String,
        updatedConfig: GenericUiProjectConfig
    ): String {
        val newGenericUiBlock = scriptGenerator.generateGenericUiDslBlock(updatedConfig)
        return replaceTopLevelBlock(existingBuildGradleKts, "genericui", newGenericUiBlock)
    }

    private fun extractBlockContent(source: String, blockName: String): String? {
        val headerRegex = Regex("""\b$blockName\s*\{""")
        val match = headerRegex.find(source) ?: return null
        val openBraceIndex = source.indexOf('{', match.range.first)
        if (openBraceIndex < 0) return null

        var depth = 0
        for (i in openBraceIndex until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(openBraceIndex + 1, i)
                    }
                }
            }
        }
        return null
    }

    private fun replaceTopLevelBlock(source: String, blockName: String, replacementBlock: String): String {
        val headerRegex = Regex("""(?m)^$blockName\s*\{""")
        val match = headerRegex.find(source) ?: return "$source\n\n$replacementBlock\n"
        val blockStart = match.range.first
        val openBraceIndex = source.indexOf('{', blockStart)
        if (openBraceIndex < 0) return source

        var depth = 0
        for (i in openBraceIndex until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(0, blockStart) +
                            replacementBlock +
                            source.substring(i + 1)
                    }
                }
            }
        }
        return source
    }
}
