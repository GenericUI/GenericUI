package com.nexustools.genericui.plugin

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry of [AndroidActivityExtensionContributor] implementations used by
 * [GenericUiGenerateAndroidActivitiesTask] and [GenericUiAndroidManifestXmlGenerator].
 */
object AndroidActivityExtensionRegistry {

    private val registeredContributors = CopyOnWriteArrayList<AndroidActivityExtensionContributor>(
        listOf(PipAndroidActivityExtensionContributor)
    )

    /** Registers [contributor] if it is not already registered. */
    fun register(contributor: AndroidActivityExtensionContributor) {
        if (contributor !in registeredContributors) {
            registeredContributors.add(contributor)
        }
    }

    /** Unregisters [contributor] from this registry. */
    fun unregister(contributor: AndroidActivityExtensionContributor) {
        registeredContributors.remove(contributor)
    }

    /** Returns an immutable snapshot of all registered contributors. */
    fun allContributors(): List<AndroidActivityExtensionContributor> = registeredContributors.toList()

    /**
     * Returns all registered contributors whose [AndroidActivityExtensionContributor.appliesTo]
     * predicate returns `true` for [fragment] and [enabledExtensionPaths].
     */
    fun contributorsFor(
        fragment: GenericUiDiscoveredFragment,
        enabledExtensionPaths: Set<String>
    ): List<AndroidActivityExtensionContributor> =
        registeredContributors.filter { it.appliesTo(fragment, enabledExtensionPaths) }

    /**
     * Resolves all [AndroidActivityExtensionContribution] entries applicable to [fragment] given
     * [enabledExtensionPaths].
     */
    fun resolveContributions(
        fragment: GenericUiDiscoveredFragment,
        enabledExtensionPaths: Set<String>
    ): List<AndroidActivityExtensionContribution> =
        contributorsFor(fragment, enabledExtensionPaths).map { it.contribute(fragment) }
}
