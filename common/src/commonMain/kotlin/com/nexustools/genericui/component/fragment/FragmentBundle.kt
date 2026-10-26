package com.nexustools.genericui.component.fragment

/**
 * Key-value state bundle for saving and restoring [Fragment] instance state (`onSaveInstanceState` / `onCreate`).
 * Directly mirrors Android's `Bundle` across all 8 backends.
 */
class FragmentBundle {
    private val map = linkedMapOf<String, Any?>()

    fun putString(key: String, value: String?) { map[key] = value }
    fun getString(key: String, defaultValue: String? = null): String? = map[key] as? String ?: defaultValue

    fun putInt(key: String, value: Int) { map[key] = value }
    fun getInt(key: String, defaultValue: Int = 0): Int = map[key] as? Int ?: defaultValue

    fun putLong(key: String, value: Long) { map[key] = value }
    fun getLong(key: String, defaultValue: Long = 0L): Long = map[key] as? Long ?: defaultValue

    fun putDouble(key: String, value: Double) { map[key] = value }
    fun getDouble(key: String, defaultValue: Double = 0.0): Double = map[key] as? Double ?: defaultValue

    fun putBoolean(key: String, value: Boolean) { map[key] = value }
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = map[key] as? Boolean ?: defaultValue

    fun containsKey(key: String): Boolean = map.containsKey(key)
    fun keys(): Set<String> = map.keys.toSet()
    fun clear() = map.clear()

    /**
     * Stores an arbitrary [value] under [key]. Values of type `String`, `Int`, `Long`, `Double`, and `Boolean`
     * remain readable through the typed getters ([getString], [getInt], ...); any other type is stored as-is
     * and readable via [get].
     */
    fun put(key: String, value: Any?) { map[key] = value }

    /** Returns the raw value stored under [key], or `null` if absent. */
    operator fun get(key: String): Any? = map[key]

    /** Copies every entry of [other] into this bundle (overwriting existing keys). */
    fun putAll(other: FragmentBundle) {
        map.putAll(other.map)
    }

    /** Returns a read-only snapshot of all entries in this bundle. */
    fun toMap(): Map<String, Any?> = LinkedHashMap(map)

    /** Returns `true` if command-line arguments have been stored under [KEY_COMMAND_LINE_ARGS]. */
    fun hasCommandLineArgs(): Boolean = containsKey(KEY_COMMAND_LINE_ARGS)

    /**
     * Returns the command-line arguments stored under [KEY_COMMAND_LINE_ARGS] as a [List] of [String]s,
     * or [emptyList] if none were provided.
     */
    fun getCommandLineArgs(): List<String> = when (val raw = map[KEY_COMMAND_LINE_ARGS]) {
        is List<*> -> raw.filterIsInstance<String>()
        is Array<*> -> raw.filterIsInstance<String>()
        is String -> if (raw.isEmpty()) emptyList() else listOf(raw)
        else -> emptyList()
    }

    /** Returns the command-line arguments stored under [KEY_COMMAND_LINE_ARGS] as a typed [Array] of [String]s. */
    fun getCommandLineArgsArray(): Array<String> = getCommandLineArgs().toTypedArray()

    /** Stores [args] under [KEY_COMMAND_LINE_ARGS] for the initial `@MainAppFragment`. */
    fun putCommandLineArgs(args: Array<String>) {
        map[KEY_COMMAND_LINE_ARGS] = args.toList()
    }

    /** Stores [args] under [KEY_COMMAND_LINE_ARGS] for the initial `@MainAppFragment`. */
    fun putCommandLineArgs(args: List<String>) {
        map[KEY_COMMAND_LINE_ARGS] = args.toList()
    }

    companion object {
        /** Canonical bundle key under which `main(args)` command-line arguments are passed to the initial `@MainAppFragment`. */
        const val KEY_COMMAND_LINE_ARGS: String = "genericui:command_line_args"

        /**
         * Creates a [FragmentBundle] populated with [args] under [KEY_COMMAND_LINE_ARGS].
         */
        fun fromCommandLineArgs(args: Array<String>): FragmentBundle =
            FragmentBundle().apply { putCommandLineArgs(args) }

        /**
         * Creates a [FragmentBundle] populated with [args] under [KEY_COMMAND_LINE_ARGS].
         */
        fun fromCommandLineArgs(args: List<String>): FragmentBundle =
            FragmentBundle().apply { putCommandLineArgs(args) }

        /**
         * Creates a bundle from key-value [pairs], e.g. `FragmentBundle.of("id" to 42, "title" to "Details")`.
         * Values are stored as-is (see [put]); `String`/`Int`/`Long`/`Double`/`Boolean` values remain readable
         * through the typed getters.
         */
        fun of(vararg pairs: Pair<String, Any?>): FragmentBundle {
            val bundle = FragmentBundle()
            for ((key, value) in pairs) {
                bundle.map[key] = value
            }
            return bundle
        }
    }
}
