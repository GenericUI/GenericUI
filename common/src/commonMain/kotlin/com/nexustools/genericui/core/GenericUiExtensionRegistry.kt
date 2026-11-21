package com.nexustools.genericui.core

import com.nexustools.genericui.component.shell.IApplication

/**
 * Core extension runtime registry enabling first-party and third-party GenericUI extensions to
 * attach application lifecycle hooks and shared runtime properties in `:common` without any
 * core-to-extension compile dependencies.
 */
object GenericUiExtensionRegistry {
    private val lifecycleHooks = mutableListOf<ApplicationLifecycleExtensionHook>()
    private val extensionProperties = linkedMapOf<String, Any?>()

    fun registerLifecycleHook(hook: ApplicationLifecycleExtensionHook) {
        if (hook !in lifecycleHooks) {
            lifecycleHooks.add(hook)
        }
    }

    fun unregisterLifecycleHook(hook: ApplicationLifecycleExtensionHook) {
        lifecycleHooks.remove(hook)
    }

    fun registeredLifecycleHooks(): List<ApplicationLifecycleExtensionHook> = lifecycleHooks.toList()

    fun setExtensionProperty(key: String, value: Any?) {
        val trimmed = key.trim()
        if (trimmed.isNotEmpty()) {
            extensionProperties[trimmed] = value
        }
    }

    fun getExtensionProperty(key: String): Any? = extensionProperties[key.trim()]

    fun clear() {
        lifecycleHooks.clear()
        extensionProperties.clear()
    }

    fun dispatchBeforeAppPrepare(descriptor: GenericUiAppDescriptor) {
        for (hook in lifecycleHooks.toList()) {
            hook.onBeforeAppPrepare(descriptor)
        }
    }

    fun dispatchAppPrepared(app: IApplication, descriptor: GenericUiAppDescriptor) {
        for (hook in lifecycleHooks.toList()) {
            hook.onAppPrepared(app, descriptor)
        }
    }

    fun dispatchBeforeAppRun(app: IApplication, factory: NativeFactory?) {
        for (hook in lifecycleHooks.toList()) {
            hook.onBeforeAppRun(app, factory)
        }
    }

    fun dispatchAppShutdown(app: IApplication?) {
        for (hook in lifecycleHooks.toList()) {
            hook.onAppShutdown(app)
        }
    }
}
