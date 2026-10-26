package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.component.dialog.Dialogs
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.component.shell.MenuBar
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Activity-like lifecycle component extending [Container] and implementing [IFragment].
 *
 * - **1:1 Android `Activity` Compatibility**: Mirrors the full Android `Activity` lifecycle (`onCreate`,
 *   `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`, `onSaveInstanceState`, `onRestoreInstanceState`,
 *   `onConfigurationChanged`, `onBackPressed`, `onPictureInPictureModeChanged`, `onWindowFocusChanged`).
 *   On `:android`, any [Fragment] can be hosted 1:1 as an Android `Activity` via `GenericUiFragmentActivity`.
 * - **Optional Fragment Chrome (`setNavBar`, `setBottomNavigationBar`, `setMenuBar`, `setTitleBar`, `setFullscreen`)**:
 *   Top navigation bars (`Navbar`) and other window chrome are completely optional (`null` by default) and
 *   only attach when explicitly configured on the fragment.
 */
open class Fragment : Container("fragment"), IFragment {

    override var state: FragmentState = FragmentState.INITIALIZED
        internal set

    override var fragmentHost: FragmentHost? = null
        internal set

    override var attachedWindow: Window? = null
        internal set

    internal var attachedIWindow: IWindow? = null

    @Suppress("USELESS_CAST")
    override val hostingWindow: IWindow?
        get() = attachedIWindow ?: (attachedWindow as? IWindow)

    override var arguments: FragmentBundle? = null

    /** Optional route identifier when hosted inside `FragmentHostSystem` / `FragmentHostFragment`. */
    open var routeKey: String = ""

    /** Optional display title for window titlebars and navigation bars. */
    open var pageTitle: String = ""

    private var navBar: Navbar? = null
    private var bottomNavigationBar: BottomNavigationBar? = null
    private var menuBar: MenuBar? = null
    private var titleBar: TitleBar? = null
    private var fullscreenMode: Boolean = false
    private var displayCutoutMode: DisplayCutoutMode = DisplayCutoutMode.DEFAULT

    override fun setNavBar(navBar: Navbar?) {
        this.navBar = navBar
        syncChromeToAttachedWindow()
    }

    override fun getNavBar(): Navbar? = navBar

    override fun setBottomNavigationBar(bottomNav: BottomNavigationBar?) {
        this.bottomNavigationBar = bottomNav
        syncChromeToAttachedWindow()
    }

    override fun getBottomNavigationBar(): BottomNavigationBar? = bottomNavigationBar

    override fun setMenuBar(menuBar: MenuBar?) {
        this.menuBar = menuBar
        syncChromeToAttachedWindow()
    }

    override fun getMenuBar(): MenuBar? = menuBar

    override fun setTitleBar(titleBar: TitleBar?) {
        this.titleBar = titleBar
        syncChromeToAttachedWindow()
    }

