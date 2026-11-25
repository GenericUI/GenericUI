package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.exception.FragmentNotRegisteredException
import kotlin.reflect.KClass

/**
 * Multiplatform registry mapping [KClass] of [IFragment] implementations to factory functions so
 * `startFragment(MyFragment::class, launchArgs)` works across Native, JVM, Android, and Web targets.
 *
 * Because Kotlin/Native, JS, and Wasm do not provide `KClass.createInstance()` reflection, fragments
 * launched by class must either be registered explicitly via [register] / [registerNoArg], or be
 * instantiable through the optional platform [fallbackInstantiator] (e.g. JVM/Android constructor reflection).
 */
object FragmentRegistry {
    private val factories = mutableMapOf<KClass<out IFragment>, (FragmentBundle?) -> IFragment>()
    private val metadataByClass = mutableMapOf<KClass<out IFragment>, FragmentMetadata>()
    private val fragmentIdByClass = mutableMapOf<KClass<out IFragment>, String>()
    private val routeByClass = mutableMapOf<KClass<out IFragment>, String>()
    private val classByFragmentId = mutableMapOf<String, KClass<out IFragment>>()
    private val classByRoute = mutableMapOf<String, KClass<out IFragment>>()

    /** Optional platform fallback instantiator (e.g., JVM/Android constructor reflection). */
    var fallbackInstantiator: ((KClass<out IFragment>, FragmentBundle?) -> IFragment?)? = null

    /** Registers full [FragmentMetadata] for [fragmentClass]. */
    fun <T : IFragment> registerMetadata(
        fragmentClass: KClass<T>,
        metadata: FragmentMetadata
    ) {
        val normalizedId = metadata.fragmentId.trim()
        if (normalizedId.isNotEmpty()) {
            fragmentIdByClass[fragmentClass]?.let { oldId ->
                if (classByFragmentId[oldId] == fragmentClass) {
                    classByFragmentId.remove(oldId)
                }
            }
            fragmentIdByClass[fragmentClass] = normalizedId
            classByFragmentId[normalizedId] = fragmentClass
        }

        val normalizedRoute = metadata.route.trim()
        routeByClass[fragmentClass]?.let { oldRoute ->
            if (oldRoute.isNotEmpty() && oldRoute != normalizedRoute && classByRoute[oldRoute] == fragmentClass) {
                classByRoute.remove(oldRoute)
            }
        }
        if (normalizedRoute.isNotEmpty()) {
            routeByClass[fragmentClass] = normalizedRoute
            classByRoute[normalizedRoute] = fragmentClass
        } else {
            routeByClass.remove(fragmentClass)
        }

        val resolvedId = normalizedId.ifEmpty {
            metadataByClass[fragmentClass]?.fragmentId
                ?: fragmentIdByClass[fragmentClass]
                ?: resolveFragmentId(fragmentClass)
        }
        val normalizedDialog = if (metadata.isMainFragment) false else metadata.dialog
        metadataByClass[fragmentClass] = FragmentMetadata(
            fragmentId = resolvedId,
            route = normalizedRoute,
            label = metadata.label.trim(),
            isMainFragment = metadata.isMainFragment,
            multipleWindows = metadata.multipleWindows,
            dialog = normalizedDialog
        )
    }

    /** Registers canonical [fragmentId] and optional metadata flags for [fragmentClass]. */
    fun <T : IFragment> registerMetadata(
        fragmentClass: KClass<T>,
        fragmentId: String,
        route: String = "",
        label: String = "",
        isMainFragment: Boolean = false,
        multipleWindows: Boolean = false,
        dialog: Boolean = false
    ) {
        registerMetadata(
            fragmentClass = fragmentClass,
            metadata = FragmentMetadata(
                fragmentId = fragmentId,
                route = route,
                label = label,
                isMainFragment = isMainFragment,
                multipleWindows = multipleWindows,
                dialog = if (isMainFragment) false else dialog
            )
        )
    }

    /** Registers a [factory] receiving the launch arguments for [fragmentClass]. */
    fun <T : IFragment> register(
        fragmentClass: KClass<T>,
        factory: (FragmentBundle?) -> T
    ) {
        factories[fragmentClass] = factory
    }

