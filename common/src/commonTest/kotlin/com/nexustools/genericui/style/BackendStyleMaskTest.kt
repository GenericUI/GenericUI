package com.nexustools.genericui.style

import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.style.css.BackendStyleMask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackendStyleMaskTest {

    @Test
    fun testConcreteBitsAndAllMatchBackendTargetEntries() {
        assertEquals(0, BackendStyleMask.NONE)
        assertEquals(0x1FF, BackendStyleMask.ALL)
        assertEquals(1 shl 0, BackendStyleMask.WINDOWS)
        assertEquals(1 shl 1, BackendStyleMask.MACOS)
        assertEquals(1 shl 2, BackendStyleMask.GTK)
        assertEquals(1 shl 3, BackendStyleMask.KDE)
        assertEquals(1 shl 4, BackendStyleMask.SWING)
        assertEquals(1 shl 5, BackendStyleMask.ANDROID)
        assertEquals(1 shl 6, BackendStyleMask.IOS)
        assertEquals(1 shl 7, BackendStyleMask.IPADOS)
        assertEquals(1 shl 8, BackendStyleMask.WEB)

        var union = 0
        val seenBits = mutableSetOf<Int>()
        for (target in BackendTarget.entries) {
            val bit = BackendStyleMask.bitFor(target)
            assertEquals(1, bit.countOneBits(), "Expected single bit for $target")
            assertTrue(seenBits.add(bit), "Duplicate bit for $target")
            union = union or bit
        }
        assertEquals(BackendStyleMask.ALL, union)
        assertEquals(9, BackendTarget.entries.size)
    }

    @Test
    fun testAllThirteenGroupsMatchBackendTargetPropertiesOneToOne() {
        fun maskFromPredicate(predicate: (BackendTarget) -> Boolean): Int =
            BackendTarget.entries.filter(predicate).fold(0) { acc, t -> acc or BackendStyleMask.bitFor(t) }

        assertEquals(maskFromPredicate { it.isSystem }, BackendStyleMask.SYSTEM)
        assertEquals(maskFromPredicate { it.isDesktop }, BackendStyleMask.DESKTOP)
        assertEquals(maskFromPredicate { it.isNativeDesktop }, BackendStyleMask.NATIVE_DESKTOP)
        assertEquals(maskFromPredicate { it.isLinux }, BackendStyleMask.LINUX)
        assertEquals(maskFromPredicate { it.isMobile }, BackendStyleMask.MOBILE)
        assertEquals(maskFromPredicate { it.isApple }, BackendStyleMask.APPLE)
        assertEquals(maskFromPredicate { it.isIosFamily }, BackendStyleMask.IOS_FAMILY)
        assertEquals(maskFromPredicate { it.isUnix }, BackendStyleMask.UNIX)
        assertEquals(maskFromPredicate { it.isPosix }, BackendStyleMask.POSIX)
        assertEquals(maskFromPredicate { it.isNative }, BackendStyleMask.NATIVE)
        assertEquals(maskFromPredicate { it.isJvm }, BackendStyleMask.JVM)
        assertEquals(maskFromPredicate { it.isTouchPrimary }, BackendStyleMask.TOUCH_PRIMARY)
        assertEquals(maskFromPredicate { it.isBrowser }, BackendStyleMask.BROWSER)
    }

    @Test
    fun testMatchesBehavior() {
        assertFalse(BackendStyleMask.matches(BackendStyleMask.NONE, null))
        for (target in BackendTarget.entries) {
            assertFalse(BackendStyleMask.matches(BackendStyleMask.NONE, target))
            assertTrue(BackendStyleMask.matches(BackendStyleMask.ALL, target))
            assertTrue(BackendStyleMask.matches(BackendStyleMask.bitFor(target), target))
            assertTrue(BackendStyleMask.matches(BackendStyleMask.bitFor(target), null))
        }
        assertTrue(BackendStyleMask.matches(BackendStyleMask.DESKTOP, BackendTarget.MacOS))
        assertFalse(BackendStyleMask.matches(BackendStyleMask.DESKTOP, BackendTarget.Web))
        assertFalse(BackendStyleMask.matches(BackendStyleMask.SYSTEM, BackendTarget.Web))
        assertTrue(BackendStyleMask.matches(BackendStyleMask.SYSTEM, BackendTarget.Android))
    }

    @Test
    fun testParseTokenRecognizesAllConcreteAndGroupAliases() {
        val expected = mapOf(
            "windows" to BackendStyleMask.WINDOWS,
            "  MacOS  " to BackendStyleMask.MACOS,
            "GTK" to BackendStyleMask.GTK,
            "kde" to BackendStyleMask.KDE,
            "swing" to BackendStyleMask.SWING,
            "android" to BackendStyleMask.ANDROID,
            "ios" to BackendStyleMask.IOS,
            "ipados" to BackendStyleMask.IPADOS,
            "web" to BackendStyleMask.WEB,
            "system" to BackendStyleMask.SYSTEM,
            "desktop" to BackendStyleMask.DESKTOP,
            "native-desktop" to BackendStyleMask.NATIVE_DESKTOP,
            "nativedesktop" to BackendStyleMask.NATIVE_DESKTOP,
            "linux" to BackendStyleMask.LINUX,
            "mobile" to BackendStyleMask.MOBILE,
            "touch" to BackendStyleMask.TOUCH_PRIMARY,
            "touch-primary" to BackendStyleMask.TOUCH_PRIMARY,
            "apple" to BackendStyleMask.APPLE,
            "ios-family" to BackendStyleMask.IOS_FAMILY,
            "uikit" to BackendStyleMask.IOS_FAMILY,
            "unix" to BackendStyleMask.UNIX,
            "posix" to BackendStyleMask.POSIX,
            "native" to BackendStyleMask.NATIVE,
            "jvm" to BackendStyleMask.JVM,
            "browser" to BackendStyleMask.BROWSER,
            "all" to BackendStyleMask.ALL
        )
        for ((token, mask) in expected) {
            assertEquals(mask, BackendStyleMask.parseToken(token), "Failed for token '$token'")
        }
        assertNull(BackendStyleMask.parseToken(""))
        assertNull(BackendStyleMask.parseToken("media"))
        assertNull(BackendStyleMask.parseToken("unknown-os"))
    }

    @Test
    fun testParseExpressionPrecedenceAndMalformedInputs() {
        assertEquals(BackendStyleMask.MACOS, BackendStyleMask.parseExpression("macos"))
        assertEquals(BackendStyleMask.MACOS or BackendStyleMask.WINDOWS, BackendStyleMask.parseExpression("macos, windows"))
        assertEquals(BackendStyleMask.MACOS or BackendStyleMask.LINUX, BackendStyleMask.parseExpression("macos or linux"))
        assertEquals(
            BackendStyleMask.DESKTOP and BackendStyleMask.MACOS.inv(),
            BackendStyleMask.parseExpression("desktop and not macos")
        )
        assertEquals(BackendStyleMask.SYSTEM, BackendStyleMask.parseExpression("not web"))
        assertEquals(BackendStyleMask.WEB, BackendStyleMask.parseExpression("not not web"))
        assertEquals(
            BackendStyleMask.WINDOWS or BackendStyleMask.SWING,
            BackendStyleMask.parseExpression("windows or (desktop and jvm)")
        )
        // Precedence: not > and > or/,
        assertEquals(
            BackendStyleMask.WINDOWS or (BackendStyleMask.DESKTOP and (BackendStyleMask.ALL and BackendStyleMask.MACOS.inv())),
            BackendStyleMask.parseExpression("windows or desktop and not macos")
        )
        assertEquals(
            (BackendStyleMask.MACOS or BackendStyleMask.WINDOWS) and BackendStyleMask.SWING.inv(),
            BackendStyleMask.parseExpression("(macos, windows) and not swing")
        )
        assertEquals(BackendStyleMask.NONE, BackendStyleMask.parseExpression("not all"))

        // Malformed inputs must return null
        val invalid = listOf(
            "",
            "   ",
            "()",
            "(macos",
            "macos)",
            "macos,",
            ",macos",
            "macos windows",
            "and macos",
            "macos and",
            "not",
            "macos and or windows",
            "unknown",
            "macos and unknown",
            "macos | windows",
            "macos & windows"
        )
        for (expr in invalid) {
            assertNull(BackendStyleMask.parseExpression(expr), "Expected null for '$expr'")
        }
    }

    @Test
    fun testDirectiveHeaderParsingValidationAndFormatting() {
        assertEquals(BackendStyleMask.MACOS, BackendStyleMask.parseDirectiveHeader("@macos"))
        assertEquals(BackendStyleMask.MACOS, BackendStyleMask.parseDirectiveHeader("  @MacOS  "))
        assertEquals(BackendStyleMask.SYSTEM, BackendStyleMask.parseDirectiveHeader("@system"))
        assertEquals(
            BackendStyleMask.WINDOWS or BackendStyleMask.LINUX,
            BackendStyleMask.parseDirectiveHeader("@backend(windows, linux)")
        )
        assertEquals(
            BackendStyleMask.DESKTOP and BackendStyleMask.MACOS.inv(),
            BackendStyleMask.parseDirectiveHeader("@platform(desktop and not macos)")
        )
        assertEquals(
            BackendStyleMask.APPLE,
            BackendStyleMask.parseDirectiveHeader("@target (apple)")
        )

        // Standard CSS at-rules and trailing tokens must return null
        assertNull(BackendStyleMask.parseDirectiveHeader("@media (min-width: 600px)"))
        assertNull(BackendStyleMask.parseDirectiveHeader("@keyframes fade"))
        assertNull(BackendStyleMask.parseDirectiveHeader("@supports (display: grid)"))
        assertNull(BackendStyleMask.parseDirectiveHeader("@font-face"))
        assertNull(BackendStyleMask.parseDirectiveHeader("@layer utilities"))
        assertNull(BackendStyleMask.parseDirectiveHeader("@container (min-width: 400px)"))
        assertNull(BackendStyleMask.parseDirectiveHeader("@property --x"))
        assertNull(BackendStyleMask.parseDirectiveHeader("@macos screen"))
        assertNull(BackendStyleMask.parseDirectiveHeader("@backend(macos) extra"))

        assertTrue(BackendStyleMask.isBackendDirectivePrefix("@backend"))
        assertTrue(BackendStyleMask.isBackendDirectivePrefix("@platform(bad"))
        assertTrue(BackendStyleMask.isBackendDirectivePrefix("@target(unknown)"))
        assertTrue(BackendStyleMask.isBackendDirectivePrefix("@macos"))
        assertFalse(BackendStyleMask.isBackendDirectivePrefix("@media (min-width: 600px)"))

        assertNull(BackendStyleMask.validateDirectiveHeader("@backend(macos, windows)"))
        assertNull(BackendStyleMask.validateDirectiveHeader("@macos"))
        assertNotNull(BackendStyleMask.validateDirectiveHeader("@backend"))
        assertNotNull(BackendStyleMask.validateDirectiveHeader("@backend()"))
        assertNotNull(BackendStyleMask.validateDirectiveHeader("@backend(macos"))
        assertNotNull(BackendStyleMask.validateDirectiveHeader("@platform(macos and )"))
        assertNotNull(BackendStyleMask.validateDirectiveHeader("@target(unknown)"))
        assertNotNull(BackendStyleMask.validateDirectiveHeader("@backend(macos) trailing"))

        assertEquals("@macos", BackendStyleMask.formatCanonicalDirective(BackendStyleMask.MACOS))
        assertEquals("@system", BackendStyleMask.formatCanonicalDirective(BackendStyleMask.SYSTEM))
        assertEquals("@desktop", BackendStyleMask.formatCanonicalDirective(BackendStyleMask.DESKTOP))
        assertEquals(
            "@backend(windows, macos)",
            BackendStyleMask.formatCanonicalDirective(BackendStyleMask.WINDOWS or BackendStyleMask.MACOS)
        )

        assertEquals("BackendStyleMask.ALL", BackendStyleMask.formatKotlinExpr(BackendStyleMask.ALL))
        assertEquals("BackendStyleMask.MACOS", BackendStyleMask.formatKotlinExpr(BackendStyleMask.MACOS))
        assertEquals(
            "0x3",
            BackendStyleMask.formatKotlinExpr(BackendStyleMask.WINDOWS or BackendStyleMask.MACOS)
        )
    }

    @Test
    fun testContainsGenericUiBackendDirectiveAndDescriptions() {
        assertTrue(BackendStyleMask.containsGenericUiBackendDirective("@macos { .a { color: red; } }"))
        assertTrue(BackendStyleMask.containsGenericUiBackendDirective(".a { @desktop { padding: 8px; } }"))
        assertTrue(BackendStyleMask.containsGenericUiBackendDirective("@backend(windows, linux) { .a {} }"))
        assertTrue(BackendStyleMask.containsGenericUiBackendDirective("@platform(not web) { .a {} }"))
        assertTrue(BackendStyleMask.containsGenericUiBackendDirective("@target(apple) { .a {} }"))

        assertFalse(BackendStyleMask.containsGenericUiBackendDirective("@media (min-width: 600px) { .a { color: red; } }"))
        assertFalse(BackendStyleMask.containsGenericUiBackendDirective("@keyframes fade { from { opacity: 0; } to { opacity: 1; } }"))
        assertFalse(BackendStyleMask.containsGenericUiBackendDirective("/* @macos { .a {} } */ .b { color: blue; }"))
        assertFalse(BackendStyleMask.containsGenericUiBackendDirective("// @macos { .a {} }\n.b { color: blue; }"))
        assertFalse(BackendStyleMask.containsGenericUiBackendDirective(".icon { background-image: url(\"icon@2x.png\"); content: \"@macos { }\"; }"))
        assertFalse(BackendStyleMask.containsGenericUiBackendDirective(".btn { color: @color/primary; }"))

        val keys = BackendStyleMask.ALL_DIRECTIVE_DESCRIPTIONS.map { it.first }.toSet()
        for (target in BackendTarget.entries) {
            assertTrue("@${target.id}" in keys, "Missing description for @${target.id}")
        }
        assertTrue("@system" in keys)
        assertTrue("@desktop" in keys)
        assertTrue("@backend(...)" in keys)
    }
}
