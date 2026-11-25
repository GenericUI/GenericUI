package com.nexustools.genericui.window

import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.fragment.FragmentLaunchCoordinator
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.exception.NativeUninitializedException
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import kotlin.reflect.KClass

/**
 * Common contract implemented by all window types in GenericUI:
 * - [com.nexustools.genericui.component.shell.Window] (top-level native window)
 * - [com.nexustools.genericui.component.dock.PanelWindow] (floating tool-modal dock window, inherits from `Window`)
 * - [com.nexustools.genericui.component.dialog.DialogWindow] (modal/modeless dialog window)
 * - [com.nexustools.genericui.component.shell.MdiWindow] (in-viewport Multi-Document Interface sub-window)
 *
 * `IWindow` only extends [Disposable] because the implementing classes share no common component interface
 * (they all extend the abstract class [com.nexustools.genericui.core.AbstractComponent], which already
 * implements [Disposable]).
 *
 * **JVM signature notes for implementers** (`:common` compiles for JVM, so property accessors and functions
 * with identical JVM signatures clash):
 * - `DialogWindow.title` / `MdiWindow.title` (`var title: String`) clash with [getTitle] / [setTitle]. Annotate the
 *   existing property with `@get:JvmName("getTitleProperty") @set:JvmName("setTitleProperty")` and implement
 *   `override fun getTitle(): String = title` / `override fun setTitle(title: String) { this.title = title }`.
 * - `DialogWindow.isFullscreen` (`var isFullscreen: Boolean`) clashes with [isFullscreen]. Annotate the existing
 *   property with `@get:JvmName("isFullscreenProperty")` and implement `override fun isFullscreen(): Boolean = isFullscreen`.
 * - [windowBounds] is a property (not `getWindowBounds()` / `setWindowBounds()` functions) so the existing
 *   `MdiWindow.windowBounds` simply becomes `override var windowBounds`.
 * - Overrides must not re-declare default parameter values: remove `= DisplayCutoutMode.SHORT_EDGES` from
 *   `Window.setFullscreen` and `= null` from `Window.show` when adding `override` (defaults are inherited).
 * - `Window` is an `open` class: `isWindowShown` / `isWindowFocused` keep their `private set` only if declared
 *   `final override var`. Members that `PanelWindow` re-declares (`ownerWindow`, `isMinimizable`, `hide()`, and
 *   new `isToolWindow` / `isMaximizable` / `isAlwaysOnTop`) must be `open` / `override` in `Window`, with
 *   `PanelWindow` using `override` (covariant `override val ownerWindow: Window?` is allowed).
 * - `MdiWindow.maximize(desktopWidth: Int = 1160, desktopHeight: Int = 640)` stays as an overload; add
 *   `override fun maximize() = maximize(1160, 640)` (Kotlin resolves `maximize()` to the no-default-args overload).
 * - `MdiWindow.setContent(component: AbstractComponent): MdiWindow` stays as an overload; add
 *   `override fun setContent(container: Container)` delegating to it.
 * - `MdiWindow.windowState: MdiWindowState` does not clash with [getWindowState] (different JVM return type);
 *   [getWindowState] / [setWindowState] map between `MdiWindowState` and [WindowState].
 */
interface IWindow : Disposable {
    /** Unique identifier for this window instance. */
    val windowId: String

    /** Owning parent window if this is a child, modal, or tool window (`null` for root windows). */
    val ownerWindow: IWindow?

    /** Child/attached windows owned by this window (e.g., attached `PanelWindow`s). */
    val childWindows: List<IWindow>

    /** Capabilities of this window's titlebar on the current backend and window kind. */
    val titleCapabilities: TitleCapabilities

    // Title & Icon
    fun getTitle(): String
    fun setTitle(title: String)

    /** Window icon (image path or inline SVG), or `null` for the default/no icon. */
    fun getWindowIcon(): String?
    fun setWindowIcon(iconPathOrSvg: String?)
    fun setWindowIcon(resId: com.nexustools.genericui.resources.ResourceID) =
        setWindowIcon(resId.toResourceUri())

    // TitleBar Configuration
    fun getTitleBar(): TitleBar?
    fun setTitleBar(titleBar: TitleBar?)

