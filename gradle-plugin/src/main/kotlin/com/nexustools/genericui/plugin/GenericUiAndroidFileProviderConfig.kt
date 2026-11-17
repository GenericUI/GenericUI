package com.nexustools.genericui.plugin

import org.gradle.api.GradleException
import java.io.Serializable

/**
 * DSL configuration for synthesizing an AndroidX `FileProvider` `<provider>` entry and its
 * companion `res/xml/file_paths.xml` (`genericui { android { manifest { fileProvider { ... } } } }`).
 */
open class GenericUiAndroidFileProviderConfig : Serializable {
    internal val explicitFields: MutableSet<String> = linkedSetOf()

    var enabled: Boolean = false
        set(value) {
            field = value
            explicitFields.add("enabled")
        }

    var providerClass: String = "androidx.core.content.FileProvider"
        set(value) {
            field = value
            explicitFields.add("providerClass")
        }

    var authorities: String = "\${applicationId}.fileprovider"
        set(value) {
            field = value
            explicitFields.add("authorities")
        }

    val paths: MutableList<GenericUiAndroidFileProviderPathSpec> = mutableListOf()

    /**
     * Adds a `<*-path>` entry to `file_paths.xml`. Normalizes [kind] aliases:
     * `"cache"` -> `"cache-path"`, `"files"` -> `"files-path"`, `"external"` -> `"external-path"`,
     * `"external-files"` -> `"external-files-path"`, `"external-cache"` -> `"external-cache-path"`,
     * `"root"` -> `"root-path"`.
     */
    @JvmOverloads
    fun path(kind: String, path: String = ".", name: String? = null) {
        val normalizedTag = normalizePathTag(kind)
        val trimmedPath = path.trim().ifEmpty { "." }
        val baseName = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: normalizedTag.removeSuffix("-path").replace('-', '_')
        var resolvedName = baseName
        var counter = 2
        while (paths.any { it.name == resolvedName }) {
            resolvedName = "${baseName}_$counter"
            counter++
        }
        if ("enabled" !in explicitFields) {
            enabled = true
        }
        paths.add(
            GenericUiAndroidFileProviderPathSpec(
                tag = normalizedTag,
                name = resolvedName,
                path = trimmedPath
            )
        )
    }

    /** Merges explicit fields and path entries from [overlay] onto a copy of this configuration. */
    fun overlayWith(overlay: GenericUiAndroidFileProviderConfig): GenericUiAndroidFileProviderConfig =
        copySpec().apply {
            if ("enabled" in overlay.explicitFields) enabled = overlay.enabled
            if ("providerClass" in overlay.explicitFields) providerClass = overlay.providerClass
            if ("authorities" in overlay.explicitFields) authorities = overlay.authorities
            for (p in overlay.paths) {
                if (p !in paths) paths.add(p.copy())
            }
        }

    /** Returns an independent deep copy of this FileProvider configuration. */
    fun copySpec(): GenericUiAndroidFileProviderConfig = GenericUiAndroidFileProviderConfig().also { copy ->
        copy.enabled = enabled
        copy.providerClass = providerClass
        copy.authorities = authorities
        paths.mapTo(copy.paths) { it.copy() }
        copy.explicitFields.clear()
        copy.explicitFields.addAll(explicitFields)
    }

    companion object {
        /** Normalizes shorthand FileProvider path kinds to valid XML element tags. */
        fun normalizePathTag(kind: String): String = when (kind.trim().lowercase()) {
            "cache", "cache-path" -> "cache-path"
            "files", "files-path" -> "files-path"
            "external", "external-path" -> "external-path"
            "external-files", "external_files", "external-files-path" -> "external-files-path"
            "external-cache", "external_cache", "external-cache-path" -> "external-cache-path"
            "root", "root-path" -> "root-path"
            else -> throw GradleException(
                "Unsupported Android FileProvider path kind '$kind'. Expected one of: " +
                    "cache, files, external, external-files, external-cache, root."
            )
        }
    }
}
