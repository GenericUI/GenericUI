package com.nexustools.genericui.plugin

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidSvgAndLauncherIconTest {

    @Test
    fun `builtin and official converters handle stroke-only currentColor icons and shapes without black fill blobs`() {
        val strokeIconSvg = """
            <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"
                 fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9" />
              <line x1="8" y1="12" x2="16" y2="12" />
              <rect x="4" y="4" width="16" height="16" rx="3" />
              <g transform="translate(2, 2)">
                <polygon points="4,4 10,4 7,9" />
              </g>
            </svg>
        """.trimIndent()

        // 1. Offline built-in DOM converter
        val builtinResult = GenericUiBuiltinSvgToVectorConverter.convert(strokeIconSvg, "ic_cast.svg")
        assertFalse(builtinResult.usedOfficialTool)
        assertFalse(builtinResult.fellBackToRaster)
        assertTrue(builtinResult.warnings.isEmpty(), "Expected no warnings: ${builtinResult.warnings}")
        val builtinXml = assertNotNull(builtinResult.vectorXml)
        assertTrue(builtinXml.contains("android:width=\"24dp\""))
        assertTrue(builtinXml.contains("android:height=\"24dp\""))
        assertTrue(builtinXml.contains("android:strokeColor=\"#FF000000\""), "currentColor stroke should resolve to #FF000000")
        assertFalse(builtinXml.contains("android:fillColor="), "Stroke-only paths with fill=\"none\" must omit android:fillColor")
        assertTrue(builtinXml.contains("android:strokeLineCap=\"round\""))
        assertTrue(builtinXml.contains("android:strokeLineJoin=\"round\""))
        assertTrue(builtinXml.contains("M6,6"), "Group transform translate(2,2) should shift polygon coordinates")

        // 2. Official Svg2Vector via isolated URLClassLoader (or clean fallback if cache missing)
        val project = ProjectBuilder.builder().build()
        val cacheDir = File(project.projectDir, "build/svg-cache")
        val officialResult = GenericUiAndroidSvgConverter.convertSvgText(
            svgText = strokeIconSvg,
            sourceName = "ic_cast.svg",
            cacheDir = cacheDir,
            project = project,
            allowNetworkDownload = false
        )
        assertTrue(officialResult.usedOfficialTool, "Expected official Android Svg2Vector tool to be resolved and executed")
        assertFalse(officialResult.fellBackToRaster)
        assertTrue(officialResult.warnings.isEmpty())
        val officialXml = assertNotNull(officialResult.vectorXml)
        assertTrue(officialXml.contains("android:strokeColor=\"#FF000000\""), "currentColor stroke should resolve to #FF000000")
        assertFalse(officialXml.contains("android:fillColor="), "Stroke-only icon must omit android:fillColor even when fill=\"none\" is only on <svg>")
    }

    @Test
    fun `oversized 512x512 and rectangular SVGs clamp dp dimensions proportionally to 108dp and cache results`() {
        val wideSvg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 256">
              <ellipse cx="256" cy="128" rx="200" ry="90" fill="#2563EB" fill-opacity="0.8" />
              <polyline points="40,200 180,80 320,160 470,40" fill="none" stroke="#FFFFFF" stroke-width="12" />
            </svg>
        """.trimIndent()

        val builtin = GenericUiBuiltinSvgToVectorConverter.convert(wideSvg, "wide.svg")
        val builtinXml = assertNotNull(builtin.vectorXml)
        assertTrue(builtinXml.contains("android:width=\"108dp\""), "Expected width clamped to 108dp: $builtinXml")
        assertTrue(builtinXml.contains("android:height=\"54dp\""), "Expected proportional height 54dp: $builtinXml")
        assertTrue(builtinXml.contains("android:viewportWidth=\"512\""))
        assertTrue(builtinXml.contains("android:viewportHeight=\"256\""))

        val project = ProjectBuilder.builder().build()
        val cacheDir = File(project.projectDir, "build/svg-cache")
        val first = GenericUiAndroidSvgConverter.convertSvgText(
            svgText = wideSvg,
            sourceName = "wide.svg",
            cacheDir = cacheDir,
            project = project,
            allowNetworkDownload = false
        )
        assertNotNull(first.vectorXml)
        assertTrue(first.vectorXml!!.contains("android:width=\"108dp\""))
        assertTrue(first.vectorXml!!.contains("android:height=\"54dp\""))
        assertTrue(cacheDir.listFiles()?.any { it.name.endsWith(".cache") } == true, "Expected content-hash cache file")

        val second = GenericUiAndroidSvgConverter.convertSvgText(
            svgText = wideSvg,
            sourceName = "wide.svg",
            cacheDir = cacheDir,
            project = project,
            allowNetworkDownload = false
        )
        assertEquals(first.vectorXml, second.vectorXml)
    }

    @Test
    fun `unsupported SVG elements trigger raster fallback preserving folder qualifiers or fail when strict`() {
        val project = ProjectBuilder.builder().withName("svg-fallback-app").build()
        project.group = "com.example.fallback"
        project.pluginManager.apply("com.nexustools.genericui")

        val resRoot = File(project.projectDir, "src/commonMain/resources")
        val drawableDir = File(resRoot, "drawable").apply { mkdirs() }
        val drawableNightDir = File(resRoot, "drawable-night").apply { mkdirs() }
        val mipmapAnydpiDir = File(resRoot, "mipmap-anydpi").apply { mkdirs() }

        // Clean vector drawables in both default and -night qualifiers, plus mipmap-anydpi
        File(drawableDir, "ic_play.svg").writeText(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="6,4 20,12 6,20" /></svg>"""
        )
        File(drawableNightDir, "ic_play.svg").writeText(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#E2E8F0" stroke-width="2"><polygon points="6,4 20,12 6,20" /></svg>"""
        )
        File(mipmapAnydpiDir, "badge_logo.svg").writeText(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><circle cx="24" cy="24" r="20" fill="#0D6EFD" /></svg>"""
        )

        // Unsupported SVG with <text> and <filter> in drawable-night
        File(drawableNightDir, "hero_text_badge.svg").writeText(
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 32">
              <defs><filter id="blur"><feGaussianBlur stdDeviation="2"/></filter></defs>
              <rect x="0" y="0" width="64" height="32" rx="8" fill="#1E293B" filter="url(#blur)" />
              <text x="12" y="22" fill="#FFFFFF">HD</text>
            </svg>
            """.trimIndent()
        )

        val resTask = project.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
        resTask.failOnSvgConversionWarnings.set(false)
        resTask.compileResources()

        val androidResDir = File(resTask.backendOutputDirectory.get().asFile, "android/res")
        assertTrue(File(androidResDir, "drawable/ic_play.xml").isFile, "Default drawable/ic_play.xml should be emitted")
        assertTrue(File(androidResDir, "drawable-night/ic_play.xml").isFile, "Qualified drawable-night/ic_play.xml should be preserved")
        assertTrue(File(androidResDir, "mipmap-anydpi/badge_logo.xml").isFile, "Qualified mipmap-anydpi/badge_logo.xml should be preserved")

        for (density in listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")) {
            val png = File(androidResDir, "drawable-night-$density/hero_text_badge.png")
            assertTrue(png.isFile && png.length() > 100L, "Expected multi-density raster fallback at drawable-night-$density/hero_text_badge.png")
        }
        assertFalse(File(androidResDir, "drawable-night/hero_text_badge.xml").exists())

        // Strict mode must fail with GradleException on warnings
        resTask.failOnSvgConversionWarnings.set(true)
        assertFailsWith<GradleException> {
            resTask.compileResources()
        }
    }

    @Test
    fun `launcher icon generator emits vector icon, adaptive layers, outlined monochrome, legacy mipmaps, and Material theme`() {
        val project = ProjectBuilder.builder().withName("launcher-app").build()
        project.group = "com.example.launcher"
        project.pluginManager.apply("com.nexustools.genericui")

        val resRoot = File(project.projectDir, "src/commonMain/resources")
        val drawableDir = File(resRoot, "drawable").apply { mkdirs() }
        // Outlined multi-layer artwork (paths with BOTH fill and stroke) + fill-only accent dot
        File(drawableDir, "icon.svg").writeText(
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
              <rect x="48" y="96" width="416" height="320" rx="48" fill="#FF7E96" stroke="#000000" stroke-width="24" />
              <rect x="96" y="144" width="320" height="224" rx="24" fill="#181920" stroke="#000000" stroke-width="16" />
              <circle cx="256" cy="256" r="44" fill="#FFD700" />
            </svg>
            """.trimIndent()
        )

        val launcherConfig = GenericUiAndroidLauncherIconConfig().apply {
            enabled = true
            backgroundColor = "#121826"
            backgroundColorNight = "#05070B"
            foregroundInsetPercent = 18
            monochrome = true
            roundIcon = true
            legacyPngs = true
        }
        val roundTripped = GenericUiAndroidLauncherIconConfig.fromSerializedString(launcherConfig.toSerializedString())
        assertEquals(launcherConfig.enabled, roundTripped.enabled)
        assertEquals(launcherConfig.backgroundColor, roundTripped.backgroundColor)
        assertEquals(launcherConfig.backgroundColorNight, roundTripped.backgroundColorNight)
        assertEquals(launcherConfig.foregroundInsetPercent, roundTripped.foregroundInsetPercent)
        assertEquals(launcherConfig.monochrome, roundTripped.monochrome)
        assertEquals(launcherConfig.roundIcon, roundTripped.roundIcon)
        assertEquals(launcherConfig.legacyPngs, roundTripped.legacyPngs)

        val resTask = project.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
        resTask.androidLauncherIconConfig.set(launcherConfig.toSerializedString())
        resTask.compileResources()

        val androidRes = File(resTask.backendOutputDirectory.get().asFile, "android/res")

        // Vector drawable icon.xml without stale icon.png
        val iconXml = File(androidRes, "drawable/icon.xml")
        assertTrue(iconXml.isFile, "Expected vector drawable/icon.xml")
        assertFalse(File(androidRes, "drawable/icon.png").exists(), "Stale drawable/icon.png must not coexist with drawable/icon.xml")
        val iconXmlText = iconXml.readText()
        assertTrue(iconXmlText.contains("android:width=\"108dp\""))
        assertTrue(iconXmlText.contains("android:viewportWidth=\"512\""))

        // Foreground inset drawable
        val fgXml = File(androidRes, "drawable/ic_launcher_foreground.xml").readText()
        assertTrue(fgXml.contains("android:drawable=\"@drawable/icon\""))
        assertTrue(fgXml.contains("android:inset=\"18%\""))

        // Day & night background colors
        assertTrue(File(androidRes, "values/ic_launcher_background.xml").readText().contains("#121826"))
        assertTrue(File(androidRes, "values-night/ic_launcher_background.xml").readText().contains("#05070B"))

        // Intelligent monochrome derivation: dual fill+stroke keeps stroke=#FFFFFFFF & fill=#00000000, fill-only gets #FFFFFFFF
        val monoXml = File(androidRes, "drawable/ic_launcher_monochrome.xml").readText()
        assertTrue(monoXml.contains("android:strokeColor=\"#FFFFFFFF\""))
        assertTrue(monoXml.contains("android:fillColor=\"#00000000\""))
        assertTrue(monoXml.contains("android:fillColor=\"#FFFFFFFF\""))

        // Adaptive launcher icon XMLs
        val adaptiveMain = File(androidRes, "mipmap-anydpi-v26/ic_launcher.xml").readText()
        val adaptiveRound = File(androidRes, "mipmap-anydpi-v26/ic_launcher_round.xml").readText()
        for (xml in listOf(adaptiveMain, adaptiveRound)) {
            assertTrue(xml.contains("<adaptive-icon"))
            assertTrue(xml.contains("<background android:drawable=\"@color/ic_launcher_background\""))
            assertTrue(xml.contains("<foreground android:drawable=\"@drawable/ic_launcher_foreground\""))
            assertTrue(xml.contains("<monochrome android:drawable=\"@drawable/ic_launcher_monochrome\""))
        }

        // Legacy mipmaps at exact pixel sizes with masked corners
        val expectedSizes = mapOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192
        )
        for ((density, px) in expectedSizes) {
            val sqFile = File(androidRes, "mipmap-$density/ic_launcher.png")
            val rdFile = File(androidRes, "mipmap-$density/ic_launcher_round.png")
            assertTrue(sqFile.isFile, "Expected mipmap-$density/ic_launcher.png")
            assertTrue(rdFile.isFile, "Expected mipmap-$density/ic_launcher_round.png")
            val sqImg = ImageIO.read(sqFile)
            val rdImg = ImageIO.read(rdFile)
            assertEquals(px, sqImg.width)
            assertEquals(px, sqImg.height)
            assertEquals(px, rdImg.width)
            assertEquals(px, rdImg.height)
            val cornerAlpha = (rdImg.getRGB(0, 0) ushr 24) and 0xFF
            assertEquals(0, cornerAlpha, "Round launcher icon corner (0,0) must be transparent")
            val centerAlpha = (rdImg.getRGB(px / 2, px / 2) ushr 24) and 0xFF
            assertTrue(centerAlpha > 200, "Round launcher icon center must be opaque")
        }

        // Material Components themes
        val dayTheme = File(androidRes, "values/themes.xml").readText()
        val nightTheme = File(androidRes, "values-night/themes.xml").readText()
        assertTrue(dayTheme.contains("parent=\"Theme.MaterialComponents.Light.NoActionBar\""))
        assertTrue(nightTheme.contains("parent=\"Theme.MaterialComponents.NoActionBar\""))
        for (itemName in listOf("colorPrimary", "colorPrimaryDark", "colorAccent", "colorPrimaryVariant", "colorSecondary")) {
            assertTrue(dayTheme.contains("name=\"$itemName\""), "Expected Material theme attribute $itemName")
        }
    }

    @Test
    fun `launcher icon supports custom backgroundDrawable, explicit monochromeSource, and toggle flags`() {
        val project = ProjectBuilder.builder().withName("custom-launcher-app").build()
        project.pluginManager.apply("com.nexustools.genericui")

        File(project.projectDir, "bg.svg").writeText(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><rect x="0" y="0" width="108" height="108" fill="#1E3A8A" /></svg>"""
        )
        File(project.projectDir, "mono.svg").writeText(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M4,4 L20,20" fill="none" stroke="#111111" stroke-width="2" /></svg>"""
        )

        val customCfg = GenericUiAndroidLauncherIconConfig().apply {
            enabled = true
            backgroundDrawable = "bg.svg"
            monochrome = true
            monochromeSource = "mono.svg"
            roundIcon = false
            legacyPngs = false
        }

        val resTask = project.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
        resTask.androidLauncherIconConfig.set(customCfg.toSerializedString())
        resTask.compileResources()

        val androidRes = File(resTask.backendOutputDirectory.get().asFile, "android/res")
        assertTrue(File(androidRes, "drawable/ic_launcher_background_layer.xml").isFile)
        val adaptiveXml = File(androidRes, "mipmap-anydpi-v26/ic_launcher.xml").readText()
        assertTrue(adaptiveXml.contains("<background android:drawable=\"@drawable/ic_launcher_background_layer\""))
        assertFalse(File(androidRes, "mipmap-anydpi-v26/ic_launcher_round.xml").exists())
        assertFalse(File(androidRes, "mipmap-xhdpi/ic_launcher.png").exists())

        val monoXml = File(androidRes, "drawable/ic_launcher_monochrome.xml").readText()
        assertTrue(monoXml.contains("android:strokeColor=\"#FFFFFFFF\""))
        assertNull(File(androidRes, "values/ic_launcher_background.xml").takeIf { it.exists() })
    }

    @Test
    fun `real Episode Caster SVG artwork converts cleanly and passes aapt2 compile`() {
        val project = ProjectBuilder.builder().withName("ec-svg-test").build()
        project.group = "com.example.ecsvg"
        project.pluginManager.apply("com.nexustools.genericui")

        val resRoot = File(project.projectDir, "src/commonMain/resources")
        val drawableDir = File(resRoot, "drawable").apply { mkdirs() }

        // Exact ic_cast.svg from Episode Caster (stroke="currentColor", fill="none" ONLY on root <svg>)
        File(drawableDir, "ic_cast.svg").writeText(
            """
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none">
              <path d="M3,8.2502V8.2002C3,7.08009 3,6.51962 3.21799,6.0918C3.40973,5.71547 3.71547,5.40973 4.0918,5.21799C4.51962,5 5.08009,5 6.2002,5H17.8002C18.9203,5 19.4796,5 19.9074,5.21799C20.2837,5.40973 20.5905,5.71547 20.7822,6.0918C21,6.5192 21,7.07899 21,8.19691V15.8031C21,16.921 21,17.48 20.7822,17.9074C20.5905,18.2837 20.2839,18.5905 19.9076,18.7822C19.4802,19 18.921,19 17.8031,19H14M5,19C5,17.8954 4.10457,17 3,17M8,19C8,16.2386 5.76142,14 3,14M11,19C11,14.5817 7.41828,11 3,11" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            """.trimIndent()
        )

        // Exact icon.svg from Episode Caster (line, circle, path, rect, polygon, fill="none" on root <svg>)
        File(resRoot, "icon.svg").writeText(
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512" fill="none">
              <line x1="100" y1="31.5" x2="249" y2="82" stroke="#000000" stroke-width="20" stroke-linecap="round"/>
              <line x1="397.5" y1="31.5" x2="249" y2="82" stroke="#000000" stroke-width="20" stroke-linecap="round"/>
              <circle cx="100" cy="31.5" r="22" fill="#E0E7EF" stroke="#000000" stroke-width="20"/>
              <rect x="34" y="112" width="444" height="345" rx="46" fill="#FF7E96"/>
              <path d="M 118 162 Q 214 150 310 162 A 30 30 0 0 1 338 190 Q 352 284 338 378 A 30 30 0 0 1 310 406 Q 214 418 118 406 A 30 30 0 0 1 90 378 Q 76 284 90 190 A 30 30 0 0 1 118 162 Z" fill="#BEDAFF" stroke="#000000" stroke-width="20" stroke-linejoin="round"/>
              <polygon points="214,201 236,248 288,255 250,291 260,343 214,318 168,343 178,291 140,255 192,248" fill="#FFC135" stroke="#000000" stroke-width="20" stroke-linejoin="round"/>
            </svg>
            """.trimIndent()
        )

        val resTask = project.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
        resTask.configuredIconPath.set("icon.svg")
        resTask.compileResources()

        val androidRes = File(resTask.backendOutputDirectory.get().asFile, "android/res")
        val icCastXml = File(androidRes, "drawable/ic_cast.xml").readText()
        assertFalse(icCastXml.contains("android:fillColor="), "ic_cast.xml must preserve fill=\"none\" and omit fillColor:\n$icCastXml")
        assertTrue(icCastXml.contains("android:strokeColor=\"#FF000000\""), "ic_cast.xml must resolve currentColor stroke:\n$icCastXml")
        assertTrue(icCastXml.contains("android:strokeWidth=\"2\""), "ic_cast.xml must preserve stroke-width=2:\n$icCastXml")
        assertTrue(icCastXml.contains("android:strokeLineCap=\"round\""), "ic_cast.xml must preserve lineCap=round:\n$icCastXml")
        assertTrue(icCastXml.contains("android:strokeLineJoin=\"round\""), "ic_cast.xml must preserve lineJoin=round:\n$icCastXml")

        val appIconXml = File(androidRes, "drawable/icon.xml").readText()
        assertTrue(appIconXml.contains("android:viewportWidth=\"512\""), "icon.xml must keep 512 viewport")
        assertTrue(appIconXml.contains("#FFFF7E96") || appIconXml.contains("#ff7e96", ignoreCase = true), "icon.xml must preserve TV pink fill:\n$appIconXml")
        assertTrue(appIconXml.contains("#FFBEDAFF") || appIconXml.contains("#bedaff", ignoreCase = true), "icon.xml must preserve screen blue fill:\n$appIconXml")
        assertTrue(appIconXml.contains("#FFFFC135") || appIconXml.contains("#ffc135", ignoreCase = true), "icon.xml must preserve star gold fill:\n$appIconXml")

        val aapt2 = findHostAapt2Executable()
        if (aapt2 != null) {
            val flataOut = File(project.projectDir, "build/test-aapt2-res.flata").apply { parentFile.mkdirs() }
            val proc = ProcessBuilder(
                aapt2.absolutePath,
                "compile",
                "--dir",
                androidRes.absolutePath,
                "-o",
                flataOut.absolutePath
            ).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, proc.waitFor(), "aapt2 compile failed on generated android/res:\n$output")
            assertTrue(flataOut.isFile && flataOut.length() > 0L)
        }
    }

    private fun findHostAapt2Executable(): File? {
        val candidates = listOfNotNull(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            System.getProperty("user.home")?.let { "$it/AppData/Local/Android/Sdk" }
        ).map { File(it) }.filter { it.isDirectory }
        val exeName = if (System.getProperty("os.name").lowercase().contains("win")) "aapt2.exe" else "aapt2"
        for (sdkDir in candidates) {
            val btDir = File(sdkDir, "build-tools")
            if (!btDir.isDirectory) continue
            val versions = btDir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }.orEmpty()
            for (v in versions) {
                val exe = File(v, exeName)
                if (exe.isFile) return exe
            }
        }
        return null
    }
}