    // Geometry & Bounds
    fun getWindowSize(): Size
    fun setWindowSize(size: Size)
    fun getMinWindowSize(): Size? = null
    fun setMinWindowSize(minSize: Size?) {}
    fun getAspectRatio(): Double? = null
    fun setAspectRatio(aspectRatio: Double?) {}
    fun setAspectRatio(widthRatio: Int, heightRatio: Int) {
        require(widthRatio > 0 && heightRatio > 0) {
            "Aspect ratio dimensions must be positive, got ${widthRatio}:${heightRatio}"
        }
        setAspectRatio(widthRatio.toDouble() / heightRatio.toDouble())
    }

    /** Window bounds (position + size) in screen pixels (or viewport pixels for `MdiWindow`). */
    var windowBounds: Rect

    // Window State & Flags
    fun getWindowState(): WindowState
    fun setWindowState(state: WindowState)

    /** `true` once the window has been shown and not yet closed/hidden. */
    val isWindowShown: Boolean

    /** `true` when the window is currently visible on screen (`false` after [hide] until [show] is called). */
    val isVisible: Boolean

    /** `true` while the window has input focus (active window). */
    val isWindowFocused: Boolean
    val isClosable: Boolean
    val isMinimizable: Boolean
    val isMaximizable: Boolean
    val isResizable: Boolean
    val isModal: Boolean
    val isToolWindow: Boolean
    val isAlwaysOnTop: Boolean
    fun setAlwaysOnTop(alwaysOnTop: Boolean) {}

    val isUndecorated: Boolean
        get() = false
    fun setUndecorated(undecorated: Boolean) {}
    fun startSystemMove() {}

