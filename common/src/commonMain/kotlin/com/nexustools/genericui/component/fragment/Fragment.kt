package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.component.dialog.Dialogs
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.component.shell.MenuBar
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Activity-like lifecycle component extending [Container].
 *
 * - **1:1 Android `Activity` Compatibility**: Mirrors the full Android `Activity` lifecycle (`onCreate`,
 *   `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`, `onSaveInstanceState`, `onRestoreInstanceState`,
 *   `onConfigurationChanged`, `onBackPressed`, `onPictureInPictureModeChanged`, `onWindowFocusChanged`).
 *   On `:android`, any [Fragment] can be hosted 1:1 as an Android `Activity` via `GenericUiFragmentActivity`.
 * - **Optional Fragment Chrome (`setNavBar`, `setBottomNavigationBar`, `setMenuBar`, `setTitleBar`, `setFullscreen`)**:
 *   Top navigation bars (`Navbar`) and other window chrome are completely optional (`null` by default) and
 *   only attach when explicitly configured on the fragment.
 */
abstract class Fragment : Container("fragment") {

    var state: FragmentState = FragmentState.INITIALIZED
        internal set

    var fragmentHost: FragmentHost? = null
        internal set

    var attachedWindow: Window? = null
        internal set

    private var navBar: Navbar? = null
    private var bottomNavigationBar: BottomNavigationBar? = null
    private var menuBar: MenuBar? = null
    private var titleBar: TitleBar? = null
    private var fullscreenMode: Boolean = false
    private var displayCutoutMode: DisplayCutoutMode = DisplayCutoutMode.DEFAULT

    fun setNavBar(navBar: Navbar?) {
        this.navBar = navBar
        syncChromeToAttachedWindow()
    }

    fun getNavBar(): Navbar? = navBar

    fun setBottomNavigationBar(bottomNav: BottomNavigationBar?) {
        this.bottomNavigationBar = bottomNav
        syncChromeToAttachedWindow()
    }

    fun getBottomNavigationBar(): BottomNavigationBar? = bottomNavigationBar

    fun setMenuBar(menuBar: MenuBar?) {
        this.menuBar = menuBar
        syncChromeToAttachedWindow()
    }

    fun getMenuBar(): MenuBar? = menuBar

    fun setTitleBar(titleBar: TitleBar?) {
        this.titleBar = titleBar
        syncChromeToAttachedWindow()
    }

    fun getTitleBar(): TitleBar? = titleBar

    fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode = DisplayCutoutMode.SHORT_EDGES) {
        this.fullscreenMode = fullscreen
        this.displayCutoutMode = cutoutMode
        syncChromeToAttachedWindow()
    }

    fun isFullscreen(): Boolean = fullscreenMode

    fun getDisplayCutoutMode(): DisplayCutoutMode = displayCutoutMode

    internal fun syncChromeToAttachedWindow() {
        val win = attachedWindow ?: return
        win.setFullscreen(fullscreenMode, displayCutoutMode)
        win.setNavBar(if (fullscreenMode) null else navBar)
        win.setBottomNavigationBar(if (fullscreenMode) null else bottomNavigationBar)
        if (titleBar != null) {
            win.setTitleBar(titleBar)
        }
        if (menuBar != null) {
            win.setMenuBar(menuBar)
        }
    }

    // 1:1 Android Activity & Cross-Platform Lifecycle Callbacks
    open fun onCreate(savedState: FragmentBundle?) {}
    open fun onStart() {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onStop() {}
    open fun onDestroy() {}
    open fun onSaveInstanceState(outState: FragmentBundle) {}
    open fun onRestoreInstanceState(savedState: FragmentBundle) {}
    open fun onConfigurationChanged(newConfig: WindowConfiguration) {}
    open fun onBackPressed(): Boolean = false
    open fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {}
    open fun onWindowFocusChanged(hasFocus: Boolean) {}

    // Reference-counted asynchronous loading state (`loading++` / `loading--`)
    private var loadingCounter: Int = 0

    var loading: Int
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

    val isLoading: Boolean
        get() = loadingCounter > 0

    var onLoadingStateChanged: ((isLoading: Boolean, count: Int) -> Unit)? = null

    fun beginLoading() {
        loading += 1
    }

    fun endLoading() {
        loading = (loading - 1).coerceAtLeast(0)
    }

    open fun onLoadingStart() {}
    open fun onLoadingFinished() {}

    // Lifecycle-scoped timers (automatically cancelled on performDestroy / dispose)
    private class ScopedTimerEntry(
        val id: Int,
        val delayMs: Int,
        val repeating: Boolean,
        val callback: () -> Unit,
        var active: Boolean = true
    )

    private var nextTimerId: Int = 1
    private val activeTimers = linkedMapOf<Int, ScopedTimerEntry>()

    val activeTimerCount: Int
        get() = activeTimers.size

    fun setImmediate(block: () -> Unit): Int {
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

    fun setTimeout(delayMs: Int, block: () -> Unit): Int {
        if (state == FragmentState.DESTROYED) return -1
        val id = nextTimerId++
        val entry = ScopedTimerEntry(id = id, delayMs = delayMs.coerceAtLeast(0), repeating = false, callback = block)
        activeTimers[id] = entry
        if (delayMs <= 0 && entry.active && state != FragmentState.DESTROYED) {
            activeTimers.remove(id)
            entry.active = false
            block()
        }
        return id
    }

    fun setInterval(intervalMs: Int, block: () -> Unit): Int {
        if (state == FragmentState.DESTROYED) return -1
        val id = nextTimerId++
        val entry = ScopedTimerEntry(id = id, delayMs = intervalMs.coerceAtLeast(1), repeating = true, callback = block)
        activeTimers[id] = entry
        return id
    }

    fun clearTimeout(id: Int) {
        activeTimers.remove(id)?.active = false
    }

    fun clearInterval(id: Int) {
        activeTimers.remove(id)?.active = false
    }

    fun clearAllTimers() {
        for (entry in activeTimers.values) {
            entry.active = false
        }
        activeTimers.clear()
    }

    /**
     * Executes a pending timer by [id] if it is still active and this fragment has not been destroyed.
     * Repeating intervals remain registered; one-shot timeouts are removed upon firing.
     */
    fun triggerTimer(id: Int): Boolean {
        if (state == FragmentState.DESTROYED) return false
        val entry = activeTimers[id] ?: return false
        if (!entry.active) return false
        if (!entry.repeating) {
            activeTimers.remove(id)
            entry.active = false
        }
        entry.callback()
        return true
    }

    /**
     * Advances and executes all currently active one-shot timeouts whose `delayMs <= maxDelayMs`.
     */
    fun flushTimeouts(maxDelayMs: Int = Int.MAX_VALUE): Int {
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

    // Convenience Dialog Helpers (matching DCGame PageController / Context patterns)
    fun alert(message: String, title: String = "Notice", onDismiss: () -> Unit = {}) {
        Dialogs.alert(title = title, message = message, ownerWindow = attachedWindow, onDismiss = onDismiss)
    }

    fun confirm(message: String, title: String = "Confirm", onResult: (Boolean) -> Unit) {
        Dialogs.confirm(title = title, message = message, ownerWindow = attachedWindow, onResult = onResult)
    }

    fun prompt(
        message: String,
        defaultValue: String = "",
        title: String = "Input",
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

    fun promptInt(
        message: String,
        defaultValue: Int = 1,
        min: Int = 1,
        max: Int = Int.MAX_VALUE,
        title: String = "Enter Amount",
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
            onCreate(savedState)
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
