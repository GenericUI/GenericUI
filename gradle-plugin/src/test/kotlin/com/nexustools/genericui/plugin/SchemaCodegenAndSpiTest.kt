package com.nexustools.genericui.plugin

import com.nexustools.genericui.plugin.spi.ExtensionBuildContext
import com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
import com.nexustools.genericui.plugin.spi.SchemaExtensionContributor
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchemaCodegenAndSpiTest {

    @Test
    fun testSchemaDslWiringMediaAutoDependencyAndSpiContributor() {
        val extConfig = GenericUiExtensionsConfig()
        assertFalse(extConfig.schemaEnabled)
        assertTrue(":extensions:data:schema" !in extConfig.enabledExtensionModulePaths())

        extConfig.schema(true)
        assertTrue(":extensions:data:schema" in extConfig.enabledExtensionModulePaths())
        assertTrue(":extensions:data:schema:dsl" !in extConfig.enabledExtensionModulePaths())

        extConfig.dsl(true)
        assertTrue(":extensions:data:schema:dsl" in extConfig.enabledExtensionModulePaths())

        // Enabling media automatically activates schema + overlay
        val mediaOnly = GenericUiExtensionsConfig().apply {
            media(true)
        }
        assertTrue(mediaOnly.schemaEnabled)
        assertTrue(mediaOnly.overlayEnabled)
        assertTrue(":extensions:data:schema" in mediaOnly.enabledExtensionModulePaths())

        // Verify SPI registry includes SchemaExtensionContributor and emits registrar call across backends
        val registryContributors = GenericUiExtensionContributorRegistry.allContributors(null)
        assertTrue(registryContributors.any { it === SchemaExtensionContributor })

        val swingCalls = extConfig.providerInstallerCallsForBackend(
            backendScope = GenericUiVariantScope.SWING,
            generatedPackageName = "com.example.studio.generated"
        )
        assertTrue("com.example.studio.generated.GeneratedSchemaRegistrar.ensureRegistered()" in swingCalls)

        val androidCalls = extConfig.providerInstallerCallsForBackend(
            backendScope = GenericUiVariantScope.ANDROID,
            generatedPackageName = "com.example.studio"
        )
        assertTrue("com.example.studio.generated.GeneratedSchemaRegistrar.ensureRegistered()" in androidCalls)

        // Generated launcher sources are intentionally empty because generateGenericUiSchemas writes to commonMain
        val ctx = ExtensionBuildContext(
            projectRootDir = File("."),
            projectName = "studio",
            appId = "com.example.studio",
            appName = "Studio",
            appVersion = "1.0.0",
            baseName = "studio",
            generatedPackageName = "com.example.studio.generated",
            enabledExtensionModulePaths = setOf(":extensions:data:schema")
        )
        assertTrue(SchemaExtensionContributor.generatedLauncherSources(GenericUiVariantScope.SWING, ctx).isEmpty())
    }

    @Test
    fun testCompileTimeSchemaCodegenEmitsStrictOneTypePerFileAndRegistrar() {
        val tempRoot = Files.createTempDirectory("genericui_schema_codegen").toFile()
        try {
            val srcDir = File(tempRoot, "src/commonMain/kotlin/com/demo/model").apply { mkdirs() }
            val outDir = File(tempRoot, "build/generated/genericui/schema/commonMain/kotlin")

            File(srcDir, "DemoModels.kt").writeText(
                """
                package com.demo.model

                import com.nexustools.genericui.ext.schema.SchemaData
                import com.nexustools.genericui.ext.schema.SchemaEnabledWhen
                import com.nexustools.genericui.ext.schema.SchemaField
                import com.nexustools.genericui.ext.schema.SchemaIgnore
                import com.nexustools.genericui.ext.schema.SchemaVisibleWhen
                import com.nexustools.genericui.ext.schema.SchemaWidgetHint
                import com.nexustools.genericui.style.color.RgbaColor

                enum class RenderQuality {
                    LOW,
                    HIGH
                }

                @SchemaData(id = "audio_cfg", title = "Audio Config")
                data class AudioConfig(
                    @SchemaField(min = 0.0, max = 100.0, unit = "%")
                    val volume: Int = 80,
                    val muted: Boolean = false
                )

                @SchemaData(id = "studio_settings", title = "Studio Settings", description = "Main export profile")
                data class StudioSettings(
                    val enabled: Boolean = true,
                    val maxSizeMb: Long = 2048L,
                    @SchemaField(min = 0.0, max = 1.0, step = 0.05, precision = 2)
                    val opacity: Float = 0.9f,
                    @SchemaField(group = "video", legacyKeys = ["legacy_scale"])
                    val scale: Double = 1.5,
                    @SchemaField(required = true, maxLength = 64, placeholder = "Profile name")
                    val name: String = "Default",
                    val quality: RenderQuality = RenderQuality.HIGH,
                    val tags: List<String> = listOf("hdr", "stereo"),
                    val accent: RgbaColor = RgbaColor(255, 128, 0, 255),
                    val audio: AudioConfig = AudioConfig(),
                    @SchemaVisibleWhen(field = "enabled")
                    @SchemaEnabledWhen(field = "quality", equalsValue = "HIGH")
                    val highBitrateKbps: Int = 12000,
                    @SchemaIgnore
                    val transientCacheKey: String = "tmp"
                )
                """.trimIndent(),
                Charsets.UTF_8
            )

            val generated = SchemaExtensionContributor.generateSchemaSources(
                sourceDirectories = listOf(File(tempRoot, "src/commonMain/kotlin")),
                outputDirectory = outDir,
                registrarPackageName = "com.demo.generated"
            )

            assertEquals(3, generated.size)
            val audioFile = File(outDir, "com/demo/model/AudioConfigSchemaDescriptor.kt")
            val studioFile = File(outDir, "com/demo/model/StudioSettingsSchemaDescriptor.kt")
            val registrarFile = File(outDir, "com/demo/generated/GeneratedSchemaRegistrar.kt")
            assertTrue(audioFile.isFile)
            assertTrue(studioFile.isFile)
            assertTrue(registrarFile.isFile)

            // Verify strict 1 public type per file in every generated file
            val topLevelTypeRegex = Regex("""(?m)^(?:object|class|interface|enum\s+class)\s+([A-Za-z_][A-Za-z0-9_]*)\b""")
            for (genFile in listOf(audioFile, studioFile, registrarFile)) {
                val text = genFile.readText()
                val declaredTypes = topLevelTypeRegex.findAll(text).map { it.groupValues[1] }.toList()
                assertEquals(1, declaredTypes.size, "Expected exactly 1 public type in ${genFile.name}, got $declaredTypes")
                assertEquals(genFile.nameWithoutExtension, declaredTypes.first())
            }

            val studioText = studioFile.readText()
            assertTrue("BooleanFieldSpec(" in studioText)
            assertTrue("isLong = true" in studioText)
            assertTrue("isFloat = true" in studioText)
            assertTrue("legacyKeys = listOf(\"legacy_scale\")" in studioText)
            assertTrue("SelectFieldSpec(" in studioText)
            assertTrue("OrderedMultiSelectFieldSpec(" in studioText)
            assertTrue("ColorFieldSpec(" in studioText)
            assertTrue("SchemaGroupSpec(" in studioText)
            assertTrue("SchemaCondition.IsTrue(\"enabled\")" in studioText)
            assertTrue("SchemaCondition.Equals(\"quality\", \"HIGH\")" in studioText)
            assertFalse("transientCacheKey" in studioText)
            assertTrue("override fun toPreferenceElement(" in studioText)
            assertTrue("override fun fromPreferenceElement(" in studioText)
            assertTrue("override fun toJson(" in studioText)
            assertTrue("override fun fromJson(" in studioText)
            assertTrue("override fun saveTo(" in studioText)
            assertTrue("override fun loadFrom(" in studioText)

            val registrarText = registrarFile.readText()
            assertTrue("SchemaRegistry.register(com.demo.model.AudioConfigSchemaDescriptor, com.demo.model.AudioConfig::class)" in registrarText)
            assertTrue("SchemaRegistry.register(com.demo.model.StudioSettingsSchemaDescriptor, com.demo.model.StudioSettings::class)" in registrarText)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testPluginRegistersGenerateGenericUiSchemasTask() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(GenericUiGradlePlugin::class.java)
        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        ext.extensions {
            schema(true)
        }
        val task = project.tasks.findByName("generateGenericUiSchemas")
        assertNotNull(task)
        assertTrue(task is GenericUiGenerateSchemaSourcesTask)
    }
}
