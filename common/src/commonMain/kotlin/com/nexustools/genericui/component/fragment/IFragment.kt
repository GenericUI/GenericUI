package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.component.shell.MenuBar
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.system.PreferenceStore
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.SoftInputMode
import com.nexustools.genericui.exception.NativeUninitializedException
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.reflect.KClass

/**
 * Common interface implemented by all fragments in GenericUI ([Fragment], [ScrollFragment],
 * `PageFragment`, `DockWorkspaceFragment`, etc.).
 *
 * Every member mirrors the existing [Fragment] API (same names, signatures, and nullability), so [Fragment]
 * implements this interface by adding `: IFragment` and `override` modifiers. Notes for the implementer:
 * - Overrides must not re-declare default parameter values; remove the defaults from `Fragment.setFullscreen`,
 *   `Fragment.flushTimeouts`, `Fragment.alert`, `Fragment.confirm`, `Fragment.prompt`, and `Fragment.promptInt`
 *   (the identical defaults declared here are inherited).
 * - `state`, `fragmentHost`, and `attachedWindow` are read-only here; [Fragment] keeps them as
 *   `override var ... internal set`.
 * - New members [hostingWindow] and [arguments] must be added to [Fragment] (the window property is named
 *   `hostingWindow`, not `window`, so it never shadows `kotlinx.browser.window` inside `:web` fragment subclasses).
 *
 * Provides lifecycle callbacks, window chrome synchronization ([TitleBar], [MenuBar], [Navbar],
 * [BottomNavigationBar], fullscreen), [TitleCapabilities] inspection, and [startFragment] navigation
 * where `launchArgs` ([FragmentBundle]) are delivered to the created fragment's [onCreate].
 */
interface IFragment : Disposable {
    /** Current lifecycle state of this fragment. */
    val state: FragmentState

    /** The [FragmentHost] (such as [FragmentContainer]) currently hosting this fragment. */
    val fragmentHost: FragmentHost?

    /** The top-level [Window] this fragment is attached to, if hosted in a top-level [Window]. */
    val attachedWindow: Window?

    /**
     * The [IWindow] (`Window`, `PanelWindow`, `DialogWindow`, or `MdiWindow`) hosting this fragment, if any.
     * Equal to [attachedWindow] when hosted in a top-level [Window].
     */
    val hostingWindow: IWindow?

    /**
     * The owning [IApplication] hosting this fragment, if attached to an application.
     */
    val application: IApplication?
        get() = null

    /**
     * Launch arguments ([FragmentBundle]) supplied when this fragment was launched via [startFragment]
     * (assigned by [FragmentRegistry.instantiate] / [FragmentLaunchCoordinator]). Also delivered into
     * [onCreate] when the fragment is first created.
     */
    var arguments: FragmentBundle?

    /** Convenience alias for [arguments]. */
    val launchArgs: FragmentBundle?
        get() = arguments

    /** [TitleCapabilities] of the [hostingWindow] (or default capabilities if unattached). */
    val titleCapabilities: TitleCapabilities
        get() = hostingWindow?.titleCapabilities ?: TitleCapabilities()

    /** `true` if the active backend allows opening multiple concurrent top-level windows. */
    val supportsMultipleWindows: Boolean
        get() {
            hostingWindow?.let { return it.supportsMultipleWindows }
            application?.let { return it.supportsMultipleWindows }
            val factory = GenericUI.activeFactory
                ?: throw NativeUninitializedException("IFragment", "supportsMultipleWindows")
            return factory.supportsMultipleWindows
        }

    /** `true` if the active backend supports floating/modal dialog windows. */
    val supportsDialogWindows: Boolean
        get() {
            hostingWindow?.let { return it.supportsDialogWindows }
            application?.let { return it.supportsDialogWindows }
            val factory = GenericUI.activeFactory
                ?: throw NativeUninitializedException("IFragment", "supportsDialogWindows")
            return factory.supportsDialogWindows
        }

    /** `true` if the active backend AND [fragmentClass]'s metadata allow opening another window. */
    fun canOpenAdditionalWindow(fragmentClass: KClass<out IFragment>): Boolean {
        hostingWindow?.let { return it.canOpenAdditionalWindow(fragmentClass) }
        application?.let { return it.canOpenAdditionalWindow(fragmentClass) }
        val factory = GenericUI.activeFactory
            ?: throw NativeUninitializedException("IFragment", "canOpenAdditionalWindow")
        return factory.supportsMultipleWindows &&
            !FragmentRegistry.isDialogFragment(fragmentClass) &&
            FragmentRegistry.supportsMultipleWindows(fragmentClass)
    }

