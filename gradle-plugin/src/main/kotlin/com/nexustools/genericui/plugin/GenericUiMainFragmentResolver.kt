package com.nexustools.genericui.plugin

/**
 * Resolves the single winning `@MainAppFragment` and all applicable `@AppFragment` / `@MainAppFragment`
 * classes for each enabled [GenericUiVariantScope] backend target using specificity ranking (`5 -> 0`).
 *
 * Enforces:
 * 1. At least one `@MainAppFragment` matches the target backend.
 * 2. Exactly one `@MainAppFragment` wins at the highest matching `specificityRank` (ambiguous ties fail the build).
 * 3. Every resolved `@MainAppFragment` and `@AppFragment` has a no-arg (or all-default) constructor.
 */
object GenericUiMainFragmentResolver {

    private data class RankedCandidate(
        val fragment: GenericUiDiscoveredFragment,
        val matchingRank: Int,
        val matchingTargets: List<GenericUiVariantScope>
    )

    /**
     * Resolves the winning `@MainAppFragment` for [targetBackend].
     *
     * @throws IllegalStateException if no `@MainAppFragment` matches [targetBackend], if multiple
     *   `@MainAppFragment` classes tie at the highest specificity rank for [targetBackend], or if
     *   the winning fragment class lacks a no-arg constructor.
     */
    fun resolveMainFragmentForBackend(
        discoveredFragments: List<GenericUiDiscoveredFragment>,
        targetBackend: GenericUiVariantScope
    ): GenericUiDiscoveredFragment {
        val mainCandidates = discoveredFragments
            .filter { it.isMainFragment && !it.isApplicationClass }
            .mapNotNull { fragment ->
                val matching = fragment.targets.filter { it.matchesBackend(targetBackend) }
                val maxRank = matching.maxOfOrNull { it.specificityRank } ?: return@mapNotNull null
                RankedCandidate(
                    fragment = fragment,
                    matchingRank = maxRank,
                    matchingTargets = matching
                )
            }

        if (mainCandidates.isEmpty()) {
            throw IllegalStateException(
                "No @MainAppFragment found matching target backend '${targetBackend.id}'. " +
                    "Annotate a Fragment subclass with @MainAppFragment(targets = [AppTarget.COMMON]) " +
                    "or a scope matching '${targetBackend.id}'."
            )
        }

        val highestRank = mainCandidates.maxOf { it.matchingRank }
        val winners = mainCandidates.filter { it.matchingRank == highestRank }

        if (winners.size > 1) {
            val conflictDetails = winners.joinToString("; ") { candidate ->
                val targetNames = candidate.fragment.targets.joinToString(", ") { "AppTarget.${it.name}" }
                "${candidate.fragment.qualifiedName} (targets = [$targetNames])"
            }
            throw IllegalStateException(
                "Conflicting @MainAppFragment declarations for target backend '${targetBackend.id}' " +
                    "at specificity rank $highestRank: $conflictDetails. " +
                    "Assign distinct AppTarget scopes so each enabled backend resolves a single most-specific @MainAppFragment."
            )
        }

        val winningFragment = winners.single().fragment
        validateNoArgConstructor(winningFragment)
        return winningFragment
    }

    /**
     * Returns all `@MainAppFragment` and `@AppFragment` classes whose `targets` match [targetBackend],
     * validating that every applicable fragment has a no-arg (or all-default) constructor.
     */
    fun resolveApplicableFragmentsForBackend(
        discoveredFragments: List<GenericUiDiscoveredFragment>,
        targetBackend: GenericUiVariantScope
    ): List<GenericUiDiscoveredFragment> {
        val applicable = discoveredFragments.filter { fragment ->
            !fragment.isApplicationClass && fragment.targets.any { it.matchesBackend(targetBackend) }
        }
        for (fragment in applicable) {
            validateNoArgConstructor(fragment)
        }
        return applicable
    }

    /**
     * Resolves the winning `@MainAppFragment` for every backend in [enabledBackends].
     */
    fun resolveAllBackends(
        discoveredFragments: List<GenericUiDiscoveredFragment>,
        enabledBackends: Iterable<GenericUiVariantScope> = GenericUiVariantScope.ALL_BACKENDS
    ): Map<GenericUiVariantScope, GenericUiDiscoveredFragment> {
        val result = LinkedHashMap<GenericUiVariantScope, GenericUiDiscoveredFragment>()
        for (backend in enabledBackends) {
            result[backend] = resolveMainFragmentForBackend(discoveredFragments, backend)
        }
        return result
    }

    /**
     * Resolves an optional `@AppApplication` class for [targetBackend] using the same specificity rules,
     * or returns `null` if no `@AppApplication` is declared for [targetBackend].
     */
    fun resolveApplicationForBackendOrNull(
        discoveredFragments: List<GenericUiDiscoveredFragment>,
        targetBackend: GenericUiVariantScope
    ): GenericUiDiscoveredFragment? {
        val appCandidates = discoveredFragments
            .filter { it.isApplicationClass }
            .mapNotNull { appClass ->
                val matching = appClass.targets.filter { it.matchesBackend(targetBackend) }
                val maxRank = matching.maxOfOrNull { it.specificityRank } ?: return@mapNotNull null
                RankedCandidate(
                    fragment = appClass,
                    matchingRank = maxRank,
                    matchingTargets = matching
                )
            }
        if (appCandidates.isEmpty()) return null
        val highestRank = appCandidates.maxOf { it.matchingRank }
        val winners = appCandidates.filter { it.matchingRank == highestRank }
        if (winners.size > 1) {
            val conflictDetails = winners.joinToString("; ") { candidate ->
                val targetNames = candidate.fragment.targets.joinToString(", ") { "AppTarget.${it.name}" }
                "${candidate.fragment.qualifiedName} (targets = [$targetNames])"
            }
            throw IllegalStateException(
                "Conflicting @AppApplication declarations for target backend '${targetBackend.id}' " +
                    "at specificity rank $highestRank: $conflictDetails."
            )
        }
        val winner = winners.single().fragment
        validateNoArgConstructor(winner)
        return winner
    }

    private fun validateNoArgConstructor(fragment: GenericUiDiscoveredFragment) {
        if (!fragment.hasNoArgConstructor) {
            val annotationLabel = when {
                fragment.isApplicationClass -> "@AppApplication"
                fragment.isMainFragment -> "@MainAppFragment"
                else -> "@AppFragment"
            }
            throw IllegalStateException(
                "$annotationLabel class '${fragment.qualifiedName}' must declare a no-arg constructor " +
                    "(or default values for all primary constructor parameters) so GenericUI can instantiate it."
            )
        }
    }
}
