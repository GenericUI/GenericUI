package com.nexustools.genericui.window

import com.nexustools.genericui.component.dock.PanelWindow
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiLaunchSequence
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.AppleIconTileComposer
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowAspectRatioAndAppleIconTileTest {

    private var savedThemeMode = GenericUI.defaultThemeMode
    private var savedThemeConfig = GenericUI.defaultThemeConfig
    private var savedAppName = GenericUI.applicationName
    private var savedActiveFactory = GenericUI.activeFactory
    private var savedAppleIconProvided = GenericUI.appleIconExplicitlyProvided

    @BeforeTest
    fun saveGlobals() {
        savedThemeMode = GenericUI.defaultThemeMode
        savedThemeConfig = GenericUI.defaultThemeConfig
        savedAppName = GenericUI.applicationName
        savedActiveFactory = GenericUI.activeFactory
        savedAppleIconProvided = GenericUI.appleIconExplicitlyProvided
    }

    @AfterTest
    fun restoreGlobals() {
        GenericUI.defaultThemeMode = savedThemeMode
        GenericUI.defaultThemeConfig = savedThemeConfig
        GenericUI.applicationName = savedAppName
        GenericUI.activeFactory = savedActiveFactory
        GenericUI.appleIconExplicitlyProvided = savedAppleIconProvided
    }

    @Test
    fun alwaysOnTopAnd16By9AspectRatioLockBehaveCorrectlyAcrossWindowedMaximizeAndFullscreen() {
        val window = Window(title = "Receiver", initialSize = Size(1280, 800))
        assertFalse(window.isAlwaysOnTop)
        window.setAlwaysOnTop(true)
        assertTrue(window.isAlwaysOnTop)
        window.setAlwaysOnTop(false)
        assertFalse(window.isAlwaysOnTop)

        val panel = PanelWindow(title = "Tool", ownerWindow = window)
        assertTrue(panel.isAlwaysOnTop)

        // Setting 16:9 clamps 1280x800 to 1280x720 while NORMAL
        window.setAspectRatio(16, 9)
        assertEquals(16.0 / 9.0, window.getAspectRatio() ?: 0.0, 1e-6)
        assertEquals(Size(1280, 720), window.getWindowSize())

        // Programmatic resize while NORMAL is clamped to 16:9 and respects minWindowSize
        window.setMinWindowSize(Size(640, 360))
        window.setWindowSize(Size(960, 900))
        assertEquals(Size(960, 540), window.getWindowSize())

        // Maximize & Fullscreen are completely unconstrained (no 16:9 clamp!)
        window.setWindowState(WindowState.MAXIMIZED)
        window.setWindowSize(Size(1920, 1200))
        assertEquals(Size(1920, 1200), window.getWindowSize())

        window.restore()
        assertEquals(WindowState.NORMAL, window.getWindowState())
        assertEquals(Size(960, 540), window.getWindowSize())

        window.setFullscreen(true)
        assertTrue(window.isFullscreen())
        window.setWindowSize(Size(2560, 1600))
        assertEquals(Size(2560, 1600), window.getWindowSize())

        window.setFullscreen(false)
        assertFalse(window.isFullscreen())
        assertEquals(WindowState.NORMAL, window.getWindowState())
        assertEquals(Size(960, 540), window.getWindowSize())

        window.setAspectRatio(null)
        assertNull(window.getAspectRatio())
    }

    @Test
    fun launchSequenceAppliesWindowAlwaysOnTopAspectRatioAndAppleIconFlag() {
        class DummyFragment : Fragment()

        val descriptor = GenericUiAppDescriptor(
            appId = "com.example.caster",
            appName = "Episode Caster",
            windowTitle = "Episode Caster Receiver",
            windowWidth = 1280,
            windowHeight = 800,
            windowAlwaysOnTop = true,
            windowAspectRatio = 16.0 / 9.0,
            appleIconExplicitlyProvided = true,
            mainFragmentClass = DummyFragment::class,
            mainFragmentFactory = { _ -> DummyFragment() }
        )

        val app = GenericUiLaunchSequence.prepareApplication(
            factory = null,
            descriptor = descriptor,
            isSystemTarget = true
        )

        assertTrue(app.mainWindow.isAlwaysOnTop)
        assertEquals(16.0 / 9.0, app.mainWindow.getAspectRatio() ?: 0.0, 1e-6)
        assertEquals(Size(1280, 720), app.mainWindow.getWindowSize())
        assertTrue(GenericUI.appleIconExplicitlyProvided)
    }

    @Test
    fun appleIconTileComposerWrapsArtworkOnWhiteSquircleForMacAndFullBleedForIos() {
        val srcW = 64
        val srcH = 64
        val srcRgba = ByteArray(srcW * srcH * 4)
        for (i in 0 until srcW * srcH) {
            srcRgba[i * 4] = 230.toByte()
            srcRgba[i * 4 + 1] = 57.toByte()
            srcRgba[i * 4 + 2] = 70.toByte()
            srcRgba[i * 4 + 3] = 255.toByte()
        }
        val sourceBuffer = ImageBuffer(srcW, srcH, pixelFormat = PixelFormat.RGBA_8888, bytes = srcRgba)

        // macOS Dock mode: transparent corners, white squircle rim, centered red artwork
        val macIcon = AppleIconTileComposer.composeImageBuffer(sourceBuffer, targetSize = 128, fullBleedSquare = false)
        val macBytes = macIcon.bytes
        val cornerAlpha = macBytes[3].toInt() and 0xFF
        assertEquals(0, cornerAlpha, "macOS icon canvas corners outside squircle must be transparent")

        // White border ring just inside top-center of tile (~y=14, x=64 -> pure/near white)
        val borderIdx = (16 * 128 + 64) * 4
        assertTrue((macBytes[borderIdx].toInt() and 0xFF) >= 245)
        assertTrue((macBytes[borderIdx + 1].toInt() and 0xFF) >= 245)
        assertTrue((macBytes[borderIdx + 2].toInt() and 0xFF) >= 245)
        assertTrue((macBytes[borderIdx + 3].toInt() and 0xFF) >= 245)

        // Center pixel is the source red artwork
        val centerIdx = (64 * 128 + 64) * 4
        assertTrue((macBytes[centerIdx].toInt() and 0xFF) >= 220)
        assertTrue((macBytes[centerIdx + 1].toInt() and 0xFF) <= 75)

        // iOS/iPadOS AppIcon mode: full-bleed opaque white at corner, red artwork centered
        val iosIcon = AppleIconTileComposer.composeImageBuffer(sourceBuffer, targetSize = 128, fullBleedSquare = true)
        val iosBytes = iosIcon.bytes
        assertEquals(255, iosBytes[0].toInt() and 0xFF)
        assertEquals(255, iosBytes[1].toInt() and 0xFF)
        assertEquals(255, iosBytes[2].toInt() and 0xFF)
        assertEquals(255, iosBytes[3].toInt() and 0xFF)

        val iosCenterIdx = (64 * 128 + 64) * 4
        assertTrue((iosBytes[iosCenterIdx].toInt() and 0xFF) >= 220)
        assertTrue((iosBytes[iosCenterIdx + 1].toInt() and 0xFF) <= 75)
    }

    @Test
    fun windowAspectRatioHelperEnforcesAxisDominantResize() {
        val ratio = 16.0 / 9.0
        val widthDragged = WindowAspectRatioHelper.enforceResizeDelta(
            previousSize = Size(1280, 720),
            requestedSize = Size(1600, 720),
            aspectRatio = ratio,
            minSize = Size(320, 180)
        )
        assertEquals(Size(1600, 900), widthDragged)

        val heightDragged = WindowAspectRatioHelper.enforceResizeDelta(
            previousSize = Size(1280, 720),
            requestedSize = Size(1280, 540),
            aspectRatio = ratio,
            minSize = Size(320, 180)
        )
        assertEquals(Size(960, 540), heightDragged)
    }
}
