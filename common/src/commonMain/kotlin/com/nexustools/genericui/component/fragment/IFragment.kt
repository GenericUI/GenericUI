package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.component.shell.MenuBar
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.fragment.FragmentLaunchMode
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.enums.window.DisplayCutoutMode
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
 * [BottomNavigationBar], fullscreen), [TitleCapabilities] inspection, and [launchFragment] navigation
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
     * Launch arguments ([FragmentBundle]) supplied when this fragment was launched via [launchFragment]
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
     * Called when the fragment is created. When launched via [launchFragment], [savedState] receives the
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
    fun onConfigurationChanged(newConfig: WindowConfiguration)
    fun onBackPressed(): Boolean
    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean)
    fun onWindowFocusChanged(hasFocus: Boolean)

    // Fragment Launching & Navigation (Interacting with IWindow & FragmentHost)
    /**
     * Instantiates [fragmentClass] (via [FragmentRegistry]), sets its [arguments] to [launchArgs],
     * delivers [launchArgs] to the new fragment's [onCreate], and mounts it in the target [IWindow]
     * ([targetWindow], else this fragment's [hostingWindow]) according to [launchMode] (updating the window's
     * content, [TitleBar], [MenuBar], [Navbar], [BottomNavigationBar], and fullscreen state).
     */
    fun <T : IFragment> launchFragment(
        fragmentClass: KClass<T>,
        launchArgs: FragmentBundle? = null,
        addToBackStack: Boolean = true,
        launchMode: FragmentLaunchMode = FragmentLaunchMode.SAME_WINDOW,
        targetWindow: IWindow? = null
    ): T = FragmentLaunchCoordinator.launch(
        sourceWindow = hostingWindow,
        sourceFragment = this,
        fragmentClass = fragmentClass,
        launchArgs = launchArgs,
        addToBackStack = addToBackStack,
        launchMode = launchMode,
        targetWindow = targetWindow
    )

    /**
     * Convenience overload accepting key-value pairs that are packed into a [FragmentBundle]
     * (via [FragmentBundle.of]) and delivered to [onCreate] of the newly created [fragmentClass] instance.
     * Must be called with at least one pair (a call without pairs resolves to the [FragmentBundle] overload).
     */
    fun <T : IFragment> launchFragment(
        fragmentClass: KClass<T>,
        vararg args: Pair<String, Any?>,
        addToBackStack: Boolean = true,
        launchMode: FragmentLaunchMode = FragmentLaunchMode.SAME_WINDOW,
        targetWindow: IWindow? = null
    ): T = launchFragment(
        fragmentClass = fragmentClass,
        launchArgs = if (args.isEmpty()) null else FragmentBundle.of(*args),
        addToBackStack = addToBackStack,
        launchMode = launchMode,
        targetWindow = targetWindow
    )

    /**
     * Launches an already-constructed [fragment] instance, assigning [launchArgs] (when non-null) to its
     * [arguments], delivering them to its [onCreate], and mounting it in the target [IWindow] according to
     * [launchMode].
     */
    fun <T : IFragment> launchFragment(
        fragment: T,
        launchArgs: FragmentBundle? = null,
        addToBackStack: Boolean = true,
        launchMode: FragmentLaunchMode = FragmentLaunchMode.SAME_WINDOW,
        targetWindow: IWindow? = null
    ): T = FragmentLaunchCoordinator.launch(
        sourceWindow = hostingWindow,
        sourceFragment = this,
        fragment = fragment,
        launchArgs = launchArgs,
        addToBackStack = addToBackStack,
        launchMode = launchMode,
        targetWindow = targetWindow
    )

    /**
     * Finishes this fragment: pops the hosting [FragmentHost] / [IWindow] back stack if there is a
     * previous fragment, or closes the hosting secondary window (`PanelWindow` / `DialogWindow` / `MdiWindow`)
     * if this was its root fragment. Returns `true` if anything was popped or closed.
     */
    fun finishFragment(): Boolean = FragmentLaunchCoordinator.finish(this)

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
