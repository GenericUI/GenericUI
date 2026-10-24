package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl

/**
 * Standard Container-driven application (`Application`).
 *
 * Accepts a [Container] (and does not force developers to use `Fragment`s if they do not need them).
 * For Fragment-driven applications (including Helper Fragments like `AdaptiveSplitTabFragment` and `DockWorkspaceFragment`),
 * use [FragmentApplication].
 */
open class Application(
    initialContent: Container? = null,
    title: String = "GenericUI Application"
) : Disposable {

    val mainWindow: Window = Window(title = title)
    var appNativeImpl: ApplicationNativeImpl<*>? = null
        private set

    private var themeModeState: ThemeMode = ThemeMode.DARK

    init {
        if (initialContent != null) {
            mainWindow.setContent(initialContent)
        }
    }

    open fun setContent(container: Container) {
        mainWindow.setContent(container)
    }

    open fun getContent(): Container? = mainWindow.getContent()

    fun setThemeMode(mode: ThemeMode) {
        themeModeState = mode
        appNativeImpl?.setThemeMode(mode)
    }

    fun getThemeMode(): ThemeMode = appNativeImpl?.getThemeMode() ?: themeModeState

    open fun run(factory: NativeFactory = GenericUI.requireFactory()) {
        GenericUI.initialize(factory)
        val impl = factory.createApplication()
        appNativeImpl = impl
        impl.setThemeMode(themeModeState)
        impl.runEventLoop {
            mainWindow.show(factory)
            onApplicationStarted()
        }
    }

    protected open fun onApplicationStarted() {}

    fun exit(exitCode: Int = 0) {
        mainWindow.close()
        appNativeImpl?.exitApplication(exitCode)
    }

    override fun dispose() {
        mainWindow.dispose()
        appNativeImpl?.dispose()
        appNativeImpl = null
    }
}
