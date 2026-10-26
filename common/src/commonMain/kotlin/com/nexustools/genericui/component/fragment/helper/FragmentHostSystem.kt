package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.feedback.Spinner
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentHost
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.reflect.KClass

/**
 * Core state, routing, browser history, `.loader` spinner overlay, and transition engine
 * implementing [FragmentHost].
 *
 * Powers [FragmentHostFragment] and can also be attached to any host [Container].
 */
class FragmentHostSystem(
    val rootContainer: Container,
    var appTitle: String = "",
    var transitionSpec: FragmentTransitionSpec = FragmentTransitionSpec.None,
    val historyBridge: FragmentHistoryBridge = FragmentHistoryBridge.create()
) : FragmentHost, Disposable {

    private class HistoryStackEntry(
        val route: String,
        val fragmentClass: KClass<out Fragment>,
        val title: String,
        var fragment: Fragment?,
        val savedState: FragmentBundle,
        val historyIndex: Int
    )

    val stageContainer: Container = Container("div", "fragment-stage")
    val loaderContainer: Container = Container("div", "loader").apply {
        isVisible = false
    }
    val loaderSpinner: Spinner = Spinner()
    val transitionController: FragmentTransitionController = FragmentTransitionController(stageContainer)

    private val routes = linkedMapOf<String, FragmentRouteEntry>()
    private val backStack = mutableListOf<HistoryStackEntry>()
    private val forwardStack = mutableListOf<HistoryStackEntry>()
    private val historyEntriesByIndex = linkedMapOf<Int, HistoryStackEntry>()

    var currentHistoryIndex: Int = 0
        private set

    var currentRoute: String = ""
        private set

    private var isHandlingPopState: Boolean = false

    override var currentFragment: Fragment? = null
        private set

    override val backStackDepth: Int
        get() = backStack.size

    override val forwardStackDepth: Int
        get() = forwardStack.size

    override val canGoBack: Boolean
        get() = backStack.isNotEmpty()

    override val canGoForward: Boolean
        get() = forwardStack.isNotEmpty()

    val isAnyFragmentLoading: Boolean
        get() = currentFragment?.isLoading == true

    var viewportWidthPx: Int = 1280
        private set
    var viewportHeightPx: Int = 720
        private set

    var themeStyleSheet: CssStyleSheet? = null
    var precompiledStyleSheet: PrecompiledStyleSheet? = null

    internal var hostFragmentReference: FragmentHost? = null
    var hostingWindowProvider: (() -> IWindow?)? = null
    var attachedWindowProvider: (() -> Window?)? = null

    var onNavigationStateChanged: (() -> Unit)? = null
    var onActiveFragmentChanged: ((Fragment?) -> Unit)? = null
    var onFragmentChanged: ((Fragment) -> Unit)? = null
    var onLoadingChanged: ((Boolean) -> Unit)? = null

    val registeredRoutes: Map<String, FragmentRouteEntry>
        get() = routes.toMap()

    init {
        if (!rootContainer.hasClass("fragment-host")) {
            rootContainer.addClass("fragment-host")
        }
        loaderContainer.addChild(loaderSpinner)
        rootContainer.addChild(stageContainer)
        rootContainer.addChild(loaderContainer)

        historyBridge.setOnPopStateListener { route, historyIndex, restoredState ->
            handleBrowserPopState(route, historyIndex, restoredState)
        }
    }

    /** Normalizes a route string by trimming whitespace and stripping any leading `#`. */
    fun normalizeRoute(route: String): String =
        route.trim().removePrefix("#").trim()

    /** Formats a route string as `#<route>` with a single leading `#`. */
    fun formatRouteHash(route: String): String {
        val norm = normalizeRoute(route)
        return if (norm.isEmpty()) "#" else "#$norm"
    }

    fun registerRoute(entry: FragmentRouteEntry): FragmentRouteEntry {
        val normalized = normalizeRoute(entry.route)
        val normalizedEntry = entry.copy(route = normalized)
        routes[normalized] = normalizedEntry
        val customFactory = entry.factory
        if (customFactory != null && !FragmentRegistry.isRegistered(entry.fragmentClass)) {
            @Suppress("UNCHECKED_CAST")
            FragmentRegistry.register(entry.fragmentClass as KClass<Fragment>) { args ->
                customFactory(args)
            }
        }
        return normalizedEntry
    }

    fun <T : Fragment> registerRoute(
        route: String,
        fragmentClass: KClass<T>,
        title: String = "",
        factory: ((FragmentBundle?) -> T)? = null
    ): FragmentRouteEntry {
        if (factory != null) {
            FragmentRegistry.register(fragmentClass, factory)
        }
        return registerRoute(
            FragmentRouteEntry(
                route = route,
                fragmentClass = fragmentClass,
                title = title,
                factory = factory
            )
        )
    }

    inline fun <reified T : Fragment> registerRoute(
        route: String,
        title: String = "",
        noinline factory: ((FragmentBundle?) -> T)? = null
    ): FragmentRouteEntry = registerRoute(route, T::class, title, factory)

    fun getRouteEntry(route: String): FragmentRouteEntry? =
        routes[normalizeRoute(route)]

    fun resolveRouteForFragment(fragment: Fragment): String {
        if (fragment.routeKey.isNotBlank()) {
            return normalizeRoute(fragment.routeKey)
        }
        val matched = routes.values.firstOrNull { it.fragmentClass == fragment::class }
        if (matched != null) {
            return matched.route
        }
        return fragment::class.simpleName?.lowercase() ?: "fragment"
    }

    fun resolveTitleForFragment(fragment: Fragment? = currentFragment): String {
        val target = fragment ?: return ""
        if (target.pageTitle.isNotBlank()) {
            return target.pageTitle
        }
        if (target is PageFragment && target.title.isNotBlank()) {
            return target.title
        }
        val matched = routes[resolveRouteForFragment(target)]
            ?: routes.values.firstOrNull { it.fragmentClass == target::class }
        return matched?.title.orEmpty()
    }

    fun resolveWindowTitle(fragment: Fragment? = currentFragment): String {
        val pageTitle = resolveTitleForFragment(fragment)
        return when {
            appTitle.isNotBlank() && pageTitle.isNotBlank() -> "$appTitle - $pageTitle"
            pageTitle.isNotBlank() -> pageTitle
            else -> appTitle
        }
    }

    override fun setFragment(fragment: Fragment) {
        setRootFragment(fragment, route = null, args = fragment.arguments)
    }

    fun setRootFragment(
        fragment: Fragment,
        route: String? = null,
        args: FragmentBundle? = null
    ) {
        clearAndDestroyStack(backStack)
        clearAndDestroyStack(forwardStack)
        historyEntriesByIndex.clear()
        currentHistoryIndex = 0

        if (args != null) {
            fragment.arguments = args
        }
        val resolvedRoute = normalizeRoute(route ?: resolveRouteForFragment(fragment))
        fragment.routeKey = resolvedRoute
        if (fragment.pageTitle.isBlank()) {
            routes[resolvedRoute]?.title?.takeIf { it.isNotBlank() }?.let { fragment.pageTitle = it }
        }
        currentRoute = resolvedRoute

        performFragmentSwitch(
            incoming = fragment,
            destroyOutgoing = true,
            restoredState = null
        )

        val initialState = FragmentBundle().apply {
            fragment.arguments?.let { putAll(it) }
        }
        val entry = HistoryStackEntry(
            route = resolvedRoute,
            fragmentClass = fragment::class,
            title = resolveTitleForFragment(fragment),
            fragment = fragment,
            savedState = initialState,
            historyIndex = currentHistoryIndex
        )
        historyEntriesByIndex[currentHistoryIndex] = entry

        if (historyBridge.isSupported && !isHandlingPopState) {
            historyBridge.replaceState(
                route = resolvedRoute,
                historyIndex = currentHistoryIndex,
                title = resolveWindowTitle(fragment),
                state = initialState
            )
        }
        notifyNavigationStateChanged()
    }

    override fun pushFragment(fragment: Fragment) {
        pushFragment(fragment, route = null, args = fragment.arguments)
    }

    fun pushFragment(
        fragment: Fragment,
        route: String? = null,
        args: FragmentBundle? = null
    ) {
        val outgoing = currentFragment
        if (outgoing == null) {
            setRootFragment(fragment, route, args)
            return
        }

        val outgoingBundle = captureFragmentState(outgoing)
        val outgoingRoute = resolveRouteForFragment(outgoing)
        val outgoingTitle = resolveTitleForFragment(outgoing)
        if (historyBridge.isSupported && !isHandlingPopState) {
            historyBridge.replaceState(
                route = outgoingRoute,
                historyIndex = currentHistoryIndex,
                title = resolveWindowTitle(outgoing),
                state = outgoingBundle
            )
        }
        val outgoingEntry = HistoryStackEntry(
            route = outgoingRoute,
            fragmentClass = outgoing::class,
            title = outgoingTitle,
            fragment = outgoing,
            savedState = outgoingBundle,
            historyIndex = currentHistoryIndex
        )
        backStack.add(outgoingEntry)
        historyEntriesByIndex[currentHistoryIndex] = outgoingEntry

        clearAndDestroyStack(forwardStack)
        currentHistoryIndex += 1

        if (args != null) {
            fragment.arguments = args
        }
        val resolvedRoute = normalizeRoute(route ?: resolveRouteForFragment(fragment))
        fragment.routeKey = resolvedRoute
        if (fragment.pageTitle.isBlank()) {
            routes[resolvedRoute]?.title?.takeIf { it.isNotBlank() }?.let { fragment.pageTitle = it }
        }
        currentRoute = resolvedRoute

        performFragmentSwitch(
            incoming = fragment,
            destroyOutgoing = false,
            restoredState = null
        )

        val incomingBundle = FragmentBundle().apply {
            fragment.arguments?.let { putAll(it) }
        }
        val incomingEntry = HistoryStackEntry(
            route = resolvedRoute,
            fragmentClass = fragment::class,
            title = resolveTitleForFragment(fragment),
            fragment = fragment,
            savedState = incomingBundle,
            historyIndex = currentHistoryIndex
        )
        historyEntriesByIndex[currentHistoryIndex] = incomingEntry

        if (historyBridge.isSupported && !isHandlingPopState) {
            historyBridge.pushState(
                route = resolvedRoute,
                historyIndex = currentHistoryIndex,
                title = resolveWindowTitle(fragment),
                state = incomingBundle
            )
        }
        notifyNavigationStateChanged()
    }

    override fun replaceFragment(fragment: Fragment) {
        replaceFragment(fragment, route = null, args = fragment.arguments)
    }

    fun replaceFragment(
        fragment: Fragment,
        route: String? = null,
        args: FragmentBundle? = null
    ) {
        clearAndDestroyStack(forwardStack)
        if (args != null) {
            fragment.arguments = args
        }
        val resolvedRoute = normalizeRoute(route ?: resolveRouteForFragment(fragment))
        fragment.routeKey = resolvedRoute
        if (fragment.pageTitle.isBlank()) {
            routes[resolvedRoute]?.title?.takeIf { it.isNotBlank() }?.let { fragment.pageTitle = it }
        }
        currentRoute = resolvedRoute

        performFragmentSwitch(
            incoming = fragment,
            destroyOutgoing = true,
            restoredState = null
        )

        val incomingBundle = FragmentBundle().apply {
            fragment.arguments?.let { putAll(it) }
        }
        val incomingEntry = HistoryStackEntry(
            route = resolvedRoute,
            fragmentClass = fragment::class,
            title = resolveTitleForFragment(fragment),
            fragment = fragment,
            savedState = incomingBundle,
            historyIndex = currentHistoryIndex
        )
        historyEntriesByIndex[currentHistoryIndex] = incomingEntry

        if (historyBridge.isSupported && !isHandlingPopState) {
            historyBridge.replaceState(
                route = resolvedRoute,
                historyIndex = currentHistoryIndex,
                title = resolveWindowTitle(fragment),
                state = incomingBundle
            )
        }
        notifyNavigationStateChanged()
    }

    fun navigateToRoute(
        route: String,
        args: FragmentBundle? = null,
        addToBackStack: Boolean = true
    ): Fragment {
        val normalized = normalizeRoute(route)
        val entry = routes[normalized]
            ?: throw IllegalStateException("Route '$normalized' is not registered in FragmentHostSystem.")
        val fragment = instantiateRouteFragment(entry, args)
        when {
            currentFragment == null -> setRootFragment(fragment, normalized, args)
            addToBackStack -> pushFragment(fragment, normalized, args)
            else -> replaceFragment(fragment, normalized, args)
        }
        return fragment
    }

    override fun popFragment(): Boolean = popBackStack()

    fun popBackStack(): Boolean {
        if (backStack.isEmpty()) return false
        val outgoing = currentFragment
        if (outgoing != null) {
            val outgoingBundle = captureFragmentState(outgoing)
            val outgoingRoute = resolveRouteForFragment(outgoing)
            if (historyBridge.isSupported && !isHandlingPopState) {
                historyBridge.replaceState(
                    route = outgoingRoute,
                    historyIndex = currentHistoryIndex,
                    title = resolveWindowTitle(outgoing),
                    state = outgoingBundle
                )
            }
            val forwardEntry = HistoryStackEntry(
                route = outgoingRoute,
                fragmentClass = outgoing::class,
                title = resolveTitleForFragment(outgoing),
                fragment = outgoing,
                savedState = outgoingBundle,
                historyIndex = currentHistoryIndex
            )
            forwardStack.add(forwardEntry)
            historyEntriesByIndex[currentHistoryIndex] = forwardEntry
        }

        val targetEntry = backStack.removeAt(backStack.lastIndex)
        currentHistoryIndex = targetEntry.historyIndex
        currentRoute = targetEntry.route
        val targetFragment = obtainOrInstantiateStackEntryFragment(targetEntry, targetEntry.savedState)

        performFragmentSwitch(
            incoming = targetFragment,
            destroyOutgoing = false,
            restoredState = targetEntry.savedState
        )

        if (historyBridge.isSupported && !isHandlingPopState) {
            if (!historyBridge.goBack()) {
                historyBridge.replaceState(
                    route = targetEntry.route,
                    historyIndex = targetEntry.historyIndex,
                    title = resolveWindowTitle(targetFragment),
                    state = targetEntry.savedState
                )
            }
        }
        notifyNavigationStateChanged()
        return true
    }

    override fun goForward(): Boolean {
        if (forwardStack.isEmpty()) return false
        val outgoing = currentFragment
        if (outgoing != null) {
            val outgoingBundle = captureFragmentState(outgoing)
            val outgoingRoute = resolveRouteForFragment(outgoing)
            if (historyBridge.isSupported && !isHandlingPopState) {
                historyBridge.replaceState(
                    route = outgoingRoute,
                    historyIndex = currentHistoryIndex,
                    title = resolveWindowTitle(outgoing),
                    state = outgoingBundle
                )
            }
            val backEntry = HistoryStackEntry(
                route = outgoingRoute,
                fragmentClass = outgoing::class,
                title = resolveTitleForFragment(outgoing),
                fragment = outgoing,
                savedState = outgoingBundle,
                historyIndex = currentHistoryIndex
            )
            backStack.add(backEntry)
            historyEntriesByIndex[currentHistoryIndex] = backEntry
        }

        val targetEntry = forwardStack.removeAt(forwardStack.lastIndex)
        currentHistoryIndex = targetEntry.historyIndex
        currentRoute = targetEntry.route
        val targetFragment = obtainOrInstantiateStackEntryFragment(targetEntry, targetEntry.savedState)

        performFragmentSwitch(
            incoming = targetFragment,
            destroyOutgoing = false,
            restoredState = targetEntry.savedState
        )

        if (historyBridge.isSupported && !isHandlingPopState) {
            if (!historyBridge.goForward()) {
                historyBridge.replaceState(
                    route = targetEntry.route,
                    historyIndex = targetEntry.historyIndex,
                    title = resolveWindowTitle(targetFragment),
                    state = targetEntry.savedState
                )
            }
        }
        notifyNavigationStateChanged()
        return true
    }

    /**
     * Resolves an initial deep link from [FragmentHistoryBridge.currentRouteHash] and
     * [FragmentHistoryBridge.currentSavedState], falling back to [defaultRoute] if present.
     */
    fun resolveInitialDeepLink(
        defaultRoute: String? = null,
        defaultArgs: FragmentBundle? = null
    ): Fragment? {
        val rawHash = historyBridge.currentRouteHash()
        val hashRoute = rawHash?.let { normalizeRoute(it) }?.takeIf { it.isNotEmpty() }
        val restoredState = historyBridge.currentSavedState() ?: defaultArgs
        if (hashRoute != null && routes.containsKey(hashRoute)) {
            return navigateToRoute(hashRoute, restoredState, addToBackStack = false)
        }
        val fallback = defaultRoute?.let { normalizeRoute(it) }?.takeIf { it.isNotEmpty() }
        if (fallback != null && routes.containsKey(fallback)) {
            return navigateToRoute(fallback, restoredState, addToBackStack = false)
        }
        return null
    }

    private fun handleBrowserPopState(
        rawRoute: String,
        historyIndex: Int?,
        restoredState: FragmentBundle?
    ) {
        val prevFlag = isHandlingPopState
        isHandlingPopState = true
        try {
            val normalizedRoute = normalizeRoute(rawRoute)

            // 1. Check if historyIndex matches backStack or forwardStack
            if (historyIndex != null) {
                if (historyIndex < currentHistoryIndex && backStack.any { it.historyIndex == historyIndex }) {
                    while (backStack.isNotEmpty() && currentHistoryIndex > historyIndex) {
                        val nextBack = backStack.last()
                        if (nextBack.historyIndex == historyIndex && restoredState != null) {
                            nextBack.savedState.putAll(restoredState)
                        }
                        popBackStack()
                    }
                    return
                }
                if (historyIndex > currentHistoryIndex && forwardStack.any { it.historyIndex == historyIndex }) {
                    while (forwardStack.isNotEmpty() && currentHistoryIndex < historyIndex) {
                        val nextForward = forwardStack.last()
                        if (nextForward.historyIndex == historyIndex && restoredState != null) {
                            nextForward.savedState.putAll(restoredState)
                        }
                        goForward()
                    }
                    return
                }
                val indexedEntry = historyEntriesByIndex[historyIndex]
                if (indexedEntry != null && historyIndex != currentHistoryIndex) {
                    if (restoredState != null) {
                        indexedEntry.savedState.putAll(restoredState)
                    }
                    val frag = obtainOrInstantiateStackEntryFragment(indexedEntry, indexedEntry.savedState)
                    currentHistoryIndex = historyIndex
                    currentRoute = indexedEntry.route
                    performFragmentSwitch(
                        incoming = frag,
                        destroyOutgoing = false,
                        restoredState = indexedEntry.savedState
                    )
                    notifyNavigationStateChanged()
                    return
                }
            }

            // 2. Match by route in immediate back/forward stack or registered routes
            if (backStack.lastOrNull()?.route == normalizedRoute) {
                val entry = backStack.last()
                if (restoredState != null) {
                    entry.savedState.putAll(restoredState)
                }
                popBackStack()
                return
            }
            if (forwardStack.lastOrNull()?.route == normalizedRoute) {
                val entry = forwardStack.last()
                if (restoredState != null) {
                    entry.savedState.putAll(restoredState)
                }
                goForward()
                return
            }
            val registered = routes[normalizedRoute]
            if (registered != null && normalizedRoute != currentRoute) {
                val fragment = instantiateRouteFragment(registered, restoredState)
                pushFragment(fragment, normalizedRoute, restoredState)
            } else if (restoredState != null) {
                currentFragment?.onRestoreInstanceState(restoredState)
            }
        } finally {
            isHandlingPopState = prevFlag
        }
    }

    private fun instantiateRouteFragment(
        entry: FragmentRouteEntry,
        args: FragmentBundle?
    ): Fragment {
        val customFactory = entry.factory
        val created = if (customFactory != null) {
            customFactory(args).also { it.arguments = args }
        } else {
            FragmentRegistry.instantiate(entry.fragmentClass, args)
        }
        created.routeKey = entry.route
        if (created.pageTitle.isBlank() && entry.title.isNotBlank()) {
            created.pageTitle = entry.title
        }
        return created
    }

    private fun obtainOrInstantiateStackEntryFragment(
        entry: HistoryStackEntry,
        restoredState: FragmentBundle?
    ): Fragment {
        val existing = entry.fragment
        if (existing != null && existing.state != FragmentState.DESTROYED) {
            if (restoredState != null) {
                val mergedArgs = FragmentBundle().apply {
                    existing.arguments?.let { putAll(it) }
                    putAll(restoredState)
                }
                existing.arguments = mergedArgs
            }
            return existing
        }
        val routeEntry = routes[entry.route]
        val recreated = if (routeEntry != null) {
            instantiateRouteFragment(routeEntry, restoredState)
        } else {
            FragmentRegistry.instantiate(entry.fragmentClass, restoredState)
        }
        recreated.routeKey = entry.route
        if (recreated.pageTitle.isBlank() && entry.title.isNotBlank()) {
            recreated.pageTitle = entry.title
        }
        entry.fragment = recreated
        return recreated
    }

    private fun captureFragmentState(fragment: Fragment): FragmentBundle {
        val bundle = FragmentBundle()
        fragment.arguments?.let { bundle.putAll(it) }
        fragment.onSaveInstanceState(bundle)
        return bundle
    }

    private fun resolveHostingIWindow(): IWindow? =
        hostingWindowProvider?.invoke() ?: ( resolveAttachedWindow() as? IWindow )

    private fun resolveAttachedWindow(): Window? =
        attachedWindowProvider?.invoke() ?: (hostingWindowProvider?.invoke() as? Window)

    private fun performFragmentSwitch(
        incoming: Fragment,
        destroyOutgoing: Boolean,
        restoredState: FragmentBundle?
    ) {
        val outgoing = currentFragment
        currentFragment = incoming

        val resolvedWindow = resolveAttachedWindow()
        val resolvedIWindow = resolveHostingIWindow()
        incoming.fragmentHost = hostFragmentReference ?: this
        incoming.attachedWindow = resolvedWindow
        incoming.attachedIWindow = resolvedIWindow

        val prevLoadingCallback = incoming.onLoadingStateChanged
        incoming.onLoadingStateChanged = { isLoading, count ->
            prevLoadingCallback?.invoke(isLoading, count)
            syncGlobalLoaderVisibility()
        }

        val winTitle = resolveWindowTitle(incoming)
        if (winTitle.isNotBlank()) {
            resolvedIWindow?.setTitle(winTitle) ?: resolvedWindow?.setTitle(winTitle)
        }

        transitionController.runTransition(
            outgoing = outgoing,
            incoming = incoming,
            spec = transitionSpec,
            destroyOutgoing = destroyOutgoing,
            mountIncoming = { frag ->
                if (!stageContainer.children.contains(frag)) {
                    stageContainer.addChild(frag)
                }
                if (frag.state == FragmentState.INITIALIZED || frag.state == FragmentState.DESTROYED) {
                    frag.performCreate(restoredState)
                } else if (restoredState != null) {
                    frag.onRestoreInstanceState(restoredState)
                }
                frag.performStart()
                frag.performResume()
            },
            finalizeOutgoing = { outFrag, shouldDestroy ->
                if (shouldDestroy) {
                    if (outFrag.state != FragmentState.DESTROYED) {
                        outFrag.performDestroy()
                    }
                } else {
                    if (outFrag.state == FragmentState.RESUMED || outFrag.state == FragmentState.PAUSED || outFrag.state == FragmentState.STARTED) {
                        outFrag.performStop()
                    }
                }
                outFrag.attachedWindow = null
                outFrag.attachedIWindow = null
                if (stageContainer.children.contains(outFrag)) {
                    stageContainer.removeChild(outFrag)
                }
            },
            applyStylesTo = { frag ->
                applyStoredStyleSheetTo(frag)
            },
            onIncomingActivated = { frag ->
                if (frag === currentFragment) {
                    syncGlobalLoaderVisibility()
                    frag.onResize(viewportWidthPx, viewportHeightPx)
                }
            }
        )

        syncGlobalLoaderVisibility()
        onActiveFragmentChanged?.invoke(incoming)
        onFragmentChanged?.invoke(incoming)
    }

    fun syncGlobalLoaderVisibility() {
        val loading = currentFragment?.isLoading == true
        loaderContainer.isVisible = loading
        if (loading) {
            if (!rootContainer.hasClass("loading")) {
                rootContainer.addClass("loading")
            }
            if (!stageContainer.hasClass("loading")) {
                stageContainer.addClass("loading")
            }
        } else {
            if (rootContainer.hasClass("loading")) {
                rootContainer.removeClass("loading")
            }
            if (stageContainer.hasClass("loading")) {
                stageContainer.removeClass("loading")
            }
        }
        val active = currentFragment
        if (active != null) {
            applyStoredStyleSheetTo(active)
            if (!loading) {
                active.onResize(viewportWidthPx, viewportHeightPx)
            }
        }
        onLoadingChanged?.invoke(loading)
    }

    fun applyStoredStyleSheetTo(fragment: Fragment) {
        val precompiled = precompiledStyleSheet
        val sheet = themeStyleSheet
        when {
            precompiled != null -> fragment.applyPrecompiledStyleSheet(precompiled, viewportWidthPx)
            sheet != null -> fragment.applyStyleSheet(sheet, viewportWidthPx)
        }
    }

    fun updateViewportSize(widthPx: Int, heightPx: Int) {
        viewportWidthPx = widthPx.coerceAtLeast(1)
        viewportHeightPx = heightPx.coerceAtLeast(1)
        val active = currentFragment ?: return
        applyStoredStyleSheetTo(active)
        active.onResize(viewportWidthPx, viewportHeightPx)
    }

    fun onConfigurationChanged(newConfig: WindowConfiguration) {
        viewportWidthPx = newConfig.widthPx.coerceAtLeast(1)
        viewportHeightPx = newConfig.heightPx.coerceAtLeast(1)
        val active = currentFragment ?: return
        applyStoredStyleSheetTo(active)
        active.onConfigurationChanged(newConfig)
        active.onResize(viewportWidthPx, viewportHeightPx)
    }

    fun onApplyStyleSheet(styleSheet: CssStyleSheet, widthPx: Int) {
        themeStyleSheet = styleSheet
        if (widthPx > 0) {
            viewportWidthPx = widthPx
        }
        currentFragment?.onResize(viewportWidthPx, viewportHeightPx)
    }

    fun onApplyPrecompiledStyleSheet(precompiled: PrecompiledStyleSheet, widthPx: Int) {
        precompiledStyleSheet = precompiled
        themeStyleSheet = precompiled.styleSheet
        if (widthPx > 0) {
            viewportWidthPx = widthPx
        }
        currentFragment?.onResize(viewportWidthPx, viewportHeightPx)
    }

    fun onSaveInstanceState(outState: FragmentBundle) {
        if (currentRoute.isNotEmpty()) {
            outState.putString("fragmentHost.currentRoute", currentRoute)
        }
        outState.putInt("fragmentHost.currentHistoryIndex", currentHistoryIndex)
        currentFragment?.onSaveInstanceState(outState)
    }

    fun onRestoreInstanceState(savedState: FragmentBundle) {
        val savedRoute = savedState.getString("fragmentHost.currentRoute")
        if (!savedRoute.isNullOrEmpty() && currentFragment == null && routes.containsKey(savedRoute)) {
            navigateToRoute(savedRoute, savedState, addToBackStack = false)
        } else {
            currentFragment?.onRestoreInstanceState(savedState)
        }
    }

    private fun clearAndDestroyStack(stack: MutableList<HistoryStackEntry>) {
        for (entry in stack) {
            val frag = entry.fragment
            if (frag != null && frag !== currentFragment && frag.state != FragmentState.DESTROYED) {
                frag.performDestroy()
            }
            entry.fragment = null
        }
        stack.clear()
    }

    private fun notifyNavigationStateChanged() {
        val winTitle = resolveWindowTitle(currentFragment)
        if (winTitle.isNotBlank()) {
            resolveHostingIWindow()?.setTitle(winTitle) ?: resolveAttachedWindow()?.setTitle(winTitle)
        }
        onNavigationStateChanged?.invoke()
    }

    override fun dispose() {
        historyBridge.setOnPopStateListener(null)
        historyBridge.dispose()
        transitionController.dispose()
        clearAndDestroyStack(backStack)
        clearAndDestroyStack(forwardStack)
        historyEntriesByIndex.clear()
        currentFragment?.let {
            if (it.state != FragmentState.DESTROYED) {
                it.performDestroy()
            }
        }
        currentFragment = null
    }
}
