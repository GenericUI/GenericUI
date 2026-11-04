package com.nexustools.genericui.component.layout

/**
 * Bootstrap 5 grid column component (`<genericui:col>`) hosted inside a [ColumnLayout] / [ColumnContainer].
 *
 * Supports full Bootstrap 5 responsive column classes (`.col`, `.col-auto`, `.col-1`..`.col-12`,
 * `.col-{sm,md,lg,xl,xxl}-{1..12|auto}`, `.offset-*`, and `.order-*`).
 */
open class Column(
    spec: ColumnSpec = ColumnSpec()
) : Container("genericui:col", "col") {

    constructor(
        span: Int?,
        sm: Int? = null,
        md: Int? = null,
        lg: Int? = null,
        xl: Int? = null,
        xxl: Int? = null,
        autoWidth: Boolean = false
    ) : this(
        ColumnSpec(
            span = span,
            autoWidth = autoWidth,
            sm = sm,
            md = md,
            lg = lg,
            xl = xl,
            xxl = xxl
        )
    )

    private val managedBootstrapClasses = mutableSetOf<String>()

    var spec: ColumnSpec = spec
        set(value) {
            field = value
            syncColumnClasses()
        }

    var span: Int?
        get() = spec.span
        set(value) {
            spec = spec.copy(span = value?.coerceIn(1, 12))
        }

    var autoWidth: Boolean
        get() = spec.autoWidth
        set(value) {
            spec = spec.copy(autoWidth = value)
        }

    var sm: Int?
        get() = spec.sm
        set(value) {
            spec = spec.copy(sm = value?.coerceIn(1, 12))
        }

    var smAuto: Boolean
        get() = spec.smAuto
        set(value) {
            spec = spec.copy(smAuto = value)
        }

    var md: Int?
        get() = spec.md
        set(value) {
            spec = spec.copy(md = value?.coerceIn(1, 12))
        }

    var mdAuto: Boolean
        get() = spec.mdAuto
        set(value) {
            spec = spec.copy(mdAuto = value)
        }

    var lg: Int?
        get() = spec.lg
        set(value) {
            spec = spec.copy(lg = value?.coerceIn(1, 12))
        }

    var lgAuto: Boolean
        get() = spec.lgAuto
        set(value) {
            spec = spec.copy(lgAuto = value)
        }

    var xl: Int?
        get() = spec.xl
        set(value) {
            spec = spec.copy(xl = value?.coerceIn(1, 12))
        }

    var xlAuto: Boolean
        get() = spec.xlAuto
        set(value) {
            spec = spec.copy(xlAuto = value)
        }

    var xxl: Int?
        get() = spec.xxl
        set(value) {
            spec = spec.copy(xxl = value?.coerceIn(1, 12))
        }

    var xxlAuto: Boolean
        get() = spec.xxlAuto
        set(value) {
            spec = spec.copy(xxlAuto = value)
        }

    var offset: Int?
        get() = spec.offset
        set(value) {
            spec = spec.copy(offset = value?.coerceIn(0, 11))
        }

    var offsetSm: Int?
        get() = spec.offsetSm
        set(value) {
            spec = spec.copy(offsetSm = value?.coerceIn(0, 11))
        }

    var offsetMd: Int?
        get() = spec.offsetMd
        set(value) {
            spec = spec.copy(offsetMd = value?.coerceIn(0, 11))
        }

    var offsetLg: Int?
        get() = spec.offsetLg
        set(value) {
            spec = spec.copy(offsetLg = value?.coerceIn(0, 11))
        }

    var offsetXl: Int?
        get() = spec.offsetXl
        set(value) {
            spec = spec.copy(offsetXl = value?.coerceIn(0, 11))
        }

    var offsetXxl: Int?
        get() = spec.offsetXxl
        set(value) {
            spec = spec.copy(offsetXxl = value?.coerceIn(0, 11))
        }

    var order: Int?
        get() = spec.order
        set(value) {
            spec = spec.copy(order = value?.coerceIn(0, 5))
        }

    var orderSm: Int?
        get() = spec.orderSm
        set(value) {
            spec = spec.copy(orderSm = value?.coerceIn(0, 5))
        }

    var orderMd: Int?
        get() = spec.orderMd
        set(value) {
            spec = spec.copy(orderMd = value?.coerceIn(0, 5))
        }

    var orderLg: Int?
        get() = spec.orderLg
        set(value) {
            spec = spec.copy(orderLg = value?.coerceIn(0, 5))
        }

    var orderXl: Int?
        get() = spec.orderXl
        set(value) {
            spec = spec.copy(orderXl = value?.coerceIn(0, 5))
        }

    var orderXxl: Int?
        get() = spec.orderXxl
        set(value) {
            spec = spec.copy(orderXxl = value?.coerceIn(0, 5))
        }

    init {
        syncColumnClasses()
    }

    /**
     * Synchronizes the Bootstrap 5 `.col*`, `.offset-*`, and `.order-*` classes on this [Column].
     */
    fun syncColumnClasses() {
        for (oldClass in managedBootstrapClasses) {
            removeClass(oldClass)
        }
        managedBootstrapClasses.clear()

        val nextClasses = spec.toBootstrapClasses()
        if ("col" !in nextClasses) {
            removeClass("col")
        }
        for (cls in nextClasses) {
            addClass(cls)
            managedBootstrapClasses.add(cls)
        }
        val ord = spec.order
        if (ord != null) {
            setStyleProperty("order", ord.toString())
        } else {
            removeStyleProperty("order")
        }
    }
}
