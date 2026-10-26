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
    val isApplicationClass: Boolean = false
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
}
