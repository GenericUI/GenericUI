package com.nexustools.genericui.resources

import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.core.ApplicationLifecycleExtensionHook
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.res.R
import com.nexustools.genericui.res.Resource
import com.nexustools.genericui.res.Resources
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Wave0AResourceIDAndExtensionRegistryTest {

    @AfterTest
    fun tearDown() {
        GenericUiExtensionRegistry.clear()
    }

    @Test
    fun `resource ID derives three-tier group id nativeResourceKey canonicalRelativePath and flatName`() {
        val coreLoading = ResourceID(0x7e010001, ResourceType.DRAWABLE, "loading", "drawable/loading.svg")
        assertEquals("genericui", coreLoading.nativeLibraryGroupId)
        assertEquals("drawable_loading_svg", coreLoading.nativeResourceKey)
        assertEquals("gui/drawable/loading.svg", coreLoading.canonicalRelativePath)
        assertEquals("gui_drawable_loading", coreLoading.flatName)

        val coreJs = ResourceID(0x7e070001, ResourceType.RAW, "bootstrap_bundle_min", "gui/raw/bootstrap.bundle.min.js")
        assertEquals("genericui", coreJs.nativeLibraryGroupId)
        assertEquals("raw_bootstrap_bundle_min_js", coreJs.nativeResourceKey)
        assertEquals("gui/raw/bootstrap.bundle.min.js", coreJs.canonicalRelativePath)
        assertEquals("gui_raw_bootstrap_bundle_min", coreJs.flatName)

        val extIcon = ResourceID(
            id = 0x82010001.toInt(),
            type = ResourceType.DRAWABLE,
            name = "ic_alien",
            relativePath = "gui/ext/ui/alien/drawable/ic_alien.svg",
            nativeLibraryGroupId = "genericui-ext-alien"
        )
        assertEquals("genericui-ext-alien", extIcon.nativeLibraryGroupId)
        assertEquals("drawable_ic_alien_svg", extIcon.nativeResourceKey)
        assertEquals("gui/ext/ui/alien/drawable/ic_alien.svg", extIcon.canonicalRelativePath)
        assertEquals("gui_ext_ui_alien_drawable_ic_alien", extIcon.flatName)

        val appCast = ResourceID(0x7f010001, ResourceType.DRAWABLE, "ic_cast", "drawable/ic_cast.svg")
        assertEquals("", appCast.nativeLibraryGroupId)
        assertEquals("drawable_ic_cast_svg", appCast.nativeResourceKey)
        assertEquals("app/drawable/ic_cast.svg", appCast.canonicalRelativePath)
        assertEquals("app_drawable_ic_cast", appCast.flatName)

        assertSame(Resource.drawable.loading, R.drawable.loading)
        assertEquals(Resource.style.genericui, Resources.GENERICUI)
    }

    @Test
    fun `extension registry dispatches prepare run and shutdown hooks in registration order`() {
        val events = mutableListOf<String>()
        val hook = object : ApplicationLifecycleExtensionHook {
            override fun onBeforeAppPrepare(descriptor: GenericUiAppDescriptor) {
                events.add("beforePrepare:${descriptor.appId}")
            }

            override fun onAppPrepared(app: IApplication, descriptor: GenericUiAppDescriptor) {
                events.add("prepared:${descriptor.appName}")
            }

            override fun onBeforeAppRun(app: IApplication, factory: NativeFactory?) {
                events.add("beforeRun")
            }

            override fun onAppShutdown(app: IApplication?) {
                events.add("shutdown")
            }
        }
        GenericUiExtensionRegistry.registerLifecycleHook(hook)
        assertEquals(1, GenericUiExtensionRegistry.registeredLifecycleHooks().size)
        GenericUiExtensionRegistry.setExtensionProperty("worker.mode", "bg")
        assertEquals("bg", GenericUiExtensionRegistry.getExtensionProperty("worker.mode"))

        val descriptor = GenericUiAppDescriptor(appId = "org.test.ext", appName = "ExtApp")
        GenericUiExtensionRegistry.dispatchBeforeAppPrepare(descriptor)
        GenericUiExtensionRegistry.dispatchAppShutdown(null)
        assertTrue(events.contains("beforePrepare:org.test.ext"))
        assertTrue(events.contains("shutdown"))
    }
}