    override fun getTitleBar(): TitleBar? = titleBar

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        this.fullscreenMode = fullscreen
        this.displayCutoutMode = cutoutMode
        syncChromeToAttachedWindow()
    }

    override fun isFullscreen(): Boolean = fullscreenMode

    override fun getDisplayCutoutMode(): DisplayCutoutMode = displayCutoutMode

    internal fun syncChromeToAttachedWindow() {
        val win = attachedWindow ?: (attachedIWindow as? Window)
        if (win != null) {
            win.setFullscreen(fullscreenMode, displayCutoutMode)
            win.setNavBar(if (fullscreenMode) null else navBar)
            win.setBottomNavigationBar(if (fullscreenMode) null else bottomNavigationBar)
            if (titleBar != null) {
                win.setTitleBar(titleBar)
            }
            if (menuBar != null) {
                win.setMenuBar(menuBar)
            }
            return
        }
        val iWin = attachedIWindow ?: return
        iWin.setFullscreen(fullscreenMode, displayCutoutMode)
        if (titleBar != null) {
            iWin.setTitleBar(titleBar)
        }
    }

    // 1:1 Android Activity & Cross-Platform Lifecycle Callbacks
    override fun onCreate(savedState: FragmentBundle?) {}
    override fun onStart() {}
    override fun onResume() {}
    override fun onPause() {}
    override fun onStop() {}
    override fun onDestroy() {}
    override fun onSaveInstanceState(outState: FragmentBundle) {}
    override fun onRestoreInstanceState(savedState: FragmentBundle) {}
    override fun onConfigurationChanged(newConfig: WindowConfiguration) {}
    override fun onBackPressed(): Boolean = false
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {}
    override fun onWindowFocusChanged(hasFocus: Boolean) {}

    // Customizable Transition & Viewport Lifecycle Hooks
    open fun onBeginEnterTransition(durationMs: Int) {}
    open fun onActivateEnterTransition(durationMs: Int) {}
    open fun onBeginExitTransition(durationMs: Int) {}
    open fun onFinishExitTransition() {}
    open fun onResize(viewportWidth: Int, viewportHeight: Int) {}

    // Reference-counted asynchronous loading state (`loading++` / `loading--`)
    private var loadingCounter: Int = 0

    override var loading: Int
        get() = loadingCounter
        set(value) {
            val next = value.coerceAtLeast(0)
            val wasLoading = loadingCounter > 0
            loadingCounter = next
            val nowLoading = loadingCounter > 0
            if (!wasLoading && nowLoading) {
                addClass("loading")
                onLoadingStart()
                onLoadingStateChanged?.invoke(true, loadingCounter)
            } else if (wasLoading && !nowLoading) {
                removeClass("loading")
                onLoadingFinished()
                onLoadingStateChanged?.invoke(false, 0)
            } else {
                onLoadingStateChanged?.invoke(nowLoading, loadingCounter)
            }
        }

    override val isLoading: Boolean
        get() = loadingCounter > 0

    override var onLoadingStateChanged: ((isLoading: Boolean, count: Int) -> Unit)? = null

    override fun beginLoading() {
        loading += 1
    }

    override fun endLoading() {
        loading = (loading - 1).coerceAtLeast(0)
    }

    override fun onLoadingStart() {}
    override fun onLoadingFinished() {}

    // Lifecycle-scoped timers (automatically cancelled on performDestroy / dispose)
    private class ScopedTimerEntry(
        val id: Int,
        val delayMs: Int,
        val repeating: Boolean,
        val callback: () -> Unit,
        var schedulerTimerId: Int = -1,
        var active: Boolean = true
    )

    private var nextTimerId: Int = 1
    private val activeTimers = linkedMapOf<Int, ScopedTimerEntry>()

    override val activeTimerCount: Int
        get() = activeTimers.size

    override fun setImmediate(block: () -> Unit): Int {
        if (state == FragmentState.DESTROYED) return -1
        val id = nextTimerId++
        val entry = ScopedTimerEntry(id = id, delayMs = 0, repeating = false, callback = block)
        activeTimers[id] = entry
        if (entry.active && state != FragmentState.DESTROYED) {
            activeTimers.remove(id)
            entry.active = false
            block()
        }
        return id
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun setTimeout(delayMs: Int, callback: () -> Unit): Int {
        if (state == FragmentState.DESTROYED) return -1
        val id = nextTimerId++
        val clampedDelay = delayMs.coerceAtLeast(0)
        val entry = ScopedTimerEntry(id = id, delayMs = clampedDelay, repeating = false, callback = callback)
        activeTimers[id] = entry
        if (clampedDelay <= 0 && entry.active && state != FragmentState.DESTROYED) {
            activeTimers.remove(id)
            entry.active = false
            callback()
        } else if (clampedDelay > 0) {
            entry.schedulerTimerId = UiTimerScheduler.setTimeout(clampedDelay) {
                triggerTimer(id)
            }
        }
        return id
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun setInterval(intervalMs: Int, callback: () -> Unit): Int {
        if (state == FragmentState.DESTROYED) return -1
        val id = nextTimerId++
        val clampedInterval = intervalMs.coerceAtLeast(1)
        val entry = ScopedTimerEntry(id = id, delayMs = clampedInterval, repeating = true, callback = callback)
        activeTimers[id] = entry
        entry.schedulerTimerId = UiTimerScheduler.setInterval(clampedInterval) {
            triggerTimer(id)
        }
        return id
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun clearTimeout(timerId: Int) {
        val removed = activeTimers.remove(timerId)
        if (removed != null) {
            removed.active = false
            if (removed.schedulerTimerId > 0) {
                UiTimerScheduler.clearTimer(removed.schedulerTimerId)
            }
        }
        super.clearTimeout(timerId)
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun clearInterval(timerId: Int) {
        val removed = activeTimers.remove(timerId)
        if (removed != null) {
            removed.active = false
            if (removed.schedulerTimerId > 0) {
                UiTimerScheduler.clearTimer(removed.schedulerTimerId)
            }
        }
        super.clearInterval(timerId)
    }

    override fun clearAllTimers() {
        for (entry in activeTimers.values) {
            entry.active = false
            if (entry.schedulerTimerId > 0) {
                UiTimerScheduler.clearTimer(entry.schedulerTimerId)
            }
        }
        activeTimers.clear()
        super.clearAllTimers()
    }

    /**
     * Executes a pending timer by [id] if it is still active and this fragment has not been destroyed.
     * Repeating intervals remain registered; one-shot timeouts are removed upon firing.
     */
    override fun triggerTimer(id: Int): Boolean {
        if (state == FragmentState.DESTROYED) return false
        val entry = activeTimers[id] ?: return false
        if (!entry.active) return false
        if (!entry.repeating) {
            activeTimers.remove(id)
            entry.active = false
            if (entry.schedulerTimerId > 0) {
                UiTimerScheduler.clearTimer(entry.schedulerTimerId)
            }
        }
        entry.callback()
        return true
    }

    /**
     * Advances and executes all currently active one-shot timeouts whose `delayMs <= maxDelayMs`.
     */
    override fun flushTimeouts(maxDelayMs: Int): Int {
        if (state == FragmentState.DESTROYED) return 0
        val dueIds = activeTimers.values
            .filter { it.active && !it.repeating && it.delayMs <= maxDelayMs }
            .map { it.id }
        var executed = 0
        for (id in dueIds) {
            if (triggerTimer(id)) executed++
        }
        return executed
    }

    // Convenience Dialog Helpers
    override fun alert(message: String, title: String, onDismiss: () -> Unit) {
        Dialogs.alert(title = title, message = message, ownerWindow = attachedWindow, onDismiss = onDismiss)
    }

    override fun confirm(message: String, title: String, onResult: (Boolean) -> Unit) {
        Dialogs.confirm(title = title, message = message, ownerWindow = attachedWindow, onResult = onResult)
    }

    override fun prompt(
        message: String,
        defaultValue: String,
        title: String,
        onResult: (String?) -> Unit
    ) {
        Dialogs.prompt(
            title = title,
            message = message,
            defaultValue = defaultValue,
            ownerWindow = attachedWindow,
            onResult = onResult
        )
    }

    override fun promptInt(
        message: String,
        defaultValue: Int,
        min: Int,
        max: Int,
        title: String,
        onResult: (Int?) -> Unit
    ) {
        prompt(message = message, defaultValue = defaultValue.toString(), title = title) { raw ->
            val parsed = raw?.trim()?.toIntOrNull()
            if (parsed != null && parsed in min..max) {
                onResult(parsed)
            } else {
                onResult(null)
            }
        }
    }

    internal fun performCreate(savedState: FragmentBundle? = null) {
        if (state == FragmentState.INITIALIZED || state == FragmentState.DESTROYED) {
            state = FragmentState.CREATED
            val launchArgs = arguments
            val effectiveBundle = if (launchArgs != null) {
                FragmentBundle().apply {
                    putAll(launchArgs)
                    savedState?.let { putAll(it) }
                }
            } else {
                savedState
            }
            onCreate(effectiveBundle)
            if (savedState != null) {
                onRestoreInstanceState(savedState)
            }
        }
    }

    internal fun performStart() {
        if (state == FragmentState.INITIALIZED) {
            performCreate(null)
        }
        if (state == FragmentState.CREATED || state == FragmentState.STOPPED) {
            state = FragmentState.STARTED
            onStart()
        }
    }

    internal fun performResume() {
        if (state != FragmentState.STARTED && state != FragmentState.PAUSED) {
            performStart()
        }
        if (state == FragmentState.STARTED || state == FragmentState.PAUSED) {
            state = FragmentState.RESUMED
            syncChromeToAttachedWindow()
            onResume()
        }
    }

    internal fun performPause() {
        if (state == FragmentState.RESUMED) {
            state = FragmentState.PAUSED
            onPause()
        }
    }

    internal fun performStop() {
        if (state == FragmentState.RESUMED) {
            performPause()
        }
        if (state == FragmentState.PAUSED || state == FragmentState.STARTED) {
            state = FragmentState.STOPPED
            onStop()
        }
    }

    internal fun performDestroy() {
        clearAllTimers()
        if (state == FragmentState.RESUMED || state == FragmentState.PAUSED || state == FragmentState.STARTED) {
            performStop()
        }
        if (state != FragmentState.DESTROYED && state != FragmentState.INITIALIZED) {
            state = FragmentState.DESTROYED
            onDestroy()
        }
        dispose()
    }

    override fun dispose() {
        clearAllTimers()
        super.dispose()
    }
}
