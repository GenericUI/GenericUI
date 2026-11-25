package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.component.shell.Window
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FragmentRegistryAndContainerLifecycleTest {

    private class AlphaDetailFragment(val label: String = "default") : Fragment() {
        var pausedCount: Int = 0
        var stoppedCount: Int = 0
        var destroyedCount: Int = 0

        override fun onPause() {
            super.onPause()
            pausedCount++
        }

        override fun onStop() {
            super.onStop()
            stoppedCount++
        }

        override fun onDestroy() {
            super.onDestroy()
            destroyedCount++
        }
    }

    private class SecondarySettingsFragment : Fragment()

    private class FirstSharedNamedHolder {
        class DuplicateNameFragment : Fragment()
    }

    private class SecondSharedNamedHolder {
        class DuplicateNameFragment : Fragment()
    }

    @BeforeTest
    fun setup() {
        FragmentRegistry.clear()
    }

    @AfterTest
    fun teardown() {
        FragmentRegistry.clear()
    }

    @Test
    fun `FragmentRegistry resolves IDs routes unambiguous simpleName suffixes and instantiates with args`() {
        FragmentRegistry.register(
            fragmentClass = AlphaDetailFragment::class,
            fragmentId = "com.example.AlphaDetailFragment",
            route = "/alpha/detail"
        ) { bundle ->
            AlphaDetailFragment(bundle?.getString("label") ?: "none")
        }
        FragmentRegistry.registerMetadata(
            fragmentClass = SecondarySettingsFragment::class,
            fragmentId = "com.example.SecondarySettingsFragment",
            route = "/settings"
        )

        assertEquals("com.example.AlphaDetailFragment", FragmentRegistry.resolveFragmentId(AlphaDetailFragment::class))
        assertEquals("/alpha/detail", FragmentRegistry.resolveRoute(AlphaDetailFragment::class))

        assertEquals(AlphaDetailFragment::class, FragmentRegistry.resolveFragmentClass("com.example.AlphaDetailFragment"))
        assertEquals(AlphaDetailFragment::class, FragmentRegistry.resolveFragmentClass("/alpha/detail"))
        assertEquals(AlphaDetailFragment::class, FragmentRegistry.resolveFragmentClass("alpha/detail"))
        assertEquals(AlphaDetailFragment::class, FragmentRegistry.resolveFragmentClass("AlphaDetailFragment"))
        assertEquals(SecondarySettingsFragment::class, FragmentRegistry.resolveFragmentClass("settings"))
        assertEquals(SecondarySettingsFragment::class, FragmentRegistry.resolveFragmentClass("/settings"))

        val args = FragmentBundle.of("label" to "Episode 5")
        val instantiated = FragmentRegistry.instantiateByIdOrRoute("/alpha/detail", args) as? AlphaDetailFragment
        assertNotNull(instantiated)
        assertEquals("Episode 5", instantiated.label)
        assertEquals("Episode 5", instantiated.arguments?.getString("label"))

        // Metadata-only fragment without factory returns null on instantiateByIdOrRoute
        assertNull(FragmentRegistry.instantiateByIdOrRoute("com.example.SecondarySettingsFragment"))

        // Ambiguous simpleName suffix must resolve to null
        FragmentRegistry.registerMetadata(
            FirstSharedNamedHolder.DuplicateNameFragment::class,
            "com.a.DuplicateNameFragment",
            "/dup-a"
        )
        FragmentRegistry.registerMetadata(
            SecondSharedNamedHolder.DuplicateNameFragment::class,
            "com.b.DuplicateNameFragment",
            "/dup-b"
        )
        assertNull(FragmentRegistry.resolveFragmentClass("DuplicateNameFragment"))
        assertEquals(
            FirstSharedNamedHolder.DuplicateNameFragment::class,
            FragmentRegistry.resolveFragmentClass("com.a.DuplicateNameFragment")
        )
        assertEquals(
            SecondSharedNamedHolder.DuplicateNameFragment::class,
            FragmentRegistry.resolveFragmentClass("/dup-b")
        )
    }

    @Test
    fun `clearCurrentFragment tears down currentFragment and stacks without disposing host Window`() {
        val window = Window(title = "Host Window")
        val container = FragmentContainer(hostWindow = window)
        val first = AlphaDetailFragment("first")
        val second = AlphaDetailFragment("second")

        container.setFragment(first)
        first.performStart()
        first.performResume()
        container.pushFragment(second)
        second.performStart()
        second.performResume()

        assertEquals(1, container.backStackDepth)
        assertEquals(second, container.currentFragment)
        assertTrue(container.children.contains(second))

        container.clearCurrentFragment()

        assertNull(container.currentFragment)
        assertEquals(0, container.backStackDepth)
        assertEquals(0, container.forwardStackDepth)
        assertFalse(container.children.contains(second))
        assertNull(second.fragmentHost)
        assertNull(second.attachedWindow)
        assertEquals(1, second.pausedCount)
        assertEquals(1, second.stoppedCount)
        assertEquals(1, second.destroyedCount)
        assertEquals(1, first.destroyedCount)
        assertFalse(window.isDisposed)
    }
}
