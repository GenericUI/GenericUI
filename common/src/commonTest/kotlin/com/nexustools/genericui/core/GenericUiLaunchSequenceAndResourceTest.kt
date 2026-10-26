package com.nexustools.genericui.core

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.typography.Icon
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolver
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenericUiLaunchSequenceAndResourceTest {

    private class CliAwareMainFragment : Fragment() {
        var receivedBundle: FragmentBundle? = null
        override fun onCreate(savedState: FragmentBundle?) {
            super.onCreate(savedState)
            receivedBundle = savedState
        }
    }

    private class SecondaryDetailFragment : Fragment()

    @Test
    fun `GenericUiLaunchSequence injects command line args on system targets and omits on web`() {
        var resourceInstalled = false
        var providerInstalled = false

        val descriptor = GenericUiAppDescriptor(
            appId = "com.example.test",
            appName = "TestApp",
            windowTitle = "Test Window",
            windowWidth = 1440,
            windowHeight = 900,
            themeMode = "light",
            themeColor = "#ff5500",
            mainFragmentClass = CliAwareMainFragment::class,
            mainFragmentFactory = { _ -> CliAwareMainFragment() },
            fragmentRegistrations = listOf(
                CliAwareMainFragment::class to { _ -> CliAwareMainFragment() },
                SecondaryDetailFragment::class to { _ -> SecondaryDetailFragment() }
            ),
            providerInstallers = listOf({ providerInstalled = true }),
            resourceRegistryInstaller = { resourceInstalled = true }
        )

        val systemApp = GenericUiLaunchSequence.prepareApplication(
            factory = null,
            args = arrayOf("--workspace", "D:/Projects", "--debug"),
            descriptor = descriptor,
            isSystemTarget = true
        )
        assertTrue(resourceInstalled)
        assertTrue(providerInstalled)
        assertTrue(FragmentRegistry.isRegistered(SecondaryDetailFragment::class))
        assertEquals(ThemeMode.LIGHT, systemApp.getThemeMode())
        assertEquals("#ff5500", systemApp.getThemeColor())
        assertEquals("Test Window", systemApp.mainWindow.getTitle())
        assertEquals(1440, systemApp.mainWindow.getWindowSize().width)
        assertEquals(900, systemApp.mainWindow.getWindowSize().height)

        val activeMain = systemApp.mainWindow.activeFragment as? CliAwareMainFragment
        assertNotNull(activeMain)
        val sysBundle = activeMain.receivedBundle ?: activeMain.arguments
        assertNotNull(sysBundle)
        assertTrue(sysBundle.hasCommandLineArgs())
        assertEquals(listOf("--workspace", "D:/Projects", "--debug"), sysBundle.getCommandLineArgs())

        val webApp = GenericUiLaunchSequence.prepareApplication(
            factory = null,
            args = arrayOf("--ignored"),
            descriptor = descriptor,
            isSystemTarget = false
        )
        val webMain = webApp.mainWindow.activeFragment as? CliAwareMainFragment
        assertNotNull(webMain)
        assertFalse(webMain.receivedBundle?.hasCommandLineArgs() == true)
        assertFalse(webMain.arguments?.hasCommandLineArgs() == true)
    }

    @Test
    fun `ResourceID and ResourceResolver resolve strings colors dimens and component overloads`() {
        val titleId = ResourceID(0x7f020001, ResourceType.STRING, "welcome_msg")
        val accentId = ResourceID(0x7f030001, ResourceType.COLOR, "accent_color")
        val spacingId = ResourceID(0x7f040001, ResourceType.DIMEN, "header_height")
        val logoId = ResourceID(0x7f010001, ResourceType.DRAWABLE, "app_logo", "drawable/app_logo.png")

        Resources.activeTable.registerString(titleId, "Hello %1\$s (%2\$d)")
        Resources.activeTable.registerColor(accentId, RgbaColor.fromHex("#123456"))
        Resources.activeTable.registerDimen(spacingId, 64f)

        assertEquals("Hello World (42)", ResourceResolver.getString(titleId, "World", 42))
        assertEquals(RgbaColor.fromHex("#123456"), ResourceResolver.getColor(accentId))
        assertEquals(64f, ResourceResolver.getDimenPx(spacingId))
        assertTrue(ResourceResolver.getUrl(logoId).contains("app_logo"))

        val label = Label(titleId)
        assertEquals("Hello %1\$s (%2\$d)", label.text)
        label.setText(titleId, "GenericUI", 7)
        assertEquals("Hello GenericUI (7)", label.text)

        val btn = Button(titleId, logoId)
        assertTrue(btn.text.isNotEmpty())

        val img = Image(logoId)
        assertEquals(logoId, img.resourceId)

        val icon = Icon(logoId, 24)
        assertEquals(logoId, icon.iconRes)

        val win = Window(title = "Icon Window")
        win.setWindowIcon(logoId)
        assertEquals(logoId.toResourceUri(), win.getWindowIcon())
    }
}