    // Window & Fragment Chrome Management
    fun setNavBar(navBar: Navbar?)
    fun getNavBar(): Navbar?
    fun setBottomNavigationBar(bottomNav: BottomNavigationBar?)
    fun getBottomNavigationBar(): BottomNavigationBar?
    fun setMenuBar(menuBar: MenuBar?)
    fun getMenuBar(): MenuBar?
    fun setTitleBar(titleBar: TitleBar?)
    fun getTitleBar(): TitleBar?
    fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode = DisplayCutoutMode.SHORT_EDGES)
    fun isFullscreen(): Boolean
    fun getDisplayCutoutMode(): DisplayCutoutMode

    /** Controls how the hosting window adjusts for the on-screen soft keyboard while this fragment is active. */
    var softInputMode: SoftInputMode

    val isWindowVisible: Boolean
        get() = hostingWindow?.isVisible ?: false

    fun hideWindow() {
        hostingWindow?.hide()
    }

    fun showWindow() {
        hostingWindow?.show()
    }

    // Reference-counted Loading State (`loading++` / `loading--`)
    var loading: Int
    val isLoading: Boolean
    var onLoadingStateChanged: ((isLoading: Boolean, count: Int) -> Unit)?
    fun beginLoading()
    fun endLoading()
    fun onLoadingStart()
    fun onLoadingFinished()

    // Lifecycle Callbacks (1:1 Android Activity)
    /**
     * Called when the fragment is created. When launched via [startFragment], [savedState] receives the
     * `launchArgs` [FragmentBundle] (merged with any restored instance state if applicable).
     */
    fun onCreate(savedState: FragmentBundle?)
    fun onStart()
    fun onResume()
    fun onPause()
    fun onStop()
    fun onDestroy()

    // State Persistence & System Events
    fun onSaveInstanceState(outState: FragmentBundle)
    fun onRestoreInstanceState(savedState: FragmentBundle)
    fun onResize(viewportWidth: Int, viewportHeight: Int) {}
    fun onConfigurationChanged(newConfig: WindowConfiguration)
    fun onBackPressed(): Boolean
    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean)
    fun onWindowFocusChanged(hasFocus: Boolean)

    // Customizable Transition Hooks
    fun onBeginEnterTransition(durationMs: Int) {}
    fun onActivateEnterTransition(durationMs: Int) {}
    fun onBeginExitTransition(durationMs: Int) {}
    fun onFinishExitTransition() {}

    // Fragment Launching & Navigation (Interacting with IWindow & FragmentHost)
    /**
     * Instantiates [fragmentClass] (via [FragmentRegistry]), sets its [arguments] to [launchArgs],
     * delivers [launchArgs] to the new fragment's [onCreate], and mounts it in the target [IWindow]
     * (or opens an additional window / dialog window according to [openAdditionalWindow] and
     * [FragmentRegistry] metadata).
     */
    fun <T : IFragment> startFragment(
        fragmentClass: KClass<T>,
        launchArgs: FragmentBundle? = null,
        openAdditionalWindow: Boolean = false,
        addToBackStack: Boolean = true,
        clearTask: Boolean = false,
        targetWindow: IWindow? = null
    ): T = FragmentLaunchCoordinator.start(
        sourceWindow = hostingWindow,
        sourceFragment = this,
        fragmentClass = fragmentClass,
        launchArgs = launchArgs,
        openAdditionalWindow = openAdditionalWindow,
        addToBackStack = addToBackStack,
        clearTask = clearTask,
        targetWindow = targetWindow
    )

    /**
     * Convenience overload accepting key-value pairs that are packed into a [FragmentBundle]
     * (via [FragmentBundle.of]) and delivered to [onCreate] of the newly created [fragmentClass] instance.
     */
    fun <T : IFragment> startFragment(
        fragmentClass: KClass<T>,
        firstArg: Pair<String, Any?>,
        vararg extraArgs: Pair<String, Any?>,
        openAdditionalWindow: Boolean = false,
        addToBackStack: Boolean = true,
        clearTask: Boolean = false,
        targetWindow: IWindow? = null
    ): T = startFragment(
        fragmentClass = fragmentClass,
        launchArgs = FragmentBundle.of(firstArg, *extraArgs),
        openAdditionalWindow = openAdditionalWindow,
        addToBackStack = addToBackStack,
        clearTask = clearTask,
        targetWindow = targetWindow
    )

    /**
     * Starts an already-constructed [fragment] instance, assigning [launchArgs] (when non-null) to its
     * [arguments], delivering them to its [onCreate], and presenting it in the target window, a new
     * additional window, or a dialog window.
     */
    fun <T : IFragment> startFragment(
        fragment: T,
        launchArgs: FragmentBundle? = null,
        openAdditionalWindow: Boolean = false,
        addToBackStack: Boolean = true,
        clearTask: Boolean = false,
        targetWindow: IWindow? = null
    ): T = FragmentLaunchCoordinator.start(
        sourceWindow = hostingWindow,
        sourceFragment = this,
        fragment = fragment,
        launchArgs = launchArgs,
        openAdditionalWindow = openAdditionalWindow,
        addToBackStack = addToBackStack,
        clearTask = clearTask,
        targetWindow = targetWindow
    )

    /**
     * Finishes this fragment: pops the hosting [FragmentHost] / [IWindow] back stack if there is a
     * previous fragment, or closes the hosting secondary window (`PanelWindow` / `DialogWindow` / `MdiWindow`)
     * if this was its root fragment. Returns `true` if anything was popped or closed.
     */
    fun finishFragment(): Boolean = FragmentLaunchCoordinator.finish(this)

    /**
     * Returns the [PreferenceStore] associated with this fragment.
     *
     * Delegates to [application]'s [IApplication.getPreferenceStore] when attached to an application,
     * or lazily creates and caches a fragment-scoped fallback [PreferenceStore] instance.
     */
    fun getPreferenceStore(): PreferenceStore

    // Lifecycle-scoped Timers (automatically cancelled on destroy / dispose)
    val activeTimerCount: Int
    fun setImmediate(block: () -> Unit): Int
    fun setTimeout(delayMs: Int, block: () -> Unit): Int
    fun setInterval(intervalMs: Int, block: () -> Unit): Int
    fun clearTimeout(id: Int)
    fun clearInterval(id: Int)
    fun clearAllTimers()
    fun triggerTimer(id: Int): Boolean
    fun flushTimeouts(maxDelayMs: Int = Int.MAX_VALUE): Int

    // Convenience Dialog Helpers
    fun alert(message: String, title: String = "Notice", onDismiss: () -> Unit = {})
    fun confirm(message: String, title: String = "Confirm", onResult: (Boolean) -> Unit)
    fun prompt(
        message: String,
        defaultValue: String = "",
        title: String = "Input",
        onResult: (String?) -> Unit
    )
    fun promptInt(
        message: String,
        defaultValue: Int = 1,
        min: Int = 1,
        max: Int = Int.MAX_VALUE,
        title: String = "Enter Amount",
        onResult: (Int?) -> Unit
    )
}

