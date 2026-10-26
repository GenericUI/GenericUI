package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.feedback.Spinner
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentHost
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.reflect.KClass

/**
 * Versatile multi-fragment host [Fragment] wrapping [FragmentHostSystem].
 *
 * Supports:
 * - Configurable enter/exit transitions via [FragmentTransitionSpec] (`None`, `Crossfade`,
 *   `FadeExpandShrink`, `SlideHorizontal`, or custom hooks).
 * - Optional Back/Forward and custom navigation chrome ([FragmentNavigationChromeConfig]) that embeds
 *   into the custom [TitleBar] when `titleCapabilities.canEmbedComponentsInTitleBar && navigationChrome.preferTitleBarNavigation`
 *   is `true`, falls back to a top [Navbar] ([fallbackNavbar]) when not embedded in the titlebar, and adds
 *   **neither** titlebar buttons nor a fallback navbar when no navigation chrome items are enabled (e.g. `DCGame`).
 * - Route registration, back/forward history stack, and `:web` `#hash` + browser `History.state` integration
 *   via [FragmentHistoryBridge].
 * - Global `.loader` [Spinner] overlay synchronized with the active child fragment's `isLoading` state.
 */
open class FragmentHostFragment(
    appTitle: String = "",
    transitionSpec: FragmentTransitionSpec = FragmentTransitionSpec.None,
    val navigationChrome: FragmentNavigationChromeConfig = FragmentNavigationChromeConfig()
) : Fragment(), FragmentHost {

    val hostSystem: FragmentHostSystem = FragmentHostSystem(
        rootContainer = this,
        appTitle = appTitle,
        transitionSpec = transitionSpec
    )

    var appTitle: String
        get() = hostSystem.appTitle
        set(value) {
            hostSystem.appTitle = value
            refreshNavigationChrome()
        }

    var transitionSpec: FragmentTransitionSpec
        get() = hostSystem.transitionSpec
        set(value) {
            hostSystem.transitionSpec = value
        }

    val stageContainer: Container
        get() = hostSystem.stageContainer

    val loaderContainer: Container
        get() = hostSystem.loaderContainer

    val loaderSpinner: Spinner
        get() = hostSystem.loaderSpinner

    val fallbackNavbar: Navbar = Navbar().apply {
        addClass("fragment-host-navbar")
        isVisible = false
    }

    val backButton: Button = Button(
        text = navigationChrome.backButtonText,
        type = BootstrapType.SECONDARY,
        size = BootstrapSize.SM
    ).apply {
        addClass("fragment-nav-back")
        setOnClick {
            popBackStack()
        }
    }

    val forwardButton: Button = Button(
        text = navigationChrome.forwardButtonText,
        type = BootstrapType.SECONDARY,
        size = BootstrapSize.SM
    ).apply {
        addClass("fragment-nav-forward")
        setOnClick {
            goForward()
        }
    }

    val navbarTitleLabel: Label = Label("").apply {
        addClass("fragment-nav-title fw-semibold mx-2")
    }

    private var managedTitleBar: TitleBar? = null

    init {
        hostSystem.hostFragmentReference = this
        hostSystem.hostingWindowProvider = { hostingWindow }
        hostSystem.attachedWindowProvider = { attachedWindow }
        hostSystem.onNavigationStateChanged = {
            refreshNavigationChrome()
        }
        hostSystem.onActiveFragmentChanged = {
            refreshNavigationChrome()
        }
        refreshNavigationChrome()
    }

    override val currentFragment: Fragment?
        get() = hostSystem.currentFragment

    val activeFragment: Fragment?
        get() = hostSystem.currentFragment

    val exitingFragments: List<Fragment>
        get() = hostSystem.transitionController.exitingFragments

    override val backStackDepth: Int
        get() = hostSystem.backStackDepth

    override val forwardStackDepth: Int
        get() = hostSystem.forwardStackDepth

    override val canGoBack: Boolean
        get() = hostSystem.canGoBack

    override val canGoForward: Boolean
        get() = hostSystem.canGoForward

    override fun setFragment(fragment: Fragment) {
        hostSystem.setFragment(fragment)
    }

    fun setRootFragment(
        fragment: Fragment,
        route: String? = null,
        args: FragmentBundle? = null
    ) {
        hostSystem.setRootFragment(fragment, route, args)
    }

    override fun pushFragment(fragment: Fragment) {
        hostSystem.pushFragment(fragment)
    }

    fun pushFragment(
        fragment: Fragment,
        route: String? = null,
        args: FragmentBundle? = null
    ) {
        hostSystem.pushFragment(fragment, route, args)
    }

    override fun replaceFragment(fragment: Fragment) {
        hostSystem.replaceFragment(fragment)
    }

    fun replaceFragment(
        fragment: Fragment,
        route: String? = null,
        args: FragmentBundle? = null
    ) {
        hostSystem.replaceFragment(fragment, route, args)
    }

    override fun popFragment(): Boolean = popBackStack()

    fun popBackStack(): Boolean = hostSystem.popBackStack()

    override fun goForward(): Boolean = hostSystem.goForward()

    fun <T : Fragment> registerRoute(
        route: String,
        fragmentClass: KClass<T>,
        title: String = "",
        factory: ((FragmentBundle?) -> T)? = null
    ): FragmentRouteEntry = hostSystem.registerRoute(route, fragmentClass, title, factory)

    inline fun <reified T : Fragment> registerRoute(
        route: String,
        title: String = "",
        noinline factory: ((FragmentBundle?) -> T)? = null
    ): FragmentRouteEntry = hostSystem.registerRoute(route, T::class, title, factory)

    fun navigateToRoute(
        route: String,
        args: FragmentBundle? = null,
        addToBackStack: Boolean = true
    ): Fragment = hostSystem.navigateToRoute(route, args, addToBackStack)

    fun resolveInitialDeepLink(
        defaultRoute: String? = null,
        defaultArgs: FragmentBundle? = null
    ): Fragment? = hostSystem.resolveInitialDeepLink(defaultRoute, defaultArgs)

    /**
     * Re-evaluates and synchronizes the Back/Forward buttons, custom navigation components,
     * [TitleBar] embedding (`mountedInTitleBar = true`), [fallbackNavbar] visibility, and window title.
     */
    fun refreshNavigationChrome() {
        backButton.text = navigationChrome.backButtonText
        forwardButton.text = navigationChrome.forwardButtonText
        backButton.isEnabled = hostSystem.canGoBack
        forwardButton.isEnabled = hostSystem.canGoForward
        backButton.isVisible = navigationChrome.showBackButton &&
            (!navigationChrome.hideDisabledNavButtons || hostSystem.canGoBack)
        forwardButton.isVisible = navigationChrome.showForwardButton &&
            (!navigationChrome.hideDisabledNavButtons || hostSystem.canGoForward)

        val winTitle = hostSystem.resolveWindowTitle(currentFragment)
        if (winTitle.isNotBlank()) {
            hostingWindow?.setTitle(winTitle) ?: attachedWindow?.setTitle(winTitle)
        }

        val hasItems = navigationChrome.hasNavigationItems
        if (!hasItems) {
            unmarkTitleBarComponents()
            managedTitleBar?.clearComponents()
            detachFallbackNavbar()
            return
        }

        val canEmbedInTitleBar = titleCapabilities.canEmbedComponentsInTitleBar &&
            navigationChrome.preferTitleBarNavigation

        if (canEmbedInTitleBar) {
            detachFallbackNavbar()
            val tb = getTitleBar() ?: managedTitleBar ?: TitleBar()
            managedTitleBar = tb
            tb.clearComponents()

            if (navigationChrome.showBackButton) {
                backButton.mountedInTitleBar = true
                tb.addLeading(backButton)
            } else {
                backButton.mountedInTitleBar = false
            }

            if (navigationChrome.showForwardButton) {
                forwardButton.mountedInTitleBar = true
                tb.addLeading(forwardButton)
            } else {
                forwardButton.mountedInTitleBar = false
            }

            for (comp in navigationChrome.leadingComponents) {
                comp.mountedInTitleBar = true
                tb.addLeading(comp)
            }
            for (comp in navigationChrome.centerComponents) {
                comp.mountedInTitleBar = true
                tb.addCenter(comp)
            }
            for (comp in navigationChrome.trailingComponents) {
                comp.mountedInTitleBar = true
                tb.addTrailing(comp)
            }

            if (getTitleBar() !== tb) {
                setTitleBar(tb)
            } else {
                syncChromeToAttachedWindow()
            }
            hostingWindow?.let { win ->
                if (win.getTitleBar() !== tb) {
                    win.setTitleBar(tb)
                }
            }
        } else {
            unmarkTitleBarComponents()
            managedTitleBar?.clearComponents()

            if (navigationChrome.showNavbarFallback) {
                fallbackNavbar.clearChildren()
                if (navigationChrome.showBackButton) {
                    fallbackNavbar.addChild(backButton)
                }
                if (navigationChrome.showForwardButton) {
                    fallbackNavbar.addChild(forwardButton)
                }
                for (comp in navigationChrome.leadingComponents) {
                    fallbackNavbar.addChild(comp)
                }

                val activePageTitle = hostSystem.resolveTitleForFragment(currentFragment).ifBlank { appTitle }
                if (navigationChrome.showCurrentPageTitleInNavbar) {
                    fallbackNavbar.brandTitle = activePageTitle
                    navbarTitleLabel.text = activePageTitle
                    navbarTitleLabel.isVisible = activePageTitle.isNotBlank()
                    if (activePageTitle.isNotBlank()) {
                        fallbackNavbar.addChild(navbarTitleLabel)
                    }
                } else {
                    fallbackNavbar.brandTitle = ""
                    navbarTitleLabel.isVisible = false
                }

                for (comp in navigationChrome.centerComponents) {
                    fallbackNavbar.addChild(comp)
                }
                for (comp in navigationChrome.trailingComponents) {
                    fallbackNavbar.addChild(comp)
                }

                fallbackNavbar.isVisible = true
                if (!children.contains(fallbackNavbar)) {
                    addChild(fallbackNavbar, 0)
                }
            } else {
                detachFallbackNavbar()
            }
        }
    }

    private fun unmarkTitleBarComponents() {
        backButton.mountedInTitleBar = false
        forwardButton.mountedInTitleBar = false
        for (comp in navigationChrome.leadingComponents) {
            comp.mountedInTitleBar = false
        }
        for (comp in navigationChrome.centerComponents) {
            comp.mountedInTitleBar = false
        }
        for (comp in navigationChrome.trailingComponents) {
            comp.mountedInTitleBar = false
        }
    }

    private fun detachFallbackNavbar() {
        fallbackNavbar.clearChildren()
        fallbackNavbar.isVisible = false
        if (children.contains(fallbackNavbar)) {
            removeChild(fallbackNavbar)
        }
    }

    override fun onStart() {
        super.onStart()
        refreshNavigationChrome()
        val active = currentFragment
        if (active != null && (active.state == FragmentState.CREATED || active.state == FragmentState.STOPPED)) {
            active.performStart()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNavigationChrome()
        val active = currentFragment
        if (active != null && active.state != FragmentState.RESUMED) {
            active.performResume()
        }
    }

    override fun onPause() {
        currentFragment?.performPause()
        super.onPause()
    }

    override fun onStop() {
        currentFragment?.performStop()
        super.onStop()
    }

    override fun onDestroy() {
        hostSystem.dispose()
        super.onDestroy()
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        hostSystem.updateViewportSize(viewportWidth, viewportHeight)
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        hostSystem.onConfigurationChanged(newConfig)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        currentFragment?.onWindowFocusChanged(hasFocus)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        currentFragment?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onBackPressed(): Boolean {
        if (currentFragment?.onBackPressed() == true) {
            return true
        }
        if (hostSystem.popBackStack()) {
            return true
        }
        return super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: FragmentBundle) {
        super.onSaveInstanceState(outState)
        hostSystem.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedState: FragmentBundle) {
        super.onRestoreInstanceState(savedState)
        hostSystem.onRestoreInstanceState(savedState)
    }

    override fun applyStyleSheet(styleSheet: CssStyleSheet, viewportWidthPx: Int) {
        super.applyStyleSheet(styleSheet, viewportWidthPx)
        hostSystem.onApplyStyleSheet(styleSheet, viewportWidthPx)
    }

    override fun applyPrecompiledStyleSheet(precompiled: PrecompiledStyleSheet, viewportWidthPx: Int) {
        super.applyPrecompiledStyleSheet(precompiled, viewportWidthPx)
        hostSystem.onApplyPrecompiledStyleSheet(precompiled, viewportWidthPx)
    }

    override fun dispose() {
        unmarkTitleBarComponents()
        hostSystem.dispose()
        super.dispose()
    }
}