    /** Registers a [factory] receiving the launch arguments for [fragmentClass], with optional metadata flags. */
    fun <T : IFragment> register(
        fragmentClass: KClass<T>,
        multipleWindows: Boolean? = null,
        dialog: Boolean? = null,
        label: String? = null,
        isMainFragment: Boolean? = null,
        factory: (FragmentBundle?) -> T
    ) {
        factories[fragmentClass] = factory
        if (multipleWindows != null || dialog != null || label != null || isMainFragment != null) {
            val existing = metadataByClass[fragmentClass]
            val resolvedMain = isMainFragment ?: existing?.isMainFragment ?: false
            registerMetadata(
                fragmentClass = fragmentClass,
                metadata = FragmentMetadata(
                    fragmentId = existing?.fragmentId ?: fragmentIdByClass[fragmentClass] ?: resolveFragmentId(fragmentClass),
                    route = existing?.route ?: routeByClass[fragmentClass].orEmpty(),
                    label = label ?: existing?.label.orEmpty(),
                    isMainFragment = resolvedMain,
                    multipleWindows = multipleWindows ?: existing?.multipleWindows ?: false,
                    dialog = if (resolvedMain) false else (dialog ?: existing?.dialog ?: false)
                )
            )
        }
    }

    /** Registers [fragmentId], optional [route], and [factory] for [fragmentClass]. */
    fun <T : IFragment> register(
        fragmentClass: KClass<T>,
        fragmentId: String,
        route: String = "",
        factory: (FragmentBundle?) -> T
    ) {
        registerMetadata(fragmentClass, fragmentId, route)
        factories[fragmentClass] = factory
    }

    /** Registers [fragmentId], optional metadata, and [factory] for [fragmentClass]. */
    fun <T : IFragment> register(
        fragmentClass: KClass<T>,
        fragmentId: String,
        route: String = "",
        multipleWindows: Boolean = false,
        dialog: Boolean = false,
        label: String = "",
        isMainFragment: Boolean = false,
        factory: (FragmentBundle?) -> T
    ) {
        registerMetadata(
            fragmentClass = fragmentClass,
            fragmentId = fragmentId,
            route = route,
            label = label,
            isMainFragment = isMainFragment,
            multipleWindows = multipleWindows,
            dialog = dialog
        )
        factories[fragmentClass] = factory
    }

    /** Registers a no-argument [noArgFactory] for [fragmentClass]. */
    fun <T : IFragment> registerNoArg(
        fragmentClass: KClass<T>,
        noArgFactory: () -> T
    ) {
        register(fragmentClass) { _ -> noArgFactory() }
    }

    /** Registers a no-argument [noArgFactory] for [fragmentClass], with optional metadata flags. */
    fun <T : IFragment> registerNoArg(
        fragmentClass: KClass<T>,
        multipleWindows: Boolean? = null,
        dialog: Boolean? = null,
        label: String? = null,
        isMainFragment: Boolean? = null,
        noArgFactory: () -> T
    ) {
        register(
            fragmentClass = fragmentClass,
            multipleWindows = multipleWindows,
            dialog = dialog,
            label = label,
            isMainFragment = isMainFragment
        ) { _ -> noArgFactory() }
    }

    /** Reified convenience for [register]. */
    inline fun <reified T : IFragment> register(
        noinline factory: (FragmentBundle?) -> T
    ) {
        register(T::class, factory)
    }

    /** Reified convenience for [register] with metadata flags. */
    inline fun <reified T : IFragment> register(
        multipleWindows: Boolean? = null,
        dialog: Boolean? = null,
        label: String? = null,
        isMainFragment: Boolean? = null,
        noinline factory: (FragmentBundle?) -> T
    ) {
        register(T::class, multipleWindows, dialog, label, isMainFragment, factory)
    }

    /** Reified convenience for [registerNoArg]. */
    inline fun <reified T : IFragment> registerNoArg(
        noinline noArgFactory: () -> T
    ) {
        registerNoArg(T::class, noArgFactory)
    }

