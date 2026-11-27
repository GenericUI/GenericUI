package com.nexustools.genericui.component.typography

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolver
import com.nexustools.genericui.style.css.resolveHostDefaultBackendTarget

/**
 * Single-line or multi-line text label component (`<label>` / `<span>`).
 *
 * When [target] is bound:
 * - On Web (`supportsNativeHtmlLabelFor == true`), ensures [target] has a stable DOM `id` via
 *   [ILabelTarget.ensureLabelTargetId] and synchronizes the HTML `"for"` attribute on this label.
 * - On all non-Web backends, clicking the label invokes [ILabelTarget.focusFromLabel] without
 *   clobbering any user-supplied [onClick] callback.
 */
open class Label(
    text: String = "",
    private val tagName: String = "label",
    private val classPrefix: String = "form-label",
    target: ILabelTarget? = null
) : AbstractComponent(componentClassPrefix = classPrefix, htmlTag = tagName) {
    private var managedForTargetId: String? = null

    @set:kotlin.jvm.JvmName("assignTarget")
    var target: ILabelTarget? = target
        set(value) {
            if (field === value) return
            field = value
            syncLabelTargetBinding()
        }

    init {
        htmlTag = tagName
        syncLabelTargetBinding()
    }

    override val acceptsTypographyComponentStyles: Boolean
        get() = true

    var textRes: ResourceID? = null
        private set

    constructor(text: String, target: ILabelTarget?) : this(
        text = text,
        tagName = "label",
        classPrefix = "form-label",
        target = target
    )

    constructor(resId: ResourceID, target: ILabelTarget? = null) : this(
        text = ResourceResolver.getString(resId),
        target = target
    ) {
        this.textRes = resId
    }

    fun setTarget(target: ILabelTarget?): Label = apply {
        this.target = target
    }

    fun setText(resId: ResourceID, vararg formatArgs: Any) {
        this.textRes = resId
        this.text = ResourceResolver.getString(resId, *formatArgs)
    }

    var text: String = text
        set(value) {
            if (field != value) {
                field = value
                notifyParentCacheDirty()
                runOnNativeUi { (it as? TextNativeImpl<*>)?.setText(value) }
            }
        }

    var maxLines: Int? = null
        set(value) {
            field = value
            runOnNativeUi { (it as? TextNativeImpl<*>)?.setMaxLines(value) }
        }

    /**
     * `kotlinx.html`-style unary plus operator to append text to this label/heading/paragraph.
     */
    operator fun String.unaryPlus() {
        text = if (text.isEmpty()) this else text + this
    }

    private fun usesWebHtmlLabelForAssociation(): Boolean {
        val factory = resolveActiveFactory() ?: GenericUI.activeFactory
        if (factory != null) {
            return factory.supportsNativeHtmlLabelFor
        }
        return resolveHostDefaultBackendTarget().isBrowser
    }

    private fun syncLabelTargetBinding() {
        val currentTarget = target
        if (currentTarget == null) {
            if (managedForTargetId != null) {
                managedForTargetId = null
                removeAttribute("for")
            }
            internalClickAction = null
            return
        }
        if (usesWebHtmlLabelForAssociation()) {
            internalClickAction = null
            val resolvedId = currentTarget.ensureLabelTargetId()
            managedForTargetId = resolvedId
            setAttribute("for", resolvedId)
        } else {
            if (managedForTargetId != null) {
                managedForTargetId = null
                removeAttribute("for")
            }
            internalClickAction = {
                this.target?.focusFromLabel()
            }
        }
    }

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        syncLabelTargetBinding()
        super.onAttachNative(impl)
    }

    protected override fun createNativeImpl(factory: NativeFactory): TextNativeImpl<*> =
        factory.createText(htmlTag, classPrefix)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TextNativeImpl<*>>() ?: return
        impl.setText(text)
        impl.setMaxLines(maxLines)
    }
}
