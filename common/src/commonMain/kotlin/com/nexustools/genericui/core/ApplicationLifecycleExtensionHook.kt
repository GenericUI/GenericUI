package com.nexustools.genericui.core

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.IApplication

/**
 * Extension lifecycle callback contract invoked by [GenericUiExtensionRegistry] during
 * application preparation, launch, and shutdown across all GenericUI targets without coupling
 * `:common` to extension modules.
 */
interface ApplicationLifecycleExtensionHook {
    fun onBeforeAppPrepare(descriptor: GenericUiAppDescriptor) {}
    fun onAppPrepared(app: IApplication, descriptor: GenericUiAppDescriptor) {}
    fun onBeforeAppRun(app: IApplication, factory: NativeFactory?) {}
    fun onAppEventLoopStarted(app: Application, factory: NativeFactory) {}
    fun hasOpenFragments(app: Application): Boolean = false
    fun onAppShutdown(app: IApplication?) {}
}