/** Reified convenience for [IFragment.canOpenAdditionalWindow]. */
inline fun <reified T : IFragment> IFragment.canOpenAdditionalWindow(): Boolean =
    canOpenAdditionalWindow(T::class)

/** Reified convenience for [IFragment.startFragment]. */
inline fun <reified T : IFragment> IFragment.startFragment(
    launchArgs: FragmentBundle? = null,
    openAdditionalWindow: Boolean = false,
    addToBackStack: Boolean = true,
    clearTask: Boolean = false,
    targetWindow: IWindow? = null
): T = startFragment(
    fragmentClass = T::class,
    launchArgs = launchArgs,
    openAdditionalWindow = openAdditionalWindow,
    addToBackStack = addToBackStack,
    clearTask = clearTask,
    targetWindow = targetWindow
)

/** Reified convenience for [IFragment.startFragment] with key-value pair arguments. */
inline fun <reified T : IFragment> IFragment.startFragment(
    firstArg: Pair<String, Any?>,
    vararg extraArgs: Pair<String, Any?>,
    openAdditionalWindow: Boolean = false,
    addToBackStack: Boolean = true,
    clearTask: Boolean = false,
    targetWindow: IWindow? = null
): T = startFragment(
    fragmentClass = T::class,
    launchArgs = FragmentBundle.of(firstArg, *extraArgs),
    openAdditionalWindow = openAdditionalWindow,
    addToBackStack = addToBackStack,
    clearTask = clearTask,
    targetWindow = targetWindow
)