    /** Reified convenience for [registerNoArg] with metadata flags. */
    inline fun <reified T : IFragment> registerNoArg(
        multipleWindows: Boolean? = null,
        dialog: Boolean? = null,
        label: String? = null,
        isMainFragment: Boolean? = null,
        noinline noArgFactory: () -> T
    ) {
        registerNoArg(T::class, multipleWindows, dialog, label, isMainFragment, noArgFactory)
    }

    /** Removes the registration and metadata for [fragmentClass], if any. */
    fun unregister(fragmentClass: KClass<out IFragment>) {
        factories.remove(fragmentClass)
        metadataByClass.remove(fragmentClass)
        fragmentIdByClass.remove(fragmentClass)?.let { id ->
            if (classByFragmentId[id] == fragmentClass) {
                classByFragmentId.remove(id)
            }
        }
        routeByClass.remove(fragmentClass)?.let { route ->
            if (classByRoute[route] == fragmentClass) {
                classByRoute.remove(route)
            }
        }
    }

    /** Returns registered [FragmentMetadata] for [fragmentClass], or `null` if none was registered. */
    fun getMetadata(fragmentClass: KClass<out IFragment>): FragmentMetadata? =
        metadataByClass[fragmentClass]

    /** Looks up registered [FragmentMetadata] by canonical fragment ID, route, or unambiguous simple name. */
    fun getMetadataByIdOrRoute(query: String): FragmentMetadata? =
        resolveAnyFragmentClass(query)?.let { metadataByClass[it] }

    /** `true` if [fragmentClass] explicitly allows opening multiple concurrent windows (`multipleWindows = true`). */
    fun supportsMultipleWindows(fragmentClass: KClass<out IFragment>): Boolean =
        metadataByClass[fragmentClass]?.multipleWindows == true

    /** `true` if [fragmentClass] is a secondary fragment configured with `dialog = true`. */
    fun isDialogFragment(fragmentClass: KClass<out IFragment>): Boolean =
        metadataByClass[fragmentClass]?.let { !it.isMainFragment && it.dialog } == true

    /** `true` if [fragmentClass] has an explicit factory registration. */
    fun isRegistered(fragmentClass: KClass<out IFragment>): Boolean =
        factories.containsKey(fragmentClass)

    /** `true` if [fragmentClass] is registered or a [fallbackInstantiator] is installed. */
    fun canInstantiate(fragmentClass: KClass<out IFragment>): Boolean =
        isRegistered(fragmentClass) || fallbackInstantiator != null

    /**
     * Resolves canonical fragment ID for [fragmentClass]: registered ID -> `qualifiedName` ->
     * `simpleName` -> `"Fragment"`.
     */
    fun resolveFragmentId(fragmentClass: KClass<out IFragment>): String {
        fragmentIdByClass[fragmentClass]?.let { return it }
        val qualifiedFallback = resolveQualifiedNameFallback(fragmentClass)
        if (!qualifiedFallback.isNullOrBlank()) return qualifiedFallback
        val simple = runCatching { fragmentClass.simpleName }.getOrNull()
        if (!simple.isNullOrBlank()) return simple
        return "Fragment"
    }

    /** Resolves the registered route for [fragmentClass], or `null` if none was registered. */
    fun resolveRoute(fragmentClass: KClass<out IFragment>): String? =
        routeByClass[fragmentClass]?.takeIf { it.isNotEmpty() }

    /**
     * Resolves a [Fragment] class matching [fragmentIdOrRoute] by:
     * 1. Exact registered fragment ID
     * 2. Exact registered route
     * 3. Stripped leading `/` route (when unambiguous)
     * 4. Unambiguous simple-name / `.fragmentId` suffix match across registered fragments
     */
    @Suppress("UNCHECKED_CAST")
    fun resolveFragmentClass(fragmentIdOrRoute: String): KClass<out Fragment>? =
        resolveAnyFragmentClass(fragmentIdOrRoute) as? KClass<out Fragment>

