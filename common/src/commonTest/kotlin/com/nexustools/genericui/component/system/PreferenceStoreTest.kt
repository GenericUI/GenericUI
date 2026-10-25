package com.nexustools.genericui.component.system

import com.nexustools.genericui.core.GenericUI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreferenceStoreTest {

    private data class EditorUserSettings(
        val theme: String,
        val fontSize: Int,
        val autoSaveIntervalMs: Long,
        val uiScale: Float,
        val hardwareAcceleration: Boolean,
        val pinnedPanels: List<String>
    ) : PreferenceSerializable {
        override fun toPreferenceMap(): Map<String, Any?> = linkedMapOf(
            "theme" to theme,
            "fontSize" to fontSize,
            "autoSaveIntervalMs" to autoSaveIntervalMs,
            "uiScale" to uiScale,
            "hardwareAcceleration" to hardwareAcceleration,
            "pinnedPanels" to pinnedPanels
        )
    }

    @Test
    fun `PreferenceStore saves and loads objects primitives and notifies listeners`() {
        GenericUI.applicationName = "TestStudioApp"
        val store = PreferenceStore.open()
        assertEquals("TestStudioApp", store.appName)
        assertTrue(store.usesNativeChangeListener)

        val events = mutableListOf<Triple<String, String?, Any?>>()
        val listener = PreferenceChangeListener { storeName, key, newValue ->
            events.add(Triple(storeName, key, newValue))
        }
        store.addChangeListener(listener)

        val settings = EditorUserSettings(
            theme = "dark-tokyo",
            fontSize = 15,
            autoSaveIntervalMs = 30_000L,
            uiScale = 1.25f,
            hardwareAcceleration = true,
            pinnedPanels = listOf("layers", "timeline", "inspector")
        )
        store.save("editor_settings", settings)
        assertTrue(store.contains("editor_settings"))

        val loaded = store.load(
            "editor_settings",
            EditorUserSettings("light", 12, 5000L, 1.0f, false, emptyList())
        )
        assertEquals(settings, loaded)

        store.edit {
            putString("username", "taylor")
            putInt("launch_count", 17)
            putLong("last_opened", 1700000000000L)
            putFloat("master_volume", 0.85f)
            putDouble("zoom_ratio", 1.75)
            putBoolean("first_run_completed", true)
            putStringSet("recent_workspaces", linkedSetOf("/work/a", "/work/b"))
        }

        assertEquals("taylor", store.getString("username"))
        assertEquals(17, store.getInt("launch_count"))
        assertEquals(1700000000000L, store.getLong("last_opened"))
        assertEquals(0.85f, store.getFloat("master_volume"))
        assertEquals(1.75, store.getDouble("zoom_ratio"))
        assertTrue(store.getBoolean("first_run_completed"))
        assertEquals(setOf("/work/a", "/work/b"), store.getStringSet("recent_workspaces"))

        store.remove("username")
        assertFalse(store.contains("username"))
        assertNull(store.getString("username"))

        store.clear()
        assertTrue(store.keys().isEmpty())
        assertTrue(events.isNotEmpty())
        assertEquals(null, events.last().second)

        store.removeChangeListener(listener)
        store.dispose()
        assertTrue(store.isDisposed)
    }
}