    fun isFullscreen(): Boolean
    fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode = DisplayCutoutMode.SHORT_EDGES)

    // Lifecycle, Visibility & Focus Actions
    fun show(factory: NativeFactory? = null)
    fun hide()
    fun close()
    fun minimize()
    fun maximize()
    fun restore()
    fun focus()
    fun bringToFront()

    // Content & Fragment Hosting
    fun setContent(container: Container)
    fun getContent(): Container?

    /** The [FragmentContainer] currently hosting fragments inside this window, if any. */
    val fragmentContainer: FragmentContainer?

    /**
     * Returns the window's [FragmentContainer], creating it and installing it as the window content
     * (via [setContent]) if not already present. Used by [FragmentLaunchCoordinator] to mount fragments.
     */
    fun getOrCreateFragmentContainer(): FragmentContainer

    /** Currently active [IFragment] mounted in this window's [fragmentContainer], if any. */
    val activeFragment: IFragment?
        get() = fragmentContainer?.currentFragment as? IFragment

    /** `true` if the active backend allows opening multiple concurrent top-level windows. */
    val supportsMultipleWindows: Boolean
        get() {
            val factory = (this as? AbstractComponent)?.resolveActiveFactory()
                ?: GenericUI.activeFactory
                ?: throw NativeUninitializedException("IWindow", "supportsMultipleWindows")
            return factory.supportsMultipleWindows
        }

    /** `true` if the active backend supports floating/modal dialog windows. */
    val supportsDialogWindows: Boolean
        get() {
            val factory = (this as? AbstractComponent)?.resolveActiveFactory()
                ?: GenericUI.activeFactory
                ?: throw NativeUninitializedException("IWindow", "supportsDialogWindows")
            return factory.supportsDialogWindows
        }

    /** `true` if the active backend AND [fragmentClass]'s metadata allow opening another window. */
    fun canOpenAdditionalWindow(fragmentClass: KClass<out IFragment>): Boolean {
        val factory = (this as? AbstractComponent)?.resolveActiveFactory()
            ?: GenericUI.activeFactory
            ?: throw NativeUninitializedException("IWindow", "canOpenAdditionalWindow")
        return factory.supportsMultipleWindows &&
            !FragmentRegistry.isDialogFragment(fragmentClass) &&
            FragmentRegistry.supportsMultipleWindows(fragmentClass)
    }

    /**
     * Instantiates [fragmentClass] (via [FragmentRegistry]), delivers [launchArgs] to [IFragment.onCreate],
     * and starts it inside this window (or opens an additional window / dialog window according to
     * [openAdditionalWindow] and [FragmentRegistry] metadata).
     */
    fun <T : IFragment> startFragment(
        fragmentClass: KClass<T>,
        launchArgs: FragmentBundle? = null,
        openAdditionalWindow: Boolean = false,
        addToBackStack: Boolean = true,
        clearTask: Boolean = false
    ): T = FragmentLaunchCoordinator.start(
        sourceWindow = this,
        sourceFragment = null,
        fragmentClass = fragmentClass,
        launchArgs = launchArgs,
        openAdditionalWindow = openAdditionalWindow,
        addToBackStack = addToBackStack,
        clearTask = clearTask,
        targetWindow = null
    )

    /**
     * Convenience overload packing key-value pairs into a [FragmentBundle] via [FragmentBundle.of].
     */
    fun <T : IFragment> startFragment(
        fragmentClass: KClass<T>,
        firstArg: Pair<String, Any?>,
        vararg extraArgs: Pair<String, Any?>,
        openAdditionalWindow: Boolean = false,
        addToBackStack: Boolean = true,
        clearTask: Boolean = false
    ): T = startFragment(
        fragmentClass = fragmentClass,
        launchArgs = FragmentBundle.of(firstArg, *extraArgs),
        openAdditionalWindow = openAdditionalWindow,
        addToBackStack = addToBackStack,
        clearTask = clearTask
    )

    /**
     * Starts an already-constructed [fragment] instance, delivering [launchArgs] (when non-null) to
     * [IFragment.onCreate] and presenting it in this window or a new window/dialog.
     */
    fun <T : IFragment> startFragment(
        fragment: T,
        launchArgs: FragmentBundle? = null,
        openAdditionalWindow: Boolean = false,
        addToBackStack: Boolean = true,
        clearTask: Boolean = false
    ): T = FragmentLaunchCoordinator.start(
        sourceWindow = this,
        sourceFragment = null,
        fragment = fragment,
        launchArgs = launchArgs,
        openAdditionalWindow = openAdditionalWindow,
        addToBackStack = addToBackStack,
        clearTask = clearTask,
        targetWindow = null
    )

    /**
     * Pops the window's fragment back stack if possible (never closes the window).
     * Returns `true` if a fragment was popped.
     */
    fun popFragmentBackStack(): Boolean = fragmentContainer?.popFragment() ?: false

    // Window Lifecycle Listeners
    fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?)
    fun setOnWindowStateChanged(listener: ((WindowState) -> Unit)?) {}
    fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?)
    fun addOnWindowFocusChanged(listener: (Boolean) -> Unit) {}
    fun removeOnWindowFocusChanged(listener: (Boolean) -> Unit) {}
    fun setOnWindowCloseRequested(listener: (() -> Boolean)?)
    fun addOnWindowTitleChanged(listener: (String) -> Unit) {}
    fun removeOnWindowTitleChanged(listener: (String) -> Unit) {}
}

/** Reified convenience for [IWindow.canOpenAdditionalWindow]. */
inline fun <reified T : IFragment> IWindow.canOpenAdditionalWindow(): Boolean =
    canOpenAdditionalWindow(T::class)

/** Reified convenience for [IWindow.startFragment]. */
inline fun <reified T : IFragment> IWindow.startFragment(
    launchArgs: FragmentBundle? = null,
    openAdditionalWindow: Boolean = false,
    addToBackStack: Boolean = true,
    clearTask: Boolean = false
): T = startFragment(
    fragmentClass = T::class,
    launchArgs = launchArgs,
    openAdditionalWindow = openAdditionalWindow,
    addToBackStack = addToBackStack,
    clearTask = clearTask
)

/** Reified convenience for [IWindow.startFragment] with key-value pair arguments. */
inline fun <reified T : IFragment> IWindow.startFragment(
    firstArg: Pair<String, Any?>,
    vararg extraArgs: Pair<String, Any?>,
    openAdditionalWindow: Boolean = false,
    addToBackStack: Boolean = true,
    clearTask: Boolean = false
): T = startFragment(
    fragmentClass = T::class,
    launchArgs = FragmentBundle.of(firstArg, *extraArgs),
    openAdditionalWindow = openAdditionalWindow,
    addToBackStack = addToBackStack,
    clearTask = clearTask
)