    private fun resolveAnyFragmentClass(fragmentIdOrRoute: String): KClass<out IFragment>? {
        val query = fragmentIdOrRoute.trim()
        if (query.isEmpty()) return null

        classByFragmentId[query]?.let { return it }
        classByRoute[query]?.let { return it }

        val strippedQuery = query.trimStart('/')
        if (strippedQuery.isNotEmpty()) {
            classByRoute[strippedQuery]?.let { return it }
            val routeMatches = routeByClass.entries
                .filter { (_, registeredRoute) -> registeredRoute.trimStart('/') == strippedQuery }
                .map { it.key }
            if (routeMatches.size == 1) {
                return routeMatches.first()
            }
        }

        val suffixMatches = linkedSetOf<KClass<out IFragment>>()
        val dotSuffix = ".$query"
        for ((registeredId, cls) in classByFragmentId) {
            if (registeredId == query || registeredId.endsWith(dotSuffix)) {
                suffixMatches.add(cls)
            }
        }
        val knownClasses = linkedSetOf<KClass<out IFragment>>().apply {
            addAll(factories.keys)
            addAll(metadataByClass.keys)
            addAll(fragmentIdByClass.keys)
            addAll(routeByClass.keys)
        }
        for (cls in knownClasses) {
            val simple = runCatching { cls.simpleName }.getOrNull()
            val qualified = resolveQualifiedNameFallback(cls)
            if (simple == query || qualified == query || (qualified != null && qualified.endsWith(dotSuffix))) {
                suffixMatches.add(cls)
            }
        }
        return if (suffixMatches.size == 1) {
            suffixMatches.first()
        } else {
            null
        }
    }

    /**
     * Resolves [fragmentIdOrRoute] via [resolveFragmentClass] and instantiates it with [args]
     * when a factory (or [fallbackInstantiator]) is available, returning `null` otherwise.
     */
    @Suppress("UNCHECKED_CAST")
    fun instantiateByIdOrRoute(fragmentIdOrRoute: String, args: FragmentBundle? = null): Fragment? {
        val fragmentClass = resolveFragmentClass(fragmentIdOrRoute) ?: return null
        if (!canInstantiate(fragmentClass)) return null
        return instantiate(fragmentClass as KClass<Fragment>, args)
    }

    /**
     * Creates a new instance of [fragmentClass] and assigns [IFragment.arguments] = [launchArgs].
     *
     * @throws FragmentNotRegisteredException if the class is neither registered nor instantiable by the
     *   [fallbackInstantiator].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : IFragment> instantiate(fragmentClass: KClass<T>, launchArgs: FragmentBundle? = null): T {
        val registered = factories[fragmentClass]
        if (registered != null) {
            return (registered(launchArgs) as T).also { it.arguments = launchArgs }
        }
        val reflected = fallbackInstantiator?.invoke(fragmentClass, launchArgs) as? T
        if (reflected != null) {
            return reflected.also { it.arguments = launchArgs }
        }
        val fragmentId = resolveFragmentId(fragmentClass)
        val label = runCatching { fragmentClass.simpleName }.getOrNull() ?: "Fragment"
        throw FragmentNotRegisteredException(
            fragmentId = fragmentId,
            message = "Fragment class '$label' (id '$fragmentId') is not registered in FragmentRegistry. " +
                "Register it via FragmentRegistry.register($label::class) { ... } " +
                "before calling startFragment($label::class, launchArgs)."
        )
    }

    /** Removes all registrations and metadata (does not reset [fallbackInstantiator]). */
    fun clear() {
        factories.clear()
        metadataByClass.clear()
        fragmentIdByClass.clear()
        routeByClass.clear()
        classByFragmentId.clear()
        classByRoute.clear()
        FragmentLaunchCoordinator.resetForTests()
    }

    private fun resolveQualifiedNameFallback(fragmentClass: KClass<*>): String? {
        val raw = runCatching { fragmentClass.toString() }.getOrNull()?.trim().orEmpty()
        val trimmed = raw
            .removePrefix("class ")
            .removePrefix("interface ")
            .trim()
        if (trimmed.isEmpty() || ' ' in trimmed || '$' in trimmed || '.' !in trimmed) {
            return null
        }
        return trimmed
    }
}
