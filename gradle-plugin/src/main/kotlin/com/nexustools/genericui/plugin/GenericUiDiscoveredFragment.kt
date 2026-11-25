package com.nexustools.genericui.plugin

/**
 * Metadata for a `@MainAppFragment`, `@AppFragment`, or `@AppApplication` class discovered by
 * [GenericUiFragmentScanner] in project Kotlin sources.
 */
data class GenericUiDiscoveredFragment(
    val packageName: String,
    val simpleName: String,
    val qualifiedName: String,
    val isMainFragment: Boolean,
    val targets: List<GenericUiVariantScope>,
    val label: String = "",
    val route: String = "",
    val exported: Boolean = isMainFragment,
    val screenOrientation: String = "",
    val hasNoArgConstructor: Boolean = true,
    val sourceFilePath: String = "",
    val isApplicationClass: Boolean = false,
    val annotations: Set<String> = emptySet(),
    val multipleWindows: Boolean = false,
    val dialog: Boolean = false,
    val hasIllegalMainDialogFlag: Boolean = false
) {
    /**
     * Simple class name of the 1:1 Android `AppCompatActivity` generated for this fragment
     * (`<SimpleName>Activity`, or `<SimpleName>HostActivity` if [simpleName] already ends with `"Activity"`).
     */
    val generatedActivitySimpleName: String
        get() = if (simpleName.endsWith("Activity")) "${simpleName}HostActivity" else "${simpleName}Activity"

    /**
     * Fully qualified class name of the 1:1 Android `AppCompatActivity` generated for this fragment.
     */
    val generatedActivityQualifiedName: String
        get() = if (packageName.isEmpty()) generatedActivitySimpleName else "$packageName.$generatedActivitySimpleName"

    /**
     * Returns `true` if this discovered class carries an annotation matching [simpleOrQualifiedName]
     * (ignoring an optional leading `'@'`, case-insensitively across simple or qualified names).
     */
    fun hasAnnotation(simpleOrQualifiedName: String): Boolean {
        val normalizedTarget = simpleOrQualifiedName.trim().removePrefix("@").trim()
        if (normalizedTarget.isEmpty() || annotations.isEmpty()) return false
        val targetSimple = normalizedTarget.substringAfterLast('.')
        val targetIsQualified = '.' in normalizedTarget
        return annotations.any { raw ->
            val candidate = raw.trim().removePrefix("@").trim()
            if (candidate.isEmpty()) {
                false
            } else if (candidate.equals(normalizedTarget, ignoreCase = true)) {
                true
            } else if (targetIsQualified) {
                if ('.' in candidate) {
                    candidate.equals(normalizedTarget, ignoreCase = true)
                } else {
                    candidate.equals(targetSimple, ignoreCase = true)
                }
            } else {
                candidate.substringAfterLast('.').equals(normalizedTarget, ignoreCase = true) ||
                    candidate.endsWith(".$normalizedTarget", ignoreCase = true)
            }
        }
    }
}
