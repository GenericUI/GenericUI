package com.nexustools.genericui.component.fragment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FragmentBundleSerializationAndCliTest {

    @Test
    fun `toJson and fromJson are deterministic and roundtrip all supported value types`() {
        val bundleA = FragmentBundle().apply {
            putString("title", "Episode \"1\" \\ \n\t 日本語 \u0007")
            putInt("episodeIndex", -42)
            putLong("positionMs", Long.MAX_VALUE)
            putLong("minLong", Long.MIN_VALUE)
            putFloat("playbackRate", 1.25f)
            putFloat("nanFloat", Float.NaN)
            putFloat("infFloat", Float.POSITIVE_INFINITY)
            putDouble("aspectRatio", 16.0 / 9.0)
            putDouble("negInfDouble", Double.NEGATIVE_INFINITY)
            putBoolean("autoplay", true)
            putBoolean("muted", false)
            putStringList("subtitleTracks", listOf("en", "ja", "es"))
            putStringList("emptyList", emptyList())
            putString("nullableKey", null)
        }

        val bundleB = FragmentBundle().apply {
            putString("nullableKey", null)
            putStringList("emptyList", emptyList())
            putStringList("subtitleTracks", listOf("en", "ja", "es"))
            putBoolean("muted", false)
            putBoolean("autoplay", true)
            putDouble("negInfDouble", Double.NEGATIVE_INFINITY)
            putDouble("aspectRatio", 16.0 / 9.0)
            putFloat("infFloat", Float.POSITIVE_INFINITY)
            putFloat("nanFloat", Float.NaN)
            putFloat("playbackRate", 1.25f)
            putLong("minLong", Long.MIN_VALUE)
            putLong("positionMs", Long.MAX_VALUE)
            putInt("episodeIndex", -42)
            putString("title", "Episode \"1\" \\ \n\t 日本語 \u0007")
        }

        val jsonA = bundleA.toJson()
        val jsonB = bundleB.toJson()
        assertEquals(jsonA, jsonB, "Key sorting should produce byte-identical JSON regardless of insertion order.")

        val decoded = FragmentBundle.fromJson(jsonA)
        assertEquals("Episode \"1\" \\ \n\t 日本語 \u0007", decoded.getString("title"))
        assertEquals(-42, decoded.getInt("episodeIndex"))
        assertEquals(Long.MAX_VALUE, decoded.getLong("positionMs"))
        assertEquals(Long.MIN_VALUE, decoded.getLong("minLong"))
        assertEquals(1.25f, decoded.getFloat("playbackRate"))
        assertTrue(decoded.getFloat("nanFloat").isNaN())
        assertEquals(Float.POSITIVE_INFINITY, decoded.getFloat("infFloat"))
        assertEquals(16.0 / 9.0, decoded.getDouble("aspectRatio"))
        assertEquals(Double.NEGATIVE_INFINITY, decoded.getDouble("negInfDouble"))
        assertTrue(decoded.getBoolean("autoplay"))
        assertFalse(decoded.getBoolean("muted"))
        assertEquals(listOf("en", "ja", "es"), decoded.getStringList("subtitleTracks"))
        assertEquals(emptyList(), decoded.getStringList("emptyList"))
        assertTrue(decoded.containsKey("nullableKey"))
        assertNull(decoded.getString("nullableKey"))

        val b64 = bundleA.toBase64Url()
        assertFalse('=' in b64)
        assertFalse('+' in b64)
        assertFalse('/' in b64)
        val decodedB64 = FragmentBundle.fromBase64Url(b64)
        assertEquals(Long.MAX_VALUE, decodedB64.getLong("positionMs"))
        assertEquals(listOf("en", "ja", "es"), decodedB64.getStringList("subtitleTracks"))
        assertEquals("Episode \"1\" \\ \n\t 日本語 \u0007", decodedB64.getString("title"))
    }

    @Test
    fun `fromCommandLineArgs decodes fragment-args-b64 then overlays explicit CLI flags and preserves open-window`() {
        val encodedArgs = FragmentBundle().apply {
            putString(FragmentBundle.KEY_LAUNCH_FRAGMENT_ID, "com.example.OldFragment")
            putString(FragmentBundle.KEY_LAUNCH_ROUTE, "/old")
            putInt("episodeId", 9001)
            putBoolean(FragmentBundle.KEY_LAUNCH_SINGLE_TOP, false)
            putStringList("filters", listOf("hd", "sub"))
        }.toBase64Url()

        val parsed = FragmentBundle.fromCommandLineArgs(
            arrayOf(
                "--open-window",
                "--fragment-args-b64=$encodedArgs",
                "--fragment=com.example.PlayerFragment",
                "--route=/player",
                "--launch-mode=NEW_WINDOW",
                "--single-top=true"
            )
        )

        assertEquals(9001, parsed.getInt("episodeId"))
        assertEquals(listOf("hd", "sub"), parsed.getStringList("filters"))
        assertEquals("com.example.PlayerFragment", parsed.getString(FragmentBundle.KEY_LAUNCH_FRAGMENT_ID))
        assertEquals("/player", parsed.getString(FragmentBundle.KEY_LAUNCH_ROUTE))
        assertEquals("NEW_WINDOW", parsed.getString(FragmentBundle.KEY_LAUNCH_MODE))
        assertTrue(parsed.getBoolean(FragmentBundle.KEY_LAUNCH_SINGLE_TOP))
        assertTrue(parsed.hasCommandLineArgs())
        assertTrue(parsed.getCommandLineArgs().contains("--open-window"))
    }

    @Test
    fun `fromCommandLineArgs rejects invalid single-top values and malformed payloads fail fast`() {
        assertFailsWith<IllegalArgumentException> {
            FragmentBundle.fromCommandLineArgs(arrayOf("--single-top=maybe"))
        }
        assertFailsWith<IllegalArgumentException> {
            FragmentBundle.fromJson("")
        }
        assertFailsWith<IllegalArgumentException> {
            FragmentBundle.fromBase64Url("   ")
        }
    }
}
