package com.nexustools.genericui.window

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.system.PreferenceStore
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainWindowStatePersistenceTest {

    private var savedFactory = GenericUI.activeFactory

    @AfterTest
    fun cleanup() {
        GenericUI.activeFactory = savedFactory
    }

    @Test
    fun savedStateRoundTripAndMonitorVisibilityOverlap() {
        val state = MainWindowSavedState(
            x = 120,
            y = 85,
            width = 1280,
            height = 720,
            fullscreen = false,
            windowState = WindowState.MAXIMIZED
        )
        val decoded = MainWindowSavedState.fromPreferenceElement(state.toPreferenceElement())
        assertNotNull(decoded)
        assertEquals(state, decoded)
        assertTrue(decoded.hasValidSize)
        assertTrue(decoded.hasPosition)
        assertEquals(Rect(120, 85, 1280, 720), decoded.toBounds())

        // Legacy fullscreen-only migration payload keeps fullscreen while leaving size unconfigured
        val legacyFullscreenOnly = MainWindowSavedState(width = 0, height = 0, fullscreen = false)
        val decodedLegacy = MainWindowSavedState.fromPreferenceElement(legacyFullscreenOnly.toPreferenceElement())
        assertNotNull(decodedLegacy)
        assertFalse(decodedLegacy.hasValidSize)
        assertFalse(decodedLegacy.fullscreen)
        assertNull(decodedLegacy.toBounds())

        val monitor = Rect(0, 0, 1920, 1080)
        assertTrue(MainWindowSavedState.intersectsVisibleArea(Rect(100, 100, 800, 600), monitor))
        assertTrue(MainWindowSavedState.intersectsVisibleArea(Rect(-700, 100, 800, 600), monitor)) // 100px visible
        assertFalse(MainWindowSavedState.intersectsVisibleArea(Rect(-790, 100, 800, 600), monitor)) // only 10px visible (<48)
        assertFalse(MainWindowSavedState.intersectsVisibleArea(Rect(2000, 100, 800, 600), monitor))
    }

    @Test
    fun consumerDefaultFullscreenAndGeometryPersistAcrossHideShowAndRestarts() {
        val prevFactory = GenericUI.activeFactory
        GenericUI.activeFactory = null
        try {
            val prefNamespace = "common_main_win_persist_${Random.nextInt()}"
            val store = PreferenceStore(prefNamespace).apply {
                clear()
                flush()
            }

            class ReceiverLikeApp(private val customStore: PreferenceStore) : Application() {
                init {
                    defaultMainWindowFullscreen = true
                    mainWindow.setMinWindowSize(Size(640, 360))
                    mainWindow.setAspectRatio(16, 9)
                    mainWindow.setContent(Container())
                    mainWindow.hide()
                }

                override fun getPreferenceStore(): PreferenceStore = customStore
            }

            // 1. Starts hidden -> does NOT enter fullscreen prematurely
            val app1 = ReceiverLikeApp(store)
            assertFalse(app1.mainWindow.isVisible)
            assertFalse(app1.mainWindow.isFullscreen())

            // 2. First show enters consumer default fullscreen
            app1.mainWindow.show()
            assertTrue(app1.mainWindow.isVisible)
            assertTrue(app1.mainWindow.isFullscreen())

            // 3. Re-hiding and re-showing stays fullscreen
            app1.mainWindow.hide()
            assertFalse(app1.mainWindow.isVisible)
            assertTrue(app1.mainWindow.isFullscreen())
            app1.mainWindow.show()
            assertTrue(app1.mainWindow.isFullscreen())

            // 4. User exits fullscreen, moves and resizes window -> persisted immediately & flushed on hide
            app1.mainWindow.setFullscreen(false)
            assertFalse(app1.mainWindow.isFullscreen())
            app1.mainWindow.setWindowPosition(220, 140)
            app1.mainWindow.setWindowSize(Size(960, 540))
            app1.mainWindow.hide()

            val persisted = MainWindowSavedState.fromPreferenceElement(
                store.getElement(MainWindowSavedState.PREF_KEY)
            )
            assertNotNull(persisted)
            assertFalse(persisted.fullscreen)
            assertEquals(220, persisted.x)
            assertEquals(140, persisted.y)
            assertEquals(960, persisted.width)
            assertEquals(540, persisted.height)

            // 5. Re-showing preserves windowed geometry & non-fullscreen state
            app1.mainWindow.show()
            assertFalse(app1.mainWindow.isFullscreen())
            assertEquals(Rect(220, 140, 960, 540), app1.mainWindow.windowBounds)
            app1.dispose()

            // 6. Fresh Application restart reads saved state and overrides defaultMainWindowFullscreen=true
            val app2 = ReceiverLikeApp(store)
            assertFalse(app2.mainWindow.isFullscreen())
            app2.mainWindow.show()
            assertFalse(app2.mainWindow.isFullscreen())
            assertEquals(Rect(220, 140, 960, 540), app2.mainWindow.windowBounds)
            app2.dispose()
        } finally {
            GenericUI.activeFactory = prevFactory
        }
    }

    @Test
    fun consumerDefaultGeometryAppliesWhenEmptyAndOptOutFlagDisablesPersistence() {
        val prevFactory = GenericUI.activeFactory
        GenericUI.activeFactory = null
        try {
            val prefNamespace = "common_main_win_optout_${Random.nextInt()}"
            val store = PreferenceStore(prefNamespace).apply {
                clear()
                flush()
            }

            val defaultApp = object : Application() {
                init {
                    setDefaultMainWindowGeometry(width = 1024, height = 576, x = 90, y = 75)
                    mainWindow.setContent(Container())
                }

                override fun getPreferenceStore(): PreferenceStore = store
            }
            defaultApp.mainWindow.show()
            assertEquals(Rect(90, 75, 1024, 576), defaultApp.mainWindow.windowBounds)

            // User mutates state -> written to store
            defaultApp.mainWindow.setWindowSize(Size(900, 500))
            defaultApp.mainWindow.setFullscreen(true)
            defaultApp.dispose()
            assertNotNull(store.getElement(MainWindowSavedState.PREF_KEY))

            // App opting out via persistMainWindowState = false ignores existing store & does not overwrite it
            val optOutApp = object : Application() {
                init {
                    persistMainWindowState = false
                    setDefaultMainWindowGeometry(width = 800, height = 600, x = 40, y = 30)
                    mainWindow.setContent(Container())
                }

                override fun getPreferenceStore(): PreferenceStore = store
            }
            optOutApp.mainWindow.show()
            assertFalse(optOutApp.mainWindow.isFullscreen())
            assertEquals(Rect(40, 30, 800, 600), optOutApp.mainWindow.windowBounds)
            optOutApp.mainWindow.setWindowSize(Size(700, 500))
            optOutApp.dispose()

            val untouchedSaved = MainWindowSavedState.fromPreferenceElement(
                store.getElement(MainWindowSavedState.PREF_KEY)
            )
            assertNotNull(untouchedSaved)
            assertTrue(untouchedSaved.fullscreen)
            assertEquals(900, untouchedSaved.width)
            assertEquals(500, untouchedSaved.height)
        } finally {
            GenericUI.activeFactory = prevFactory
        }
    }
}
