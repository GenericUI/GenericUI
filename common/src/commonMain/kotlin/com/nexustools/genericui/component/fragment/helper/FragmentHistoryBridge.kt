package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.core.Disposable

/**
 * Platform history bridge used by [FragmentHostSystem] to synchronize fragment navigation
 * with native browser URL `#hashes` (`window.location.hash`) and page history state
 * (`window.history.pushState` / `replaceState` / `popstate`) on `:web`.
 *
 * Non-web backends use the default no-op implementation unless a custom history bridge is installed.
 */
interface FragmentHistoryBridge : Disposable {
    /** Whether this bridge actively synchronizes with a platform history stack (e.g. browser History API). */
    val isSupported: Boolean
        get() = false

    /** Returns the current route token from the URL `#hash` (without leading `#`), or `null` if none. */
    fun currentRouteHash(): String? = null

    /** Returns the current history entry index stored in the native history state, or `null` if none. */
    fun currentHistoryIndex(): Int? = null

    /** Restores the [FragmentBundle] stored in the current native history entry, or `null` if none. */
    fun currentSavedState(): FragmentBundle? = null

    /**
     * Pushes a new entry onto the platform history stack with `#route` and serialized [state].
     */
    fun pushState(route: String, historyIndex: Int, title: String, state: FragmentBundle?) {}

    /**
     * Replaces the current entry in the platform history stack with `#route` and serialized [state].
     */
    fun replaceState(route: String, historyIndex: Int, title: String, state: FragmentBundle?) {}

    /** Navigates one step back in the platform history stack if supported. */
    fun goBack(): Boolean = false

    /** Navigates one step forward in the platform history stack if supported. */
    fun goForward(): Boolean = false

    /**
     * Registers a callback invoked when the user triggers native Back/Forward navigation (`popstate` / `hashchange`).
     */
    fun setOnPopStateListener(
        listener: ((route: String, historyIndex: Int?, restoredState: FragmentBundle?) -> Unit)?
    ) {}

    override fun dispose() {}

    companion object {
        /** Active platform factory for creating [FragmentHistoryBridge] instances (installed by `:web`). */
        var factory: (() -> FragmentHistoryBridge)? = null

        private var inMemoryRouteHash: String = ""
        private var inMemoryHistoryIndex: Int = 0
        private var inMemorySavedState: FragmentBundle? = null
        private val inMemoryListeners = mutableListOf<(route: String, historyIndex: Int?, restoredState: FragmentBundle?) -> Unit>()

        fun resetInMemoryHistoryForTesting() {
            inMemoryRouteHash = ""
            inMemoryHistoryIndex = 0
            inMemorySavedState = null
            inMemoryListeners.clear()
        }

        fun currentHashRoute(): String = inMemoryRouteHash

        fun currentSavedStateForTesting(): FragmentBundle? = inMemorySavedState

        fun simulatePopState(
            route: String,
            stateBundle: FragmentBundle? = null,
            index: Int? = null
        ) {
            val norm = route.trim().removePrefix("#").trim()
            inMemoryRouteHash = norm
            if (index != null) {
                inMemoryHistoryIndex = index
            }
            if (stateBundle != null) {
                inMemorySavedState = FragmentBundle().apply { putAll(stateBundle) }
            }
            val snapshot = inMemoryListeners.toList()
            for (listener in snapshot) {
                listener(norm, index, inMemorySavedState)
            }
        }

        fun create(): FragmentHistoryBridge = factory?.invoke() ?: object : FragmentHistoryBridge {
            private var listener: ((route: String, historyIndex: Int?, restoredState: FragmentBundle?) -> Unit)? = null

            override val isSupported: Boolean
                get() = true

            override fun currentRouteHash(): String? = inMemoryRouteHash.takeIf { it.isNotEmpty() }

            override fun currentHistoryIndex(): Int? = inMemoryHistoryIndex

            override fun currentSavedState(): FragmentBundle? = inMemorySavedState

            override fun pushState(route: String, historyIndex: Int, title: String, state: FragmentBundle?) {
                inMemoryRouteHash = route.trim().removePrefix("#").trim()
                inMemoryHistoryIndex = historyIndex
                inMemorySavedState = state?.let { FragmentBundle().apply { putAll(it) } }
            }

            override fun replaceState(route: String, historyIndex: Int, title: String, state: FragmentBundle?) {
                inMemoryRouteHash = route.trim().removePrefix("#").trim()
                inMemoryHistoryIndex = historyIndex
                inMemorySavedState = state?.let { FragmentBundle().apply { putAll(it) } }
            }

            override fun setOnPopStateListener(
                listener: ((route: String, historyIndex: Int?, restoredState: FragmentBundle?) -> Unit)?
            ) {
                this.listener?.let { inMemoryListeners.remove(it) }
                this.listener = listener
                if (listener != null) {
                    inMemoryListeners.add(listener)
                }
            }

            override fun dispose() {
                this.listener?.let { inMemoryListeners.remove(it) }
                this.listener = null
            }
        }
    }
}
