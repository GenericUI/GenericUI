package com.nexustools.genericui.component.fragment

import kotlin.reflect.KClass

/**
 * Multiplatform registry mapping [KClass] of [IFragment] implementations to factory functions so
 * `launchFragment(MyFragment::class, launchArgs)` works across Native, JVM, Android, and Web targets.
 *
 * Because Kotlin/Native, JS, and Wasm do not provide `KClass.createInstance()` reflection, fragments
 * launched by class must either be registered explicitly via [register] / [registerNoArg], or be
 * instantiable through the optional platform [fallbackInstantiator] (e.g. JVM/Android constructor reflection).
 */
object FragmentRegistry {
    private val factories = mutableMapOf<KClass<out IFragment>, (FragmentBundle?) -> IFragment>()

    /** Optional platform fallback instantiator (e.g., JVM/Android constructor reflection). */
    var fallbackInstantiator: ((KClass<out IFragment>, FragmentBundle?) -> IFragment?)? = null

    /** Registers a [factory] receiving the launch arguments for [fragmentClass]. */
    fun <T : IFragment> register(fragmentClass: KClass<T>, factory: (FragmentBundle?) -> T) {
        factories[fragmentClass] = factory
    }

    /** Registers a no-argument [noArgFactory] for [fragmentClass]. */
    fun <T : IFragment> registerNoArg(fragmentClass: KClass<T>, noArgFactory: () -> T) {
        factories[fragmentClass] = { _ -> noArgFactory() }
    }

    /** Reified convenience for [register]. */
    inline fun <reified T : IFragment> register(noinline factory: (FragmentBundle?) -> T) {
        register(T::class, factory)
    }

    /** Reified convenience for [registerNoArg]. */
    inline fun <reified T : IFragment> registerNoArg(noinline factory: () -> T) {
        registerNoArg(T::class, factory)
    }

    /** Removes the registration for [fragmentClass], if any. */
    fun unregister(fragmentClass: KClass<out IFragment>) {
        factories.remove(fragmentClass)
    }

    /** `true` if [fragmentClass] has an explicit factory registration. */
    fun isRegistered(fragmentClass: KClass<out IFragment>): Boolean =
        factories.containsKey(fragmentClass)

    /** `true` if [fragmentClass] is registered or a [fallbackInstantiator] is installed. */
    fun canInstantiate(fragmentClass: KClass<out IFragment>): Boolean =
        isRegistered(fragmentClass) || fallbackInstantiator != null

    /**
     * Creates a new instance of [fragmentClass] and assigns [IFragment.arguments] = [launchArgs].
     *
     * @throws IllegalStateException if the class is neither registered nor instantiable by the
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
        throw IllegalStateException(
            "Fragment class ${fragmentClass.simpleName} is not registered in FragmentRegistry. " +
                "Register it via FragmentRegistry.register(${fragmentClass.simpleName}::class) { ... } " +
                "before calling launchFragment(${fragmentClass.simpleName}::class, launchArgs)."
        )
    }

    /** Removes all registrations (does not reset [fallbackInstantiator]). */
    fun clear() {
        factories.clear()
    }
}
