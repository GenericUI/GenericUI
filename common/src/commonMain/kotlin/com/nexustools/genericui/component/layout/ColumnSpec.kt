package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.ResponsiveBreakpoint

/**
 * Bootstrap 5 responsive 12-column grid specification for a [Column] inside a [ColumnLayout].
 */
data class ColumnSpec(
    val span: Int? = null,
    val autoWidth: Boolean = false,
    val sm: Int? = null,
    val smAuto: Boolean = false,
    val md: Int? = null,
    val mdAuto: Boolean = false,
    val lg: Int? = null,
    val lgAuto: Boolean = false,
    val xl: Int? = null,
    val xlAuto: Boolean = false,
    val xxl: Int? = null,
    val xxlAuto: Boolean = false,
    val offset: Int? = null,
    val offsetSm: Int? = null,
    val offsetMd: Int? = null,
    val offsetLg: Int? = null,
    val offsetXl: Int? = null,
    val offsetXxl: Int? = null,
    val order: Int? = null,
    val orderSm: Int? = null,
    val orderMd: Int? = null,
    val orderLg: Int? = null,
    val orderXl: Int? = null,
    val orderXxl: Int? = null
) {
    /**
     * Generates the canonical Bootstrap 5 CSS classes (`.col`, `.col-auto`, `.col-1`..`.col-12`,
     * `.col-{sm,md,lg,xl,xxl}-{1..12|auto}`, `.offset-*`, `.order-*`) for this column specification.
     */
    fun toBootstrapClasses(): List<String> {
        val classes = mutableListOf<String>()
        val hasResponsiveTier = sm != null || smAuto ||
            md != null || mdAuto ||
            lg != null || lgAuto ||
            xl != null || xlAuto ||
            xxl != null || xxlAuto

        when {
            autoWidth -> classes.add("col-auto")
            span != null -> classes.add("col-${span.coerceIn(1, 12)}")
            !hasResponsiveTier -> classes.add("col")
        }

        when {
            smAuto -> classes.add("col-sm-auto")
            sm != null -> classes.add("col-sm-${sm.coerceIn(1, 12)}")
        }
        when {
            mdAuto -> classes.add("col-md-auto")
            md != null -> classes.add("col-md-${md.coerceIn(1, 12)}")
        }
        when {
            lgAuto -> classes.add("col-lg-auto")
            lg != null -> classes.add("col-lg-${lg.coerceIn(1, 12)}")
        }
        when {
            xlAuto -> classes.add("col-xl-auto")
            xl != null -> classes.add("col-xl-${xl.coerceIn(1, 12)}")
        }
        when {
            xxlAuto -> classes.add("col-xxl-auto")
            xxl != null -> classes.add("col-xxl-${xxl.coerceIn(1, 12)}")
        }

        offset?.let { classes.add("offset-${it.coerceIn(0, 11)}") }
        offsetSm?.let { classes.add("offset-sm-${it.coerceIn(0, 11)}") }
        offsetMd?.let { classes.add("offset-md-${it.coerceIn(0, 11)}") }
        offsetLg?.let { classes.add("offset-lg-${it.coerceIn(0, 11)}") }
        offsetXl?.let { classes.add("offset-xl-${it.coerceIn(0, 11)}") }
        offsetXxl?.let { classes.add("offset-xxl-${it.coerceIn(0, 11)}") }

        order?.let { classes.add("order-${it.coerceIn(0, 5)}") }
        orderSm?.let { classes.add("order-sm-${it.coerceIn(0, 5)}") }
        orderMd?.let { classes.add("order-md-${it.coerceIn(0, 5)}") }
        orderLg?.let { classes.add("order-lg-${it.coerceIn(0, 5)}") }
        orderXl?.let { classes.add("order-xl-${it.coerceIn(0, 5)}") }
        orderXxl?.let { classes.add("order-xxl-${it.coerceIn(0, 5)}") }

        return classes
    }

    /**
     * Resolves whether `auto` width is active at the given [tier].
     */
    fun resolveAutoWidthForBreakpoint(tier: ResponsiveBreakpoint): Boolean {
        var resolved = autoWidth
        if (tier.ordinal >= ResponsiveBreakpoint.SM.ordinal) {
            if (smAuto) resolved = true else if (sm != null) resolved = false
        }
        if (tier.ordinal >= ResponsiveBreakpoint.MD.ordinal) {
            if (mdAuto) resolved = true else if (md != null) resolved = false
        }
        if (tier.ordinal >= ResponsiveBreakpoint.LG.ordinal) {
            if (lgAuto) resolved = true else if (lg != null) resolved = false
        }
        if (tier.ordinal >= ResponsiveBreakpoint.XL.ordinal) {
            if (xlAuto) resolved = true else if (xl != null) resolved = false
        }
        if (tier.ordinal >= ResponsiveBreakpoint.XXL.ordinal) {
            if (xxlAuto) resolved = true else if (xxl != null) resolved = false
        }
        return resolved
    }

    /**
     * Resolves the effective 1..12 column span for the given [tier] using Bootstrap 5 mobile-first cascading rules,
     * or returns `null` when equal-width `.col` or `.col-*-auto` sizing applies.
     */
    fun resolveSpanForBreakpoint(tier: ResponsiveBreakpoint): Int? {
        if (resolveAutoWidthForBreakpoint(tier)) return null

        val hasResponsiveSpan = sm != null || md != null || lg != null || xl != null || xxl != null
        var resolved: Int? = span?.coerceIn(1, 12) ?: if (hasResponsiveSpan && !autoWidth) 12 else null

        if (tier.ordinal >= ResponsiveBreakpoint.SM.ordinal) {
            if (smAuto) resolved = null else if (sm != null) resolved = sm.coerceIn(1, 12)
        }
        if (tier.ordinal >= ResponsiveBreakpoint.MD.ordinal) {
            if (mdAuto) resolved = null else if (md != null) resolved = md.coerceIn(1, 12)
        }
        if (tier.ordinal >= ResponsiveBreakpoint.LG.ordinal) {
            if (lgAuto) resolved = null else if (lg != null) resolved = lg.coerceIn(1, 12)
        }
        if (tier.ordinal >= ResponsiveBreakpoint.XL.ordinal) {
            if (xlAuto) resolved = null else if (xl != null) resolved = xl.coerceIn(1, 12)
        }
        if (tier.ordinal >= ResponsiveBreakpoint.XXL.ordinal) {
            if (xxlAuto) resolved = null else if (xxl != null) resolved = xxl.coerceIn(1, 12)
        }
        return resolved
    }
}
