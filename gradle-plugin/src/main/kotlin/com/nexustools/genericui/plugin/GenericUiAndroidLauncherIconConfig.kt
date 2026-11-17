package com.nexustools.genericui.plugin

import org.gradle.kotlin.dsl.GradleDsl
import java.util.Base64

/**
 * DSL configuration (`genericui { android { launcherIcon { ... } } }`) for Android adaptive
 * launcher icons, monochrome Android 13+ themed icons, and legacy `mipmap` density PNG assets.
 */
@GradleDsl
open class GenericUiAndroidLauncherIconConfig {
    var enabled: Boolean = true
    var source: String? = null
    var backgroundColor: String = "#FFFFFF"
    var backgroundColorNight: String? = null
    var backgroundDrawable: String? = null
    var foregroundInsetPercent: Int = 16
    var monochrome: Boolean = true
    var monochromeSource: String? = null
    var roundIcon: Boolean = true
    var legacyPngs: Boolean = true

    /**
     * Serializes this configuration into a deterministic string for Gradle `@Input` tracking.
     */
    fun toSerializedString(): String = buildString {
        append("v1|")
        append("enabled=").append(enabled).append('|')
        append("source=").append(encodeOptional(source)).append('|')
        append("backgroundColor=").append(encodeString(backgroundColor)).append('|')
        append("backgroundColorNight=").append(encodeOptional(backgroundColorNight)).append('|')
        append("backgroundDrawable=").append(encodeOptional(backgroundDrawable)).append('|')
        append("foregroundInsetPercent=").append(foregroundInsetPercent).append('|')
        append("monochrome=").append(monochrome).append('|')
        append("monochromeSource=").append(encodeOptional(monochromeSource)).append('|')
        append("roundIcon=").append(roundIcon).append('|')
        append("legacyPngs=").append(legacyPngs)
    }

    companion object {
        private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder: Base64.Decoder = Base64.getUrlDecoder()

        /**
         * Restores a [GenericUiAndroidLauncherIconConfig] previously serialized with
         * [toSerializedString], or returns default settings when [raw] is null/blank.
         */
        fun fromSerializedString(raw: String?): GenericUiAndroidLauncherIconConfig {
            val config = GenericUiAndroidLauncherIconConfig()
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return config

            val fields = text.split('|')
            for (token in fields) {
                val eqIdx = token.indexOf('=')
                if (eqIdx <= 0) continue
                val key = token.substring(0, eqIdx)
                val value = token.substring(eqIdx + 1)
                when (key) {
                    "enabled" -> config.enabled = value.toBooleanStrictOrNull() ?: true
                    "source" -> config.source = decodeOptional(value)
                    "backgroundColor" -> config.backgroundColor = decodeOptional(value) ?: "#FFFFFF"
                    "backgroundColorNight" -> config.backgroundColorNight = decodeOptional(value)
                    "backgroundDrawable" -> config.backgroundDrawable = decodeOptional(value)
                    "foregroundInsetPercent" -> config.foregroundInsetPercent = value.toIntOrNull() ?: 16
                    "monochrome" -> config.monochrome = value.toBooleanStrictOrNull() ?: true
                    "monochromeSource" -> config.monochromeSource = decodeOptional(value)
                    "roundIcon" -> config.roundIcon = value.toBooleanStrictOrNull() ?: true
                    "legacyPngs" -> config.legacyPngs = value.toBooleanStrictOrNull() ?: true
                }
            }
            return config
        }

        private fun encodeString(value: String): String =
            "b64:" + encoder.encodeToString(value.toByteArray(Charsets.UTF_8))

        private fun encodeOptional(value: String?): String {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return "null"
            return encodeString(trimmed)
        }

        private fun decodeOptional(raw: String): String? {
            if (raw == "null" || raw.isEmpty()) return null
            if (!raw.startsWith("b64:")) return raw
            return runCatching {
                String(decoder.decode(raw.removePrefix("b64:")), Charsets.UTF_8)
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
    }
}
